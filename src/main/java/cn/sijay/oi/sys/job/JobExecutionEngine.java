package cn.sijay.oi.sys.job;

import cn.sijay.oi.common.util.RedisUtil;
import cn.sijay.oi.sys.entity.SysJob;
import cn.sijay.oi.sys.entity.SysJobLog;
import cn.sijay.oi.sys.entity.SysJobRetryDetail;
import cn.sijay.oi.sys.enums.JobExecuteStatus;
import cn.sijay.oi.sys.repository.SysJobLogRepository;
import cn.sijay.oi.sys.repository.SysJobRetryDetailRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.*;

/**
 * 任务执行引擎：任务实例的完整执行流水线——
 * 分布式锁并发控制 → 超时保护中断 → 重试循环（退避 + 抖动）→
 * 执行记录/重试明细落库 → 死信转移 → 重试完成回调 → 告警评估。
 *
 * <p><b>执行流水线</b>（单次触发的完整生命周期）：
 * <ol>
 *   <li>插入 RUNNING 执行记录（sys_job_log）；</li>
 *   <li>循环执行（首试 + N 次重试）：每轮提交独立线程，超时则 cancel 中断、
 *       记录 TIMEOUT 轮次明细，可超时重试；每轮失败按重试判定与退避计算
 *       决定是否继续；全部尝试明细逐轮 append 到 sys_job_retry_detail；</li>
 *   <li>终态落库（SUCCESS/FAILED/TIMEOUT/DEAD）；</li>
 *   <li>重试耗尽 → 写入死信（含任务配置 JSON 快照与全量历史关联）；</li>
 *   <li>触发 {@link RetryCallback} 回调（隔离执行，异常不影响任务状态）；</li>
 *   <li>触发告警评估（死信告警 + 连续失败 N 次告警）。</li>
 * </ol>
 *
 * <p><b>并发控制</b>：同一任务实例严格串行——执行前获取 Redisson 分布式锁
 * {@code oi:job:lock:{jobKey}}（进程内锁语义同分布式锁统一收口），获取失败的
 * 触发按任务冲突策略处理（DISCARD 丢弃 / QUEUE 排队）。集群部署下多节点
 * 同刻触发同一任务时，仅锁持有节点执行，其余节点直接跳过。
 *
 * <p><b>超时中断</b>：每轮执行提交到工作线程并 {@code Future.get(timeout)}，
 * 超时后 {@code future.cancel(true)} 强制中断工作线程（业务代码可感知中断），
 * 本轮记为 TIMEOUT；超时与业务异常一样参与重试判定。
 *
 * <p><b>无状态设计</b>：本类不持有任何任务运行时状态（执行态全部落库），
 * 任务分片由"谁抢到锁谁执行"实现，天然支持集群水平扩容。
 *
 * @author sijay
 */
@Slf4j
@Component
public class JobExecutionEngine {

    /**
     * 分布式锁 key 前缀；与 RedisUtil 命名规范一致。
     */
    private static final String LOCK_KEY_PREFIX = "oi:job:lock:";

    /**
     * 异常堆栈入库截断长度（与 DDL 列宽一致）。
     */
    private static final int STACK_TRUNCATE_LENGTH = 4000;

    /**
     * 任务执行工作线程池；守护线程避免阻断 JVM 退出。
     */
    private final ExecutorService workerExecutor;

    private final RedisUtil redisUtil;
    private final SysJobLogRepository jobLogRepository;
    private final SysJobRetryDetailRepository retryDetailRepository;
    private final JobRetryDecider retryDecider;
    private final JobBackoffCalculator backoffCalculator;
    private final List<RetryCallback> retryCallbacks;
    private final JobAlertService alertService;

    /**
     * 执行机标识；主机名（截断 100 字符），集群排查用。
     */
    private final String executorId;

    public JobExecutionEngine(RedisUtil redisUtil,
                              SysJobLogRepository jobLogRepository,
                              SysJobRetryDetailRepository retryDetailRepository,
                              JobRetryDecider retryDecider,
                              JobBackoffCalculator backoffCalculator,
                              List<RetryCallback> retryCallbacks,
                              JobAlertService alertService,
                              @Value("${server.port:29979}") int serverPort) {
        this.redisUtil = redisUtil;
        this.jobLogRepository = jobLogRepository;
        this.retryDetailRepository = retryDetailRepository;
        this.retryDecider = retryDecider;
        this.backoffCalculator = backoffCalculator;
        this.retryCallbacks = List.copyOf(retryCallbacks);
        this.alertService = alertService;
        this.workerExecutor = Executors.newCachedThreadPool(r -> {
            Thread t = new Thread(r, "job-worker");
            t.setDaemon(true);
            return t;
        });
        String host;
        try {
            host = InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            host = "unknown";
        }
        this.executorId = (host + ":" + serverPort).length() > 100
                ? (host + ":" + serverPort).substring(0, 100) : host + ":" + serverPort;
    }

    /**
     * 执行任务（入口：调度触发 / 手动触发 / 死信重放统一走本方法）。
     *
     * <p><b>分布式锁并发控制</b>：获取锁失败时按冲突策略处理——
     * DISCARD 记 SKIPPED 后返回；QUEUE 阻塞等待前序完成后立即串行执行。
     *
     * @param job    任务配置
     * @param action 业务执行体（@OiScheduled 方法代理或重放执行体）
     * @param source 触发来源（SCHEDULE/MANUAL/REPLAY）
     * @return 执行结果
     */
    public JobExecutionResult execute(SysJob job, Runnable action, SysJobLog.TriggerSource source) {
        String lockKey = LOCK_KEY_PREFIX + job.getJobKey();
        boolean locked = false;
        try {
            locked = redisUtil.tryLock(lockKey, 0, computeLease(job), TimeUnit.MILLISECONDS);
            if (!locked) {
                if (job.getConflictStrategy() == cn.sijay.oi.sys.enums.JobConflictStrategy.DISCARD) {
                    return recordSkipped(job, source);
                }
                // QUEUE：排队等待前序执行完成（上限等待 = 一个最长超时窗口）
                locked = redisUtil.tryLock(lockKey, computeMaxWait(job), computeLease(job), TimeUnit.MILLISECONDS);
                if (!locked) {
                    return recordSkipped(job, source);
                }
            }
            return doExecute(job, action, source);
        } finally {
            if (locked) {
                redisUtil.unlock(lockKey);
            }
        }
    }

    /**
     * 核心执行循环（已持锁）：首试 + 重试，逐轮落明细，终态落库后触发回调与告警。
     */
    private JobExecutionResult doExecute(SysJob job, Runnable action, SysJobLog.TriggerSource source) {
        SysJobLog jobLog = insertRunningLog(job, source);
        int maxRetry = Math.max(0, job.getMaxRetryCount());
        long totalStart = System.nanoTime();
        Throwable error = null;
        // 最后一轮实际执行的状态与轮次索引（终态/重试计数依据）
        JobExecuteStatus lastAttemptStatus = JobExecuteStatus.FAILED;
        int lastExecutedAttempt = -1;

        for (int attempt = 0; attempt <= maxRetry; attempt++) {
            if (attempt > 0) {
                Duration backoff = backoffCalculator.compute(job, attempt);
                try {
                    Thread.sleep(backoff.toMillis());
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    // 等待期被中断（应用停机）：直接判失败，未执行的轮次不计入重试
                    error = ie;
                    break;
                }
            }
            LocalDateTime attemptStart = LocalDateTime.now();
            long attemptStartNanos = System.nanoTime();
            JobExecuteStatus attemptStatus;
            Throwable attemptError = null;
            try {
                runWithTimeout(job, action);
                attemptStatus = JobExecuteStatus.SUCCESS;
            } catch (JobTimeoutException toe) {
                attemptStatus = JobExecuteStatus.TIMEOUT;
                attemptError = toe;
            } catch (Throwable t) {
                attemptStatus = JobExecuteStatus.FAILED;
                attemptError = t;
            }
            long attemptDuration = (System.nanoTime() - attemptStartNanos) / 1_000_000;
            lastAttemptStatus = attemptStatus;
            lastExecutedAttempt = attempt;
            saveAttemptDetail(jobLog.getId(), attempt, attemptStatus, attemptError,
                    attemptStart, attemptDuration);
            if (attemptStatus == JobExecuteStatus.SUCCESS) {
                error = null;
                break;
            }
            error = attemptError;
            // 不再重试的分支：不可重试 or 已到重试上限
            boolean canRetry = attempt < maxRetry
                    && (attemptStatus == JobExecuteStatus.TIMEOUT || retryDecider.isRetryable(job, attemptError));
            if (!canRetry) {
                break;
            }
            // 记录本轮计算出的下次重试时刻（退避 + 抖动）
            LocalDateTime nextRetryAt = LocalDateTime.now().plus(backoffCalculator.compute(job, attempt + 1));
            updateAttemptNextRetry(jobLog.getId(), attempt, nextRetryAt);
        }

        long totalDuration = (System.nanoTime() - totalStart) / 1_000_000;
        boolean success = error == null;
        // 重试次数 = 最后一轮执行索引（0=首试成功无重试；1=重试 1 次后成功；中断场景为最后执行轮）
        int retryCount = Math.max(0, lastExecutedAttempt);
        // 不可重试异常：直接 FAILED（不产生死信）；可重试但重试耗尽：转死信
        boolean toDeadLetter = !success && maxRetry > 0 && retryDecider.isRetryable(job, error);

        JobExecuteStatus finalStatus;
        if (success) {
            finalStatus = JobExecuteStatus.SUCCESS;
        } else if (toDeadLetter) {
            finalStatus = JobExecuteStatus.DEAD;
        } else if (lastAttemptStatus == JobExecuteStatus.TIMEOUT) {
            // 最后一轮为超时且不再重试：终态保留 TIMEOUT（区别于业务异常失败）
            finalStatus = JobExecuteStatus.TIMEOUT;
        } else {
            finalStatus = JobExecuteStatus.FAILED;
        }
        finalizeLog(jobLog, finalStatus, retryCount, totalDuration, error);

        // 死信转移（完整保留任务上下文 + 全量执行历史关联）
        if (toDeadLetter) {
            alertService.recordDeadLetter(job, jobLog, error, retryCount);
        }

        // 重试完成回调（隔离执行，异常不影响任务状态）
        invokeCallbacks(job, jobLog.getId(), finalStatus, retryCount, totalDuration, error);

        // 告警评估（连续失败 N 次阈值）
        if (!success) {
            alertService.evaluateConsecutiveFailureAlert(job);
        }
        return new JobExecutionResult(jobLog.getId(), success, finalStatus.name(), error);
    }

    /**
     * 单轮执行 + 超时保护。
     *
     * @throws JobTimeoutException 超时被强制中断
     * @throws Throwable           业务异常原样抛出
     */
    private void runWithTimeout(SysJob job, Runnable action) throws Throwable {
        long timeout = job.getTimeoutMillis();
        if (timeout <= 0) {
            action.run();
            return;
        }
        Future<?> future = workerExecutor.submit(action);
        try {
            future.get(timeout, TimeUnit.MILLISECONDS);
        } catch (TimeoutException te) {
            future.cancel(true);
            throw new JobTimeoutException("任务执行超时（" + timeout + "ms），已强制中断", te);
        } catch (CancellationException ce) {
            throw new JobTimeoutException("任务执行被取消（超时中断生效）", ce);
        } catch (ExecutionException ee) {
            throw ee.getCause() == null ? ee : ee.getCause();
        }
    }

    /**
     * 插入 RUNNING 执行记录。
     */
    private SysJobLog insertRunningLog(SysJob job, SysJobLog.TriggerSource source) {
        SysJobLog jobLog = new SysJobLog();
        jobLog.setJobId(job.getId());
        jobLog.setJobKey(job.getJobKey());
        jobLog.setJobName(job.getName());
        jobLog.setTriggerSource(source.name());
        jobLog.setExecuteStatus(JobExecuteStatus.RUNNING.name());
        jobLog.setStartTime(LocalDateTime.now());
        jobLog.setExecutor(executorId);
        return jobLogRepository.save(jobLog);
    }

    /**
     * SKIPPED 记录（并发冲突丢弃）。
     */
    private JobExecutionResult recordSkipped(SysJob job, SysJobLog.TriggerSource source) {
        LocalDateTime now = LocalDateTime.now();
        SysJobLog jobLog = new SysJobLog();
        jobLog.setJobId(job.getId());
        jobLog.setJobKey(job.getJobKey());
        jobLog.setJobName(job.getName());
        jobLog.setTriggerSource(source.name());
        jobLog.setExecuteStatus(JobExecuteStatus.SKIPPED.name());
        jobLog.setStartTime(now);
        jobLog.setEndTime(now);
        jobLog.setDurationMillis(0L);
        jobLog.setExecutor(executorId);
        jobLogRepository.save(jobLog);
        log.info("[Job] 并发冲突丢弃: jobKey={}, source={}", job.getJobKey(), source);
        return new JobExecutionResult(jobLog.getId(), false, JobExecuteStatus.SKIPPED.name(), null);
    }

    /**
     * 逐轮 append 尝试明细。
     */
    private void saveAttemptDetail(Long logId, int attemptNo, JobExecuteStatus status,
                                   Throwable error, LocalDateTime start, long duration) {
        SysJobRetryDetail detail = new SysJobRetryDetail();
        detail.setLogId(logId);
        detail.setAttemptNo(attemptNo);
        detail.setAttemptStatus(status.name());
        detail.setAttemptStartTime(start);
        detail.setAttemptEndTime(LocalDateTime.now());
        detail.setAttemptDurationMillis(duration);
        if (error != null) {
            detail.setErrorType(rootCause(error).getClass().getName());
            detail.setErrorStack(truncate(formatStack(error)));
        }
        retryDetailRepository.save(detail);
    }

    /**
     * 回填本轮计算出的下次重试时刻（最近一轮明细的 next_retry_at）。
     */
    private void updateAttemptNextRetry(Long logId, int attemptNo, LocalDateTime nextRetryAt) {
        retryDetailRepository.findByLogIdOrderByAttemptNoAsc(logId).stream()
                             .filter(d -> d.getAttemptNo() == attemptNo)
                             .findFirst()
                             .ifPresent(d -> {
                                 d.setNextRetryAt(nextRetryAt);
                                 retryDetailRepository.save(d);
                             });
    }

    /**
     * 终态落库（状态/异常/耗时/重试计数）。
     */
    private void finalizeLog(SysJobLog jobLog, JobExecuteStatus finalStatus, int retryCount,
                             long duration, Throwable error) {
        jobLog.setExecuteStatus(finalStatus.name());
        jobLog.setRetryCount(Math.max(0, retryCount));
        jobLog.setEndTime(LocalDateTime.now());
        jobLog.setDurationMillis(duration);
        if (error != null) {
            jobLog.setErrorType(rootCause(error).getClass().getName());
            jobLog.setErrorStack(truncate(formatStack(error)));
        }
        jobLogRepository.save(jobLog);
    }

    /**
     * 重试完成回调；隔离执行——任一回调异常仅记录告警日志。
     */
    private void invokeCallbacks(SysJob job, Long logId, JobExecuteStatus finalStatus,
                                 int retryCount, long duration, Throwable error) {
        RetryCallback.RetryResult result = new RetryCallback.RetryResult(
                job.getJobKey(), job.getName(),
                finalStatus == JobExecuteStatus.SUCCESS,
                finalStatus.name(), Math.max(0, retryCount), duration, logId,
                error == null ? null : error.getMessage());
        for (RetryCallback callback : retryCallbacks) {
            try {
                callback.onCompleted(result);
            } catch (Exception e) {
                log.warn("[Job] 重试完成回调异常（已隔离，不影响任务状态）: jobKey={}, callback={}",
                        job.getJobKey(), callback.getClass().getName(), e);
            }
        }
    }

    /**
     * 计算锁持有时间：超时窗口（或默认上限）+ 重试总等待余量。
     */
    private long computeLease(SysJob job) {
        long base = job.getTimeoutMillis() > 0 ? job.getTimeoutMillis() : 300_000L;
        // 重试等待不计入锁持有：锁仅保护"单轮执行"的互斥已足够（重试期间持锁）
        return base * (Math.max(0, job.getMaxRetryCount()) + 1);
    }

    /**
     * QUEUE 策略的最长排队等待：一轮完整执行窗口（超时或默认值）。
     */
    private long computeMaxWait(SysJob job) {
        return job.getTimeoutMillis() > 0 ? job.getTimeoutMillis() : 300_000L;
    }

    /**
     * 提取异常根因（沿因果链下钻；包装异常按根因记录，排查价值更高）。
     */
    private Throwable rootCause(Throwable error) {
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    /**
     * 异常堆栈格式化（message + 限定深度堆栈；从根因展开）。
     */
    private String formatStack(Throwable error) {
        Throwable root = rootCause(error);
        StringBuilder sb = new StringBuilder(512);
        sb.append(root.getClass().getName()).append(": ").append(root.getMessage()).append('\n');
        StackTraceElement[] elements = root.getStackTrace();
        for (int i = 0; i < Math.min(elements.length, 20); i++) {
            sb.append("\tat ").append(elements[i]).append('\n');
        }
        return sb.toString();
    }

    /**
     * 字符串截断。
     */
    private String truncate(String value) {
        if (value == null || value.length() <= STACK_TRUNCATE_LENGTH) {
            return value;
        }
        return value.substring(0, STACK_TRUNCATE_LENGTH);
    }

    /**
     * 任务超时异常；语义化标记"超时中断"（区别于业务异常）。
     */
    public static class JobTimeoutException extends Exception {
        public JobTimeoutException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    /**
     * 单次执行结果。
     *
     * @param logId   执行记录 id
     * @param success 是否成功
     * @param status  终态状态名
     * @param error   终态异常；成功为 null
     */
    public record JobExecutionResult(Long logId, boolean success, String status, Throwable error) {
    }
}
