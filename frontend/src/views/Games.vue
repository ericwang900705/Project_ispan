<script setup>
// computed：依其他變數自動算出結果；onMounted：元件顯示到畫面上之後要執行的動作
import { computed, onMounted } from 'vue'
// useRoute：讀取目前網址；useRouter：切換網址
import { useRoute, useRouter } from 'vue-router'
// storeToRefs：把 store 裡的資料拆出來，拆完仍會自動更新畫面
import { storeToRefs } from 'pinia'
// 加入購物車按鈕元件
import AddToCartButton from '@/components/AddToCartButton.vue'
// 共用的購物車 store
import { useCartStore } from '@/stores/cart'
// 共用的遊戲 store 和價格小工具
import { useGamesStore, hasDiscount, discountPercent } from '@/stores/games'

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
      <button :class="{ active: selectedGenre === '' }" @click="selectGenre('')">全部</button>
      <button v-for="genre in genres" :key="genre" :class="{ active: selectedGenre === genre }"
        @click="selectGenre(genre)">{{ genre }}</button>
    </div>
    <!-- 金色外框區塊，裡面放遊戲卡片 -->
    <div class="panel">
      <!-- v-for：每款遊戲顯示一張卡片；:key 用遊戲 ID 讓 Vue 辨識每張卡片 -->
      <div v-for="game in filteredGames" :key="game.gameId" class="capsule">
        <!-- 遊戲封面（資料庫 game.cover_url 欄位存的網址） -->
        <img :src="game.coverUrl" :alt="game.gameName" />
        <!-- 分類標籤：有填分類才顯示，固定在封面右上角 -->
        <span v-if="game.genre" class="genre-tag">{{ game.genre }}</span>
        <!-- 滑鼠移上去才顯示的遊戲名稱，蓋在封面上方 -->
        <div class="name">{{ game.gameName }}</div>
        <!-- 底部列：左邊加入購物車按鈕，右邊價格條 -->
        <div class="bottom">
          <!-- 加入購物車按鈕，傳入遊戲 ID -->
          <AddToCartButton :game-id="game.gameId" class="cart-btn" />
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
/* 整頁深色背景 */
.store { background: #1b0f0d; min-height: 100vh; padding: 32px 16px; color: #fff; }
/* 標題和外框都置中，最寬 1200px */
.section-head, .panel { max-width: 1200px; margin: 0 auto; }
/* 主標題：白色粗體、字距拉開 */
.section-head h2 { margin: 0; font-size: 22px; letter-spacing: 2px; }
/* 副標題：灰色 */
.section-head span { color: #b8b6b4; font-size: 16px; }
/* 分類按鈕列：置中、最寬 1200px，按鈕之間留空隙，太多時自動換行 */
.genre-bar { max-width: 1200px; margin: 12px auto 0; display: flex; flex-wrap: wrap; gap: 8px; }
/* 分類按鈕：深色底、灰字 */
.genre-bar button {
  border: 1px solid #6b4e0f; background: #2a1a16; color: #b8b6b4;
  padding: 4px 14px; font-size: 14px; border-radius: 2px; cursor: pointer;
}
/* 滑鼠移上去或選中時：金色底、白字 */
.genre-bar button:hover, .genre-bar button.active { background: #6b4e0f; color: #fff; }
/* 分類標籤：封面右上角的小標籤 */
.genre-tag {
  position: absolute; top: 6px; right: 6px; padding: 2px 8px; font-size: 12px;
  background: rgba(0, 0, 0, .7); color: #fff; border-radius: 2px;
}
/* 金色外框：卡片用格線排列，每張至少 260px 寬，依畫面寬度自動決定一列放幾張 */
.panel {
  margin: 12px auto 0; padding: 18px; background: #6b4e0f; border-radius: 3px;
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
/* 底部列：按鈕靠左、價格靠右 */
.bottom { display: flex; justify-content: space-between; background: rgba(0, 0, 0, .3); }
/* 加入購物車按鈕：金色漸層（:deep 讓樣式套到子元件的 button 上） */
.bottom :deep(.cart-btn) {
  border: none; padding: 0 10px; font-size: 12px; font-weight: bold; color: #1b0f0d; cursor: pointer;
  background: linear-gradient(to bottom, #e8be63, #b8892f);
}
/* 已擁有 / 已在購物車時按鈕變灰 */
.bottom :deep(.cart-btn:disabled) { background: #4a3a32; color: #a8988a; cursor: default; }
/* 價格條：折扣% 和價格並排 */
.price-bar { display: flex; font-weight: bold; }
/* 折扣%：紅底、米白字 */
.discount { background: #b23a2a; color: #fff1e0; padding: 4px 6px; font-size: 15px; }
/* 價格：深棕底 */
.prices { background: #2a1a14; padding: 4px 8px; font-size: 14px; display: flex; align-items: center; gap: 6px; }
/* 刪除線原價：較小、灰褐色 */
.prices s { color: #8a7a6e; font-size: 11px; font-weight: normal; }
</style>
