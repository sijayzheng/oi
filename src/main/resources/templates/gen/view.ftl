<script setup lang="ts">
/**
 * ${functionName}管理页：列表（分页/排序/列设置/批量删除）+ 新增/编辑表单。
 *
 * <p>由代码生成器生成（模板：view.ftl）；校验规则与后端 Sys${className}CreateCmd/Sys${className}UpdateCmd
 * 的 Bean Validation 注解逐条对齐，提交前经 el-form 预校验拦截非法请求。
 */
import type { FormInstance, FormRules } from 'element-plus'
import type { Sys${className}CreateCmd, Sys${className}Query, Sys${className}UpdateCmd, Sys${className}VO } from '@/types/${businessName}'
import { Delete, Plus, Refresh, Search } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { onMounted, reactive, ref } from 'vue'
import { ${businessName}Api } from '@/api/${businessName}'
<#assign hasDict = false><#list columns as col><#if col.dictType?? && col.dictType != ''><#assign hasDict = true></#if></#list><#if hasDict>
import { dictItemApi } from '@/api/dict'
</#if>
import ColumnSetting from '@/components/ColumnSetting.vue'
import { useTablePage } from '@/composables/useTablePage'

/* ==================== 列表与搜索 ==================== */

const {
<#if queryColumns?has_content>
  query,
</#if>
  pageNum,
  pageSize,
  total,
  loading,
  rows,
  load,
<#if queryColumns?has_content>
  search,
  reset,
</#if>
  pageChange,
  sortChange,
} = useTablePage<Sys${className}Query, Sys${className}VO>(data => ${businessName}Api.page(data), () => ({
<#list queryColumns as col>
  ${col.javaField}: <#if col.isString>''<#else>undefined</#if>,
</#list>
}))

/** 列设置候选与当前展示列 */
const columnOptions = [
  { value: 'id', label: 'ID' },
<#list listColumns as col>
  { value: '${col.javaField}', label: '${col.label}' },
</#list>
]
const visibleColumns = ref<string[]>(columnOptions.map(option => option.value))

/** 多选行（批量删除用） */
const selection = ref<Sys${className}VO[]>([])

/* ==================== 下拉选项 ==================== */
<#list columns as col><#if (col.htmlType == 'select' || col.htmlType == 'radio') && col.optionsJson?? && col.optionsJson != ''>

/** ${col.label}静态选项（由字段配置 options_json 生成） */
const ${col.javaField}Options = ${col.optionsJson} as Array<{ value: string; label: string }>
<#elseif (col.htmlType == 'select' || col.htmlType == 'radio') && col.dictType?? && col.dictType != ''>

/** ${col.label}下拉选项（数据源：字典 ${col.dictType}） */
const ${col.javaField}Options = ref<Array<{ value: string; label: string }>>([])
</#if></#list>
<#assign hasDictOptions = false><#list columns as col><#if (col.htmlType == 'select' || col.htmlType == 'radio') && col.dictType?? && col.dictType != ''><#assign hasDictOptions = true></#if></#list><#if hasDictOptions>

/** 加载字典下拉选项 */
function loadDictOptions() {
<#list columns as col><#if (col.htmlType == 'select' || col.htmlType == 'radio') && col.dictType?? && col.dictType != ''>
  dictItemApi.options('${col.dictType}').then(options => {
    ${col.javaField}Options.value = options
  })
</#if></#list>
}
</#if>

/* ==================== 新增/编辑表单 ==================== */

const createVisible = ref(false)
const editVisible = ref(false)
const submitting = ref(false)
const createFormRef = ref<FormInstance>()
const editFormRef = ref<FormInstance>()

const createForm = reactive<Sys${className}CreateCmd>({
<#list insertColumns as col>
  ${col.javaField}: <#if col.isString>''<#elseif col.isNumber>0<#elseif col.htmlType == 'switch'>0<#else>null</#if>,
</#list>
})

/** 编辑行 id（Long 序列化为字符串） */
const editId = ref('')
const editForm = reactive<Sys${className}UpdateCmd>({
<#list editColumns as col>
  ${col.javaField}: <#if col.isString>''<#elseif col.isNumber>0<#elseif col.htmlType == 'switch'>0<#else>null</#if>,
</#list>
})

const createRules: FormRules = {
<#list insertColumns as col><#assign hasRule = col.required || (col.isString && col.hasMax) || col.hasRegex || (col.isNumber && col.hasMin)><#if hasRule>
  ${col.javaField}: [
<#if col.required>
    { required: true, message: '${col.label}不能为空', trigger: <#if col.htmlType == 'select' || col.htmlType == 'radio'>'change'<#else>'blur'</#if> },
</#if>
<#if col.isString && col.hasMin && col.hasMax>
    { min: ${col.minLength}, max: ${col.maxLength}, message: '${col.label}长度须在 ${col.minLength}~${col.maxLength} 位之间', trigger: 'blur' },
<#elseif col.isString && col.hasMax>
    { max: ${col.maxLength}, message: '${col.label}最长 ${col.maxLength} 字符', trigger: 'blur' },
</#if>
<#if col.hasRegex>
    { pattern: /${col.regex}/, message: '${col.label}格式不正确', trigger: 'blur' },
</#if>
<#if col.isNumber && col.hasMin>
    { type: 'number', min: ${col.minLength}, message: '${col.label}不能为负数', trigger: 'blur' },
</#if>
  ],
</#if></#list>
}

const editRules: FormRules = {
<#list editColumns as col><#assign hasRule = col.required || (col.isString && col.hasMax) || col.hasRegex || (col.isNumber && col.hasMin)><#if hasRule>
  ${col.javaField}: [
<#if col.required>
    { required: true, message: '${col.label}不能为空', trigger: <#if col.htmlType == 'select' || col.htmlType == 'radio'>'change'<#else>'blur'</#if> },
</#if>
<#if col.isString && col.hasMin && col.hasMax>
    { min: ${col.minLength}, max: ${col.maxLength}, message: '${col.label}长度须在 ${col.minLength}~${col.maxLength} 位之间', trigger: 'blur' },
<#elseif col.isString && col.hasMax>
    { max: ${col.maxLength}, message: '${col.label}最长 ${col.maxLength} 字符', trigger: 'blur' },
</#if>
<#if col.hasRegex>
    { pattern: /${col.regex}/, message: '${col.label}格式不正确', trigger: 'blur' },
</#if>
<#if col.isNumber && col.hasMin>
    { type: 'number', min: ${col.minLength}, message: '${col.label}不能为负数', trigger: 'blur' },
</#if>
  ],
</#if></#list>
}

/* ==================== 操作 ==================== */

function openCreate() {
  Object.assign(createForm, {
<#list insertColumns as col>
    ${col.javaField}: <#if col.isString>''<#elseif col.isNumber>0<#elseif col.htmlType == 'switch'>0<#else>null</#if>,
</#list>
  })
  createVisible.value = true
}

async function submitCreate() {
  try {
    await createFormRef.value?.validate()
  }
  catch {
    return
  }
  submitting.value = true
  try {
    await ${businessName}Api.create(createForm)
    ElMessage.success('创建成功')
    createVisible.value = false
    load()
  }
  finally {
    submitting.value = false
  }
}

function openEdit(row: Sys${className}VO) {
  editId.value = row.id
  Object.assign(editForm, {
<#list editColumns as col>
    ${col.javaField}: row.${col.javaField} ?? <#if col.isString>''<#elseif col.isNumber>0<#else>null</#if>,
</#list>
  })
  editVisible.value = true
}

async function submitEdit() {
  try {
    await editFormRef.value?.validate()
  }
  catch {
    return
  }
  submitting.value = true
  try {
    await ${businessName}Api.update(editId.value, editForm)
    ElMessage.success('更新成功')
    editVisible.value = false
    load()
  }
  finally {
    submitting.value = false
  }
}

async function handleRemove(row: Sys${className}VO) {
  await ${businessName}Api.remove(row.id)
  ElMessage.success('删除成功')
  load()
}

async function handleBatchRemove() {
  const ids = selection.value.map(row => row.id)
  await ElMessageBox.confirm(`确认删除选中的 ${'$'}{ids.length} 条${functionName}？`, '批量删除', { type: 'warning' })
  const results = await Promise.allSettled(ids.map(id => ${businessName}Api.remove(id)))
  const failed = results.filter(result => result.status === 'rejected').length
  if (failed === 0)
    ElMessage.success('批量删除成功')
  else
    ElMessage.warning(`批量删除完成：成功 ${'$'}{ids.length - failed} 条，失败 ${'$'}{failed} 条`)
  load()
}
<#if hasDictOptions>

onMounted(() => {
  load()
  loadDictOptions()
})
<#else>

onMounted(load)
</#if>
</script>

<template>
  <el-card shadow="never" class="page-card">
    <!-- 搜索筛选区（无查询字段时省略） -->
<#if queryColumns?has_content>
    <el-form inline class="search-form" @submit.prevent="search">
<#list queryColumns as col>
      <el-form-item label="${col.label}">
<#if col.htmlType == 'select'>
        <el-select v-model="query.${col.javaField}" placeholder="全部" clearable class="filter-select">
          <el-option v-for="item in ${col.javaField}Options" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
<#else>
        <el-input v-model="query.${col.javaField}" placeholder="<#if col.likeQuery>模糊匹配<#else>精确匹配</#if>" clearable @keyup.enter="search" />
</#if>
      </el-form-item>
</#list>
      <el-form-item>
        <el-button type="primary" :icon="Search" @click="search">
          搜索
        </el-button>
        <el-button :icon="Refresh" @click="reset">
          重置
        </el-button>
      </el-form-item>
    </el-form>
</#if>

    <!-- 工具栏：新增/批量删除 + 列设置 -->
    <div class="table-toolbar">
      <div>
        <el-button type="primary" :icon="Plus" @click="openCreate">
          新增${functionName}
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
<#list listColumns as col>
      <el-table-column v-if="visibleColumns.includes('${col.javaField}')" prop="${col.javaField}" label="${col.label}"<#if col.htmlType == 'datetime'> width="170"<#elseif col.htmlType == 'textarea'> min-width="180" show-overflow-tooltip<#else> min-width="130" show-overflow-tooltip</#if>><#if col.htmlType == 'select' || col.htmlType == 'radio'>
        <template #default="{ row }">
          {{ ${col.javaField}Options.find(o => o.value === row.${col.javaField})?.label ?? row.${col.javaField} }}
        </template>
      </el-table-column><#elseif col.htmlType == 'switch'>
        <template #default="{ row }">
          <el-tag :type="row.${col.javaField} === 1 ? 'success' : 'info'">
            {{ row.${col.javaField} === 1 ? '是' : '否' }}
          </el-tag>
        </template>
      </el-table-column><#else>
      </el-table-column></#if>
</#list>
      <el-table-column label="操作" fixed="right" width="140">
        <template #default="{ row }">
          <el-button link type="primary" @click="openEdit(row as Sys${className}VO)">
            编辑
          </el-button>
          <el-popconfirm title="确认删除该${functionName}？" confirm-button-text="删除" cancel-button-text="取消" @confirm="handleRemove(row as Sys${className}VO)">
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

  <!-- 新增${functionName} -->
  <el-dialog v-model="createVisible" title="新增${functionName}" width="780px" destroy-on-close>
    <el-form ref="createFormRef" :model="createForm" :rules="createRules" label-width="100px">
<#list insertColumns as col>
      <el-form-item label="${col.label}" prop="${col.javaField}">
<#if col.htmlType == 'select'>
        <el-select v-model="createForm.${col.javaField}" placeholder="请选择${col.label}" clearable>
          <el-option v-for="item in ${col.javaField}Options" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
<#elseif col.htmlType == 'radio'>
        <el-radio-group v-model="createForm.${col.javaField}">
          <el-radio v-for="item in ${col.javaField}Options" :key="item.value" :value="item.value">
            {{ item.label }}
          </el-radio>
        </el-radio-group>
<#elseif col.htmlType == 'switch'>
        <el-switch v-model="createForm.${col.javaField}"<#if col.isNumber> :active-value="1" :inactive-value="0"</#if> />
<#elseif col.htmlType == 'textarea'>
        <el-input v-model="createForm.${col.javaField}" type="textarea" :rows="4" placeholder="请输入${col.label}"<#if col.isString && col.hasMax> maxlength="${col.maxLength}"</#if> />
<#elseif col.htmlType == 'number'>
        <el-input-number v-model="createForm.${col.javaField}"<#if col.hasMin> :min="${col.minLength}"</#if> :step="1" step-strictly />
<#elseif col.htmlType == 'datetime'>
        <el-date-picker v-model="createForm.${col.javaField}" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" placeholder="请选择${col.label}" />
<#else>
        <el-input v-model="createForm.${col.javaField}" placeholder="请输入${col.label}"<#if col.isString && col.hasMax> maxlength="${col.maxLength}"</#if> />
</#if>
      </el-form-item>
</#list>
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

  <!-- 编辑${functionName} -->
  <el-dialog v-model="editVisible" title="编辑${functionName}" width="780px" destroy-on-close>
    <el-form ref="editFormRef" :model="editForm" :rules="editRules" label-width="100px">
<#list editColumns as col>
      <el-form-item label="${col.label}" prop="${col.javaField}">
<#if col.htmlType == 'select'>
        <el-select v-model="editForm.${col.javaField}" placeholder="请选择${col.label}" clearable>
          <el-option v-for="item in ${col.javaField}Options" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
<#elseif col.htmlType == 'radio'>
        <el-radio-group v-model="editForm.${col.javaField}">
          <el-radio v-for="item in ${col.javaField}Options" :key="item.value" :value="item.value">
            {{ item.label }}
          </el-radio>
        </el-radio-group>
<#elseif col.htmlType == 'switch'>
        <el-switch v-model="editForm.${col.javaField}"<#if col.isNumber> :active-value="1" :inactive-value="0"</#if> />
<#elseif col.htmlType == 'textarea'>
        <el-input v-model="editForm.${col.javaField}" type="textarea" :rows="4" placeholder="请输入${col.label}"<#if col.isString && col.hasMax> maxlength="${col.maxLength}"</#if> />
<#elseif col.htmlType == 'number'>
        <el-input-number v-model="editForm.${col.javaField}"<#if col.hasMin> :min="${col.minLength}"</#if> :step="1" step-strictly />
<#elseif col.htmlType == 'datetime'>
        <el-date-picker v-model="editForm.${col.javaField}" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" placeholder="请选择${col.label}" />
<#else>
        <el-input v-model="editForm.${col.javaField}" placeholder="请输入${col.label}"<#if col.isString && col.hasMax> maxlength="${col.maxLength}"</#if> />
</#if>
      </el-form-item>
</#list>
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
