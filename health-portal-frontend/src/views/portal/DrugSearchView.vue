<template>
  <div class="container page">
    <div class="hero">
      <h1>药品信息查询</h1>
      <p class="subtitle">查询药品适应症、禁忌、用法用量</p>
      <el-autocomplete
        v-model="keyword"
        :fetch-suggestions="quickSearch"
        placeholder="输入药品名称、成分或适应症关键词"
        :trigger-on-focus="false"
        size="large"
        class="search-input"
        @keyup.enter="search"
        @select="goDetail"
      >
        <template #prefix><el-icon><Search /></el-icon></template>
        <template #suffix>
          <el-button type="primary" @click="search" :loading="loading">搜索</el-button>
        </template>
      </el-autocomplete>
      <div class="hot-tags">
        <span class="label">热门搜索：</span>
        <el-tag v-for="tag in hotTags" :key="tag" @click="quickTag(tag)" class="hot-tag">{{ tag }}</el-tag>
      </div>
    </div>

    <el-alert title="本平台所有药品信息仅供参考，不作为诊疗依据，用药请遵医嘱。" type="warning" show-icon :closable="false" class="disclaimer" />

    <div class="toolbar">
      <router-link to="/drugs/catalog"><el-button>📋 医保目录</el-button></router-link>
      <router-link to="/drugs/stats"><el-button>📊 数据看板</el-button></router-link>
      <router-link to="/drugs/recommend"><el-button>💊 症状选药</el-button></router-link>
    </div>

    <div v-if="showResult" class="result-area">
      <p class="result-count">找到约 {{ total }} 条结果</p>
      <div v-if="!results.length" class="empty"><el-empty description="未找到匹配药品" /></div>
      <div v-else class="drug-list">
        <div v-for="drug in results" :key="drug.id" class="drug-card" @click="$router.push(`/drugs/${drug.id}`)">
          <div class="drug-header">
            <h3>{{ drug.genericName }} <small v-if="drug.brandName">{{ drug.brandName }}</small></h3>
            <el-tag :type="drug.prescriptionType === '非处方药' ? 'success' : 'danger'" size="small">
              {{ drug.prescriptionType || '处方药' }}
            </el-tag>
            <el-tag v-if="drug.category" type="info" size="small">{{ drug.category }}</el-tag>
          </div>
          <p v-if="drug.indicationsSummary" class="indications">{{ drug.indicationsSummary }}</p>
          <div class="drug-meta">
            <span v-if="drug.dosageForm">剂型：{{ drug.dosageForm }}</span>
          </div>
        </div>
      </div>
      <el-pagination
        v-if="total > pageSize" v-model:current-page="page" :page-size="pageSize"
        :total="total" layout="prev, pager, next" @current-change="loadResults"
      />
    </div>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { Search } from '@element-plus/icons-vue'
import { portalApi } from '../../api'

const router = useRouter()
const keyword = ref('')
const loading = ref(false)
const results = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = 20
const showResult = ref(false)



const hotTags = ['阿莫西林', '布洛芬', '阿司匹林', '奥美拉唑', '二甲双胍', '氨氯地平']

const search = () => { page.value = 1; loadResults() }

const loadResults = async () => {
  if (!keyword.value.trim()) return
  loading.value = true; showResult.value = true
  try {
    const res = await portalApi.drugSearch({ keyword: keyword.value.trim(), page: page.value, size: pageSize })
    results.value = res.data?.records || []
    // 按通用名称去重，保留第一个
    const seen = new Set()
    results.value = results.value.filter(d => {
      const key = d.genericName
      if (seen.has(key)) return false
      seen.add(key)
      return true
    })
    total.value = res.data?.total || 0
  } catch { results.value = []; total.value = 0 }
  finally { loading.value = false }
}

const quickSearch = async (query, cb) => {
  if (!query) return cb([])
  try {
    const res = await portalApi.drugSearch({ keyword: query, page: 1, size: 10 })
    cb((res.data?.records || []).map(r => ({ value: `${r.genericName} ${r.brandName || ''}`, id: r.id })))
  } catch { cb([]) }
}

const goDetail = (item) => { if (item.id) router.push(`/drugs/${item.id}`) }
const quickTag = (tag) => { keyword.value = tag; search() }
</script>

<style scoped>
.page { padding: 24px; max-width: 1000px; margin: 0 auto; }
.hero { text-align: center; padding: 40px 0 20px; }
.hero h1 { margin: 0 0 8px; color: #1a6fb5; }
.subtitle { color: #666; margin-bottom: 20px; }
.search-input { width: 100%; max-width: 600px; }
.hot-tags { margin-top: 16px; display: flex; justify-content: center; align-items: center; gap: 6px; flex-wrap: wrap; }
.hot-tags .label { color: #999; font-size: 13px; }
.hot-tag { cursor: pointer; }
.disclaimer { margin: 16px 0; }
.toolbar { display: flex; gap: 10px; margin-bottom: 20px; justify-content: center; }
.result-count { color: #909399; font-size: 13px; margin-bottom: 12px; }
.drug-list { display: flex; flex-direction: column; gap: 10px; }
.drug-card { border: 1px solid #e4e7ed; border-radius: 8px; padding: 16px; cursor: pointer; transition: 0.2s; }
.drug-card:hover { border-color: #409eff; box-shadow: 0 2px 8px rgba(64,158,255,.12); }
.drug-header { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; margin-bottom: 8px; }
.drug-header h3 { margin: 0; font-size: 16px; }
.drug-header h3 small { font-weight: normal; color: #909399; font-size: 13px; }
.indications { color: #606266; font-size: 13px; line-height: 1.6; margin: 0 0 6px; }
.drug-meta { color: #909399; font-size: 12px; }
.empty { padding: 40px 0; }
.el-pagination { margin-top: 18px; justify-content: center; }

</style>
