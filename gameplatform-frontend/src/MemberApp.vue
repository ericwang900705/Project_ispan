<script setup>
// 引入 vue-router 提供的元件：
// RouterLink：產生導覽連結（取代 <a>，切換頁面不會重新整理）
// RouterView：依目前路由顯示對應的頁面元件
import { RouterLink, RouterView } from 'vue-router'
import { onMounted, onUnmounted, ref, computed, watchEffect, watch } from 'vue'
import { useRoute } from 'vue-router'
import { useCartStore } from './cart/stores/cart'
import { useOrdersStore } from './cart/stores/orders'
import { useReviewStore } from './cart/stores/reviews'
import { session, logout } from './api'
import PublisherSidebar from './components/PublisherSidebar.vue'
import PublisherToast from './components/PublisherToast.vue'
import FriendsList from './cart/components/FriendsList.vue'
import ChatWidget from './components/ChatWidget.vue'
import GlobalNotificationBell from './components/GlobalNotificationBell.vue'
// 共用的遊戲 store：下拉選單的分類清單從這裡來
// genreIcon：依分類名稱拿到對應的圖示
import { useGamesStore, genreIcon } from '@/cart/stores/games'
// 好友列表使用目前會員的實際 API 資料。

// 右下角客服：獨立一條，疊在好友名單上面


const gamesStore = useGamesStore()
const route = useRoute()
const cart = useCartStore(), orders = useOrdersStore(), reviews = useReviewStore()
watch(() => session.user?.id, () => {
  cart.items = []; cart.library = []; cart.coupon = null; cart.coupons = []; cart.totalPrice = 0; orders.orders = []; reviews.reviews = []; reviews.gameId = null
  if (session.user) Promise.all([cart.fetchCart(), cart.fetchLibrary()]).catch(e => { catalogError.value = e.message })
})
const publisher = computed(() => route.path.startsWith('/publisher'))
const account = computed(() =>
  route.path.startsWith('/member/') &&
  !route.path.startsWith('/member/support')
)
const support = computed(() => route.path.startsWith('/member/support') || route.path.startsWith('/tickets'))
const catalogError = ref('')
watchEffect(() => {
  document.body.classList.remove('support-portal', 'staff-workspace')
  document.body.classList.toggle('publisher-workspace', publisher.value)
  document.body.classList.add('cart-portal')
})

// 右下角目前展開的是哪一個：'support'（客服）、'friends'（好友）或 null（都收合）
// 同一時間只會展開一個：點客服時好友收下去，點好友時客服收下去
const supportChatRef = ref(null)
const friendsChatRef = ref(null)

// 開啟客服聊天室時，關閉好友聊天室
function onSupportExpanded(isOpen) {
  if (isOpen) {
    friendsChatRef.value?.collapse()
  }
}

// 開啟好友聊天室時，關閉客服聊天室
function onFriendsExpanded(isOpen) {
  if (isOpen) {
    supportChatRef.value?.collapse()
  }
}

// 網站一打開就抓遊戲清單，下拉選單才有分類可以顯示
onMounted(() => {
  gamesStore.fetchGames().catch(e => {
    catalogError.value = e.message
  })

  document.addEventListener('pointerdown', onClickOutside)
  document.addEventListener('keydown', onAccountEscape)
})

onUnmounted(() => {
  document.removeEventListener('pointerdown', onClickOutside)
  document.removeEventListener('keydown', onAccountEscape)
})
// 縮小視窗後的商城選單
const navOpen = ref(false)
const genreMenuOpen = ref(false)
const navigationEl = ref(null)

function toggleNavigation() {
  navOpen.value = !navOpen.value

  if (!navOpen.value) {
    genreMenuOpen.value = false
  }
}

const accountMenuOpen = ref(false)
const accountMenuEl = ref(null)

// 點選單外側時關閉
function onClickOutside(event) {
  if (accountMenuEl.value && !accountMenuEl.value.contains(event.target)) {
    accountMenuOpen.value = false
  }

  if (navigationEl.value && !navigationEl.value.contains(event.target)) {
    navOpen.value = false
    genreMenuOpen.value = false
  }
}

// 按 Escape 關閉
function onAccountEscape(event) {
  if (event.key === 'Escape') {
    accountMenuOpen.value = false
    navOpen.value = false
    genreMenuOpen.value = false
  }
}

// 登出
function handleLogout() {
  accountMenuOpen.value = false
  logout()
}

// 切換頁面時關閉
watch(() => route.fullPath, () => {
  accountMenuOpen.value = false
  navOpen.value = false
  genreMenuOpen.value = false
})
</script>

<template>
  <!-- 頁首區塊 -->
  <header class="store-header">
    <div class="store-header-inner">

      <!-- 左上角商城名稱 -->
      <RouterLink class="store-brand" to="/">
        <strong>HERO 赫洛</strong>
        <small>遊戲商城</small>
      </RouterLink>

      <!-- 中間導覽列 -->
      <!-- 中央導覽區 -->
      <div ref="navigationEl" class="store-navigation">

        <!-- 縮小視窗才顯示 -->
        <button type="button" class="nav-toggle" :aria-expanded="navOpen" aria-controls="store-navigation-menu"
          aria-label="開啟或收合商城選單" @click="toggleNavigation">
          <span aria-hidden="true">☰</span>
          <span class="nav-toggle-label">選單</span>
        </button>

        <!-- 商城導覽列 -->
        <nav id="store-navigation-menu" class="store-nav" :class="{ 'is-open': navOpen }" aria-label="商城導覽列">
          <RouterLink to="/">首頁</RouterLink>

          <!-- 所有遊戲 -->
          <div class="dropdown" :class="{ 'genre-menu-open': genreMenuOpen }">
            <div class="genre-row">
              <RouterLink to="/games/all">
                所有遊戲
                <span class="desktop-chevron">▾</span>
              </RouterLink>

              <button type="button" class="genre-toggle" :aria-expanded="genreMenuOpen" aria-label="展開遊戲分類"
                @click="genreMenuOpen = !genreMenuOpen">
                ▾
              </button>
            </div>

            <div class="dropdown-menu">
              <RouterLink to="/games/all" class="genre-link">
                <span class="genre-icon">🎮</span>
                全部
              </RouterLink>

              <RouterLink v-for="genre in gamesStore.genres" :key="genre" class="genre-link" :to="{
                path: '/games/all',
                query: { genre }
              }">
                <span class="genre-icon">
                  {{ genreIcon(genre) }}
                </span>
                {{ genre }}
              </RouterLink>
            </div>
          </div>

          <RouterLink to="/about">
            平台介紹
          </RouterLink>

          <RouterLink to="/point-store">
            點數商店
          </RouterLink>

          <RouterLink to="/member-games">
            遊戲庫
          </RouterLink>

          <RouterLink to="/shopping-cart">
            購物車
          </RouterLink>

          <RouterLink v-if="session.user?.role === 'PUBLISHER'" to="/publisher/overview">
            發行商工作區
          </RouterLink>

          <RouterLink v-if="session.user" to="/member/support">
            客服中心
          </RouterLink>

        </nav>
      </div>

      <!-- 右側通知及會員功能 -->
      <div class="store-actions">

        <!-- 通知鈴鐺 -->
        <span v-if="session.user" class="support-ui member-bell">
          <GlobalNotificationBell :key="session.user.role + ':' + session.user.id" />
        </span>

        <!-- 會員名稱下拉選單 -->
        <div v-if="session.user" ref="accountMenuEl" class="account-dropdown">
          <button type="button" class="account-trigger" aria-haspopup="true" :aria-expanded="accountMenuOpen"
            @click="accountMenuOpen = !accountMenuOpen">
            <span class="account-name">
              {{ session.user.username }}
            </span>
            <span>
              {{ accountMenuOpen ? '▴' : '▾' }}
            </span>
          </button>

          <div v-if="accountMenuOpen" class="account-menu">
            <div class="account-menu-heading">
              {{ session.user.username }}
            </div>

            <RouterLink to="/member/profile">
              個人資料
            </RouterLink>

            <RouterLink to="/orders">
              我的訂單
            </RouterLink>

            <RouterLink to="/member/change-password">
              修改密碼
            </RouterLink>

            <button type="button" class="account-logout" @click="handleLogout">
              登出
            </button>
          </div>
        </div>

        <RouterLink v-else class="store-login" to="/member/login">
          登入
        </RouterLink>

      </div>
    </div>
  </header>
  <!-- 佔位用：補回固定 header 的高度 -->
  <div class="header-spacer"></div>

  <!-- 路由出口：目前網址對應的頁面會渲染在這裡 -->
  <p v-if="catalogError && !publisher" class="catalog-error" role="alert">{{ catalogError }}</p>
  <PublisherSidebar v-if="publisher" />
  <main v-if="publisher" class="publisher-workspace publisher-content">
    <RouterView />
  </main>
  <main v-else-if="support || account" class="support-ui member-ui support-page">
    <RouterView />
  </main>
  <RouterView v-else />
  <PublisherToast v-if="publisher" />

  <!-- 右下角：客服和好友合成一個欄位（客服在上、好友在下），每一頁都會顯示 -->
  <div v-if="session.user" class="support-ui member-ui support-scope support-friend-skin">
    <div class="member-social-dock">
      <ChatWidget ref="supportChatRef" :key="session.user.id" docked @expanded-change="onSupportExpanded" />

      <FriendsList ref="friendsChatRef" :key="session.user.role + '-' + session.user.id"
        @expanded-change="onFriendsExpanded" />
    </div>
  </div>
</template>

<!-- scoped：樣式只作用於此元件，不會影響其他元件 -->

<style scoped>
/* 頂欄 */
.store-header {
  position: fixed;
  inset: 0 0 auto;
  z-index: 1300;
  height: 70px;
  background: #101329;
  color: #dfe6ff;
  border-bottom: 1px solid #35406f;
  box-shadow: 0 2px 12px rgb(0 0 0 / 30%);
}

.header-spacer {
  height: 70px;
}

.store-header-inner {
  box-sizing: border-box;
  width: min(1180px, calc(100% - 32px));
  height: 100%;
  margin: 0 auto;
  padding: 0;
  display: flex;
  align-items: center;
  gap: 28px;
  /* 商城名稱與「首頁」之間的距離 */
}

/* 商城名稱 */
.store-brand {
  flex: none;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 2px;
  padding: 0;
  color: #f0c060;
  line-height: 1.1;
  text-decoration: none;
  white-space: nowrap;
}

.store-brand strong {
  font-size: 19px;
  font-weight: 800;
}

.store-brand small {
  font-size: 10px;
  letter-spacing: 2px;
  color: #b9c8eb;
}

/* 名稱後方緊接導覽列（不要用 Grid 置中） */
.store-navigation {
  display: flex;
  align-items: center;
  height: 100%;
  min-width: 0;
  margin: 0;
}

/* 桌面版隱藏漢堡選單按鈕 */
.nav-toggle,
.genre-toggle {
  display: none;
}

/* 導覽列 */
.store-nav {
  display: flex;
  flex: none;
  align-items: center;
  justify-content: flex-start;
  width: max-content;
  height: 100%;
  gap: 8px;
  white-space: nowrap;
}

.store-nav>a,
.store-nav>.dropdown>.genre-row>a {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  height: 42px;
  padding: 0 16px;
  color: #d4a64a;
  font-size: 14px;
  font-weight: 650;
  text-decoration: none;
}

.store-nav>a:hover,
.store-nav>.dropdown>.genre-row>a:hover,
.store-nav>a.router-link-active {
  color: #f9d88d;
  background: transparent;
}

.store-nav>a:first-child {
  border-left: 0;
}

/* 所有遊戲標題 + 分類展開鍵 */
.genre-row {
  display: flex;
  align-items: center;
  height: 100%;
}

/* 遊戲分類 */
.dropdown {
  display: flex;
  align-items: center;
  height: 100%;
}

.dropdown-menu {
  display: none;
  position: absolute;
  top: 100%;
  left: 0;
  right: 0;
  z-index: 1310;
  justify-content: center;
  flex-wrap: wrap;
  gap: 10px 24px;
  padding: 16px 20px;
  background: #232c62;
  box-shadow: 0 10px 18px rgb(0 0 0 / 30%);
}

.dropdown:hover .dropdown-menu,
.dropdown:focus-within .dropdown-menu {
  display: flex;
}

.dropdown-menu .genre-link {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6px;
  padding: 7px 10px;
  color: #dfe6ff;
  text-decoration: none;
  font-size: 13px;
}

.dropdown-menu .genre-link:hover {
  color: #f0c060;
  background: transparent;
}

.genre-icon {
  font-size: 27px;
  line-height: 1;
}

/* 右邊帳號 */
.store-actions {
  display: flex;
  flex: none;
  align-items: center;
  justify-content: flex-end;
  gap: 10px;
  margin-left: auto;
  /* 將登入或鈴鐺／名稱推到右邊 */
}

.store-actions .member-bell {
  display: inline-flex;
  align-items: center;
}

.account-dropdown {
  position: relative;
}

.account-trigger {
  display: flex;
  align-items: center;
  gap: 9px;
  padding: 10px 8px;
  border: 0;
  background: transparent;
  color: #f0c060;
  cursor: pointer;
  font-size: 13px;
}

.account-trigger:hover {
  color: #ffe1a2;
}

.account-name {
  max-width: 145px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* 會員下拉選單 */
.account-menu {
  position: absolute;
  top: calc(100% + 8px);
  right: 0;
  z-index: 1320;
  width: 214px;
  padding: 7px;
  border: 1px solid #485383;
  border-radius: 10px;
  background: #151b45;
  box-shadow: 0 15px 30px rgb(0 0 0 / 40%);
}

.account-menu-heading {
  padding: 10px 12px;
  border-bottom: 1px solid #35406f;
  margin-bottom: 5px;
  color: #f0c060;
  font-size: 13px;
  overflow-wrap: anywhere;
}

.account-menu>a,
.account-menu>button {
  display: flex;
  width: 100%;
  align-items: center;
  justify-content: space-between;
  padding: 10px 12px;
  border: 0;
  border-radius: 6px;
  text-align: left;
  color: #dfe6ff;
  background: transparent;
  font-size: 13px;
  text-decoration: none;
}

.account-menu>a:hover,
.account-menu>button:hover {
  background: #28346d;
  color: #f0c060;
}

.account-menu>button {
  cursor: pointer;
}

.account-menu>.account-logout {
  margin-top: 4px;
  border-top: 1px solid #35406f;
  color: #ffd0d0;
}

.store-login {
  padding: 7px 12px;
  border: 1px solid #485383;
  border-radius: 6px;
  color: #dfe6ff;
}

/* 所有商城頁的通知鈴鐺 */
.store-header :deep(.global-bell) {
  position: relative;
  margin-left: 0;
  flex: none;
}

.store-header :deep(.support-notification-bell) {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  position: relative;
  box-sizing: border-box;
  width: 50px;
  min-width: 50px;
  height: 42px;
  padding: 0;
  border: 1px solid #455385;
  border-radius: 9px;
  background: #202b5d;
  color: #dfe6ff;
  cursor: pointer;
}

.store-header :deep(.support-notification-bell:hover),
.store-header :deep(.support-notification-bell[aria-expanded="true"]) {
  background: #303e79;
  border-color: #687bb8;
}

.store-header :deep(.support-notification-bell .ui-icon) {
  width: 20px;
  height: 20px;
}

.store-header :deep(.global-bell-count) {
  position: absolute;
  right: -6px;
  top: -7px;
  padding: 1px 5px;
  min-width: 18px;
  border-radius: 999px;
  background: #e54858;
  color: white;
  font-size: 10px;
  line-height: 17px;
  text-align: center;
}

.store-header :deep(.global-bell-panel) {
  position: absolute;
  right: 0;
  left: auto;
  top: calc(100% + 12px);
  z-index: 1600;
  width: min(390px, calc(100vw - 24px));
  max-height: min(560px, 70vh);
  overflow-y: auto;
}

.catalog-error {
  padding: 12px;
  background: #4b2535;
  color: #ffd3d3;
  border-radius: 8px;
}

/* 縮小視窗：切換成下拉選單 */

@media (max-width: 1250px) {

  /* 保持頂欄單排 */
  .store-header,
  .header-spacer {
    height: 70px;
  }

  .store-header-inner {
    display: flex;
    flex-wrap: nowrap;
    width: min(1100px, calc(100% - 32px));
    gap: 14px;
    padding: 0;
  }

  .store-actions {
    margin-left: 0;
    justify-content: flex-end;
  }

  .store-navigation {
    margin-left: auto;
    height: auto;
  }

  /* 漢堡選單按鈕 */
  .nav-toggle {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    gap: 7px;

    height: 42px;
    padding: 0 13px;

    border: 1px solid #455385;
    border-radius: 9px;

    background: #202b5d;
    color: #f0c060;

    font-size: 14px;
    cursor: pointer;
  }

  .nav-toggle:hover,
  .nav-toggle[aria-expanded="true"] {
    background: #303e79;
  }

  /* 下拉選單，預設隱藏 */
  .store-nav {
    display: none;
    position: absolute;

    left: 12px;
    right: 12px;
    top: calc(100% + 1px);
    z-index: 1400;

    box-sizing: border-box;
    width: auto;
    height: auto;
    max-height: min(68vh, 600px);

    flex: none;
    flex-direction: column;
    align-items: stretch;
    gap: 0;
    order: initial;

    padding: 8px;
    overflow-x: hidden;
    overflow-y: auto;

    border: 1px solid #455385;
    border-radius: 0 0 12px 12px;

    background: #151b43;
    box-shadow: 0 18px 35px #0008;
  }

  /* 按 ☰ 才展開 */
  .store-nav.is-open {
    display: flex;
  }

  /* 選單每個項目 */
  .store-nav>a,
  .store-nav>.dropdown>.genre-row>a {
    box-sizing: border-box;
    justify-content: flex-start;

    width: 100%;
    height: auto;
    min-height: 46px;
    padding: 12px 16px;

    border-left: 0;
    border-bottom: 1px solid #35406f;
    font-size: 14px;
  }

  .store-nav>a:hover,
  .store-nav>a.router-link-active,
  .store-nav>.dropdown>.genre-row>a:hover {
    background: #202b5d;
  }

  /* 所有遊戲分類 */
  .store-nav .dropdown {
    flex-direction: column;
    align-items: stretch;
    height: auto;
  }

  .store-nav .genre-row {
    height: auto;
  }

  .store-nav .genre-row>a {
    flex: 1;
  }

  .desktop-chevron {
    display: none;
  }

  .genre-toggle {
    display: inline-flex;
    align-items: center;
    justify-content: center;

    flex: none;
    width: 48px;

    border: 0;
    border-bottom: 1px solid #35406f;

    background: transparent;
    color: #f0c060;

    font-size: 16px;
    cursor: pointer;
  }

  /* 分類子選單先隱藏 */
  .store-navigation .store-nav .dropdown .dropdown-menu {
    display: none;
    position: static;

    box-shadow: none;
    padding: 10px;
    background: #101735;
    border: 0;

    max-height: 230px;
    overflow-y: auto;
    justify-content: flex-start;
  }

  /* 點分類箭頭後展開 */
  .store-navigation .store-nav .dropdown.genre-menu-open .dropdown-menu {
    display: grid;
    grid-template-columns:
      repeat(auto-fill, minmax(100px, 1fr));
    gap: 8px;
  }
}


/* ==================================
   手機版：縮小按鈕與文字
================================== */

@media (max-width: 550px) {

  .store-header-inner {
    width: calc(100% - 20px);
    padding: 0;
    gap: 8px;
  }

  .store-brand strong {
    font-size: 15px;
  }

  .store-brand small {
    font-size: 9px;
    letter-spacing: 1px;
  }

  .nav-toggle {
    width: 42px;
    padding: 0;
    font-size: 18px;
  }

  .nav-toggle-label {
    display: none;
  }

  .store-actions {
    gap: 6px;
  }

  .account-trigger {
    gap: 4px;
    padding: 8px 2px;
    font-size: 12px;
  }

  .account-name {
    max-width: 78px;
  }

  .store-header :deep(.support-notification-bell) {
    width: 42px;
    min-width: 42px;
    height: 42px;
  }
}

@media (max-width: 380px) {
  .account-name {
    max-width: 54px;
  }

  .store-brand strong {
    font-size: 14px;
  }
}
</style>
