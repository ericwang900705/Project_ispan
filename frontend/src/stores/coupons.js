import { ref } from 'vue'
import { defineStore } from 'pinia'
import axios from 'axios'
import { MEMBER_ID } from './cart'

// 把 axios 錯誤轉成後端回傳的訊息（後端格式：{ message: '...' }）
function toError(e, fallback) {
  return new Error(e.response?.data?.message ?? fallback)
}

// 券的說明文字：打折 → 「8 折」、折抵金額 → 「折 NT$100」；有低消加上「滿 NT$500」
export function discountLabel(c) {
  const discount = c.discountType === 'AMOUNT'
    ? `折 NT$${Number(c.discountValue)}`
    : `${Number((c.discountValue * 10).toFixed(1))} 折`
  return Number(c.minSpend) > 0 ? `滿 NT$${Number(c.minSpend)} ${discount}` : discount
}

// 「優惠券」頁用的 Pinia store：領券中心 + 我的優惠券
export const useCouponsStore = defineStore('coupons', () => {
  // 領券中心：[{ coupon, claimedByMe, totalClaimed, canClaim, reason }]
  const center = ref([])
  // 我的優惠券：[{ memberCouponId, coupon, status, receivedAt, usedAt, orderId }]
  const mine = ref([])
  const loading = ref(false)

  // 兩邊一起抓
  async function fetchAll() {
    loading.value = true
    try {
      const [c, m] = await Promise.all([
        // GET /api/coupons/center?memberId=1
        axios.get('/api/coupons/center', { params: { memberId: MEMBER_ID } }),
        // GET /api/coupons/mine/1
        axios.get(`/api/coupons/mine/${MEMBER_ID}`),
      ])
      center.value = c.data
      mine.value = m.data
    } finally {
      loading.value = false
    }
  }

  // 領券：成功後重新抓，張數和按鈕狀態才會更新
  async function claim(couponId) {
    loading.value = true
    try {
      // POST /api/coupons/1/claim/3
      await axios.post(`/api/coupons/${MEMBER_ID}/claim/${couponId}`)
    } catch (e) {
      throw toError(e, '領取失敗')
    } finally {
      loading.value = false
    }
    await fetchAll()
  }

  return { center, mine, loading, fetchAll, claim }
})
