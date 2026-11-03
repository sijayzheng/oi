<template>
  <el-container class="admin-layout">
    <el-aside :width="collapsed ? '64px' : '200px'" class="admin-aside">
      <div class="admin-logo">
        <img src="/logo.jpg" alt="logo" class="admin-logo-img">
        <span v-if="!collapsed" class="admin-logo-text">Oi 管理系统</span>
      </div>
      <el-menu :default-active="activeIndex" :collapse="collapsed" :collapse-transition="false" router
        class="admin-menu" text-color="#606266">
        <el-menu-item v-for="menu in menus" :key="menu.index" :index="menu.index">
          <el-icon>
            <component :is="menu.icon" />
          </el-icon>
          <template #title>
            {{ menu.title }}
          </template>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container class="admin-body">
      <el-header class="admin-header" height="48px">
        <el-button link class="admin-collapse-btn" @click="collapsed = !collapsed">
          <el-icon :size="18">
            <Expand v-if="collapsed" />
            <Fold v-else />
          </el-icon>
        </el-button>
        <el-breadcrumb separator="/" class="admin-breadcrumb">
          <el-breadcrumb-item v-if="breadcrumb.parent">
            {{ breadcrumb.parent }}
          </el-breadcrumb-item>
          <el-breadcrumb-item>
            {{ breadcrumb.current }}
          </el-breadcrumb-item>
        </el-breadcrumb>
        <div class="admin-header-right">
          <el-dropdown @command="onCommand">
            <span class="admin-user">
              <el-avatar v-if="auth.avatarUrl && !avatarLoadFailed" :size="26" :src="auth.avatarUrl"
                class="admin-user-avatar" @error="avatarLoadFailed = true" />
              <el-avatar v-else :size="26" class="admin-user-avatar is-text">
                {{ avatarText }}
              </el-avatar>
              {{ auth.nickname || '用户' }}
              <el-icon class="admin-user-arrow">
                <ArrowDown />
              </el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="profile">
                  个人中心
                </el-dropdown-item>
                <el-dropdown-item divided command="logout">
                  退出登录
                </el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>
      <div class="admin-tabs">
        <div v-for="tab in tabs" :key="tab.path" class="admin-tab" :class="{ 'is-active': tab.path === route.path }"
          @click="router.push(tab.path)">
          <span>{{ tab.title }}</span>
          <el-icon class="admin-tab-close" @click.stop="closeTab(tab)">
            <Close />
          </el-icon>
        </div>
      </div>
      <el-main class="admin-main">
        <RouterView />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup lang="ts">
/**
 * 后台管理整体布局（Naive Admin 视觉规范）：白色侧边栏 + 面包屑顶栏 + 路由标签栏 + 主内容区。
 *
 * <p>动态路由约定：侧边栏菜单由当前登录用户的菜单权限树（auth store）驱动渲染，
 * 与动态注册的路由同源；visible=false（隐藏）节点整棵子树在服务端剪枝，
 * 不进导航、不生成路由、不参与权限计算。
 * 面包屑由菜单权限树推导当前路由的父目录；标签栏记录访问过的路由，支持切换与关闭。
 * 顶栏头像：进入布局时拉取个人资料，优先展示自定义头像（签名访问地址），
 * 未设置头像或地址加载失败时回退为昵称首字生成的文字头像。
 * 响应式约定：视口宽度 < 768px 时侧边栏自动折叠为图标模式（窄屏仍可完整操作），
 * 展开按钮常驻标题栏，用户可手动切换。
 */
import { clearDynamicRoutes, firstMenuPath } from '@/router'
import { useAuthStore } from '@/stores/auth'
import { useWindowSize } from '@vueuse/core'
import type { RouteLocationNormalizedLoaded } from 'vue-router'
import { useRoute, useRouter } from 'vue-router'

const route: RouteLocationNormalizedLoaded = useRoute()
const router = useRouter()
const auth = useAuthStore()
const { width } = useWindowSize()

/** 顶栏头像：进入布局时拉取个人资料（同步昵称 + 解析头像签名地址）；失败静默回退文字头像 */
const avatarLoadFailed = ref(false)
onMounted(() => {
  auth.fetchProfile().catch(() => {
    avatarLoadFailed.value = true
  })
})

/** 文字头像文案：昵称（兜底"用户"）的首字符 */
const avatarText = computed(() => {
  const text = (auth.nickname || '用户').trim()
  return text.charAt(0).toUpperCase() || '用'
})

/** 侧边栏菜单：菜单树拉平后的 MENU 节点（隐藏节点已由服务端剪枝，此处防御性兜底过滤），保持单层导航体验 */
const menus = computed(() => {
  const items: { index: string, title: string, icon: string }[] = []
  const walk = (nodes: SysUserMenuVO[]) => {
    for (const node of nodes) {
      if (node.type === 'MENU' && node.path && node.visible !== false)
        items.push({ index: node.path, title: node.name, icon: node.icon || 'Menu' })
      if (node.children?.length)
        walk(node.children)
    }
  }
  walk(auth.menus)
  return items
})

/** 面包屑：菜单权限树中查找当前路由，父级目录名 + 当前页名 */
const breadcrumb = computed(() => {
  const current = (route.meta.title as string | undefined) ?? route.path
  let parent = ''
  const walk = (nodes: SysUserMenuVO[], parentName: string): boolean => {
    for (const node of nodes) {
      if (node.path === route.path) {
        parent = parentName
        return true
      }
      if (node.children?.length && walk(node.children, node.type === 'DIRECTORY'
        ? node.name
        : parentName)) {
        return true
      }
    }
    return false
  }
  walk(auth.menus, '')
  return { parent, current }
})

/** 路由标签：访问过的路由自动成签（去重），标题取路由 meta.title */
interface RouteTab {
  path: string
  title: string
}
const tabs = ref<RouteTab[]>([])
watch(() => route.path, (path) => {
  if (!path || path === '/login')
    return
  if (tabs.value.some(tab => tab.path === path))
    return
  tabs.value.push({ path, title: (route.meta.title as string | undefined) ?? path })
}, { immediate: true })

/** 关闭标签：关闭当前签时跳往相邻签；全部关闭时回第一个可导航菜单 */
function closeTab(tab: RouteTab) {
  const index = tabs.value.findIndex(item => item.path === tab.path)
  if (index < 0)
    return
  tabs.value.splice(index, 1)
  if (tab.path !== route.path)
    return
  const next = tabs.value[index - 1] ?? tabs.value[index]
  if (next) {
    router.push(next.path)
    return
  }
  router.push(firstMenuPath(auth.menus) || '/profile')
}

/** 下拉命令分发：个人中心跳转 / 退出登录 */
async function onCommand(command: string) {
  if (command === 'profile') {
    router.push('/profile')
    return
  }
  if (command !== 'logout')
    return
  try {
    await authApi.logout()
  } catch {
    // 令牌已失效等场景：本地状态仍需清理，不阻断退出
  }
  auth.reset()
  clearDynamicRoutes()
  router.replace('/login')
}

/** 是否移动端视口（< 768px） */
const isMobile = computed(() => width.value < 768)
/** 侧边栏折叠状态；进入移动端视口时强制折叠 */
const collapsed = ref(false)
watch(isMobile, (mobile) => {
  if (mobile)
    collapsed.value = true
}, { immediate: true })

/** 当前激活菜单（与路由同步，保持刷新后高亮） */
const activeIndex = computed(() => route.path)
</script>

<style scoped>
.admin-layout {
  width: 100vw;
  height: 100vh;
  overflow: hidden;
}

.admin-body {
  /* 弹性布局滚动链关键：允许内层容器被压缩，滚动收敛到 .admin-main 内部，避免整页出滚动条 */
  min-height: 0;
  overflow: hidden;
}

.admin-aside {
  display: flex;
  flex-direction: column;
  background-color: #fff;
  border-right: 1px solid var(--el-border-color-light);
  transition: width 0.2s;
}

.admin-logo {
  display: flex;
  gap: 8px;
  align-items: center;
  justify-content: center;
  height: 48px;
  padding: 0 12px;
  overflow: hidden;
  color: #1a2233;
  font-size: 16px;
  font-weight: 600;
  white-space: nowrap;
  border-bottom: 1px solid var(--el-border-color-light);
}

.admin-logo-img {
  flex-shrink: 0;
  width: 28px;
  height: 28px;
  object-fit: cover;
  border-radius: 8px;
}

.admin-menu {
  flex: 1;
  overflow-y: auto;
  border-right: none;
}

/* 菜单项：圆角高亮（浅蓝底 + 主色文字），与设计稿一致 */
.admin-menu :deep(.el-menu-item) {
  height: 42px;
  margin: 4px 10px;
  border-radius: 6px;
}

.admin-menu :deep(.el-menu-item.is-active) {
  background-color: var(--el-color-primary-light-9);
  font-weight: 600;
}

.admin-header {
  display: flex;
  align-items: center;
  gap: 12px;
  background-color: #fff;
  border-bottom: 1px solid var(--el-border-color-light);
}

.admin-breadcrumb {
  font-size: 14px;
}

.admin-header-right {
  display: flex;
  align-items: center;
  margin-left: auto;
}

.admin-user {
  display: flex;
  gap: 6px;
  align-items: center;
  color: var(--el-text-color-primary);
  font-size: 14px;
  cursor: pointer;
  outline: none;
}

/* 顶栏头像：与昵称文字等高对齐；文字头像取主题色浅底，保持导航区视觉一致 */
.admin-user-avatar {
  flex-shrink: 0;
}

.admin-user-avatar.is-text {
  font-size: 12px;
  color: var(--el-color-primary);
  background-color: var(--el-color-primary-light-8);
}

.admin-user-arrow {
  font-size: 12px;
  color: var(--el-text-color-secondary);
}

/* 路由标签栏：白底横条，激活签浅蓝底 + 主色文字，可关闭 */
.admin-tabs {
  display: flex;
  flex-shrink: 0;
  gap: 8px;
  align-items: center;
  height: 40px;
  padding: 0 12px;
  overflow-x: auto;
  background-color: #fff;
  border-bottom: 1px solid var(--el-border-color-light);
}

.admin-tab {
  display: flex;
  flex-shrink: 0;
  gap: 4px;
  align-items: center;
  padding: 4px 10px;
  font-size: 13px;
  color: var(--el-text-color-regular);
  cursor: pointer;
  user-select: none;
  border-radius: 4px;
}

.admin-tab:hover {
  color: var(--el-color-primary);
}

.admin-tab.is-active {
  color: var(--el-color-primary);
  background-color: var(--el-color-primary-light-9);
}

.admin-tab-close {
  font-size: 12px;
  border-radius: 50%;
}

.admin-tab-close:hover {
  color: #fff;
  background-color: var(--el-color-primary);
}

.admin-main {
  padding: 12px;
  overflow: auto;
  background-color: #f5f6f7;
}
</style>
