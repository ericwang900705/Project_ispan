<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { RouterLink } from 'vue-router'
import { api } from '../api'
import UiIcon from './UiIcon.vue'
const unread = ref(null), failed = ref(false)
let timer, alive = true, run = 0
async function refresh() {
  const id = ++run
  try { const data = await api('/aki/publisher/notifications'); if (alive && id === run) { unread.value = data.unread; failed.value = false } }
  catch { if (alive && id === run) failed.value = true }
}
function visible() { if (document.visibilityState === 'visible') refresh() }
onMounted(() => { refresh(); timer = setInterval(visible, 30000); window.addEventListener('publisher-notifications-changed', refresh); document.addEventListener('visibilitychange', visible) })
onUnmounted(() => { alive = false; clearInterval(timer); window.removeEventListener('publisher-notifications-changed', refresh); document.removeEventListener('visibilitychange', visible) })
</script>
<template><RouterLink class="publisher-notification-bell" to="/aki/publisher/notifications" :title="failed ? '通知暫時無法更新，點擊重試' : '審核通知'" :aria-label="failed ? '審核通知，更新失敗' : `審核通知，${unread ?? '讀取中'} 則未讀`"><UiIcon name="bell" /><span>通知</span><b v-if="unread">{{ unread > 99 ? '99+' : unread }}</b><small v-if="failed">!</small></RouterLink></template>
