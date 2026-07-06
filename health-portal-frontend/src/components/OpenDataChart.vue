<template>
  <div class="chart-wrap">
    <div ref="chartRef" class="chart" :style="{ height: height + 'px' }"></div>
    <DataSourceNotice v-if="showNotice" compact />
  </div>
</template>

<script setup>
import { onMounted, onUnmounted, ref, watch } from 'vue'
import * as echarts from 'echarts'
import DataSourceNotice from './DataSourceNotice.vue'

const props = defineProps({
  title: { type: String, required: true },
  values: { type: Object, default: () => ({}) },
  unit: { type: String, default: '' },
  height: { type: Number, default: 280 },
  showNotice: { type: Boolean, default: true },
  chartType: { type: String, default: 'line' }
})

const chartRef = ref()
let chart

const render = () => {
  if (!chartRef.value) return
  if (!chart) chart = echarts.init(chartRef.value)
  const years = Object.keys(props.values).sort()
  const data = years.map(y => props.values[y])
  chart.setOption({
    title: { text: props.title, left: 'center', textStyle: { fontSize: 14 } },
    tooltip: { trigger: 'axis', valueFormatter: v => `${v}${props.unit ? ' ' + props.unit : ''}` },
    grid: { left: 48, right: 24, top: 48, bottom: 32 },
    xAxis: { type: 'category', data: years },
    yAxis: { type: 'value', name: props.unit },
    series: [{
      type: props.chartType === 'bar' ? 'bar' : 'line',
      data,
      smooth: true,
      itemStyle: { color: '#1a6fb5' },
      areaStyle: props.chartType === 'line' ? { color: 'rgba(26,111,181,0.12)' } : undefined
    }]
  })
}

onMounted(() => {
  render()
  window.addEventListener('resize', () => chart?.resize())
})

onUnmounted(() => {
  chart?.dispose()
  chart = null
})

watch(() => props.values, render, { deep: true })
</script>

<style scoped>
.chart-wrap { margin-bottom: 12px; }
.chart { background: #fff; border-radius: 8px; padding: 8px; border: 1px solid #eee; }
</style>
