package cn.sijay.oi.sys.job;

import cn.sijay.oi.sys.entity.SysJob;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;

/**
 * 重试判定器：求值任务级白名单/黑名单表达式，未命中时回退默认匹配器。
 *
 * <p><b>判定优先级</b>（规范要求"白名单/黑名单判定规则"）：
 * <ol>
 *   <li><b>黑名单</b>（retry_exclude）命中 → 不可重试；</li>
 *   <li><b>白名单</b>（retry_include）命中 → 可重试；</li>
 *   <li>两级表达式均未命中 → 业务自定义 {@link RetryableExceptionMatcher}
 *       （若注册）或 {@link RetryableExceptionMatcher#DEFAULT} 判定。</li>
 * </ol>
 *
 * <p><b>表达式语法</b>：逗号分隔的类型全限定名模式，支持 {@code *} 尾通配——
 * {@code java.io.IOException}（精确）、{@code java.net.*}（前缀段通配）。
 * 沿异常因果链逐层匹配（包装异常按根因判定）。
 *
 * @author sijay
 */
@Component
public class JobRetryDecider {

    /**
     * 注入的可重试异常匹配器；未注册自定义 Bean 时为 null（走 DEFAULT）。
     */
    private final RetryableExceptionMatcher customMatcher;

    /**
     * 构造注入；{@link ObjectProvider} 使自定义匹配器为<b>可选依赖</b>——
     * 业务方注册即生效，未注册时回退 {@link RetryableExceptionMatcher#DEFAULT}。
     *
     * @param customMatcherProvider 自定义匹配器提供器
     */
    public JobRetryDecider(ObjectProvider<RetryableExceptionMatcher> customMatcherProvider) {
        this.customMatcher = customMatcherProvider.getIfAvailable();
    }

    /**
     * 判定异常是否进入重试流程。
     *
     * @param job   任务配置（承载白名单/黑名单表达式）
     * @param error 执行异常
     * @return true=可重试
     */
    public boolean isRetryable(SysJob job, Throwable error) {
        // 1. 黑名单优先：命中直接判失败
        if (matchesAnyPattern(job.getRetryExclude(), error)) {
            return false;
        }
        // 2. 白名单：命中即重试
        if (matchesAnyPattern(job.getRetryInclude(), error)) {
            return true;
        }
        // 3. 表达未命中：回退匹配器（自定义 Bean 或默认瞬态类型判定）
        RetryableExceptionMatcher matcher = customMatcher == null
                ? RetryableExceptionMatcher.DEFAULT : customMatcher;
        return matcher.matches(error);
    }

    /**
     * 求值模式列表是否命中异常（沿因果链逐层匹配）。
     *
     * @param patterns 逗号分隔的类型模式串；空表示无规则（恒不命中）
     * @param error   异常
     * @return 任一层因果链上的异常类型命中任一模式返回 true
     */
    private boolean matchesAnyPattern(String patterns, Throwable error) {
        if (patterns == null || patterns.isBlank()) {
            return false;
        }
        List<String> normalized = Arrays.stream(patterns.split(","))
                .map(String::trim)
                .filter(p -> !p.isEmpty())
                .toList();
        if (normalized.isEmpty()) {
            return false;
        }
        Throwable current = error;
        while (current != null) {
            for (String pattern : normalized) {
                if (matchesPattern(pattern, current.getClass().getName())) {
                    return true;
                }
            }
            current = current.getCause() == current ? null : current.getCause();
        }
        return false;
    }

    /**
     * 单模式匹配：{@code *} 尾通配（前缀匹配），其余精确匹配。
     *
     * @param pattern   类型模式
     * @param className 异常类型全限定名
     * @return 匹配返回 true
     */
    private boolean matchesPattern(String pattern, String className) {
        if (pattern.endsWith("*")) {
            String prefix = pattern.substring(0, pattern.length() - 1);
            return className.startsWith(prefix);
        }
        return pattern.equals(className);
    }
}
