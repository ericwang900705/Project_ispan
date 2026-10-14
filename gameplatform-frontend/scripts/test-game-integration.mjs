import test from 'node:test'
import assert from 'node:assert/strict'
import fs from 'node:fs'
import vm from 'node:vm'

// Node-only API-contract tests, runnable before the Vue dependencies are installed.
// The Pinia setup store is evaluated with lightweight reactive substitutes.
const source = fs.readFileSync(new URL('../src/cart/stores/games.js', import.meta.url), 'utf8')
  .replace(/^import .+$/gm, '')
  .replace(/export (const|function)/g, '$1')

function loadStore(get) {
  const refs = () => {
    const ref = value => ({ value })
    const computed = getter => ({ get value() { return getter() } })
    const defineStore = (_id, setup) => setup
    const axios = { get }
    return vm.runInNewContext(`${source}\nuseGamesStore()`, { ref, computed, defineStore, axios })
  }
  return refs()
}

test('讀取公開列表會取得第二頁，不漏掉第 101 筆', async () => {
  const calls = []
  const get = async (_url, { params } = {}) => {
    calls.push(params.page)
    return { data: { content: params.page === 0 ? [{ gameId: 1 }] : [{ gameId: 101 }], last: params.page === 1, totalPages: 2 } }
  }
  const store = loadStore(get)
  await store.fetchGames()
  assert.equal(store.games.value.length, 2)
  assert.deepEqual(calls, [0, 1])
  await store.fetchGames()
  assert.equal(calls.length, 2, '重複進入頁面不應再抓一次清單')
})

test('同一時間重複進入商城只送出一組請求', async () => {
  let calls = 0
  const store = loadStore(async () => { calls++; await new Promise(resolve => setTimeout(resolve, 8)); return { data: { content: [], last: true } } })
  await Promise.all([store.fetchGames(), store.fetchGames()])
  assert.equal(calls, 1)
})

test('商城搜尋與遊戲詳情用 GitHub 路由', async () => {
  const urls = []
  const store = loadStore(async (url, options) => {
    urls.push([url, options?.params?.keyword])
    if (url.endsWith('/search')) return { data: [{ gameId: 8, gameName: '冒險' }] }
    return { data: { gameId: 8, gameName: '冒險' } }
  })
  const matches = await store.searchGames('  冒險  ')
  assert.equal(matches.length, 1)
  const detail = await store.fetchGame(8)
  assert.equal(detail.gameName, '冒險')
  assert.deepEqual(urls, [['/api/public/games/search', '冒險'], ['/api/public/games/8', undefined]])
  await assert.rejects(() => store.fetchGame('../admin'), /遊戲編號不正確/)
})

test('後端失敗時不顯示假資料', async () => {
  const store = loadStore(async () => { throw new Error('connection refused') })
  await assert.rejects(store.fetchGames())
  assert.equal(store.games.value.length, 0)
  assert.match(store.error.value, /connection refused/)
})

const root = new URL('../../', import.meta.url)
const file = path => fs.readFileSync(new URL(path, root), 'utf8')
test('發售排程保留 ZIP 審核與可下載版本限制', () => {
  const app = file('gameplatform-backend/src/main/java/gameplatform/GameplatformApplication.java')
  const repo = file('gameplatform-backend/src/main/java/gameplatform/game/repository/GameRepository.java')
  const service = file('gameplatform-backend/src/main/java/gameplatform/game/service/GameService.java')
  const publisher = file('gameplatform-backend/src/main/java/gameplatform/support/service/PublisherService.java')
  assert.match(app, /@EnableScheduling/)
  assert.match(service, /@Scheduled\(initialDelay\s*=\s*5000,\s*fixedDelay\s*=\s*60000\)/)
  for (const condition of [/status = 'COMING_SOON'/, /release_date <= CURRENT_TIMESTAMP/, /pending_review_type IS NULL/, /b\.status = 'ACTIVE'/]) assert.match(repo, condition)
  assert.match(publisher, /state\s*=\s*"COMING_SOON"/)
  assert.match(publisher, /state\s*=\s*"PUBLISHED"/)
})

test('客服使用的 member_id/game_id 與商城同一份資料', () => {
  const schema = file('gameplatform-backend/src/test/resources/demo-schema.sql')
  const support = file('gameplatform-backend/src/main/java/gameplatform/support/service/SupportService.java')
  assert.match(schema, /CREATE TABLE games\s*\(/)
  assert.match(schema, /CREATE TABLE customer_service_ticket_games\s*\(/)
  assert.match(schema, /REFERENCES games\(game_id\)/)
  assert.match(schema, /REFERENCES members\(member_id\)/)
  assert.match(support, /customer_service_ticket_games/)
})
