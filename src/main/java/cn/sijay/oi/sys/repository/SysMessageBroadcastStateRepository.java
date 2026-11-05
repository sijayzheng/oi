package cn.sijay.oi.sys.repository;

import cn.sijay.oi.sys.entity.SysMessageBroadcastState;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * 站内信广播个性化状态仓库（惰性创建：用户首次标记某条广播时才落行）。
 *
 * <p>未落行的广播按默认态（未读/未归档/未删除）参与收件箱查询；
 * 状态变更由服务层"查询-修改-保存"单行 upsert，唯一键
 * {@code uk_broadcast_state(message_id, user_id)} 兜底并发重复。
 *
 * @author sijay
 */
public interface SysMessageBroadcastStateRepository extends JpaRepository<SysMessageBroadcastState, Long> {

    /**
     * 定位用户对某条广播的状态行（不存在表示默认态）。
     */
    Optional<SysMessageBroadcastState> findByMessageIdAndUserId(Long messageId, Long userId);

    /**
     * 用户已读（或已删除）的广播消息 id 集合（未读数计算用）。
     */
    @Query("""
            select s.messageId from SysMessageBroadcastState s
            where s.userId = :userId and (s.readStatus = 1 or s.deleted <> 0)
            """)
    List<Long> findHandledMessageIds(@Param("userId") Long userId);
}
