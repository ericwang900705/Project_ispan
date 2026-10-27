<script setup>
import { onMounted, onUnmounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { api, session } from '../api'
import { notificationTarget } from '../notification-target.js'
import UiIcon from './UiIcon.vue'

const router = useRouter()
const root = ref(null)
const opened = ref(false)
const items = ref([])
const unread = ref(0)
const busy = ref(false)
const error = ref('')
let timer

async function refresh() {
  try {
    const result = await api('/notifications')
    items.value = result.items || []
    unread.value = result.unread || 0
    error.value = ''
  } catch (exception) {
    error.value = `無法載入通知：${exception.message}`
  }
}

async function openNotification(item) {
  if (busy.value) return
  busy.value = true
  try {
    if (!item.read_at) {
      await api(`/notifications/${item.notification_id}/read`, { method: 'POST' })
    }
    opened.value = false
    await router.push(notificationTarget(item, session.user?.role))
    await refresh()
  } catch (exception) {
    error.value = exception.message
  } finally {
    busy.value = false
  }
}

async function markAllRead() {
  if (busy.value) return
  busy.value = true
  try {
    await api('/notifications/read-all', { method: 'POST' })
    await refresh()
  } catch (exception) {
    error.value = exception.message
  } finally {
    busy.value = false
  }
}

function closeOutside(event) {
  if (root.value && !root.value.contains(event.target)) opened.value = false
}

function closeEscape(event) {
  if (event.key === 'Escape') opened.value = false
}

function refreshWhenVisible() {
  if (document.visibilityState === 'visible') refresh()
}

onMounted(() => {
  refresh()
  timer = setInterval(refreshWhenVisible, 30000)
  window.addEventListener('support-refresh', refresh)
  window.addEventListener('publisher-notifications-changed', refresh)
  document.addEventListener('visibilitychange', refreshWhenVisible)
  document.addEventListener('click', closeOutside)
  document.addEventListener('keydown', closeEscape)
})

onUnmounted(() => {
  clearInterval(timer)
  window.removeEventListener('support-refresh', refresh)
  window.removeEventListener('publisher-notifications-changed', refresh)
  document.removeEventListener('visibilitychange', refreshWhenVisible)
  document.removeEventListener('click', closeOutside)
  document.removeEventListener('keydown', closeEscape)
})
</script>

<template>
  <div ref="root" class="support-bell-wrap global-bell">
    <button
      class="support-notification-bell"
      type="button"
      aria-label="通知中心"
      :aria-expanded="opened"
      @click="opened = !opened; if (opened) refresh()"
    >
      <UiIcon name="bell" />
      <b v-if="unread" class="global-bell-count">{{ unread > 99 ? '99+' : unread }}</b>
    </button>

    <section v-if="opened" class="support-bell-popover global-bell-panel" aria-label="通知中心">
      <header class="global-bell-heading">
        <div>
          <strong>通知中心</strong>
          <small>{{ unread }} 則未讀通知</small>
        </div>
        <button type="button" :disabled="busy || !unread" @click="markAllRead">全部已讀</button>
      </header>

      <p v-if="error" class="global-bell-error" role="alert">{{ error }}</p>
      <div v-if="!items.length && !error" class="global-bell-empty">
        <UiIcon name="bell" />
        <strong>目前沒有通知</strong>
        <span>客服訊息、案件更新與遊戲審核通知會顯示在這裡。</span>
      </div>

      <button
        v-for="item in items"
        :key="item.notification_id"
        class="support-bell-item global-bell-item"
        :class="{ unread: !item.read_at }"
        type="button"
        :disabled="busy"
        @click="openNotification(item)"
      >
        <span class="global-bell-item-title">
          <span v-if="!item.read_at" class="global-bell-dot" aria-label="未讀" />
          <strong>{{ item.title }}</strong>
        </span>
        <span class="global-bell-body">{{ item.body }}</span>
        <small>{{ item.created_at }}</small>
      </button>
    </section>
  </div>
</template>
