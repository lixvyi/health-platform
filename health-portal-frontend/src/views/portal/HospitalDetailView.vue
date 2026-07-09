<template>
  <div class="container page">
    <el-skeleton v-if="loading" :rows="10" animated />
    <el-result v-else-if="error" icon="error" title="医院信息加载失败" :sub-title="error">
      <template #extra><el-button type="primary" @click="$router.push('/medical')">返回医疗资源</el-button></template>
    </el-result>
    <template v-else-if="hospital">
      <el-breadcrumb separator="/" class="breadcrumb">
        <el-breadcrumb-item :to="{ path: '/' }">首页</el-breadcrumb-item>
        <el-breadcrumb-item :to="{ path: '/medical' }">医疗资源</el-breadcrumb-item>
        <el-breadcrumb-item>{{ hospital.name }}</el-breadcrumb-item>
      </el-breadcrumb>
      <h1>{{ hospital.name }}</h1>
      <div class="tags">
        <el-tag v-if="hospital.level">{{ hospital.level }}</el-tag>
        <el-tag v-if="hospital.type" type="success">{{ hospital.type }}</el-tag>
        <el-tag v-if="hospital.operationMode" type="info">{{ hospital.operationMode }}</el-tag>
        <el-tag v-if="hospital.isInsurance === 1" type="warning">医保</el-tag>
      </div>

      <section class="card">
        <h3>基本信息</h3>
        <el-descriptions :column="2" border>
          <el-descriptions-item label="医院别名">{{ hospital.aliasName || '—' }}</el-descriptions-item>
          <el-descriptions-item label="所在地区">{{ location }}</el-descriptions-item>
          <el-descriptions-item label="地址" :span="2">{{ hospital.address || '—' }}</el-descriptions-item>
          <el-descriptions-item label="电话">{{ hospital.phone || '—' }}</el-descriptions-item>
          <el-descriptions-item label="邮箱">{{ hospital.email || '—' }}</el-descriptions-item>
          <el-descriptions-item label="建院年份">{{ hospital.foundedYear || '—' }}</el-descriptions-item>
          <el-descriptions-item label="床位数">{{ hospital.bedCount ?? '原表未提供' }}</el-descriptions-item>
        </el-descriptions>
      </section>

      <section class="card" v-if="hospital.departments">
        <h3>原表科室信息</h3><p>{{ hospital.departments }}</p>
      </section>
      <section class="card" v-if="hospital.introduction">
        <h3>医院简介</h3><p class="intro">{{ hospital.introduction }}</p>
      </section>
      <section class="card source-card">
        <h3>数据来源与核验</h3>
        <p>来源：{{ hospital.sourceName || '用户提供的医院名录 Excel' }}</p>
        <p v-if="hospital.dataAsOfDate">数据截至：{{ hospital.dataAsOfDate }}</p>
        <p>核验状态：{{ statusText(hospital.verificationStatus) }}</p>
        <p class="hint">数据库更新时间不等同于官方数据截至时间。</p>
      </section>
      <el-button type="primary" plain @click="$router.push('/medical')">返回医疗资源</el-button>
    </template>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { portalApi } from '../../api'

const route = useRoute()
const hospital = ref(null)
const loading = ref(true)
const error = ref('')
const location = computed(() => [hospital.value?.province, hospital.value?.city, hospital.value?.district].filter(Boolean).join(' · ') || '—')
const statusText = (value) => ({ VERIFIED: '已核验', OUTDATED: '已过期', UNVERIFIED: '未核验' }[value] || value || '未核验')

onMounted(async () => {
  try { hospital.value = (await portalApi.medicalHospitalDetail(route.params.id)).data }
  catch (e) { error.value = e?.response?.data?.message || '医院不存在或服务暂时不可用' }
  finally { loading.value = false }
})
</script>

<style scoped>
.page { padding: 28px 32px; margin: 20px auto; background: #fff; border-radius: 8px; max-width: 950px; }
.breadcrumb { margin-bottom: 20px; } h1 { margin-bottom: 14px; }.tags { display: flex; gap: 8px; flex-wrap: wrap; margin-bottom: 22px; }
.card { padding: 18px; background: #f8fafc; border-radius: 8px; margin-bottom: 16px; }.card h3 { margin-top: 0; color: #334155; }
.intro { white-space: pre-wrap; line-height: 1.8; }.source-card { background: #f0f9ff; border: 1px solid #bae6fd; }.hint { color: #909399; font-size: 13px; }
@media (max-width: 640px) { .page { padding: 20px; } }
</style>

