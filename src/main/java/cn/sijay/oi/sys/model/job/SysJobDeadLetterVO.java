package cn.sijay.oi.sys.model.job;

import cn.sijay.oi.sys.entity.SysJobDeadLetter;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 任务死信出参模型（死信管理页）。
 *
 * @author sijay
 */
@Data
public class SysJobDeadLetterVO {

    /**
     * 死信 id。
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
     * 死信来源执行记录 id（跳转执行详情页）。
     */
    private Long sourceLogId;

    /**
     * 任务配置快照（JSON）。
     */
    private String jobConfig;

    /**
     * 最终异常类型。
     */
    private String errorType;

    /**
     * 最终异常信息。
     */
    private String errorStack;

    /**
     * 累计重试次数。
     */
    private Integer totalRetryCount;

    /**
     * 处理状态；PENDING / RESOLVED / REPLAY_FAILED。
     */
    private String status;

    /**
     * 处理状态中文描述。
     */
    private String statusDesc;

    /**
     * 最近重放时间。
     */
    private LocalDateTime replayTime;

    /**
     * 死信产生时间。
     */
    private LocalDateTime createTime;

    /**
     * 由实体构建视图对象。
     *
     * @param entity 死信实体
     * @return 视图对象
     */
    public static SysJobDeadLetterVO from(SysJobDeadLetter entity) {
        SysJobDeadLetterVO vo = new SysJobDeadLetterVO();
        vo.setId(entity.getId());
        vo.setJobId(entity.getJobId());
        vo.setJobKey(entity.getJobKey());
        vo.setJobName(entity.getJobName());
        vo.setSourceLogId(entity.getSourceLogId());
        vo.setJobConfig(entity.getJobConfig());
        vo.setErrorType(entity.getErrorType());
        vo.setErrorStack(entity.getErrorStack());
        vo.setTotalRetryCount(entity.getTotalRetryCount());
        vo.setStatus(entity.getStatus());
        vo.setStatusDesc(statusDesc(entity.getStatus()));
        vo.setReplayTime(entity.getReplayTime());
        vo.setCreateTime(entity.getCreateTime());
        return vo;
    }

    private static String statusDesc(String status) {
        return switch (status == null ? "" : status) {
            case "PENDING" -> "待处理";
            case "RESOLVED" -> "已重放成功";
            case "REPLAY_FAILED" -> "重放失败";
            default -> status;
        };
    }
}
