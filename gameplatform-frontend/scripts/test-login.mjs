import assert from 'node:assert/strict'
import { test } from 'node:test'

test('separate login endpoints, role redirects, logout and expiry return to correct entrance', async () => {
  globalThis.window = new EventTarget()
  const stored = new Map()
  globalThis.sessionStorage = { getItem: k => stored.get(k), setItem: (k,v) => stored.set(k,v), removeItem: k => stored.delete(k) }
  const requests = [], expired = []
  window.addEventListener('auth-expired', e => expired.push(e.detail.loginPath))
  let role = 'MEMBER', rejectRequest = false
  globalThis.fetch = async (url, options = {}) => {
    requests.push(url)
    if (url.endsWith('/events')) return new Promise((_,reject) => {
      options.signal.addEventListener('abort', () => reject(new DOMException('aborted','AbortError')), {once:true})
    })
    if (url.endsWith('/login')) return Response.json({accessToken:'test-token',expiresIn:900})
    if (url.endsWith('/me')) return Response.json({id:1,role,username:'same-account-name'})
    if (url.endsWith('/logout')) return new Response(null,{status:204})
    return rejectRequest ? Response.json({message:'expired'},{status:401}) : Response.json({ok:true})
  }
  const auth = await import('../src/api.js')
  try {
    await auth.login('same-account-name','pw','member')
    assert.equal(requests[0],'/api/auth/member/login')
    await auth.logout()
    assert.equal(expired.at(-1),'/member/login')
    role = 'REVIEWER'
    const before=requests.length
    await auth.login('same-account-name','pw','staff')
    assert.equal(requests[before],'/api/auth/staff/login')
    assert(requests.slice(before).includes('/api/events'))
    assert.equal(stored.get('support-login-area'),'staff')
    rejectRequest=true
    await assert.rejects(auth.api('/admin/workload'),/expired/)
    assert.equal(expired.at(-1),'/staff/login')
    assert.equal(auth.session.user,null)
    await assert.rejects(auth.login('x','pw','member'),/不適用/)
    assert.equal(auth.session.user,null)
    role='PUBLISHER';rejectRequest=false
    const publisherStart=requests.length
    await auth.login('publisher1','pw','member')
    assert.equal(auth.session.user.role,'PUBLISHER')
    assert(requests.slice(publisherStart).includes('/api/events'))
    await auth.logout()
    assert.equal(expired.at(-1),'/member/login')
    await assert.rejects(auth.login('publisher1','pw','staff'),/不適用/)
    assert.equal(stored.size,1) // Logout removes the saved Token; only the entrance hint remains.
  } finally { stored.delete('support-auth-session-v1'); await auth.restore() }
})
