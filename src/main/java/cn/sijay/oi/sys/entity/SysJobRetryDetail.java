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
 * 任务重试明细实体，映射表 {@code sys_job_retry_detail}（DDL 见 classpath:sql/schema-h2.sql）。
 *
 * <p>规范要求"完整记录每一次重试的详细信息"：重试触发时间、异常堆栈、单次执行耗时。
 * 一行记录一次尝试（首次执行 attempt_no=0 也记录），与 {@link SysJobLog} 一对多
 * （log_id 外键），提供完整的重试过程追溯能力。
 *
 * <p>关键约定：
 * <ul>
 *   <li><b>append-only</b>：仅插入不修改，重试过程落库即固化；</li>
 *   <li><b>异常栈截断</b>：入库前截断至 4000 字符；</li>
 *   <li><b>重试等待时间</b>：next_retry_at 记录本次失败后计算出的下次重试时刻
 *       （指数退避 + 抖动后的实际值），最后一轮为空。</li>
 * </ul>
 *
 * @author sijay
 */
@Data
@NoArgsConstructor
@Entity
@Table(name = "sys_job_retry_detail")
public class SysJobRetryDetail {

    /**
     * 主键；IDENTITY 自增。
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 所属执行记录 id（sys_job_log.id）。
     */
    @Column(name = "log_id", nullable = false)
    private Long logId;

    /**
     * 尝试轮次；0=首次执行，n=第 n 次重试。
     */
    @Column(name = "attempt_no", nullable = false)
    private Integer attemptNo;

    /**
     * 本轮执行状态；RUNNING/SUCCESS/FAILED/TIMEOUT。
     */
    @Column(name = "attempt_status", nullable = false, length = 20)
    private String attemptStatus;

    /**
     * 本轮异常类型名。
     */
    @Column(name = "error_type", length = 255)
    private String errorType;

    /**
     * 本轮异常信息（message + 堆栈）；入库前截断至 4000 字符。
     */
    @Column(name = "error_stack", length = 4000)
    private String errorStack;

    /**
     * 本轮开始时间。
     */
    @Column(name = "attempt_start_time", nullable = false)
    private LocalDateTime attemptStartTime;

    /**
     * 本轮结束时间。
     */
    @Column(name = "attempt_end_time")
    private LocalDateTime attemptEndTime;

    /**
     * 本轮执行耗时（毫秒）。
     */
    @Column(name = "attempt_duration_millis")
    private Long attemptDurationMillis;

    /**
     * 本轮失败后计算出的下次重试时刻（含退避 + 抖动）；重试耗尽或成功为空。
     */
    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    /**
     * 记录创建时间。
     */
    @CreationTimestamp
    @Column(name = "create_time", nullable = false)
    private LocalDateTime createTime;
}
