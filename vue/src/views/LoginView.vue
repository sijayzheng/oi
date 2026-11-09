<template>
  <div class="login-page">
    <el-card class="login-card" shadow="always">
      <div class="login-title">
        Oi 管理系统
      </div>
      <div class="login-subtitle">
        欢迎登录，请输入账号信息
      </div>
      <el-form ref="formRef" :model="form" :rules="rules" size="large" @keyup.enter="submit">
        <el-form-item prop="username">
          <el-input v-model="form.username" placeholder="用户名" :prefix-icon="User" autocomplete="username" />
        </el-form-item>
        <el-form-item prop="password">
          <el-input
            ref="passwordRef" v-model="form.password" type="password" placeholder="密码" show-password
            :prefix-icon="Lock" autocomplete="current-password"
          />
        </el-form-item>
        <el-button type="primary" class="login-btn" size="large" :loading="loading" @click="submit">
          登 录
        </el-button>
      </el-form>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import type { InputInstance } from 'element-plus'
import { useRoute, useRouter } from 'vue-router'
/**
 * 登录页：用户名密码登录，成功后装载动态路由并跳转目标页面。
 *
 * <p>跳转约定：优先回跳登录前拦截的地址（redirect 查询参数），
 * 无回跳地址时进入当前用户第一个可导航菜单（根路径守卫同样兜底）。
 * 登录失败提示由 request.ts 统一弹出（601003 凭据错误 / 601004 账号禁用 /
 * 601007 登录锁定，含剩余等待分钟数）；失败后清空密码并聚焦，便于直接重试。
 */
import { firstMenuPath, registerDynamicRoutes } from '@/router'
import { useAuthStore } from '@/stores/auth'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()

const formRef = ref<FormInstance>()
const passwordRef = ref<InputInstance>()
const loading = ref(false)

const form = reactive({
  username: '',
  password: '',
})

const rules: FormRules = {
  username: [
    { required: true, message: '请输入用户名', trigger: 'blur' },
  ],
  password: [
    { required: true, message: '请输入密码', trigger: 'blur' },
  ],
}

/** 提交登录：成功后装载动态路由 → 跳回原目标或第一个可用菜单 */
async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid)
    return
  loading.value = true
  try {
    await auth.doLogin(form)
    const menus = await auth.fetchMenus()
    registerDynamicRoutes(menus)
    const redirect = typeof route.query.redirect === 'string'
      ? route.query.redirect
      : ''
    router.replace(redirect || firstMenuPath(menus) || '/')
  } catch {
    // 登录失败（错误提示已由 request.ts 统一弹出）：清空密码并聚焦，保留用户名便于直接重试
    form.password = ''
    passwordRef.value?.focus()
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.login-page {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100vw;
  height: 100vh;
  overflow: hidden;
  background: linear-gradient(135deg, #1f2d3d 0%, #001529 100%);
}

.login-card {
  width: 380px;
  padding: 8px 12px 4px;
}

.login-title {
  margin-bottom: 8px;
  color: var(--el-text-color-primary);
  font-size: 22px;
  font-weight: 600;
  text-align: center;
}

.login-subtitle {
  margin-bottom: 24px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  text-align: center;
}

.login-btn {
  width: 100%;
  margin-top: 4px;
}
</style>
