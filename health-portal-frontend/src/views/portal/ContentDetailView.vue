<template>
  <div class="container page">
    <el-skeleton v-if="loading" :rows="10" animated />
    <el-result v-else-if="error" icon="error" title="内容加载失败" :sub-title="error">
      <template #extra>
        <el-button type="primary" @click="router.push('/knowledge')">返回健康百科</el-button>
        <el-button @click="load">重试</el-button>
      </template>
    </el-result>
    <template v-else-if="item">
      <el-breadcrumb separator="/" class="breadcrumb">
        <el-breadcrumb-item :to="{ path: '/' }">首页</el-breadcrumb-item>
        <el-breadcrumb-item :to="{ path: '/knowledge' }">健康百科</el-breadcrumb-item>
        <el-breadcrumb-item>{{ item.title }}</el-breadcrumb-item>
      </el-breadcrumb>

      <article>
        <h1>{{ item.title }}</h1>
        <div class="meta">
          <span v-if="item.publisher || item.author">{{ item.publisher || item.author }}</span>
          <span v-if="item.publishTime">发布：{{ formatDate(item.publishTime) }}</span>
          <span v-if="item.lastReviewTime">复核：{{ formatDate(item.lastReviewTime) }}</span>
          <span>阅读 {{ item.viewCount || 0 }}</span>
        </div>

        <el-alert
          v-if="item.isMedical === 1"
          title="本内容仅用于健康科普，不能替代医生面诊、诊断或治疗建议。"
          type="warning"
          show-icon
          :closable="false"
          class="notice"
        />
        <el-alert
          v-if="item.hasEmergencyWarning === 1"
          title="如出现急危重症表现，请立即拨打 120 或前往急诊。"
          type="error"
          show-icon
          :closable="false"
          class="notice"
        />

        <div class="content" v-html="item.content"></div>

        <section v-if="item.contraindications || item.adverseReactions" class="medical-extra">
          <h3 v-if="item.contraindications">禁忌证</h3>
          <p v-if="item.contraindications">{{ item.contraindications }}</p>
          <h3 v-if="item.adverseReactions">不良反应</h3>
          <p v-if="item.adverseReactions">{{ item.adverseReactions }}</p>
        </section>

        <section class="source-card" v-if="item.sourceName || item.sourceUrl">
          <h3>来源与时效</h3>
          <p v-if="item.sourceName">数据来源：{{ item.sourceName }}</p>
          <p v-if="item.sourcePublishDate">来源发布日期：{{ item.sourcePublishDate }}</p>
          <p v-if="item.targetAudience">适用人群：{{ item.targetAudience }}</p>
          <p v-if="item.verificationStatus">核验状态：{{ statusText(item.verificationStatus) }}</p>
          <a v-if="item.sourceUrl" :href="item.sourceUrl" target="_blank" rel="noopener noreferrer">查看原始来源</a>
        </section>
      </article>

      <section class="related">
        <h3>相关推荐</h3>
        <el-empty v-if="!related.length" description="暂无同类推荐" :image-size="60" />
        <div v-else class="related-list">
          <button v-for="content in related" :key="content.id" @click="openRelated(content.id)">
            <strong>{{ content.title }}</strong>
            <span>{{ content.summary || '查看详情' }}</span>
          </button>
        </div>
      </section>

      <div class="actions"><el-button type="primary" plain @click="router.push('/knowledge')">返回健康百科</el-button></div>
    </template>
  </div>
</template>

<script setup>
import { onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { portalApi } from '../../api'

const route = useRoute()
const router = useRouter()
const item = ref(null)
const related = ref([])
const loading = ref(true)
const error = ref('')

const formatDate = (value) => value ? String(value).slice(0, 10) : ''
const statusText = (value) => ({ VERIFIED: '已核验', OUTDATED: '已过期', UNVERIFIED: '未核验' }[value] || value)

const load = async () => {
  loading.value = true
  error.value = ''
  item.value = null
  related.value = []
  try {
    const [detailResponse, relatedResponse] = await Promise.all([
      portalApi.contentDetail(route.params.id),
      portalApi.relatedContents(route.params.id, { limit: 6 })
    ])
    item.value = detailResponse.data
    related.value = relatedResponse.data || []
  } catch (e) {
    error.value = e?.response?.data?.message || '内容不存在、未发布或服务暂时不可用'
  } finally {
    loading.value = false
  }
}

const openRelated = (id) => router.push(`/content/${id}`)

watch(() => route.params.id, load)
onMounted(load)
</script>

<style scoped>
.page { padding: 28px 32px; background: #fff; margin: 20px auto; border-radius: 8px; max-width: 900px; }
.breadcrumb { margin-bottom: 20px; }
h1 { margin: 0 0 12px; color: #303133; line-height: 1.4; }
.meta { display: flex; flex-wrap: wrap; gap: 16px; color: #909399; font-size: 13px; margin-bottom: 20px; }
.notice { margin-bottom: 14px; }
.content { line-height: 1.9; color: #303133; overflow-wrap: anywhere; }
.content :deep(img) { max-width: 100%; height: auto; }
.medical-extra, .source-card { margin-top: 24px; padding: 18px; border-radius: 8px; background: #f8fafc; }
.source-card { background: #f0f9ff; border: 1px solid #bae6fd; }
.source-card h3, .medical-extra h3 { margin-top: 0; }
.source-card p { margin: 6px 0; color: #475569; }
.source-card a { color: #1a6fb5; }
.related { border-top: 1px solid #ebeef5; margin-top: 28px; padding-top: 20px; }
.related-list { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 10px; }
.related-list button { text-align: left; border: 1px solid #e4e7ed; background: #fff; padding: 12px; border-radius: 6px; cursor: pointer; }
.related-list button:hover { border-color: #409eff; background: #f5faff; }
.related-list strong, .related-list span { display: block; }
.related-list span { margin-top: 6px; color: #909399; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.actions { margin-top: 24px; }
@media (max-width: 640px) { .page { padding: 20px; } .related-list { grid-template-columns: 1fr; } }
</style>

