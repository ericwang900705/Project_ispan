<script setup>
import { ref, onMounted, onUnmounted, computed, watch } from 'vue'
import { useRouter } from 'vue-router'
import { api, session, timeText } from '../api'
import SearchBar from '../components/SearchBar.vue'
const router = useRouter(), counts = ref({
  waitingRooms : 0, myRooms : 0, waitingTickets : 0, myTickets : 0
})
const kind = ref('rooms'), scope = ref('unassigned'), search = ref(''), page = ref(0), rows = ref([]), total = ref(0)
const loading = ref(true), error = ref(''), busy = ref(null)
let alive = true, request = 0, timer
const cards = [
{
  key : 'waitingRooms', title : '待接手即時聊天', kind : 'rooms', scope : 'unassigned', note : '尚未指派客服'
}, {
  key : 'myRooms', title : '我接手的聊天', kind : 'rooms', scope : 'mine', note : '繼續回覆會員'
}, {
  key : 'waitingTickets', title : '待接手案件', kind : 'tickets', scope : 'unassigned', note : '尚未指派負責人'
}, {
  key : 'myTickets', title : '我處理中的案件', kind : 'tickets', scope : 'mine', note : '已接手、尚未結案'
}
]
const title = computed(() => ({
  unassigned : '等待接手', mine : '我正在處理', others : '其他客服處理中', all : '全部未結束工作'
}
[scope.value]))
async function load() {
  const token = ++request
  try {
    const [stats, result] = await Promise.all([api('/admin/workload'), api(`/admin/queue?kind=${kind.value}&scope=${scope.value}&q=${encodeURIComponent(search.value)}&page=${page.value}`)])
    if (alive && token === request) {
      counts.value = stats;
      rows.value = result.items;
      total.value = result.total
    }
  } catch (e) {
    if (alive && token === request) error.value = e.message
  } finally {
    if (alive && token === request) loading.value = false
  }
}
function choose(card) {
  kind.value = card.kind;
  scope.value = card.scope;
  page.value = 0
}
function searchNow(q) {
  search.value = q;
  page.value = 0;
  loading.value = true;
  load()
}
function open(item) {
  router.push(kind.value === 'rooms' ? {
    path : '/admin/chats', query : {
      room : item.id
    }
  }
  : '/tickets/' + item.id)
}
async function claim(item) {
  if (busy.value !== null) return
  busy.value = item.id;
  error.value = '';
  const type = kind.value
  try {
    await api(`/admin/${type}/${item.id}/claim`, {
      method : 'POST'
    });
    router.push(type === 'rooms' ? {
      path : '/admin/chats', query : {
        room : item.id
      }
    }
    : '/tickets/' + item.id)
  } catch (e) {
    error.value = e.message;
    await load()
  } finally {
    busy.value = null
  }
}
watch([kind, scope], () => {
  page.value = 0;loading.value = true;load()
});
watch(page, load)
onMounted(() => {
  load();window.addEventListener('support-refresh', load);timer = setInterval(() => {
    if (document.visibilityState === 'visible') load()
  }, 10000)
})
onUnmounted(() => {
  alive = false;request++;clearInterval(timer);window.removeEventListener('support-refresh', load)
})
</script>

<template>
  <div class="page dashboard-page">
    <span class="eyebrow">
      SUPPORT WORKSPACE
    </span>
    <div class="page-title">
      <div>
        <h1>
          客服工作總覽
        </h1>
        <p class="muted">
          先接手等待中的問題，再接續你負責的對話與案件。
        </p>
      </div>
      <span class="connection" :class="{live:session.online}">
        {{session.online?'● 訊息連線正常':'○ 重新連線中'}}
      </span>
    </div>
    <div class="work-stats">
      <button v-for="card in cards" :key="card.key" class="work-stat" :class="{active:kind===card.kind&&scope===card.scope}" @click="choose(card)">
        <span>
          {{card.title}}
        </span>
        <strong>
          {{counts[card.key]}}
        </strong>
        <small>
          {{card.note}}
          <span aria-hidden="true">
            ↗
          </span>
        </small>
      </button>
    </div>
    <p class="work-explanation">
      數量顯示所有未結束工作。轉案預設由原客服繼續負責，會列在「我處理中的案件」。
    </p>
    <section class="work-queue">
      <div class="queue-heading">
        <div>
          <span class="eyebrow">
            WORK QUEUE
          </span>
          <h2>
            {{title}}
          </h2>
        </div>
        <RouterLink :to="kind==='rooms'?'/admin/chats':'/tickets'" class="text-button">
          查看全部與歷史紀錄 ↗
        </RouterLink>
      </div>
      <div class="queue-controls">
        <div class="queue-tabs" aria-label="工作類型">
          <button :class="{active:kind==='rooms'}" :aria-pressed="kind==='rooms'" @click="kind='rooms'">
            即時聊天
          </button>
          <button :class="{active:kind==='tickets'}" :aria-pressed="kind==='tickets'" @click="kind='tickets'">
            案件
          </button>
        </div>
        <label>
          負責狀態
          <select v-model="scope" aria-label="負責狀態">
            <option value="unassigned">
              待接手
            </option>
            <option value="mine">
              我負責的
            </option>
            <option value="others">
              其他客服負責
            </option>
            <option value="all">
              全部未結束
            </option>
          </select>
        </label>
      </div>
      <SearchBar label="搜尋工作" :placeholder="kind==='rooms'?'對話編號、會員名稱或訊息':'案件編號、會員名稱、標題或摘要'" @search="searchNow"/>
      <div class="queue-caption">
        <span>
          {{search?'搜尋「'+search+'」 · ':''}}
          共
          {{total}}
          筆
        </span>
        <span>
          依建立時間排序，較早的優先
        </span>
      </div>
      <p v-if="error" role="alert" class="error">
        {{error}}
      </p>
      <p v-if="loading" class="empty">
        載入工作清單中…
      </p>
      <div v-else-if="!rows.length" class="queue-empty">
        <span class="empty-symbol">
          ✓
        </span>
        <h3>
          {{search?'沒有符合搜尋條件的工作':'目前沒有這類待處理工作'}}
        </h3>
        <p>
          可切換工作類型或負責狀態查看其他項目。
        </p>
      </div>
      <div v-else class="queue-list">
        <article v-for="item in rows" :key="kind+item.id" class="queue-row">
          <div class="queue-main">
            <small class="muted">
              {{kind==='rooms'?'對話 #'+item.id:'案件 '+item.ticketNo}}
              ·
              {{item.memberName}}
            </small>
            <h3>
              {{kind==='rooms'?'即時客服詢問':item.subject}}
            </h3>
            <p v-if="kind==='tickets'">
              {{item.content}}
            </p>
            <small class="muted">
              建立於
              {{timeText(item.createdAt)}}
              <template v-if="item.categoryName">
                ·
                {{item.categoryName}}
              </template>
            </small>
          </div>
          <div class="queue-action">
            <span class="assignment-pill" :class="{waiting:!item.adminId}">
              {{!item.adminId?'尚無人接手':item.adminId===session.user.id?'由我負責':'客服 #'+item.adminId+' 負責'}}
            </span>
            <button v-if="!item.adminId" class="primary" :disabled="busy!==null" @click="claim(item)">
              {{busy===item.id?'接手中…':'接手並處理'}}
            </button>
            <button v-else class="secondary" @click="open(item)">
              {{item.adminId===session.user.id?'繼續處理':'查看內容'}}
              ↗
            </button>
          </div>
        </article>
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
        <button :disabled="(page+1)*30>=total" @click="page++">
          下一頁
        </button>
      </div>
    </section>
  </div>
</template>
