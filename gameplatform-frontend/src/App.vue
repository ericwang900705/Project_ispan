<script setup>
import { useRouter } from 'vue-router'
import { ref } from 'vue'
import { session, logout } from './api'
import ChatWidget from './components/ChatWidget.vue'
const router = useRouter(), error = ref('')
async function leave() {
  try {
    await logout();
    router.push('/login')
  } catch (e) {
    error.value = e.message
  }
}
</script>

<template>
  <header v-if="session.user" class="site-header">
    <RouterLink class="brand" :to="session.user.role==='ADMIN'?'/admin':'/tickets'">
      森遊
      <span>
        SUPPORT
      </span>
    </RouterLink>
    <nav>
      <RouterLink v-if="session.user.role==='ADMIN'" to="/admin" :class="{'dashboard-nav':true}">
        工作總覽
      </RouterLink>
      <RouterLink to="/tickets">
        {{session.user.role==='ADMIN'?'案件管理':'我的案件'}}
      </RouterLink>
      <RouterLink v-if="session.user.role==='ADMIN'" to="/admin/chats">
        即時客服
      </RouterLink>
    </nav>
    <div class="account">
      <span>
        {{session.user.username}}
      </span>
      <button class="text-button" @click="leave">
        登出
      </button>
    </div>
  </header>
  <p v-if="error" class="error banner">
    {{error}}
  </p>
  <main>
    <RouterView :key="$route.fullPath" />
  </main>
  <ChatWidget v-if="session.user?.role==='MEMBER'" :key="session.user.username" />
  <footer>
    森遊客服中心 · 每一個問題，都有後續。
  </footer>
</template>
