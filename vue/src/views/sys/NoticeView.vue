<template>
  <el-card shadow="never" class="search-card">
    <!-- 搜索筛选区（无查询字段时省略） -->
    <el-form inline class="search-form" @submit.prevent="search">
      <el-form-item label="通知标题">
        <el-input v-model="query.title" placeholder="模糊匹配" clearable @keyup.enter="search" />
      </el-form-item>
      <el-form-item label="通知类型；0=通知 1=公告">
        <el-select v-model="query.noticeType" placeholder="全部" clearable class="filter-select">
          <el-option
            v-for="item in noticeTypeOptions" :key="item.value" :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="通知内容">
        <el-input v-model="query.content" placeholder="模糊匹配" clearable @keyup.enter="search" />
      </el-form-item>
      <el-form-item label="是否置顶">
        <el-select v-model="query.topMarked" placeholder="全部" clearable>
          <el-option :value="true" label="已置顶" />
          <el-option :value="false" label="未置顶" />
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

  <!-- 数据列表卡片 -->
  <el-card shadow="never" class="table-card">
    <!-- 工具栏：新增/批量删除 + 列设置 -->
    <div class="table-toolbar">
      <div>
        <el-button type="primary" :icon="Plus" @click="openCreate">
          新增通知公告
        </el-button>
        <el-button
          type="danger" plain :icon="Delete" :disabled="selection.length === 0"
          @click="handleBatchRemove"
        >
          批量删除
        </el-button>
      </div>
      <ColumnSetting v-model="visibleColumns" :options="columnOptions" />
    </div>

    <!-- 数据列表 -->
    <el-table
      v-loading="loading"
      :data="rows"
      row-key="id"
      border
      stripe
      @selection-change="selection = $event"
      @sort-change="sortChange"
    >
      <el-table-column type="selection" width="44" />
      <el-table-column v-if="visibleColumns.includes('id')" prop="id" label="ID" width="90" />
      <el-table-column
        v-if="visibleColumns.includes('title')" prop="title" label="通知标题"
        min-width="130" sortable="custom" show-overflow-tooltip
      />
      <el-table-column
        v-if="visibleColumns.includes('noticeType')" prop="noticeType"
        label="通知类型；0=通知 1=公告" min-width="130" show-overflow-tooltip
      >
        <template #default="{ row }">
          {{ noticeTypeOptions.find(o => o.value === row.noticeType)?.label ?? row.noticeType }}
        </template>
      </el-table-column>
      <el-table-column
        v-if="visibleColumns.includes('publishTime')" prop="publishTime"
        label="发布时间" width="170" sortable="custom"
      />
      <el-table-column
        v-if="visibleColumns.includes('topMarked')" prop="topMarked"
        label="是否置顶" width="90"
      >
        <template #default="{ row }">
          <el-tag :type="row.topMarked ? 'warning' : 'info'">
            {{ row.topMarked ? '已置顶' : '未置顶' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column
        v-if="visibleColumns.includes('createTime')" prop="createTime"
        label="createTime" width="170"
      />
      <el-table-column
        v-if="visibleColumns.includes('updateTime')" prop="updateTime"
        label="updateTime" width="170"
      />
      <el-table-column label="操作" fixed="right" width="140">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row as SysNoticeVO)">
            编辑
          </el-button>
          <el-popconfirm
            title="确认删除该通知公告？" confirm-button-text="删除"
            cancel-button-text="取消" @confirm="handleRemove(row as SysNoticeVO)"
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

    <!-- 分页 -->
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

  <!-- 新增通知公告 -->
  <el-dialog v-model="createVisible" title="新增通知公告" width="780px" destroy-on-close>
    <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="100px">
      <el-form-item label="通知标题" prop="title">
        <el-input v-model="createForm.title" placeholder="请输入通知标题" maxlength="100" />
      </el-form-item>
      <el-form-item label="通知类型" prop="noticeType">
        <el-select v-model="createForm.noticeType" placeholder="请选择通知类型" clearable>
          <el-option
            v-for="item in noticeTypeOptions" :key="item.value" :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="通知内容" prop="content">
        <el-input
          v-model="createForm.content" type="textarea" :rows="4"
          placeholder="请输入通知内容" maxlength="2000"
        />
      </el-form-item>
      <el-form-item label="发布时间" prop="publishTime">
        <el-date-picker
          v-model="createForm.publishTime" type="datetime"
          value-format="YYYY-MM-DD HH:mm:ss" placeholder="请选择发布时间"
        />
      </el-form-item>
      <el-form-item label="是否置顶" prop="topMarked">
        <el-switch v-model="createForm.topMarked" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="createVisible = false">
        取消
      </el-button>
      <el-button type="primary" :loading="submitting" @click="submitCreate">
        确定
      </el-button>
    </template>
  </el-dialog>

  <!-- 编辑通知公告 -->
  <el-dialog v-model="editVisible" title="编辑通知公告" width="780px" destroy-on-close>
    <el-form ref="editFormRef" :model="editForm" :rules="editRules" label-width="100px">
      <el-form-item label="通知标题" prop="title">
        <el-input v-model="editForm.title" placeholder="请输入通知标题" maxlength="100" />
      </el-form-item>
      <el-form-item label="通知类型；0=通知 1=公告" prop="noticeType">
        <el-select
          v-model="editForm.noticeType" placeholder="请选择通知类型；0=通知 1=公告"
          clearable
        >
          <el-option
            v-for="item in noticeTypeOptions" :key="item.value" :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="通知内容" prop="content">
        <el-input
          v-model="editForm.content" type="textarea" :rows="4" placeholder="请输入通知内容"
          maxlength="2000"
        />
      </el-form-item>
      <el-form-item label="发布时间" prop="publishTime">
        <el-date-picker
          v-model="editForm.publishTime" type="datetime"
          value-format="YYYY-MM-DD HH:mm:ss" placeholder="请选择发布时间"
        />
      </el-form-item>
      <el-form-item label="是否置顶" prop="topMarked">
        <el-switch v-model="editForm.topMarked" />
      </el-form-item>
    </el-form>
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
 * 通知公告管理页：列表（分页/排序/列设置/批量删除）+ 新增/编辑表单。
 *
 * <p>由代码生成器生成（模板：view.ftl）；校验规则与后端 SysNoticeCreateCmd/SysNoticeUpdateCmd
 * 的 Bean Validation 注解逐条对齐，提交前经 el-form 预校验拦截非法请求。
 */
/* ==================== 列表与搜索 ==================== */

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
} = useTablePage<SysNoticeQuery, SysNoticeVO>(data => noticeApi.page(data), () => ({
  title: '',
  noticeType: '',
  content: '',
  topMarked: undefined,
}))

/** 列设置候选与当前展示列 */
const columnOptions = [
  { value: 'id', label: 'ID' },
  { value: 'title', label: '通知标题' },
  { value: 'noticeType', label: '通知类型；0=通知 1=公告' },
  { value: 'publishTime', label: '发布时间' },
  { value: 'topMarked', label: '是否置顶' },
  { value: 'createTime', label: 'createTime' },
  { value: 'updateTime', label: 'updateTime' },
]
const visibleColumns = ref<string[]>(columnOptions.map(option => option.value))

/** 多选行（批量删除用） */
const selection = ref<SysNoticeVO[]>([])

/* ==================== 下拉选项 ==================== */

/** 通知类型；0=通知 1=公告静态选项（由字段配置 options_json 生成） */
const noticeTypeOptions = [
  { value: '0', label: '通知' },
  { value: '1', label: '公告' },
] as Array<{ value: string, label: string }>

/* ==================== 新增/编辑表单 ==================== */

const createVisible = ref(false)
const editVisible = ref(false)
const submitting = ref(false)
const createFormRef = ref<FormInstance>()
const editFormRef = ref<FormInstance>()

const createForm = reactive<SysNoticeCreateCmd>({
  title: '',
  noticeType: '',
  content: '',
  publishTime: null,
  topMarked: false,
})

/** 编辑行 id（Long 序列化为字符串） */
const editId = ref('')
const editForm = reactive<SysNoticeUpdateCmd>({
  title: '',
  noticeType: '',
  content: '',
  publishTime: null,
  topMarked: false,
})

const createRules: FormRules = {
  title: [
    { required: true, message: '通知标题不能为空', trigger: 'blur' },
    { max: 100, message: '通知标题最长 100 字符', trigger: 'blur' },
  ],
  noticeType: [
    { required: true, message: '通知类型不能为空', trigger: 'change' },
  ],
  content: [
    { required: true, message: '通知内容不能为空', trigger: 'blur' },
    { max: 2000, message: '通知内容最长 2000 字符', trigger: 'blur' },
  ],
}

const editRules: FormRules = {
  title: [
    { required: true, message: '通知标题不能为空', trigger: 'blur' },
    { max: 100, message: '通知标题最长 100 字符', trigger: 'blur' },
  ],
  noticeType: [
    { required: true, message: '通知类型不能为空', trigger: 'change' },
  ],
  content: [
    { required: true, message: '通知内容不能为空', trigger: 'blur' },
    { max: 2000, message: '通知内容最长 2000 字符', trigger: 'blur' },
  ],
}

/* ==================== 操作 ==================== */

function openCreate() {
  Object.assign(createForm, {
    title: '',
    noticeType: '',
    content: '',
    publishTime: null,
    topMarked: false,
  })
  createVisible.value = true
}

async function submitCreate() {
  try {
    await createFormRef.value?.validate()
  } catch {
    return
  }
  submitting.value = true
  try {
    await noticeApi.create(createForm)
    ElMessage.success('创建成功')
    createVisible.value = false
    load()
  } finally {
    submitting.value = false
  }
}

function openEdit(row: SysNoticeVO) {
  editId.value = row.id
  Object.assign(editForm, {
    title: row.title ?? '',
    noticeType: row.noticeType ?? '',
    content: row.content ?? '',
    publishTime: row.publishTime ?? null,
    topMarked: row.topMarked ?? false,
  })
  editVisible.value = true
}

async function submitEdit() {
  try {
    await editFormRef.value?.validate()
  } catch {
    return
  }
  submitting.value = true
  try {
    await noticeApi.update(editId.value, editForm)
    ElMessage.success('更新成功')
    editVisible.value = false
    load()
  } finally {
    submitting.value = false
  }
}

async function handleRemove(row: SysNoticeVO) {
  await noticeApi.remove(row.id)
  ElMessage.success('删除成功')
  load()
}

async function handleBatchRemove() {
  const ids = selection.value.map(row => row.id)
  await ElMessageBox.confirm(`确认删除选中的 ${ids.length} 条通知公告？`, '批量删除', { type: 'warning' })
  const results = await Promise.allSettled(ids.map(id => noticeApi.remove(id)))
  const failed = results.filter(result => result.status === 'rejected').length
  if (failed === 0)
    ElMessage.success('批量删除成功')
  else
    ElMessage.warning(`批量删除完成：成功 ${ids.length - failed} 条，失败 ${failed} 条`)
  load()
}

onMounted(load)
</script>

<style scoped>
.search-form :deep(.el-form-item) {
  margin-bottom: 12px;
}

.filter-select {
  width: 130px;
}

.table-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}

.page-pagination {
  justify-content: flex-end;
  margin-top: 12px;
}
</style>
