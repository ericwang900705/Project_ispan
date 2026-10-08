<script setup>
import { ref, computed, watch, onMounted } from 'vue'
import { useRoute, RouterLink } from 'vue-router'
import { useGamesStore } from '@/stores/games'
import { useCartStore } from '@/stores/cart'
import { useReviewStore } from '@/stores/reviews'
import AddToCartButton from '@/components/AddToCartButton.vue'
import PriceBlock from '@/components/PriceBlock.vue'
import ReviewForm from '@/components/ReviewForm.vue'

// 網址 /games/:id 的 id 就是遊戲編號
const route = useRoute()
const gamesStore = useGamesStore()
const cart = useCartStore()
const reviewStore = useReviewStore()

// 目前這款遊戲（從共用的遊戲清單裡找）
const game = computed(() => gamesStore.games.find((g) => String(g.gameId) === String(route.params.id)))
// 擁有這款遊戲才能寫評論
const owned = computed(() => cart.isOwned(route.params.id))
// 撰寫 / 修改評論視窗是否打開
const formOpen = ref(false)
// 正在修改的評論（新增時是 null）
const editing = ref(null)

// 打開評論視窗：傳評論就是修改，不傳就是新增
function openForm(review = null) {
  editing.value = review
  formOpen.value = true
}

// 進入頁面：遊戲清單、遊戲庫、購物車沒抓過就抓
onMounted(() => {
  if (!gamesStore.games.length) gamesStore.fetchGames()
  cart.fetchLibrary()
  cart.fetchCart()
})
// 換到另一款遊戲時（網址 id 改變）重新抓評論
watch(() => route.params.id, (id) => id && reviewStore.fetchReviews(id), { immediate: true })

// 推薦比例換成 Steam 風格的文字
const summary = computed(() => {
  const p = reviewStore.recommendPercent
  if (p === null) return '尚無評論'
  if (p >= 80) return '極度好評'
  if (p >= 70) return '大多好評'
  if (p >= 40) return '褒貶不一'
  return '大多負評'
})

// 刪除自己的某則評論
async function remove(review) {
  if (!confirm('確定要刪除這則評論嗎？')) return
  try {
    await reviewStore.deleteReview(route.params.id, review.id)
  } catch (e) {
    alert(e.message)
  }
}

// 日期只顯示到日
function day(dateTime) {
  return dateTime?.slice(0, 10)
}
</script>

<template>
  <main class="detail">
    <p v-if="!game" class="none">{{ gamesStore.games.length ? '找不到這款遊戲' : '載入中…' }}</p>

    <template v-else>
      <!-- 上半部：封面 + 遊戲資訊 -->
      <section class="top">
        <img v-if="game.coverUrl" :src="game.coverUrl" :alt="game.gameName" class="cover" />
        <div class="info">
          <h1>{{ game.gameName }}</h1>
          <span v-if="game.genre" class="tag">{{ game.genre }}</span>
          <p class="desc">{{ game.description }}</p>
          <div class="score">
            整體評價：<b :class="{ good: reviewStore.recommendPercent >= 70, bad: reviewStore.recommendPercent < 40 }">{{ summary }}</b>
            <span v-if="reviewStore.reviews.length">
              （{{ reviewStore.reviews.length }} 則評論，{{ reviewStore.recommendPercent }}% 推薦，平均 ★ {{ reviewStore.averageRating }}）
            </span>
          </div>
          <div class="buy">
            <PriceBlock :game="game" />
            <AddToCartButton :game-id="game.gameId" class="cart-btn" />
          </div>
        </div>
      </section>

      <!-- 下半部：評論 -->
      <section class="reviews">
        <div class="head">
          <h2>玩家評論</h2>
          <!-- 有這款遊戲：可以一直寫評論；沒有：提示要先擁有 -->
          <button v-if="owned" class="btn" @click="openForm()">✎ 撰寫評論</button>
          <span v-else class="hint">擁有這款遊戲後即可撰寫評論</span>
        </div>

        <p v-if="!reviewStore.reviews.length" class="none">還沒有人評論這款遊戲</p>

        <!-- 每則評論一張卡片；自己的評論排第一並顯示修改 / 刪除 -->
        <article v-for="r in [...reviewStore.reviews].sort((a, b) => b.mine - a.mine)" :key="r.id" class="review"
          :class="{ mine: r.mine }">
          <div class="verdict" :class="{ no: !r.recommended }">
            <span class="thumb">{{ r.recommended ? '👍' : '👎' }}</span>
            <span>{{ r.recommended ? '推薦' : '不推薦' }}</span>
          </div>
          <div class="body">
            <div class="meta">
              <b>{{ r.username }}</b><span v-if="r.mine" class="me">（你）</span>
              <span class="stars">{{ '★'.repeat(r.rating) }}<i>{{ '★'.repeat(5 - r.rating) }}</i></span>
              <span class="date">{{ day(r.createdAt) }}<template v-if="r.updatedAt"> · 修改於 {{ day(r.updatedAt) }}</template></span>
            </div>
            <p v-if="r.content" class="content">{{ r.content }}</p>
            <div v-if="r.mine" class="own-actions">
              <button @click="openForm(r)">修改</button>
              <button class="del" @click="remove(r)">刪除</button>
            </div>
          </div>
        </article>
      </section>

      <RouterLink to="/games/all" class="back">← 回到所有遊戲</RouterLink>
    </template>

    <ReviewForm v-if="formOpen && game" :game="game" :review="editing" @close="formOpen = false" />
  </main>
</template>

<!-- 遊戲詳情頁樣式（深藍底 + 金色重點色，和遊戲庫同色系） -->
<style scoped>
.detail {
  --bg: #0b0c32;
  --surface: #121a4a;
  --surface-2: #1c2a66;
  --gold: #d4a64a;
  --border: #2e4a9a;
  --text: #e4e8f8;
  --text-muted: #98a2c8;
  --text-dim: #5e6894;

  max-width: 1080px; margin: 0 auto; padding: 16px; min-height: 70vh; color: var(--text);
}
.none { text-align: center; color: var(--text-muted); padding: 40px 0; }

/* ===== 上半部 ===== */
.top { display: flex; gap: 22px; padding-bottom: 22px; border-bottom: 1px solid var(--border); }
.cover { width: 46%; aspect-ratio: 16 / 9; object-fit: cover; border-radius: 6px; box-shadow: 0 6px 18px rgba(0, 0, 0, .5); }
.info { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 10px; }
.info h1 { margin: 0; font-size: 28px; color: var(--gold); line-height: 1.2; }
.tag { align-self: flex-start; padding: 2px 10px; border-radius: 10px; font-size: 12px; background: var(--surface-2); color: var(--text-muted); }
.desc { margin: 0; font-size: 14px; color: var(--text-muted); }
.score { font-size: 14px; color: var(--text-muted); }
.score b { color: var(--text); }
.score b.good { color: #66c0f4; }
.score b.bad { color: #e06060; }
.buy { margin-top: auto; display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }

/* ===== 評論區 ===== */
.reviews { padding-top: 18px; }
.head { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-bottom: 14px; }
.head h2 { margin: 0; font-size: 20px; letter-spacing: 1px; }
.hint { font-size: 13px; color: var(--text-dim); }
.btn {
  padding: 8px 18px; border: none; border-radius: 4px; font-size: 14px; font-weight: bold; cursor: pointer;
  color: #1b0f0d; background: linear-gradient(to bottom, #e8be63, #b8892f);
}
.btn:hover { background: linear-gradient(to bottom, #f5d27f, #c99a3a); }

.review {
  display: flex; margin-bottom: 12px; border-radius: 6px; overflow: hidden;
  background: var(--surface); border: 1px solid transparent;
}
.review.mine { border-color: var(--gold); }
/* 左邊：推薦 / 不推薦 */
.verdict {
  width: 92px; flex-shrink: 0; display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 4px;
  font-size: 13px; color: #66c0f4; background: rgba(102, 192, 244, .1);
}
.verdict.no { color: #e06060; background: rgba(224, 96, 96, .1); }
.thumb { font-size: 26px; }
/* 右邊：作者、星星、內容 */
.body { flex: 1; min-width: 0; padding: 10px 14px; }
.meta { display: flex; align-items: baseline; flex-wrap: wrap; gap: 4px 10px; font-size: 13px; }
.me { color: var(--gold); margin-left: -8px; }
.stars { color: #f0c060; letter-spacing: 1px; }
.stars i { font-style: normal; color: #3a4680; }
.date { color: var(--text-dim); font-size: 12px; }
.content { margin: 8px 0 0; font-size: 14px; white-space: pre-wrap; word-break: break-word; }
.own-actions { display: flex; gap: 8px; margin-top: 8px; }
.own-actions button {
  padding: 3px 12px; border-radius: 4px; font-size: 12px; cursor: pointer;
  border: 1px solid var(--border); background: var(--surface-2); color: var(--text);
}
.own-actions .del:hover { border-color: #e06060; color: #e06060; }

.back { display: inline-block; margin-top: 14px; font-size: 14px; color: var(--text-muted); }
.back:hover { color: var(--gold); }

/* 手機：封面和資訊上下排 */
@media (max-width: 700px) {
  .top { flex-direction: column; }
  .cover { width: 100%; }
  .verdict { width: 64px; }
}
</style>
