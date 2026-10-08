<script setup>
import { ref, onMounted, onUnmounted } from 'vue'
import { RouterLink, useRouter } from 'vue-router'
import { api } from '../api'
import '../styles/publisher.css'
const router = useRouter(), data = ref(null), page = ref(0), loading = ref(false), busy = ref(false), error = ref('')
let run = 0, timer
const title = n => n.review_type === 'OFF_SHELF' ? (n.comment?.startsWith('[管理員強制下架]') ? '管理員強制下架' : n.decision === 'APPROVED' ? '下架審核通過' : '下架申請退回') : n.decision === 'APPROVED' ? '上架審核通過' : '上架申請退回'
const date = v => v ? new Date(v).toLocaleString('zh-TW', { hour12: false }) : '—'
async function load() {
  const id = ++run; loading.value = true; error.value = ''
  try { const result = await api('/aki/publisher/notifications?page=' + page.value); if (id === run) data.value = result }
  catch (e) { if (id === run) error.value = e.message }
  finally { if (id === run) loading.value = false }
}
async function read(item, navigate = false) {
  if (busy.value) return
  busy.value = true; error.value = ''
  try { await api('/aki/publisher/notifications/' + item.notification_id + '/read', { method: 'POST' }); window.dispatchEvent(new Event('publisher-notifications-changed')); if (navigate) await router.push({path:'/aki/publisher/games',query:{mode:'history',game:item.game_id}}); else await load() }
  catch (e) { error.value = e.message }
  finally { busy.value = false }
}
async function readAll() {
  busy.value = true; error.value = ''
  try { await api('/aki/publisher/notifications/read-all?throughId=' + data.value.latestId, { method: 'POST' }); window.dispatchEvent(new Event('publisher-notifications-changed')); await load() }
  catch (e) { error.value = e.message }
  finally { busy.value = false }
}
function changePage(delta) { page.value += delta; load() }
onMounted(() => { load(); timer = setInterval(() => { if (!busy.value && document.visibilityState === 'visible') load() },30000) })
onUnmounted(() => { run++; clearInterval(timer) })
</script>
<template>
  <section class="publisher-insights platform-dashboard">
    <header class="publisher-page-heading"><div><span class="eyebrow">PUBLISHER CENTER / NOTIFICATIONS</span><h1>審核通知</h1><p class="muted">審核結果與強制下架原因，每30秒更新。已讀狀態會隨帳號保留。</p></div><RouterLink class="secondary" to="/aki/publisher/overview">發行商主頁</RouterLink></header>
    <p v-if="error" class="error" role="alert">{{ error }}</p>
    <section class="publisher-panel"><div class="publisher-notice-toolbar"><strong>{{ data?.unread ?? '—' }} 則未讀 / {{ data?.total ?? '—' }} 則通知</strong><div class="publisher-button-row"><button class="secondary" :disabled="loading || busy" @click="load">重新整理</button><button class="primary" :disabled="!data?.unread || loading || busy" @click="readAll">全部標示已讀</button></div></div>
      <p v-if="loading && !data" role="status">載入中…</p><p v-else-if="data && !data.items.length" class="publisher-empty">目前沒有審核結果通知。送審完成處理後會顯示在這裡。</p>
      <article v-for="item in data?.items" :key="item.notification_id" class="publisher-notice" :class="{unread: !item.publisher_read_at}"><div><span class="publisher-notice-kind" :class="item.decision === 'REJECTED' || item.review_type === 'OFF_SHELF' ? 'attention' : ''">{{ title(item) }}</span><span v-if="!item.publisher_read_at" class="publisher-unread-dot">未讀</span><h2>{{ item.game_name }}</h2><p>{{ item.comment || '未提供補充說明' }}</p><small>{{ date(item.reviewed_at) }}</small></div><div class="publisher-notice-actions"><button class="primary" :disabled="busy" @click="read(item, true)">查看遊戲與紀錄</button><button v-if="!item.publisher_read_at" class="secondary" :disabled="busy" @click="read(item)">標示已讀</button></div></article>
      <div class="publisher-pagination"><button :disabled="!page || loading || busy" @click="changePage(-1)">上一頁</button><span>{{ page + 1 }} / {{ Math.max(1,Math.ceil((data?.total || 0)/20)) }}</span><button :disabled="!data || (page + 1)*20 >= data.total || loading || busy" @click="changePage(1)">下一頁</button></div>
    </section>
  </section>
</template>
