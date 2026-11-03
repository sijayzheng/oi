/**
 * 操作日志类型定义；与后端 cn.sijay.oi.sys.model.SysLogVO 对齐。
 */

/** 日志级别联合类型 */
export type LogLevel = 'DEBUG' | 'INFO' | 'WARN' | 'ERROR'

/** 操作日志（视图对象） */
export interface SysLogVO {
  /** 日志 id（Long 序列化为字符串） */
  id: string
  /** 日志级别；DEBUG/INFO/WARN/ERROR */
  level: LogLevel
  /** 业务模块标识（如 auth/online） */
  module: string
  /** 操作动作（如 login/kick） */
  action: string
  /** 操作人登录名快照 */
  username?: string | null
  /** 操作人用户 id（Long 序列化为字符串） */
  userId?: string | null
  /** 客户端 IP */
  ip?: string | null
  /** 请求路径 */
  uri?: string | null
  /** HTTP 方法 */
  method?: string | null
  /** 请求 ID（短追踪标识） */
  requestId?: string | null
  /** 执行结果；SUCCESS/FAIL */
  result: 'SUCCESS' | 'FAIL'
  /** 日志内容（成功=请求参数摘要，失败=失败原因）；列表展示用 */
  content?: string | null
  /** 请求参数 JSON；详情查看用 */
  params?: string | null
  /** 失败原因摘要；详情查看用 */
  errorMsg?: string | null
  /** 记录时间（yyyy-MM-dd HH:mm:ss） */
  createTime: string
}

/** 操作日志分页条件查询入参 */
export interface SysLogQuery {
  /** 日志级别；空为全部 */
  level?: LogLevel | ''
  /** 关键词（匹配内容/动作/模块/操作人/路径/请求 ID） */
  keyword?: string
  /** 开始时间（含，ISO 格式 yyyy-MM-DDTHH:mm:ss） */
  startTime?: string
  /** 结束时间（含，ISO 格式） */
  endTime?: string
}
