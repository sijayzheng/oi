<template>
  <el-card shadow="never" class="profile-card">
    <el-row :gutter="24">
      <!-- 个人信息 -->
      <el-col :xs="24" :md="12">
        <div class="profile-section-title">
          个人信息
        </div>
        <div class="profile-avatar-row">
          <el-avatar :size="72" :src="avatarSrc || undefined" class="profile-avatar">
            {{ profileForm.nickname?.charAt(0) || 'U' }}
          </el-avatar>
          <div class="profile-avatar-actions">
            <el-upload
              :show-file-list="false"
              accept=".jpg,.jpeg,.png,.gif,.webp"
              :before-upload="beforeAvatarUpload"
              :http-request="handleAvatarUpload"
            >
              <el-button :loading="avatarUploading">
                更换头像
              </el-button>
            </el-upload>
            <div class="profile-avatar-tip">
              支持 jpg/png/gif/webp，不超过 2MB
            </div>
          </div>
        </div>
        <el-form ref="profileFormRef" :model="profileForm" :rules="profileRules" label-width="72px">
          <el-form-item label="登录名">
            <el-input :model-value="username" disabled />
          </el-form-item>
          <el-form-item label="昵称" prop="nickname">
            <el-input v-model="profileForm.nickname" maxlength="50" placeholder="请输入昵称" />
          </el-form-item>
          <el-form-item label="手机号" prop="mobile">
            <el-input v-model="profileForm.mobile" maxlength="20" placeholder="请输入手机号" />
          </el-form-item>
          <el-form-item label="邮箱" prop="email">
            <el-input v-model="profileForm.email" maxlength="100" placeholder="请输入邮箱" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="profileSaving" @click="saveProfile">
              保存修改
            </el-button>
          </el-form-item>
        </el-form>
      </el-col>

      <!-- 修改密码 -->
      <el-col :xs="24" :md="12">
        <div class="profile-section-title">
          修改密码
        </div>
        <el-form ref="passwordFormRef" :model="passwordForm" :rules="passwordRules" label-width="88px">
          <el-form-item label="原密码" prop="oldPassword">
            <el-input v-model="passwordForm.oldPassword" type="password" show-password placeholder="请输入原密码" />
          </el-form-item>
          <el-form-item label="新密码" prop="newPassword">
            <el-input v-model="passwordForm.newPassword" type="password" show-password placeholder="6~100 位，含字母和数字" />
          </el-form-item>
          <el-form-item label="确认新密码" prop="confirmPassword">
            <el-input v-model="passwordForm.confirmPassword" type="password" show-password placeholder="请再次输入新密码" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" :loading="passwordSaving" @click="savePassword">
              修改密码
            </el-button>
            <div class="profile-password-tip">
              修改成功后将退出登录，需使用新密码重新登录
            </div>
          </el-form-item>
        </el-form>
      </el-col>
    </el-row>
  </el-card>
</template>

<script setup lang="ts">
/**
 * 个人中心：个人信息修改（昵称/手机/邮箱/头像）+ 修改密码。
 *
 * <p>文件调用规范：头像上传经统一文件服务完成，表单只保存文件唯一 id
 * （随「保存修改」提交）；展示用临时访问地址经 fileApi.getUrl 换取（带有效期）。
 * 密码修改成功后后端注销当前会话，前端清空认证态并跳转登录页。
 * 所有操作均带加载态，成功/失败反馈由 ElMessage 与统一拦截器承担。
 */
import { useRouter } from 'vue-router'
import { clearDynamicRoutes } from '@/router'
import { useAuthStore } from '@/stores/auth'

const router = useRouter()
const auth = useAuthStore()

/* ==================== 个人信息 ==================== */

const profileFormRef = ref<FormInstance>()
const profileSaving = ref(false)
const avatarUploading = ref(false)
const username = ref('')
/** 头像展示地址（临时访问 URL，由文件 id 换取；表单只保存文件 id） */
const avatarSrc = ref('')

const profileForm = reactive({
  nickname: '',
  mobile: '',
  email: '',
  avatar: '' as string | null,
})

const profileRules: FormRules = {
  nickname: [
    { required: true, message: '请输入昵称', trigger: 'blur' },
    { max: 50, message: '昵称最长 50 字符', trigger: 'blur' },
  ],
  email: [
    {
      type: 'email',
      message: '邮箱格式不正确',
      trigger: ['blur', 'change'],
    },
  ],
}

/** 回显个人资料；头像凭文件 id 换取临时访问地址 */
async function loadProfile() {
  const profile: SysProfileVO = await authApi.profile()
  username.value = profile.username
  profileForm.nickname = profile.nickname
  profileForm.mobile = profile.mobile ?? ''
  profileForm.email = profile.email ?? ''
  profileForm.avatar = profile.avatar
  avatarSrc.value = profile.avatar
    ? toFileSrc(await fileApi.getUrl(profile.avatar))
    : ''
}

/** 头像上传前置校验：类型白名单 + 2MB 大小限制 */
function beforeAvatarUpload(file: File) {
  const allowed = [
    'image/jpeg',
    'image/png',
    'image/gif',
    'image/webp',
  ]
  if (!allowed.includes(file.type)) {
    ElMessage.error('头像仅支持 jpg/png/gif/webp 格式')
    return false
  }
  if (file.size > 2 * 1024 * 1024) {
    ElMessage.error('头像文件不能超过 2MB')
    return false
  }
  return true
}

/** 自定义上传：经统一文件服务上传，表单只保存文件 id（随「保存修改」一并提交） */
async function handleAvatarUpload(options: UploadRequestOptions) {
  avatarUploading.value = true
  try {
    const file = await authApi.uploadAvatar(options.file as File)
    profileForm.avatar = file.id
    avatarSrc.value = file.accessUrl
      ? toFileSrc(file.accessUrl)
      : ''
    ElMessage.success('头像上传成功，请点击「保存修改」生效')
  } finally {
    avatarUploading.value = false
  }
}

/** 保存个人资料；昵称同步顶栏展示 */
async function saveProfile() {
  const valid = await profileFormRef.value?.validate().catch(() => false)
  if (!valid)
    return
  profileSaving.value = true
  try {
    await authApi.updateProfile({
      nickname: profileForm.nickname,
      mobile: profileForm.mobile || null,
      email: profileForm.email || null,
      avatar: profileForm.avatar,
    })
    // 即时同步顶栏：昵称 + 头像（avatarSrc 已是签名访问地址；清空头像时回退文字头像）
    auth.nickname = profileForm.nickname
    auth.avatarUrl = avatarSrc.value
    ElMessage.success('个人资料保存成功')
  } finally {
    profileSaving.value = false
  }
}

/* ==================== 修改密码 ==================== */

const passwordFormRef = ref<FormInstance>()
const passwordSaving = ref(false)

const passwordForm = reactive({
  oldPassword: '',
  newPassword: '',
  confirmPassword: '',
})

const passwordRules: FormRules = {
  oldPassword: [
    { required: true, message: '请输入原密码', trigger: 'blur' },
  ],
  newPassword: [
    { required: true, message: '请输入新密码', trigger: 'blur' },
    {
      min: 6,
      max: 100,
      message: '新密码长度须在 6~100 位之间',
      trigger: 'blur',
    },
    { pattern: /^(?=.*[a-z])(?=.*\d).{6,100}$/i, message: '新密码须同时包含字母和数字', trigger: 'blur' },
  ],
  confirmPassword: [
    { required: true, message: '请再次输入新密码', trigger: 'blur' },
    {
      validator: (_rule, value: string, callback) => {
        if (value && value !== passwordForm.newPassword)
          callback(new Error('两次输入的密码不一致'))
        else
          callback()
      },
      trigger: 'blur',
    },
  ],
}

/** 修改密码：成功后后端注销当前会话，清空认证态回登录页 */
async function savePassword() {
  const valid = await passwordFormRef.value?.validate().catch(() => false)
  if (!valid)
    return
  passwordSaving.value = true
  try {
    await authApi.changePassword({
      oldPassword: passwordForm.oldPassword,
      newPassword: passwordForm.newPassword,
      confirmPassword: passwordForm.confirmPassword,
    })
    ElMessage.success('密码修改成功，请重新登录')
    auth.reset()
    clearDynamicRoutes()
    router.replace('/login')
  } finally {
    passwordSaving.value = false
  }
}

onMounted(loadProfile)
</script>

<style scoped>
.profile-card {
  max-width: 1080px;
}

.profile-section-title {
  margin-bottom: 20px;
  color: var(--el-text-color-primary);
  font-size: 15px;
  font-weight: 600;
}

.profile-avatar-row {
  display: flex;
  gap: 16px;
  align-items: center;
  margin-bottom: 24px;
}

.profile-avatar-actions {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.profile-avatar-tip {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.profile-password-tip {
  color: var(--el-text-color-secondary);
  font-size: 12px;
  line-height: 1.6;
}
</style>
