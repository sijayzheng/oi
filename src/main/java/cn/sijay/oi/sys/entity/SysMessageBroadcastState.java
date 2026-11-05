package cn.sijay.oi.sys.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 站内信广播个性化状态实体，映射表 {@code sys_message_broadcast_state}。
 *
 * <p>公告广播的用户侧已读/归档/删除状态。<b>惰性创建</b>：广播发送时不为本表
 * 生成任何行，仅当用户首次标记（已读/归档/删除）某条广播时才 upsert 单行，
 * 保证万级用户的广播不产生全量行膨胀（N 用户 × M 广播仅在实际操作后才落行）。
 * 未落行的广播按"未读、未归档、未删除"默认态参与收件箱查询。
 *
 * <p><b>删除语义特殊</b>：本表"用户删除该广播"（{@code deleted} 置为主键 id）
 * 是业务状态而非清理动作——收件箱查询需要区分"用户已删除（不可见）"与
 * "从未操作（默认态）"，因此<b>不使用</b> {@code @SQLRestriction} 全局过滤，
 * deleted 由服务层手动管理。
 *
 * @author sijay
 */
@Data
@NoArgsConstructor
@Entity
@Table(name = "sys_message_broadcast_state",
        uniqueConstraints = @UniqueConstraint(name = "uk_broadcast_state", columnNames = {"message_id", "user_id"}))
public class SysMessageBroadcastState {

    /**
     * 主键；IDENTITY 自增，禁止手工指定。
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 消息 id（sys_message.id）。
     */
    @Column(name = "message_id", nullable = false)
    private Long messageId;

    /**
     * 用户 id。
     */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /**
     * 已读状态；0=未读 1=已读。
     */
    @Column(name = "read_status", nullable = false)
    private Integer readStatus = 0;

    /**
     * 首次已读时间。
     */
    @Column(name = "read_time")
    private LocalDateTime readTime;

    /**
     * 归档标记；1=已归档。
     */
    @Column(name = "archived", nullable = false)
    private Integer archived = 0;

    /**
     * 软删标记。
     */
    @Column(name = "deleted")
    private Long deleted = 0L;
}
