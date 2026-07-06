<template>
  <div class="container page">
    <h2>AI 健康知识问答</h2>
    <p class="tip">结合门户政策库、健康知识、开放数据与权威网址检索，由 DeepSeek 生成专业回答（不提供医疗诊断）</p>
    <div class="chat-box">
      <div v-for="(m, i) in messages" :key="i" :class="['msg', m.role]">
        <strong>{{ m.role === 'user' ? '我' : '助手' }}：</strong>
        <span class="msg-text">{{ m.text }}</span>
        <div v-if="m.refs?.length" class="refs">
          <span class="refs-label">检索到的知识源：</span>
          <a v-for="(r, j) in m.refs" :key="j" :href="resolveUrl(r.url)" target="_blank" rel="noopener" class="ref-link">
            {{ r.title }}
          </a>
        </div>
      </div>
    </div>
    <el-input v-model="input" type="textarea" :rows="3" placeholder="例如：健康中国2030有什么要求？上海医疗机构数据在哪查？" />
    <el-button type="primary" style="margin-top:12px" :loading="loading" @click="send">发送</el-button>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { portalApi } from '../../api'

const input = ref('')
const loading = ref(false)
const sessionId = ref('')
const messages = ref([])

const resolveUrl = (url) => {
  if (!url) return '#'
  if (url.startsWith('http://') || url.startsWith('https://')) return url
  return url.startsWith('/') ? url : `/${url}`
}

const send = async () => {
  if (!input.value.trim()) return
  const q = input.value.trim()
  messages.value.push({ role: 'user', text: q })
  input.value = ''
  loading.value = true
  try {
    const res = await portalApi.aiChat({ message: q, sessionId: sessionId.value })
    sessionId.value = res.data.sessionId
    messages.value.push({
      role: 'assistant',
      text: res.data.answer,
      refs: res.data.references || []
    })
  } catch (e) {
    messages.value.push({ role: 'assistant', text: e.message })
  } finally {
    loading.value = false
  }
}
</script>

<style scoped>
.page { padding: 20px; background: #fff; margin: 20px auto; border-radius: 8px; max-width: 800px; }
.tip { color: #888; font-size: 13px; line-height: 1.6; }
.chat-box { min-height: 200px; max-height: 400px; overflow-y: auto; border: 1px solid #eee; padding: 12px; margin: 16px 0; border-radius: 8px; }
.msg { margin-bottom: 12px; }
.msg.user { text-align: right; }
.msg-text { white-space: pre-wrap; }
.refs { margin-top: 8px; font-size: 12px; text-align: left; }
.refs-label { color: #666; margin-right: 6px; }
.ref-link { display: inline-block; margin: 2px 8px 2px 0; color: #1a6fb5; text-decoration: none; }
.ref-link:hover { text-decoration: underline; }
</style>
