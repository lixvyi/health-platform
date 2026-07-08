import { createRouter, createWebHistory } from 'vue-router'
import PortalLayout from '../layouts/PortalLayout.vue'
import AdminLayout from '../layouts/AdminLayout.vue'

const routes = [
  {
    path: '/',
    component: PortalLayout,
    children: [
      { path: '', name: 'home', component: () => import('../views/portal/HomeView.vue') },
      { path: 'news', name: 'news', component: () => import('../views/portal/ContentListView.vue'), props: { category: 'NEWS', title: '新闻中心' } },
      { path: 'notice', name: 'notice', component: () => import('../views/portal/ContentListView.vue'), props: { category: 'NOTICE', title: '通知公告' } },
      { path: 'policy', name: 'policy', component: () => import('../views/portal/ContentListView.vue'), props: { category: 'POLICY', title: '卫生政策' } },
      { path: 'knowledge', name: 'knowledge', component: () => import('../views/portal/ContentListView.vue'), props: { category: 'KNOWLEDGE', title: '健康知识库' } },
      { path: 'content/:id', name: 'contentDetail', component: () => import('../views/portal/ContentDetailView.vue') },
      { path: 'apps', name: 'apps', component: () => import('../views/portal/AppsView.vue') },
      { path: 'data', name: 'data', component: () => import('../views/portal/DataResourceView.vue') },
      { path: 'data-pool', name: 'dataPool', component: () => import('../views/portal/DataPoolView.vue') },
      { path: 'data-agreement', name: 'dataAgreement', component: () => import('../views/portal/DataAgreementView.vue') },
      { path: 'about', name: 'about', component: () => import('../views/portal/AboutView.vue') },
      { path: 'ai', name: 'ai', component: () => import('../views/portal/AiChatView.vue') },
      { path: 'symptom-check', name: 'symptomCheck', component: () => import('../views/portal/SymptomCheckView.vue') }
    ]
  },
  { path: '/admin/login', name: 'adminLogin', component: () => import('../views/admin/LoginView.vue') },
  {
    path: '/admin',
    component: AdminLayout,
    meta: { requiresAuth: true },
    children: [
      { path: '', redirect: '/admin/dashboard' },
      { path: 'dashboard', component: () => import('../views/admin/DashboardView.vue') },
      { path: 'contents/:code', component: () => import('../views/admin/ContentManageView.vue') },
      { path: 'banners', component: () => import('../views/admin/BannerManageView.vue') },
      { path: 'apps', component: () => import('../views/admin/AppManageView.vue') },
      { path: 'about', component: () => import('../views/admin/AboutManageView.vue') },
      { path: 'data-collect', component: () => import('../views/admin/DataCollectView.vue') }
    ]
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

router.beforeEach((to, from, next) => {
  if (to.meta.requiresAuth && !localStorage.getItem('token')) {
    next('/admin/login')
  } else {
    next()
  }
})

export default router
