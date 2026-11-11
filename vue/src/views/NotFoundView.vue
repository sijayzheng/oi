<template>
  <div class="not-found-page">
    <el-result icon="warning" title="404" sub-title="页面不存在或您没有访问权限">
      <template #extra>
        <el-button type="primary" @click="backHome">
          返回首页
        </el-button>
      </template>
    </el-result>
  </div>
</template>

<script setup lang="ts">
/**
 * 404 页：路由未匹配兜底。动态路由机制下，访问未被授权分配的路由
 * （无对应菜单权限）或输错地址时到达本页。
 */
import { useRouter } from 'vue-router'

const router = useRouter()

/** 返回默认首页（静态路由，所有登录用户可见） */
function backHome() {
  router.replace('/')
}
</script>

<style scoped>
.not-found-page {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 100vh;
}
</style>
