<template>
  <div class="container page">
    <h2>API 服务中心</h2>
    <p class="lead">浏览接口文档无需登录。科研人员身份认证通过后，可测试调用 API 并复制返回结果。</p>

    <el-alert v-if="store.isLoggedIn && !store.isResearcher" type="warning" :closable="false" show-icon
      title="普通用户可浏览 API 文档。调用 API 需先完成科研人员身份认证（在「我的申请」提交）。" style="margin-bottom:16px" />

    <el-row :gutter="16">
      <el-col :span="10">
        <div class="block">
          <h3>接口列表</h3>
          <el-table :data="apis" stripe highlight-current-row @row-click="selectApi">
            <el-table-column prop="name" label="接口名称" min-width="140" />
            <el-table-column prop="method" label="方法" width="70" />
          </el-table>
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

          <div class="actions">
            <el-button type="success" :disabled="!store.isResearcher" :loading="invoking" @click="handleInvoke">
              测试调用
            </el-button>
            <span v-if="!store.isLoggedIn" class="hint">登录后可操作</span>
            <span v-else-if="!store.isResearcher" class="hint">需科研人员身份</span>
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

const handleInvoke = () => {
  store.requireAuth(async () => {
    if (!store.isResearcher) {
      ElMessage.warning('API 调用仅限科研人员，请先在「我的申请」完成身份认证')
      return
    }
    invoking.value = true
    try {
      const res = await portalUserApi.invokeApi(active.value.id)
      invokeResult.value = res.data
    } catch (e) {
      ElMessage.error(e.message)
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
.block { background: #fff; border-radius: 8px; padding: 20px; margin-bottom: 16px; min-height: 400px; }
.detail-block { display: flex; flex-direction: column; }
.block h3 { color: #1a6fb5; margin: 0 0 12px; }
.meta pre, .result-pre { margin: 0; font-size: 12px; white-space: pre-wrap; word-break: break-all; }
.actions { margin-top: 16px; display: flex; gap: 12px; align-items: center; flex-wrap: wrap; }
.hint { color: #909399; font-size: 13px; }
code { background: #f5f5f5; padding: 2px 6px; border-radius: 4px; }
.result-panel {
  margin-top: 12px;
  padding: 12px;
  background: #f8fafc;
  border: 1px solid #e4e7ed;
  border-radius: 6px;
  flex: 1;
  display: flex;
  flex-direction: column;
}
.result-pre {
  flex: 1;
  max-height: 280px;
  overflow: auto;
  color: #303133;
}
.result-footer {
  display: flex;
  justify-content: flex-end;
  margin-top: 10px;
}
</style>
