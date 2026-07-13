<template>
  <div>
    <h2 style="margin:0 0 16px">科研人员认证审核</h2>
    <el-radio-group v-model="filter" @change="load" style="margin-bottom:16px">
      <el-radio-button label="">全部</el-radio-button>
      <el-radio-button label="PENDING">待审核</el-radio-button>
      <el-radio-button label="APPROVED">已通过</el-radio-button>
      <el-radio-button label="REJECTED">已拒绝</el-radio-button>
    </el-radio-group>
    <el-table :data="list" stripe>
      <el-table-column prop="username" label="账号" width="120" />
      <el-table-column prop="realName" label="姓名" width="100" />
      <el-table-column prop="organization" label="机构" min-width="140" />
      <el-table-column prop="orgType" label="类型" width="90" />
      <el-table-column prop="researchDirection" label="研究方向" min-width="120" show-overflow-tooltip />
      <el-table-column prop="certifyReason" label="申请理由" min-width="140" show-overflow-tooltip />
      <el-table-column label="状态" width="90">
        <template #default="{ row }">
          <el-tag :type="tagType(row.certifyStatus)" size="small">{{ row.certifyStatus }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="160" fixed="right">
        <template #default="{ row }">
          <template v-if="row.certifyStatus === 'PENDING'">
            <el-button link type="success" @click="review(row, true)">通过</el-button>
            <el-button link type="danger" @click="review(row, false)">拒绝</el-button>
          </template>
          <span v-else class="muted">{{ row.certifyRemark || '-' }}</span>
        </template>
      </el-table-column>
    </el-table>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { portalAdminApi } from '../../api/portalUser'

const list = ref([])
const filter = ref('PENDING')

const tagType = (s) => ({ APPROVED: 'success', PENDING: 'warning', REJECTED: 'danger' }[s] || 'info')

const load = async () => {
  const res = await portalAdminApi.certifications(filter.value ? { status: filter.value } : {})
  list.value = res.data || []
}

const review = async (row, approved) => {
  let remark = ''
  if (!approved) {
    const { value } = await ElMessageBox.prompt('请输入拒绝原因', '拒绝认证', { inputPlaceholder: '可选' })
    remark = value || ''
  }
  await portalAdminApi.reviewCertification(row.userId, { approved, remark })
  ElMessage.success(approved ? '已通过认证' : '已拒绝')
  load()
}

onMounted(load)
</script>

<style scoped>
.muted { color: #999; font-size: 12px; }
</style>
