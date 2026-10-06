<script setup>
import SearchBar from "../components/SearchBar.vue"
import { ref, computed, onMounted, onUnmounted, watch } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import { api, session, timeText } from '../api';
import Conversation from '../components/Conversation.vue'
const router = useRouter(), route = useRoute(), rooms = ref([]), room = ref(null), status = ref('OPEN'), mine = ref(false), page = ref(0), error = ref(''), busy = ref(false), modal = ref(false), formError = ref(''), categories = ref([]), games = ref([]), query = ref(''), search = ref('')
const categoryId = ref(null), gameId = ref(null), subject = ref(''), summary = ref(''), isGame = computed(() => categories.value.find(x => x.id === categoryId.value)?.name === '遊戲')
const own = computed(() => room.value?.adminId === session.user.id), canSend = computed(() => own.value && room.value?.status === 'OPEN')
let alive = true, timer, selection = 0, listRequest = 0
async function load() {
  const request = ++listRequest;
  try {
    const list = await api(`/rooms?status=${status.value}&mine=${mine.value}&page=${page.value}&q=${encodeURIComponent(search.value)}`);
    if (alive && request === listRequest) rooms.value = list;
    if (room.value) {
      const id = room.value.id, token = selection;
      const r = await api('/rooms/' + id);
      if (alive && selection === token) room.value = r
    }
  } catch (e) {
    if (alive) error.value = e.message
  }
}
function searchNow(q) {
  search.value = q;
  page.value = 0;
  load()
}
function pick(r) {
  selection++;
  room.value = r;
  error.value = ''
}
async function action(name) {
  if (name === 'close' && !confirm('確定問題已解決並結束對話？')) return;
  busy.value = true;
  error.value = '';
  try {
    const path = name === 'claim' ? `/admin/rooms/${room.value.id}/claim` : `/rooms/${room.value.id}/close`;
    room.value = await api(path, {
      method : 'POST'
    });
    await load()
  } catch (e) {
    error.value = e.message
  } finally {
    busy.value = false
  }
}
async function searchGames() {
  try {
    games.value = await api('/games?q=' + encodeURIComponent(query.value))
  } catch (e) {
    formError.value = e.message
  }
}
async function openModal() {
  formError.value = '';
  categoryId.value = null;
  gameId.value = null;
  subject.value = '';
  summary.value = '問題：\n\n已確認事項：\n\n已嘗試處理：\n\n待處理事項：';
  query.value = '';
  modal.value = true;
  try {
    categories.value = await api('/categories');
    await searchGames()
  } catch (e) {
    formError.value = e.message
  }
}
async function transfer() {
  busy.value = true;
  formError.value = '';
  try {
    const t = await api(`/admin/rooms/${room.value.id}/escalate`, {
      method : 'POST', body : {
        categoryId : categoryId.value, gameId : isGame.value ? gameId.value : null, subject : subject.value, summary : summary.value
      }
    });
    modal.value = false;
    router.push('/tickets/' + t.id)
  } catch (e) {
    formError.value = e.message
  } finally {
    busy.value = false
  }
}
watch([status, mine], () => {
  page.value = 0;load()
});
watch(page, load);
watch(categoryId, () => gameId.value = null)
onMounted(async() => {
  if (route.query.room) {
    try {
      room.value = await api('/rooms/' + encodeURIComponent(route.query.room))
    } catch (e) {
      error.value = e.message
    }
  }
  load();window.addEventListener('support-refresh', load);timer = setInterval(() => {
    if (document.visibilityState === 'visible') load()
  }, 10000)
});
onUnmounted(() => {
  alive = false;clearInterval(timer);window.removeEventListener('support-refresh', load)
})
</script>

<template>
  <div class="page admin-page">
    <span class="eyebrow">
      FIRST-LINE SUPPORT
    </span>
    <div class="page-title">
      <div>
        <h1>
          即時客服工作台
        </h1>
        <p class="muted">
          先理解問題，需要追蹤時再整理摘要轉成案件。
        </p>
      </div>
      <span class="connection" :class="{live:session.online}">
        {{session.online?'● 訊息連線正常':'○ 重新連線中'}}
      </span>
    </div>
    <p v-if="error" class="error" role="alert">
      {{error}}
    </p>
    <div class="admin-layout">
      <aside class="room-sidebar">
        <div class="room-filters">
          <SearchBar label="搜尋聊天室" placeholder="編號、會員名稱或訊息" @search="searchNow"/>
          <select v-model="status" aria-label="聊天室狀態">
            <option value="OPEN">
              進行中的對話
            </option>
            <option value="CLOSED">
              已結束的對話
            </option>
            <option value="">
              全部對話
            </option>
          </select>
          <label class="checkbox">
            <input type="checkbox" v-model="mine">
            只看我負責的
          </label>
        </div>
        <p v-if="!rooms.length" class="empty">
          目前沒有符合條件的對話
        </p>
        <button v-for="r in rooms" :key="r.id" class="room-row" :class="{selected:room?.id===r.id}" @click="pick(r)">
          <div>
            <strong>
              {{r.memberName}}
            </strong>
            <small>
              #
              {{r.id}}
            </small>
          </div>
          <p>
            {{r.ticketId?'已轉案件 '+r.ticketNo:r.status==='CLOSED'?'已結束':r.adminId?'客服 #'+r.adminId+' 處理中':'等待客服接手'}}
          </p>
          <small>
            {{timeText(r.createdAt)}}
          </small>
        </button>
        <div class="pager">
          <button :disabled="page===0" @click="page--">
            上一頁
          </button>
          <button :disabled="rooms.length<30" @click="page++">
            下一頁
          </button>
        </div>
      </aside>
      <section class="admin-discussion">
        <template v-if="room">
          <div class="discussion-heading">
            <div>
              <h2>
                {{room.memberName}}
              </h2>
              <small class="muted">
                對話 #
                {{room.id}}
                ·
                {{room.status==='OPEN'?'進行中':'已結束'}}
              </small>
            </div>
            <div class="actions">
              <button v-if="room.status==='OPEN'&&!room.adminId" class="primary" :disabled="busy" @click="action('claim')">
                接手對話
              </button>
              <template v-if="canSend">
                <button class="secondary" :disabled="busy" @click="openModal">
                  整理摘要，轉成案件
                </button>
                <button class="text-button" :disabled="busy" @click="action('close')">
                  已解決，結束
                </button>
              </template>
              <RouterLink v-if="room.ticketId" :to="'/tickets/'+room.ticketId" class="primary">
                查看案件
                {{room.ticketNo}}
              </RouterLink>
            </div>
          </div>
          <Conversation :key="room.id" kind="rooms" :id="room.id" :can-send="canSend" :closed-text="room.status==='CLOSED'?'對話已結束，請查看案件或歷史紀錄。':room.adminId?'目前由其他客服負責。':'請先接手對話，再回覆會員。'"/>
        </template>
        <div v-else class="workbench-empty">
          <span class="empty-symbol">
            ✦
          </span>
          <h2>
            選擇一段對話
          </h2>
          <p>
            從左側接手會員的詢問，
            <br>
            讓每個問題都有下一步。
          </p>
        </div>
      </section>
    </div>
  </div>
  <div v-if="modal" class="modal-backdrop">
    <section class="modal" role="dialog" aria-modal="true" aria-labelledby="transfer-title">
      <div class="modal-heading">
        <div>
          <span class="eyebrow">
            ESCALATE TO CASE
          </span>
          <h2 id="transfer-title">
            整理摘要，接續處理
          </h2>
        </div>
        <button class="text-button" :disabled="busy" aria-label="關閉轉案表單" @click="modal=false">
          ✕
        </button>
      </div>
      <p class="muted">
        轉案後，會員會看到案件入口，並在案件頁繼續對話。
      </p>
      <form @submit.prevent="transfer">
        <label>
          問題分類
          <select v-model="categoryId" required>
            <option :value="null" disabled>
              選擇問題類型
            </option>
            <option v-for="c in categories" :value="c.id" :key="c.id">
              {{c.name}}
            </option>
          </select>
        </label>
        <template v-if="isGame">
          <label>
            搜尋遊戲
            <div class="search-game">
              <input v-model="query" maxlength="100" placeholder="輸入遊戲名稱">
              <button type="button" class="secondary" @click="searchGames">
                搜尋
              </button>
            </div>
          </label>
          <label>
            相關遊戲
            <select v-model="gameId" required>
              <option :value="null" disabled>
                選擇遊戲
              </option>
              <option v-for="g in games" :key="g.id" :value="g.id">
                {{g.name}}
              </option>
            </select>
          </label>
        </template>
        <label>
          案件標題
          <input v-model="subject" required maxlength="100" placeholder="以一句話說明會員的問題">
        </label>
        <label>
          客服摘要
          <textarea v-model="summary" required maxlength="8000" rows="8">
          </textarea>
        </label>
        <p v-if="formError" class="error" role="alert">
          {{formError}}
        </p>
        <div class="modal-actions">
          <button type="button" class="secondary" :disabled="busy" @click="modal=false">
            取消
          </button>
          <button class="primary" :disabled="busy||!categoryId||!subject.trim()||!summary.trim()||(isGame&&!gameId)">
            {{busy?'建立案件中…':'建立案件並通知會員 ↗'}}
          </button>
        </div>
      </form>
    </section>
  </div>
</template>
