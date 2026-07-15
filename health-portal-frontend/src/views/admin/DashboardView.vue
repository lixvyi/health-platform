<template>
  <div class="dashboard-page">
    <div class="page-heading">
      <div>
        <h2>数据看板</h2>
        <p>统一查看门户内容、数据资源池和数据治理状态</p>
      </div>
    </div>

    <el-tabs v-model="activeTab" class="dashboard-tabs" @tab-change="handleTabChange">
      <el-tab-pane label="门户统计" name="stats">
        <el-alert
          v-if="loadError"
          class="load-error"
          type="warning"
          :closable="false"
          show-icon
          title="统计数据暂时无法加载"
          :description="loadError"
        />
        <el-row :gutter="16" v-loading="loading">
          <el-col :xs="24" :sm="12" :lg="6" v-for="(v, k) in stats" :key="k">
            <el-card class="stat-card">
              <div class="stat-icon">
                <el-icon><component :is="statMeta[k]?.icon || DataLine" /></el-icon>
              </div>
              <div>
                <div class="stat">{{ statMeta[k]?.label || labels[k] || k }}</div>
                <div class="num">{{ v }}</div>
              </div>
            </el-card>
          </el-col>
        </el-row>
        <div ref="chartRef" class="content-chart"></div>

        <el-alert class="notice" type="info" :closable="false" show-icon title="数据说明">
          本看板统计的是<strong>本门户 CMS 内容数量</strong>（新闻、公告等），非国家统计局数据。
          开放统计数据请见公众端
          <el-button type="primary" link @click="openData">数据资源目录</el-button>
          ，引用国家统计局数据须遵守
          <el-button type="primary" link @click="openAgreement">用户使用协议</el-button>。
        </el-alert>
      </el-tab-pane>
      <el-tab-pane label="资源池概览" name="pool" lazy>
        <DataPoolView embedded />
      </el-tab-pane>
      <el-tab-pane label="数据治理" name="governance" lazy>
        <DataGovernanceView embedded />
      </el-tab-pane>
      <el-tab-pane label="API用量" name="api" lazy>
        <ApiUsageView />
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import * as echarts from 'echarts'
import { Bell, Collection, DataAnalysis, DataLine, Document, Grid } from '@element-plus/icons-vue'
import { adminApi } from '../../api'
import DataPoolView from '../portal/DataPoolView.vue'
import DataGovernanceView from '../portal/DataGovernanceView.vue'
import ApiUsageView from './ApiUsageView.vue'

const route = useRoute()
const router = useRouter()
const validTabs = new Set(['stats', 'pool', 'governance', 'api'])
const initialTab = typeof route.query.tab === 'string' && validTabs.has(route.query.tab) ? route.query.tab : 'stats'
const activeTab = ref(initialTab)
const stats = ref({})
const chartRef = ref()
const loading = ref(false)
const loadError = ref('')
const statsLoaded = ref(false)
const labels = { NEWS: '新闻', NOTICE: '公告', POLICY: '政策', KNOWLEDGE: '知识库', APP: '应用' }
const statMeta = {
  NEWS: { label: '新闻', icon: Document },
  NOTICE: { label: '公告', icon: Bell },
  POLICY: { label: '政策', icon: Collection },
  KNOWLEDGE: { label: '知识库', icon: DataAnalysis },
  APP: { label: '应用', icon: Grid }
}
let chart

const openData = () => window.open('/data', '_blank')
const openAgreement = () => window.open('/data-agreement', '_blank')

const renderChart = (data) => {
  if (!chartRef.value) return
  chart = chart || echarts.init(chartRef.value)
  chart.setOption({
    title: { text: '门户内容统计' },
    tooltip: {},
    xAxis: { type: 'category', data: Object.keys(data).map(k => labels[k] || k) },
    yAxis: { type: 'value' },
    series: [{ type: 'bar', data: Object.values(data), itemStyle: { color: '#1a6fb5' } }]
  })
}

const loadStats = async () => {
  if (statsLoaded.value) {
    await nextTick()
    renderChart(stats.value)
    chart?.resize()
    return
  }
  loading.value = true
  loadError.value = ''
  try {
    const res = await adminApi.stats()
    const data = res.data || {}
    stats.value = data
    statsLoaded.value = true
    await nextTick()
    renderChart(data)
  } catch (error) {
    loadError.value = error?.response?.data?.message || error?.message || '请确认后端服务和数据库连接正常。'
  } finally {
    loading.value = false
  }
}

const handleTabChange = async (name) => {
  await router.replace({ query: { ...route.query, tab: name === 'stats' ? undefined : name } })
  if (name === 'stats') await loadStats()
}

watch(() => route.query.tab, (value) => {
  const nextTab = typeof value === 'string' && validTabs.has(value) ? value : 'stats'
  if (nextTab === activeTab.value) return
  activeTab.value = nextTab
  if (nextTab === 'stats') loadStats()
})

onMounted(() => {
  if (activeTab.value === 'stats') loadStats()
})

onBeforeUnmount(() => {
  if (chart) {
    chart.dispose()
  }
})
</script>

<style scoped>
.dashboard-page { min-width: 0; }
.page-heading { display: flex; align-items: flex-start; justify-content: space-between; margin-bottom: 10px; }
.page-heading h2 { margin: 0; color: #203943; }
.page-heading p { margin: 7px 0 0; color: #7b8991; font-size: 13px; }
.dashboard-tabs { min-width: 0; }
.stat-card :deep(.el-card__body) { display: flex; align-items: center; gap: 14px; min-height: 88px; }
.stat-icon { width: 44px; height: 44px; border-radius: 8px; background: #eaf4ff; color: #1a6fb5; display: flex; align-items: center; justify-content: center; font-size: 24px; }
.stat { color: #888; font-size: 13px; }
.num { font-size: 28px; font-weight: 700; color: #1a6fb5; margin-top: 8px; }
.content-chart { height: 360px; margin-top: 24px; background: #fff; padding: 12px; border-radius: 8px; }
.load-error { margin-bottom: 16px; }
.notice { margin-top: 20px; }
</style>
