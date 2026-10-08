<script setup>
import { RouterLink } from 'vue-router'
import { session } from '../api'
import UiIcon from '../components/UiIcon.vue'
function ask(){window.dispatchEvent(new Event('open-chat'))}
</script>
<template>
 <section class="member-support-home">
  <div class="member-support-hero"><span class="eyebrow">MEMBER SUPPORT</span><h1>有什麼需要我們協助？</h1><p>你好，{{session.user?.displayName || session.user?.username}}。<br>遊戲、購買或帳號遇到問題，都可以聯絡客服。</p><button class="primary" @click="ask"><UiIcon name="headset" />詢問線上客服 <UiIcon name="arrow" /></button></div>
  <div class="member-support-options"><article><span class="member-option-icon"><UiIcon name="chat" /></span><h2>線上客服</h2><p>直接告訴我們你的問題。需要後續追蹤時，客服會協助整理成案件。</p><button class="text-button" @click="ask">開啟客服視窗 →</button></article><article><span class="member-option-icon"><UiIcon name="file" /></span><h2>我的案件</h2><p>查看處理進度、補充問題資料，並閱讀客服的後續回覆。</p><RouterLink class="text-button" to="/aki/tickets">查看我的案件 →</RouterLink></article></div>
  <p class="member-support-tip">右下角「線上客服」隨時都能展開；縮小視窗後，對話紀錄仍會保留。</p>
 </section>
</template>
