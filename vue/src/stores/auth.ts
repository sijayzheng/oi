import { defineStore } from 'pinia'
import { sm2, sm3 } from 'sm-crypto'
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
   * 国密公钥内存缓存（keyId 为键）；避免每次登录重复拉取。
   * 仅缓存公钥等公开信息，不缓存任何密码相关数据。
   */
  const smKeyCache = new Map<string, SmKeyVO>()

  /**
   * 获取国密登录公钥：优先取未过期的内存缓存，否则拉取并校验完整性。
   *
   * <p>完整性校验：前端以 SM3 复算公钥摘要，与后端下发的 digest 比对，
   * 不一致视为公钥在传输链路中被篡改，直接抛错终止登录（不使用被篡改公钥加密）。
   */
  async function getSmKey(): Promise<SmKeyVO> {
    const cached = smKeyCache.get(smKeyCacheCurrentKeyId)
    if (cached && new Date(cached.expireAt).getTime() > Date.now()) {
      return cached
    }
    const key: SmKeyVO = await authApi.sm2PublicKey()
    if (sm3(key.publicKey) !== key.digest) {
      throw new Error('公钥完整性校验失败，请检查网络环境后重试')
    }
    smKeyCache.clear()
    smKeyCache.set(key.keyId, key)
    smKeyCacheCurrentKeyId = key.keyId
    return key
  }

  /**
   * 缓存中当前公钥的密钥标识（Map 取值辅助）。
   */
  let smKeyCacheCurrentKeyId = ''

  /**
   * 登录：先取国密公钥并对密码做 SM2 加密（C1C3C2），再提交密文登录。
   *
   * <p>加密格式约定：sm-crypto 输出的密文不含 04 前缀（C1 首字节），
   * 后端 BouncyCastle 解码要求非压缩点编码，故拼接 '04' 前缀后提交。
   * 全程密码明文只存在于本函数内的临时变量，不写入日志或任何持久化状态。
   */
  async function doLogin(data: SysLoginCmd) {
    const key = await getSmKey()
    const cipher = '04' + sm2.doEncrypt(data.password, key.publicKey, 1)
    const vo = await authApi.login({ username: data.username, password: cipher, keyId: key.keyId })
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
