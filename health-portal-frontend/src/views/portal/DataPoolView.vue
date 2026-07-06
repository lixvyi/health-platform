<template>
  <div class="container page">
    <h2>统一数据资源池与计算平台</h2>
    <p class="lead">{{ arch.description }}</p>

    <el-row :gutter="12" class="stats-row" v-if="arch.stats">
      <el-col :span="4" v-for="s in statCards" :key="s.label">
        <div class="stat-card">
          <div class="num">{{ s.value }}</div>
          <div class="label">{{ s.label }}</div>
        </div>
      </el-col>
    </el-row>

    <section class="block">
      <h3>平台分层架构</h3>
      <div class="layers">
        <div class="layer" v-for="l in arch.layers" :key="l.name">
          <div class="layer-head">
            <strong>{{ l.name }}</strong>
            <el-tag size="small" :type="l.status.includes('预留') ? 'info' : 'success'">{{ l.status }}</el-tag>
          </div>
          <p class="role">{{ l.role }}</p>
          <ul>
            <li v-for="c in l.components" :key="c">{{ c }}</li>
          </ul>
        </div>
      </div>
      <p class="tech">技术栈：{{ arch.techStack?.join(' · ') }}</p>
    </section>

    <section class="block">
      <h3>互联网公开数据采集（爬虫）</h3>
      <p class="hint">
        已实现合规采集：<strong>中国政府网数据栏目</strong> + <strong>国家统计局官网公开页</strong>。
        间隔 ≥3 秒，不爬政务内网，不使用 API 密钥。
      </p>
      <el-collapse v-if="feeds.length">
        <el-collapse-item v-for="f in feeds" :key="f.sourceId" :title="`${f.sourceName}（${f.items?.length || 0} 条）`">
          <DataSourceNotice compact :source="f.sourceName.replace(/-.*/, '')" :license="f.attribution" />
          <ul class="feed-list">
            <li v-for="item in f.items?.slice(0, 15)" :key="item.url">
              <a :href="item.url" target="_blank" rel="noopener noreferrer">{{ item.title }}</a>
            </li>
          </ul>
        </el-collapse-item>
      </el-collapse>
    </section>

    <section class="block">
      <h3>开放数据文件池（依法下载）</h3>
      <ul>
        <li>国家统计局：25 类年度卫生 Excel → 解析为 JSON 指标库</li>
        <li>上海市开放平台：20 类 CSV（医疗机构、预防接种等）→ 表格资源目录</li>
      </ul>
      <el-button type="primary" @click="$router.push('/data')">进入数据资源目录</el-button>
    </section>

    <section class="block" v-if="bigdata">
      <h3>大数据计算层（Spark / MinIO）</h3>
      <el-alert :title="bigdata.message" type="info" :closable="false" show-icon class="mb" />
      <p>存储：{{ bigdata.storageLayer }} · 计算：{{ bigdata.computeLayer }}</p>
      <p v-if="bigdata.etlEngine">最近 ETL 引擎：{{ bigdata.etlEngine }} · 上海记录合计：{{ bigdata.etlSummary?.shanghaiRecordSum }}</p>
      <p>Spark UI（Docker 启动后）：<a :href="bigdata.sparkUrl" target="_blank">{{ bigdata.sparkUrl }}</a></p>
    </section>

    <section class="block collect" v-if="collect">
      <h3>最近采集任务</h3>
      <p>完成时间：{{ collect.finishedAt || '—' }}</p>
      <el-table :data="collect.sources || []" size="small">
        <el-table-column prop="sourceName" label="来源" min-width="200" />
        <el-table-column prop="status" label="状态" width="90" />
        <el-table-column prop="recordCount" label="记录数" width="90" />
      </el-table>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import DataSourceNotice from '../../components/DataSourceNotice.vue'
import { portalApi } from '../../api'

const arch = ref({ layers: [], stats: {}, techStack: [] })
const feeds = ref([])
const collect = ref(null)
const bigdata = ref(null)

const statCards = computed(() => {
  const s = arch.value.stats || {}
  return [
    { label: '国家统计局数据集', value: s.nbsDatasets || 0 },
    { label: '上海开放数据集', value: s.shanghaiDatasets || 0 },
    { label: '互联网采集条', value: s.internetItems || 0 },
    { label: '开放文件数', value: s.openDataFiles || 0 },
    { label: '资源池总记录', value: s.totalRecords || 0 }
  ]
})

onMounted(async () => {
  const [a, f, c, b] = await Promise.all([
    portalApi.dataPoolArchitecture(),
    portalApi.dataPoolInternet(),
    portalApi.dataPoolCollectStatus(),
    portalApi.dataPoolBigDataStatus()
  ])
  arch.value = a.data || {}
  feeds.value = f.data || []
  collect.value = c.data
  bigdata.value = b.data
})
</script>

<style scoped>
.page { padding: 24px; margin: 20px auto; max-width: 1100px; }
.lead { color: #555; line-height: 1.8; margin-bottom: 20px; }
.stats-row { margin-bottom: 20px; }
.stat-card { background: #fff; border-radius: 8px; padding: 16px; text-align: center; box-shadow: 0 1px 4px rgba(0,0,0,.06); }
.stat-card .num { font-size: 22px; font-weight: 700; color: #1a6fb5; }
.stat-card .label { font-size: 12px; color: #666; margin-top: 4px; }
.block { background: #fff; border-radius: 8px; padding: 20px; margin-bottom: 16px; }
.block h3 { color: #1a6fb5; margin: 0 0 12px; }
.layers { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 12px; }
.layer { border: 1px solid #e8eef5; border-radius: 8px; padding: 12px; background: #fafcff; }
.layer-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 6px; }
.role { font-size: 13px; color: #666; margin: 0 0 8px; }
.layer ul { margin: 0; padding-left: 18px; font-size: 13px; line-height: 1.7; color: #444; }
.tech { margin-top: 16px; font-size: 13px; color: #888; }
.hint { font-size: 14px; color: #555; line-height: 1.7; }
.feed-list { padding-left: 18px; line-height: 1.8; }
.feed-list a { color: #1a6fb5; text-decoration: none; }
.collect p { font-size: 13px; color: #888; }
</style>
