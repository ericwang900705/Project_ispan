import { ref } from 'vue'
import { defineStore } from 'pinia'
import axios from '../../http.js'
import { session } from '../../api'
import { watch } from 'vue'
import { memberId } from './cart'

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
  const error = ref('')
  let generation = 0
  watch(() => session.user?.id, () => { generation++; center.value=[]; mine.value=[]; error.value=''; loading.value=false })

  // 兩邊一起抓
  async function fetchAll() {
    const owner=memberId(), version=++generation
    loading.value = true; error.value=''
    try {
      const [c, m] = await Promise.all([
        // 由 JWT 決定會員，不接受固定測試會員。
        axios.get('/api/coupons/center'),
        // 查詢目前會員的已領取優惠券。
        axios.get('/api/coupons/mine'),
      ])
      if(version===generation && session.user?.id===owner) { center.value = c.data; mine.value = m.data }
    } catch(e) {
      if(version===generation && session.user?.id===owner) error.value=toError(e,'載入優惠券失敗').message
    } finally {
      if(version===generation && session.user?.id===owner) loading.value = false
    }
  }

  // 領券：成功後重新抓，張數和按鈕狀態才會更新
  async function claim(couponId) {
    const owner=memberId(), version=generation
    if(loading.value) return false
    loading.value = true
    try {
      // 領券對象由 JWT 決定。
      await axios.post(`/api/coupons/${couponId}/claim`)
    } catch (e) {
      if(session.user?.id===owner && version===generation) throw toError(e, '領取失敗')
      return false
    } finally {
      if(session.user?.id===owner && version===generation) loading.value = false
    }
    if(session.user?.id!==owner || version!==generation) return false
    await fetchAll()
    return true
  }

  return { center, mine, loading, error, fetchAll, claim }
})

