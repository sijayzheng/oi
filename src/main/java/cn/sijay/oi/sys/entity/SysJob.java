package cn.sijay.oi.sys.entity;

import cn.sijay.oi.sys.enums.JobConflictStrategy;
import cn.sijay.oi.sys.enums.JobRetryPolicy;
import cn.sijay.oi.sys.enums.JobTriggerType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Enumerated;
import jakarta.persistence.EnumType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.SQLRestriction;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * 任务调度定义实体，映射表 {@code sys_job}（DDL 见 classpath:sql/schema-h2.sql）。
 *
 * <p>一条记录即一个可调度任务，承载触发配置（类型 + 触发值）、
 * 重试策略、超时保护、并发冲突策略与告警阈值，全部支持运行时动态修改、
 * 即时生效（无需重启应用）。
 *
 * <p>关键约定（与 DDL 注释对齐）：
 * <ul>
 *   <li><b>软删除</b>：{@code deleted} 默认 0，删除时写入本行主键 id；
 *       {@link SQLRestriction} 使本实体的所有查询自动携带 {@code deleted = 0} 条件；</li>
 *   <li><b>唯一约束</b>：{@code UNIQUE (job_key, deleted)}，job_key 为业务唯一键，
 *       与 {@code @OiScheduled} 注解的 jobKey 对应；</li>
 *   <li><b>trigger_value</b>：统一字符串存储——CRON 类型存 6 位表达式，
 *       fixed-rate/fixed-delay 类型存毫秒周期数；</li>
 *   <li><b>超时/重试等时长</b>：统一毫秒存储，语义明确且免解析歧义。</li>
 * </ul>
 *
 * @author sijay
 */
@Data
@NoArgsConstructor
@Entity
@Table(name = "sys_job")
@SQLRestriction("deleted = 0")
public class SysJob {

    /**
     * 主键；IDENTITY 自增，禁止手工指定。
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 任务业务键；全局唯一，与 {@code @OiScheduled#jobKey} 对应，
     * 运维操作与死信重放按此定位任务，创建后禁止修改。
     */
    @Column(name = "job_key", nullable = false, length = 100)
    private String jobKey;

    /**
     * 任务名称；管理界面展示用。
     */
    @Column(nullable = false, length = 100)
    private String name;

    /**
     * 触发类型；CRON / FIXED_RATE / FIXED_DELAY。
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "trigger_type", nullable = false, length = 20)
    private JobTriggerType triggerType = JobTriggerType.CRON;

    /**
     * 触发值；CRON 存 6 位表达式（如 0 * * * * *），
     * fixed-rate/fixed-delay 存毫秒周期数（如 60000）。
     */
    @Column(name = "trigger_value", nullable = false, length = 100)
    private String triggerValue;

    /**
     * 任务超时时间（毫秒）；&gt;0 时启用超时保护，超时强制中断并标记 TIMEOUT；
     * 0 表示不限时。
     */
    @Column(name = "timeout_millis", nullable = false)
    private Long timeoutMillis = 0L;

    /**
     * 重试策略；FIXED_INTERVAL 固定间隔 / EXPONENTIAL_BACKOFF 指数退避（含抖动）。
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "retry_policy", nullable = false, length = 30)
    private JobRetryPolicy retryPolicy = JobRetryPolicy.FIXED_INTERVAL;

    /**
     * 最大重试次数；0 表示不重试。
     */
    @Column(name = "max_retry_count", nullable = false)
    private Integer maxRetryCount = 0;

    /**
     * 初始重试间隔（毫秒）；固定间隔策略的等待时长、指数退避策略的基数。
     */
    @Column(name = "retry_initial_interval", nullable = false)
    private Long retryInitialInterval = 60_000L;

    /**
     * 最大重试间隔（毫秒）；指数退避的封顶值。
     */
    @Column(name = "retry_max_interval", nullable = false)
    private Long retryMaxInterval = 600_000L;

    /**
     * 可重试异常白名单；类型全限定名模式列表（逗号分隔，支持 * 尾通配，
     * 如 {@code java.io.IOException,java.net.SocketTimeoutException}）；
     * 命中即进入重试流程（黑名单优先级更高）。
     */
    @Column(name = "retry_include", length = 1000)
    private String retryInclude;

    /**
     * 不可重试异常黑名单；类型全限定名模式列表（逗号分隔，支持 * 尾通配）；
     * 命中即直接判失败不重试（优先级高于白名单与默认判定）。
     */
    @Column(name = "retry_exclude", length = 1000)
    private String retryExclude;

    /**
     * 并发冲突策略；DISCARD 丢弃（默认）/ QUEUE 排队等待。
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "conflict_strategy", nullable = false, length = 20)
    private JobConflictStrategy conflictStrategy = JobConflictStrategy.DISCARD;

    /**
     * 连续失败告警阈值；&gt;0 时连续失败达到该次数触发站内信告警，
     * 0 表示不启用该类告警。
     */
    @Column(name = "alert_failure_threshold", nullable = false)
    private Integer alertFailureThreshold = 0;

    /**
     * 任务备注；说明任务用途与注意事项。
     */
    @Column(length = 500)
    private String remark;

    /**
     * 启用状态；TRUE=已启用参与调度 / FALSE=已暂停（保留配置但不触发）。
     */
    @Column(nullable = false)
    private Boolean enabled = Boolean.TRUE;

    /**
     * 创建人用户 id；0 或 NULL=系统初始化；审计字段，应用层写入。
     */
    @Column(name = "create_by")
    private Long createBy;

    /**
     * 创建时间；审计字段。
     */
    @CreationTimestamp
    @Column(name = "create_time", nullable = false)
    private LocalDateTime createTime;

    /**
     * 最后修改人用户 id；审计字段，应用层写入。
     */
    @Column(name = "update_by")
    private Long updateBy;

    /**
     * 更新时间；行被 UPDATE 时自动刷新。
     */
    @UpdateTimestamp
    @Column(name = "update_time")
    private LocalDateTime updateTime;

    /**
     * 软删标记；0=未删除，删除时=本行主键 id；唯一键 uk_job_key 包含本列。
     */
    @Column(nullable = false)
    private Long deleted = 0L;
}
