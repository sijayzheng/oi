/**
 * 字典模块类型定义（类型 + 项双表），与后端 cn.sijay.oi.sys.model.dict 包模型严格对应。
 */

/**
 * 下拉选择框选项；对应后端 SelectOption<T> record（value=itemCode、label=itemLabel）。
 */
export interface SelectOption<T = string> {
  /**
   * 选项提交值；与字典项 itemCode 对应。
   */
  value: T
  /**
   * 选项显示文本。
   */
  label: string
}

/**
 * 创建字典类型入参；对应 SysDictTypeCreateCmd。
 */
export interface SysDictTypeCreateCmd {
  /**
   * 字典编码；必填，小写下划线格式（如 user_status），与软删组合唯一，创建后禁止修改。
   */
  dictType: string
  /**
   * 字典名称；必填，最长 100 字符。
   */
  name: string
  /**
   * 启用状态；必填，true=正常 / false=禁用。
   */
  enabled: boolean
  /**
   * 备注；选填，最长 255 字符。
   */
  remark?: string | null
}

/**
 * 更新字典类型入参；对应 SysDictTypeUpdateCmd（dictType 被字典项冗余引用，不可修改）。
 */
export interface SysDictTypeUpdateCmd {
  /**
   * 字典名称；必填，最长 100 字符。
   */
  name: string
  /**
   * 启用状态；必填，true=正常 / false=禁用。
   */
  enabled: boolean
  /**
   * 备注；选填，最长 255 字符。
   */
  remark?: string | null
}

/**
 * 字典类型分页条件查询入参；对应 SysDictTypeQuery，全部选填。
 */
export interface SysDictTypeQuery {
  /**
   * 字典编码过滤；模糊匹配（包含），选填。
   */
  dictType?: string
  /**
   * 字典名称过滤；模糊匹配（包含），选填。
   */
  name?: string
  /**
   * 启用状态过滤；精确匹配，选填。
   */
  enabled?: boolean
}

/**
 * 字典类型出参；对应 SysDictTypeVO。
 */
export interface SysDictTypeVO {
  /**
   * 字典类型 id；后端 Long 序列化为字符串。
   */
  id: string
  /**
   * 字典编码。
   */
  dictType: string
  /**
   * 字典名称。
   */
  name: string
  /**
   * 启用状态；true=正常 / false=禁用。
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

/**
 * 创建字典项入参；对应 SysDictItemCreateCmd。
 */
export interface SysDictItemCreateCmd {
  /**
   * 所属字典编码；必填，须为已存在的字典类型（不存在返回 605002）。
   */
  dictType: string
  /**
   * 项编码；必填，业务实际存储值，同字典内唯一（重复返回 605003），创建后不可修改。
   */
  itemCode: string
  /**
   * 项显示名；必填，前端展示文案，最长 100 字符。
   */
  itemLabel: string
  /**
   * 排序号；选填，非负整数，越小越靠前，不传默认 0。
   */
  sort?: number
  /**
   * 启用状态；必填，true=正常 / false=禁用。
   */
  enabled: boolean
  /**
   * 备注；选填，最长 255 字符。
   */
  remark?: string | null
}

/**
 * 更新字典项入参；对应 SysDictItemUpdateCmd（dictType/itemCode 为业务存储值，不可修改）。
 */
export interface SysDictItemUpdateCmd {
  /**
   * 项显示名；必填，最长 100 字符。
   */
  itemLabel: string
  /**
   * 排序号；必填，非负整数。
   */
  sort: number
  /**
   * 启用状态；必填，true=正常 / false=禁用。
   */
  enabled: boolean
  /**
   * 备注；选填，最长 255 字符。
   */
  remark?: string | null
}

/**
 * 字典项分页条件查询入参；对应 SysDictItemQuery，全部选填。
 */
export interface SysDictItemQuery {
  /**
   * 所属字典编码过滤；精确匹配，选填。
   */
  dictType?: string
  /**
   * 项显示名过滤；模糊匹配（包含），选填。
   */
  itemLabel?: string
  /**
   * 启用状态过滤；精确匹配，选填。
   */
  enabled?: boolean
}

/**
 * 字典项出参；对应 SysDictItemVO（本表无审计字段，不输出时间字段）。
 */
export interface SysDictItemVO {
  /**
   * 字典项 id；后端 Long 序列化为字符串。
   */
  id: string
  /**
   * 所属字典编码。
   */
  dictType: string
  /**
   * 项编码（业务实际存储值）。
   */
  itemCode: string
  /**
   * 项显示名。
   */
  itemLabel: string
  /**
   * 排序号。
   */
  sort: number
  /**
   * 启用状态；true=正常 / false=禁用。
   */
  enabled: boolean
  /**
   * 备注。
   */
  remark: string | null
}
