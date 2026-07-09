<template>
  <div class="container page">
    <div class="page-header">
      <h1>症状自查</h1>
      <p class="subtitle">选择身体部位和症状，获取就医指导</p>
    </div>

    <!-- 主内容区：左侧部位选择 + 右侧症状选择 -->
    <div v-if="!showResult" class="main-content">
      <div class="selection-area">
        <!-- 左侧：部位选择 -->
        <div class="card parts-card">
          <h3><el-icon><Location /></el-icon> 选择不适部位</h3>
          <p class="hint">可点击多个部位，加载对应症状</p>
          
          <div class="body-parts-grid">
            <div
              v-for="part in bodyParts"
              :key="part.id"
              class="body-part-item"
              :class="{ active: selectedPartIds.includes(part.id) }"
              @click="togglePart(part)"
            >
              <span class="part-name">{{ part.name }}</span>
              <span v-if="part.children && part.children.length" class="has-children">
                <el-icon><ArrowRight /></el-icon>
              </span>
            </div>
          </div>

          <!-- 子部位选择（当选中父部位时显示） -->
          <div v-if="expandedPart && expandedPart.children && expandedPart.children.length" class="sub-parts">
            <h4>{{ expandedPart.name }} - 细分部位</h4>
            <div class="body-parts-grid sub">
              <div
                v-for="child in expandedPart.children"
                :key="child.id"
                class="body-part-item small"
                :class="{ active: selectedPartIds.includes(child.id) }"
                @click="togglePart(child)"
              >
                {{ child.name }}
              </div>
            </div>
          </div>
        </div>

        <!-- 右侧：症状选择 -->
        <div class="card symptoms-card">
          <div class="step-header">
            <h3><el-icon><List /></el-icon> 选择症状</h3>
            <span class="selected-count">已选 {{ selectedSymptomIds.length }}/5</span>
          </div>

          <div v-if="loadingSymptoms" class="loading">
            <el-skeleton :rows="3" animated />
          </div>

          <div v-else-if="allSymptoms.length === 0" class="empty-state">
            <el-empty description="请先选择部位以加载症状" />
          </div>

          <div v-else class="symptoms-list">
            <el-tooltip
              v-for="symptom in allSymptoms"
              :key="symptom.id"
              :content="symptom.description"
              placement="top"
            >
              <el-check-tag
                :checked="selectedSymptomIds.includes(symptom.id)"
                :disabled="!selectedSymptomIds.includes(symptom.id) && selectedSymptomIds.length >= 5"
                @change="(checked) => toggleSymptom(symptom.id, checked)"
                class="symptom-tag"
              >
                {{ symptom.name }}
                <span v-if="symptom.aliases" class="alias">({{ symptom.aliases }})</span>
              </el-check-tag>
            </el-tooltip>
          </div>

          <el-alert
            v-if="selectedSymptomIds.length >= 5"
            title="最多选择5个症状"
            type="info"
            show-icon
            :closable="false"
            class="limit-alert"
          />

          <div class="actions">
            <el-button 
              type="primary" 
              size="large"
              :disabled="selectedSymptomIds.length === 0" 
              @click="doCheck" 
              :loading="checking"
            >
              查看引导结果
            </el-button>
          </div>
        </div>
      </div>
    </div>

    <!-- 结果展示 -->
    <div v-else class="result-section">
      <!-- 紧急警示（仅显示level 2，多个合并为一个卡片） -->
      <div v-if="criticalAlerts.length" class="alerts-section">
        <div class="alert-card alert-orange">
          <div class="alert-header">
            <el-icon class="alert-icon"><WarningFilled /></el-icon>
            <span class="alert-level">{{ getLevelText() }}</span>
          </div>
          <div v-if="criticalAlerts.length === 1" class="alert-single">
            <div class="alert-title">{{ criticalAlerts[0].description }}</div>
            <div class="alert-advice">{{ criticalAlerts[0].advice }}</div>
          </div>
          <div v-else class="alert-multi">
            <div class="alert-summary">您有以下 {{ criticalAlerts.length }} 项需要关注：</div>
            <div class="alert-list">
              <div v-for="(alert, index) in criticalAlerts" :key="alert.id" class="alert-list-item">
                <div class="alert-list-title">{{ index + 1 }}. {{ alert.description }}</div>
                <div class="alert-list-advice">{{ alert.advice }}</div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- 推荐科室 -->
      <div class="card result-card">
        <h3><el-icon><FirstAidKit /></el-icon> 推荐科室</h3>
        <div class="departments-list">
          <el-tag
            v-for="(dept, index) in checkResult.departments"
            :key="index"
            :type="getDeptType(dept)"
            :effect="getDeptEffect(dept)"
            size="large"
            class="dept-tag"
            :class="{ 'dept-recommended': dept.recommended }"
          >
            {{ dept.recommended ? '★ ' : '' }}建议挂号【{{ dept.name }}】
          </el-tag>
          <span v-if="!checkResult.departments || !checkResult.departments.length" class="no-data">
            暂无推荐科室
          </span>
        </div>
      </div>

      <!-- 建议关注 -->
      <div v-if="checkResult.reminders && checkResult.reminders.length" class="card result-card">
        <h3><el-icon><InfoFilled /></el-icon> 建议关注</h3>
        <ul class="reminders-list">
          <li v-for="(reminder, index) in checkResult.reminders" :key="index">
            {{ reminder }}
          </li>
        </ul>
      </div>

      <!-- 就医建议（level 3/4 规则建议） -->
      <div v-if="checkResult.suggestions && checkResult.suggestions.length" class="card result-card">
        <h3><el-icon><InfoFilled /></el-icon> 就医建议</h3>
        <ul class="reminders-list">
          <li v-for="(suggestion, index) in checkResult.suggestions" :key="index" class="suggestion-item">
            <div class="suggestion-title">{{ suggestion.description }}</div>
            <div class="suggestion-advice">{{ suggestion.advice }}</div>
          </li>
        </ul>
      </div>

      <!-- 免责声明 -->
      <div class="disclaimer">
        <el-icon><Warning /></el-icon>
        {{ checkResult.disclaimer }}
      </div>

      <div class="actions">
        <el-button @click="reset">重新自查</el-button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted } from 'vue'
import { ArrowRight, WarningFilled, FirstAidKit, InfoFilled, Warning, Location, List } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { symptomApi } from '../../api'

// 状态
const bodyParts = ref([])
const selectedPartIds = ref([]) // 支持多选部位
const expandedPart = ref(null) // 当前展开子部位的父部位
const symptomsMap = ref({}) // partId -> symptoms[]
const selectedSymptomIds = ref([])
const loadingSymptoms = ref(false)
const checking = ref(false)
const checkResult = ref({})
const showResult = ref(false)

// 计算属性：合并所有选中部位的症状（去重）
const allSymptoms = computed(() => {
  const symptomSet = new Map()
  for (const partId of selectedPartIds.value) {
    const symptoms = symptomsMap.value[partId] || []
    for (const symptom of symptoms) {
      symptomSet.set(symptom.id, symptom)
    }
  }
  return Array.from(symptomSet.values())
})

// 计算属性：仅获取 level 2 的紧急警报
const criticalAlerts = computed(() => {
  if (!checkResult.value.alerts) return []
  return checkResult.value.alerts.filter(alert => alert.level === 2)
})

// 初始化加载部位列表
onMounted(async () => {
  try {
    const res = await symptomApi.getBodyParts()
    bodyParts.value = res.data || []
  } catch (e) {
    ElMessage.error('加载部位列表失败')
  }
})

// 切换部位选择（支持多选，但父子部位互斥）
const togglePart = async (part) => {
  const index = selectedPartIds.value.indexOf(part.id)
  
  if (index > -1) {
    // 取消选择
    selectedPartIds.value.splice(index, 1)
    // 如果取消的是当前展开的父部位，清空展开状态
    if (expandedPart.value && expandedPart.value.id === part.id) {
      expandedPart.value = null
    }
  } else {
    // 添加选择
    // 如果是子部位，先移除其父部位（如果已选中）
    if (part.parentId !== null && part.parentId !== undefined) {
      const parentIndex = selectedPartIds.value.indexOf(part.parentId)
      if (parentIndex > -1) {
        selectedPartIds.value.splice(parentIndex, 1)
      }
    }
    
    // 如果是父部位且有子部位，先移除所有已选中的子部位
    if (part.children && part.children.length) {
      for (const child of part.children) {
        const childIndex = selectedPartIds.value.indexOf(child.id)
        if (childIndex > -1) {
          selectedPartIds.value.splice(childIndex, 1)
        }
      }
      expandedPart.value = part
    }
    
    selectedPartIds.value.push(part.id)
    
    // 加载该部位的症状（如果尚未加载）
    if (!symptomsMap.value[part.id]) {
      await loadSymptomsForPart(part.id)
    }
  }
}

// 加载指定部位的症状
const loadSymptomsForPart = async (partId) => {
  loadingSymptoms.value = true
  try {
    const res = await symptomApi.getSymptomsByPartId(partId)
    symptomsMap.value[partId] = res.data || []
  } catch (e) {
    ElMessage.error('加载症状列表失败')
  } finally {
    loadingSymptoms.value = false
  }
}

// 切换症状选择
const toggleSymptom = (id, checked) => {
  if (checked) {
    if (selectedSymptomIds.value.length < 5) {
      selectedSymptomIds.value.push(id)
    }
  } else {
    selectedSymptomIds.value = selectedSymptomIds.value.filter(sid => sid !== id)
  }
}

// 执行自查
const doCheck = async () => {
  if (selectedSymptomIds.value.length === 0) return

  checking.value = true
  try {
    const res = await symptomApi.checkSymptoms(selectedSymptomIds.value)
    checkResult.value = res.data || {}
    showResult.value = true
  } catch (e) {
    ElMessage.error('自查请求失败')
  } finally {
    checking.value = false
  }
}

// 重置
const reset = () => {
  selectedPartIds.value = []
  expandedPart.value = null
  symptomsMap.value = {}
  selectedSymptomIds.value = []
  checkResult.value = {}
  showResult.value = false
}

// 获取级别文本
const getLevelText = () => {
  return '重要提示'
}

// 获取科室标签类型
const getDeptType = (dept) => {
  if (dept.recommended) return 'danger' // 紧急推荐科室
  return 'primary' // 普通科室
}

// 获取科室标签效果
const getDeptEffect = (dept) => {
  if (dept.recommended) return 'dark' // 紧急推荐科室深色
  return 'plain' // 普通科室浅色
}
</script>

<style scoped>
.page {
  padding: 20px;
  max-width: 1200px;
  margin: 0 auto;
}

.page-header {
  text-align: center;
  margin-bottom: 24px;
}

.page-header h1 {
  margin: 0 0 8px;
  color: #1a6fb5;
}

.subtitle {
  color: #666;
  font-size: 14px;
}

/* 主内容区布局 */
.main-content {
  animation: fadeIn 0.3s ease;
}

.selection-area {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
}

@media (max-width: 900px) {
  .selection-area {
    grid-template-columns: 1fr;
  }
}

.card {
  background: #fff;
  border-radius: 8px;
  padding: 20px;
  margin-bottom: 16px;
  box-shadow: 0 1px 4px rgba(0,0,0,.06);
}

.card h3 {
  margin: 0 0 16px;
  display: flex;
  align-items: center;
  gap: 8px;
  color: #333;
}

.hint {
  font-size: 12px;
  color: #999;
  margin: -8px 0 16px;
}

/* 部位选择 */
.body-parts-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(90px, 1fr));
  gap: 10px;
}

.body-parts-grid.sub {
  grid-template-columns: repeat(auto-fill, minmax(70px, 1fr));
}

.body-part-item {
  padding: 14px 10px;
  border: 2px solid #e4e7ed;
  border-radius: 8px;
  text-align: center;
  cursor: pointer;
  transition: all 0.2s;
  position: relative;
  font-size: 14px;
}

.body-part-item:hover {
  border-color: #1a6fb5;
  background: #f0f7fc;
}

.body-part-item.active {
  border-color: #1a6fb5;
  background: #1a6fb5;
  color: #fff;
}

.body-part-item.small {
  padding: 10px 8px;
  font-size: 13px;
}

.has-children {
  position: absolute;
  right: 4px;
  top: 50%;
  transform: translateY(-50%);
  font-size: 12px;
  opacity: 0.6;
}

.sub-parts {
  margin-top: 20px;
  padding-top: 20px;
  border-top: 1px dashed #eee;
}

.sub-parts h4 {
  margin: 0 0 12px;
  color: #666;
  font-size: 14px;
}

/* 症状选择 */
.step-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.step-header h3 {
  margin: 0;
}

.selected-count {
  font-size: 14px;
  color: #666;
}

.symptoms-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  max-height: 400px;
  overflow-y: auto;
}

.symptom-tag {
  font-size: 14px;
  padding: 8px 14px;
  border-radius: 20px;
}

.symptom-tag .alias {
  font-size: 12px;
  opacity: 0.7;
  margin-left: 4px;
}

.limit-alert {
  margin-top: 16px;
}

.loading {
  padding: 20px 0;
}

.empty-state {
  padding: 40px 0;
}

.actions {
  display: flex;
  justify-content: center;
  gap: 12px;
  margin-top: 24px;
}

/* 结果展示 */
.result-section {
  animation: fadeIn 0.3s ease;
}

.alerts-section {
  margin-bottom: 16px;
}

.alert-card {
  padding: 16px;
  border-radius: 8px;
  margin-bottom: 12px;
  border-left: 4px solid;
}

.alert-orange {
  background: #fff7e6;
  border-color: #ff6600;
}



.alert-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  font-size: 13px;
}

.alert-icon {
  font-size: 18px;
}

.alert-orange .alert-icon { color: #ff6600; }

.alert-level {
  font-weight: bold;
}



.alert-title {
  font-weight: bold;
  font-size: 16px;
  margin-bottom: 8px;
}

.alert-advice {
  font-size: 14px;
  line-height: 1.6;
  color: #333;
}

.alert-single {
  margin-top: 8px;
}

.alert-multi {
  margin-top: 8px;
}

.alert-summary {
  font-size: 14px;
  color: #333;
  margin-bottom: 12px;
}

.alert-list {
  display: flex;
  flex-direction: column;
  gap: 10px;
}

.alert-list-item {
  padding: 10px 12px;
  background: #fff3e0;
  border-radius: 6px;
}

.alert-list-title {
  font-weight: 600;
  font-size: 14px;
  margin-bottom: 4px;
  color: #d4380d;
}

.alert-list-advice {
  font-size: 13px;
  line-height: 1.6;
  color: #555;
}

.result-card h3 {
  color: #1a6fb5;
}

.departments-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.dept-tag {
  font-size: 14px;
}

.dept-recommended {
  font-weight: bold;
  box-shadow: 0 2px 8px rgba(245, 108, 108, 0.3);
}

.no-data {
  color: #999;
  font-size: 14px;
}

.reminders-list {
  list-style: none;
  padding: 0;
  margin: 0;
}

.reminders-list li {
  padding: 10px 0;
  border-bottom: 1px dashed #eee;
  color: #666;
  font-size: 14px;
  line-height: 1.6;
}

.suggestion-item {
  padding: 12px 0;
}

.suggestion-title {
  font-weight: 600;
  color: #333;
  margin-bottom: 6px;
  font-size: 14px;
}

.suggestion-advice {
  color: #666;
  font-size: 13px;
  line-height: 1.6;
  padding-left: 8px;
  border-left: 3px solid #e4e7ed;
}

.reminders-list li:last-child {
  border-bottom: none;
}

.disclaimer {
  background: #f5f7fa;
  padding: 16px;
  border-radius: 8px;
  color: #909399;
  font-size: 13px;
  display: flex;
  align-items: flex-start;
  gap: 8px;
  margin-bottom: 16px;
}

.disclaimer .el-icon {
  margin-top: 2px;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}
</style>
