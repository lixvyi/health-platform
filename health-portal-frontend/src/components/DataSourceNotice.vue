<template>
  <div class="data-source-notice" :class="{ compact }">
    <el-icon><InfoFilled /></el-icon>
    <span>
      <strong>来源：{{ source }}</strong>
      <template v-if="license"> · {{ license }}</template>
      <template v-if="link">
        · <a :href="link" target="_blank" rel="noopener noreferrer" @click.stop>访问官方数据库</a>
      </template>
    </span>
  </div>
</template>

<script setup>
import { InfoFilled } from '@element-plus/icons-vue'

defineProps({
  source: { type: String, default: '国家统计局' },
  license: { type: String, default: '转载或引用须注明出处，不得曲解原意' },
  link: { type: String, default: 'https://data.stats.gov.cn' },
  compact: { type: Boolean, default: false }
})
</script>

<style scoped>
.data-source-notice {
  display: flex;
  align-items: flex-start;
  gap: 8px;
  padding: 10px 12px;
  background: #f0f7ff;
  border: 1px solid #c6e2ff;
  border-radius: 6px;
  font-size: 13px;
  color: #3d5a80;
  line-height: 1.6;
}
.data-source-notice.compact { padding: 6px 10px; font-size: 12px; }
.data-source-notice a { color: #1a6fb5; text-decoration: none; }
.data-source-notice a:hover { text-decoration: underline; }
</style>
