import request from '@/utils/request'

/**
 * 在线用户管理 API；接口清单与后端 OnlineController 对齐。
 *
 * <p>权限约定：本模块全部接口仅系统管理员可用（后端硬校验，非管理员返回 403）。
 */
export const onlineApi = {
  /**
   * 查询在线用户实时列表。
   *
   * @return 在线用户列表（含用户名/昵称/角色/登录 IP/登录时间/在线时长）
   */
  list() {
    return request.get<OnlineUserVO[]>('/online/list')
  },

  /**
   * 强制下线目标用户。
   *
   * <p>作废目标用户全部登录令牌，被踢用户自动跳转登录页；操作写入服务端日志留痕。
   *
   * @param userId 目标用户 id
   */
  kick(userId: string) {
    return request.post(`/online/kick/${userId}`)
  },
}
