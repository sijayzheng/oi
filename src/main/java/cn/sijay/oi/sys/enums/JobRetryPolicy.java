package cn.sijay.oi.sys.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 任务重试策略枚举，对应 {@code sys_job.retry_policy} 字段取值集，
 * 数据库以 VARCHAR 存储枚举名。
 *
 * <p>两种标准策略：
 * <ul>
 *   <li>{@link #FIXED_INTERVAL}：固定间隔重试——每次重试等待
 *       {@code retry_initial_interval} 毫秒；</li>
 *   <li>{@link #EXPONENTIAL_BACKOFF}：指数退避重试——第 n 次重试等待
 *       {@code min(initial * 2^n + 随机抖动, maxInterval)} 毫秒；
 *       抖动因子避免分布式场景下的惊群效应（重试时刻打散）。</li>
 * </ul>
 * 两策略均以 {@code retry_max_interval} 为单次等待上限封顶。
 *
 * @author sijay
 */
@AllArgsConstructor
@Getter
public enum JobRetryPolicy {

    /**
     * 固定间隔重试；每次等待 retry_initial_interval 毫秒。
     */
    FIXED_INTERVAL("固定间隔"),

    /**
     * 指数退避重试（含随机抖动因子）；等待时间按 2 的幂递增并封顶。
     */
    EXPONENTIAL_BACKOFF("指数退避"),
    ;

    private final String desc;
}
