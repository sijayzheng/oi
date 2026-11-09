<template>
  <el-form ref="formRef" :model="form" :rules="rules" label-width="120px">
    <el-form-item v-if="isCreate" label="任务键" prop="jobKey">
      <el-input v-model="form.jobKey" placeholder="点分命名（如 report.daily），创建后不可修改" />
    </el-form-item>
    <el-form-item label="任务名称" prop="name">
      <el-input v-model="form.name" placeholder="必填，最长 100 字符" />
    </el-form-item>
    <el-form-item label="触发类型" prop="triggerType">
      <el-select v-model="form.triggerType">
        <el-option label="CRON 表达式" value="CRON" />
        <el-option label="固定频率（fixed-rate）" value="FIXED_RATE" />
        <el-option label="固定延迟（fixed-delay）" value="FIXED_DELAY" />
      </el-select>
    </el-form-item>
    <el-form-item :label="form.triggerType === 'CRON' ? 'CRON 表达式' : '执行周期'" prop="triggerValue">
      <el-input
        v-if="form.triggerType === 'CRON'"
        v-model="form.triggerValue"
        placeholder="6 位表达式：秒 分 时 日 月 周（如 0 0 2 * * *）"
      />
      <template v-else>
        <el-input-number
          :model-value="periodNumber"
          :min="1000"
          :step="1000"
          controls-position="right"
          placeholder="毫秒（>=1000）"
          class="period-input"
          @update:model-value="v => form.triggerValue = String(v ?? '')"
        />
        <span class="form-tip">毫秒，最小 1000</span>
      </template>
    </el-form-item>
    <el-form-item v-if="form.triggerType === 'CRON'" label=" ">
      <CronVisualizer :expression="form.triggerValue" class="cron-visualizer-item" />
    </el-form-item>
    <el-form-item label="超时时间（毫秒）" prop="timeoutMillis">
      <el-input-number
        :model-value="Number(form.timeoutMillis)"
        :min="0"
        :step="1000"
        controls-position="right"
        class="period-input"
        @update:model-value="v => form.timeoutMillis = String(v ?? 0)"
      />
      <span class="form-tip">0=不限时；超时强制中断并标记 TIMEOUT</span>
    </el-form-item>
    <el-divider content-position="left">
      重试配置
    </el-divider>
    <el-form-item label="重试策略" prop="retryPolicy">
      <el-select v-model="form.retryPolicy">
        <el-option label="固定间隔" value="FIXED_INTERVAL" />
        <el-option label="指数退避（含抖动）" value="EXPONENTIAL_BACKOFF" />
      </el-select>
    </el-form-item>
    <el-form-item label="最大重试次数" prop="maxRetryCount">
      <el-input-number
        v-model="form.maxRetryCount"
        :min="0"
        :max="99"
        controls-position="right"
        class="period-input"
      />
      <span class="form-tip">0=不重试；耗尽后可重试异常转死信</span>
    </el-form-item>
    <el-form-item label="初始重试间隔（毫秒）" prop="retryInitialInterval">
      <el-input-number
        :model-value="Number(form.retryInitialInterval)"
        :min="0"
        :step="1000"
        controls-position="right"
        class="period-input"
        @update:model-value="v => form.retryInitialInterval = String(v ?? 0)"
      />
    </el-form-item>
    <el-form-item label="最大重试间隔（毫秒）" prop="retryMaxInterval">
      <el-input-number
        :model-value="Number(form.retryMaxInterval)"
        :min="0"
        :step="1000"
        controls-position="right"
        class="period-input"
        @update:model-value="v => form.retryMaxInterval = String(v ?? 0)"
      />
      <span class="form-tip">指数退避封顶值，须 >= 初始间隔</span>
    </el-form-item>
    <el-form-item label="可重试白名单" prop="retryInclude">
      <el-input
        v-model="form.retryInclude"
        type="textarea"
        :rows="2"
        placeholder="异常类型全限定名，逗号分隔，支持 * 尾通配（如 java.io.IOException,java.net.*）；命中即重试"
      />
    </el-form-item>
    <el-form-item label="不可重试黑名单" prop="retryExclude">
      <el-input
        v-model="form.retryExclude"
        type="textarea"
        :rows="2"
        placeholder="异常类型模式，逗号分隔；命中直接判失败（优先级高于白名单）"
      />
    </el-form-item>
    <el-form-item label="并发冲突策略" prop="conflictStrategy">
      <el-select v-model="form.conflictStrategy">
        <el-option label="丢弃后续执行（默认）" value="DISCARD" />
        <el-option label="排队等待前序完成" value="QUEUE" />
      </el-select>
    </el-form-item>
    <el-form-item label="连续失败告警阈值" prop="alertFailureThreshold">
      <el-input-number
        v-model="form.alertFailureThreshold"
        :min="0"
        :max="99"
        controls-position="right"
        class="period-input"
      />
      <span class="form-tip">0=不启用；达到阈值发站内信告警</span>
    </el-form-item>
    <el-form-item label="备注" prop="remark">
      <el-input v-model="form.remark" type="textarea" :rows="2" placeholder="选填，最长 500 字符" />
    </el-form-item>
  </el-form>
</template>

<script setup lang="ts">
/**
 * 任务定义表单（新增/编辑共用）；校验规则与后端 SysJobCreateCmd 的
 * Bean Validation 逐条对齐，CRON 合法性/周期下限由后端业务校验兜底。
 *
 * <p>数据流：本地 reactive 副本承载编辑态（打开时由父组件 snapshot 同步），
 * 提交时父组件经 getFormValues() 读取——避免 v-model 直接变更 props 违反单向数据流。
 *
 * <p>CRON 触发时下方内嵌 {@link CronVisualizer} 可视化组件（字段分解 + 释义 +
 * 后续 5 次执行时间预览，预览经后端与调度器同源计算）。
 */

import CronVisualizer from './CronVisualizer.vue'

const props = defineProps<{
  /** 表单初始值快照（打开对话框时传入；组件内不回写） */
  modelValue: SysJobCreateCmd | SysJobUpdateCmd
}>()

/** 是否创建模式（显示任务键字段） */
const isCreate = computed(() => 'jobKey' in props.modelValue)

/** 本地编辑副本 */
const form = reactive<SysJobCreateCmd>(JSON.parse(JSON.stringify(props.modelValue)))

/** 打开时同步初始值（同一对话框复用连续编辑两个对象时生效） */
watch(() => props.modelValue, (value) => {
  Object.assign(form, JSON.parse(JSON.stringify(value)))
})

const formRef = ref<FormInstance>()

/** 周期字段数字形态（el-input-number 受控展示） */
const periodNumber = computed(() => Number(form.triggerValue) || undefined)

const rules: FormRules = {
  jobKey: [
    { required: true, message: '任务键不能为空', trigger: 'blur' },
    {
      min: 2,
      max: 100,
      message: '任务键长度须在 2~100 位之间',
      trigger: 'blur',
    },
    { pattern: /^[\w-]+(\.[\w-]+)*$/, message: '任务键须为点分命名格式（如 report.daily）', trigger: 'blur' },
  ],
  name: [
    { required: true, message: '任务名称不能为空', trigger: 'blur' },
    { max: 100, message: '任务名称最长 100 字符', trigger: 'blur' },
  ],
  triggerType: [
    { required: true, message: '触发类型不能为空', trigger: 'change' },
  ],
  triggerValue: [
    { required: true, message: '触发配置不能为空', trigger: 'blur' },
  ],
  retryInclude: [
    { max: 1000, message: '白名单最长 1000 字符', trigger: 'blur' },
  ],
  retryExclude: [
    { max: 1000, message: '黑名单最长 1000 字符', trigger: 'blur' },
  ],
  remark: [
    { max: 500, message: '备注最长 500 字符', trigger: 'blur' },
  ],
}

/**
 * 校验表单；通过返回 true（供父组件 await）。
 */
async function validate() {
  try {
    await formRef.value?.validate()
    return true
  } catch {
    return false
  }
}

/**
 * 读取当前编辑值（父组件提交时调用）。
 */
function getFormValues(): SysJobCreateCmd | SysJobUpdateCmd {
  if (isCreate.value)
    return { ...form } as SysJobCreateCmd
  const rest = { ...form } as Partial<SysJobCreateCmd>
  delete rest.jobKey
  return rest as SysJobUpdateCmd
}

defineExpose({ validate, getFormValues })
</script>

<style scoped>
.period-input {
  width: 200px;
}

.form-tip {
  margin-left: 8px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.cron-visualizer-item {
  width: 100%;
}
</style>
