package cn.sijay.oi.sys.model.notice;

import jakarta.validation.constraints.*;
import lombok.Data;
import java.time.LocalDateTime;

/**
 * 更新通知公告入参模型，字段与表 {@code sys_notice} DDL 定义严格对应。
 *
 * <p>校验规则（失败信息直接面向调用方展示）：
 * <ul>
 *   <li>必填列生成 @NotBlank/@NotNull；</li>
 *   <li>字符串长度生成 @Size、格式约束生成 @Pattern；数值范围生成 @Min。</li>
 * </ul>
 *
 * @author sijay
 */
@Data
public class SysNoticeUpdateCmd {

    /**
     * 通知标题；必填。
     */
    @NotBlank(message = "通知标题不能为空")
    @Size(max = 100, message = "通知标题最长 100 字符")
    private String title;

    /**
     * 通知类型；0=通知 1=公告；必填（全量更新语义，NULL 入库将触发数据库约束异常）。
     */
    @NotBlank(message = "通知类型不能为空")
    @Pattern(regexp = "^[01]$", message = "通知类型仅允许 0（通知）或 1（公告）")
    private String noticeType;

    /**
     * 通知内容；必填。
     */
    @NotBlank(message = "通知内容不能为空")
    @Size(max = 2000, message = "通知内容最长 2000 字符")
    private String content;

    /**
     * 发布时间；选填。
     */
    private LocalDateTime publishTime;

    /**
     * 是否置顶；1=置顶；选填。
     */
    @Min(value = 0, message = "是否置顶；1=置顶不能为负数")
    private Integer topMarked;

}
