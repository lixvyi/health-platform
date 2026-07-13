<template>
  <div class="container page">
    <DataPoolNav />
    <h2>{{ arch.title || '统一数据资源池与计算平台' }}</h2>
    <p class="lead">{{ arch.description }}</p>

    <el-row :gutter="12" class="stats-row" v-if="arch.stats">
      <el-col :xs="12" :sm="8" :md="4" v-for="s in statCards" :key="s.label">
        <div class="stat-card">
          <div class="num">{{ s.value }}</div>
          <div class="label">{{ s.label }}</div>
        </div>
      </el-col>
    </el-row>

    <section class="block" v-if="resourceDatasets.length">
      <div class="block-head">
        <div>
          <h3>健康资源导入数据集</h3>
          <p class="hint">这里展示外部导入表格、标准化JSON和入库状态。后续每次导入数据，都应同步更新这张资源池清单。</p>
        </div>
      </div>
      <el-table :data="resourceDatasets" size="small" border>
        <el-table-column prop="datasetName" label="数据集" min-width="190" />
        <el-table-column prop="sourceName" label="来源" min-width="160" />
        <el-table-column prop="sourceType" label="类型" width="100" />
        <el-table-column prop="recordCount" label="记录数" width="95" align="right" />
        <el-table-column prop="errorCount" label="错误/跳过" width="95" align="right" />
        <el-table-column label="状态" width="105">
          <template #default="{ row }">
            <el-tag size="small" :type="statusType(row.status)">{{ statusText(row.status) }}</el-tag>
          </template>
        </el-table-column>
        <el-table-column prop="lastImportedAt" label="最近更新" min-width="150" />
        <el-table-column label="来源文件/链接" min-width="220">
          <template #default="{ row }">
            <a v-if="row.officialUrl" :href="row.officialUrl" target="_blank" rel="noopener noreferrer">官方入口</a>
            <span v-else>{{ row.sourceFile || '—' }}</span>
          </template>
        </el-table-column>
      </el-table>
    </section>

    <section class="block">
      <h3>平台分层架构</h3>
      <div class="layers">
        <div class="layer" v-for="l in arch.layers" :key="l.name">
          <div class="layer-head">
            <strong>{{ l.name }}</strong>
            <el-tag size="small" :type="l.status?.includes('预留') ? 'info' : 'success'">{{ l.status }}</el-tag>
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
      <h3>互联网公开数据采集</h3>
      <p class="hint">
        仅采集公开网页信息，保留来源和原文链接；对无法合法稳定获取的数据源，不写入虚构内容。
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
      <el-empty v-else description="暂无互联网采集记录" />
    </section>

    <section class="block">
      <h3>开放数据文件池</h3>
      <ul>
        <li>国家统计局：年度卫生健康相关Excel数据，解析为指标资源。</li>
        <li>地方开放平台：CSV/Excel表格资源，统一纳入资源目录。</li>
        <li>外部导入：医院名录、三级公立综合医院、复旦医院分档、医保药品目录、疫苗表格。</li>
      </ul>
      <el-button type="primary" @click="$router.push('/data')">进入数据资源目录</el-button>
    </section>

    <section class="block" v-if="bigdata">
      <h3>大数据计算扩展层</h3>
      <el-alert :title="bigdata.message" type="info" :closable="false" show-icon class="mb" />
      <p>存储：{{ bigdata.storageLayer }} · 计算：{{ bigdata.computeLayer }}</p>
      <p v-if="bigdata.etlEngine">最近ETL引擎：{{ bigdata.etlEngine }} · 上海记录合计：{{ bigdata.etlSummary?.shanghaiRecordSum }}</p>
      <p>Spark UI（Docker启动后）：<a :href="bigdata.sparkUrl" target="_blank" rel="noopener noreferrer">{{ bigdata.sparkUrl }}</a></p>
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
import DataPoolNav from '../../components/DataPoolNav.vue'
import { portalApi } from '../../api'

const arch = ref({ layers: [], stats: {}, techStack: [], datasets: [] })
const feeds = ref([])
const collect = ref(null)
const bigdata = ref(null)

const resourceDatasets = computed(() => arch.value.datasets || [])

const statCards = computed(() => {
  const s = arch.value.stats || {}
  return [
    { label: '国家统计局数据集', value: s.nbsDatasets || 0 },
    { label: '地方开放数据集', value: s.shanghaiDatasets || 0 },
    { label: '互联网采集条目', value: s.internetItems || 0 },
    { label: '开放文件数', value: s.openDataFiles || 0 },
    { label: '健康资源数据集', value: s.healthResourceDatasets || 0 },
    { label: '资源池总记录', value: s.totalRecords || 0 }
  ]
})

const statusText = (status) => {
  const map = {
    SUCCESS: '已入库',
    IMPORTED: '已入库',
    EXPORTED: '已标准化',
    DRY_RUN: '仅审计',
    FAILED: '失败'
  }
  return map[status] || status || '未知'
}

const statusType = (status) => {
  if (status === 'SUCCESS') return 'success'
  if (status === 'IMPORTED') return 'success'
  if (status === 'EXPORTED') return 'warning'
  if (status === 'FAILED') return 'danger'
  return 'info'
}

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
.page { padding: 24px; margin: 20px auto; max-width: 1200px; }
.lead { color: #555; line-height: 1.8; margin-bottom: 20px; }
.stats-row { margin-bottom: 20px; }
.stat-card {
  background: #fff;
  border-radius: 8px;
  padding: 16px;
  text-align: center;
  box-shadow: 0 1px 4px rgba(0,0,0,.06);
  margin-bottom: 12px;
}
.stat-card .num { font-size: 22px; font-weight: 700; color: #1a6fb5; }
.stat-card .label { font-size: 12px; color: #666; margin-top: 4px; }
.block { background: #fff; border-radius: 8px; padding: 20px; margin-bottom: 16px; }
.block-head { display: flex; justify-content: space-between; align-items: flex-start; gap: 12px; }
.block h3 { color: #1a6fb5; margin: 0 0 12px; }
.layers { display: grid; grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); gap: 12px; }
.layer { border: 1px solid #e8eef5; border-radius: 8px; padding: 12px; background: #fafcff; }
.layer-head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 6px; }
.role { font-size: 13px; color: #666; margin: 0 0 8px; }
.layer ul,
.block ul { margin: 0; padding-left: 18px; font-size: 13px; line-height: 1.7; color: #444; }
.tech { margin-top: 16px; font-size: 13px; color: #888; }
.hint { font-size: 14px; color: #555; line-height: 1.7; margin: 0 0 12px; }
.feed-list { padding-left: 18px; line-height: 1.8; }
.feed-list a,
.block a { color: #1a6fb5; text-decoration: none; }
.collect p { font-size: 13px; color: #888; }
.mb { margin-bottom: 12px; }
</style>
