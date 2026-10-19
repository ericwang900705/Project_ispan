<script setup>
import { computed } from 'vue'
import { session } from '../api'
import { hasPermission } from '../permissions'
const links = computed(() => [
  { permission: 'GAME_REVIEW', name: '遊戲審核', to: '/admin/games' },
  { permission: 'SUPPORT_CASES', name: '客服工作總覽', to: '/admin/support' },
  { permission: 'SUPPORT_CASES', name: '即時客服', to: '/admin/chats' },
  { permission: 'SUPPORT_CASES', name: '客服案件', to: '/tickets' }
].filter(item => hasPermission(session.user, item.permission)))
</script>
<template>
  <section class="staff-home">
    <h1>工作後台</h1>
    <p>依最高管理員授予的權限，選擇要使用的功能。</p>
    <nav><RouterLink v-for="item in links" :key="item.to" :to="item.to">{{ item.name }}</RouterLink></nav>
    <p v-if="!links.length">目前沒有可開啟的功能頁面，請聯繫最高管理員確認授權。</p>
    <p v-if="hasPermission(session.user, 'MEMBER_MANAGE') || hasPermission(session.user, 'PUBLISHER_REVIEW')">
      會員管理與發行商審核已具備後端授權，對應的前端頁面尚待串接。
    </p>
  </section>
</template>
<style scoped>
.staff-home { max-width: 1100px; margin: auto; padding: 32px; color: #20384e; }
p { line-height: 1.8; color: #61798c; }
nav { display: flex; flex-wrap: wrap; gap: 16px; margin: 28px 0; }
nav a { background: white; border: 1px solid #c5dce8; border-radius: 10px; padding: 24px; color: #17647d; }
</style>
