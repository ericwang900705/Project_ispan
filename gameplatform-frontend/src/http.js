import axios from 'axios'
import { accessToken, session, logout } from './api'

const http = axios.create({ headers: { 'ngrok-skip-browser-warning': '1' } })
http.interceptors.request.use(config => {
  const token = accessToken()
  config.sessionToken = token
  if (token) config.headers.Authorization = `Bearer ${token}`
  return config
})

http.interceptors.response.use(response => {
  if (response.config.sessionToken !== accessToken()) return Promise.reject(Error('登入狀態已變更，請重新載入'))
  if (typeof response.data === 'string' && /^\s*</.test(response.data))
    return Promise.reject(Error('API 收到網頁而非資料，請確認 ngrok 與 Nginx 設定，並重新部署前端。'))
  return response
}, error => {
  if (error.response?.status === 401 && session.user) logout()
  return Promise.reject(error)
})
export default http
