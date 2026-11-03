import { defineStore } from 'pinia'
/**
 * 认证状态：令牌、用户昵称、头像展示地址与菜单权限树（动态路由数据源）。
 *
 * <p>持久化约定：仅持久化 token/nickname（pinia-plugin-persistedstate，
 * localStorage key=auth）；菜单树不持久化——每次刷新由路由守卫重新拉取，
 * 保证权限变更即时生效且动态路由在刷新后必然重建。
 * 头像地址不持久化——其为带有效期的签名访问地址，进入布局时经 fetchProfile 重新签发。
 */
export const useAuthStore = defineStore('auth', () => {
  /**
   * 登录令牌；空串表示未登录。
   */
  const token = ref('')

  /**
   * 用户昵称；顶栏展示用。
   */
  const nickname = ref('')

  /**
   * 顶栏头像展示地址（签名访问 URL，临时有效）；空串表示未设置头像（回退文字头像）。
   */
  const avatarUrl = ref('')

  /**
   * 当前用户菜单权限树；登录后由路由守卫拉取。
   */
  const menus = ref<SysUserMenuVO[]>([])

  /**
   * 动态路由是否已注册（内存态，不持久化；刷新后由守卫重建）。
   */
  const loaded = ref(false)

  /**
   * 登录：校验由后端完成，成功后保存令牌与昵称。
   */
  async function doLogin(data: SysLoginCmd) {
    const vo = await authApi.login(data)
    token.value = vo.tokenValue
    nickname.value = vo.nickname
  }

  /**
   * 拉取当前用户个人资料：同步昵称并解析头像展示地址（凭文件 id 换取带有效期的签名地址）。
   *
   * <p>调用时机：进入后台布局时（AdminLayout 挂载）、个人中心保存资料后（即时同步顶栏）。
   * 失败不阻断主流程（调用方 catch 后顶栏回退文字头像）。
   */
  async function fetchProfile() {
    const profile: SysProfileVO = await authApi.profile()
    nickname.value = profile.nickname
    avatarUrl.value = profile.avatar
      ? toFileSrc(await fileApi.getUrl(profile.avatar))
      : ''
  }

  /**
   * 拉取当前用户菜单权限树并标记路由已装载（守卫随后据此注册动态路由）。
   *
   * @return 菜单树根节点列表
   */
  async function fetchMenus() {
    menus.value = await authApi.menus()
    loaded.value = true
    return menus.value
  }

  /**
   * 清空认证状态（退出登录/令牌失效时调用）。
   */
  function reset() {
    token.value = ''
    nickname.value = ''
    avatarUrl.value = ''
    menus.value = []
    loaded.value = false
  }

  return {
    token,
    nickname,
    avatarUrl,
    menus,
    loaded,
    doLogin,
    fetchProfile,
    fetchMenus,
    reset,
  }
}, {
  persist: {
    pick: ['token', 'nickname'],
  },
})
