import { reactive } from 'vue'
import { loginFor } from './permissions.js'

// --- 全域會話狀態 ---
export const session = reactive({
  user: null,
  ready: false,
  online: false,
  loginArea: readLoginArea()
})

function readLoginArea() {
  try { return sessionStorage.getItem('support-login-area') === 'staff' ? 'staff' : 'member' }
  catch { return 'member' }
}

const SESSION_KEY = 'support-auth-session-v1'
let authGeneration = 0
let token = null
let stream = null
let expiryTimer = null
let generation = 0

// --- 清除本地驗證與狀態 ---
function clearLocal(removeStored = true) {
  authGeneration++
  if (removeStored) { try { sessionStorage.removeItem(SESSION_KEY) } catch {} }
  token = null
  clearTimeout(expiryTimer)
  stopEvents()
  session.user = null
}

// --- 登入逾期處理 ---
function expired() {
  const wasLoggedIn = !!session.user
  const loginPath = loginFor(session.user?.role || (session.loginArea === 'staff' ? 'SUPPORT' : 'MEMBER'))
  clearLocal()
  if (wasLoggedIn) {
    window.dispatchEvent(new CustomEvent('auth-expired', { detail: { loginPath } }))
  }
}

// --- 核心請求包裝函式 ---
async function request(path, options = {}, responseType = 'json') {
  const method = options.method || 'GET'
  const headers = { ...options.headers }
  const requestToken = token

  if (requestToken) {
    headers.Authorization = `Bearer ${requestToken}`
  }

  let body = options.body
  if (body != null && !(body instanceof FormData)) {
    headers['Content-Type'] = 'application/json'
    body = JSON.stringify(body)
  }

  const r = await fetch('/api' + path, {
    ...options,
    method,
    headers,
    body,
    credentials: 'omit'
  })

  if (!r.ok) {
    const data = await r.json().catch(() => ({}))
    if (r.status === 401 && requestToken === token && !['/auth/login', '/auth/member/login', '/auth/staff/login'].includes(path)) {
      expired()
    }
    const error = Error(data.message || data.detail || (r.status === 401 ? '登入已失效或帳密錯誤，請重新登入' : `請求失敗（${r.status}）`))
    error.status = r.status
    throw error
  }

  if (requestToken && requestToken !== token) throw Error('登入狀態已變更，請重新載入')
  if (r.status === 204) return null

  // 處理 Blob 二進位檔案（如圖片）
  if (responseType === 'blob') {
    if (!requestToken || requestToken !== token) {
      throw Error('登入狀態已變更，請重新載入圖片')
    }
    if (!['image/jpeg', 'image/png'].includes(r.headers.get('Content-Type')?.split(';')[0])) {
      throw Error('無法讀取此圖片')
    }
    return r.blob()
  }

  const result = await r.json()
  if (requestToken && requestToken !== token) throw Error('登入狀態已變更，請重新載入')
  return result
}

// --- 對外公開的 API 介面 ---
export const api = (path, options = {}) => request(path, options)

// 透過私密 Fetch 取得 Blob，再轉為暫時性 Object URL 以夾帶 Bearer Header
export const apiImage = (path, options = {}) => request(path, options, 'blob')

// 同一分頁重整時還原 Token；身分必須由後端 /auth/me 驗證。
// expiresAt 是原登入的到期時間，重整不會延長登入期限。
function scheduleExpiry(expiresAt) {
  clearTimeout(expiryTimer)
  if (expiresAt !== null) expiryTimer = setTimeout(expired, Math.max(0, expiresAt - Date.now()))
}

export async function restore() {
  clearLocal(false)
  session.ready = false
  const run = authGeneration
  try {
    const saved = JSON.parse(sessionStorage.getItem(SESSION_KEY) || 'null')
    if (!saved) return
    if (typeof saved.accessToken !== 'string' || !saved.accessToken ||
        (saved.expiresAt !== null && (!Number.isFinite(saved.expiresAt) || saved.expiresAt <= Date.now()))) {
      clearLocal()
      return
    }
    token = saved.accessToken
    scheduleExpiry(saved.expiresAt)
    const user = await api('/auth/me')
    if (run !== authGeneration) return
    if (!['MEMBER', 'PUBLISHER', 'ADMIN', 'REVIEWER', 'SUPPORT'].includes(user.role)) {
      clearLocal()
      return
    }
    session.user = user
    session.loginArea = ['MEMBER', 'PUBLISHER'].includes(user.role) ? 'member' : 'staff'
    try { sessionStorage.setItem('support-login-area', session.loginArea) } catch {}
    connectEvents()
  } catch {
    if (run === authGeneration) clearLocal()
  } finally {
    if (run === authGeneration) session.ready = true
  }
}

// --- 登入方法 ---
export async function login(username, password, area = 'member') {
  if (!['member', 'staff'].includes(area)) throw Error('無效的登入入口')
  clearLocal()
  const run = authGeneration
  const data = await api(`/auth/${area}/login`, {
    method: 'POST',
    body: { username, password }
  })

  if (run !== authGeneration) throw Error('登入操作已取消')
  if (typeof data.accessToken !== 'string' || !data.accessToken || !Number.isFinite(data.expiresIn) || data.expiresIn < 0) {
    clearLocal()
    throw Error('伺服器回傳無效的登入資料')
  }
  token = data.accessToken
  const expiresAt = data.expiresIn === 0 ? null : Date.now() + data.expiresIn * 1000
  scheduleExpiry(expiresAt)

  try {
    const user = await api('/auth/me')
    if (run !== authGeneration) throw Error('登入操作已取消')
    const correctArea = area === 'member' ? ['MEMBER','PUBLISHER'].includes(user.role) : ['ADMIN', 'REVIEWER', 'SUPPORT'].includes(user.role)
    if (!correctArea) throw Error('此帳號不適用這個登入入口')
    session.user = user
    session.loginArea = area
    try { sessionStorage.setItem(SESSION_KEY, JSON.stringify({ accessToken: token, expiresAt })) } catch {}
    try { sessionStorage.setItem('support-login-area', area) } catch {}
    connectEvents()
  } catch (e) {
    if (run === authGeneration) clearLocal()
    throw e
  }
}

// --- 登出方法 ---
export async function logout() {
  const run = authGeneration
  try {
    await api('/auth/logout', { method: 'POST' })
  } finally {
    if (run === authGeneration) expired()
  }
}

// --- 停止 SSE 事件串流 ---
export function stopEvents() {
  generation++
  stream?.abort()
  stream = null
  session.online = false
}

// --- 建立 SSE 即時事件連線 ---
function connectEvents() {
  stopEvents()
  const run = generation
  stream = new AbortController()
  const signal = stream.signal

  const delay = () => new Promise(resolve => {
    const finish = () => {
      clearTimeout(timer)
      signal.removeEventListener('abort', finish)
      resolve()
    }
    const timer = setTimeout(finish, 2000)
    signal.addEventListener('abort', finish, { once: true })
  })

    ; (async () => {
      while (!signal.aborted && run === generation && token) {
        try {
          const r = await fetch('/api/events', {
            headers: {
              Authorization: `Bearer ${token}`,
              Accept: 'text/event-stream'
            },
            credentials: 'omit',
            signal
          })

          if (signal.aborted || run !== generation) return
          if (r.status === 401) {
            expired()
            return
          }
          if (!r.ok || !r.body) throw Error('SSE connection failed')

          session.online = true
          window.dispatchEvent(new Event('support-refresh'))

          const reader = r.body.getReader()
          const decoder = new TextDecoder()
          let pending = ''

          try {
            while (true) {
              const { done, value } = await reader.read()
              if (done) break

              pending += decoder.decode(value, { stream: true })
              let pos
              while ((pos = pending.indexOf('\n')) >= 0) {
                const line = pending.slice(0, pos).replace(/\r$/, '')
                pending = pending.slice(pos + 1)
                if (/^event:\s*refresh\s*$/.test(line)) {
                  window.dispatchEvent(new Event('support-refresh'))
                }
              }
            }
          } finally {
            await reader.cancel().catch(() => { })
            reader.releaseLock()
          }
        } catch (e) {
          if (signal.aborted) return
        }

        if (run !== generation) return
        session.online = false
        await delay()
      }
    })()
}

// --- 輔助格式化函式 ---
export const statusText = s => ({
  OPEN: '待處理',
  IN_PROGRESS: '處理中',
  CLOSED: '已結束'
}[s] || s)

export const timeText = t => t ? new Intl.DateTimeFormat('zh-TW', {
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit'
}).format(new Date(t)) : ''
