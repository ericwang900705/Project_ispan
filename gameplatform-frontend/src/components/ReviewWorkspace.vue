<script setup>
import { ref, computed, onMounted } from 'vue'
import { api } from '../api'
import UiIcon from './UiIcon.vue'
import '../styles/review-workspace.css'
const props = defineProps({ kind: { type: String, required: true } })
const game = computed(() => props.kind === 'games')
const endpoint = computed(() => '/aki/review/' + props.kind)
const states = computed(() => game.value ? ['PENDING','APPROVED','REJECTED'] : ['PENDING','APPROVED','REMOVED'])
const label = s => ({PENDING:'待審核',APPROVED:game.value ? '已通過' : '保留顯示',REJECTED:'已退回',REMOVED:'已隱藏',ALL:'全部'}[s] || s)
const sourceReady = ref(false)
const status = ref('PENDING'), search = ref(''), page = ref(0), total = ref(0), items = ref([])
const counts = ref({}), detail = ref(null), loading = ref(false), selecting = ref(false), busy = ref(false)
const error = ref(''), detailError = ref(''), success = ref(''), comment = ref(''), confirmation = ref('')
let listRun = 0, detailRun = 0
const date = value => value ? new Date(value).toLocaleString('zh-TW', {hour12:false}) : '—'
const price = value => new Intl.NumberFormat('zh-TW',{style:'currency',currency:'TWD',maximumFractionDigits:0}).format(value)
async function load() {
  if (!sourceReady.value) return
  const run = ++listRun; loading.value = true; error.value = ''
  try {
    const result = await api(endpoint.value + '?status=' + status.value + '&q=' + encodeURIComponent(search.value.trim()) + '&page=' + page.value)
    if (run !== listRun) return
    items.value = result.items; total.value = result.total
    if (!result.items.length && page.value > 0) { page.value--; return load() }
  } catch(e) { if(run === listRun) error.value = e.message }
  finally { if(run === listRun) loading.value = false }
}
async function statistics() {
  if (!sourceReady.value) return
  try { const data = await Promise.all(states.value.map(s => api(endpoint.value+'?status='+s))); counts.value = Object.fromEntries(states.value.map((s,i) => [s,data[i].total])) }
  catch { /* Main list shows API errors; statistics remain unavailable. */ }
}
function resetDetail() { detailRun++; detail.value = null; selecting.value = false; detailError.value = ''; confirmation.value = ''; comment.value = '' }
async function filter(value = status.value) { if(busy.value) return; status.value = value; page.value = 0; resetDetail(); await load() }
async function select(item) {
  if(busy.value) return
  const run = ++detailRun; selecting.value = true; detail.value = null; detailError.value = ''; success.value = ''; confirmation.value = ''; comment.value = ''
  try { const data = await api(endpoint.value + '/' + item.review_id); if(run === detailRun) detail.value = data }
  catch(e) { if(run === detailRun) detailError.value = e.message }
  finally { if(run === detailRun) selecting.value = false }
}
function prepare(decision) {
  if(!comment.value.trim()) { detailError.value = '請先填寫處理說明，再選擇審核結果。'; return }
  detailError.value = ''; confirmation.value = decision
}
async function decide() {
  if(busy.value || !confirmation.value || !detail.value) return
  const id = detail.value.item.review_id, decision = confirmation.value
  busy.value = true; detailError.value = ''; success.value = ''
  try {
    detail.value = await api(endpoint.value + '/' + id, {method:'PUT',body:{decision,comment:comment.value.trim(), ...(game.value ? {requestKey:detail.value.item.request_key} : {})}})
    confirmation.value = ''; comment.value = ''; success.value = '已完成審核，結果與處理說明已保存。'
    await Promise.all([load(),statistics()])
  } catch(e) { detailError.value = e.message; confirmation.value = '' }
  finally { busy.value = false }
}
async function paginate(step) { if(busy.value || loading.value) return; page.value += step; resetDetail(); await load() }
onMounted(async () => {
  try {
    const state = await api('/aki/review/status')
    sourceReady.value = game.value ? state.gamesConnected : state.commentsConnected
    if (sourceReady.value) await Promise.all([load(), statistics()])
  } catch(e) { error.value = e.message }
})
</script>
<template>
  <section class="review-workspace">
    <header class="review-title-row"><div><span class="eyebrow">REVIEW WORKSPACE</span><h1>{{game ? '遊戲上架審核' : '評論審核'}}</h1><p class="muted">{{game ? '檢查遊戲資訊與版本資料，決定是否通過上架申請。' : '檢視會員評論與檢舉原因，保留合適內容或隱藏違規評論。'}}</p></div><span class="review-title-icon"><UiIcon :name="game ? 'game' : 'chat'" /></span></header>
    <p v-if="!sourceReady" class="review-source-note" role="status">尚未串接：{{game ? '遊戲送審' : '評論'}}資料來源。</p>
    <div class="review-stat-grid"><button v-for="s in states" :key="s" class="review-stat" :class="{active:status === s}" :disabled="busy" @click="filter(s)"><span>{{label(s)}}</span><strong>{{counts[s] ?? '—'}}</strong><small>查看項目 →</small></button></div>
    <div class="review-toolbar"><div class="review-tabs" aria-label="審核狀態"><button v-for="s in [...states,'ALL']" :key="s" :class="{active:status === s}" :aria-pressed="status === s" :disabled="busy" @click="filter(s)">{{label(s)}}</button></div><form class="review-search" @submit.prevent="filter()"><input v-model="search" maxlength="100" :disabled="busy || !sourceReady" :placeholder="game ? '搜尋遊戲或開發者' : '搜尋遊戲或會員'" aria-label="搜尋審核項目"><button class="primary" :disabled="busy || loading || !sourceReady">搜尋</button></form></div>
    <p v-if="error" class="error" role="alert">{{error}} <button @click="load();statistics()">重新載入</button></p><p v-if="success" class="review-success" role="status">{{success}}</p>
    <div class="review-columns">
      <section class="review-list-card" aria-label="審核清單"><div class="review-card-heading"><h2>{{label(status)}}清單</h2><span>{{sourceReady ? total + ' 筆' : '尚未串接'}}</span></div><p v-if="loading" class="review-empty" role="status">載入中…</p><p v-else-if="!items.length" class="review-empty">{{sourceReady ? '目前沒有符合條件的項目。' : '尚未串接'}}</p><div v-else class="review-list"><button v-for="item in items" :key="item.review_id" class="review-list-item" :class="{selected: detail?.item.review_id === item.review_id}" :disabled="busy" @click="select(item)"><div class="review-item-top"><strong>{{item.game_name}}</strong><span class="review-status" :class="item.status.toLowerCase()">{{label(item.status)}}</span></div><p>{{item.submitter_name}}<template v-if="game"> · {{ item.review_type === 'OFF_SHELF' ? '下架紀錄' : '上架申請' }}</template><template v-if="!game"> · {{item.rating}} / 5 星</template></p><p v-if="!game" class="review-preview">{{item.content}}</p><small>{{date(item.submitted_at)}} · #{{item.review_id}}</small></button></div><div class="review-pagination"><button :disabled="page === 0 || busy || loading" @click="paginate(-1)">上一頁</button><span>{{sourceReady ? (page + 1) + ' / ' + Math.max(1,Math.ceil(total / 20)) : '—'}}</span><button :disabled="(page + 1) * 20 >= total || busy || loading" @click="paginate(1)">下一頁</button></div></section>
      <section class="review-detail-card" aria-label="審核詳情"><p v-if="selecting" class="review-empty">載入詳情中…</p><template v-else-if="detail"><div class="review-card-heading"><h2>{{detail.item.game_name}}</h2><span class="review-status" :class="detail.item.status.toLowerCase()">{{label(detail.item.status)}}</span></div><div class="review-detail-body"><dl class="review-facts"><div><dt>{{game ? '開發者' : '評論會員'}}</dt><dd>{{detail.item.submitter_name}}</dd></div><div><dt>送審時間</dt><dd>{{date(detail.item.submitted_at)}}</dd></div><template v-if="game"><div><dt>售價</dt><dd>{{price(detail.item.price)}}</dd></div><div><dt>預定上架時間</dt><dd>{{date(detail.item.release_at)}}</dd></div><div class="wide"><dt>版本資料</dt><dd>{{detail.item.build_reference}}</dd></div></template><div v-else><dt>評分</dt><dd>{{detail.item.rating}} / 5 星</dd></div></dl><template v-if="game"><p><strong>申請類型：</strong>{{ detail.item.review_type === 'OFF_SHELF' ? '下架紀錄' : '上架申請' }}</p><p v-if="detail.item.request_reason" class="review-content">申請說明：{{ detail.item.request_reason }}</p><p>標籤：{{ detail.item.tags.map(x => x.tag_name).join('、') || '尚未設定' }}</p><p v-for="build in detail.item.builds" :key="build.file_url"><a :href="build.file_url" target="_blank" rel="noopener noreferrer">版本 {{ build.version }} · {{ build.status }} ↗</a></p><p v-for="media in detail.item.media" :key="media.media_url"><a :href="media.media_url" target="_blank" rel="noopener noreferrer">{{ media.media_type === 'IMAGE' ? '查看圖片' : '查看影片' }} ↗</a></p></template><h3>{{game ? '遊戲介紹' : '評論內容'}}</h3><p class="review-content">{{game ? detail.item.description : detail.item.content}}</p><template v-if="!game && detail.item.report_reason"><h3>檢舉原因</h3><p class="review-report-reason">{{detail.item.report_reason}}</p></template><div v-if="detail.item.status === 'PENDING'" class="review-decision"><h3>審核處理</h3><label for="review-comment">處理說明（必填）</label><textarea id="review-comment" v-model="comment" maxlength="500" rows="3" :disabled="busy" placeholder="填寫通過、退回或隱藏的原因"></textarea><small>{{comment.length}} / 500</small><div v-if="!confirmation" class="review-actions"><button class="primary" :disabled="busy" @click="prepare('APPROVED')">{{game ? '通過上架審核' : '保留評論'}}</button><button class="review-danger" :disabled="busy" @click="prepare(game ? 'REJECTED' : 'REMOVED')">{{game ? '退回申請' : '隱藏評論'}}</button></div><div v-else class="review-confirm" role="alert"><p>確認將此項目設為「{{label(confirmation)}}」？</p><div class="review-actions"><button class="primary" :disabled="busy" @click="decide">{{busy ? '儲存中…' : '確認送出'}}</button><button :disabled="busy" @click="confirmation = ''">取消</button></div></div></div><div v-else class="review-completed"><strong>此項目已完成審核</strong><p>{{detail.item.review_comment}}</p></div><h3>審核紀錄</h3><p v-if="!detail.history.length" class="muted">尚無處理紀錄。</p><article v-for="record in detail.history" :key="record.decision_id" class="review-history"><div><strong>{{label(record.decision)}}</strong><small>{{record.reviewer_name}} · {{date(record.decided_at)}}</small></div><p>{{record.comment}}</p></article></div></template><div v-else class="review-empty"><UiIcon :name="game ? 'game' : 'chat'" /><h3>{{sourceReady ? '選擇一筆項目開始審核' : '尚未串接'}}</h3><p>接入實際資料後，詳細內容與處理紀錄會顯示在這裡。</p></div><p v-if="detailError" class="error review-detail-error" role="alert">{{detailError}}</p></section>
    </div>
  </section>
</template>
