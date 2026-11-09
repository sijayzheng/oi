<template>
  <el-card shadow="never" class="search-card">
    <el-form inline class="search-form" @submit.prevent="search">
      <el-form-item label="任务键">
        <el-input v-model="query.jobKey" placeholder="精确匹配" clearable @keyup.enter="search" />
      </el-form-item>
      <el-form-item label="处理状态">
        <el-select v-model="query.status" placeholder="全部" clearable class="status-select">
          <el-option label="待处理" value="PENDING" />
          <el-option label="已重放成功" value="RESOLVED" />
          <el-option label="重放失败" value="REPLAY_FAILED" />
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
      <el-alert type="info" :closable="false" show-icon>
        死信为重试耗尽仍失败的任务；重放将重置重试计数并按任务<b>当前配置</b>重新执行完整流程。
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
      <el-table-column label="处理状态" width="110">
        <template #default="{ row }">
          <el-tag :type="statusTagType((row as SysJobDeadLetterVO).status)">
            {{ (row as SysJobDeadLetterVO).statusDesc ?? row.status }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="累计重试" width="90">
        <template #default="{ row }">
          {{ (row as SysJobDeadLetterVO).totalRetryCount }} 次
        </template>
      </el-table-column>
      <el-table-column label="最终异常" min-width="200" show-overflow-tooltip>
        <template #default="{ row }">
          {{ (row as SysJobDeadLetterVO).errorType ?? '—' }}
        </template>
      </el-table-column>
      <el-table-column prop="createTime" label="死信时间" width="170" sortable="custom" />
      <el-table-column label="重放时间" width="170">
        <template #default="{ row }">
          {{ (row as SysJobDeadLetterVO).replayTime ?? '—' }}
        </template>
      </el-table-column>
      <el-table-column label="操作" fixed="right" width="190">
        <template #default="{ row }">
          <el-button link type="primary" @click="openDetail(row as SysJobDeadLetterVO)">
            详情
          </el-button>
          <el-button
            link
            type="success"
            :loading="replayingId === row.id"
            @click="handleReplay(row as SysJobDeadLetterVO)"
          >
            重放
          </el-button>
          <el-popconfirm
            title="确认删除该死信记录？" confirm-button-text="删除"
            cancel-button-text="取消"
            @confirm="handleRemove(row as SysJobDeadLetterVO)"
          >
            <template #reference>
              <el-button link type="danger">
                删除
              </el-button>
            </template>
          </el-popconfirm>
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

  <!-- 死信详情 -->
  <el-dialog v-model="detailVisible" title="死信详情" width="820px" destroy-on-close>
    <el-descriptions v-if="detail" :column="2" border size="small">
      <el-descriptions-item label="任务键">
        {{ detail.jobKey }}
      </el-descriptions-item>
      <el-descriptions-item label="任务名称">
        {{ detail.jobName }}
      </el-descriptions-item>
      <el-descriptions-item label="处理状态">
        {{ detail.statusDesc ?? detail.status }}
      </el-descriptions-item>
      <el-descriptions-item label="累计重试">
        {{ detail.totalRetryCount }} 次
      </el-descriptions-item>
      <el-descriptions-item label="死信时间">
        {{ detail.createTime }}
      </el-descriptions-item>
      <el-descriptions-item label="重放时间">
        {{ detail.replayTime ?? '—' }}
      </el-descriptions-item>
    </el-descriptions>
    <el-divider content-position="left">
      任务配置快照（死信产生时刻）
    </el-divider>
    <pre v-if="prettyConfig" class="config-pre">{{ prettyConfig }}</pre>
    <el-divider content-position="left">
      最终异常
    </el-divider>
    <pre class="error-pre">{{ detail?.errorType }}
    {{ detail?.errorStack ?? '（无异常信息）' }}</pre>
  </el-dialog>
</template>

<script setup lang="ts">
/**
 * 死信任务管理页：死信检索、详情（配置快照 + 全量异常）、手动重放与删除。
 *
 * <p>重放结果即时反馈：成功 → 状态置"已重放成功"；失败 → "重放失败"（可再次重放）。
 * "查看执行历史"跳转执行详情页并按来源记录定位。
 */
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
} = useTablePage<{ jobKey?: string, status?: DeadLetterStatus }, SysJobDeadLetterVO>(
  data => jobApi.pageDeadLetters(data),
  () => ({
    jobKey: '',
    status: undefined,
  }),
)

/* ==================== 详情 ==================== */

const detailVisible = ref(false)
const detail = ref<SysJobDeadLetterVO>()
const prettyConfig = computed(() => {
  if (!detail.value?.jobConfig)
    return ''
  try {
    return JSON.stringify(JSON.parse(detail.value.jobConfig), null, 2)
  } catch {
    return detail.value.jobConfig
  }
})

async function openDetail(row: SysJobDeadLetterVO) {
  detail.value = row
  detailVisible.value = true
}

/* ==================== 重放 ==================== */

const replayingId = ref('')

/** 手动重放：重置重试计数，重新走完整执行流程 */
async function handleReplay(row: SysJobDeadLetterVO) {
  const confirmed = await ElMessageBox.confirm(
    `确认重放任务「${row.jobName}」的死信吗？将按任务当前配置重新执行完整流程（重试计数重置）。`,
    '死信重放确认',
    { type: 'warning', confirmButtonText: '重放', cancelButtonText: '取消' },
  ).then(() => true).catch(() => false)
  if (!confirmed)
    return
  replayingId.value = row.id
  try {
    const result = await jobApi.replayDeadLetter(row.id)
    if (result.success)
      ElMessage.success(`重放成功（执行记录 ${result.logId}）`)
    else
      ElMessage.warning(`重放后仍失败：${result.status}，可修正后再次重放`)
    load()
  } finally {
    replayingId.value = ''
  }
}

/* ==================== 删除 ==================== */

async function handleRemove(row: SysJobDeadLetterVO) {
  await jobApi.removeDeadLetter(row.id)
  ElMessage.success('删除成功')
  load()
}

/* ==================== 展示辅助 ==================== */

function statusTagType(status: DeadLetterStatus | string) {
  switch (status) {
    case 'RESOLVED':
      return 'success'
    case 'REPLAY_FAILED':
      return 'danger'
    default:
      return 'warning'
  }
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

.config-pre,
.error-pre {
  max-height: 320px;
  overflow: auto;
  margin: 0;
  padding: 8px;
  background: var(--el-fill-color-light);
  border-radius: 4px;
  font-family: ui-monospace, monospace;
  font-size: 12px;
  line-height: 1.5;
  white-space: pre-wrap;
}
</style>
