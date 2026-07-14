<template>
  <div class="container page">
    <!-- 骨架屏加载 -->
    <el-skeleton v-if="loading" :rows="10" animated />

    <!-- 错误状态 -->
    <el-result v-else-if="error" icon="error" title="药品信息加载失败" :sub-title="error">
      <template #extra>
        <el-button type="primary" @click="$router.push('/drugs')">返回药品查询</el-button>
        <el-button @click="loadDrug">重试</el-button>
      </template>
    </el-result>

    <template v-else-if="drug">
      <!-- 面包屑 -->
      <el-breadcrumb separator="/" class="breadcrumb">
        <el-breadcrumb-item :to="{ path: '/' }">首页</el-breadcrumb-item>
        <el-breadcrumb-item :to="{ path: '/drugs' }">药品查询</el-breadcrumb-item>
        <el-breadcrumb-item>{{ drug.genericName }}</el-breadcrumb-item>
      </el-breadcrumb>

      <el-alert title="本平台所有药品信息仅供参考，不作为诊疗依据，用药请遵医嘱。" type="warning" show-icon :closable="false" class="disclaimer" />

      <!-- 药品基本信息卡片 -->
      <div class="basic-info card">
        <h1 class="drug-name">
          {{ drug.genericName }}
          <small v-if="drug.brandName" class="brand-name">（商品名：{{ drug.brandName }}）</small>
        </h1>
        <div class="tags">
          <el-tag :type="drug.prescriptionType === '非处方药' ? 'success' : 'danger'" size="large">
            <el-icon style="margin-right:4px"><component :is="drug.prescriptionType === '非处方药' ? 'SuccessFilled' : 'Warning'" /></el-icon>
            {{ drug.prescriptionType || '处方药' }}
          </el-tag>
          <el-tag v-if="drug.category" type="info" effect="plain" size="large">{{ drug.category }}</el-tag>
          <el-tag v-if="drug.dosageForm" type="warning" effect="plain" size="large">{{ drug.dosageForm }}</el-tag>
        </div>
        <el-descriptions :column="2" border size="small" class="meta-descriptions">
          <el-descriptions-item label="批准文号" v-if="drug.approvalNumber">{{ drug.approvalNumber }}</el-descriptions-item>
          <el-descriptions-item label="ATC编码" v-if="drug.atcCode">{{ drug.atcCode }}</el-descriptions-item>
          <el-descriptions-item label="生产企业" :span="drug.approvalNumber && drug.atcCode ? 1 : 2" v-if="drug.manufacturer">{{ drug.manufacturer }}</el-descriptions-item>
          <el-descriptions-item label="贮藏" v-if="drug.storage">{{ drug.storage }}</el-descriptions-item>
          <el-descriptions-item label="有效期" v-if="drug.validity">{{ drug.validity }}</el-descriptions-item>
        </el-descriptions>
      </div>

      <!-- 适应症 -->
      <section v-if="drug.indications" class="card info-card">
        <h3 class="section-title">
          <el-icon class="title-icon"><Document /></el-icon>
          适应症
        </h3>
        <p class="section-content" :class="{ collapsed: indicationsCollapsed && drug.indications.length > 200 }">
          {{ drug.indications }}
        </p>
        <el-button v-if="drug.indications.length > 200" link type="primary" @click="indicationsCollapsed = !indicationsCollapsed" class="toggle">
          {{ indicationsCollapsed ? '展开全文' : '收起' }}
        </el-button>
      </section>

      <!-- 禁忌 - 红色警示卡片 -->
      <section v-if="drug.contraindications" class="card danger-card">
        <h3 class="section-title">
          <el-icon class="title-icon"><WarningFilled /></el-icon>
          禁忌
        </h3>
        <p class="section-content">{{ drug.contraindications }}</p>
      </section>

      <!-- 不良反应 - 橙色警示卡片 -->
      <section v-if="drug.adverseReactions" class="card warning-card">
        <h3 class="section-title">
          <el-icon class="title-icon"><Warning /></el-icon>
          不良反应
        </h3>
        <p class="section-content" :class="{ collapsed: reactionsCollapsed && drug.adverseReactions.length > 200 }">
          {{ drug.adverseReactions }}
        </p>
        <el-button v-if="drug.adverseReactions.length > 200" link type="warning" @click="reactionsCollapsed = !reactionsCollapsed" class="toggle">
          {{ reactionsCollapsed ? '展开全文' : '收起' }}
        </el-button>
      </section>

      <!-- 用法用量 -->
      <section v-if="drug.usageDosage" class="card info-card">
        <h3 class="section-title">
          <el-icon class="title-icon"><Tickets /></el-icon>
          用法用量
        </h3>
        <p class="section-content" :class="{ collapsed: dosageCollapsed && drug.usageDosage.length > 200 }">
          {{ drug.usageDosage }}
        </p>
        <el-button v-if="drug.usageDosage.length > 200" link type="primary" @click="dosageCollapsed = !dosageCollapsed" class="toggle">
          {{ dosageCollapsed ? '展开全文' : '收起' }}
        </el-button>
      </section>

      <!-- 注意事项 - 橙色警示卡片 -->
      <section v-if="drug.warnings" class="card warning-card">
        <h3 class="section-title">
          <el-icon class="title-icon"><InfoFilled /></el-icon>
          注意事项
        </h3>
        <p class="section-content" :class="{ collapsed: warningsCollapsed && drug.warnings.length > 200 }">
          {{ drug.warnings }}
        </p>
        <el-button v-if="drug.warnings.length > 200" link type="warning" @click="warningsCollapsed = !warningsCollapsed" class="toggle">
          {{ warningsCollapsed ? '展开全文' : '收起' }}
        </el-button>
      </section>

      <!-- 成份 -->
      <section v-if="drug.composition" class="card info-card">
        <h3 class="section-title">
          <el-icon class="title-icon"><List /></el-icon>
          成份
        </h3>
        <p class="section-content" :class="{ collapsed: compositionCollapsed && drug.composition.length > 200 }">
          {{ drug.composition }}
        </p>
        <el-button v-if="drug.composition.length > 200" link type="primary" @click="compositionCollapsed = !compositionCollapsed" class="toggle">
          {{ compositionCollapsed ? '展开全文' : '收起' }}
        </el-button>
      </section>

      <!-- 返回按钮 -->
      <div class="actions">
        <el-button type="primary" plain @click="$router.push('/drugs')">返回药品查询</el-button>
      </div>
    </template>

    <!-- 空状态 -->
    <el-empty v-else-if="!loading" description="药品不存在" />
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { portalApi } from '../../api'

// 图标
import { Document, WarningFilled, Warning, Tickets, InfoFilled, List, SuccessFilled } from '@element-plus/icons-vue'

const route = useRoute()
const router = useRouter()
const loading = ref(true)
const error = ref('')
const drug = ref(null)

// 各段落展开状态
const indicationsCollapsed = ref(true)
const reactionsCollapsed = ref(true)
const dosageCollapsed = ref(true)
const warningsCollapsed = ref(true)
const compositionCollapsed = ref(true)

const loadDrug = async () => {
  loading.value = true
  error.value = ''
  try {
    const res = await portalApi.drugDetail(route.params.id)
    drug.value = res.data || null
  } catch (e) {
    drug.value = null
    error.value = e?.response?.data?.message || '药品不存在或服务暂时不可用'
  } finally {
    loading.value = false
  }
}

onMounted(loadDrug)
</script>

<style scoped>
.page {
  padding: 28px 32px;
  margin: 20px auto;
  background: #fff;
  border-radius: 8px;
  max-width: 950px;
}

/* 面包屑 */
.breadcrumb { margin-bottom: 12px; }

/* 免责声明 */
.disclaimer { margin-bottom: 18px; }

/* 通用卡片 */
.card {
  padding: 20px;
  border-radius: 8px;
  margin-bottom: 18px;
  border: 1px solid #ebeef5;
  transition: box-shadow 0.2s;
}
.card:hover { box-shadow: 0 2px 12px rgba(0,0,0,0.06); }

/* 基本信息卡片 */
.basic-info { background: #fafcff; }

/* 药品名称 */
.drug-name { margin: 0 0 12px; font-size: 22px; color: #1a1a2e; }
.brand-name { font-weight: normal; color: #909399; font-size: 14px; }

/* 标签 */
.tags { display: flex; gap: 8px; flex-wrap: wrap; margin-bottom: 16px; }

/* el-descriptions 元信息 */
.meta-descriptions { margin-top: 6px; }
:deep(.el-descriptions__title) { font-size: 14px; }
:deep(.el-descriptions__label) { color: #606266; font-weight: 500; }

/* 区块标题 */
.section-title {
  display: flex;
  align-items: center;
  gap: 8px;
  margin: 0 0 12px;
  font-size: 16px;
  font-weight: 600;
}
.title-icon { font-size: 18px; }

/* 普通信息卡片（蓝色主题） */
.info-card { background: #f8fafc; border-color: #e2e8f0; }
.info-card .section-title { color: #1a6fb5; }
.info-card .title-icon { color: #1a6fb5; }

/* 红色警示卡片（禁忌） */
.danger-card {
  background: #fff2f0;
  border-color: #ffccc7;
}
.danger-card .section-title { color: #cf1322; }
.danger-card .title-icon { color: #cf1322; }
.danger-card .section-content { color: #820014; }

/* 橙色警示卡片（不良反应/注意事项） */
.warning-card {
  background: #fff7e6;
  border-color: #ffd591;
}
.warning-card .section-title { color: #d46b08; }
.warning-card .title-icon { color: #d46b08; }
.warning-card .section-content { color: #873800; }

/* 内容文本 */
.section-content {
  color: #333;
  line-height: 1.8;
  margin: 0;
  white-space: pre-wrap;
  font-size: 14px;
}
.section-content.collapsed {
  overflow: hidden;
  display: -webkit-box;
  -webkit-line-clamp: 5;
  -webkit-box-orient: vertical;
}

/* 展开/收起按钮 */
.toggle { margin-top: 8px; }

/* 底部操作栏 */
.actions { text-align: center; margin-top: 28px; }

/* 响应式 */
@media (max-width: 640px) {
  .page { padding: 16px; }
}
</style>
