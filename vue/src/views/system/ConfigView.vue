<template>
  <el-card shadow="never" class="search-card">
    <el-form inline class="search-form" @submit.prevent="search">
      <el-form-item label="参数键">
        <el-input v-model="query.configKey" placeholder="模糊匹配" clearable @keyup.enter="search" />
      </el-form-item>
      <el-form-item label="参数名称">
        <el-input v-model="query.name" placeholder="模糊匹配" clearable @keyup.enter="search" />
      </el-form-item>
      <el-form-item label="值类型">
        <el-select v-model="query.valueType" placeholder="全部" clearable class="value-type-select">
          <el-option v-for="item in valueTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
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
          新增配置
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
      <el-table-column v-if="visibleColumns.includes('configKey')" prop="configKey" label="参数键" min-width="160" sortable="custom" show-overflow-tooltip />
      <el-table-column v-if="visibleColumns.includes('configValue')" prop="configValue" label="参数值" min-width="160" show-overflow-tooltip />
      <el-table-column v-if="visibleColumns.includes('valueType')" prop="valueType" label="值类型" width="110">
        <template #default="{ row }">
          <el-tag type="info">
            {{ row.valueTypeDesc ?? row.valueType }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column v-if="visibleColumns.includes('name')" prop="name" label="参数名称" min-width="140" sortable="custom" show-overflow-tooltip />
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
          <el-button link type="primary" @click="openEdit(row as SysConfigVO)">
            编辑
          </el-button>
          <el-popconfirm title="确认删除该配置？" confirm-button-text="删除" cancel-button-text="取消" @confirm="handleRemove(row as SysConfigVO)">
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

  <!-- 新增配置 -->
  <el-dialog v-model="createVisible" title="新增配置" width="780px" destroy-on-close>
    <el-form ref="createFormRef" :model="createForm" :rules="configKeyRules" label-width="90px">
      <el-form-item label="参数键" prop="configKey">
        <el-input v-model="createForm.configKey" placeholder="点分命名（如 site.name），创建后不可修改" />
      </el-form-item>
      <el-form-item label="值类型" prop="valueType">
        <el-select v-model="createForm.valueType">
          <el-option v-for="item in valueTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="参数值" prop="configValue">
        <el-input v-if="createValueInputType === 'text' || createValueInputType === 'number'" v-model="createForm.configValue" placeholder="统一按字符串存储，最长 2000 字符" />
        <el-switch v-else-if="createValueInputType === 'switch'" v-model="createForm.configValue" active-value="true" inactive-value="false" />
        <el-input v-else v-model="createForm.configValue" type="textarea" :rows="5" placeholder="合法 JSON 文本" />
      </el-form-item>
      <el-form-item label="参数名称" prop="name">
        <el-input v-model="createForm.name" placeholder="必填，最长 100 字符" />
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

  <!-- 编辑配置 -->
  <el-dialog v-model="editVisible" title="编辑配置" width="780px" destroy-on-close>
    <el-alert type="info" :closable="false" show-icon class="edit-tip">
      参数键不支持修改（代码中以常量引用）
    </el-alert>
    <el-form ref="editFormRef" :model="editForm" :rules="editRules" label-width="90px">
      <el-form-item label="值类型" prop="valueType">
        <el-select v-model="editForm.valueType">
          <el-option v-for="item in valueTypeOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="参数值" prop="configValue">
        <el-input v-if="editValueInputType === 'text' || editValueInputType === 'number'" v-model="editForm.configValue" placeholder="统一按字符串存储，最长 2000 字符" />
        <el-switch v-else-if="editValueInputType === 'switch'" v-model="editForm.configValue" active-value="true" inactive-value="false" />
        <el-input v-else v-model="editForm.configValue" type="textarea" :rows="5" placeholder="合法 JSON 文本" />
      </el-form-item>
      <el-form-item label="参数名称" prop="name">
        <el-input v-model="editForm.name" placeholder="必填，最长 100 字符" />
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
 * 参数配置管理页：列表（分页/排序/列设置/批量删除）+ 新增/编辑表单。
 *
 * <p>校验规则与后端 SysConfigCreateCmd/SysConfigUpdateCmd 的 Bean Validation 注解逐条对齐；
 * 参数键创建后不可修改（编辑表单不提供），键重复返回 604001 由 request.ts 统一提示。
 * valueType 决定值的编辑控件：STRING 输入框 / NUMBER 数字框 / BOOLEAN 开关 / JSON 代码域。
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
} = useTablePage<SysConfigQuery, SysConfigVO>(data => configApi.page(data), () => ({
  configKey: '',
  name: '',
  valueType: undefined,
}))

/** 值类型选项（与后端 ConfigValueType 枚举一致） */
const valueTypeOptions = [
  { value: 'STRING', label: '字符串' },
  { value: 'NUMBER', label: '数字' },
  { value: 'BOOLEAN', label: '布尔' },
  { value: 'JSON', label: '结构化对象' },
] as const

const columnOptions = [
  { value: 'id', label: 'ID' },
  { value: 'configKey', label: '参数键' },
  { value: 'configValue', label: '参数值' },
  { value: 'valueType', label: '值类型' },
  { value: 'name', label: '参数名称' },
  { value: 'remark', label: '备注' },
  { value: 'createTime', label: '创建时间' },
]
const visibleColumns = ref<string[]>(columnOptions.map(option => option.value))

const selection = ref<SysConfigVO[]>([])

/* ==================== 新增/编辑表单 ==================== */

const createVisible = ref(false)
const editVisible = ref(false)
const submitting = ref(false)
const createFormRef = ref<FormInstance>()
const editFormRef = ref<FormInstance>()

const createForm = reactive<SysConfigCreateCmd>({
  configKey: '',
  configValue: '',
  valueType: 'STRING',
  name: '',
  remark: '',
})

const editId = ref('')
const editForm = reactive<SysConfigUpdateCmd>({
  configValue: '',
  valueType: 'STRING',
  name: '',
  remark: '',
})

/** 参数键校验规则：2~100 位点分命名格式（对齐后端 @Size + @Pattern） */
const configKeyRules: FormRules = {
  configKey: [
    { required: true, message: '参数键不能为空', trigger: 'blur' },
    {
      min: 2,
      max: 100,
      message: '参数键长度须在 2~100 位之间',
      trigger: 'blur',
    },
    {
      pattern: /^[\w-]+(\.[\w-]+)*$/,
      message: '参数键须为点分命名格式（如 site.name），仅允许字母、数字、下划线、连字符与点号',
      trigger: 'blur',
    },
  ],
  configValue: [
    { required: true, message: '参数值不能为空', trigger: 'blur' },
    { max: 2000, message: '参数值最长 2000 字符', trigger: 'blur' },
  ],
  valueType: [
    { required: true, message: '值类型不能为空', trigger: 'change' },
  ],
  name: [
    { required: true, message: '参数名称不能为空', trigger: 'blur' },
    { max: 100, message: '参数名称最长 100 字符', trigger: 'blur' },
  ],
  remark: [
    { max: 255, message: '备注最长 255 字符', trigger: 'blur' },
  ],
}

const editRules: FormRules = {
  configValue: [
    { required: true, message: '参数值不能为空', trigger: 'blur' },
    { max: 2000, message: '参数值最长 2000 字符', trigger: 'blur' },
  ],
  valueType: [
    { required: true, message: '值类型不能为空', trigger: 'change' },
  ],
  name: [
    { required: true, message: '参数名称不能为空', trigger: 'blur' },
    { max: 100, message: '参数名称最长 100 字符', trigger: 'blur' },
  ],
  remark: [
    { max: 255, message: '备注最长 255 字符', trigger: 'blur' },
  ],
}

/** 按值类型切换参数值编辑控件（新增/编辑共用） */
const createValueInputType = computed(() => editorType(createForm.valueType))
const editValueInputType = computed(() => editorType(editForm.valueType))

function editorType(valueType: ConfigValueType) {
  switch (valueType) {
    case 'NUMBER':
      return 'number'
    case 'BOOLEAN':
      return 'switch'
    case 'JSON':
      return 'json'
    default:
      return 'text'
  }
}

/* ==================== 操作 ==================== */

function openCreate() {
  Object.assign(createForm, {
    configKey: '',
    configValue: '',
    valueType: 'STRING',
    name: '',
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
    await configApi.create(createForm)
    ElMessage.success('创建成功')
    createVisible.value = false
    load()
  } finally {
    submitting.value = false
  }
}

function openEdit(row: SysConfigVO) {
  editId.value = row.id
  Object.assign(editForm, {
    configValue: row.configValue,
    valueType: row.valueType,
    name: row.name,
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
    await configApi.update(editId.value, editForm)
    ElMessage.success('更新成功')
    editVisible.value = false
    load()
  } finally {
    submitting.value = false
  }
}

async function handleRemove(row: SysConfigVO) {
  await configApi.remove(row.id)
  ElMessage.success('删除成功')
  load()
}

async function handleBatchRemove() {
  const ids = selection.value.map(row => row.id)
  await ElMessageBox.confirm(`确认删除选中的 ${ids.length} 条配置？`, '批量删除', { type: 'warning' })
  const results = await Promise.allSettled(ids.map(id => configApi.remove(id)))
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

.value-type-select {
  width: 140px;
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
