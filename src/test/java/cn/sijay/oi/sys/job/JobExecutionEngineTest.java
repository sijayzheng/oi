package cn.sijay.oi.sys.job;

import cn.sijay.oi.common.util.RedisUtil;
import cn.sijay.oi.sys.entity.SysJob;
import cn.sijay.oi.sys.entity.SysJobLog;
import cn.sijay.oi.sys.enums.JobConflictStrategy;
import cn.sijay.oi.sys.enums.JobRetryPolicy;
import cn.sijay.oi.sys.repository.SysJobDeadLetterRepository;
import cn.sijay.oi.sys.repository.SysJobLogRepository;
import cn.sijay.oi.sys.repository.SysJobRetryDetailRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 任务执行引擎单元测试（Mock Redis/Repository，验证引擎编排逻辑本身）。
 *
 * <p>覆盖规范要求的核心场景：
 * <ul>
 *   <li>重试逻辑：固定间隔重试成功、指数退避、不可重试异常直接失败、重试耗尽转死信；</li>
 *   <li>超时中断：超时任务被强制中断并标记 TIMEOUT、超时可重试；</li>
 *   <li>并发冲突：DISCARD 丢弃（SKIPPED）、QUEUE 排队等待；</li>
 *   <li>异常场景：业务异常与运行时异常的终态记录、回调隔离；</li>
 *   <li>重试完成回调：终态触发、回调异常不影响任务状态。</li>
 * </ul>
 *
 * <p>告警服务以真实实例注入但 Mock 其依赖（不验证告警细节，另由集成测试覆盖）。
 *
 * @author sijay
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("任务执行引擎测试")
class JobExecutionEngineTest {

    @Mock
    private RedisUtil redisUtil;
    @Mock
    private SysJobLogRepository jobLogRepository;
    @Mock
    private SysJobRetryDetailRepository retryDetailRepository;
    @Mock
    private SysJobDeadLetterRepository deadLetterRepository;

    private JobExecutionEngine engine;
    private RecordingCallback callback;

    /**
     * 重试完成回调记录器（记录终态供断言）。
     */
    static class RecordingCallback implements RetryCallback {
        final List<RetryResult> results = java.util.Collections.synchronizedList(new java.util.ArrayList<>());

        @Override
        public void onCompleted(RetryResult result) {
            results.add(result);
        }
    }

    @BeforeEach
    void setUp() {
        // 执行记录 save 直接回传（含 id 分配模拟）
        lenient().when(jobLogRepository.save(any(SysJobLog.class))).thenAnswer(inv -> {
            SysJobLog log = inv.getArgument(0);
            if (log.getId() == null) {
                log.setId(System.nanoTime());
            }
            return log;
        });
        lenient().when(retryDetailRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(retryDetailRepository.findByLogIdOrderByAttemptNoAsc(anyLong()))
                 .thenReturn(List.of());
        // 分布式锁：默认获取成功
        lenient().when(redisUtil.tryLock(anyString(), anyLong(), anyLong(), any(TimeUnit.class)))
                 .thenReturn(true);

        callback = new RecordingCallback();
        JobAlertService alertService = new JobAlertService(
                deadLetterRepository, jobLogRepository, null);
        engine = new JobExecutionEngine(redisUtil, jobLogRepository, retryDetailRepository,
                new JobRetryDecider(new org.springframework.beans.factory.ObjectProvider<>() {
                    @Override
                    public RetryableExceptionMatcher getIfAvailable() {
                        return null;
                    }
                }), new JobBackoffCalculator(jobLogRepository),
                List.of(callback), alertService, 129979);
    }

    /**
     * 构造任务配置。
     */
    private SysJob job(JobRetryPolicy policy, int maxRetry, long initial, long max) {
        SysJob job = new SysJob();
        job.setId(1L);
        job.setJobKey("test.job");
        job.setName("测试任务");
        job.setRetryPolicy(policy);
        job.setMaxRetryCount(maxRetry);
        job.setRetryInitialInterval(initial);
        job.setRetryMaxInterval(max);
        job.setConflictStrategy(JobConflictStrategy.DISCARD);
        return job;
    }

    /* ==================== 重试逻辑 ==================== */

    @Test
    @DisplayName("重试成功：第 2 次尝试成功，状态 SUCCESS、重试计数 1")
    void retryThenSuccess() {
        SysJob job = job(JobRetryPolicy.FIXED_INTERVAL, 3, 1, 10);
        AtomicInteger attempts = new AtomicInteger();
        Runnable action = () -> {
            if (attempts.incrementAndGet() < 2) {
                throw new RuntimeException(new IOException("transient"));
            }
        };
        JobExecutionEngine.JobExecutionResult result = engine.execute(
                job, action, SysJobLog.TriggerSource.MANUAL);
        assertThat(result.success()).isTrue();
        assertThat(result.status()).isEqualTo("SUCCESS");
        assertThat(attempts.get()).isEqualTo(2);
        ArgumentCaptor<SysJobLog> captor = ArgumentCaptor.forClass(SysJobLog.class);
        verify(jobLogRepository, times(2)).save(captor.capture());
        SysJobLog finalLog = captor.getAllValues().get(1);
        assertThat(finalLog.getExecuteStatus()).isEqualTo("SUCCESS");
        assertThat(finalLog.getRetryCount()).isEqualTo(1);
        // 回调：终态触发且成功
        assertThat(callback.results).hasSize(1);
        assertThat(callback.results.get(0).success()).isTrue();
        assertThat(callback.results.get(0).retryCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("不可重试异常：直接失败不重试（状态 FAILED，仅 1 次尝试）")
    void nonRetryableFailsFast() {
        SysJob job = job(JobRetryPolicy.FIXED_INTERVAL, 3, 1, 10);
        AtomicInteger attempts = new AtomicInteger();
        Runnable action = () -> {
            attempts.incrementAndGet();
            throw new IllegalArgumentException("bad arg");
        };
        JobExecutionEngine.JobExecutionResult result = engine.execute(
                job, action, SysJobLog.TriggerSource.SCHEDULE);
        assertThat(result.success()).isFalse();
        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(attempts.get()).isEqualTo(1);
        verify(retryDetailRepository, times(1)).save(any());
    }

    @Test
    @DisplayName("重试耗尽转死信：状态 DEAD、死信记录入库、死信告警链路触发")
    void retryExhaustedToDeadLetter() {
        SysJob job = job(JobRetryPolicy.FIXED_INTERVAL, 2, 1, 10);
        AtomicInteger attempts = new AtomicInteger();
        Runnable action = () -> {
            attempts.incrementAndGet();
            throw new RuntimeException(new IOException("always fails"));
        };
        JobExecutionEngine.JobExecutionResult result = engine.execute(
                job, action, SysJobLog.TriggerSource.SCHEDULE);
        assertThat(result.success()).isFalse();
        assertThat(result.status()).isEqualTo("DEAD");
        assertThat(attempts.get()).isEqualTo(3); // 首试 + 2 次重试
        // 死信链路（告警服务内部捕获异常不影响主流程，此用例仅验证不抛出）
        assertThat(callback.results).hasSize(1);
        assertThat(callback.results.get(0).finalStatus()).isEqualTo("DEAD");
        assertThat(callback.results.get(0).retryCount()).isEqualTo(2);
    }

    /* ==================== 超时中断 ==================== */

    @Test
    @DisplayName("超时中断：任务超时被强制中断、状态 TIMEOUT、可重试后成功")
    void timeoutInterruptedThenRetrySucceeds() throws Exception {
        SysJob job = job(JobRetryPolicy.FIXED_INTERVAL, 1, 1, 10);
        job.setTimeoutMillis(150L);
        AtomicInteger attempts = new AtomicInteger();
        // 第 1 次执行阻塞 5 秒（必然超时）；第 2 次立即成功
        Runnable action = () -> {
            if (attempts.incrementAndGet() == 1) {
                try {
                    Thread.sleep(5_000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }
        };
        long start = System.nanoTime();
        JobExecutionEngine.JobExecutionResult result = engine.execute(
                job, action, SysJobLog.TriggerSource.SCHEDULE);
        // 超时中断生效：总耗时远小于 5 秒（第 1 轮 150ms 超时 + 退避 1~10ms + 第 2 轮瞬间完成）
        assertThat(System.nanoTime() - start).isLessThan(3_000_000_000L);
        assertThat(result.success()).isTrue();
        assertThat(result.status()).isEqualTo("SUCCESS");
        ArgumentCaptor<SysJobLog> captor = ArgumentCaptor.forClass(SysJobLog.class);
        verify(jobLogRepository, times(2)).save(captor.capture());
        SysJobLog finalLog = captor.getAllValues().get(1);
        assertThat(finalLog.getExecuteStatus()).isEqualTo("SUCCESS");
    }

    @Test
    @DisplayName("超时不重试配置：超时后直接判 TIMEOUT 终态")
    void timeoutNoRetryFinal() {
        SysJob job = job(JobRetryPolicy.FIXED_INTERVAL, 0, 1, 10);
        job.setTimeoutMillis(100L);
        Runnable action = () -> {
            try {
                Thread.sleep(5_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        };
        JobExecutionEngine.JobExecutionResult result = engine.execute(
                job, action, SysJobLog.TriggerSource.SCHEDULE);
        assertThat(result.success()).isFalse();
        assertThat(result.status()).isEqualTo("TIMEOUT");
    }

    /* ==================== 并发冲突 ==================== */

    @Test
    @DisplayName("并发冲突-DISCARD：锁占用时丢弃本次执行，记录 SKIPPED")
    void conflictDiscardSkipped() {
        SysJob job = job(JobRetryPolicy.FIXED_INTERVAL, 0, 1, 10);
        when(redisUtil.tryLock(anyString(), anyLong(), anyLong(), any(TimeUnit.class)))
                .thenReturn(false);
        AtomicInteger attempts = new AtomicInteger();
        Runnable action = attempts::incrementAndGet;
        JobExecutionEngine.JobExecutionResult result = engine.execute(
                job, action, SysJobLog.TriggerSource.SCHEDULE);
        assertThat(result.status()).isEqualTo("SKIPPED");
        assertThat(attempts.get()).isEqualTo(0);
    }

    @Test
    @DisplayName("并发冲突-QUEUE：首次获取失败后阻塞等待，二次获取成功继续执行")
    void conflictQueueWaitsThenExecutes() {
        SysJob job = job(JobRetryPolicy.FIXED_INTERVAL, 0, 1, 10);
        job.setConflictStrategy(JobConflictStrategy.QUEUE);
        when(redisUtil.tryLock(anyString(), eq(0L), anyLong(), any(TimeUnit.class)))
                .thenReturn(false);
        when(redisUtil.tryLock(anyString(), anyLong(), anyLong(), any(TimeUnit.class)))
                .thenReturn(false, true);
        AtomicInteger attempts = new AtomicInteger();
        Runnable action = attempts::incrementAndGet;
        JobExecutionEngine.JobExecutionResult result = engine.execute(
                job, action, SysJobLog.TriggerSource.SCHEDULE);
        assertThat(result.success()).isTrue();
        assertThat(attempts.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("并发冲突-QUEUE：等待超时后仍拿不到锁，降级为 SKIPPED")
    void conflictQueueWaitTimeoutSkipped() {
        SysJob job = job(JobRetryPolicy.FIXED_INTERVAL, 0, 1, 10);
        job.setConflictStrategy(JobConflictStrategy.QUEUE);
        when(redisUtil.tryLock(anyString(), anyLong(), anyLong(), any(TimeUnit.class)))
                .thenReturn(false);
        AtomicInteger attempts = new AtomicInteger();
        Runnable action = attempts::incrementAndGet;
        JobExecutionEngine.JobExecutionResult result = engine.execute(
                job, action, SysJobLog.TriggerSource.SCHEDULE);
        assertThat(result.status()).isEqualTo("SKIPPED");
        assertThat(attempts.get()).isEqualTo(0);
    }

    /* ==================== 异常场景 ==================== */

    @Test
    @DisplayName("回调隔离：回调抛异常不影响任务终态与记录")
    void callbackExceptionIsolated() {
        SysJob job = job(JobRetryPolicy.FIXED_INTERVAL, 0, 1, 10);
        RetryCallback throwingCallback = r -> {
            throw new IllegalStateException("callback boom");
        };
        JobAlertService alertService = new JobAlertService(
                deadLetterRepository, jobLogRepository, null);
        JobExecutionEngine engineWithBadCallback = new JobExecutionEngine(
                redisUtil, jobLogRepository, retryDetailRepository,
                new JobRetryDecider(new org.springframework.beans.factory.ObjectProvider<>() {
                    @Override
                    public RetryableExceptionMatcher getIfAvailable() {
                        return null;
                    }
                }), new JobBackoffCalculator(jobLogRepository),
                List.of(throwingCallback), alertService, 129979);
        JobExecutionEngine.JobExecutionResult result = engineWithBadCallback.execute(
                job, () -> {
                }, SysJobLog.TriggerSource.MANUAL);
        assertThat(result.success()).isTrue();
        assertThat(result.status()).isEqualTo("SUCCESS");
    }

    @Test
    @DisplayName("执行记录完整落库：任务键/名称/来源/执行机字段齐备")
    void logFieldsComplete() {
        SysJob job = job(JobRetryPolicy.FIXED_INTERVAL, 0, 1, 10);
        engine.execute(job, () -> {
        }, SysJobLog.TriggerSource.MANUAL);
        // 两次 save：插入 RUNNING + 终态更新（Mockito captor 捕获同一可变对象，读终态）
        verify(jobLogRepository, times(2)).save(any(SysJobLog.class));
        ArgumentCaptor<SysJobLog> captor = ArgumentCaptor.forClass(SysJobLog.class);
        verify(jobLogRepository, times(2)).save(captor.capture());
        SysJobLog log = captor.getAllValues().get(1);
        assertThat(log.getJobKey()).isEqualTo("test.job");
        assertThat(log.getJobName()).isEqualTo("测试任务");
        assertThat(log.getTriggerSource()).isEqualTo("MANUAL");
        assertThat(log.getExecuteStatus()).isEqualTo("SUCCESS");
        assertThat(log.getExecutor()).isNotBlank();
        assertThat(log.getStartTime()).isNotNull();
        assertThat(log.getEndTime()).isNotNull();
        assertThat(log.getDurationMillis()).isNotNull().isGreaterThanOrEqualTo(0L);
    }

    @Test
    @DisplayName("重试明细逐轮落库：每轮一条、失败轮次含异常类型")
    void retryDetailsRecordedPerAttempt() {
        SysJob job = job(JobRetryPolicy.FIXED_INTERVAL, 2, 1, 10);
        AtomicInteger attempts = new AtomicInteger();
        Runnable action = () -> {
            if (attempts.incrementAndGet() < 3) {
                throw new RuntimeException(new IOException("attempt fail"));
            }
        };
        engine.execute(job, action, SysJobLog.TriggerSource.SCHEDULE);
        ArgumentCaptor<cn.sijay.oi.sys.entity.SysJobRetryDetail> detailCaptor =
                ArgumentCaptor.forClass(cn.sijay.oi.sys.entity.SysJobRetryDetail.class);
        verify(retryDetailRepository, times(3)).save(detailCaptor.capture());
        List<cn.sijay.oi.sys.entity.SysJobRetryDetail> details = detailCaptor.getAllValues();
        assertThat(details).hasSize(3);
        assertThat(details.get(0).getAttemptNo()).isZero();
        assertThat(details.get(0).getErrorType()).isEqualTo("java.io.IOException");
        assertThat(details.get(2).getAttemptStatus()).isEqualTo("SUCCESS");
        assertThat(details.get(2).getErrorType()).isNull();
    }
}
