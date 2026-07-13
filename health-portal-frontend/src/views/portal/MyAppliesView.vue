<template>

  <div class="container page">

    <h2>我的申请</h2>

    <p class="lead">此处查看您的身份认证状态与已开通权限。管理员仅审核科研人员身份，通过后同等级资源与 API 可直接使用，无需逐条申请。</p>



    <el-card class="profile-card">

      <template #header>

        <div class="card-header">

          <span>账号信息</span>

          <el-button v-if="profile.certifyStatus !== 'APPROVED' && profile.role !== 'RESEARCHER'" type="warning" @click="certifyVisible = true">

            申请科研人员认证

          </el-button>

        </div>

      </template>

      <el-descriptions :column="2" border>

        <el-descriptions-item label="用户名">{{ profile.username }}</el-descriptions-item>

        <el-descriptions-item label="当前角色">

          <el-tag :type="profile.role === 'RESEARCHER' ? 'success' : 'info'">{{ roleLabel(profile.role) }}</el-tag>

        </el-descriptions-item>

        <el-descriptions-item label="认证状态">

          <el-tag :type="statusTag(profile.certifyStatus)">{{ statusLabel(profile.certifyStatus) }}</el-tag>

        </el-descriptions-item>

        <el-descriptions-item label="所属机构">{{ profile.organization || '—' }}</el-descriptions-item>

        <el-descriptions-item label="机构类型">{{ profile.orgType || '—' }}</el-descriptions-item>

        <el-descriptions-item label="研究方向">{{ profile.researchDirection || '—' }}</el-descriptions-item>

        <el-descriptions-item v-if="profile.certifyRemark" label="审核备注" :span="2">{{ profile.certifyRemark }}</el-descriptions-item>

      </el-descriptions>

    </el-card>



    <el-card class="rights-card">

      <template #header>已开通权限</template>

      <ul v-if="profile.accessRights?.length" class="rights-list">

        <li v-for="r in profile.accessRights" :key="r">{{ r }}</li>

      </ul>

      <el-empty v-else description="登录后可查看权限" :image-size="60" />

      <div class="quick-links">

        <el-button type="primary" @click="$router.push('/resources')">去下载数据资源</el-button>

        <el-button v-if="profile.role === 'RESEARCHER'" @click="$router.push('/api-services')">去调用 API</el-button>

      </div>

    </el-card>



    <CertifyDialog v-model="certifyVisible" @success="reload" />

  </div>

</template>



<script setup>

import { onMounted, ref } from 'vue'

import { useRouter } from 'vue-router'

import { portalUserApi } from '../../api/portalUser'

import { usePortalAuthStore } from '../../stores/portalAuth'

import CertifyDialog from '../../components/CertifyDialog.vue'



const router = useRouter()

const store = usePortalAuthStore()

const profile = ref({})

const certifyVisible = ref(false)



const statusLabel = (s) => ({ NONE: '未申请', PENDING: '审核中', APPROVED: '已通过', REJECTED: '已拒绝' }[s] || s)

const statusTag = (s) => ({ APPROVED: 'success', PENDING: 'warning', REJECTED: 'danger', NONE: 'info' }[s] || 'info')

const roleLabel = (r) => ({ USER: '普通用户', RESEARCHER: '科研人员' }[r] || r)



const reload = async () => {

  await store.fetchMe()

  const res = await portalUserApi.myApplies()

  profile.value = res.data || {}

}



onMounted(async () => {

  if (!store.isLoggedIn) {

    router.push('/')

    store.openAuthDialog('login')

    return

  }

  await reload()

})

</script>



<style scoped>

.page { padding: 24px; max-width: 800px; margin: 20px auto; }

.lead { color: #555; line-height: 1.8; margin-bottom: 20px; }

.profile-card, .rights-card { margin-bottom: 20px; }

.card-header { display: flex; justify-content: space-between; align-items: center; }

.rights-list { margin: 0 0 16px; padding-left: 20px; line-height: 2; color: #303133; }

.quick-links { display: flex; gap: 12px; flex-wrap: wrap; }

</style>


