<template>
  <el-card shadow="never" class="search-card">
    <el-form inline class="search-form" @submit.prevent="search">
      <el-form-item label="角色编码">
        <el-input v-model="query.code" placeholder="模糊匹配" clearable @keyup.enter="search" />
      </el-form-item>
      <el-form-item label="角色名称">
        <el-input v-model="query.name" placeholder="模糊匹配" clearable @keyup.enter="search" />
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.enabled" placeholder="全部" clearable class="status-select">
          <el-option v-for="item in enabledOptions" :key="String(item.value)" :label="item.label" :value="item.value" />
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
    <div class="table-toolbar">
      <div>
        <el-button type="primary" :icon="Plus" @click="openCreate">
          新增角色
        </el-button>
        <el-button type="danger" plain :icon="Delete" :disabled="selection.length === 0" @click="handleBatchRemove">
          批量删除
        </el-button>
      </div>
      <ColumnSetting v-model="visibleColumns" :options="columnOptions" />
    </div>

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
      <el-table-column v-if="visibleColumns.includes('code')" prop="code" label="角色编码" min-width="130" show-overflow-tooltip />
      <el-table-column v-if="visibleColumns.includes('name')" prop="name" label="角色名称" min-width="130" show-overflow-tooltip />
      <el-table-column v-if="visibleColumns.includes('sort')" prop="sort" label="排序号" width="90" sortable="custom" />
      <el-table-column v-if="visibleColumns.includes('enabled')" prop="enabled" label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="row.enabled ? 'success' : 'danger'">
            {{ row.enabled ? '正常' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column v-if="visibleColumns.includes('remark')" prop="remark" label="备注" min-width="140" show-overflow-tooltip />
      <el-table-column
        v-if="visibleColumns.includes('createTime')"
        prop="createTime"
        label="创建时间"
        width="170"
        sortable="custom"
      />
      <el-table-column label="操作" fixed="right" width="140">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row as SysRoleVO)">
            编辑
          </el-button>
          <el-popconfirm title="确认删除该角色？" confirm-button-text="删除" cancel-button-text="取消" @confirm="handleRemove(row as SysRoleVO)">
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

  <!-- 新增角色 -->
  <el-dialog v-model="createVisible" title="新增角色" width="520px" destroy-on-close>
    <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="90px">
      <el-form-item label="角色编码" prop="code">
        <el-input v-model="createForm.code" placeholder="2~50 位字母/数字/下划线/连字符，创建后不可修改" />
      </el-form-item>
      <el-form-item label="角色名称" prop="name">
        <el-input v-model="createForm.name" placeholder="必填，最长 50 字符" />
      </el-form-item>
      <el-form-item label="排序号" prop="sort">
        <el-input-number v-model="createForm.sort" :min="0" :step="1" step-strictly />
      </el-form-item>
      <el-form-item label="状态" prop="enabled">
        <el-radio-group v-model="createForm.enabled">
          <el-radio v-for="item in enabledOptions" :key="String(item.value)" :value="item.value">
            {{ item.label }}
          </el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="备注" prop="remark">
        <el-input v-model="createForm.remark" type="textarea" :rows="2" placeholder="选填，最长 255 字符" />
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

  <!-- 编辑角色 -->
  <el-dialog v-model="editVisible" title="编辑角色" width="520px" destroy-on-close>
    <el-alert type="info" :closable="false" show-icon class="edit-tip">
      角色编码不支持修改（权限逻辑以编码引用）
    </el-alert>
    <el-form ref="editFormRef" :model="editForm" :rules="editRules" label-width="90px">
      <el-form-item label="角色名称" prop="name">
        <el-input v-model="editForm.name" placeholder="必填，最长 50 字符" />
      </el-form-item>
      <el-form-item label="排序号" prop="sort">
        <el-input-number v-model="editForm.sort" :min="0" :step="1" step-strictly />
      </el-form-item>
      <el-form-item label="状态" prop="enabled">
        <el-radio-group v-model="editForm.enabled">
          <el-radio v-for="item in enabledOptions" :key="String(item.value)" :value="item.value">
            {{ item.label }}
          </el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="备注" prop="remark">
        <el-input v-model="editForm.remark" type="textarea" :rows="2" placeholder="选填，最长 255 字符" />
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
 * 角色管理页：列表（分页/排序/列设置/批量删除）+ 新增/编辑表单。
 *
 * <p>校验规则与后端 SysRoleCreateCmd/SysRoleUpdateCmd 的 Bean Validation 注解逐条对齐；
 * 角色编码创建后不可修改（编辑表单不提供），编码重复返回 602002 由 request.ts 统一提示。
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
} = useTablePage<SysRoleQuery, SysRoleVO>(data => roleApi.page(data), () => ({
  code: '',
  name: '',
  enabled: undefined,
}))

const enabledOptions = [
  { value: true, label: '正常' },
  { value: false, label: '禁用' },
] as const

const columnOptions = [
  { value: 'id', label: 'ID' },
  { value: 'code', label: '角色编码' },
  { value: 'name', label: '角色名称' },
  { value: 'sort', label: '排序号' },
  { value: 'enabled', label: '状态' },
  { value: 'remark', label: '备注' },
  { value: 'createTime', label: '创建时间' },
]
const visibleColumns = ref<string[]>(columnOptions.map(option => option.value))

const selection = ref<SysRoleVO[]>([])

/* ==================== 新增/编辑表单 ==================== */

const createVisible = ref(false)
const editVisible = ref(false)
const submitting = ref(false)
const createFormRef = ref<FormInstance>()
const editFormRef = ref<FormInstance>()

const createForm = reactive<SysRoleCreateCmd>({
  code: '',
  name: '',
  sort: 0,
  enabled: true,
  remark: '',
})

const editId = ref('')
const editForm = reactive<SysRoleUpdateCmd>({
  name: '',
  sort: 0,
  enabled: true,
  remark: '',
})

/** 排序号规则：非负整数（对齐后端 @Min(0)） */
const sortRules: FormRules = {
  sort: [
    {
      type: 'number',
      min: 0,
      message: '排序号不能为负数',
      trigger: 'blur',
    },
  ],
}

const createRules: FormRules = {
  code: [
    { required: true, message: '角色编码不能为空', trigger: 'blur' },
    {
      min: 2,
      max: 50,
      message: '角色编码长度须在 2~50 位之间',
      trigger: 'blur',
    },
    { pattern: /^[\w-]+$/, message: '角色编码仅允许字母、数字、下划线、连字符', trigger: 'blur' },
  ],
  name: [
    { required: true, message: '角色名称不能为空', trigger: 'blur' },
    { max: 50, message: '角色名称最长 50 字符', trigger: 'blur' },
  ],
  ...sortRules,
  enabled: [
    { required: true, message: '角色状态不能为空', trigger: 'change' },
  ],
  remark: [
    { max: 255, message: '备注最长 255 字符', trigger: 'blur' },
  ],
}

const editRules: FormRules = {
  name: [
    { required: true, message: '角色名称不能为空', trigger: 'blur' },
    { max: 50, message: '角色名称最长 50 字符', trigger: 'blur' },
  ],
  ...sortRules,
  enabled: [
    { required: true, message: '角色状态不能为空', trigger: 'change' },
  ],
  remark: [
    { max: 255, message: '备注最长 255 字符', trigger: 'blur' },
  ],
}

/* ==================== 操作 ==================== */

function openCreate() {
  Object.assign(createForm, {
    code: '',
    name: '',
    sort: 0,
    enabled: true,
    remark: '',
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
    await roleApi.create(createForm)
    ElMessage.success('创建成功')
    createVisible.value = false
    load()
  } finally {
    submitting.value = false
  }
}

function openEdit(row: SysRoleVO) {
  editId.value = row.id
  Object.assign(editForm, {
    name: row.name,
    sort: row.sort,
    enabled: row.enabled,
    remark: row.remark ?? '',
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
    await roleApi.update(editId.value, editForm)
    ElMessage.success('更新成功')
    editVisible.value = false
    load()
  } finally {
    submitting.value = false
  }
}

async function handleRemove(row: SysRoleVO) {
  await roleApi.remove(row.id)
  ElMessage.success('删除成功')
  load()
}

async function handleBatchRemove() {
  const ids = selection.value.map(row => row.id)
  await ElMessageBox.confirm(`确认删除选中的 ${ids.length} 个角色？`, '批量删除', { type: 'warning' })
  const results = await Promise.allSettled(ids.map(id => roleApi.remove(id)))
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

.status-select {
  width: 120px;
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

.edit-tip {
  margin-bottom: 16px;
}
</style>
