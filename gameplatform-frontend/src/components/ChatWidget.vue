<script setup>
import '../styles/support.css'
import { ref, onMounted, onUnmounted, watch } from 'vue'
import { useRouter } from 'vue-router'
import { api, session, timeText } from '../api'
import SearchBar from './SearchBar.vue'
import Conversation from './Conversation.vue'
import UiIcon from './UiIcon.vue'

const props = defineProps({ docked: Boolean })
const emit = defineEmits(['expanded-change'])
const router = useRouter()

// --- 狀態定義 ---
const expanded = ref(sessionStorage.getItem('support-open-' + session.user?.username) === '1')
const room = ref(null)
const error = ref('')
const busy = ref(false)
const history = ref(false)
const rooms = ref([])
const page = ref(0)
const search = ref('')

let timer = null
let alive = true
let selected = false
let refreshing = false
let selectionVersion = 0
let historyRequest = 0

// --- 偵聽器 ---
watch(expanded, (v) => {
  sessionStorage.setItem('support-open-' + session.user?.username, v ? '1' : '0')
  emit('expanded-change', v)
}, { immediate: true, flush: 'sync' })
defineExpose({ collapse: () => { expanded.value = false } })

// --- 核心方法 ---
async function refresh() {
  if (refreshing) return
  refreshing = true
  const version = selectionVersion

  try {
    const result = selected && room.value
      ? await api(`/rooms/${room.value.id}`)
      : await api('/member/rooms/latest')

    if (alive && version === selectionVersion) {
      room.value = result.room === undefined ? result : result.room
      error.value = ''
    }
  } catch (e) {
    if (alive) error.value = e.message
  } finally {
    refreshing = false
  }
}

async function start() {
  if (busy.value) return
  selectionVersion++
  busy.value = true
  error.value = ''

  try {
    room.value = await api('/member/rooms', { method: 'POST' })
    selected = false
    history.value = false
  } catch (e) {
    error.value = e.message
  } finally {
    busy.value = false
  }
}

async function close() {
  if (!confirm('確定結束這次對話？紀錄會保留，需要其他協助時可開始新對話。')) return

  try {
    room.value = await api(`/rooms/${room.value.id}/close`, { method: 'POST' })
  } catch (e) {
    error.value = e.message
  }
}

async function loadHistory() {
  const request = ++historyRequest
  history.value = true

  try {
    const result = await api(`/rooms?page=${page.value}&q=${encodeURIComponent(search.value)}`)
    if (alive && request === historyRequest) {
      rooms.value = result
    }
  } catch (e) {
    error.value = e.message
  }
}

function searchNow(q) {
  search.value = q
  page.value = 0
  loadHistory()
}

async function pick(r) {
  selectionVersion++
  room.value = r
  selected = true
  history.value = false
  await refresh()
}

function openWidget() {
  expanded.value = true
  refresh()
}

function goTicket() {
  const id = room.value.ticketId
  expanded.value = false
  router.push('/aki/tickets/' + id)
}

// --- 生命週期 ---
onMounted(() => {
  refresh()
  window.addEventListener('open-chat', openWidget)
  window.addEventListener('support-refresh', refresh)

  timer = setInterval(() => {
    if (expanded.value && document.visibilityState === 'visible') {
      refresh()
    }
  }, 5000)
})

onUnmounted(() => {
  alive = false
  clearInterval(timer)
  window.removeEventListener('open-chat', openWidget)
  window.removeEventListener('support-refresh', refresh)
})
</script>

<template>
  <div class="chat-widget" :class="{ docked: props.docked }">
    <!-- 聊天視窗展開按鈕 -->
    <button v-if="!expanded" class="chat-launcher" aria-expanded="false" aria-controls="member-support-panel" @click="openWidget">
      <span><UiIcon name="headset" /></span>
      <strong>線上客服</strong><span class="support-bar-arrow" aria-hidden="true">▴</span>
    </button>

    <!-- 聊天主面板 -->
    <section v-else id="member-support-panel" class="chat-panel" aria-label="線上客服聊天視窗">
      <header class="chat-header">
        <div class="chat-brand"><span class="chat-brand-icon"><UiIcon name="headset" /></span><div>
          <strong>森遊線上客服</strong>
          <small>
            {{ session.online ? '訊息連線正常' : '重新連線中…' }} ·
            {{ room?.adminId ? '客服已接手' : '可先留言等候回覆' }}
          </small>
        </div></div>
        <button aria-label="縮小客服視窗" @click="expanded = false">▾</button>
      </header>

      <div class="chat-toolbar">
        <span>{{ room ? '對話 #' + room.id : '我們在這裡協助你' }}</span>
        <button class="text-button" @click="history ? history = false : loadHistory()">
          {{ history ? '返回對話' : '歷史對話' }}
        </button>
        <button v-if="room?.status === 'OPEN' && !history" class="text-button" @click="close">
          結束
        </button>
      </div>

      <p v-if="error" class="error inline-error" role="alert">
        {{ error }}
      </p>

      <!-- 歷史對話列表 -->
      <div v-if="history" class="chat-history">
        <div class="chat-history-heading"><h3>歷史對話</h3><p>先前的詢問與處理紀錄都在這裡。</p></div>
        <SearchBar label="搜尋歷史對話" placeholder="對話編號或訊息關鍵字" @search="searchNow" />

        <button v-for="r in rooms" :key="r.id" class="chat-history-row" @click="pick(r)">
          <span class="history-row-icon"><UiIcon name="chat" /></span>
          <div><strong>對話 # {{ r.id }}</strong>
          <small>
            {{ r.ticketId ? '已轉案件 ' + r.ticketNo : r.status === 'OPEN' ? '進行中' : '已結束' }} ·
            {{ timeText(r.createdAt) }}
          </small></div><span class="history-row-arrow" aria-hidden="true">›</span>
        </button>

        <p v-if="!rooms.length" class="empty">
          沒有符合條件的聊天紀錄
        </p>

        <div class="pager">
          <button :disabled="page === 0" @click="page--; loadHistory()">上一頁</button>
          <button :disabled="rooms.length < 30" @click="page++; loadHistory()">下一頁</button>
        </div>
      </div>

      <!-- 當前對話內容 -->
      <template v-else-if="room">
        <div v-if="room.ticketId" class="transfer-notice">
          <strong>已轉成案件 {{ room.ticketNo }}</strong>
          <p>客服已整理問題摘要，後續請到案件中回覆。</p>
          <button class="primary small" @click="goTicket">查看案件 ↗</button>
          <button class="text-button" :disabled="busy" @click="start">詢問新問題</button>
        </div>

        <Conversation :key="room.id" kind="rooms" :id="room.id" :can-send="room.status === 'OPEN'"
          :closed-text="room.ticketId ? '此對話已轉案，請到案件頁接續回覆。' : '這次對話已結束，紀錄會保留。'" />

        <div v-if="room.status === 'CLOSED' && !room.ticketId" class="new-chat">
          <button class="primary full" :disabled="busy" @click="start">
            開始新對話
          </button>
        </div>
      </template>

      <!-- 歡迎畫面（無對話時） -->
      <div v-else class="chat-welcome">
        <span class="empty-symbol"><UiIcon name="chat" /></span>
        <h3>先聊聊你的問題</h3>
        <p>
          商品、購買或帳號問題，<br>
          都可以在這裡詢問。
        </p>
        <button class="primary" :disabled="busy" @click="start">
          {{ busy ? '建立中…' : '開始對話' }}
        </button>
      </div>
    </section>
  </div>
</template>