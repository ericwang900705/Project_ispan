<script setup>
import SearchBar from "../components/SearchBar.vue"
import { ref, onMounted, onUnmounted, watch } from 'vue';
import { api, session, statusText, timeText } from '../api'
const rows = ref([]), status = ref(''), page = ref(0), error = ref(''), loading = ref(true), search = ref('')
let alive = true, request = 0
async function load() {
  const token = ++request;
  try {
    const result = await api(`/tickets?status=${status.value}&page=${page.value}&q=${encodeURIComponent(search.value)}`);
    if (alive && token === request) {
      rows.value = result;
      error.value = ''
    }
  } catch (e) {
    if (alive) error.value = e.message
  } finally {
    loading.value = false
  }
}
function searchNow(q) {
  search.value = q;
  page.value = 0;
  load()
}
watch(status, () => {
  page.value = 0;load()
});
watch(page, load)
onMounted(() => {
  load();window.addEventListener('support-refresh', load)
});
onUnmounted(() => {
  alive = false;window.removeEventListener('support-refresh', load)
})
</script>

<template>
  <div class="page">
    <span class="eyebrow">
      CASE TRACKING
    </span>
    <div class="page-title">
      <div>
        <h1>
          {{session.user.role==='ADMIN'?'客服案件管理':'我的客服案件'}}
        </h1>
        <p class="muted">
          客服已整理問題重點，你可以在案件裡繼續對話。
        </p>
      </div>
      <label class="filter">
        案件狀態
        <select v-model="status">
          <option value="">
            全部狀態
          </option>
          <option value="OPEN">
            待處理
          </option>
          <option value="IN_PROGRESS">
            處理中
          </option>
          <option value="CLOSED">
            已結案
          </option>
        </select>
      </label>
    </div>
    <p v-if="error" class="error">
      {{error}}
    </p>
    <SearchBar label="搜尋案件" :placeholder="session.user.role==='ADMIN'?'案件編號、標題、摘要或會員名稱':'案件編號、標題或摘要'" @search="searchNow"/>
    <p v-if="search" class="search-hint">
      搜尋「
      {{search}}
      」的結果
    </p>
    <div class="ticket-table">
      <div class="table-head">
        <span>
          案件與問題摘要
        </span>
        <span>
          狀態／更新時間
        </span>
      </div>
      <p v-if="loading" class="empty">
        載入案件中…
      </p>
      <p v-else-if="!rows.length" class="empty">
        目前沒有符合條件的案件。
        <br>
        需要協助時，請從線上客服開始詢問。
      </p>
      <RouterLink v-for="t in rows" :key="t.id" :to="'/tickets/'+t.id" class="ticket-row">
        <div>
          <small class="muted">
            {{t.ticketNo}}
            ·
            {{t.categoryName}}
            <template v-if="session.user.role==='ADMIN'">
              ·
              {{t.memberName}}
            </template>
          </small>
          <h3>
            {{t.subject}}
          </h3>
          <p>
            {{t.content}}
          </p>
        </div>
        <div class="ticket-meta">
          <span class="status" :class="t.status">
            {{t.status==='CLOSED'?'已結案':statusText(t.status)}}
          </span>
          <small>
            {{timeText(t.updatedAt)}}
          </small>
          <span>
            查看案件 ↗
          </span>
        </div>
      </RouterLink>
    </div>
    <div class="pager">
      <button :disabled="page===0" @click="page--">
        上一頁
      </button>
      <span>
        第
        {{page+1}}
        頁
      </span>
      <button :disabled="rows.length<30" @click="page++">
        下一頁
      </button>
    </div>
  </div>
</template>
