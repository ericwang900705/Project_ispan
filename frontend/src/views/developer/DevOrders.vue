<script setup>
import { ref, onMounted } from 'vue'
import axios from 'axios'

// 異常訂單：已取消，卻收到過付款成功通知的（例如取消後才去超商繳費）
const anomalies = ref([])
// 已付款的訂單（可以退款）
const paid = ref([])
// 正在處理的訂單編號（按鈕停用，避免連按兩次）
const busyId = ref(null)
// 處理結果的訊息
const message = ref('')

// 抓兩個清單
async function load() {
  const [a, p] = await Promise.all([
    // GET /api/developer/orders/anomalies、/api/developer/orders/paid
    axios.get('/api/developer/orders/anomalies'),
    axios.get('/api/developer/orders/paid'),
  ])
  anomalies.value = a.data
  paid.value = p.data
}
onMounted(load)

// 補發遊戲 / 退款：action 是 'grant' 或 'refund'
async function handle(order, action) {
  const text = action === 'grant'
    ? `確定補發訂單 #${order.orderId} 的遊戲？訂單會改成「已付款」。`
    : `確定將訂單 #${order.orderId} 標記為已退款（NT$ ${order.totalAmount}）？\n遊戲會從遊戲庫停用，評論會隱藏。\n實際退錢請到綠界後台操作。`
  if (!confirm(text)) return
  busyId.value = order.orderId
  message.value = ''
  try {
    // POST /api/developer/orders/{orderId}/grant 或 /refund
    await axios.post(`/api/developer/orders/${order.orderId}/${action}`)
    message.value = action === 'grant' ? `訂單 #${order.orderId} 已補發遊戲` : `訂單 #${order.orderId} 已標記為退款`
    await load()
  } catch (e) {
    message.value = e.response?.data?.message ?? '處理失敗'
  } finally {
    busyId.value = null
  }
}

// 後端時間 2026-10-13T15:03:21.123 → 2026-10-13 15:03
function formatTime(value) {
  return value ? value.replace('T', ' ').slice(0, 16) : '—'
}

// 綠界的付款方式代碼換成中文
function paymentLabel(type) {
  if (!type) return '—'
  if (type.startsWith('Credit')) return '信用卡'
  if (type.startsWith('ATM')) return 'ATM 轉帳'
  if (type.startsWith('CVS')) return '超商代碼'
  return type
}
</script>

<template>
  <section class="dev-orders">
    <h1>訂單管理</h1>
    <p v-if="message" class="message">{{ message }}</p>

    <!-- 異常訂單：有才顯示，紅色邊框提醒要處理 -->
    <h2>⚠️ 異常訂單 <small>（{{ anomalies.length }}）</small></h2>
    <p class="hint">訂單已取消，但綠界通知付款成功。系統不會自動給遊戲，請選擇補發遊戲或退款。</p>
    <div class="table-wrap" :class="{ alert: anomalies.length }">
      <table>
        <thead>
          <tr><th>訂單</th><th>會員</th><th>遊戲</th><th>付款方式</th><th class="num">金額</th><th>下單時間</th><th></th></tr>
        </thead>
        <tbody>
          <tr v-for="o in anomalies" :key="o.orderId">
            <td>#{{ o.orderId }}</td>
            <td>{{ o.username }}</td>
            <td class="games">{{ o.games.join('、') }}</td>
            <td>{{ paymentLabel(o.paymentType) }}</td>
            <td class="num">NT$ {{ o.totalAmount }}</td>
            <td>{{ formatTime(o.createdAt) }}</td>
            <td class="actions">
              <button class="grant" :disabled="busyId === o.orderId" @click="handle(o, 'grant')">補發遊戲</button>
              <button class="refund" :disabled="busyId === o.orderId" @click="handle(o, 'refund')">退款</button>
            </td>
          </tr>
          <tr v-if="!anomalies.length"><td colspan="7" class="empty">沒有異常訂單</td></tr>
        </tbody>
      </table>
    </div>

    <!-- 已付款訂單：可以退款 -->
    <h2>已付款訂單 <small>（{{ paid.length }}）</small></h2>
    <div class="table-wrap">
      <table>
        <thead>
          <tr><th>訂單</th><th>會員</th><th>遊戲</th><th>付款方式</th><th class="num">金額</th><th>付款時間</th><th></th></tr>
        </thead>
        <tbody>
          <tr v-for="o in paid" :key="o.orderId">
            <td>#{{ o.orderId }}</td>
            <td>{{ o.username }}</td>
            <td class="games">{{ o.games.join('、') }}</td>
            <td>{{ paymentLabel(o.paymentType) }}</td>
            <td class="num">NT$ {{ o.totalAmount }}</td>
            <td>{{ formatTime(o.paidAt ?? o.createdAt) }}</td>
            <td class="actions">
              <button class="refund" :disabled="busyId === o.orderId" @click="handle(o, 'refund')">退款</button>
            </td>
          </tr>
          <tr v-if="!paid.length"><td colspan="7" class="empty">沒有已付款的訂單</td></tr>
        </tbody>
      </table>
    </div>
  </section>
</template>

<style scoped>
.dev-orders h1 { margin: 0 0 16px; font-size: 24px; }
.dev-orders h2 { margin: 24px 0 6px; font-size: 18px; }
.dev-orders h2 small { font-size: 14px; color: #98a2c8; font-weight: normal; }
.hint { margin: 0 0 10px; font-size: 13px; color: #98a2c8; }
.message { padding: 10px 14px; border-radius: 6px; background: #1c2a66; border: 1px solid #d4a64a; }

/* 表格外框：太寬時在框裡左右捲動 */
.table-wrap { overflow-x: auto; background: rgba(18, 26, 74, .85); border: 1px solid #2e4a9a; border-radius: 8px; }
/* 有異常訂單時邊框變紅 */
.table-wrap.alert { border-color: #b23a2a; }
table { width: 100%; border-collapse: collapse; font-size: 14px; }
th { text-align: left; padding: 12px; font-size: 13px; font-weight: normal; color: #98a2c8; border-bottom: 1px solid #2e4a9a; white-space: nowrap; }
td { padding: 10px 12px; border-bottom: 1px solid #1c2a66; vertical-align: middle; white-space: nowrap; }
td.games { white-space: normal; min-width: 160px; }
tbody tr:hover { background: #1c2a66; }
.num { text-align: right; font-variant-numeric: tabular-nums; }
.empty { text-align: center; color: #98a2c8; padding: 24px; }

/* 操作按鈕 */
.actions { display: flex; gap: 8px; }
.actions button {
  padding: 4px 12px; font-size: 13px; border-radius: 4px; cursor: pointer; white-space: nowrap;
  border: 1px solid #2e4a9a; background: #121a4a; color: #e4e8f8;
}
.actions button:disabled { opacity: .5; cursor: default; }
.actions .grant:hover:not(:disabled) { border-color: #8fd07a; color: #8fd07a; }
.actions .refund:hover:not(:disabled) { border-color: #e06060; color: #e06060; }
</style>
