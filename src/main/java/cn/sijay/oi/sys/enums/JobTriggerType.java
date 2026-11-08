package cn.sijay.oi.sys.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 任务触发类型枚举，对应 {@code sys_job.trigger_type} 字段取值集，
 * 数据库以 VARCHAR 存储枚举名。
 *
 * <p>与标准 Spring {@code @Scheduled} 注解的调度语义一一对应：
 * <ul>
 *   <li>{@link #CRON}：CRON 表达式（Spring 6 位格式：秒 分 时 日 月 周，
 *       与 {@code @Scheduled(cron=...)} 兼容，支持时区）；</li>
 *   <li>{@link #FIXED_RATE}：固定频率（上次<b>开始</b>时间 + 周期，
 *       与 {@code @Scheduled(fixedRate=...)} 语义一致）；</li>
 *   <li>{@link #FIXED_DELAY}：固定延迟（上次<b>结束</b>时间 + 周期，
 *       与 {@code @Scheduled(fixedDelay=...)} 语义一致）。</li>
 * </ul>
 *
 * @author sijay
 */
@AllArgsConstructor
@Getter
public enum JobTriggerType {

    /**
     * CRON 表达式触发；trigger_value 存 6 位 CRON（如 0 * * * * *）。
     */
    CRON("CRON 表达式"),

    /**
     * 固定频率触发；trigger_value 存毫秒周期（如 60000 = 每 60 秒）。
     */
    FIXED_RATE("固定频率"),

    /**
     * 固定延迟触发；trigger_value 存毫秒周期（上次执行结束 + 周期）。
     */
    FIXED_DELAY("固定延迟"),
    ;

    private final String desc;
}
