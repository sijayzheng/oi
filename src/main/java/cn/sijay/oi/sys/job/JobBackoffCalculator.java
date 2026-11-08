package cn.sijay.oi.sys.job;

import cn.sijay.oi.sys.entity.SysJob;
import cn.sijay.oi.sys.entity.SysJobLog;
import cn.sijay.oi.sys.enums.JobExecuteStatus;
import cn.sijay.oi.sys.repository.SysJobLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 重试间隔计算器：按任务重试策略计算第 n 次重试前的等待时长。
 *
 * <p><b>两种标准策略</b>：
 * <ul>
 *   <li>{@code FIXED_INTERVAL}：恒定 {@code retryInitialInterval} 毫秒；</li>
 *   <li>{@code EXPONENTIAL_BACKOFF}：{@code min(initial * 2^n + 抖动, maxInterval)}
 *       ——指数递增封顶，抖动因子打散重试时刻，避免分布式惊群。</li>
 * </ul>
 *
 * <p><b>抖动算法</b>：在计算值的基础上叠加 [0, 25%) 的随机增量——
 * 同批失败任务的等待时长彼此错开，重试风暴被自然摊薄。
 *
 * @author sijay
 */
@Component
public class JobBackoffCalculator {

    /**
     * 抖动比例上限；实际抖动 ∈ [0, base × JITTER_RATIO)。
     */
    private static final double JITTER_RATIO = 0.25;

    private final SysJobLogRepository jobLogRepository;

    public JobBackoffCalculator(SysJobLogRepository jobLogRepository) {
        this.jobLogRepository = jobLogRepository;
    }

    /**
     * 计算第 attemptNo 次重试前的等待时长。
     *
     * @param job       任务配置
     * @param attemptNo 即将执行的重试轮次（1=第一次重试）
     * @return 等待时长
     */
    public Duration compute(SysJob job, int attemptNo) {
        long initial = Math.max(0, job.getRetryInitialInterval());
        long max = Math.max(initial, job.getRetryMaxInterval());
        long base = switch (job.getRetryPolicy()) {
            case FIXED_INTERVAL -> initial;
            case EXPONENTIAL_BACKOFF -> {
                // 2^n 指数递增，防止溢出：>62 轮后封顶
                long exp = initial;
                for (int i = 1; i < attemptNo && exp < max; i++) {
                    exp = exp << 1;
                    if (exp <= 0) {
                        exp = max;
                        break;
                    }
                }
                yield Math.min(exp, max);
            }
        };
        // 抖动因子：[0, 25%) 随机增量
        long jitter = ThreadLocalRandom.current().nextLong((long) (base * JITTER_RATIO) + 1);
        return Duration.ofMillis(Math.min(base + jitter, max));
    }

    /**
     * 查询任务执行记录（供告警服务复用）。
     *
     * @param jobKey 任务业务键
     * @param limit  最近条数
     * @return 最近执行记录（按开始时间倒序）
     */
    public List<SysJobLog> recentLogs(String jobKey, int limit) {
        return jobLogRepository.findRecent(jobKey,
                org.springframework.data.domain.PageRequest.of(0, limit));
    }

    /**
     * 判定执行记录是否计入"失败"口径（连续失败告警统计用）。
     *
     * @param status 状态名
     * @return FAILED/TIMEOUT/DEAD 计为失败
     */
    public static boolean isFailureStatus(String status) {
        return JobExecuteStatus.FAILED.name().equals(status)
                || JobExecuteStatus.TIMEOUT.name().equals(status)
                || JobExecuteStatus.DEAD.name().equals(status);
    }
}
