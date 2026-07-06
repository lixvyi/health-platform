<template>
  <div>
    <h2>数据看板</h2>
    <el-row :gutter="16">
      <el-col :span="6" v-for="(v, k) in stats" :key="k">
        <el-card><div class="stat">{{ labels[k] || k }}</div><div class="num">{{ v }}</div></el-card>
      </el-col>
    </el-row>
    <div ref="chartRef" style="height:360px;margin-top:24px;background:#fff;padding:12px;border-radius:8px"></div>

    <el-alert class="notice" type="info" :closable="false" show-icon title="数据说明">
      本看板统计的是<strong>本门户 CMS 内容数量</strong>（新闻、公告等），非国家统计局数据。
      开放统计数据请见公众端
      <el-button type="primary" link @click="openData">数据资源目录</el-button>
      ，引用国家统计局数据须遵守
      <el-button type="primary" link @click="openAgreement">用户使用协议</el-button>。
    </el-alert>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import * as echarts from 'echarts'
import { adminApi } from '../../api'

const stats = ref({})
const chartRef = ref()
const labels = { NEWS: '新闻', NOTICE: '公告', POLICY: '政策', KNOWLEDGE: '知识库', APP: '应用' }

const openData = () => window.open('/data', '_blank')
const openAgreement = () => window.open('/data-agreement', '_blank')

onMounted(async () => {
  const res = await adminApi.stats()
  stats.value = res.data
  const chart = echarts.init(chartRef.value)
  chart.setOption({
    title: { text: '门户内容统计' },
    tooltip: {},
    xAxis: { type: 'category', data: Object.keys(res.data).map(k => labels[k] || k) },
    yAxis: { type: 'value' },
    series: [{ type: 'bar', data: Object.values(res.data), itemStyle: { color: '#1a6fb5' } }]
  })
})
</script>

<style scoped>
.stat { color: #888; font-size: 13px; }
.num { font-size: 28px; font-weight: 700; color: #1a6fb5; margin-top: 8px; }
.notice { margin-top: 20px; }
</style>
