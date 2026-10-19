<script setup>
import { ref, computed, watch, onMounted, onUnmounted } from 'vue'
import { useRoute, RouterLink } from 'vue-router'
import { useGamesStore, addRecent } from '@/stores/games'
import { useCartStore } from '@/stores/cart'
import { useReviewStore } from '@/stores/reviews'
import AddToCartButton from '@/components/AddToCartButton.vue'
import GiftButton from '@/components/GiftButton.vue'
import PriceBlock from '@/components/PriceBlock.vue'
import ReviewForm from '@/components/ReviewForm.vue'
import axios from 'axios'


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

// 分頁列：點了就捲到對應的區塊（key 就是區塊的 id）
const tabs = [
  { key: 'media', label: '遊戲畫面' },
  { key: 'info', label: '詳細資訊' },
  { key: 'intro', label: '遊戲介紹' },
  { key: 'reviews', label: '玩家評論' },
]
// 目前選到的分頁（底下有藍線）
const tab = ref('media')
function goTo(key) {
  tab.value = key
  document.getElementById(key)?.scrollIntoView({ behavior: 'smooth' })
}

// 遊戲畫面：封面排第一張，後面接資料庫 game_images 的截圖
const images = ref([])
const shots = computed(() => [game.value?.coverUrl, ...images.value].filter(Boolean))

// 向後端拿這款遊戲的截圖網址；拿不到就只顯示封面
async function fetchImages(id) {
  images.value = []
  try {
    // GET /api/games/{id}/images：回傳 [ "網址1", "網址2", ... ]
    const { data } = await axios.get(`/api/games/${id}/images`)
    images.value = data
  } catch {
    // 截圖不影響買遊戲，失敗就算了
  }
}

// 一頁顯示 5 張（1 張大圖 + 4 張小圖），按左右箭頭換頁
const PAGE_SIZE = 5
const page = ref(0)
const pageCount = computed(() => Math.max(1, Math.ceil(shots.value.length / PAGE_SIZE)))
const pageShots = computed(() => shots.value.slice(page.value * PAGE_SIZE, (page.value + 1) * PAGE_SIZE))


// 捲過上方橫幅之後，分頁列左邊出現小封面、右邊出現購買按鈕
const hero = ref(null)
const stuck = ref(false)
function onScroll() {
  stuck.value = hero.value ? hero.value.getBoundingClientRect().bottom < 0 : false
}

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
  window.addEventListener('scroll', onScroll)
})
// 離開頁面時把捲動監聽拿掉
onUnmounted(() => window.removeEventListener('scroll', onScroll))
// 換到另一款遊戲時（網址 id 改變）重新抓評論
// 同時記到「最近瀏覽」（首頁分頁會用到）
watch(() => route.params.id, (id) => {
  if (!id) return
  reviewStore.fetchReviews(id)
  addRecent(id)
  page.value = 0
  fetchImages(id)
}, { immediate: true })

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
      <!-- ① 上方橫幅：封面圖放大、模糊當背景 -->
      <section ref="hero" class="hero">
        <div class="hero-bg" :style="{ backgroundImage: `url(${game.coverUrl})` }"></div>
        <div class="hero-inner">
          <img v-if="game.coverUrl" :src="game.coverUrl" :alt="game.gameName" class="cover" />
          <div class="info">
            <h1>{{ game.gameName }}</h1>
            <p class="desc">{{ game.description }}</p>
            <span v-if="game.genre" class="tag">{{ game.genre }}</span>
            <div class="score">
              整體評價：<b :class="{ good: reviewStore.recommendPercent >= 70, bad: reviewStore.recommendPercent < 40 }">{{ summary }}</b>
              <span v-if="reviewStore.reviews.length">（{{ reviewStore.reviews.length }} 則評論）</span>
            </div>
          </div>
          <!-- 右側購買框 -->
          <aside class="buy-box">
            <PriceBlock :game="game" />
            <AddToCartButton :game-id="game.gameId" />
            <GiftButton :game-id="game.gameId" :game-name="game.gameName" />
          </aside>
        </div>
      </section>

      <!-- ② 分頁列：捲動時黏在畫面最上面 -->
      <nav class="tabs">
        <img v-if="stuck && game.coverUrl" :src="game.coverUrl" :alt="game.gameName" class="tabs-cover" />
        <button v-for="t in tabs" :key="t.key" :class="{ on: tab === t.key }" @click="goTo(t.key)">
          {{ t.label }}
        </button>
        <AddToCartButton v-if="stuck" :game-id="game.gameId" class="tabs-buy" />
      </nav>

      <!-- ③ 遊戲畫面：第一張大圖，其他小圖排在右邊；超過 5 張可以左右換頁 -->
      <section id="media" class="block full">
        <!-- 標題 + 右邊的頁數小方塊和左右箭頭（超過一頁才顯示） -->
        <div class="media-head">
          <h2>遊戲畫面</h2>
          <div v-if="pageCount > 1" class="pager">
            <span v-for="n in pageCount" :key="n" class="dot" :class="{ on: page === n - 1 }"></span>
            <button :disabled="page === 0" @click="page--">‹</button>
            <button :disabled="page === pageCount - 1" @click="page++">›</button>
          </div>
        </div>
        <div class="gallery" :class="{ single: pageShots.length === 1 }">
          <img v-for="(src, i) in pageShots" :key="page * PAGE_SIZE + i" :src="src" :alt="game.gameName" />
        </div>
      </section>

      <!-- ④ 詳細資訊：左邊標題、右邊資訊框 -->
      <section id="info" class="block">
        <h2>詳細資訊</h2>
        <dl class="box facts">
          <div><dt>類型</dt><dd>{{ game.genre || '未分類' }}</dd></div>
          <div><dt>售價</dt><dd>{{ game.price === 0 ? '免費' : `NT$ ${game.price}` }}</dd></div>
          <div v-if="game.originalPrice > game.price"><dt>原價</dt><dd>NT$ {{ game.originalPrice }}</dd></div>
          <div><dt>整體評價</dt><dd>{{ summary }}</dd></div>
        </dl>
      </section>

      <!-- ⑤ 遊戲介紹：左邊標題、右邊介紹框 -->
      <section id="intro" class="block">
        <h2>遊戲介紹</h2>
        <p class="box intro">{{ game.description || '尚無介紹' }}</p>
      </section>

      <!-- ⑥ 玩家評論 -->
      <section id="reviews" class="reviews">
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
            <!-- 開發商的回覆 -->
            <div v-if="r.developerReply" class="dev-reply">
              <span class="dev-reply-label">開發商回覆 · {{ day(r.repliedAt) }}</span>
              <p>{{ r.developerReply }}</p>
            </div>
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

  max-width: 1200px; margin: 0 auto; padding: 16px; min-height: 70vh; color: var(--text);
}
.none { text-align: center; color: var(--text-muted); padding: 40px 0; }

/* ===== ① 上方橫幅 ===== */
.hero { position: relative; overflow: hidden; border-radius: 8px; }
/* 背景：封面圖放大、模糊、變暗；inset 設負的，模糊的邊緣才不會露出來 */
.hero-bg {
  position: absolute; inset: -30px;
  background-size: cover; background-position: center;
  filter: blur(18px) brightness(.35);
}
/* 內容疊在背景上面：封面、資訊、購買框橫排 */
.hero-inner { position: relative; display: flex; align-items: center; gap: 28px; padding: 40px 32px; }
.cover { width: 320px; aspect-ratio: 16 / 9; object-fit: cover; border-radius: 6px; flex-shrink: 0; box-shadow: 0 8px 24px rgba(0, 0, 0, .6); }
.info { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 12px; }
.info h1 { margin: 0; font-size: 36px; color: #fff; line-height: 1.2; }
/* 介紹最多顯示 3 行，多的變成 …（完整的在「遊戲介紹」分頁） */
.desc {
  margin: 0; font-size: 15px; line-height: 1.7; color: var(--text-muted);
  display: -webkit-box; -webkit-line-clamp: 3; -webkit-box-orient: vertical; overflow: hidden;
}
.tag { align-self: flex-start; padding: 3px 12px; border-radius: 14px; font-size: 12px; border: 1px solid var(--text-dim); color: var(--text-muted); }
.score { font-size: 14px; color: var(--text-muted); }
.score b { color: var(--text); }
.score b.good { color: #66c0f4; }
.score b.bad { color: #e06060; }
/* 右側購買框：半透明深色底，按鈕撐滿寬度 */
.buy-box {
  width: 260px; flex-shrink: 0; display: flex; flex-direction: column; gap: 10px;
  padding: 24px 20px; border-radius: 6px; background: rgba(11, 12, 50, .75);
}
.buy-box :deep(button) { width: 100%; padding: 12px; font-size: 15px; }

/* ===== ② 分頁列：黏在畫面最上面，選到的分頁底下有藍線 ===== */
.tabs {
  position: sticky; top: 0; z-index: 10; display: flex; align-items: center; gap: 4px;
  margin-top: 12px; background: var(--bg); border-bottom: 1px solid var(--border);
}
.tabs button {
  padding: 16px 24px; border: none; border-bottom: 3px solid transparent; background: none;
  font-size: 15px; color: var(--text-muted); cursor: pointer;
}
.tabs button:hover { color: var(--text); }
.tabs button.on { color: var(--text); border-bottom-color: #2f7bff; }
/* 捲過橫幅後才出現：左邊小封面、右邊購買按鈕 */
.tabs-cover { width: 48px; height: 48px; object-fit: cover; border-radius: 4px; margin-right: 12px; }
.tabs-buy { margin-left: auto; padding: 10px 24px; }

/* ===== ③～⑤ 各區塊：左邊標題、右邊內容框 ===== */
/* scroll-margin-top：捲過去時留位置給分頁列，標題才不會被擋住 */
.block { display: grid; grid-template-columns: 220px 1fr; gap: 24px; padding: 40px 0; scroll-margin-top: 70px; }
.block.full { grid-template-columns: 1fr; }
.block h2 { margin: 0; font-size: 22px; font-weight: normal; }
.box { margin: 0; padding: 28px 36px; border-radius: 6px; background: var(--surface); border: 1px solid var(--border); }
/* 遊戲畫面：第一張大圖佔左邊兩列，其他小圖兩兩排在右邊 */
.gallery { display: grid; grid-template-columns: 2fr 1fr 1fr; gap: 16px; }
.gallery img { width: 100%; aspect-ratio: 16 / 9; object-fit: cover; border-radius: 6px; }
.gallery img:first-child { grid-row: span 2; height: 100%; }
/* 只有一張圖：撐滿整排，但不要太高 */
.gallery.single { grid-template-columns: 1fr; }
.gallery.single img { max-height: 480px; }
.media-head {display: flex;align-items: center;justify-content: space-between}
.pager { display: flex; align-items: center; gap: 8px; }
.dot{width: 6px;height: 6px;background:var(--text-dim);}
.dot.on{background: var(--text);}
.pager button{
  width:36px; height: 36px; border:1px solid var(--border); border-radius: 4px;
  background: var(--surface); color:var(--text); font-size:20px;cursor: pointer;
}
.pager button:hover:not(:disabled){border-color: #2f7bff ;}
.pager button:disabled{opacity: .3;cursor: default;}
/* 詳細資訊：每一項橫著排 */
.facts { display: flex; flex-wrap: wrap; gap: 16px 80px; }
.facts dt { font-size: 13px; color: var(--text-dim); margin-bottom: 6px; }
.facts dd { margin: 0; font-size: 15px; }
.intro { font-size: 15px; line-height: 1.9; color: var(--text-muted); white-space: pre-wrap; }

/* ===== ⑥ 評論區 ===== */
.reviews { padding-top: 18px; scroll-margin-top: 70px; }
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
/* 開發商回覆：左邊金色線，跟玩家的評論區分開 */
.dev-reply { margin-top: 8px; padding: 6px 12px; border-left: 3px solid var(--gold); background: var(--surface-2); border-radius: 0 6px 6px 0; }
.dev-reply-label { font-size: 12px; color: var(--gold); }
.dev-reply p { margin: 4px 0 0; font-size: 14px; white-space: pre-wrap; word-break: break-word; }
.own-actions { display: flex; gap: 8px; margin-top: 8px; }
.own-actions button {
  padding: 3px 12px; border-radius: 4px; font-size: 12px; cursor: pointer;
  border: 1px solid var(--border); background: var(--surface-2); color: var(--text);
}
.own-actions .del:hover { border-color: #e06060; color: #e06060; }

.back { display: inline-block; margin-top: 14px; font-size: 14px; color: var(--text-muted); }
.back:hover { color: var(--gold); }

/* 手機 / 小螢幕：封面、資訊、購買框改成上下排 */
@media (max-width: 900px) {
  .hero-inner { flex-direction: column; align-items: stretch; padding: 24px 16px; }
  .cover, .buy-box { width: 100%; }
  .tabs button { padding: 14px 16px; }
  .block { grid-template-columns: 1fr; }
  .tabs { overflow-x: auto; }
  .tabs-cover { display: none; }
  .verdict { width: 64px; }
}

</style>
