import { reactive } from 'vue'
import { readJsonResponse } from './response-body.js'
import { loginFor } from './permissions.js'
export const frontendArea = ['staff', 'member'].includes(import.meta.env.MODE) ? import.meta.env.MODE : 'combined'

// --- 全域會話狀態 ---
export const session = reactive({
  user: null,
  ready: false,
  online: false,
  loginArea: frontendArea
})

const SESSION_KEY = 'support-auth-session-shared-jwt-v2'
let authGeneration = 0
let token = null
let stream = null
let expiryTimer = null
let generation = 0

// 獨立遊戲分頁共用登出通知；不在頻道或網址傳送 JWT。
const memberLogoutChannel = typeof window !== 'undefined' && typeof BroadcastChannel !== 'undefined'
  ? new BroadcastChannel('hero-member-logout-v1') : null
if (memberLogoutChannel) memberLogoutChannel.onmessage = event => {
  if (event.data?.type === 'member-logout' && ['MEMBER', 'PUBLISHER'].includes(session.user?.role)
      && event.data.memberId === session.user.id) expired()
}

/** 只複製目前會員憑證至自己剛開的空白分頁，沿用原期限並由新分頁重新驗證身分。 */
export function prepareMemberGameWindow(popup) {
  if (!token || !['MEMBER', 'PUBLISHER'].includes(session.user?.role)) throw Error('請先登入會員再開啟遊戲')
  const expiresAt = expiresAtOf(token)
  popup.sessionStorage.setItem(SESSION_KEY, JSON.stringify({ accessToken: token, expiresAt }))
  popup.opener = null
}

// --- 清除本地驗證與狀態 ---
function clearLocal(removeStored = true) {
  authGeneration++
  if (removeStored) { try { sessionStorage.removeItem(SESSION_KEY) } catch { } }
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
  const headers = { ...options.headers, 'ngrok-skip-browser-warning': '1' }
  const requestToken = token
  const requestGeneration = authGeneration

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
    if (r.status === 401 && requestGeneration === authGeneration && requestToken === token && !['/auth/login', '/admin/auth/login'].includes(path)) {
      expired()
    }
    const error = Error(data.message || data.detail || (r.status === 401 ? '登入已失效或帳密錯誤，請重新登入' : `請求失敗（${r.status}）`))
    error.status = r.status
    throw error
  }

  if (requestGeneration !== authGeneration || (requestToken && requestToken !== token)) throw Error('登入狀態已變更，請重新載入')
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

  if (responseType === 'file') {
    const result = await r.blob()
    if (requestGeneration !== authGeneration || requestToken !== token) throw Error('登入狀態已變更，請重新下載')
    return result
  }
  if (responseType === 'text') return r.text()
  const result = await readJsonResponse(r)
  if (requestGeneration !== authGeneration || (requestToken && requestToken !== token)) throw Error('登入狀態已變更，請重新載入')
  return result
}

// --- 對外公開的 API 介面 ---
export const api = (path, options = {}) => request(path, options)

// 透過私密 Fetch 取得 Blob，再轉為暫時性 Object URL 以夾帶 Bearer Header
export const apiImage = (path, options = {}) => request(path, options, 'blob')
export const apiFile = path => request(path, {}, 'file')

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
    const expiresAt = expiresAtOf(token)
    scheduleExpiry(expiresAt)
    const user = await api('/auth/me')
    if (run !== authGeneration) return
    if (!(frontendArea === 'member' ? ['MEMBER', 'PUBLISHER'] : frontendArea === 'staff' ? ['ADMIN', 'STAFF', 'SUPPORT', 'REVIEWER'] : ['MEMBER', 'PUBLISHER', 'ADMIN', 'STAFF', 'SUPPORT', 'REVIEWER']).includes(user.role)) {
      clearLocal()
      return
    }
    session.user = user
    session.loginArea = ['MEMBER', 'PUBLISHER'].includes(user.role) ? 'member' : 'staff'

    connectEvents()
  } catch {
    if (run === authGeneration) clearLocal()
  } finally {
    if (run === authGeneration) session.ready = true
  }
}

// Refresh live grants before routing to a staff feature.
export async function refreshUser() {
  if (session.user) session.user = await api('/auth/me')
}

// --- 登入方法 ---
function expiresAtOf(value) {
  if (typeof value !== 'string' || value.split('.').length !== 3) throw Error('登入回傳的 JWT 無效')
  try {
    const part = value.split('.')[1].replace(/-/g, '+').replace(/_/g, '/')
    const payload = JSON.parse(atob(part))
    if (!Number.isFinite(payload.exp) || payload.exp * 1000 <= Date.now()) throw Error()
    return payload.exp * 1000
  } catch { throw Error('登入回傳的 JWT 無效或已過期') }
}

export async function login(username, password, area = frontendArea) {
  if (frontendArea !== 'combined' && area !== frontendArea) throw Error('請使用對應的登入入口')
  clearLocal()
  const run = authGeneration
  try {
    const data = await request(area === 'member' ? '/auth/login' : '/admin/auth/login',
      {
        method: 'POST',
        body: area === 'member' ? { username, password } : { adminAccount: username, password }
      },
      area === 'member' ? 'text' : 'json')
    if (run !== authGeneration) throw Error('登入操作已取消')
    token = area === 'member' ? data : data.token
    await finishLogin(area, run)
  } catch (error) {
    if (run === authGeneration) clearLocal()
    throw error
  }
}

export async function loginWithGoogle(idToken, nonce) {
  if (frontendArea === 'staff') throw Error('請使用前臺會員登入入口')
  clearLocal();
  const run = authGeneration
  try {
    const value = await request('/auth/google-login', { method: 'POST', body: { idToken, nonce } }, 'text');
    if (run !== authGeneration) throw Error('登入操作已取消');
    token = value;
    await finishLogin('member', run)
  } catch (e) {
    if (run === authGeneration) clearLocal();
    throw e
  }
}
async function finishLogin(area, run) {
  const expiresAt = expiresAtOf(token)
  const user = await api('/auth/me')

  if (run !== authGeneration) throw Error('登入操作已取消')
  const correctArea = area === 'member' ? ['MEMBER', 'PUBLISHER'].includes(user.role) : ['ADMIN', 'STAFF', 'SUPPORT', 'REVIEWER'].includes(user.role)
  if (!correctArea) throw Error('此帳號不適用這個登入入口')
  session.user = user
  session.loginArea = area
  try {
    sessionStorage.setItem(SESSION_KEY, JSON.stringify({
      accessToken: token, expiresAt
    }))
  } catch { }
  scheduleExpiry(expiresAt)
  connectEvents()
}

// 與組員的無狀態 JWT 行為一致：清除本機憑證與 SSE，不另外維護客服撤銷清單。
export async function logout() {
  if (['MEMBER', 'PUBLISHER'].includes(session.user?.role))
    memberLogoutChannel?.postMessage({ type: 'member-logout', memberId: session.user.id })
  expired()
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
              'ngrok-skip-browser-warning': '1',
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
          if (!r.ok || !r.body || !r.headers.get('Content-Type')?.toLowerCase().includes('text/event-stream')) throw Error('SSE connection failed')

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
  hour: '2-digit', hourCycle: 'h23',
  minute: '2-digit'
}).format(new Date(t)) : ''

export const accessToken = () => token

// WebSocket JWT is sent in the first frame, never in a URL or browser storage.
export function connectFriendEvents(onEvent, onStatus = () => { }) {
  const ownerToken = token
  let socket, retry, heartbeat, stopped = false
  const current = () => !stopped && !!ownerToken && token === ownerToken
  function open() {
    if (!current()) return
    socket = new WebSocket(`${location.protocol === 'https:' ? 'wss:' : 'ws:'}//${location.host}/ws/friends`)
    socket.onopen = () => {
      if (current()) socket.send(JSON.stringify({
        type: 'auth', token: ownerToken
      }));
      else socket.close()
    }
    socket.onmessage = event => {
      if (!current()) return
      try {
        const data = JSON.parse(event.data)
        if (data.type === 'ready') { onStatus(true); onEvent(data) }
        else if (['friends', 'rooms', 'message'].includes(data.type)) onEvent(data)
      } catch { }
    }
    socket.onclose = () => {
      clearInterval(heartbeat);
      onStatus(false);
      if (current()) retry = setTimeout(open, 5000)
    }
    socket.onerror = () => socket.close()
    heartbeat = setInterval(() => {
      if (!current()) { socket.close(); return }
      if (socket.readyState === WebSocket.OPEN) socket.send(JSON.stringify({
        type: 'ping'
      }))
    }, 25000)
  }
  open()
  return () => {
    stopped = true;
    clearTimeout(retry);
    clearInterval(heartbeat);
    socket?.close()
  }
}
