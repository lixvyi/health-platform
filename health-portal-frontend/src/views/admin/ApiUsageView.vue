<template>
  <div class="api-usage">
    <el-row :gutter="16" v-loading="loading">
      <el-col :xs="24" :sm="8" v-for="(v, k) in overview" :key="k">
        <el-card class="stat-card">
          <div class="stat">{{ overviewMeta[k]?.label || k }}</div>
          <div class="num">{{ v }}</div>
        </el-card>
      </el-col>
    </el-row>

    <el-card class="chart-card" v-loading="loading">
      <template #header>
        <div class="chart-header">
          <span>近30天调用趋势</span>
          <el-radio-group v-model="trendDays" size="small" @change="loadTrend">
            <el-radio-button :value="7">7天</el-radio-button>
            <el-radio-button :value="30">30天</el-radio-button>
            <el-radio-button :value="90">90天</el-radio-button>
          </el-radio-group>
        </div>
      </template>
      <div ref="trendChartRef" class="chart"></div>
    </el-card>

    <el-card class="chart-card">
      <template #header>
        <span>今日各 AppKey 调用排行</span>
      </template>
      <el-table :data="todayData" stripe v-loading="loading" @row-click="handleRowClick">
        <el-table-column prop="appName" label="应用名称" />
        <el-table-column prop="appKey" label="AppKey" width="300" />
        <el-table-column prop="count" label="调用次数" width="120" align="center" sortable />
      </el-table>
    </el-card>
  </div>
</template>

<script setup>
import { nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import * as echarts from 'echarts'
import { adminApi } from '../../api'

const loading = ref(false)
const overview = ref({})
const todayData = ref([])
const trendDays = ref(30)
const trendChartRef = ref()
let trendChart = null

const overviewMeta = {
  appCount: { label: '活跃应用数' },
  totalCalls: { label: '近30天调用总量' }
}

const loadOverview = async () => {
  try {
    const res = await adminApi.apiUsageOverview(30)
    overview.value = res.data || {}
  } catch (_) { /* ignore */ }
}

const loadToday = async () => {
  try {
    const res = await adminApi.apiUsageToday()
    todayData.value = res.data || []
  } catch (_) { /* ignore */ }
}

const loadTrend = async () => {
  try {
    const res = await adminApi.apiUsageTrend(trendDays.value)
    const data = res.data || []

    // 按 appKey 分组构建多条折线
    const seriesMap = {}
    const daysSet = new Set()
    data.forEach(item => {
      const key = item.appKey || 'unknown'
      const name = item.appName || key
      if (!seriesMap[key]) {
        seriesMap[key] = { name, type: 'line', smooth: true, data: [] }
      }
      seriesMap[key].data.push(item.count)
      daysSet.add(item.day)
    })

    const days = Array.from(daysSet).sort()
    const series = Object.values(seriesMap)

    await nextTick()
    if (!trendChart) {
      trendChart = echarts.init(trendChartRef.value)
    }
    trendChart.setOption({
      tooltip: { trigger: 'axis' },
      legend: { type: 'scroll', bottom: 0 },
      xAxis: { type: 'category', data: days, axisLabel: { rotate: 45 } },
      yAxis: { type: 'value' },
      grid: { left: 50, right: 20, bottom: 60 },
      series
    })
    trendChart.resize()
  } catch (_) { /* ignore */ }
}

const handleRowClick = (row) => {
  // 点击某个 AppKey 查看其趋势
}

onMounted(async () => {
  loading.value = true
  await Promise.all([loadOverview(), loadToday(), loadTrend()])
  loading.value = false
})

onBeforeUnmount(() => {
  if (trendChart) {
    trendChart.dispose()
    trendChart = null
  }
})
</script>

<style scoped>
.api-usage { min-width: 0; }
.stat-card :deep(.el-card__body) { text-align: center; padding: 20px; }
.stat { color: #888; font-size: 13px; }
.num { font-size: 28px; font-weight: 700; color: #1a6fb5; margin-top: 8px; }
.chart-card { margin-top: 16px; }
.chart-header { display: flex; align-items: center; justify-content: space-between; }
.chart { height: 360px; }
</style>
