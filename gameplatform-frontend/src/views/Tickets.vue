<script setup>
import '../styles/support.css'
import { canSupport, canReview, canUseTickets, homeFor, roleName } from '../permissions'

import { ref, onMounted, onUnmounted, watch } from 'vue'
import { api, session, statusText, timeText } from '../api'
import SearchBar from '../components/SearchBar.vue'
import UiIcon from '../components/UiIcon.vue'
function openSupport(){window.dispatchEvent(new Event('open-chat'))}

// --- 狀態定義 ---
const rows = ref([])
const status = ref('')
const page = ref(0)
const error = ref('')
const loading = ref(true)
const search = ref('')

let alive = true
let request = 0

// --- 載入案件清單 ---
async function load() {
  const token = ++request
  try {
    const result = await api(`/aki/tickets?status=${status.value}&page=${page.value}&q=${encodeURIComponent(search.value)}`)
    if (alive && token === request) {
      rows.value = result
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
watch(status, () => {
  page.value = 0
  load()
})

watch(page, load)

onMounted(() => {
  load()
  window.addEventListener('support-refresh', load)
})

onUnmounted(() => {
  alive = false
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

    <p v-if="error" class="error">
      {{ error }}
    </p>

    <section v-if="session.user?.role === 'MEMBER'" class="member-help-strip">
      <span class="member-help-icon"><UiIcon name="headset" /></span>
      <div><strong>有新的問題需要協助？</strong><p>先從線上客服詢問，需要追蹤時由客服整理成案件。</p></div>
      <button class="primary small" @click="openSupport">詢問客服 <UiIcon name="arrow" /></button>
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
        <span>案件與問題摘要 <small v-if="session.user?.role === 'MEMBER'">本頁 {{rows.length}} 筆</small></span>
        <span>狀態／更新時間</span>
      </div>

      <p v-if="loading" class="empty">
        載入案件中…
      </p>

      <p v-else-if="!rows.length" class="empty">
        目前沒有符合條件的案件。<br>
        需要協助時，請從線上客服開始詢問。
      </p>

      <RouterLink v-for="t in rows" :key="t.id" :to="'/aki/tickets/' + t.id" class="ticket-row">
        <div>
          <small class="muted">
            {{ t.ticketNo }} · {{ t.categoryName }}
            <template v-if="canSupport(session.user?.role)">
              · {{ t.memberName }}
            </template>
          </small>
          <h3>{{ t.subject }}</h3>
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