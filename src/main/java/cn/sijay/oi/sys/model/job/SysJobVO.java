package cn.sijay.oi.sys.model.job;

import cn.sijay.oi.sys.entity.SysJob;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 任务定义出参模型（视图对象），字段为 {@code sys_job} 的对外全量子集。
 *
 * <p>输出约定：id 为 Long（全局 Jackson 配置序列化为字符串）；
 * 时间字段为 LocalDateTime（序列化为 yyyy-MM-dd HH:mm:ss）；
 * 枚举同时输出枚举名与中文描述（triggerTypeDesc/retryPolicyDesc/conflictStrategyDesc）。
 *
 * @author sijay
 */
@Data
public class SysJobVO {

    /**
     * 任务 id。
     */
    private Long id;

    /**
     * 任务业务键。
     */
    private String jobKey;

    /**
     * 任务名称。
     */
    private String name;

    /**
     * 触发类型；CRON / FIXED_RATE / FIXED_DELAY。
     */
    private String triggerType;

    /**
     * 触发类型中文描述。
     */
    private String triggerTypeDesc;

    /**
     * 触发值；CRON 表达式或毫秒周期。
     */
    private String triggerValue;

    /**
     * 时区；空为应用默认时区。
     */
    private String timeZone;

    /**
     * 超时时间（毫秒）；0 表示不限时。
     */
    private Long timeoutMillis;

    /**
     * 重试策略；FIXED_INTERVAL / EXPONENTIAL_BACKOFF。
     */
    private String retryPolicy;

    /**
     * 重试策略中文描述。
     */
    private String retryPolicyDesc;

    /**
     * 最大重试次数。
     */
    private Integer maxRetryCount;

    /**
     * 初始重试间隔（毫秒）。
     */
    private Long retryInitialInterval;

    /**
     * 最大重试间隔（毫秒）。
     */
    private Long retryMaxInterval;

    /**
     * 可重试异常白名单。
     */
    private String retryInclude;

    /**
     * 不可重试异常黑名单。
     */
    private String retryExclude;

    /**
     * 并发冲突策略；DISCARD / QUEUE。
     */
    private String conflictStrategy;

    /**
     * 并发冲突策略中文描述。
     */
    private String conflictStrategyDesc;

    /**
     * 连续失败告警阈值；0 表示不启用。
     */
    private Integer alertFailureThreshold;

    /**
     * 备注。
     */
    private String remark;

    /**
     * 启用状态；true=启用中 / false=已暂停。
     */
    private Boolean enabled;

    /**
     * 创建时间。
     */
    private LocalDateTime createTime;

    /**
     * 更新时间。
     */
    private LocalDateTime updateTime;

    /**
     * 由实体构建视图对象（实体 → VO 的唯一映射入口）。
     *
     * @param entity 任务实体
     * @return 视图对象
     */
    public static SysJobVO from(SysJob entity) {
        SysJobVO vo = new SysJobVO();
        vo.setId(entity.getId());
        vo.setJobKey(entity.getJobKey());
        vo.setName(entity.getName());
        vo.setTriggerType(entity.getTriggerType() == null ? null : entity.getTriggerType().name());
        vo.setTriggerTypeDesc(entity.getTriggerType() == null ? null : entity.getTriggerType().getDesc());
        vo.setTriggerValue(entity.getTriggerValue());
        vo.setTimeZone(entity.getTimeZone());
        vo.setTimeoutMillis(entity.getTimeoutMillis());
        vo.setRetryPolicy(entity.getRetryPolicy() == null ? null : entity.getRetryPolicy().name());
        vo.setRetryPolicyDesc(entity.getRetryPolicy() == null ? null : entity.getRetryPolicy().getDesc());
        vo.setMaxRetryCount(entity.getMaxRetryCount());
        vo.setRetryInitialInterval(entity.getRetryInitialInterval());
        vo.setRetryMaxInterval(entity.getRetryMaxInterval());
        vo.setRetryInclude(entity.getRetryInclude());
        vo.setRetryExclude(entity.getRetryExclude());
        vo.setConflictStrategy(entity.getConflictStrategy() == null ? null : entity.getConflictStrategy().name());
        vo.setConflictStrategyDesc(entity.getConflictStrategy() == null
                ? null : entity.getConflictStrategy().getDesc());
        vo.setAlertFailureThreshold(entity.getAlertFailureThreshold());
        vo.setRemark(entity.getRemark());
        vo.setEnabled(entity.getEnabled());
        vo.setCreateTime(entity.getCreateTime());
        vo.setUpdateTime(entity.getUpdateTime());
        return vo;
    }
}
