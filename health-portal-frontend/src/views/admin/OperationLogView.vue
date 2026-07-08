<template>
  <div>
    <div class="toolbar">
      <h2>操作日志</h2>
      <div class="stats">
        <el-tag type="info">今日 {{ logStats.today || 0 }} 次</el-tag>
        <el-tag>总计 {{ logStats.total || 0 }} 次</el-tag>
      </div>
    </div>

    <!-- 筛选 -->
    <div class="filter-bar">
      <el-select v-model="moduleFilter" placeholder="按模块筛选" clearable style="width: 160px" @change="load">
        <el-option label="内容管理" value="内容管理" />
        <el-option label="轮播管理" value="轮播管理" />
        <el-option label="应用管理" value="应用管理" />
        <el-option label="系统配置" value="系统配置" />
        <el-option label="用户管理" value="用户管理" />
      </el-select>
      <el-button @click="load">刷新</el-button>
    </div>

    <!-- 日志列表 -->
    <el-table :data="list" stripe size="small">
      <el-table-column prop="module" label="模块" width="100" />
      <el-table-column prop="operation" label="操作" width="140" />
      <el-table-column prop="status" label="状态" width="70">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'danger'" size="small">
            {{ row.status === 1 ? '成功' : '失败' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="ip" label="IP" width="130" />
      <el-table-column prop="method" label="方法" min-width="200" show-overflow-tooltip />
      <el-table-column prop="errorMsg" label="错误信息" min-width="160" show-overflow-tooltip v-if="hasError" />
      <el-table-column prop="createdAt" label="时间" width="170" />
    </el-table>

    <!-- 分页 -->
    <el-pagination
      v-if="total > 20"
      v-model:current-page="page"
      :page-size="20"
      :total="total"
      layout="prev, pager, next, total"
      @current-change="load"
      style="margin-top: 16px; text-align: center"
    />
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { adminApi } from '../../api'

const list = ref([])
const page = ref(1)
const total = ref(0)
const moduleFilter = ref('')
const logStats = ref({})

const hasError = computed(() => list.value.some(l => l.errorMsg))

const load = async () => {
  const params = { page: page.value, size: 20 }
  if (moduleFilter.value) params.module = moduleFilter.value
  const res = await adminApi.logs(params)
  list.value = res.data.records
  total.value = res.data.total
}

const loadStats = async () => {
  const res = await adminApi.logStats()
  logStats.value = res.data
}

onMounted(() => {
  load()
  loadStats()
})
</script>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
.stats { display: flex; gap: 8px; }
.filter-bar { display: flex; gap: 10px; margin-bottom: 16px; }
</style>
