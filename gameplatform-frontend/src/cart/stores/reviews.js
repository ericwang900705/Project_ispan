import { ref, computed, watch } from 'vue'
import { defineStore } from 'pinia'
// axios：發送 HTTP 請求到後端 API
import { api, session } from '../../api.js'
// 目前已驗證的會員編號
import { currentMemberId } from './cart'

// 把 axios 錯誤轉成後端回傳的訊息（後端格式：{ message: '...' }）
function toError(e, fallback) {
  return new Error(e.message || fallback)
}

// 定義遊戲評論的 Pinia store（id 為 'reviews'），存放「目前正在看的那款遊戲」的評論
export const useReviewStore = defineStore('reviews', () => {
  // 目前載入的是哪款遊戲的評論
  const gameId = ref(null)
  // 評論清單，格式與後端回傳的相同
  // [{ id, gameId, username, rating, recommended, content, createdAt, updatedAt, mine }]
  const reviews = ref([])
  let request = 0
  watch(() => session.user, () => { request++; gameId.value = null; reviews.value = [] }, { flush: 'sync' })

  // 推薦比例（0～100），沒有評論時是 null
  const recommendPercent = computed(() =>
    reviews.value.length ? Math.round((reviews.value.filter((r) => r.recommended).length / reviews.value.length) * 100) : null
  )
  // 平均評分（小數一位），沒有評論時是 null
  const averageRating = computed(() =>
    reviews.value.length ? Math.round((reviews.value.reduce((sum, r) => sum + r.rating, 0) / reviews.value.length) * 10) / 10 : null
  )

  // 取得某款遊戲的評論
  async function fetchReviews(id) {
    // GET /api/reviews/game/{gameId}?memberId=：帶 memberId 後端才知道哪則是自己的（mine）
    const expected = ++request
    const data = await api(`/reviews/game/${id}`)
    if (expected === request) { gameId.value = id; reviews.value = data }
  }

  // 新增或修改評論：有 reviewId 就是修改
  // form：{ rating, recommended, content }
  async function saveReview(id, form, reviewId) {
    try {
      if (reviewId) {
        // PUT /api/reviews/{memberId}/{reviewId}
        await api(`/reviews/${currentMemberId()}/${reviewId}`, { method: 'PUT', body: form })
      } else {
        // POST /api/reviews/{memberId}/game/{gameId}
        await api(`/reviews/${currentMemberId()}/game/${id}`, { method: 'POST', body: form })
      }
    } catch (e) {
      throw toError(e, '評論送出失敗')
    }
    await fetchReviews(id)
  }

  // 刪除自己的評論
  async function deleteReview(id, reviewId) {
    try {
      // DELETE /api/reviews/{memberId}/{reviewId}
      await api(`/reviews/${currentMemberId()}/${reviewId}`, { method: 'DELETE' })
    } catch (e) {
      throw toError(e, '刪除失敗')
    }
    await fetchReviews(id)
  }

  // 對外公開的狀態與方法
  return { gameId, reviews, recommendPercent, averageRating, fetchReviews, saveReview, deleteReview }
})
