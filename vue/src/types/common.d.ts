/**
 * 通用类型定义，与后端 cn.sijay.oi.common 包模型严格对齐。
 *
 * <p>序列化约定（由后端全局 Jackson 配置决定，前端类型据此映射）：
 * <ul>
 *   <li>Long/BigInteger 统一序列化为字符串（防 JavaScript 数值精度丢失），因此 id/total 等大整数字段为 string；</li>
 *   <li>时间字段统一序列化为 yyyy-MM-dd HH:mm:ss 格式字符串。</li>
 * </ul>
 */

/**
 * 分页与排序参数；对应后端 PageQuery record。
 *
 * <p>全部选填——后端构造期归一化：页码下限 1、每页条数区间 [1, 100]、未传默认降序；
 * 排序字段非法（注入风险）返回业务码 600001。
 */
export interface PageQuery {
  /**
   * 页码；从 1 开始，未传或越界后端归一化为 1。
   */
  pageNum?: number
  /**
   * 每页条数；未传默认 10，上限 100。
   */
  pageSize?: number
  /**
   * 排序字段；实体属性名（如 createTime），未传为数据库自然顺序。
   */
  sortField?: string
  /**
   * 是否降序；true=DESC（默认），false=ASC。
   */
  desc?: boolean
}

/**
 * 统一响应结构；对应后端 Result<T> record。
 */
export interface Result<T = unknown> {
  /**
   * 状态码；200=成功，400=参数校验失败，401=未认证，403=无权限，500=系统异常，>=600=业务错误（6XXXXX 按模块分段）。
   */
  code: number
  /**
   * 提示消息；可直接 toast 展示给用户。
   */
  message: string
  /**
   * 业务数据；无数据返回时为 null。
   */
  data: T
  /**
   * 分页总记录数；仅分页查询接口返回（后端 Long 序列化为字符串）。
   */
  total?: string | null
}
