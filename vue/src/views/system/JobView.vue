<template>
  <el-card shadow="never" class="search-card">
    <el-form inline class="search-form" @submit.prevent="search">
      <el-form-item label="任务键">
        <el-input v-model="query.jobKey" placeholder="模糊匹配" clearable @keyup.enter="search" />
      </el-form-item>
      <el-form-item label="任务名称">
        <el-input v-model="query.name" placeholder="模糊匹配" clearable @keyup.enter="search" />
      </el-form-item>
      <el-form-item label="触发类型">
        <el-select v-model="query.triggerType" placeholder="全部" clearable class="type-select">
          <el-option
            v-for="item in triggerTypeOptions" :key="item.value" :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="状态">
        <el-radio-group v-model="query.enabled" placeholder="全部" clearable @change="search">
          <el-radio-button label="启用中" :value="true" />
          <el-radio-button label="已暂停" :value="false" />
        </el-radio-group>
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
      <div>
        <el-button type="primary" :icon="Plus" @click="openCreate">
          新增任务
        </el-button>
      </div>
      <el-button :icon="Refresh" @click="load">
        刷新
      </el-button>
    </div>

    <el-table v-loading="loading" :data="rows" row-key="id" border stripe>
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="jobKey" label="任务键" min-width="150" sortable="custom" show-overflow-tooltip />
      <el-table-column prop="name" label="任务名称" min-width="130" sortable="custom" show-overflow-tooltip />
      <el-table-column label="触发配置" min-width="170" show-overflow-tooltip>
        <template #default="{ row }">
          <el-tag type="info" size="small" class="trigger-tag">
            {{ row.triggerTypeDesc ?? row.triggerType }}
          </el-tag>
          {{ formatTriggerValue(row as SysJobVO) }}
        </template>
      </el-table-column>
      <el-table-column label="重试策略" min-width="130" show-overflow-tooltip>
        <template #default="{ row }">
          {{ (row as SysJobVO).retryPolicyDesc }}（{{ (row as SysJobVO).maxRetryCount }} 次）
        </template>
      </el-table-column>
      <el-table-column label="超时" width="90">
        <template #default="{ row }">
          {{
            (row as SysJobVO).timeoutMillis === '0' ? '不限' : `${Number((row as SysJobVO).timeoutMillis) / 1000}s`
          }}
        </template>
      </el-table-column>
      <el-table-column label="冲突策略" width="90">
        <template #default="{ row }">
          {{ (row as SysJobVO).conflictStrategyDesc ?? row.conflictStrategy }}
        </template>
      </el-table-column>
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="(row as SysJobVO).enabled ? 'success' : 'info'">
            {{ (row as SysJobVO).enabled ? '启用中' : '已暂停' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="updateTime" label="更新时间" width="170" sortable="custom" />
      <el-table-column label="操作" fixed="right" width="250">
        <template #default="{ row }">
          <el-button
            link type="success" :loading="triggeringId === row.id"
            @click="handleTrigger(row as SysJobVO)"
          >
            执行
          </el-button>
          <el-button link type="primary" @click="openEdit(row as SysJobVO)">
            编辑
          </el-button>
          <el-button
            link :type="(row as SysJobVO).enabled ? 'warning' : 'success'"
            @click="handleToggle(row as SysJobVO)"
          >
            {{ (row as SysJobVO).enabled ? '暂停' : '恢复' }}
          </el-button>
          <el-button link type="danger" @click="handleLogs(row as SysJobVO)">
            记录
          </el-button>
          <el-popconfirm
            title="确认删除该任务？（调度停止，执行历史保留）" confirm-button-text="删除"
            cancel-button-text="取消" @confirm="handleRemove(row as SysJobVO)"
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

  <!-- 新增任务 -->
  <el-dialog v-model="createVisible" title="新增任务" width="780px" destroy-on-close>
    <JobForm ref="createFormRef" :model-value="createForm" />
    <template #footer>
      <el-button @click="createVisible = false">
        取消
      </el-button>
      <el-button type="primary" :loading="submitting" @click="submitCreate">
        确定
      </el-button>
    </template>
  </el-dialog>

  <!-- 编辑任务 -->
  <el-dialog v-model="editVisible" title="编辑任务" width="780px" destroy-on-close>
    <el-alert type="info" :closable="false" show-icon class="edit-tip">
      任务键不可修改（调度与执行历史按任务键关联）；保存后调度计划立即生效，无需重启。
    </el-alert>
    <JobForm ref="editFormRef" :model-value="editForm" />
    <template #footer>
      <el-button @click="editVisible = false">
        取消
      </el-button>
      <el-button type="primary" :loading="submitting" @click="submitEdit">
        确定
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
/**
 * 任务调度管理页（任务列表页）：任务定义的增删改查、启停、手动执行，
 * 以及跳转执行记录页（按任务键过滤）。
 *
 * <p>校验规则与后端 SysJobCreateCmd/SysJobUpdateCmd 的 Bean Validation 逐条对齐；
 * CRON 合法性/周期下限/重试间隔约束由后端业务校验兜底（608003/608005）。
 * 配置变更保存后即时生效（后端调度器重注册，免重启）。
 */
import JobForm from './job/JobForm.vue'

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
} = useTablePage<SysJobQuery, SysJobVO>(data => jobApi.page(data), () => ({
  jobKey: '',
  name: '',
  triggerType: undefined,
  enabled: true,
}))

// 首次进入页面自动查询（useTablePage 只提供加载方法，不自动请求）
onMounted(load)

const triggerTypeOptions = [
  { value: 'CRON', label: 'CRON 表达式' },
  { value: 'FIXED_RATE', label: '固定频率' },
  { value: 'FIXED_DELAY', label: '固定延迟' },
] as const

/** 触发值展示：CRON 原样、周期转人类可读 */
function formatTriggerValue(row: SysJobVO) {
  if (row.triggerType === 'CRON') {
    return row.triggerValue
  }
  const millis = Number(row.triggerValue)
  if (Number.isNaN(millis)) {
    return row.triggerValue
  }
  return millis % 60000 === 0
    ? `每 ${millis / 60000} 分钟`
    : `每 ${millis / 1000} 秒`
}

/* ==================== 手动执行 ==================== */

const triggeringId = ref('')

/** 手动立即执行一次；按钮转圈等待结果，结束提示终态 */
async function handleTrigger(row: SysJobVO) {
  triggeringId.value = row.id
  try {
    const result = await jobApi.trigger(row.id)
    if (result.success)
      ElMessage.success(`任务已执行成功（记录 ${result.logId}）`)
    else
      ElMessage.warning(`任务执行结束：${result.status}`)
  } finally {
    triggeringId.value = ''
  }
}

/* ==================== 启停 ==================== */

/** 暂停/恢复切换 */
async function handleToggle(row: SysJobVO) {
  if (row.enabled) {
    await jobApi.pause(row.id)
  } else {
    await jobApi.resume(row.id)
  }
  ElMessage.success(row.enabled ? '已暂停' : '已恢复')
  load()
}

/* ==================== 删除 ==================== */

async function handleRemove(row: SysJobVO) {
  await jobApi.remove(row.id)
  ElMessage.success('删除成功')
  load()
}

/* ==================== 执行记录跳转 ==================== */

const router = useRouter()

function handleLogs(row: SysJobVO) {
  router.push({
    path: '/system/job/log',
    query: { jobKey: row.jobKey },
  })
}

/* ==================== 新增/编辑表单 ==================== */

const createVisible = ref(false)
const editVisible = ref(false)
const submitting = ref(false)
const createFormRef = ref<InstanceType<typeof JobForm>>()
const editFormRef = ref<InstanceType<typeof JobForm>>()

const createForm = reactive<SysJobCreateCmd>({
  jobKey: '',
  name: '',
  triggerType: 'CRON',
  triggerValue: '',
  timeoutMillis: '0',
  retryPolicy: 'FIXED_INTERVAL',
  maxRetryCount: 0,
  retryInitialInterval: '60000',
  retryMaxInterval: '600000',
  retryInclude: '',
  retryExclude: '',
  conflictStrategy: 'DISCARD',
  alertFailureThreshold: 0,
  remark: '',
})

const editId = ref('')
const editForm = reactive<SysJobUpdateCmd>({
  name: '',
  triggerType: 'CRON',
  triggerValue: '',
  timeoutMillis: '0',
  retryPolicy: 'FIXED_INTERVAL',
  maxRetryCount: 0,
  retryInitialInterval: '60000',
  retryMaxInterval: '600000',
  retryInclude: '',
  retryExclude: '',
  conflictStrategy: 'DISCARD',
  alertFailureThreshold: 0,
  remark: '',
})

function openCreate() {
  Object.assign(createForm, {
    jobKey: '',
    name: '',
    triggerType: 'CRON',
    triggerValue: '',
    timeoutMillis: '0',
    retryPolicy: 'FIXED_INTERVAL',
    maxRetryCount: 0,
    retryInitialInterval: '60000',
    retryMaxInterval: '600000',
    retryInclude: '',
    retryExclude: '',
    conflictStrategy: 'DISCARD',
    alertFailureThreshold: 0,
    remark: '',
  })
  createVisible.value = true
}

async function submitCreate() {
  if (!await createFormRef.value?.validate())
    return
  submitting.value = true
  try {
    const values = createFormRef.value!.getFormValues()
    await jobApi.create(values as SysJobCreateCmd)
    ElMessage.success('创建成功，调度已生效')
    createVisible.value = false
    load()
  } finally {
    submitting.value = false
  }
}

function openEdit(row: SysJobVO) {
  editId.value = row.id
  Object.assign(editForm, {
    name: row.name,
    triggerType: row.triggerType,
    triggerValue: row.triggerValue,
    timeoutMillis: row.timeoutMillis,
    retryPolicy: row.retryPolicy,
    maxRetryCount: row.maxRetryCount,
    retryInitialInterval: row.retryInitialInterval,
    retryMaxInterval: row.retryMaxInterval,
    retryInclude: row.retryInclude ?? '',
    retryExclude: row.retryExclude ?? '',
    conflictStrategy: row.conflictStrategy,
    alertFailureThreshold: row.alertFailureThreshold,
    remark: row.remark ?? '',
  })
  editVisible.value = true
}

async function submitEdit() {
  if (!await editFormRef.value?.validate())
    return
  submitting.value = true
  try {
    await jobApi.update(editId.value, editFormRef.value!.getFormValues())
    ElMessage.success('更新成功，调度计划已生效')
    editVisible.value = false
    load()
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.search-card {
  margin-bottom: 12px;
}

.type-select {
  width: 140px;
}

.trigger-tag {
  margin-right: 4px;
}

.table-toolbar {
  display: flex;
  justify-content: space-between;
  margin-bottom: 12px;
}

.edit-tip {
  margin-bottom: 12px;
}
</style>
