/**
 * ${functionName}模块类型定义，与后端 cn.sijay.oi.${moduleName}.model.${businessName} 包模型严格对应。
 */

/**
 * 创建${functionName}入参；对应 Sys${className}CreateCmd。
 */
export interface Sys${className}CreateCmd {
<#list insertColumns as col>
  /**
   * ${col.label}；<#if col.required>必填<#else>选填</#if>。
   */
  ${col.javaField}<#if col.required>: ${col.tsType}<#else>?: ${col.tsType} | null</#if>
</#list>
}

/**
 * 更新${functionName}入参；对应 Sys${className}UpdateCmd。
 */
export interface Sys${className}UpdateCmd {
<#list editColumns as col>
  /**
   * ${col.label}；<#if col.required>必填<#else>选填</#if>。
   */
  ${col.javaField}<#if col.required>: ${col.tsType}<#else>?: ${col.tsType} | null</#if>
</#list>
}

/**
 * ${functionName}分页条件查询入参；对应 Sys${className}Query，全部选填。
 */
export interface Sys${className}Query {
<#list queryColumns as col>
  /**
   * ${col.label}过滤；<#if col.likeQuery>模糊匹配（包含）<#else>精确匹配</#if>，选填。
   */
  ${col.javaField}?: ${col.tsType}
</#list>
}

/**
 * ${functionName}出参；对应 Sys${className}VO。
 */
export interface Sys${className}VO {
  /**
   * ${functionName} id；后端 Long 序列化为字符串。
   */
  id: string
<#list columns as col><#if !col.pk && !col.systemHidden && !col.auditTime>
  /**
   * ${col.label}。
   */
  ${col.javaField}: ${col.tsType}<#if !col.required> | null</#if>
</#if></#list>
  /**
   * 创建时间；yyyy-MM-dd HH:mm:ss。
   */
  createTime: string
  /**
   * 更新时间；yyyy-MM-dd HH:mm:ss。
   */
  updateTime: string | null
}
