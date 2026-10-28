<script setup>
// 後台入口自行宣告樣式依賴，避免換入口或直接開啟管理頁時漏載。
import './styles/shared.js'
import { computed, ref, watch, watchEffect, onMounted, onUnmounted } from 'vue'
import { RouterLink, RouterView, useRouter, useRoute } from 'vue-router'
import { session, logout } from './api'
import { homeFor, roleName, loginFor, canSupport } from './permissions'
import GlobalNotificationBell from './components/GlobalNotificationBell.vue'
import UiIcon from './components/UiIcon.vue'
import ChatWidget from './components/ChatWidget.vue'
import ManagementSidebar from './components/ManagementSidebar.vue'
import StaffWorkspaceBackdrop from './components/StaffWorkspaceBackdrop.vue'

const router = useRouter()
const route = useRoute()
const menuOpen = ref(false)
const error = ref('')
const staff = computed(() => [
  'ADMIN',
  'STAFF',
  'SUPPORT',
  'REVIEWER'
].includes(session.user?.role) || route.path.startsWith('/staff/'))
const workspace = computed(() => canSupport(session.user?.role) || session.user?.role === 'REVIEWER')
const member = computed(() => [
  'MEMBER',
  'PUBLISHER'
].includes(session.user?.role))
const authPage = computed(() => [
  '/staff/login',
  '/staff/register',
  '/member/login'
].includes(route.path))
watchEffect(() => {
  document.body.classList.remove('cart-portal', 'publisher-workspace')
  document.body.classList.add('support-portal')
  document.body.classList.toggle('staff-workspace', staff.value)
  document.body.classList.toggle('staff-background-active', staff.value && !!session.user && !authPage.value)
  document.body.classList.toggle('member-workspace', !staff.value)
})
watch(() => route.fullPath, () => { menuOpen.value = false })
watch(menuOpen, value => {
  document.body.style.overflow = value ? 'hidden' : ''
})
function escape(event) {
  if (event.key === 'Escape') menuOpen.value = false
}
onMounted(() => window.addEventListener('keydown', escape))
onUnmounted(() => {
  window.removeEventListener('keydown', escape);
  document.body.style.overflow = ''
  document.body.classList.remove('staff-background-active')
})
async function leave() {
  const destination = loginFor(session.user?.role)
  try {
    await logout();
    await router.push(destination)
  } catch (e) {
    error.value = e.message
  }
}
</script>

<template>
  <StaffWorkspaceBackdrop v-if="staff && session.user && !authPage" />
  <div v-if="session.user" class="support-ui"
    :class="[staff ? 'staff-ui' : 'member-ui', workspace ? 'workspace-header-shell' : 'site-header-shell']">
    <header class="site-header" :class="{ 'management-header': workspace }">
      <button v-if="workspace" class="sidebar-mobile-toggle" aria-label="開啟管理選單" aria-controls="management-sidebar"
        :aria-expanded="menuOpen" @click="menuOpen = !menuOpen">
        <UiIcon name="menu" />
      </button>
      <RouterLink class="brand" :to="homeFor(session.user.role)">
        <UiIcon name="game" />
        <span>HERO 赫洛</span>
      </RouterLink>
      <span class="header-workspace-title">
        {{ session.user?.role === 'ADMIN' ? '最高管理員工作後台' : session.user?.role === 'STAFF' ? 'Staff 工作後台' :
          session.user?.role === 'REVIEWER' ? '遊戲審核工作區' : staff ? '客服工作後台'
            : '客服中心' }}
      </span>
      <nav v-if="member" class="support-nav">
        <RouterLink to="/member/support">我的案件</RouterLink>
      </nav>
      <div class="header-actions">
        <GlobalNotificationBell v-if="!!session.user" :key="session.user.role + session.user.id" />
        <span>
          {{ session.user.displayName || session.user.username }} · {{ roleName(session.user.role) }}
        </span>
        <button type="button" @click="leave">登出</button>
      </div>
    </header>
  </div>

  <ManagementSidebar v-if="workspace" :open="menuOpen" @close="menuOpen = false" />
  <main class="support-ui support-page support-scope"
    :class="[staff ? 'staff-ui' : 'member-ui', { 'auth-page': authPage, 'management-content': workspace, 'staff-content-layer': staff && !!session.user && !authPage }]">
    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <RouterView />
  </main>
  <div v-if="member" class="support-ui support-scope">
    <ChatWidget :key="session.user.username" />
  </div>
</template>

<style scoped>
.support-nav {
  display: flex;
  gap: 18px;
}

.header-actions {
  display: flex;
  align-items: center;
  gap: 12px;
  margin-left: auto;
}

.header-actions button {
  padding: 7px 14px;
  border: 1px solid #cadce9;
  border-radius: 8px;
  background: white;
  color: #17647d;
}

@media(max-width:700px) {
  .header-workspace-title {
    display: none
  }

  .header-actions span {
    display: none
  }

  .support-nav {
    gap: 10px;
    font-size: 13px
  }
}
</style>
