import assert from 'node:assert/strict'
import { readFileSync } from 'node:fs'
import { test } from 'node:test'
import { ref, computed } from 'vue'

// Execute the actual Admin setup functions with network/router/lifecycle boundaries stubbed.
function setup(api) {
  const source = readFileSync(new URL('../src/views/Admin.vue', import.meta.url), 'utf8')
    .split('<script setup>')[1].split('</script>')[0].replace(/^import .*$/gm, '')
  return new Function('ref', 'computed', 'watch', 'onMounted', 'onUnmounted', 'useRouter', 'useRoute', 'api', 'session',
    source + '\nreturn { selectedGames,gameIds,games,gameKeyword,gameDropdownOpen,selectGame,removeGame,onGameInput,openGameDropdown,closeGameDropdown,loadGames,resetGameState,transfer,transferRoomId,room,categories,categoryId,subject,summary }')(
      ref, computed, () => {}, () => {}, () => {}, () => ({ push() {} }), () => ({ query: {} }), api,
      { user: { id: 1, role: 'ADMIN' } })
}
test('multi-selection survives searches, excludes duplicates, removes chips and submits every ID', async () => {
  const requests = []
  const s = setup(async (path, options) => { requests.push({path,options}); return path.startsWith('/games') ? [{id:99,name:'AION2'}] : {id:10} })
  try {
    s.selectGame({id:1,name:'星際旅人'})
    assert.equal(s.gameKeyword.value, '')
    s.gameKeyword.value = 'AION'
    s.onGameInput()
    await s.loadGames()
    assert.equal(requests.at(-1).path, '/games?q=AION')
    assert.deepEqual(s.gameIds.value, [1])
    s.selectGame(s.games.value[0])
    s.selectGame({id:1,name:'星際旅人'})
    assert.deepEqual(s.gameIds.value, [1,99])
    s.gameDropdownOpen.value = false
    s.openGameDropdown()
    assert.equal(s.gameDropdownOpen.value, true)
    s.room.value = {id:1}
    s.transferRoomId.value = 1
    s.categories.value = [{id:1,name:'遊戲'}]
    s.categoryId.value = 1
    s.subject.value = '兩款遊戲無法啟動'
    s.summary.value = '客服摘要'
    await s.transfer()
    const body = requests.find(r => r.options?.method === 'POST').options.body
    assert.deepEqual(body.gameIds,[1,99])
    assert.equal(body.gameId,undefined)
    s.removeGame(1)
    assert.deepEqual(s.gameIds.value,[99])
    s.resetGameState()
    assert.deepEqual(s.gameIds.value,[])
  } finally { s.resetGameState() }
})
test('out-of-order search responses cannot overwrite newer results', async () => {
  const pending = []
  const s = setup(() => new Promise(resolve => pending.push(resolve)))
  try {
    s.gameKeyword.value = 'old'; const old = s.loadGames()
    s.gameKeyword.value = 'new'; const current = s.loadGames()
    pending[1]([{id:99,name:'new'}]); await current
    pending[0]([{id:1,name:'old'}]); await old
    assert.equal(s.games.value[0].id,99)
  } finally { s.resetGameState() }
})
