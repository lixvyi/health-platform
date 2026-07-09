<template>
  <div class="portal">
    <header class="header">
      <div class="container header-inner">
        <div class="logo" @click="$router.push('/')">健康大数据创新研发中心</div>
        <nav>
          <router-link to="/">首页</router-link>
          <router-link to="/news">新闻中心</router-link>
          <router-link to="/notice">通知公告</router-link>
          <router-link to="/policy">卫生政策</router-link>
          <router-link to="/knowledge">健康百科</router-link>
          <router-link to="/medical">医疗资源</router-link>
          <router-link to="/data">数据资源</router-link>
          <router-link to="/data-pool">数据资源池</router-link>
          <router-link to="/apps">应用中心</router-link>
          <router-link to="/symptom-check">症状自查</router-link>
          <router-link to="/about">关于我们</router-link>
          <router-link to="/ai">AI问答</router-link>
        </nav>
        <el-button type="primary" link @click="$router.push('/admin/login')">管理入口</el-button>
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
          <router-link to="/data-agreement">统计数据库用户协议</router-link>
          <span>|</span>
          <router-link to="/about">关于我们</router-link>
        </p>
      </div>
    </footer>
  </div>
</template>

<style scoped>
.portal { min-height: 100vh; display: flex; flex-direction: column; background: #f5f7fa; }
.header { background: #1a6fb5; color: #fff; box-shadow: 0 2px 8px rgba(0,0,0,.1); }
.header-inner { display: flex; align-items: center; justify-content: space-between; padding: 14px 20px; flex-wrap: wrap; gap: 8px; }
.logo { font-size: 18px; font-weight: 700; cursor: pointer; }
nav a { color: #fff; margin: 0 8px; text-decoration: none; opacity: .9; font-size: 14px; }
nav a.router-link-active { opacity: 1; font-weight: 600; border-bottom: 2px solid #fff; }
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
