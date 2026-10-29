import { openEcpayPayment } from '../utils/ecpayWindow'
import { ref } from 'vue'
import { defineStore } from 'pinia'
import axios from '../../http.js'
import { memberId, useCartStore } from './cart'
import { useCouponsStore } from './coupons'

// 「我的訂單」頁用的 Pinia store
export const useOrdersStore = defineStore('orders', () => {
  // 會員的所有訂單（新的在前面），格式與後端回傳相同
  // [{ orderId, totalAmount, orderStatus, createdAt, paidAt, paymentType, pointsEarned,
  //    orderItems: [{ orderItemId, game, unitPrice, isGift, recipientMember, coupon }],
  //    paymentInfo: { bankCode, virtualAccount, paymentNo, expireDate } | null }]
  const orders = ref([])
  // 是否正在與後端溝通中
  const loading = ref(false)

  // 取得會員的所有訂單
  async function fetchOrders() {
    loading.value = true
    try {
      // GET /api/orders/{memberId}
      const { data } = await axios.get(`/api/orders/${memberId()}`)
      orders.value = data
    } finally {
      loading.value = false
    }
  }

  // 取消後釋放優惠券占用，同步刷新購物車與優惠券狀態。
  async function cancelOrder(orderId) {
    loading.value = true
    try {
      // POST /api/orders/{memberId}/{orderId}/cancel
      await axios.post(`/api/orders/${memberId()}/${orderId}/cancel`)
    } catch (e) {
      throw new Error(e.response?.data?.message ?? '取消訂單失敗')
    } finally {
      loading.value = false
    }
    await Promise.all([fetchOrders(), useCartStore().fetchCart(), useCartStore().fetchCoupons(), useCouponsStore().fetchAll()])
  }

  // 直接送出退款申請，不自動建立客服案件，款項、遊戲及優惠券狀態仍由後續人工處理。
  async function requestRefund(orderId, reason) {
    loading.value = true
    try {
      const { data } = await axios.post(`/api/orders/${orderId}/refund-request`, { reason })
      return data
    } catch (e) {
      throw new Error(e.response?.data?.message ?? '退款申請失敗')
    } finally {
      loading.value = false
    }
  }

  async function payOrder(orderId) {
    if (loading.value) return
    loading.value = true
    try {
      return await openEcpayPayment(async () => {
        const { data } = await axios.post(`/api/payment/${memberId()}/orders/${orderId}/pay`)
        return data
      })
    } catch (e) { throw new Error(e.response?.data?.message ?? e.message ?? '無法繼續付款') }
    finally { loading.value = false }
  }

  async function syncPayment(orderId) {
    loading.value = true
    try {
      const { data } = await axios.post(`/api/payment/${memberId()}/orders/${orderId}/sync`)
      await fetchOrders()
      return data
    } catch (e) { throw new Error(e.response?.data?.message ?? '核對付款失敗，請稍後再試') }
    finally { loading.value = false }
  }

  return { orders, loading, fetchOrders, cancelOrder, requestRefund, payOrder, syncPayment }
})
