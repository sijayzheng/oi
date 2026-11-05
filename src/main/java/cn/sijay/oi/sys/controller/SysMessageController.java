package cn.sijay.oi.sys.controller;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.stp.StpUtil;
import cn.sijay.oi.common.model.PageQuery;
import cn.sijay.oi.common.model.Result;
import cn.sijay.oi.common.web.BaseController;
import cn.sijay.oi.sys.model.MessageOperateCmd;
import cn.sijay.oi.sys.model.MessageSendCmd;
import cn.sijay.oi.sys.model.MessageVO;
import cn.sijay.oi.sys.service.SysMessageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

/**
 * 站内信控制器：消息收发、收件箱/发件箱分页、已读/未读标记、批量操作、
 * 归档、撤回、未读数与 SSE 实时推送连接。
 *
 * <p>统一约定：继承 {@link BaseController}，响应一律通过父类 ok/fail 包装方法构建
 * {@link Result}；入参格式校验由 Bean Validation（@Valid）触发，业务校验在
 * {@code SysMessageService}，控制器内不写 try-catch。
 *
 * <p>鉴权约定：除 SSE 连接端点外全部走全局登录校验（SaTokenConfigure）；
 * {@code GET /message/stream} 因浏览器 EventSource 无法携带自定义请求头而
 * 豁免全局拦截，改为<b>凭查询参数令牌手动校验</b>（令牌无效返回 401），
 * 校验语义与全局拦截器一致。
 *
 * <p>接口清单：
 * <pre>
 * GET    /message/stream?satoken=   SSE 实时推送连接（connected/message/unread/withdraw/heartbeat 事件）
 * GET    /message/unread-count      当前用户未读数
 * GET    /message/inbox             收件箱分页（点对点 + 广播统一视图；msgType/readStatus/archived/keyword 过滤）
 * GET    /message/sent              发件箱分页（msgType/keyword 过滤，含已撤回状态展示）
 * GET    /message/{id}              消息详情（收发双方可见，接收方首次查看自动已读）
 * POST   /message                   发送消息（LETTER 任意登录用户；NOTICE/BROADCAST 仅管理员）
 * PUT    /message/read              批量标记已读（body: {ids:[...]})
 * PUT    /message/read-all          全部标记已读
 * PUT    /message/archive           批量归档/取消归档（body: {ids:[...], archived:0|1})
 * PUT    /message/trash             批量删除（body: {ids:[...]}；点对点软删回执，广播落用户删除状态）
 * POST   /message/withdraw/{id}     撤回消息（仅发送人，发送后 5 分钟内，幂等）
 * </pre>
 *
 * @author sijay
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/message")
public class SysMessageController extends BaseController {

    private final SysMessageService messageService;

    /**
     * SSE 实时推送连接。
     *
     * <p>EventSource 无法自定义请求头，令牌经查询参数传递并手动校验
     * （{@code StpUtil.getLoginIdByToken}），无效令牌返回 401。
     * 连接建立后立即推送 connected 事件（携带初始未读数）。
     *
     * @param satoken 登录令牌
     * @return SSE emitter（异步长连接）
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream(@RequestParam("satoken") String satoken) {
        Object loginId = StpUtil.getLoginIdByToken(satoken);
        if (loginId == null) {
            throw new NotLoginException("令牌无效", StpUtil.getLoginType(), NotLoginException.INVALID_TOKEN);
        }
        return messageService.connectStream(Long.parseLong(String.valueOf(loginId))).emitter();
    }

    /**
     * 当前用户未读数（点对点未读 + 未读广播）。
     *
     * @return 未读总数
     */
    @GetMapping("/unread-count")
    public Result<Long> unreadCount() {
        return ok(messageService.countUnread(StpUtil.getLoginIdAsLong()));
    }

    /**
     * 收件箱分页查询（点对点回执 + 公告广播统一视图）。
     *
     * @param msgType    消息类型过滤（NOTICE/LETTER/BROADCAST）；选填
     * @param readStatus 已读状态过滤（0/1）；选填
     * @param archived   归档过滤（0=未归档 1=已归档）；选填
     * @param keyword    标题/内容模糊关键词；选填
     * @param page       分页参数
     * @return 收件箱分页
     */
    @GetMapping("/inbox")
    public Result<List<MessageVO>> inbox(@RequestParam(value = "msgType", required = false) String msgType,
                                         @RequestParam(value = "readStatus", required = false) Integer readStatus,
                                         @RequestParam(value = "archived", required = false) Integer archived,
                                         @RequestParam(value = "keyword", required = false) String keyword,
                                         @Valid PageQuery page) {
        // 归档语义：归档即从默认收件箱移除——缺省按"未归档"过滤，归档箱显式传 archived=1
        Integer archivedFilter = archived == null ? 0 : archived;
        Page<MessageVO> result = messageService.inbox(StpUtil.getLoginIdAsLong(), msgType, readStatus, archivedFilter, keyword, page);
        return ok(result.getContent(), result.getTotalElements());
    }

    /**
     * 发件箱分页查询（本人发送的消息，含已撤回状态展示）。
     *
     * @param msgType 消息类型过滤；选填
     * @param keyword 标题/内容模糊关键词；选填
     * @param page    分页参数
     * @return 发件箱分页
     */
    @GetMapping("/sent")
    public Result<List<MessageVO>> sent(@RequestParam(value = "msgType", required = false) String msgType,
                                        @RequestParam(value = "keyword", required = false) String keyword,
                                        @Valid PageQuery page) {
        Page<MessageVO> result = messageService.sent(StpUtil.getLoginIdAsLong(), msgType, keyword, page);
        return ok(result.getContent(), result.getTotalElements());
    }

    /**
     * 消息详情（收发双方可见；接收方首次查看自动标记已读）。
     *
     * @param id 消息 id
     * @return 消息详情
     */
    @GetMapping("/{id}")
    public Result<MessageVO> detail(@PathVariable Long id) {
        return ok(messageService.detail(StpUtil.getLoginIdAsLong(), id));
    }

    /**
     * 发送消息（LETTER 任意登录用户；NOTICE/BROADCAST 仅管理员，业务层校验）。
     *
     * @param cmd 发送入参
     * @return 消息 id
     */
    @PostMapping
    public Result<Long> send(@RequestBody @Valid MessageSendCmd cmd) {
        return ok(messageService.send(cmd, StpUtil.getLoginIdAsLong()));
    }

    /**
     * 批量标记已读（仅本人消息生效）。
     *
     * @param cmd 操作入参（ids）
     * @return 实际新标记条数
     */
    @PutMapping("/read")
    public Result<Integer> markRead(@RequestBody @Valid MessageOperateCmd cmd) {
        return ok(messageService.markRead(StpUtil.getLoginIdAsLong(), cmd.getIds()));
    }

    /**
     * 全部标记已读。
     *
     * @return 实际标记条数
     */
    @PutMapping("/read-all")
    public Result<Integer> markAllRead() {
        return ok(messageService.markAllRead(StpUtil.getLoginIdAsLong()));
    }

    /**
     * 批量归档/取消归档（仅本人消息生效）。
     *
     * @param cmd 操作入参（ids + archived）
     * @return 实际生效条数
     */
    @PutMapping("/archive")
    public Result<Integer> archive(@RequestBody @Valid MessageOperateCmd cmd) {
        return ok(messageService.archive(StpUtil.getLoginIdAsLong(), cmd.getIds(), cmd.getArchived()));
    }

    /**
     * 批量删除（仅本人消息生效；点对点软删回执，广播落用户删除状态）。
     *
     * @param cmd 操作入参（ids）
     * @return 实际删除条数
     */
    @PutMapping("/trash")
    public Result<Integer> trash(@RequestBody @Valid MessageOperateCmd cmd) {
        return ok(messageService.delete(StpUtil.getLoginIdAsLong(), cmd.getIds()));
    }

    /**
     * 撤回消息（仅发送人、发送后 5 分钟内；重复撤回幂等成功）。
     *
     * @param id 消息 id
     * @return 成功响应
     */
    @PostMapping("/withdraw/{id}")
    public Result<Void> withdraw(@PathVariable Long id) {
        messageService.withdraw(StpUtil.getLoginIdAsLong(), id);
        return ok();
    }
}
