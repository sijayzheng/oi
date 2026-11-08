package cn.sijay.oi.sys.controller;

import cn.sijay.oi.common.model.PageQuery;
import cn.sijay.oi.common.model.Result;
import cn.sijay.oi.common.web.BaseController;
import cn.sijay.oi.sys.entity.SysJob;
import cn.sijay.oi.sys.entity.SysJobDeadLetter;
import cn.sijay.oi.sys.entity.SysJobLog;
import cn.sijay.oi.sys.entity.SysJobRetryDetail;
import cn.sijay.oi.sys.job.JobExecutionEngine;
import cn.sijay.oi.sys.job.JobOperateService;
import cn.sijay.oi.sys.model.job.SysJobCreateCmd;
import cn.sijay.oi.sys.model.job.SysJobDeadLetterVO;
import cn.sijay.oi.sys.model.job.SysJobLogVO;
import cn.sijay.oi.sys.model.job.SysJobQuery;
import cn.sijay.oi.sys.model.job.SysJobUpdateCmd;
import cn.sijay.oi.sys.model.job.SysJobVO;
import cn.sijay.oi.sys.repository.SysJobRetryDetailRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 任务调度运维控制器：任务列表页、执行详情页、死信管理页三页接口。
 *
 * <p>统一约定：继承 {@link BaseController}，响应一律通过父类 ok/fail 包装；
 * 入参校验由 Bean Validation 触发；业务异常由全局异常处理器统一包装；
 * 全部运维操作由 {@code JobOperateService} 写审计日志（sys_log）。
 *
 * <p>接口清单：
 * <pre>
 * 任务列表页：
 * POST   /job                      创建任务
 * PUT    /job/{id}                 修改任务配置（即时生效）
 * DELETE /job/{id}                删除任务
 * GET    /job/{id}                任务详情
 * GET    /job/page                分页检索
 * PUT    /job/{id}/pause          暂停任务
 * PUT    /job/{id}/resume         恢复任务
 * POST   /job/{id}/trigger        手动立即执行
 *
 * 执行详情页：
 * GET    /job/log/page             执行记录分页
 * GET    /job/log/{logId}          执行记录详情（含重试明细）
 *
 * 死信管理页：
 * GET    /job/dead-letter/page     死信分页
 * POST   /job/dead-letter/{id}/replay  死信重放（重置重试计数重新执行）
 * DELETE /job/dead-letter/{id}    删除死信
 * </pre>
 *
 * @author sijay
 */
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/job")
public class SysJobController extends BaseController {

    private final JobOperateService jobOperateService;
    private final SysJobRetryDetailRepository retryDetailRepository;

    /* ==================== 任务列表页 ==================== */

    /**
     * 创建任务（触发配置即时注册调度）。
     *
     * @param cmd 创建入参
     * @return 成功响应（无数据）
     */
    @PostMapping
    public Result<Void> create(@RequestBody @Valid SysJobCreateCmd cmd) {
        jobOperateService.create(cmd);
        return ok();
    }

    /**
     * 修改任务配置（调度计划即时按新配置生效，免重启）。
     *
     * @param id  任务 id
     * @param cmd 更新入参
     * @return 成功响应（无数据）
     */
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable @Positive(message = "任务 id 必须为正整数") Long id,
                               @RequestBody @Valid SysJobUpdateCmd cmd) {
        jobOperateService.update(id, cmd);
        return ok();
    }

    /**
     * 删除任务（取消调度 + 软删，执行历史保留）。
     *
     * @param id 任务 id
     * @return 成功响应（无数据）
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable @Positive(message = "任务 id 必须为正整数") Long id) {
        jobOperateService.delete(id);
        return ok();
    }

    /**
     * 查询任务详情。
     *
     * @param id 任务 id
     * @return 任务详情
     */
    @GetMapping("/{id}")
    public Result<SysJobVO> get(@PathVariable @Positive(message = "任务 id 必须为正整数") Long id) {
        return ok(SysJobVO.from(jobOperateService.requireJob(id)));
    }

    /**
     * 任务定义分页检索（管理列表页）。
     *
     * @param query 过滤条件（jobKey/name 模糊、triggerType/enabled 精确，全部选填）
     * @param page  分页参数
     * @return 当前页任务集合与总数
     */
    @GetMapping("/page")
    public Result<List<SysJobVO>> page(@Valid SysJobQuery query, @Valid PageQuery page) {
        Page<SysJob> result = jobOperateService.page(query, page);
        return ok(result.getContent().stream().map(SysJobVO::from).toList(), result.getTotalElements());
    }

    /**
     * 暂停任务（取消调度，配置保留；重复暂停幂等成功）。
     *
     * @param id 任务 id
     * @return 成功响应（无数据）
     */
    @PutMapping("/{id}/pause")
    public Result<Void> pause(@PathVariable @Positive(message = "任务 id 必须为正整数") Long id) {
        jobOperateService.pause(id);
        return ok();
    }

    /**
     * 恢复任务（按当前配置重新注册调度）。
     *
     * @param id 任务 id
     * @return 成功响应（无数据）
     */
    @PutMapping("/{id}/resume")
    public Result<Void> resume(@PathVariable @Positive(message = "任务 id 必须为正整数") Long id) {
        jobOperateService.resume(id);
        return ok();
    }

    /**
     * 手动立即执行一次（不影响既有调度节奏）。
     *
     * @param id 任务 id
     * @return 执行结果（logId/status；并发冲突丢弃时 status=SKIPPED）
     */
    @PostMapping("/{id}/trigger")
    public Result<Map<String, Object>> trigger(@PathVariable @Positive(message = "任务 id 必须为正整数") Long id) {
        JobExecutionEngine.JobExecutionResult result = jobOperateService.trigger(id);
        return ok(Map.of("logId", result.logId() == null ? "" : String.valueOf(result.logId()),
                "status", result.status(),
                "success", result.success()));
    }

    /* ==================== 执行详情页 ==================== */

    /**
     * 执行记录分页检索（时间范围与状态过滤）。
     *
     * @param jobKey    任务业务键过滤；选填
     * @param status    执行状态过滤；选填
     * @param source    触发来源过滤；选填
     * @param beginTime 开始时间下限；选填
     * @param endTime   开始时间上限；选填
     * @param page      分页参数
     * @return 当前页执行记录与总数
     */
    @GetMapping("/log/page")
    public Result<List<SysJobLogVO>> pageLogs(@RequestParam(required = false) String jobKey,
                                              @RequestParam(required = false) String status,
                                              @RequestParam(required = false) String source,
                                              @RequestParam(required = false) LocalDateTime beginTime,
                                              @RequestParam(required = false) LocalDateTime endTime,
                                              @Valid PageQuery page) {
        Page<SysJobLog> result = jobOperateService.pageLogs(jobKey, status, source, beginTime, endTime, page);
        return ok(result.getContent().stream().map(SysJobLogVO::from).toList(), result.getTotalElements());
    }

    /**
     * 执行记录详情（含逐轮重试明细）。
     *
     * @param logId 执行记录 id
     * @return 执行记录详情（attempts 数组按轮次升序）
     */
    @GetMapping("/log/{logId}")
    public Result<SysJobLogVO> getLog(@PathVariable @Positive(message = "执行记录 id 必须为正整数") Long logId) {
        SysJobLog log = jobOperateService.getLog(logId);
        SysJobLogVO vo = SysJobLogVO.from(log);
        vo.fillAttempts(retryDetailRepository.findByLogIdOrderByAttemptNoAsc(logId));
        return ok(vo);
    }

    /* ==================== 死信管理页 ==================== */

    /**
     * 死信分页检索（死信管理页）。
     *
     * @param jobKey 任务业务键过滤；选填
     * @param status 处理状态过滤；选填
     * @param page   分页参数
     * @return 当前页死信与总数
     */
    @GetMapping("/dead-letter/page")
    public Result<List<SysJobDeadLetterVO>> pageDeadLetters(
            @RequestParam(required = false) String jobKey,
            @RequestParam(required = false) String status,
            @Valid PageQuery page) {
        Page<SysJobDeadLetter> result = jobOperateService.pageDeadLetters(jobKey, status, page);
        return ok(result.getContent().stream().map(SysJobDeadLetterVO::from).toList(), result.getTotalElements());
    }

    /**
     * 死信重放（重置重试计数，按任务当前配置重新走完整执行流程）。
     *
     * @param id 死信 id
     * @return 执行结果（logId/status）
     */
    @PostMapping("/dead-letter/{id}/replay")
    public Result<Map<String, Object>> replayDeadLetter(
            @PathVariable @Positive(message = "死信 id 必须为正整数") Long id) {
        JobExecutionEngine.JobExecutionResult result = jobOperateService.replayDeadLetter(id);
        return ok(Map.of("logId", result.logId() == null ? "" : String.valueOf(result.logId()),
                "status", result.status(),
                "success", result.success()));
    }

    /**
     * 删除死信记录（处理完毕后的物理清理）。
     *
     * @param id 死信 id
     * @return 成功响应（无数据）
     */
    @DeleteMapping("/dead-letter/{id}")
    public Result<Void> deleteDeadLetter(@PathVariable @Positive(message = "死信 id 必须为正整数") Long id) {
        jobOperateService.deleteDeadLetter(id);
        return ok();
    }
}
