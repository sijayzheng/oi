package cn.sijay.oi.sys.job;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Oi 任务调度接入注解；标注在 Spring Bean 的方法上，声明该方法为受管调度任务。
 *
 * <p><b>低侵入接入</b>：业务侧仅需在方法上添加本注解，无需实现任何接口、
 * 无需修改原有业务逻辑、无需感知调度框架。模块启动时扫描全部
 * {@code @OiScheduled} 方法，按 {@link #jobKey()} 关联 {@code sys_job} 表中的
 * 任务配置（触发计划/重试/超时/并发策略均以库中配置为准，注解仅声明任务身份），
 * 注册到动态调度器。
 *
 * <p><b>配置来源约定</b>：
 * <ul>
 *   <li>注解仅承载<b>任务身份</b>（jobKey/name）与<b>默认调度参数</b>（首次注册
 *       时的初始配置）；</li>
 *   <li>库中已有同 jobKey 记录时以库为准（运维在线修改即时生效）；</li>
 *   <li>库中不存在时按注解默认参数自动建档并启用。</li>
 * </ul>
 *
 * <p>使用示例：
 * <pre>{@code
 * @Component
 * public class ReportTask {
 *
 *     @OiScheduled(jobKey = "report.daily", name = "日报生成",
 *                  cron = "0 0 2 * * *")
 *     public void generateDaily() {
 *         // 原有业务逻辑，无需任何调度相关代码
 *     }
 * }
 * }</pre>
 *
 * <p><b>方法约束</b>：无参方法（带参方法注册失败并告警，避免隐式依赖注入歧义）；
 * 方法抛出的异常由框架统一捕获并按重试策略处理，业务方无需自行 try-catch。
 *
 * @author sijay
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface OiScheduled {

    /**
     * 任务业务键；全局唯一，创建后禁止修改。
     *
     * <p>命名规范：点分小写风格（如 report.daily）；与 {@code sys_job.job_key}
     * 唯一约束对应，重复的 jobKey 启动注册失败。
     *
     * @return 业务键
     */
    String jobKey();

    /**
     * 任务名称；管理界面展示用，默认取方法全限定名。
     *
     * @return 任务名称
     */
    String name() default "";

    /**
     * 默认 CRON 表达式（Spring 6 位格式：秒 分 时 日 月 周）；与 {@link #fixedRate()}
     * / {@link #fixedDelay()} 三者必须且只能设置一个。
     *
     * <p>仅作初始建档默认值；库中已有配置时以库为准。
     *
     * @return CRON 表达式；未使用 CRON 触发时为空串
     */
    String cron() default "";

    /**
     * 默认固定频率（毫秒；上次开始时间 + 周期触发）；与 {@link #cron()}
     * / {@link #fixedDelay()} 互斥。
     *
     * @return 周期毫秒数；未使用固定频率时为 0
     */
    long fixedRate() default 0;

    /**
     * 默认固定延迟（毫秒；上次结束时间 + 周期触发）；与 {@link #cron()}
     * / {@link #fixedRate()} 互斥。
     *
     * @return 周期毫秒数；未使用固定延迟时为 0
     */
    long fixedDelay() default 0;

    /**
     * 默认任务超时时间（毫秒）；0 表示不限时。超时强制中断并标记 TIMEOUT。
     *
     * @return 超时毫秒数
     */
    long timeoutMillis() default 0;

    /**
     * 默认重试策略名；对应 {@code JobRetryPolicy} 枚举名，非法值注册时校验。
     *
     * @return FIXED_INTERVAL 或 EXPONENTIAL_BACKOFF
     */
    String retryPolicy() default "FIXED_INTERVAL";

    /**
     * 默认最大重试次数；0 表示不重试。
     *
     * @return 重试次数上限
     */
    int maxRetryCount() default 0;

    /**
     * 默认初始重试间隔（毫秒）。
     *
     * @return 初始间隔毫秒数
     */
    long retryInitialInterval() default 60_000;

    /**
     * 默认最大重试间隔（毫秒）；指数退避封顶值。
     *
     * @return 最大间隔毫秒数
     */
    long retryMaxInterval() default 600_000;

    /**
     * 默认并发冲突策略名；对应 {@code JobConflictStrategy} 枚举名。
     *
     * @return DISCARD 或 QUEUE
     */
    String conflictStrategy() default "DISCARD";

    /**
     * 默认连续失败告警阈值；0 表示不启用。
     *
     * @return 阈值次数
     */
    int alertFailureThreshold() default 0;
}
