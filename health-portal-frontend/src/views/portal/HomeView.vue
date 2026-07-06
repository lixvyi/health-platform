<template>
  <div class="container page">
    <el-carousel height="320px" v-if="banners.length">
      <el-carousel-item v-for="b in banners" :key="b.id">
        <div class="banner" :style="{ backgroundImage: `url(${b.imageUrl})` }" @click="go(b.linkUrl)">
          <h2>{{ b.title }}</h2>
        </div>
      </el-carousel-item>
    </el-carousel>

    <section class="intro card">
      <h1>健康大数据应用创新研发中心</h1>
      <p>{{ intro }}</p>
      <DataSourceNotice class="intro-notice" compact />
    </section>

    <section class="card data-entry">
      <div class="card-title">
        开放健康数据
        <router-link to="/data">全部 {{ dataTotal || '45' }} 类</router-link>
      </div>
      <p class="data-desc">
        国家统计局宏观统计 + 上海市医疗机构/基层卫生等 {{ shCount || 20 }} 类 CSV 开放数据
      </p>
      <el-row :gutter="12" v-if="featured.length">
        <el-col :span="8" v-for="item in featured" :key="item.indicatorName">
          <OpenDataChart :title="item.indicatorName" :values="item.values" :unit="item.unit" :height="180" />
        </el-col>
      </el-row>
      <div class="data-actions">
        <el-button type="primary" plain size="small" @click="$router.push('/data')">数据资源目录</el-button>
        <el-button size="small" @click="activeTabSh">上海开放数据</el-button>
      </div>
    </section>

    <el-row :gutter="16">
      <el-col :span="12">
        <div class="card">
          <div class="card-title">新闻中心 <router-link to="/news">更多</router-link></div>
          <ul>
            <li v-for="n in news" :key="n.id" @click="$router.push(`/content/${n.id}`)">
              <span v-if="n.categoryCode === 'POLICY'" class="tag policy">政策</span>
              <span v-else-if="(n.title || '').includes('资源池')" class="tag pool">资源池</span>
              {{ n.title }}
            </li>
          </ul>
        </div>
      </el-col>
      <el-col :span="12">
        <div class="card">
          <div class="card-title">通知公告 <router-link to="/notice">更多</router-link></div>
          <ul>
            <li v-for="n in notices" :key="n.id" @click="$router.push(`/content/${n.id}`)">{{ n.title }}</li>
          </ul>
        </div>
      </el-col>
    </el-row>

    <div class="card">
      <div class="card-title">应用中心</div>
      <el-row :gutter="12">
        <el-col :span="8" v-for="app in apps" :key="app.id">
          <div class="app-item" @click="openApp(app.linkUrl)">
            <img :src="app.iconUrl" alt="" />
            <div>
              <strong>{{ app.name }}</strong>
              <p>{{ app.description }}</p>
            </div>
          </div>
        </el-col>
      </el-row>
    </div>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { portalApi } from '../../api'
import DataSourceNotice from '../../components/DataSourceNotice.vue'
import OpenDataChart from '../../components/OpenDataChart.vue'

const router = useRouter()

const intro = ref('')
const banners = ref([])
const news = ref([])
const notices = ref([])
const apps = ref([])
const featured = ref([])
const dataTotal = ref(0)
const shCount = ref(0)

const activeTabSh = () => router.push({ path: '/data', query: { tab: 'shanghai' } })

onMounted(async () => {
  const [homeRes, featRes, catRes] = await Promise.all([
    portalApi.home(),
    portalApi.openDataFeatured().catch(() => ({ data: [] })),
    portalApi.openDataMeta().catch(() => ({ data: { platforms: [] } }))
  ])
  const res = homeRes
  intro.value = res.data.intro
  banners.value = res.data.banners || []
  news.value = res.data.news || []
  notices.value = res.data.notices || []
  apps.value = res.data.apps || []
  featured.value = featRes.data || []
  const platforms = catRes.data?.platforms || []
  dataTotal.value = platforms.reduce((s, p) => s + (p.datasets?.length || 0), 0)
  shCount.value = platforms.find(p => p.id === 'shanghai')?.datasets?.length || 0
})

const go = (url) => { if (url) window.location.href = url.startsWith('http') ? url : url }
const openApp = (url) => {
  if (url.startsWith('http')) window.open(url)
  else window.location.href = url
}
</script>

<style scoped>
.page { padding: 20px; }
.banner { height: 320px; background-size: cover; background-position: center; display: flex; align-items: flex-end; padding: 24px; color: #fff; text-shadow: 0 2px 8px #000; cursor: pointer; }
.card { background: #fff; border-radius: 8px; padding: 16px; margin-bottom: 16px; box-shadow: 0 1px 4px rgba(0,0,0,.06); }
.intro h1 { margin: 0 0 8px; color: #1a6fb5; }
.intro-notice { margin-top: 12px; }
.data-entry .data-desc { margin: 0 0 12px; color: #666; font-size: 14px; line-height: 1.6; }
.data-actions { margin-top: 12px; display: flex; gap: 8px; }
.card-title { display: flex; justify-content: space-between; font-weight: 700; margin-bottom: 12px; }
ul { list-style: none; padding: 0; margin: 0; }
li { padding: 8px 0; border-bottom: 1px dashed #eee; cursor: pointer; }
li:hover { color: #1a6fb5; }
.tag { font-size: 11px; padding: 1px 6px; border-radius: 4px; margin-right: 6px; }
.tag.policy { background: #e8f4fc; color: #1a6fb5; }
.tag.pool { background: #f0f9eb; color: #52a823; }
.app-item { display: flex; gap: 12px; padding: 12px; border: 1px solid #eee; border-radius: 8px; cursor: pointer; margin-bottom: 12px; }
.app-item img { width: 48px; height: 48px; border-radius: 8px; }
.app-item p { margin: 4px 0 0; font-size: 12px; color: #666; }
</style>
