import type { PageQuery, Result } from '@/types/common'
import type { Sys${className}CreateCmd, Sys${className}Query, Sys${className}UpdateCmd, Sys${className}VO } from '@/types/${businessName}'
import request from '@/utils/request'

/**
 * ${functionName}管理 API；接口清单与后端 Sys${className}Controller 对齐。
 */
export const ${businessName}Api = {
  /**
   * 分页条件查询${functionName}列表。
   *
   * @param data 业务过滤条件 + 分页排序参数（全部选填）
   * @return 当前页${functionName}集合（data）与总记录数（total）
   */
  page(data: Sys${className}Query & PageQuery) {
    return request.page<Result<Sys${className}VO[]>>('/${businessName}/page', data)
  },

  /**
   * 查询${functionName}详情。
   *
   * @param id ${functionName} id
   * @return ${functionName}详情
   */
  get(id: string) {
    return request.get<Sys${className}VO>(`/${businessName}/${'$'}{id}`)
  },

  /**
   * 创建${functionName}。
   *
   * @param data 创建入参
   */
  create(data: Sys${className}CreateCmd) {
    return request.post('/${businessName}', data)
  },

  /**
   * 更新${functionName}档案。
   *
   * @param id   ${functionName} id
   * @param data 更新入参
   */
  update(id: string, data: Sys${className}UpdateCmd) {
    return request.put(`/${businessName}/${'$'}{id}`, data)
  },

  /**
   * 删除${functionName}（软删除）。
   *
   * @param id ${functionName} id
   */
  remove(id: string) {
    return request.delete(`/${businessName}/${'$'}{id}`)
  },
}
