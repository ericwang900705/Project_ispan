<script setup>
// computed：依其他變數自動算出結果；onMounted：元件顯示到畫面上之後要執行的動作
import { ref, computed, onMounted } from 'vue'
// useRoute：讀網址上綠界導回來的付款結果；useRouter：清掉網址上的參數
import { useRoute, useRouter } from 'vue-router'
// 共用的購物車 store
import { useCartStore } from '@/stores/cart'

// 取得購物車 store（與其他頁面共用同一份資料）
const cart = useCartStore()
const route = useRoute()
const router = useRouter()

// 付款方式：value 是送給綠界的 ChoosePayment
const paymentMethods = [
  { value: 'Credit', label: '信用卡', icon: '💳' },
  { value: 'ATM', label: 'ATM 轉帳', icon: '🏦' },
  { value: 'CVS', label: '超商代碼', icon: '🏪' },
]
const choosePayment = ref('Credit')

// 綠界導回來的付款結果：{ status: 'success' | 'pending' | 'fail', order: 後端查到的訂單 }
const paymentResult = ref(null)

// 進入頁面時向後端抓最新的購物車內容；如果是從綠界導回來的，順便顯示付款結果
onMounted(async () => {
  const { payment, tradeNo } = route.query
  if (payment) {
    // 網址上的參數只用一次，清掉才不會重新整理又跳一次
    router.replace({ query: {} })
    let order = null
    try {
      if (tradeNo) order = await cart.fetchPaymentOrder(tradeNo)
    } catch {
      // 查不到訂單也照樣顯示結果，只是沒有細節
    }
    paymentResult.value = { status: payment, order }
    if (payment === 'success') cart.fetchLibrary()
  }
  cart.fetchCart()
})

// 套用優惠券前的小計（每項遊戲售價加總）
const subtotal = computed(() => cart.items.reduce((sum, item) => sum + item.game.price, 0))
// 優惠券省下的金額 = 小計 - 後端算好的總金額
const saved = computed(() => subtotal.value - cart.totalPrice)

// 移除一個項目
async function remove(cartItemId) {
  try {
    await cart.removeItem(cartItemId)
  } catch (e) {
    alert(e.message)
  }
}

// 結帳：跳到綠界付款頁（測試環境），付款完會導回這一頁
async function checkout() {
  try {
    await cart.payWithEcpay(choosePayment.value)
  } catch (e) {
    alert(e.message) // 例如「購物車是空的」、「已經擁有這款遊戲」
  }
}
</script>

<template>
  <!-- 購物車頁面主要內容 -->
  <main class="shopping-cart">
    <div class="wrap">
      <!-- 標題 + 項目數量 -->
      <div class="head">
        <h1>我的購物車</h1>
        <span v-if="cart.items.length">{{ cart.items.length }} 個項目</span>
      </div>

      <!-- 從綠界導回來的付款結果 -->
      <div v-if="paymentResult" class="result" :class="paymentResult.status">
        <button class="result-close" title="關閉" @click="paymentResult = null">✕</button>
        <!-- 付款成功 -->
        <template v-if="paymentResult.status === 'success'">
          <h2>✅ 付款成功</h2>
          <p v-if="paymentResult.order">
            訂單 #{{ paymentResult.order.orderId }}，共 NT$ {{ paymentResult.order.totalAmount }}，獲得 {{ paymentResult.order.pointsEarned }} 點
          </p>
          <p>遊戲已經加入你的遊戲庫</p>
          <RouterLink to="/member-games" class="btn-gold">前往遊戲庫</RouterLink>
        </template>
        <!-- ATM / 超商代碼：已取號，等待繳費 -->
        <template v-else-if="paymentResult.status === 'pending'">
          <h2>🕒 等待繳費</h2>
          <template v-if="paymentResult.order">
            <p>訂單 #{{ paymentResult.order.orderId }}，應繳 NT$ {{ paymentResult.order.totalAmount }}</p>
            <dl class="pay-info">
              <template v-if="paymentResult.order.virtualAccount">
                <dt>銀行代碼</dt><dd>{{ paymentResult.order.bankCode }}</dd>
                <dt>轉帳帳號</dt><dd>{{ paymentResult.order.virtualAccount }}</dd>
              </template>
              <template v-if="paymentResult.order.paymentNo">
                <dt>繳費代碼</dt><dd>{{ paymentResult.order.paymentNo }}</dd>
              </template>
              <dt>繳費期限</dt><dd>{{ paymentResult.order.expireDate }}</dd>
            </dl>
          </template>
          <p class="hint">繳費完成後遊戲才會加入遊戲庫</p>
        </template>
        <!-- 付款失敗 / 取消 -->
        <template v-else>
          <h2>❌ 付款沒有完成</h2>
          <p>購物車的遊戲都還在，可以再結帳一次</p>
        </template>
      </div>

      <!-- 沒有任何項目時：大圖示 + 提示 + 去逛逛按鈕 -->
      <div v-if="cart.items.length === 0" class="empty">
        <div class="empty-icon">🛒</div>
        <p>購物車是空的</p>
        <RouterLink to="/" class="btn-gold">去逛逛遊戲</RouterLink>
      </div>

      <!-- 有項目時：左邊清單、右邊結帳摘要 -->
      <div v-else class="layout">
        <div class="items">
          <!-- v-for：每個購物車項目顯示一張卡片；:key 用項目 ID 讓 Vue 辨識每一張 -->
          <div v-for="item in cart.items" :key="item.cartItemId" class="item">
            <!-- 遊戲封面 -->
            <img :src="item.game.coverUrl" :alt="item.game.gameName" />
            <div class="info">
              <!-- 遊戲名稱 -->
              <div class="name">{{ item.game.gameName }}</div>
              <!-- 分類、送禮、優惠券以小標籤顯示 -->
              <div class="tags">
                <span v-if="item.game.genre" class="tag">{{ item.game.genre }}</span>
                <span v-if="item.isGift" class="tag gift">🎁 送給 {{ item.recipientMember.username }}</span>
                <span v-if="item.coupon" class="tag coupon">🏷️ {{ item.coupon.couponName }}</span>
              </div>
              <!-- 移除：做成文字連結，比較不搶眼 -->
              <button class="remove" :disabled="cart.loading" @click="remove(item.cartItemId)">移除</button>
            </div>
            <!-- 價格：有折扣時原價加刪除線 -->
            <div class="price">
              <del v-if="item.finalPrice !== item.game.price">NT$ {{ item.game.price }}</del>
              <span>NT$ {{ item.finalPrice }}</span>
            </div>
          </div>
          <RouterLink to="/" class="continue">← 繼續購物</RouterLink>
        </div>

        <!-- 右側結帳摘要：捲動時固定在畫面上 -->
        <aside class="summary">
          <h2>訂單摘要</h2>
          <div class="line">
            <span>小計</span>
            <span>NT$ {{ subtotal }}</span>
          </div>
          <!-- 有用優惠券省到錢才顯示 -->
          <div v-if="saved > 0" class="line saved">
            <span>優惠券折抵</span>
            <span>- NT$ {{ saved }}</span>
          </div>
          <div class="line total">
            <span>總金額</span>
            <span>NT$ {{ cart.totalPrice }}</span>
          </div>
          <!-- 付款方式 -->
          <div class="methods">
            <label v-for="m in paymentMethods" :key="m.value" class="method" :class="{ on: choosePayment === m.value }">
              <input v-model="choosePayment" type="radio" name="payment" :value="m.value" />
              <span class="method-icon">{{ m.icon }}</span>{{ m.label }}
            </label>
          </div>
          <button class="btn-gold checkout" :disabled="cart.loading" @click="checkout">
            {{ cart.loading ? '處理中…' : '前往付款' }}
          </button>
          <p class="note">將前往綠界付款頁（測試環境，不會真的扣款）</p>
        </aside>
      </div>
    </div>
  </main>
</template>

<!-- 購物車頁面樣式（scoped：只作用於此元件；暗棕底 + 金色，和遊戲頁同色系） -->
<style scoped>
/* ===== 配色：要換顏色改這裡就好 ===== */
.shopping-cart {
  --bg: #1b0f0d;          /* 整頁底色 */
  --surface: #2a1a14;     /* 卡片底色 */
  --surface-2: #3a261c;   /* 比較亮的區塊底色 */
  --gold: #d4a64a;        /* 重點色（金色） */
  --gold-dark: #6b4e0f;   /* 深金色 */
  --sale: #b23a2a;        /* 紅色（移除、折抵） */
  --text: #e8dcc8;        /* 一般文字 */
  --text-muted: #a8988a;  /* 次要文字 */

  min-height: 100vh; padding: 32px 16px; color: var(--text);
  background: radial-gradient(ellipse at top, #3a2216 0%, var(--bg) 60%);
}
/* 內容最寬 1100px 置中 */
.wrap { max-width: 1100px; margin: 0 auto; }

/* 標題：左邊金色直線，右邊灰色項目數 */
.head { display: flex; align-items: baseline; gap: 12px; margin-bottom: 20px; }
.head h1 { margin: 0; font-size: 26px; font-weight: bold; color: var(--text); padding-left: 12px; border-left: 4px solid var(--gold); }
.head span { color: var(--text-muted); font-size: 14px; }

/* ===== 空購物車 ===== */
.empty { text-align: center; padding: 60px 0; background: var(--surface); border-radius: 8px; border: 1px dashed var(--gold-dark); }
.empty-icon { font-size: 64px; opacity: .6; }
.empty p { color: var(--text-muted); margin: 8px 0 20px; }

/* ===== 左清單 + 右摘要 ===== */
.layout { display: flex; gap: 24px; align-items: flex-start; }
.items { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 12px; }

/* 單一項目卡片：滑鼠移上去邊框變金色 */
.item {
  display: flex; gap: 16px; padding: 12px; background: var(--surface);
  border: 1px solid var(--surface-2); border-radius: 8px; transition: border-color .2s;
}
.item:hover { border-color: var(--gold-dark); }
/* 封面：橫幅比例，圓角 */
.item img { width: 184px; height: 86px; object-fit: cover; border-radius: 4px; flex-shrink: 0; }
/* 名稱、標籤、移除上下排列 */
.info { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 6px; }
.name { font-size: 17px; font-weight: bold; color: var(--text); white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.tags { display: flex; flex-wrap: wrap; gap: 6px; }
.tag { font-size: 11px; padding: 2px 8px; border-radius: 10px; background: var(--bg); border: 1px solid var(--gold-dark); color: var(--text-muted); }
.tag.gift { border-color: #a8506a; color: #f0a0b8; }
.tag.coupon { border-color: var(--gold); color: var(--gold); }
/* 移除：灰色文字按鈕，滑鼠移上去變紅 */
.remove { align-self: flex-start; margin-top: auto; border: none; background: none; padding: 0; font-size: 12px; color: var(--text-muted); cursor: pointer; text-decoration: underline; }
.remove:hover:not(:disabled) { color: #ff7a5c; }
.remove:disabled { opacity: .5; cursor: default; }
/* 價格：靠右，原價刪除線在上、實付金額在下 */
.price { display: flex; flex-direction: column; align-items: flex-end; justify-content: center; white-space: nowrap; }
.price del { color: var(--text-muted); font-size: 12px; }
.price span { font-size: 18px; font-weight: bold; color: var(--gold); }

/* 繼續購物連結 */
.continue { align-self: flex-start; color: var(--text-muted); font-size: 14px; }
.continue:hover { color: var(--gold); background: transparent; }

/* ===== 結帳摘要：捲動時固定在畫面上方 ===== */
.summary {
  width: 320px; flex-shrink: 0; position: sticky; top: 16px; padding: 20px;
  background: linear-gradient(180deg, var(--surface-2), var(--surface));
  border: 1px solid var(--gold-dark); border-radius: 8px; box-shadow: 0 8px 24px rgba(0, 0, 0, .5);
}
.summary h2 { margin: 0 0 16px; font-size: 18px; color: var(--gold); }
.line { display: flex; justify-content: space-between; padding: 6px 0; font-size: 14px; color: var(--text-muted); }
.line.saved { color: #ff8a6a; }
/* 總金額：上方分隔線、大字金色 */
.line.total { margin-top: 8px; padding-top: 14px; border-top: 1px solid var(--gold-dark); font-size: 16px; color: var(--text); }
.line.total span:last-child { font-size: 24px; font-weight: bold; color: var(--gold); }
.note { margin: 12px 0 0; font-size: 12px; color: var(--text-muted); text-align: center; }

/* ===== 付款方式 ===== */
.methods { display: flex; flex-direction: column; gap: 6px; margin-top: 16px; }
.method {
  display: flex; align-items: center; gap: 8px; padding: 8px 12px; border-radius: 6px; cursor: pointer;
  border: 1px solid var(--surface-2); background: var(--bg); font-size: 14px; color: var(--text-muted);
  transition: border-color .2s, color .2s;
}
.method input { display: none; }
.method:hover { border-color: var(--gold-dark); }
.method.on { border-color: var(--gold); color: var(--text); }
.method-icon { font-size: 18px; }

/* ===== 綠界付款結果 ===== */
.result {
  position: relative; margin-bottom: 20px; padding: 18px 20px; border-radius: 8px;
  background: var(--surface); border: 1px solid var(--gold-dark);
}
.result.success { border-color: #6a9a4a; }
.result.fail { border-color: var(--sale); }
.result h2 { margin: 0 0 8px; font-size: 20px; color: var(--text); }
.result p { margin: 4px 0; color: var(--text-muted); }
.result .btn-gold { margin-top: 12px; padding: 8px 20px; font-size: 14px; }
.result-close { position: absolute; top: 10px; right: 12px; border: none; background: none; color: var(--text-muted); font-size: 16px; cursor: pointer; }
.result-close:hover { color: var(--text); }
.pay-info { display: grid; grid-template-columns: auto 1fr; gap: 4px 16px; margin: 10px 0; font-size: 15px; }
.pay-info dt { color: var(--text-muted); }
.pay-info dd { margin: 0; color: var(--gold); font-weight: bold; letter-spacing: 1px; }
.hint { font-size: 12px; }

/* ===== 金色按鈕（結帳、去逛逛） ===== */
.btn-gold {
  display: inline-block; border: none; border-radius: 6px; padding: 12px 28px; cursor: pointer;
  font-size: 16px; font-weight: bold; color: #1b0f0d; text-align: center;
  background: linear-gradient(to bottom, #e8be63, #b8892f); transition: filter .2s, transform .1s;
}
.btn-gold:hover:not(:disabled) { filter: brightness(1.12); background-color: transparent; }
.btn-gold:active:not(:disabled) { transform: translateY(1px); }
.btn-gold:disabled { background: #4a3a32; color: var(--text-muted); cursor: default; }
.checkout { width: 100%; margin-top: 16px; }

/* ===== 手機 / 小螢幕：摘要移到清單下方，封面縮小 ===== */
@media (max-width: 800px) {
  .layout { flex-direction: column; align-items: stretch; }
  .summary { width: auto; position: static; }
  .item img { width: 110px; height: 52px; }
}
</style>
