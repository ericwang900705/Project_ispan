<script setup>
import { publisherToast } from '../publisher-notifications'
import UiIcon from './UiIcon.vue'
</script>
<template><Teleport to="body"><div v-if="publisherToast" class="publisher-toast" role="status" aria-live="polite"><UiIcon name="check" /><span>{{ publisherToast }}</span><button aria-label="關閉成功提示" @click="publisherToast = ''"><UiIcon name="close" /></button></div></Teleport></template>
