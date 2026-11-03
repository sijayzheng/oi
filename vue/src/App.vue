<template>
  <el-config-provider :locale="zhCn">
    <RouterView />
  </el-config-provider>
</template>

<script setup lang="ts">
// Element Plus 全局语言包：中文（unplugin 按需引入模式，经 ConfigProvider 下发，
// 覆盖分页/空数据/气泡确认/日期选择等内置组件的默认英文文案）
import zhCn from 'element-plus/es/locale/lang/zh-cn'
</script>
