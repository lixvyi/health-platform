<template>
  <div class="container page">
    <div class="page-heading">
      <h2>我的 API 密钥</h2>
      <el-button type="primary" @click="showApplyDialog">+ 申请新密钥</el-button>
    </div>
    <p class="lead">申请后需管理员审核通过方可使用。AppSecret 仅在创建时显示一次，请立即保存。</p>

    <el-table :data="keys" stripe v-loading="loading">
      <el-table-column prop="appName" label="应用名称" width="160" />
      <el-table-column label="AppKey" min-width="250">
        <template #default="{ row }">
          <code class="key-text">{{ row.appKey }}</code>
        </template>
      </el-table-column>
      <el-table-column label="状态" width="80">
        <template #default="{ row }">
          <el-tag v-if="row.approvedAt" type="success" size="small">已启用</el-tag>
          <el-tag v-else type="warning" size="small">待审批</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="tier" label="套餐" width="70" align="center" />
      <el-table-column label="日配额" width="90" align="center">
        <template #default="{ row }">{{ row.dailyQuota?.toLocaleString() }}</template>
      </el-table-column>
      <el-table-column label="创建时间" width="100">
        <template #default="{ row }">{{ row.createdAt?.slice(0, 10) }}</template>
      </el-table-column>
      <el-table-column label="操作" width="100">
        <template #default="{ row }">
          <el-button v-if="row.approvedAt" link type="primary" @click="showGuide(row)">调用指引</el-button>
        </template>
      </el-table-column>
    </el-table>

    <el-empty v-if="!loading && keys.length === 0" description="暂无 API 密钥，点击上方按钮申请" :image-size="60" />

    <!-- 申请密钥对话框 -->
    <el-dialog v-model="applyVisible" title="申请 API 密钥" width="500px">
      <el-form ref="formRef" :model="form" :rules="rules" label-width="100px">
        <el-form-item label="应用名称" prop="appName">
          <el-input v-model="form.appName" placeholder="如：健康数据分析" />
        </el-form-item>
        <el-form-item label="应用描述">
          <el-input v-model="form.description" type="textarea" :rows="2" placeholder="简要说明使用场景" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="applyVisible = false">取消</el-button>
        <el-button type="primary" :loading="submitting" @click="doApply">提交申请</el-button>
      </template>
    </el-dialog>

    <!-- 申请成功 → 展示密钥 -->
    <el-dialog v-model="resultVisible" title="密钥已创建，待审批" width="520px" :close-on-click-modal="false">
      <el-alert type="warning" :closable="false" show-icon title="请立即保存 AppSecret！关闭后不再显示。" style="margin-bottom:16px" />
      <el-descriptions :column="1" border>
        <el-descriptions-item label="AppKey">
          <code style="user-select:all">{{ result.appKey }}</code>
        </el-descriptions-item>
        <el-descriptions-item label="AppSecret">
          <code style="user-select:all;word-break:break-all">{{ result.appSecret }}</code>
        </el-descriptions-item>
      </el-descriptions>
      <template #footer>
        <el-button type="primary" @click="resultVisible = false; load()">我已保存</el-button>
      </template>
    </el-dialog>

    <!-- 调用指引 -->
    <el-dialog v-model="guideVisible" title="API 调用指引" width="560px">
      <div v-if="guideKey">
        <el-alert type="info" :closable="false" show-icon style="margin-bottom:16px">
          调用外部 API 时，请在请求 Header 中传递以下三个字段：
        </el-alert>
        <el-descriptions :column="1" border style="margin-bottom:16px">
          <el-descriptions-item label="X-App-Key"><code class="key-text">{{ guideKey.appKey }}</code></el-descriptions-item>
          <el-descriptions-item label="X-Timestamp">当前 Unix 毫秒时间戳</el-descriptions-item>
          <el-descriptions-item label="X-Sign">HMAC-SHA256 签名</el-descriptions-item>
        </el-descriptions>
        <h4 style="margin:0 0 8px">签名示例（JavaScript）</h4>
        <pre class="code-block">const signPayload = appKey + timestamp + 'GET' + path + query;
const sign = CryptoJS.HmacSHA256(signPayload, 'AppSecret')
  .toString(CryptoJS.enc.Hex);</pre>
        <p style="color:#999;font-size:12px">可用服务：<router-link to="/api-services">API 服务目录</router-link></p>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { portalUserApi } from '../../api/portalUser'

const keys = ref([])
const loading = ref(false)
const submitting = ref(false)

// 申请
const applyVisible = ref(false)
const formRef = ref()
const form = ref({ appName: '', description: '', tier: 'FREE' })
const rules = { appName: [{ required: true, message: '请输入应用名称', trigger: 'blur' }] }
const resultVisible = ref(false)
const result = ref({})

// 指引
const guideVisible = ref(false)
const guideKey = ref(null)

const load = async () => {
  loading.value = true
  try {
    const res = await portalUserApi.myApiKeys()
    keys.value = res.data || []
  } catch (_) { /* ignore */ } finally { loading.value = false }
}

const showApplyDialog = () => {
  form.value = { appName: '', description: '', tier: 'FREE' }
  applyVisible.value = true
}

const doApply = async () => {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    const res = await portalUserApi.applyApiKey(form.value)
    result.value = res.data || {}
    applyVisible.value = false
    resultVisible.value = true
    ElMessage.success('申请已提交，等待管理员审批')
  } catch (e) {
    ElMessage.error('申请失败: ' + (e.message || ''))
  } finally { submitting.value = false }
}

const showGuide = (row) => {
  guideKey.value = row
  guideVisible.value = true
}

onMounted(load)
</script>

<style scoped>
.page { padding: 24px; max-width: 1000px; margin: 20px auto; }
.page-heading { display: flex; align-items: center; justify-content: space-between; }
.lead { color: #555; line-height: 1.8; margin: 16px 0 20px; }
.key-text { font-size: 13px; user-select: all; background: #f5f7fa; padding: 2px 6px; border-radius: 4px; }
.code-block { background: #1e1e1e; color: #d4d4d4; padding: 16px; border-radius: 8px; font-size: 13px; line-height: 1.7; overflow-x: auto; white-space: pre-wrap; }
</style>
