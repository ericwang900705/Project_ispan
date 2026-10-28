<script setup>
// 遊戲路由使用獨立外框，不掛載商城導覽列、好友或客服元件。
import { onMounted, onBeforeUnmount } from 'vue'
import { RouterView, useRoute } from 'vue-router'
const route = useRoute()
onMounted(() => {
  document.body.classList.remove('cart-portal', 'support-portal', 'staff-workspace', 'publisher-workspace')
  document.body.classList.add('standalone-game')
})
onBeforeUnmount(() => document.body.classList.remove('standalone-game'))
</script>
<template><RouterView :key="route.params.id" /></template>
<style>
body.standalone-game { margin: 0; min-width: 280px; background: #0b1020; color: #edf2ff; }
body.standalone-game #app { width: 100%; max-width: none; padding: 0; margin: 0; }
</style>
