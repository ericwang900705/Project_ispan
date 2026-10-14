<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import UiIcon from './UiIcon.vue'
const expanded = ref(false)
const router = useRouter()
function signIn() {
  expanded.value = false
  router.push({ path: '/member/login', query: { support: '1' } })
}
</script>

<template>
  <aside class="guest-support support-ui support-scope support-friend-skin" aria-label="訪客客服入口">
    <div class="chat-widget docked">
      <button v-if="!expanded" class="chat-launcher" aria-expanded="false" aria-controls="guest-support-panel" @click="expanded = true">
        <span><UiIcon name="headset" /></span><strong>線上客服</strong><span class="support-bar-arrow" aria-hidden="true">▴</span>
      </button>
      <section v-else id="guest-support-panel" class="chat-panel" role="region" aria-label="訪客線上客服" @keydown.esc="expanded = false">
        <div class="chat-header">
          <div class="chat-brand"><span class="chat-brand-icon"><UiIcon name="headset" /></span><div><strong>赫洛線上客服</strong><small>商品、購買或帳號問題</small></div></div>
          <button aria-label="縮小訪客客服" @click="expanded = false">▾</button>
        </div>
        <div class="chat-welcome">
          <div class="empty-symbol"><UiIcon name="chat" /></div>
          <h3>需要我們協助嗎？</h3>
          <p>請先登入會員，登入後會自動開啟客服，<br>也能查看自己的對話與案件紀錄。</p>
          <button class="primary" @click="signIn">登入會員並詢問客服</button>
        </div>
      </section>
    </div>
  </aside>
</template>

<style scoped>
/* Scoped styles for components/GuestSupport.vue; Vue supplies data-v scope. */
.guest-support { position: fixed; right: 16px; bottom: 16px; z-index: 1100; max-width: calc(100vw - 32px); }
</style>
