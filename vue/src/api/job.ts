import request from '@/utils/request'

/**
 * 任务调度运维 API；接口清单与后端 SysJobController 对齐。
 */
export const jobApi = {
  /**
   * 分页条件查询任务列表。
   *
   * @param data 业务过滤条件 + 分页排序参数（全部选填）
   * @return 当前页任务集合（data）与总记录数（total）
   */
  page(data: SysJobQuery & PageQuery) {
    return request.page<Result<SysJobVO[]>>('/job/page', data)
  },

  /**
   * 按 id 查询任务详情。
   *
   * @param id 任务 id
   * @return 任务详情
   */
  get(id: string) {
    return request.get<SysJobVO>(`/job/${id}`)
  },

  /**
   * 创建任务（触发配置即时注册调度）。
   *
   * @param data 创建入参
   */
  create(data: SysJobCreateCmd) {
    return request.post('/job', data)
  },

  /**
   * 修改任务配置（调度计划即时生效，免重启）。
   *
   * @param id   任务 id
   * @param data 更新入参
   */
  update(id: string, data: SysJobUpdateCmd) {
    return request.put(`/job/${id}`, data)
  },

  /**
   * 删除任务（取消调度 + 软删）。
   *
   * @param id 任务 id
   */
  remove(id: string) {
    return request.delete(`/job/${id}`)
  },

  /**
   * 暂停任务（取消调度，配置保留；幂等）。
   *
   * @param id 任务 id
   */
  pause(id: string) {
    return request.put(`/job/${id}/pause`)
  },

  /**
   * 恢复任务（按当前配置重新注册调度；幂等）。
   *
   * @param id 任务 id
   */
  resume(id: string) {
    return request.put(`/job/${id}/resume`)
  },

  /**
   * 手动立即执行一次（不影响调度节奏）。
   *
   * @param id 任务 id
   * @return 执行结果（logId/status/success）
   */
  trigger(id: string) {
    return request.post<JobTriggerResult>(`/job/${id}/trigger`)
  },

  /**
   * CRON 表达式后续执行时间预览（与调度器同源 Spring CronExpression，应用默认时区）。
   *
   * @param expression 6 位 CRON 表达式
   * @param count      预览次数（1~10，默认 5）
   * @return 按时间升序的后续执行时间（yyyy-MM-dd HH:mm:ss）
   */
  nextCronTimes(expression: string, count = 5) {
    return request.get<string[]>('/job/cron/next-times', { expression, count }, { silent: true })
  },

  /**
   * 执行记录分页检索。
   *
   * @param params 过滤条件（jobKey/status/source/beginTime/endTime 全部选填）+ 分页参数
   * @return 当前页执行记录与总数
   */
  pageLogs(params: {
    jobKey?: string
    status?: JobExecuteStatus
    source?: JobTriggerSource
    beginTime?: string
    endTime?: string
  } & PageQuery) {
    return request.page<Result<SysJobLogVO[]>>('/job/log/page', params)
  },

  /**
   * 执行记录详情（含逐轮重试明细）。
   *
   * @param logId 执行记录 id
   * @return 执行记录详情（attempts 按轮次升序）
   */
  getLog(logId: string) {
    return request.get<SysJobLogVO>(`/job/log/${logId}`)
  },

  /**
   * 死信分页检索。
   *
   * @param params 过滤条件（jobKey/status 选填）+ 分页参数
   * @return 当前页死信与总数
   */
  pageDeadLetters(params: { jobKey?: string, status?: DeadLetterStatus } & PageQuery) {
    return request.page<Result<SysJobDeadLetterVO[]>>('/job/dead-letter/page', params)
  },

  /**
   * 死信重放（重置重试计数，重新走完整执行流程）。
   *
   * @param id 死信 id
   * @return 执行结果（logId/status/success）
   */
  replayDeadLetter(id: string) {
    return request.post<JobTriggerResult>(`/job/dead-letter/${id}/replay`)
  },

  /**
   * 删除死信记录（物理清理）。
   *
   * @param id 死信 id
   */
  removeDeadLetter(id: string) {
    return request.delete(`/job/dead-letter/${id}`)
  },
}
