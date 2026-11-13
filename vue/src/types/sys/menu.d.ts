/**
 * 菜单模块类型定义，与后端 cn.sijay.oi.sys.model.menu 包模型严格对应。
 */

/**
 * 菜单节点类型；对应后端 MenuType 枚举（目录 → 菜单 → 按钮三级层级）。
 */
export type MenuType = 'DIRECTORY' | 'MENU' | 'BUTTON'

/**
 * 创建菜单入参；对应 SysMenuCreateCmd。
 */
export interface SysMenuCreateCmd {
  /**
   * 父菜单 id；不传=根节点；非空时须指向已存在的菜单且层级合法
   * （目录必须为根、菜单父须为目录、按钮父须为菜单，非法返回 603003）。
   * 来自 VO 的 id 为字符串，可直接传入。
   */
  parentId?: number | string
  /**
   * 菜单/按钮名称；必填，最长 50 字符。
   */
  name: string
  /**
   * 节点类型；必填。
   */
  type: MenuType
  /**
   * 前端路由路径；选填，最长 200 字符，DIRECTORY/MENU 使用。
   */
  path?: string | null
  /**
   * 前端组件路径；选填，最长 200 字符，MENU 使用。
   */
  component?: string | null
  /**
   * 权限标识；选填，最长 100 字符，BUTTON 使用（如 user:add）。
   */
  perms?: string | null
  /**
   * 图标标识；选填，最长 50 字符。
   */
  icon?: string | null
  /**
   * 同级排序号；必填，非负整数，越小越靠前。
   */
  sort: number
  /**
   * 显示状态；必填，true=显示 / false=隐藏（隐藏菜单不下发前端）。
   */
  visible: boolean
  /**
   * 启用状态；必填，true=正常 / false=禁用（FALSE 时整棵子树权限不生效）。
   */
  enabled: boolean
}

/**
 * 更新菜单入参；对应 SysMenuUpdateCmd（parentId/type 不可通过本接口修改，防环）。
 */
export interface SysMenuUpdateCmd {
  /**
   * 菜单/按钮名称；必填，最长 50 字符。
   */
  name: string
  /**
   * 前端路由路径；选填，最长 200 字符。
   */
  path?: string | null
  /**
   * 前端组件路径；选填，最长 200 字符。
   */
  component?: string | null
  /**
   * 权限标识；选填，最长 100 字符。
   */
  perms?: string | null
  /**
   * 图标标识；选填，最长 50 字符。
   */
  icon?: string | null
  /**
   * 同级排序号；必填，非负整数。
   */
  sort: number
  /**
   * 显示状态；必填，true=显示 / false=隐藏（隐藏菜单不下发前端）。
   */
  visible: boolean
  /**
   * 启用状态；必填，true=正常 / false=禁用（FALSE 时整棵子树权限不生效）。
   */
  enabled: boolean
}

/**
 * 菜单分页条件查询入参；对应 SysMenuQuery，全部选填。
 */
export interface SysMenuQuery {
  /**
   * 菜单名称过滤；模糊匹配（包含），选填。
   */
  name?: string
  /**
   * 节点类型过滤；精确匹配，选填。
   */
  type?: MenuType
  /**
   * 启用状态过滤；精确匹配，选填。
   */
  enabled?: boolean
}

/**
 * 菜单出参；对应 SysMenuVO。
 */
export interface SysMenuVO {
  /**
   * 菜单 id；后端 Long 序列化为字符串。
   */
  id: string
  /**
   * 父菜单 id；后端 Long 序列化为字符串，"0"=根节点。
   */
  parentId: string
  /**
   * 菜单/按钮名称。
   */
  name: string
  /**
   * 节点类型。
   */
  type: MenuType
  /**
   * 节点类型中文描述；目录 / 菜单 / 按钮。
   */
  typeDesc: string | null
  /**
   * 前端路由路径。
   */
  path: string | null
  /**
   * 前端组件路径。
   */
  component: string | null
  /**
   * 权限标识。
   */
  perms: string | null
  /**
   * 图标标识。
   */
  icon: string | null
  /**
   * 同级排序号。
   */
  sort: number
  /**
   * 显示状态；true=显示 / false=隐藏（隐藏菜单整棵子树不下发前端）。
   */
  visible: boolean
  /**
   * 启用状态；true=正常 / false=禁用（FALSE 时整棵子树权限不生效）。
   */
  enabled: boolean
  /**
   * 创建时间；yyyy-MM-dd HH:mm:ss。
   */
  createTime: string
  /**
   * 更新时间；yyyy-MM-dd HH:mm:ss。
   */
  updateTime: string | null
}

/**
 * 通用树形节点；对应后端 TreeNode<ID> record（菜单树接口返回结构）。
 *
 * <p>根节点 parentId 为 null；children 永不为 null（叶子节点为空数组）；
 * disabled=true 表示状态为 DISABLED 的节点。
 */
export interface TreeNode<ID = string> {
  /**
   * 节点 id；菜单树中为菜单 id（字符串）。
   */
  id: ID
  /**
   * 节点显示文本；菜单名称。
   */
  label: string
  /**
   * 父节点 id；根节点为 null。
   */
  parentId: ID | null
  /**
   * 是否禁用；禁用节点前端展示但不可选中。
   */
  disabled: boolean
  /**
   * 子节点列表；叶子节点为空数组。
   */
  children: TreeNode<ID>[]
}
