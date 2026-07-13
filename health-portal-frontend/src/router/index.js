import {createRouter, createWebHistory} from 'vue-router'
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
      { path: 'knowledge', name: 'knowledge', component: () => import('../views/portal/KnowledgeView.vue') },
      { path: 'medical', name: 'medical', component: () => import('../views/portal/MedicalResourceView.vue') },
      { path: 'medical/hospitals/:id', name: 'hospitalDetail', component: () => import('../views/portal/HospitalDetailView.vue') },
      { path: 'content/:id', name: 'contentDetail', component: () => import('../views/portal/ContentDetailView.vue') },
      { path: 'apps', name: 'apps', component: () => import('../views/portal/AppsView.vue') },
      { path: 'data', name: 'data', component: () => import('../views/portal/DataResourceView.vue') },
      { path: 'resources', name: 'resources', component: () => import('../views/portal/ResourceApplyView.vue') },
      { path: 'api-services', name: 'apiServices', component: () => import('../views/portal/ApiServiceView.vue') },
      { path: 'my-applies', name: 'myApplies', component: () => import('../views/portal/MyAppliesView.vue') },
      { path: 'data-pool', name: 'dataPool', component: () => import('../views/portal/DataPoolView.vue') },
      { path: 'data-agreement', name: 'dataAgreement', component: () => import('../views/portal/DataAgreementView.vue') },
      { path: 'about', name: 'about', component: () => import('../views/portal/AboutView.vue') },
      { path: 'ai', name: 'ai', component: () => import('../views/portal/AiChatView.vue') },
      {path: 'symptom-check', name: 'symptomCheck', component: () => import('../views/portal/SymptomCheckView.vue')},
      {
        path: 'policy-hotword-trend',
        name: 'policyHotwordTrend',
        component: () => import('../views/portal/PolicyHotwordTrendView.vue')
      }
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
      { path: 'data-collect', component: () => import('../views/admin/DataCollectView.vue') },
      { path: 'certifications', component: () => import('../views/admin/CertificationReviewView.vue') },
      { path: 'apply-review', component: () => import('../views/admin/ApplyReviewView.vue') },
      { path: 'about', component: () => import('../views/admin/AboutManageView.vue') }
    ]
  },
  { path: '/:pathMatch(.*)*', name: 'notFound', component: () => import('../views/portal/NotFoundView.vue') }
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
