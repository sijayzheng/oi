/**
 * 统一文件管理类型定义；与后端 cn.sijay.oi.file.model.SysFileVO 对齐。
 *
 * <p>调用规范：业务模块上传文件后只持久化 {@link SysFileVO.id}（文件唯一标识），
 * 禁止保存 URL 或存储路径；展示时经 GET /file/{id}/url 换取带有效期的临时访问地址。
 */

/** 文件信息（视图对象） */
export interface SysFileVO {
  /** 文件唯一标识（Long 序列化为字符串）；业务侧持久化引用的唯一凭据 */
  id: string
  /** 原始文件名（含扩展名） */
  originalName: string
  /** 文件扩展名；小写、不含点 */
  extension?: string | null
  /** MIME 类型 */
  contentType?: string | null
  /** 文件大小（字节） */
  size: number
  /** 业务标识；文件归属场景（如 avatar） */
  businessKey?: string | null
  /** 临时访问地址（带签名与有效期，如 /file/access/1?expires=...&sign=...） */
  accessUrl?: string | null
  /** 上传时间（yyyy-MM-dd HH:mm:ss） */
  createTime?: string | null
}

/** 文件分页条件查询入参 */
export interface SysFileQuery {
  /** 原始文件名；模糊匹配 */
  originalName?: string
  /** 业务标识；精确匹配 */
  businessKey?: string
  /** 文件扩展名；精确匹配 */
  extension?: string
}
