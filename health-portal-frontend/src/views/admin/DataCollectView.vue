<template>
  <div>
    <h2>数据采集与资源池</h2>
    <p class="desc">
      触发合规互联网采集（中国政府网 + 国家统计局公开页），同步开放数据文件，并自动将<strong>政府官网公开的政策/健康科普</strong>（摘要+原文链接）写入 CMS 政策库与知识库。
    </p>

    <el-button type="primary" :loading="loading" @click="runCollect">执行互联网采集</el-button>
    <el-button :loading="etlLoading" @click="runEtl">执行 Spark ETL</el-button>

    <el-alert v-if="message" :title="message" type="success" show-icon class="mt" />

    <section class="block" v-if="status">
      <h3>最近任务</h3>
      <p>开始：{{ status.startedAt }} · 结束：{{ status.finishedAt }}</p>
      <el-table :data="status.sources || []" stripe size="small">
        <el-table-column prop="sourceName" label="采集源" min-width="220" />
        <el-table-column prop="status" label="状态" width="100" />
        <el-table-column prop="recordCount" label="记录" width="80" />
        <el-table-column prop="error" label="错误" min-width="160" />
      </el-table>
      <pre v-if="status.imports?.length" class="log">{{ status.imports.join('\n') }}</pre>
    </section>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { adminApi } from '../../api'

const loading = ref(false)
const etlLoading = ref(false)
const message = ref('')
const status = ref(null)
const etlStatus = ref(null)

const load = async () => {
  const res = await adminApi.dataPoolCollectStatus()
  status.value = res.data
}

const runCollect = async () => {
  loading.value = true
  message.value = ''
  try {
    const res = await adminApi.dataPoolCollect()
    status.value = res.data
    message.value = '采集完成，资源池已更新'
  } catch (e) {
    message.value = e?.response?.data?.message || '采集失败'
  } finally {
    loading.value = false
  }
}

const runEtl = async () => {
  etlLoading.value = true
  try {
    const res = await adminApi.dataPoolEtlRun()
    etlStatus.value = res.data
    message.value = 'ETL 完成：' + (res.data?.etlEngine || 'ok')
  } catch (e) {
    message.value = e?.message || 'ETL 失败'
  } finally {
    etlLoading.value = false
  }
}

onMounted(load)
</script>

<style scoped>
.desc { color: #666; margin-bottom: 16px; line-height: 1.6; }
.block { margin-top: 24px; background: #fff; padding: 16px; border-radius: 8px; }
.block h3 { margin: 0 0 12px; color: #1a6fb5; }
.log { margin-top: 12px; background: #f5f5f5; padding: 12px; font-size: 12px; overflow: auto; }
.mt { margin-top: 16px; }
</style>
