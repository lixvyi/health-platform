<template>
  <div class="container page">
    <div class="page-head">
      <div>
        <h2>AI 健康知识问答</h2>
        <p class="tip">检索新闻中心、卫生政策、健康百科、医疗资源、数据资源等内容作答；相关功能以顶部可点击提示展示。</p>
      </div>
      <div class="toolbar">
        <el-button v-if="auth.isLoggedIn" @click="startNewChat">新对话</el-button>
        <el-button v-if="auth.isLoggedIn" @click="openHistory">历史对话</el-button>
        <el-button :disabled="!messages.length" @click="clearCurrentChat">清空当前</el-button>
      </div>
    </div>

    <div ref="chatBoxRef" class="chat-box">
      <el-empty v-if="!messages.length" description="开始提问吧" :image-size="72" />
      <div v-for="(m, i) in messages" :key="i" :class="['msg', m.role]">
        <template v-if="m.role === 'user'">
          <strong>我：</strong>
          <span class="msg-text">{{ m.text }}</span>
        </template>
        <template v-else>
          <div v-if="m.actions?.length" class="feature-tip">
            <div class="feature-tip-head">功能提示</div>
            <p class="feature-tip-desc">根据你的问题，可先打开对应功能：</p>
            <div class="feature-tip-btns">
              <button
                v-for="(a, j) in m.actions"
                :key="j"
                type="button"
                class="action-btn"
                @click="goFeature(a.path)"
              >
                <span class="action-label">{{ a.label }}</span>
                <span v-if="a.tip" class="action-tip">{{ a.tip }}</span>
              </button>
            </div>
          </div>
          <div class="assistant-body">
            <strong>助手：</strong>
            <span class="msg-text">{{ m.text }}</span>
            <div v-if="m.refs?.length" class="refs">
              <span class="refs-label">检索到的知识源：</span>
              <a v-for="(r, j) in m.refs" :key="j" :href="resolveUrl(r.url)" target="_blank" rel="noopener" class="ref-link">
                {{ r.title }}
              </a>
            </div>
          </div>
        </template>
      </div>
    </div>

    <el-input
      v-model="input"
      type="textarea"
      :rows="3"
      placeholder="例如：头晕还有些看不清，发热，这是怎么了？"
      @keydown="onKeydown"
    />
    <el-button type="primary" style="margin-top:12px" :loading="loading" @click="send">发送</el-button>

    <el-drawer v-model="historyVisible" title="历史对话" size="360px">
      <div v-if="!auth.isLoggedIn" class="history-login">
        <p>登录后可同步并查看过往对话</p>
        <el-button type="primary" @click="auth.openAuthDialog('login')">去登录</el-button>
      </div>
      <template v-else>
        <el-button type="primary" plain style="width:100%;margin-bottom:12px" @click="startNewChat">开启新对话</el-button>
        <el-skeleton v-if="historyLoading" :rows="6" animated />
        <el-empty v-else-if="!sessions.length" description="暂无历史对话" />
        <div v-else class="session-list">
          <button
            v-for="s in sessions"
            :key="s.sessionId"
            type="button"
            class="session-item"
            :class="{ active: s.sessionId === sessionId }"
            @click="openSession(s.sessionId)"
          >
            <strong>{{ s.title }}</strong>
            <span>{{ formatTime(s.updatedAt) }} · {{ s.messageCount }} 条</span>
          </button>
        </div>
      </template>
    </el-drawer>
  </div>
</template>

<script setup>
import { nextTick, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { portalApi } from '../../api'
import { usePortalAuthStore } from '../../stores/portalAuth'

const STORAGE_KEY = 'aiChatLocalState'

const route = useRoute()
const router = useRouter()
const auth = usePortalAuthStore()

const input = ref('')
const loading = ref(false)
const sessionId = ref('')
const messages = ref([])
const chatBoxRef = ref(null)
const historyVisible = ref(false)
const historyLoading = ref(false)
const sessions = ref([])

const resolveUrl = (url) => {
  if (!url) return '#'
  if (url.startsWith('http://') || url.startsWith('https://')) return url
  return url.startsWith('/') ? url : `/${url}`
}

const goFeature = (path) => {
  if (!path) return
  if (path.startsWith('http://') || path.startsWith('https://')) {
    window.open(path, '_blank', 'noopener')
    return
  }
  router.push(path.startsWith('/') ? path : `/${path}`)
}

const onKeydown = (e) => {
  if (e.key !== 'Enter') return
  if (!e.shiftKey) {
    e.preventDefault()
    send()
  }
}

const scrollToBottom = async () => {
  await nextTick()
  const el = chatBoxRef.value
  if (el) el.scrollTop = el.scrollHeight
}

const persistLocal = () => {
  const payload = {
    sessionId: sessionId.value,
    messages: messages.value
  }
  sessionStorage.setItem(STORAGE_KEY, JSON.stringify(payload))
}

const restoreLocal = () => {
  try {
    const raw = sessionStorage.getItem(STORAGE_KEY)
    if (!raw) return false
    const data = JSON.parse(raw)
    sessionId.value = data.sessionId || ''
    messages.value = Array.isArray(data.messages) ? data.messages : []
    return messages.value.length > 0
  } catch {
    return false
  }
}

const formatTime = (value) => {
  if (!value) return ''
  const text = String(value).replace('T', ' ')
  return text.slice(0, 16)
}

const loadSessions = async () => {
  if (!auth.isLoggedIn) {
    sessions.value = []
    return
  }
  historyLoading.value = true
  try {
    const res = await portalApi.aiSessions()
    sessions.value = res.data || []
  } catch (e) {
    sessions.value = []
    ElMessage.error(e.message || '加载历史失败')
  } finally {
    historyLoading.value = false
  }
}

const openHistory = async () => {
  historyVisible.value = true
  if (auth.isLoggedIn) await loadSessions()
}

const startNewChat = () => {
  sessionId.value = ''
  messages.value = []
  persistLocal()
  historyVisible.value = false
  router.replace({ path: '/ai' })
}

const clearCurrentChat = async () => {
  try {
    await ElMessageBox.confirm('确定清空当前对话内容？', '清空对话', { type: 'warning' })
  } catch {
    return
  }
  const current = sessionId.value
  messages.value = []
  if (auth.isLoggedIn && current) {
    try {
      await portalApi.aiDeleteSession(current)
      await loadSessions()
    } catch {
      // ignore delete errors for guest-created ids
    }
  }
  sessionId.value = ''
  persistLocal()
  ElMessage.success('已清空')
}

const openSession = async (id) => {
  if (!auth.isLoggedIn) return
  historyLoading.value = true
  try {
    const res = await portalApi.aiSessionMessages(id)
    sessionId.value = id
    messages.value = (res.data || []).map((m) => ({
      role: m.role,
      text: m.message,
      refs: [],
      actions: []
    }))
    persistLocal()
    historyVisible.value = false
    router.replace({ path: '/ai', query: { session: id } })
    await scrollToBottom()
  } catch (e) {
    ElMessage.error(e.message || '打开对话失败')
  } finally {
    historyLoading.value = false
  }
}

const send = async () => {
  if (loading.value || !input.value.trim()) return
  const q = input.value.trim()
  messages.value.push({ role: 'user', text: q })
  input.value = ''
  loading.value = true
  persistLocal()
  await scrollToBottom()
  try {
    const res = await portalApi.aiChat({ message: q, sessionId: sessionId.value })
    sessionId.value = res.data.sessionId
    messages.value.push({
      role: 'assistant',
      text: res.data.answer,
      refs: res.data.references || [],
      actions: res.data.actions || []
    })
    persistLocal()
    if (auth.isLoggedIn) await loadSessions()
  } catch (e) {
    messages.value.push({ role: 'assistant', text: e.message })
    persistLocal()
  } finally {
    loading.value = false
    await scrollToBottom()
  }
}

const bootstrap = async () => {
  if (route.query.new === '1') {
    startNewChat()
    return
  }
  if (route.query.history === '1') {
    restoreLocal()
    historyVisible.value = true
    if (auth.isLoggedIn) await loadSessions()
    return
  }
  const qSession = route.query.session
  if (auth.isLoggedIn && qSession) {
    await openSession(String(qSession))
    return
  }
  restoreLocal()
  await scrollToBottom()
}

onMounted(bootstrap)

watch(() => route.fullPath, () => {
  if (route.path === '/ai') bootstrap()
})

watch(() => auth.isLoggedIn, async (loggedIn) => {
  if (loggedIn) await loadSessions()
})
</script>

<style scoped>
.page { padding: 20px; background: #fff; margin: 20px auto; border-radius: 8px; max-width: 800px; }
.page-head { display: flex; justify-content: space-between; gap: 12px; align-items: flex-start; flex-wrap: wrap; }
.page-head h2 { margin: 0 0 6px; }
.tip { color: #888; font-size: 13px; line-height: 1.6; margin: 0; }
.toolbar { display: flex; gap: 8px; flex-wrap: wrap; }
.chat-box { min-height: 220px; max-height: 480px; overflow-y: auto; border: 1px solid #eee; padding: 12px; margin: 16px 0; border-radius: 8px; }
.msg { margin-bottom: 16px; text-align: left; }
.msg.user { text-align: right; }
.msg-text { white-space: pre-wrap; }
.feature-tip {
  background: linear-gradient(180deg, #f3f9ff 0%, #eef6fd 100%);
  border: 1px solid #c5dff3;
  border-radius: 10px;
  padding: 12px 14px;
  margin-bottom: 10px;
  box-shadow: 0 4px 14px rgba(26, 111, 181, 0.08);
}
.feature-tip-head {
  display: inline-block;
  font-size: 12px;
  font-weight: 600;
  color: #fff;
  background: #1a6fb5;
  border-radius: 4px;
  padding: 2px 8px;
  margin-bottom: 8px;
}
.feature-tip-desc { margin: 0 0 10px; color: #4a667a; font-size: 13px; }
.feature-tip-btns { display: flex; flex-direction: column; gap: 8px; }
.action-btn {
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
  width: 100%;
  text-align: left;
  border: 1px solid #9ec9ea;
  background: #fff;
  color: #1a6fb5;
  border-radius: 8px;
  padding: 10px 12px;
  cursor: pointer;
}
.action-btn:hover { background: #f7fbff; border-color: #1a6fb5; }
.action-label { font-size: 14px; font-weight: 600; }
.action-tip { font-size: 12px; color: #6b8496; line-height: 1.45; }
.refs { margin-top: 8px; font-size: 12px; }
.refs-label { color: #666; margin-right: 6px; }
.ref-link { display: inline-block; margin: 2px 8px 2px 0; color: #1a6fb5; text-decoration: none; }
.session-list { display: flex; flex-direction: column; gap: 8px; }
.session-item {
  text-align: left;
  border: 1px solid #e8eef3;
  background: #fff;
  border-radius: 8px;
  padding: 10px 12px;
  cursor: pointer;
}
.session-item:hover, .session-item.active { border-color: #1a6fb5; background: #f5faff; }
.session-item strong, .session-item span { display: block; }
.session-item span { margin-top: 4px; color: #888; font-size: 12px; }
.history-login { text-align: center; padding: 24px 8px; color: #666; }
</style>
