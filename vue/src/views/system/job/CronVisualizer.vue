<template>
  <div class="cron-visualizer">
    <!-- 字段分解：6 位表达式的结构化展示 -->
    <div class="cron-fields">
      <div v-for="field in fields" :key="field.name" class="cron-field">
        <span class="field-label">{{ field.label }}</span>
        <span
          class="field-value"
          :class="{ 'field-wildcard': field.value === '*', 'field-invalid': !field.valid }"
        >
          {{ field.value }}
        </span>
        <span class="field-hint">{{ field.hint }}</span>
      </div>
    </div>

    <!-- 表达式释义 -->
    <div class="cron-explain">
      <el-icon class="explain-icon">
        <InfoFilled />
      </el-icon>
      <span>{{ explainText }}</span>
    </div>

    <!-- 后续执行时间预览 -->
    <div class="cron-preview">
      <div class="preview-title">
        <span>后续 {{ PREVIEW_COUNT }} 次执行时间</span>
        <el-button link type="primary" size="small" :loading="loading" @click="refresh">
          刷新
        </el-button>
      </div>
      <div v-if="loading" class="preview-loading">
        <el-skeleton :rows="2" animated />
      </div>
      <div v-else-if="errorMessage" class="preview-error">
        <el-icon>
          <CircleCloseFilled />
        </el-icon>
        <span>{{ errorMessage }}</span>
      </div>
      <ol v-else class="preview-list">
        <li v-for="(time, index) in nextTimes" :key="time" class="preview-item">
          <span class="preview-index">{{ index + 1 }}</span>
          <span class="preview-time">{{ time }}</span>
          <span v-if="index === 0" class="preview-next-tag">下一次</span>
        </li>
      </ol>
    </div>
  </div>
</template>

<script setup lang="ts">
/**
 * CRON 表达式可视化组件：字段分解展示 + 表达式释义 + 后续执行时间预览。
 *
 * <p>字段分解将 6 位表达式（秒 分 时 日 月 周）按位拆解，`*` 与数字段分色标注，
 * 帮助理解表达式含义；释义由各字段的取值生成中文描述；
 * 后续执行时间经后端 /job/cron/next-times 计算——与调度器同源使用 Spring
 * CronExpression（应用默认时区），保证预览时间与实际触发完全一致。
 */

import { CircleCloseFilled, InfoFilled } from '@element-plus/icons-vue'
import { jobApi } from '@/api/job'

const props = defineProps<{
  /** 6 位 CRON 表达式（空串视为未输入，不请求预览） */
  expression: string
}>()

/** 预览次数（固定 5 次） */
const PREVIEW_COUNT = 5

/** 字段分解的单元结构 */
interface CronField {
  /** 字段名 */
  label: string
  /** 原始取值 */
  value: string
  /** 中文提示（含义/范围） */
  hint: string
  /** 是否合法（占位时 false） */
  valid: boolean
}

/** 6 位字段的静态元信息（顺序与表达式位序一致） */
const FIELD_META: Array<{ label: string, hint: string, max: number }> = [
  { label: '秒', hint: '0-59', max: 59 },
  { label: '分', hint: '0-59', max: 59 },
  { label: '时', hint: '0-23', max: 23 },
  { label: '日', hint: '1-31', max: 31 },
  { label: '月', hint: '1-12', max: 12 },
  { label: '周', hint: '0-7（0 和 7 均为周日）', max: 7 },
]

/**
 * 表达式字段分解；未输入/位数不足时占位显示 `--`。
 */
const fields = computed<CronField[]>(() => {
  const parts = props.expression.trim().split(/\s+/).filter(Boolean)
  return FIELD_META.map((meta, index) => {
    const value = parts[index] ?? '--'
    const valid = value !== '--'
    const hint = valid
      ? (value === '*'
          ? '每次'
          : meta.hint)
      : '未填'
    return {
      label: meta.label,
      value,
      hint,
      valid,
    }
  })
})

/**
 * 表达式释义；全部字段就绪时按字段生成中文描述，否则提示待补全。
 */
const explainText = computed(() => {
  const parts = props.expression.trim().split(/\s+/).filter(Boolean)
  if (parts.length < 6)
    return '请输入完整的 6 位表达式（秒 分 时 日 月 周）'
  if (!props.expression.trim().includes('*') && parts.length === 6
    && parts.every(p => /^\d+$/.test(p))) {
    return '仅执行一次'
  }
  const segments: string[] = []
  const [sec, min, hour, day, month, week] = parts
  segments.push(describeField(sec, '秒', FIELD_META[0].max, '每秒'))
  segments.push(describeField(min, '分', FIELD_META[1].max, '每分'))
  segments.push(describeField(hour, '时', FIELD_META[2].max, '每小时'))
  if (day !== '*' || week !== '*')
    segments.push(describeDayWeek(day, week))
  else
    segments.push('每日')
  if (month !== '*')
    segments.push(describeField(month, '月', FIELD_META[3].max, '每月'))
  if (week !== '*' && day !== '*')
    segments.push('（日与周同时指定时按"或"匹配，注意语义）')
  return segments.filter(Boolean).join('；')
})

/**
 * 单字段中文描述：星号表示每单位、星号斜杠 n 表示间隔、具体值表示固定时刻。
 */
function describeField(value: string, unit: string, max: number, everyText: string): string {
  if (value === '*')
    return everyText
  if (value.startsWith('*/')) {
    const step = Number(value.slice(2))
    return Number.isInteger(step) && step > 0
      ? `每 ${step} ${unit}`
      : `每 ${value.slice(2)} ${unit}`
  }
  if (value.includes(',')) {
    const values = value.split(',').filter(v => /^\d+$/.test(v) && Number(v) <= max)
    if (values.length > 0)
      return `第 ${values.join('、')} ${unit}`
    return `${value}（${unit}）`
  }
  if (value.includes('-')) {
    return `${value} ${unit}（范围）`
  }
  return `第 ${value} ${unit}`
}

/**
 * 日/周组合描述（日与周均为 `*` 时由调用方处理为"每日"）。
 */
function describeDayWeek(day: string, week: string): string {
  if (day === '*')
    return describeField(week, '周（0=周日）', 7, '每周')
  if (week === '*')
    return describeField(day, '日', 31, '每日')
  return `日 ${day} 或 周 ${week}`
}

/* ==================== 后续执行时间预览 ==================== */

const loading = ref(false)
const errorMessage = ref('')
const nextTimes = ref<string[]>([])

/**
 * 请求后续执行时间；静默模式——业务失败不弹全局提示，由组件内展示错误文案。
 */
async function refresh() {
  const expression = props.expression.trim()
  if (expression.length === 0) {
    errorMessage.value = ''
    nextTimes.value = []
    return
  }
  loading.value = true
  try {
    nextTimes.value = await jobApi.nextCronTimes(expression, PREVIEW_COUNT)
    errorMessage.value = ''
  } catch (error) {
    nextTimes.value = []
    errorMessage.value = error instanceof Error && error.message
      ? error.message
      : '预览失败，请检查表达式'
  } finally {
    loading.value = false
  }
}

// 表达式变化防抖后自动刷新预览（300ms 内连续输入只发一次请求）
watchDebounced(() => props.expression, (value) => {
  if (value.trim().length > 0)
    refresh()
}, { debounce: 300 })

// 组件挂载且已有表达式时立即预览（编辑场景）
onMounted(() => {
  if (props.expression.trim().length > 0)
    refresh()
})
</script>

<style scoped>
.cron-visualizer {
  padding: 12px;
  background: var(--el-fill-color-lighter);
  border-radius: 6px;
}

/* 字段分解：横向排布 6 个字段单元 */
.cron-fields {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.cron-field {
  display: flex;
  flex-direction: column;
  align-items: center;
  min-width: 68px;
  padding: 6px 8px;
  background: var(--el-bg-color);
  border-radius: 4px;
}

.field-label {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.field-value {
  margin: 2px 0;
  font-family: monospace;
  font-size: 15px;
  font-weight: 600;
  color: var(--el-color-primary);
  word-break: break-all;
}

.field-value.field-wildcard {
  color: var(--el-color-success);
}

.field-value.field-invalid {
  color: var(--el-text-color-placeholder);
}

.field-hint {
  color: var(--el-text-color-placeholder);
  font-size: 11px;
}

/* 释义 */
.cron-explain {
  display: flex;
  align-items: flex-start;
  gap: 6px;
  margin-top: 10px;
  color: var(--el-text-color-regular);
  font-size: 13px;
  line-height: 1.5;
}

.explain-icon {
  flex-shrink: 0;
  margin-top: 2px;
  color: var(--el-color-primary);
}

/* 预览区 */
.cron-preview {
  margin-top: 10px;
}

.preview-title {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 6px;
  color: var(--el-text-color-regular);
  font-size: 13px;
  font-weight: 600;
}

.preview-error {
  display: flex;
  align-items: center;
  gap: 6px;
  color: var(--el-color-danger);
  font-size: 13px;
}

.preview-list {
  margin: 0;
  padding: 0;
  list-style: none;
}

.preview-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 3px 0;
}

.preview-index {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 18px;
  height: 18px;
  background: var(--el-fill-color);
  border-radius: 50%;
  color: var(--el-text-color-secondary);
  font-size: 11px;
}

.preview-time {
  font-family: monospace;
  color: var(--el-text-color-primary);
}

.preview-next-tag {
  padding: 0 6px;
  background: var(--el-color-primary-light-9);
  border-radius: 3px;
  color: var(--el-color-primary);
  font-size: 11px;
}
</style>
