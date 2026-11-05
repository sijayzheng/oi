package cn.sijay.oi.sys.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 站内信消息实体，映射表 {@code sys_message}（DDL 见 classpath:sql/schema-h2.sql）。
 *
 * <p>三类消息共用本表（见 {@link MessageType}）：
 * <ul>
 *   <li>NOTICE（系统通知）/ LETTER（私信）：点对点，receiver_id 必填，
 *       发送时同步生成 sys_message_receipt 收件箱行；</li>
 *   <li>BROADCAST（公告广播）：全员广播，receiver_id 为空，不预插回执，
 *       用户侧已读/归档/删除状态由 sys_message_broadcast_state 惰性记录。</li>
 * </ul>
 *
 * <p>关键约定（与 DDL 注释对齐）：
 * <ul>
 *   <li><b>软删除</b>：{@code deleted} 默认 0，删除时写入本行主键 id；
 *       {@link SQLRestriction} 使本实体的所有查询自动携带 {@code deleted = 0} 条件；</li>
 *   <li><b>撤回</b>：status 置为 WITHDRAWN，收件箱与发件箱查询均排除已撤回消息。</li>
 * </ul>
 *
 * @author sijay
 */
@Data
@NoArgsConstructor
@Entity
@Table(name = "sys_message")
@SQLRestriction("deleted = 0")
public class SysMessage {

    /**
     * 主键；IDENTITY 自增，禁止手工指定。
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 消息类型；NOTICE=系统通知 LETTER=私信 BROADCAST=公告广播。
     */
    @Column(name = "msg_type", nullable = false, length = 20)
    private String msgType;

    /**
     * 发送人 id；0=系统。
     */
    @Column(name = "sender_id", nullable = false)
    private Long senderId;

    /**
     * 接收人 id；点对点消息必填，广播为空。
     */
    @Column(name = "receiver_id")
    private Long receiverId;

    /**
     * 消息标题。
     */
    @Column(name = "title", nullable = false, length = 100)
    private String title;

    /**
     * 消息内容。
     */
    @Column(name = "content", nullable = false, length = 4000)
    private String content;

    /**
     * 附件文件 id 列表（统一文件服务），逗号分隔。
     */
    @Column(name = "attachment_ids", length = 500)
    private String attachmentIds;

    /**
     * 消息状态；SENT=已发送 WITHDRAWN=已撤回。
     */
    @Column(name = "status", nullable = false, length = 20)
    private String status = MessageStatus.SENT.name();

    /**
     * 发送时间；撤回时限（5 分钟）以此为基准。
     */
    @Column(name = "send_time", nullable = false)
    private LocalDateTime sendTime;

    /**
     * 创建人；审计字段。
     */
    @Column(name = "create_by")
    private Long createBy;

    /**
     * 创建时间；审计字段。
     */
    @CreationTimestamp
    @Column(name = "create_time", nullable = false)
    private LocalDateTime createTime;

    /**
     * 更新时间；自动刷新。
     */
    @UpdateTimestamp
    @Column(name = "update_time")
    private LocalDateTime updateTime;

    /**
     * 软删标记。
     */
    @Column(name = "deleted")
    private Long deleted = 0L;

    /**
     * 消息类型枚举。
     */
    public enum MessageType {
        /**
         * 系统通知（管理员发给指定用户）。
         */
        NOTICE,
        /**
         * 用户私信（点对点）。
         */
        LETTER,
        /**
         * 公告广播（管理员全员广播）。
         */
        BROADCAST
    }

    /**
     * 消息状态枚举。
     */
    public enum MessageStatus {
        /**
         * 已发送。
         */
        SENT,
        /**
         * 已撤回。
         */
        WITHDRAWN
    }
}
