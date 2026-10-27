// 真實瀏覽器驗證樣式是否套用；API 使用模擬資料，不登入或修改實際資料庫。
const assert = require('node:assert/strict')
const { chromium } = require('playwright')
const staffUrl = process.env.STAFF_CSS_QA_URL || 'http://127.0.0.1:5174'
const memberUrl = process.env.MEMBER_CSS_QA_URL
let checks = 0
function check(value, description) { assert.ok(value, description); checks++ }
const grants = ['DASHBOARD_VIEW', 'STAFF_MANAGE', 'PERMISSION_MANAGE', 'TICKET_HANDLE', 'MEMBER_SUSPEND', 'GAME_REVIEW', 'PUBLISHER_REVIEW', 'ORDER_VIEW', 'COUPON_MANAGE']
async function run() {
  for (const role of (process.env.CSS_QA_ROLE ? [process.env.CSS_QA_ROLE] : ['GUEST', 'ADMIN', 'STAFF', 'REVIEWER', 'SUPPORT', ...(memberUrl ? ['MEMBER_GUEST'] : [])])) {
    const browser = await chromium.launch({ headless: true })
    try {
      const ctx = await browser.newContext({ viewport: { width: 1440, height: 1000 } })
      const authenticated = !['GUEST', 'MEMBER_GUEST'].includes(role)
      await ctx.addInitScript(({ authenticated }) => {
        if (location.protocol !== 'http:') return
        if (authenticated) {
          const exp = Math.floor(Date.now() / 1000) + 3600
          sessionStorage.setItem('support-auth-session-shared-jwt-v2', JSON.stringify({ accessToken: 'e30.' + btoa(JSON.stringify({ exp })) + '.css-test', expiresAt: exp * 1000 }))
        }
        const original = window.fetch
        window.fetch = (url, options) => String(url).includes('/api/events') ? Promise.resolve(new Response(new ReadableStream({ start(c) { c.enqueue(new TextEncoder().encode('event: heartbeat\ndata: {}\n\n')) } }), { headers: { 'Content-Type': 'text/event-stream' } })) : original(url, options)
        window.WebSocket = class { static OPEN = 1; readyState = 1; constructor() { setTimeout(() => this.onopen?.({}), 0) } send() {} close() { this.readyState = 3 } }
      }, { authenticated })
      await ctx.route('**/api/**', route => {
        const p = new URL(route.request().url()).pathname
        let data = []
        if (p === '/api/auth/me') data = { id: 5, role, username: 'style_test', displayName: '樣式測試人員', permissions: role === 'STAFF' ? ['ORDER_VIEW', 'TICKET_HANDLE'] : grants }
        else if (p === '/api/management/dashboard') data = { memberCount: 2, gameCount: 1, monthlyOrderCount: 1, monthlyRevenue: 599, revenueTrend: [{ month: '2026-10', amount: 599 }] }
        else if (p === '/api/notifications') data = { items: [], unread: 0 }
        else if (p === '/api/admin/workload') data = { waitingRooms: 1, myRooms: 1, waitingTickets: 0, myTickets: 1 }
        else if (p === '/api/admin/queue') data = { items: [], total: 0 }
        else if (p === '/api/admin/members' || p.startsWith('/api/admin/games')) data = { content: [], totalElements: 0 }
        else if (p.startsWith('/api/admin/support/')) data = { items: [], hasMore: false }
        else if (p === '/api/backoffice/coupons') data = { items: [], total: 0 }
        else if (p === '/api/backoffice/coupon-options') data = { publishers: [], tags: [] }
        else if (p === '/api/public/google-login') data = { enabled: false }
        else if (p === '/api/public/support-contact') data = { email: '', available: false }
        else if (p.includes('/cart/')) data = { items: [], totalPrice: 0 }
        return route.fulfill({ json: data })
      })
      const page = await ctx.newPage(), errors = [], failures = []
      page.on('pageerror', error => errors.push(error.message))
      page.on('response', response => { if (response.status() >= 400 && !response.url().includes('/api/')) failures.push(response.url()) })
      const go = path => page.goto((role === 'MEMBER_GUEST' ? memberUrl : staffUrl) + '/#' + path, { waitUntil: 'domcontentloaded' })
      if (role === 'GUEST') {
        await go('/staff/login')
        await page.locator('.staff-login-card').waitFor()
        const css = await page.locator('.staff-login-card').evaluate(el => ({ background: getComputedStyle(el).backgroundColor, radius: parseFloat(getComputedStyle(el).borderRadius) }))
        check(css.background !== 'rgba(0, 0, 0, 0)' && css.radius >= 8, '後台登入卡片應套用背景及圓角')
        check(await page.locator('body').evaluate(el => el.classList.contains('staff-workspace')), '後台主題狀態')
      } else if (role === 'MEMBER_GUEST') {
        await go('/member/login')
        await page.locator('.member-login-help').waitFor()
        const css = await page.locator('.member-login .login-card').evaluate(el => ({ background: getComputedStyle(el).backgroundColor, radius: parseFloat(getComputedStyle(el).borderRadius) }))
        const rgb = css.background.match(/\d+/g).slice(0, 3).map(Number)
        check(rgb.every(value => value < 100) && css.radius > 0, '會員登入應保留深色卡片樣式')
        check(await page.locator('body').evaluate(el => el.classList.contains('cart-portal') && !el.classList.contains('staff-workspace')), '會員與後台主題應分開')
      } else {
        const paths = { ADMIN: '/admin', STAFF: '/staff/home', REVIEWER: '/reviewer', SUPPORT: '/admin/support' }
        await go(paths[role])
        await page.locator('.management-sidebar').waitFor()
        const layout = await page.evaluate(() => {
          const header = document.querySelector('.site-header'), sidebar = document.querySelector('.management-sidebar'), main = document.querySelector('main.support-page')
          return { header: getComputedStyle(header).position, height: header.getBoundingClientRect().height, sidebar: getComputedStyle(sidebar).position, sidebarRight: sidebar.getBoundingClientRect().right, mainLeft: main.getBoundingClientRect().left }
        })
        check(layout.header === 'fixed' && layout.height >= 64, role + ' 頂欄固定')
        check(layout.sidebar === 'fixed' && layout.mainLeft >= layout.sidebarRight - 2, role + ' 側欄不覆蓋工作區')
        if (role === 'ADMIN') {
          await page.locator('.platform-stat-grid').waitFor()
          check(await page.locator('.platform-stat-grid').evaluate(el => getComputedStyle(el).display === 'grid'), '主頁統計卡片網格')
          await go('/admin/members'); await page.locator('.management-heading').waitFor()
          check(await page.locator('.management-heading').evaluate(el => getComputedStyle(el).display === 'flex'), '會員管理標題樣式')
          await page.reload({ waitUntil: 'domcontentloaded' }); await page.locator('.management-heading').waitFor()
          check(await page.locator('.management-heading').evaluate(el => getComputedStyle(el).display === 'flex'), '直接重整管理頁樣式')
          await go('/admin/support/faqs'); await page.getByRole('button', { name: '＋ 新增常見問題', exact: true }).click()
          check(await page.locator('.sx-content-editor').evaluate(el => parseFloat(getComputedStyle(el).borderRadius) >= 8), 'FAQ 編輯表單樣式')
          await page.setViewportSize({ width: 390, height: 844 })
          await page.getByRole('button', { name: '開啟管理選單', exact: true }).click()
          await page.waitForFunction(() => document.querySelector('.management-sidebar').getBoundingClientRect().left >= -1)
          check(await page.locator('.management-sidebar').getAttribute('class').then(value => value.includes('open')), '手機側欄可展開')
          await page.keyboard.press('Escape')
          await page.waitForFunction(() => document.querySelector('.management-sidebar').getBoundingClientRect().right <= 1)
          check(await page.locator('body').evaluate(el => el.style.overflow !== 'hidden'), '關閉手機側欄可恢復捲動')
        } else if (role === 'SUPPORT') {
          await page.locator('.work-stats').waitFor()
          check(await page.locator('.work-stats').evaluate(el => getComputedStyle(el).display === 'grid'), '客服統計卡片樣式')
        } else {
          await page.locator('.management-heading').waitFor()
          check(await page.locator('.management-heading').evaluate(el => getComputedStyle(el).display === 'flex'), role + ' 工作總覽標題樣式')
        }
      }
      check(errors.length === 0, role + ' 不應有頁面錯誤：' + errors.join('; '))
      check(failures.length === 0, role + ' 不應有靜態資源載入失敗：' + failures.join('; '))
      console.log(role + ' styles passed')
    } finally { await browser.close() }
  }
  console.log('PASS ' + checks + ' rendered CSS, responsive layout and resource checks (mock API)')
}
run().catch(error => { console.error(error); process.exit(1) })
