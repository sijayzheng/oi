<template>
  <el-card shadow="never" class="search-card">
    <!-- 搜索筛选区 -->
    <el-form inline class="search-form" @submit.prevent="search">
      <el-form-item label="登录名">
        <el-input v-model="query.username" placeholder="模糊匹配" clearable @keyup.enter="search" />
      </el-form-item>
      <el-form-item label="昵称">
        <el-input v-model="query.nickname" placeholder="模糊匹配" clearable @keyup.enter="search" />
      </el-form-item>
      <el-form-item label="手机号">
        <el-input v-model="query.mobile" placeholder="模糊匹配" clearable @keyup.enter="search" />
      </el-form-item>
      <el-form-item label="邮箱">
        <el-input v-model="query.email" placeholder="模糊匹配" clearable @keyup.enter="search" />
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
    <!-- 工具栏：新增/批量删除 + 列设置 -->
    <div class="table-toolbar">
      <div>
        <el-button type="primary" :icon="Plus" @click="openCreate">
          新增用户
        </el-button>
        <el-button type="danger" plain :icon="Delete" :disabled="selection.length === 0" @click="handleBatchRemove">
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
      <el-table-column v-if="visibleColumns.includes('username')" prop="username" label="登录名" min-width="110" sortable="custom" show-overflow-tooltip />
      <el-table-column v-if="visibleColumns.includes('nickname')" prop="nickname" label="昵称" min-width="110" sortable="custom" show-overflow-tooltip />
      <el-table-column v-if="visibleColumns.includes('mobile')" prop="mobile" label="手机号" width="130" show-overflow-tooltip />
      <el-table-column v-if="visibleColumns.includes('email')" prop="email" label="邮箱" min-width="160" show-overflow-tooltip />
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
          <el-button link type="primary" @click="openEdit(row as SysUserVO)">
            编辑
          </el-button>
          <el-popconfirm title="确认删除该用户？" confirm-button-text="删除" cancel-button-text="取消" @confirm="handleRemove(row as SysUserVO)">
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

  <!-- 新增用户 -->
  <el-dialog v-model="createVisible" title="新增用户" width="780px" destroy-on-close>
    <el-form ref="createFormRef" :model="createForm" :rules="usernameRules" label-width="90px">
      <el-form-item label="登录名" prop="username">
        <el-input v-model="createForm.username" placeholder="2~50 位字母/数字/下划线" />
      </el-form-item>
      <el-form-item label="密码" prop="password">
        <el-input v-model="createForm.password" type="password" show-password placeholder="6~100 位" />
      </el-form-item>
      <el-form-item label="昵称" prop="nickname">
        <el-input v-model="createForm.nickname" placeholder="选填，最长 50 字符" />
      </el-form-item>
      <el-form-item label="手机号" prop="mobile">
        <el-input v-model="createForm.mobile" placeholder="选填" />
      </el-form-item>
      <el-form-item label="邮箱" prop="email">
        <el-input v-model="createForm.email" placeholder="选填" />
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

  <!-- 编辑用户 -->
  <el-dialog v-model="editVisible" title="编辑用户" width="780px" destroy-on-close>
    <el-alert type="info" :closable="false" show-icon class="edit-tip">
      登录名与密码不支持修改（改密为独立安全操作）
    </el-alert>
    <el-form ref="editFormRef" :model="editForm" :rules="editRules" label-width="90px">
      <el-form-item label="昵称" prop="nickname">
        <el-input v-model="editForm.nickname" placeholder="必填，最长 50 字符" />
      </el-form-item>
      <el-form-item label="手机号" prop="mobile">
        <el-input v-model="editForm.mobile" placeholder="选填" />
      </el-form-item>
      <el-form-item label="邮箱" prop="email">
        <el-input v-model="editForm.email" placeholder="选填" />
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
 * 用户管理页：列表（分页/排序/列设置/批量删除）+ 新增/编辑表单。
 *
 * <p>校验规则与后端 SysUserCreateCmd/SysUserUpdateCmd 的 Bean Validation 注解逐条对齐，
 * 提交前经 el-form 预校验拦截非法请求；业务失败（如用户名重复 601001）由
 * request.ts 统一 toast 提示。
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
} = useTablePage<SysUserQuery, SysUserVO>(data => userApi.page(data), () => ({
  username: '',
  nickname: '',
  mobile: '',
  email: '',
  enabled: undefined,
}))

const enabledOptions = [
  { value: true, label: '正常' },
  { value: false, label: '禁用' },
] as const

/** 列设置候选与当前展示列 */
const columnOptions = [
  { value: 'id', label: 'ID' },
  { value: 'username', label: '登录名' },
  { value: 'nickname', label: '昵称' },
  { value: 'mobile', label: '手机号' },
  { value: 'email', label: '邮箱' },
  { value: 'enabled', label: '状态' },
  { value: 'remark', label: '备注' },
  { value: 'createTime', label: '创建时间' },
]
const visibleColumns = ref<string[]>(columnOptions.map(option => option.value))

/** 多选行（批量删除用） */
const selection = ref<SysUserVO[]>([])

/* ==================== 新增/编辑表单 ==================== */

const createVisible = ref(false)
const editVisible = ref(false)
const submitting = ref(false)
const createFormRef = ref<FormInstance>()
const editFormRef = ref<FormInstance>()

const createForm = reactive<SysUserCreateCmd>({
  username: '',
  password: '',
  nickname: '',
  mobile: '',
  email: '',
  enabled: true,
  remark: '',
})

/** 编辑行 id（Long 序列化为字符串） */
const editId = ref('')
const editForm = reactive<SysUserUpdateCmd>({
  nickname: '',
  mobile: '',
  email: '',
  enabled: true,
  remark: '',
})

/** 用户名校验规则：2~50 位字母/数字/下划线（对齐后端 @Size + @Pattern） */
const usernameRules: FormRules = {
  username: [
    { required: true, message: '用户名不能为空', trigger: 'blur' },
    {
      min: 2,
      max: 50,
      message: '用户名长度须在 2~50 位之间',
      trigger: 'blur',
    },
    { pattern: /^\w+$/, message: '用户名仅允许字母、数字、下划线', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '密码不能为空', trigger: 'blur' },
    {
      min: 6,
      max: 100,
      message: '密码长度须在 6~100 位之间',
      trigger: 'blur',
    },
  ],
  nickname: [
    { max: 50, message: '昵称最长 50 字符', trigger: 'blur' },
  ],
  mobile: [
    { max: 20, message: '手机号最长 20 字符', trigger: 'blur' },
  ],
  email: [
    { max: 100, message: '邮箱最长 100 字符', trigger: 'blur' },
    {
      type: 'email',
      message: '邮箱格式不正确',
      trigger: ['blur', 'change'],
    },
  ],
  enabled: [
    { required: true, message: '账号状态不能为空', trigger: 'change' },
  ],
  remark: [
    { max: 255, message: '备注最长 255 字符', trigger: 'blur' },
  ],
}

/** 编辑规则：username/password 不可修改，nickname/enabled 必填（整档案更新语义） */
const editRules: FormRules = {
  nickname: [
    { required: true, message: '昵称不能为空', trigger: 'blur' },
    { max: 50, message: '昵称最长 50 字符', trigger: 'blur' },
  ],
  mobile: [
    { max: 20, message: '手机号最长 20 字符', trigger: 'blur' },
  ],
  email: [
    { max: 100, message: '邮箱最长 100 字符', trigger: 'blur' },
    {
      type: 'email',
      message: '邮箱格式不正确',
      trigger: ['blur', 'change'],
    },
  ],
  enabled: [
    { required: true, message: '账号状态不能为空', trigger: 'change' },
  ],
  remark: [
    { max: 255, message: '备注最长 255 字符', trigger: 'blur' },
  ],
}

/* ==================== 操作 ==================== */

function openCreate() {
  Object.assign(createForm, {
    username: '',
    password: '',
    nickname: '',
    mobile: '',
    email: '',
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
    await userApi.create(createForm)
    ElMessage.success('创建成功')
    createVisible.value = false
    load()
  } finally {
    submitting.value = false
  }
}

function openEdit(row: SysUserVO) {
  editId.value = row.id
  Object.assign(editForm, {
    nickname: row.nickname ?? '',
    mobile: row.mobile ?? '',
    email: row.email ?? '',
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
    await userApi.update(editId.value, editForm)
    ElMessage.success('更新成功')
    editVisible.value = false
    load()
  } finally {
    submitting.value = false
  }
}

async function handleRemove(row: SysUserVO) {
  await userApi.remove(row.id)
  ElMessage.success('删除成功')
  load()
}

async function handleBatchRemove() {
  const ids = selection.value.map(row => row.id)
  await ElMessageBox.confirm(`确认删除选中的 ${ids.length} 个用户？`, '批量删除', { type: 'warning' })
  const results = await Promise.allSettled(ids.map(id => userApi.remove(id)))
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
