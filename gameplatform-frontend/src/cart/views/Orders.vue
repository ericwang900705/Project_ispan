<script setup>
import { ref, computed, onMounted } from 'vue'
import { useOrdersStore } from '@/cart/stores/orders'

const store = useOrdersStore()

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
  { key: 'closed', label: '已取消 / 失敗' },
]
const filters = {
  all: () => true,
  pending: (o) => o.orderStatus === 'PENDING',
  paid: (o) => o.orderStatus === 'PAID',
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

onMounted(() => store.fetchOrders())

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
          <span class="status" :class="order.orderStatus">{{ STATUS_LABELS[order.orderStatus] ?? order.orderStatus }}</span>
          <span class="games">
            {{ order.orderItems.map((i) => i.game.gameName).join('、') || '—' }}
          </span>
          <span class="amount">NT$ {{ order.totalAmount }}</span>
          <span class="arrow">{{ openId === order.orderId ? '▴' : '▾' }}</span>
        </button>

        <div v-if="openId === order.orderId" class="detail">
          <RouterLink class="sx-contact-link" :to="{path:'/member/support',query:{orderId:String(order.orderId)}}">此訂單有問題？聯絡客服 →</RouterLink>
          <!-- 待付款：ATM／超商代碼的繳費資訊 + 取消按鈕 -->
          <div v-if="order.orderStatus === 'PENDING'" class="pending">
            <dl v-if="order.paymentInfo" class="pay-info">
              <template v-if="order.paymentInfo.virtualAccount">
                <dt>銀行代碼</dt><dd>{{ order.paymentInfo.bankCode }}</dd>
                <dt>轉帳帳號</dt><dd>{{ order.paymentInfo.virtualAccount }}</dd>
              </template>
              <template v-if="order.paymentInfo.paymentNo">
                <dt>繳費代碼</dt><dd>{{ order.paymentInfo.paymentNo }}</dd>
              </template>
              <dt>繳費期限</dt><dd>{{ order.paymentInfo.expireDate }}</dd>
            </dl>
            <p v-else-if="order.paymentType" class="hint">繳費資訊請查看綠界寄給你的通知信</p>
            <p v-else class="hint">付款還沒完成，超過 1 小時沒付款會自動取消</p>
            <button class="cancel" :disabled="store.loading" @click="cancel(order)">取消訂單</button>
          </div>

          <!-- 訂單項目 -->
          <ul class="items">
            <li v-for="item in order.orderItems" :key="item.orderItemId">
              <img :src="item.game.coverUrl" :alt="item.game.gameName" />
              <div class="info">
                <RouterLink :to="`/games/${item.game.gameId}`" class="name">{{ item.game.gameName }}</RouterLink>
                <div class="tags">
                  <span v-if="item.isGift" class="tag gift">🎁 送給 {{ item.recipientMember?.username }}</span>
                  <span v-if="item.coupon" class="tag">🏷️ {{ item.coupon.couponName }}</span>
                </div>
              </div>
              <div class="price">
                <del v-if="Number(item.discountAmount) > 0">NT$ {{ item.unitPrice }}</del>
                <span>NT$ {{ Number(item.unitPrice) - Number(item.discountAmount || 0) }}</span>
              </div>
            </li>
            <li v-if="!order.orderItems.length" class="no-items">付款失敗的訂單不保留明細</li>
          </ul>

          <!-- 付款資訊 -->
          <dl class="meta">
            <template v-if="Number(order.discountAmount) > 0"><dt>優惠券折抵</dt><dd>NT$ {{ order.discountAmount }}</dd></template>
            <dt>付款方式</dt><dd>{{ paymentLabel(order.paymentType) }}</dd>
            <dt>付款時間</dt><dd>{{ formatTime(order.paidAt) }}</dd>
            <template v-if="order.ecpayTradeNo">
              <dt>綠界交易編號</dt><dd>{{ order.ecpayTradeNo }}</dd>
            </template>
            <template v-if="order.orderStatus === 'PAID'">
              <dt>獲得點數</dt><dd>{{ order.pointsEarned }} 點</dd>
            </template>
          </dl>
        </div>
      </div>
    </div>
  </main>
</template>

<!-- 我的訂單樣式：和購物車同色系（暗棕底 + 金色） -->
<style scoped>
/* ===== 配色：和首頁一樣的深藍 + 金色，要換顏色改這裡就好 ===== */
.orders {
  --bg: #0b0c32;          /* 整頁底色 */
  --surface: #121a4a;     /* 卡片底色 */
  --surface-2: #1c2a66;   /* 比較亮的區塊底色 */
  --gold: #d4a64a;        /* 重點色（金色） */
  --gold-dark: #2e4a9a;   /* 邊框色（藍） */
  --text: #e4e8f8;        /* 一般文字 */
  --text-muted: #98a2c8;  /* 次要文字 */

  /* 不設底色，讓 App.vue 的粒子連線背景透出來 */
  min-height: 100vh; padding: 32px 16px; color: var(--text);
}
.wrap { max-width: 1000px; margin: 0 auto; }

.head { display: flex; align-items: baseline; gap: 12px; margin-bottom: 16px; }
.head h1 { margin: 0; font-size: 26px; padding-left: 12px; border-left: 4px solid var(--gold); }
.head span { color: var(--text-muted); font-size: 14px; }

/* 篩選分頁 */
.tabs { display: flex; flex-wrap: wrap; gap: 8px; margin-bottom: 16px; }
.tabs button {
  padding: 6px 14px; border-radius: 16px; font-size: 14px; cursor: pointer;
  border: 1px solid var(--surface-2); background: var(--surface); color: var(--text-muted);
}
.tabs button.on { border-color: var(--gold); color: var(--text); }
.count { font-size: 12px; opacity: .7; }

.empty { text-align: center; padding: 48px 0; color: var(--text-muted); background: var(--surface); border-radius: 8px; border: 1px dashed var(--gold-dark); }
.empty p { margin: 0 0 16px; }

/* 訂單卡片 */
.order { margin-bottom: 10px; border-radius: 8px; background: var(--surface); border: 1px solid var(--surface-2); overflow: hidden; }
.order.open { border-color: var(--gold-dark); }
.summary {
  display: flex; align-items: center; gap: 14px; width: 100%; padding: 14px 16px; cursor: pointer;
  border: none; background: none; color: var(--text); font-size: 14px; text-align: left;
}
.summary:hover { background: var(--surface-2); }
.no { font-weight: bold; color: var(--gold); }
.time { color: var(--text-muted); white-space: nowrap; }
.games { flex: 1; min-width: 0; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.amount { font-weight: bold; white-space: nowrap; }
.arrow { color: var(--text-muted); }

/* 狀態標籤 */
.status { padding: 2px 10px; border-radius: 10px; font-size: 12px; white-space: nowrap; border: 1px solid; }
.status.PAID { color: #8fd07a; border-color: #4a7a3a; }
.status.PENDING { color: #f0c060; border-color: #8a6a20; }
.status.CANCELLED, .status.REFUNDED { color: var(--text-muted); border-color: var(--surface-2); }

/* 展開的明細 */
.detail { padding: 4px 16px 16px; border-top: 1px solid var(--surface-2); }
.pending { display: flex; align-items: center; justify-content: space-between; gap: 16px; flex-wrap: wrap; margin: 12px 0; padding: 12px; border-radius: 6px; background: var(--bg); border: 1px solid #8a6a20; }
.pay-info { display: grid; grid-template-columns: auto 1fr; gap: 4px 16px; margin: 0; font-size: 14px; }
.pay-info dt { color: var(--text-muted); }
.pay-info dd { margin: 0; color: var(--gold); font-weight: bold; letter-spacing: 1px; }
.hint { margin: 0; font-size: 13px; color: var(--text-muted); }
.cancel { padding: 6px 16px; border-radius: 4px; font-size: 13px; cursor: pointer; border: 1px solid #b23a2a; background: none; color: #ff8a6a; }
.cancel:hover:not(:disabled) { background: rgba(178, 58, 42, .15); }
.cancel:disabled { opacity: .5; cursor: default; }

.items { list-style: none; margin: 12px 0 0; padding: 0; display: flex; flex-direction: column; gap: 8px; }
.items li { display: flex; align-items: center; gap: 12px; }
.items img { width: 120px; height: 56px; object-fit: cover; border-radius: 4px; flex-shrink: 0; }
.info { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 4px; }
.name { color: var(--text); font-weight: bold; }
.name:hover { color: var(--gold); background: transparent; }
.tags { display: flex; flex-wrap: wrap; gap: 6px; }
.tag { font-size: 11px; padding: 2px 8px; border-radius: 10px; background: var(--bg); border: 1px solid var(--gold-dark); color: var(--text-muted); }
.tag.gift { border-color: #a8506a; color: #f0a0b8; }
.price { display: flex; flex-direction: column; align-items: flex-end; white-space: nowrap; }
.price del { color: var(--text-muted); font-size: 12px; }
.price span { color: var(--gold); font-weight: bold; }

.meta { display: grid; grid-template-columns: auto 1fr; gap: 4px 16px; margin: 14px 0 0; padding-top: 12px; border-top: 1px dashed var(--surface-2); font-size: 13px; }
.meta dt { color: var(--text-muted); }
.meta dd { margin: 0; }

.btn-gold {
  display: inline-block; border-radius: 6px; padding: 10px 24px; font-weight: bold; color: #1b0f0d;
  background: linear-gradient(to bottom, #e8be63, #b8892f);
}
.btn-gold:hover { filter: brightness(1.12); }

/* 手機：標題列換行，隱藏遊戲名稱 */
@media (max-width: 640px) {
  .summary { flex-wrap: wrap; gap: 8px 12px; }
  .games { display: none; }
  .amount { margin-left: auto; }
  .items img { width: 80px; height: 38px; }
}
</style>
