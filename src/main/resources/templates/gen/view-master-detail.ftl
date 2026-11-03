<script setup lang="ts">
/**
 * ${functionName}管理页（主子表场景）：左栏主表 + 右栏${child.functionName}双栏联动。
 *
 * <p>交互约定：左栏行点击选中主表记录（高亮当前行），右栏展示该主表关联的${child.functionName}；
 * 主表删除时同事务级联软删${child.functionName}（后端约定），删除选中行后右栏回空态。
 * 由代码生成器生成（模板：view-master-detail.ftl）。
 */
import type { FormInstance, FormRules } from 'element-plus'
import type { Sys${className}CreateCmd, Sys${className}Query, Sys${className}UpdateCmd, Sys${className}VO } from '@/types/${businessName}'
import type { Sys${child.className}CreateCmd, Sys${child.className}Query, Sys${child.className}UpdateCmd, Sys${child.className}VO } from '@/types/${child.businessName}'
import { Delete, Plus, Refresh, Search } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { onMounted, reactive, ref } from 'vue'
import { ${businessName}Api } from '@/api/${businessName}'
import { ${child.businessName?replace('_', '')}Api } from '@/api/${child.businessName}'
<#assign hasDict = false><#list columns as col><#if col.dictType?? && col.dictType != ''><#assign hasDict = true></#if></#list><#list child.columns as col><#if col.dictType?? && col.dictType != ''><#assign hasDict = true></#if></#list><#if hasDict>
import { dictItemApi } from '@/api/dict'
</#if>
import { useTablePage } from '@/composables/useTablePage'

/* ==================== 左栏：主表 ${functionName} ==================== */

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
} = useTablePage<Sys${className}Query, Sys${className}VO>(data => ${businessName}Api.page(data), () => ({
<#list queryColumns as col>
  ${col.javaField}: <#if col.isString>''<#else>undefined</#if>,
</#list>
}))

/** 当前选中的主表记录（右栏数据源） */
const selectedRow = ref<Sys${className}VO>()

function selectRow(row: Sys${className}VO) {
  selectedRow.value = row
  resetChild()
}

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
  ElMessage.success('删除成功（已级联删除关联${child.functionName}）')
  if (selectedRow.value?.id === row.id)
    selectedRow.value = undefined
  load()
}

/* ==================== 右栏：${child.functionName} ==================== */

const {
  query: childQuery,
  pageNum: childPageNum,
  pageSize: childPageSize,
  total: childTotal,
  loading: childLoading,
  rows: childRows,
  load: loadChild,
  reset: resetChild,
  pageChange: childPageChange,
} = useTablePage<Sys${child.className}Query, Sys${child.className}VO>(data => ${child.businessName?replace('_', '')}Api.page(data), () => ({
  ${child.fkJavaField}: selectedRow.value?.id ?? '',
}))

const childCreateVisible = ref(false)
const childEditVisible = ref(false)
const childSubmitting = ref(false)
const childCreateFormRef = ref<FormInstance>()
const childEditFormRef = ref<FormInstance>()

const childCreateForm = reactive<Sys${child.className}CreateCmd>({
<#list child.insertColumns as col>
  ${col.javaField}: <#if col.isString>''<#elseif col.isNumber>0<#elseif col.htmlType == 'switch'>0<#else>null</#if>,
</#list>
})

const childEditId = ref('')
const childEditForm = reactive<Sys${child.className}UpdateCmd>({
<#list child.editColumns as col>
  ${col.javaField}: <#if col.isString>''<#elseif col.isNumber>0<#elseif col.htmlType == 'switch'>0<#else>null</#if>,
</#list>
})

const childCreateRules: FormRules = {
<#list child.insertColumns as col><#if col.javaField == child.fkJavaField><#continue></#if><#assign hasRule = col.required || (col.isString && col.hasMax) || col.hasRegex || (col.isNumber && col.hasMin)><#if hasRule>
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

const childEditRules: FormRules = {
<#list child.editColumns as col><#if col.javaField == child.fkJavaField><#continue></#if><#assign hasRule = col.required || (col.isString && col.hasMax) || col.hasRegex || (col.isNumber && col.hasMin)><#if hasRule>
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

function openChildCreate() {
  Object.assign(childCreateForm, {
<#list child.insertColumns as col>
    ${col.javaField}: <#if col.javaField == child.fkJavaField>selectedRow.value?.id ?? ''<#elseif col.isString>''<#elseif col.isNumber>0<#elseif col.htmlType == 'switch'>0<#else>null</#if>,
</#list>
  })
  childCreateVisible.value = true
}

async function submitChildCreate() {
  try {
    await childCreateFormRef.value?.validate()
  }
  catch {
    return
  }
  childSubmitting.value = true
  try {
    await ${child.businessName?replace('_', '')}Api.create(childCreateForm)
    ElMessage.success('创建成功')
    childCreateVisible.value = false
    loadChild()
  }
  finally {
    childSubmitting.value = false
  }
}

function openChildEdit(row: Sys${child.className}VO) {
  childEditId.value = row.id
  Object.assign(childEditForm, {
<#list child.editColumns as col>
    ${col.javaField}: row.${col.javaField} ?? <#if col.isString>''<#elseif col.isNumber>0<#else>null</#if>,
</#list>
  })
  childEditVisible.value = true
}

async function submitChildEdit() {
  try {
    await childEditFormRef.value?.validate()
  }
  catch {
    return
  }
  childSubmitting.value = true
  try {
    await ${child.businessName?replace('_', '')}Api.update(childEditId.value, childEditForm)
    ElMessage.success('更新成功')
    childEditVisible.value = false
    loadChild()
  }
  finally {
    childSubmitting.value = false
  }
}

async function handleChildRemove(row: Sys${child.className}VO) {
  await ${child.businessName?replace('_', '')}Api.remove(row.id)
  ElMessage.success('删除成功')
  loadChild()
}
<#assign hasDictOptions = false><#list columns as col><#if (col.htmlType == 'select' || col.htmlType == 'radio') && col.dictType?? && col.dictType != ''><#assign hasDictOptions = true></#if></#list><#list child.columns as col><#if (col.htmlType == 'select' || col.htmlType == 'radio') && col.dictType?? && col.dictType != ''><#assign hasDictOptions = true></#if></#list><#if hasDictOptions>

/** 加载字典下拉选项（主表 + 子表） */
function loadDictOptions() {
<#list columns as col><#if (col.htmlType == 'select' || col.htmlType == 'radio') && col.dictType?? && col.dictType != ''>
  dictItemApi.options('${col.dictType}').then(options => {
    ${col.javaField}Options.value = options
  })
</#if></#list>
<#list child.columns as col><#if (col.htmlType == 'select' || col.htmlType == 'radio') && col.dictType?? && col.dictType != ''>
  dictItemApi.options('${col.dictType}').then(options => {
    ${col.javaField}Options.value = options
  })
</#if></#list>
}
</#if>

onMounted(() => {
  load()
<#if hasDictOptions>
  loadDictOptions()
</#if>
})
</script>

<template>
  <el-row :gutter="16" class="master-detail-page">
    <!-- 左栏：主表 ${functionName} -->
    <el-col :xs="24" :md="10" :xl="9">
      <el-card shadow="never" class="pane-card">
        <template #header>
          <span class="pane-title">${functionName}</span>
        </template>

        <el-form inline class="search-form" @submit.prevent="search">
<#list queryColumns as col>
          <el-form-item label="${col.label}">
<#if col.htmlType == 'select'>
            <el-select v-model="query.${col.javaField}" placeholder="全部" clearable>
              <el-option v-for="item in ${col.javaField}Options" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
<#else>
            <el-input v-model="query.${col.javaField}" placeholder="请输入${col.label}" clearable @keyup.enter="search" />
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

        <div class="table-toolbar">
          <el-button type="primary" :icon="Plus" @click="openCreate">
            新增${functionName}
          </el-button>
        </div>

        <el-table
          v-loading="loading"
          :data="rows"
          row-key="id"
          border
          highlight-current-row
          class="master-table"
          @row-click="selectRow"
        >
<#list listColumns as col>
          <el-table-column prop="${col.javaField}" label="${col.label}"<#if col.htmlType == 'datetime'> width="170"<#else> min-width="120" show-overflow-tooltip</#if>><#if col.htmlType == 'select' || col.htmlType == 'radio'>
            <template #default="{ row }">
              {{ ${col.javaField}Options.find(o => o.value === row.${col.javaField})?.label ?? row.${col.javaField} }}
            </template>
          </el-table-column><#else>
          </el-table-column></#if>
</#list>
          <el-table-column label="操作" width="120">
            <template #default="{ row }">
              <el-button link type="primary" @click.stop="openEdit(row as Sys${className}VO)">
                编辑
              </el-button>
              <el-popconfirm title="删除将级联删除关联${child.functionName}，确认？" confirm-button-text="删除" cancel-button-text="取消" width="240" @confirm="handleRemove(row as Sys${className}VO)">
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
          v-model:current-page="pageNum"
          v-model:page-size="pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, prev, pager, next"
          small
          background
          class="page-pagination"
          @change="pageChange"
        />
      </el-card>
    </el-col>

    <!-- 右栏：${child.functionName} -->
    <el-col :xs="24" :md="14" :xl="15">
      <el-card shadow="never" class="pane-card">
        <template #header>
          <span class="pane-title">
            ${child.functionName}
            <el-tag v-if="selectedRow" type="primary" class="selected-tag">
              {{ selectedRow.<#list listColumns as col><#if col_index == 0>${col.javaField}</#if></#list> }}
            </el-tag>
          </span>
        </template>

        <el-empty v-if="!selectedRow" description="请选择左侧${functionName}查看${child.functionName}" />

        <template v-else>
          <div class="table-toolbar">
            <el-button type="primary" :icon="Plus" @click="openChildCreate">
              新增${child.functionName}
            </el-button>
          </div>

          <el-table v-loading="childLoading" :data="childRows" row-key="id" border stripe>
<#list child.listColumns as col><#if col.javaField == child.fkJavaField><#continue></#if>
            <el-table-column prop="${col.javaField}" label="${col.label}"<#if col.htmlType == 'datetime'> width="170"<#else> min-width="120" show-overflow-tooltip</#if>><#if col.htmlType == 'select' || col.htmlType == 'radio'>
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
            <el-table-column label="操作" width="120">
              <template #default="{ row }">
                <el-button link type="primary" @click="openChildEdit(row as Sys${child.className}VO)">
                  编辑
                </el-button>
                <el-popconfirm title="确认删除该${child.functionName}？" confirm-button-text="删除" cancel-button-text="取消" @confirm="handleChildRemove(row as Sys${child.className}VO)">
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
            v-model:current-page="childPageNum"
            v-model:page-size="childPageSize"
            :total="childTotal"
            :page-sizes="[10, 20, 50]"
            layout="total, prev, pager, next"
            small
            background
            class="page-pagination"
            @change="childPageChange"
          />
        </template>
      </el-card>
    </el-col>
  </el-row>

  <!-- 新增${functionName} -->
  <el-dialog v-model="createVisible" title="新增${functionName}" width="560px" destroy-on-close>
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
        <el-input v-model="createForm.${col.javaField}" type="textarea" :rows="4" placeholder="请输入${col.label}" />
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
  <el-dialog v-model="editVisible" title="编辑${functionName}" width="560px" destroy-on-close>
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
        <el-input v-model="editForm.${col.javaField}" type="textarea" :rows="4" placeholder="请输入${col.label}" />
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

  <!-- 新增${child.functionName} -->
  <el-dialog v-model="childCreateVisible" title="新增${child.functionName}" width="520px" destroy-on-close>
    <el-form ref="childCreateFormRef" :model="childCreateForm" :rules="childCreateRules" label-width="100px">
<#list child.insertColumns as col><#if col.javaField == child.fkJavaField>
      <el-form-item label="${col.label}">
        <el-input :model-value="childCreateForm.${col.javaField}" disabled />
      </el-form-item>
<#else>
      <el-form-item label="${col.label}" prop="${col.javaField}">
<#if col.htmlType == 'select'>
        <el-select v-model="childCreateForm.${col.javaField}" placeholder="请选择${col.label}" clearable>
          <el-option v-for="item in ${col.javaField}Options" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
<#elseif col.htmlType == 'radio'>
        <el-radio-group v-model="childCreateForm.${col.javaField}">
          <el-radio v-for="item in ${col.javaField}Options" :key="item.value" :value="item.value">
            {{ item.label }}
          </el-radio>
        </el-radio-group>
<#elseif col.htmlType == 'switch'>
        <el-switch v-model="childCreateForm.${col.javaField}"<#if col.isNumber> :active-value="1" :inactive-value="0"</#if> />
<#elseif col.htmlType == 'textarea'>
        <el-input v-model="childCreateForm.${col.javaField}" type="textarea" :rows="4" placeholder="请输入${col.label}" />
<#elseif col.htmlType == 'number'>
        <el-input-number v-model="childCreateForm.${col.javaField}"<#if col.hasMin> :min="${col.minLength}"</#if> :step="1" step-strictly />
<#elseif col.htmlType == 'datetime'>
        <el-date-picker v-model="childCreateForm.${col.javaField}" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" placeholder="请选择${col.label}" />
<#else>
        <el-input v-model="childCreateForm.${col.javaField}" placeholder="请输入${col.label}"<#if col.isString && col.hasMax> maxlength="${col.maxLength}"</#if> />
</#if>
      </el-form-item>
</#if>
</#list>
    </el-form>
    <template #footer>
      <el-button @click="childCreateVisible = false">
        取消
      </el-button>
      <el-button type="primary" :loading="childSubmitting" @click="submitChildCreate">
        确定
      </el-button>
    </template>
  </el-dialog>

  <!-- 编辑${child.functionName} -->
  <el-dialog v-model="childEditVisible" title="编辑${child.functionName}" width="520px" destroy-on-close>
    <el-form ref="childEditFormRef" :model="childEditForm" :rules="childEditRules" label-width="100px">
<#list child.editColumns as col><#if col.javaField == child.fkJavaField><#continue></#if>
      <el-form-item label="${col.label}" prop="${col.javaField}">
<#if col.htmlType == 'select'>
        <el-select v-model="childEditForm.${col.javaField}" placeholder="请选择${col.label}" clearable>
          <el-option v-for="item in ${col.javaField}Options" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
<#elseif col.htmlType == 'radio'>
        <el-radio-group v-model="childEditForm.${col.javaField}">
          <el-radio v-for="item in ${col.javaField}Options" :key="item.value" :value="item.value">
            {{ item.label }}
          </el-radio>
        </el-radio-group>
<#elseif col.htmlType == 'switch'>
        <el-switch v-model="childEditForm.${col.javaField}"<#if col.isNumber> :active-value="1" :inactive-value="0"</#if> />
<#elseif col.htmlType == 'textarea'>
        <el-input v-model="childEditForm.${col.javaField}" type="textarea" :rows="4" placeholder="请输入${col.label}" />
<#elseif col.htmlType == 'number'>
        <el-input-number v-model="childEditForm.${col.javaField}"<#if col.hasMin> :min="${col.minLength}"</#if> :step="1" step-strictly />
<#elseif col.htmlType == 'datetime'>
        <el-date-picker v-model="childEditForm.${col.javaField}" type="datetime" value-format="YYYY-MM-DD HH:mm:ss" placeholder="请选择${col.label}" />
<#else>
        <el-input v-model="childEditForm.${col.javaField}" placeholder="请输入${col.label}"<#if col.isString && col.hasMax> maxlength="${col.maxLength}"</#if> />
</#if>
      </el-form-item>
</#list>
    </el-form>
    <template #footer>
      <el-button @click="childEditVisible = false">
        取消
      </el-button>
      <el-button type="primary" :loading="childSubmitting" @click="submitChildEdit">
        确定
      </el-button>
    </template>
  </el-dialog>
</template>

<style scoped>
.master-detail-page {
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

.master-table {
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
</style>
