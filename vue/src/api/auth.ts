import request from '@/utils/request'

/**
 * 认证 API；接口清单与后端 AuthController 对齐。
 */
export const authApi = {
  /**
   * 用户登录。
   *
   * @param data 登录入参；username/password 必填
   * @return 令牌对与用户昵称；失败返回 601003（凭据错误）/601004（账号禁用）
   */
  login(data: SysLoginCmd) {
    return request.post<SysLoginVO>('/auth/login', data)
  },

  /**
   * 退出登录；作废当前令牌。
   */
  logout() {
    return request.post('/auth/logout')
  },

  /**
   * 获取当前登录用户的菜单权限树（动态路由数据源）。
   *
   * @return 菜单树根节点列表；无角色/未授权时为空数组
   */
  menus() {
    return request.get<SysUserMenuVO[]>('/auth/menus')
  },

  /**
   * 获取当前登录用户的个人资料。
   */
  profile() {
    return request.get<SysProfileVO>('/auth/profile')
  },

  /**
   * 修改个人资料（昵称/手机/邮箱/头像）。
   *
   * @param data 资料修改入参；昵称必填
   * @return 更新后的个人资料
   */
  updateProfile(data: SysProfileUpdateCmd) {
    return request.put<SysProfileVO>('/auth/profile', data)
  },

  /**
   * 修改密码；成功后当前会话被注销，需重新登录。
   */
  changePassword(data: SysChangePasswordCmd) {
    return request.post('/auth/password', data)
  },

  /**
   * 上传头像（委托统一文件管理服务，multipart 字段名 file）。
   *
   * <p>调用规范：只持久化返回的文件 id（随「保存修改」提交到 profile.avatar），
   * 禁止保存 URL 或路径；展示时经 fileApi.getUrl 换取临时访问地址。
   *
   * @param file 图片文件；jpg/jpeg/png/gif/webp
   * @return 文件信息（含唯一 id 与临时访问地址）
   */
  uploadAvatar(file: File) {
    const formData = new FormData()
    formData.append('file', file)
    return request.post<SysFileVO>('/auth/avatar', formData, {
      timeout: 30_000,
    })
  },
}
