package cn.sijay.oi.sys.job;

import cn.sijay.oi.sys.entity.SysJob;
import cn.sijay.oi.sys.entity.SysJobDeadLetter;
import cn.sijay.oi.sys.entity.SysJobLog;
import cn.sijay.oi.sys.repository.SysJobDeadLetterRepository;
import cn.sijay.oi.sys.repository.SysJobLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 任务调度告警服务：内置站内信告警通道（零第三方依赖），
 * 承载死信入库与两类告警触发（死信告警 + 连续失败 N 次告警）。
 *
 * <p><b>告警内容规范</b>：任务唯一标识、失败原因摘要、最近 3 次执行历史、
 * 运维页面快速跳转链接（任务列表/死信管理页）。
 *
 * <p><b>隔离性</b>：告警链路任何异常（站内信发送失败等）仅记录告警日志，
 * 不影响任务执行主流程——死信入库与告警发送分事务，告警失败不回滚死信。
 *
 * @author sijay
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JobAlertService {

    /**
     * 任务调度运维页路径；拼入告警正文供快速跳转。
     */
    private static final String JOB_PAGE_PATH = "/system/job";

    /**
     * 死信管理页路径。
     */
    private static final String DEAD_LETTER_PAGE_PATH = "/system/job/dead-letter";

    /**
     * 连续失败告警的执行历史回看条数（含失败当次，防长周期任务漏报）。
     */
    private static final int CONSECUTIVE_SCAN_WINDOW = 50;

    private final SysJobDeadLetterRepository deadLetterRepository;
    private final SysJobLogRepository jobLogRepository;
    private final cn.sijay.oi.sys.repository.SysMessageRepository messageRepository;

    /**
     * 死信入库（重试耗尽）+ 死信告警评估。
     *
     * <p><b>事务边界设计</b>：死信入库（本方法）与告警发送严格解耦——
     * 入库事务（REQUIRES_NEW）仅含 save，保证调度线程无登录态等环境异常
     * 不会把整个死信插入回滚；告警发送移出入库事务（发送失败仅记日志），
     * 二者独立，任一失败均不阻断任务主流程。
     *
     * @param job        任务配置
     * @param sourceLog  死信来源执行记录
     * @param error      最终异常
     * @param retryCount 累计重试次数
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordDeadLetter(SysJob job, SysJobLog sourceLog, Throwable error, int retryCount) {
        SysJobDeadLetter deadLetter = null;
        try {
            deadLetter = new SysJobDeadLetter();
            deadLetter.setJobId(job.getId());
            deadLetter.setJobKey(job.getJobKey());
            deadLetter.setJobName(job.getName());
            deadLetter.setSourceLogId(sourceLog.getId());
            deadLetter.setJobConfig(cn.sijay.oi.common.util.JacksonUtils.toJson(JobConfigSnapshot.of(job)));
            deadLetter.setTotalRetryCount(Math.max(0, retryCount));
            if (error != null) {
                deadLetter.setErrorType(error.getClass().getName());
                deadLetter.setErrorStack(truncate(error.getMessage() == null
                        ? error.getClass().getName() : error.getMessage()));
            }
            deadLetterRepository.save(deadLetter);
        } catch (Exception e) {
            // 死信入库异常不阻断任务主流程（告警不发送）
            log.error("[Job] 死信入库失败（已隔离）: jobKey={}", job.getJobKey(), e);
            return;
        }
        // 告警发送在入库事务之外：发送异常仅记日志，不影响已入库的死信
        sendDeadLetterAlert(job, deadLetter, error);
    }

    /**
     * 连续失败 N 次告警评估（阈值按任务独立配置）。
     *
     * <p>统计口径：从最新一条执行记录向前数，连续 FAILED/TIMEOUT/DEAD 的条数
     * 达到 {@code job.alertFailureThreshold} 即告警；遇到成功/SKIPPED 即归零。
     * SKIPPED 为并发冲突跳过、非执行失败，不计入连续失败。
     *
     * @param job 任务配置
     */
    public void evaluateConsecutiveFailureAlert(SysJob job) {
        int threshold = job.getAlertFailureThreshold();
        if (threshold <= 0) {
            return;
        }
        try {
            List<SysJobLog> recent = jobLogRepository.findSinceForConsecutive(job.getJobKey(),
                    LocalDateTime.now().minusDays(7),
                    org.springframework.data.domain.PageRequest.of(0, CONSECUTIVE_SCAN_WINDOW));
            int consecutive = 0;
            for (SysJobLog l : recent) {
                if (JobBackoffCalculator.isFailureStatus(l.getExecuteStatus())) {
                    consecutive++;
                    if (consecutive >= threshold) {
                        sendConsecutiveFailureAlert(job, threshold, recent);
                        return;
                    }
                } else {
                    break;
                }
            }
        } catch (Exception e) {
            log.warn("[Job] 连续失败告警评估异常（已隔离）: jobKey={}", job.getJobKey(), e);
        }
    }

    /**
     * 死信告警（站内信）：任务标识 + 失败原因 + 最近 3 次执行 + 死信页跳转链接。
     */
    private void sendDeadLetterAlert(SysJob job, SysJobDeadLetter deadLetter, Throwable error) {
        String recent = recentHistoryText(job.getJobKey());
        String content = """
                任务进入死信队列，请及时处理。

                任务标识：%s（%s）
                失败原因：%s
                累计重试：%d 次

                最近 3 次执行历史：
                %s

                快速处理：%s（死信任务管理页）
                """.formatted(job.getJobKey(), job.getName(),
                error == null ? "未知异常" : Optional.ofNullable(error.getMessage()).orElse(error.getClass().getName()),
                deadLetter.getTotalRetryCount(), recent, DEAD_LETTER_PAGE_PATH);
        sendNotice("【任务调度】任务死信告警：" + job.getName(), content);
    }

    /**
     * 连续失败告警（站内信）：任务标识 + 失败原因 + 最近 3 次执行 + 任务页跳转链接。
     */
    private void sendConsecutiveFailureAlert(SysJob job, int threshold, List<SysJobLog> recent) {
        String recentText = recentHistoryText(job.getJobKey());
        String latestError = recent.isEmpty() ? "未知异常"
                : Optional.ofNullable(recent.get(0).getErrorType()).orElse("未知异常");
        String content = """
                任务连续失败达到阈值，请及时排查。

                任务标识：%s（%s）
                连续失败：已达 %d 次（阈值 %d 次）
                最近失败原因：%s

                最近 3 次执行历史：
                %s

                快速处理：%s（任务管理页）
                """.formatted(job.getJobKey(), job.getName(), threshold, threshold,
                latestError, recentText, JOB_PAGE_PATH);
        sendNotice("【任务调度】任务连续失败告警：" + job.getName(), content);
    }

    /**
     * 最近 3 次执行历史文本（告警正文）。
     */
    private String recentHistoryText(String jobKey) {
        List<SysJobLog> recent = jobLogRepository.findRecent(jobKey,
                org.springframework.data.domain.PageRequest.of(0, 3));
        if (recent.isEmpty()) {
            return "（暂无执行记录）";
        }
        StringBuilder sb = new StringBuilder();
        for (SysJobLog l : recent) {
            sb.append("- ").append(l.getStartTime()).append("  ")
                    .append(l.getExecuteStatus())
                    .append(l.getRetryCount() > 0 ? "（重试 " + l.getRetryCount() + " 次）" : "")
                    .append('\n');
        }
        return sb.toString().stripTrailing();
    }

    /**
     * 站内信告警发送（系统身份直写 sys_message 广播，sender=0）。
     *
     * <p><b>不走 SysMessageService.send 的原因</b>：send 对 BROADCAST 强制
     * {@code StpUtil.hasRole("admin")} 校验，而调度线程无登录态必被拒绝；
     * 告警属系统行为，以 sender=0（系统）直接落广播消息（全员可读）。
     * 发送失败仅记日志，不回滚、不阻断任务主流程。
     */
    private void sendNotice(String title, String content) {
        try {
            cn.sijay.oi.sys.entity.SysMessage message = new cn.sijay.oi.sys.entity.SysMessage();
            message.setMsgType(cn.sijay.oi.sys.entity.SysMessage.MessageType.BROADCAST.name());
            message.setSenderId(0L);
            message.setTitle(title.length() > 100 ? title.substring(0, 100) : title);
            message.setContent(content.length() > 4000 ? content.substring(0, 4000) : content);
            message.setStatus(cn.sijay.oi.sys.entity.SysMessage.MessageStatus.SENT.name());
            message.setSendTime(java.time.LocalDateTime.now());
            message.setCreateBy(0L);
            messageRepository.save(message);
        } catch (Exception e) {
            // 告警失败不阻断任何主流程（任务/死信均已落库）
            log.warn("[Job] 站内信告警发送失败（已隔离）: title={}", title, e);
        }
    }

    /**
     * 截断至 4000 字符。
     */
    private String truncate(String value) {
        return value != null && value.length() > 4000 ? value.substring(0, 4000) : value;
    }

    /**
     * 任务配置快照（死信上下文保留）。
     *
     * @param triggerType     触发类型
     * @param triggerValue    触发值
     * @param timeoutMillis   超时毫秒
     * @param retryPolicy     重试策略
     * @param maxRetryCount   最大重试次数
     * @param retryInitialInterval 初始重试间隔
     * @param retryMaxInterval     最大重试间隔
     */
    record JobConfigSnapshot(String triggerType, String triggerValue,
                             Long timeoutMillis, String retryPolicy, Integer maxRetryCount,
                             Long retryInitialInterval, Long retryMaxInterval) {
        static JobConfigSnapshot of(SysJob job) {
            return new JobConfigSnapshot(
                    job.getTriggerType().name(), job.getTriggerValue(),
                    job.getTimeoutMillis(), job.getRetryPolicy().name(),
                    job.getMaxRetryCount(), job.getRetryInitialInterval(), job.getRetryMaxInterval());
        }
    }
}
