<template>
  <div class="container page">
    <h2>应用中心</h2>
    <el-row :gutter="16">
      <el-col :span="8" v-for="app in apps" :key="app.id">
        <el-card shadow="hover" class="card" @click="open(app.linkUrl)">
          <div class="row">
            <img :src="app.iconUrl" />
            <div>
              <h3>{{ app.name }}</h3>
              <p>{{ app.description }}</p>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { portalApi } from '../../api'

const apps = ref([])
onMounted(async () => {
  const res = await portalApi.apps()
  apps.value = res.data
})
const open = (url) => {
  if (url.startsWith('http')) window.open(url)
  else window.location.href = url
}
</script>

<style scoped>
.page { padding: 20px; }
.card { cursor: pointer; margin-bottom: 16px; }
.row { display: flex; gap: 12px; }
img { width: 56px; height: 56px; border-radius: 8px; }
</style>
