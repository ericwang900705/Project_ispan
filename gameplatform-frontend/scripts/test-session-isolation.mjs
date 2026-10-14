import { test } from 'node:test'
import assert from 'node:assert/strict'
import { createPinia, setActivePinia } from 'pinia'
const deferred = () => {
  let resolve
  const promise = new Promise(r => { resolve = r })
  return { promise, resolve }
}
test('overlapping login, stale responses and account changes stay isolated', async () => {
  globalThis.window = new EventTarget()
  const stored = new Map()
  globalThis.sessionStorage = { getItem: k => stored.get(k), setItem: (k, v) => stored.set(k, v), removeItem: k => stored.delete(k) }
  const first = deferred(), slow = deferred()
  let hold = true
  globalThis.fetch = async (url, options = {}) => {
    if (url.endsWith('/events')) return new Promise((_, reject) => options.signal.addEventListener('abort', () => reject(Error('aborted')), { once: true }))
    if (url.endsWith('/login')) {
      const user = JSON.parse(options.body).username
      if (user === 'first' && hold) return first.promise
      return Response.json({ accessToken: user, expiresIn: 0 })
    }
    if (url.endsWith('/me')) return Response.json({ id: options.headers.Authorization === 'Bearer second' ? 2 : 1, role: 'MEMBER', username: options.headers.Authorization.slice(7) })
    if (url.endsWith('/slow')) return slow.promise
    if (url.endsWith('/cart/2')) {
      assert.equal(options.headers.Authorization, 'Bearer second')
      return Response.json({ items: [], coupon: null, totalPrice: 0 })
    }
    if (url.endsWith('/logout')) return new Response(null, { status: 204 })
    return Response.json({})
  }
  const auth = await import('../src/api.js')
  setActivePinia(createPinia())
  const { useCartStore } = await import('../src/cart/stores/cart.js')
  const cart = useCartStore()
  try {
    const oldRejected = assert.rejects(auth.login('first', 'password'), /取消/)
    await auth.login('second', 'password')
    first.resolve(Response.json({ accessToken: 'first', expiresIn: 0 }))
    await oldRejected
    assert.equal(auth.session.user.username, 'second')
    await cart.fetchCart()
    cart.items = [{ cartItemId: 123 }]
    cart.library = [{ libraryId: 456 }]
    const oldRequestRejected = assert.rejects(auth.api('/slow'), /登入狀態已變更/)
    hold = false
    await auth.login('first', 'password')
    assert.deepEqual(cart.items, [])
    assert.deepEqual(cart.library, [])
    slow.resolve(Response.json({ secret: 'second account' }))
    await oldRequestRejected
    assert.equal(auth.session.user.username, 'first')
  } finally { await auth.logout() }
})
