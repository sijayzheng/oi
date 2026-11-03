import type { RouteRecordRaw } from 'vue-router'
import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

/**
 * 路由元信息扩展：动态路由与守卫使用的核心元字段。
 */
declare module 'vue-router' {

  interface RouteMeta {
    /** 页面标题；顶栏与 document.title 使用 */
    title?: string
    /** 菜单图标；Element Plus 图标组件名 */
    icon?: string
    /** 权限标识；预留按钮级权限判断 */
    perms?: string
    /** 公开路由；无需登录态即可访问（登录页） */
    public?: boolean
  }
}

/**
 * 视图组件自动导入映射：key 为相对 src 的文件路径（../views/<component>.vue）。
 * 动态路由按菜单的 component 字段（如 system/UserView）在此解析组件加载器，
 * 新增页面文件无需注册即自动可被路由引用。
 */
const viewModules = import.meta.glob('../views/**/*.vue')

/**
 * 按菜单 component 字段解析视图组件加载器。
 *
 * @param component 组件路径（相对 src/views，不含扩展名）
 * @return 组件加载器；匹配失败返回 undefined 并打印告警（该菜单将被跳过，防止运行时异常）
 */
function resolveView(component: string | null): (() => Promise<unknown>) | undefined {
  if (!component)
    return undefined
  const loader = viewModules[`../views/${component}.vue`] as (() => Promise<unknown>) | undefined
  if (!loader)
    console.warn(`[router] 未找到视图组件，已跳过该菜单: ${component}`)
  return loader
}

/**
 * 路由路径 → 路由名（/system/user → system-user），动态注册去重标识。
 */
function routeName(path: string): string {
  return path.replace(/^\//, '').replaceAll('/', '-')
}

/**
 * 深度优先查找第一个可导航菜单路径（根路径重定向目标）。
 */
export function firstMenuPath(nodes: SysUserMenuVO[]): string | null {
  for (const node of nodes) {
    if (node.type === 'MENU' && node.path)
      return node.path
    const childPath = node.children?.length
      ? firstMenuPath(node.children)
      : null
    if (childPath)
      return childPath
  }
  return null
}

/**
 * 由菜单权限树生成路由记录（深度优先遍历）。
 *
 * <p>生成规则：仅 MENU 节点生成路由（DIRECTORY 仅作分组、BUTTON 为按钮权限点）；
 * 组件解析失败的菜单跳过；visible=false（隐藏）节点已在服务端剪枝，
 * 不会出现在下发菜单树中（不生成路由、不参与权限计算）；
 * 路由 meta 携带 title/icon/perms 核心字段。
 *
 * @param nodes 菜单树根节点列表
 * @return 可注册到布局子级的路由记录
 */
export function buildRoutes(nodes: SysUserMenuVO[]): RouteRecordRaw[] {
  const routes: RouteRecordRaw[] = []
  const walk = (list: SysUserMenuVO[]) => {
    for (const node of list) {
      if (node.type === 'MENU' && node.path) {
        const component = resolveView(node.component)
        if (component) {
          routes.push({
            path: node.path,
            name: routeName(node.path),
            component,
            meta: { title: node.name, icon: node.icon ?? '', perms: node.perms ?? '' },
          })
        }
      }
      if (node.children?.length)
        walk(node.children)
    }
  }
  walk(nodes)
  return routes
}

/**
 * 路由表：静态公共路由（登录页、根布局、404）+ 动态业务路由。
 *
 * <p>动态路由机制：业务路由不再静态枚举，登录后由路由守卫调用
 * GET /auth/menus 获取当前用户菜单权限树，经 buildRoutes 生成路由记录并
 * addRoute 注册到根布局（name=layout）子级；未授权路由不可达（匹配 404）。
 */
const router = createRouter({
  history: createWebHistory(import.meta.env.BASE_URL),
  routes: [
    {
      path: '/login',
      name: 'login',
      component: () => import('../views/LoginView.vue'),
      meta: { title: '登录', public: true },
    },
    {
      // 根布局：业务子路由动态注册；个人中心为登录即可用的静态子路由（不进菜单授权）
      path: '/',
      name: 'layout',
      component: () => import('../layouts/AdminLayout.vue'),
      children: [
        {
          path: '/profile',
          name: 'profile',
          component: () => import('../views/ProfileView.vue'),
          meta: { title: '个人中心' },
        },
      ],
    },
    {
      // 404 兜底：未登录先跳登录页（守卫）；已登录访问未授权/不存在路由时到达
      path: '/:pathMatch(.*)*',
      name: 'not-found',
      component: () => import('../views/NotFoundView.vue'),
      meta: { title: '页面不存在' },
    },
  ],
})

/**
 * 全局路由守卫：登录态校验 + 动态路由按需注册 + 根路径重定向。
 */
router.beforeEach(async (to) => {
  const auth = useAuthStore()

  // 公开路由（登录页）直接放行
  if (to.meta.public)
    return true

  // 未登录：跳登录页并携带回跳地址
  if (!auth.token) {
    return {
      name: 'login',
      query: to.fullPath === '/'
        ? undefined
        : { redirect: to.fullPath },
    }
  }

  // 动态路由未注册（首次进入/刷新）：拉取菜单权限树并注册后重放当前导航；
  // 拉取失败（如令牌被踢返回 401）时清空认证状态回登录页
  if (!auth.loaded) {
    try {
      const menus = await auth.fetchMenus()
      buildRoutes(menus).forEach(route => router.addRoute('layout', route))
    } catch {
      auth.reset()
      return { name: 'login' }
    }
    return {
      path: to.path,
      query: to.query,
      hash: to.hash,
      replace: true,
    }
  }

  // 根路径：重定向到当前用户第一个可导航菜单；无任何菜单权限时落入 404
  if (to.path === '/') {
    const first = firstMenuPath(auth.menus)
    return first || { name: 'not-found' }
  }

  return true
})

/**
 * 登录成功后装载动态路由（登录页调用；守卫处理刷新/直达场景）。
 *
 * @param menus 菜单权限树
 */
export function registerDynamicRoutes(menus: SysUserMenuVO[]) {
  buildRoutes(menus).forEach(route => router.addRoute('layout', route))
}

/**
 * 重置动态路由（退出登录时调用）：移除布局下全部动态子路由，防止切换账号后残留越权路由。
 */
export function clearDynamicRoutes() {
  router.getRoutes()
    .filter(route => route.name && route.name !== 'layout' && route.name !== 'login' && route.name !== 'not-found')
    .forEach(route => router.removeRoute(route.name as string))
}

export default router
