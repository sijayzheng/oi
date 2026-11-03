<template>
  <el-row :gutter="16" class="dict-page">
    <!-- 左栏：字典类型 -->
    <el-col :xs="24" :md="10" :xl="9">
      <el-card shadow="never" class="pane-card">
        <template #header>
          <span class="pane-title">字典类型</span>
        </template>
        <el-form inline class="search-form" @submit.prevent="searchTypes">
          <el-form-item label="编码">
            <el-input v-model="typeQuery.dictType" placeholder="模糊匹配" clearable @keyup.enter="searchTypes" />
          </el-form-item>
          <el-form-item label="名称">
            <el-input v-model="typeQuery.name" placeholder="模糊匹配" clearable @keyup.enter="searchTypes" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :icon="Search" @click="searchTypes">
              搜索
            </el-button>
            <el-button :icon="Refresh" @click="resetTypes">
              重置
            </el-button>
          </el-form-item>
        </el-form>

        <div class="table-toolbar">
          <el-button type="primary" :icon="Plus" @click="openTypeCreate">
            新增类型
          </el-button>
        </div>

        <el-table
          v-loading="typeLoading"
          :data="typeRows"
          row-key="id"
          border
          highlight-current-row
          class="dict-type-table"
          @row-click="selectType"
          @sort-change="typeSortChange"
        >
          <el-table-column prop="dictType" label="字典编码" min-width="130" show-overflow-tooltip />
          <el-table-column prop="name" label="字典名称" min-width="120" show-overflow-tooltip />
          <el-table-column prop="enabled" label="状态" width="80">
            <template #default="{ row }">
              <el-tag :type="row.enabled ? 'success' : 'danger'">
                {{ row.enabled ? '正常' : '禁用' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="120">
            <template #default="{ row }">
              <el-button link type="primary" @click.stop="openTypeEdit(row as SysDictTypeVO)">
                编辑
              </el-button>
              <el-popconfirm title="删除类型将级联删除其全部字典项，确认？" confirm-button-text="删除" cancel-button-text="取消" width="240" @confirm="handleTypeRemove(row as SysDictTypeVO)">
                <template #reference>
                  <el-button link type="danger" @click.stop>
                    删除
                  </el-button>
                </template>
              </el-popconfirm>
            </template>
          </el-table-column>
        </el-table>

        <el-pagination
          v-model:current-page="typePageNum"
          v-model:page-size="typePageSize"
          :total="typeTotal"
          :page-sizes="[10, 20, 50]"
          layout="total, prev, pager, next"
          small
          background
          class="page-pagination"
          @change="typePageChange"
        />
      </el-card>
    </el-col>

    <!-- 右栏：字典项 -->
    <el-col :xs="24" :md="14" :xl="15">
      <el-card shadow="never" class="pane-card">
        <template #header>
          <span class="pane-title">
            字典项
            <el-tag v-if="selectedType" type="primary" class="selected-tag">
              {{ selectedType.dictType }}（{{ selectedType.name }}）
            </el-tag>
          </span>
        </template>

        <el-empty v-if="!selectedType" description="请选择左侧字典类型查看字典项" />

        <template v-else>
          <el-form inline class="search-form" @submit.prevent="() => { itemQuery.dictType = selectedType!.dictType; loadItems() }">
            <el-form-item label="显示名">
              <el-input v-model="itemQuery.itemLabel" placeholder="模糊匹配" clearable @keyup.enter="loadItems" />
            </el-form-item>
            <el-form-item label="状态">
              <el-select v-model="itemQuery.enabled" placeholder="全部" clearable class="status-select">
                <el-option v-for="item in enabledOptions" :key="String(item.value)" :label="item.label" :value="item.value" />
              </el-select>
            </el-form-item>
            <el-form-item>
              <el-button type="primary" :icon="Search" @click="() => { itemQuery.dictType = selectedType!.dictType; loadItems() }">
                搜索
              </el-button>
              <el-button :icon="Refresh" @click="resetItems">
                重置
              </el-button>
            </el-form-item>
          </el-form>

          <div class="table-toolbar">
            <el-button type="primary" :icon="Plus" @click="openItemCreate">
              新增字典项
            </el-button>
          </div>

          <el-table v-loading="itemLoading" :data="itemRows" row-key="id" border stripe @sort-change="itemSortChange">
            <el-table-column prop="itemCode" label="项编码" min-width="120" show-overflow-tooltip />
            <el-table-column prop="itemLabel" label="项显示名" min-width="130" show-overflow-tooltip />
            <el-table-column prop="sort" label="排序号" width="80" sortable="custom" />
            <el-table-column prop="enabled" label="状态" width="80">
              <template #default="{ row }">
                <el-tag :type="row.enabled ? 'success' : 'danger'">
                  {{ row.enabled ? '正常' : '禁用' }}
                </el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="remark" label="备注" min-width="130" show-overflow-tooltip />
            <el-table-column label="操作" width="120">
              <template #default="{ row }">
                <el-button link type="primary" @click="openItemEdit(row as SysDictItemVO)">
                  编辑
                </el-button>
                <el-popconfirm title="确认删除该字典项？" confirm-button-text="删除" cancel-button-text="取消" @confirm="handleItemRemove(row as SysDictItemVO)">
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
            v-model:current-page="itemPageNum"
            v-model:page-size="itemPageSize"
            :total="itemTotal"
            :page-sizes="[10, 20, 50]"
            layout="total, prev, pager, next"
            small
            background
            class="page-pagination"
            @change="itemPageChange"
          />
        </template>
      </el-card>
    </el-col>
  </el-row>

  <!-- 新增字典类型 -->
  <el-dialog v-model="typeCreateVisible" title="新增字典类型" width="520px" destroy-on-close>
    <el-form ref="typeCreateFormRef" :model="typeCreateForm" :rules="typeCreateRules" label-width="90px">
      <el-form-item label="字典编码" prop="dictType">
        <el-input v-model="typeCreateForm.dictType" placeholder="小写下划线格式（如 user_status），创建后不可修改" />
      </el-form-item>
      <el-form-item label="字典名称" prop="name">
        <el-input v-model="typeCreateForm.name" placeholder="必填，最长 100 字符" />
      </el-form-item>
      <el-form-item label="状态" prop="enabled">
        <el-radio-group v-model="typeCreateForm.enabled">
          <el-radio v-for="item in enabledOptions" :key="String(item.value)" :value="item.value">
            {{ item.label }}
          </el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="备注" prop="remark">
        <el-input v-model="typeCreateForm.remark" type="textarea" :rows="2" placeholder="选填，最长 255 字符" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="typeCreateVisible = false">
        取消
      </el-button>
      <el-button type="primary" :loading="typeSubmitting" @click="submitTypeCreate">
        确定
      </el-button>
    </template>
  </el-dialog>

  <!-- 编辑字典类型 -->
  <el-dialog v-model="typeEditVisible" title="编辑字典类型" width="520px" destroy-on-close>
    <el-alert type="info" :closable="false" show-icon class="edit-tip">
      字典编码不支持修改（被字典项冗余引用）
    </el-alert>
    <el-form ref="typeEditFormRef" :model="typeEditForm" :rules="typeEditRules" label-width="90px">
      <el-form-item label="字典名称" prop="name">
        <el-input v-model="typeEditForm.name" placeholder="必填，最长 100 字符" />
      </el-form-item>
      <el-form-item label="状态" prop="enabled">
        <el-radio-group v-model="typeEditForm.enabled">
          <el-radio v-for="item in enabledOptions" :key="String(item.value)" :value="item.value">
            {{ item.label }}
          </el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="备注" prop="remark">
        <el-input v-model="typeEditForm.remark" type="textarea" :rows="2" placeholder="选填，最长 255 字符" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="typeEditVisible = false">
        取消
      </el-button>
      <el-button type="primary" :loading="typeSubmitting" @click="submitTypeEdit">
        确定
      </el-button>
    </template>
  </el-dialog>

  <!-- 新增字典项 -->
  <el-dialog v-model="itemCreateVisible" title="新增字典项" width="520px" destroy-on-close>
    <el-form ref="itemCreateFormRef" :model="itemCreateForm" :rules="itemCreateRules" label-width="90px">
      <el-form-item label="所属字典">
        <el-input :model-value="itemCreateForm.dictType" disabled />
      </el-form-item>
      <el-form-item label="项编码" prop="itemCode">
        <el-input v-model="itemCreateForm.itemCode" placeholder="业务存储值，同字典内唯一，创建后不可修改" />
      </el-form-item>
      <el-form-item label="项显示名" prop="itemLabel">
        <el-input v-model="itemCreateForm.itemLabel" placeholder="必填，最长 100 字符" />
      </el-form-item>
      <el-form-item label="排序号" prop="sort">
        <el-input-number v-model="itemCreateForm.sort" :min="0" :step="1" step-strictly />
      </el-form-item>
      <el-form-item label="状态" prop="enabled">
        <el-radio-group v-model="itemCreateForm.enabled">
          <el-radio v-for="item in enabledOptions" :key="String(item.value)" :value="item.value">
            {{ item.label }}
          </el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="备注" prop="remark">
        <el-input v-model="itemCreateForm.remark" type="textarea" :rows="2" placeholder="选填，最长 255 字符" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="itemCreateVisible = false">
        取消
      </el-button>
      <el-button type="primary" :loading="itemSubmitting" @click="submitItemCreate">
        确定
      </el-button>
    </template>
  </el-dialog>

  <!-- 编辑字典项 -->
  <el-dialog v-model="itemEditVisible" title="编辑字典项" width="520px" destroy-on-close>
    <el-alert type="info" :closable="false" show-icon class="edit-tip">
      所属字典与项编码不支持修改（itemCode 为业务实际存储值）
    </el-alert>
    <el-form ref="itemEditFormRef" :model="itemEditForm" :rules="itemEditRules" label-width="90px">
      <el-form-item label="项显示名" prop="itemLabel">
        <el-input v-model="itemEditForm.itemLabel" placeholder="必填，最长 100 字符" />
      </el-form-item>
      <el-form-item label="排序号" prop="sort">
        <el-input-number v-model="itemEditForm.sort" :min="0" :step="1" step-strictly />
      </el-form-item>
      <el-form-item label="状态" prop="enabled">
        <el-radio-group v-model="itemEditForm.enabled">
          <el-radio v-for="item in enabledOptions" :key="String(item.value)" :value="item.value">
            {{ item.label }}
          </el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="备注" prop="remark">
        <el-input v-model="itemEditForm.remark" type="textarea" :rows="2" placeholder="选填，最长 255 字符" />
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="itemEditVisible = false">
        取消
      </el-button>
      <el-button type="primary" :loading="itemSubmitting" @click="submitItemEdit">
        确定
      </el-button>
    </template>
  </el-dialog>
</template>

<script setup lang="ts">
/**
 * 字典管理页：左栏字典类型 + 右栏字典项双表联动。
 *
 * <p>交互约定：左栏表格行点击选中类型（高亮当前行），右栏展示该类型下的全部字典项；
 * 新增/编辑/删除字典项均作用于当前选中类型，类型删除（级联软删其字典项）后清空右栏。
 * 校验规则与后端 SysDictTypeCreateCmd/SysDictTypeUpdateCmd/SysDictItemCreateCmd/SysDictItemUpdateCmd 逐条对齐。
 */
const enabledOptions = [
  { value: true, label: '正常' },
  { value: false, label: '禁用' },
] as const

/* ==================== 左栏：字典类型 ==================== */

const {
  query: typeQuery,
  pageNum: typePageNum,
  pageSize: typePageSize,
  total: typeTotal,
  loading: typeLoading,
  rows: typeRows,
  load: loadTypes,
  search: searchTypes,
  reset: resetTypes,
  pageChange: typePageChange,
  sortChange: typeSortChange,
} = useTablePage<SysDictTypeQuery, SysDictTypeVO>(data => dictTypeApi.page(data), () => ({
  dictType: '',
  name: '',
  enabled: undefined,
}))

/** 当前选中的字典类型（右栏数据源） */
const selectedType = ref<SysDictTypeVO>()

const typeCreateVisible = ref(false)
const typeEditVisible = ref(false)
const typeSubmitting = ref(false)
const typeCreateFormRef = ref<FormInstance>()
const typeEditFormRef = ref<FormInstance>()

const typeCreateForm = reactive<SysDictTypeCreateCmd>({
  dictType: '',
  name: '',
  enabled: true,
  remark: '',
})

const typeEditId = ref('')
const typeEditForm = reactive<SysDictTypeUpdateCmd>({
  name: '',
  enabled: true,
  remark: '',
})

/** 字典编码校验：2~50 位小写下划线（对齐后端 @Size + @Pattern） */
const typeCreateRules: FormRules = {
  dictType: [
    { required: true, message: '字典编码不能为空', trigger: 'blur' },
    {
      min: 2,
      max: 50,
      message: '字典编码长度须在 2~50 位之间',
      trigger: 'blur',
    },
    {
      pattern: /^[a-z][a-z0-9_]*$/,
      message: '字典编码须为小写下划线格式（如 user_status），以小写字母开头，仅含小写字母、数字、下划线',
      trigger: 'blur',
    },
  ],
  name: [
    { required: true, message: '字典名称不能为空', trigger: 'blur' },
    { max: 100, message: '字典名称最长 100 字符', trigger: 'blur' },
  ],
  enabled: [
    { required: true, message: '状态不能为空', trigger: 'change' },
  ],
  remark: [
    { max: 255, message: '备注最长 255 字符', trigger: 'blur' },
  ],
}

const typeEditRules: FormRules = {
  name: [
    { required: true, message: '字典名称不能为空', trigger: 'blur' },
    { max: 100, message: '字典名称最长 100 字符', trigger: 'blur' },
  ],
  enabled: [
    { required: true, message: '状态不能为空', trigger: 'change' },
  ],
  remark: [
    { max: 255, message: '备注最长 255 字符', trigger: 'blur' },
  ],
}

function openTypeCreate() {
  Object.assign(typeCreateForm, {
    dictType: '',
    name: '',
    enabled: true,
    remark: '',
  })
  typeCreateVisible.value = true
}

async function submitTypeCreate() {
  try {
    await typeCreateFormRef.value?.validate()
  } catch {
    return
  }
  typeSubmitting.value = true
  try {
    await dictTypeApi.create(typeCreateForm)
    ElMessage.success('创建成功')
    typeCreateVisible.value = false
    loadTypes()
  } finally {
    typeSubmitting.value = false
  }
}

function openTypeEdit(row: SysDictTypeVO) {
  typeEditId.value = row.id
  Object.assign(typeEditForm, { name: row.name, enabled: row.enabled, remark: row.remark ?? '' })
  typeEditVisible.value = true
}

async function submitTypeEdit() {
  try {
    await typeEditFormRef.value?.validate()
  } catch {
    return
  }
  typeSubmitting.value = true
  try {
    await dictTypeApi.update(typeEditId.value, typeEditForm)
    ElMessage.success('更新成功')
    typeEditVisible.value = false
    loadTypes()
  } finally {
    typeSubmitting.value = false
  }
}

async function handleTypeRemove(row: SysDictTypeVO) {
  await dictTypeApi.remove(row.id)
  ElMessage.success('删除成功（其字典项已级联删除）')
  if (selectedType.value?.id === row.id)
    selectedType.value = undefined
  loadTypes()
}

/* ==================== 右栏：字典项 ==================== */

const {
  query: itemQuery,
  pageNum: itemPageNum,
  pageSize: itemPageSize,
  total: itemTotal,
  loading: itemLoading,
  rows: itemRows,
  load: loadItems,
  reset: resetItems,
  pageChange: itemPageChange,
  sortChange: itemSortChange,
} = useTablePage<SysDictItemQuery, SysDictItemVO>(data => dictItemApi.page(data), () => ({
  dictType: selectedType.value?.dictType ?? '',
  itemLabel: '',
  enabled: undefined,
}))

/**
 * 选中字典类型：切换右栏数据源并重置字典项查询。
 */
function selectType(row: SysDictTypeVO) {
  selectedType.value = row
  resetItems()
}

const itemCreateVisible = ref(false)
const itemEditVisible = ref(false)
const itemSubmitting = ref(false)
const itemCreateFormRef = ref<FormInstance>()
const itemEditFormRef = ref<FormInstance>()

const itemCreateForm = reactive<SysDictItemCreateCmd>({
  dictType: '',
  itemCode: '',
  itemLabel: '',
  sort: 0,
  enabled: true,
  remark: '',
})

const itemEditId = ref('')
const itemEditForm = reactive<SysDictItemUpdateCmd>({
  itemLabel: '',
  sort: 0,
  enabled: true,
  remark: '',
})

/** 字典项校验规则（对齐后端 @Size/@Pattern/@Min） */
const itemCreateRules: FormRules = {
  itemCode: [
    { required: true, message: '项编码不能为空', trigger: 'blur' },
    { max: 50, message: '项编码最长 50 字符', trigger: 'blur' },
    { pattern: /^[\w-]+$/, message: '项编码仅允许字母、数字、下划线、连字符', trigger: 'blur' },
  ],
  itemLabel: [
    { required: true, message: '项显示名不能为空', trigger: 'blur' },
    { max: 100, message: '项显示名最长 100 字符', trigger: 'blur' },
  ],
  sort: [
    {
      type: 'number',
      min: 0,
      message: '排序号不能为负数',
      trigger: 'blur',
    },
  ],
  enabled: [
    { required: true, message: '项状态不能为空', trigger: 'change' },
  ],
  remark: [
    { max: 255, message: '备注最长 255 字符', trigger: 'blur' },
  ],
}

const itemEditRules: FormRules = {
  itemLabel: [
    { required: true, message: '项显示名不能为空', trigger: 'blur' },
    { max: 100, message: '项显示名最长 100 字符', trigger: 'blur' },
  ],
  sort: [
    {
      type: 'number',
      min: 0,
      message: '排序号不能为负数',
      trigger: 'blur',
    },
  ],
  enabled: [
    { required: true, message: '项状态不能为空', trigger: 'change' },
  ],
  remark: [
    { max: 255, message: '备注最长 255 字符', trigger: 'blur' },
  ],
}

function openItemCreate() {
  Object.assign(itemCreateForm, {
    dictType: selectedType.value?.dictType ?? '',
    itemCode: '',
    itemLabel: '',
    sort: 0,
    enabled: true,
    remark: '',
  })
  itemCreateVisible.value = true
}

async function submitItemCreate() {
  try {
    await itemCreateFormRef.value?.validate()
  } catch {
    return
  }
  itemSubmitting.value = true
  try {
    await dictItemApi.create(itemCreateForm)
    ElMessage.success('创建成功')
    itemCreateVisible.value = false
    loadItems()
  } finally {
    itemSubmitting.value = false
  }
}

function openItemEdit(row: SysDictItemVO) {
  itemEditId.value = row.id
  Object.assign(itemEditForm, {
    itemLabel: row.itemLabel,
    sort: row.sort,
    enabled: row.enabled,
    remark: row.remark ?? '',
  })
  itemEditVisible.value = true
}

async function submitItemEdit() {
  try {
    await itemEditFormRef.value?.validate()
  } catch {
    return
  }
  itemSubmitting.value = true
  try {
    await dictItemApi.update(itemEditId.value, itemEditForm)
    ElMessage.success('更新成功')
    itemEditVisible.value = false
    loadItems()
  } finally {
    itemSubmitting.value = false
  }
}

async function handleItemRemove(row: SysDictItemVO) {
  await dictItemApi.remove(row.id)
  ElMessage.success('删除成功')
  loadItems()
}

onMounted(loadTypes)
</script>

<style scoped>
.dict-page {
  --pane-height: calc(100vh - 140px);
}

.pane-card {
  --el-card-padding: 14px;
}

.pane-title {
  font-size: 15px;
  font-weight: 600;
}

.selected-tag {
  margin-left: 8px;
}

.dict-type-table {
  cursor: pointer;
}

.search-form :deep(.el-form-item) {
  margin-bottom: 12px;
}

.table-toolbar {
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
