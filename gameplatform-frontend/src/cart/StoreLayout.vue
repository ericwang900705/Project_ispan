<script setup>
import { computed, onMounted, watch } from 'vue'
import { RouterView, useRoute } from 'vue-router'
import { session } from '../api'
import { useCartStore } from './stores/cart'
import { useGamesStore } from './stores/games'

const route = useRoute()
const store = useGamesStore()
const cart = useCartStore()
watch(() => session.user, async user => {
  if (user && ['MEMBER','PUBLISHER'].includes(user.role)) await Promise.allSettled([cart.fetchCart(),cart.fetchLibrary()])
}, {immediate:true})
// 尚未提供對應後端的頁面，清楚標示目前狀態，避免誤以為交易已可使用。
const unconnected = computed(() => {
  const path = route.path
  if (path === '/point-store') return '點數商店未串接：尚缺商品查詢及兌換後端 API。'
  if (path === '/') return '' // 商城首頁已使用 GitHub 公開遊戲 API
  return ''
})
onMounted(() => { if (!store.loaded && !store.loading) store.fetchGames().catch(() => {}) })
</script>
<template>
  <div class="hero-store-layout integrated-store-layout">
    <p v-if="unconnected" class="store-api-warning" role="status"><strong>串接狀態</strong>｜{{ unconnected }}</p>
    <RouterView />
  </div>
</template>
<style scoped>
/* Scoped styles for cart/StoreLayout.vue; Vue supplies data-v scope. */
.hero-store-layout {
  min-height: 100vh;
  background: transparent;
  color: var(--hero-page-ink);
}
.store-api-warning {
  max-width: 1200px;
  margin: 1.25rem auto 0;
  padding: .85rem 1rem;
  border: 1px solid #e3c7a6;
  border-radius: 12px;
  color: #79501b;
  background: #fff8eb;
  box-shadow: 0 8px 20px rgba(23, 66, 92, 0.05);
}
</style>
