<template>
  <div class="container page">
    <h2>{{ title }}</h2>
    <el-input v-model="keyword" placeholder="搜索标题" clearable style="max-width:320px;margin-bottom:16px" @keyup.enter="load" />
    <el-button type="primary" @click="load">搜索</el-button>
    <el-table :data="list" stripe @row-click="row => $router.push(`/content/${row.id}`)" style="margin-top:16px;cursor:pointer">
      <el-table-column prop="title" label="标题" />
      <el-table-column prop="author" label="作者" width="120" />
      <el-table-column prop="publishTime" label="发布时间" width="180" />
    </el-table>
    <el-pagination v-model:current-page="page" :page-size="10" :total="total" layout="prev, pager, next" @current-change="load" style="margin-top:16px" />
  </div>
</template>

<script setup>
import { onMounted, ref, watch } from 'vue'
import { portalApi } from '../../api'

const props = defineProps({ category: String, title: String })
const keyword = ref('')
const list = ref([])
const page = ref(1)
const total = ref(0)

const load = async () => {
  const res = await portalApi.contents({ categoryCode: props.category, keyword: keyword.value, page: page.value, size: 10 })
  list.value = res.data.records
  total.value = res.data.total
}

onMounted(load)
watch(() => props.category, load)
</script>

<style scoped>
.page { padding: 20px; background: #fff; margin: 20px auto; border-radius: 8px; max-width: 1200px; }
</style>
