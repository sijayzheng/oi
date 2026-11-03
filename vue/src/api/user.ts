import request from '@/utils/request'

/**
 * 用户管理 API；接口清单与后端 SysUserController 对齐。
 */
export const userApi = {
  /**
   * 分页条件查询用户列表。
   *
   * @param data 业务过滤条件 + 分页排序参数（全部选填）
   * @return 当前页用户集合（data）与总记录数（total）
   */
  page(data: SysUserQuery & PageQuery) {
    return request.page<Result<SysUserVO[]>>('/user/page', data)
  },

  /**
   * 查询用户详情。
   *
   * @param id 用户 id
   * @return 用户详情（不含密码）
   */
  get(id: string) {
    return request.get<SysUserVO>(`/user/${id}`)
  },

  /**
   * 创建用户。
   *
   * @param data 创建入参；username/password/enabled 必填
   */
  create(data: SysUserCreateCmd) {
    return request.post('/user', data)
  },

  /**
   * 更新用户档案（username/password 不可修改）。
   *
   * @param id   用户 id
   * @param data 更新入参；nickname/enabled 必填
   */
  update(id: string, data: SysUserUpdateCmd) {
    return request.put(`/user/${id}`, data)
  },

  /**
   * 删除用户（软删除）。
   *
   * @param id 用户 id
   */
  remove(id: string) {
    return request.delete(`/user/${id}`)
  },
}
