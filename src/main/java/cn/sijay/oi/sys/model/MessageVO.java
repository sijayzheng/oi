package cn.sijay.oi.sys.model;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 站内信视图对象（收件箱/发件箱/详情通用结构）。
 *
 * <p>由服务层从消息主表 + 回执/广播状态装配；Long 字段统一序列化为字符串
 * （与全局 Jackson 约定一致）。
 *
 * @author sijay
 */
@Data
public class MessageVO {

    /**
     * 消息 id。
     */
    private Long id;

    /**
     * 消息类型；NOTICE / LETTER / BROADCAST。
     */
    private String msgType;

    /**
     * 发送人 id；0=系统。
     */
    private Long senderId;

    /**
     * 接收人 id；广播为 null。
     */
    private Long receiverId;

    /**
     * 消息标题。
     */
    private String title;

    /**
     * 消息内容。
     */
    private String content;

    /**
     * 附件文件 id 列表。
     */
    private List<String> attachmentIds;

    /**
     * 消息状态；SENT / WITHDRAWN。
     */
    private String status;

    /**
     * 发送时间。
     */
    private LocalDateTime sendTime;

    /**
     * 已读状态（收件箱视角）；0=未读 1=已读；发件箱视角为 null。
     */
    private Integer readStatus;

    /**
     * 归档标记（收件箱视角）；1=已归档；发件箱视角为 null。
     */
    private Integer archived;

    /**
     * 当前登录人是否可撤回（发送人本人 + 已发送 + 发送后 5 分钟内）；列表项直接据此渲染撤回按钮。
     */
    private Boolean withdrawable;
}
