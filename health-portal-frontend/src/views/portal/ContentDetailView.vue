<template>
  <div class="container page" v-if="item">
    <h1>{{ item.title }}</h1>
    <div class="meta">{{ item.author }} · {{ item.publishTime }} · 阅读 {{ item.viewCount }}</div>
    <div class="content" v-html="item.content"></div>
    <el-button @click="$router.back()">返回</el-button>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { portalApi } from '../../api'

const route = useRoute()
const item = ref(null)

onMounted(async () => {
  const res = await portalApi.contentDetail(route.params.id)
  item.value = res.data
})
</script>

<style scoped>
.page { padding: 24px; background: #fff; margin: 20px auto; border-radius: 8px; max-width: 900px; }
.meta { color: #888; margin-bottom: 20px; }
.content { line-height: 1.8; margin-bottom: 24px; }
</style>
