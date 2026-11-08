package cn.sijay.oi.sys.model.job;

import cn.sijay.oi.sys.entity.SysJobLog;
import cn.sijay.oi.sys.entity.SysJobRetryDetail;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 任务执行记录出参模型（执行详情页），含逐轮重试明细。
 *
 * @author sijay
 */
@Data
public class SysJobLogVO {

    /**
     * 执行记录 id。
     */
    private Long id;

    /**
     * 任务 id。
     */
    private Long jobId;

    /**
     * 任务业务键。
     */
    private String jobKey;

    /**
     * 任务名称快照。
     */
    private String jobName;

    /**
     * 触发来源；SCHEDULE / MANUAL / REPLAY。
     */
    private String triggerSource;

    /**
     * 触发来源中文描述。
     */
    private String triggerSourceDesc;

    /**
     * 执行状态；PENDING/RUNNING/SUCCESS/FAILED/TIMEOUT/DEAD/SKIPPED。
     */
    private String executeStatus;

    /**
     * 执行状态中文描述。
     */
    private String executeStatusDesc;

    /**
     * 当前轮次。
     */
    private Integer attemptNo;

    /**
     * 累计重试次数。
     */
    private Integer retryCount;

    /**
     * 异常类型。
     */
    private String errorType;

    /**
     * 异常信息（message + 堆栈）。
     */
    private String errorStack;

    /**
     * 执行开始时间。
     */
    private LocalDateTime startTime;

    /**
     * 执行结束时间。
     */
    private LocalDateTime endTime;

    /**
     * 执行耗时（毫秒）。
     */
    private Long durationMillis;

    /**
     * 执行机标识。
     */
    private String executor;

    /**
     * 逐轮重试明细；列表页不携带，详情页填充。
     */
    private List<AttemptDetailVO> attempts;

    /**
     * 由实体构建（不含重试明细）。
     *
     * @param entity 执行记录实体
     * @return 视图对象
     */
    public static SysJobLogVO from(SysJobLog entity) {
        SysJobLogVO vo = new SysJobLogVO();
        vo.setId(entity.getId());
        vo.setJobId(entity.getJobId());
        vo.setJobKey(entity.getJobKey());
        vo.setJobName(entity.getJobName());
        vo.setTriggerSource(entity.getTriggerSource());
        vo.setTriggerSourceDesc(triggerSourceDesc(entity.getTriggerSource()));
        vo.setExecuteStatus(entity.getExecuteStatus());
        vo.setExecuteStatusDesc(executeStatusDesc(entity.getExecuteStatus()));
        vo.setAttemptNo(entity.getAttemptNo());
        vo.setRetryCount(entity.getRetryCount());
        vo.setErrorType(entity.getErrorType());
        vo.setErrorStack(entity.getErrorStack());
        vo.setStartTime(entity.getStartTime());
        vo.setEndTime(entity.getEndTime());
        vo.setDurationMillis(entity.getDurationMillis());
        vo.setExecutor(entity.getExecutor());
        return vo;
    }

    /**
     * 填充重试明细。
     *
     * @param details 重试明细实体列表
     */
    public void fillAttempts(List<SysJobRetryDetail> details) {
        this.attempts = details.stream().map(AttemptDetailVO::from).toList();
    }

    private static String executeStatusDesc(String status) {
        for (cn.sijay.oi.sys.enums.JobExecuteStatus s : cn.sijay.oi.sys.enums.JobExecuteStatus.values()) {
            if (s.name().equals(status)) {
                return s.getDesc();
            }
        }
        return status;
    }

    private static String triggerSourceDesc(String source) {
        return switch (source == null ? "" : source) {
            case "SCHEDULE" -> "调度触发";
            case "MANUAL" -> "手动触发";
            case "REPLAY" -> "死信重放";
            default -> source;
        };
    }

    /**
     * 单轮尝试明细视图。
     *
     * @author sijay
     */
    @Data
    public static class AttemptDetailVO {

        /**
         * 尝试轮次；0=首次执行，n=第 n 次重试。
         */
        private Integer attemptNo;

        /**
         * 本轮状态。
         */
        private String attemptStatus;

        /**
         * 本轮异常类型。
         */
        private String errorType;

        /**
         * 本轮异常信息。
         */
        private String errorStack;

        /**
         * 本轮开始时间。
         */
        private LocalDateTime attemptStartTime;

        /**
         * 本轮结束时间。
         */
        private LocalDateTime attemptEndTime;

        /**
         * 本轮耗时（毫秒）。
         */
        private Long attemptDurationMillis;

        /**
         * 本轮失败后计算出的下次重试时刻。
         */
        private LocalDateTime nextRetryAt;

        /**
         * 由实体构建。
         *
         * @param entity 明细实体
         * @return 视图对象
         */
        public static AttemptDetailVO from(SysJobRetryDetail entity) {
            AttemptDetailVO vo = new AttemptDetailVO();
            vo.setAttemptNo(entity.getAttemptNo());
            vo.setAttemptStatus(entity.getAttemptStatus());
            vo.setErrorType(entity.getErrorType());
            vo.setErrorStack(entity.getErrorStack());
            vo.setAttemptStartTime(entity.getAttemptStartTime());
            vo.setAttemptEndTime(entity.getAttemptEndTime());
            vo.setAttemptDurationMillis(entity.getAttemptDurationMillis());
            vo.setNextRetryAt(entity.getNextRetryAt());
            return vo;
        }
    }
}
