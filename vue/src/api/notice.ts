import request from '@/utils/request'

/**
 * 通知公告管理 API；接口清单与后端 SysNoticeController 对齐。
 */
export const noticeApi = {
  /**
   * 分页条件查询通知公告列表。
   *
   * @param data 业务过滤条件 + 分页排序参数（全部选填）
   * @return 当前页通知公告集合（data）与总记录数（total）
   */
  page(data: SysNoticeQuery & PageQuery) {
    return request.page<Result<SysNoticeVO[]>>('/notice/page', data)
  },

  /**
   * 查询通知公告详情。
   *
   * @param id 通知公告 id
   * @return 通知公告详情
   */
  get(id: string) {
    return request.get<SysNoticeVO>(`/notice/${id}`)
  },

  /**
   * 创建通知公告。
   *
   * @param data 创建入参
   */
  create(data: SysNoticeCreateCmd) {
    return request.post('/notice', data)
  },

  /**
   * 更新通知公告档案。
   *
   * @param id   通知公告 id
   * @param data 更新入参
   */
  update(id: string, data: SysNoticeUpdateCmd) {
    return request.put(`/notice/${id}`, data)
  },

  /**
   * 删除通知公告（软删除）。
   *
   * @param id 通知公告 id
   */
  remove(id: string) {
    return request.delete(`/notice/${id}`)
  },
}
