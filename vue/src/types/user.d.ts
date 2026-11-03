/**
 * 用户模块类型定义，与后端 cn.sijay.oi.sys.model.user 包模型严格对应。
 */

/**
 * 创建用户入参；对应 SysUserCreateCmd。
 */
export interface SysUserCreateCmd {
  /**
   * 登录名；必填，2~50 位字母/数字/下划线，与软删组合唯一。
   */
  username: string
  /**
   * 登录密码；必填，明文长度 6~100。
   */
  password: string
  /**
   * 用户昵称；选填，最长 50 字符。
   */
  nickname?: string | null
  /**
   * 手机号；选填，最长 20 字符。
   */
  mobile?: string | null
  /**
   * 邮箱；选填，最长 100 字符，须符合邮箱格式。
   */
  email?: string | null
  /**
   * 启用状态；必填，true=正常（可登录）/ false=禁用（拒绝登录）。
   */
  enabled: boolean
  /**
   * 备注；选填，最长 255 字符。
   */
  remark?: string | null
}

/**
 * 更新用户入参；对应 SysUserUpdateCmd（username/password 不可通过本接口修改）。
 */
export interface SysUserUpdateCmd {
  /**
   * 用户昵称；必填（传空串表示清空昵称），最长 50 字符。
   */
  nickname: string
  /**
   * 手机号；选填，最长 20 字符。
   */
  mobile?: string | null
  /**
   * 邮箱；选填，最长 100 字符，须符合邮箱格式。
   */
  email?: string | null
  /**
   * 启用状态；必填，true=正常（可登录）/ false=禁用（拒绝登录）。
   */
  enabled: boolean
  /**
   * 备注；选填，最长 255 字符。
   */
  remark?: string | null
}

/**
 * 用户分页条件查询入参；对应 SysUserQuery，全部选填（未设置的字段不参与匹配）。
 */
export interface SysUserQuery {
  /**
   * 登录名过滤；模糊匹配（包含），选填。
   */
  username?: string
  /**
   * 昵称过滤；模糊匹配（包含），选填。
   */
  nickname?: string
  /**
   * 手机号过滤；模糊匹配（包含），选填。
   */
  mobile?: string
  /**
   * 邮箱过滤；模糊匹配（包含），选填。
   */
  email?: string
  /**
   * 启用状态过滤；精确匹配，选填。
   */
  enabled?: boolean
}

/**
 * 用户出参；对应 SysUserVO（不含 password，密码密文不出服务层）。
 */
export interface SysUserVO {
  /**
   * 用户 id；后端 Long 序列化为字符串。
   */
  id: string
  /**
   * 登录名。
   */
  username: string
  /**
   * 用户昵称。
   */
  nickname: string | null
  /**
   * 手机号。
   */
  mobile: string | null
  /**
   * 邮箱。
   */
  email: string | null
  /**
   * 启用状态；true=正常（可登录）/ false=禁用（拒绝登录）。
   */
  enabled: boolean
  /**
   * 备注。
   */
  remark: string | null
  /**
   * 创建时间；yyyy-MM-dd HH:mm:ss。
   */
  createTime: string
  /**
   * 更新时间；yyyy-MM-dd HH:mm:ss。
   */
  updateTime: string | null
}
