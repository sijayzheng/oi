package cn.sijay.oi.common.util;

import org.jspecify.annotations.Nullable;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * Spring 上下文工具类，提供 ApplicationContext 的静态访问、多种方式的 Bean 获取
 * 以及 AOP 代理对象获取（解决自调用无法触发切面的问题）。
 *
 * <p>本类通过实现 {@link ApplicationContextAware} 由 Spring 容器在启动时自动注入上下文，
 * 并以 {@code volatile} 静态字段线程安全地持有；业务代码静态导入后即可在
 * 非 Spring 托管对象（如工具方法、静态上下文）中访问容器。
 *
 * <p>使用示例：
 * <pre>{@code
 * import static cn.sijay.oi.common.util.SpringUtils.*;
 *
 * ApplicationContext ctx = getContext();                  // 获取上下文
 * UserService userService = getBean(UserService.class);   // 按类型获取
 * Object bean = getBean("userService");                   // 按名称获取
 * UserService named = getBean(UserService.class, "userService"); // 类型+名称
 *
 * // 自调用触发切面：改为从容器获取代理对象后调用
 * public void batchSave() {
 *     getAopProxy(UserService.class).saveWithLog(data);   // 切面正常生效
 * }
 * }</pre>
 *
 * <p><b>边界场景处理</b>：
 * <ul>
 *   <li><b>上下文未初始化</b>（容器未启动完成即调用，或在非 Spring 环境直接调用）：
 *       {@link #getContext()} 及所有依赖上下文的方法抛出 {@link IllegalStateException}，
 *       消息明确提示初始化时序问题；</li>
 *   <li><b>Bean 不存在</b>：{@code getBean} 系列按 Spring 原生语义抛出
 *       {@link NoSuchBeanDefinitionException}；若"不存在"属合法业务状态，
 *       请使用 {@link #getBeanOrNull(Class)} 返回 null；</li>
 *   <li><b>Bean 存在多个同类型实例</b>：按类型获取抛出 NoUniqueBeanDefinitionException，
 *       此时须用"类型 + 名称"组合获取；</li>
 *   <li><b>AopContext 未开启</b>（未配置 exposeProxy = true）：{@link #getCurrentAopProxy()}
 *       返回 null 而非抛异常，请改用 {@link #getAopProxy(Class)}（始终可用）。</li>
 * </ul>
 *
 * <p>适用环境：Spring Boot / Spring MVC / 任意基于 Spring IoC 的应用，仅依赖
 * spring-context 与 spring-aop（Web 应用默认传递引入），无额外依赖要求。
 *
 * @author sijay
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public final class SpringUtils implements ApplicationContextAware {

    /**
     * 应用上下文静态引用；volatile 保证多线程可见性（容器刷新线程写入，业务线程读取）。
     * 注意：容器关闭后本引用仍指向已关闭的上下文，此时调用 getBean 会抛
     * IllegalStateException（Spring 原生行为），属预期边界场景。
     */
    private static volatile ApplicationContext context;

    /**
     * 私有构造方法；本类作为 Spring Bean 由容器反射实例化（用于接收上下文回调），
     * 业务代码禁止手动实例化，一律通过静态方法使用。
     */
    private SpringUtils() {
    }

    /**
     * Spring 容器回调：启动时自动注入应用上下文。
     *
     * <p>由容器在 Bean 初始化阶段调用，业务代码不应手动调用。
     *
     * @param applicationContext 容器应用上下文；由 Spring 保证非 null
     * @throws BeansException 注入过程中发生容器错误时抛出（透传 Spring 原生异常）
     */
    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        SpringUtils.context = applicationContext;
    }

    /**
     * 获取应用上下文实例。
     *
     * <p>使用场景：需要 ApplicationContext 原生能力（事件发布、资源加载、环境属性）时。
     *
     * @return 应用上下文
     * @throws IllegalStateException 上下文尚未初始化（容器未启动完成或非 Spring 环境）
     */
    public static ApplicationContext getContext() {
        ApplicationContext current = context;
        if (current == null) {
            throw new IllegalStateException("Spring 应用上下文尚未初始化：请确认容器已启动，或当前代码运行于非 Spring 环境");
        }
        return current;
    }

    /**
     * 按类型获取 Bean（静态代理目标：容器中注册的即代理对象）。
     *
     * <p>使用场景：类型唯一时最简获取方式。
     *
     * @param type Bean 类型
     * @param <T>  Bean 类型
     * @return Bean 实例（若被 AOP 增强，返回的是代理对象）
     * @throws IllegalStateException              上下文尚未初始化
     * @throws NoSuchBeanDefinitionException      不存在该类型的 Bean
     * @throws org.springframework.beans.factory.NoUniqueBeanDefinitionException 存在多个该类型的 Bean（请改用"类型+名称"组合）
     */
    public static <T> T getBean(Class<T> type) {
        return getContext().getBean(type);
    }

    /**
     * 按名称获取 Bean。
     *
     * <p>使用场景：仅持有 Bean 名称（如配置项、参数传入）时的动态获取。
     *
     * @param name Bean 名称；须与容器注册名完全一致
     * @return Bean 实例；调用方自行确认具体类型
     * @throws IllegalStateException         上下文尚未初始化
     * @throws NoSuchBeanDefinitionException 不存在该名称的 Bean
     */
    public static Object getBean(String name) {
        return getContext().getBean(name);
    }

    /**
     * 按"类型 + 名称"组合获取 Bean。
     *
     * <p>使用场景：同一类型存在多个实例（如多数据源、多队列消费者）时精确定位。
     *
     * @param type Bean 类型
     * @param name Bean 名称
     * @param <T>  Bean 类型
     * @return Bean 实例；名称存在但类型不匹配时抛 BeansException
     * @throws IllegalStateException         上下文尚未初始化
     * @throws NoSuchBeanDefinitionException 不存在该名称的 Bean
     */
    public static <T> T getBean(Class<T> type, String name) {
        return getContext().getBean(name, type);
    }

    /**
     * 按类型获取 Bean 的空安全变体（Bean 不存在时返回 null 而非抛异常）。
     *
     * <p>使用场景：Bean 是否注册属于合法的业务分支（如可选组件、条件装配的结果），
     * 调用方以 null 判断决定降级逻辑。
     *
     * @param type Bean 类型
     * @param <T>  Bean 类型
     * @return Bean 实例；不存在时返回 null（存在多个同类型实例时同样返回 null，因无法确定唯一实例）
     * @throws IllegalStateException 上下文尚未初始化
     */
    @Nullable
    public static <T> T getBeanOrNull(Class<T> type) {
        try {
            return getContext().getBean(type);
        } catch (NoSuchBeanDefinitionException e) {
            // NoUniqueBeanDefinitionException 是本异常的子类，一并在此降级为 null
            return null;
        }
    }

    /**
     * 判断指定名称的 Bean 是否存在于容器中。
     *
     * <p>使用场景：获取前的存在性预检，避免依赖异常控制流程。
     *
     * @param name Bean 名称
     * @return 存在返回 true；上下文未初始化时抛 IllegalStateException
     */
    public static boolean containsBean(String name) {
        return getContext().containsBean(name);
    }

    /**
     * 获取指定类型 Bean 的 AOP 代理对象（自调用触发切面的通用兼容方案，始终可用）。
     *
     * <p>原理：容器中注册的 Bean 本身就是代理对象（被切面增强后），直接从容器重新获取
     * 即可绕过 {@code this} 自调用；不依赖 {@code AopContext}，无需额外配置。
     *
     * <p>使用场景：类内部方法自调用导致 @Transactional、自定义切面失效时——
     * <pre>{@code
     * // 失效写法：this.saveWithLog(data)，切面不生效
     * // 正确写法：
     * getAopProxy(UserService.class).saveWithLog(data);
     * }</pre>
     *
     * @param type Bean 类型（须与切面增强的目标类型一致）
     * @param <T>  Bean 类型
     * @return 容器中的代理对象；未被切面增强时即为原始对象（等价 getBean(type)）
     * @throws IllegalStateException         上下文尚未初始化
     * @throws NoSuchBeanDefinitionException 不存在该类型的 Bean
     */
    public static <T> T getAopProxy(Class<T> type) {
        return getContext().getBean(type);
    }

    /**
     * 获取当前调用链上的 AOP 代理对象（整合 {@link AopContext#currentProxy()}）。
     *
     * <p>原理与限制：仅当目标方法<b>正在被代理调用</b>且容器开启了
     * {@code @EnableAspectJAutoProxy(exposeProxy = true)}（或等效配置
     * {@code spring.aop.proxy-target-class} 配合 exposeProxy）时可用；
     * 否则 {@link AopContext#currentProxy()} 抛 IllegalStateException，
     * 本方法将其降级为返回 null——调用方应判空并回退到 {@link #getAopProxy(Class)}。
     *
     * <p>使用场景：切面逻辑内部需要拿到当前代理继续调用后续方法时。
     *
     * @param <T> 代理类型；由调用处按目标类型推断（运行时不做类型校验）
     * @return 当前代理对象；AopContext 未开启或不在代理调用链内时返回 null
     */
    @Nullable
    @SuppressWarnings("unchecked")
    public static <T> T getCurrentAopProxy() {
        try {
            return (T) AopContext.currentProxy();
        } catch (IllegalStateException e) {
            return null;
        }
    }
}
