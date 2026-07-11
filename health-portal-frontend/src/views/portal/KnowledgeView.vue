<template>
  <div class="container page">
    <div class="page-head">
      <div>
        <h2>健康百科</h2>
        <p class="lead">按权威来源、正式分类和关键词检索健康知识</p>
      </div>
      <el-tag type="info" effect="plain">内容仅供科普参考，不能替代医生诊断</el-tag>
    </div>

    <div class="topic-tags" aria-label="健康百科分类">
      <el-tag
        :type="activeCategory ? 'info' : 'primary'"
        :effect="activeCategory ? 'plain' : 'dark'"
        class="topic-tag"
        @click="selectCategory('')"
      >全部</el-tag>
      <el-tag
        v-for="category in categories"
        :key="category.code"
        :type="activeCategory === category.code ? 'primary' : 'info'"
        :effect="activeCategory === category.code ? 'dark' : 'plain'"
        class="topic-tag"
        @click="selectCategory(category.code)"
      >{{ category.icon }} {{ category.name }}</el-tag>
    </div>

    <div class="search-row">
      <el-input
        v-model="keyword"
        placeholder="搜索标题、摘要、发布机构或来源"
        clearable
        @clear="search"
        @keyup.enter="search"
      />
      <el-button type="primary" :loading="loading" @click="search">搜索</el-button>
    </div>

    <div class="result-meta" v-if="loaded">找到 {{ total }} 条内容</div>

    <div v-if="loading" class="loading"><el-skeleton :rows="5" animated /></div>
    <el-alert
      v-else-if="error"
      :title="error"
      type="error"
      show-icon
      :closable="false"
    >
      <template #default><el-button link type="primary" @click="load">重新加载</el-button></template>
    </el-alert>
    <div v-else-if="list.length" class="knowledge-list">
      <article
        v-for="item in list"
        :key="item.id"
        class="knowledge-item"
        tabindex="0"
        @click="openDetail(item.id)"
        @keyup.enter="openDetail(item.id)"
      >
        <div class="knowledge-cover">
          <img :src="articleImageOf(item)" :alt="item.title" />
        </div>
        <div class="knowledge-info">
          <h3>{{ item.title }}</h3>
          <p class="summary">{{ item.summary || '暂无摘要' }}</p>
          <div class="meta">
            <span v-if="item.publisher || item.author">{{ item.publisher || item.author }}</span>
            <span v-if="item.sourceName">来源：{{ item.sourceName }}</span>
            <span v-if="item.publishTime">{{ item.publishTime.slice(0, 10) }}</span>
            <span>阅读 {{ item.viewCount || 0 }}</span>
          </div>
        </div>
      </article>
    </div>
    <el-empty v-else description="该分类暂无已核验内容" />

    <el-pagination
      v-if="total > pageSize"
      v-model:current-page="page"
      :page-size="pageSize"
      :total="total"
      layout="prev, pager, next"
      @current-change="load"
    />
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { portalApi } from '../../api'
import { articleImageOf } from '../../utils/articleImages'

const router = useRouter()
const categories = ref([])
const activeCategory = ref('')
const keyword = ref('')
const list = ref([])
const page = ref(1)
const pageSize = 10
const total = ref(0)
const loaded = ref(false)
const loading = ref(false)
const error = ref('')

const loadCategories = async () => {
  const response = await portalApi.knowledgeCategories()
  categories.value = response.data || []
}

const load = async () => {
  loading.value = true
  error.value = ''
  try {
    const response = await portalApi.contents({
      categoryCode: 'KNOWLEDGE',
      knowledgeCategoryCode: activeCategory.value || undefined,
      keyword: keyword.value.trim() || undefined,
      page: page.value,
      size: pageSize
    })
    list.value = response.data?.records || []
    total.value = response.data?.total || 0
    loaded.value = true
  } catch (e) {
    list.value = []
    total.value = 0
    error.value = e?.response?.data?.message || '健康百科加载失败，请稍后重试'
  } finally {
    loading.value = false
  }
}

const selectCategory = (code) => {
  activeCategory.value = code
  page.value = 1
  load()
}

const search = () => {
  page.value = 1
  load()
}

const openDetail = (id) => router.push(`/content/${id}`)

onMounted(async () => {
  try {
    await loadCategories()
  } catch (e) {
    error.value = '健康百科分类加载失败'
  }
  await load()
})
</script>

<style scoped>
.page { padding: 24px; background: #fff; margin: 20px auto; border-radius: 8px; max-width: 1000px; }
.page-head { display: flex; justify-content: space-between; align-items: flex-start; gap: 16px; flex-wrap: wrap; }
.page-head h2 { margin: 0 0 8px; }
.lead { color: #666; font-size: 14px; margin: 0 0 18px; }
.topic-tags { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 20px; }
.topic-tag { cursor: pointer; padding: 6px 14px; border-radius: 20px; }
.search-row { display: flex; gap: 10px; margin-bottom: 12px; max-width: 620px; }
.result-meta { color: #888; font-size: 13px; margin-bottom: 8px; }
.loading { padding: 12px 0; }
.knowledge-item { display: flex; gap: 16px; padding: 16px; border-bottom: 1px solid #f0f0f0; cursor: pointer; border-radius: 6px; }
.knowledge-item:hover, .knowledge-item:focus { background: #f7fbff; outline: none; }
.knowledge-cover { flex-shrink: 0; width: 120px; height: 80px; border-radius: 6px; overflow: hidden; }
.knowledge-cover img { width: 100%; height: 100%; object-fit: cover; }
.knowledge-info { flex: 1; min-width: 0; }
.knowledge-info h3 { margin: 0 0 6px; font-size: 17px; color: #303133; }
.summary { margin: 0 0 10px; color: #606266; line-height: 1.6; display: -webkit-box; -webkit-box-orient: vertical; -webkit-line-clamp: 2; overflow: hidden; }
.meta { display: flex; flex-wrap: wrap; gap: 14px; color: #909399; font-size: 12px; }
.el-pagination { margin-top: 20px; justify-content: center; }
@media (max-width: 640px) { .search-row { flex-direction: column; } .knowledge-cover { display: none; } }
</style>

