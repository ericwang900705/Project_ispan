<script setup>
// onMounted：元件顯示到畫面上之後要執行的動作
import { ref, computed, onMounted } from 'vue'
import { RouterLink } from 'vue-router'
// 共用的購物車 store（遊戲庫資料也放在這裡）
import { useCartStore } from '@/stores/cart'
import ReviewForm from '@/components/ReviewForm.vue'

const cart = useCartStore()
// 進入頁面時向後端抓最新的遊戲庫
onMounted(() => cart.fetchLibrary())

// 取得途徑的中文顯示
const sourceLabel = { PURCHASE: '購買', GIFT: '禮物' }

// 搜尋框文字、排序方式
const keyword = ref('')
const sortBy = ref('recent')
const sortOptions = { recent: '最近取得', name: '名稱', playtime: '遊玩時數' }

// 依搜尋文字過濾，再依選的方式排序
const shown = computed(() => {
  const list = cart.library.filter((e) =>
    e.game.gameName.toLowerCase().includes(keyword.value.trim().toLowerCase())
  )
  if (sortBy.value === 'name') return list.sort((a, b) => a.game.gameName.localeCompare(b.game.gameName, 'zh-TW'))
  if (sortBy.value === 'playtime') return list.sort((a, b) => (b.totalPlayMinutes ?? 0) - (a.totalPlayMinutes ?? 0))
  return list.sort((a, b) => String(b.obtainedAt ?? '').localeCompare(String(a.obtainedAt ?? '')))
})

// 總遊玩時數（顯示在標題旁）
const totalMinutes = computed(() => cart.library.reduce((sum, e) => sum + (e.totalPlayMinutes ?? 0), 0))

// 分鐘換成好讀的文字：沒玩過、45 分鐘、3.5 小時
function playTime(minutes) {
  if (!minutes) return '尚未遊玩'
  if (minutes < 60) return `${minutes} 分鐘`
  return `${Math.round((minutes / 60) * 10) / 10} 小時`
}

// 點「開始遊戲」：目前還沒有遊戲本體，先跳提示
function play(entry) {
  alert(`啟動 ${entry.game.gameName}（尚未實作）`)
}

// 正在寫評論的遊戲（null 代表評論視窗沒打開）
const reviewGame = ref(null)

// 點「評論」：每次都是寫一則新評論（修改舊評論請到遊戲詳情頁）
function openReview(entry) {
  reviewGame.value = entry.game
}
</script>

<template>
  <!-- 遊戲庫頁面主要內容 -->
  <main class="library">
    <!-- 標題列：標題、遊戲數量、總時數；右邊是搜尋和排序 -->
    <header class="lib-head">
      <div class="title">
        <h1>遊戲庫</h1>
        <span class="stats">
          共 <b>{{ cart.library.length }}</b> 款遊戲 · 總遊玩 <b>{{ playTime(totalMinutes) }}</b>
        </span>
      </div>
      <div v-if="cart.library.length" class="tools">
        <input v-model="keyword" class="search" placeholder="搜尋遊戲庫" />
        <select v-model="sortBy" class="sort">
          <option v-for="(label, key) in sortOptions" :key="key" :value="key">排序：{{ label }}</option>
        </select>
      </div>
    </header>

    <!-- 還沒有遊戲：提示去商店逛逛 -->
    <div v-if="cart.library.length === 0" class="empty">
      <div class="empty-icon">🎮</div>
      <p>你的遊戲庫還是空的</p>
      <RouterLink to="/games/all" class="btn">去商店逛逛</RouterLink>
    </div>

    <!-- 有遊戲但搜尋不到 -->
    <p v-else-if="shown.length === 0" class="none">找不到「{{ keyword }}」</p>

    <!-- v-for：每筆遊戲庫紀錄顯示一張卡片 -->
    <div v-else class="grid">
      <article v-for="entry in shown" :key="entry.libraryId" class="card">
        <!-- 封面：滑鼠移上去放大，中間出現開始遊戲按鈕 -->
        <div class="cover">
          <img v-if="entry.game.coverUrl" :src="entry.game.coverUrl" :alt="entry.game.gameName" />
          <div v-else class="no-cover">{{ entry.game.gameName.charAt(0) }}</div>
          <!-- 取得途徑標籤：禮物用不同顏色 -->
          <span class="badge" :class="{ gift: entry.obtainedSource === 'GIFT' }">
            {{ entry.obtainedSource === 'GIFT' ? '🎁 ' : '' }}{{ sourceLabel[entry.obtainedSource] ?? entry.obtainedSource }}
          </span>
          <button class="play" @click="play(entry)">▶ 開始遊戲</button>
        </div>

        <!-- 文字：名稱、遊玩時數、取得日期 -->
        <div class="info">
          <!-- 點名稱到遊戲詳情頁看所有評論 -->
          <h3 :title="entry.game.gameName">
            <RouterLink :to="`/games/${entry.game.gameId}`">{{ entry.game.gameName }}</RouterLink>
          </h3>
          <div class="meta">
            <span class="time" :class="{ fresh: !entry.totalPlayMinutes }">⏱ {{ playTime(entry.totalPlayMinutes) }}</span>
            <span class="date">{{ entry.obtainedAt?.slice(0, 10) }}</span>
          </div>
          <button class="review-btn" @click="openReview(entry)">✎ 評論</button>
        </div>
      </article>
    </div>

    <!-- 撰寫評論視窗：每次都新增一則 -->
    <ReviewForm v-if="reviewGame" :game="reviewGame" @close="reviewGame = null" />
  </main>
</template>

<!-- 遊戲庫頁面樣式（深藍底 + 金色重點色，和遊戲首頁同色系） -->
<style scoped>
/* ===== 配色：和 GamesHome.vue 一樣，要換顏色改這裡就好 ===== */
.library {
  --bg: #0b0c32;
  --surface: #121a4a;
  --surface-2: #1c2a66;
  --gold: #d4a64a;
  --border: #2e4a9a;
  --text: #e4e8f8;
  --text-muted: #98a2c8;
  --text-dim: #5e6894;

  max-width: 1180px; margin: 0 auto; padding: 16px; min-height: 70vh; color: var(--text);
}

/* ===== 標題列 ===== */
.lib-head {
  display: flex; align-items: flex-end; justify-content: space-between; flex-wrap: wrap; gap: 12px;
  padding-bottom: 14px; margin-bottom: 22px; border-bottom: 1px solid var(--border);
}
.title { display: flex; align-items: baseline; gap: 14px; flex-wrap: wrap; }
.title h1 { margin: 0; font-size: 28px; font-weight: bold; color: var(--gold); letter-spacing: 2px; }
.stats { font-size: 13px; color: var(--text-muted); }
.stats b { color: var(--text); }

/* 搜尋和排序 */
.tools { display: flex; gap: 8px; }
.search, .sort {
  height: 32px; padding: 0 12px; border-radius: 16px; border: 1px solid var(--border);
  background: var(--surface); color: var(--text); font-size: 13px; outline: none;
}
.search { width: 200px; }
.search:focus, .sort:focus { border-color: var(--gold); }
.sort { cursor: pointer; }

/* ===== 卡片格線 ===== */
.grid { display: grid; grid-template-columns: repeat(auto-fill, minmax(220px, 1fr)); gap: 18px; }

.card {
  display: flex; flex-direction: column; border-radius: 8px; overflow: hidden;
  background: linear-gradient(180deg, var(--surface-2), var(--surface));
  border: 1px solid transparent; box-shadow: 0 4px 14px rgba(0, 0, 0, .4);
  transition: transform .2s, border-color .2s, box-shadow .2s;
}
/* 滑鼠移上去：微微浮起、金色外框 */
.card:hover { transform: translateY(-4px); border-color: var(--gold); box-shadow: 0 10px 24px rgba(0, 0, 0, .55); }

/* 封面：固定 16:9 比例 */
.cover { position: relative; aspect-ratio: 16 / 9; overflow: hidden; background: var(--bg); }
.cover img { width: 100%; height: 100%; object-fit: cover; display: block; transition: transform .35s; }
.card:hover .cover img { transform: scale(1.06); }
/* 沒有封面：漸層底 + 遊戲名稱第一個字 */
.no-cover {
  width: 100%; height: 100%; display: flex; align-items: center; justify-content: center;
  font-size: 48px; font-weight: bold; color: var(--gold);
  background: linear-gradient(135deg, #1f3a8a, #3a1f6b);
}

/* 左上角取得途徑標籤 */
.badge {
  position: absolute; top: 8px; left: 8px; padding: 2px 8px; border-radius: 10px;
  font-size: 11px; background: rgba(11, 12, 50, .8); color: var(--text-muted); border: 1px solid var(--border);
}
.badge.gift { color: #f0c060; border-color: var(--gold); }

/* 開始遊戲按鈕：平常隱藏，滑鼠移到卡片上才出現在封面正中間 */
.play {
  position: absolute; left: 50%; top: 50%; transform: translate(-50%, -40%); opacity: 0;
  border: none; border-radius: 4px; padding: 8px 18px; font-size: 14px; font-weight: bold; cursor: pointer;
  color: #1b0f0d; background: linear-gradient(to bottom, #e8be63, #b8892f);
  box-shadow: 0 4px 12px rgba(0, 0, 0, .5); transition: opacity .2s, transform .2s;
}
.card:hover .play { opacity: 1; transform: translate(-50%, -50%); }
.play:hover { background: linear-gradient(to bottom, #f5d27f, #c99a3a); }

/* 卡片文字 */
.info { padding: 10px 12px 12px; }
.info h3 {
  margin: 0 0 6px; font-size: 15px; font-weight: bold; color: var(--text);
  white-space: nowrap; overflow: hidden; text-overflow: ellipsis;
}
.meta { display: flex; justify-content: space-between; font-size: 12px; color: var(--text-muted); }
.info h3 a { color: inherit; }
.info h3 a:hover { color: var(--gold); }
/* 評論按鈕 */
.review-btn {
  width: 100%; margin-top: 10px; padding: 5px 0; border-radius: 4px; font-size: 13px; cursor: pointer;
  border: 1px solid var(--border); background: var(--surface); color: var(--text-muted);
  transition: border-color .2s, color .2s;
}
.review-btn:hover { border-color: var(--gold); color: var(--gold); }
.time { color: #9bd46a; }
.time.fresh { color: var(--text-dim); }
.date { color: var(--text-dim); }

/* ===== 空的遊戲庫 ===== */
.empty {
  display: flex; flex-direction: column; align-items: center; gap: 10px; padding: 70px 16px;
  border: 1px dashed var(--border); border-radius: 10px; color: var(--text-muted);
}
.empty-icon { font-size: 54px; }
.empty p { margin: 0; font-size: 16px; }
.btn {
  margin-top: 6px; padding: 8px 20px; border-radius: 4px; font-weight: bold;
  color: #1b0f0d; background: linear-gradient(to bottom, #e8be63, #b8892f);
}
.btn:hover { background: linear-gradient(to bottom, #f5d27f, #c99a3a); }
.none { text-align: center; color: var(--text-muted); padding: 40px 0; }

/* 手機：搜尋框撐滿，卡片小一點 */
@media (max-width: 700px) {
  .tools { width: 100%; }
  .search { flex: 1; width: auto; }
  .grid { grid-template-columns: repeat(auto-fill, minmax(150px, 1fr)); gap: 12px; }
}
</style>
