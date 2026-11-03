import request from '@/utils/request'

/**
 * 参数配置管理 API；接口清单与后端 SysConfigController 对齐。
 */
export const configApi = {
  /**
   * 分页条件查询参数配置列表。
   *
   * @param data 业务过滤条件 + 分页排序参数（全部选填）
   * @return 当前页配置集合（data）与总记录数（total）
   */
  page(data: SysConfigQuery & PageQuery) {
    return request.page<Result<SysConfigVO[]>>('/config/page', data)
  },

  /**
   * 按 id 查询配置详情。
   *
   * @param id 配置 id
   * @return 配置详情
   */
  get(id: string) {
    return request.get<SysConfigVO>(`/config/${id}`)
  },

  /**
   * 按参数键读取配置（运行时取值入口）。
   *
   * @param configKey 参数键；点分命名格式
   * @return 配置详情；参数不存在返回业务码 604002
   */
  getByKey(configKey: string) {
    return request.get<SysConfigVO>(`/config/key/${configKey}`)
  },

  /**
   * 创建参数配置。
   *
   * @param data 创建入参；configKey/configValue/valueType/name 必填
   */
  create(data: SysConfigCreateCmd) {
    return request.post('/config', data)
  },

  /**
   * 更新参数配置（configKey 不可修改）。
   *
   * @param id   配置 id
   * @param data 更新入参；configValue/valueType/name 必填
   */
  update(id: string, data: SysConfigUpdateCmd) {
    return request.put(`/config/${id}`, data)
  },

  /**
   * 删除参数配置（软删除）。
   *
   * @param id 配置 id
   */
  remove(id: string) {
    return request.delete(`/config/${id}`)
  },
}
