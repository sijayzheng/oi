import request from '@/utils/request'

/**
 * 菜单管理 API；接口清单与后端 SysMenuController 对齐。
 */
export const menuApi = {
  /**
   * 分页条件查询菜单列表（平铺结构）。
   *
   * @param data 业务过滤条件 + 分页排序参数（全部选填）
   * @return 当前页菜单集合（data）与总记录数（total）
   */
  page(data: SysMenuQuery & PageQuery) {
    return request.page<Result<SysMenuVO[]>>('/menu/page', data)
  },

  /**
   * 查询菜单详情。
   *
   * @param id 菜单 id
   * @return 菜单详情
   */
  get(id: string) {
    return request.get<SysMenuVO>(`/menu/${id}`)
  },

  /**
   * 创建菜单。
   *
   * @param data 创建入参；name/type/sort/visible/enabled 必填，parentId 不传=根节点
   *             （层级非法返回 603003，父不存在返回 603001）
   */
  create(data: SysMenuCreateCmd) {
    return request.post('/menu', data)
  },

  /**
   * 更新菜单档案（parentId/type 不可修改）。
   *
   * @param id   菜单 id
   * @param data 更新入参；name/sort/visible/enabled 必填
   */
  update(id: string, data: SysMenuUpdateCmd) {
    return request.put(`/menu/${id}`, data)
  },

  /**
   * 删除菜单（软删除；存在子菜单返回业务码 603002）。
   *
   * @param id 菜单 id
   */
  remove(id: string) {
    return request.delete(`/menu/${id}`)
  },

  /**
   * 获取完整菜单树。
   *
   * @return 根节点列表（parentId=0 的节点为根；children 永不为 null）
   */
  tree() {
    return request.get<TreeNode[]>('/menu/tree')
  },
}
