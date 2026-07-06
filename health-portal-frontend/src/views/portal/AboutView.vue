<template>
  <div class="container page">
    <h2>{{ about.title || '关于我们' }}</h2>
    <div v-html="about.content"></div>

    <section class="compliance">
      <h3>数据合规与版权声明</h3>
      <p>
        本中心引用<a href="https://data.stats.gov.cn" target="_blank" rel="noopener noreferrer">国家统计数据库</a>
        等政府开放数据时，遵守官方用户使用协议，转载或引用须注明「来源：国家统计局」，
        不得曲解、修改原意。政务共享数据须通过正规授权渠道接入，本门户不爬取政务内网数据。
      </p>
      <router-link to="/data-agreement">查看国家统计数据库用户使用协议</router-link>
      ·
      <router-link to="/data">查看数据资源目录</router-link>
    </section>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { portalApi } from '../../api'

const about = ref({})
onMounted(async () => {
  const res = await portalApi.about()
  about.value = res.data
})
</script>

<style scoped>
.page { padding: 24px; background: #fff; margin: 20px auto; border-radius: 8px; max-width: 900px; line-height: 1.8; }
.compliance { margin-top: 32px; padding-top: 20px; border-top: 1px solid #eee; }
.compliance h3 { color: #1a6fb5; font-size: 16px; margin-bottom: 12px; }
.compliance a { color: #1a6fb5; text-decoration: none; }
.compliance a:hover { text-decoration: underline; }
</style>
