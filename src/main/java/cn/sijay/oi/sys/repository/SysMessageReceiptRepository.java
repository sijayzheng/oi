package cn.sijay.oi.sys.repository;

import cn.sijay.oi.sys.entity.SysMessageReceipt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 站内信点对点回执仓库（收件箱行）。
 *
 * <p>用户侧状态变更（已读/归档/删除）均为带 receiver_id 条件的批量更新，
 * 从 SQL 层面杜绝跨用户越权修改；收件箱统一分页视图由服务层原生 SQL 实现。
 *
 * @author sijay
 */
public interface SysMessageReceiptRepository extends JpaRepository<SysMessageReceipt, Long> {

    /**
     * 按消息 + 接收人定位回执（权限校验与详情装配用）。
     */
    Optional<SysMessageReceipt> findByMessageIdAndReceiverId(Long messageId, Long receiverId);

    /**
     * 未读回执列表（未读角标与"全部已读"前的广播 upsert 场景）。
     */
    List<SysMessageReceipt> findByReceiverIdAndReadStatus(Long receiverId, Integer readStatus);

    /**
     * 批量标记已读（仅本人回执，幂等）。
     */
    @Modifying
    @Query("""
            update SysMessageReceipt r set r.readStatus = 1, r.readTime = CURRENT_TIMESTAMP
            where r.id in :ids and r.receiverId = :receiverId and r.readStatus = 0
            """)
    int markRead(@Param("ids") Collection<Long> ids, @Param("receiverId") Long receiverId);

    /**
     * 全部标记已读（本人所有未读回执）。
     */
    @Modifying
    @Query("""
            update SysMessageReceipt r set r.readStatus = 1, r.readTime = CURRENT_TIMESTAMP
            where r.receiverId = :receiverId and r.readStatus = 0
            """)
    int markAllRead(@Param("receiverId") Long receiverId);

    /**
     * 批量归档/取消归档（仅本人回执）。
     */
    @Modifying
    @Query("""
            update SysMessageReceipt r set r.archived = :archived
            where r.id in :ids and r.receiverId = :receiverId
            """)
    int archive(@Param("ids") Collection<Long> ids, @Param("receiverId") Long receiverId, @Param("archived") Integer archived);

    /**
     * 批量删除（软删，仅本人回执；deleted 置为本行主键 id）。
     */
    @Modifying
    @Query("update SysMessageReceipt r set r.deleted = r.id where r.id in :ids and r.receiverId = :receiverId")
    int softDelete(@Param("ids") Collection<Long> ids, @Param("receiverId") Long receiverId);

    /**
     * 消息撤回时作废该消息的全部回执（所有接收人收件箱同步移除）。
     */
    @Modifying
    @Query("update SysMessageReceipt r set r.deleted = r.id where r.messageId in :messageIds")
    int softDeleteByMessageIds(@Param("messageIds") Collection<Long> messageIds);
}
