import axios from 'axios'
import { accessToken, session, logout } from './api'
const http=axios.create()
http.interceptors.request.use(config=>{
  const token=accessToken()
  config.sessionToken=token
  if(token) config.headers.Authorization=`Bearer ${token}`
  return config
})
http.interceptors.response.use(response=>{
  if(response.config.sessionToken!==accessToken()) return Promise.reject(Error('登入狀態已變更，請重新載入'))
  return response
},error=>{
  if(error.response?.status===401 && session.user) logout()
  return Promise.reject(error)
})
export default http
