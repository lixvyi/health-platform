<template>
  <div>
    <h2 style="margin:0 0 16px">数据与 API 申请审核</h2>
    <el-tabs v-model="tab" @tab-change="load">
      <el-tab-pane label="数据资源申请" name="data">
        <el-radio-group v-model="dataFilter" @change="loadData" style="margin-bottom:12px">
          <el-radio-button label="PENDING">待审核</el-radio-button>
          <el-radio-button label="">全部</el-radio-button>
        </el-radio-group>
        <el-table :data="dataList" stripe>
          <el-table-column prop="username" label="账号" width="110" />
          <el-table-column prop="realName" label="姓名" width="90" />
          <el-table-column prop="organization" label="机构" width="120" />
          <el-table-column prop="resourceName" label="资源" min-width="160" />
          <el-table-column prop="projectName" label="项目" width="120" />
          <el-table-column prop="purpose" label="用途" width="120" show-overflow-tooltip />
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="tagType(row.status)" size="small">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="140" fixed="right">
            <template #default="{ row }">
              <template v-if="row.status === 'PENDING'">
                <el-button link type="success" @click="reviewData(row, true)">通过</el-button>
                <el-button link type="danger" @click="reviewData(row, false)">拒绝</el-button>
              </template>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
      <el-tab-pane label="API 权限申请" name="api">
        <el-radio-group v-model="apiFilter" @change="loadApi" style="margin-bottom:12px">
          <el-radio-button label="PENDING">待审核</el-radio-button>
          <el-radio-button label="">全部</el-radio-button>
        </el-radio-group>
        <el-table :data="apiList" stripe>
          <el-table-column prop="username" label="账号" width="110" />
          <el-table-column prop="realName" label="姓名" width="90" />
          <el-table-column prop="apiName" label="API" min-width="160" />
          <el-table-column prop="projectName" label="项目" width="120" />
          <el-table-column prop="purpose" label="用途" width="120" />
          <el-table-column label="状态" width="90">
            <template #default="{ row }">
              <el-tag :type="tagType(row.status)" size="small">{{ row.status }}</el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="140" fixed="right">
            <template #default="{ row }">
              <template v-if="row.status === 'PENDING'">
                <el-button link type="success" @click="reviewApi(row, true)">通过</el-button>
                <el-button link type="danger" @click="reviewApi(row, false)">拒绝</el-button>
              </template>
            </template>
          </el-table-column>
        </el-table>
      </el-tab-pane>
    </el-tabs>
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { portalAdminApi } from '../../api/portalUser'

const tab = ref('data')
const dataList = ref([])
const apiList = ref([])
const dataFilter = ref('PENDING')
const apiFilter = ref('PENDING')

const tagType = (s) => ({ APPROVED: 'success', PENDING: 'warning', REJECTED: 'danger' }[s] || 'info')

const loadData = async () => {
  const res = await portalAdminApi.dataApplies(dataFilter.value ? { status: dataFilter.value } : {})
  dataList.value = res.data || []
}

const loadApi = async () => {
  const res = await portalAdminApi.apiApplies(apiFilter.value ? { status: apiFilter.value } : {})
  apiList.value = res.data || []
}

const load = () => tab.value === 'data' ? loadData() : loadApi()

const reviewData = async (row, approved) => {
  await portalAdminApi.reviewDataApply(row.id, { approved, remark: approved ? '审核通过' : '不符合要求' })
  ElMessage.success('已处理')
  loadData()
}

const reviewApi = async (row, approved) => {
  await portalAdminApi.reviewApiApply(row.id, { approved, remark: approved ? '审核通过' : '不符合要求' })
  ElMessage.success('已处理')
  loadApi()
}

onMounted(loadData)
</script>
