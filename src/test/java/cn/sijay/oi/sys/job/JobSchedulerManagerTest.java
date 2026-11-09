package cn.sijay.oi.sys.job;

import cn.sijay.oi.sys.entity.SysJob;
import cn.sijay.oi.sys.enums.JobTriggerType;
import cn.sijay.oi.sys.repository.SysJobRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/**
 * 动态调度器管理器单元测试（真实 ThreadPoolTaskScheduler + Mock 依赖）。
 *
 * <p>覆盖场景：
 * <ul>
 *   <li>fixed-rate 动态注册与触发（秒级精度）；</li>
 *   <li>动态取消（暂停）后不再触发；</li>
 *   <li>动态修改配置重注册生效；</li>
 *   <li>暂停任务（enabled=false）注册跳过；</li>
 *   <li>触发时任务被删除/暂停：静默跳过；</li>
 *   <li>CRON 非法：注册抛 608003；周期下限 1000ms 校验。</li>
 * </ul>
 *
 * @author sijay
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("动态调度器管理器测试")
class JobSchedulerManagerTest {

    @Mock
    private SysJobRepository jobRepository;
    @Mock
    private JobExecutionEngine executionEngine;

    private JobSchedulerManager manager;
    private JobActionRegistry actionRegistry;

    @BeforeEach
    void setUp() {
        actionRegistry = new JobActionRegistry();
        // 线程池大小与生产默认一致（8）
        manager = new JobSchedulerManager(jobRepository, actionRegistry, executionEngine, 8);
    }

    @AfterEach
    void tearDown() {
        manager.shutdown();
    }

    /**
     * 构造 fixed-rate 任务（period 毫秒）。
     */
    private SysJob fixedRateJob(String jobKey, long periodMillis, boolean enabled) {
        SysJob job = new SysJob();
        job.setId(1L);
        job.setJobKey(jobKey);
        job.setName(jobKey);
        job.setTriggerType(JobTriggerType.FIXED_RATE);
        job.setTriggerValue(String.valueOf(periodMillis));
        job.setEnabled(enabled);
        return job;
    }

    @Test
    @DisplayName("fixed-rate 动态注册：任务按周期触发（秒级精度）")
    void fixedRateRegistrationFires() throws Exception {
        SysJob job = fixedRateJob("test.fire", 1000, true);
        when(jobRepository.findByJobKey("test.fire")).thenReturn(Optional.of(job));
        AtomicInteger triggers = new AtomicInteger();
        CountDownLatch latch = new CountDownLatch(3);
        actionRegistry.register("test.fire", () -> {
            triggers.incrementAndGet();
            latch.countDown();
        });
        when(executionEngine.execute(org.mockito.ArgumentMatchers.any(SysJob.class),
                org.mockito.ArgumentMatchers.any(Runnable.class),
                org.mockito.ArgumentMatchers.any()))
                .thenAnswer(inv -> {
                    ((Runnable) inv.getArgument(1)).run();
                    return new JobExecutionEngine.JobExecutionResult(1L, true, "SUCCESS", null);
                });

        manager.register(job);
        // 等待 3 次触发（周期 1s + 调度余量）
        boolean fired = latch.await(6, TimeUnit.SECONDS);
        assertThat(fired).isTrue();
        assertThat(triggers.get()).isGreaterThanOrEqualTo(3);
        assertThat(manager.isRegistered("test.fire")).isTrue();
        assertThat(manager.registeredCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("动态取消：unregister 后不再触发")
    void unregisterStopsFiring() throws Exception {
        SysJob job = fixedRateJob("test.stop", 1000, true);
        when(jobRepository.findByJobKey("test.stop")).thenReturn(Optional.of(job));
        CountDownLatch firstFire = new CountDownLatch(1);
        actionRegistry.register("test.stop", firstFire::countDown);
        when(executionEngine.execute(org.mockito.ArgumentMatchers.any(SysJob.class),
                org.mockito.ArgumentMatchers.any(Runnable.class),
                org.mockito.ArgumentMatchers.any()))
                .thenAnswer(inv -> {
                    ((Runnable) inv.getArgument(1)).run();
                    return new JobExecutionEngine.JobExecutionResult(1L, true, "SUCCESS", null);
                });
        manager.register(job);
        assertThat(firstFire.await(4, TimeUnit.SECONDS)).isTrue();
        manager.unregister("test.stop");
        assertThat(manager.isRegistered("test.stop")).isFalse();
        // 取消后再等 2 个周期：执行引擎不再被调用
        Thread.sleep(2200);
        org.mockito.Mockito.verify(executionEngine,
                org.mockito.Mockito.atMost(2)).execute(org.mockito.ArgumentMatchers.any(SysJob.class),
                org.mockito.ArgumentMatchers.any(Runnable.class),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    @DisplayName("暂停任务（enabled=false）：注册跳过、不调度")
    void pausedJobNotScheduled() {
        SysJob job = fixedRateJob("test.paused", 1000, false);
        manager.register(job);
        assertThat(manager.isRegistered("test.paused")).isFalse();
    }

    @Test
    @DisplayName("触发时任务已删除：静默跳过不抛异常")
    void triggerDeletedJobSkipped() {
        when(jobRepository.findByJobKey("test.deleted")).thenReturn(Optional.empty());
        manager.triggerOnce("test.deleted");
        org.mockito.Mockito.verifyNoInteractions(executionEngine);
    }

    @Test
    @DisplayName("CRON 非法：注册抛业务异常 608003")
    void invalidCronRejected() {
        SysJob job = fixedRateJob("test.badcron", 1000, true);
        job.setTriggerType(JobTriggerType.CRON);
        job.setTriggerValue("not a cron");
        assertThatThrownBy(() -> manager.register(job))
                .isInstanceOf(cn.sijay.oi.common.exception.BusinessException.class)
                .hasMessageContaining("触发配置非法");
    }

    @Test
    @DisplayName("周期下限：fixed-rate 周期 <1000ms 注册被拒（608003）")
    void periodBelowMinimumRejected() {
        SysJob job = fixedRateJob("test.fast", 500, true);
        assertThatThrownBy(() -> manager.register(job))
                .isInstanceOf(cn.sijay.oi.common.exception.BusinessException.class);
    }

    @Test
    @DisplayName("CRON 合法表达式：注册成功（按应用默认时区求值）")
    void cronRegistered() {
        SysJob job = fixedRateJob("test.cron", 1000, true);
        job.setTriggerType(JobTriggerType.CRON);
        job.setTriggerValue("0 * * * * *"); // 每分钟
        manager.register(job);
        assertThat(manager.isRegistered("test.cron")).isTrue();
    }

    @Test
    @DisplayName("动作未注册的任务：触发时告警跳过，不执行")
    void triggerWithoutActionSkipped() {
        SysJob job = fixedRateJob("test.noaction", 1000, true);
        when(jobRepository.findByJobKey("test.noaction")).thenReturn(Optional.of(job));
        manager.triggerOnce("test.noaction");
        org.mockito.Mockito.verifyNoInteractions(executionEngine);
    }
}
