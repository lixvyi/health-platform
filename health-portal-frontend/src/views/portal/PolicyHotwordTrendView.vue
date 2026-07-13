<template>
  <div class="container page">
    <div class="page-head">
      <h2>📊 卫生政策热词演变分析</h2>
      <p class="page-subtitle">基于国家卫健委、国家医保局等公开政策文件，提取高频关键词并展示其关注度随时间的变化趋势。</p>
    </div>

    <el-card shadow="never" class="controls-card">
      <div class="controls">
        <div class="control-item">
          <span class="label">年份范围</span>
          <el-slider
            v-model="yearRange"
            :min="minYear"
            :max="maxYear"
            range
            :marks="yearMarks"
            style="width:320px"
            @change="updateCharts"
          />
        </div>
      </div>
    </el-card>

    <el-tabs v-model="activeTab" type="border-card" class="chart-tabs">
      <el-tab-pane label="📈 趋势折线图" name="line">
        <div class="chart-hint" style="display:flex;align-items:center;gap:12px;flex-wrap:wrap;">
          <span><el-icon><InfoFilled /></el-icon> 选择关键词查看趋势，图例支持点击切换。</span>
          <el-select
            v-model="selectedKeywords"
            multiple
            collapse-tags
            collapse-tags-tooltip
            placeholder="选择要对比的关键词"
            style="width:360px"
            @change="updateLineChart"
          >
            <el-option
              v-for="kw in allKeywords"
              :key="kw"
              :label="kw"
              :value="kw"
            />
          </el-select>
        </div>
        <div ref="lineChartRef" class="chart-container"></div>
      </el-tab-pane>
      <el-tab-pane label="🌊 主题河流图" name="river">
        <div class="chart-hint">
          <el-icon><InfoFilled /></el-icon>
          <span>河流图展示不同类别词汇的年度占比变化，宽度代表该类词汇在当年所有热词中的关注度比重。</span>
        </div>
        <div ref="riverChartRef" class="chart-container"></div>
      </el-tab-pane>
      <el-tab-pane label="🌐 热词共现网络" name="graph">
        <div class="chart-hint" style="display:flex;align-items:center;justify-content:space-between;">
          <span><el-icon><InfoFilled /></el-icon> 节点大小代表词频，连线粗细代表共现强度，可拖拽交互。</span>
          <el-select v-model="graphYear" style="width:120px" @change="updateGraphChart">
            <el-option v-for="y in graphYears" :key="y" :label="y + '年'" :value="y" />
          </el-select>
        </div>
        <div ref="graphChartRef" class="chart-container"></div>
      </el-tab-pane>
    </el-tabs>

  </div>

  <!-- 热词点击联动抽屉 -->
  <el-drawer
    v-model="drawerVisible"
    :title="drawerTitle"
    direction="rtl"
    size="500px"
  >
    <div v-if="drawerLoading" style="text-align:center;padding:40px">
      <el-icon class="is-loading" :size="24"><Loading /></el-icon>
      <p style="margin-top:12px;color:#909399">正在搜索相关政策...</p>
    </div>
    <div v-else-if="drawerError" style="text-align:center;padding:40px;color:#f56c6c">
      <el-icon :size="24"><WarningFilled /></el-icon>
      <p style="margin-top:12px">{{ drawerError }}</p>
    </div>
    <div v-else-if="!drawerList.length" style="text-align:center;padding:40px;color:#909399">
      <el-icon :size="24"><Search /></el-icon>
      <p style="margin-top:12px">未找到包含「{{ clickedWord }}」的政策标题</p>
    </div>
    <el-timeline v-else>
      <el-timeline-item
        v-for="(item, i) in drawerList"
        :key="i"
        :timestamp="item.date"
        placement="top"
      >
        <el-link
          :href="item.link"
          target="_blank"
          type="primary"
          :underline="false"
          style="font-size:14px;line-height:1.5"
        >{{ item.title }}</el-link>
      </el-timeline-item>
    </el-timeline>
    <div v-if="drawerList.length > 0" style="text-align:center;padding:16px 0 0;color:#909399;font-size:13px">
      共 {{ drawerList.length }} 条结果
    </div>
  </el-drawer>
</template>

<script setup>
import { ref, onMounted, onBeforeUnmount, nextTick, watch } from 'vue'
import { InfoFilled, Loading, WarningFilled, Search } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import { portalApi } from '../../api'

// 热词分类映射 — 每个关键词归入唯一的大类
const categoryMap = {
  '传染病防控': ['疫情', '肺炎', '新冠', '防控', '感染', '核酸', '检测', '消毒', '防护', '联防'],
  '医疗服务': ['医疗', '服务', '诊疗', '临床', '护理', '医务人员', '技术', '指南', '医院'],
  '公共卫生': ['公共卫生', '爱国卫生', '防治', '食品', '慢性病', '人群', '物质'],
  '药品管理': ['药品', '医药', '用药', '购销', '保障'],
  '机构管理': ['医疗机构', '机构', '基层', '部门', '行政部门', '人员', '专业'],
  '政策法规': ['国家', '办公厅', '意见', '方案', '规范', '标准', '指标体系', '修订', '指导', '解读'],
  '体系建设': ['医疗卫生', '改革', '强化', '完善', '建设', '质量', '发展', '高质量', '应用'],
  '管理机制': ['管理', '机制', '评估', '推进', '行动', '项目', '组织', '专项', '纠风'],
  '重点人群': ['患者', '老年人', '儿童', '照护', '县域', '城市']
}

// 反向查找：词 → 类别
const wordToCategory = {}
for (const [cat, words] of Object.entries(categoryMap)) {
  for (const w of words) {
    wordToCategory[w] = cat
  }
}

const allKeywords = Object.keys(wordToCategory)
const minYear = 2019
const maxYear = 2026
const yearRange = ref([2019, 2026])
const selectedKeywords = ref([])
const activeTab = ref('line')

const yearMarks = {}
for (let y = minYear; y <= maxYear; y++) {
  yearMarks[y] = String(y)
}

const lineChartRef = ref(null)
const riverChartRef = ref(null)
const graphChartRef = ref(null)
let lineChart = null
let riverChart = null
let graphChart = null

// 共现网络状态
const graphYear = ref(2025)
const graphYears = []
for (let y = minYear; y <= maxYear; y++) graphYears.push(y)

// 抽屉联动状态
const drawerVisible = ref(false)
const drawerLoading = ref(false)
const drawerError = ref('')
const drawerTitle = ref('')
const drawerList = ref([])
const clickedWord = ref('')

// 原始数据存储
let rawData = {}

// 获取年份范围内的年份列表
const yearsInRange = () => {
  const [start, end] = yearRange.value
  const years = []
  for (let y = start; y <= end; y++) years.push(y)
  return years
}

// 加载数据
const loadData = async () => {
  try {
    const resp = await fetch('/hotwords_data.json')
    rawData = await resp.json()
    // 按词频总和降序排列，取前10个最高频词
    const wordFreq = allKeywords.map(k => {
      const data = rawData[k]
      if (!data) return { word: k, total: 0 }
      const total = data.reduce((s, d) => s + d.value, 0)
      return { word: k, total }
    })
    wordFreq.sort((a, b) => b.total - a.total)
    const topWords = wordFreq.filter(w => w.total > 0).slice(0, 10).map(w => w.word)
    if (topWords.length) selectedKeywords.value = topWords
    // 等待 DOM 渲染后初始化两个图表
    nextTick(() => {
      updateLineChart()
      updateRiverChart()
    })
  } catch (e) {
    console.error('加载热词数据失败:', e)
  }
}

// 更新两个图表
const updateCharts = () => {
  updateLineChart()
  updateRiverChart()
}

// --- 折线图 ---
const updateLineChart = () => {
  if (!lineChartRef.value) return
  if (!lineChart) {
    lineChart = echarts.init(lineChartRef.value)
    window.addEventListener('resize', () => lineChart.resize())
  }

  const years = yearsInRange()
  const colors = ['#5470c6', '#91cc75', '#fac858', '#ee6666', '#73c0de', '#3ba272', '#fc8452', '#9a60b4', '#ea7ccc']
  const series = []
  const legendData = []

  selectedKeywords.value.forEach((kw, idx) => {
    const data = rawData[kw]
    if (!data) return
    const values = years.map(y => {
      const item = data.find(d => d.year === y)
      return item ? item.value : 0
    })
    if (values.some(v => v > 0)) {
      legendData.push(kw)
      series.push({
        name: kw,
        type: 'line',
        smooth: true,
        symbol: 'circle',
        symbolSize: 12,
        lineStyle: { width: 2.5 },
        emphasis: { focus: 'series' },
        data: values
      })
    }
  })

  lineChart.setOption({
    tooltip: {
      trigger: 'axis',
      formatter: params => {
        let html = `<strong>${params[0].axisValue}年</strong><br/>`
        params.forEach(p => {
          html += `<span style="display:inline-block;width:10px;height:10px;border-radius:50%;background:${p.color};margin-right:6px;"></span>${p.seriesName}: ${(p.value * 100).toFixed(2)}%<br/>`
        })
        return html
      }
    },
    legend: {
      data: legendData,
      top: 0,
      type: 'scroll',
      selector: [
        { type: 'all', title: '全选' },
        { type: 'inverse', title: '反选' }
      ]
    },
    grid: { left: 60, right: 30, bottom: 30, top: 50 },
    xAxis: {
      type: 'category',
      data: years.map(String),
      axisLabel: { fontSize: 13 }
    },
    yAxis: {
      type: 'value',
      name: '提及比例',
      axisLabel: {
        formatter: v => (v * 100).toFixed(0) + '%'
      }
    },
    color: colors,
    series
  })

  // 绑定点击事件：点击数据点 → 查政策
  lineChart.off('click')
  lineChart.on('click', 'series', (params) => {
    if (params && params.seriesName) {
      openPolicyDrawer(params.seriesName)
    }
  })
}

// 打开政策抽屉
const openPolicyDrawer = async (word) => {
  clickedWord.value = word
  drawerTitle.value = `包含「${word}」的政策文件`
  drawerVisible.value = true
  drawerLoading.value = true
  drawerError.value = ''
  drawerList.value = []
  try {
    const res = await portalApi.policiesByWord(word)
    drawerList.value = res.data || []
  } catch (e) {
    drawerError.value = '请求失败：' + (e.message || '未知错误')
  } finally {
    drawerLoading.value = false
  }
}

// --- 共现网络图 ---
const updateGraphChart = async () => {
  const container = graphChartRef.value
  if (!container) return
  if (!graphChart) {
    // 容器隐藏时传默认尺寸
    const w = container.clientWidth || container.offsetWidth || 800
    const h = container.clientHeight || container.offsetHeight || 520
    graphChart = echarts.init(container, null, { width: w, height: h })
    window.addEventListener('resize', () => {
      if (graphChart && !graphChart.isDisposed()) graphChart.resize()
    })
  }
  // 显示加载状态
  graphChart.showLoading('default', { text: '加载中...' })
  try {
    const res = await portalApi.cooccurrence(graphYear.value)
    const { nodes, edges } = res.data || { nodes: [], edges: [] }
    if (!nodes.length) {
      graphChart.hideLoading()
      graphChart.setOption({
        title: { text: '该年份暂无共现数据', left: 'center', top: 'center' },
        series: []
      })
      return
    }
    graphChart.setOption({
      title: { show: false },
      tooltip: {
        formatter: params => {
          if (params.dataType === 'node') {
            return `<strong>${params.name}</strong><br/>词频: ${params.data.frequency}`
          }
          if (params.dataType === 'edge') {
            return `共现: ${params.data.source} ↔ ${params.data.target}<br/>强度: ${params.data.weight}`
          }
          return ''
        }
      },
      series: [{
        type: 'graph',
        layout: 'force',
        force: {
          repulsion: 300,
          edgeLength: [80, 200],
          gravity: 0.1,
          friction: 0.2,
          layoutAnimation: true
        },
        roam: true,
        draggable: true,
        data: nodes,
        edges: edges,
        categories: [{ name: '热词' }],
        label: {
          show: true,
          position: 'right',
          fontSize: 12,
          color: '#333'
        },
        edgeSymbol: ['none', 'none'],
        edgeLabel: {
          show: false
        },
        lineStyle: {
          color: 'source',
          curveness: 0.3,
          width: 1,
          opacity: 0.6
        },
        emphasis: {
          focus: 'adjacency',
          lineStyle: { width: 3 }
        }
      }]
    })
    // 首次加载后确保尺寸适配
    graphChart.resize()
  } catch (e) {
    console.error('加载共现数据失败:', e)
    graphChart.setOption({ title: { text: '加载失败', left: 'center', top: 'center' }, series: [] })
  } finally {
    graphChart.hideLoading()
  }
}

// --- 主题河流图 ---
const updateRiverChart = () => {
  const container = riverChartRef.value
  if (!container) return
  if (!riverChart) {
    // 容器隐藏时 clientWidth 为 0，传默认尺寸确保 init 成功
    const w = container.clientWidth || container.offsetWidth || 800
    const h = container.clientHeight || container.offsetHeight || 520
    riverChart = echarts.init(container, null, { width: w, height: h })
    window.addEventListener('resize', () => {
      if (riverChart && !riverChart.isDisposed()) riverChart.resize()
    })
  }

  const years = yearsInRange()
  const categories = Object.keys(categoryMap)

  // 计算每年每个类别的总词频
  const catYearTotal = {}
  const yearGrandTotal = {}
  for (const y of years) {
    catYearTotal[y] = {}
    yearGrandTotal[y] = 0
    for (const cat of categories) {
      let sum = 0
      for (const w of categoryMap[cat]) {
        const d = rawData[w]
        if (d) {
          const item = d.find(e => e.year === y)
          if (item) sum += item.value
        }
      }
      catYearTotal[y][cat] = sum
      yearGrandTotal[y] += sum
    }
  }

  // 构建 ThemeRiver 数据：[year, value, category]
  // time 列使用 ISO 日期字符串以配合 singleAxis.type: 'time'
  const riverData = []
  for (const y of years) {
    const grand = yearGrandTotal[y] || 1
    for (const cat of categories) {
      const proportion = catYearTotal[y][cat] / grand
      if (proportion > 0) {
        riverData.push([`${y}-01-01`, +(proportion * 100).toFixed(2), cat])
      }
    }
  }

  // 从 riverData 提取实际存在的类别，排除全零类别
  const activeCategories = [...new Set(riverData.map(d => d[2]))]

  const categoryColors = {
    '传染病防控': '#ee6666',
    '医疗服务': '#5470c6',
    '公共卫生': '#91cc75',
    '药品管理': '#fac858',
    '机构管理': '#73c0de',
    '政策法规': '#3ba272',
    '体系建设': '#fc8452',
    '管理机制': '#9a60b4',
    '重点人群': '#ea7ccc'
  }

  riverChart.setOption({
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'line' },
      formatter: params => {
        if (!params || !params.length) return ''
        const year = params[0].data[0].slice(0, 4)
        let html = `<strong>${year}年</strong><br/>`
        // 显示该年份下所有类别的占比
        params.forEach(p => {
          const color = p.color || '#999'
          html += `<span style="display:inline-block;width:10px;height:10px;border-radius:50%;background:${color};margin-right:6px;"></span>${p.data[2]}: ${p.data[1]}%<br/>`
        })
        return html
      }
    },
    legend: {
      data: activeCategories,
      top: 0,
      type: 'scroll'
    },
    singleAxis: {
      type: 'time',
      axisLabel: {
        formatter: {
          year: '{yyyy}'
        },
        fontSize: 13
      },
      boundaryGap: ['5%', '5%']
    },
    series: [{
      type: 'themeRiver',
      color: activeCategories.map(c => categoryColors[c] || '#999'),
      label: {
        show: true,
        position: 'inside',
        fontSize: 11,
        color: '#fff',
        formatter: p => p.data[2]
      },
      data: riverData
    }]
  })
}

onMounted(() => {
  nextTick(loadData)
})

// Tab 切换时 resize 可见图表
watch(activeTab, (tab) => {
  nextTick(() => {
    if (tab === 'line' && lineChart) lineChart.resize()
    if (tab === 'river' && riverChart) riverChart.resize()
    if (tab === 'graph') {
      if (!graphChart) {
        updateGraphChart()
      } else {
        graphChart.resize()
      }
    }
  })
})

onBeforeUnmount(() => {
  if (lineChart) { lineChart.dispose(); lineChart = null }
  if (riverChart) { riverChart.dispose(); riverChart = null }
  if (graphChart) { graphChart.dispose(); graphChart = null }
  window.removeEventListener('resize', () => {})
})
</script>

<style scoped>
.page { padding: 20px; max-width: 1200px; margin: 20px auto; }
.page-head { margin-bottom: 20px; }
.page-head h2 { margin: 0 0 8px; font-size: 22px; }
.page-subtitle { color: #606266; line-height: 1.6; margin: 0; font-size: 14px; }

.controls-card { margin-bottom: 16px; }
.controls { display: flex; flex-direction: column; gap: 16px; }
.control-item { display: flex; align-items: center; gap: 16px; flex-wrap: wrap; }
.label { font-size: 14px; color: #303133; font-weight: 500; min-width: 100px; }

.chart-tabs { margin-bottom: 16px; }
.chart-hint { display: flex; align-items: center; gap: 6px; font-size: 13px; color: #909399; margin-bottom: 12px; }
.chart-container { width: 100%; height: 520px; }

@media (max-width: 768px) {
  .page { margin: 12px auto; padding: 14px; }
  .chart-container { height: 360px; }
  .control-item { flex-direction: column; align-items: flex-start; }
}
</style>
