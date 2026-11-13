/**
 * 在线用户管理类型定义；与后端 cn.sijay.oi.online.model.OnlineUserVO 对齐。
 */

/** 在线用户（视图对象） */
export interface OnlineUserVO {
  /** 用户 id（Long 序列化为字符串）；强制下线目标标识 */
  userId: string
  /** 登录名 */
  username: string
  /** 用户昵称 */
  nickname: string
  /** 用户角色名集合（顿号分隔）；无角色为空串 */
  roles: string
  /** 登录 IP */
  loginIp: string
  /** 登录时间（yyyy-MM-dd HH:mm:ss） */
  loginTime: string
  /** 在线时长（分钟） */
  onlineMinutes: number
}
