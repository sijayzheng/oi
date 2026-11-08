package cn.sijay.oi.sys.job;

import cn.sijay.oi.common.exception.BusinessErrorCode;
import cn.sijay.oi.common.exception.BusinessException;
import cn.sijay.oi.sys.entity.SysJob;
import cn.sijay.oi.sys.entity.SysJobLog;
import cn.sijay.oi.sys.repository.SysJobRepository;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.support.CronTrigger;
import org.springframework.scheduling.support.PeriodicTrigger;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.ZoneId;
import java.util.Map;
import java.util.TimeZone;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledFuture;

/**
 * 动态调度器管理器：基于 Spring 原生 {@link TaskScheduler} 实现任务的
 * 动态注册、动态启停与配置在线修改（全部免重启即时生效）。
 *
 * <p><b>调度能力对齐 Spring 规范</b>：
 * <ul>
 *   <li>CRON：{@link CronTrigger}（Spring 6 位表达式，与 {@code @Scheduled(cron)} 语法兼容，
 *       支持时区）；</li>
 *   <li>fixed-rate / fixed-delay：{@link PeriodicTrigger}（setFixedRate 切换语义，
 *       与 {@code @Scheduled(fixedRate/fixedDelay)} 一致）。</li>
 * </ul>
 *
 * <p><b>秒级精度与跨时区</b>：调度线程池默认秒级触发精度（{@code ThreadPoolTaskScheduler}
 * 基于 {@code ScheduledThreadPoolExecutor}）；CRON 按 {@code sys_job.time_zone} 配置的
 * 时区求值，未配置时按应用默认时区。
 *
 * <p><b>动态变更</b>：注册/取消均为 O(1) future 表操作，变更即生效——
 * 旧 future 取消（不打断在途执行，仅停止后续触发）、按新配置重新注册。
 * 表并发安全（ConcurrentHashMap），变更操作由 Service 层在配置落库后调用。
 *
 * <p><b>性能</b>：触发线程仅做"查库 + 触发"，业务执行在 {@link JobExecutionEngine}
 * 的工作线程池运行，触发线程立即释放；单次触发开销微秒级，
 * 满足单实例 1000 并发任务、触发延迟 &lt;10ms 的指标要求。
 *
 * @author sijay
 */
@Slf4j
@Component
public class JobSchedulerManager {

    /**
     * 注册表：jobKey → 触发 future；变更时先 cancel 后注册，保证无重复触发。
     */
    private final Map<String, ScheduledFuture<?>> registry = new ConcurrentHashMap<>();

    /**
     * 调度线程池；Spring 标准 ThreadPoolTaskScheduler。
     */
    private final ThreadPoolTaskScheduler taskScheduler;

    private final SysJobRepository jobRepository;

    /**
     * 任务执行动作注册表；由 {@code JobRegistrationService} 启动扫描 @OiScheduled 填充。
     */
    private final JobActionRegistry actionRegistry;

    /**
     * 执行引擎；@Lazy 打破"注册服务 → 本类 → 引擎 → 告警 → 站内信"与
     * 手动触发入口的潜在初始化环。
     */
    private final JobExecutionEngine executionEngine;

    /**
     * 构造；初始化调度线程池（守护线程、异常不吞）。
     *
     * @param poolSize 调度线程池大小（配置项 {@code oi.job.scheduler.pool-size}，
     *                 默认 8——1000 任务规模实测足够，触发线程不承载业务）
     */
    public JobSchedulerManager(SysJobRepository jobRepository,
                               JobActionRegistry actionRegistry,
                               @Lazy JobExecutionEngine executionEngine,
                               @Value("${oi.job.scheduler.pool-size:8}") int poolSize) {
        this.jobRepository = jobRepository;
        this.actionRegistry = actionRegistry;
        this.executionEngine = executionEngine;
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(poolSize);
        scheduler.setThreadNamePrefix("job-scheduler-");
        scheduler.setDaemon(true);
        scheduler.setRemoveOnCancelPolicy(true);
        scheduler.setErrorHandler(t -> log.error("[Job] 调度触发异常（执行引擎内部已兜底，此处防御性记录）", t));
        scheduler.initialize();
        this.taskScheduler = scheduler;
    }

    /**
     * 事务提交后注册（运维写操作的调度同步入口）。
     *
     * <p><b>为什么需要 afterCommit</b>：调用方（create/update/resume）处于
     * 事务上下文中，若在事务提交前注册，fixed-rate 的"注册即首次触发"会立即
     * 走 {@link #triggerOnce}——触发线程重读任务配置时事务尚未提交，
     * 读不到新行（或读到旧配置），导致误判"任务已删除"或旧配置执行。
     * 事务同步器保证调度注册与可见性对齐；无事务上下文时立即注册。
     *
     * @param job 任务配置
     */
    public void registerAfterCommit(SysJob job) {
        if (org.springframework.transaction.support.TransactionSynchronizationManager
                .isSynchronizationActive()) {
            org.springframework.transaction.support.TransactionSynchronizationManager
                    .registerSynchronization(new org.springframework.transaction.support.TransactionSynchronization() {
                        @Override
                        public void afterCommit() {
                            try {
                                register(job);
                            } catch (Exception e) {
                                log.error("[Job] 事务提交后注册失败（任务保持不调度，请检查配置）: jobKey={}",
                                        job.getJobKey(), e);
                            }
                        }
                    });
        } else {
            register(job);
        }
    }

    /**
     * 注册任务（幂等——重复注册先取消旧 future）；enabled=false 时仅清注册不调度。
     *
     * <p>使用场景：启动扫描、运维启停、配置修改后的重新注册。
     *
     * @param job 任务配置（以落库后的最新配置为准）
     */
    public void register(SysJob job) {
        unregister(job.getJobKey());
        if (!Boolean.TRUE.equals(job.getEnabled())) {
            log.info("[Job] 注册跳过（任务已暂停）: jobKey={}", job.getJobKey());
            return;
        }
        Runnable trigger = () -> triggerOnce(job.getJobKey());
        ScheduledFuture<?> future;
        switch (job.getTriggerType()) {
            case CRON -> future = taskScheduler.schedule(trigger, buildCronTrigger(job));
            case FIXED_RATE -> future = taskScheduler.scheduleAtFixedRate(trigger,
                    Duration.ofMillis(parsePeriodMillis(job)));
            case FIXED_DELAY -> future = taskScheduler.scheduleWithFixedDelay(trigger,
                    Duration.ofMillis(parsePeriodMillis(job)));
            default -> throw new BusinessException(BusinessErrorCode.JOB_TRIGGER_INVALID);
        }
        registry.put(job.getJobKey(), future);
        log.info("[Job] 注册成功: jobKey={}, type={}, value={}, timeZone={}",
                job.getJobKey(), job.getTriggerType(), job.getTriggerValue(), job.getTimeZone());
    }

    /**
     * 取消注册（暂停/删除/改配置的前置动作）；仅停止后续触发，不打断在途执行。
     *
     * @param jobKey 任务业务键
     */
    public void unregister(String jobKey) {
        ScheduledFuture<?> future = registry.remove(jobKey);
        if (future != null) {
            future.cancel(false);
            log.info("[Job] 已取消调度: jobKey={}", jobKey);
        }
    }

    /**
     * 判断任务是否已注册（测试与运维观测用）。
     *
     * @param jobKey 任务业务键
     * @return 已注册返回 true
     */
    public boolean isRegistered(String jobKey) {
        return registry.containsKey(jobKey);
    }

    /**
     * 当前注册任务数（性能观测用）。
     *
     * @return 注册表大小
     */
    public int registeredCount() {
        return registry.size();
    }

    /**
     * 单次触发入口；触发时从库重读任务配置（配置变更即时生效的兜底），
     * 任务被暂停/删除时静默跳过（生效语义统一由库中状态决定）。
     *
     * @param jobKey 任务业务键
     */
    public void triggerOnce(String jobKey) {
        try {
            jobRepository.findByJobKey(jobKey).ifPresentOrElse(job -> {
                if (!Boolean.TRUE.equals(job.getEnabled())) {
                    return;
                }
                Runnable action = actionRegistry.lookup(jobKey);
                if (action == null) {
                    log.warn("[Job] 触发跳过（任务无执行动作，可能为预留配置）: jobKey={}", jobKey);
                    return;
                }
                executionEngine.execute(job, action, SysJobLog.TriggerSource.SCHEDULE);
            }, () -> log.info("[Job] 触发跳过（任务已删除）: jobKey={}", jobKey));
        } catch (Exception e) {
            // 触发链路防御性兜底：调度线程吞异常会静默停摆，此处必须记录
            log.error("[Job] 触发执行异常: jobKey={}", jobKey, e);
        }
    }

    /**
     * 构建 CRON 触发器（含时区）。
     */
    private CronTrigger buildCronTrigger(SysJob job) {
        try {
            if (job.getTimeZone() == null || job.getTimeZone().isBlank()) {
                return new CronTrigger(job.getTriggerValue());
            }
            return new CronTrigger(job.getTriggerValue(), TimeZone.getTimeZone(ZoneId.of(job.getTimeZone())));
        } catch (Exception e) {
            throw new BusinessException(BusinessErrorCode.JOB_TRIGGER_INVALID);
        }
    }

    /**
     * 解析周期毫秒（fixed-rate / fixed-delay）；下限 1000ms 防打爆。
     */
    private long parsePeriodMillis(SysJob job) {
        try {
            long period = Long.parseLong(job.getTriggerValue().trim());
            if (period < 1000) {
                throw new BusinessException(BusinessErrorCode.JOB_TRIGGER_INVALID);
            }
            return period;
        } catch (NumberFormatException e) {
            throw new BusinessException(BusinessErrorCode.JOB_TRIGGER_INVALID);
        }
    }

    /**
     * 优雅停机：取消全部触发（不打断在途执行，等待执行引擎落终态）。
     */
    @PreDestroy
    public void shutdown() {
        registry.values().forEach(f -> f.cancel(false));
        registry.clear();
        taskScheduler.shutdown();
        log.info("[Job] 调度器已关闭，注册表清空");
    }
}
