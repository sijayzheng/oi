/**
 * 通知公告模块类型定义，与后端 cn.sijay.oi.sys.model.notice 包模型严格对应。
 */

/**
 * 创建通知公告入参；对应 SysNoticeCreateCmd。
 */
export interface SysNoticeCreateCmd {
  /**
   * 通知标题；必填。
   */
  title: string
  /**
   * 通知类型；0=通知 1=公告；选填。
   */
  noticeType?: string | null
  /**
   * 通知内容；必填。
   */
  content: string
  /**
   * 发布时间；选填。
   */
  publishTime?: string | null
  /**
   * 是否置顶；true=置顶；选填。
   */
  topMarked?: boolean
}

/**
 * 更新通知公告入参；对应 SysNoticeUpdateCmd。
 */
export interface SysNoticeUpdateCmd {
  /**
   * 通知标题；必填。
   */
  title: string
  /**
   * 通知类型；0=通知 1=公告；选填。
   */
  noticeType?: string | null
  /**
   * 通知内容；必填。
   */
  content: string
  /**
   * 发布时间；选填。
   */
  publishTime?: string | null
  /**
   * 是否置顶；true=置顶；选填。
   */
  topMarked?: boolean
}

/**
 * 通知公告分页条件查询入参；对应 SysNoticeQuery，全部选填。
 */
export interface SysNoticeQuery {
  /**
   * 通知标题过滤；模糊匹配（包含），选填。
   */
  title?: string
  /**
   * 通知类型；0=通知 1=公告过滤；模糊匹配（包含），选填。
   */
  noticeType?: string
  /**
   * 通知内容过滤；模糊匹配（包含），选填。
   */
  content?: string
  /**
   * 是否置顶过滤；true=置顶，精确匹配，选填。
   */
  topMarked?: boolean
}

/**
 * 通知公告出参；对应 SysNoticeVO。
 */
export interface SysNoticeVO {
  /**
   * 通知公告 id；后端 Long 序列化为字符串。
   */
  id: string
  /**
   * 通知标题。
   */
  title: string
  /**
   * 通知类型；0=通知 1=公告。
   */
  noticeType: string | null
  /**
   * 通知内容。
   */
  content: string
  /**
   * 发布时间。
   */
  publishTime: string | null
  /**
   * 是否置顶；true=置顶。
   */
  topMarked: boolean | null
  /**
   * 创建时间；yyyy-MM-dd HH:mm:ss。
   */
  createTime: string
  /**
   * 更新时间；yyyy-MM-dd HH:mm:ss。
   */
  updateTime: string | null
}
