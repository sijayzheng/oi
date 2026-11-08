package cn.sijay.oi.sys.model.job;

import cn.sijay.oi.sys.enums.JobConflictStrategy;
import cn.sijay.oi.sys.enums.JobRetryPolicy;
import cn.sijay.oi.sys.enums.JobTriggerType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 更新任务配置入参模型（jobKey 不可修改，与 {@link SysJobCreateCmd} 其余字段一致）。
 *
 * <p>更新语义：全部配置即时生效（调度器按新配置重注册，免重启）。
 *
 * @author sijay
 */
@Data
public class SysJobUpdateCmd {

    /**
     * 任务名称。
     */
    @NotBlank(message = "任务名称不能为空")
    @Size(max = 100, message = "任务名称最长 100 字符")
    private String name;

    /**
     * 触发类型；CRON / FIXED_RATE / FIXED_DELAY。
     */
    @NotNull(message = "触发类型不能为空")
    private JobTriggerType triggerType;

    /**
     * 触发值。
     */
    @NotBlank(message = "触发值不能为空")
    @Size(max = 100, message = "触发值最长 100 字符")
    private String triggerValue;

    /**
     * 时区；空为应用默认时区。
     */
    @Size(max = 50, message = "时区最长 50 字符")
    private String timeZone;

    /**
     * 超时时间（毫秒）；0 表示不限时。
     */
    @NotNull(message = "超时时间不能为 null")
    @Min(value = 0, message = "超时时间不能为负")
    private Long timeoutMillis;

    /**
     * 重试策略。
     */
    @NotNull(message = "重试策略不能为空")
    private JobRetryPolicy retryPolicy;

    /**
     * 最大重试次数；0 表示不重试。
     */
    @NotNull(message = "最大重试次数不能为 null")
    @Min(value = 0, message = "最大重试次数不能为负")
    private Integer maxRetryCount;

    /**
     * 初始重试间隔（毫秒）。
     */
    @NotNull(message = "初始重试间隔不能为 null")
    @Min(value = 0, message = "初始重试间隔不能为负")
    private Long retryInitialInterval;

    /**
     * 最大重试间隔（毫秒）；须 >= 初始间隔。
     */
    @NotNull(message = "最大重试间隔不能为 null")
    @Min(value = 0, message = "最大重试间隔不能为负")
    private Long retryMaxInterval;

    /**
     * 可重试异常白名单。
     */
    @Size(max = 1000, message = "可重试异常白名单最长 1000 字符")
    private String retryInclude;

    /**
     * 不可重试异常黑名单。
     */
    @Size(max = 1000, message = "不可重试异常黑名单最长 1000 字符")
    private String retryExclude;

    /**
     * 并发冲突策略。
     */
    @NotNull(message = "并发冲突策略不能为空")
    private JobConflictStrategy conflictStrategy;

    /**
     * 连续失败告警阈值；0 表示不启用。
     */
    @NotNull(message = "连续失败告警阈值不能为 null")
    @Min(value = 0, message = "连续失败告警阈值不能为负")
    private Integer alertFailureThreshold;

    /**
     * 备注。
     */
    @Size(max = 500, message = "备注最长 500 字符")
    private String remark;
}
