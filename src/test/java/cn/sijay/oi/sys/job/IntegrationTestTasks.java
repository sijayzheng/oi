package cn.sijay.oi.sys.job;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * 任务调度集成测试样本任务（@OiScheduled 低侵入接入的真实演示）。
 *
 * <p>两个任务专供 {@link JobIntegrationTest} 使用：
 * <ul>
 *   <li>{@link #alwaysSuccess}：立即成功，验证调度触发与执行记录；</li>
 *   <li>{@link #alwaysFail}：恒定抛瞬态异常，验证重试与死信全链路
 *       （maxRetry=1、间隔 100ms，两次尝试后转死信）。</li>
 * </ul>
 *
 * <p><b>上下文隔离</b>：本类为 {@code @TestConfiguration}（Spring Boot 测试的
 * TypeExcludeFilter 会将其排除出常规组件扫描），仅由 {@link JobIntegrationTest}
 * 通过 {@code @Import} 显式引入。若注册为普通 {@code @Component}，任何
 * {@code @SpringBootTest}（站内信、用户等模块测试）都会加载本类——
 * it.job.fail 定时触发死信并发送站内信告警，污染这些测试的数据断言。
 *
 * @author sijay
 */
@TestConfiguration
public class IntegrationTestTasks {

    /**
     * 样本任务 Bean（@OiScheduled 注解扫描基于 Bean 实例）。
     */
    @Bean
    IntegrationTestTasks integrationTestTasks() {
        return new IntegrationTestTasks();
    }

    /**
     * 成功样本任务业务键。
     */
    static final String ALWAYS_SUCCESS_KEY = "it.job.success";

    /**
     * 失败样本任务业务键（瞬态异常，重试耗尽转死信）。
     */
    static final String ALWAYS_FAIL_KEY = "it.job.fail";

    /**
     * 立即成功的样本任务（周期 2 秒）。
     */
    @OiScheduled(jobKey = ALWAYS_SUCCESS_KEY, name = "集成测试-成功任务",
            fixedRate = 2000, timeoutMillis = 5000)
    public void alwaysSuccess() {
        // 无业务动作，立即返回成功
    }

    /**
     * 恒定失败的样本任务（周期 3 秒，重试 1 次、间隔 100ms）。
     *
     * <p>抛 IOException（默认匹配器判定为瞬态可重试），
     * 重试耗尽后转死信，供死信链路用例消费。
     */
    @OiScheduled(jobKey = ALWAYS_FAIL_KEY, name = "集成测试-失败任务",
            fixedRate = 3000, timeoutMillis = 1000,
            maxRetryCount = 1, retryInitialInterval = 100, retryMaxInterval = 1000)
    public void alwaysFail() {
        throw new RuntimeException(new java.io.IOException("集成测试模拟瞬态失败"));
    }

    /**
     * 慢任务执行体（2.5 秒；并发冲突用例动态注册）。
     */
    static void sleepSlow() {
        try {
            Thread.sleep(2500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
