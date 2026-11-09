<template>
  <el-card shadow="never" class="search-card">
    <el-form inline class="search-form" @submit.prevent="search">
      <el-form-item label="日志级别">
        <el-select v-model="query.level" placeholder="全部" clearable class="level-select">
          <el-option v-for="item in levelOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="时间范围">
        <el-date-picker
          v-model="timeRange"
          type="datetimerange"
          range-separator="至"
          start-placeholder="开始时间"
          end-placeholder="结束时间"
          value-format="YYYY-MM-DD[T]HH:mm:ss"
          :shortcuts="timeShortcuts"
        />
      </el-form-item>
      <el-form-item label="关键词">
        <el-input v-model="query.keyword" placeholder="内容/动作/模块/操作人/请求 ID" clearable class="keyword-input" @keyup.enter="search" />
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :icon="Search" @click="search">
          搜索
        </el-button>
        <el-button :icon="RefreshLeft" @click="reset">
          重置
        </el-button>
      </el-form-item>
    </el-form>
  </el-card>

  <!-- 数据列表卡片 -->
  <el-card shadow="never" class="table-card">
    <div class="table-toolbar">
      <div class="toolbar-left">
        <el-button :icon="Download" :loading="exporting" @click="handleExport">
          导出
        </el-button>
        <el-button :icon="Refresh" :loading="loading" @click="refreshCurrent">
          刷新
        </el-button>
      </div>
      <div class="toolbar-right">
        <span class="auto-refresh-label">自动刷新</span>
        <el-switch v-model="autoRefresh" />
        <el-select v-model="refreshInterval" class="interval-select" :disabled="!autoRefresh">
          <el-option label="10 秒" :value="10" />
          <el-option label="30 秒" :value="30" />
          <el-option label="60 秒" :value="60" />
        </el-select>
      </div>
    </div>

    <el-table v-loading="loading" :data="rows" border stripe>
      <el-table-column prop="createTime" label="记录时间" width="170" />
      <el-table-column prop="level" label="级别" width="90">
        <template #default="{ row }">
          <el-tag :type="levelTagType((row as SysLogVO).level)">
            {{ (row as SysLogVO).level }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="module" label="模块" width="90">
        <template #default="{ row }">
          {{ (row as SysLogVO).module || '—' }}
        </template>
      </el-table-column>
      <el-table-column prop="content" label="日志内容" min-width="240" show-overflow-tooltip>
        <template #default="{ row }">
          {{ (row as SysLogVO).content || '—' }}
        </template>
      </el-table-column>
      <el-table-column prop="result" label="结果" width="80">
        <template #default="{ row }">
          <el-tag :type="(row as SysLogVO).result === 'SUCCESS' ? 'success' : 'danger'" effect="plain">
            {{ (row as SysLogVO).result === 'SUCCESS' ? '成功' : '失败' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="username" label="操作人" width="110">
        <template #default="{ row }">
          {{ (row as SysLogVO).username || '—' }}
        </template>
      </el-table-column>
      <el-table-column prop="requestId" label="请求 ID" width="110">
        <template #default="{ row }">
          {{ (row as SysLogVO).requestId || '—' }}
        </template>
      </el-table-column>
      <el-table-column label="操作" fixed="right" width="80">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row as SysLogVO)">
            详情
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-model:current-page="pageNum"
      v-model:page-size="pageSize"
      :total="total"
      :page-sizes="[10, 20, 50, 100]"
      layout="total, sizes, prev, pager, next, jumper"
      background
      class="page-pagination"
      @change="pageChange"
    />
  </el-card>

  <!-- 日志详情 -->
  <el-dialog v-model="detailVisible" title="日志详情" width="780px" destroy-on-close>
    <el-descriptions v-if="detail" :column="2" border>
      <el-descriptions-item label="日志 ID">
        {{ detail.id }}
      </el-descriptions-item>
      <el-descriptions-item label="记录时间">
        {{ detail.createTime }}
      </el-descriptions-item>
      <el-descriptions-item label="日志级别">
        <el-tag :type="levelTagType(detail.level)">
          {{ detail.level }}
        </el-tag>
      </el-descriptions-item>
      <el-descriptions-item label="执行结果">
        <el-tag :type="detail.result === 'SUCCESS' ? 'success' : 'danger'" effect="plain">
          {{ detail.result === 'SUCCESS' ? '成功' : '失败' }}
        </el-tag>
      </el-descriptions-item>
      <el-descriptions-item label="模块">
        {{ detail.module || '—' }}
      </el-descriptions-item>
      <el-descriptions-item label="动作">
        {{ detail.action || '—' }}
      </el-descriptions-item>
      <el-descriptions-item label="操作人">
        {{ detail.username || '—' }}
      </el-descriptions-item>
      <el-descriptions-item label="客户端 IP">
        {{ detail.ip || '—' }}
      </el-descriptions-item>
      <el-descriptions-item label="请求路径">
        {{ detail.uri || '—' }}
      </el-descriptions-item>
      <el-descriptions-item label="请求方法">
        {{ detail.method || '—' }}
      </el-descriptions-item>
      <el-descriptions-item label="请求 ID" :span="2">
        {{ detail.requestId || '—' }}
      </el-descriptions-item>
      <el-descriptions-item label="请求参数" :span="2">
        <pre class="detail-pre">{{ detail.params || '—' }}</pre>
      </el-descriptions-item>
      <el-descriptions-item label="失败原因" :span="2">
        <pre class="detail-pre">{{ detail.errorMsg || '—' }}</pre>
      </el-descriptions-item>
    </el-descriptions>
    <template #footer>
      <el-button type="primary" @click="detailVisible = false">
        关闭
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
/**
 * 操作日志管理（仅系统管理员）：级别/时间范围/关键词筛选 + 分页 + 详情 + 导出 + 自动刷新。
 *
 * <p>渲染性能约定：后端分页（每页条数上限 100）+ 内容列截断渲染（show-overflow-tooltip），
 * 超长参数仅在详情弹窗中完整展示，避免大报文撑爆列表；导出按当前筛选条件后端全量生成 CSV。
 * 自动刷新：开关 + 可选间隔（10/30/60 秒），保持当前页码刷新；组件卸载时清理定时器。
 */
/** 日志级别下拉选项 */
const levelOptions = [
  { value: 'DEBUG', label: 'DEBUG' },
  { value: 'INFO', label: 'INFO' },
  { value: 'WARN', label: 'WARN' },
  { value: 'ERROR', label: 'ERROR' },
] as const

/** 级别 → el-tag 类型映射（与主题色体系对齐） */
const levelTagTypeMap = {
  DEBUG: 'info',
  INFO: 'primary',
  WARN: 'warning',
  ERROR: 'danger',
} as const

/** 时间范围快捷选项（近 1 小时/今天/近 24 小时/近 7 天） */
const timeShortcuts = [
  {
    text: '近 1 小时',
    value: () => {
      const end = new Date()
      const start = new Date(Date.now() - 3600_000)
      return [start, end]
    },
  },
  {
    text: '今天',
    value: () => {
      const start = new Date()
      start.setHours(0, 0, 0, 0)
      return [
        start,
        new Date(),
      ]
    },
  },
  {
    text: '近 24 小时',
    value: () => {
      const end = new Date()
      const start = new Date(Date.now() - 86_400_000)
      return [start, end]
    },
  },
  {
    text: '近 7 天',
    value: () => {
      const end = new Date()
      const start = new Date(Date.now() - 7 * 86_400_000)
      return [start, end]
    },
  },
]

const getDefaultQuery = (): SysLogQuery => ({ level: '', keyword: '' })

const { query, pageNum, pageSize, total, loading, rows, load, pageChange }
  = useTablePage<SysLogQuery, SysLogVO>(params => logApi.page(params), getDefaultQuery)

/** 时间范围（datetimerange 双向绑定，展开为查询条件） */
const timeRange = ref<[string, string] | null>(null)

/** 当前生效的查询条件（含时间范围展开；导出与加载共用） */
const effectiveQuery = computed<SysLogQuery>(() => ({
  ...query,
  startTime: timeRange.value?.[0],
  endTime: timeRange.value?.[1],
}))

/** 将时间范围并入查询条件容器（useTablePage.load 以 query 组装请求参数） */
function syncRangeIntoQuery() {
  query.startTime = timeRange.value?.[0]
  query.endTime = timeRange.value?.[1]
}

/** 搜索：重置页码后按当前条件（含时间范围）加载 */
function search() {
  pageNum.value = 1
  syncRangeIntoQuery()
  load()
}

/** 重置：清空全部筛选条件（含时间范围）并回到第一页 */
function reset() {
  timeRange.value = null
  Object.assign(query, getDefaultQuery())
  delete query.startTime
  delete query.endTime
  pageNum.value = 1
  load()
}

/** 按当前条件与页码刷新（自动刷新定时器复用，保持当前页） */
function refreshCurrent() {
  syncRangeIntoQuery()
  load()
}

/** 详情弹窗 */
const detailVisible = ref(false)
const detail = ref<SysLogVO | null>(null)

function openDetail(row: SysLogVO) {
  detail.value = row
  detailVisible.value = true
}

/** 导出状态 */
const exporting = ref(false)

/** 导出当前筛选条件的日志 CSV（追加 UTF-8 BOM，防 Excel 打开乱码） */
async function handleExport() {
  exporting.value = true
  try {
    const csv = await logApi.exportCsv(effectiveQuery.value)
    if (!csv || csv.split('\n').length <= 1) {
      ElMessage.warning('当前筛选条件下没有可导出的日志')
      return
    }
    const blob = new Blob(['\uFEFF', csv], { type: 'text/csv;charset=utf-8' })
    const url = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = `operation-logs-${new Date().toISOString().slice(0, 19).replaceAll(':', '')}.csv`
    link.click()
    URL.revokeObjectURL(url)
    ElMessage.success('日志导出成功')
  } finally {
    exporting.value = false
  }
}

/** 自动刷新开关与间隔（秒） */
const autoRefresh = ref(false)
const refreshInterval = ref(30)
let refreshTimer: ReturnType<typeof setInterval> | undefined

watch([autoRefresh, refreshInterval], ([enabled, interval]) => {
  if (refreshTimer) {
    clearInterval(refreshTimer)
    refreshTimer = undefined
  }
  if (enabled) {
    refreshTimer = setInterval(refreshCurrent, interval * 1000)
  }
})

onMounted(() => {
  search()
})

onBeforeUnmount(() => {
  if (refreshTimer) {
    clearInterval(refreshTimer)
    refreshTimer = undefined
  }
})

/** 级别 → el-tag 类型 */
function levelTagType(level: SysLogVO['level']) {
  return levelTagTypeMap[level] ?? 'info'
}
</script>

<style scoped>
.level-select {
  width: 120px;
}

.keyword-input {
  width: 220px;
}

.table-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.toolbar-left {
  display: flex;
  gap: 0;
}

.toolbar-right {
  display: flex;
  gap: 8px;
  align-items: center;
}

.auto-refresh-label {
  color: var(--el-text-color-regular);
  font-size: 13px;
}

.interval-select {
  width: 96px;
}

.detail-pre {
  margin: 0;
  font-family: inherit;
  font-size: 13px;
  line-height: 1.6;
  white-space: pre-wrap;
  word-break: break-all;
}
</style>
