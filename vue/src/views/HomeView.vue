<template>
  <div class="home-page">
    <el-card shadow="never" class="home-clock-card">
      <div class="home-time">
        {{ time }}
      </div>
      <div class="home-date">
        {{ date }}
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
/**
 * 默认首页：暂时仅展示当前时间（每秒刷新）。
 *
 * <p>访问约定：作为布局的静态子路由（path 为空，即根路径 /）注册，
 * 不依赖菜单权限，所有登录用户均可见；侧边栏固定入口"首页"与之对应。
 */
import dayjs from 'dayjs'

/** 星期文案下标与 Date#getDay 对齐（0 = 星期日） */
const WEEKDAYS = [
  '星期日',
  '星期一',
  '星期二',
  '星期三',
  '星期四',
  '星期五',
  '星期六',
]

/** 当前时刻（每秒更新一次） */
const now = ref(new Date())

/** 秒级定时器句柄；组件卸载时清理，避免离开页面后继续空转 */
let timer: ReturnType<typeof setInterval> | undefined

onMounted(() => {
  timer = setInterval(() => {
    now.value = new Date()
  }, 1000)
})

onUnmounted(() => {
  if (timer)
    clearInterval(timer)
})

/** 时间：HH:mm:ss */
const time = computed(() => dayjs(now.value).format('HH:mm:ss'))

/** 日期：YYYY年MM月DD日 星期X */
const date = computed(() => `${dayjs(now.value).format('YYYY年MM月DD日')} ${WEEKDAYS[now.value.getDay()]}`)
</script>

<style scoped>
.home-page {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 100%;
}

.home-clock-card {
  min-width: 320px;
  padding: 24px 48px;
  text-align: center;
}

.home-time {
  font-size: 48px;
  font-weight: 600;
  font-variant-numeric: tabular-nums;
  color: var(--el-text-color-primary);
  letter-spacing: 2px;
  line-height: 1.2;
}

.home-date {
  margin-top: 12px;
  font-size: 16px;
  color: var(--el-text-color-secondary);
}
</style>
