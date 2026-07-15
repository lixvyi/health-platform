import http from './http'

export const portalApi = {
  home: () => http.get('/portal/home'),
  contents: (params) => http.get('/portal/contents', { params }),
  contentDetail: (id) => http.get(`/portal/contents/${id}`),
  relatedContents: (id, params) => http.get(`/portal/contents/${id}/related`, { params }),
  knowledgeCategories: () => http.get('/portal/knowledge/categories'),
  medicalProvinces: () => http.get('/portal/medical/provinces'),
  medicalCities: (province) => http.get(`/portal/medical/cities/${encodeURIComponent(province)}`),
  medicalHospitals: (params) => http.get('/portal/medical/hospitals', { params }),
  medicalHospitalDetail: (id) => http.get(`/portal/medical/hospitals/${id}`),
  medicalPublicTertiaryHospitals: (params) => http.get('/portal/medical/public-tertiary-hospitals', { params }),
  medicalHospitalGrades: () => http.get('/portal/medical/hospital-grades'),
  medicalSpecialtyRankings: (params) => http.get('/portal/medical/specialty-rankings', { params }),
  medicalDrugs: (params) => http.get('/portal/medical/drugs', { params }),
  banners: () => http.get('/portal/banners'),
  apps: () => http.get('/portal/apps'),
  about: () => http.get('/portal/about'),
  stats: () => http.get('/portal/stats'),
  openDataMeta: () => http.get('/portal/open-data'),
  openDataFeatured: () => http.get('/portal/open-data/featured'),
  openDataDetail: (id) => http.get(`/portal/open-data/${id}`),
  dataPoolArchitecture: () => http.get('/portal/data-pool/architecture'),
  dataPoolInternet: () => http.get('/portal/data-pool/internet'),
  dataPoolCollectStatus: () => http.get('/portal/data-pool/collect/status'),
  dataPoolBigDataStatus: () => http.get('/portal/data-pool/bigdata/status'),
  dataPoolGovernance: () => http.get('/portal/data-pool/governance'),
  // AI：优先带门户登录 token，方便绑定会话到当前用户
  aiChat: (data) => {
    const headers = {}
    const portalToken = localStorage.getItem('portalToken')
    if (portalToken) headers.Authorization = `Bearer ${portalToken}`
    return http.post('/ai/chat', data, { headers })
  },
  aiSessions: () => {
    const headers = {}
    const portalToken = localStorage.getItem('portalToken')
    if (portalToken) headers.Authorization = `Bearer ${portalToken}`
    return http.get('/ai/sessions', { headers })
  },
  aiSessionMessages: (sessionId) => {
    const headers = {}
    const portalToken = localStorage.getItem('portalToken')
    if (portalToken) headers.Authorization = `Bearer ${portalToken}`
    return http.get(`/ai/sessions/${encodeURIComponent(sessionId)}`, { headers })
  },
  aiDeleteSession: (sessionId) => {
    const headers = {}
    const portalToken = localStorage.getItem('portalToken')
    if (portalToken) headers.Authorization = `Bearer ${portalToken}`
    return http.delete(`/ai/sessions/${encodeURIComponent(sessionId)}`, { headers })
  },
  // 热词关联政策搜索
  policiesByWord: (word) => http.get('/portal/policies-by-word', {params: {word}}),
  // 热词共现网络
  cooccurrence: (year) => http.get('/portal/cooccurrence', {params: {year}}),

  // 药品详情
  drugSearch: (params) => http.get('/drug/search', {params}),
  drugDetail: (id) => http.get(`/drug/${id}`),
  drugRecommend: (params) => http.get('/drug/recommend', {params}),
  drugCategoryStats: () => http.get('/drug/stats/category-distribution'),
  drugDosageFormStats: () => http.get('/drug/stats/dosage-form-stats')
}

// 症状自查API
export const symptomApi = {
  // 获取身体部位树
  getBodyParts: () => http.get('/v1/body-parts'),
  // 获取指定部位下的症状
  getSymptomsByPartId: (partId) => http.get('/v1/symptoms', { params: { partId } }),
  // 症状自查
  checkSymptoms: (symptomIds) => http.post('/v1/check', { symptomIds })
}

export const adminApi = {
  login: (data) => http.post('/auth/login', data),
  contents: (params) => http.get('/admin/contents', { params }),
  createContent: (data) => http.post('/admin/contents', data),
  updateContent: (id, data) => http.put(`/admin/contents/${id}`, data),
  deleteContent: (id) => http.delete(`/admin/contents/${id}`),
  banners: () => http.get('/admin/banners'),
  createBanner: (data) => http.post('/admin/banners', data),
  updateBanner: (id, data) => http.put(`/admin/banners/${id}`, data),
  deleteBanner: (id) => http.delete(`/admin/banners/${id}`),
  apps: () => http.get('/admin/apps'),
  createApp: (data) => http.post('/admin/apps', data),
  updateApp: (id, data) => http.put(`/admin/apps/${id}`, data),
  deleteApp: (id) => http.delete(`/admin/apps/${id}`),
  about: () => http.get('/admin/about'),
  updateAbout: (data) => http.put('/admin/about', data),
  updateHomeIntro: (data) => http.put('/admin/home-intro', data),
  stats: () => http.get('/admin/stats'),
  dataPoolCollect: () => http.post('/admin/data-pool/collect'),
  dataPoolCollectStatus: () => http.get('/admin/data-pool/collect/status'),
  dataPoolEtlRun: () => http.post('/admin/data-pool/etl/run'),
  updateGovernanceIssue: (id, data) => http.patch(`/admin/data-pool/governance/issues/${id}`, data),
  upload: (file) => {
    const form = new FormData()
    form.append('file', file)
    return http.post('/admin/files/upload', form, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
  },
  // API 应用管理
  apiApps: () => http.get('/admin/api-apps'),
  approveApiApp: (id) => http.put(`/admin/api-apps/${id}/approve`),
  toggleApiApp: (id) => http.put(`/admin/api-apps/${id}/toggle-status`),
  rotateApiSecret: (id) => http.put(`/admin/api-apps/${id}/rotate-secret`),
  updateApiAppQuota: (id, data) => http.put(`/admin/api-apps/${id}/quota`, data),
  // API 用量统计
  apiUsageToday: () => http.get('/admin/api-usage/today'),
  apiUsageTrend: (days) => http.get('/admin/api-usage/trend', {params: {days}}),
  apiUsageOverview: (days) => http.get('/admin/api-usage/overview', {params: {days}})
}
