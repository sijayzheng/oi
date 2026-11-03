package cn.sijay.oi.common.util;

import org.aopalliance.intercept.MethodInterceptor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactoryBean;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@link SpringUtils} 验证用例。
 *
 * <p>通过独立的 {@link AnnotationConfigApplicationContext} 加载 SpringUtils（验证
 * ApplicationContextAware 注入与私有构造器的容器实例化），并使用 {@link ProxyFactoryBean}
 * 构造真实的 AOP 代理，验证代理与原始对象的引用差异；边界场景（上下文未初始化、
 * Bean 不存在、AopContext 未开启）单独覆盖。
 */
@DisplayName("Spring 工具类测试")
class SpringUtilsTest {

    /** 无接口目标类；Spring AOP 对无接口目标自动使用 CGLIB 代理 */
    static class Greeter {
        public String greet() {
            return "raw";
        }
    }

    /** AOP 测试配置：注册被增强的 Greeter 代理 Bean */
    @Configuration
    static class ProxyConfig {

        @Bean
        public ProxyFactoryBean greeter() {
            ProxyFactoryBean factoryBean = new ProxyFactoryBean();
            factoryBean.setTarget(new Greeter());
            factoryBean.addAdvice((MethodInterceptor) invocation -> "proxied:" + invocation.proceed());
            return factoryBean;
        }
    }

    private AnnotationConfigApplicationContext ctx;

    @BeforeEach
    void setUp() {
        ctx = new AnnotationConfigApplicationContext();
        ctx.register(SpringUtils.class);
        ctx.register(ProxyConfig.class);
        ctx.refresh();
    }

    @AfterEach
    void tearDown() {
        ctx.close();
    }

    @Test
    @DisplayName("上下文注入：ApplicationContextAware 自动注入且实例一致")
    void contextInjected() {
        assertThat(SpringUtils.getContext()).isNotNull();
        assertThat(SpringUtils.getContext()).isSameAs(ctx);
    }

    @Test
    @DisplayName("Bean 获取：按类型 / 按名称 / 类型+名称三种重载均返回同一实例")
    void getBeanOverloads() {
        Object byType = SpringUtils.getBean(Greeter.class);
        Object byName = SpringUtils.getBean("greeter");
        Greeter byTypeAndName = SpringUtils.getBean(Greeter.class, "greeter");

        assertThat(byType).isNotNull();
        assertThat(byType).isSameAs(byName);
        assertThat(byType).isSameAs(byTypeAndName);
    }

    @Test
    @DisplayName("AOP 代理：代理对象与原始对象引用不同，且增强逻辑生效")
    void aopProxyDiffersFromRawObject() {
        Greeter raw = new Greeter();
        Greeter proxy = SpringUtils.getBean(Greeter.class);

        // 引用差异：容器中是 CGLIB 代理对象，非原始实例
        assertThat(proxy).isNotSameAs(raw);
        assertThat(proxy.getClass()).isNotEqualTo(Greeter.class);

        // 增强生效：拦截器在原始逻辑前拼接了前缀
        assertThat(proxy.greet()).isEqualTo("proxied:raw");
        assertThat(raw.greet()).isEqualTo("raw");

        // getAopProxy 与 getBean 等价，返回同一代理
        assertThat(SpringUtils.getAopProxy(Greeter.class)).isSameAs(proxy);
    }

    @Test
    @DisplayName("空安全变体：Bean 存在返回实例，不存在返回 null")
    void getBeanOrNull() {
        assertThat(SpringUtils.getBeanOrNull(Greeter.class)).isNotNull();
        assertThat(SpringUtils.getBeanOrNull(String.class)).isNull();
    }

    @Test
    @DisplayName("containsBean：存在返回 true，不存在返回 false")
    void containsBean() {
        assertThat(SpringUtils.containsBean("greeter")).isTrue();
        assertThat(SpringUtils.containsBean("notRegistered")).isFalse();
    }

    @Test
    @DisplayName("getCurrentAopProxy：不在代理调用链内（AopContext 未开启）降级返回 null")
    void getCurrentAopProxyFallback() {
        Object proxy = SpringUtils.getCurrentAopProxy();
        assertThat(proxy).isNull();
    }

    @Test
    @DisplayName("上下文未初始化：getContext 与 getBean 均抛 IllegalStateException")
    void contextNotInitialized() throws Exception {
        Field field = SpringUtils.class.getDeclaredField("context");
        field.setAccessible(true);
        Object original = field.get(null);
        try {
            field.set(null, null);

            assertThatThrownBy(SpringUtils::getContext)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("尚未初始化");
            assertThatThrownBy(() -> SpringUtils.getBean(Greeter.class))
                    .isInstanceOf(IllegalStateException.class);
        } finally {
            field.set(null, original);
        }
    }

    @Test
    @DisplayName("Bean 不存在：getBean 抛 NoSuchBeanDefinitionException")
    void beanNotFound() {
        assertThatThrownBy(() -> SpringUtils.getBean(String.class))
                .isInstanceOf(NoSuchBeanDefinitionException.class);
    }
}
