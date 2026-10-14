<script setup>
import { RouterLink, RouterView, useRouter, useRoute } from 'vue-router'
import { ref, computed, watchEffect, watch, nextTick, onMounted, onUnmounted } from 'vue'
import { session, logout } from './api'
import { canSupport, canReview, canUseTickets, homeFor, roleName, loginFor } from './permissions'
import UiIcon from './components/UiIcon.vue'
import ChatWidget from './components/ChatWidget.vue'
import ManagementSidebar from './components/ManagementSidebar.vue'
import GlobalNotificationBell from './components/GlobalNotificationBell.vue'
import PublisherToast from './components/PublisherToast.vue'
import PublisherSidebar from './components/PublisherSidebar.vue'










const router = useRouter()
const route = useRoute()
const error = ref('')
const menuOpen = ref(false)
const siteMoreOpen = ref(false)
const siteMoreRoot = ref(null)
const viewportWidth = ref(typeof window !== 'undefined' ? window.innerWidth : 1440)
function syncViewport() { viewportWidth.value = window.innerWidth }
const menuButton = ref(null)
const accountMenuOpen = ref(false)
const accountRoot = ref(null)
const workspace = computed(() => !!session.user && ['ADMIN','SUPPORT','REVIEWER'].includes(session.user.role))
const isPublisherWorkspace = computed(() => session.user?.role === 'PUBLISHER' && route.path.startsWith('/publisher'))
const isAuthPage = computed(() => ['/member/login', '/staff/login', '/login', '/member/register', '/member/verification-sent', '/member/verify', '/member/resend-verification'].includes(route.path))
const registrationPage = computed(() => ['/member/register', '/member/verification-sent', '/member/verify', '/member/resend-verification'].includes(route.path))
const showSiteHeader = computed(() => !registrationPage.value && (!!session.user || !isAuthPage.value))
const memberNavLinks = computed(() => [
  { to: '/', label: '商店首頁' },
  { to: '/games/all', label: '所有遊戲' },
  { to: '/point-store', label: '點數商店' },
  { to: '/member-games', label: '我的遊戲庫' },
  { to: '/shopping-cart', label: '購物車' },
  { to: '/tickets', label: '客服中心' }
])
const publisherNavLinks = computed(() => [
  { to: '/', label: '商店首頁' },
  { to: '/games/all', label: '所有遊戲' },
  { to: '/point-store', label: '點數商店' },
  { to: '/shopping-cart', label: '購物車' },
  { to: '/tickets', label: '客服中心' },
  { to: '/publisher/overview', label: '發行商工作區' }
])
// The same role-based navigation is used across shop, game details, tickets and publisher workspace.
// Keep all links visible when they fit.  At narrower widths, keep the important shortcuts
// and move only secondary links into the HERO “更多” popover (not a Steam-style menu).
const siteNavLinks = computed(() => session.user?.role === 'PUBLISHER' ? publisherNavLinks.value : memberNavLinks.value)
const navSelection = computed(() => {
  const mobile = viewportWidth.value < 780
  const publisher = session.user?.role === 'PUBLISHER'
  if (viewportWidth.value >= 1200) return siteNavLinks.value.map(item => item.to)
  if (mobile) return publisher
    ? ['/', '/shopping-cart', '/tickets', '/publisher/overview']
    : ['/', '/games/all', '/shopping-cart', '/tickets']
  if (viewportWidth.value >= 1000) return publisher
    ? ['/', '/games/all', '/shopping-cart', '/publisher/overview']
    : ['/', '/games/all', '/member-games', '/shopping-cart']
  return publisher
    ? ['/', '/shopping-cart', '/publisher/overview']
    : ['/', '/games/all', '/shopping-cart']
})
const visibleSiteLinks = computed(() => siteNavLinks.value.filter(item => navSelection.value.includes(item.to)))
const overflowSiteLinks = computed(() => siteNavLinks.value.filter(item => !navSelection.value.includes(item.to)))
const navIcon = (path) => ({'/':'game', '/games/all':'game', '/point-store':'wallet',
  '/member-games':'folder', '/shopping-cart':'cart', '/tickets':'headset', '/publisher/overview':'building'})[path] || 'menu'
const compactLabel = (item) => item.to === '/publisher/overview' ? '工作區' :
  item.to === '/' ? '首頁' : item.to === '/tickets' ? '客服' : item.to === '/games/all' ? '遊戲' : item.label
async function closeMenu(){const wasOpen=menuOpen.value;menuOpen.value=false;if(wasOpen){await nextTick();menuButton.value?.focus()}}
function escape(event) {
  if (event.key !== 'Escape') return
  if (menuOpen.value) closeMenu()
  siteMoreOpen.value = false
  accountMenuOpen.value = false
}
function outsideHeaderMenu(event) {
  if (accountRoot.value && !accountRoot.value.contains(event.target)) accountMenuOpen.value = false
  if (siteMoreRoot.value && !siteMoreRoot.value.contains(event.target)) siteMoreOpen.value = false
}
onMounted(()=>{window.addEventListener('keydown',escape);window.addEventListener('resize',syncViewport);document.addEventListener('click',outsideHeaderMenu);syncViewport()})
onUnmounted(()=>{window.removeEventListener('keydown',escape);window.removeEventListener('resize',syncViewport);document.removeEventListener('click',outsideHeaderMenu);document.body.style.overflow=''})
watch(()=>session.user?.role,()=>{menuOpen.value=false;siteMoreOpen.value=false;accountMenuOpen.value=false})
watch(()=>route.fullPath,()=>{siteMoreOpen.value=false;accountMenuOpen.value=false})
watch(overflowSiteLinks, () => { siteMoreOpen.value = false })
watch(menuOpen,value=>{document.body.style.overflow=value?'hidden':''})
const staff = computed(() => router.currentRoute.value.path === '/staff/login' || !!(session.user && ['ADMIN','SUPPORT','REVIEWER'].includes(session.user.role)))
watchEffect(() => {
  document.body.classList.add('support-portal')
  document.body.classList.toggle('staff-workspace', staff.value)
  document.body.classList.toggle('member-workspace', !staff.value)
  document.body.classList.toggle('publisher-workspace', isPublisherWorkspace.value)
})
async function leave() { try { const destination = loginFor(session.user?.role); await logout(); await router.push(destination) } catch(e) { error.value = e.message } }
</script>
<template>
  <PublisherToast />
  <div class="support-ui" :class="[staff ? 'staff-ui' : 'member-ui', {'workspace-header-shell':workspace, 'site-header-shell': showSiteHeader && !workspace}]">
    <div v-if="showSiteHeader" class="site-header" :class="{'management-header':workspace}">
      <button v-if="workspace" ref="menuButton" class="sidebar-mobile-toggle" aria-label="開啟管理選單" aria-controls="management-sidebar" :aria-expanded="menuOpen" @click="menuOpen = !menuOpen"><UiIcon name="menu" /></button>
      <RouterLink class="brand" :to="workspace ? homeFor(session.user?.role) : '/'" :aria-label="workspace ? 'HERO 赫洛－返回後台首頁' : 'HERO 赫洛－返回商城首頁'"><UiIcon name="game" /><span title="赫洛遊戲社交平台（HERO Game Platform）">HERO 赫洛</span></RouterLink>
      <span v-if="workspace" class="header-workspace-title">管理工作後台</span>
      <nav v-if="!workspace" class="integrated-member-nav" aria-label="HERO 全站導覽">
        <RouterLink v-for="link in visibleSiteLinks" :key="link.to" :to="link.to" class="primary-nav-link" :title="link.label">
          <UiIcon :name="navIcon(link.to)" />
          <span class="nav-full-label">{{ link.label }}</span>
          <span class="nav-short-label">{{ compactLabel(link) }}</span>
        </RouterLink>
        <div v-if="overflowSiteLinks.length" ref="siteMoreRoot" class="site-more-root">
          <button type="button" class="site-more-trigger" aria-controls="site-more-menu" :aria-expanded="siteMoreOpen" aria-haspopup="true" @click="siteMoreOpen = !siteMoreOpen; accountMenuOpen = false">
            <UiIcon name="menu" /><span>更多</span><UiIcon name="chevron" class="more-chevron" />
          </button>
          <div v-if="siteMoreOpen" id="site-more-menu" class="site-more-panel" aria-label="更多導覽項目">
            <RouterLink v-for="link in overflowSiteLinks" :key="link.to" :to="link.to" @click="siteMoreOpen = false">
              <UiIcon :name="navIcon(link.to)" /><span>{{ link.label }}</span>
            </RouterLink>
          </div>
        </div>
      </nav>
      <div class="header-actions">
        <template v-if="!session.user">
          <RouterLink class="guest-login-link" to="/member/login"><UiIcon name="user" /> 登入／註冊</RouterLink>
        </template>
        <GlobalNotificationBell v-if="session.user" :key="session.user.role + session.user.username" />
        <div v-if="session.user" ref="accountRoot" class="header-account-area">
          <button type="button" class="account-trigger" :aria-expanded="accountMenuOpen" aria-haspopup="menu" @click="accountMenuOpen=!accountMenuOpen">
            <span class="account-avatar"><UiIcon name="user" /></span>
            <span class="account-trigger-label"><strong>{{ session.user.displayName || session.user.username }}</strong><small>{{ roleName(session.user.role) }}</small></span>
            <UiIcon name="chevron-down" />
          </button>
          <div v-if="accountMenuOpen" class="header-dropdown account-dropdown" role="menu" aria-label="帳號選單">
            <div class="account-dropdown-heading"><strong>{{ session.user.displayName || session.user.username }}</strong><span>{{ roleName(session.user.role) }}</span></div>
            <RouterLink v-if="['MEMBER','PUBLISHER'].includes(session.user.role)" to="/member/profile" class="account-menu-item" role="menuitem" @click="accountMenuOpen=false"><UiIcon name="user" />個人資料</RouterLink>
            <button type="button" class="account-menu-item logout-item" role="menuitem" @click="accountMenuOpen=false;leave()"><UiIcon name="log-out" />登出帳號</button>
          </div>
        </div>
      </div>
    </div>
  </div>
  <ManagementSidebar v-if="workspace" :open="menuOpen" @close="closeMenu" />
  <PublisherSidebar v-if="isPublisherWorkspace" />
  <main class="support-ui support-page support-scope" :class="[staff ? 'staff-ui' : 'member-ui', { 'auth-page': isAuthPage, 'management-content':workspace, 'publisher-global-content':isPublisherWorkspace }]">
    <p v-if="error" class="error" role="alert">{{ error }}</p><RouterView />
  </main>
  <div v-if="['MEMBER', 'PUBLISHER'].includes(session.user?.role)" class="support-ui support-scope support-friend-skin"><ChatWidget :key="session.user.username" /></div>
</template>

<style scoped>
/* Scoped styles for App.vue; Vue supplies data-v scope. */
.guest-login-link {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 10px 17px;
  border: 1px solid #d4e5ee;
  border-radius: 10px;
  background: #fff;
  color: #17647d;
  font-size: 13px;
  font-weight: 650;
  text-decoration: none;
  white-space: nowrap;
}
.guest-login-link:hover { background: #edf8fc; border-color: #99cedc; }
.guest-login-link :deep(.ui-icon) { width: 17px; height: 17px; }
.integrated-member-nav {
  display: flex;
  align-items: center;
  gap: 1.15rem;
  flex-wrap: wrap;
}
.integrated-member-nav a {
  white-space: nowrap;
}
</style>
