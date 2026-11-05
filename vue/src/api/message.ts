import request from '@/utils/request'

/**
 * 站内信 API；接口清单与后端 SysMessageController 对齐。
 *
 * <p>实时推送（SSE）由 {@link useMessageStore} 独立维护长连接，
 * 本对象只承载常规请求。
 */
export const messageApi = {
  /**
   * 当前用户未读数（点对点未读 + 未读广播）。
   */
  unreadCount() {
    return request.get<number>('/message/unread-count')
  },

  /**
   * 收件箱分页（点对点回执 + 公告广播统一视图）。
   */
  inbox(params: MessageQuery) {
    return request.page<Result<MessageItem[]>>('/message/inbox', params)
  },

  /**
   * 发件箱分页（本人发送的消息，含已撤回状态展示）。
   */
  sent(params: MessageQuery) {
    return request.page<Result<MessageItem[]>>('/message/sent', params)
  },

  /**
   * 消息详情（接收方首次查看自动标记已读）。
   */
  detail(id: string) {
    return request.get<MessageItem>(`/message/${id}`)
  },

  /**
   * 发送消息（LETTER 任意登录用户；NOTICE/BROADCAST 仅管理员）。
   *
   * @return 消息 id
   */
  send(data: MessageSendCmd) {
    return request.post<number>('/message', data)
  },

  /**
   * 批量标记已读。
   *
   * @return 实际新标记条数
   */
  markRead(ids: string[]) {
    return request.put<number>('/message/read', { ids })
  },

  /**
   * 全部标记已读。
   */
  markAllRead() {
    return request.put<number>('/message/read-all')
  },

  /**
   * 批量归档/取消归档。
   */
  archive(ids: string[], archived: number) {
    return request.put<number>('/message/archive', { ids, archived })
  },

  /**
   * 批量删除（点对点软删回执，广播落用户删除状态）。
   */
  trash(ids: string[]) {
    return request.put<number>('/message/trash', { ids })
  },

  /**
   * 撤回消息（仅发送人，发送后 5 分钟内；幂等）。
   */
  withdraw(id: string) {
    return request.post<void>(`/message/withdraw/${id}`)
  },
}
