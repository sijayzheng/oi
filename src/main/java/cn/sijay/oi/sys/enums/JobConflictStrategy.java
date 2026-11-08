package cn.sijay.oi.sys.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 任务并发冲突处理策略枚举，对应 {@code sys_job.conflict_strategy} 字段取值集，
 * 数据库以 VARCHAR 存储枚举名。
 *
 * <p>冲突定义：同一任务的上一次执行尚未结束时，下一次调度触发到达。
 * 两种处理模式：
 * <ul>
 *   <li>{@link #DISCARD}：直接丢弃本次触发（默认策略，语义与
 *       Spring 单线程 {@code @Scheduled} 的"错过即跳过"对齐）；</li>
 *   <li>{@link #QUEUE}：排队等待——本次触发入队，待上一次执行完成后
 *       立即开始（同一时刻队列至多 1 个等待项，再冲突时丢弃最旧项）。</li>
 * </ul>
 * 无论哪种策略，同一任务实例严格串行（分布式锁保证集群内也不并发）。
 *
 * @author sijay
 */
@AllArgsConstructor
@Getter
public enum JobConflictStrategy {

    /**
     * 丢弃后续执行（默认）。
     */
    DISCARD("丢弃"),

    /**
     * 排队等待前序任务执行完成。
     */
    QUEUE("排队等待"),
    ;

    private final String desc;
}
