<script setup>
import SupportFaqPanel from '../components/SupportFaqPanel.vue'
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
const searchBar = ref(null)
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

function clearFilters() {
  status.value = ''; categoryId.value = ''; categorySearch.value = ''
  priority.value = ''; waitingFor.value = ''; sortBy.value = 'NEWEST'
  // Clear both the actual query and the search field's displayed value.
  searchBar.value?.clear()
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
  <div class="page tickets-page support-case-page">
    <!-- 後台採精簡標題列，不套用共用標題卡片的背景及大面積留白。 -->
    <header v-if="canSupport(session.user?.role)" class="case-admin-heading">
      <div>
        <h1>客服案件管理</h1>
        <p>集中查看會員案件、分類、優先程度與回覆狀態。</p>
      </div>
    </header>
    <section v-else class="case-hero">
      <div class="case-hero-main">
        <span class="eyebrow">CASE TRACKING</span>
        <h1>
          {{ canSupport(session.user?.role) ? '客服案件管理' : '會員客服中心' }}
        </h1>
        <p class="hero-text">
          {{
            canSupport(session.user?.role)
              ? '集中查看會員案件、分類、優先程度與回覆狀態。'
              : '查看處理進度、補充問題資料，讓客服更快接續協助你。'
          }}
        </p>
      </div>

    </section>

    <SupportFaqPanel v-if="['MEMBER', 'PUBLISHER'].includes(session.user?.role)" />

    <section class="case-toolbar">

      <!-- 搜尋案件 -->
      <div class="toolbar-search">
        <span class="toolbar-label">搜尋案件</span>

        <SearchBar ref="searchBar" label="搜尋案件" :placeholder="canSupport(session.user?.role)
          ? '案件編號、標題、摘要或會員名稱'
          : '案件編號、標題或摘要'" @search="searchNow" />
      </div>

      <!-- 案件狀態 -->
      <label class="case-status-filter">
        <span>案件狀態</span>

        <select v-model="status">
          <option value="">全部狀態</option>
          <option value="OPEN">待處理</option>
          <option value="IN_PROGRESS">處理中</option>
          <option value="CLOSED">已結案</option>
        </select>
      </label>

    </section>

    <div v-if="canSupport(session.user?.role)" class="ticket-filters admin-filters">
      <label>
        <span>分類名稱搜尋</span>
        <input v-model="categorySearch" type="search" placeholder="輸入退款、付款、帳號…" />
      </label>

      <label>
        <span>案件分類</span>
        <select v-model="categoryId">
          <option value="">全部分類</option>
          <option v-for="c in visibleCategories" :key="c.id" :value="c.id">
            {{ c.name }}
          </option>
        </select>
      </label>

      <label>
        <span>優先程度</span>
        <select v-model="priority">
          <option value="">全部優先程度</option>
          <option value="URGENT">緊急</option>
          <option value="HIGH">高</option>
          <option value="NORMAL">一般</option>
          <option value="LOW">低</option>
        </select>
      </label>

      <label>
        <span>等待對象</span>
        <select v-model="waitingFor">
          <option value="">全部</option>
          <option value="ADMIN">等待客服</option>
          <option value="MEMBER">等待會員</option>
        </select>
      </label>

      <label>
        <span>顯示順序</span>
        <select v-model="sortBy">
          <option value="NEWEST">最新案件優先</option>
          <option value="OLDEST">等待最久優先</option>
          <option value="PRIORITY">緊急程度優先</option>
        </select>
      </label>

      <div class="case-filter-actions">
        <button type="button" class="secondary clear-filter" @click="clearFilters">
          <UiIcon name="refresh" />清除篩選
        </button>
      </div>
    </div>

    <p v-if="search" class="search-hint">
      搜尋「{{ search }}」的結果
    </p>

    <p v-if="error" class="error">
      {{ error }}
    </p>

    <section class="ticket-table-wrap">
      <div class="ticket-table">
        <div class="table-head">
          <span>案件資訊</span>
          <span>狀態與更新時間</span>
        </div>

        <p v-if="loading" class="empty">
          載入案件中…
        </p>

        <div v-else-if="!rows.length" class="empty-state">
          <div class="empty-icon">📁</div>
          <h3>目前沒有符合條件的案件</h3>
          <p>你可以更換搜尋條件，或從右下角線上客服開始詢問。</p>
        </div>

        <RouterLink v-for="t in rows" :key="t.id"
          :to="(canSupport(session.user?.role) ? '/tickets/' : '/member/support/') + t.id" class="ticket-row">
          <div class="ticket-main">
            <small class="ticket-sub">
              {{ t.ticketNo }} · {{ t.categoryName }}
              <template v-if="canSupport(session.user?.role)">
                · {{ t.memberName }}
              </template>
            </small>

            <h3>{{ t.subject }}</h3>

            <div class="ticket-extra">
              <span class="priority-pill" :class="t.priority">
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

            <span class="ticket-open-link">查看案件 →</span>
          </div>
        </RouterLink>
      </div>
    </section>

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

<style scoped>
.support-case-page .case-admin-heading {
  display: flex;
  align-items: center;
  gap: 14px;
  margin: 0 0 24px;
  padding: 0;
  border: 0;
  border-radius: 0;
  background: none;
  box-shadow: none;
  color: #234456;
}

.support-case-page .case-admin-heading>svg {
  width: 28px;
  height: 28px;
  flex: 0 0 28px;
  color: #188297;
}

.support-case-page .case-admin-heading h1 {
  margin: 0 0 8px;
  font-size: 28px;
  font-weight: 650;
  line-height: 1.3;
  color: #234456;
}

.support-case-page .case-admin-heading p {
  margin: 0;
  font-size: 13px;
  line-height: 1.7;
  color: #61798c;
}

@media (max-width: 760px) {
  .support-case-page .case-admin-heading {
    gap: 10px;
    align-items: flex-start;
  }

  .support-case-page .case-admin-heading h1 {
    font-size: 24px;
  }
}

.support-case-page {
  max-width: 1180px;
  margin: 0 auto;
  padding: 42px 24px 70px;
  color: #e8eeff;
}

/* 上方標題區 */
.case-hero {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 220px;
  gap: 24px;
  align-items: end;
  margin-bottom: 24px;
}

.case-hero-main {
  min-width: 0;
}

.eyebrow {
  display: inline-block;
  margin-bottom: 10px;
  color: #78a9d6;
  font-size: 12px;
  letter-spacing: 2px;
  font-weight: 700;
}

.case-hero h1 {
  margin: 0 0 12px;
  color: #ffffff;
  font-size: 46px;
  font-weight: 800;
  line-height: 1.2;
}

.hero-text {
  margin: 0;
  color: #9eb7de;
  font-size: 16px;
  line-height: 1.8;
}

/* 會員提示 */
.member-help-strip {
  display: flex;
  align-items: center;
  gap: 16px;
  margin-bottom: 24px;
  padding: 18px 22px;
  background: linear-gradient(135deg, #172156, #20306f);
  border: 1px solid #3b4f96;
  border-radius: 18px;
  box-shadow: 0 10px 24px rgb(0 0 0 / 16%);
}

.member-help-icon {
  display: grid;
  place-items: center;
  width: 48px;
  height: 48px;
  border-radius: 14px;
  background: #f0c060;
  color: #16204f;
  flex-shrink: 0;
}

.member-help-strip>div {
  flex: 1;
}

.member-help-strip strong {
  color: #ffffff;
  font-size: 15px;
}

.member-help-strip p {
  margin: 6px 0 0;
  color: #cbd7f3;
  font-size: 13px;
  line-height: 1.7;
}

/* 搜尋與案件狀態外框 */
.case-toolbar {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 150px;
  align-items: end;
  gap: 16px;

  padding: 20px;
  margin-bottom: 24px;

  background: #161d4f;
  border: 1px solid #314079;
  border-radius: 16px;
}

/* 搜尋區與狀態區相同排列 */
.toolbar-search,
.case-status-filter {
  display: flex;
  flex-direction: column;
  gap: 8px;
  min-width: 0;
  margin: 0;
  padding: 0;
}

/* 兩側標題相同高度 */
.toolbar-label,
.case-status-filter>span {
  display: block;
  height: 20px;
  line-height: 20px;
  color: #cbd7f3;
  font-size: 13px;
  font-weight: 600;
}

/* 搜尋表單移除多餘間距 */
.toolbar-search :deep(.support-search) {
  display: block;
  width: 100%;
  margin: 0;
  padding: 0;
}

/* 搜尋框與按鈕 */
.toolbar-search :deep(.support-search-row) {
  display: flex;
  align-items: center;
  gap: 12px;
  margin: 0;
  padding: 0;
}

/* 統一三個控制項高度 */
.toolbar-search :deep(.support-search input),
.toolbar-search :deep(.search-button),
.case-status-filter select {
  box-sizing: border-box;
  height: 46px;
  min-height: 46px;
  margin: 0;
  border-radius: 10px;
  font-size: 14px;
}

/* 搜尋輸入框 */
.toolbar-search :deep(.support-search input) {
  flex: 1;
  min-width: 0;
  width: 100%;
  padding: 0 16px;
  background: #f7f9ff;
  color: #1f2f52;
  border: 1px solid #506198;
}

/* 搜尋按鈕 */
.toolbar-search :deep(.search-button) {
  flex-shrink: 0;
  padding: 0 20px;
}

/* 案件狀態 */
.case-status-filter select {
  display: block;
  width: 100%;
  padding: 0 10px;
  color: #1f2f52;
  background: #f7f9ff;
  border: 1px solid #506198;
}

/* 手機版 */
@media (max-width: 760px) {
  .case-toolbar {
    grid-template-columns: 1fr;
    gap: 16px;
  }
}

/* SearchBar 元件 */
:deep(.support-search) {
  width: 100%;
}

:deep(.support-search-row) {
  display: flex;
  gap: 12px;
  align-items: center;
}

:deep(.support-search input) {
  flex: 1;
  min-width: 0;
  height: 46px;
  padding: 0 16px;
  border: 1px solid #506198;
  border-radius: 12px;
  background: #f7f9ff;
  color: #1f2f52;
  font-size: 14px;
}

:deep(.support-search input::placeholder) {
  color: #6d7ea9;
}

:deep(.support-search .search-button),
:deep(.support-search .clear-button) {
  height: 46px;
  padding: 0 18px;
  border-radius: 12px;
}

/* 篩選列 */
.admin-filters {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 16px;
  margin-bottom: 18px;
  padding: 18px;
  background: linear-gradient(180deg, #161d4f 0%, #121842 100%);
  border: 1px solid #314079;
  border-radius: 18px;
}

.admin-filters label {
  display: flex;
  flex-direction: column;
  gap: 8px;
}

.admin-filters label span {
  color: #b9c8eb;
  font-size: 12px;
  font-weight: 700;
}

.admin-filters input,
.admin-filters select {
  height: 42px;
  border: 1px solid #506198;
  border-radius: 10px;
  background: #f7f9ff;
  color: #1f2f52;
  padding: 0 12px;
  font-size: 14px;
}

.clear-filter {
  align-self: end;
  height: 42px;
  border-radius: 10px;
}

.search-hint {
  margin: 0 0 16px;
  color: #f0c060;
  font-size: 13px;
}

.error {
  margin: 0 0 16px;
  padding: 14px 16px;
  border-radius: 12px;
  background: #4b2535;
  color: #ffd3d3;
}

/* 案件表格區 */
.ticket-table-wrap {
  margin-top: 12px;
}

.ticket-table {
  overflow: hidden;
  border: 1px solid #314079;
  border-radius: 22px;
  background: #f8faff;
  box-shadow: 0 18px 38px rgb(0 0 0 / 18%);
}

.table-head {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 180px;
  gap: 20px;
  padding: 16px 24px;
  background: linear-gradient(180deg, #eef3ff 0%, #e5edff 100%);
  border-bottom: 1px solid #d9e2fa;
  color: #5b6f95;
  font-size: 12px;
  font-weight: 800;
  letter-spacing: 1px;
  text-transform: uppercase;
}

.ticket-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 180px;
  gap: 20px;
  align-items: center;
  padding: 22px 24px;
  border-bottom: 1px solid #e4eafb;
  text-decoration: none;
  transition: background 0.2s ease, transform 0.2s ease;
}

.ticket-row:last-child {
  border-bottom: 0;
}

.ticket-row:hover {
  background: #f1f5ff;
}

.ticket-main {
  min-width: 0;
}

.ticket-sub {
  display: inline-block;
  margin-bottom: 6px;
  color: #7b8cad;
  font-size: 12px;
}

.ticket-row h3 {
  margin: 0 0 10px;
  color: #1c2d57;
  font-size: 19px;
  font-weight: 800;
  line-height: 1.4;
}

.ticket-extra {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 10px;
}

.ticket-row p {
  margin: 0;
  color: #5f6f91;
  font-size: 14px;
  line-height: 1.8;
}

.ticket-meta {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 10px;
  text-align: right;
}

.ticket-meta small {
  color: #6f7fa4;
  font-size: 12px;
}

.ticket-open-link {
  color: #2958d0;
  font-size: 13px;
  font-weight: 700;
}

.status {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 86px;
  padding: 8px 12px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 800;
}

.status.OPEN {
  background: #fff0c9;
  color: #94670d;
}

.status.IN_PROGRESS {
  background: #dff2ff;
  color: #216b91;
}

.status.CLOSED {
  background: #ecedf3;
  color: #677489;
}

.priority-pill,
.reply-pill,
.wait-duration {
  display: inline-flex;
  align-items: center;
  padding: 5px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
  white-space: nowrap;
}

.priority-pill {
  background: #e9eefc;
  color: #38559a;
}

.priority-pill.URGENT {
  background: #ffe2e2;
  color: #b52828;
}

.priority-pill.HIGH {
  background: #fff1d7;
  color: #a86007;
}

.priority-pill.NORMAL {
  background: #e5efff;
  color: #305fb2;
}

.priority-pill.LOW {
  background: #e5f6eb;
  color: #25734d;
}

.reply-pill {
  background: #edf1fb;
  color: #53668f;
}

.wait-duration {
  background: #f5f0dd;
  color: #8a6b16;
}

.empty,
.empty-state {
  padding: 48px 24px;
  text-align: center;
  color: #6c7ea4;
}

.empty-state .empty-icon {
  font-size: 36px;
  margin-bottom: 10px;
}

.empty-state h3 {
  margin: 0 0 10px;
  color: #263761;
  font-size: 20px;
}

.empty-state p {
  margin: 0;
  color: #7181a4;
  line-height: 1.8;
}

/* 分頁 */
.pager {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 14px;
  margin-top: 22px;
}

.pager button {
  min-width: 88px;
  height: 40px;
  padding: 0 16px;
  border: 1px solid #41548f;
  border-radius: 10px;
  background: #182255;
  color: #ffffff;
  cursor: pointer;
}

.pager button:hover:not(:disabled) {
  background: #22306e;
}

.pager button:disabled {
  opacity: 0.4;
  cursor: not-allowed;
}

.pager span {
  color: #b9c8eb;
  font-size: 13px;
}

/* 響應式 */
@media (max-width: 1100px) {

  .case-hero {
    grid-template-columns: 1fr;
  }

  .admin-filters {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 760px) {
  .support-case-page {
    padding: 28px 14px 60px;
  }

  .case-hero h1 {
    font-size: 32px;
  }

  .member-help-strip {
    flex-wrap: wrap;
  }

  .table-head {
    display: none;
  }

  .ticket-row {
    grid-template-columns: 1fr;
    gap: 16px;
  }

  .ticket-meta {
    align-items: flex-start;
    text-align: left;
  }

  .admin-filters {
    grid-template-columns: 1fr;
  }

  :deep(.support-search-row) {
    flex-wrap: wrap;
  }

  :deep(.support-search .search-button),
  :deep(.support-search .clear-button) {
    width: 100%;
  }
}

.support-ui .support-case-page .ticket-filters.admin-filters {
  grid-template-columns: repeat(3, minmax(0, 1fr));
  align-items: end;
}

.support-ui .support-case-page .admin-filters label {
  margin: 0;
  min-width: 0;
}

.support-ui .support-case-page .admin-filters input,
.support-ui .support-case-page .admin-filters select {
  width: 100%;
  min-width: 0;
  margin: 0;
  height: 44px;
}

.support-ui .support-case-page .case-filter-actions {
  grid-column: 1 / -1;
  display: flex;
  justify-content: flex-end;
  border-top: 1px solid #dce8ee;
  padding-top: 14px;
}

.support-ui .support-case-page .case-filter-actions .clear-filter {
  position: static;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 8px;
  width: auto;
  min-height: 44px;
  height: 44px;
  margin: 0;
  padding: 0 18px;
}

@media(max-width:1000px) {
  .support-ui .support-case-page .ticket-filters.admin-filters {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media(max-width:600px) {
  .support-ui .support-case-page .ticket-filters.admin-filters {
    grid-template-columns: minmax(0, 1fr);
  }

  .support-ui .support-case-page .case-filter-actions .clear-filter {
    width: 100%;
  }
}
</style>