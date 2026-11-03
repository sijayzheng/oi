import request from '@/utils/request'

/**
 * 角色管理 API；接口清单与后端 SysRoleController 对齐。
 */
export const roleApi = {
  /**
   * 分页条件查询角色列表。
   *
   * @param data 业务过滤条件 + 分页排序参数（全部选填）
   * @return 当前页角色集合（data）与总记录数（total）
   */
  page(data: SysRoleQuery & PageQuery) {
    return request.page<Result<SysRoleVO[]>>('/role/page', data)
  },

  /**
   * 查询角色详情。
   *
   * @param id 角色 id
   * @return 角色详情
   */
  get(id: string) {
    return request.get<SysRoleVO>(`/role/${id}`)
  },

  /**
   * 创建角色。
   *
   * @param data 创建入参；code/name/enabled 必填
   */
  create(data: SysRoleCreateCmd) {
    return request.post('/role', data)
  },

  /**
   * 更新角色档案（code 不可修改）。
   *
   * @param id   角色 id
   * @param data 更新入参；name/sort/enabled 必填
   */
  update(id: string, data: SysRoleUpdateCmd) {
    return request.put(`/role/${id}`, data)
  },

  /**
   * 删除角色（软删除）。
   *
   * @param id 角色 id
   */
  remove(id: string) {
    return request.delete(`/role/${id}`)
  },
}
