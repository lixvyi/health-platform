<template>
  <el-container class="admin-layout">
    <el-aside width="220px" class="aside">
      <div class="brand">门户管理后台</div>
      <el-menu :default-active="$route.path" router background-color="#001529" text-color="#fff" active-text-color="#409eff">
        <el-menu-item index="/admin/dashboard">数据看板</el-menu-item>
        <el-menu-item index="/admin/contents/NEWS">新闻中心</el-menu-item>
        <el-menu-item index="/admin/contents/NOTICE">通知公告</el-menu-item>
        <el-menu-item index="/admin/contents/POLICY">卫生政策</el-menu-item>
        <el-menu-item index="/admin/contents/KNOWLEDGE">健康知识库</el-menu-item>
        <el-menu-item index="/admin/banners">轮播管理</el-menu-item>
        <el-menu-item index="/admin/apps">应用中心</el-menu-item>
        <el-menu-item index="/admin/data-collect">数据采集</el-menu-item>
        <el-menu-item index="/admin/certifications">科研认证审核</el-menu-item>
        <el-menu-item index="/admin/about">关于我们</el-menu-item>
      </el-menu>
    </el-aside>
    <el-container>
      <el-header class="header">
        <el-button type="primary" link @click="goPortal">← 返回门户首页</el-button>
        <div class="header-right">
          <span>{{ user?.realName || user?.username }}</span>
          <el-button type="danger" link @click="logout">退出</el-button>
        </div>
      </el-header>
      <el-main><router-view /></el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()
const user = computed(() => {
  try { return JSON.parse(localStorage.getItem('user') || '{}') } catch { return {} }
})
const logout = () => {
  localStorage.removeItem('token')
  localStorage.removeItem('user')
  router.push('/admin/login')
}
const goPortal = () => router.push('/')
</script>

<style scoped>
.admin-layout { min-height: 100vh; }
.aside { background: #001529; }
.brand { color: #fff; padding: 20px; font-weight: 700; }
.header { display: flex; justify-content: space-between; align-items: center; gap: 12px; background: #fff; border-bottom: 1px solid #eee; }
.header-right { display: flex; align-items: center; gap: 12px; }
</style>
