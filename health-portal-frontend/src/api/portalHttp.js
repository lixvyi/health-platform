import axios from 'axios'

const portalHttp = axios.create({
  baseURL: '/api',
  timeout: 30000
})

portalHttp.interceptors.request.use(config => {
  const token = localStorage.getItem('portalToken')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

portalHttp.interceptors.response.use(
  res => {
    const data = res.data
    if (data.code && data.code !== 200) {
      return Promise.reject(new Error(data.message || '请求失败'))
    }
    return data
  },
  err => {
    const msg = err.response?.data?.message || err.message
    return Promise.reject(new Error(msg))
  }
)

export default portalHttp
