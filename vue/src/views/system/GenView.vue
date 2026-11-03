<template>
  <el-card shadow="never" class="page-card">
    <el-alert type="info" :closable="false" show-icon class="gen-tip">
      代码生成器：导入业务表 → 微调字段配置（类型/校验/表单组件）→ 预览/生成前后端全套 CRUD 代码。
      业务表 DDL 需先加入 sql/schema-h2.sql（列注释用 COMMENT ON 书写，将自动映射为字段注释）。
    </el-alert>

    <div class="table-toolbar">
      <div>
        <el-button type="primary" :icon="Plus" @click="openImport">
          导入表
        </el-button>
        <el-button :icon="Refresh" @click="load">
          刷新
        </el-button>
      </div>
    </div>

    <el-table v-loading="loading" :data="rows" row-key="id" border stripe>
      <el-table-column prop="tableName" label="业务表" min-width="150" show-overflow-tooltip />
      <el-table-column prop="tableComment" label="表注释" min-width="130" show-overflow-tooltip />
      <el-table-column prop="className" label="类名主干" min-width="110" />
      <el-table-column prop="moduleName" label="模块" width="90" />
      <el-table-column prop="businessName" label="业务名" min-width="110" />
      <el-table-column prop="functionName" label="功能名" min-width="120" show-overflow-tooltip />
      <el-table-column label="场景" width="90">
        <template #default="{ row }">
          <el-tag v-if="row.parentTableId" type="warning">
            子表
          </el-tag>
          <el-tag v-else type="info">
            标准
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" fixed="right" width="240">
        <template #default="{ row }">
          <el-button link type="primary" :icon="Edit" @click="openEdit(row as GenTable)">
            编辑
          </el-button>
          <el-button link type="primary" :icon="View" @click="openPreview(row as GenTable)">
            预览
          </el-button>
          <el-button link type="success" :icon="MagicStick" @click="handleGenerate(row as GenTable)">
            生成
          </el-button>
          <el-button link type="danger" :icon="Delete" @click="handleRemove(row as GenTable)">
            删除
          </el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-pagination
      v-model:current-page="pageNum"
      v-model:page-size="pageSize"
      :total="total"
      :page-sizes="[10, 20, 50]"
      layout="total, sizes, prev, pager, next"
      background
      class="page-pagination"
      @change="pageChange"
    />
  </el-card>

  <!-- 导入表 -->
  <el-dialog v-model="importVisible" title="导入业务表" width="520px" destroy-on-close>
    <el-table v-loading="importLoading" :data="importTables" border max-height="420" highlight-current-row @row-click="(row: ImportableTable) => handleImport(row.tableName)">
      <el-table-column prop="tableName" label="业务表" min-width="160" />
      <el-table-column prop="tableComment" label="表注释" min-width="160" />
    </el-table>
    <template #footer>
      <el-button @click="importVisible = false">
        关闭
      </el-button>
    </template>
  </el-dialog>

  <!-- 编辑字段配置 -->
  <el-dialog v-model="editVisible" title="编辑生成配置" width="92%" destroy-on-close class="gen-edit-dialog">
    <el-form ref="editFormRef" :model="tableForm" :rules="tableRules" inline label-width="90px">
      <el-form-item label="业务表">
        <el-input :model-value="rows.find(r => r.id === editId)?.tableName" disabled />
      </el-form-item>
      <el-form-item label="类名主干" prop="className">
        <el-input v-model="tableForm.className" placeholder="如 Notice → SysNotice" />
      </el-form-item>
      <el-form-item label="模块名" prop="moduleName">
        <el-input v-model="tableForm.moduleName" />
      </el-form-item>
      <el-form-item label="业务名" prop="businessName">
        <el-input v-model="tableForm.businessName" placeholder="接口路径段" />
      </el-form-item>
      <el-form-item label="功能名" prop="functionName">
        <el-input v-model="tableForm.functionName" />
      </el-form-item>
      <el-form-item label="作者">
        <el-input v-model="tableForm.author" />
      </el-form-item>
    </el-form>

    <el-table :data="columns" border max-height="420" size="small">
      <el-table-column prop="columnName" label="列名" width="130" show-overflow-tooltip />
      <el-table-column label="字段注释" width="180">
        <template #default="{ row }">
          <el-input v-model="row.columnComment" placeholder="字段注释" />
        </template>
      </el-table-column>
      <el-table-column label="Java 字段" width="130">
        <template #default="{ row }">
          <el-input v-model="row.javaField" :disabled="row.isPk" />
        </template>
      </el-table-column>
      <el-table-column label="Java 类型" width="140">
        <template #default="{ row }">
          <el-select v-model="row.javaType" :disabled="row.isPk">
            <el-option v-for="item in javaTypeOptions" :key="item" :label="item" :value="item" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="TS 类型" width="110">
        <template #default="{ row }">
          <el-select v-model="row.tsType" :disabled="row.isPk">
            <el-option v-for="item in tsTypeOptions" :key="item" :label="item" :value="item" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="表单组件" width="120">
        <template #default="{ row }">
          <el-select v-model="row.htmlType">
            <el-option v-for="item in htmlTypeOptions" :key="item" :label="item" :value="item" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="必填" width="70">
        <template #default="{ row }">
          <el-switch v-model="row.isRequired" :disabled="row.isPk" />
        </template>
      </el-table-column>
      <el-table-column label="列表" width="70">
        <template #default="{ row }">
          <el-switch v-model="row.isList" :disabled="row.isPk" />
        </template>
      </el-table-column>
      <el-table-column label="查询" width="70">
        <template #default="{ row }">
          <el-switch v-model="row.isQuery" :disabled="row.isPk || row.javaType === 'LocalDateTime'" />
        </template>
      </el-table-column>
      <el-table-column label="查询方式" width="100">
        <template #default="{ row }">
          <el-select v-model="row.queryType" :disabled="!row.isQuery">
            <el-option v-for="item in queryTypeOptions" :key="item" :label="item" :value="item" />
          </el-select>
        </template>
      </el-table-column>
      <el-table-column label="最大长度" width="110">
        <template #default="{ row }">
          <el-input-number v-model="row.maxLength" :min="0" :step="1" controls-position="right" style="width: 88px" />
        </template>
      </el-table-column>
      <el-table-column label="静态选项 JSON" width="220">
        <template #default="{ row }">
          <el-input v-model="row.optionsJson" placeholder="[{&quot;value&quot;:&quot;0&quot;,&quot;label&quot;:&quot;通知&quot;}]" />
        </template>
      </el-table-column>
      <el-table-column label="字典编码" width="130">
        <template #default="{ row }">
          <el-input v-model="row.dictType" placeholder="如 user_status" />
        </template>
      </el-table-column>
      <el-table-column label="校验正则" min-width="180">
        <template #default="{ row }">
          <el-input v-model="row.regex" placeholder="如 ^[a-zA-Z0-9_]+$" />
        </template>
      </el-table-column>
    </el-table>

    <template #footer>
      <el-button @click="editVisible = false">
        取消
      </el-button>
      <el-button type="primary" :loading="editSaving" @click="submitEdit">
        保存配置
      </el-button>
    </template>
  </el-dialog>

  <!-- 产物预览 -->
  <el-dialog v-model="previewVisible" title="生成产物预览" width="80%" destroy-on-close>
    <el-tabs v-model="previewActive" type="border-card">
      <el-tab-pane v-for="(content, path) in previewFiles" :key="path" :name="path" :label="path.split('/').pop()">
        <div class="preview-path">
          {{ path }}（{{ content.length }} 字符）
        </div>
        <pre class="preview-code">{{ content }}</pre>
      </el-tab-pane>
    </el-tabs>
  </el-dialog>
</template>

<script setup lang="ts">
/**
 * 代码生成管理页：表配置列表 + 导入表结构 + 字段配置编辑 + 产物预览 + 一键生成落盘。
 *
 * <p>使用流程：导入表（自动推导字段配置）→ 编辑微调（类名/字段类型/校验/表单组件）
 * → 预览产物 → 生成落盘（自动注册前端路由与菜单）→ 重启后端生效。详见代码生成文档。
 */
/* ==================== 表配置列表 ==================== */

interface GenPageQuery {
}

const {
  pageNum,
  pageSize,
  total,
  loading,
  rows,
  load,
  pageChange,
} = useTablePage<GenPageQuery, GenTable>(() => genApi.page({ pageNum: 1, pageSize: 10 }), () => ({}))

/* ==================== 导入表 ==================== */

const importVisible = ref(false)
const importTables = ref<ImportableTable[]>([])
const importLoading = ref(false)

async function openImport() {
  importVisible.value = true
  importLoading.value = true
  try {
    importTables.value = await genApi.importableTables()
  } finally {
    importLoading.value = false
  }
}

async function handleImport(tableName: string) {
  await genApi.importTable(tableName)
  ElMessage.success(`已导入 ${tableName}，可在编辑中微调字段配置`)
  importVisible.value = false
  load()
}

async function handleRemove(row: GenTable) {
  await genApi.remove(row.id)
  ElMessage.success('删除成功')
  load()
}

/* ==================== 编辑：表信息 + 字段配置 ==================== */

const editVisible = ref(false)
const editId = ref('')
const editSaving = ref(false)
const editFormRef = ref<FormInstance>()

const tableForm = reactive({
  className: '',
  moduleName: 'sys',
  businessName: '',
  functionName: '',
  author: 'sijay',
})

/** 字段配置编辑数据（GenColumnSaveCmd 列表，全量提交） */
const columns = reactive<GenColumnSaveCmd[]>([])

const tableRules: FormRules = {
  className: [
    { required: true, message: '实体类名不能为空', trigger: 'blur' },
  ],
  moduleName: [
    { required: true, message: '业务模块名不能为空', trigger: 'blur' },
  ],
  businessName: [
    { required: true, message: '业务名不能为空', trigger: 'blur' },
  ],
  functionName: [
    { required: true, message: '功能名不能为空', trigger: 'blur' },
  ],
}

/** Java 类型候选 */
const javaTypeOptions = [
  'String',
  'Long',
  'Integer',
  'BigDecimal',
  'Boolean',
  'LocalDateTime',
]
/** TS 类型候选 */
const tsTypeOptions = ['string', 'number', 'boolean']
/** 表单组件候选 */
const htmlTypeOptions = [
  'input',
  'textarea',
  'select',
  'radio',
  'switch',
  'number',
  'datetime',
]
/** 查询方式候选 */
const queryTypeOptions = ['EQ', 'LIKE']

async function openEdit(row: GenTable) {
  editId.value = row.id
  const detail: GenTableDetail = await genApi.get(row.id)
  Object.assign(tableForm, {
    className: detail.table.className,
    moduleName: detail.table.moduleName,
    businessName: detail.table.businessName,
    functionName: detail.table.functionName,
    author: detail.table.author,
  })
  columns.splice(0, columns.length, ...detail.columns.map(column => ({
    id: column.id,
    columnName: column.columnName,
    columnComment: column.columnComment ?? '',
    columnSort: column.columnSort,
    javaField: column.javaField,
    javaType: column.javaType,
    tsType: column.tsType,
    isPk: column.isPk,
    isRequired: column.isRequired,
    isInsert: column.isInsert,
    isEdit: column.isEdit,
    isList: column.isList,
    isQuery: column.isQuery,
    queryType: column.queryType,
    htmlType: column.htmlType,
    optionsJson: column.optionsJson,
    dictType: column.dictType,
    minLength: column.minLength,
    maxLength: column.maxLength,
    regex: column.regex,
  })))
  editVisible.value = true
}

async function submitEdit() {
  try {
    await editFormRef.value?.validate()
  } catch {
    return
  }
  editSaving.value = true
  try {
    await genApi.save(editId.value, {
      tableName: rows.value.find(row => row.id === editId.value)?.tableName ?? '',
      className: tableForm.className,
      moduleName: tableForm.moduleName,
      businessName: tableForm.businessName,
      functionName: tableForm.functionName,
      author: tableForm.author,
      columns: columns.map(column => ({
        ...column,
        columnComment: column.columnComment || null,
        optionsJson: column.optionsJson || null,
        dictType: column.dictType || null,
        minLength: column.minLength,
        maxLength: column.maxLength,
        regex: column.regex || null,
      })),
    })
    ElMessage.success('配置已保存')
    editVisible.value = false
  } finally {
    editSaving.value = false
  }
}

/* ==================== 预览与生成 ==================== */

const previewVisible = ref(false)
const previewFiles = ref<Record<string, string>>({})
const previewActive = ref('')

async function openPreview(row: GenTable) {
  previewFiles.value = await genApi.preview(row.id)
  previewActive.value = Object.keys(previewFiles.value)[0] ?? ''
  previewVisible.value = true
}

async function handleGenerate(row: GenTable) {
  await ElMessageBox.confirm(
    `将生成前后端全套代码并写入项目目录（覆盖同名文件），并自动注册前端路由与菜单。确认生成「${row.tableName}」？`,
    '生成代码',
    { type: 'warning' },
  )
  const files = await genApi.generate(row.id)
  ElMessageBox.alert(
    `<p>已生成 ${files.length} 个文件：</p><ul style="max-height:240px;overflow:auto;">${files.map(file => `<li><code>${file}</code></li>`).join('')}</ul>
     <p>后端代码需<b>重启应用</b>后生效；前端页面已自动注册路由与菜单，刷新后可见。</p>`,
    '生成完成',
    { dangerouslyUseHTMLString: true, type: 'success' },
  )
}

onMounted(load)
</script>

<style scoped>
.gen-tip {
  margin-bottom: 12px;
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

.preview-path {
  padding: 4px 12px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.preview-code {
  max-height: 460px;
  margin: 0;
  overflow: auto;
  padding: 12px;
  background: var(--el-fill-color-light);
  font-size: 12px;
  line-height: 1.5;
}
</style>
