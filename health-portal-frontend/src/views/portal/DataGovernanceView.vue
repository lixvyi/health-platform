<template>
  <div class="container governance-page">
    <DataPoolNav />

    <div class="page-heading">
      <div>
        <h2>数据治理看板</h2>
        <p>监控数据采集、质量、新鲜度与异常处理状态</p>
      </div>
      <div class="heading-actions">
        <span class="generated-at">数据更新于 {{ formatDateTime(dashboard.generatedAt) }}</span>
        <el-tooltip content="刷新看板" placement="top">
          <el-button class="icon-button" circle :loading="loading" aria-label="刷新看板" @click="loadDashboard">
            <el-icon><Refresh /></el-icon>
          </el-button>
        </el-tooltip>
      </div>
    </div>

    <el-alert
      v-if="errorMessage"
      class="load-alert"
      type="error"
      :title="errorMessage"
      :closable="false"
      show-icon
    />

    <div v-loading="loading" class="dashboard-content">
      <section class="metrics-grid" aria-label="治理指标概览">
        <div v-for="metric in summaryMetrics" :key="metric.label" class="metric-card" :class="metric.tone">
          <div class="metric-icon"><el-icon><component :is="metric.icon" /></el-icon></div>
          <div class="metric-copy">
            <span>{{ metric.label }}</span>
            <strong>{{ metric.value }}</strong>
            <small>{{ metric.note }}</small>
          </div>
        </div>
      </section>

      <section class="charts-grid">
        <div class="panel trend-panel">
          <div class="panel-head">
            <div>
              <h3>数据总量与新增量趋势</h3>
              <p>最近 14 天入池记录变化</p>
            </div>
            <div class="chart-legend" aria-hidden="true">
              <span><i class="total-dot"></i>数据总量</span>
              <span><i class="added-dot"></i>新增量</span>
            </div>
          </div>
          <div ref="trendChartRef" class="trend-chart"></div>
        </div>

        <div class="panel freshness-panel">
          <div class="panel-head">
            <div>
              <h3>数据新鲜度</h3>
              <p>按各来源更新周期评估</p>
            </div>
          </div>
          <div class="freshness-summary">
            <div class="freshness-score">
              <strong>{{ freshRatio }}%</strong>
              <span>数据源新鲜</span>
            </div>
            <div class="freshness-bars">
              <div v-for="item in freshnessItems" :key="item.key" class="freshness-row">
                <span><i :class="item.className"></i>{{ item.label }}</span>
                <div class="bar-track"><i :class="item.className" :style="{ width: item.percent + '%' }"></i></div>
                <strong>{{ item.value }}</strong>
              </div>
            </div>
          </div>
          <div class="quality-note">
            <el-icon><InfoFilled /></el-icon>
            按最后采集时间和数据源更新频率动态判断
          </div>
        </div>
      </section>

      <section class="panel source-panel">
        <div class="panel-head source-head">
          <div>
            <h3>数据源治理明细</h3>
            <p>共 {{ filteredSources.length }} 个数据源</p>
          </div>
          <div class="filters">
            <el-input v-model="keyword" clearable placeholder="搜索数据源" :prefix-icon="Search" />
            <el-select v-model="freshnessFilter" aria-label="筛选新鲜度">
              <el-option label="全部新鲜度" value="ALL" />
              <el-option label="新鲜" value="FRESH" />
              <el-option label="需关注" value="WARNING" />
              <el-option label="已过期" value="STALE" />
              <el-option label="未评估" value="UNKNOWN" />
            </el-select>
            <el-select v-model="issueFilter" aria-label="筛选异常状态">
              <el-option label="全部处理状态" value="ALL" />
              <el-option label="待处理" value="PENDING" />
              <el-option label="处理中" value="IN_PROGRESS" />
              <el-option label="已处理" value="RESOLVED" />
              <el-option label="无异常" value="NORMAL" />
            </el-select>
          </div>
        </div>

        <el-table :data="filteredSources" stripe class="source-table" empty-text="暂无符合条件的数据源">
          <el-table-column label="数据源" min-width="220" fixed="left">
            <template #default="{ row }">
              <div class="source-name">{{ row.datasetName || row.sourceName }}</div>
              <div class="source-code">{{ row.datasetCode }} · {{ sourceTypeText(row.sourceType) }}</div>
            </template>
          </el-table-column>
          <el-table-column label="最后采集" min-width="150">
            <template #default="{ row }">
              <div>{{ formatDateTime(row.lastCollectedAt) }}</div>
              <span class="cell-note">{{ row.freshnessText || '暂无时间' }}</span>
            </template>
          </el-table-column>
          <el-table-column label="成功率" width="120">
            <template #default="{ row }">
              <strong :class="rateClass(row.successRate)">{{ formatPercent(row.successRate) }}</strong>
              <div class="mini-track"><i :style="{ width: `${row.successRate || 0}%` }"></i></div>
            </template>
          </el-table-column>
          <el-table-column label="数据量 / 新增" min-width="135" align="right">
            <template #default="{ row }">
              <strong>{{ formatNumber(row.totalRecords) }}</strong>
              <div class="cell-note">最近 +{{ formatNumber(row.latestAddedRecords) }}</div>
            </template>
          </el-table-column>
          <el-table-column label="重复 / 缺失" min-width="125" align="right">
            <template #default="{ row }">
              <span>{{ nullablePercent(row.duplicateRatio) }}</span>
              <div class="cell-note">缺失 {{ nullablePercent(row.missingFieldRatio) }}</div>
            </template>
          </el-table-column>
          <el-table-column label="新鲜度" width="105">
            <template #default="{ row }">
              <el-tag size="small" effect="plain" :type="freshnessTag(row.freshnessLevel)">
                {{ freshnessText(row.freshnessLevel) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="来源与许可" min-width="245">
            <template #default="{ row }">
              <div class="origin-name">{{ row.sourceName || '来源未标注' }}</div>
              <div class="license-text">{{ row.licenseName }}</div>
              <a v-if="row.originalUrl" class="source-link" :href="row.originalUrl" target="_blank" rel="noopener noreferrer">
                查看原文 <el-icon><TopRight /></el-icon>
              </a>
              <el-tooltip v-else-if="row.sourceFile" :content="row.sourceFile" placement="top">
                <span class="file-source"><el-icon><Document /></el-icon> 本地来源文件</span>
              </el-tooltip>
              <span v-else class="cell-note">未提供原文链接</span>
            </template>
          </el-table-column>
          <el-table-column label="人工处理" width="105" fixed="right">
            <template #default="{ row }">
              <el-tag size="small" :type="issueTag(row.anomalyStatus)">{{ issueText(row.anomalyStatus) }}</el-tag>
              <div v-if="row.anomalyCount" class="cell-note">{{ row.anomalyCount }} 项异常</div>
            </template>
          </el-table-column>
        </el-table>
      </section>

      <section class="panel issues-panel">
        <div class="panel-head">
          <div>
            <h3>异常数据人工处理记录</h3>
            <p>异常状态由管理端更新，处理结论会在这里留痕</p>
          </div>
          <el-tag :type="openIssues.length ? 'warning' : 'success'" effect="plain">
            {{ openIssues.length }} 项待跟进
          </el-tag>
        </div>
        <el-table v-if="dashboard.issues?.length" :data="dashboard.issues" size="small" class="issues-table">
          <el-table-column label="数据源" prop="sourceName" min-width="180" />
          <el-table-column label="异常类型" width="115">
            <template #default="{ row }">{{ issueTypeText(row.issueType) }}</template>
          </el-table-column>
          <el-table-column label="异常说明" prop="description" min-width="280" />
          <el-table-column label="发现时间" width="150">
            <template #default="{ row }">{{ formatDateTime(row.detectedAt) }}</template>
          </el-table-column>
          <el-table-column label="处理状态" width="105">
            <template #default="{ row }">
              <el-tag size="small" :type="issueTag(row.status)">{{ issueText(row.status) }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="处理备注" min-width="180">
            <template #default="{ row }">{{ row.handlerNote || '—' }}</template>
          </el-table-column>
        </el-table>
        <el-empty v-else description="当前没有异常处理记录" :image-size="72" />
      </section>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import * as echarts from 'echarts'
import {
  CircleCheck, Coin, DataLine, Document, InfoFilled, Refresh, Search,
  SuccessFilled, TopRight, TrendCharts, WarningFilled
} from '@element-plus/icons-vue'
import DataPoolNav from '../../components/DataPoolNav.vue'
import { portalApi } from '../../api'

const loading = ref(false)
const errorMessage = ref('')
const keyword = ref('')
const freshnessFilter = ref('ALL')
const issueFilter = ref('ALL')
const trendChartRef = ref()
const dashboard = ref({
  generatedAt: null,
  summary: {},
  trends: [],
  sources: [],
  issues: [],
  freshnessDistribution: {}
})
let trendChart

const summaryMetrics = computed(() => {
  const summary = dashboard.value.summary || {}
  return [
    { label: '纳管数据源', value: formatNumber(summary.sourceCount), note: `${formatNumber(summary.healthySourceCount)} 个运行健康`, icon: DataLine, tone: 'teal' },
    { label: '数据总量', value: formatNumber(summary.totalRecords), note: `最近一次 +${formatNumber(summary.latestAddedRecords)}`, icon: Coin, tone: 'blue' },
    { label: '平均成功率', value: formatPercent(summary.averageSuccessRate), note: '采集与导入综合', icon: CircleCheck, tone: 'green' },
    { label: '重复数据比例', value: nullablePercent(summary.duplicateRatio), note: '按已评估数据源', icon: TrendCharts, tone: 'orange' },
    { label: '缺失字段比例', value: nullablePercent(summary.missingFieldRatio), note: '未评估来源不计入', icon: SuccessFilled, tone: 'cyan' },
    { label: '待处理异常', value: formatNumber(summary.openIssueCount), note: `${formatNumber(summary.freshSourceCount)} 个数据源新鲜`, icon: WarningFilled, tone: summary.openIssueCount ? 'red' : 'green' }
  ]
})

const filteredSources = computed(() => {
  const query = keyword.value.trim().toLowerCase()
  return (dashboard.value.sources || []).filter((source) => {
    const matchesKeyword = !query || [source.datasetName, source.sourceName, source.datasetCode]
      .filter(Boolean).some(value => value.toLowerCase().includes(query))
    const matchesFreshness = freshnessFilter.value === 'ALL' || source.freshnessLevel === freshnessFilter.value
    const matchesIssue = issueFilter.value === 'ALL' || source.anomalyStatus === issueFilter.value
    return matchesKeyword && matchesFreshness && matchesIssue
  })
})

const openIssues = computed(() => (dashboard.value.issues || [])
  .filter(issue => issue.status === 'PENDING' || issue.status === 'IN_PROGRESS'))

const freshRatio = computed(() => {
  const total = dashboard.value.summary?.sourceCount || 0
  return total ? Math.round((dashboard.value.freshnessDistribution?.FRESH || 0) * 100 / total) : 0
})

const freshnessItems = computed(() => {
  const distribution = dashboard.value.freshnessDistribution || {}
  const total = dashboard.value.summary?.sourceCount || 1
  return [
    { key: 'FRESH', label: '新鲜', value: distribution.FRESH || 0, percent: (distribution.FRESH || 0) * 100 / total, className: 'fresh' },
    { key: 'WARNING', label: '需关注', value: distribution.WARNING || 0, percent: (distribution.WARNING || 0) * 100 / total, className: 'warning' },
    { key: 'STALE', label: '已过期', value: distribution.STALE || 0, percent: (distribution.STALE || 0) * 100 / total, className: 'stale' },
    { key: 'UNKNOWN', label: '未评估', value: distribution.UNKNOWN || 0, percent: (distribution.UNKNOWN || 0) * 100 / total, className: 'unknown' }
  ]
})

const renderTrendChart = () => {
  if (!trendChartRef.value) return
  if (!trendChart) trendChart = echarts.init(trendChartRef.value)
  const trends = dashboard.value.trends || []
  trendChart.setOption({
    animationDuration: 450,
    tooltip: {
      trigger: 'axis',
      backgroundColor: '#17333c',
      borderWidth: 0,
      textStyle: { color: '#fff', fontSize: 12 },
      formatter: (items) => {
        const date = items?.[0]?.axisValueLabel || ''
        return [date, ...items.map(item => `${item.marker}${item.seriesName}：${formatNumber(item.value)}`)].join('<br/>')
      }
    },
    grid: { left: 18, right: 20, top: 20, bottom: 10, containLabel: true },
    xAxis: {
      type: 'category',
      boundaryGap: true,
      data: trends.map(item => item.date.slice(5)),
      axisLine: { lineStyle: { color: '#d9e2e7' } },
      axisTick: { show: false },
      axisLabel: { color: '#7b8991', fontSize: 11, interval: 1 }
    },
    yAxis: [
      {
        type: 'value',
        splitLine: { lineStyle: { color: '#edf1f3' } },
        axisLabel: { color: '#7b8991', formatter: value => compactNumber(value) }
      },
      {
        type: 'value',
        splitLine: { show: false },
        axisLabel: { color: '#7b8991', formatter: value => compactNumber(value) }
      }
    ],
    series: [
      {
        name: '数据总量',
        type: 'line',
        smooth: 0.3,
        symbol: 'circle',
        symbolSize: 6,
        data: trends.map(item => item.totalRecords),
        itemStyle: { color: '#12758a' },
        lineStyle: { width: 3, color: '#12758a' },
        areaStyle: { color: 'rgba(18,117,138,.10)' }
      },
      {
        name: '新增量',
        type: 'bar',
        yAxisIndex: 1,
        barMaxWidth: 16,
        data: trends.map(item => item.addedRecords),
        itemStyle: { color: '#e99b40', borderRadius: [3, 3, 0, 0] }
      }
    ]
  }, true)
}

const loadDashboard = async () => {
  loading.value = true
  errorMessage.value = ''
  try {
    const response = await portalApi.dataPoolGovernance()
    dashboard.value = response.data || dashboard.value
    await nextTick()
    renderTrendChart()
  } catch (error) {
    errorMessage.value = error?.response?.data?.message || '数据治理看板加载失败，请确认后端服务已更新。'
  } finally {
    loading.value = false
  }
}

const formatNumber = value => new Intl.NumberFormat('zh-CN').format(Number(value) || 0)
const formatPercent = value => `${Number(value || 0).toFixed(1)}%`
const nullablePercent = value => value === null || value === undefined ? '未评估' : `${Number(value).toFixed(1)}%`
const compactNumber = value => value >= 10000 ? `${(value / 10000).toFixed(value >= 100000 ? 0 : 1)}万` : value
const formatDateTime = (value) => {
  if (!value) return '暂无记录'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit', hour12: false
  }).format(date)
}

const sourceTypeText = type => ({
  DATABASE: '数据库',
  FILE: '文件',
  CLEANED: '清洗数据',
  CRAWL: '网页采集',
  INTERNET_CRAWL: '网页采集',
  OPEN_DATA: '开放数据',
  ETL: '清洗任务',
  CMS: '内容数据'
}[type] || type || '数据源')
const freshnessText = level => ({ FRESH: '新鲜', WARNING: '需关注', STALE: '已过期', UNKNOWN: '未评估' }[level] || '未评估')
const freshnessTag = level => ({ FRESH: 'success', WARNING: 'warning', STALE: 'danger', UNKNOWN: 'info' }[level] || 'info')
const issueText = status => ({ NORMAL: '无异常', PENDING: '待处理', IN_PROGRESS: '处理中', RESOLVED: '已处理', IGNORED: '已忽略' }[status] || status || '无异常')
const issueTag = status => ({ NORMAL: 'success', PENDING: 'warning', IN_PROGRESS: 'primary', RESOLVED: 'success', IGNORED: 'info' }[status] || 'info')
const issueTypeText = type => ({ IMPORT_ERROR: '导入错误', DUPLICATE_DATA: '重复数据', MISSING_FIELD: '字段缺失', COLLECTION_FAILED: '采集失败' }[type] || type)
const rateClass = rate => rate >= 98 ? 'rate-good' : rate >= 90 ? 'rate-warning' : 'rate-bad'

const handleResize = () => trendChart?.resize()

onMounted(() => {
  loadDashboard()
  window.addEventListener('resize', handleResize)
})

onUnmounted(() => {
  window.removeEventListener('resize', handleResize)
  trendChart?.dispose()
  trendChart = null
})
</script>

<style scoped>
.governance-page {
  max-width: 1320px;
  padding-top: 22px;
  padding-bottom: 32px;
}

.page-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 20px;
  margin-bottom: 18px;
}

.page-heading h2 { margin: 0; color: #17333c; font-size: 25px; letter-spacing: 0; }
.page-heading p { margin: 7px 0 0; color: #6c7b83; font-size: 14px; }
.heading-actions { display: flex; align-items: center; gap: 10px; }
.generated-at { color: #7b8991; font-size: 12px; white-space: nowrap; }
.icon-button { width: 34px; height: 34px; }
.load-alert { margin-bottom: 14px; }
.dashboard-content { min-height: 360px; }

.metrics-grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 10px;
  margin-bottom: 14px;
}

.metric-card {
  min-height: 116px;
  padding: 15px;
  border: 1px solid #e0e7eb;
  border-top: 3px solid var(--metric-color);
  border-radius: 7px;
  background: #fff;
  display: flex;
  align-items: flex-start;
  gap: 11px;
}

.metric-card.teal { --metric-color: #12758a; --metric-bg: #e5f3f5; }
.metric-card.blue { --metric-color: #3478b6; --metric-bg: #e8f0f8; }
.metric-card.green { --metric-color: #3a8b69; --metric-bg: #e7f3ed; }
.metric-card.orange { --metric-color: #d8842f; --metric-bg: #fbefe1; }
.metric-card.cyan { --metric-color: #3a8f9f; --metric-bg: #e6f2f4; }
.metric-card.red { --metric-color: #c45a54; --metric-bg: #f8e9e8; }
.metric-icon {
  flex: 0 0 31px;
  width: 31px;
  height: 31px;
  border-radius: 6px;
  background: var(--metric-bg);
  color: var(--metric-color);
  display: grid;
  place-items: center;
  font-size: 17px;
}
.metric-copy { min-width: 0; display: flex; flex-direction: column; }
.metric-copy span { color: #6d7b83; font-size: 12px; }
.metric-copy strong { margin-top: 4px; color: #203943; font-size: 22px; line-height: 1.2; white-space: nowrap; }
.metric-copy small { margin-top: 5px; color: #8b969c; font-size: 11px; line-height: 1.35; }

.charts-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.8fr) minmax(290px, .8fr);
  gap: 14px;
  margin-bottom: 14px;
}
.panel { border: 1px solid #e0e7eb; border-radius: 7px; background: #fff; }
.trend-panel, .freshness-panel { padding: 18px; min-height: 340px; }
.panel-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; }
.panel-head h3 { margin: 0; color: #203943; font-size: 16px; letter-spacing: 0; }
.panel-head p { margin: 5px 0 0; color: #87939a; font-size: 12px; }
.chart-legend { display: flex; align-items: center; gap: 14px; color: #68777f; font-size: 11px; }
.chart-legend span { display: inline-flex; align-items: center; gap: 5px; white-space: nowrap; }
.chart-legend i { width: 9px; height: 9px; border-radius: 2px; }
.total-dot { background: #12758a; }
.added-dot { background: #e99b40; }
.trend-chart { height: 270px; width: 100%; }

.freshness-summary { display: flex; align-items: center; gap: 22px; min-height: 220px; }
.freshness-score {
  flex: 0 0 108px;
  width: 108px;
  height: 108px;
  border: 10px solid #dcecee;
  border-top-color: #2f9072;
  border-right-color: #2f9072;
  border-radius: 50%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}
.freshness-score strong { color: #245b55; font-size: 23px; }
.freshness-score span { color: #7d8b91; font-size: 11px; margin-top: 2px; }
.freshness-bars { flex: 1; min-width: 0; }
.freshness-row { display: grid; grid-template-columns: 58px 1fr 20px; align-items: center; gap: 8px; margin: 12px 0; }
.freshness-row > span { color: #66767e; font-size: 11px; display: flex; align-items: center; gap: 5px; }
.freshness-row > span i { width: 7px; height: 7px; border-radius: 50%; }
.freshness-row strong { color: #45575f; font-size: 12px; text-align: right; }
.bar-track { height: 6px; overflow: hidden; border-radius: 3px; background: #edf1f3; }
.bar-track i { display: block; min-width: 2px; height: 100%; border-radius: inherit; }
.fresh { background: #3a8b69; }
.warning { background: #e0a044; }
.stale { background: #c45a54; }
.unknown { background: #99a5ab; }
.quality-note { border-top: 1px solid #edf1f3; padding-top: 12px; color: #849198; font-size: 11px; display: flex; gap: 6px; align-items: center; }

.source-panel, .issues-panel { padding: 18px; margin-bottom: 14px; overflow: hidden; }
.source-head { align-items: center; margin-bottom: 14px; }
.filters { display: flex; align-items: center; gap: 8px; }
.filters .el-input { width: 190px; }
.filters .el-select { width: 145px; }
.source-table { width: 100%; }
.source-name { color: #263f49; font-weight: 600; line-height: 1.35; }
.source-code { margin-top: 4px; color: #97a1a6; font-size: 10px; word-break: break-all; }
.cell-note { display: block; margin-top: 4px; color: #939ea4; font-size: 11px; }
.origin-name { color: #45575f; font-size: 12px; line-height: 1.4; }
.license-text { margin-top: 3px; color: #849198; font-size: 11px; line-height: 1.35; }
.source-link, .file-source { margin-top: 5px; color: #12758a; font-size: 11px; display: inline-flex; align-items: center; gap: 3px; text-decoration: none; }
.file-source { color: #74848c; cursor: help; }
.rate-good { color: #2e8060; }
.rate-warning { color: #bf7627; }
.rate-bad { color: #b84e49; }
.mini-track { width: 76px; height: 3px; margin-top: 6px; overflow: hidden; border-radius: 2px; background: #e8edef; }
.mini-track i { display: block; height: 100%; border-radius: inherit; background: #3a8b69; }
.issues-panel .panel-head { align-items: center; margin-bottom: 12px; }

:deep(.el-table) { --el-table-header-bg-color: #f5f8f9; --el-table-row-hover-bg-color: #f1f7f7; color: #45575f; font-size: 12px; }
:deep(.el-table th.el-table__cell) { color: #607179; font-weight: 600; }
:deep(.el-table .cell) { line-height: 1.45; }

@media (max-width: 1180px) {
  .metrics-grid { grid-template-columns: repeat(3, minmax(0, 1fr)); }
}

@media (max-width: 880px) {
  .charts-grid { grid-template-columns: 1fr; }
  .source-head { align-items: flex-start; flex-direction: column; }
  .filters { width: 100%; flex-wrap: wrap; }
  .filters .el-input { flex: 1 1 220px; width: auto; }
  .filters .el-select { flex: 1 1 150px; width: auto; }
}

@media (max-width: 640px) {
  .governance-page { padding-top: 14px; }
  .page-heading { flex-direction: column; gap: 10px; }
  .heading-actions { width: 100%; justify-content: space-between; }
  .metrics-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .metric-card { min-height: 108px; padding: 12px; }
  .metric-copy strong { font-size: 19px; }
  .trend-panel, .freshness-panel, .source-panel, .issues-panel { padding: 14px; }
  .chart-legend { display: none; }
  .freshness-summary { gap: 14px; }
  .freshness-score { flex-basis: 94px; width: 94px; height: 94px; border-width: 8px; }
}
</style>
