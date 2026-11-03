<template>
  <el-popover placement="bottom-end" trigger="click" :width="180">
    <template #reference>
      <el-button :icon="Setting">
        列设置
      </el-button>
    </template>
    <div class="column-setting-header">
      <el-checkbox :model-value="allSelected" @change="toggleAll">
        全选
      </el-checkbox>
    </div>
    <el-checkbox-group :model-value="modelValue" @change="handleChange">
      <el-checkbox v-for="option in options" :key="option.value" :value="option.value">
        {{ option.label }}
      </el-checkbox>
    </el-checkbox-group>
  </el-popover>
</template>

<script setup lang="ts">
/**
 * 表格列设置组件：弹层内勾选控制列的显示/隐藏，配合 el-table-column 的 v-if 使用。
 *
 * <p>使用方式：`<ColumnSetting v-model="visibleColumns" :options="columnOptions" />`，
 * visibleColumns 为当前展示列的 prop 名集合，columnOptions 为全部候选列。
 */
/**
 * 列候选选项。
 */
interface ColumnOption {
  /** 列标识（el-table-column 的 prop 名） */
  value: string
  /** 列显示名 */
  label: string
}

const props = defineProps<{
  /** 当前展示的列标识集合 */
  modelValue: string[]
  /** 全部候选列 */
  options: ColumnOption[]
}>()

const emit = defineEmits<{
  'update:modelValue': [value: string[]]
}>()

/** 是否全选（用于全选开关状态展示） */
const allSelected = computed(() => props.options.every(option => props.modelValue.includes(option.value)))

/**
 * 勾选变更：同步至父组件。
 */
function handleChange(value: Array<string | number | boolean>) {
  emit('update:modelValue', value.map(String))
}

/**
 * 全选/清空。
 */
function toggleAll(selected: string | number | boolean) {
  emit('update:modelValue', selected
    ? props.options.map(option => option.value)
    : [])
}
</script>

<style scoped>
.column-setting-header {
  margin-bottom: 8px;
  padding-bottom: 8px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}
</style>
