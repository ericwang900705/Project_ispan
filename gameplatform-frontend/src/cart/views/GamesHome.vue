<script setup>
// ref：會自動更新畫面的變數；computed：依其他變數自動算出結果
// onMounted / onUnmounted：元件顯示到畫面上 / 離開畫面時要執行的動作
import { ref, computed, onMounted, onUnmounted } from 'vue'
// useRouter：用程式切換網址（搜尋時跳到所有遊戲頁）
import { useRouter } from 'vue-router'
// storeToRefs：把 store 裡的資料拆出來，拆完仍會自動更新畫面
import { storeToRefs } from 'pinia'
// 加入購物車按鈕、Steam 風格價格區塊
import AddToCartButton from '@/cart/components/AddToCartButton.vue'
import PriceBlock from '@/cart/components/PriceBlock.vue'
// 整頁背景：粒子連線（滑鼠靠近會互動）
import ParticleBackground from '@/cart/components/ParticleBackground.vue'
// 共用的購物車 store、遊戲 store
import { useCartStore } from '@/cart/stores/cart'
import { useGamesStore, hasDiscount, discountPercent, getRecentIds } from '@/cart/stores/games'

const router = useRouter()
const cart = useCartStore()
const gamesStore = useGamesStore()
const { games, genres, discountedGames, stats } = storeToRefs(gamesStore)

// ===== 搜尋框 =====
// 搜尋框輸入的文字
const keyword = ref('')
const searchFocused = ref(false)
const selectedSuggestion = ref(-1)
const searchTerm = computed(() => keyword.value.trim().toLocaleLowerCase())
// 只顯示已公開且仍可在前台瀏覽的遊戲，最多顯示 6 筆。
const searchSuggestions = computed(() => {
  if (!searchTerm.value) return []
  return games.value.filter((game) =>
    ['PUBLISHED', 'PENDING_OFF_SHELF', 'ON_SALE'].includes(game.status) &&
    String(game.gameName ?? '').toLocaleLowerCase().includes(searchTerm.value)
  ).slice(0, 6)
})
const showSuggestions = computed(() => searchFocused.value && Boolean(searchTerm.value))
function selectSuggestion(game) {
  searchFocused.value = false
  selectedSuggestion.value = -1
  router.push(`/games/${game.gameId}`)
}
function onSearchKeydown(event) {
  if (event.key === 'Escape') {
    searchFocused.value = false
    selectedSuggestion.value = -1
    event.target.blur()
  } else if (showSuggestions.value && event.key === 'ArrowDown') {
    event.preventDefault()
    selectedSuggestion.value = Math.min(selectedSuggestion.value + 1, searchSuggestions.value.length - 1)
  } else if (showSuggestions.value && event.key === 'ArrowUp') {
    event.preventDefault()
    selectedSuggestion.value = Math.max(selectedSuggestion.value - 1, -1)
  }
}
function submitSearch() {
  const selected = searchSuggestions.value[selectedSuggestion.value]
  if (showSuggestions.value && selected) return selectSuggestion(selected)
  searchFocused.value = false
  selectedSuggestion.value = -1
  search()
}

// 按 Enter 或放大鏡：跳到所有遊戲頁，並把關鍵字放在網址 ?q=
function search() {
  router.push({ path: '/games/all', query: keyword.value ? { q: keyword.value } : {} })
}

// 點遊戲封面 / 名稱：到遊戲詳情頁（介紹 + 玩家評論）
function goDetail(game) {
  router.push(`/games/${game.gameId}`)
}

// ===== 精選與推薦（大輪播） =====
// 輪播的遊戲：有打折的排前面，最多 8 款
const featured = computed(() =>
  [...games.value].sort((a, b) => hasDiscount(b) - hasDiscount(a)).slice(0, 8)
)
// 目前顯示第幾張
const slide = ref(0)
// 目前顯示的那款遊戲
const current = computed(() => featured.value[slide.value])

// 上一張 / 下一張：超過頭尾就繞回來
function prev() {
  slide.value = (slide.value - 1 + featured.value.length) % featured.value.length
}
function next() {
  slide.value = (slide.value + 1) % featured.value.length
}

// 自動輪播：每 5 秒換下一張；滑鼠移到輪播上時暫停
let timer = null
function startAuto() {
  stopAuto()
  timer = setInterval(next, 5000)
}
function stopAuto() {
  clearInterval(timer)
}

// ===== 底部分頁清單 =====
const tabs = [
  { key: 'new', label: '新上架' },
  { key: 'sale', label: '特價優惠' },
  { key: 'all', label: '全部遊戲' },
  { key: 'rating', label: '評價排行' },
  { key: 'upcoming', label: '即將發行' },
  { key: 'best', label: '暢銷商品' },
  { key: 'recent', label: '最近瀏覽' },
]
// 目前選的分頁
const activeTab = ref('new')
// 某款遊戲的統計（還沒抓到就當 0）
function statOf(game) {
  return stats.value[game.gameId] ?? { avgRating: 0, reviewCount: 0, soldCount: 0 }
}
// 最近瀏覽過的 gameId（進入首頁時從 localStorage 讀一次）
const recentIds = ref(getRecentIds())
// 依分頁決定清單內容
const tabGames = computed(() => {
  switch (activeTab.value) {
    // 新上架：遊戲編號由大到小（越晚加入的越新）
    case 'new': return [...games.value].sort((a, b) => b.gameId - a.gameId).slice(0, 10)
    case 'sale': return discountedGames.value
    // 評價排行：只列有評論的，平均分數高的在前，同分比評論數
    case 'rating': return games.value.filter((g) => statOf(g).reviewCount > 0)
      .sort((a, b) => statOf(b).avgRating - statOf(a).avgRating || statOf(b).reviewCount - statOf(a).reviewCount)
      .slice(0, 10)
    // 即將發行：遊戲狀態是 COMING_SOON 的
    case 'upcoming': return games.value.filter((g) => g.status === 'COMING_SOON')
    // 暢銷商品：只列有賣出的，賣最多的在前
    case 'best': return games.value.filter((g) => statOf(g).soldCount > 0)
      .sort((a, b) => statOf(b).soldCount - statOf(a).soldCount)
      .slice(0, 10)
    // 最近瀏覽：照看過的順序
    case 'recent': return recentIds.value.map((id) => games.value.find((g) => g.gameId === id)).filter(Boolean)
    default: return games.value
  }
})
// 清單每一列名稱下面那行小字：依分頁顯示評分 / 銷量，其他分頁顯示分類
function rowSub(game) {
  const s = statOf(game)
  if (activeTab.value === 'rating') return `★ ${s.avgRating.toFixed(1)}（${s.reviewCount} 則評論）`
  if (activeTab.value === 'best') return `已售出 ${s.soldCount} 套`
  if (activeTab.value === 'upcoming') return '即將推出'
  return game.genre || '未分類'
}
// 右側預覽：滑鼠移到哪一列就預覽哪款，沒移過就預覽第一款
const hovered = ref(null)
const preview = computed(() => hovered.value ?? tabGames.value[0])
// 換分頁時清掉預覽，改回預覽新分頁的第一款
function switchTab(key) {
  activeTab.value = key
  hovered.value = null
}

// ===== 依類型瀏覽 =====
// 每個分類方塊輪流用的漸層顏色
const tileColors = [
  ['#1f3a8a', '#4a7fd4'], ['#3a1f6b', '#8a5ad4'], ['#0f4a5e', '#3ab0c0'],
  ['#1a2a6b', '#d4a64a'], ['#4a1f5e', '#c0508a'], ['#12305a', '#5a9ae0'],
]
function tileStyle(i) {
  const [from, to] = tileColors[i % tileColors.length]
  return { background: `linear-gradient(135deg, ${from}, ${to})` }
}
// 某分類有幾款遊戲
function countOf(genre) {
  return games.value.filter((g) => g.genre === genre).length
}

// 進入頁面：抓遊戲、統計、購物車、遊戲庫，開始自動輪播
onMounted(() => {
  gamesStore.fetchGames()
  gamesStore.fetchStats()
  cart.fetchCart()
  cart.fetchLibrary()
  startAuto()
})
// 離開頁面：停止自動輪播，避免計時器一直跑
onUnmounted(stopAuto)
</script>

<template>
  <main class="home">
    <!-- ===== 粒子連線背景（滑鼠靠近會互動） ===== -->
    <ParticleBackground />



    <!-- ===== 搜尋框 ===== -->
    <div class="store-nav">
      <!-- 搜尋框：按 Enter 或放大鏡就搜尋 -->
      <div class="search-container">
        <form class="search" role="search" @submit.prevent="submitSearch">
          <input v-model="keyword" placeholder="搜尋你想玩的遊戲…" autocomplete="off"
            aria-label="搜尋遊戲" :aria-expanded="showSuggestions" aria-controls="store-search-suggestions"
            @focus="searchFocused = true; selectedSuggestion = -1"
            @blur="searchFocused = false" @input="selectedSuggestion = -1"
            @keydown="onSearchKeydown" />
          <button type="submit" aria-label="搜尋遊戲" class="search-submit">
            <svg aria-hidden="true" viewBox="0 0 24 24" width="21" height="21" fill="none" stroke="currentColor" stroke-width="2.2" stroke-linecap="round" stroke-linejoin="round"><circle cx="10.8" cy="10.8" r="6.6"/><path d="m16 16 4.4 4.4"/></svg>
          </button>
        </form>
        <div v-if="showSuggestions" id="store-search-suggestions" class="search-suggestions">
          <div class="search-suggestions-heading"><span>搜尋結果</span><span class="search-match-count">{{ searchSuggestions.length }} 款遊戲</span></div>
          <button v-for="(game, index) in searchSuggestions" :key="game.gameId" type="button"
            class="search-result" :class="{ selected: index === selectedSuggestion }"
            @mousedown.prevent="selectSuggestion(game)">
            <img v-if="game.coverUrl" :src="game.coverUrl" :alt="game.gameName" class="search-cover" />
            <span v-else class="search-cover search-cover-empty">🎮</span>
            <span class="search-result-info">
              <strong>{{ game.gameName }}</strong>
              <small>{{ game.genre || '未分類' }}</small>
            </span>
            <PriceBlock class="search-result-price" :game="game" />
          </button>
          <div v-if="!searchSuggestions.length" class="search-no-results">找不到符合的遊戲</div>
          <button v-else type="button" class="search-all" @mousedown.prevent="searchFocused = false; search()">
            查看全部搜尋結果
          </button>
        </div>
      </div>
    </div>

    <div class="layout">
      <!-- ===== 主要內容 ===== -->
      <div class="content">
        <!-- 還沒抓到資料時顯示 -->
        <p v-if="!games.length" class="empty">載入中…</p>

        <template v-else>
          <!-- ===== 精選與推薦 ===== -->
          <section>
            <div class="head">
              <h2>精選與推薦</h2>
            </div>
            <div class="carousel" @mouseenter="stopAuto" @mouseleave="startAuto">
              <button class="arrow left" aria-label="上一個" @click="prev">‹</button>
              <!-- 大封面 + 右側資訊 -->
              <div v-if="current" class="main-cap">
                <img :src="current.coverUrl" :alt="current.gameName" class="main-img link" @click="goDetail(current)" />
                <div class="info">
                  <div class="title link" @click="goDetail(current)">{{ current.gameName }}</div>
                  <p class="desc">{{ current.description }}</p>
                  <div class="tags">
                    <span v-if="current.genre" class="tag">{{ current.genre }}</span>
                    <span v-if="hasDiscount(current)" class="tag hot">特價中</span>
                  </div>
                  <div class="reason">現已推出</div>
                  <div class="buy-row">
                    <AddToCartButton :status="current.status" :game-id="current.gameId" class="cart-btn" />
                    <PriceBlock :game="current" />
                  </div>
                </div>
              </div>
              <button class="arrow right" aria-label="下一個" @click="next">›</button>
            </div>
            <!-- 下方小圓點：顯示目前第幾張，點了直接跳過去 -->
            <div class="dots">
              <span v-for="(g, i) in featured" :key="g.gameId" :class="{ on: i === slide }" @click="slide = i"></span>
            </div>
          </section>

          <!-- ===== 特別優惠 ===== -->
          <section v-if="discountedGames.length" id="specials">
            <div class="head">
              <h2>特別優惠</h2>
              <RouterLink to="/games/all" class="more">瀏覽更多</RouterLink>
            </div>
            <div class="specials">
              <div v-for="game in discountedGames.slice(0, 4)" :key="game.gameId" class="special link"
                @click="goDetail(game)">
                <img :src="game.coverUrl" :alt="game.gameName" />
                <div class="special-body">
                  <div class="special-label">限時特賣</div>
                  <div class="special-name">{{ game.gameName }}</div>
                  <div class="special-off">省下 {{ discountPercent(game) }}%</div>
                  <PriceBlock :game="game" />
                </div>
              </div>
            </div>
          </section>

          <!-- ===== 依類型瀏覽 ===== -->
          <section v-if="genres.length" id="genres">
            <div class="head">
              <h2>依類型瀏覽</h2>
            </div>
            <div class="tiles">
              <RouterLink v-for="(genre, i) in genres" :key="genre" :to="{ path: '/games/all', query: { genre } }"
                class="tile" :style="tileStyle(i)">
                <span class="tile-name">{{ genre }}</span>
                <span class="tile-count">{{ countOf(genre) }} 款遊戲</span>
              </RouterLink>
            </div>
          </section>

          <!-- ===== 分頁清單 + 右側預覽 ===== -->
          <section id="tabs">
            <div class="tab-bar">
              <button v-for="tab in tabs" :key="tab.key" :class="{ active: activeTab === tab.key }"
                @click="switchTab(tab.key)">{{ tab.label }}</button>
            </div>
            <div class="tab-body">
              <!-- 左邊清單：滑鼠移到哪一列，右邊就預覽哪款 -->
              <div class="rows">
                <p v-if="!tabGames.length" class="empty">目前沒有遊戲</p>
                <div v-for="game in tabGames" :key="game.gameId" class="row"
                  :class="{ hover: preview && preview.gameId === game.gameId }" @mouseenter="hovered = game"
                  @click="goDetail(game)">
                  <img :src="game.coverUrl" :alt="game.gameName" />
                  <div class="row-text">
                    <div class="row-name">{{ game.gameName }}</div>
                    <div class="row-genre">{{ rowSub(game) }}</div>
                  </div>
                  <PriceBlock :game="game" />
                </div>
              </div>
              <!-- 右邊預覽 -->
              <div v-if="preview" class="preview">
                <div class="preview-name link" @click="goDetail(preview)">{{ preview.gameName }}</div>
                <div class="tags">
                  <span v-if="preview.genre" class="tag">{{ preview.genre }}</span>
                </div>
                <img :src="preview.coverUrl" :alt="preview.gameName" />
                <p>{{ preview.description }}</p>
                <AddToCartButton :status="preview.status" :game-id="preview.gameId" class="cart-btn" />
              </div>
            </div>
          </section>
        </template>
      </div>
    </div>
  </main>
</template>

<!-- 遊戲首頁樣式（深藍底 + 金色重點色，和網站底色同色系） -->
<style scoped>
/* ===== 配色：要換顏色改這裡就好，下面全部都用這些變數 ===== */
.home {
  --bg: #0b0c32;
  /* 整頁底色（和網站底色同一個深藍） */
  --bg-glow: #1f3a8a;
  /* 整頁上方的藍色光暈 */
  --surface: #121a4a;
  /* 卡片、區塊底色 */
  --surface-2: #1c2a66;
  /* 比較亮的區塊底色 */
  --gold: #d4a64a;
  /* 主要重點色（金色） */
  --gold-dark: #2e4a9a;
  /* 邊框色（藍） */
  --sale: #b23a2a;
  /* 特價紅 */
  --text: #e4e8f8;
  /* 一般文字 */
  --text-muted: #98a2c8;
  /* 次要文字 */
  --text-dim: #5e6894;
  /* 最淡的文字 */

  min-height: 100vh;
  padding: 16px;
  color: var(--text);
}

/* 共用：主要內容最寬 1180px 置中 */
.store-nav,
.layout {
  max-width: 1180px;
  margin: 0 auto;
}

/* ===== 商店導覽列 ===== */
.store-nav {
  display: flex;
  align-items: center;
  height: 40px;
}

/* 搜尋框：靠右、圓角 */
.search {
  margin-left: auto;
  display: flex;
  height: 28px;
}

.search input {
  width: 200px;
  border: 1px solid var(--gold-dark);
  padding: 0 12px;
  background: var(--bg);
  color: var(--text);
  font-size: 13px;
  border-radius: 14px 0 0 14px;
  outline: none;
}

.search input::placeholder {
  color: var(--text-dim);
}

.search input:focus {
  border-color: var(--gold);
}

.search button {
  border: none;
  background: var(--gold);
  width: 34px;
  cursor: pointer;
  border-radius: 0 14px 14px 0;
}

/* ===== 主要內容 ===== */
.layout {
  display: flex;
  gap: 24px;
  margin-top: 24px;
}

.content {
  flex: 1;
  min-width: 0;
}

.empty {
  color: var(--text-muted);
  padding: 24px 0;
}

/* 區塊標題：金色左邊線，右邊「瀏覽更多」按鈕 */
section {
  margin-bottom: 36px;
}

.head {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10px;
}

.head h2 {
  margin: 0;
  font-size: 18px;
  color: var(--text);
  letter-spacing: 2px;
  padding-left: 10px;
  border-left: 4px solid var(--gold);
}

.more {
  font-size: 12px;
  color: var(--gold);
  border: 1px solid var(--gold);
  border-radius: 12px;
  padding: 2px 12px;
}

.more:hover {
  background: var(--gold);
  color: var(--bg);
}

/* ===== 精選大輪播 ===== */
.carousel {
  position: relative;
}

.main-cap {
  display: flex;
  height: 353px;
  border-radius: 6px;
  overflow: hidden;
  border: 1px solid var(--gold-dark);
  box-shadow: 0 8px 24px rgba(0, 0, 0, .6);
}

.main-img {
  width: 66%;
  height: 100%;
  object-fit: cover;
  display: block;
}

/* 右側資訊欄 */
.info {
  flex: 1;
  padding: 16px 18px;
  display: flex;
  flex-direction: column;
  background: linear-gradient(180deg, var(--surface-2), var(--surface));
}

.link {
  cursor: pointer;
}

.title {
  font-size: 24px;
  color: var(--gold);
  line-height: 1.2;
  margin-bottom: 10px;
  font-weight: bold;
}

.desc {
  font-size: 13px;
  line-height: 1.6;
  color: var(--text-muted);
  flex: 1;
  overflow: hidden;
}

.tags {
  display: flex;
  flex-wrap: wrap;
  gap: 4px;
  margin: 8px 0;
}

.tag {
  background: var(--bg);
  color: var(--text);
  border: 1px solid var(--gold-dark);
  font-size: 11px;
  padding: 2px 8px;
  border-radius: 10px;
}

.tag.hot {
  background: var(--sale);
  border-color: var(--sale);
  color: #fff1e0;
}

.reason {
  font-size: 12px;
  color: var(--text-muted);
  margin-bottom: 8px;
}

.buy-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 8px;
}

/* 左右箭頭：貼在輪播左右兩側的圓形按鈕 */
.arrow {
  position: absolute;
  top: 50%;
  transform: translateY(-50%);
  width: 40px;
  height: 40px;
  z-index: 2;
  border-radius: 50%;
  border: 1px solid var(--gold);
  font-size: 26px;
  line-height: 1;
  color: var(--gold);
  cursor: pointer;
  background: rgba(11, 12, 50, .75);
}

.arrow:hover {
  background: var(--gold);
  color: var(--bg);
}

.arrow.left {
  left: 10px;
}

.arrow.right {
  right: 10px;
}

/* 小圓點：目前那張拉長成金色膠囊 */
.dots {
  display: flex;
  justify-content: center;
  gap: 6px;
  margin-top: 12px;
}

.dots span {
  width: 8px;
  height: 8px;
  border-radius: 4px;
  background: var(--surface-2);
  cursor: pointer;
  transition: width .2s;
}

.dots span.on {
  width: 22px;
  background: var(--gold);
}

/* 加入購物車按鈕：金色（:deep 讓樣式套到子元件的 button 上） */
:deep(.cart-btn) {
  border: none;
  border-radius: 4px;
  padding: 6px 14px;
  font-size: 13px;
  font-weight: bold;
  color: #1b0f0d;
  cursor: pointer;
  background: linear-gradient(to bottom, #e8be63, #b8892f);
}

:deep(.cart-btn:hover:not(:disabled)) {
  background: linear-gradient(to bottom, #f5d27f, #c99a3a);
}

/* 已擁有 / 已在購物車時按鈕變灰 */
:deep(.cart-btn:disabled) {
  background: #2a3460;
  color: #98a2c8;
  cursor: default;
}

/* ===== 特別優惠 ===== */
.specials {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 14px;
}

.special {
  background: var(--surface);
  border: 1px solid var(--surface-2);
  border-radius: 6px;
  overflow: hidden;
  transition: transform .2s, border-color .2s;
}

.special:hover {
  transform: translateY(-3px);
  border-color: var(--gold);
}

.special img {
  width: 100%;
  aspect-ratio: 460 / 215;
  object-fit: cover;
  display: block;
}

.special-body {
  padding: 10px;
}

.special-label {
  font-size: 11px;
  color: #ff8a6a;
  letter-spacing: 1px;
}

.special-name {
  color: var(--text);
  font-size: 14px;
  margin: 4px 0;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.special-off {
  font-size: 12px;
  color: var(--gold);
  margin-bottom: 8px;
}

/* ===== 依類型瀏覽 ===== */
.tiles {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(170px, 1fr));
  gap: 12px;
}

.tile {
  height: 110px;
  border-radius: 6px;
  padding: 12px;
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
  color: #fff;
  transition: transform .2s, box-shadow .2s;
}

.tile:hover {
  transform: scale(1.04);
  box-shadow: 0 6px 16px rgba(0, 0, 0, .5);
  background-color: transparent;
}

.tile-name {
  font-size: 20px;
  font-weight: bold;
  text-shadow: 0 2px 4px rgba(0, 0, 0, .5);
}

.tile-count {
  font-size: 12px;
  opacity: .85;
}

/* ===== 分頁清單：選中的分頁底下一條金線 ===== */
.tab-bar {
  display: flex;
  gap: 4px;
}

.tab-bar button {
  border: none;
  padding: 8px 16px;
  font-size: 14px;
  cursor: pointer;
  color: var(--text-muted);
  background: transparent;
  border-bottom: 2px solid transparent;
}

.tab-bar button:hover {
  color: var(--text);
}

.tab-bar button.active {
  color: var(--gold);
  border-bottom-color: var(--gold);
}

.tab-body {
  display: flex;
  gap: 10px;
  background: var(--surface);
  padding: 10px;
  border-radius: 0 0 6px 6px;
  border-top: 1px solid var(--surface-2);
}

.rows {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.row {
  display: flex;
  align-items: center;
  gap: 10px;
  background: var(--bg);
  padding-right: 8px;
  cursor: pointer;
  border-radius: 4px;
  overflow: hidden;
  border-left: 3px solid transparent;
}

.row.hover {
  background: var(--surface-2);
  border-left-color: var(--gold);
}

.row.hover .row-name {
  color: var(--gold);
}

.row img {
  width: 184px;
  height: 69px;
  object-fit: cover;
  display: block;
  flex-shrink: 0;
}

.row-text {
  flex: 1;
  min-width: 0;
}

.row-name {
  color: var(--text);
  font-size: 15px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.row-genre {
  color: var(--text-dim);
  font-size: 12px;
}

/* 右側預覽 */
.preview {
  width: 300px;
  flex-shrink: 0;
  padding: 12px;
  background: var(--surface-2);
  border-radius: 4px;
}

.preview-name {
  font-size: 20px;
  color: var(--gold);
  line-height: 1.2;
  font-weight: bold;
}

.preview img {
  width: 100%;
  aspect-ratio: 460 / 215;
  object-fit: cover;
  display: block;
  margin-bottom: 8px;
  border-radius: 4px;
}

.preview p {
  font-size: 13px;
  color: var(--text-muted);
}

/* ===== 手機 / 小螢幕：輪播和預覽改成上下排 ===== */
@media (max-width: 900px) {

  .preview,
  .arrow {
    display: none;
  }

  .main-cap {
    flex-direction: column;
    height: auto;
  }

  .main-img {
    width: 100%;
    aspect-ratio: 16 / 9;
  }

  .search input {
    width: 110px;
  }

  .row img {
    width: 120px;
    height: 45px;
  }
}

/* 商店搜尋視覺整理：限定首頁搜尋欄位，避免影響其他元件 */
.store-nav { position: relative; z-index: 35; }
.search-container { position: relative; margin-left: auto; width: min(420px, 100%); }
.search-container .search {
  margin: 0; width: 100%; height: 50px; display: flex; align-items: center;
  padding: 4px; box-sizing: border-box; overflow: hidden;
  border: 1px solid rgba(219, 177, 89, .55); border-radius: 16px;
  background: linear-gradient(135deg, rgba(16, 26, 63, .98), rgba(30, 45, 91, .98));
  box-shadow: 0 5px 18px rgba(1, 5, 27, .27), inset 0 1px 0 rgba(255,255,255,.05);
  transition: border-color .2s ease, box-shadow .2s ease, transform .2s ease;
}
.search-container .search:hover { border-color: rgba(239, 192, 106, .85); }
.search-container .search:focus-within {
  border-color: #f4c86e;
  box-shadow: 0 0 0 3px rgba(231,185,92,.15), 0 10px 26px rgba(0,0,0,.32);
}
.search-container .search input {
  width: 100%; min-width: 0; flex: 1; height: 100%; border: 0; border-radius: 0;
  background: transparent; box-shadow: none; outline: none;
  padding: 0 13px 0 16px; font-size: 15px; letter-spacing: .2px;
  color: #f5f7ff; line-height: 1.4;
}
.search-container .search input:focus { outline: none; border: 0; }
.search-container .search input::placeholder { color: #a5b3d2; opacity: 1; }
.search-container .search .search-submit {
  flex: 0 0 44px; width: 44px; height: 40px; min-height: 40px; border: 0;
  border-radius: 12px; display: grid; place-items: center; color: #20243b;
  background: linear-gradient(140deg, #f6d68d 0%, #d6a94e 100%);
  box-shadow: 0 2px 8px rgba(0,0,0,.2); cursor: pointer;
  transition: filter .2s ease, transform .2s ease;
}
.search-container .search .search-submit:hover { filter: brightness(1.08); transform: translateY(-1px); }
.search-container .search .search-submit:active { transform: translateY(0); }
.search-container .search .search-submit:focus-visible { outline: 2px solid #fff; outline-offset: -3px; }
.search-suggestions {
  position: absolute; z-index: 65; top: calc(100% + 12px); right: 0;
  width: min(500px, 92vw); box-sizing: border-box; overflow: hidden;
  border: 1px solid rgba(139,163,210,.5); border-radius: 17px;
  background: linear-gradient(160deg, #182653 0%, #101939 100%);
  box-shadow: 0 22px 55px rgba(0, 4, 29, .55), 0 3px 12px rgba(0, 4, 29, .2);
  padding: 9px;
}
.search-suggestions-heading {
  display: flex; justify-content: space-between; align-items: center; gap: 12px;
  padding: 9px 12px 11px; color: #f5f7ff; font-weight: 700; font-size: 13px;
  letter-spacing: .5px;
}
.search-match-count { color: #aebddc; font-size: 12px; font-weight: 500; letter-spacing: 0; }
.search-suggestions .search-result {
  width: 100%; height: auto; min-height: 76px; display: flex; align-items: center;
  padding: 10px 12px; gap: 13px; text-align: left;
  border: 1px solid transparent; border-radius: 11px;
  background: transparent; color: #f1f4ff; cursor: pointer;
  transition: background .15s ease, border-color .15s ease;
}
.search-suggestions .search-result:hover,
.search-suggestions .search-result.selected,
.search-suggestions .search-result:focus-visible {
  background: rgba(56, 83, 144, .55); border-color: rgba(130, 165, 228, .24); outline: none;
}
.search-cover {
  flex-shrink: 0; width: 76px; height: 54px; object-fit: cover;
  background: #2a3762; border: 1px solid rgba(255,255,255,.1); border-radius: 9px;
}
.search-cover-empty { display: grid; place-items: center; font-size: 24px; }
.search-result-info { display: flex; flex: 1; min-width: 0; flex-direction: column; gap: 5px; }
.search-result-info strong { color: #f7f8ff; font-size: 14px; line-height: 1.4; overflow-wrap: anywhere; }
.search-result-info small { color: #b6c6e7; font-size: 12px; }
.search-result-price { color: #ffdc94; white-space: nowrap; font-size: 13px; font-weight: 750; }
.search-no-results { padding: 22px 12px; color: #c1cbe3; text-align: center; font-size: 14px; }
.search-suggestions .search-all {
  display: block; width: 100%; height: auto; min-height: 42px; padding: 11px 14px;
  margin-top: 5px; border: 0; border-top: 1px solid rgba(157, 174, 216, .22);
  border-radius: 8px; background: transparent; color: #f4c96d; font-weight: 650;
  font-size: 13px; cursor: pointer; text-align: center;
}
.search-suggestions .search-all:hover,
.search-suggestions .search-all:focus-visible { background: rgba(58,78,126,.5); outline: none; }
@media (max-width: 600px) {
  .search-container { width: min(100%, 360px); }
  .search-container .search { height: 46px; }
  .search-container .search input { font-size: 14px; padding-left: 12px; }
  .search-container .search .search-submit { height: 36px; min-height: 36px; flex-basis: 40px; width: 40px; }
  .search-suggestions { width: min(440px, calc(100vw - 28px)); }
  .search-suggestions .search-result { gap: 10px; padding: 9px; }
  .search-cover { width: 58px; height: 44px; }
  .search-result-price { font-size: 12px; }
}
</style>

