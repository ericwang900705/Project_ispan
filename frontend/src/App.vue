<script setup>
// 引入 vue-router 提供的元件：
// RouterLink：產生導覽連結（取代 <a>，切換頁面不會重新整理）
// RouterView：依目前路由顯示對應的頁面元件
import { RouterLink, RouterView } from 'vue-router'
import { onMounted, ref } from 'vue'
// 共用的遊戲 store：下拉選單的分類清單從這裡來
// genreIcon：依分類名稱拿到對應的圖示
import { useGamesStore, genreIcon } from '@/stores/games'
// 右下角好友名單 fetchFriends()記得換掉5個好友都是假資料不然我都會看到5個人
import FriendsList from '@/components/FriendsList.vue'
// 右下角客服：獨立一條，疊在好友名單上面
import CustomerService from '@/components/CustomerService.vue'

const gamesStore = useGamesStore()

// 右下角目前展開的是哪一個：'support'（客服）、'friends'（好友）或 null（都收合）
// 同一時間只會展開一個：點客服時好友收下去，點好友時客服收下去
const dockOpen = ref(null)
function toggleDock(name) {
  dockOpen.value = dockOpen.value === name ? null : name
}

// 網站一打開就抓遊戲清單，下拉選單才有分類可以顯示
onMounted(() => gamesStore.fetchGames())
</script>

<template>
  <!-- 頁首區塊 -->
  <header>
    <div class="wrapper">
      <!-- 導覽列：每個 RouterLink 對應 router 中設定的一個路徑 -->
      <nav>
        <!-- 首頁（遊戲商店首頁） -->
        <RouterLink to="/">首頁</RouterLink>
        <!-- 所有遊戲下拉選單：滑鼠移到「所有遊戲」上才往下展開分類（橫向排列） -->
        <div class="dropdown">
          <RouterLink to="/games/all">所有遊戲 ▾</RouterLink>
          <!-- 分類選單：點了會到所有遊戲頁並只顯示該分類 -->
          <!-- 每個分類：圖示在上、文字在下 -->
          <div class="dropdown-menu">
            <RouterLink to="/games/all" class="genre-link">
              <span class="genre-icon">🎮</span>全部
            </RouterLink>
            <RouterLink v-for="genre in gamesStore.genres" :key="genre" class="genre-link"
              :to="{ path: '/games/all', query: { genre } }">
              <span class="genre-icon">{{ genreIcon(genre) }}</span>{{ genre }}
            </RouterLink>
          </div>
        </div>
        <RouterLink to="/about">平台介紹</RouterLink>
        <RouterLink to="/point-store">點數商店</RouterLink>
        <RouterLink to="/member-games">遊戲庫</RouterLink>
        <RouterLink to="/shopping-cart">購物車</RouterLink>
      </nav>
    </div>
  </header>
  <!-- 佔位用：補回固定 header 的高度 -->
  <div class="header-spacer"></div>

  <!-- 路由出口：目前網址對應的頁面會渲染在這裡 -->
  <RouterView />

  <!-- 右下角：客服和好友合成一個欄位（客服在上、好友在下），每一頁都會顯示 -->
  <div class="dock">
    <CustomerService :open="dockOpen === 'support'" @toggle="toggleDock('support')" />
    <FriendsList :open="dockOpen === 'friends'" @toggle="toggleDock('friends')" />
  </div>
</template>

<!-- scoped：樣式只作用於此元件，不會影響其他元件 -->
<style scoped>
/* 頁首：設定行高，並限制最大高度不超過視窗高度 */
/* 固定在畫面最上方：不管怎麼往下滑都會停在頂端，半透明深色底讓字在背景圖上也看得清楚 */
header {
  line-height: 1.5;
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 1000;
  background: rgba(15, 18, 40, .85);
  backdrop-filter: blur(6px);
  box-shadow: 0 2px 10px rgba(0, 0, 0, .5);
}

/* header 固定後不佔位置，用同高度的空白把頁面內容往下推，才不會被蓋住 */
.header-spacer {
  height: 4.5rem;
}

/* 導覽列：滿版寬度、大字體、文字置中 */
nav {
  width: 100%;
  font-size: 1.25rem;
  text-align: center;
  padding: 1rem 0;
}

/* 目前所在頁面的連結（路徑完全相符時 vue-router 會自動加上此 class）：使用一般文字顏色 */
nav a.router-link-exact-active {
  color: var(--color-text);
}

/* 目前所在頁面的連結滑鼠移上去時：不顯示背景色 */
nav a.router-link-exact-active:hover {
  background-color: transparent;
}

/* 導覽列中的每個連結：橫向排列、左右留白，左側加一條分隔線 */
nav a {
  display: inline-block;
  padding: 0 1rem;
  border-left: 1px solid var(--color-border);
}

/* 第一個項目（首頁下拉選單）和它裡面的「首頁」連結：移除左側分隔線 */
nav > :first-child,
.dropdown > a {
  border: 0;
}

/* 下拉選單外框：上下撐滿導覽列高度，滑鼠往下移到選單時才不會中途斷掉 */
/* 不設 position，讓選單以整個 header 為基準定位（才能滿版） */
.dropdown {
  display: inline-block;
  padding: 1rem 0;
  margin: -1rem 0;
  border-left: 1px solid var(--color-border);
}

/* 「所有遊戲」本身當小三角形的定位基準 */
.dropdown > a {
  position: relative;
}

/* 展開時在「所有遊戲」正下方出現一個指向它的小三角形（顏色同選單底色） */
.dropdown:hover > a::after {
  content: '';
  position: absolute;
  left: 50%;
  top: calc(100% + 1rem - 18px);
  transform: translateX(-50%);
  border: 9px solid transparent;
  border-bottom-color: #2d3a7a;
  z-index: 101;
}

/* 下拉選單本體：平常隱藏，展開時是 header 正下方一整條滿版的淺色橫條 */
.dropdown-menu {
  display: none;
  position: absolute;
  top: 100%;
  left: 0;
  right: 0;
  z-index: 100;
  padding: 14px 2rem;
  background: linear-gradient(to bottom, #2d3a7a, #232c62);
  box-shadow: 0 6px 16px rgba(0, 0, 0, .35);
  justify-content: center;
  flex-wrap: wrap;
  gap: 0 3.5rem;
}

/* 滑鼠移到「所有遊戲」或選單上時展開：選項橫向平均排成一列 */
.dropdown:hover .dropdown-menu {
  display: flex;
}

/* 選單內的連結：不要左側分隔線，深灰字 */
.dropdown-menu a {
  display: block;
  border: 0;
  padding: 6px 16px;
  color: #dfe6ff;
  white-space: nowrap;
  transition: color .2s;
}

/* 目前所在的分類：金色（避免沿用導覽列淺色字，在淺底上看不到） */
.dropdown-menu a.router-link-exact-active {
  color: #f0c060;
}

.dropdown-menu a:hover,
.dropdown-menu a.router-link-exact-active:hover {
  background: transparent;
  color: #f0c060;
}

/* 分類連結：大圖示在上、文字在下，置中 */
.dropdown-menu a.genre-link {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4px;
  padding: 6px 10px;
  font-size: 1.05rem;
  font-weight: 600;
  letter-spacing: 1px;
}

.genre-icon {
  font-size: 36px;
  line-height: 1;
  transition: transform .2s;
}

/* 滑鼠移上去圖示微微往上跳 */
.dropdown-menu a.genre-link:hover .genre-icon {
  transform: translateY(-3px);
}

/* 選單展開時，「所有遊戲」保持反白 */
.dropdown:hover > a {
  color: #d4a64a;
}

/* 右下角容器：客服和好友合成一個欄位，外框、圓角、陰影都由這裡統一畫 */
.dock {
  position: fixed;
  right: 16px;
  bottom: 0;
  z-index: 1000;
  width: 280px;
  display: flex;
  flex-direction: column;
  background: #2a1a14;
  border: 1px solid #6b4e0f;
  border-bottom: none;
  border-radius: 8px 8px 0 0;
  box-shadow: 0 -4px 20px rgba(0, 0, 0, .6);
}

/* 手機：右下角容器縮小 */
@media (max-width: 700px) {
  .dock {
    right: 8px;
    width: 220px;
  }
}

/* 響應式設計：螢幕寬度 ≥ 1024px（桌機）時套用 */
@media (min-width: 1024px) {
  nav {
    font-size: 1.4rem;   /* 桌機再放大一點 */
  }
}
</style>
