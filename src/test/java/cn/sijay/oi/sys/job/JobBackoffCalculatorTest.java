package cn.sijay.oi.sys.job;

import cn.sijay.oi.sys.entity.SysJob;
import cn.sijay.oi.sys.enums.JobRetryPolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 重试间隔计算器单元测试（纯内存逻辑，无 Spring 上下文）。
 *
 * <p>覆盖场景：固定间隔策略、指数退避递增与封顶、抖动上下界、封顶值不小于初始值。
 *
 * @author sijay
 */
@DisplayName("重试间隔计算器测试")
class JobBackoffCalculatorTest {

    private final JobBackoffCalculator calculator = new JobBackoffCalculator(null);

    /**
     * 构造任务配置。
     */
    private SysJob job(JobRetryPolicy policy, long initial, long max) {
        SysJob job = new SysJob();
        job.setRetryPolicy(policy);
        job.setRetryInitialInterval(initial);
        job.setRetryMaxInterval(max);
        return job;
    }

    @Test
    @DisplayName("固定间隔策略：等待时长恒等于初始间隔（抖动仅小幅上浮）")
    void fixedIntervalStable() {
        SysJob job = job(JobRetryPolicy.FIXED_INTERVAL, 1000, 60_000);
        for (int attempt = 1; attempt <= 3; attempt++) {
            Duration d = calculator.compute(job, attempt);
            assertThat(d.toMillis()).isBetween(1000L, 1250L);
        }
    }

    @Test
    @DisplayName("指数退避：按 2 的幂递增")
    void exponentialBackoffDoubles() {
        SysJob job = job(JobRetryPolicy.EXPONENTIAL_BACKOFF, 1000, 600_000);
        long first = calculator.compute(job, 1).toMillis();
        long third = calculator.compute(job, 3).toMillis();
        // 第 1 轮基数 1000（+抖动 [0,250)）；第 3 轮基数 4000（+抖动 [0,1000)）
        assertThat(first).isBetween(1000L, 1250L);
        assertThat(third).isBetween(4000L, 5000L);
    }

    @Test
    @DisplayName("指数退避：达到最大间隔后封顶")
    void exponentialBackoffCapped() {
        SysJob job = job(JobRetryPolicy.EXPONENTIAL_BACKOFF, 1000, 4000);
        // 第 10 轮已远超封顶，基数应停在 4000（抖动上限 1000，总值不超 5000）
        long late = calculator.compute(job, 10).toMillis();
        assertThat(late).isLessThanOrEqualTo(5000L);
    }

    @Test
    @DisplayName("抖动因子：分布落点存在差异（打散惊群）")
    void jitterRandomizes() {
        SysJob job = job(JobRetryPolicy.EXPONENTIAL_BACKOFF, 10_000, 100_000);
        boolean anyDifference = false;
        long previous = -1;
        for (int i = 0; i < 20 && !anyDifference; i++) {
            long current = calculator.compute(job, 1).toMillis();
            if (previous >= 0 && current != previous) {
                anyDifference = true;
            }
            previous = current;
        }
        assertThat(anyDifference).isTrue();
    }

    @Test
    @DisplayName("max < initial 的异常配置：计算时自动取 max 兜底为上限")
    void maxLowerThanInitialSafelyCapped() {
        SysJob job = job(JobRetryPolicy.EXPONENTIAL_BACKOFF, 5000, 1000);
        // 计算器防御：max=max(initial, retryMaxInterval)=5000，不崩溃不越界
        long value = calculator.compute(job, 1).toMillis();
        assertThat(value).isBetween(5000L, 6250L);
    }
}
