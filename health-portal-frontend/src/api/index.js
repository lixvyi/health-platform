import http from './http'

export const portalApi = {
  home: () => http.get('/portal/home'),
  contents: (params) => http.get('/portal/contents', { params }),
  contentDetail: (id) => http.get(`/portal/contents/${id}`),
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
  aiChat: (data) => http.post('/ai/chat', data)
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
  upload: (file) => {
    const form = new FormData()
    form.append('file', file)
    return http.post('/admin/files/upload', form, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
  }
}
