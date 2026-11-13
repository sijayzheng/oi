/**
 * 参数配置模块类型定义，与后端 cn.sijay.oi.sys.model.config 包模型严格对应。
 */

/**
 * 配置值类型；对应后端 ConfigValueType 枚举，决定 configValue 的解析方式与编辑控件
 * （STRING 输入框 / NUMBER 数字框 / BOOLEAN 开关 / JSON 代码编辑器）。
 */
export type ConfigValueType = 'STRING' | 'NUMBER' | 'BOOLEAN' | 'JSON'

/**
 * 创建参数配置入参；对应 SysConfigCreateCmd。
 */
export interface SysConfigCreateCmd {
  /**
   * 参数键；必填，点分命名格式（如 site.name），与软删组合唯一，创建后禁止修改。
   */
  configKey: string
  /**
   * 参数值；必填，统一按字符串存储，按 valueType 解析，最长 2000 字符。
   */
  configValue: string
  /**
   * 值类型；必填。
   */
  valueType: ConfigValueType
  /**
   * 参数名称；必填，管理界面展示用，最长 100 字符。
   */
  name: string
  /**
   * 备注；选填，最长 255 字符。
   */
  remark?: string | null
}

/**
 * 更新参数配置入参；对应 SysConfigUpdateCmd（configKey 不可通过本接口修改）。
 */
export interface SysConfigUpdateCmd {
  /**
   * 参数值；必填，统一按字符串存储，最长 2000 字符。
   */
  configValue: string
  /**
   * 值类型；必填。
   */
  valueType: ConfigValueType
  /**
   * 参数名称；必填，最长 100 字符。
   */
  name: string
  /**
   * 备注；选填，最长 255 字符。
   */
  remark?: string | null
}

/**
 * 参数配置分页条件查询入参；对应 SysConfigQuery，全部选填。
 */
export interface SysConfigQuery {
  /**
   * 参数键过滤；模糊匹配（包含），选填。
   */
  configKey?: string
  /**
   * 参数名称过滤；模糊匹配（包含），选填。
   */
  name?: string
  /**
   * 值类型过滤；精确匹配，选填。
   */
  valueType?: ConfigValueType
}

/**
 * 参数配置出参；对应 SysConfigVO。
 */
export interface SysConfigVO {
  /**
   * 配置 id；后端 Long 序列化为字符串。
   */
  id: string
  /**
   * 参数键。
   */
  configKey: string
  /**
   * 参数值（统一字符串形态）。
   */
  configValue: string
  /**
   * 值类型。
   */
  valueType: ConfigValueType
  /**
   * 值类型中文描述；字符串 / 数字 / 布尔 / 结构化对象。
   */
  valueTypeDesc: string | null
  /**
   * 参数名称。
   */
  name: string
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
