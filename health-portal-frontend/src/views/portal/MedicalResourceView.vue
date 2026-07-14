<template>
  <div class="container page">
    <div class="page-head">
      <div>
        <h2>医疗资源</h2>
      </div>
      <div class="page-actions">
        <router-link to="/drugs"><el-button type="primary" size="small">💊 药品查询</el-button></router-link>
        <el-tag type="info" effect="plain">信息可能随官方发布更新，请以来源为准</el-tag>
      </div>
    </div>

    <el-tabs v-model="activeTab" type="border-card">
      <el-tab-pane label="医院查询" name="hospitals">
        <div class="filters">
          <el-select v-model="hospitalFilter.province" placeholder="省份" clearable @change="provinceChanged">
            <el-option v-for="value in provinces" :key="value" :label="value" :value="value" />
          </el-select>
          <el-select v-model="hospitalFilter.city" placeholder="城市" clearable :disabled="!hospitalFilter.province" @change="searchHospitals">
            <el-option v-for="value in cities" :key="value" :label="value" :value="value" />
          </el-select>
          <el-input v-model="hospitalFilter.district" placeholder="区县" clearable @keyup.enter="searchHospitals" />
          <el-input v-model="hospitalFilter.level" placeholder="医院等级" clearable @keyup.enter="searchHospitals" />
          <el-input v-model="hospitalFilter.type" placeholder="医院类型" clearable @keyup.enter="searchHospitals" />
          <el-select v-model="hospitalFilter.insurance" placeholder="医保状态" clearable @change="searchHospitals">
            <el-option label="医保" :value="true" /><el-option label="非医保/未标注" :value="false" />
          </el-select>
          <el-input v-model="hospitalFilter.keyword" placeholder="医院名称、别名或地址" clearable @keyup.enter="searchHospitals" />
          <el-button type="primary" :loading="hospitalLoading" @click="searchHospitals">查询</el-button>
          <el-button @click="resetHospitals">清空</el-button>
        </div>
        <p class="result-count" v-if="hospitalLoaded">找到 {{ hospitalTotal }} 家医院</p>
        <el-skeleton v-if="hospitalLoading" :rows="6" animated />
        <el-alert v-else-if="hospitalError" :title="hospitalError" type="error" show-icon :closable="false" />
        <div v-else-if="hospitals.length" class="hospital-list">
          <article v-for="hospital in hospitals" :key="hospital.id" class="hospital-card" @click="openHospital(hospital.id)">
            <h3>{{ hospital.name }}</h3>
            <div class="tags">
              <el-tag v-if="hospital.level" size="small">{{ hospital.level }}</el-tag>
              <el-tag v-if="hospital.type" size="small" type="success">{{ hospital.type }}</el-tag>
              <el-tag v-if="hospital.isInsurance === 1" size="small" type="warning">医保</el-tag>
            </div>
            <p>{{ [hospital.province, hospital.city, hospital.district].filter(Boolean).join(' · ') }}</p>
            <p v-if="hospital.address">{{ hospital.address }}</p>
          </article>
        </div>
        <el-empty v-else-if="hospitalLoaded" description="没有符合条件的医院" />
        <el-pagination v-if="hospitalTotal > pageSize" v-model:current-page="hospitalPage" :page-size="pageSize" :total="hospitalTotal" layout="prev, pager, next" @current-change="loadHospitals" />
      </el-tab-pane>

      <el-tab-pane label="三级公立医院" name="tertiary">
        <div class="section-head">
          <div>
            <h3>全国三级公立综合医院等级名单</h3>
          </div>
          <a href="https://zgcx.nhc.gov.cn/unit" target="_blank" rel="noopener noreferrer">国家卫健委医疗机构查询</a>
        </div>
        <div class="filters compact">
          <el-select v-model="tertiaryFilter.province" placeholder="省份" clearable><el-option v-for="value in provinces" :key="value" :label="value" :value="value" /></el-select>
          <el-select v-model="tertiaryFilter.grade" placeholder="等级" clearable><el-option label="A++等级" value="A++等级" /><el-option label="A+等级" value="A+等级" /></el-select>
          <el-input v-model="tertiaryFilter.keyword" placeholder="医院名称" clearable @keyup.enter="searchTertiary" />
          <el-button type="primary" :loading="tertiaryLoading" @click="searchTertiary">查询</el-button>
        </div>
        <p class="result-count">找到 {{ tertiaryTotal }} 家医院</p>
        <el-table :data="tertiaryHospitals" stripe v-loading="tertiaryLoading">
          <el-table-column prop="grade" label="等级" width="110" />
          <el-table-column prop="province" label="省份" width="120" />
          <el-table-column prop="hospitalName" label="医院名称" min-width="260" />
          <el-table-column prop="verificationStatus" label="核验状态" width="110" />
        </el-table>
        <el-pagination v-if="tertiaryTotal > pageSize" v-model:current-page="tertiaryPage" :page-size="pageSize" :total="tertiaryTotal" layout="prev, pager, next" @current-change="loadTertiary" />
      </el-tab-pane>

      <el-tab-pane label="医院等级分档" name="grades">
        <div class="grade-groups" v-loading="gradeLoading">
          <section v-for="grade in gradeOrder" :key="grade" class="grade-group" :class="{ featured: grade === 'A++++' }">
            <h3>{{ grade }} <small>{{ gradeGroups[grade]?.length || 0 }} 家</small></h3>
            <ol><li v-for="item in gradeGroups[grade] || []" :key="item.id">{{ item.hospitalName }}</li></ol>
          </section>
        </div>
      </el-tab-pane>

    </el-tabs>
  </div>
</template>

<script setup>
import { onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { portalApi } from '../../api'

const router = useRouter()
const activeTab = ref('hospitals')
const pageSize = 10
const provinces = ref([])
const cities = ref([])

const hospitalFilter = ref({ province: '', city: '', district: '', level: '', type: '', insurance: null, keyword: '' })
const hospitals = ref([]); const hospitalPage = ref(1); const hospitalTotal = ref(0)
const hospitalLoading = ref(false); const hospitalLoaded = ref(false); const hospitalError = ref('')

const tertiaryFilter = ref({ province: '', grade: '', keyword: '' })
const tertiaryHospitals = ref([]); const tertiaryPage = ref(1); const tertiaryTotal = ref(0); const tertiaryLoading = ref(false)
const gradeGroups = ref({}); const gradeLoading = ref(false); const gradeOrder = ['A++++', 'A+++', 'A++', 'A+', 'A']

const cleanParams = (object) => Object.fromEntries(Object.entries(object).filter(([, value]) => value !== '' && value !== null && value !== undefined))

const loadProvinces = async () => { provinces.value = (await portalApi.medicalProvinces()).data || [] }
const provinceChanged = async () => {
  hospitalFilter.value.city = ''
  cities.value = hospitalFilter.value.province ? (await portalApi.medicalCities(hospitalFilter.value.province)).data || [] : []
  searchHospitals()
}
const loadHospitals = async () => {
  hospitalLoading.value = true; hospitalError.value = ''
  try {
    const response = await portalApi.medicalHospitals({ ...cleanParams(hospitalFilter.value), page: hospitalPage.value, size: pageSize })
    hospitals.value = response.data?.records || []; hospitalTotal.value = response.data?.total || 0; hospitalLoaded.value = true
  } catch (e) { hospitals.value = []; hospitalTotal.value = 0; hospitalError.value = e?.response?.data?.message || '医院查询失败' }
  finally { hospitalLoading.value = false }
}
const searchHospitals = () => { hospitalPage.value = 1; loadHospitals() }
const resetHospitals = () => { hospitalFilter.value = { province: '', city: '', district: '', level: '', type: '', insurance: null, keyword: '' }; cities.value = []; searchHospitals() }
const openHospital = (id) => router.push(`/medical/hospitals/${id}`)

const loadTertiary = async () => {
  tertiaryLoading.value = true
  try { const response = await portalApi.medicalPublicTertiaryHospitals({ ...cleanParams(tertiaryFilter.value), page: tertiaryPage.value, size: pageSize }); tertiaryHospitals.value = response.data?.records || []; tertiaryTotal.value = response.data?.total || 0 }
  finally { tertiaryLoading.value = false }
}
const searchTertiary = () => { tertiaryPage.value = 1; loadTertiary() }
const loadGrades = async () => { gradeLoading.value = true; try { gradeGroups.value = (await portalApi.medicalHospitalGrades()).data || {} } finally { gradeLoading.value = false } }

watch(activeTab, (tab) => {
  if (tab === 'tertiary' && !tertiaryHospitals.value.length) loadTertiary()
  if (tab === 'grades' && !Object.keys(gradeGroups.value).length) loadGrades()
})

onMounted(async () => { await Promise.all([loadProvinces(), loadHospitals()]) })
</script>

<style scoped>
.page { padding: 24px; background: #fff; margin: 20px auto; border-radius: 8px; max-width: 1200px; }
.page-head, .section-head { display: flex; justify-content: space-between; align-items: flex-start; gap: 16px; flex-wrap: wrap; }
.page-head h2, .section-head h3 { margin: 0 0 8px; }
.lead, .section-head p { margin: 0 0 18px; color: #666; }
.filters { display: grid; grid-template-columns: repeat(4, minmax(150px, 1fr)); gap: 10px; background: #f5f7fa; padding: 16px; border-radius: 8px; margin-bottom: 10px; }
.filters.compact { grid-template-columns: repeat(4, minmax(160px, 1fr)); }
.result-count, .source { color: #909399; font-size: 13px; }
.hospital-list { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 12px; }
.hospital-card { border: 1px solid #e4e7ed; border-radius: 8px; padding: 16px; cursor: pointer; }
.hospital-card:hover { border-color: #409eff; background: #f7fbff; }
.hospital-card h3 { margin: 0 0 10px; }
.hospital-card p { margin: 8px 0 0; color: #606266; }
.tags { display: flex; gap: 6px; flex-wrap: wrap; }
.grade-groups { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px; margin-top: 16px; }
.grade-group { border: 1px solid #e4e7ed; border-radius: 8px; padding: 16px; }
.grade-group.featured { border-color: #e6a23c; background: #fff9ed; grid-column: 1 / -1; }
.grade-group h3 { margin-top: 0; color: #1a6fb5; }.grade-group small { color: #909399; font-weight: normal; }
.grade-group ol { columns: 2; line-height: 1.8; padding-left: 24px; }
.el-pagination { margin-top: 18px; justify-content: center; }
@media (max-width: 900px) { .filters, .filters.compact { grid-template-columns: repeat(2, 1fr); } }
@media (max-width: 640px) { .filters, .filters.compact, .hospital-list, .grade-groups { grid-template-columns: 1fr; } .grade-group.featured { grid-column: auto; } .grade-group ol { columns: 1; } }
</style>
