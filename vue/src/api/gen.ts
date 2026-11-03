import request from '@/utils/request'

/**
 * 代码生成管理 API；接口清单与后端 GenTableController 对齐。
 */
export const genApi = {
  /**
   * 查询可导入的业务表清单（已排除 gen 元数据表与已导入表）。
   */
  importableTables() {
    return request.get<ImportableTable[]>('/gen/db/tables')
  },

  /**
   * 导入业务表结构（自动推导字段默认配置）。
   *
   * @param tableName 业务表名
   */
  importTable(tableName: string) {
    return request.post(`/gen/table/import/${tableName}`)
  },

  /**
   * 分页查询表配置列表。
   *
   * @param params 分页参数（pageNum/pageSize）
   */
  page(params: PageQuery) {
    return request.page<Result<GenTable[]>>('/gen/table/page', params)
  },

  /**
   * 查询表配置详情（含字段配置）。
   *
   * @param id 表配置 id
   */
  get(id: string) {
    return request.get<GenTableDetail>(`/gen/table/${id}`)
  },

  /**
   * 保存表/字段配置（全量覆盖）。
   *
   * @param id  表配置 id
   * @param data 保存入参
   */
  save(id: string, data: GenTableSaveCmd) {
    return request.put(`/gen/table/${id}`, data)
  },

  /**
   * 删除表配置及其字段配置。
   *
   * @param id 表配置 id
   */
  remove(id: string) {
    return request.delete(`/gen/table/${id}`)
  },

  /**
   * 预览生成产物（相对路径 → 文件内容）。
   *
   * @param id 表配置 id
   */
  preview(id: string) {
    return request.get<Record<string, string>>(`/gen/table/preview/${id}`)
  },

  /**
   * 生成并落盘 + 前端路由/菜单锚点注册。
   *
   * @param id 表配置 id
   * @return 已写入文件的相对路径清单
   */
  generate(id: string) {
    return request.post<string[]>(`/gen/table/generate/${id}`)
  },
}
