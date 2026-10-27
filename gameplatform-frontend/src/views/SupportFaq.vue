<script setup>
// 會員／訪客皆可開啟的常見問題頁。
import SupportFaqPanel from '../components/SupportFaqPanel.vue'
import SupportAccountHelp from '../components/SupportAccountHelp.vue'
</script>
<template>
  <main class="page sx-faq-page">
    <span class="sx-eyebrow">SUPPORT CENTER</span>
    <h1>客服中心</h1>
    <p class="sx-page-description">查詢常見問題，或讓客服接續協助你。</p>
    <SupportAccountHelp />
    <SupportFaqPanel standalone />
  </main>
</template>
