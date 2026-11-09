import { defineStore } from 'pinia'
import { messageApi } from '@/api/message'
import { useAuthStore } from '@/stores/auth'
/**
 * 站内信实时状态：未读角标 + SSE 长连接管理。
 *
 * <p><b>SSE 协议</b>（与后端 MessageSseService 对齐）：
 * <ul>
 *   <li>connected —— 连接建立，data 为初始未读数；</li>
 *   <li>message —— 新消息到达（data 为消息摘要 JSON），本地未读 +1 并通知监听方；</li>
 *   <li>unread —— 未读数权威值变更（data 为数字），直接覆盖本地角标；</li>
 *   <li>withdraw —— 消息被撤回（data 为消息 id），通知监听方刷新列表；</li>
 *   <li>heartbeat —— 心跳保活，无业务动作。</li>
 * </ul>
 *
 * <p><b>重连</b>：连接异常断开时 5 秒后自动重连（令牌失效的 401 响应不会建立
 * EventSource——后端直接拒绝）；退出登录（token 清空）时主动断开。
 *
 * <p><b>监听方式</b>：页面组件 watch {@link lastEvent}（最近一次 message/withdraw
 * 事件快照）刷新列表与轻提示，store 不直接耦合具体页面。
 */
export const useMessageStore = defineStore('message', () => {
  /**
   * 未读消息数（顶栏角标）。
   */
  const unread = ref(0)

  /**
   * 最近一次"新消息"事件快照（SSE message 事件负载）；页面 watch 刷新用。
   */
  const lastEvent = ref<{ id: string, msgType: string, title: string, senderId: string, ts: number } | null>(null)

  /**
   * 最近一次"撤回"事件的消息 id；页面 watch 刷新用。
   */
  const lastWithdrawId = ref<string | null>(null)

  /**
   * SSE 连接实例；null 表示未连接。
   */
  let source: EventSource | null = null

  /**
   * 重连定时器句柄。
   */
  let reconnectTimer: ReturnType<typeof setTimeout> | undefined

  const auth = useAuthStore()

  /**
   * 拉取未读数（角标初始化/本地校正兜底）。
   */
  async function refreshUnread() {
    if (!auth.token)
      return
    try {
      // Number 归一：后端 Long 统一序列化为字符串，el-badge 的 max 逻辑要求 number
      unread.value = Number(await messageApi.unreadCount()) || 0
    } catch {
      // 拉取失败保持现有角标（SSE 连接成功后 connected 事件会再次校准）
    }
  }

  /**
   * 关闭当前 SSE 连接（登录失效/组件卸载场景）。
   */
  function close() {
    if (reconnectTimer) {
      clearTimeout(reconnectTimer)
      reconnectTimer = undefined
    }
    source?.close()
    source = null
  }

  /**
   * 建立 SSE 长连接（已连接时幂等跳过）；断线自动重连。
   */
  function connect() {
    if (source || !auth.token)
      return
    const es = new EventSource(`/api/message/stream?satoken=${encodeURIComponent(auth.token)}`)
    source = es

    es.addEventListener('connected', (e) => {
      unread.value = Number(e.data) || 0
    })
    es.addEventListener('unread', (e) => {
      unread.value = Number(e.data) || 0
    })
    es.addEventListener('message', (e) => {
      try {
        const payload = JSON.parse(e.data) as { id: string, msgType: string, title: string, senderId: string }
        unread.value++
        lastEvent.value = {
          ...payload,
          ts: Date.now(),
        }
      } catch {
        // 非 JSON 负载忽略（协议内不会出现）
      }
    })
    es.addEventListener('withdraw', (e) => {
      lastWithdrawId.value = e.data
    })
    // heartbeat 事件无需处理（保活由服务端完成）

    es.onerror = () => {
      // 断开（网络抖动/服务重启）：关闭当前实例并延时重连
      es.close()
      if (source === es) {
        source = null
        if (auth.token)
          reconnectTimer = setTimeout(connect, 5000)
      }
    }
  }

  // 退出登录（token 清空）时断开连接并重置状态
  watch(() => auth.token, (token) => {
    if (!token) {
      close()
      unread.value = 0
    }
  })

  return {
    unread,
    lastEvent,
    lastWithdrawId,
    refreshUnread,
    connect,
    close,
  }
})
