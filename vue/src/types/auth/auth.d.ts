/**
 * 认证模块类型定义，与后端 cn.sijay.oi.auth 包模型严格对应。
 */
import type { MenuType } from './menu'

/**
 * 登录入参；对应 SysLoginCmd。
 */
export interface SysLoginCmd {
  /**
   * 登录名；必填。
   */
  username: string
  /**
   * 登录密码；必填。携带 keyId 时为 SM2 密文（C1C3C2 格式，04 前缀 hex 串），
   * 未携带时为明文（兼容模式，传输安全依赖 HTTPS）。
   */
  password: string
  /**
   * 国密密钥标识；携带时表示 password 为 SM2 密文，后端按密钥标识匹配私钥解密。
   */
  keyId?: string
}

/**
 * 国密登录公钥下发信息；对应 SmKeyVO。
 */
export interface SmKeyVO {
  /**
   * 密钥标识；登录提交时回传，供后端定位配套私钥（支持轮换期间在途密钥）。
   */
  keyId: string
  /**
   * SM2 公钥（非压缩点 hex 串，04 前缀，130 字符）。
   */
  publicKey: string
  /**
   * 公钥完整性摘要；SM3(publicKey UTF-8) hex 串，前端复算比对防传输篡改。
   */
  digest: string
  /**
   * 公钥过期时间（ISO-8601）；过期后需重新拉取。
   */
  expireAt: string
}

/**
 * 登录出参；对应 SysLoginVO。
 */
export interface SysLoginVO {
  /**
   * 令牌名称；即受保护接口需携带的请求头名（默认 satoken）。
   */
  tokenName: string
  /**
   * 令牌值；后续请求以该值携带请求头访问受保护接口。
   */
  tokenValue: string
  /**
   * 用户昵称；顶栏展示用。
   */
  nickname: string
}

/**
 * 当前用户菜单树节点；对应 SysUserMenuVO，动态路由与侧边栏的数据源。
 */
export interface SysUserMenuVO {
  /**
   * 菜单 id；后端 Long 序列化为字符串。
   */
  id: string
  /**
   * 菜单/目录名称。
   */
  name: string
  /**
   * 节点类型；DIRECTORY=目录 / MENU=菜单 / BUTTON=按钮。
   */
  type: MenuType
  /**
   * 前端路由路径；DIRECTORY/MENU 使用。
   */
  path: string | null
  /**
   * 前端组件路径；MENU 使用（相对 src/views 的视图路径，如 system/UserView）。
   */
  component: string | null
  /**
   * 权限标识；BUTTON 使用（如 user:add）。
   */
  perms: string | null
  /**
   * 图标标识；Element Plus 图标组件名。
   */
  icon: string | null
  /**
   * 显示状态；true=显示 / false=隐藏（false 的节点整棵子树在服务端剪枝，
   * 不会出现在返回结果中：不渲染、不生成路由、不参与权限计算）。
   */
  visible: boolean
  /**
   * 子节点列表；叶子节点为空数组。
   */
  children: SysUserMenuVO[]
}

/**
 * 个人资料出参；对应 SysProfileVO。
 */
export interface SysProfileVO {
  /**
   * 用户 id；后端 Long 序列化为字符串。
   */
  id: string
  /**
   * 登录名；不可修改。
   */
  username: string
  /**
   * 用户昵称。
   */
  nickname: string
  /**
   * 手机号。
   */
  mobile: string | null
  /**
   * 邮箱。
   */
  email: string | null
  /**
   * 头像 URL。
   */
  avatar: string | null
}

/**
 * 个人资料修改入参；对应 SysProfileUpdateCmd。
 */
export interface SysProfileUpdateCmd {
  /**
   * 用户昵称；必填。
   */
  nickname: string
  /**
   * 手机号；选填。
   */
  mobile?: string | null
  /**
   * 邮箱；选填，须符合邮箱格式。
   */
  email?: string | null
  /**
   * 头像 URL；选填（先经 /auth/avatar 上传取得）。
   */
  avatar?: string | null
}

/**
 * 修改密码入参；对应 SysChangePasswordCmd。
 */
export interface SysChangePasswordCmd {
  /**
   * 原密码；必填。
   */
  oldPassword: string
  /**
   * 新密码；必填，6~100 位且同时包含字母和数字。
   */
  newPassword: string
  /**
   * 确认新密码；必填，须与新密码一致。
   */
  confirmPassword: string
}
