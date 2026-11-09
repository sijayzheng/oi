package cn.sijay.oi.sys.model.job;

import cn.sijay.oi.sys.enums.JobConflictStrategy;
import cn.sijay.oi.sys.enums.JobRetryPolicy;
import cn.sijay.oi.sys.enums.JobTriggerType;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 创建任务入参模型，字段与表 {@code sys_job} DDL 定义严格对应。
 *
 * <p>校验规则（失败信息直接面向调用方展示）：
 * <ul>
 *   <li>jobKey：必填，2~100 位点分命名规范，创建后禁止修改；</li>
 *   <li>name：必填，最长 100 字符；</li>
 *   <li>triggerType/triggerValue：必填；CRON 合法性与周期下限由 Service 业务校验；</li>
 *   <li>重试参数：非负，max >= initial 由 Service 业务校验；</li>
 *   <li>retryInclude/retryExclude：类型全限定名模式，逗号分隔，最长 1000 字符。</li>
 * </ul>
 *
 * @author sijay
 */
@Data
public class SysJobCreateCmd {

    /**
     * 任务业务键；点分小写风格（如 report.daily），全局唯一，创建后禁止修改。
     */
    @NotBlank(message = "任务业务键不能为空")
    @Size(min = 2, max = 100, message = "任务业务键长度须在 2~100 位之间")
    @Pattern(regexp = "^[a-zA-Z0-9_-]+(\\.[a-zA-Z0-9_-]+)*$",
            message = "任务业务键须为点分命名格式（如 report.daily）")
    private String jobKey;

    /**
     * 任务名称；管理界面展示用。
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
     * 触发值；CRON 存 6 位表达式，周期类型存毫秒数（>=1000）。
     */
    @NotBlank(message = "触发值不能为空")
    @Size(max = 100, message = "触发值最长 100 字符")
    private String triggerValue;

    /**
     * 超时时间（毫秒）；0 表示不限时。
     */
    @NotNull(message = "超时时间不能为 null")
    @Min(value = 0, message = "超时时间不能为负")
    private Long timeoutMillis = 0L;

    /**
     * 重试策略；FIXED_INTERVAL / EXPONENTIAL_BACKOFF。
     */
    @NotNull(message = "重试策略不能为空")
    private JobRetryPolicy retryPolicy = JobRetryPolicy.FIXED_INTERVAL;

    /**
     * 最大重试次数；0 表示不重试。
     */
    @NotNull(message = "最大重试次数不能为 null")
    @Min(value = 0, message = "最大重试次数不能为负")
    private Integer maxRetryCount = 0;

    /**
     * 初始重试间隔（毫秒）。
     */
    @NotNull(message = "初始重试间隔不能为 null")
    @Min(value = 0, message = "初始重试间隔不能为负")
    private Long retryInitialInterval = 60_000L;

    /**
     * 最大重试间隔（毫秒）；指数退避封顶值，须 >= 初始间隔。
     */
    @NotNull(message = "最大重试间隔不能为 null")
    @Min(value = 0, message = "最大重试间隔不能为负")
    private Long retryMaxInterval = 600_000L;

    /**
     * 可重试异常白名单；类型全限定名模式（逗号分隔，支持 * 尾通配）。
     */
    @Size(max = 1000, message = "可重试异常白名单最长 1000 字符")
    private String retryInclude;

    /**
     * 不可重试异常黑名单；优先级高于白名单。
     */
    @Size(max = 1000, message = "不可重试异常黑名单最长 1000 字符")
    private String retryExclude;

    /**
     * 并发冲突策略；DISCARD 丢弃（默认）/ QUEUE 排队等待。
     */
    @NotNull(message = "并发冲突策略不能为空")
    private JobConflictStrategy conflictStrategy = JobConflictStrategy.DISCARD;

    /**
     * 连续失败告警阈值；0 表示不启用。
     */
    @NotNull(message = "连续失败告警阈值不能为 null")
    @Min(value = 0, message = "连续失败告警阈值不能为负")
    private Integer alertFailureThreshold = 0;

    /**
     * 备注。
     */
    @Size(max = 500, message = "备注最长 500 字符")
    private String remark;
}
