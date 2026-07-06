<template>
  <div class="container page">
    <h2>国家统计数据库用户使用协议</h2>
    <p class="lead">
      本门户引用
      <a href="https://data.stats.gov.cn" target="_blank" rel="noopener noreferrer">国家统计数据库</a>
      公开发布数据时，用户须同时遵守国家统计局公布的用户使用协议。以下为协议要点摘要，完整条款以官方为准。
    </p>

    <el-alert type="info" :closable="false" show-icon class="mb">
      使用或注册为国家统计数据库用户，即视为已阅读并同意官方协议全部条款。
    </el-alert>

    <section class="block">
      <h3>版权声明</h3>
      <ol>
        <li v-for="(item, i) in summary.copyright" :key="'c' + i">{{ item }}</li>
        <li>
          若第三方网站拟以任何形式建立链接至国家统计数据库，须先取得国家统计局同意；国家统计局有权决定是否核准。
        </li>
        <li>对于违反法律法规、不尊重声明的行为，国家统计局保留追究法律责任的权利。</li>
      </ol>
    </section>

    <section class="block">
      <h3>免责声明</h3>
      <ol>
        <li v-for="(item, i) in summary.disclaimer" :key="'d' + i">{{ item }}</li>
      </ol>
    </section>

    <section class="block">
      <h3>隐私保密说明</h3>
      <ol>
        <li v-for="(item, i) in summary.privacy" :key="'p' + i">{{ item }}</li>
      </ol>
    </section>

    <section class="block">
      <h3>附则</h3>
      <p>本协议未涉及的问题适用国家有关法律法规；本协议与法律法规冲突时，以国家法律法规为准。对本协议的解释、修改及更新权均属于国家统计局。</p>
    </section>

    <DataSourceNotice source="国家统计局" license="本页为协议摘要，数据引用须注明出处" />

    <div class="actions">
      <el-button type="primary" @click="$router.push('/data')">查看数据资源目录</el-button>
      <el-button @click="$router.push('/')">返回首页</el-button>
    </div>
  </div>
</template>

<script setup>
import DataSourceNotice from '../../components/DataSourceNotice.vue'
import { NBS_AGREEMENT_SUMMARY as summary } from '../../constants/dataSources.js'
</script>

<style scoped>
.page { padding: 24px; background: #fff; margin: 20px auto; border-radius: 8px; max-width: 900px; line-height: 1.8; }
.lead { color: #555; }
.lead a { color: #1a6fb5; }
.mb { margin-bottom: 20px; }
.block { margin-bottom: 24px; }
.block h3 { color: #1a6fb5; margin-bottom: 12px; font-size: 16px; }
.block ol { padding-left: 20px; color: #444; }
.block li { margin-bottom: 8px; }
.actions { margin-top: 24px; display: flex; gap: 12px; }
</style>
