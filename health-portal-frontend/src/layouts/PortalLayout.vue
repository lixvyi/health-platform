<template>
  <div class="portal">
    <header class="header">
      <div class="container header-inner">
        <button class="notice-button" type="button" title="通知公告" aria-label="通知公告" @click="$router.push('/notice')">
          <el-icon><Bell /></el-icon>
        </button>
        <div class="logo" @click="$router.push('/')">健康大数据创新研发中心</div>
        <nav>
          <router-link to="/">首页</router-link>
          <router-link to="/news">新闻中心</router-link>
          <router-link to="/policy">卫生政策</router-link>
          <router-link to="/knowledge">健康百科</router-link>
          <router-link to="/medical">医疗资源</router-link>
          <router-link to="/data">数据资源</router-link>
          <router-link to="/resources">资源下载</router-link>
          <router-link to="/api-services">API服务</router-link>
          <router-link to="/data-pool">数据资源池</router-link>
          <router-link to="/symptom-check">症状自查</router-link>
          <router-link to="/about">关于我们</router-link>
          <router-link to="/ai">AI问答</router-link>
        </nav>
        <div class="user-area">
          <template v-if="store.isLoggedIn">
            <el-dropdown trigger="click">
              <span class="user-name">{{ store.user?.username }} <el-icon><ArrowDown /></el-icon></span>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item @click="$router.push('/my-applies')">我的申请</el-dropdown-item>
                  <el-dropdown-item v-if="store.certifyStatus !== 'APPROVED'" @click="certifyVisible = true">科研人员认证</el-dropdown-item>
                  <el-dropdown-item divided @click="$router.push({ path: '/ai', query: { new: '1' } })">新对话</el-dropdown-item>
                  <el-dropdown-item @click="$router.push({ path: '/ai', query: { history: '1' } })">历史对话</el-dropdown-item>
                  <el-dropdown-item divided @click="logout">退出登录</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
          </template>
          <el-button v-else type="primary" link class="login-link" @click="store.openAuthDialog('login')">登录</el-button>
          <el-button type="primary" link @click="$router.push('/admin/login')">管理入口</el-button>
        </div>
      </div>
    </header>
    <main class="main">
      <router-view />
    </main>
    <footer class="footer">
      <div class="container footer-inner">
        <p class="copyright">© 2026 健康大数据应用创新研发中心 | 中南大学计算机学院实训项目</p>
        <p class="legal">
          引用
          <a href="https://data.stats.gov.cn" target="_blank" rel="noopener noreferrer">国家统计数据库</a>
          等开放数据时，须注明「来源：国家统计局」并遵守其
          <router-link to="/data-agreement">用户使用协议</router-link>。
          本门户不爬取政务内网数据。
        </p>
        <p class="links">
          <router-link to="/data">数据资源目录</router-link>
          <span>|</span>
          <router-link to="/resources">资源申请</router-link>
          <span>|</span>
          <router-link to="/api-services">API服务</router-link>
          <span>|</span>
          <router-link to="/data-agreement">统计数据库用户协议</router-link>
          <span>|</span>
          <router-link to="/about">关于我们</router-link>
        </p>
      </div>
    </footer>

    <PortalAuthDialog />
    <CertifyDialog v-model="certifyVisible" @success="store.fetchMe()" />
  </div>
</template>

<script setup>
import { onMounted, ref } from 'vue'
import { Bell, ArrowDown } from '@element-plus/icons-vue'
import { usePortalAuthStore } from '../stores/portalAuth'
import PortalAuthDialog from '../components/PortalAuthDialog.vue'
import CertifyDialog from '../components/CertifyDialog.vue'

const store = usePortalAuthStore()
const certifyVisible = ref(false)

const logout = () => {
  store.logout()
}

onMounted(() => {
  if (store.isLoggedIn) store.fetchMe()
})
</script>

<style scoped>
.portal { min-height: 100vh; display: flex; flex-direction: column; background: #f5f7fa; }
.header { background: #1a6fb5; color: #fff; box-shadow: 0 2px 8px rgba(0,0,0,.1); }
.header-inner { display: flex; align-items: center; justify-content: space-between; padding: 14px 20px; flex-wrap: wrap; gap: 8px; }
.notice-button { width: 32px; height: 32px; border: 1px solid rgba(255,255,255,.35); border-radius: 50%; background: rgba(255,255,255,.12); color: #fff; display: inline-flex; align-items: center; justify-content: center; cursor: pointer; transition: background .2s, transform .2s; }
.notice-button:hover { background: rgba(255,255,255,.22); transform: translateY(-1px); }
.logo { font-size: 18px; font-weight: 700; cursor: pointer; }
nav a { color: #fff; margin: 0 6px; text-decoration: none; opacity: .9; font-size: 13px; }
nav a.router-link-active { opacity: 1; font-weight: 600; border-bottom: 2px solid #fff; }
.user-area { display: flex; align-items: center; gap: 8px; }
.user-name { color: #fff; cursor: pointer; font-size: 14px; display: inline-flex; align-items: center; gap: 4px; }
.login-link { color: #fff !important; }
.main { flex: 1; }
.footer { background: #0d3f66; color: #cde; padding: 20px 16px; font-size: 12px; line-height: 1.8; }
.footer-inner { text-align: center; }
.copyright { margin: 0 0 6px; font-size: 13px; }
.legal { margin: 0 0 8px; color: #a8c5db; max-width: 800px; margin-left: auto; margin-right: auto; }
.links { margin: 0; }
.footer a { color: #9fd4ff; text-decoration: none; }
.footer a:hover { text-decoration: underline; }
.links span { margin: 0 8px; color: #5a8aaa; }
.container { max-width: 1200px; margin: 0 auto; }
</style>
