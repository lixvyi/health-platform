<template>
  <div class="container page">
    <!-- 面包屑 -->
    <el-breadcrumb separator="/" class="breadcrumb">
      <el-breadcrumb-item :to="{ path: '/' }">首页</el-breadcrumb-item>
      <el-breadcrumb-item :to="{ path: '/drugs' }">药品查询</el-breadcrumb-item>
      <el-breadcrumb-item>国家医保药品目录</el-breadcrumb-item>
    </el-breadcrumb>

    <!-- 页面标题 -->
    <div class="hero">
      <h1>国家医保药品目录</h1>
      <p class="subtitle">国家基本医疗保险、工伤保险和生育保险药品目录数据查询</p>
    </div>

    <el-alert title="本平台所有药品信息仅供参考，不作为诊疗依据，用药请遵医嘱。" type="warning" show-icon :closable="false" class="disclaimer" />

    <!-- 快捷入口 -->
    <div class="quick-links">
      <router-link to="/drugs"><el-button size="small">🔍 药品查询</el-button></router-link>
      <router-link to="/drugs/recommend"><el-button size="small">💊 症状选药</el-button></router-link>
      <router-link to="/drugs/stats"><el-button size="small">📊 数据看板</el-button></router-link>
    </div>

    <!-- 筛选区 -->
    <div class="filter-card">
      <div class="filter-grid">
        <el-input v-model="filters.drugName" placeholder="药品名称" clearable size="default" @keyup.enter="doSearch">
          <template #prefix><el-icon><Search /></el-icon></template>
        </el-input>
        <el-input v-model="filters.categoryName" placeholder="药品分类名称" clearable size="default" @keyup.enter="doSearch" />
        <el-input v-model="filters.categoryCode" placeholder="分类代码" clearable size="default" @keyup.enter="doSearch" />
        <el-input v-model="filters.drugNumber" placeholder="目录编号" clearable size="default" @keyup.enter="doSearch" />
        <el-input v-model="filters.dosageForm" placeholder="剂型" clearable size="default" @keyup.enter="doSearch" />
      </div>
      <div class="filter-actions">
        <el-button type="primary" :loading="loading" @click="doSearch">
          <el-icon><Search /></el-icon> 检索
        </el-button>
        <el-button @click="resetFilters">清空条件</el-button>
      </div>
    </div>

    <!-- 结果区 -->
    <template v-if="searched">
      <div class="result-bar">
        <span class="result-count">共找到 <strong>{{ total }}</strong> 条目录记录</span>
        <span v-if="total > 0" class="result-hint">点击药品名称可查看详情</span>
      </div>

      <el-table
        v-loading="loading"
        :data="list"
        stripe
        size="default"
        class="data-table"
        @row-click="goDetail"
      >
        <el-table-column prop="drugName" label="药品名称" min-width="200">
          <template #default="scope">
            <span class="link-text">{{ scope.row.drugName }}</span>
          </template>
        </el-table-column>
        <el-table-column prop="categoryName" label="药品分类" min-width="180" />
        <el-table-column prop="categoryCode" label="分类代码" width="110" />
        <el-table-column prop="drugNumber" label="目录编号" width="110" />
        <el-table-column prop="dosageForm" label="剂型" min-width="120">
          <template #default="scope">{{ scope.row.dosageForm || '—' }}</template>
        </el-table-column>
        <el-table-column prop="insuranceType" label="医保类型" width="120">
          <template #default="scope">
            <el-tag v-if="scope.row.insuranceType" :type="insuranceTagType(scope.row.insuranceType)" size="small" effect="plain">
              {{ scope.row.insuranceType }}
            </el-tag>
            <span v-else class="placeholder">—</span>
          </template>
        </el-table-column>
        <el-table-column prop="catalogYear" label="年份" width="80">
          <template #default="scope">
            <span v-if="scope.row.catalogYear">{{ scope.row.catalogYear }}</span>
            <span v-else class="placeholder">—</span>
          </template>
        </el-table-column>
        <el-table-column label="来源" min-width="200" show-overflow-tooltip>
          <template #default="scope">
            <span v-if="scope.row.sourceFile" class="source-info">
              {{ sourceLabel(scope.row) }}
            </span>
            <span v-else class="placeholder">—</span>
          </template>
        </el-table-column>
      </el-table>

      <el-empty v-if="!loading && !list.length" description="未找到匹配的药品目录记录，请调整筛选条件重试" />

      <el-pagination
        v-if="total > pageSize"
        v-model:current-page="page"
        :page-size="pageSize"
        :total="total"
        layout="total, prev, pager, next, jumper"
        background
        @current-change="loadData"
      />
    </template>

    <el-empty v-else description="请输入筛选条件开始查询" class="initial-empty" />
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useRouter } from 'vue-router'
import { Search } from '@element-plus/icons-vue'
import { portalApi } from '../../api'

const router = useRouter()
const loading = ref(false)
const list = ref([])
const total = ref(0)
const page = ref(1)
const pageSize = 15
const searched = ref(false)

const filters = reactive({
  drugName: '',
  categoryName: '',
  categoryCode: '',
  drugNumber: '',
  dosageForm: ''
})

const resetFilters = () => {
  filters.drugName = ''
  filters.categoryName = ''
  filters.categoryCode = ''
  filters.drugNumber = ''
  filters.dosageForm = ''
  page.value = 1
  searched.value = false
  list.value = []
  total.value = 0
}

const doSearch = () => {
  page.value = 1
  searched.value = true
  loadData()
}

const loadData = async () => {
  loading.value = true
  try {
    const params = { page: page.value, size: pageSize }
    // 只传有值的参数
    for (const [key, value] of Object.entries(filters)) {
      if (value.trim()) params[key] = value.trim()
    }
    const res = await portalApi.medicalDrugs(params)
    list.value = res.data?.records || []
    total.value = res.data?.total || 0
  } catch {
    list.value = []
    total.value = 0
  } finally {
    loading.value = false
  }
}

const insuranceTagType = (type) => {
  if (!type) return 'info'
  if (type.includes('甲类')) return 'danger'
  if (type.includes('乙类')) return 'warning'
  return 'info'
}

const sourceLabel = (row) => {
  const file = (row.sourceFile || '').split(/[\\/]/).pop() || '本地导入文件'
  const sheet = row.sourceSheet ? ` / ${row.sourceSheet}` : ''
  const line = row.sourceRow ? ` / 第${row.sourceRow}行` : ''
  return `${file}${sheet}${line}`
}

const goDetail = (row) => {
  // 如果存在关联的 drug 表 id 则跳转详情页
  if (row.drugId) {
    router.push(`/drugs/${row.drugId}`)
  }
  // 否则尝试按药品名称搜索药品（不做跳转）
}
</script>

<style scoped>
.page {
  padding: 28px 32px;
  margin: 20px auto;
  background: #fff;
  border-radius: 8px;
  max-width: 1200px;
}

.breadcrumb { margin-bottom: 12px; }

.hero { text-align: center; padding: 20px 0 10px; }
.hero h1 { margin: 0 0 8px; font-size: 24px; color: #1a6fb5; }
.subtitle { color: #666; font-size: 14px; margin: 0; }

.disclaimer { margin: 16px 0; }

.quick-links {
  display: flex;
  gap: 10px;
  margin-bottom: 16px;
  justify-content: center;
}

/* 筛选卡片 */
.filter-card {
  background: #f5f7fa;
  border-radius: 8px;
  padding: 20px;
  margin-bottom: 20px;
  border: 1px solid #e4e7ed;
}
.filter-grid {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  gap: 12px;
  margin-bottom: 16px;
}
.filter-actions {
  display: flex;
  gap: 10px;
  justify-content: center;
}

/* 结果信息栏 */
.result-bar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 12px;
}
.result-count { color: #606266; font-size: 14px; }
.result-count strong { color: #1a6fb5; }
.result-hint { color: #909399; font-size: 12px; }

/* 表格 */
.data-table {
  width: 100%;
}
:deep(.el-table__row) { cursor: pointer; }
.link-text { color: #409eff; font-weight: 500; }
.link-text:hover { text-decoration: underline; }
.placeholder { color: #c0c4cc; }
.source-info { font-size: 12px; color: #909399; }

/* 分页 */
.el-pagination { margin-top: 20px; justify-content: center; }

/* 初始空态 */
.initial-empty { padding: 60px 0; }

/* 响应式 */
@media (max-width: 900px) {
  .filter-grid { grid-template-columns: repeat(3, 1fr); }
}
@media (max-width: 640px) {
  .page { padding: 16px; }
  .filter-grid { grid-template-columns: 1fr 1fr; }
  .hero h1 { font-size: 20px; }
}
</style>
