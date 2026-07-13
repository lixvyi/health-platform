<template>

  <div class="container page">

    <h2>数据资源下载中心</h2>

    <p class="lead">
      已同步项目内 <strong>{{ resources.length }}</strong> 类真实开放数据（国家统计局年度数据、上海 CSV 原始文件、已入库加工 JSON）。
      下载内容为实际导入的数据，非模拟样本。
    </p>



    <el-table :data="resources" stripe>

      <el-table-column prop="name" label="资源名称" min-width="200" />

      <el-table-column prop="category" label="类别" width="100" />

      <el-table-column prop="source" label="来源" min-width="140" />

      <el-table-column prop="sizeLabel" label="文件大小" width="110" />

      <el-table-column label="权限" width="100">

        <template #default="{ row }">

          <el-tag size="small" :type="levelTag(row.permissionLevel)">{{ levelLabel(row.permissionLevel) }}</el-tag>

        </template>

      </el-table-column>

      <el-table-column label="操作" width="160" fixed="right">

        <template #default="{ row }">

          <el-button link type="primary" @click="showDetail(row)">详情</el-button>

          <el-button link type="success" :disabled="!canDownload(row)" @click="handleDownload(row)">下载</el-button>

        </template>

      </el-table-column>

    </el-table>



    <el-dialog v-model="detailVisible" :title="detail?.name" width="600px">

      <p>{{ detail?.description }}</p>

      <el-descriptions :column="2" border size="small">

        <el-descriptions-item label="数据类型">{{ detail?.dataType }}</el-descriptions-item>

        <el-descriptions-item label="文件大小">{{ detail?.sizeLabel }}</el-descriptions-item>

        <el-descriptions-item label="来源" :span="2">{{ detail?.source }}</el-descriptions-item>

      </el-descriptions>

    </el-dialog>

  </div>

</template>



<script setup>

import { onMounted, ref } from 'vue'

import { ElMessage } from 'element-plus'

import { portalUserApi } from '../../api/portalUser'

import { usePortalAuthStore } from '../../stores/portalAuth'



const store = usePortalAuthStore()

const resources = ref([])

const detail = ref(null)

const detailVisible = ref(false)



const levelLabel = (l) => ({ PUBLIC: '公开', STANDARD: '标准', RESEARCHER: '科研' }[l] || l)

const levelTag = (l) => ({ PUBLIC: 'success', RESEARCHER: 'warning' }[l] || 'info')



const canDownload = (row) => {

  if (!store.isLoggedIn) return false

  if (row.permissionLevel === 'RESEARCHER') return store.isResearcher

  return true

}



const showDetail = (row) => {

  detail.value = row

  detailVisible.value = true

}



const handleDownload = (row) => {

  store.requireAuth(async () => {

    if (row.permissionLevel === 'RESEARCHER' && !store.isResearcher) {

      ElMessage.warning('该资源仅限科研人员下载，请先在「我的申请」提交身份认证')

      return

    }

    try {

      await portalUserApi.downloadResourceFile(row.id)

      ElMessage.success('文件已开始下载')

    } catch (e) {

      ElMessage.error(e.message)

    }

  })

}



onMounted(async () => {

  const res = await portalUserApi.listResources()

  resources.value = res.data || []

  if (store.isLoggedIn) await store.fetchMe()

})

</script>



<style scoped>

.page { padding: 24px; max-width: 1100px; margin: 20px auto; }

.lead { color: #555; line-height: 1.8; margin-bottom: 16px; }

</style>


