package cn.sijay.oi.sys.service;

import cn.sijay.oi.common.exception.BusinessErrorCode;
import cn.sijay.oi.common.exception.BusinessException;
import cn.sijay.oi.common.model.PageQuery;
import cn.sijay.oi.sys.entity.SysMessage;
import cn.sijay.oi.sys.entity.SysMessageBroadcastState;
import cn.sijay.oi.sys.entity.SysMessageReceipt;
import cn.sijay.oi.sys.entity.SysUser;
import cn.sijay.oi.sys.model.MessageSendCmd;
import cn.sijay.oi.sys.model.MessageVO;
import cn.sijay.oi.sys.repository.SysMessageBroadcastStateRepository;
import cn.sijay.oi.sys.repository.SysMessageReceiptRepository;
import cn.sijay.oi.sys.repository.SysMessageRepository;
import cn.sijay.oi.sys.repository.SysUserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * 站内信业务层：三类消息（系统通知/私信/公告广播）的发送、收件箱查询、
 * 已读/未读标记、批量操作、归档、撤回与未读数计算。
 *
 * <p><b>存储模型</b>：消息主表（sys_message）+ 点对点回执（sys_message_receipt，
 * 发送时生成）+ 广播个性化状态（sys_message_broadcast_state，惰性创建）。
 * 收件箱为"点对点回执 UNION 广播"的原生 SQL 统一分页视图（见 {@link #inbox}）。
 *
 * <p><b>权限模型</b>：所有用户侧操作均以当前登录人 id 作为 SQL 条件（回执/状态行
 * 仅在本人数据上生效），详情访问校验收发双方身份，杜绝跨用户查看；
 * 系统通知与公告广播仅系统管理员可发布（{@code StpUtil.hasRole("admin")}）。
 *
 * <p><b>实时推送</b>：发送/撤回后经 {@link MessageSseService} 推送 SSE 事件
 * （message / unread / withdraw），前端角标与列表实时刷新。
 *
 * @author sijay
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SysMessageService {

    /**
     * 撤回时限：发送后 5 分钟内可撤回。
     */
    private static final Duration WITHDRAW_WINDOW = Duration.ofMinutes(5);

    /**
     * 消息内容最长长度（与 DDL 一致）。
     */
    private static final int CONTENT_MAX_LENGTH = 4000;

    private final SysMessageRepository messageRepository;
    private final SysMessageReceiptRepository receiptRepository;
    private final SysMessageBroadcastStateRepository broadcastStateRepository;
    private final SysUserRepository userRepository;
    private final MessageSseService sseService;
    private final EntityManager entityManager;

    /* ==================== 发送 ==================== */

    /**
     * 发送站内信。
     *
     * <ul>
     *   <li>LETTER（私信）：任意登录用户可发；校验接收人存在且启用、不能发给自己；</li>
     *   <li>NOTICE / BROADCAST：仅系统管理员可发布（非管理员抛 607009）；</li>
     * </ul>
     * 点对点消息落库同时生成回执；发送成功后经 SSE 实时推送给接收方
     * （message 事件携带消息摘要，unread 事件携带最新未读数）。
     *
     * @param cmd      发送入参（格式校验已由 Bean Validation 完成）
     * @param senderId 发送人 id
     * @return 已落库的消息 id
     * @throws BusinessException 接收人不存在（607003）/
     *                           接收人禁用（607004）/ 发给自己（607008）/
     *                           非管理员发布（607009）
     */
    @Transactional
    public Long send(MessageSendCmd cmd, Long senderId) {
        SysMessage.MessageType type = SysMessage.MessageType.valueOf(cmd.getMsgType());
        String attachmentIds = joinAttachmentIds(cmd.getAttachmentIds());

        SysMessage message = new SysMessage();
        message.setMsgType(type.name());
        message.setSenderId(senderId);
        message.setTitle(cmd.getTitle());
        message.setContent(cmd.getContent());
        message.setAttachmentIds(attachmentIds);
        message.setStatus(SysMessage.MessageStatus.SENT.name());
        message.setSendTime(LocalDateTime.now());
        message.setCreateBy(senderId);

        if (type == SysMessage.MessageType.BROADCAST) {
            requireAdmin();
            messageRepository.save(message);
            // 全员广播：向全部在线连接推送新消息事件（未读数由客户端本地累加或拉取）
            sseService.broadcast("message", summarize(message));
            log.info("[SysMessage] 公告广播发送成功: id={}, sender={}", message.getId(), senderId);
            return message.getId();
        }

        requireAdminForNotice(type);
        SysUser receiver = userRepository.findByUsername(cmd.getReceiverUsername())
                                         .orElseThrow(() -> new BusinessException(BusinessErrorCode.MESSAGE_RECEIVER_NOT_FOUND));
        if (receiver.getId().equals(senderId)) {
            throw new BusinessException(BusinessErrorCode.MESSAGE_SELF_RECEIVE);
        }
        if (Boolean.FALSE.equals(receiver.getEnabled())) {
            throw new BusinessException(BusinessErrorCode.MESSAGE_RECEIVER_DISABLED);
        }
        message.setReceiverId(receiver.getId());
        messageRepository.save(message);

        SysMessageReceipt receipt = new SysMessageReceipt();
        receipt.setMessageId(message.getId());
        receipt.setReceiverId(receiver.getId());
        receiptRepository.save(receipt);

        // 实时推送：新消息 + 最新未读数
        sseService.pushToUser(receiver.getId(), "message", summarize(message));
        sseService.pushToUser(receiver.getId(), "unread", String.valueOf(countUnread(receiver.getId())));
        log.info("[SysMessage] 点对点消息发送成功: id={}, type={}, sender={}, receiver={}",
                message.getId(), type, senderId, receiver.getId());
        return message.getId();
    }

    /* ==================== 收件箱（点对点 + 广播统一视图） ==================== */

    /**
     * 收件箱分页查询：点对点回执与公告广播的统一视图。
     *
     * <p>过滤条件（均可选）：消息类型 / 已读状态 / 归档标记 / 标题内容关键词；
     * 固定按发送时间倒序。实现为动态条件原生 SQL（UNION ALL + 参数化过滤），
     * 收件箱过滤默认排除已归档行（归档箱请传 archived=1）。
     *
     * @param receiverId 当前登录人 id
     * @param msgType    消息类型过滤；空为全部
     * @param readStatus 已读状态过滤（0/1）；null 为全部
     * @param archived   归档过滤（0/1）；null 为全部（含归档与未归档）
     * @param keyword    标题/内容模糊关键词；空为全部
     * @param page       分页参数
     * @return 收件箱分页
     */
    @Transactional(readOnly = true)
    public Page<MessageVO> inbox(Long receiverId, String msgType, Integer readStatus,
                                 Integer archived, String keyword, PageQuery page) {
        page.validate();
        StringBuilder where = new StringBuilder(" where 1 = 1");
        var params = new ArrayList<Object>();
        if (notBlank(msgType)) {
            where.append(" and t.msg_type = ?").append(params.size() + 1);
            params.add(msgType);
        }
        if (readStatus != null) {
            where.append(" and t.read_status = ?").append(params.size() + 1);
            params.add(readStatus);
        }
        if (archived != null) {
            where.append(" and t.archived = ?").append(params.size() + 1);
            params.add(archived);
        }
        if (notBlank(keyword)) {
            int idx = params.size() + 1;
            where.append(" and (lower(t.title) like ?").append(idx)
                 .append(" or lower(t.content) like ?").append(idx + 1).append(")");
            String kw = "%" + keyword.toLowerCase() + "%";
            params.add(kw);
            params.add(kw);
        }

        String baseSql = """
                select * from (
                  select m.id as message_id, m.msg_type as msg_type, m.sender_id as sender_id,
                         m.title as title, m.content as content, m.attachment_ids as attachment_ids,
                         m.status as status, m.send_time as send_time,
                         r.read_status as read_status, r.archived as archived
                    from sys_message_receipt r
                    join sys_message m on m.id = r.message_id and m.status = 'SENT' and m.deleted = 0
                   where r.receiver_id = ?UID and r.deleted = 0
                  union all
                  select m.id, m.msg_type, m.sender_id, m.title, m.content, m.attachment_ids,
                         m.status, m.send_time,
                         coalesce(s.read_status, 0), coalesce(s.archived, 0)
                    from sys_message m
                    left join sys_message_broadcast_state s
                           on s.message_id = m.id and s.user_id = ?UID and s.deleted = 0
                   where m.msg_type = 'BROADCAST' and m.status = 'SENT' and m.deleted = 0
                ) t
                """;
        String dataSql = baseSql.replace("?UID", String.valueOf(receiverId)) + where
                + " order by t.send_time desc, t.message_id desc limit ?" + (params.size() + 1)
                + " offset ?" + (params.size() + 2);
        String countSql = "select count(*) from (" + baseSql.replace("?UID", String.valueOf(receiverId))
                + where + ") ct";

        var dataQuery = entityManager.createNativeQuery(dataSql, Tuple.class)
                                     .setParameter(params.size() + 1, page.pageSize())
                                     .setParameter(params.size() + 2, page.offset());
        for (int i = 0; i < params.size(); i++) {
            dataQuery.setParameter(i + 1, params.get(i));
        }
        @SuppressWarnings("unchecked")
        List<Tuple> rows = dataQuery.getResultList();

        var countQuery = entityManager.createNativeQuery(countSql);
        for (int i = 0; i < params.size(); i++) {
            countQuery.setParameter(i + 1, params.get(i));
        }
        long total = ((Number) countQuery.getSingleResult()).longValue();

        List<MessageVO> content = rows.stream().map(tuple -> toVO(tuple, receiverId)).toList();
        return new PageImpl<>(content, PageRequest.of(page.pageNum() - 1, page.pageSize()), total);
    }

    /* ==================== 发件箱 ==================== */

    /**
     * 发件箱分页查询：当前登录人发送的消息（含已撤回，状态列展示撤回标记）。
     *
     * @param senderId 当前登录人 id
     * @param msgType  消息类型过滤；空为全部
     * @param keyword  标题/内容模糊关键词；空为全部
     * @param page     分页参数
     * @return 发件箱分页
     */
    @Transactional(readOnly = true)
    public Page<MessageVO> sent(Long senderId, String msgType, String keyword, PageQuery page) {
        page.validate();
        StringBuilder where = new StringBuilder("""
                where m.sender_id = ?1 and m.deleted = 0
                """);
        var params = new ArrayList<Object>();
        params.add(senderId);
        if (notBlank(msgType)) {
            where.append(" and m.msg_type = ?").append(params.size() + 1);
            params.add(msgType);
        }
        if (notBlank(keyword)) {
            int idx = params.size() + 1;
            where.append(" and (lower(m.title) like ?").append(idx)
                 .append(" or lower(m.content) like ?").append(idx + 1).append(")");
            String kw = "%" + keyword.toLowerCase() + "%";
            params.add(kw);
            params.add(kw);
        }
        String baseSql = "select m.id as message_id, m.msg_type as msg_type, m.sender_id as sender_id, "
                + "m.title as title, m.content as content, m.attachment_ids as attachment_ids, "
                + "m.status as status, m.send_time as send_time, "
                + "cast(null as int) as read_status, cast(null as int) as archived from sys_message m ";
        String dataSql = baseSql + where + " order by m.send_time desc, m.id desc limit ?"
                + (params.size() + 1) + " offset ?" + (params.size() + 2);
        String countSql = "select count(*) from sys_message m " + where;

        var dataQuery = entityManager.createNativeQuery(dataSql, Tuple.class)
                                     .setParameter(params.size() + 1, page.pageSize())
                                     .setParameter(params.size() + 2, page.offset());
        for (int i = 0; i < params.size(); i++) {
            dataQuery.setParameter(i + 1, params.get(i));
        }
        @SuppressWarnings("unchecked")
        List<Tuple> rows = dataQuery.getResultList();

        var countQuery = entityManager.createNativeQuery(countSql);
        for (int i = 0; i < params.size(); i++) {
            countQuery.setParameter(i + 1, params.get(i));
        }
        long total = ((Number) countQuery.getSingleResult()).longValue();

        List<MessageVO> content = rows.stream().map(tuple -> toVO(tuple, senderId)).toList();
        return new PageImpl<>(content, PageRequest.of(page.pageNum() - 1, page.pageSize()), total);
    }

    /* ==================== 详情 ==================== */

    /**
     * 查询消息详情（收发双方可见；接收方首次查看自动标记已读）。
     *
     * <p>权限校验：仅消息发送人、点对点接收人、广播全员（任意登录用户）可见，
     * 其余一律 607002，杜绝跨用户查看。
     *
     * @param userId 当前登录人 id
     * @param id     消息 id
     * @return 消息详情
     * @throws BusinessException 消息不存在（607001）/ 无权查看（607002）
     */
    @Transactional
    public MessageVO detail(Long userId, Long id) {
        SysMessage message = messageRepository.findById(id)
                                              .orElseThrow(() -> new BusinessException(BusinessErrorCode.MESSAGE_NOT_FOUND));
        boolean isBroadcast = message.getMsgType().equals(SysMessage.MessageType.BROADCAST.name());
        SysMessageReceipt receipt = receiptRepository.findByMessageIdAndReceiverId(id, userId).orElse(null);
        boolean isSender = message.getSenderId().equals(userId);
        if (!isSender && !isBroadcast && receipt == null) {
            throw new BusinessException(BusinessErrorCode.MESSAGE_FORBIDDEN);
        }
        // 接收方首次查看自动标记已读（广播同理），并同步未读数
        if (!isSender && isBroadcast) {
            markBroadcastRead(userId, List.of(id));
        } else if (receipt != null && receipt.getReadStatus() == 0) {
            receipt.setReadStatus(1);
            receipt.setReadTime(LocalDateTime.now());
        }
        MessageVO vo = toVO(message);
        vo.setReadStatus(isSender ? null
                : (isBroadcast ? 1 : (receipt != null ? receipt.getReadStatus() : null)));
        vo.setArchived(isSender ? null
                : (isBroadcast ? null : (receipt != null ? receipt.getArchived() : null)));
        vo.setWithdrawable(isWithdrawable(message, userId));
        if (!isSender) {
            sseService.pushToUser(userId, "unread", String.valueOf(countUnread(userId)));
        }
        return vo;
    }

    /* ==================== 已读 / 归档 / 删除 ==================== */

    /**
     * 批量标记已读（点对点回执批量更新 + 广播状态行惰性 upsert）。
     *
     * @param userId 当前登录人 id
     * @param ids    消息 id 列表
     * @return 实际新标记为已读的条数
     */
    @Transactional
    public int markRead(Long userId, Collection<Long> ids) {
        int personal = receiptRepository.markRead(ids, userId);
        int broadcast = 0;
        for (Long id : ids) {
            if (isBroadcastMessage(id)) {
                broadcast += markBroadcastRead(userId, List.of(id));
            }
        }
        sseService.pushToUser(userId, "unread", String.valueOf(countUnread(userId)));
        return personal + broadcast;
    }

    /**
     * 全部标记已读（本人所有未读回执 + 全部未读广播）。
     *
     * @param userId 当前登录人 id
     * @return 实际标记条数
     */
    @Transactional
    public int markAllRead(Long userId) {
        int personal = receiptRepository.markAllRead(userId);
        int broadcast = 0;
        for (Long id : unreadBroadcastIds(userId)) {
            broadcast += markBroadcastRead(userId, List.of(id));
        }
        sseService.pushToUser(userId, "unread", "0");
        return personal + broadcast;
    }

    /**
     * 批量归档/取消归档。
     *
     * @param userId   当前登录人 id
     * @param ids      消息 id 列表
     * @param archived 1=归档 0=取消归档
     * @return 实际生效条数
     */
    @Transactional
    public int archive(Long userId, Collection<Long> ids, Integer archived) {
        int personal = receiptRepository.archive(ids, userId, archived);
        int broadcast = 0;
        for (Long id : ids) {
            if (isBroadcastMessage(id)) {
                broadcast += upsertBroadcastState(userId, id, state -> state.setArchived(archived));
            }
        }
        return personal + broadcast;
    }

    /**
     * 批量删除（点对点回执软删；广播落"用户已删除"状态行，仅影响本人可见性）。
     *
     * @param userId 当前登录人 id
     * @param ids    消息 id 列表
     * @return 实际删除条数
     */
    @Transactional
    public int delete(Long userId, Collection<Long> ids) {
        int personal = receiptRepository.softDelete(ids, userId);
        int broadcast = 0;
        for (Long id : ids) {
            if (isBroadcastMessage(id)) {
                broadcast += upsertBroadcastState(userId, id, state -> {
                    if (state.getDeleted() == 0) {
                        state.setDeleted(state.getId());
                    }
                });
            }
        }
        sseService.pushToUser(userId, "unread", String.valueOf(countUnread(userId)));
        return personal + broadcast;
    }

    /* ==================== 撤回 ==================== */

    /**
     * 撤回消息：仅发送人本人、发送后 5 分钟内、已发送状态可撤回（重复撤回幂等成功）。
     *
     * <p>撤回效果：消息状态置为 WITHDRAWN + 点对点回执全部软删（所有接收人
     * 收件箱同步移除）+ SSE 推送 withdraw 事件（接收端实时移除并刷新角标）。
     *
     * @param userId 当前登录人 id
     * @param id     消息 id
     * @throws BusinessException 消息不存在（601007 段 607001）/ 非发送人（607007）/
     *                           超过时限（607006）
     */
    @Transactional
    public void withdraw(Long userId, Long id) {
        SysMessage message = messageRepository.findById(id)
                                              .orElseThrow(() -> new BusinessException(BusinessErrorCode.MESSAGE_NOT_FOUND));
        if (message.getStatus().equals(SysMessage.MessageStatus.WITHDRAWN.name())) {
            return;
        }
        if (!message.getSenderId().equals(userId)) {
            throw new BusinessException(BusinessErrorCode.MESSAGE_WITHDRAW_FORBIDDEN);
        }
        if (message.getSendTime().plus(WITHDRAW_WINDOW).isBefore(LocalDateTime.now())) {
            throw new BusinessException(BusinessErrorCode.MESSAGE_WITHDRAW_EXPIRED);
        }
        messageRepository.withdraw(id);
        boolean broadcast = message.getMsgType().equals(SysMessage.MessageType.BROADCAST.name());
        if (!broadcast) {
            receiptRepository.softDeleteByMessageIds(List.of(id));
            sseService.pushToUser(message.getReceiverId(), "withdraw", String.valueOf(id));
            sseService.pushToUser(message.getReceiverId(), "unread",
                    String.valueOf(countUnread(message.getReceiverId())));
        } else {
            sseService.broadcast("withdraw", String.valueOf(id));
        }
        log.info("[SysMessage] 消息撤回成功: id={}, operator={}", id, userId);
    }

    /* ==================== 未读数 ==================== */

    /**
     * 计算当前未读数：点对点未读回执 + 未读广播（已读或已删除的广播不计）。
     *
     * @param userId 用户 id
     * @return 未读总数
     */
    @Transactional(readOnly = true)
    public long countUnread(Long userId) {
        String sql = """
                select (
                  select count(*) from sys_message_receipt r
                   where r.receiver_id = ?1 and r.deleted = 0 and r.read_status = 0
                ) + (
                  select count(*) from sys_message m
                   where m.msg_type = 'BROADCAST' and m.status = 'SENT' and m.deleted = 0
                     and not exists (select 1 from sys_message_broadcast_state s
                                      where s.message_id = m.id and s.user_id = ?1 and s.read_status = 1)
                     and not exists (select 1 from sys_message_broadcast_state s2
                                      where s2.message_id = m.id and s2.user_id = ?1 and s2.deleted <> 0)
                )
                """;
        return ((Number) entityManager.createNativeQuery(sql)
                                      .setParameter(1, userId)
                                      .getSingleResult()).longValue();
    }

    /* ==================== SSE 连接 ==================== */

    /**
     * 建立 SSE 实时推送连接（控制器直接返回 emitter 进入异步上下文）。
     *
     * @param userId 用户 id（已凭令牌校验）
     * @return SSE emitter 与初始未读数
     */
    public SseConnectResult connectStream(Long userId) {
        long unread = countUnread(userId);
        SseEmitter emitter = sseService.connect(userId, unread);
        return new SseConnectResult(emitter, unread);
    }

    /**
     * SSE 连接结果：emitter + 初始未读数。
     *
     * @param emitter       SSE emitter
     * @param initialUnread 初始未读数
     */
    public record SseConnectResult(SseEmitter emitter, long initialUnread) {
    }

    /* ==================== 私有辅助 ==================== */

    /**
     * 判断消息是否为公告广播（状态行 upsert 仅对广播生效）。
     */
    private boolean isBroadcastMessage(Long messageId) {
        return messageRepository.findById(messageId)
                                .map(m -> m.getMsgType().equals(SysMessage.MessageType.BROADCAST.name()))
                                .orElse(false);
    }

    /**
     * 广播已读 upsert：返回实际新增已读条数（已读幂等不重复计数）。
     */
    private int markBroadcastRead(Long userId, Collection<Long> messageIds) {
        int count = 0;
        for (Long messageId : messageIds) {
            SysMessageBroadcastState state = broadcastStateRepository
                    .findByMessageIdAndUserId(messageId, userId)
                    .orElseGet(() -> {
                        SysMessageBroadcastState created = new SysMessageBroadcastState();
                        created.setMessageId(messageId);
                        created.setUserId(userId);
                        return created;
                    });
            if (state.getDeleted() != 0) {
                continue;
            }
            if (state.getReadStatus() == 0) {
                state.setReadStatus(1);
                state.setReadTime(LocalDateTime.now());
                broadcastStateRepository.save(state);
                count++;
            }
        }
        return count;
    }

    /**
     * 广播状态行惰性 upsert：不存在则先落行取得自增 id，再应用变更并保存。
     *
     * @param userId    用户 id
     * @param messageId 消息 id
     * @param mutator   状态变更动作
     * @return 实际落库条数（恒为 1）
     */
    private int upsertBroadcastState(Long userId, Long messageId, java.util.function.Consumer<SysMessageBroadcastState> mutator) {
        SysMessageBroadcastState state = broadcastStateRepository
                .findByMessageIdAndUserId(messageId, userId)
                .orElseGet(() -> {
                    SysMessageBroadcastState created = new SysMessageBroadcastState();
                    created.setMessageId(messageId);
                    created.setUserId(userId);
                    return created;
                });
        if (state.getId() == null) {
            // 新行先落库取得自增 id（"用户删除"需以本行主键作为 deleted 标记）
            broadcastStateRepository.save(state);
        }
        mutator.accept(state);
        broadcastStateRepository.save(state);
        return 1;
    }

    /**
     * 当前用户的未读广播消息 id 列表（"全部已读"前的批量 upsert 用）。
     */
    @SuppressWarnings("unchecked")
    private List<Long> unreadBroadcastIds(Long userId) {
        String sql = """
                select m.id from sys_message m
                 where m.msg_type = 'BROADCAST' and m.status = 'SENT' and m.deleted = 0
                   and not exists (select 1 from sys_message_broadcast_state s
                                    where s.message_id = m.id and s.user_id = ?1 and s.read_status = 1)
                   and not exists (select 1 from sys_message_broadcast_state s2
                                    where s2.message_id = m.id and s2.user_id = ?1 and s2.deleted <> 0)
                """;
        return entityManager.createNativeQuery(sql)
                            .setParameter(1, userId)
                            .getResultList();
    }

    /**
     * Tuple 行 → VO（收件箱/发件箱通用）。
     */
    private MessageVO toVO(Tuple tuple, Long viewerId) {
        MessageVO vo = new MessageVO();
        vo.setId(tuple.get("message_id", Number.class).longValue());
        vo.setMsgType(tuple.get("msg_type", String.class));
        vo.setSenderId(tuple.get("sender_id", Number.class).longValue());
        vo.setTitle(tuple.get("title", String.class));
        vo.setContent(tuple.get("content", String.class));
        vo.setAttachmentIds(splitAttachmentIds(tuple.get("attachment_ids", String.class)));
        vo.setStatus(tuple.get("status", String.class));
        Timestamp sendTime = null;
        Object sendTimeRaw = tuple.get("send_time");
        if (sendTimeRaw instanceof Timestamp ts) {
            sendTime = ts;
        } else if (sendTimeRaw instanceof LocalDateTime ldt) {
            sendTime = Timestamp.valueOf(ldt);
        }
        vo.setSendTime(sendTime == null ? null : sendTime.toLocalDateTime());
        Number readStatus = tuple.get("read_status", Number.class);
        vo.setReadStatus(readStatus == null ? null : readStatus.intValue());
        Number archived = tuple.get("archived", Number.class);
        vo.setArchived(archived == null ? null : archived.intValue());
        vo.setWithdrawable(isWithdrawable(vo, viewerId));
        return vo;
    }

    /**
     * 实体 → VO（详情用）。
     */
    private MessageVO toVO(SysMessage message) {
        MessageVO vo = new MessageVO();
        vo.setId(message.getId());
        vo.setMsgType(message.getMsgType());
        vo.setSenderId(message.getSenderId());
        vo.setReceiverId(message.getReceiverId());
        vo.setTitle(message.getTitle());
        vo.setContent(message.getContent());
        vo.setAttachmentIds(splitAttachmentIds(message.getAttachmentIds()));
        vo.setStatus(message.getStatus());
        vo.setSendTime(message.getSendTime());
        return vo;
    }

    /**
     * 是否可撤回：发送人本人 + 已发送 + 发送后 5 分钟内。
     */
    private boolean isWithdrawable(SysMessage message, Long userId) {
        return message.getSenderId().equals(userId)
                && message.getStatus().equals(SysMessage.MessageStatus.SENT.name())
                && message.getSendTime() != null
                && message.getSendTime().plus(WITHDRAW_WINDOW).isAfter(LocalDateTime.now());
    }

    private boolean isWithdrawable(MessageVO vo, Long userId) {
        return vo.getSenderId() != null && Objects.equals(vo.getSenderId(), userId)
                && SysMessage.MessageStatus.SENT.name().equals(vo.getStatus())
                && vo.getSendTime() != null
                && vo.getSendTime().plus(WITHDRAW_WINDOW).isAfter(LocalDateTime.now());
    }

    /**
     * 消息摘要 JSON（SSE message 事件负载；字段精简供角标与轻提示使用）。
     */
    private String summarize(SysMessage message) {
        String title = message.getTitle().replace("\\", "\\\\").replace("\"", "\\\"");
        return "{\"id\":\"%s\",\"msgType\":\"%s\",\"title\":\"%s\",\"senderId\":\"%s\"}"
                .formatted(message.getId(), message.getMsgType(), title, message.getSenderId());
    }

    /**
     * 附件 id 列表 → 逗号分隔存储串（去空白项）。
     */
    private String joinAttachmentIds(List<String> attachmentIds) {
        if (attachmentIds == null || attachmentIds.isEmpty()) {
            return null;
        }
        List<String> cleaned = attachmentIds.stream().filter(id -> id != null && !id.isBlank()).toList();
        return cleaned.isEmpty() ? null : String.join(",", cleaned);
    }

    /**
     * 存储串 → 附件 id 列表。
     */
    private List<String> splitAttachmentIds(String stored) {
        if (stored == null || stored.isBlank()) {
            return List.of();
        }
        return List.of(stored.split(","));
    }

    /**
     * 系统通知发布权限：仅管理员。
     */
    private void requireAdminForNotice(SysMessage.MessageType type) {
        if (type == SysMessage.MessageType.NOTICE) {
            requireAdmin();
        }
    }

    /**
     * 管理员校验（sa-token 角色数据源见 StpInterfaceImpl：超管固定 admin 角色）。
     */
    private void requireAdmin() {
        if (!cn.dev33.satoken.stp.StpUtil.hasRole("admin")) {
            throw new BusinessException(BusinessErrorCode.MESSAGE_PUBLISH_FORBIDDEN);
        }
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }
}
