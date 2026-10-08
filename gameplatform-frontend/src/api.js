import { reactive } from 'vue'

export const session = reactive({ user: null, ready: true, online: false })
export const integration = reactive({ connected: { support: false, gameReview: false, commentReview: false, publisher: false, adminGames: false } })
let transport = null, generation = 0, cleanup = null, timer = null
export class NotConnectedError extends Error {
  constructor(message = '尚未串接：登入身分或資料 API') { super(message); this.name = 'NotConnectedError' }
}
export function stopEvents() { cleanup?.(); cleanup = null; clearInterval(timer); timer = null; session.online = false }
// Call again when the host account changes or signs out. Never store passwords/tokens here.
export function configureAkiModule({ user = null, request = null, connected = {}, subscribe = null } = {}) {
  stopEvents(); generation++; session.user = user ? { ...user } : null; transport = request
  for (const key of Object.keys(integration.connected)) integration.connected[key] = connected[key] === true
  if (user && request && integration.connected.support && ['MEMBER','ADMIN','SUPPORT'].includes(user.role)) {
    const refresh = () => { if (typeof window !== 'undefined') window.dispatchEvent(new Event('support-refresh')) }
    if (subscribe) { cleanup = subscribe(refresh); session.online = true }
    else if (typeof window !== 'undefined') timer = setInterval(() => { if (document.visibilityState === 'visible') refresh() },10000)
  }
}
export function connectedFor(feature) { return !!session.user && typeof transport === 'function' && integration.connected[feature] === true }
function normalize(path) {
  const value = path.replace(/^\/aki(?=\/)/, '')
  if (value.startsWith('/support') || value.startsWith('/publisher') || value.startsWith('/review')) return value
  if (value.startsWith('/management/games')) return value.replace('/management/games','/review/admin-games')
  return '/support' + value
}
function featureFor(path) {
  if (path.startsWith('/review/admin-games')) return 'adminGames'
  if (path.startsWith('/publisher')) return 'publisher'
  if (path.startsWith('/review/comments')) return 'commentReview'
  if (path.startsWith('/review')) return 'gameReview'
  return 'support'
}
async function request(path, options = {}, responseType = 'json') {
  const normalized = normalize(path), run = generation
  if (normalized === '/review/status') return { gamesConnected: connectedFor('gameReview'), commentsConnected: connectedFor('commentReview') }
  if (!connectedFor(featureFor(normalized))) throw new NotConnectedError()
  const result = await transport(normalized, options, responseType)
  if (run !== generation) throw Error('主系統身分已變更，請重新載入')
  if (responseType === 'blob' && (!(result instanceof Blob) || !['image/jpeg','image/png'].includes(result.type))) throw Error('無法讀取此圖片')
  return result
}
export const api = (path, options = {}) => request(path,options)
export const apiImage = (path, options = {}) => request(path,options,'blob')
// Optional helper: the host supplies verified auth headers / CSRF headers on each call.
export function createAkiFetchAdapter({ baseUrl = '/api', getHeaders = () => ({}), credentials = 'same-origin', onUnauthorized = () => {} } = {}) {
  return async (path, options = {}, responseType = 'json') => {
    const headers = new Headers(await getHeaders())
    new Headers(options.headers || {}).forEach((value,key) => headers.set(key,value))
    let body = options.body
    if (body != null && !(body instanceof FormData)) { headers.set('Content-Type','application/json'); body = JSON.stringify(body) }
    const response = await fetch(baseUrl.replace(/\/$/,'') + path,{ ...options, headers, body, credentials })
    if (!response.ok) {
      const data = await response.json().catch(() => ({}))
      if (response.status === 401) onUnauthorized()
      if ([501,503].includes(response.status)) throw new NotConnectedError(data.message || '尚未串接：資料服務')
      throw Error(data.message || `請求失敗（${response.status}）`)
    }
    return response.status === 204 ? null : responseType === 'blob' ? response.blob() : response.json()
  }
}
export const statusText = s => ({ OPEN:'待處理',IN_PROGRESS:'處理中',CLOSED:'已結束' }[s] || s)
export const timeText = t => t ? new Intl.DateTimeFormat('zh-TW',{month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit'}).format(new Date(t)) : ''
