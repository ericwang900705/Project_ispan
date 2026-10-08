import { ref } from 'vue'
import { defineStore } from 'pinia'
// axios：發送 HTTP 請求到後端 API
import axios from 'axios'

// 登入功能做好之前，先固定用測試會員（後端啟動時自動建立，memberId = 1）
export const MEMBER_ID = 1

// 把 axios 錯誤轉成後端回傳的訊息（後端格式：{ message: '...' }）
function toError(e, fallback) {
  return new Error(e.response?.data?.message ?? fallback)
}

// 定義購物車的 Pinia store（id 為 'cart'），各元件可透過 useCartStore() 共用同一份購物車資料
export const useCartStore = defineStore('cart', () => {
  // 購物車內的項目清單，格式與後端回傳的 items 相同
  // [{ cartItemId, game: { gameId, gameName, coverUrl, price }, isGift, recipientMember, coupon, finalPrice }]
  const items = ref([])
  // 購物車總金額（由後端計算，已套用優惠券）
  const totalPrice = ref(0)
  // 會員遊戲庫（已擁有的遊戲），用來判斷「已擁有」
  const library = ref([])
  // 是否正在與後端溝通中（用來停用按鈕、顯示載入狀態）
  const loading = ref(false)

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
    loading.value = true
    try {
      // GET /api/cart/{memberId}：回傳 { items: [...], totalPrice }
      const { data } = await axios.get(`/api/cart/${MEMBER_ID}`)
      items.value = data.items
      totalPrice.value = data.totalPrice
    } finally {
      // 無論成功或失敗都結束載入狀態
      loading.value = false
    }
  }

  // 取得自己的遊戲庫
  async function fetchLibrary() {
    // GET /api/member-games/{memberId}
    const { data } = await axios.get(`/api/member-games/${MEMBER_ID}`)
    library.value = data
  }

  // 加入購物車；後端拒絕時（例如已擁有）把錯誤訊息往外丟給元件顯示
  async function addItem(gameId) {
    loading.value = true
    try {
      // POST /api/cart/{memberId}/add：自己買只需要 gameId
      await axios.post(`/api/cart/${MEMBER_ID}/add`, { gameId })
    } catch (e) {
      throw toError(e, '加入購物車失敗')
    } finally {
      loading.value = false
    }
    await fetchCart()
  }

  // 從購物車移除一個項目
  async function removeItem(cartItemId) {
    loading.value = true
    try {
      // DELETE /api/cart-items/{memberId}/{cartItemId}
      await axios.delete(`/api/cart-items/${MEMBER_ID}/${cartItemId}`)
    } catch (e) {
      throw toError(e, '移除失敗')
    } finally {
      loading.value = false
    }
    await fetchCart()
  }

  // 綠界付款：後端用購物車建立待付款訂單並回傳綠界表單參數，再整頁跳到綠界付款頁
  // choosePayment：'Credit'（信用卡）、'ATM'、'CVS'（超商代碼）
  // 付款完綠界會導回 /shopping-cart?payment=success|pending|fail&tradeNo=...，由購物車頁顯示結果
  async function payWithEcpay(choosePayment) {
    loading.value = true
    try {
      // POST /api/payment/{memberId}/checkout：回傳 { actionUrl, params }
      const { data } = await axios.post(`/api/payment/${MEMBER_ID}/checkout`, { choosePayment })
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
    const { data } = await axios.get(`/api/payment/${MEMBER_ID}/orders/${tradeNo}`)
    return data
  }

  // 對外公開的狀態與方法
  return { items, totalPrice, library, loading, isInCart, isOwned, fetchCart, fetchLibrary, addItem, removeItem, payWithEcpay, fetchPaymentOrder }
})
