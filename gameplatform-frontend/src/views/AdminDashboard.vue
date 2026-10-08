<script setup>
import '../styles/support.css'
import { ref, onMounted, onUnmounted, computed, watch } from 'vue'
import { useRouter } from 'vue-router'
import { api, session, timeText } from '../api'
import SearchBar from '../components/SearchBar.vue'
import UiIcon from '../components/UiIcon.vue'

const router = useRouter()

// --- 狀態定義 ---
const counts = ref({
  waitingRooms: 0,
  myRooms: 0,
  waitingTickets: 0,
  myTickets: 0
})

const kind = ref('rooms')
const scope = ref('unassigned')
const search = ref('')
const page = ref(0)
const rows = ref([])
const total = ref(0)
const loading = ref(true)
const error = ref('')
const busy = ref(null)

let alive = true
let request = 0
let timer = null

// --- 卡片選單配置 ---
const cards = [
  {
    key: 'waitingRooms', icon: 'chat',
    title: '待接手聊天',
    kind: 'rooms',
    scope: 'unassigned',
    note: '尚未指派客服'
  },
  {
    key: 'myRooms', icon: 'user',
    title: '我處理中的聊天',
    kind: 'rooms',
    scope: 'mine',
    note: '繼續回覆會員'
  },
  {
    key: 'waitingTickets', icon: 'file',
    title: '待接手案件',
    kind: 'tickets',
    scope: 'unassigned',
    note: '尚未指派負責人'
  },
  {
    key: 'myTickets', icon: 'folder',
    title: '我處理中的案件',
    kind: 'tickets',
    scope: 'mine',
    note: '已接手、尚未結案'
  }
]

// --- 計算標題 ---
const title = computed(() => {
  const titles = {
    unassigned: '等待接手',
    mine: '我正在處理',
    others: '其他客服處理中',
    all: '全部未結束工作'
  }
  return titles[scope.value] || '工作佇列'
})

// --- 載入資料 ---
async function load() {
  const token = ++request
  try {
    const [stats, result] = await Promise.all([
      api('/aki/admin/workload'),
      api(`/aki/admin/queue?kind=${kind.value}&scope=${scope.value}&q=${encodeURIComponent(search.value)}&page=${page.value}`)
    ])

    if (alive && token === request) {
      error.value = ''
      counts.value = stats
      rows.value = result.items
      total.value = result.total
    }
  } catch (e) {
    if (alive && token === request) {
      error.value = e.message
    }
  } finally {
    if (alive && token === request) {
      loading.value = false
    }
  }
}

// --- 互動方法 ---
function choose(card) {
  kind.value = card.kind
  scope.value = card.scope
  page.value = 0
}

function searchNow(q) {
  search.value = q
  page.value = 0
  loading.value = true
  load()
}

function open(item) {
  router.push(
    kind.value === 'rooms'
      ? { path: '/aki/admin/chats', query: { room: item.id } }
      : '/aki/tickets/' + item.id
  )
}

async function claim(item) {
  if (busy.value !== null) return
  busy.value = item.id
  error.value = ''
  const type = kind.value

  try {
    await api(`/aki/admin/${type}/${item.id}/claim`, { method: 'POST' })
    router.push(
      type === 'rooms'
        ? { path: '/aki/admin/chats', query: { room: item.id } }
        : '/aki/tickets/' + item.id
    )
  } catch (e) {
    error.value = e.message
    await load()
  } finally {
    busy.value = null
  }
}

// --- 偵聽器與生命週期 ---
watch([kind, scope], () => {
  page.value = 0
  loading.value = true
  load()
})

watch(page, load)

onMounted(() => {
  load()
  window.addEventListener('support-refresh', load)

  timer = setInterval(() => {
    if (document.visibilityState === 'visible') {
      load()
    }
  }, 10000)
})

onUnmounted(() => {
  alive = false
  request++
  clearInterval(timer)
  window.removeEventListener('support-refresh', load)
})
</script>

<template>
  <div class="page dashboard-page">
    <div class="page-title">
      <div><h1>客服工作總覽</h1><p class="muted">先接手等待中的問題，再接續你負責的對話與案件。</p></div>
      <span class="connection" :class="{ live: session.online }">{{ session.online ? '● 訊息連線正常' : '○ 重新連線中' }}</span>
    </div>
    <div class="work-stats">
      <button v-for="card in cards" :key="card.key" class="work-stat" :class="{ active: kind === card.kind && scope === card.scope }" :aria-pressed="kind === card.kind && scope === card.scope" @click="choose(card)">
        <span class="stat-icon"><UiIcon :name="card.icon" /></span>
        <span class="stat-copy"><span>{{ card.title }}</span><strong>{{ counts[card.key] }}</strong><small>{{ card.note }}</small></span>
      </button>
    </div>
    <section class="work-queue">
      <div class="queue-heading"><h2>工作佇列</h2><RouterLink :to="kind === 'rooms' ? '/aki/admin/chats' : '/aki/tickets'" class="text-button">查看全部與歷史紀錄 ↗</RouterLink></div>
      <div class="queue-toolbar">
        <div class="queue-tabs" aria-label="工作類型">
          <button :class="{ active: kind === 'rooms' }" :aria-pressed="kind === 'rooms'" @click="kind = 'rooms'">即時聊天</button>
          <button :class="{ active: kind === 'tickets' }" :aria-pressed="kind === 'tickets'" @click="kind = 'tickets'">案件</button>
        </div>
        <label class="queue-scope">負責狀態<select v-model="scope" aria-label="負責狀態"><option value="unassigned">待接手</option><option value="mine">我負責的</option><option value="others">其他客服負責</option><option value="all">全部未結束</option></select></label>
        <SearchBar label="搜尋工作" :placeholder="kind === 'rooms' ? '對話編號、會員名稱或訊息' : '案件編號、會員名稱、標題或摘要'" @search="searchNow" />
      </div>
      <div class="queue-caption"><span>{{ title }} · 共 {{ total }} 筆</span><span>較早建立的優先處理</span></div>
      <p v-if="error" role="alert" class="error">{{ error }}</p>
      <p v-if="loading" class="empty">載入工作清單中…</p>
      <div v-else-if="!rows.length" class="queue-empty"><UiIcon name="check" /><h3>{{ search ? '沒有符合搜尋條件的工作' : '目前沒有這類待處理工作' }}</h3><p>可切換工作類型或負責狀態查看其他項目。</p></div>
      <div v-else class="queue-table-wrap">
        <table class="queue-table"><thead><tr><th scope="col">類型</th><th scope="col">會員／問題</th><th scope="col">建立時間</th><th scope="col">負責狀態</th><th scope="col">操作</th></tr></thead>
          <tbody><tr v-for="item in rows" :key="kind + item.id">
            <td><span class="queue-kind"><UiIcon :name="kind === 'rooms' ? 'chat' : 'file'" />{{ kind === 'rooms' ? '即時聊天' : '案件' }}</span></td>
            <td><strong>{{ item.memberName }}</strong><p>{{ kind === 'rooms' ? '即時客服詢問 · #' + item.id : item.subject }}</p><small v-if="kind === 'tickets'" class="muted">{{ item.ticketNo }} · {{ item.categoryName }}</small></td>
            <td class="queue-time">{{ timeText(item.createdAt) }}</td>
            <td><span class="assignment-pill" :class="{ waiting: !item.adminId }">{{ !item.adminId ? '待接手' : item.adminId === session.user?.id ? '我處理中' : '客服 #' + item.adminId }}</span></td>
            <td><button v-if="!item.adminId" class="primary small" :disabled="busy !== null" @click="claim(item)">{{ busy === item.id ? '接手中…' : '接手' }}</button><button v-else class="secondary small" @click="open(item)">{{ item.adminId === session.user?.id ? '繼續處理' : '查看內容' }}</button></td>
          </tr></tbody>
        </table>
      </div>
      <p v-if="rows.length" class="mobile-table-hint">左右滑動表格，可查看負責狀態與操作。</p>
      <div class="pager"><button :disabled="page === 0" @click="page--">上一頁</button><span>第 {{ page + 1 }} 頁</span><button :disabled="(page + 1) * 30 >= total" @click="page++">下一頁</button></div>
    </section>
    <p class="work-explanation">轉案預設由原客服繼續負責，會列在「我處理中的案件」。</p>
  </div>
</template>
