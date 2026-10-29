<script setup>
import WorkspacePager from '../components/WorkspacePager.vue'
// 退款專用工作頁：只列退款申請，集中核對、完成退款或回覆無法退款。
import { ref, computed, onMounted, onBeforeUnmount } from 'vue'
import { useRoute } from 'vue-router'
import { api, session, timeText } from '../api'
import { hasPermission } from '../permissions'
import UiIcon from '../components/UiIcon.vue'

const route = useRoute()
const q = ref(String(route.query.q || '')), status = ref(''), page = ref(0)
const rows = ref([]), total = ref(0), loading = ref(false), busy = ref(false)
const error = ref(''), notice = ref(''), selected = ref(null), action = ref('')
const reason = ref(''), reference = ref(''), confirmed = ref(false), dialogError = ref('')
const supportNote = ref('')
let generation = 0, alive = true
onBeforeUnmount(() => { alive = false; generation++ })
const states = { PENDING: '待處理', REFUNDED: '已退款', REJECTED: '無法退款' }
const isTest = computed(() => selected.value?.payment_environment === 'TEST')
const money = n => new Intl.NumberFormat('zh-TW', { maximumFractionDigits: 0 }).format(Number(n || 0))
async function load() {
  const run = ++generation
  loading.value = true; error.value = ''
  try {
    const data = await api(`/backoffice/refund-requests?q=${encodeURIComponent(q.value.trim())}&status=${status.value}&page=${page.value}`)
    if (!alive || run !== generation) return
    rows.value = data.items; total.value = data.total
  } catch (e) { if (alive && run === generation) { error.value = e.message; rows.value = []; total.value = 0 } }
  finally { if (alive && run === generation) loading.value = false }
}
function search() { page.value = 0; load() }
function clear() { q.value = ''; status.value = ''; search() }
async function open(row, next = '') {
  error.value = ''; dialogError.value = ''; reason.value = ''; reference.value = ''; confirmed.value = false; supportNote.value = ''
  try { selected.value = await api(`/backoffice/refund-requests/${row.refund_id}`); action.value = next }
  catch (e) { error.value = e.message }
}
function close() { if (!busy.value) selected.value = null }
async function submit() {
  if (busy.value || !selected.value) return
  busy.value = true; dialogError.value = ''
  try {
    const data = await api(`/backoffice/refund-requests/${selected.value.refund_id}/decision`, {
      method: 'POST', body: { action: action.value, reason: reason.value.trim(), reference: reference.value.trim(), confirmed: confirmed.value }
    })
    notice.value = data.message; selected.value = null; await load()
  } catch (e) { dialogError.value = e.message }
  finally { busy.value = false }
}
async function requestSupport() {
  if (busy.value || !supportNote.value.trim()) return
  busy.value = true; dialogError.value = ''
  try {
    const data = await api(`/backoffice/refund-requests/${selected.value.refund_id}/support-case`, { method: 'POST', body: { note: supportNote.value.trim() } })
    selected.value = await api(`/backoffice/refund-requests/${selected.value.refund_id}`)
    notice.value = data.message; action.value = ''; supportNote.value = ''; await load()
  } catch (e) { dialogError.value = e.message }
  finally { busy.value = false }
}
async function paginate(delta) { page.value += delta; await load() }
onMounted(load)
</script>

<template>
  <section class="refund-requests-page refund-layout-v2">
    <header class="management-heading">
      <div class="management-heading-main"><span class="management-heading-icon"><UiIcon name="wallet" /></span>
        <div><span class="management-eyebrow">REFUND WORKSPACE</span><h1>退款申請管理</h1><p>集中處理會員提出的退款申請，保留原因、處理人員與結果。</p></div>
      </div>
      <div class="management-heading-count"><strong>{{ total }}</strong><span>符合條件的申請</span></div>
    </header>
    <p v-if="notice" class="management-notice" role="status">{{ notice }}</p>
    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <form class="management-search refund-search" @submit.prevent="search">
      <label>搜尋<input v-model="q" maxlength="100" placeholder="會員帳號、訂單編號或案件編號"></label>
      <label>申請狀態<select v-model="status" aria-label="申請狀態" :disabled="busy" @change="search"><option value="">全部</option><option v-for="(label, code) in states" :key="code" :value="code">{{ label }}</option></select></label>
      <div class="refund-filter-actions"><button class="primary" :disabled="loading">搜尋</button><button type="button" class="secondary" :disabled="loading" @click="clear">清除篩選</button><button type="button" class="secondary" :disabled="loading" @click="load">重新整理</button></div>
    </form>
    <p class="list-scope">{{ loading ? '載入中…' : `共 ${total} 筆退款申請，分頁顯示` }}</p>
    <div class="management-table-wrap refund-table-panel" :aria-busy="loading">
      <table class="refund-data-table"><thead><tr><th>訂單／會員</th><th>申請原因</th><th>實付金額</th><th>申請時間</th><th>狀態</th><th>處理</th></tr></thead>
        <tbody><tr v-for="row in rows" :key="row.refund_id">
          <td><strong>{{ row.order_no }}</strong><small>{{ row.username }}</small><small>{{ row.payment_environment === 'TEST' ? '測試訂單' : row.payment_environment === 'PROD' ? '正式訂單' : '付款來源待核對' }}</small></td>
          <td class="refund-reason-preview">{{ row.reason }}</td><td>NT$ {{ money(row.total_amount) }}</td><td>{{ timeText(row.requested_at) }}</td>
          <td><span class="refund-status" :class="row.status">{{ states[row.status] }}{{ row.refund_mode === 'TEST' ? '（測試）' : '' }}</span></td>
          <td><div class="refund-row-actions"><button type="button" class="action-info" @click="open(row)">查看</button><template v-if="row.status === 'PENDING'"><button type="button" class="action-success" @click="open(row, 'REFUND')">退款</button><button type="button" class="action-danger" @click="open(row, 'REJECT')">無法退款</button></template></div></td>
        </tr><tr v-if="!loading && !rows.length"><td colspan="6" class="refund-empty">目前沒有符合條件的退款申請。</td></tr></tbody>
      </table>
    </div>
    <WorkspacePager :page="page" :total="total" :size="20" :busy="loading || busy" @change="paginate($event - page)" />
    <Teleport to="body"><div v-if="selected" class="refund-dialog-backdrop support-ui" @click.self="close" @keydown.esc="close">
      <section class="refund-review-dialog" role="dialog" aria-modal="true" aria-labelledby="refund-review-title" tabindex="-1">
        <header><div><span class="management-eyebrow">REFUND REVIEW</span><h2 id="refund-review-title">{{ action === 'REFUND' ? '確認退款' : action === 'REJECT' ? '無法退款' : action === 'SUPPORT' ? '請會員補充資料' : '退款申請明細' }}</h2></div><button type="button" class="secondary" :disabled="busy" aria-label="關閉退款視窗" @click="close">✕</button></header>
        <dl class="refund-summary"><div><dt>訂單</dt><dd>{{ selected.order_no }}</dd></div><div><dt>申請會員</dt><dd>{{ selected.username }}</dd></div><div><dt>實付金額</dt><dd>NT$ {{ money(selected.total_amount) }}</dd></div><div><dt>付款方式</dt><dd>{{ selected.payment_type || '待核對' }}</dd></div></dl>
        <div class="refund-original-reason"><h3>會員申請原因</h3><p>{{ selected.reason }}</p></div>
        <ul class="refund-items"><li v-for="(item, index) in selected.items" :key="index">{{ item.game_name }}<span v-if="item.is_gift">（贈禮）</span><span>NT$ {{ money(Number(item.unit_price) - Number(item.discount_amount)) }}</span></li></ul>
        <section v-if="selected.playTimes?.length" class="refund-play-times"><h3>遊玩時數</h3><p v-for="(item, index) in selected.playTimes" :key="index">{{ item.game_name }}：{{ item.total_play_minutes || 0 }} 分鐘<span v-if="item.last_played_at"> · 最後遊玩 {{ timeText(item.last_played_at) }}</span></p></section>
        <RouterLink v-if="selected.ticket_id && hasPermission(session.user, 'TICKET_HANDLE')" class="refund-ticket-link" :to="`/tickets/${selected.ticket_id}`" @click="close">查看客服案件 {{ selected.ticket_no }} →</RouterLink>
        <template v-if="selected.status !== 'PENDING'"><p class="refund-final-result">{{ states[selected.status] }}{{ selected.refund_mode === 'TEST' ? '（測試）' : '' }} · {{ selected.processed_by_name }} · {{ timeText(selected.processed_at) }}</p><p class="refund-decision-text">{{ selected.decision_reason }}</p><p v-if="selected.refund_reference">退款紀錄：{{ selected.refund_reference }}</p></template>
        <div v-else-if="!action" class="refund-dialog-actions"><button class="action-success" @click="action = 'REFUND'">退款</button><button class="action-danger" @click="action = 'REJECT'">無法退款</button><button v-if="!selected.ticket_id" class="secondary" @click="action = 'SUPPORT'">請會員補充資料</button></div>
        <form v-else-if="action === 'SUPPORT'" @submit.prevent="requestSupport">
          <p class="refund-mode-note">需要補充資料時才建立客服案件。會員會收到通知，退款仍由退款管理頁決定。</p>
          <label>需要補充的資料<textarea v-model="supportNote" required maxlength="2000" rows="4" :disabled="busy" /></label>
          <p v-if="dialogError" class="error" role="alert">{{ dialogError }}</p>
          <footer class="refund-dialog-actions"><button type="button" class="secondary" :disabled="busy" @click="action = ''">返回明細</button><button class="primary" :disabled="busy || !supportNote.trim()">{{ busy ? '建立中…' : '建立補件案件並通知會員' }}</button></footer>
        </form>
        <form v-else @submit.prevent="submit">
          <p v-if="action === 'REFUND'" class="refund-mode-note">{{ isTest ? '測試退款：只更新測試訂單、遊戲庫及優惠券狀態，不會執行真實金流退刷。' : '此頁不會自動執行綠界退刷。請先於金流／銀行完成退款，再填入退款紀錄編號並確認。' }}</p>
          <p v-else class="refund-mode-note">請填寫無法退款原因，處理結果將顯示在會員的「我的訂單」，並發送通知。</p>
          <label>{{ action === 'REJECT' ? '無法退款原因' : '處理說明' }}<textarea v-model="reason" required maxlength="1000" rows="3" placeholder="請清楚說明處理結果，會員也會收到此內容。" :disabled="busy" /></label>
          <label v-if="action === 'REFUND' && !isTest">退款紀錄編號<input v-model="reference" required minlength="5" maxlength="100" placeholder="已完成退款的金流或銀行紀錄編號" :disabled="busy"></label>
          <label v-if="action === 'REFUND'" class="refund-confirm"><input v-model="confirmed" type="checkbox" required :disabled="busy"><span>{{ isTest ? '確認執行整筆測試退款，撤銷本訂單購買／贈送的遊戲權益，並釋放優惠券使用次數。' : '確認已完成整筆實際退款；登記後撤銷本訂單的遊戲權益並釋放優惠券使用次數。' }}</span></label>
          <p v-if="dialogError" class="error" role="alert">{{ dialogError }}</p>
          <footer class="refund-dialog-actions"><button type="button" class="secondary" :disabled="busy" @click="close">取消</button><button class="primary" :disabled="busy || !reason.trim() || (action === 'REFUND' && !confirmed)">{{ busy ? '處理中…' : action === 'REJECT' ? '送出無法退款原因' : isTest ? '確認測試退款' : '登記退款完成' }}</button></footer>
        </form>
      </section>
    </div></Teleport>
  </section>
</template>

<style scoped>
.refund-play-times { padding: 14px; margin-bottom: 16px; background: #eef5fa; border-radius: 10px; color: #244157; }
.refund-play-times p { line-height: 1.7; margin: 6px 0; }

/* Refund workspace: consistent gutters, intentional search and table layout. */
.refund-layout-v2 {
  width: min(100%, 1500px);
  margin-inline: auto;
  padding: 26px clamp(20px, 2.4vw, 38px) 42px;
  box-sizing: border-box;
  min-width: 0;
}
.refund-layout-v2 .management-heading {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24px;
  padding: 4px 8px 24px;
  margin: 0 0 22px;
  min-width: 0;
}
.refund-layout-v2 .management-heading-main { min-width: 0; }
.refund-layout-v2 .management-heading-main h1 { margin: 4px 0 8px; line-height: 1.3; }
.refund-layout-v2 .management-heading-main p { margin: 0; line-height: 1.65; }
.refund-layout-v2 .management-heading-count { flex: none; min-width: 120px; text-align: center; }
.refund-layout-v2 .refund-search {
  display: grid;
  grid-template-columns: minmax(220px, 1.8fr) minmax(160px, .9fr) auto;
  align-items: end;
  gap: 18px 22px;
  margin: 0 0 22px;
  padding: clamp(20px, 2vw, 30px);
  border-radius: 16px;
  box-sizing: border-box;
  width: 100%;
  min-width: 0;
}
.refund-layout-v2 .refund-search > label {
  display: flex;
  flex-direction: column;
  gap: 10px;
  min-width: 0;
  line-height: 1.4;
  margin: 0;
}
.refund-layout-v2 .refund-search input,
.refund-layout-v2 .refund-search select {
  display: block;
  width: 100%;
  min-width: 0;
  height: 44px;
  padding: 10px 13px;
  box-sizing: border-box;
  border-radius: 8px;
  font-size: 14px;
}
.refund-layout-v2 .refund-filter-actions {
  grid-column: auto;
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  align-items: center;
  gap: 10px;
  padding-top: 0;
}
.refund-layout-v2 .refund-filter-actions button {
  min-width: 88px;
  min-height: 44px;
  margin: 0;
  padding: 8px 16px;
  border-radius: 8px;
  white-space: nowrap;
}
.refund-layout-v2 .list-scope {
  margin: 0 0 12px;
  padding: 0 6px;
  line-height: 1.6;
}
.refund-layout-v2 .refund-table-panel {
  width: 100%;
  max-width: 100%;
  overflow-x: auto;
  border-radius: 16px;
  margin: 0;
  box-sizing: border-box;
}
.refund-layout-v2 .refund-data-table {
  width: 100%;
  min-width: 920px;
  border-collapse: collapse;
  table-layout: fixed;
}
.refund-layout-v2 .refund-data-table th,
.refund-layout-v2 .refund-data-table td {
  box-sizing: border-box;
  padding: 20px 18px;
  line-height: 1.7;
  vertical-align: middle;
  text-align: left;
  overflow-wrap: anywhere;
}
.refund-layout-v2 .refund-data-table thead th {
  padding-block: 15px;
  white-space: nowrap;
}
.refund-layout-v2 .refund-data-table th:nth-child(1) { width: 21%; }
.refund-layout-v2 .refund-data-table th:nth-child(2) { width: 20%; }
.refund-layout-v2 .refund-data-table th:nth-child(3) { width: 11%; }
.refund-layout-v2 .refund-data-table th:nth-child(4) { width: 15%; }
.refund-layout-v2 .refund-data-table th:nth-child(5) { width: 13%; }
.refund-layout-v2 .refund-data-table th:nth-child(6) { width: 20%; }
.refund-layout-v2 .refund-data-table td strong { display: block; margin-bottom: 4px; }
.refund-layout-v2 .refund-data-table td small { line-height: 1.55; margin-top: 4px; }
.refund-layout-v2 .refund-data-table .refund-reason-preview { max-width: none; }
.refund-layout-v2 .refund-row-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  min-width: 0;
}
.refund-layout-v2 .refund-row-actions button {
  min-height: 34px;
  padding: 6px 10px;
  margin: 0 !important;
  border-radius: 7px;
  line-height: 1.25;
  font-size: 12px;
  font-weight: 600;
  white-space: nowrap;
}
.refund-layout-v2 .management-pagination.unified-pager { margin-top: 26px; }
@media (max-width: 960px) {
  .refund-layout-v2 { padding-inline: 18px; }
  .refund-layout-v2 .refund-search { grid-template-columns: minmax(0, 1fr) minmax(180px, .7fr); }
  .refund-layout-v2 .refund-filter-actions { grid-column: 1 / -1; }
}
@media (max-width: 640px) {
  .refund-layout-v2 { padding: 18px 12px 32px; }
  .refund-layout-v2 .management-heading { align-items: flex-start; flex-wrap: wrap; }
  .refund-layout-v2 .refund-search { grid-template-columns: minmax(0, 1fr); padding: 18px; }
  .refund-layout-v2 .refund-filter-actions { justify-content: flex-start; }
  .refund-layout-v2 .refund-data-table { min-width: 820px; }
}

.refund-search { display: grid; grid-template-columns: minmax(180px, 1fr) minmax(140px, 220px) auto; gap: 18px; align-items: end; }
.refund-search label, .refund-review-dialog form > label { display: grid; gap: 8px; font-size: 13px; font-weight: 600; }
.refund-search :is(input, select), .refund-review-dialog :is(input:not([type=checkbox]), textarea) { box-sizing: border-box; width: 100%; }
.refund-filter-actions, .refund-row-actions { display: flex; flex-wrap: wrap; gap: 8px; }
.refund-row-actions { min-width: 150px; }.refund-row-actions button { margin: 0 !important; white-space: nowrap; }
td small { display: block; color: #688195; font-size: 11px; margin-top: 5px; }
.refund-reason-preview { max-width: 240px; white-space: pre-line; overflow-wrap: anywhere; }
.refund-status { display: inline-block; padding: 5px 9px; border-radius: 20px; font-size: 12px; background: #fff3d9; color: #805b14; white-space: nowrap; }
.refund-status.REFUNDED { background: #e6f5ed; color: #236443; }.refund-status.REJECTED { background: #fcecee; color: #9c424d; }
.refund-empty { padding: 38px !important; text-align: center; }
.refund-dialog-backdrop { position: fixed; inset: 0; z-index: 1600; display: grid; place-items: center; overflow-y: auto; padding: 24px; background: #10243a99; box-sizing: border-box; }
.refund-review-dialog { box-sizing: border-box; width: min(100%, 700px); max-height: calc(100dvh - 48px); overflow-y: auto; padding: 28px; border-radius: 20px; background: #fff; color: #29485c; box-shadow: 0 25px 80px #001a3340; }
.refund-review-dialog header { display: flex; justify-content: space-between; align-items: start; gap: 16px; margin-bottom: 24px; }.refund-review-dialog h2 { margin: 5px 0; color: #243e52; font-size: 24px; }.refund-review-dialog h3 { margin: 0 0 8px; font-size: 13px; }
.refund-summary { display: grid; grid-template-columns: 1fr 1fr; gap: 18px; padding: 18px; background: #f1f7fa; border-radius: 12px; }.refund-summary dt { color: #718a9a; font-size: 11px; }.refund-summary dd { margin: 5px 0 0; font-weight: 600; overflow-wrap: anywhere; }
.refund-original-reason { padding: 16px 0; }.refund-original-reason p, .refund-decision-text { white-space: pre-wrap; overflow-wrap: anywhere; line-height: 1.8; margin: 0; }
.refund-items { list-style: none; padding: 0; margin: 0 0 18px; border-top: 1px solid #e0e9ef; }.refund-items li { display: flex; gap: 12px; padding: 10px 0; border-bottom: 1px solid #e0e9ef; font-size: 13px; }.refund-items li > span:last-child { margin-left: auto; }
.refund-ticket-link { display: inline-block; color: #147b90; margin-bottom: 14px; font-size: 13px; }
.refund-mode-note { padding: 14px 16px; border-radius: 10px; background: #fff6df; color: #796020; font-size: 13px; line-height: 1.8; }
.refund-review-dialog form { display: grid; gap: 16px; margin: 0; }.refund-review-dialog .refund-confirm { display: flex; align-items: start; gap: 10px; font-weight: 400; font-size: 12px; line-height: 1.8; }.refund-confirm input { margin-top: 5px; flex-shrink: 0; }
.refund-dialog-actions { display: flex; justify-content: flex-end; gap: 10px; flex-wrap: wrap; margin-top: 14px; }.refund-final-result { padding: 12px; background: #edf6fa; border-radius: 10px; }
@media(max-width:850px) { .refund-search { grid-template-columns: 1fr 1fr; }.refund-filter-actions { grid-column: 1 / -1; } }
@media(max-width:520px) { .refund-search { grid-template-columns: 1fr; }.refund-dialog-backdrop { padding: 12px; }.refund-review-dialog { padding: 20px; max-height: calc(100dvh - 24px); }.refund-summary { grid-template-columns: 1fr; }.refund-review-dialog h2 { font-size: 21px; } }
/* 搜尋按鈕與欄位同高，避免共用按鈕樣式將按鈕縮小。 */
body.staff-workspace .support-ui .refund-requests-page .refund-search .refund-filter-actions button {
  height: 44px; min-height: 44px; box-sizing: border-box; white-space: nowrap;
}
</style>

<style>
/* 後台與發行商分頁統一樣式：僅渲染可翻頁的方向，第一頁不顯示上一頁、末頁不顯示下一頁。 */
:where(.management-pagination,.publisher-pagination,.sx-pager,.pager).unified-pager {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-wrap: wrap;
  gap: 14px;
  margin: 24px auto 12px;
  padding: 8px 12px;
  min-height: 56px;
}
:where(.management-pagination,.publisher-pagination,.sx-pager,.pager).unified-pager > button {
  appearance: none;
  min-height: 38px;
  min-width: 76px;
  padding: 8px 16px;
  border: 1px solid #c6dcec;
  border-radius: 9px;
  background: rgba(255,255,255,.96);
  color: #305674;
  font-weight: 500;
  box-shadow: none;
  line-height: 1.3;
  cursor: pointer;
}
:where(.management-pagination,.publisher-pagination,.sx-pager,.pager).unified-pager > button:hover:not(:disabled) {
  background: #f0f7ff;
  border-color: #8eb8d3;
}
:where(.management-pagination,.publisher-pagination,.sx-pager,.pager).unified-pager > button:disabled {
  opacity: .5;
  cursor: not-allowed;
}
:where(.management-pagination,.publisher-pagination,.sx-pager,.pager).unified-pager > span {
  color: #355673;
  font-size: 14px;
  white-space: nowrap;
}

</style>
