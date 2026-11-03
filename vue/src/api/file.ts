import request from '@/utils/request'

/**
 * 统一文件管理 API；接口清单与后端 FileController 对齐。
 *
 * <p>调用规范（全系统强制约定）：业务场景上传文件仅调用 {@link upload}，
 * 上传成功后只持久化返回的文件 id；需要访问文件时凭 id 调用 {@link getUrl}
 * 换取带有效期的临时访问地址，禁止直接拼接存储路径。
 */
export const fileApi = {
  /**
   * 上传文件（multipart，字段名 file）。
   *
   * @param file        文件
   * @param businessKey 业务标识（可选）；标记文件归属场景
   * @return 文件信息（含唯一 id 与临时访问地址）
   */
  upload(file: File, businessKey?: string) {
    const formData = new FormData()
    formData.append('file', file)
    if (businessKey)
      formData.append('businessKey', businessKey)
    return request.post<SysFileVO>('/file/upload', formData, {
      timeout: 30_000,
    })
  },

  /**
   * 查询文件元数据（含新签发的临时访问地址）。
   *
   * @param id 文件 id
   */
  get(id: string) {
    return request.get<SysFileVO>(`/file/${id}`)
  },

  /**
   * 分页条件查询文件列表（管理端检索）。
   */
  page(params: SysFileQuery & PageQuery) {
    return request.page<Result<SysFileVO[]>>('/file/page', params)
  },

  /**
   * 签发新的临时访问地址（旧地址过期后调用）。
   *
   * @param id 文件 id
   * @return 临时访问地址（相对路径，如 /file/access/1?expires=...&sign=...）
   */
  getUrl(id: string) {
    return request.get<string>(`/file/${id}/url`)
  },

  /**
   * 删除文件（软删记录 + 清理物理文件）。
   *
   * <p>注意：删除后所有引用该文件 id 的业务数据将无法再获取访问地址，
   * 调用方须自行评估引用关系。
   */
  remove(id: string) {
    return request.delete(`/file/${id}`)
  },
}

/**
 * 将文件临时访问地址转换为可直接用于 img src 等场景的完整 URL
 * （补齐 API 前缀，与 request.ts 的 baseURL 约定一致）。
 *
 * @param accessUrl 文件临时访问地址（相对路径）
 * @return 完整访问 URL
 */
export function toFileSrc(accessUrl: string): string {
  const base = import.meta.env.VITE_API_BASE_URL || '/api'
  return base + accessUrl
}
