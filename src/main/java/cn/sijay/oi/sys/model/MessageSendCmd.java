package cn.sijay.oi.sys.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 发送站内信入参模型。
 *
 * <p>校验规则（失败信息直接面向调用方展示）：
 * <ul>
 *   <li>msgType 必填，仅允许 NOTICE（系统通知）/ LETTER（私信）/ BROADCAST（公告广播）；</li>
 *   <li>点对点消息（NOTICE/LETTER）必须携带 receiverUsername；广播无需接收人；</li>
 *   <li>标题/内容必填并限长。</li>
 * </ul>
 *
 * <p>权限约定（业务层校验）：LETTER 任意登录用户可发；NOTICE / BROADCAST
 * 仅系统管理员可发（见 SysMessageService.send）。
 *
 * @author sijay
 */
@Data
public class MessageSendCmd {

    /**
     * 消息类型；NOTICE=系统通知 LETTER=私信 BROADCAST=公告广播。
     */
    @NotNull(message = "消息类型不能为空")
    @Pattern(regexp = "NOTICE|LETTER|BROADCAST", message = "消息类型仅允许 NOTICE / LETTER / BROADCAST")
    private String msgType;

    /**
     * 接收人用户名；点对点消息（NOTICE/LETTER）必填，广播忽略。
     */
    @Size(max = 50, message = "接收人用户名最长 50 字符")
    private String receiverUsername;

    /**
     * 消息标题。
     */
    @NotBlank(message = "消息标题不能为空")
    @Size(max = 100, message = "消息标题最长 100 字符")
    private String title;

    /**
     * 消息内容。
     */
    @NotBlank(message = "消息内容不能为空")
    @Size(max = 4000, message = "消息内容最长 4000 字符")
    private String content;

    /**
     * 附件文件 id 列表（统一文件服务上传后回传的 id）；最多 10 个。
     */
    @Size(max = 10, message = "附件最多 10 个")
    private List<String> attachmentIds;
}
