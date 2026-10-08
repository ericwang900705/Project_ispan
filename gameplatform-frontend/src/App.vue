<script setup>
import { RouterLink, RouterView } from 'vue-router'
import { akiRoutes } from './routes'
import AkiMemberChat from './components/AkiMemberChat.vue'
import PublisherToast from './components/PublisherToast.vue'
const links = [ ['會員客服','/aki/member/support'],['客服工作區','/aki/admin'],['遊戲上架審核','/aki/review'],['評論審核','/aki/review/comments'],['管理員遊戲查詢','/aki/admin/games'],['發行商主頁','/aki/publisher/overview'],['銷售統計','/aki/publisher/sales'],['審核通知','/aki/publisher/notifications'] ]
</script>
<template><header class="aki-preview-header"><strong>客服・審核・發行商</strong><p>功能模組接線預覽</p><nav><RouterLink v-for="[label,path] in links" :key="path" :to="path">{{ label }}</RouterLink></nav></header><RouterView /><AkiMemberChat /><PublisherToast /></template>
<style>.aki-preview-header{padding:24px;background:#fff;color:#244656;font-family:system-ui,"Microsoft JhengHei",sans-serif}.aki-preview-header p{font-size:12px;color:#78909f}.aki-preview-header nav{display:flex;flex-wrap:wrap;gap:16px}.aki-preview-header a{font-size:13px;color:#24677f;text-decoration:none}.aki-preview-header .router-link-active{text-decoration:underline}</style>
