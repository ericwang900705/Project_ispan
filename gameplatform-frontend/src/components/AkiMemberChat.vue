<script setup>
import { ref } from 'vue'
import { session, connectedFor } from '../api'
import ChatWidget from './ChatWidget.vue'
import '../style.css'
import '../styles/support-friend-skin.css'
const show = ref(false)
</script>
<template><div class="aki-module aki-widget support-ui support-scope support-friend-skin"><ChatWidget v-if="connectedFor('support') && session.user?.role === 'MEMBER'" :key="session.user.id" /><template v-else-if="!connectedFor('support')"><button class="aki-unconnected-chat" @click="show = !show">客服</button><p v-if="show" class="aki-chat-note" role="status">尚未串接</p></template></div></template>
<style>.aki-module.aki-widget { min-height: 0; background: transparent; }.aki-unconnected-chat{position:fixed;right:24px;bottom:24px;z-index:900;padding:12px 22px;border:1px solid #adcddc;border-radius:10px;background:#fff;color:#24677f;cursor:pointer}.aki-chat-note{position:fixed;right:24px;bottom:76px;z-index:900;background:#fff;padding:20px;border:1px solid #d1e2eb;border-radius:12px;color:#24677f}</style>
