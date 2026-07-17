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

    // 生成完整日期轴（确保每天都有刻度，无数据补 0）
    const days = buildDateRange(trendDays.value)

    // 按 appKey 分组，用 day→count 映射保证数据与 x 轴对齐
    const groupMap = {}
    data.forEach(item => {
      const key = item.appKey || 'unknown'
      if (!groupMap[key]) groupMap[key] = { name: item.appName || key, counts: {} }
      // day 可能是 "2026-07-15" 或 "2026-07-15T00:00:00" 格式，统一截取前 10 位
      const dayStr = String(item.day).slice(0, 10)
      groupMap[key].counts[dayStr] = (groupMap[key].counts[dayStr] || 0) + Number(item.count)
    })

    const series = Object.values(groupMap).map(g => ({
      name: g.name,
      type: 'line',
      smooth: true,
      data: days.map(d => g.counts[d] || 0)
    }))

    await nextTick()
    if (!trendChart) {
      trendChart = echarts.init(trendChartRef.value)
    }
    trendChart.setOption({
      tooltip: { trigger: 'axis' },
      legend: { type: 'scroll', bottom: 0 },
      xAxis: {
        type: 'category',
        data: days,
        axisLabel: {
          rotate: 45,
          // 只显示 MM-DD，超过 14 天时自动间隔
          formatter: v => v.slice(5),
          interval: trendDays.value <= 7 ? 0 : trendDays.value <= 30 ? 2 : 6
        }
      },
      yAxis: { type: 'value' },
      grid: { left: 50, right: 20, bottom: 60 },
      series
    }, true)
    trendChart.resize()
  } catch (_) { /* ignore */ }
}

/** 生成近 N 天的日期数组 [YYYY-MM-DD, ...] */
function buildDateRange(n) {
  const result = []
  const today = new Date()
  for (let i = n - 1; i >= 0; i--) {
    const d = new Date(today)
    d.setDate(d.getDate() - i)
    result.push(d.toISOString().slice(0, 10))
  }
  return result
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
