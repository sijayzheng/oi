<template>
  <div class="message-view">
    <!-- 顶部：箱体切换 + 工具栏 -->
    <el-card shadow="never" class="message-toolbar-card">
      <div class="message-toolbar">
        <el-radio-group v-model="activeBox" @change="onBoxChange">
          <el-radio-button value="inbox">
            收件箱
            <el-badge v-if="inboxUnreadTotal > 0" :value="inboxUnreadTotal" :max="99" class="box-badge" />
          </el-radio-button>
          <el-radio-button value="sent">
            发件箱
          </el-radio-button>
          <el-radio-button value="archive">
            归档箱
          </el-radio-button>
        </el-radio-group>

        <div class="message-actions">
          <el-button :icon="Refresh" circle @click="reload" />
          <el-button v-if="activeBox === 'inbox'" :disabled="!selection.length" @click="batchMarkRead">
            批量已读
          </el-button>
          <el-button :disabled="!selection.length" @click="batchArchive(activeBox === 'archive' ? 0 : 1)">
            {{ activeBox === 'archive' ? '取消归档' : '批量归档' }}
          </el-button>
          <el-button :disabled="!selection.length" type="danger" plain @click="batchTrash">
            批量删除
          </el-button>
          <el-button v-if="activeBox === 'inbox'" link type="primary" @click="markAllRead">
            全部已读
          </el-button>
          <el-button type="primary" :icon="Plus" @click="openCompose">
            写消息
          </el-button>
        </div>
      </div>

      <!-- 过滤行 -->
      <el-form inline class="message-filter">
        <el-form-item v-if="activeBox !== 'sent'" label="消息类型">
          <el-select v-model="query.msgType" placeholder="全部" clearable class="filter-select" @change="search">
            <el-option label="系统通知" value="NOTICE" />
            <el-option label="私信" value="LETTER" />
            <el-option label="公告广播" value="BROADCAST" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="activeBox === 'inbox'" label="已读状态">
          <el-select v-model="query.readStatus" placeholder="全部" clearable class="filter-select filter-narrow" @change="search">
            <el-option label="未读" :value="0" />
            <el-option label="已读" :value="1" />
          </el-select>
        </el-form-item>
        <el-form-item label="关键词">
          <el-input
            v-model="query.keyword"
            placeholder="标题/内容模糊匹配"
            clearable
            class="filter-keyword"
            @keyup.enter="search"
            @clear="search"
          />
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :icon="Search" @click="search">
            搜索
          </el-button>
          <el-button :icon="RefreshLeft" @click="resetFilter">
            重置
          </el-button>
        </el-form-item>
      </el-form>
    </el-card>

    <!-- 列表 -->
    <el-card shadow="never">
      <el-table
        ref="tableRef"
        :data="rows"
        v-loading="loading"
        row-key="id"
        @selection-change="(items: MessageItem[]) => selection = items"
      >
        <el-table-column type="selection" width="42" />
        <el-table-column width="46">
          <template #default="{ row }">
            <el-badge is-dot :hidden="isRead(row as MessageItem)" class="unread-dot">
              <span class="dot-holder" />
            </el-badge>
          </template>
        </el-table-column>
        <el-table-column label="类型" width="96">
          <template #default="{ row }">
            <el-tag :type="typeTag(row.msgType)" size="small" disable-transitions>
              {{ typeLabel(row.msgType) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="标题" min-width="200" show-overflow-tooltip>
          <template #default="{ row }">
            <el-link type="primary" :underline="false" class="title-link" :class="{ 'is-unread': !isRead(row as MessageItem) }" @click="openDetail(row as MessageItem)">
              {{ row.title }}
              <el-tag v-if="row.status === 'WITHDRAWN'" size="small" type="info" disable-transitions class="withdrawn-tag">
                已撤回
              </el-tag>
            </el-link>
          </template>
        </el-table-column>
        <el-table-column :label="activeBox === 'sent' ? '接收' : '发送'" width="110" show-overflow-tooltip>
          <template #default="{ row }">
            {{ partyLabel(row as MessageItem) }}
          </template>
        </el-table-column>
        <el-table-column label="附件" width="70">
          <template #default="{ row }">
            <span v-if="row.attachmentIds.length" class="attachment-flag">📎 {{ row.attachmentIds.length }}</span>
            <span v-else>-</span>
          </template>
        </el-table-column>
        <el-table-column label="时间" width="170" prop="sendTime" />
        <el-table-column label="操作" width="210" fixed="right">
          <template #default="{ row }">
            <el-button link type="primary" @click="openDetail(row as MessageItem)">
              查看
            </el-button>
            <el-button
              v-if="activeBox === 'inbox'"
              link
              type="primary"
              :disabled="isRead(row as MessageItem)"
              @click="markRead(row as MessageItem)"
            >
              已读
            </el-button>
            <el-button
              v-if="row.withdrawable"
              link
              type="warning"
              @click="withdraw(row as MessageItem)"
            >
              撤回
            </el-button>
            <el-button
              v-if="activeBox !== 'sent'"
              link
              :type="activeBox === 'archive' ? 'primary' : 'default'"
              @click="archiveOne(row as MessageItem)"
            >
              {{ activeBox === 'archive' ? '取消归档' : '归档' }}
            </el-button>
            <el-button link type="danger" @click="trashOne(row as MessageItem)">
              删除
            </el-button>
          </template>
        </el-table-column>
        <template #empty>
          <el-empty description="暂无消息" :image-size="80" />
        </template>
      </el-table>

      <div class="message-pagination">
        <el-pagination
          v-model:current-page="page.pageNum"
          v-model:page-size="page.pageSize"
          :total="total"
          :page-sizes="[10, 20, 50]"
          layout="total, sizes, prev, pager, next, jumper"
          @current-change="load"
          @size-change="search"
        />
      </div>
    </el-card>

    <!-- 详情抽屉 -->
    <el-drawer v-model="detailVisible" :size="isMobile ? '92%' : '42%'" title="消息详情">
      <div v-if="detail" class="message-detail">
        <h3 class="detail-title">
          {{ detail.title }}
        </h3>
        <div class="detail-meta">
          <el-tag :type="typeTag(detail.msgType)" size="small" disable-transitions>
            {{ typeLabel(detail.msgType) }}
          </el-tag>
          <span>{{ partyLabel(detail) }}</span>
          <span>{{ detail.sendTime }}</span>
          <el-tag v-if="detail.status === 'WITHDRAWN'" size="small" type="info" disable-transitions>
            已撤回
          </el-tag>
        </div>
        <div class="detail-content">
          {{ detail.content }}
        </div>
        <div v-if="detailAttachments.length" class="detail-attachments">
          <div class="attachment-title">
            附件（{{ detailAttachments.length }}）
          </div>
          <div class="attachment-images">
            <template v-for="att in detailAttachments" :key="att.id">
              <el-image
                v-if="att.image"
                :src="att.accessUrl"
                :preview-src-list="[att.accessUrl]"
                :initial-index="detailAttachments.indexOf(att)"
                fit="cover"
                class="attachment-image"
                lazy
              />
            </template>
          </div>
          <div class="attachment-files">
            <el-link
              v-for="att in detailAttachments.filter(a => !a.image)"
              :key="att.id"
              type="primary"
              :href="att.accessUrl"
              target="_blank"
              :underline="false"
            >
              📄 {{ att.originalName }}
            </el-link>
          </div>
        </div>
        <div v-if="detail.withdrawable" class="detail-actions">
          <el-button type="warning" plain @click="withdraw(detail); detailVisible = false">
            撤回此消息
          </el-button>
          <span class="withdraw-tip">发送后 5 分钟内可撤回</span>
        </div>
      </div>
    </el-drawer>

    <!-- 写消息对话框 -->
    <el-dialog v-model="composeVisible" title="写消息" width="560px" destroy-on-close>
      <el-form ref="composeFormRef" :model="composeForm" :rules="composeRules" label-width="90px">
        <el-form-item label="消息类型" prop="msgType">
          <el-select v-model="composeForm.msgType" :disabled="!isAdmin" class="compose-type">
            <el-option v-if="isAdmin" label="系统通知（发给指定用户）" value="NOTICE" />
            <el-option v-if="isAdmin" label="公告广播（发给全员）" value="BROADCAST" />
            <el-option label="私信" value="LETTER" />
          </el-select>
        </el-form-item>
        <el-form-item v-if="composeForm.msgType !== 'BROADCAST'" label="接收人" prop="receiverUsername">
          <el-input v-model="composeForm.receiverUsername" placeholder="接收人登录名" clearable />
        </el-form-item>
        <el-form-item label="标题" prop="title">
          <el-input v-model="composeForm.title" placeholder="消息标题" maxlength="100" show-word-limit />
        </el-form-item>
        <el-form-item label="内容" prop="content">
          <el-input v-model="composeForm.content" type="textarea" :rows="5" placeholder="消息内容" maxlength="4000" show-word-limit />
        </el-form-item>
        <el-form-item label="附件">
          <div class="compose-upload">
            <el-upload
              :show-file-list="false"
              :http-request="handleUpload"
              multiple
            >
              <el-button :icon="Plus">
                添加附件
              </el-button>
            </el-upload>
            <div class="upload-tip">
              最多 10 个，单个不超过 10MB
            </div>
            <div v-if="uploadFiles.length" class="upload-chips">
              <el-tag
                v-for="item in uploadFiles"
                :key="item.uid"
                closable
                type="info"
                disable-transitions
                @close="removeUpload(item.uid)"
              >
                📄 {{ item.name }}
              </el-tag>
            </div>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="composeVisible = false">
          取消
        </el-button>
        <el-button type="primary" :loading="sending" @click="submitCompose">
          发送
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup lang="ts">
/**
 * 消息中心：站内信收件箱/发件箱/归档箱，覆盖系统通知、私信、公告广播三类消息。
 *
 * <p>功能约定：未读角标由顶栏 SSE 实时驱动（messageStore）；列表未读行加粗红点；
 * 详情抽屉内附件图片内嵌预览、文件链接下载；发送后 5 分钟内可撤回（后端校验时限，
 * 前端以 withdrawable 标记渲染按钮）；管理员可发布系统通知/公告广播（后端角色校验兜底）。
 * 响应式约定：视口宽度 < 768px 时详情抽屉转为 92% 宽度，过滤器换行堆叠。
 */
import { useWindowSize } from '@vueuse/core'
import { ElNotification } from 'element-plus'
import { Refresh, RefreshLeft, Search } from '@element-plus/icons-vue'
import { storeToRefs } from 'pinia'
import { useAuthStore } from '@/stores/auth'
import { useMessageStore } from '@/stores/message'
import { messageApi } from '@/api/message'

const messageStore = useMessageStore()
const auth = useAuthStore()
const { lastEvent, lastWithdrawId } = storeToRefs(messageStore)
const { width } = useWindowSize()
const isMobile = computed(() => width.value < 768)

/* ==================== 列表状态 ==================== */

type Box = 'inbox' | 'sent' | 'archive'
const activeBox = ref<Box>('inbox')
const loading = ref(false)
const rows = ref<MessageItem[]>([])
const selection = ref<MessageItem[]>([])
const total = ref(0)
/** 收件箱未读总数（箱体切换徽标；随列表刷新/全部已读同步） */
const inboxUnreadTotal = ref(0)

const query = reactive<MessageQuery>({
  msgType: '',
  readStatus: null,
  archived: null,
  keyword: '',
})

const page = reactive({ pageNum: 1, pageSize: 10 })

/** 管理员判定：菜单权限树含系统管理模块（后端仍有角色校验兜底） */
const isAdmin = computed(() => auth.menus.some(node => node.children?.some(child => child.path?.startsWith('/system'))))

function isRead(row: MessageItem) {
  return row.readStatus === null || row.readStatus === 1
}

function typeLabel(type: MessageType) {
  return type === 'NOTICE' ? '系统通知' : type === 'LETTER' ? '私信' : '公告广播'
}

function typeTag(type: MessageType) {
  return type === 'NOTICE' ? 'primary' : type === 'LETTER' ? 'success' : 'warning'
}

/** 收发相对方展示文案（收件箱看发送方，发件箱看接收方） */
function partyLabel(row: MessageItem) {
  if (activeBox.value === 'sent') {
    return row.msgType === 'BROADCAST' ? '全员' : `用户 ${row.receiverId}`
  }
  return row.senderId === '0' ? '系统' : row.msgType === 'BROADCAST' ? '全员广播' : `用户 ${row.senderId}`
}

/* ==================== 加载 ==================== */

async function load() {
  loading.value = true
  try {
    // 仅组装有效过滤条件：null/空串不传（后端缺省语义：archived=0 仅看未归档）
    const params: MessageQuery = { pageNum: page.pageNum, pageSize: page.pageSize }
    if (query.msgType)
      params.msgType = query.msgType
    if (query.readStatus !== null && query.readStatus !== undefined)
      params.readStatus = query.readStatus
    if (activeBox.value === 'archive')
      params.archived = 1
    if (query.keyword)
      params.keyword = query.keyword
    if (activeBox.value === 'sent') {
      const result = await messageApi.sent(params)
      rows.value = result.data
      total.value = Number(result.total ?? 0)
    }
    else {
      const result = await messageApi.inbox(params)
      rows.value = result.data
      total.value = Number(result.total ?? 0)
      if (activeBox.value === 'inbox' && page.pageNum === 1 && !params.readStatus) {
        refreshInboxUnread()
      }
    }
  } finally {
    loading.value = false
  }
}

/** 收件箱未读总数：按未读过滤单独查一次（仅首页全量时同步，避免额外开销） */
async function refreshInboxUnread() {
  try {
    const result = await messageApi.inbox({ readStatus: 0, pageNum: 1, pageSize: 1 })
    inboxUnreadTotal.value = Number(result.total ?? 0)
  } catch {
    inboxUnreadTotal.value = 0
  }
}

function search() {
  page.pageNum = 1
  load()
}

function reload() {
  load()
}

function resetFilter() {
  query.msgType = ''
  query.readStatus = null
  query.keyword = ''
  search()
}

function onBoxChange() {
  query.readStatus = null
  page.pageNum = 1
  load()
}

/* ==================== 单条/批量操作 ==================== */

async function markRead(row: MessageItem) {
  await messageApi.markRead([row.id])
  row.readStatus = 1
  syncUnreadAfterRead()
}

async function markAllRead() {
  const count = await messageApi.markAllRead()
  ElMessage.success(`已全部标记为已读（${count} 条）`)
  load()
}

async function batchMarkRead() {
  const ids = selection.value.filter(row => !isRead(row as MessageItem)).map(row => row.id)
  if (!ids.length) {
    ElMessage.info('所选消息均已读')
    return
  }
  const count = await messageApi.markRead(ids)
  ElMessage.success(`已标记 ${count} 条为已读`)
  load()
}

function batchArchive(archived: number) {
  const ids = selection.value.map(row => row.id)
  doArchive(ids, archived)
}

async function doArchive(ids: string[], archived: number) {
  const count = await messageApi.archive(ids, archived)
  ElMessage.success(archived === 1 ? `已归档 ${count} 条消息` : `已取消归档 ${count} 条消息`)
  load()
}

function archiveOne(row: MessageItem) {
  doArchive([row.id], activeBox.value === 'archive' ? 0 : 1)
}

function batchTrash() {
  doTrash(selection.value.map(row => row.id))
}

async function doTrash(ids: string[]) {
  const count = await messageApi.trash(ids)
  ElMessage.success(`已删除 ${count} 条消息`)
  syncUnreadAfterRead()
  load()
}

function trashOne(row: MessageItem) {
  doTrash([row.id])
}

/** 已读/删除后按当前可见未读数收缩本地角标（权威值以后端 unread 事件为准） */
function syncUnreadAfterRead() {
  inboxUnreadTotal.value = Math.max(0, inboxUnreadTotal.value - 1)
  if (messageStore.unread > 0)
    messageStore.unread--
}

async function withdraw(row: MessageItem) {
  await messageApi.withdraw(row.id)
  ElMessage.success('消息已撤回')
  load()
}

/* ==================== 详情 ==================== */

const detailVisible = ref(false)
const detail = ref<MessageItem | null>(null)
const detailAttachments = ref<MessageAttachment[]>([])

async function openDetail(row: MessageItem) {
  detail.value = row
  detailVisible.value = true
  detailAttachments.value = []
  // 详情接口对接收方自动置已读；回写列表行状态
  const vo = await messageApi.detail(row.id)
  detail.value = vo
  row.readStatus = vo.readStatus
  row.content = vo.content
  row.withdrawable = vo.withdrawable
  if (!isRead(row as MessageItem))
    syncUnreadAfterRead()
  // 附件解析：图片内嵌展示、文件提供下载链接
  detailAttachments.value = await Promise.all(vo.attachmentIds.map(async (id) => {
    const file = await fileApi.get(id)
    const image = file.contentType?.startsWith('image/') ?? /\.(png|jpe?g|gif|webp)$/i.test(file.originalName ?? '')
    return {
      id,
      originalName: file.originalName ?? id,
      accessUrl: toFileSrc(file.accessUrl ?? ''),
      image,
    }
  }))
}

/* ==================== 写消息 ==================== */

const composeVisible = ref(false)
const sending = ref(false)
const composeFormRef = ref<FormInstance>()

const composeForm = reactive<MessageSendCmd>({
  msgType: 'LETTER',
  receiverUsername: '',
  title: '',
  content: '',
  attachmentIds: [],
})

const composeRules: FormRules = {
  msgType: [{ required: true, message: '请选择消息类型', trigger: 'change' }],
  receiverUsername: [{
    validator: (_rule, value, callback) => {
      if (composeForm.msgType !== 'BROADCAST' && !value)
        callback(new Error('请输入接收人登录名'))
      else
        callback()
    },
    trigger: 'blur',
  }],
  title: [
    { required: true, message: '请输入消息标题', trigger: 'blur' },
    { max: 100, message: '标题最长 100 字符', trigger: 'blur' },
  ],
  content: [
    { required: true, message: '请输入消息内容', trigger: 'blur' },
    { max: 4000, message: '内容最长 4000 字符', trigger: 'blur' },
  ],
}

interface UploadEntry {
  /** el-upload 分配的文件 uid（删除时对应用） */
  uid: number | string
  /** 文件 id（统一文件服务回传） */
  id: string
  /** 展示文件名 */
  name: string
}

const uploadFiles = ref<UploadEntry[]>([])

function openCompose() {
  composeForm.msgType = isAdmin.value ? 'NOTICE' : 'LETTER'
  composeForm.receiverUsername = ''
  composeForm.title = ''
  composeForm.content = ''
  composeForm.attachmentIds = []
  uploadFiles.value = []
  composeVisible.value = true
}

/** 附件上传：经统一文件服务，仅保存文件 id */
async function handleUpload(options: UploadRequestOptions) {
  // el-upload 会在原始 File 上附加 uid（用于文件列表对应用）
  const raw = options.file as File & { uid?: number | string }
  const file = await fileApi.upload(raw, 'message')
  uploadFiles.value.push({ uid: raw.uid ?? Date.now(), id: file.id, name: file.originalName ?? raw.name })
  composeForm.attachmentIds = uploadFiles.value.map(item => item.id)
}

function removeUpload(uid: number | string) {
  uploadFiles.value = uploadFiles.value.filter(item => item.uid !== uid)
  composeForm.attachmentIds = uploadFiles.value.map(item => item.id)
}

async function submitCompose() {
  const valid = await composeFormRef.value?.validate().catch(() => false)
  if (!valid)
    return
  sending.value = true
  try {
    await messageApi.send({
      ...composeForm,
      receiverUsername: composeForm.msgType === 'BROADCAST' ? undefined : composeForm.receiverUsername,
      attachmentIds: composeForm.attachmentIds,
    })
    ElMessage.success('消息发送成功')
    composeVisible.value = false
    // 私信/系统通知在发件箱可见；广播仅管理员发件箱可见
    if (activeBox.value === 'sent') {
      search()
    }
  } finally {
    sending.value = false
  }
}

/* ==================== SSE 实时联动 ==================== */

// 新消息：轻提示 + 当前在收件箱首页时刷新列表
watch(lastEvent, (event) => {
  if (!event)
    return
  ElNotification({
    title: event.msgType === 'BROADCAST' ? '收到公告广播' : '收到新消息',
    message: event.title,
    type: 'info',
    duration: 4000,
  })
  inboxUnreadTotal.value++
  if (activeBox.value === 'inbox' && page.pageNum === 1) {
    load()
  }
})

// 撤回：刷新当前列表（被撤回消息即时消失）
watch(lastWithdrawId, (id) => {
  if (!id)
    return
  if (rows.value.some(row => row.id === id)) {
    ElMessage.warning('一条消息已被发送人撤回')
    load()
  }
})

/* ==================== 初始化 ==================== */

messageStore.refreshUnread()
load()
</script>

<style scoped>
.message-view {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.message-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
}

.message-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.box-badge {
  margin-left: 6px;
  vertical-align: text-top;
}

.box-badge :deep(.el-badge__content) {
  position: static;
  transform: none;
}

.message-filter {
  margin-top: 8px;
}

.filter-select {
  width: 140px;
}

.filter-narrow {
  width: 100px;
}

.filter-keyword {
  width: 220px;
}

.dot-holder {
  display: block;
  width: 8px;
  height: 8px;
}

.title-link.is-unread {
  font-weight: 600;
}

.withdrawn-tag {
  margin-left: 6px;
}

.attachment-flag {
  color: var(--el-text-color-secondary);
}

.message-pagination {
  display: flex;
  justify-content: flex-end;
  margin-top: 12px;
}

/* 详情抽屉 */
.detail-title {
  margin: 0 0 8px;
  font-size: 17px;
}

.detail-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  padding-bottom: 12px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
  border-bottom: 1px solid var(--el-border-color-lighter);
}

.detail-content {
  padding: 16px 0;
  color: var(--el-text-color-primary);
  line-height: 1.8;
  white-space: pre-wrap;
  word-break: break-word;
}

.attachment-title {
  margin-bottom: 8px;
  color: var(--el-text-color-secondary);
  font-size: 13px;
}

.attachment-images {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.attachment-image {
  width: 120px;
  height: 120px;
  border-radius: 6px;
  border: 1px solid var(--el-border-color-lighter);
}

.attachment-files {
  display: flex;
  flex-direction: column;
  gap: 6px;
  margin-top: 8px;
}

.detail-actions {
  display: flex;
  gap: 10px;
  align-items: center;
  padding-top: 16px;
  border-top: 1px solid var(--el-border-color-lighter);
}

.withdraw-tip {
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.compose-type {
  width: 240px;
}

.compose-upload {
  width: 100%;
}

.upload-tip {
  margin-top: 4px;
  color: var(--el-text-color-secondary);
  font-size: 12px;
}

.upload-chips {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
  margin-top: 8px;
}

@media (max-width: 768px) {
  .message-actions {
    width: 100%;
  }

  .filter-keyword {
    width: 100%;
  }
}
</style>
