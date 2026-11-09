<template>
  <el-card shadow="never" class="search-card">
    <el-form inline class="search-form" @submit.prevent="search">
      <el-form-item label="名称">
        <el-input v-model="query.name" placeholder="模糊匹配" clearable @keyup.enter="search" />
      </el-form-item>
      <el-form-item label="类型">
        <el-select v-model="query.type" placeholder="全部" clearable class="type-select">
          <el-option
            v-for="item in typeOptions" :key="item.value" :label="item.label"
            :value="item.value"
          />
        </el-select>
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.enabled" placeholder="全部" clearable class="status-select">
          <el-option
            v-for="item in enabledOptions" :key="String(item.value)" :label="item.label"
            :value="item.value"
          />
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
          新增菜单
        </el-button>
        <el-button
          type="danger" plain :icon="Delete" :disabled="selection.length === 0"
          @click="handleBatchRemove"
        >
          批量删除
        </el-button>
        <el-button :icon="Share" @click="openTree">
          树形预览
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
      <el-table-column
        v-if="visibleColumns.includes('name')" prop="name" label="名称"
        min-width="130" sortable="custom" show-overflow-tooltip
      />
      <el-table-column v-if="visibleColumns.includes('type')" prop="type" label="类型" width="90">
        <template #default="{ row }">
          <el-tag :type="typeOptions.find(o => o.value === row.type)?.tag">
            {{ typeLabel(row.type) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column
        v-if="visibleColumns.includes('parentId')" prop="parentId" label="父ID"
        width="90"
      />
      <el-table-column
        v-if="visibleColumns.includes('path')" prop="path" label="路由路径"
        min-width="140" show-overflow-tooltip
      />
      <el-table-column
        v-if="visibleColumns.includes('component')" prop="component" label="组件路径"
        min-width="150" show-overflow-tooltip
      />
      <el-table-column
        v-if="visibleColumns.includes('perms')" prop="perms" label="权限标识"
        min-width="130" show-overflow-tooltip
      />
      <el-table-column
        v-if="visibleColumns.includes('icon')" prop="icon" label="图标" width="110"
        show-overflow-tooltip
      />
      <el-table-column
        v-if="visibleColumns.includes('sort')" prop="sort" label="排序号" width="90"
        sortable="custom"
      />
      <el-table-column
        v-if="visibleColumns.includes('visible')" prop="visible" label="显示状态"
        width="90"
      >
        <template #default="{ row }">
          <el-tag :type="row.visible ? 'success' : 'info'">
            {{ row.visible ? '显示' : '隐藏' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column
        v-if="visibleColumns.includes('enabled')" prop="enabled" label="状态"
        width="90"
      >
        <template #default="{ row }">
          <el-tag :type="row.enabled ? 'success' : 'danger'">
            {{ row.enabled ? '正常' : '禁用' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column
        v-if="visibleColumns.includes('createTime')"
        prop="createTime"
        label="创建时间"
        width="170"
        sortable="custom"
      />
      <el-table-column label="操作" fixed="right" width="140">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row as SysMenuVO)">
            编辑
          </el-button>
          <el-popconfirm
            title="确认删除该菜单？" confirm-button-text="删除"
            cancel-button-text="取消" @confirm="handleRemove(row as SysMenuVO)"
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

  <!-- 新增菜单 -->
  <el-dialog v-model="createVisible" title="新增菜单" width="780px" destroy-on-close>
    <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="90px">
      <el-form-item label="节点类型" prop="type">
        <el-radio-group v-model="createForm.type">
          <el-radio-button v-for="item in typeOptions" :key="item.value" :value="item.value">
            {{ item.label }}
          </el-radio-button>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="父节点" prop="parentId">
        <el-select
          v-model="createForm.parentId"
          :disabled="parentSelectDisabled"
          placeholder="不选则为根节点"
          clearable
          filterable
          class="parent-select"
        >
          <el-option
            v-for="item in parentCandidates" :key="item.id" :label="item.name"
            :value="item.id"
          />
        </el-select>
        <div class="form-hint">
          {{ parentHint }}
        </div>
      </el-form-item>
      <el-form-item label="名称" prop="name">
        <el-input v-model="createForm.name" placeholder="必填，最长 50 字符" />
      </el-form-item>
      <el-form-item label="路由路径" prop="path">
        <el-input v-model="createForm.path" placeholder="DIRECTORY/MENU 使用，如 /system/user" />
      </el-form-item>
      <el-form-item label="组件路径" prop="component">
        <el-input v-model="createForm.component" placeholder="MENU 使用，指向视图组件" />
      </el-form-item>
      <el-form-item label="权限标识" prop="perms">
        <el-input v-model="createForm.perms" placeholder="BUTTON 使用，格式：模块:动作（如 user:add）" />
      </el-form-item>
      <el-form-item label="图标" prop="icon">
        <el-input v-model="createForm.icon" placeholder="选填，导航栏渲染" />
      </el-form-item>
      <el-form-item label="排序号" prop="sort">
        <el-input-number v-model="createForm.sort" :min="0" :step="1" step-strictly />
      </el-form-item>
      <el-form-item label="显示状态" prop="visible">
        <el-radio-group v-model="createForm.visible">
          <el-radio v-for="item in visibleOptions" :key="String(item.value)" :value="item.value">
            {{ item.label }}
          </el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="状态" prop="enabled">
        <el-radio-group v-model="createForm.enabled">
          <el-radio v-for="item in enabledOptions" :key="String(item.value)" :value="item.value">
            {{ item.label }}
          </el-radio>
        </el-radio-group>
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

  <!-- 编辑菜单 -->
  <el-dialog v-model="editVisible" title="编辑菜单" width="780px" destroy-on-close>
    <el-alert type="info" :closable="false" show-icon class="edit-tip">
      挂载点（父节点）与节点类型不支持修改
    </el-alert>
    <el-form ref="editFormRef" :model="editForm" :rules="editRules" label-width="90px">
      <el-form-item label="节点类型">
        <el-tag :type="typeOptions.find(o => o.value === editRow?.type)?.tag">
          {{ editRow ? typeLabel(editRow.type) : '' }}
        </el-tag>
        <span v-if="editRow" class="form-hint edit-hint">父节点 ID：{{
          editRow.parentId
        }}（0 为根节点）</span>
      </el-form-item>
      <el-form-item label="名称" prop="name">
        <el-input v-model="editForm.name" placeholder="必填，最长 50 字符" />
      </el-form-item>
      <el-form-item label="路由路径" prop="path">
        <el-input v-model="editForm.path" placeholder="选填" />
      </el-form-item>
      <el-form-item label="组件路径" prop="component">
        <el-input v-model="editForm.component" placeholder="选填" />
      </el-form-item>
      <el-form-item label="权限标识" prop="perms">
        <el-input v-model="editForm.perms" placeholder="选填" />
      </el-form-item>
      <el-form-item label="图标" prop="icon">
        <el-input v-model="editForm.icon" placeholder="选填" />
      </el-form-item>
      <el-form-item label="排序号" prop="sort">
        <el-input-number v-model="editForm.sort" :min="0" :step="1" step-strictly />
      </el-form-item>
      <el-form-item label="显示状态" prop="visible">
        <el-radio-group v-model="editForm.visible">
          <el-radio v-for="item in visibleOptions" :key="String(item.value)" :value="item.value">
            {{ item.label }}
          </el-radio>
        </el-radio-group>
      </el-form-item>
      <el-form-item label="状态" prop="enabled">
        <el-radio-group v-model="editForm.enabled">
          <el-radio v-for="item in enabledOptions" :key="String(item.value)" :value="item.value">
            {{ item.label }}
          </el-radio>
        </el-radio-group>
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

  <!-- 树形预览 -->
  <el-dialog v-model="treeVisible" title="菜单树形结构" width="480px" destroy-on-close>
    <el-tree
      v-loading="treeLoading"
      :data="treeData"
      node-key="id"
      default-expand-all
      :expand-on-click-node="true"
      :props="{ label: 'label', disabled: 'disabled', children: 'children' }"
    />
  </el-dialog>
</template>

<script setup lang="ts">
/**
 * 菜单管理页：平铺列表（分页/排序/列设置）+ 树形结构预览 + 新增/编辑表单。
 *
 * <p>层级规则（目录 → 菜单 → 按钮）：新增时按所选节点类型过滤父节点候选
 * （菜单的父须为目录、按钮的父须为菜单、目录仅可为根节点），前端预校验拦截
 * 明显违规组合；后端 603001/603002/603003 业务码由 request.ts 统一提示兜底。
 * parentId/type 创建后不可修改（编辑表单仅展示不可编辑）。
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
} = useTablePage<SysMenuQuery, SysMenuVO>(data => menuApi.page(data), () => ({
  name: '',
  type: undefined,
  enabled: undefined,
}))

const typeOptions: Array<{
  value: MenuType
  label: string
  tag: 'info' | 'primary' | 'warning'
}> = [
  { value: 'DIRECTORY', label: '目录', tag: 'info' },
  { value: 'MENU', label: '菜单', tag: 'primary' },
  { value: 'BUTTON', label: '按钮', tag: 'warning' },
]

const visibleOptions: Array<{ value: boolean, label: string }> = [
  { value: true, label: '显示' },
  { value: false, label: '隐藏' },
]

const enabledOptions = [
  { value: true, label: '正常' },
  { value: false, label: '禁用' },
] as const

const columnOptions = [
  { value: 'id', label: 'ID' },
  { value: 'name', label: '名称' },
  { value: 'type', label: '类型' },
  { value: 'parentId', label: '父ID' },
  { value: 'path', label: '路由路径' },
  { value: 'component', label: '组件路径' },
  { value: 'perms', label: '权限标识' },
  { value: 'icon', label: '图标' },
  { value: 'sort', label: '排序号' },
  { value: 'visible', label: '显示状态' },
  { value: 'enabled', label: '状态' },
  { value: 'createTime', label: '创建时间' },
]
const visibleColumns = ref<string[]>(columnOptions.map(option => option.value))

const selection = ref<SysMenuVO[]>([])

/* ==================== 新增/编辑表单 ==================== */

const createVisible = ref(false)
const editVisible = ref(false)
const treeVisible = ref(false)
const submitting = ref(false)
const createFormRef = ref<FormInstance>()
const editFormRef = ref<FormInstance>()

const createForm = reactive<SysMenuCreateCmd>({
  parentId: undefined,
  name: '',
  type: 'DIRECTORY',
  path: '',
  component: '',
  perms: '',
  icon: '',
  sort: 0,
  visible: true,
  enabled: true,
})

const editId = ref('')
const editRow = ref<SysMenuVO>()
const editForm = reactive<SysMenuUpdateCmd>({
  name: '',
  path: '',
  component: '',
  perms: '',
  icon: '',
  sort: 0,
  visible: true,
  enabled: true,
})

/** 父节点候选（按所选类型过滤层级：菜单←目录、按钮←菜单；目录无候选） */
const parentCandidates = ref<SysMenuVO[]>([])
/** 目录类型下父节点选择禁用（仅可为根节点） */
const parentSelectDisabled = computed(() => createForm.type === 'DIRECTORY')

/** 父节点候选提示文案（对齐后端层级规则） */
const parentHint = computed(() => {
  switch (createForm.type) {
    case 'DIRECTORY':
      return '目录仅可作为根节点'
    case 'MENU':
      return '菜单的父节点必须为目录，不选则为根节点'
    case 'BUTTON':
      return '按钮的父节点必须为菜单'
    default:
      return ''
  }
})

/** 节点类型中文描述 */
function typeLabel(type: MenuType) {
  return typeOptions.find(option => option.value === type)?.label ?? type
}

const createRules: FormRules = {
  name: [
    { required: true, message: '菜单名称不能为空', trigger: 'blur' },
    { max: 50, message: '菜单名称最长 50 字符', trigger: 'blur' },
  ],
  type: [
    { required: true, message: '菜单类型不能为空', trigger: 'change' },
  ],
  path: [
    { max: 200, message: '路由路径最长 200 字符', trigger: 'blur' },
  ],
  component: [
    { max: 200, message: '组件路径最长 200 字符', trigger: 'blur' },
  ],
  perms: [
    { max: 100, message: '权限标识最长 100 字符', trigger: 'blur' },
  ],
  icon: [
    { max: 50, message: '图标标识最长 50 字符', trigger: 'blur' },
  ],
  sort: [
    { required: true, message: '排序号不能为空', trigger: 'blur' },
    {
      type: 'number',
      min: 0,
      message: '排序号不能为负数',
      trigger: 'blur',
    },
  ],
  visible: [
    { required: true, message: '显示状态不能为空', trigger: 'change' },
  ],
  enabled: [
    { required: true, message: '状态不能为空', trigger: 'change' },
  ],
}

const editRules: FormRules = {
  name: [
    { required: true, message: '菜单名称不能为空', trigger: 'blur' },
    { max: 50, message: '菜单名称最长 50 字符', trigger: 'blur' },
  ],
  path: [
    { max: 200, message: '路由路径最长 200 字符', trigger: 'blur' },
  ],
  component: [
    { max: 200, message: '组件路径最长 200 字符', trigger: 'blur' },
  ],
  perms: [
    { max: 100, message: '权限标识最长 100 字符', trigger: 'blur' },
  ],
  icon: [
    { max: 50, message: '图标标识最长 50 字符', trigger: 'blur' },
  ],
  sort: [
    { required: true, message: '排序号不能为空', trigger: 'blur' },
    {
      type: 'number',
      min: 0,
      message: '排序号不能为负数',
      trigger: 'blur',
    },
  ],
  visible: [
    { required: true, message: '显示状态不能为空', trigger: 'change' },
  ],
  enabled: [
    { required: true, message: '状态不能为空', trigger: 'change' },
  ],
}

/**
 * 加载父节点候选：按层级规则取"合法父类型"的平铺列表（上限 100）。
 */
async function loadParentCandidates() {
  // 目录无合法父类型；菜单←目录；按钮←菜单
  const parentType = createForm.type === 'MENU'
    ? 'DIRECTORY'
    : createForm.type === 'BUTTON'
      ? 'MENU'
      : undefined
  if (!parentType) {
    parentCandidates.value = []
    return
  }
  const result = await menuApi.page({ type: parentType, pageSize: 100 })
  parentCandidates.value = result.data ?? []
}

/**
 * 类型切换：清空已选父节点并重新加载候选。
 */
watch(() => createForm.type, () => {
  createForm.parentId = undefined
  loadParentCandidates()
})

/* ==================== 树形预览 ==================== */

const treeData = ref<TreeNode[]>([])
const treeLoading = ref(false)

async function openTree() {
  treeVisible.value = true
  treeLoading.value = true
  try {
    treeData.value = await menuApi.tree()
  } finally {
    treeLoading.value = false
  }
}

/* ==================== 操作 ==================== */

function openCreate() {
  Object.assign(createForm, {
    parentId: undefined,
    name: '',
    type: 'DIRECTORY',
    path: '',
    component: '',
    perms: '',
    icon: '',
    sort: 0,
    visible: true,
    enabled: true,
  })
  loadParentCandidates()
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
    await menuApi.create(createForm)
    ElMessage.success('创建成功')
    createVisible.value = false
    load()
  } finally {
    submitting.value = false
  }
}

function openEdit(row: SysMenuVO) {
  editId.value = row.id
  editRow.value = row
  Object.assign(editForm, {
    name: row.name,
    path: row.path ?? '',
    component: row.component ?? '',
    perms: row.perms ?? '',
    icon: row.icon ?? '',
    sort: row.sort,
    visible: row.visible,
    enabled: row.enabled,
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
    await menuApi.update(editId.value, editForm)
    ElMessage.success('更新成功')
    editVisible.value = false
    load()
  } finally {
    submitting.value = false
  }
}

async function handleRemove(row: SysMenuVO) {
  await menuApi.remove(row.id)
  ElMessage.success('删除成功')
  load()
}

async function handleBatchRemove() {
  const ids = selection.value.map(row => row.id)
  await ElMessageBox.confirm(`确认删除选中的 ${ids.length} 个菜单？`, '批量删除', { type: 'warning' })
  const results = await Promise.allSettled(ids.map(id => menuApi.remove(id)))
  const failed = results.filter(result => result.status === 'rejected').length
  if (failed === 0)
    ElMessage.success('批量删除成功')
  else
    ElMessage.warning(`批量删除完成：成功 ${ids.length - failed} 条，失败 ${failed} 条（存在子菜单的节点无法删除）`)
  load()
}

onMounted(load)
</script>

<style scoped>
.search-form :deep(.el-form-item) {
  margin-bottom: 12px;
}

.type-select,
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

.parent-select {
  width: 100%;
}

.form-hint {
  width: 100%;
  margin-top: 4px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.4;
}

.edit-hint {
  margin-left: 12px;
}

.edit-tip {
  margin-bottom: 16px;
}
</style>
