/**
 * 站内信类型定义；与后端 MessageVO / MessageSendCmd 契约对齐。
 */

/**
 * 消息类型；NOTICE=系统通知 LETTER=私信 BROADCAST=公告广播。
 */
type MessageType = 'NOTICE' | 'LETTER' | 'BROADCAST'

/**
 * 消息状态；SENT=已发送 WITHDRAWN=已撤回。
 */
type MessageStatus = 'SENT' | 'WITHDRAWN'

/**
 * 站内信视图对象（收件箱/发件箱/详情通用）。
 */
interface MessageItem {
  /** 消息 id */
  id: string
  /** 消息类型 */
  msgType: MessageType
  /** 发送人 id；0=系统 */
  senderId: string
  /** 接收人 id；广播为 null */
  receiverId: string | null
  /** 消息标题 */
  title: string
  /** 消息内容 */
  content: string
  /** 附件文件 id 列表 */
  attachmentIds: string[]
  /** 消息状态 */
  status: MessageStatus
  /** 发送时间 */
  sendTime: string
  /** 已读状态（收件箱视角）；发件箱视角为 null */
  readStatus: number | null
  /** 归档标记（收件箱视角）；发件箱视角为 null */
  archived: number | null
  /** 当前登录人是否可撤回 */
  withdrawable: boolean
}

/**
 * 发送消息入参。
 */
interface MessageSendCmd {
  /** 消息类型 */
  msgType: MessageType
  /** 接收人用户名；点对点消息必填，广播忽略 */
  receiverUsername?: string
  /** 消息标题 */
  title: string
  /** 消息内容 */
  content: string
  /** 附件文件 id 列表 */
  attachmentIds?: string[]
}

/**
 * 收件箱/发件箱查询参数。
 */
interface MessageQuery {
  /** 消息类型过滤 */
  msgType?: MessageType | ''
  /** 已读状态过滤（收件箱） */
  readStatus?: number | null
  /** 归档过滤（0=未归档 1=已归档） */
  archived?: number | null
  /** 标题/内容关键词 */
  keyword?: string
  /** 页码（1 起） */
  pageNum?: number
  /** 页大小 */
  pageSize?: number
}

/**
 * 文件元信息（附件展示用）。
 */
interface MessageAttachment {
  /** 文件 id */
  id: string
  /** 原始文件名 */
  originalName: string
  /** 访问地址（带签名） */
  accessUrl: string
  /** 是否图片（内嵌展示） */
  image: boolean
}
