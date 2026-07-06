<template>
  <div>
    <h2>关于我们 / 首页配置</h2>
    <el-form label-width="100px" style="max-width:720px">
      <el-form-item label="首页简介"><el-input v-model="intro" type="textarea" :rows="2" /></el-form-item>
      <el-button @click="saveIntro">保存简介</el-button>
      <el-divider />
      <el-form-item label="关于标题"><el-input v-model="about.title" /></el-form-item>
      <el-form-item label="关于内容"><el-input v-model="about.content" type="textarea" :rows="10" /></el-form-item>
      <el-button type="primary" @click="saveAbout">保存关于我们</el-button>
    </el-form>
  </div>
</template>

<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { adminApi, portalApi } from '../../api'

const intro = ref('')
const about = reactive({ title: '', content: '' })

onMounted(async () => {
  const home = await portalApi.home()
  intro.value = home.data.intro
  const res = await adminApi.about()
  Object.assign(about, res.data)
})

const saveIntro = async () => {
  await adminApi.updateHomeIntro({ intro: intro.value })
  ElMessage.success('已保存')
}
const saveAbout = async () => {
  await adminApi.updateAbout(about)
  ElMessage.success('已保存')
}
</script>
