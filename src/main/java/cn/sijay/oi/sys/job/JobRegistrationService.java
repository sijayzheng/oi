package cn.sijay.oi.sys.job;

import cn.sijay.oi.sys.entity.SysJob;
import cn.sijay.oi.sys.enums.JobConflictStrategy;
import cn.sijay.oi.sys.enums.JobRetryPolicy;
import cn.sijay.oi.sys.enums.JobTriggerType;
import cn.sijay.oi.sys.repository.SysJobLogRepository;
import cn.sijay.oi.sys.repository.SysJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 任务启动注册服务：应用就绪后完成三件事——
 * <ol>
 *   <li>扫描全部 {@code @OiScheduled} 方法，登记执行动作
 *       （业务侧零侵入：加注解即接入）；</li>
 *   <li>注解默认配置自动建档（库中已有同 jobKey 则以库为准，实现
 *       "代码声明身份 + 库承载可变配置"）；</li>
 *   <li>启用中的任务全部注册到动态调度器（触发即生效）。</li>
 * </ol>
 *
 * <p><b>孤儿状态修正</b>：启动时将上一进程遗留的 RUNNING 执行记录标记为
 * FAILED（应用重启中断），保证执行历史状态可读、连续失败统计准确。
 *
 * <p><b>集群语义</b>：注册仅操作本地调度器与库中配置，天然幂等；
 * 各节点独立注册触发，重复执行由执行引擎的分布式锁防重兜底。
 *
 * @author sijay
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobRegistrationService {

    /**
     * 系统操作人 id；自动建档的 create_by（DDL 约定 0=系统）。
     */
    private static final long SYSTEM_OPERATOR_ID = 0L;

    private final SysJobRepository jobRepository;
    private final SysJobLogRepository jobLogRepository;
    private final JobActionRegistry actionRegistry;
    private final JobSchedulerManager schedulerManager;
    private final org.springframework.context.ApplicationContext applicationContext;

    /**
     * 应用就绪后执行启动注册（扫描 → 建档 → 调度）。
     *
     * <p>时机选择 {@link ApplicationReadyEvent}：全部 Bean 初始化完成后再注册，
     * 避免 Bean 未就绪时触发执行导致 NPE。
     */
    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void onApplicationReady() {
        int marked = jobLogRepository.markRunningAsFailed(LocalDateTime.now(),
                "应用重启导致执行中断（启动时孤儿状态修正）");
        if (marked > 0) {
            log.warn("[Job] 启动修正：{} 条滞留 RUNNING 的执行记录已标记为 FAILED", marked);
        }
        int registered = scanAndRegisterActions();
        scheduleEnabledJobs();
        log.info("[Job] 启动注册完成：扫描动作 {} 个，调度任务 {} 个",
                registered, schedulerManager.registeredCount());
    }

    /**
     * 扫描全部 Spring Bean 的 @OiScheduled 方法并登记执行动作 + 自动建档。
     *
     * @return 成功登记的动作数
     */
    private int scanAndRegisterActions() {
        int count = 0;
        for (String beanName : applicationContext.getBeanDefinitionNames()) {
            Object bean;
            try {
                bean = applicationContext.getBean(beanName);
            } catch (Exception e) {
                // abstract/scope 原型等特殊 Bean 无法直接获取，跳过
                continue;
            }
            Class<?> targetClass = bean.getClass();
            if (targetClass.getName().contains("$$")) {
                targetClass = org.springframework.aop.support.AopUtils.getTargetClass(bean);
            }
            for (Method method : targetClass.getDeclaredMethods()) {
                OiScheduled annotation = AnnotationUtils.findAnnotation(method, OiScheduled.class);
                if (annotation == null) {
                    continue;
                }
                validateMethod(method, annotation);
                Object beanRef = bean;
                Runnable action = () -> {
                    try {
                        method.invoke(beanRef);
                    } catch (ReflectiveOperationException e) {
                        Throwable cause = e.getCause() == null ? e : e.getCause();
                        throw new RuntimeException("任务执行体调用失败: " + method.getName(), cause);
                    }
                };
                actionRegistry.register(annotation.jobKey(), action);
                ensureJobRecord(annotation, method);
                count++;
            }
        }
        return count;
    }

    /**
     * 校验任务方法签名（无参 public/protected 方法）。
     */
    private void validateMethod(Method method, OiScheduled annotation) {
        if (method.getParameterCount() > 0) {
            throw new IllegalStateException("@OiScheduled 方法必须无参: "
                    + method.getDeclaringClass().getName() + "#" + method.getName()
                    + "（jobKey=" + annotation.jobKey() + "）");
        }
    }

    /**
     * 注解默认配置自动建档；库中已有同 jobKey 配置时以库为准（不覆盖）。
     */
    private void ensureJobRecord(OiScheduled annotation, Method method) {
        if (jobRepository.existsByJobKey(annotation.jobKey())) {
            return;
        }
        SysJob job = new SysJob();
        job.setJobKey(annotation.jobKey());
        job.setName(annotation.name().isBlank()
                ? method.getDeclaringClass().getSimpleName() + "#" + method.getName() : annotation.name());
        applyDefaultTrigger(job, annotation);
        job.setTimeZone(annotation.timeZone().isBlank() ? null : annotation.timeZone());
        job.setTimeoutMillis(annotation.timeoutMillis());
        job.setRetryPolicy(JobRetryPolicy.valueOf(annotation.retryPolicy()));
        job.setMaxRetryCount(annotation.maxRetryCount());
        job.setRetryInitialInterval(annotation.retryInitialInterval());
        job.setRetryMaxInterval(annotation.retryMaxInterval());
        job.setConflictStrategy(JobConflictStrategy.valueOf(annotation.conflictStrategy()));
        job.setAlertFailureThreshold(annotation.alertFailureThreshold());
        job.setEnabled(Boolean.TRUE);
        job.setCreateBy(SYSTEM_OPERATOR_ID);
        jobRepository.save(job);
        log.info("[Job] 自动建档: jobKey={}, trigger={}/{}", job.getJobKey(),
                job.getTriggerType(), job.getTriggerValue());
    }

    /**
     * 解析注解触发配置（cron/fixedRate/fixedDelay 三选一）。
     */
    private void applyDefaultTrigger(SysJob job, OiScheduled annotation) {
        int configured = 0;
        if (!annotation.cron().isBlank()) {
            configured++;
        }
        if (annotation.fixedRate() > 0) {
            configured++;
        }
        if (annotation.fixedDelay() > 0) {
            configured++;
        }
        if (configured != 1) {
            throw new IllegalStateException("@OiScheduled 的 cron/fixedRate/fixedDelay 必须且只能设置一个: jobKey="
                    + annotation.jobKey());
        }
        if (!annotation.cron().isBlank()) {
            job.setTriggerType(JobTriggerType.CRON);
            job.setTriggerValue(annotation.cron());
        } else if (annotation.fixedRate() > 0) {
            job.setTriggerType(JobTriggerType.FIXED_RATE);
            job.setTriggerValue(String.valueOf(annotation.fixedRate()));
        } else {
            job.setTriggerType(JobTriggerType.FIXED_DELAY);
            job.setTriggerValue(String.valueOf(annotation.fixedDelay()));
        }
    }

    /**
     * 启用中的任务全部注册到调度器。
     *
     * <p>走 registerAfterCommit：本方法处于启动注册事务内，注册提交后生效
     * （fixed-rate 注册即首次触发，须保证触发线程能读到已提交的任务行）。
     */
    private void scheduleEnabledJobs() {
        List<SysJob> enabledJobs = jobRepository.findByEnabledTrue();
        for (SysJob job : enabledJobs) {
            try {
                schedulerManager.registerAfterCommit(job);
            } catch (Exception e) {
                // 单个任务注册失败不影响其余任务
                log.error("[Job] 启动注册失败（该任务暂停调度，请修正配置）: jobKey={}", job.getJobKey(), e);
            }
        }
    }
}
