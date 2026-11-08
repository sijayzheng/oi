package cn.sijay.oi.sys.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.Duration;

/**
 * 任务执行状态枚举，对应 {@code sys_job_log.execute_status} 字段取值集，
 * 数据库以 VARCHAR 存储枚举名。
 *
 * <p>状态机约定（一次执行的终态集合）：
 * <ul>
 *   <li>{@link #PENDING}：已触发尚未开始执行（仅在队列等待场景短暂存在）；</li>
 *   <li>{@link #RUNNING}：执行中（执行开始后写入，结束时覆写为终态）；</li>
 *   <li>{@link #SUCCESS}：执行成功（含重试后成功）；</li>
 *   <li>{@link #FAILED}：执行失败（不可重试异常，或重试耗尽仍失败）；</li>
 *   <li>{@link #TIMEOUT}：执行超时（超过任务配置的超时时间被强制中断）；</li>
 *   <li>{@link #SKIPPED}：并发冲突丢弃（上一实例仍在执行，本次按丢弃策略跳过）；</li>
 *   <li>{@link #DEAD}：死信（重试耗尽后转入死信队列）。</li>
 * </ul>
 *
 * @author sijay
 */
@AllArgsConstructor
@Getter
public enum JobExecuteStatus {

    /**
     * 待执行；已触发尚未开始（队列等待模式）。
     */
    PENDING("待执行"),

    /**
     * 执行中。
     */
    RUNNING("执行中"),

    /**
     * 成功；含重试后成功。
     */
    SUCCESS("成功"),

    /**
     * 失败；不可重试异常或重试耗尽。
     */
    FAILED("失败"),

    /**
     * 超时；超过超时时间被强制中断。
     */
    TIMEOUT("超时"),

    /**
     * 死信；重试耗尽后转入死信队列。
     */
    DEAD("死信"),

    /**
     * 跳过；并发冲突按丢弃策略跳过本次执行。
     */
    SKIPPED("跳过"),
    ;

    private final String desc;

    /**
     * 判断是否为终态（成功/失败/超时/死信/跳过）。
     *
     * @return 终态返回 true；待执行/执行中返回 false
     */
    public boolean isFinal() {
        return this != PENDING && this != RUNNING;
    }
}
