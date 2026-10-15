<script setup>

import { canSupport, canUseTickets, homeFor, roleName } from '../permissions'

import { ref, computed, onMounted, onUnmounted, watch } from 'vue'
import { api, session, statusText, timeText } from '../api'
import SearchBar from '../components/SearchBar.vue'
import UiIcon from '../components/UiIcon.vue'
import { priorityLabel, waitingLabel, elapsedLabel } from '../utils/ticketEnhancements'
function openSupport() {
  window.dispatchEvent(new Event('open-chat'))
}

// --- 狀態定義 ---
const rows = ref([])
const status = ref('')
const page = ref(0)
const error = ref('')
const loading = ref(true)
const search = ref('')
const categoryId = ref('')
const categories = ref([])
const priority = ref('')
const waitingFor = ref('')
const sortBy = ref('NEWEST')
const categorySearch = ref('')
const visibleCategories = computed(() =>
  categories.value.filter(c =>
    c.name.includes(categorySearch.value.trim())
  )
)
const now = ref(Date.now())
let ticker

let alive = true
let request = 0

// --- 載入案件清單 ---
async function load() {
  const token = ++request
  try {
    const params = new URLSearchParams({
      status: status.value,
      page: String(page.value),
      q: search.value
    })
    if (categoryId.value) params.set('categoryId', categoryId.value)
    if (priority.value) params.set('priority', priority.value)
    if (waitingFor.value) params.set('waitingFor', waitingFor.value)
    const result = await api(`/tickets?${params}`)
    if (alive && token === request) {
      rows.value = [...result].sort((a, b) => {
        if (sortBy.value === 'PRIORITY') {
          const levels = {
            URGENT: 4,
            HIGH: 3,
            NORMAL: 2,
            LOW: 1
          };
          return (levels[b.priority] || 0) - (levels[a.priority] || 0)
        }
        const date = x =>
          new Date(x.waitingSince || x.createdAt || 0).getTime()
        return sortBy.value === 'OLDEST' ? date(a) - date(b) : date(b) - date(a)
      })
      error.value = ''
    }
  } catch (e) {
    if (alive) {
      error.value = e.message
    }
  } finally {
    loading.value = false
  }
}

function searchNow(q) {
  search.value = q
  page.value = 0
  load()
}

// --- 偵聽器與生命週期 ---
watch([status, categoryId, priority, waitingFor, sortBy], () => {
  page.value = 0
  load()
})

watch(page, load)

onMounted(() => {
  api('/categories').then(data => { if (alive) categories.value = data }).catch(() => { })
  load()
  ticker = setInterval(() => { now.value = Date.now() }, 30000)
  window.addEventListener('support-refresh', load)
})

onUnmounted(() => {
  alive = false
  clearInterval(ticker)
  window.removeEventListener('support-refresh', load)
})
</script>

<template>
  <div class="page tickets-page">
    <span class="eyebrow">CASE TRACKING</span>

    <div class="page-title">
      <div>
        <h1>
          {{ canSupport(session.user?.role) ? '客服案件管理' : '我的客服案件' }}
        </h1>
        <p class="muted">
          {{ canSupport(session.user?.role) ? '客服已整理問題重點，你可以在案件裡繼續對話。' : '查看處理進度、補充問題資料，讓客服接續協助你。' }}
        </p>
      </div>

      <label class="filter">
        案件狀態
        <select v-model="status" aria-label="案件狀態">
          <option value="">全部狀態</option>
          <option value="OPEN">待處理</option>
          <option value="IN_PROGRESS">處理中</option>
          <option value="CLOSED">已結案</option>
        </select>
      </label>
    </div>

    <div v-if="canSupport(session.user?.role)" class="ticket-filters">
      <label>分類名稱搜尋
        <input v-model="categorySearch" type="search" placeholder="輸入退款、付款、帳號…" />
      </label>
      <label>案件分類
        <select v-model="categoryId">
          <option value="">全部分類</option>
          <option v-for="c in visibleCategories" :key="c.id" :value="c.id">
            {{ c.name }}
          </option>
        </select>
      </label>
      <label>優先程度
        <select v-model="priority">
          <option value="">全部優先程度</option>
          <option value="URGENT">緊急</option>
          <option value="HIGH">高</option>
          <option value="NORMAL">一般</option>
          <option value="LOW">低</option>
        </select>
      </label>
      <label>等待對象
        <select v-model="waitingFor">
          <option value="">全部</option>
          <option value="ADMIN">等待客服</option>
          <option value="MEMBER">等待會員</option>
        </select>
      </label>
      <label>顯示順序
        <select v-model="sortBy">
          <option value="NEWEST">最新案件優先</option>
          <option value="OLDEST">等待最久優先</option>
          <option value="PRIORITY">緊急程度優先</option>
        </select>
      </label>
      <button type="button" class="secondary"
        @click="status = ''; categoryId = ''; categorySearch = ''; priority = ''; waitingFor = ''; sortBy = 'NEWEST'; searchNow('')">清除篩選</button>
    </div>
    <p v-if="error" class="error">
      {{ error }}
    </p>

    <section v-if="session.user?.role === 'MEMBER'" class="member-help-strip">
      <span class="member-help-icon">
        <UiIcon name="headset" />
      </span>
      <div>
        <strong>有新的問題需要協助？</strong>
        <p>先從線上客服詢問，需要追蹤時由客服整理成案件。</p>
      </div>
      <button class="primary small" @click="openSupport">詢問客服
        <UiIcon name="arrow" />
      </button>
    </section>
    <!-- 搜尋列 -->
    <SearchBar label="搜尋案件" :placeholder="canSupport(session.user?.role) ? '案件編號、標題、摘要或會員名稱' : '案件編號、標題或摘要'"
      @search="searchNow" />

    <p v-if="search" class="search-hint">
      搜尋「{{ search }}」的結果
    </p>

    <!-- 案件列表表格 -->
    <div class="ticket-table">
      <div class="table-head">
        <span>案件與問題摘要
          <small v-if="session.user?.role === 'MEMBER'">
            本頁 {{ rows.length }} 筆
          </small>
        </span>
        <span>狀態／更新時間</span>
      </div>

      <p v-if="loading" class="empty">
        載入案件中…
      </p>

      <p v-else-if="!rows.length" class="empty">
        目前沒有符合條件的案件。<br>
        需要協助時，請從線上客服開始詢問。
      </p>

      <RouterLink v-for="t in rows" :key="t.id" :to="'/tickets/' + t.id" class="ticket-row">
        <div>
          <small class="muted">
            {{ t.ticketNo }} · {{ t.categoryName }}
            <template v-if="canSupport(session.user?.role)">
              · {{ t.memberName }}
            </template>
          </small>
          <h3>{{ t.subject }}</h3>
          <div class="ticket-extra"><span class="priority-pill" :class="t.priority">
              {{ priorityLabel(t.priority) }}
            </span>
            <span class="reply-pill">
              {{ waitingLabel(t) }}
            </span>
            <span v-if="t.status !== 'CLOSED'" class="wait-duration">
              已等待 {{ elapsedLabel(t.waitingSince || t.createdAt, now) }}
            </span>
          </div>
          <p>{{ t.content }}</p>
        </div>

        <div class="ticket-meta">
          <span class="status" :class="t.status">
            {{ t.status === 'CLOSED' ? '已結案' : statusText(t.status) }}
          </span>
          <small>{{ timeText(t.updatedAt) }}</small>
          <span class="ticket-open-link">查看案件 ↗</span>
        </div>
      </RouterLink>
    </div>

    <!-- 分頁導覽 -->
    <div class="pager">
      <button :disabled="page === 0" @click="page--">
        上一頁
      </button>
      <span>第 {{ page + 1 }} 頁</span>
      <button :disabled="rows.length < 30" @click="page++">
        下一頁
      </button>
    </div>
  </div>
</template>