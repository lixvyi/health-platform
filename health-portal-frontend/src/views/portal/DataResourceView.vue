<template>
  <div class="container page">
    <h2>数据资源目录</h2>
    <p class="lead">
      已导入 <strong>{{ totalDatasets }}</strong> 类开放健康数据：
      国家统计局年度卫生数据 {{ nbsCount }} 类 + 上海市公共数据 {{ shCount }} 类。
      均来自官网<strong>下载 CSV/Excel</strong>，不采用爬虫。
    </p>

    <el-tabs v-model="activeTab" class="tabs">
      <el-tab-pane label="国家统计局" name="nbs">
        <DataSourceNotice class="mb" source="国家统计局" license="来源：国家统计局" link="https://data.stats.gov.cn" />

        <section class="block">
          <h3>核心指标趋势</h3>
          <el-row :gutter="16">
            <el-col :span="8" v-for="item in featured" :key="item.indicatorName">
              <OpenDataChart :title="item.indicatorName" :values="item.values" :unit="item.unit" :height="240" />
            </el-col>
          </el-row>
        </section>

        <section class="block">
          <h3>数据集（{{ nbsCount }}）</h3>
          <el-table :data="nbsDatasets" stripe @row-click="row => openDataset(row.id)">
            <el-table-column prop="fileIndex" label="#" width="60" />
            <el-table-column prop="title" label="主题" min-width="160" />
            <el-table-column prop="timeRange" label="时间" min-width="140" />
            <el-table-column prop="indicatorCount" label="指标数" width="90" />
            <el-table-column label="操作" width="100">
              <template #default="{ row }">
                <el-button type="primary" link @click.stop="openDataset(row.id)">图表</el-button>
              </template>
            </el-table-column>
          </el-table>
        </section>
      </el-tab-pane>

      <el-tab-pane label="上海市开放数据" name="shanghai">
        <DataSourceNotice
          class="mb"
          source="上海市公共数据开放平台"
          license="来源：上海市公共数据开放平台"
          link="https://data.sh.gov.cn"
        />

        <section class="block">
          <h3>数据集概览（{{ shCount }}）</h3>
          <el-row :gutter="12" class="summary-row">
            <el-col :span="6" v-for="s in shSummary" :key="s.label">
              <div class="summary-card">
                <div class="num">{{ s.value }}</div>
                <div class="label">{{ s.label }}</div>
              </div>
            </el-col>
          </el-row>
        </section>

        <section class="block">
          <el-table :data="shDatasets" stripe @row-click="row => openDataset(row.id)">
            <el-table-column prop="id" label="编号" width="72" />
            <el-table-column prop="title" label="数据集" min-width="200" />
            <el-table-column prop="category" label="类别" width="100" />
            <el-table-column prop="district" label="区县" width="90" />
            <el-table-column prop="rowCount" label="记录数" width="80" />
            <el-table-column prop="openType" label="开放类型" width="110" />
            <el-table-column label="操作" width="100">
              <template #default="{ row }">
                <el-button type="primary" link @click.stop="openDataset(row.id)">查看</el-button>
              </template>
            </el-table-column>
          </el-table>
        </section>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="dialogVisible" :title="activeDataset?.title" width="920px" destroy-on-close top="5vh">
      <template v-if="activeDataset">
        <p class="dialog-meta">
          {{ activeDataset.district || '' }}
          {{ activeDataset.category ? ' · ' + activeDataset.category : '' }}
          · {{ activeDataset.openType || '开放数据' }}
          · 共 {{ activeDataset.rowCount || 0 }} 条
        </p>
        <DataSourceNotice
          compact
          :source="activeDataset.source || '数据来源'"
          :license="activeDataset.attribution"
          :link="activeDataset.sourceUrl"
        />

        <template v-if="activeDataset.type === 'table'">
          <OpenDataTable
            :columns="activeDataset.columns"
            :rows="activeDataset.rows"
            :total="activeDataset.rowCount"
          />
        </template>

        <template v-else-if="activeDataset.type === 'stats' || activeDataset.indicators">
          <el-select v-model="selectedIndicator" style="width:100%;margin:12px 0">
            <el-option
              v-for="ind in activeDataset.indicators"
              :key="ind.name"
              :label="ind.name"
              :value="ind.name"
            />
          </el-select>
          <OpenDataChart
            v-if="activeIndicator"
            :title="activeIndicator.name"
            :values="activeIndicator.values"
            :height="300"
            chart-type="bar"
            :show-notice="false"
          />
        </template>

        <template v-else>
          <el-select v-model="selectedIndicator" style="width:100%;margin:12px 0">
            <el-option
              v-for="ind in activeDataset.indicators"
              :key="ind.name"
              :label="ind.name"
              :value="ind.name"
            />
          </el-select>
          <OpenDataChart
            v-if="activeIndicator"
            :title="activeIndicator.name"
            :values="activeIndicator.values"
            :unit="indicatorUnit"
            :height="320"
            chart-type="bar"
            :show-notice="false"
          />
        </template>
      </template>
    </el-dialog>

    <section class="block disclaimer">
      <h3>数据使用声明</h3>
      <ul>
        <li>国家统计局数据引用须注明「来源：国家统计局」，详见 <router-link to="/data-agreement">用户协议</router-link>。</li>
        <li>上海市数据来自 <a href="https://data.sh.gov.cn" target="_blank" rel="noopener">data.sh.gov.cn</a> 无条件开放数据集，注明「来源：上海市公共数据开放平台」。</li>
        <li>本门户展示机构名称、地址、电话等均为政府已公开信息，不得用于商业用途或转供第三方。</li>
      </ul>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import DataSourceNotice from '../../components/DataSourceNotice.vue'
import OpenDataChart from '../../components/OpenDataChart.vue'
import OpenDataTable from '../../components/OpenDataTable.vue'
import { portalApi } from '../../api'

const route = useRoute()
const activeTab = ref('nbs')
const featured = ref([])
const platforms = ref([])
const dialogVisible = ref(false)
const activeDataset = ref(null)
const selectedIndicator = ref('')

const nbsPlatform = computed(() => platforms.value.find(p => p.id === 'nbs'))
const shPlatform = computed(() => platforms.value.find(p => p.id === 'shanghai'))
const nbsDatasets = computed(() => nbsPlatform.value?.datasets || [])
const shDatasets = computed(() => shPlatform.value?.datasets || [])
const nbsCount = computed(() => nbsDatasets.value.length)
const shCount = computed(() => shDatasets.value.length)
const totalDatasets = computed(() => nbsCount.value + shCount.value)

const shSummary = computed(() => {
  const list = shDatasets.value
  const institutions = list.filter(d => d.category === '医疗机构').reduce((s, d) => s + d.rowCount, 0)
  const districts = new Set(list.map(d => d.district).filter(Boolean)).size
  return [
    { label: '数据集', value: list.length },
    { label: '医疗机构记录', value: institutions },
    { label: '覆盖区县', value: districts },
    { label: '开放类型', value: '无条件' }
  ]
})

const activeIndicator = computed(() =>
  activeDataset.value?.indicators?.find(i => i.name === selectedIndicator.value)
)

const indicatorUnit = computed(() => {
  const name = activeIndicator.value?.name || ''
  const m = name.match(/\(([^)]+)\)/)
  return m ? m[1] : ''
})

onMounted(async () => {
  if (route.query.tab === 'shanghai') activeTab.value = 'shanghai'
  const [catRes, featRes] = await Promise.all([
    portalApi.openDataMeta(),
    portalApi.openDataFeatured()
  ])
  platforms.value = catRes.data?.platforms || []
  featured.value = featRes.data || []
})

watch(() => route.query.tab, (tab) => {
  if (tab === 'shanghai') activeTab.value = 'shanghai'
})

const openDataset = async (id) => {
  const res = await portalApi.openDataDetail(id)
  activeDataset.value = res.data
  selectedIndicator.value = res.data?.indicators?.[0]?.name || ''
  dialogVisible.value = true
}
</script>

<style scoped>
.page { padding: 24px; margin: 20px auto; max-width: 1100px; }
.lead { color: #555; line-height: 1.8; margin-bottom: 16px; }
.tabs { margin-bottom: 16px; }
.mb { margin-bottom: 16px; }
.block { background: #fff; border-radius: 8px; padding: 20px; margin-bottom: 16px; }
.block h3 { color: #1a6fb5; margin: 0 0 16px; font-size: 16px; }
.summary-row { margin-bottom: 8px; }
.summary-card { background: #f0f7ff; border-radius: 8px; padding: 16px; text-align: center; }
.summary-card .num { font-size: 24px; font-weight: 700; color: #1a6fb5; }
.summary-card .label { font-size: 12px; color: #666; margin-top: 4px; }
.dialog-meta { color: #888; font-size: 13px; margin: 0 0 8px; }
.disclaimer ul { padding-left: 20px; line-height: 1.8; color: #444; }
.disclaimer a { color: #1a6fb5; }
</style>
