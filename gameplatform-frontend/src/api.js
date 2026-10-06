import { reactive } from 'vue'
export const session = reactive({
  user : null, ready : false, online : false
})
let token = null, stream = null, expiryTimer = null, generation = 0
function clearLocal() {
  token = null;
  clearTimeout(expiryTimer);
  stopEvents();
  session.user = null
}
function expired() {
  const wasLoggedIn = !!session.user;
  clearLocal();
  if (wasLoggedIn) window.dispatchEvent(new Event('auth-expired'))
}
async function request(path, options = {}, responseType = 'json') {
  const method = options.method || 'GET', headers = {
    ...options.headers
  }, requestToken = token
  if (requestToken) headers.Authorization = `Bearer ${requestToken}`
  let body = options.body
  if (body != null && !(body instanceof FormData)) {
    headers['Content-Type'] = 'application/json';
    body = JSON.stringify(body)
  }
  const r = await fetch('/api' + path, {
    ...options, method, headers, body, credentials : 'omit'
  })
  if (!r.ok) {
    const data = await r.json().catch(() => ({}));
    if (r.status === 401 && requestToken === token && path !== '/auth/login') expired();
    throw Error(data.message || (r.status === 401 ? '登入已失效或帳密錯誤，請重新登入' : `請求失敗（${r.status}）`))
  }
  if (r.status === 204) return null
  if (responseType === 'blob') {
    if (!requestToken || requestToken !== token) throw Error('登入狀態已變更，請重新載入圖片')
    if (!['image/jpeg', 'image/png'].includes(r.headers.get('Content-Type')?.split(';')[0])) {
      throw Error('無法讀取此圖片')
    }
    return r.blob()
  }
  return r.json()
}
export const api = (path, options = {}) => request(path, options)
// A normal <img src="/api/..."> does not carry our Bearer header. Fetch privately, then use a temporary blob URL.
export const apiImage = (path, options = {}) => request(path, options, 'blob')
// Access token only in memory: reload/expiry requires a fresh login.
export async function restore() {
  clearLocal();
  session.ready = true
}
export async function login(username, password) {
  clearLocal()
  const data = await api('/auth/login', {
    method : 'POST', body : {
      username, password
    }
  })
  token = data.accessToken
  expiryTimer = setTimeout(expired, data.expiresIn * 1000)
  try {
    session.user = await api('/auth/me');
    connectEvents()
  } catch (e) {
    clearLocal();
    throw e
  }
}
export async function logout() {
  try {
    await api('/auth/logout', {
      method : 'POST'
    })
  } finally {
    expired()
  }
}
export function stopEvents() {
  generation++;
  stream?.abort();
  stream = null;
  session.online = false
}
function connectEvents() {
  stopEvents();
  const run = generation;
  stream = new AbortController();
  const signal = stream.signal
  const delay = () => new Promise(resolve => {
    const finish = () => {
      clearTimeout(timer);signal.removeEventListener('abort', finish);resolve()
    };const timer = setTimeout(finish, 2000);signal.addEventListener('abort', finish, {
      once : true
    })
  })
  ;
  (async() => {
    while (!signal.aborted && run === generation && token) {
      try {
        const r = await fetch('/api/events', {
          headers : {
            Authorization : `Bearer ${token}`, Accept : 'text/event-stream'
          }, credentials : 'omit', signal
        })
        if (r.status === 401) {
          expired();return
        }
        if (!r.ok || !r.body) throw Error('SSE connection failed')
        session.online = true;window.dispatchEvent(new Event('support-refresh'))
        const reader = r.body.getReader(), decoder = new TextDecoder();let pending = ''
        try {
          while (true) {
            const {
              done, value
            }
            = await reader.read();if (done) break
            pending += decoder.decode(value, {
              stream : true
            });let pos
            while ((pos = pending.indexOf('\n')) >= 0) {
              const line = pending.slice(0, pos).replace(/\r$/, '');pending = pending.slice(pos + 1)
              if (/^event:\s*refresh\s*$/.test(line)) window.dispatchEvent(new Event('support-refresh'))
            }
          }
        } finally {
          await reader.cancel().catch(() => {});reader.releaseLock()
        }
      } catch (e) {
        if (signal.aborted) return
      }
      if (run !== generation) return
      session.online = false;await delay()
    }
  })()
}
export const statusText = s => ({
  OPEN : '待處理', IN_PROGRESS : '處理中', CLOSED : '已結束'
}
[s] || s)
export const timeText = t => t ? new Intl.DateTimeFormat('zh-TW', {
  month : '2-digit', day : '2-digit', hour : '2-digit', minute : '2-digit'
}).format(new Date(t)) : ''
