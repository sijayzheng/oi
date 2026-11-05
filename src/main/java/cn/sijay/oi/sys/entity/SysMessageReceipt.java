package cn.sijay.oi.sys.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;

/**
 * 站内信点对点回执实体，映射表 {@code sys_message_receipt}。
 *
 * <p>系统通知与私信发送时按接收人生成（收件箱行），承载用户侧
 * 已读/归档/删除状态；广播消息不生成本表行（见 SysMessageBroadcastState）。
 *
 * <p>软删除约定与 {@link SysMessage} 一致：用户删除消息、或发送人撤回消息时，
 * {@code deleted} 置为本行主键 id，{@link SQLRestriction} 全局过滤。
 *
 * @author sijay
 */
@Data
@NoArgsConstructor
@Entity
@Table(name = "sys_message_receipt")
@SQLRestriction("deleted = 0")
public class SysMessageReceipt {

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
     * 接收人 id。
     */
    @Column(name = "receiver_id", nullable = false)
    private Long receiverId;

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
