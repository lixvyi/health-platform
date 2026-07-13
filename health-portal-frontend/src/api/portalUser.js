import http from './http'
import portalHttp from './portalHttp'

async function downloadBlob(url, fallbackName) {
  const token = localStorage.getItem('portalToken')
  const res = await fetch(url, {
    headers: token ? { Authorization: `Bearer ${token}` } : {}
  })
  if (!res.ok) {
    let message = '下载失败'
    try {
      const json = await res.json()
      message = json.message || message
    } catch { /* binary response */ }
    throw new Error(message)
  }
  const blob = await res.blob()
  let filename = fallbackName
  const cd = res.headers.get('Content-Disposition')
  if (cd) {
    const m = cd.match(/filename\*=UTF-8''([^;]+)|filename="?([^";]+)"?/)
    if (m) filename = decodeURIComponent(m[1] || m[2])
  }
  const objectUrl = URL.createObjectURL(blob)
  const a = document.createElement('a')
  a.href = objectUrl
  a.download = filename
  document.body.appendChild(a)
  a.click()
  a.remove()
  URL.revokeObjectURL(objectUrl)
}

export const portalUserApi = {
  register: (data) => http.post('/portal/auth/register', data),
  login: (data) => http.post('/portal/auth/login', data),
  me: () => portalHttp.get('/portal/user/me'),
  certify: (data) => portalHttp.post('/portal/user/certify', data),
  myApplies: () => portalHttp.get('/portal/user/applies'),

  listResources: () => http.get('/portal/catalog/resources'),
  getResource: (id) => http.get(`/portal/catalog/resources/${id}`),
  resourceAccessStatus: (id) => portalHttp.get(`/portal/user/resources/${id}/access-status`),
  downloadResourceFile: (id) => downloadBlob(`/api/portal/user/resources/${id}/download-file`),

  listApis: () => http.get('/portal/catalog/apis'),
  getApi: (id) => http.get(`/portal/catalog/apis/${id}`),
  apiAccessStatus: () => portalHttp.get('/portal/user/apis/access-status'),
  invokeApi: (id) => portalHttp.post(`/portal/user/apis/${id}/invoke`),

  downloadPolicyFile: (contentId) =>
    downloadBlob(`/api/portal/user/policies/${contentId}/download-file`, `policy-${contentId}.html`)
}

export const portalAdminApi = {
  certifications: (params) => http.get('/admin/portal/certifications', { params }),
  reviewCertification: (userId, data) => http.put(`/admin/portal/certifications/${userId}/review`, data)
}
