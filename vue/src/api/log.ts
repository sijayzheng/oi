import request from '@/utils/request'

/**
 * 操作日志管理 API；接口清单与后端 SysLogController 对齐。
 *
 * <p>权限约定：仅系统管理员可用（后端硬校验，非管理员返回 403）。
 */
export const logApi = {
  /**
   * 分页检索操作日志（级别/时间范围/关键词筛选）。
   *
   * @param params 业务过滤条件 + 分页参数
   * @return 当前页日志集合与总记录数
   */
  page(params: SysLogQuery & PageQuery) {
    return request.page<Result<SysLogVO[]>>('/log/page', params)
  },

  /**
   * 按当前筛选条件导出日志（CSV 文本，上限 10000 条）。
   *
   * @param query 业务过滤条件（与列表一致）
   * @return CSV 文本（首行表头）
   */
  exportCsv(query: SysLogQuery) {
    return request.get<string>('/log/export', query, { timeout: 30_000 })
  },
}
