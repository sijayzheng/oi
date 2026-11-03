<template>
  <el-card shadow="never" class="online-card">
    <div class="online-toolbar">
      <span class="online-title">在线用户</span>
      <div class="online-toolbar-actions">
        <el-button :loading="loading" @click="loadList">
          <el-icon><Refresh /></el-icon>
          刷新
        </el-button>
      </div>
    </div>
    <el-table v-loading="loading" :data="list" class="online-table">
      <el-table-column prop="username" label="用户名" min-width="120" />
      <el-table-column prop="nickname" label="昵称" min-width="120" />
      <el-table-column prop="roles" label="用户角色" min-width="140">
        <template #default="{ row }">
          {{ (row as OnlineUserVO).roles || '—' }}
        </template>
      </el-table-column>
      <el-table-column prop="loginIp" label="登录 IP" min-width="130" />
      <el-table-column prop="loginTime" label="登录时间" min-width="170" />
      <el-table-column label="在线时长" min-width="110">
        <template #default="{ row }">
          {{ formatDuration((row as OnlineUserVO).onlineMinutes) }}
        </template>
      </el-table-column>
      <el-table-column label="操作" width="110" fixed="right">
        <template #default="{ row }">
          <el-button
            type="danger"
            link
            :disabled="(row as OnlineUserVO).userId === '1'"
            @click="confirmKick(row as OnlineUserVO)"
          >
            强制下线
          </el-button>
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<script setup lang="ts">
/**
 * 在线用户管理（仅系统管理员）：实时会话列表 + 强制下线。
 *
 * <p>实时同步：手动刷新 + 30 秒自动轮询；强制下线前经 ElMessageBox 二次确认，
 * 成功后目标用户令牌立即作废（自动跳转登录页），操作留痕于服务端日志。
 * 超管账号（admin）的下线按钮禁用，防止误将自身踢出。
 */
/** 自动轮询间隔（毫秒） */
const AUTO_REFRESH_INTERVAL = 30_000

const loading = ref(false)
const list = ref<OnlineUserVO[]>([])
let refreshTimer: ReturnType<typeof setInterval> | undefined

/** 拉取在线用户列表；失败提示由统一拦截器承担，静默保持旧数据展示 */
async function loadList() {
  loading.value = true
  try {
    list.value = await onlineApi.list()
  } finally {
    loading.value = false
  }
}

/** 在线时长格式化：分钟 → "x 分钟" / "x 小时 y 分钟" */
function formatDuration(minutes: number) {
  if (minutes < 60)
    return `${minutes} 分钟`
  return `${Math.floor(minutes / 60)} 小时 ${minutes % 60} 分钟`
}

/** 强制下线：二次确认 → 调用踢人接口 → 成功/失败反馈并刷新列表 */
async function confirmKick(user: OnlineUserVO) {
  const confirmed = await ElMessageBox.confirm(
    `确定强制下线用户「${user.nickname || user.username}」吗？该用户当前会话将被立即终止。`,
    '强制下线确认',
    { type: 'warning', confirmButtonText: '强制下线', cancelButtonText: '取消' },
  ).then(() => true).catch(() => false)
  if (!confirmed)
    return
  try {
    await onlineApi.kick(user.userId)
    ElMessage.success(`已强制下线用户「${user.nickname || user.username}」`)
  } catch {
    // 失败提示由统一拦截器弹出（如目标已下线、权限不足等），此处仅阻断成功反馈
  }
  await loadList()
}

onMounted(() => {
  loadList()
  // 实时同步：定时轮询在线状态
  refreshTimer = setInterval(loadList, AUTO_REFRESH_INTERVAL)
})

onBeforeUnmount(() => {
  if (refreshTimer)
    clearInterval(refreshTimer)
})
</script>

<style scoped>
.online-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.online-title {
  color: var(--el-text-color-primary);
  font-size: 15px;
  font-weight: 600;
}
</style>
