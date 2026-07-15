<template>
  <div class="container page">
    <h2>API 服务中心</h2>
    <p class="lead">
      提供实时数据查询接口，需配置 AppKey 后通过外部端点调用。科研人员可在
      <router-link to="/my-api-keys">我的 API 密钥</router-link> 中申请。
    </p>

    <el-alert v-if="store.isLoggedIn && !store.isResearcher" type="warning" :closable="false" show-icon
      title="普通用户可浏览 API 文档。调用 API 需先完成科研人员身份认证。" style="margin-bottom:16px" />

    <el-alert v-else-if="store.isResearcher" type="info" :closable="false" show-icon style="margin-bottom:16px">
      <template #title>
        您已通过科研人员认证。调用实时数据 API 需先
        <router-link to="/my-api-keys" style="color:#409eff;font-weight:600">申请 AppKey</router-link>，
        然后在请求 Header 中携带 X-App-Key / X-Timestamp / X-Sign。
      </template>
    </el-alert>

    <el-row :gutter="16">
      <el-col :span="10">
        <div class="block">
          <h3>接口列表</h3>
          <el-table :data="apis" stripe highlight-current-row @row-click="selectApi">
            <el-table-column prop="name" label="接口名称" min-width="200" />
            <el-table-column prop="method" label="方法" width="60" />
          </el-table>
          <el-empty v-if="apis.length === 0" description="暂无 API 服务" :image-size="50" />
        </div>
      </el-col>

      <el-col :span="14">
        <div class="block detail-block" v-if="active">
          <h3>{{ active.name }}</h3>
          <p>{{ active.description }}</p>

          <el-descriptions :column="1" border size="small" class="meta">
            <el-descriptions-item label="请求方式">{{ active.method }}</el-descriptions-item>
            <el-descriptions-item label="路径"><code>{{ active.path }}</code></el-descriptions-item>
            <el-descriptions-item label="参数示例"><pre>{{ formatJson(active.paramsJson) }}</pre></el-descriptions-item>
            <el-descriptions-item label="返回示例"><pre>{{ formatJson(active.responseExample) }}</pre></el-descriptions-item>
          </el-descriptions>

          <div class="call-guide">
            <h4>外部调用方式</h4>
            <p class="guide-text">需要有效的 AppKey，在请求 Header 中传递鉴权信息：</p>
            <pre class="code-block">GET {{ active.path }}
X-App-Key: your_app_key
X-Timestamp: {{ Date.now() }}
X-Sign: HMAC-SHA256(appKey + timestamp + method + path + query, secret)</pre>
            <div class="guide-actions" style="display:flex;gap:8px;flex-wrap:wrap">
              <el-button v-if="store.isResearcher" type="success" size="small" :loading="invoking" @click="handleTest">
                测试实时数据
              </el-button>
              <el-button v-if="store.isResearcher" type="primary" size="small" @click="$router.push('/my-api-keys')">
                去申请 AppKey
              </el-button>
            </div>
          </div>

          <div v-if="invokeResult" class="result-panel">
            <pre class="result-pre">{{ resultText }}</pre>
            <div class="result-footer">
              <el-button type="primary" size="small" @click="copyResult">复制</el-button>
            </div>
          </div>
        </div>
        <el-empty v-else description="请选择左侧接口" />
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { portalUserApi } from '../../api/portalUser'
import { usePortalAuthStore } from '../../stores/portalAuth'

const store = usePortalAuthStore()
const apis = ref([])
const active = ref(null)
const invoking = ref(false)
const invokeResult = ref(null)

const resultText = computed(() =>
  invokeResult.value ? JSON.stringify(invokeResult.value, null, 2) : ''
)

const formatJson = (s) => {
  try { return JSON.stringify(JSON.parse(s), null, 2) } catch { return s || '' }
}

const selectApi = (row) => {
  active.value = row
  invokeResult.value = null
}

const handleTest = () => {
  store.requireAuth(async () => {
    if (!store.isResearcher) {
      ElMessage.warning('仅科研人员可测试 API')
      return
    }
    invoking.value = true
    try {
      const path = active.value.path
      const serviceCode = path.replace('/api/external/', '')
      const res = await portalUserApi.testExternalApi(serviceCode, {
        page: 1, size: 10
      })
      invokeResult.value = res.data
    } catch (e) {
      ElMessage.error(e.message || '测试失败')
    } finally {
      invoking.value = false
    }
  })
}

const copyResult = async () => {
  if (!resultText.value) return
  try {
    await navigator.clipboard.writeText(resultText.value)
    ElMessage.success('已复制到剪贴板')
  } catch {
    ElMessage.error('复制失败，请手动选择文本复制')
  }
}

onMounted(async () => {
  const res = await portalUserApi.listApis()
  apis.value = res.data || []
  if (apis.value.length) selectApi(apis.value[0])
  if (store.isLoggedIn) await store.fetchMe()
})
</script>

<style scoped>
.page { padding: 24px; max-width: 1100px; margin: 20px auto; }
.lead { color: #555; line-height: 1.8; margin-bottom: 16px; }
.lead a { color: #409eff; font-weight: 600; }
.block { background: #fff; border-radius: 8px; padding: 20px; margin-bottom: 16px; min-height: 400px; }
.detail-block { display: flex; flex-direction: column; }
.block h3 { color: #1a6fb5; margin: 0 0 12px; }
.meta pre, .result-pre { margin: 0; font-size: 12px; white-space: pre-wrap; word-break: break-all; }
code { background: #f5f5f5; padding: 2px 6px; border-radius: 4px; }
.result-panel {
  margin-top: 12px; padding: 12px;
  background: #f8fafc; border: 1px solid #e4e7ed; border-radius: 6px;
  flex: 1; display: flex; flex-direction: column;
}
.result-pre { flex: 1; max-height: 280px; overflow: auto; color: #303133; }
.result-footer { display: flex; justify-content: flex-end; margin-top: 10px; }
.call-guide {
  margin-top: 16px; padding: 16px; background: #f0f9ff; border-radius: 8px;
  border: 1px solid #bee3f8;
}
.call-guide h4 { margin: 0 0 8px; color: #1a6fb5; }
.guide-text { color: #555; font-size: 13px; margin-bottom: 8px; }
.code-block {
  background: #1e1e1e; color: #d4d4d4; padding: 14px; border-radius: 6px;
  font-size: 13px; line-height: 1.6; overflow-x: auto; white-space: pre-wrap;
}
.guide-actions { margin-top: 12px; }
</style>
