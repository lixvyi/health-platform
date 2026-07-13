<template>
  <div class="container page" :class="{ 'news-page': isNews }">
    <div class="page-head">
      <div>
        <h2>{{ title }}</h2>
        <p v-if="isNews" class="page-subtitle">聚合公共卫生、医疗健康和健康科普相关公开资讯，不与卫生政策栏目重复。</p>
      </div>
      <div class="search-box">
        <el-input v-model="keyword" placeholder="搜索标题" clearable @keyup.enter="search" />
        <el-button type="primary" @click="search">搜索</el-button>
      </div>
    </div>

    <template v-if="isNews">
      <section v-if="firstNews" class="news-featured" @click="open(firstNews)">
        <div class="featured-image" :style="imageStyle(firstNews)"></div>
        <div class="featured-copy">
          <div class="meta">{{ sourceOf(firstNews) }} · {{ formatDate(firstNews.publishTime) }}</div>
          <h3>{{ firstNews.title }}</h3>
          <p>{{ firstNews.summary || '查看来自公开来源的健康资讯索引。' }}</p>
        </div>
      </section>

      <section class="news-grid">
        <article v-for="(item, index) in restNews" :key="item.id" class="news-card" :class="{ wide: index % 5 === 1 }" @click="open(item)">
          <div class="card-image" :style="imageStyle(item)"></div>
          <div class="card-body">
            <div class="meta">{{ sourceOf(item) }} · {{ formatDate(item.publishTime) }}</div>
            <h3>{{ item.title }}</h3>
            <p>{{ item.summary || '健康相关公开资讯。' }}</p>
          </div>
        </article>
      </section>
      <el-empty v-if="!list.length" description="暂无新闻内容" />
    </template>

    <template v-else>
      <div style="margin-bottom:12px;display:flex;justify-content:flex-end;">
        <el-button type="primary" plain size="small" @click="goHotwordTrend">
          <el-icon style="margin-right:4px"><TrendCharts /></el-icon>
          热词演变趋势
        </el-button>
      </div>
      <el-table :data="list" stripe @row-click="open" style="margin-top:4px;cursor:pointer">
        <el-table-column prop="title" label="标题" />
        <el-table-column prop="author" label="作者" width="120" />
        <el-table-column prop="publishTime" label="发布时间" width="180" />
      </el-table>
    </template>

    <el-pagination v-model:current-page="page" :page-size="pageSize" :total="total" layout="prev, pager, next" @current-change="load" class="pager" />
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { TrendCharts } from '@element-plus/icons-vue'
import { portalApi } from '../../api'
import { articleImageOf } from '../../utils/articleImages'

const props = defineProps({ category: String, title: String })
const router = useRouter()
const keyword = ref('')
const list = ref([])
const page = ref(1)
const total = ref(0)
const isNews = computed(() => props.category === 'NEWS')
const pageSize = computed(() => isNews.value ? 12 : 10)
const firstNews = computed(() => isNews.value ? list.value[0] : null)
const restNews = computed(() => isNews.value ? list.value.slice(1) : [])

const load = async () => {
  const res = await portalApi.contents({ categoryCode: props.category, keyword: keyword.value, page: page.value, size: pageSize.value })
  list.value = res.data.records
  total.value = res.data.total
}
const search = () => { page.value = 1; load() }
const open = (row) => router.push(`/content/${row.id}`)
const goHotwordTrend = () => router.push('/policy-hotword-trend')
const sourceOf = (item) => item.sourceName || item.author || '公开来源'
const formatDate = (value) => value ? String(value).slice(0, 10) : '待发布'
const imageStyle = (item) => ({
  backgroundImage: `linear-gradient(180deg, rgba(12, 34, 48, .08), rgba(12, 34, 48, .22)), url(${articleImageOf(item)})`
})

onMounted(load)
watch(() => props.category, () => { page.value = 1; load() })
</script>

<style scoped>
.page { padding: 20px; background: #fff; margin: 20px auto; border-radius: 8px; max-width: 1200px; }
.news-page { background: transparent; padding: 20px; }
.page-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; margin-bottom: 16px; }
h2 { margin: 0 0 8px; }
.page-subtitle { margin: 0; color: #606266; line-height: 1.6; }
.search-box { display: flex; gap: 8px; min-width: 340px; }
.news-featured { display: grid; grid-template-columns: minmax(0, 1.25fr) minmax(320px, .75fr); min-height: 360px; background: #fff; border-radius: 8px; overflow: hidden; cursor: pointer; box-shadow: 0 10px 26px rgba(16, 39, 64, .08); }
.featured-image, .card-image { background-size: cover; background-position: center; }
.featured-image { min-height: 300px; }
.featured-copy { padding: 28px; display: flex; flex-direction: column; justify-content: flex-end; }
.meta { color: #789; font-size: 12px; margin-bottom: 10px; }
.featured-copy h3, .news-card h3 { margin: 0; color: #152b3c; line-height: 1.45; }
.featured-copy h3 { font-size: 26px; }
.featured-copy p, .news-card p { color: #607080; line-height: 1.7; margin: 12px 0 0; }
.news-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 14px; margin-top: 14px; }
.news-card { background: #fff; border-radius: 8px; overflow: hidden; cursor: pointer; box-shadow: 0 4px 16px rgba(16, 39, 64, .06); transition: transform .2s, box-shadow .2s; }
.news-card:hover, .news-featured:hover { box-shadow: 0 14px 28px rgba(16, 39, 64, .12); }
.news-card:hover { transform: translateY(-2px); }
.news-card.wide { grid-column: span 2; display: grid; grid-template-columns: minmax(180px, .9fr) minmax(0, 1.1fr); }
.card-image { aspect-ratio: 16 / 10; }
.news-card.wide .card-image { aspect-ratio: auto; min-height: 100%; }
.card-body { padding: 16px; }
.pager { margin-top: 18px; justify-content: center; }
@media (max-width: 900px) {
  .page-head, .news-featured { grid-template-columns: 1fr; display: grid; }
  .search-box { min-width: 0; width: 100%; }
  .news-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .news-card.wide { grid-column: span 1; display: block; }
  .featured-image { min-height: 0; aspect-ratio: 16 / 9; }
}
@media (max-width: 640px) {
  .page { margin: 12px auto; padding: 14px; }
  .news-grid { grid-template-columns: 1fr; }
  .featured-copy h3 { font-size: 20px; }
  .news-featured { min-height: 0; }
}
</style>
