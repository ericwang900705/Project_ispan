<script setup>
import { ref, onMounted, onUnmounted, computed } from 'vue';
import { api, session, timeText, statusText } from '../api';
import Conversation from '../components/Conversation.vue'
const props = defineProps({
  id : String
}), ticket = ref(null), origins = ref([]), origin = ref(null), error = ref(''), busy = ref(false), isAdmin = computed(() => session.user.role === 'ADMIN')
const canSend = computed(() => ticket.value && ticket.value.status !== 'CLOSED' && (!isAdmin.value || ticket.value.adminId === session.user.id))
let alive = true, timer
async function load() {
  try {
    const [t, o] = await Promise.all([api(`/tickets/${props.id}`), api(`/tickets/${props.id}/origins`)]);
    if (alive) {
      ticket.value = t;
      origins.value = o;
      error.value = ''
    }
  } catch (e) {
    if (alive) error.value = e.message
  }
}
async function action(name) {
  if (name === 'close' && !confirm('確定將案件結案？結案後停止新增回覆。')) return;
  busy.value = true;
  try {
    ticket.value = await api(`/admin/tickets/${props.id}/${name}`, {
      method : 'POST'
    })
  } catch (e) {
    error.value = e.message
  } finally {
    busy.value = false
  }
}
onMounted(() => {
  load();window.addEventListener('support-refresh', load);timer = setInterval(() => {
    if (document.visibilityState === 'visible') load()
  }, 10000)
});
onUnmounted(() => {
  alive = false;clearInterval(timer);window.removeEventListener('support-refresh', load)
})
</script>

<template>
  <div class="page">
    <RouterLink to="/tickets" class="back-link">
      ← 返回案件列表
    </RouterLink>
    <p v-if="error" class="error">
      {{error}}
    </p>
    <template v-if="ticket">
      <div class="page-title">
        <div>
          <span class="eyebrow">
            CASE
            {{ticket.ticketNo}}
            ·
            {{ticket.categoryName}}
          </span>
          <h1>
            {{ticket.subject}}
          </h1>
          <p class="muted">
            {{ticket.memberName}}
            · 建立於
            {{timeText(ticket.createdAt)}}
            <template v-if="ticket.gameName">
              ·
              {{ticket.gameName}}
            </template>
          </p>
        </div>
        <span class="status" :class="ticket.status">
          {{ticket.status==='CLOSED'?'已結案':statusText(ticket.status)}}
        </span>
      </div>
      <div class="detail-layout">
        <aside>
          <section class="summary-card">
            <span class="eyebrow">
              客服整理
            </span>
            <h2>
              問題摘要
            </h2>
            <p class="summary-text">
              {{ticket.content}}
            </p>
            <div class="summary-footer">
              負責客服：
              {{ticket.adminId?'#'+ticket.adminId:'等待接手'}}
              <br>
              最後更新：
              {{timeText(ticket.updatedAt)}}
            </div>
          </section>
          <div v-if="isAdmin" class="action-card">
            <button v-if="!ticket.adminId&&ticket.status!=='CLOSED'" class="primary full" :disabled="busy" @click="action('claim')">
              接手案件
            </button>
            <button v-if="ticket.adminId===session.user.id&&ticket.status!=='CLOSED'" class="secondary full" :disabled="busy" @click="action('close')">
              處理完成，結案
            </button>
          </div>
          <button v-for="r in origins" :key="r.id" class="origin-link" @click="origin=origin?.id===r.id?null:r">
            {{origin?.id===r.id?'收起':'查看'}}
            原始聊天 #
            {{r.id}}
            ↗
          </button>
        </aside>
        <section class="discussion-card">
          <div class="discussion-heading">
            <h2>
              案件對話
            </h2>
            <span class="muted">
              在這裡接續回覆
            </span>
          </div>
          <Conversation kind="tickets" :id="ticket.id" :can-send="canSend" :closed-text="ticket.status==='CLOSED'?'此案件已結案，對話紀錄保留。':'請先由負責客服接手此案件。'"/>
        </section>
      </div>
      <section v-if="origin" class="origin-panel">
        <div class="discussion-heading">
          <h2>
            轉案前的聊天 #
            {{origin.id}}
          </h2>
          <button class="text-button" @click="origin=null">
            關閉
          </button>
        </div>
        <Conversation kind="rooms" :id="origin.id" :can-send="false" closed-text="原始聊天僅供查閱，請在案件對話繼續回覆。"/>
      </section>
    </template>
    <p v-else-if="!error" class="empty">
      載入案件中…
    </p>
  </div>
</template>
