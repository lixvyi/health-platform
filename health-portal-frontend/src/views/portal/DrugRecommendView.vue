<template>
  <div class="container page">
    <el-page-header @back="$router.push('/drugs')" content="症状选药" />
    <el-alert title="本平台所有药品信息仅供参考，不作为诊疗依据，用药请遵医嘱。" type="warning" show-icon :closable="false" class="disclaimer" />

    <div class="input-area">
      <p>输入您的症状或疾病名称，获取非处方药推荐：</p>
      <el-input v-model="query" placeholder="如：感冒、头痛、胃酸、皮肤过敏" @keyup.enter="search" class="query-input">
        <template #append><el-button type="primary" @click="search" :loading="loading">推荐</el-button></template>
      </el-input>
    </div>

    <div v-if="showDisclaimer" class="disclaimer-modal">
      <el-alert title="用药须知" type="warning" show-icon :closable="false">
        <p>以上推荐仅供参考，不能替代专业诊断。用药前请仔细阅读说明书或咨询医生/药师。</p>
        <p style="font-weight:bold">如果您症状严重或持续不缓解，请及时就医。</p>
      </el-alert>
    </div>

    <div v-if="results.length" class="results">
      <p class="result-count">找到 {{ results.length }} 种推荐非处方药</p>
      <div v-for="drug in results" :key="drug.id" class="drug-card" @click="$router.push(`/drugs/${drug.id}`)">
        <h3>{{ drug.genericName }} <small v-if="drug.brandName">{{ drug.brandName }}</small></h3>
        <el-tag size="small" type="success">非处方药</el-tag>
        <p v-if="drug.indicationsSummary" class="indications">{{ drug.indicationsSummary }}</p>
      </div>
    </div>
    <el-empty v-else-if="searched" description="未找到匹配的非处方药，或症状不明确" />
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { portalApi } from '../../api'

const query = ref('')
const loading = ref(false)
const results = ref([])
const searched = ref(false)
const showDisclaimer = ref(false)

const search = async () => {
  if (!query.value.trim()) return
  loading.value = true; searched.value = true; showDisclaimer.value = true
  try {
    const res = await portalApi.drugRecommend({ query: query.value.trim() })
    results.value = res.data?.recommendations || []
    // 按通用名称去重
    const seen = new Set()
    results.value = results.value.filter(d => {
      const key = d.genericName
      if (seen.has(key)) return false
      seen.add(key)
      return true
    })
  } catch { results.value = [] }
  finally { loading.value = false }
}
</script>

<style scoped>
.page { padding: 24px; max-width: 800px; margin: 0 auto; }
.disclaimer { margin: 16px 0; }
.input-area { background: #f5f7fa; padding: 20px; border-radius: 8px; margin-top: 16px; }
.input-area p { margin: 0 0 12px; color: #666; }
.query-input { width: 100%; }
.disclaimer-modal { margin: 16px 0; }
.result-count { color: #909399; font-size: 13px; margin-bottom: 12px; }
.drug-card { border: 1px solid #e4e7ed; border-radius: 8px; padding: 14px; margin-bottom: 10px; cursor: pointer; }
.drug-card:hover { border-color: #67c23a; box-shadow: 0 2px 8px rgba(103,194,58,.15); }
.drug-card h3 { margin: 0 0 6px; }
.drug-card h3 small { font-weight: normal; color: #909399; font-size: 13px; }
.indications { color: #606266; font-size: 13px; line-height: 1.6; margin: 8px 0 0; }
</style>
