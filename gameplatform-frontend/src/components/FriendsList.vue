<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick, watch } from 'vue'
import { api, session, connectFriendEvents } from '../../api'
import UiIcon from '../../components/UiIcon.vue'

const open = ref(false), friends = ref([]), pending = ref([]), rooms = ref([]), results = ref([])
const emit = defineEmits(['expanded-change'])
const search = ref(''), error = ref(''), notice = ref(''), busy = ref(false), connected = ref(false)
const active = ref(null), messages = ref([]), draft = ref(''), selected = ref([]), groupName = ref('')
const box = ref(null), more = ref(false)
const onlineCount = computed(() => friends.value.filter(x => x.online).length)

let alive = true, refreshVersion = 0, historyVersion = 0, searchVersion = 0, stopSocket, timer

watch(open, isOpen => emit('expanded-change', isOpen), { flush: 'sync' })
defineExpose({ collapse: () => { open.value = false } })

function fail(e) { if (alive) error.value = e.message }
function roomName(room) {
  return room.roomType === 'DIRECT' ? (room.members?.find(x => x.memberId !== session.user?.id)?.username || '好友對話')
    : room.roomName || '群組聊天室'
}
async function scroll() {
  await nextTick();
  if (box.value) box.value.scrollTop = box.value.scrollHeight
}
async function history(older = false) {
  const room = active.value, version = ++historyVersion
  if (!room) return
  const before = older && messages.value.length ? '?before=' + messages.value[0].messageId : ''
  try {
    const data = await api(`/chat/rooms/${room.roomId}/history${before}`)
    if (!alive || version !== historyVersion || active.value?.roomId !== room.roomId) return
    messages.value = older ? [...data, ...messages.value] : data
    more.value = data.length === 50
    if (!older) scroll()
  } catch (e) {
    if (alive && version === historyVersion) {
      if ([403, 404].includes(e.status)) { active.value = null; messages.value = [] }
      fail(e)
    }
  }
}
async function refresh() {
  const version = ++refreshVersion
  try {
    const [f, p, r] = await Promise.all([api('/friends/list'), api('/friends/requests/pending'), api('/chat/rooms')])
    if (!alive || version !== refreshVersion) return
    friends.value = f; pending.value = p; rooms.value = r
    if (active.value) {
      const room = r.find(x => x.roomId === active.value.roomId)
      if (!room) { active.value = null; messages.value = []; ++historyVersion }
      else { active.value = room; await history() }
    }
  } catch (e) { if (version === refreshVersion) fail(e) }
}
async function run(action) {
  if (busy.value) return
  busy.value = true; error.value = ''; notice.value = ''
  try {
    const result = await action();
    if (alive) {
      notice.value = result?.message || '';
      await refresh()
    }
  } catch (e) {
    fail(e)
  } finally { if (alive) busy.value = false }
}
async function find() {
  const version = ++searchVersion, q = search.value.trim();
  error.value = ''
  if (q.length < 2) {
    results.value = [];
    error.value = '請至少輸入兩個帳號字元';
    return
  }
  try {
    const data = await api('/friends/search?username=' + encodeURIComponent(q));
    if (alive && version === searchVersion) results.value = data
  } catch (e) {
    if (version === searchVersion) fail(e)
  }
}
function request(id) {
  return run(() => api('/friends/request/' + id, { method: 'POST' }))
}
function respond(id, accept) {
  return run(() => api(`/friends/requests/${id}?accept=${accept}`,
    { method: 'PATCH' }))
}
function remove(friend) {
  if (confirm(`解除與 ${friend.username} 的好友關係？`))
    return run(() => api('/friends/' + friend.memberId, { method: 'DELETE' }))
}
async function choose(room) {
  active.value = room; messages.value = []; draft.value = '';
  await history()
}
function direct(friend) {
  return run(async () => {
    const room = await api('/chat/direct/' + friend.memberId, { method: 'POST' });
    if (alive) active.value = room;
    return room
  })
}
function group() {
  return run(async () => {
    const room = await api('/chat/group', {
      method: 'POST',
      body: { roomName: groupName.value, memberIds: selected.value }
    });
    if (alive) {
      active.value = room;
      selected.value = [];
      groupName.value = ''
    } return room
  })
}
function invite() {
  return run(() => api(`/chat/rooms/${active.value.roomId}/invite`,
    { method: 'POST', body: selected.value }))
}
function leave() {
  if (confirm('確定離開這個聊天室？'))
    return run(() => api(`/chat/rooms/${active.value.roomId}/leave`, { method: 'PATCH' }))
}
function send() {
  const room = active.value, text = draft.value.trim();
  if (!room || !text) return;
  return run(async () => {
    await api(`/chat/rooms/${room.roomId}/messages`, {
      method: 'POST',
      body: { content: text }
    });
    if (alive && active.value?.roomId === room.roomId) draft.value = ''
  })
}
onMounted(() => {
  refresh()
  stopSocket = connectFriendEvents(() => refresh(), value => { if (alive) connected.value = value })
  timer = setInterval(refresh, 10000)
})
onUnmounted(() => {
  alive = false;
  ++refreshVersion;
  ++historyVersion;
  ++searchVersion;
  clearInterval(timer);
  stopSocket?.()
})
</script>
<template>
  <section class="friends">
    <button class="bar" type="button" :aria-expanded="open" aria-controls="member-friends-panel" @click="open = !open">
      <span class="dock-icon">
        <UiIcon name="users" />
      </span>
      <span class="dock-copy"><strong>好友與聊天</strong><small>找好友，一起開始遊戲</small></span>
      <span class="dock-count" :class="{ 'has-online': onlineCount > 0 }"><i aria-hidden="true"></i>{{ onlineCount }}
        人上線</span>
      <span class="dock-chevron" aria-hidden="true">
        <UiIcon name="chevron-down" />
      </span>
    </button>
    <div v-if="open" id="member-friends-panel" class="panel">
      <div class="connection" role="status">
        <span class="connection-label"><i :class="{ connected }" aria-hidden="true"></i>{{ connected ? '即時連線中' : '定期更新中'
          }}</span>
        <button type="button" @click="refresh" aria-label="重新整理好友列表">
          <UiIcon name="refresh" />重新整理
        </button>
      </div>
      <p v-if="error" class="feedback error" role="alert">{{ error }}</p>
      <p v-if="notice" class="feedback" role="status">{{ notice }}</p>

      <form class="tools" @submit.prevent="find">
        <input v-model="search" placeholder="搜尋會員帳號" maxlength="30" aria-label="搜尋會員帳號" />
        <button>搜尋</button>
      </form>
      <div v-for="person in results" :key="person.memberId" class="row">
        <span>{{ person.username }}</span>
        <button :disabled="busy" @click="request(person.memberId)">加好友</button>
      </div>
      <div v-if="pending.length" class="section-title">收到的邀請</div>
      <div v-for="person in pending" :key="person.friendshipId" class="row">
        <span>{{ person.username }}</span>
        <button :disabled="busy" @click="respond(person.friendshipId, true)">接受</button>
        <button :disabled="busy" @click="respond(person.friendshipId, false)">拒絕</button>
      </div>
      <div class="section-title">好友列表</div>
      <div v-if="!friends.length" class="empty friends-empty">
        <UiIcon name="users" /><strong>還沒有加入好友</strong><span>搜尋會員帳號，送出第一份邀請。</span>
      </div>
      <div v-for="friend in friends" :key="friend.memberId" class="row">
        <label class="pick">
          <input v-model="selected" type="checkbox" :value="friend.memberId" :aria-label="'選取 ' + friend.username" />
        </label>
        <button class="person" @click="direct(friend)" :disabled="busy">
          <img v-if="friend.avatarUrl" :src="friend.avatarUrl" alt="" @error="event => event.target.hidden = true" />
          <span> {{ friend.username }}
            <small :class="{ online: friend.online }">
              {{ friend.online ? '在線上' : '離線' }}
            </small>
          </span>
        </button>
        <button class="muted" :disabled="busy" @click="remove(friend)">刪除</button>
      </div>
      <div class="section-title">建立群組</div>
      <p class="group-hint">勾選好友後，輸入群組名稱即可建立。</p>
      <form class="tools group-tools" @submit.prevent="group">
        <input v-model="groupName" placeholder="群組名稱（先勾選好友）" maxlength="100" aria-label="群組名稱" />
        <button :disabled="busy || !selected.length || !groupName.trim()">建立群組</button>
      </form>
      <div class="section-title">我的聊天室</div>
      <p v-if="!rooms.length" class="empty rooms-empty">尚無對話，點選好友即可開始聊天。</p>
      <button v-for="room in rooms" :key="room.roomId" class="room" @click="choose(room)">
        {{ roomName(room) }}
        <small>{{ room.roomType === 'GROUP' ? '群組' : '一對一' }}</small>
      </button>
    </div>
    <section v-if="active && open" class="chat" aria-label="好友聊天">
      <header>
        <strong>{{ roomName(active) }}</strong>
        <button @click="active = null; ++historyVersion">關閉</button>
      </header>
      <div class="room-tools">
        <button v-if="active.roomType === 'GROUP'" :disabled="busy || !selected.length" @click="invite">
          邀請勾選好友
        </button>
        <button :disabled="busy" @click="leave">離開聊天室</button>
      </div>
      <div ref="box" class="messages">
        <button v-if="more" @click="history(true)">載入較早訊息</button>
        <p v-if="!messages.length" class="empty">尚無訊息</p>
        <div v-for="m in messages" :key="m.messageId" class="message" :class="{ me: m.senderId === session.user?.id }">
          <small>{{ m.senderName }}</small>
          <p>{{ m.content }}</p>
          <time>{{ new Date(m.createdAt).toLocaleString('zh-TW') }}</time>
        </div>
      </div>
      <form class="tools" @submit.prevent="send">
        <input v-model="draft" placeholder="輸入訊息" aria-label="聊天訊息" maxlength="2000" />
        <button :disabled="busy || !draft.trim()">送出</button>
      </form>
    </section>
  </section>
</template>
<style scoped>
.friends {
  position: relative;
  color: #173d53;
  background: #fff;
  border: 1px solid #c9dfe9;
  border-radius: 12px;
  box-shadow: 0 8px 28px #123e5b22;
  font-size: 14px
}

.friends button {
  cursor: pointer;
  font: inherit;
  border: 1px solid #cadfe8;
  border-radius: 7px;
  background: #eff8fb;
  color: #17647d;
  padding: 6px 9px
}

.friends button:disabled {
  opacity: .5;
  cursor: default
}

.bar {
  display: flex;
  gap: 12px;
  align-items: center;
  width: 100%;
  padding: 12px !important;
  border: 0 !important;
  background: #17647d !important;
  color: #fff !important
}

.bar span:first-of-type {
  margin-left: auto;
  font-size: 12px
}

.panel {
  max-height: 62vh;
  overflow: auto;
  padding: 10px
}

.connection {
  display: block;
  color: #6a8390
}

.tools {
  display: flex;
  gap: 7px;
  margin: 10px 0
}

.tools input {
  min-width: 0;
  flex: 1;
  border: 1px solid #cadfe8;
  border-radius: 7px;
  padding: 8px
}

.row {
  display: flex;
  gap: 6px;
  align-items: center;
  padding: 7px 0;
  border-bottom: 1px solid #edf3f6
}

.row>span {
  flex: 1
}

.person {
  flex: 1;
  display: flex;
  text-align: left;
  align-items: center;
  gap: 8px;
  background: transparent !important;
  border: 0 !important
}

.person img {
  width: 30px;
  height: 30px;
  object-fit: cover;
  border-radius: 50%
}

.person small {
  display: block;
  font-size: 11px;
  color: #82929b
}

.person .online {
  color: #137d68
}

.pick input {
  width: 16px
}

.section-title {
  font-weight: bold;
  margin: 14px 0 5px
}

.room {
  width: 100%;
  text-align: left;
  margin: 3px 0
}

.room small {
  float: right;
  color: #789
}

.empty {
  color: #789;
  font-size: 12px;
  text-align: center
}

.feedback {
  padding: 8px;
  background: #e8f8f4
}

.error {
  background: #fff0ed;
  color: #a33
}

.chat {
  position: absolute;
  right: calc(100% + 12px);
  bottom: 0;
  width: min(355px, calc(100vw - 28px));
  height: 440px;
  background: #fff;
  border: 1px solid #cadfe8;
  border-radius: 12px;
  display: flex;
  flex-direction: column;
  box-shadow: 0 8px 28px #123e5b22
}

.chat header {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px;
  background: #eff8fb
}

.chat header strong {
  flex: 1
}

.room-tools {
  display: flex;
  gap: 6px;
  padding: 7px
}

.messages {
  overflow: auto;
  flex: 1;
  padding: 12px;
  background: #f4f9fc
}

.message {
  max-width: 85%;
  width: fit-content;
  margin: 8px 0
}

.message.me {
  margin-left: auto
}

.message p {
  margin: 3px 0;
  padding: 8px 10px;
  border-radius: 10px;
  background: #e0eef6;
  white-space: pre-wrap;
  overflow-wrap: anywhere
}

.message.me p {
  background: #17647d;
  color: #fff
}

.message small,
.message time {
  font-size: 10px;
  color: #789
}

.chat>.tools {
  padding: 0 10px
}

.muted {
  font-size: 12px !important
}

@media(max-width:740px) {
  .chat {
    right: 0;
    bottom: 48px;
    height: min(440px, 70vh);
    z-index: 2
  }
}
</style>
