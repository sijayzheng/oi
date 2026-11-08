package cn.sijay.oi.sys.job;

import cn.sijay.oi.common.exception.BusinessErrorCode;
import cn.sijay.oi.common.exception.BusinessException;
import cn.sijay.oi.common.model.PageQuery;
import cn.sijay.oi.sys.entity.SysJob;
import cn.sijay.oi.sys.entity.SysJobDeadLetter;
import cn.sijay.oi.sys.entity.SysJobLog;
import cn.sijay.oi.sys.model.job.SysJobCreateCmd;
import cn.sijay.oi.sys.model.job.SysJobQuery;
import cn.sijay.oi.sys.model.job.SysJobUpdateCmd;
import cn.sijay.oi.sys.repository.SysJobDeadLetterRepository;
import cn.sijay.oi.sys.repository.SysJobLogRepository;
import cn.sijay.oi.sys.repository.SysJobRepository;
import cn.sijay.oi.sys.service.SysLogService;
import cn.dev33.satoken.stp.StpUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 任务调度运维服务：任务定义 CRUD、启停、手动触发、死信查询与重放、
 * 执行记录检索；全部运维操作写审计日志（sys_log）。
 *
 * <p><b>动态生效链路</b>：所有配置变更在事务提交后同步调度器
 * （register/unregister 即时生效，免重启）。
 *
 * <p><b>审计约定</b>：模块 {@code job}；动作——
 * create/update/delete/pause/resume/trigger/replay/deleteDeadLetter；
 * 操作人取 sa-token 当前登录人（运维接口均要求登录态）。
 *
 * @author sijay
 */
@Slf4j
@Service
public class JobOperateService {

    /**
     * 审计日志模块标识。
     */
    private static final String LOG_MODULE = "job";

    private final SysJobRepository jobRepository;
    private final SysJobLogRepository jobLogRepository;
    private final SysJobDeadLetterRepository deadLetterRepository;
    private final JobSchedulerManager schedulerManager;
    private final JobActionRegistry actionRegistry;
    private final SysLogService logService;
    private final org.springframework.context.ApplicationContext applicationContext;

    /**
     * 构造注入（显式列明，含 @Lazy 应用上下文）。
     */
    public JobOperateService(SysJobRepository jobRepository,
                             SysJobLogRepository jobLogRepository,
                             SysJobDeadLetterRepository deadLetterRepository,
                             JobSchedulerManager schedulerManager,
                             JobActionRegistry actionRegistry,
                             SysLogService logService,
                             @org.springframework.context.annotation.Lazy
                             org.springframework.context.ApplicationContext applicationContext) {
        this.jobRepository = jobRepository;
        this.jobLogRepository = jobLogRepository;
        this.deadLetterRepository = deadLetterRepository;
        this.schedulerManager = schedulerManager;
        this.actionRegistry = actionRegistry;
        this.logService = logService;
        this.applicationContext = applicationContext;
    }

    /* ==================== 任务定义 CRUD ==================== */

    /**
     * 创建任务（触发配置即时注册调度）。
     *
     * @param cmd 创建入参
     * @return 已落库的任务
     * @throws BusinessException 业务键重复（608002）/ 触发配置非法（608003）/
     *                           时区非法（608004）/ 重试配置非法（608005）
     */
    @Transactional
    public SysJob create(SysJobCreateCmd cmd) {
        if (jobRepository.existsByJobKey(cmd.getJobKey())) {
            throw new BusinessException(BusinessErrorCode.JOB_KEY_EXISTS);
        }
        SysJob job = new SysJob();
        job.setJobKey(cmd.getJobKey());
        applyTriggerConfig(job, cmd.getTriggerType(), cmd.getTriggerValue(), cmd.getTimeZone());
        applyRetryConfig(job, cmd.getMaxRetryCount(), cmd.getRetryInitialInterval(), cmd.getRetryMaxInterval());
        job.setName(cmd.getName());
        job.setTimeoutMillis(cmd.getTimeoutMillis());
        job.setRetryPolicy(cmd.getRetryPolicy());
        job.setRetryInclude(cmd.getRetryInclude());
        job.setRetryExclude(cmd.getRetryExclude());
        job.setConflictStrategy(cmd.getConflictStrategy());
        job.setAlertFailureThreshold(cmd.getAlertFailureThreshold());
        job.setRemark(cmd.getRemark());
        job.setEnabled(Boolean.TRUE);
        job.setCreateBy(currentOperator());
        SysJob saved = jobRepository.save(job);
        schedulerManager.registerAfterCommit(saved);
        audit("create", "创建任务: " + job.getJobKey(), null);
        return saved;
    }

    /**
     * 更新任务配置（调度计划即时按新配置重注册，免重启生效）。
     *
     * @param id  任务 id
     * @param cmd 更新入参
     * @return 更新后的任务
     */
    @Transactional
    public SysJob update(Long id, SysJobUpdateCmd cmd) {
        SysJob job = requireJob(id);
        applyTriggerConfig(job, cmd.getTriggerType(), cmd.getTriggerValue(), cmd.getTimeZone());
        applyRetryConfig(job, cmd.getMaxRetryCount(), cmd.getRetryInitialInterval(), cmd.getRetryMaxInterval());
        job.setName(cmd.getName());
        job.setTimeoutMillis(cmd.getTimeoutMillis());
        job.setRetryPolicy(cmd.getRetryPolicy());
        job.setRetryInclude(cmd.getRetryInclude());
        job.setRetryExclude(cmd.getRetryExclude());
        job.setConflictStrategy(cmd.getConflictStrategy());
        job.setAlertFailureThreshold(cmd.getAlertFailureThreshold());
        job.setRemark(cmd.getRemark());
        job.setUpdateBy(currentOperator());
        // 以最新配置重注册（enabled 未变；暂停中的任务保持暂停；事务提交后生效）
        schedulerManager.registerAfterCommit(job);
        audit("update", "修改任务配置: " + job.getJobKey(), null);
        return job;
    }

    /**
     * 删除任务（取消调度 + 软删；执行历史保留审计）。
     *
     * @param id 任务 id
     */
    @Transactional
    public void delete(Long id) {
        SysJob job = requireJob(id);
        schedulerManager.unregister(job.getJobKey());
        if (jobRepository.softDelete(id) == 0) {
            throw new BusinessException(BusinessErrorCode.JOB_NOT_FOUND);
        }
        audit("delete", "删除任务: " + job.getJobKey(), null);
    }

    /**
     * 暂停任务（取消调度，配置保留）。
     *
     * @param id 任务 id
     */
    @Transactional
    public void pause(Long id) {
        SysJob job = requireJob(id);
        if (Boolean.FALSE.equals(job.getEnabled())) {
            return; // 幂等
        }
        job.setEnabled(Boolean.FALSE);
        job.setUpdateBy(currentOperator());
        schedulerManager.unregister(job.getJobKey());
        audit("pause", "暂停任务: " + job.getJobKey(), null);
    }

    /**
     * 恢复任务（按当前配置重新注册调度）。
     *
     * @param id 任务 id
     */
    @Transactional
    public void resume(Long id) {
        SysJob job = requireJob(id);
        if (Boolean.TRUE.equals(job.getEnabled())) {
            return; // 幂等
        }
        job.setEnabled(Boolean.TRUE);
        job.setUpdateBy(currentOperator());
        schedulerManager.registerAfterCommit(job);
        audit("resume", "恢复任务: " + job.getJobKey(), null);
    }

    /* ==================== 触发与执行记录 ==================== */

    /**
     * 手动立即触发一次执行（不影响调度节奏）。
     *
     * <p>同步执行并返回结果（运维操作需要即时反馈）；
     * 并发冲突按任务配置的冲突策略处理。
     *
     * <p><b>不持有跨执行事务</b>（生产正确性关键点）：执行引擎按"逐记录独立提交"
     * 落库——RUNNING 插入后立即对外可见（列表页/其他节点可观测执行中状态），
     * 逐轮重试明细、终态、死信各自独立提交。若以 {@code @Transactional} 包裹
     * 整个触发，全部落库会挂起至执行结束（最长 timeout × 重试轮数）：既阻塞
     * 执行观测，又让数据库连接被独占整个执行期。与调度线程触发路径保持一致——
     * 两种入口的落库时序完全相同。
     *
     * @param id 任务 id
     * @return 执行结果
     * @throws BusinessException 任务不存在（608001）/ 无执行动作（608008）
     */
    public JobExecutionEngine.JobExecutionResult trigger(Long id) {
        SysJob job = requireJob(id);
        Runnable action = actionRegistry.lookup(job.getJobKey());
        if (action == null) {
            // 无执行动作的任务（纯配置预留）不允许手动触发
            throw new BusinessException(BusinessErrorCode.JOB_REPLAY_TARGET_MISSING);
        }
        JobExecutionEngine engine = resolveEngine();
        JobExecutionEngine.JobExecutionResult result = engine.execute(job, action,
                SysJobLog.TriggerSource.MANUAL);
        audit("trigger", "手动触发任务: " + job.getJobKey() + "，结果=" + result.status(),
                result.success() ? null : result.status());
        return result;
    }

    /**
     * 任务定义分页检索（管理列表页）。
     *
     * @param query 过滤条件（jobKey/name 模糊、enabled/triggerType 精确）
     * @param page  分页参数
     * @return 分页结果
     */
    @Transactional(readOnly = true)
    public Page<SysJob> page(SysJobQuery query, PageQuery page) {
        page.validate();
        SysJob probe = new SysJob();
        probe.setJobKey(query.getJobKey());
        probe.setName(query.getName());
        probe.setTriggerType(query.getTriggerType());
        if (query.getEnabled() != null) {
            probe.setEnabled(query.getEnabled());
        }
        org.springframework.data.domain.ExampleMatcher matcher = org.springframework.data.domain.ExampleMatcher.matching()
                .withStringMatcher(org.springframework.data.domain.ExampleMatcher.StringMatcher.CONTAINING)
                .withIgnoreCase()
                .withIgnorePaths("id", "timeoutMillis", "maxRetryCount", "retryInitialInterval",
                        "retryMaxInterval", "alertFailureThreshold", "createBy", "updateBy",
                        "createTime", "updateTime", "deleted");
        return jobRepository.findAll(org.springframework.data.domain.Example.of(probe, matcher),
                page.toPageRequest());
    }

    /**
     * 执行记录分页检索（执行详情页）。
     *
     * @param jobKey    任务业务键过滤；空为全部
     * @param status    执行状态过滤；空为全部
     * @param source    触发来源过滤；空为全部
     * @param beginTime 开始时间下限
     * @param endTime   开始时间上限
     * @param page      分页参数
     * @return 分页结果（固定按开始时间倒序）
     */
    @Transactional(readOnly = true)
    public Page<SysJobLog> pageLogs(String jobKey, String status, String source,
                                    LocalDateTime beginTime, LocalDateTime endTime, PageQuery page) {
        page.validate();
        return jobLogRepository.search(normalize(jobKey), normalize(status), normalize(source),
                beginTime, endTime,
                PageRequest.of(page.pageNum() - 1, page.pageSize(),
                        Sort.by(Sort.Direction.DESC, "startTime")));
    }

    /**
     * 执行记录详情（含逐轮重试明细）。
     *
     * @param logId 执行记录 id
     * @return 执行记录
     */
    @Transactional(readOnly = true)
    public SysJobLog getLog(Long logId) {
        return jobLogRepository.findById(logId)
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.JOB_LOG_NOT_FOUND));
    }

    /* ==================== 死信管理 ==================== */

    /**
     * 死信分页检索（死信管理页）。
     *
     * @param jobKey 任务业务键过滤；空为全部
     * @param status 处理状态过滤；空为全部
     * @param page   分页参数
     * @return 分页结果（按产生时间倒序）
     */
    @Transactional(readOnly = true)
    public Page<SysJobDeadLetter> pageDeadLetters(String jobKey, String status, PageQuery page) {
        page.validate();
        return deadLetterRepository.search(normalize(jobKey), normalize(status), null, null,
                PageRequest.of(page.pageNum() - 1, page.pageSize(),
                        Sort.by(Sort.Direction.DESC, "createTime")));
    }

    /**
     * 死信重放：重置重试计数，重新走完整任务执行流程。
     *
     * <p>重放语义：定位死信对应任务 → 按<b>任务当前配置</b>（而非快照）执行
     * → 成功标记 RESOLVED，失败标记 REPLAY_FAILED（可再次重放）。
     *
     * <p><b>不持有跨执行事务</b>（与 {@link #trigger} 同理，见该方法注释）：
     * 引擎执行期间逐记录独立提交；死信状态在执行前后分两次独立落库——
     * 执行前置 PENDING（重放中，页面可见），执行后回写终态。
     *
     * @param deadLetterId 死信 id
     * @return 执行结果
     * @throws BusinessException 死信不存在（608006）/ 任务已删除（608008）
     */
    public JobExecutionEngine.JobExecutionResult replayDeadLetter(Long deadLetterId) {
        SysJobDeadLetter deadLetter = deadLetterRepository.findById(deadLetterId)
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.JOB_DEAD_LETTER_NOT_FOUND));
        SysJob job = jobRepository.findById(deadLetter.getJobId())
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.JOB_REPLAY_TARGET_MISSING));
        Runnable action = actionRegistry.lookup(deadLetter.getJobKey());
        if (action == null) {
            throw new BusinessException(BusinessErrorCode.JOB_REPLAY_TARGET_MISSING);
        }
        // 重放中状态先落库：重放期间死信列表页可见"处理中"而非静默无变化
        deadLetter.setStatus(SysJobDeadLetter.Status.PENDING.name());
        deadLetter.setReplayTime(LocalDateTime.now());
        deadLetterRepository.save(deadLetter);
        JobExecutionEngine engine = resolveEngine();
        // 重放触发：重试计数自然从 0 起（引擎按任务配置重新计数）
        JobExecutionEngine.JobExecutionResult result = engine.execute(job, action,
                SysJobLog.TriggerSource.REPLAY);
        // 终态回写（重读死信，防执行期间被并发删除）
        deadLetterRepository.findById(deadLetterId)
                .ifPresent(dl -> {
                    dl.setStatus(result.success()
                            ? SysJobDeadLetter.Status.RESOLVED.name()
                            : SysJobDeadLetter.Status.REPLAY_FAILED.name());
                    deadLetterRepository.save(dl);
                });
        audit("replay", "重放死信: " + deadLetter.getJobKey() + "，结果=" + result.status(),
                result.success() ? null : result.status());
        return result;
    }

    /**
     * 删除死信记录（处理完毕后的物理清理）。
     *
     * @param deadLetterId 死信 id
     */
    @Transactional
    public void deleteDeadLetter(Long deadLetterId) {
        SysJobDeadLetter deadLetter = deadLetterRepository.findById(deadLetterId)
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.JOB_DEAD_LETTER_NOT_FOUND));
        deadLetterRepository.delete(deadLetter);
        audit("deleteDeadLetter", "删除死信记录: id=" + deadLetterId + ", jobKey=" + deadLetter.getJobKey(), null);
    }

    /**
     * 按 id 加载任务（不存在/已软删抛 608001）。
     *
     * @param id 任务 id
     * @return 任务实体
     */
    @Transactional(readOnly = true)
    public SysJob requireJob(Long id) {
        return jobRepository.findById(id)
                .orElseThrow(() -> new BusinessException(BusinessErrorCode.JOB_NOT_FOUND));
    }

    /* ==================== 私有辅助 ==================== */

    /**
     * 校验并应用触发配置（CRON 解析 + 时区合法性 + 周期下限）。
     */
    private void applyTriggerConfig(SysJob job, cn.sijay.oi.sys.enums.JobTriggerType triggerType,
                                    String triggerValue, String timeZone) {
        if (triggerType == null || triggerValue == null || triggerValue.isBlank()) {
            throw new BusinessException(BusinessErrorCode.JOB_TRIGGER_INVALID);
        }
        if (triggerType == cn.sijay.oi.sys.enums.JobTriggerType.CRON) {
            if (!CronExpression.isValidExpression(triggerValue)) {
                throw new BusinessException(BusinessErrorCode.JOB_TRIGGER_INVALID);
            }
            if (timeZone != null && !timeZone.isBlank()) {
                try {
                    ZoneId.of(timeZone.trim());
                } catch (Exception e) {
                    throw new BusinessException(BusinessErrorCode.JOB_TIMEZONE_INVALID);
                }
            }
        } else {
            long period;
            try {
                period = Long.parseLong(triggerValue.trim());
            } catch (NumberFormatException e) {
                throw new BusinessException(BusinessErrorCode.JOB_TRIGGER_INVALID);
            }
            if (period < 1000) {
                throw new BusinessException(BusinessErrorCode.JOB_TRIGGER_INVALID);
            }
        }
        job.setTriggerType(triggerType);
        job.setTriggerValue(triggerValue.trim());
        job.setTimeZone(timeZone == null || timeZone.isBlank() ? null : timeZone.trim());
    }

    /**
     * 校验并应用重试配置（间隔非负 + max >= initial）。
     */
    private void applyRetryConfig(SysJob job, Integer maxRetryCount,
                                  Long initialInterval, Long maxInterval) {
        if (maxRetryCount < 0 || initialInterval < 0 || maxInterval < 0) {
            throw new BusinessException(BusinessErrorCode.JOB_RETRY_CONFIG_INVALID);
        }
        if (maxInterval < initialInterval) {
            throw new BusinessException(BusinessErrorCode.JOB_RETRY_CONFIG_INVALID);
        }
        job.setMaxRetryCount(maxRetryCount);
        job.setRetryInitialInterval(initialInterval);
        job.setRetryMaxInterval(maxInterval);
    }

    /**
     * 按需解析执行引擎（@Lazy 语义手动化：避免本类构造期依赖环）。
     */
    private JobExecutionEngine resolveEngine() {
        return applicationContext.getBean(JobExecutionEngine.class);
    }

    /**
     * 当前操作人（sa-token 登录态；调度线程等无登录态场景返回 0=系统）。
     */
    private Long currentOperator() {
        try {
            return StpUtil.getLoginIdAsLong();
        } catch (Exception e) {
            return 0L;
        }
    }

    /**
     * 写操作审计日志（失败不阻断主流程，由 SysLogService.record 保证）。
     */
    private void audit(String action, String detail, String errorMsg) {
        Long operator;
        String username;
        try {
            operator = StpUtil.getLoginIdAsLong();
            username = String.valueOf(StpUtil.getLoginId());
        } catch (Exception e) {
            operator = 0L;
            username = "system";
        }
        logService.record(LOG_MODULE, action, cn.sijay.oi.sys.entity.SysLog.LEVEL_INFO,
                operator, username, null, null, null, detail,
                errorMsg == null ? cn.sijay.oi.sys.entity.SysLog.RESULT_SUCCESS
                        : cn.sijay.oi.sys.entity.SysLog.RESULT_FAIL,
                errorMsg);
    }

    /**
     * 空串归一化为 null。
     */
    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
