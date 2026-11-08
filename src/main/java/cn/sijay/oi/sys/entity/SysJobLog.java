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

import java.time.LocalDateTime;

/**
 * 任务执行记录实体，映射表 {@code sys_job_log}（DDL 见 classpath:sql/schema-h2.sql）。
 *
 * <p>一次调度触发对应一行记录（重试在同一行内累计计数，attempt_no 区分轮次），
 * 完整承载规范要求的字段：任务唯一 ID、任务名称、执行状态、异常栈、累计重试次数、
 * 开始/结束时间与耗时。异常详情明细（每次重试的触发时间/堆栈/耗时）由
 * {@link SysJobRetryDetail} 承载，与执行记录一对多。
 *
 * <p>关键约定：
 * <ul>
 *   <li><b>append + 有限更新</b>：执行开始时插入 RUNNING 行，终态时更新终态字段；
 *       历史行不再修改（审计与统计口径稳定）；</li>
 *   <li><b>异常栈截断</b>：入库前截断至 4000 字符，防大栈撑爆列宽；</li>
 *   <li><b>耗时冗余</b>：duration_millis = 结束 - 开始（毫秒），冗余存储便于排序统计；</li>
 *   <li><b>不软删</b>：执行历史为审计数据，仅按时间归档清理，无 deleted 字段。</li>
 * </ul>
 *
 * @author sijay
 */
@Data
@NoArgsConstructor
@Entity
@Table(name = "sys_job_log")
public class SysJobLog {

    /**
     * 主键；IDENTITY 自增。
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 任务 id（sys_job.id）；死信重放后新执行的记录仍指向原任务。
     */
    @Column(name = "job_id", nullable = false)
    private Long jobId;

    /**
     * 任务业务键快照；冗余存储，任务删除后执行历史仍可读。
     */
    @Column(name = "job_key", nullable = false, length = 100)
    private String jobKey;

    /**
     * 任务名称快照；冗余存储，不随任务改名变化。
     */
    @Column(name = "job_name", nullable = false, length = 100)
    private String jobName;

    /**
     * 触发来源；SCHEDULE=调度触发 / MANUAL=手动触发 / REPLAY=死信重放。
     */
    @Column(name = "trigger_source", nullable = false, length = 20)
    private String triggerSource = TriggerSource.SCHEDULE.name();

    /**
     * 执行状态；PENDING/RUNNING/SUCCESS/FAILED/TIMEOUT/DEAD/SKIPPED。
     */
    @Column(name = "execute_status", nullable = false, length = 20)
    private String executeStatus;

    /**
     * 当前执行轮次；0=首次执行，n=第 n 次重试。
     */
    @Column(name = "attempt_no", nullable = false)
    private Integer attemptNo = 0;

    /**
     * 累计重试次数；终态时的总重试次数（成功前经历的重试轮数）。
     */
    @Column(name = "retry_count", nullable = false)
    private Integer retryCount = 0;

    /**
     * 异常类型名；如 java.lang.IllegalStateException。
     */
    @Column(name = "error_type", length = 255)
    private String errorType;

    /**
     * 异常信息（message + 堆栈摘要）；入库前截断至 4000 字符。
     */
    @Column(name = "error_stack", length = 4000)
    private String errorStack;

    /**
     * 本次执行开始时间。
     */
    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    /**
     * 本次执行结束时间；RUNNING/PENDING 状态为空。
     */
    @Column(name = "end_time")
    private LocalDateTime endTime;

    /**
     * 执行耗时（毫秒）；= end_time - start_time，终态时写入。
     */
    @Column(name = "duration_millis")
    private Long durationMillis;

    /**
     * 执行机标识；集群场景记录实际执行节点（主机名:端口），
     * 无状态设计下用于排查"哪个节点执行了本批任务"。
     */
    @Column(name = "executor", length = 100)
    private String executor;

    /**
     * 记录创建时间。
     */
    @CreationTimestamp
    @Column(name = "create_time", nullable = false)
    private LocalDateTime createTime;

    /**
     * 触发来源枚举。
     */
    public enum TriggerSource {
        /**
         * 调度器自动触发。
         */
        SCHEDULE,
        /**
         * 运维手动触发。
         */
        MANUAL,
        /**
         * 死信重放触发。
         */
        REPLAY
    }
}
