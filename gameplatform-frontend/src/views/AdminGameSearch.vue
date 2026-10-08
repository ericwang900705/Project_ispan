<script setup>
import { computed, onMounted, onUnmounted, ref, watch, nextTick } from 'vue'
import { api } from '../api'
import '../styles/publisher.css'

const keyword = ref('')
const status = ref('')
const page = ref(0)
const size = ref(5)
const sort = ref('createdAt,desc')
const searched = ref(false)
const results = ref([])
const total = ref(0)
const loading = ref(false)
const error = ref('')
const forceTarget = ref(null)
const forceReason = ref('')
const forceBusy = ref(false)
const forceError = ref('')
const forceSuccess = ref('')
function prepareForce(game) {
  forceTarget.value = game
  forceReason.value = ''
  forceError.value = ''
  forceSuccess.value = ''
}
async function forceOffShelf() {
  if (forceBusy.value || !forceTarget.value) return
  forceBusy.value = true; forceError.value = ''
  try {
    await api('/management/games/' + forceTarget.value.game_id + '/force-off-shelf', { method: 'POST', body: { reason: forceReason.value.trim() } })
    forceSuccess.value = '已強制下架，原因已保存；發行商不能自行恢復或重新送審。'
    forceTarget.value = null
    await load()
  } catch (e) { forceError.value = e.message }
  finally { forceBusy.value = false }
}
const statusLabels = { DRAFT: '草稿', PENDING: '待審核', APPROVED: '審核通過', PUBLISHED: '已上架', REJECTED: '審核退回', OFF_SHELF: '已下架', COMING_SOON: '即將上架' }
let requestSeq = 0
let keywordTimer = null
let resetting = false

const queryString = computed(() => {
  const params = new URLSearchParams()
  if (keyword.value.trim()) params.set('keyword', keyword.value.trim())
  if (status.value) params.set('status', status.value)
  params.set('page', String(page.value))
  params.set('size', String(size.value))
  params.set('sort', sort.value)
  return params.toString()
})
const totalPages = computed(() => Math.max(1, Math.ceil(total.value / size.value)))

async function load() {
  const current = ++requestSeq
  loading.value = true
  error.value = ''
  searched.value = true
  try {
    const data = await api('/management/games?' + queryString.value)
    if (current !== requestSeq) return
    results.value = data.items || []
    total.value = data.total || 0
  } catch (e) {
    if (current !== requestSeq) return
    results.value = []
    total.value = 0
    error.value = e.message || '搜尋失敗，請稍後再試'
  } finally {
    if (current === requestSeq) loading.value = false
  }
}
// 關鍵字每次變更都自動查詢；延遲 300ms 避免連續打字觸發大量請求。
watch(keyword, () => {
  if (resetting) return
  clearTimeout(keywordTimer)
  ++requestSeq // 舊請求結果不再覆蓋最新條件
  loading.value = false
  page.value = 0
  keywordTimer = setTimeout(load, 300)
})

// 下拉選單或頁數一變更，立即使用「所有目前條件」重新查詢。
watch([status, size, sort, page], () => {
  if (resetting) return
  clearTimeout(keywordTimer)
  ++requestSeq
  if (status.value !== previousStatus || size.value !== previousSize || sort.value !== previousSort) {
    page.value = 0
  }
  previousStatus = status.value
  previousSize = size.value
  previousSort = sort.value
  load()
})
let previousStatus = status.value
let previousSize = size.value
let previousSort = sort.value


function reset() {
  clearTimeout(keywordTimer)
  ++requestSeq

  resetting = true

  keyword.value = ''
  status.value = ''
  page.value = 0
  size.value = 5
  sort.value = 'createdAt,desc'

  previousStatus = status.value
  previousSize = size.value
  previousSort = sort.value

  results.value = []
  total.value = 0
  searched.value = false
  loading.value = false
  error.value = ''

  nextTick(() => {
    resetting = false
  })
}

function goToPage(next) {
  if (next < 0 || next >= totalPages.value || loading.value) return
  page.value = next
}

onUnmounted(() => {
  clearTimeout(keywordTimer)
  ++requestSeq
})
</script>

<template>
  <section class="platform-dashboard admin-games">
    <div class="platform-heading">
      <div><span class="eyebrow">GAME MANAGEMENT</span>
        <h1>遊戲搜尋</h1>
        <p class="muted">依照名稱、狀態及排序條件搜尋平台遊戲。</p>
      </div>
    </div>
    <div class="admin-game-panel">
      <h2>即時搜尋條件</h2>
      <p class="muted">輸入關鍵字或切換選項後會自動搜尋，所有條件會同時套用。</p>
      <div class="admin-game-fields">
        <label class="wide"><span>遊戲名稱關鍵字</span><input v-model="keyword" maxlength="100"
            placeholder="例如：魔法、遊戲" /></label>
        <label class="wide"><span>遊戲狀態</span><select v-model="status">
            <option value="">全部狀態</option>
            <option value="DRAFT">草稿</option>
            <option value="PENDING">待審核</option>
            <option value="APPROVED">審核通過</option>
            <option value="PUBLISHED">已上架</option>
            <option value="REJECTED">審核退回</option>
            <option value="OFF_SHELF">已下架</option>
            <option value="COMING_SOON">即將上架</option>
          </select></label>
        <label><span>頁數（從 0 開始）</span><input v-model.number="page" type="number" min="0" step="1" required /></label>
        <label><span>每頁筆數</span><select v-model.number="size">
            <option :value="5">5 筆</option>
            <option :value="10">10 筆</option>
            <option :value="20">20 筆</option>
            <option :value="50">50 筆</option>
          </select></label>
        <label class="wide"><span>排序方式</span><select v-model="sort">
            <option value="createdAt,desc">建立順序：最新優先</option>
            <option value="createdAt,asc">建立順序：最舊優先</option>
            <option value="gameName,asc">遊戲名稱：A → Z</option>
            <option value="gameName,desc">遊戲名稱：Z → A</option>
          </select></label>
      </div>
      <div class="admin-game-actions"><button class="secondary" type="button" @click="reset">清除條件</button></div>
    </div>
    <p v-if="forceSuccess" role="status">{{ forceSuccess }}</p>
    <form v-if="forceTarget" class="admin-game-panel publisher-form" @submit.prevent="forceOffShelf">
      <h2>強制下架：{{ forceTarget.game_name }}</h2>
      <p class="muted">確認後立即停止販售並取消待審申請。發行商無法自行恢復或重送上架。</p>
      <label>強制下架原因（必填）<textarea v-model="forceReason" required maxlength="480" rows="4" :disabled="forceBusy"></textarea></label>
      <p v-if="forceError" class="error" role="alert">{{ forceError }}</p>
      <div class="admin-game-actions"><button class="primary" :disabled="forceBusy || !forceReason.trim()">{{ forceBusy ? '處理中…' : '確認強制下架' }}</button><button class="secondary" type="button" :disabled="forceBusy" @click="forceTarget = null">取消</button></div>
    </form>
    <section v-if="searched" class="admin-game-panel admin-game-results" aria-live="polite">
      <div class="admin-game-result-header">
        <h2>搜尋結果</h2><span>共 {{ total }} 筆</span>
      </div>
      <p v-if="error" class="error" role="alert">{{ error }}</p>
      <div v-if="loading" class="admin-game-empty">載入中…</div>
      <div v-else-if="!results.length && !error" class="admin-game-empty"><strong>{{ searched ? '目前沒有符合條件的遊戲' :
        '請先輸入條件並搜尋' }}</strong>
        <p>資料庫沒有符合條件的遊戲時，會顯示空結果。</p>
      </div>
      <div v-else-if="results.length" class="admin-game-table-wrap">
        <table class="admin-game-table">
          <thead>
            <tr>
              <th>編號</th>
              <th>遊戲名稱</th>
              <th>狀態</th>
              <th>價格</th>
              <th>發行日期</th>
              <th>管理操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="game in results" :key="game.game_id">
              <td>{{ game.game_id }}</td>
              <td>{{ game.game_name }}</td>
              <td>{{ statusLabels[game.status] || game.status }}<small v-if="game.off_shelf_source === 'ADMIN'" style="display:block">管理員強制下架</small></td>
              <td>NT$ {{ game.price ?? 0 }}</td>
              <td>{{ game.release_date ? String(game.release_date).slice(0, 10) : '—' }}</td><td><button class="secondary" :disabled="forceBusy || game.off_shelf_source === 'ADMIN'" @click="prepareForce(game)">強制下架</button><small v-if="game.off_shelf_source === 'ADMIN'" style="display:block;max-width:220px;overflow-wrap:anywhere">{{ game.off_shelf_reason }}</small></td>
            </tr>
          </tbody>
        </table>
      </div>
      <div class="admin-game-pagination"><button class="secondary" :disabled="page === 0 || loading"
          @click="goToPage(page - 1)">上一頁</button><span>第 {{ page + 1 }} / {{ totalPages }} 頁</span><button
          class="secondary" :disabled="(page + 1) >= totalPages || loading" @click="goToPage(page + 1)">下一頁</button>
      </div>
    </section>
  </section>
</template>
