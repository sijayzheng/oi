<template>
  <el-card shadow="never" class="search-card">
    <el-form inline class="search-form" @submit.prevent="search">
      <el-form-item label="任务键">
        <el-input v-model="query.jobKey" placeholder="精确匹配" clearable @keyup.enter="search" />
      </el-form-item>
      <el-form-item label="执行状态">
        <el-select v-model="query.status" placeholder="全部" clearable class="status-select">
          <el-option
            v-for="item in statusOptions" :key="item.value" :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="触发来源">
        <el-select v-model="query.source" placeholder="全部" clearable class="status-select">
          <el-option label="调度触发" value="SCHEDULE" />
          <el-option label="手动触发" value="MANUAL" />
          <el-option label="死信重放" value="REPLAY" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" :icon="Search" @click="search">
          搜索
        </el-button>
        <el-button :icon="Refresh" @click="reset">
          重置
        </el-button>
      </el-form-item>
    </el-form>
  </el-card>

  <el-card shadow="never" class="table-card">
    <div class="table-toolbar">
      <el-alert v-if="jobKeyFilter" type="info" :closable="true" show-icon @close="clearJobFilter">
        按任务「{{ jobKeyFilter }}」过滤
      </el-alert>
      <el-button :icon="Refresh" @click="load">
        刷新
      </el-button>
    </div>

    <el-table v-loading="loading" :data="rows" row-key="id" border stripe @sort-change="sortChange">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column
        prop="jobKey" label="任务键" min-width="150" sortable="custom"
        show-overflow-tooltip
      />
      <el-table-column prop="jobName" label="任务名称" min-width="120" show-overflow-tooltip />
      <el-table-column label="触发来源" width="100">
        <template #default="{ row }">
          <el-tag type="info" size="small">
            {{ (row as SysJobLogVO).triggerSourceDesc ?? row.triggerSource }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="执行状态" width="90">
        <template #default="{ row }">
          <el-tag :type="statusTagType((row as SysJobLogVO).executeStatus)">
            {{ (row as SysJobLogVO).executeStatusDesc ?? row.executeStatus }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="重试" width="70">
        <template #default="{ row }">
          {{ (row as SysJobLogVO).retryCount > 0 ? `${(row as SysJobLogVO).retryCount} 次` : '—' }}
        </template>
      </el-table-column>
      <el-table-column label="异常类型" min-width="200" show-overflow-tooltip>
        <template #default="{ row }">
          {{ (row as SysJobLogVO).errorType ?? '—' }}
        </template>
      </el-table-column>
      <el-table-column prop="startTime" label="开始时间" width="170" sortable="custom" />
      <el-table-column label="耗时" width="90">
        <template #default="{ row }">
          {{ formatMillis((row as SysJobLogVO).durationMillis) }}
        </template>
      </el-table-column>
      <el-table-column prop="executor" label="执行机" width="130" show-overflow-tooltip />
      <el-table-column label="操作" fixed="right" width="80">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row as SysJobLogVO)">
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

  <!-- 执行详情（含重试明细） -->
  <el-dialog v-model="detailVisible" title="执行详情" width="860px" destroy-on-close>
    <el-descriptions v-if="detail" :column="3" border size="small" class="detail-desc">
      <el-descriptions-item label="任务键">
        {{ detail.jobKey }}
      </el-descriptions-item>
      <el-descriptions-item label="任务名称">
        {{ detail.jobName }}
      </el-descriptions-item>
      <el-descriptions-item label="触发来源">
        {{ detail.triggerSourceDesc ?? detail.triggerSource }}
      </el-descriptions-item>
      <el-descriptions-item label="执行状态">
        <el-tag :type="statusTagType(detail.executeStatus)">
          {{ detail.executeStatusDesc ?? detail.executeStatus }}
        </el-tag>
      </el-descriptions-item>
      <el-descriptions-item label="累计重试">
        {{ detail.retryCount }} 次
      </el-descriptions-item>
      <el-descriptions-item label="执行机">
        {{ detail.executor ?? '—' }}
      </el-descriptions-item>
      <el-descriptions-item label="开始时间">
        {{ detail.startTime }}
      </el-descriptions-item>
      <el-descriptions-item label="结束时间">
        {{ detail.endTime ?? '—' }}
      </el-descriptions-item>
      <el-descriptions-item label="总耗时">
        {{ formatMillis(detail.durationMillis) }}
      </el-descriptions-item>
    </el-descriptions>
    <el-alert
      v-if="detail?.errorStack"
      type="error"
      :closable="false"
      class="detail-error"
    >
      <pre class="error-pre">{{ detail.errorType }}
      {{ detail.errorStack }}</pre>
    </el-alert>
    <template v-if="detail?.attempts?.length">
      <el-divider content-position="left">
        重试明细（{{ detail.attempts.length }} 轮）
      </el-divider>
      <el-table :data="detail.attempts" border size="small">
        <el-table-column prop="attemptNo" label="轮次" width="60">
          <template #default="{ row }">
            {{
              (row as JobAttemptDetailVO).attemptNo === 0 ? '首试' : `第 ${(row as JobAttemptDetailVO).attemptNo} 次`
            }}
          </template>
        </el-table-column>
        <el-table-column label="状态" width="80">
          <template #default="{ row }">
            <el-tag :type="statusTagType((row as JobAttemptDetailVO).attemptStatus)" size="small">
              {{
                (row as JobAttemptDetailVO).attemptStatus === 'SUCCESS' ? '成功' : ((row as JobAttemptDetailVO).attemptStatus === 'TIMEOUT' ? '超时' : '失败')
              }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="attemptStartTime" label="开始时间" width="170" />
        <el-table-column label="单轮耗时" width="90">
          <template #default="{ row }">
            {{ formatMillis((row as JobAttemptDetailVO).attemptDurationMillis) }}
          </template>
        </el-table-column>
        <el-table-column label="下次重试" width="170">
          <template #default="{ row }">
            {{ (row as JobAttemptDetailVO).nextRetryAt ?? '—' }}
          </template>
        </el-table-column>
        <el-table-column label="异常" min-width="220" show-overflow-tooltip>
          <template #default="{ row }">
            {{ (row as JobAttemptDetailVO).errorType ?? '—' }}
          </template>
        </el-table-column>
        <el-table-column label="堆栈" width="70">
          <template #default="{ row }">
            <el-button
              v-if="(row as JobAttemptDetailVO).errorStack"
              link
              type="primary"
              @click="showStack = (row as JobAttemptDetailVO).errorStack ?? ''"
            >
              查看
            </el-button>
            <span v-else>—</span>
          </template>
        </el-table-column>
      </el-table>
    </template>
  </el-dialog>

  <!-- 堆栈查看 -->
  <el-dialog v-model="stackVisible" title="异常堆栈" width="780px">
    <pre class="error-pre">{{ showStack }}</pre>
  </el-dialog>
</template>

<script setup lang="ts">
/**
 * 任务执行详情页：执行记录检索 + 单条详情（含逐轮重试明细、异常堆栈）。
 *
 * <p>支持从任务列表页带任务键跳入（query 参数自动填充过滤条件）。
 * 状态色：成功绿 / 失败红 / 超时橙 / 死信深红 / 跳过灰。
 */
const route = useRoute()

/** 从任务页跳入的任务键过滤 */
const jobKeyFilter = ref(String(route.query.jobKey ?? ''))

const {
  query,
  pageNum,
  pageSize,
  total,
  loading,
  rows,
  load,
  search,
  reset,
  pageChange,
  sortChange,
} = useTablePage<{
  jobKey?: string
  status?: JobExecuteStatus
  source?: JobTriggerSource
}, SysJobLogVO>(
  data => jobApi.pageLogs(data),
  () => ({
    jobKey: jobKeyFilter.value,
    status: undefined,
    source: undefined,
  }),
)

const statusOptions = [
  { value: 'PENDING', label: '待执行' },
  { value: 'RUNNING', label: '执行中' },
  { value: 'SUCCESS', label: '成功' },
  { value: 'FAILED', label: '失败' },
  { value: 'TIMEOUT', label: '超时' },
  { value: 'DEAD', label: '死信' },
  { value: 'SKIPPED', label: '跳过' },
] as const

/** 清除任务键过滤并重查 */
function clearJobFilter() {
  jobKeyFilter.value = ''
  query.jobKey = ''
  search()
}

/* ==================== 详情 ==================== */

const detailVisible = ref(false)
const detail = ref<SysJobLogVO>()
const stackVisible = ref(false)
const showStack = ref('')

async function openDetail(row: SysJobLogVO) {
  detail.value = await jobApi.getLog(row.id)
  detailVisible.value = true
}

/* ==================== 展示辅助 ==================== */

/** 状态 tag 颜色 */
function statusTagType(status: JobExecuteStatus | string) {
  switch (status) {
    case 'SUCCESS':
      return 'success'
    case 'FAILED':
      return 'danger'
    case 'TIMEOUT':
      return 'warning'
    case 'DEAD':
      return 'danger'
    case 'RUNNING':
      return 'primary'
    default:
      return 'info'
  }
}

/** 毫秒 → 人类可读（<1s 显示 ms；<60s 显示 s；否则显示分秒） */
function formatMillis(value?: string | null) {
  if (!value)
    return '—'
  const millis = Number(value)
  if (Number.isNaN(millis) || millis < 0)
    return '—'
  if (millis < 1000)
    return `${millis}ms`
  if (millis < 60_000)
    return `${(millis / 1000).toFixed(1)}s`
  return `${Math.floor(millis / 60_000)}分${Math.round((millis % 60_000) / 1000)}秒`
}

onMounted(() => load())
</script>

<style scoped>
.search-card {
  margin-bottom: 12px;
}

.status-select {
  width: 140px;
}

.table-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}

.detail-desc {
  margin-bottom: 12px;
}

.detail-error {
  margin-bottom: 12px;
}

.error-pre {
  max-height: 320px;
  overflow: auto;
  margin: 0;
  font-family: ui-monospace, monospace;
  font-size: 12px;
  line-height: 1.5;
  white-space: pre-wrap;
}
</style>
