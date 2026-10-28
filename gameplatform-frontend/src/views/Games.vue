<script setup>
// computed：依其他變數自動算出結果；onMounted：元件顯示到畫面上之後要執行的動作
import { computed, onMounted } from 'vue'
// useRoute：讀取目前網址；useRouter：切換網址
import { useRoute, useRouter } from 'vue-router'
// storeToRefs：把 store 裡的資料拆出來，拆完仍會自動更新畫面
import { storeToRefs } from 'pinia'
// 加入購物車按鈕元件
import AddToCartButton from '@/cart/components/AddToCartButton.vue'
// 共用的購物車 store
import { useCartStore } from '@/cart/stores/cart'
// 共用的遊戲 store 和價格小工具
import { useGamesStore, hasDiscount, discountPercent } from '@/cart/stores/games'
import { genreIcon } from '@/cart/utils/genreIcons'

const route = useRoute()
const router = useRouter()
// 遊戲清單和分類清單都放在遊戲 store
const gamesStore = useGamesStore()
const { games, genres } = storeToRefs(gamesStore)
// 取得購物車 store（按鈕要用它判斷遊戲是否已在購物車 / 已擁有）
const cart = useCartStore()

// 目前選的分類放在網址 ?genre=射擊，這樣下拉選單和首頁的分類連結都能直接帶過來；沒有代表全部
const selectedGenre = computed(() => route.query.genre ?? '')
// 搜尋關鍵字放在網址 ?q=關鍵字（遊戲首頁的搜尋框會帶過來）
const keyword = computed(() => route.query.q ?? '')

// 點分類按鈕：改網址的 genre，畫面會跟著網址重新過濾
function selectGenre(genre) {
  router.push({ query: { ...route.query, genre: genre || undefined } })
}

// 依選的分類和搜尋關鍵字過濾後要顯示的遊戲
const filteredGames = computed(() =>
  games.value
    .filter((g) => !selectedGenre.value || g.genre === selectedGenre.value)
    .filter((g) => g.gameName.toLowerCase().includes(keyword.value.toLowerCase()))
)

// 進入頁面時抓遊戲清單，也抓購物車和遊戲庫（按鈕才知道顯示哪種狀態）
onMounted(() => {
  gamesStore.fetchGames()
  cart.fetchCart()
  cart.fetchLibrary()
})
</script>

<template>
  <!-- 遊戲頁面主要內容（Steam 風格深色背景） -->
  <main class="store">
    <!-- 區塊標題：大字主標題 + 灰色副標題 -->
    <div class="section-head">
      <h2>所有 遊戲</h2>
      <!-- 有搜尋時顯示搜尋的關鍵字，沒有就顯示副標題 -->
      <span v-if="keyword">搜尋「{{ keyword }}」：{{ filteredGames.length }} 筆結果</span>
      <span v-else>精選遊戲</span>
    </div>
    <!-- 分類按鈕：全部 + 資料庫裡出現過的每種分類，選中的會亮起來 -->
    <div class="genre-bar">
      <button :class="{ active: selectedGenre === '' }" @click="selectGenre('')"><span class="genre-icon" aria-hidden="true">🎮</span><span>全部</span></button>
      <button v-for="genre in genres" :key="genre" :class="{ active: selectedGenre === genre }"
        @click="selectGenre(genre)"><span class="genre-icon" aria-hidden="true">{{ genreIcon(genre) }}</span><span>{{ genre }}</span></button>
    </div>
    <!-- 金色外框區塊，裡面放遊戲卡片 -->
    <div class="panel">
      <!-- v-for：每款遊戲顯示一張卡片；:key 用遊戲 ID 讓 Vue 辨識每張卡片 -->
      <div v-for="game in filteredGames" :key="game.gameId" class="capsule">
        <!-- 遊戲封面（資料庫 game.cover_url 欄位存的網址） -->
        <!-- 點封面到遊戲詳情頁（介紹 + 玩家評論） -->
        <img :src="game.coverUrl" :alt="game.gameName" class="link" @click="router.push(`/games/${game.gameId}`)" />
        <!-- 分類標籤：有填分類才顯示，固定在封面右上角 -->
        <span v-if="game.genre" class="genre-tag">{{ game.genre }}</span>
        <!-- 滑鼠移上去才顯示的遊戲名稱，蓋在封面上方 -->
        <div class="name link" @click="router.push(`/games/${game.gameId}`)">{{ game.gameName }}</div>
        <!-- 底部列：左邊加入購物車按鈕，右邊價格條 -->
        <div class="bottom">
          <!-- 加入購物車按鈕，傳入遊戲 ID -->
          <AddToCartButton :status="game.status" :game-id="game.gameId" class="cart-btn" />
          <div class="price-bar">
            <!-- 折扣%：有打折才顯示 -->
            <span v-if="hasDiscount(game)" class="discount">-{{ discountPercent(game) }}%</span>
            <span class="prices">
              <!-- 刪除線原價：有打折才顯示 -->
              <s v-if="hasDiscount(game)">NT$ {{ game.originalPrice }}</s>
              NT$ {{ game.price }}
            </span>
          </div>
        </div>
      </div>
    </div>
  </main>
</template>

<!-- 遊戲頁面樣式（scoped：只作用於此元件） -->
<style scoped>
/* ===== 配色：和首頁一樣的深藍 + 金色，要換顏色改這裡就好 ===== */
/* 整頁：不設底色，讓 App.vue 的粒子連線背景透出來 */
.store {
  --surface: #121a4a;     /* 按鈕、價格的底色 */
  --surface-2: #1c2a66;   /* 比較亮的底色（滑鼠移上去、選中） */
  --panel: rgba(18, 26, 74, .6); /* 包住所有卡片的大框：半透明，背後的粒子隱約看得到 */
  --border: #2e4a9a;      /* 邊框（藍） */
  --gold: #d4a64a;        /* 重點色（金色） */
  --text: #e4e8f8;        /* 一般文字 */
  --text-muted: #98a2c8;  /* 次要文字 */
  --text-dim: #5e6894;    /* 最淡的文字（刪除線原價） */

  min-height: 100vh; padding: 32px 16px; color: var(--text);
}
/* 標題和外框都置中，最寬 1200px */
.section-head, .panel { max-width: 1200px; margin: 0 auto; }
/* 主標題：粗體、字距拉開 */
.section-head h2 { margin: 0; font-size: 22px; letter-spacing: 2px; }
/* 副標題：灰色 */
.section-head span { color: var(--text-muted); font-size: 16px; }
/* 分類按鈕列：置中、最寬 1200px，按鈕之間留空隙，太多時自動換行 */
.genre-bar { max-width: 1200px; margin: 12px auto 0; display: flex; flex-wrap: wrap; gap: 8px; }
/* 分類按鈕：深藍底、灰字 */
.genre-bar button {
  border: 1px solid var(--border); background: var(--surface); color: var(--text-muted);
  padding: 4px 14px; font-size: 14px; border-radius: 2px; cursor: pointer;
}
/* 滑鼠移上去或選中時：亮一點的藍底、白字；選中的加金色邊框 */
.genre-bar button:hover, .genre-bar button.active { background: var(--surface-2); color: var(--text); }
.genre-bar button.active { border-color: var(--gold); }
/* 排序：margin-left: auto 把它推到這一列最右邊（換行時就在新的一行靠右） */
.sort { margin-left: auto; display: flex; align-items: center; gap: 8px; font-size: 14px; color: var(--text-muted); }
.sort select {
  border: 1px solid var(--border); background: var(--surface); color: var(--text);
  padding: 4px 10px; font-size: 14px; border-radius: 2px; cursor: pointer;
}
.sort select:focus { outline: 2px solid var(--gold); outline-offset: 1px; }
/* 分類標籤：封面右上角的小標籤 */
.genre-tag {
  position: absolute; top: 6px; right: 6px; padding: 2px 8px; font-size: 12px;
  background: rgba(0, 0, 0, .7); color: #fff; border-radius: 2px;
}
/* 大框：卡片用格線排列，每張至少 260px 寬，依畫面寬度自動決定一列放幾張 */
.panel {
  margin: 12px auto 0; padding: 18px; background: var(--panel); border: 1px solid var(--border); border-radius: 3px;
  display: grid; grid-template-columns: repeat(auto-fill, minmax(260px, 1fr)); gap: 16px;
}
/* 單張卡片：滑鼠移上去稍微放大並加陰影 */
.capsule { position: relative; transition: transform .2s, box-shadow .2s; }
.capsule:hover { transform: scale(1.03); box-shadow: 0 6px 18px rgba(0, 0, 0, .6); }
/* 封面圖：Steam 橫幅比例 460:215，object-fit: cover 讓圖片裁切填滿不變形 */
.capsule img { display: block; width: 100%; aspect-ratio: 460 / 215; object-fit: cover; }
/* 遊戲名稱：平常透明看不到，滑鼠移上去才淡入 */
.name {
  position: absolute; left: 0; right: 0; top: 0; padding: 6px 8px; font-size: 14px;
  background: linear-gradient(rgba(0, 0, 0, .8), transparent); opacity: 0; transition: opacity .2s;
}
.capsule:hover .name { opacity: 1; }
/* 可以點進遊戲詳情頁的地方 */
.link { cursor: pointer; }
/* 底部列：按鈕靠左、價格靠右 */
.bottom { display: flex; justify-content: space-between; background: rgba(0, 0, 0, .3); }
/* 加入購物車按鈕：金色漸層（:deep 讓樣式套到子元件的 button 上） */
.bottom :deep(.cart-btn) {
  border: none; padding: 0 10px; font-size: 12px; font-weight: bold; color: #1b0f0d; cursor: pointer;
  background: linear-gradient(to bottom, #e8be63, #b8892f);
}
/* 已擁有 / 已在購物車時按鈕變灰 */
.bottom :deep(.cart-btn:disabled) { background: #2a3460; color: var(--text-muted); cursor: default; }
/* 價格條：折扣% 和價格並排 */
.price-bar { display: flex; font-weight: bold; }
/* 折扣%：紅底、米白字 */
.discount { background: #b23a2a; color: #fff1e0; padding: 4px 6px; font-size: 15px; }
/* 價格：深藍底 */
.prices { background: var(--surface); padding: 4px 8px; font-size: 14px; display: flex; align-items: center; gap: 6px; }
/* 刪除線原價：較小、淡灰藍色 */
.prices s { color: var(--text-dim); font-size: 11px; font-weight: normal; }
</style>

<style scoped>
/* 類型選單：上方圖示，下方名稱，保持深藍色主題 */
.genre-bar { gap: 12px; }
.genre-bar button { display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 6px; min-width: 86px; min-height: 78px; padding: 10px 12px; line-height: 1.25; border-radius: 8px; white-space: normal; }
.genre-icon { display: block; font-size: 27px; line-height: 1.2; }
@media (max-width: 600px) { .genre-bar { gap: 8px; } .genre-bar button { min-width: 76px; min-height: 72px; padding: 8px; font-size: 12px; } }
</style>
