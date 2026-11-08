package cn.sijay.oi.sys.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 任务死信实体，映射表 {@code sys_job_dead_letter}（DDL 见 classpath:sql/schema-h2.sql）。
 *
 * <p>任务经过最大重试次数后仍执行失败即产生一行死信，完整保留任务上下文
 * （触发配置/重试配置快照）、失败原因与全量执行历史的关联入口，供人工排查与重放。
 *
 * <p>关键约定：
 * <ul>
 *   <li><b>上下文快照</b>：job_config 以 JSON 快照当时的任务配置，
 *       任务后续被修改/删除不影响死信排查；</li>
 *   <li><b>重放幂等</b>：status 标记处理状态，重放成功置 RESOLVED，
 *       失败可再次重放（每次重放自动重置重试计数走完整执行流程）；</li>
 *   <li><b>物理删除</b>：死信处理完毕后允许物理删除清理，不参与软删约定。</li>
 * </ul>
 *
 * @author sijay
 */
@Data
@NoArgsConstructor
@Entity
@Table(name = "sys_job_dead_letter")
public class SysJobDeadLetter {

    /**
     * 主键；IDENTITY 自增。
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 任务 id（sys_job.id）；任务可能已被删除，仅作定位参考。
     */
    @Column(name = "job_id", nullable = false)
    private Long jobId;

    /**
     * 任务业务键快照。
     */
    @Column(name = "job_key", nullable = false, length = 100)
    private String jobKey;

    /**
     * 任务名称快照。
     */
    @Column(name = "job_name", nullable = false, length = 100)
    private String jobName;

    /**
     * 死信来源执行记录 id（sys_job_log.id）；全量执行历史经该记录
     * 关联 sys_job_retry_detail 追溯。
     */
    @Column(name = "source_log_id", nullable = false)
    private Long sourceLogId;

    /**
     * 任务配置快照（JSON）；死信产生时刻的触发/重试/超时配置全量快照。
     */
    @Column(name = "job_config", length = 4000)
    private String jobConfig;

    /**
     * 最终异常类型名。
     */
    @Column(name = "error_type", length = 255)
    private String errorType;

    /**
     * 最终异常信息（message + 堆栈）；入库前截断至 4000 字符。
     */
    @Column(name = "error_stack", length = 4000)
    private String errorStack;

    /**
     * 累计重试次数（死信产生时的总重试轮数）。
     */
    @Column(name = "total_retry_count", nullable = false)
    private Integer totalRetryCount = 0;

    /**
     * 处理状态；PENDING=待处理 / RESOLVED=已重放成功 / REPLAY_FAILED=重放失败。
     */
    @Column(name = "status", nullable = false, length = 20)
    private String status = Status.PENDING.name();

    /**
     * 最近重放时间。
     */
    @Column(name = "replay_time")
    private LocalDateTime replayTime;

    /**
     * 死信产生时间。
     */
    @CreationTimestamp
    @Column(name = "create_time", nullable = false)
    private LocalDateTime createTime;

    /**
     * 更新时间；重放等状态变更时刷新。
     */
    @UpdateTimestamp
    @Column(name = "update_time")
    private LocalDateTime updateTime;

    /**
     * 死信处理状态枚举。
     */
    public enum Status {
        /**
         * 待处理（新死信）。
         */
        PENDING,
        /**
         * 已重放成功。
         */
        RESOLVED,
        /**
         * 重放后仍失败（可再次重放）。
         */
        REPLAY_FAILED
    }
}
