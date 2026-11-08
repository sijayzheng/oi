package cn.sijay.oi.sys.job;

import java.util.List;

/**
 * 可重试异常匹配器 SPI；判定执行异常是否属于"可重试"范围，
 * 决定失败后是否进入重试流程。
 *
 * <p><b>双判定规则</b>（规范要求"表达式或自定义类"两种扩展方式）：
 * <ul>
 *   <li><b>表达式规则</b>：任务级白名单/黑名单（{@code sys_job} 的
 *       retry_include/retry_exclude，类型全限定名模式，支持 {@code *} 尾通配），
 *       由 {@code JobRetryDecider} 求值；黑名单优先于白名单；</li>
 *   <li><b>自定义类规则</b>：业务方实现本接口并注册为 Spring Bean，
 *       替换默认判定逻辑（如按异常 code、HTTP 状态段判定）。
 *       未注册自定义 Bean 时使用 {@link #DEFAULT}。</li>
 * </ul>
 *
 * <p>实现示例（自定义类接入）：
 * <pre>{@code
 * @Component
 * public class HttpRetryableMatcher implements RetryableExceptionMatcher {
 *     public boolean matches(Throwable error) {
 *         return error instanceof HttpTimeoutException;
 *     }
 * }
 * }</pre>
 *
 * @author sijay
 */
@FunctionalInterface
public interface RetryableExceptionMatcher {

    /**
     * 判定异常是否可重试。
     *
     * <p>调用约定：框架已按任务配置完成白名单/黑名单表达式判定
     * （黑名单命中→不可重试，白名单命中→可重试），仅在两级表达式均未命中时
     * 调用本接口做默认判定。
     *
     * @param error 执行中抛出的异常
     * @return true=进入重试流程；false=直接判失败（不重试）
     */
    boolean matches(Throwable error);

    /**
     * 默认匹配器；按瞬态异常类型判定——IO/超时/连接/数据库并发竞争类可重试，
     * 其余（业务规则异常、参数非法等）不可重试。
     */
    RetryableExceptionMatcher DEFAULT = error -> isRetryableType(error);

    private static boolean isRetryableType(Throwable error) {
        // 沿因果链找到根因判定（瞬态异常往往被包装传播）
        Throwable current = error;
        while (current.getCause() != null && current.getCause() != current) {
            if (matchesKnownTransient(current)) {
                return true;
            }
            current = current.getCause();
        }
        return matchesKnownTransient(current);
    }

    private static boolean matchesKnownTransient(Throwable error) {
        return error instanceof java.io.IOException
                || error instanceof java.util.concurrent.TimeoutException
                || error instanceof org.springframework.dao.TransientDataAccessException
                || error instanceof org.springframework.dao.ConcurrencyFailureException;
    }
}
