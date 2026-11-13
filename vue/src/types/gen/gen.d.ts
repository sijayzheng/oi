/**
 * 代码生成模块类型定义，与后端 cn.sijay.oi.gen 包模型严格对应。
 */

/**
 * 可导入的业务表（来自数据库元数据，已排除 gen 元数据表与已导入表）。
 */
export interface ImportableTable {
  /**
   * 业务表名。
   */
  tableName: string
  /**
   * 业务表注释。
   */
  tableComment: string
}

/**
 * 表配置；对应 GenTable 实体。
 */
export interface GenTable {
  /**
   * 配置 id；后端 Long 序列化为字符串。
   */
  id: string
  /**
   * 业务表名。
   */
  tableName: string
  /**
   * 业务表注释。
   */
  tableComment: string | null
  /**
   * 实体类名主干（不含 Sys 前缀与模型后缀）。
   */
  className: string
  /**
   * 业务模块名。
   */
  moduleName: string
  /**
   * 业务名。
   */
  businessName: string
  /**
   * 功能名。
   */
  functionName: string
  /**
   * 生成代码署名。
   */
  author: string
  /**
   * 主子表场景：主表配置 id。
   */
  parentTableId: string | null
  /**
   * 主子表场景：子表外键列名。
   */
  fkColumn: string | null
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
 * 字段配置；对应 GenTableColumn 实体。
 */
export interface GenTableColumn {
  /**
   * 配置 id；后端 Long 序列化为字符串。
   */
  id: string
  /**
   * 所属表配置 id。
   */
  tableId: string
  /**
   * 列名。
   */
  columnName: string
  /**
   * 列注释。
   */
  columnComment: string | null
  /**
   * 列序号。
   */
  columnSort: number
  /**
   * Java/TS 驼峰字段名。
   */
  javaField: string
  /**
   * Java 类型；String/Long/Integer/BigDecimal/Boolean/LocalDateTime。
   */
  javaType: 'String' | 'Long' | 'Integer' | 'BigDecimal' | 'Boolean' | 'LocalDateTime'
  /**
   * TS 类型；string/number/boolean。
   */
  tsType: 'string' | 'number' | 'boolean'
  /**
   * 是否主键。
   */
  isPk: boolean
  /**
   * 是否必填（NOT NULL 且无默认值的列推导为 true）。
   */
  isRequired: boolean
  /**
   * 新增表单是否显示。
   */
  isInsert: boolean
  /**
   * 编辑表单是否显示。
   */
  isEdit: boolean
  /**
   * 列表是否展示。
   */
  isList: boolean
  /**
   * 是否查询条件。
   */
  isQuery: boolean
  /**
   * 查询匹配方式；EQ/LIKE。
   */
  queryType: 'EQ' | 'LIKE'
  /**
   * 表单组件类型。
   */
  htmlType: 'input' | 'textarea' | 'select' | 'radio' | 'radio-button' | 'switch' | 'number' | 'datetime'
  /**
   * select/radio 静态选项 JSON。
   */
  optionsJson: string | null
  /**
   * select/radio 字典编码。
   */
  dictType: string | null
  /**
   * 字符串最小长度 / 数值最小值。
   */
  minLength: number | null
  /**
   * 字符串最大长度。
   */
  maxLength: number | null
  /**
   * 校验正则。
   */
  regex: string | null
  /**
   * 创建时间。
   */
  createTime: string
  /**
   * 更新时间。
   */
  updateTime: string | null
}

/**
 * 表配置详情响应：table + columns 两段式。
 */
export interface GenTableDetail {
  table: GenTable
  columns: GenTableColumn[]
}

/**
 * 字段配置保存入参（对应 GenTableSaveCmd.ColumnCmd；布尔标记以 true/false 提交）。
 */
export interface GenColumnSaveCmd {
  id?: string
  columnName: string
  columnComment: string | null
  columnSort: number
  javaField: string
  javaType: string
  tsType: string
  isPk: boolean
  isRequired: boolean
  isInsert: boolean
  isEdit: boolean
  isList: boolean
  isQuery: boolean
  queryType: string
  htmlType: string
  optionsJson: string | null
  dictType: string | null
  minLength: number | null
  maxLength: number | null
  regex: string | null
}

/**
 * 表配置保存入参（对应 GenTableSaveCmd）。
 */
export interface GenTableSaveCmd {
  tableName: string
  className: string
  moduleName: string
  businessName: string
  functionName: string
  author: string
  columns: GenColumnSaveCmd[]
}
