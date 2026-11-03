import request from '@/utils/request'

/**
 * 字典类型管理 API；接口清单与后端 SysDictTypeController 对齐。
 */
export const dictTypeApi = {
  /**
   * 分页条件查询字典类型列表。
   *
   * @param data 业务过滤条件 + 分页排序参数（全部选填）
   * @return 当前页字典类型集合（data）与总记录数（total）
   */
  page(data: SysDictTypeQuery & PageQuery) {
    return request.page<Result<SysDictTypeVO[]>>('/dict/type/page', data)
  },

  /**
   * 查询字典类型详情。
   *
   * @param id 字典类型 id
   * @return 字典类型详情
   */
  get(id: string) {
    return request.get<SysDictTypeVO>(`/dict/type/${id}`)
  },

  /**
   * 创建字典类型。
   *
   * @param data 创建入参；dictType/name/enabled 必填
   */
  create(data: SysDictTypeCreateCmd) {
    return request.post('/dict/type', data)
  },

  /**
   * 更新字典类型（dictType 被字典项冗余引用，不可修改）。
   *
   * @param id   字典类型 id
   * @param data 更新入参；name/enabled 必填
   */
  update(id: string, data: SysDictTypeUpdateCmd) {
    return request.put(`/dict/type/${id}`, data)
  },

  /**
   * 删除字典类型（软删除，级联软删其全部字典项）。
   *
   * @param id 字典类型 id
   */
  remove(id: string) {
    return request.delete(`/dict/type/${id}`)
  },
}

/**
 * 字典项管理 API；接口清单与后端 SysDictItemController 对齐。
 */
export const dictItemApi = {
  /**
   * 分页条件查询字典项列表。
   *
   * @param data 业务过滤条件 + 分页排序参数（全部选填）
   * @return 当前页字典项集合（data）与总记录数（total）
   */
  page(data: SysDictItemQuery & PageQuery) {
    return request.page<Result<SysDictItemVO[]>>('/dict/item/page', data)
  },

  /**
   * 查询字典项详情。
   *
   * @param id 字典项 id
   * @return 字典项详情
   */
  get(id: string) {
    return request.get<SysDictItemVO>(`/dict/item/${id}`)
  },

  /**
   * 按字典编码取下拉选项（下拉框数据源）。
   *
   * @param dictType 字典编码
   * @return 下拉选项列表（sort 升序，value=itemCode、label=itemLabel）；字典不存在返回业务码 605002
   */
  options(dictType: string) {
    return request.get<SelectOption[]>(`/dict/item/options/${dictType}`)
  },

  /**
   * 创建字典项。
   *
   * @param data 创建入参；dictType/itemCode/itemLabel/enabled 必填
   */
  create(data: SysDictItemCreateCmd) {
    return request.post('/dict/item', data)
  },

  /**
   * 更新字典项（dictType/itemCode 不可修改）。
   *
   * @param id   字典项 id
   * @param data 更新入参；itemLabel/sort/enabled 必填
   */
  update(id: string, data: SysDictItemUpdateCmd) {
    return request.put(`/dict/item/${id}`, data)
  },

  /**
   * 删除字典项（软删除）。
   *
   * @param id 字典项 id
   */
  remove(id: string) {
    return request.delete(`/dict/item/${id}`)
  },
}
