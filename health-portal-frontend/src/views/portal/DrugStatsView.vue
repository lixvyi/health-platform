<template>
  <div class="container page">
    <el-page-header @back="$router.push('/drugs')" content="药品数据看板" />
    <el-alert title="本平台所有药品信息仅供参考，不作为诊疗依据，用药请遵医嘱。" type="warning" show-icon :closable="false" class="disclaimer" />

    <div class="chart-grid" v-loading="loading">
      <div class="chart-card">
        <h3>药品类别分布</h3>
        <div ref="categoryChart" class="chart"></div>
      </div>
      <div class="chart-card">
        <h3>剂型统计</h3>
        <div ref="dosageChart" class="chart"></div>
      </div>
    </div>
    <el-empty v-if="!loading && !hasData" description="暂无统计数据" />
  </div>
</template>

<script setup>
import { ref, onMounted, nextTick } from 'vue'
import { portalApi } from '../../api'

const loading = ref(true)
const hasData = ref(false)
const categoryChart = ref(null)
const dosageChart = ref(null)

onMounted(async () => {
  const echarts = await import('echarts')
  let chart1 = null, chart2 = null

  const initPie = (el, data) => {
    if (!el || !data?.length) return null
    const c = echarts.init(el)
    c.setOption({
      tooltip: { trigger: 'item', formatter: '{b}: {c} ({d}%)' },
      series: [{ type: 'pie', radius: ['40%', '70%'],
        label: { show: true, formatter: '{b}\n{d}%' },
        data: data.map(d => ({ name: d.name, value: d.value }))
      }]
    })
    return c
  }

  const initBar = (el, data) => {
    if (!el || !data?.length) return null
    const sorted = [...data].sort((a, b) => b.value - a.value).slice(0, 20)
    const c = echarts.init(el)
    c.setOption({
      tooltip: { trigger: 'axis' },
      grid: { left: 100, right: 20 },
      yAxis: { type: 'category', data: sorted.map(d => d.name), axisLabel: { fontSize: 11 } },
      xAxis: { type: 'value' },
      series: [{ type: 'bar', data: sorted.map(d => d.value), itemStyle: { color: '#409eff' } }]
    })
    return c
  }

  try {
    const [catRes, dosRes] = await Promise.all([
      portalApi.drugCategoryStats().catch(() => ({ data: [] })),
      portalApi.drugDosageFormStats().catch(() => ({ data: [] }))
    ])
    const catData = catRes.data || []
    const dosData = dosRes.data || []
    hasData.value = catData.length > 0 || dosData.length > 0

    await nextTick()
    chart1 = initPie(categoryChart.value, catData)
    chart2 = initBar(dosageChart.value, dosData)
  } catch { hasData.value = false }
  finally { loading.value = false }
})
</script>

<style scoped>
.page { padding: 24px; max-width: 1000px; margin: 0 auto; }
.disclaimer { margin: 16px 0; }
.chart-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 20px; margin-top: 16px; }
@media (max-width: 800px) { .chart-grid { grid-template-columns: 1fr; } }
.chart-card { background: #fff; border-radius: 8px; padding: 16px; box-shadow: 0 1px 4px rgba(0,0,0,.06); }
.chart-card h3 { margin: 0 0 12px; color: #1a6fb5; font-size: 16px; }
.chart { width: 100%; height: 400px; }
</style>
