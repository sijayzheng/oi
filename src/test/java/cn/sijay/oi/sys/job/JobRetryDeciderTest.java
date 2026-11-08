package cn.sijay.oi.sys.job;

import cn.sijay.oi.sys.entity.SysJob;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.SocketTimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 重试判定器单元测试（纯内存逻辑，无 Spring 上下文）。
 *
 * <p>覆盖场景：黑名单优先、白名单命中、通配模式、因果链根因匹配、
 * 默认匹配器（瞬态异常可重试 / 业务异常不可重试）。
 *
 * @author sijay
 */
@DisplayName("重试判定器测试")
class JobRetryDeciderTest {

    private final JobRetryDecider decider = new JobRetryDecider(emptyProvider());

    /**
     * 空提供器（无自定义匹配器，走 DEFAULT 兜底）。
     */
    private static org.springframework.beans.factory.ObjectProvider<RetryableExceptionMatcher> emptyProvider() {
        return providerOf(null);
    }

    /**
     * 固定返回值的提供器。
     */
    private static org.springframework.beans.factory.ObjectProvider<RetryableExceptionMatcher> providerOf(
            RetryableExceptionMatcher matcher) {
        return new org.springframework.beans.factory.ObjectProvider<>() {
            @Override
            public RetryableExceptionMatcher getObject() {
                if (matcher == null) {
                    throw new UnsupportedOperationException("no matcher");
                }
                return matcher;
            }

            @Override
            public RetryableExceptionMatcher getIfAvailable() {
                return matcher;
            }
        };
    }

    /**
     * 构造带白名单/黑名单的任务配置。
     */
    private SysJob job(String include, String exclude) {
        SysJob job = new SysJob();
        job.setRetryInclude(include);
        job.setRetryExclude(exclude);
        return job;
    }

    @Test
    @DisplayName("黑名单命中：直接不可重试（优先级最高）")
    void blacklistWins() {
        SysJob job = job("java.lang.Exception", "java.io.IOException");
        assertThat(decider.isRetryable(job, new IOException("io"))).isFalse();
    }

    @Test
    @DisplayName("白名单命中：可重试")
    void whitelistMatches() {
        SysJob job = job("java.io.IOException", null);
        assertThat(decider.isRetryable(job, new IOException("io"))).isTrue();
    }

    @Test
    @DisplayName("通配模式：前缀段匹配（java.net.*）")
    void wildcardPatternMatches() {
        SysJob job = job("java.net.*", null);
        // 白名单通配命中
        assertThat(decider.isRetryable(job, new SocketTimeoutException("timeout"))).isTrue();
        // IOException 未命中白名单，但默认匹配器判定瞬态异常可重试（兜底生效）
        assertThat(decider.isRetryable(job, new IOException("io"))).isTrue();
        // 非瞬态、未命中白名单：不可重试
        assertThat(decider.isRetryable(job, new IllegalStateException("state"))).isFalse();
    }

    @Test
    @DisplayName("因果链：包装异常按根因判定")
    void causeChainUnwrapped() {
        SysJob job = job("java.io.IOException", null);
        RuntimeException wrapped = new RuntimeException("wrapped", new IOException("root"));
        assertThat(decider.isRetryable(job, wrapped)).isTrue();
    }

    @Test
    @DisplayName("表达式均未命中：默认匹配器——瞬态异常可重试")
    void defaultMatcherTransientRetryable() {
        SysJob job = job(null, null);
        assertThat(decider.isRetryable(job, new IOException("io"))).isTrue();
        assertThat(decider.isRetryable(job, new SocketTimeoutException("timeout"))).isTrue();
    }

    @Test
    @DisplayName("表达式均未命中：默认匹配器——业务异常不可重试")
    void defaultMatcherBusinessNotRetryable() {
        SysJob job = job(null, null);
        assertThat(decider.isRetryable(job, new IllegalArgumentException("bad arg"))).isFalse();
        assertThat(decider.isRetryable(job, new NullPointerException("npe"))).isFalse();
    }

    @Test
    @DisplayName("自定义匹配器接入：替换默认判定")
    void customMatcherOverrides() {
        JobRetryDecider custom = new JobRetryDecider(providerOf(error -> error instanceof IllegalArgumentException));
        SysJob job = job(null, null);
        assertThat(custom.isRetryable(job, new IllegalArgumentException("retry me"))).isTrue();
        assertThat(custom.isRetryable(job, new IOException("io"))).isFalse();
    }
}
