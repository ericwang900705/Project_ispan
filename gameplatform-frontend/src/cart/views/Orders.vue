<script setup>
import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useCartStore } from '@/cart/stores/cart'
import { discountLabel } from '@/cart/stores/coupons'
import { useOrdersStore } from '@/cart/stores/orders'

const store = useOrdersStore()
const route = useRoute(), router = useRouter(), cart = useCartStore()
// 優惠券在訂單層級呈現；明細只顯示遊戲金額，不顯示折扣分攤的小數。
const money = value => new Intl.NumberFormat('zh-TW', { maximumFractionDigits: 0 }).format(Number(value) || 0)
function orderCoupons(order) {
  const unique = new Map()
  for (const item of order.orderItems || []) if (item.coupon) {
    const coupon = item.coupon
    const key = coupon.couponId ?? JSON.stringify([coupon.couponName, coupon.discountType, coupon.discountValue])
    if (!unique.has(key)) unique.set(key, coupon)
  }
  return [...unique.values()]
}
function orderSubtotal(order) {
  return order.subtotalAmount ?? Number(order.totalAmount || 0) + Number(order.discountAmount || 0)
}

const refundTarget = ref(null)
const refundReason = ref('')
const refundError = ref('')
const paymentNotice = ref(''), paymentError = ref(''), checkingPayment = ref(false)
let recentPaymentId = null, orderRefreshTimer, refreshing = false
async function checkPayment(orderId) {
 if (checkingPayment.value || store.loading) return
 checkingPayment.value = true; paymentError.value = ''
 try { const result = await store.syncPayment(orderId); paymentNotice.value = result.message }
 catch(e) { paymentError.value = e.message }
 finally { checkingPayment.value = false }
}
async function pay(order) {
  paymentError.value = ''; paymentNotice.value = ''; recentPaymentId = order.orderId
  try { await store.payOrder(order.orderId); paymentNotice.value = '綠界付款頁已在新分頁開啟。'; await store.fetchOrders() }
  catch (e) { paymentError.value = e.message }
}
const now = ref(Date.now())
let clock
onMounted(() => { clock = setInterval(() => { now.value = Date.now() }, 30000) })
onUnmounted(() => clearInterval(clock))
function canRefund(order) {
  return !order.refundStatus && order.orderStatus === 'PAID' && Date.parse(order.refundDeadline) > now.value
}
function openRefund(order) {
  refundReason.value = ''
  refundError.value = ''
  refundTarget.value = order
}
async function submitRefund() {
  if (!refundReason.value.trim() || store.loading) return
  refundError.value = ''
  try {
    await store.requestRefund(refundTarget.value.orderId, refundReason.value.trim())
    refundTarget.value = null
    await store.fetchOrders()
  } catch (e) { refundError.value = e.message }
}

// 訂單狀態的中文名稱
const STATUS_LABELS = {
  PENDING: '待付款',
  PAID: '已付款',
  REFUNDED: '已退款',
  FAILED: '付款失敗',
  CANCELLED: '已取消',
  EXPIRED: '已逾期',
}

// 篩選分頁：key 對應下面的 filters
const tabs = [
  { key: 'all', label: '全部' },
  { key: 'pending', label: '待付款' },
  { key: 'paid', label: '已付款' },
  { key: 'refunded', label: '已退款' },
  { key: 'closed', label: '已取消 / 失敗' },
]
const filters = {
  all: () => true,
  pending: (o) => o.orderStatus === 'PENDING',
  paid: (o) => o.orderStatus === 'PAID',
  refunded: (o) => o.orderStatus === 'REFUNDED',
  closed: (o) => ['FAILED', 'CANCELLED', 'EXPIRED'].includes(o.orderStatus),
}
const tab = ref('all')
const shown = computed(() => store.orders.filter(filters[tab.value]))
// 每個分頁的訂單數（顯示在分頁旁邊）
const counts = computed(() =>
  Object.fromEntries(tabs.map((t) => [t.key, store.orders.filter(filters[t.key]).length])),
)

// 展開明細的訂單編號
const openId = ref(null)
function toggle(orderId) {
  openId.value = openId.value === orderId ? null : orderId
}

async function refreshOrdersWhenVisible() {
  if (document.visibilityState !== 'visible' || store.loading || refreshing) return
  refreshing = true
  try { await store.fetchOrders(); if (recentPaymentId != null) { const id = recentPaymentId; recentPaymentId = null; await checkPayment(id) } }
  catch(e) { paymentError.value = e.message }
  finally { refreshing = false }
}
// 回傳頁的 payment 參數只作提示，成功與否一律以自己的訂單資料確認。
async function verifyPaymentReturn() {
  if (!route.query.payment) return
  const trade = typeof route.query.tradeNo === 'string' ? route.query.tradeNo : ''
  paymentNotice.value = '正在核對付款結果…'
  try {
    if (!/^[A-Za-z0-9]{1,30}$/.test(trade)) throw Error('付款回傳尚未確認，請從下方訂單按「更新付款狀態」核對。')
    let order = await cart.fetchPaymentOrder(trade)
    openId.value = order.orderId; tab.value = 'all'
    if (order.orderStatus === 'PENDING') {
      await store.syncPayment(order.orderId)
      order = store.orders.find(item => item.orderId === order.orderId) || order
    }
    if (order.orderStatus === 'PAID') paymentNotice.value = '付款已確認，此訂單已付款。'
    else if (order.orderStatus === 'REFUNDED') paymentNotice.value = '此訂單已退款。'
    else if (order.orderStatus === 'PENDING') paymentNotice.value = '此訂單尚待確認付款，可稍後按「更新付款狀態」再次核對。'
    else paymentNotice.value = '此訂單目前狀態：' + (STATUS_LABELS[order.orderStatus] || order.orderStatus) + '。如已付款請聯絡客服核對。'
  } catch (e) {
    paymentNotice.value = ''; paymentError.value = e.message || '暫時無法確認付款，請稍後更新付款狀態。'
  } finally {
    const { payment, tradeNo, ...query } = route.query
    await router.replace({ query })
  }
}
function expandRefundOrder() {
  const value = String(route.query.refundOrder || '')
  if (/^[1-9]\d*$/.test(value)) { tab.value = 'all'; openId.value = Number(value) }
}
watch(() => route.query.refundOrder, expandRefundOrder)
onMounted(() => {
  store.fetchOrders().then(async () => { expandRefundOrder(); await verifyPaymentReturn() }).catch(e => { paymentError.value = e.message })
  document.addEventListener('visibilitychange', refreshOrdersWhenVisible)
  window.addEventListener('focus', refreshOrdersWhenVisible)
  orderRefreshTimer = setInterval(() => { if (store.orders.some(o => o.orderStatus === 'PENDING')) refreshOrdersWhenVisible() }, 10000)
})
onUnmounted(() => { document.removeEventListener('visibilitychange', refreshOrdersWhenVisible); window.removeEventListener('focus', refreshOrdersWhenVisible); clearInterval(orderRefreshTimer) })

// 綠界的付款方式代碼換成中文，例如 Credit_CreditCard → 信用卡、ATM_TAISHIN → ATM 轉帳
function paymentLabel(type) {
  if (!type) return '—'
  if (type.startsWith('Credit')) return '信用卡'
  if (type.startsWith('ATM')) return 'ATM 轉帳'
  if (type.startsWith('CVS')) return '超商代碼'
  return type
}

// 後端時間 2026-10-13T15:03:21.123 → 2026-10-13 15:03
function formatTime(value) {
  return value ? value.replace('T', ' ').slice(0, 16) : '—'
}

// 取消待付款訂單；ATM／超商代碼在綠界那邊不會跟著作廢，要提醒不要再去繳費
async function cancel(order) {
  const message = order.paymentType
    ? `確定取消訂單 #${order.orderId}？\n取消後請不要再去繳費；若已付款，請聯絡客服核對。\n如需再次購買，請重新加入購物車。`
    : `確定取消訂單 #${order.orderId}？如需再次購買，請重新加入購物車。`
  if (!confirm(message)) return
  try {
    await store.cancelOrder(order.orderId)
  } catch (e) {
    alert(e.message)
  }
}
</script>

<template>
  <main class="orders">
    <div class="wrap">
      <div class="head">
        <h1>我的訂單</h1>
        <span v-if="store.orders.length">共 {{ store.orders.length }} 筆</span>
      </div>

      <!-- 篩選分頁 -->
      <p v-if="paymentNotice" class="payment-feedback" role="status">{{ paymentNotice }}</p>
      <p v-if="paymentError" class="payment-feedback error" role="alert">{{ paymentError }}</p>
      <div class="tabs">
        <button v-for="t in tabs" :key="t.key" :class="{ on: tab === t.key }" @click="tab = t.key">
          {{ t.label }} <span class="count">{{ counts[t.key] }}</span>
        </button>
      </div>

      <p v-if="store.loading && !store.orders.length" class="empty">載入中…</p>
      <div v-else-if="!shown.length" class="empty">
        <p>{{ store.orders.length ? '這個分類沒有訂單' : '還沒有任何訂單' }}</p>
        <RouterLink v-if="!store.orders.length" to="/" class="btn-gold">去逛逛遊戲</RouterLink>
      </div>

      <!-- 訂單清單：點標題列展開明細 -->
      <div v-for="order in shown" :key="order.orderId" class="order" :class="{ open: openId === order.orderId }">
        <button class="summary" @click="toggle(order.orderId)">
          <span class="no">#{{ order.orderId }}</span>
          <span class="time">{{ formatTime(order.createdAt) }}</span>
          <span class="status" :class="order.orderStatus">{{ STATUS_LABELS[order.orderStatus] ?? order.orderStatus
            }}</span>
          <span class="games">
            {{order.orderItems.map((i) => i.game.gameName).join('、') || '—'}}
          </span>
          <span class="amount">NT$ {{ money(order.totalAmount) }}</span>
          <span class="arrow">{{ openId === order.orderId ? '▴' : '▾' }}</span>
        </button>

        <p v-if="order.paymentNeedsReview" class="payment-feedback error">此訂單有額外付款紀錄需核對，請聯絡客服，勿重複付款。</p>
        <div v-if="order.orderStatus === 'PENDING'" class="pending-pay-entry"><span>尚未完成付款</span><button class="btn-gold" :disabled="store.loading || checkingPayment" @click="checkPayment(order.orderId)">{{ checkingPayment ? '核對中…' : '更新付款狀態' }}</button><button class="btn-gold" :disabled="store.loading" @click="pay(order)">前往付款 ↗</button></div>
        <div v-if="openId === order.orderId" class="detail">
          <RouterLink class="sx-contact-link" :to="{ path: '/member/support', query: { orderId: String(order.orderId) } }">
            此訂單有問題？聯絡客服 →</RouterLink>
          <!-- 待付款：ATM／超商代碼的繳費資訊 + 取消按鈕 -->
          <div v-if="order.orderStatus === 'PENDING'" class="pending">
            <dl v-if="order.paymentInfo" class="pay-info">
              <template v-if="order.paymentInfo.virtualAccount">
                <dt>銀行代碼</dt>
                <dd>{{ order.paymentInfo.bankCode }}</dd>
                <dt>轉帳帳號</dt>
                <dd>{{ order.paymentInfo.virtualAccount }}</dd>
              </template>
              <template v-if="order.paymentInfo.paymentNo">
                <dt>繳費代碼</dt>
                <dd>{{ order.paymentInfo.paymentNo }}</dd>
              </template>
              <dt>繳費期限</dt>
              <dd>{{ order.paymentInfo.expireDate }}</dd>
            </dl>
            <p v-else-if="order.paymentType" class="hint">繳費資訊請查看綠界寄給你的通知信</p>
            <p v-else class="hint">付款尚未完成，可前往付款或取消訂單。</p>
            <button class="cancel" :disabled="store.loading" @click="cancel(order)">取消訂單</button>
          </div>

          <div v-if="order.refundStatus" class="refund-entry refund-result">
            <span>{{ order.refundStatus === 'PENDING' ? '退款申請處理中' : order.refundStatus === 'REJECTED' ? '退款申請未通過' : '退款已完成' }}</span>
            <p v-if="order.refundReason">申請原因：{{ order.refundReason }}</p>
            <p v-if="order.refundDecisionReason">處理說明：{{ order.refundDecisionReason }}</p>
            <p v-if="order.refundProcessedAt">處理時間：{{ formatTime(order.refundProcessedAt) }}{{ order.refundMode === 'TEST' ? '（測試退款）' : '' }}</p>
            <RouterLink v-if="order.refundTicketId" :to="`/member/support/${order.refundTicketId}`">查看補件案件 →</RouterLink>
          </div>
          <div v-if="canRefund(order)" class="refund-entry">
            <button class="cancel" :disabled="store.loading" @click="openRefund(order)">申請退款</button>
            <span class="hint">付款後 14 天內可提出申請，由具有退款權限的人員審核。</span>
          </div>
          <!-- 訂單項目 -->
          <ul class="items">
            <li v-for="item in order.orderItems" :key="item.orderItemId">
              <img :src="item.game.coverUrl" :alt="item.game.gameName" />
              <div class="info">
                <RouterLink :to="`/games/${item.game.gameId}`" class="name">{{ item.game.gameName }}</RouterLink>
                <div class="tags">
                  <span v-if="item.isGift" class="tag gift">🎁 送給 {{ item.recipientMember?.username }}</span>
                </div>
              </div>
              <div class="price">
                <span>NT$ {{ money(item.unitPrice) }}</span>
              </div>
            </li>
            <li v-if="!order.orderItems.length" class="no-items">付款失敗的訂單不保留明細</li>
          </ul>

          <!-- 優惠券不重複放在每款遊戲下方，整筆訂單統一顯示。 -->
          <section class="order-totals" aria-label="訂單金額摘要">
            <div v-if="orderCoupons(order).length" class="order-coupon-once">
              <span class="totals-caption">使用優惠券</span>
              <span v-for="coupon in orderCoupons(order)" :key="coupon.couponId ?? coupon.couponName" class="order-coupon-label">🏷️ {{ coupon.couponName }} · {{ discountLabel(coupon) }}</span>
            </div>
            <dl class="order-totals-rows">
              <div><dt>總金額</dt><dd>NT$ {{ money(orderSubtotal(order)) }}</dd></div>
              <div class="coupon-deduction"><dt>優惠券折抵</dt><dd>{{ Number(order.discountAmount) > 0 ? '− ' : '' }}NT$ {{ money(order.discountAmount) }}</dd></div>
              <div class="total-paid"><dt>{{ ['PAID', 'REFUNDED'].includes(order.orderStatus) ? '實付金額' : '應付金額' }}</dt><dd>NT$ {{ money(order.totalAmount) }}</dd></div>
            </dl>
          </section>

          <!-- 付款資訊 -->
          <dl class="meta">
            <dt>付款方式</dt>
            <dd>{{ paymentLabel(order.paymentType) }}</dd>
            <dt>付款時間</dt>
            <dd>{{ formatTime(order.paidAt) }}</dd>
            <template v-if="order.ecpayTradeNo">
              <dt>綠界交易編號</dt>
              <dd>{{ order.ecpayTradeNo }}</dd>
            </template>
            <template v-if="order.orderStatus === 'PAID'">
              <dt>獲得點數</dt>
              <dd>{{ order.pointsEarned }} 點</dd>
            </template>
          </dl>
        </div>
      </div>
    </div>
    <div v-if="refundTarget" class="refund-overlay">
      <form class="refund-dialog" role="dialog" aria-modal="true" aria-labelledby="refund-title" @submit.prevent="submitRefund">
        <h2 id="refund-title">訂單 #{{ refundTarget.orderId }} 退款申請</h2>
        <p>申請會直接送至退款管理人員。可在「我的訂單」查看進度與結果；送出申請不代表退款完成。</p>
        <label for="refund-reason">退款原因</label>
        <textarea id="refund-reason" v-model="refundReason" required maxlength="2000" rows="5" />
        <p v-if="refundError" role="alert">{{ refundError }}</p>
        <div class="refund-actions">
          <button type="button" class="cancel" :disabled="store.loading" @click="refundTarget = null">取消</button>
          <button class="btn-gold" :disabled="store.loading || !refundReason.trim()">{{ store.loading ? '送出中…' : '送出退款申請' }}</button>
        </div>
      </form>
    </div>
  </main>
</template>

<!-- 我的訂單樣式：和購物車同色系（暗棕底 + 金色） -->
<style scoped>
.order-totals {
  margin: 18px 0; padding: 18px 20px; border: 1px solid #46578b;
  border-radius: 12px; background: #0d173b; color: #e7edff;
}
.order-coupon-once { display: flex; flex-wrap: wrap; align-items: center; gap: 10px; padding-bottom: 16px; margin-bottom: 14px; border-bottom: 1px solid #344572; }
.totals-caption { color: #aebde1; font-size: 13px; }
.order-coupon-label { padding: 6px 10px; border: 1px solid #76643a; border-radius: 8px; background: #d4ab5012; color: #f0d599; font-size: 12px; line-height: 1.6; overflow-wrap: anywhere; }
.order-totals-rows { display: grid; gap: 12px; margin: 0 0 0 auto; max-width: 360px; font-variant-numeric: tabular-nums; }
.order-totals-rows > div { display: flex; justify-content: space-between; align-items: baseline; gap: 24px; }
.order-totals-rows dt { color: #bdc9e7; }
.order-totals-rows dd { margin: 0; font-weight: 600; white-space: nowrap; }
.order-totals-rows .coupon-deduction dd { color: #9ed8ba; }
.order-totals-rows .total-paid { padding-top: 14px; border-top: 1px solid #344572; }
.order-totals-rows .total-paid dt { color: #f0d599; font-weight: 700; }
.order-totals-rows .total-paid dd { color: #f0d599; font-size: 23px; }
@media (max-width: 600px) { .order-totals { padding: 16px; } .order-totals-rows { max-width: none; } }

.refund-entry.refund-result { display: grid; gap: 8px; overflow-wrap: anywhere; }
.refund-result p { margin: 0; white-space: pre-wrap; line-height: 1.7; }
.refund-entry,.refund-actions{display:flex;gap:12px;align-items:center;flex-wrap:wrap;margin:14px 0}
.refund-overlay{position:fixed;inset:0;z-index:1000;background:#05091ac9;display:grid;place-items:center;padding:20px}
.refund-dialog{box-sizing:border-box;width:min(540px,100%);max-height:90vh;overflow:auto;padding:24px;border:1px solid #5265a6;border-radius:14px;background:#121a4a;color:#e4e8f8;box-shadow:0 20px 70px #0008}
.refund-dialog h2{font-size:21px;margin-top:0}.refund-dialog p{line-height:1.7;color:#bdc7e4}
.refund-dialog textarea{display:block;box-sizing:border-box;width:100%;margin-top:8px;padding:12px;background:#0b1030;color:#e4e8f8;border:1px solid #5265a6;border-radius:8px;resize:vertical;font:inherit}
.refund-actions{justify-content:flex-end}.refund-actions button{cursor:pointer}.refund-actions button:disabled{opacity:.5;cursor:default}

/* ===== 配色：和首頁一樣的深藍 + 金色，要換顏色改這裡就好 ===== */
.orders {
  --bg: #0b0c32;
  /* 整頁底色 */
  --surface: #121a4a;
  /* 卡片底色 */
  --surface-2: #1c2a66;
  /* 比較亮的區塊底色 */
  --gold: #d4a64a;
  /* 重點色（金色） */
  --gold-dark: #2e4a9a;
  /* 邊框色（藍） */
  --text: #e4e8f8;
  /* 一般文字 */
  --text-muted: #98a2c8;
  /* 次要文字 */

  /* 不設底色，讓 App.vue 的粒子連線背景透出來 */
  min-height: 100vh;
  padding: 32px 16px;
  color: var(--text);
}

.wrap {
  max-width: 1000px;
  margin: 0 auto;
}

.head {
  display: flex;
  align-items: baseline;
  gap: 12px;
  margin-bottom: 16px;
}

.head h1 {
  margin: 0;
  font-size: 26px;
  padding-left: 12px;
  border-left: 4px solid var(--gold);
}

.head span {
  color: var(--text-muted);
  font-size: 14px;
}

/* 篩選分頁 */
.tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 16px;
}

.tabs button {
  padding: 6px 14px;
  border-radius: 16px;
  font-size: 14px;
  cursor: pointer;
  border: 1px solid var(--surface-2);
  background: var(--surface);
  color: var(--text-muted);
}

.tabs button.on {
  border-color: var(--gold);
  color: var(--text);
}

.count {
  font-size: 12px;
  opacity: .7;
}

.empty {
  text-align: center;
  padding: 48px 0;
  color: var(--text-muted);
  background: var(--surface);
  border-radius: 8px;
  border: 1px dashed var(--gold-dark);
}

.empty p {
  margin: 0 0 16px;
}

/* 訂單卡片 */
.order {
  margin-bottom: 10px;
  border-radius: 8px;
  background: var(--surface);
  border: 1px solid var(--surface-2);
  overflow: hidden;
}

.order.open {
  border-color: var(--gold-dark);
}

.summary {
  display: flex;
  align-items: center;
  gap: 14px;
  width: 100%;
  padding: 14px 16px;
  cursor: pointer;
  border: none;
  background: none;
  color: var(--text);
  font-size: 14px;
  text-align: left;
}

.summary:hover {
  background: var(--surface-2);
}

.no {
  font-weight: bold;
  color: var(--gold);
}

.time {
  color: var(--text-muted);
  white-space: nowrap;
}

.games {
  flex: 1;
  min-width: 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.amount {
  font-weight: bold;
  white-space: nowrap;
}

.arrow {
  color: var(--text-muted);
}

/* 狀態標籤 */
.status {
  padding: 2px 10px;
  border-radius: 10px;
  font-size: 12px;
  white-space: nowrap;
  border: 1px solid;
}

.status.PAID {
  color: #8fd07a;
  border-color: #4a7a3a;
}

.status.PENDING {
  color: #f0c060;
  border-color: #8a6a20;
}

.status.CANCELLED,
.status.REFUNDED {
  color: var(--text-muted);
  border-color: var(--surface-2);
}

/* 展開的明細 */
.detail {
  padding: 4px 16px 16px;
  border-top: 1px solid var(--surface-2);
}

.pending {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
  margin: 12px 0;
  padding: 12px;
  border-radius: 6px;
  background: var(--bg);
  border: 1px solid #8a6a20;
}

.pay-info {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 4px 16px;
  margin: 0;
  font-size: 14px;
}

.pay-info dt {
  color: var(--text-muted);
}

.pay-info dd {
  margin: 0;
  color: var(--gold);
  font-weight: bold;
  letter-spacing: 1px;
}

.hint {
  margin: 0;
  font-size: 13px;
  color: var(--text-muted);
}

.cancel {
  padding: 6px 16px;
  border-radius: 4px;
  font-size: 13px;
  cursor: pointer;
  border: 1px solid #b23a2a;
  background: none;
  color: #ff8a6a;
}

.cancel:hover:not(:disabled) {
  background: rgba(178, 58, 42, .15);
}

.cancel:disabled {
  opacity: .5;
  cursor: default;
}

.items {
  list-style: none;
  margin: 12px 0 0;
  padding: 0;
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.items li {
  display: flex;
  align-items: center;
  gap: 12px;
}

.items img {
  width: 120px;
  height: 56px;
  object-fit: cover;
  border-radius: 4px;
  flex-shrink: 0;
}

.info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.name {
  color: var(--text);
  font-weight: bold;
}

.name:hover {
  color: var(--gold);
  background: transparent;
}

.tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}

.tag {
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 10px;
  background: var(--bg);
  border: 1px solid var(--gold-dark);
  color: var(--text-muted);
}

.tag.gift {
  border-color: #a8506a;
  color: #f0a0b8;
}

.price {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  white-space: nowrap;
}

.price del {
  color: var(--text-muted);
  font-size: 12px;
}

.price span {
  color: var(--gold);
  font-weight: bold;
}

.meta {
  display: grid;
  grid-template-columns: auto 1fr;
  gap: 4px 16px;
  margin: 14px 0 0;
  padding-top: 12px;
  border-top: 1px dashed var(--surface-2);
  font-size: 13px;
}

.meta dt {
  color: var(--text-muted);
}

.meta dd {
  margin: 0;
}

.btn-gold {
  display: inline-block;
  border-radius: 6px;
  padding: 10px 24px;
  font-weight: bold;
  color: #1b0f0d;
  background: linear-gradient(to bottom, #e8be63, #b8892f);
}

.btn-gold:hover {
  filter: brightness(1.12);
}

/* 手機：標題列換行，隱藏遊戲名稱 */
@media (max-width: 640px) {
  .summary {
    flex-wrap: wrap;
    gap: 8px 12px;
  }

  .games {
    display: none;
  }

  .amount {
    margin-left: auto;
  }

  .items img {
    width: 80px;
    height: 38px;
  }
}
</style>

<style scoped>
.order-totals {
  margin: 18px 0; padding: 18px 20px; border: 1px solid #46578b;
  border-radius: 12px; background: #0d173b; color: #e7edff;
}
.order-coupon-once { display: flex; flex-wrap: wrap; align-items: center; gap: 10px; padding-bottom: 16px; margin-bottom: 14px; border-bottom: 1px solid #344572; }
.totals-caption { color: #aebde1; font-size: 13px; }
.order-coupon-label { padding: 6px 10px; border: 1px solid #76643a; border-radius: 8px; background: #d4ab5012; color: #f0d599; font-size: 12px; line-height: 1.6; overflow-wrap: anywhere; }
.order-totals-rows { display: grid; gap: 12px; margin: 0 0 0 auto; max-width: 360px; font-variant-numeric: tabular-nums; }
.order-totals-rows > div { display: flex; justify-content: space-between; align-items: baseline; gap: 24px; }
.order-totals-rows dt { color: #bdc9e7; }
.order-totals-rows dd { margin: 0; font-weight: 600; white-space: nowrap; }
.order-totals-rows .coupon-deduction dd { color: #9ed8ba; }
.order-totals-rows .total-paid { padding-top: 14px; border-top: 1px solid #344572; }
.order-totals-rows .total-paid dt { color: #f0d599; font-weight: 700; }
.order-totals-rows .total-paid dd { color: #f0d599; font-size: 23px; }
@media (max-width: 600px) { .order-totals { padding: 16px; } .order-totals-rows { max-width: none; } }

.refund-entry.refund-result { display: grid; gap: 8px; overflow-wrap: anywhere; }
.refund-result p { margin: 0; white-space: pre-wrap; line-height: 1.7; }
.pending-pay-entry { display: flex; align-items: center; justify-content: flex-end; gap: 16px; padding: 12px 20px; border-top: 1px solid #2e4a9a; color: #b7c5e5; font-size: 13px; }
.pending-pay-entry .btn-gold { border: 0; cursor: pointer; padding: 9px 16px; border-radius: 7px; }
.payment-feedback { padding: 12px 16px; background: #18394b; border: 1px solid #427b8e; border-radius: 9px; color: #b7efe3; }.payment-feedback.error { color: #ffc1c1; background: #422839; border-color: #86556a; }
</style>
