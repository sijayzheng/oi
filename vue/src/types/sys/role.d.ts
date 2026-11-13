/**
 * 角色模块类型定义，与后端 cn.sijay.oi.sys.model.role 包模型严格对应。
 */

/**
 * 创建角色入参；对应 SysRoleCreateCmd。
 */
export interface SysRoleCreateCmd {
  /**
   * 角色编码；必填，2~50 位字母/数字/下划线/连字符，与软删组合唯一，创建后不建议修改。
   */
  code: string
  /**
   * 角色显示名；必填，最长 50 字符。
   */
  name: string
  /**
   * 排序号；选填，非负整数，越小越靠前，不传默认 0。
   */
  sort?: number
  /**
   * 启用状态；必填，true=正常 / false=禁用（禁用后该角色的权限不再生效）。
   */
  enabled: boolean
  /**
   * 备注；选填，最长 255 字符。
   */
  remark?: string | null
}

/**
 * 更新角色入参；对应 SysRoleUpdateCmd（code 不可通过本接口修改）。
 */
export interface SysRoleUpdateCmd {
  /**
   * 角色显示名；必填，最长 50 字符。
   */
  name: string
  /**
   * 排序号；必填，非负整数。
   */
  sort: number
  /**
   * 启用状态；必填，true=正常 / false=禁用（禁用后该角色的权限不再生效）。
   */
  enabled: boolean
  /**
   * 备注；选填，最长 255 字符。
   */
  remark?: string | null
}

/**
 * 角色分页条件查询入参；对应 SysRoleQuery，全部选填。
 */
export interface SysRoleQuery {
  /**
   * 角色编码过滤；模糊匹配（包含），选填。
   */
  code?: string
  /**
   * 角色名称过滤；模糊匹配（包含），选填。
   */
  name?: string
  /**
   * 启用状态过滤；精确匹配，选填。
   */
  enabled?: boolean
}

/**
 * 角色出参；对应 SysRoleVO。
 */
export interface SysRoleVO {
  /**
   * 角色 id；后端 Long 序列化为字符串。
   */
  id: string
  /**
   * 角色编码。
   */
  code: string
  /**
   * 角色显示名。
   */
  name: string
  /**
   * 排序号。
   */
  sort: number
  /**
   * 启用状态；true=正常 / false=禁用（禁用后该角色的权限不再生效）。
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
