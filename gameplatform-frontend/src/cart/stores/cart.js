import { session } from '../../api.js'
import { ref, watch } from 'vue'
import { defineStore } from 'pinia'
// axios：發送 HTTP 請求到後端 API
import { api } from '../../api.js'

// 會員編號由後端驗證的會話提供；不可固定測試會員。
export function currentMemberId() {
  const id = session.user?.memberId ?? session.user?.member_id ?? session.user?.id
  if (!id || !['MEMBER', 'PUBLISHER'].includes(session.user?.role)) {
    throw new Error('請先登入會員帳號才能使用商城個人功能')
  }
  return id
}

// 把 axios 錯誤轉成後端回傳的訊息（後端格式：{ message: '...' }）
function toError(e, fallback) {
  return new Error(e.message || fallback)
}

// 定義購物車的 Pinia store（id 為 'cart'），各元件可透過 useCartStore() 共用同一份購物車資料
export const useCartStore = defineStore('cart', () => {
  // 購物車內的項目清單，格式與後端回傳的 items 相同
  // [{ cartItemId, game: { gameId, gameName, coverUrl, price }, isGift, recipientMember, coupon, finalPrice }]
  const items = ref([])
  // 購物車總金額（由後端計算，已套用優惠券）
  const totalPrice = ref(0)
  // 整個購物車套用的優惠券（沒有就是 null）：{ couponId, couponName, discountValue, ... }
  const coupon = ref(null)
  // 現在可以用的優惠券（購物車選券用）
  const coupons = ref([])
  // 會員遊戲庫（已擁有的遊戲），用來判斷「已擁有」
  const library = ref([])
  // 是否正在與後端溝通中（用來停用按鈕、顯示載入狀態）
  const loading = ref(false)

  let revision = 0
  watch(() => session.user, () => {
    revision++
    items.value = []
    totalPrice.value = 0
    coupon.value = null
    coupons.value = []
    library.value = []
  }, { flush: 'sync' })

  // 檢查某遊戲是否已在購物車中（只看自己要買的，送禮的不算）
  // 統一轉成字串比較，避免數字 1 與字串 '1' 比對不到
  function isInCart(gameId) {
    return items.value.some((item) => !item.isGift && String(item.game.gameId) === String(gameId))
  }

  // 檢查某遊戲是否已在自己的遊戲庫中
  function isOwned(gameId) {
    return library.value.some((entry) => String(entry.game.gameId) === String(gameId))
  }

  // 取得目前購物車內容
  async function fetchCart() {
    const expected = revision
    loading.value = true
    try {
      // GET /api/cart/{memberId}：回傳 { items: [...], coupon, totalPrice }
      const data = await api(`/cart/${currentMemberId()}`)
      if (expected === revision) setCart(data)
    } finally {
      // 無論成功或失敗都結束載入狀態
      loading.value = false
    }
  }

  // 把後端回傳的購物車存起來
  function setCart(data) {
    items.value = data.items
    coupon.value = data.coupon
    totalPrice.value = data.totalPrice
  }

  // 取得現在可以用的優惠券
  async function fetchCoupons() {
    // GET /api/coupons/usable
    const expected = revision
    const data = await api('/coupons/usable')
    if (expected === revision) coupons.value = data
  }

  // 整個購物車套用優惠券；couponId 傳 null 就是取消
  async function applyCoupon(couponId) {
    const expected = revision
    loading.value = true
    try {
      // PUT /api/cart/{memberId}/coupon：回傳套用後的購物車
      const data = await api(`/cart/${currentMemberId()}/coupon`, { method: 'PUT', body: { couponId } })
      if (expected === revision) setCart(data)
    } catch (e) {
      throw toError(e, '套用優惠券失敗')
    } finally {
      loading.value = false
    }
  }

  // 取得自己的遊戲庫
  async function fetchLibrary() {
    // GET /api/member-games/{memberId}
    const expected = revision
    const data = await api(`/member-games/${currentMemberId()}`)
    if (expected === revision) library.value = data
  }

  // 加入購物車；後端拒絕時（例如已擁有）把錯誤訊息往外丟給元件顯示
  async function addItem(gameId) {
    const expected = revision
    loading.value = true
    try {
      // POST /api/cart/{memberId}/add：自己買只需要 gameId
      await api(`/cart/${currentMemberId()}/add`, { method: 'POST', body: { gameId } })
    } catch (e) {
      throw toError(e, '加入購物車失敗')
    } finally {
      loading.value = false
    }
    if (expected === revision) await fetchCart()
  }

  // 從購物車移除一個項目
  async function removeItem(cartItemId) {
    const expected = revision
    loading.value = true
    try {
      // DELETE /api/cart-items/{memberId}/{cartItemId}
      await api(`/cart-items/${currentMemberId()}/${cartItemId}`, { method: 'DELETE' })
    } catch (e) {
      throw toError(e, '移除失敗')
    } finally {
      loading.value = false
    }
    if (expected === revision) await fetchCart()
  }

  // 綠界付款：後端用購物車建立待付款訂單並回傳綠界表單參數，再整頁跳到綠界付款頁
  // choosePayment：'Credit'（信用卡）、'ATM'、'CVS'（超商代碼）
  // 付款完綠界會導回 /shopping-cart?payment=success|pending|fail&tradeNo=...，由購物車頁顯示結果
  async function payWithEcpay(choosePayment) {
    const expected = revision
    loading.value = true
    try {
      // POST /api/payment/{memberId}/checkout：回傳 { actionUrl, params }
      const data = await api(`/payment/${currentMemberId()}/checkout`, { method: 'POST', body: { choosePayment } })
      if (expected !== revision) throw new Error('帳號已變更，請重新操作')
      // 綠界要用表單 POST 進去，不能用 axios：動態做一個隱藏表單送出，整頁會跳到綠界
      const form = document.createElement('form')
      form.method = 'POST'
      form.action = data.actionUrl
      for (const [name, value] of Object.entries(data.params)) {
        const input = document.createElement('input')
        input.type = 'hidden'
        input.name = name
        input.value = value
        form.appendChild(input)
      }
      document.body.appendChild(form)
      form.submit()
    } catch (e) {
      loading.value = false
      throw toError(e, '結帳失敗')
    }
    // 成功時頁面會跳走，loading 維持 true 讓按鈕不能再按
  }

  // 從綠界導回來後，用綠界訂單編號查付款結果（ATM / 超商代碼會有繳費資訊）
  // tradeNo：綠界訂單編號（網址上的 tradeNo）
  async function fetchPaymentOrder(tradeNo) {
    // GET /api/payment/{memberId}/orders/{tradeNo}
    const data = await api(`/payment/${currentMemberId()}/orders/${tradeNo}`)
    return data
  }

  // 對外公開的狀態與方法
  return { items, totalPrice, coupon, coupons, library, loading, isInCart, isOwned, fetchCart, fetchLibrary, fetchCoupons, applyCoupon, addItem, removeItem, payWithEcpay, fetchPaymentOrder }
})
