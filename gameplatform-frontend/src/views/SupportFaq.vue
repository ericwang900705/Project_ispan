<script setup>
// 訪客與會員共用的常見問題入口，帳號救援仍可在登入前使用。
import { computed } from 'vue'
import { useRoute } from 'vue-router'
import { session } from '../api'
import SupportFaqPanel from '../components/SupportFaqPanel.vue'
import SupportAccountHelp from '../components/SupportAccountHelp.vue'
import UiIcon from '../components/UiIcon.vue'
const route = useRoute()
const accountTopic = computed(() => route.query.topic === 'account')
</script>
<template>
  <main class="page sx-faq-page faq-center-page">
    <header class="faq-page-hero"><div><span class="faq-page-kicker"><UiIcon name="headset" />HERO 客服中心</span><h1>常見問題</h1><p>從下載更新到訂單付款，先找到適合你的操作說明。</p></div><div class="faq-hero-actions"><RouterLink class="faq-account-entry" :to="{ path: '/faq', query: { topic: 'account' } }">登入遇到問題？</RouterLink><RouterLink class="faq-case-entry" :to="session.user ? '/member/support' : { path: '/member/login', query: { redirect: '/member/support' } }"><UiIcon name="file" />我的案件<UiIcon name="arrow" /></RouterLink></div></header>
    <SupportAccountHelp v-if="accountTopic" />
    <SupportFaqPanel standalone />
    <SupportAccountHelp v-if="!accountTopic" />
  </main>
</template>
<style scoped>
.faq-hero-actions { display: flex; flex-wrap: wrap; align-items: center; gap: 15px; }.faq-account-entry { padding: 10px 0; color: #d5dff2; font-size: 13px; text-underline-offset: 4px; }.faq-account-entry:hover { color: #f1cc83; }
.faq-center-page { width: min(1120px,100%); box-sizing: border-box; margin: 0 auto; padding: 40px 22px 60px; }.faq-page-hero { display: flex; justify-content: space-between; align-items: center; gap: 25px; padding: 15px 0 7px; }.faq-page-kicker { display: inline-flex; align-items: center; gap: 9px; color: #e2bc6e; font-size: 12px; letter-spacing: 1px; }.faq-page-kicker .ui-icon { width: 18px; height: 18px; }.faq-page-hero h1 { margin: 15px 0 10px; font-size: clamp(30px,4vw,42px); font-weight: 750; letter-spacing: 1px; color: #f1f5ff; }.faq-page-hero p { margin: 0; font-size: 15px; color: #bac9e3; line-height: 1.8; }.faq-case-entry { display: inline-flex; align-items: center; justify-content: center; gap: 10px; padding: 12px 18px; min-height: 46px; box-sizing: border-box; border: 1px solid #647294; border-radius: 12px; background: #24314f; color: #f0d799; text-decoration: none; font-size: 14px; font-weight: 650; white-space: nowrap; }.faq-case-entry:hover { border-color: #e4c27c; background: #314365; }.faq-case-entry .ui-icon { width: 18px; height: 18px; }.faq-case-entry:focus-visible { outline: 2px solid #efcc83; outline-offset: 3px; }
@media(max-width:640px) { .faq-center-page { padding: 22px 14px 40px; }.faq-page-hero { align-items: flex-start; flex-direction: column; gap: 17px; }.faq-case-entry { align-self: flex-start; } }
</style>
