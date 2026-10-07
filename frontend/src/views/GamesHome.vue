<script setup>
// ref：會自動更新畫面的變數；computed：依其他變數自動算出結果
// onMounted / onUnmounted：元件顯示到畫面上 / 離開畫面時要執行的動作
import { ref, computed, onMounted, onUnmounted } from 'vue'
// useRouter：用程式切換網址（搜尋時跳到所有遊戲頁）
import { useRouter } from 'vue-router'
// storeToRefs：把 store 裡的資料拆出來，拆完仍會自動更新畫面
import { storeToRefs } from 'pinia'
// 加入購物車按鈕、Steam 風格價格區塊
import AddToCartButton from '@/components/AddToCartButton.vue'
import PriceBlock from '@/components/PriceBlock.vue'
// 共用的購物車 store、遊戲 store
import { useCartStore } from '@/stores/cart'
import { useGamesStore, hasDiscount, discountPercent } from '@/stores/games'

const router = useRouter()
const cart = useCartStore()
const gamesStore = useGamesStore()
const { games, genres, discountedGames } = storeToRefs(gamesStore)

// ===== 搜尋框 =====
// 搜尋框輸入的文字
const keyword = ref('')
// 按 Enter 或放大鏡：跳到所有遊戲頁，並把關鍵字放在網址 ?q=
function search() {
  router.push({ path: '/games/all', query: keyword.value ? { q: keyword.value } : {} })
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

// ===== 底部分頁清單（新上架 / 特價優惠 / 全部遊戲） =====
const tabs = [
  { key: 'new', label: '新上架' },
  { key: 'sale', label: '特價優惠' },
  { key: 'all', label: '全部遊戲' },
]
// 目前選的分頁
const activeTab = ref('new')
// 依分頁決定清單內容：新上架 = 遊戲編號由大到小（越晚加入的越新）
const tabGames = computed(() => {
  if (activeTab.value === 'new') return [...games.value].sort((a, b) => b.gameId - a.gameId).slice(0, 10)
  if (activeTab.value === 'sale') return discountedGames.value
  return games.value
})
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

// 進入頁面：抓遊戲、購物車、遊戲庫，開始自動輪播
onMounted(() => {
  gamesStore.fetchGames()
  cart.fetchCart()
  cart.fetchLibrary()
  startAuto()
})
// 離開頁面：停止自動輪播，避免計時器一直跑
onUnmounted(stopAuto)
</script>

<template>
  <main class="home">
    <!-- ===== 滿版背景大圖：用輪播目前那款遊戲的封面，往下漸漸淡成深藍 ===== -->
    <div class="hero-bg" :style="current ? { backgroundImage: `url(${current.coverUrl})` } : {}"></div>

    <!-- ===== 搜尋框 ===== -->
    <div class="store-nav">
      <!-- 搜尋框：按 Enter 或放大鏡就搜尋 -->
      <form class="search" @submit.prevent="search">
        <input v-model="keyword" placeholder="搜尋" />
        <button type="submit" aria-label="搜尋">🔍</button>
      </form>
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
                <img :src="current.coverUrl" :alt="current.gameName" class="main-img" />
                <div class="info">
                  <div class="title">{{ current.gameName }}</div>
                  <p class="desc">{{ current.description }}</p>
                  <div class="tags">
                    <span v-if="current.genre" class="tag">{{ current.genre }}</span>
                    <span v-if="hasDiscount(current)" class="tag hot">特價中</span>
                  </div>
                  <div class="reason">現已推出</div>
                  <div class="buy-row">
                    <AddToCartButton :game-id="current.gameId" class="cart-btn" />
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
              <div v-for="game in discountedGames.slice(0, 4)" :key="game.gameId" class="special">
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
                  :class="{ hover: preview && preview.gameId === game.gameId }" @mouseenter="hovered = game">
                  <img :src="game.coverUrl" :alt="game.gameName" />
                  <div class="row-text">
                    <div class="row-name">{{ game.gameName }}</div>
                    <div class="row-genre">{{ game.genre || '未分類' }}</div>
                  </div>
                  <PriceBlock :game="game" />
                </div>
              </div>
              <!-- 右邊預覽 -->
              <div v-if="preview" class="preview">
                <div class="preview-name">{{ preview.gameName }}</div>
                <div class="tags">
                  <span v-if="preview.genre" class="tag">{{ preview.genre }}</span>
                </div>
                <img :src="preview.coverUrl" :alt="preview.gameName" />
                <p>{{ preview.description }}</p>
                <AddToCartButton :game-id="preview.gameId" class="cart-btn" />
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
  --bg: #0b0c32;          /* 整頁底色（和網站底色同一個深藍） */
  --bg-glow: #1f3a8a;     /* 整頁上方的藍色光暈 */
  --surface: #121a4a;     /* 卡片、區塊底色 */
  --surface-2: #1c2a66;   /* 比較亮的區塊底色 */
  --gold: #d4a64a;        /* 主要重點色（金色） */
  --gold-dark: #2e4a9a;   /* 邊框色（藍） */
  --sale: #b23a2a;        /* 特價紅 */
  --text: #e4e8f8;        /* 一般文字 */
  --text-muted: #98a2c8;  /* 次要文字 */
  --text-dim: #5e6894;    /* 最淡的文字 */

  min-height: 100vh; padding: 16px; color: var(--text);
}

/* ===== 滿版背景大圖 =====
   position: absolute + 沒有定位的父層 → 以整個網頁為基準，寬度撐滿整個視窗（不受 #app 1280px 限制）
   z-index: -1 → 放在所有內容後面（包含最上面的導覽列） */
.hero-bg {
  position: absolute; top: 0; left: 0; width: 100%; height: 1000px; z-index: -1; overflow: hidden;
  background-color: var(--bg); background-size: cover; background-position: center top;
  transition: background-image .6s;
}
/* 蓋在圖上的漸層：上方稍暗、左右兩側壓暗、下方淡成深藍底色 */
.hero-bg::after {
  content: ''; position: absolute; inset: 0;
  background:
    linear-gradient(90deg, rgba(11, 12, 50, .85) 0%, transparent 25%, transparent 75%, rgba(11, 12, 50, .85) 100%),
    linear-gradient(180deg, rgba(11, 12, 50, .55) 0%, rgba(11, 12, 50, .35) 30%, rgba(11, 12, 50, .8) 65%, var(--bg) 100%);
}
/* 共用：主要內容最寬 1180px 置中 */
.store-nav, .layout { max-width: 1180px; margin: 0 auto; }

/* ===== 商店導覽列 ===== */
.store-nav {
  display: flex; align-items: center; height: 40px;
}
/* 搜尋框：靠右、圓角 */
.search { margin-left: auto; display: flex; height: 28px; }
.search input { width: 200px; border: 1px solid var(--gold-dark); padding: 0 12px; background: var(--bg); color: var(--text); font-size: 13px; border-radius: 14px 0 0 14px; outline: none; }
.search input::placeholder { color: var(--text-dim); }
.search input:focus { border-color: var(--gold); }
.search button { border: none; background: var(--gold); width: 34px; cursor: pointer; border-radius: 0 14px 14px 0; }

/* ===== 主要內容 ===== */
.layout { display: flex; gap: 24px; margin-top: 24px; }
.content { flex: 1; min-width: 0; }
.empty { color: var(--text-muted); padding: 24px 0; }

/* 區塊標題：金色左邊線，右邊「瀏覽更多」按鈕 */
section { margin-bottom: 36px; }
.head { display: flex; justify-content: space-between; align-items: center; margin-bottom: 10px; }
.head h2 { margin: 0; font-size: 18px; color: var(--text); letter-spacing: 2px; padding-left: 10px; border-left: 4px solid var(--gold); }
.more { font-size: 12px; color: var(--gold); border: 1px solid var(--gold); border-radius: 12px; padding: 2px 12px; }
.more:hover { background: var(--gold); color: var(--bg); }

/* ===== 精選大輪播 ===== */
.carousel { position: relative; }
.main-cap { display: flex; height: 353px; border-radius: 6px; overflow: hidden; border: 1px solid var(--gold-dark); box-shadow: 0 8px 24px rgba(0, 0, 0, .6); }
.main-img { width: 66%; height: 100%; object-fit: cover; display: block; }
/* 右側資訊欄 */
.info { flex: 1; padding: 16px 18px; display: flex; flex-direction: column; background: linear-gradient(180deg, var(--surface-2), var(--surface)); }
.title { font-size: 24px; color: var(--gold); line-height: 1.2; margin-bottom: 10px; font-weight: bold; }
.desc { font-size: 13px; line-height: 1.6; color: var(--text-muted); flex: 1; overflow: hidden; }
.tags { display: flex; flex-wrap: wrap; gap: 4px; margin: 8px 0; }
.tag { background: var(--bg); color: var(--text); border: 1px solid var(--gold-dark); font-size: 11px; padding: 2px 8px; border-radius: 10px; }
.tag.hot { background: var(--sale); border-color: var(--sale); color: #fff1e0; }
.reason { font-size: 12px; color: var(--text-muted); margin-bottom: 8px; }
.buy-row { display: flex; justify-content: space-between; align-items: center; gap: 8px; }
/* 左右箭頭：貼在輪播左右兩側的圓形按鈕 */
.arrow {
  position: absolute; top: 50%; transform: translateY(-50%); width: 40px; height: 40px; z-index: 2; border-radius: 50%;
  border: 1px solid var(--gold); font-size: 26px; line-height: 1; color: var(--gold); cursor: pointer; background: rgba(11, 12, 50, .75);
}
.arrow:hover { background: var(--gold); color: var(--bg); }
.arrow.left { left: 10px; }
.arrow.right { right: 10px; }
/* 小圓點：目前那張拉長成金色膠囊 */
.dots { display: flex; justify-content: center; gap: 6px; margin-top: 12px; }
.dots span { width: 8px; height: 8px; border-radius: 4px; background: var(--surface-2); cursor: pointer; transition: width .2s; }
.dots span.on { width: 22px; background: var(--gold); }

/* 加入購物車按鈕：金色（:deep 讓樣式套到子元件的 button 上） */
:deep(.cart-btn) {
  border: none; border-radius: 4px; padding: 6px 14px; font-size: 13px; font-weight: bold; color: #1b0f0d; cursor: pointer;
  background: linear-gradient(to bottom, #e8be63, #b8892f);
}
:deep(.cart-btn:hover:not(:disabled)) { background: linear-gradient(to bottom, #f5d27f, #c99a3a); }
/* 已擁有 / 已在購物車時按鈕變灰 */
:deep(.cart-btn:disabled) { background: #2a3460; color: #98a2c8; cursor: default; }

/* ===== 特別優惠 ===== */
.specials { display: grid; grid-template-columns: repeat(auto-fill, minmax(200px, 1fr)); gap: 14px; }
.special { background: var(--surface); border: 1px solid var(--surface-2); border-radius: 6px; overflow: hidden; transition: transform .2s, border-color .2s; }
.special:hover { transform: translateY(-3px); border-color: var(--gold); }
.special img { width: 100%; aspect-ratio: 460 / 215; object-fit: cover; display: block; }
.special-body { padding: 10px; }
.special-label { font-size: 11px; color: #ff8a6a; letter-spacing: 1px; }
.special-name { color: var(--text); font-size: 14px; margin: 4px 0; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.special-off { font-size: 12px; color: var(--gold); margin-bottom: 8px; }

/* ===== 依類型瀏覽 ===== */
.tiles { display: grid; grid-template-columns: repeat(auto-fill, minmax(170px, 1fr)); gap: 12px; }
.tile { height: 110px; border-radius: 6px; padding: 12px; display: flex; flex-direction: column; justify-content: flex-end; color: #fff; transition: transform .2s, box-shadow .2s; }
.tile:hover { transform: scale(1.04); box-shadow: 0 6px 16px rgba(0, 0, 0, .5); background-color: transparent; }
.tile-name { font-size: 20px; font-weight: bold; text-shadow: 0 2px 4px rgba(0, 0, 0, .5); }
.tile-count { font-size: 12px; opacity: .85; }

/* ===== 分頁清單：選中的分頁底下一條金線 ===== */
.tab-bar { display: flex; gap: 4px; }
.tab-bar button { border: none; padding: 8px 16px; font-size: 14px; cursor: pointer; color: var(--text-muted); background: transparent; border-bottom: 2px solid transparent; }
.tab-bar button:hover { color: var(--text); }
.tab-bar button.active { color: var(--gold); border-bottom-color: var(--gold); }
.tab-body { display: flex; gap: 10px; background: var(--surface); padding: 10px; border-radius: 0 0 6px 6px; border-top: 1px solid var(--surface-2); }
.rows { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 4px; }
.row { display: flex; align-items: center; gap: 10px; background: var(--bg); padding-right: 8px; cursor: pointer; border-radius: 4px; overflow: hidden; border-left: 3px solid transparent; }
.row.hover { background: var(--surface-2); border-left-color: var(--gold); }
.row.hover .row-name { color: var(--gold); }
.row img { width: 184px; height: 69px; object-fit: cover; display: block; flex-shrink: 0; }
.row-text { flex: 1; min-width: 0; }
.row-name { color: var(--text); font-size: 15px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.row-genre { color: var(--text-dim); font-size: 12px; }
/* 右側預覽 */
.preview { width: 300px; flex-shrink: 0; padding: 12px; background: var(--surface-2); border-radius: 4px; }
.preview-name { font-size: 20px; color: var(--gold); line-height: 1.2; font-weight: bold; }
.preview img { width: 100%; aspect-ratio: 460 / 215; object-fit: cover; display: block; margin-bottom: 8px; border-radius: 4px; }
.preview p { font-size: 13px; color: var(--text-muted); }

/* ===== 手機 / 小螢幕：輪播和預覽改成上下排 ===== */
@media (max-width: 900px) {
  .preview, .arrow { display: none; }
  .main-cap { flex-direction: column; height: auto; }
  .main-img { width: 100%; aspect-ratio: 16 / 9; }
  .search input { width: 110px; }
  .row img { width: 120px; height: 45px; }
}
</style>
