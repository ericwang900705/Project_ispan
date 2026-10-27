import { ref } from 'vue'
import { defineStore } from 'pinia'
import axios from 'axios'
import { MEMBER_ID, useCartStore } from './cart'

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
      const { data } = await axios.get(`/api/orders/${MEMBER_ID}`)
      orders.value = data
    } finally {
      loading.value = false
    }
  }

  // 取消待付款訂單：後端會把遊戲放回購物車，所以購物車也要重新抓
  async function cancelOrder(orderId) {
    loading.value = true
    try {
      // POST /api/orders/{memberId}/{orderId}/cancel
      await axios.post(`/api/orders/${MEMBER_ID}/${orderId}/cancel`)
    } catch (e) {
      throw new Error(e.response?.data?.message ?? '取消訂單失敗')
    } finally {
      loading.value = false
    }
    await Promise.all([fetchOrders(), useCartStore().fetchCart()])
  }

  // 自己退款（已付款、付款後 14 天內）：遊戲會停用、優惠券還回來，所以遊戲庫和可用的券也要重新抓
  async function refundOrder(orderId) {
    loading.value = true
    try {
      // POST /api/orders/{memberId}/{orderId}/refund
      await axios.post(`/api/orders/${MEMBER_ID}/${orderId}/refund`)
    } catch (e) {
      throw new Error(e.response?.data?.message ?? '退款失敗')
    } finally {
      loading.value = false
    }
    const cart = useCartStore()
    await Promise.all([fetchOrders(), cart.fetchLibrary(), cart.fetchCoupons()])
  }

  return { orders, loading, fetchOrders, cancelOrder, refundOrder }
})
