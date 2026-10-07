<!-- src/components/FriendsList.vue -->
<!-- 右下角好友名單（暫代版本：只有畫面，資料與功能由隊友負責） -->
<!-- 已放在 App.vue，所有頁面都會出現 -->
<!--
  ★ 隊友要接的地方（搜尋「TODO 隊友」就能找到）：
    1. fetchFriends()     ：改成呼叫後端 API 取得好友清單
    2. fetchMessages()    ：打開聊天視窗時，向後端取得和這位好友的歷史訊息
    3. sendMessage()      ：送出訊息時，把訊息傳到後端
    4. addFriend()        ：「新增好友」按鈕要做的事
  好友資料格式（後端回傳照這個格式，畫面就不用改）：
    { memberId, username, status: 'online' | 'ingame' | 'offline', playing: '遊戲名稱（遊戲中才有）' }
  訊息資料格式：
    { fromMe: true / false, text: '訊息內容', time: '15:03' }
-->
<script setup>
import { ref, computed, onMounted, nextTick } from 'vue'

// 好友名單是否展開：由 App.vue 控制（和客服同時只能開一個）
defineProps({ open: Boolean })
const emit = defineEmits(['toggle'])
// 好友清單
const friends = ref([])
// 搜尋框文字
const keyword = ref('')

// TODO 隊友：改成呼叫後端 API，例如
//   const { data } = await axios.get(`/api/friends/${memberId}`)
//   friends.value = data
// 目前先放假資料，讓畫面看得到效果
async function fetchFriends() {
  friends.value = [
    { memberId: 2, username: '小明', status: 'ingame', playing: 'Counter-Strike 2' },
    { memberId: 3, username: '阿華', status: 'online' },
    { memberId: 4, username: 'Kevin', status: 'online' },
    { memberId: 5, username: '小美', status: 'offline' },
    { memberId: 6, username: '老王', status: 'offline' },
  ]
}

// TODO 隊友：改成向後端取得和這位好友的歷史訊息，例如
//   const { data } = await axios.get(`/api/messages/${myId}/${friend.memberId}`)
//   messages.value = data
// 目前先放一句假訊息，讓畫面看得到效果
async function fetchMessages(friend) {
  messages.value = [{ fromMe: false, text: `嗨！我是${friend.username}`, time: nowTime() }]
}

// TODO 隊友：改成把訊息送到後端，例如
//   await axios.post('/api/messages', { toMemberId: chatFriend.value.memberId, text })
// 目前只放進畫面，重新整理就會不見
async function sendMessage(text) {
  messages.value.push({ fromMe: true, text, time: nowTime() })
}

// TODO 隊友：新增好友要做的事
function addFriend() {
  alert('新增好友（尚未實作）')
}

// ===== 以下是畫面用的，隊友不用改 =====

// 目前正在聊天的好友（null 代表沒開聊天視窗）
const chatFriend = ref(null)
// 聊天視窗裡的訊息
const messages = ref([])
// 聊天輸入框文字
const draft = ref('')
// 訊息區塊（用來自動捲到最下面）
const messageBox = ref(null)

// 現在時間，例如 15:03
function nowTime() {
  return new Date().toLocaleTimeString('zh-TW', { hour: '2-digit', minute: '2-digit', hour12: false })
}

// 捲到最新的訊息
async function scrollToBottom() {
  await nextTick()
  if (messageBox.value) messageBox.value.scrollTop = messageBox.value.scrollHeight
}

// 點好友：打開聊天視窗並載入訊息
async function openChat(friend) {
  chatFriend.value = friend
  draft.value = ''
  await fetchMessages(friend)
  scrollToBottom()
}

// 關閉聊天視窗
function closeChat() {
  chatFriend.value = null
}

// 按送出或 Enter：空白訊息不送
async function send() {
  const text = draft.value.trim()
  if (!text) return
  draft.value = ''
  await sendMessage(text)
  scrollToBottom()
}

// 依搜尋文字過濾
const filtered = computed(() =>
  friends.value.filter((f) => f.username.toLowerCase().includes(keyword.value.toLowerCase()))
)
// 分成上線中（含遊戲中，遊戲中排前面）和離線兩組
const onlineFriends = computed(() =>
  filtered.value.filter((f) => f.status !== 'offline').sort((a, b) => (b.status === 'ingame') - (a.status === 'ingame'))
)
const offlineFriends = computed(() => filtered.value.filter((f) => f.status === 'offline'))
// 上線人數（顯示在收合時的按鈕上）
const onlineCount = computed(() => friends.value.filter((f) => f.status !== 'offline').length)

// 每種狀態顯示的文字
const statusText = { online: '線上', ingame: '遊戲中', offline: '離線' }

onMounted(fetchFriends)
</script>

<template>
  <div class="friends" :class="{ open }">
    <!-- 聊天視窗：點好友後出現在好友名單左邊 -->
    <div v-if="chatFriend" class="chat">
      <!-- 標題：頭像、名稱、狀態、關閉按鈕 -->
      <div class="chat-head">
        <div class="avatar">{{ chatFriend.username.charAt(0) }}<span class="dot" :class="chatFriend.status"></span></div>
        <div class="text">
          <div class="fname">{{ chatFriend.username }}</div>
          <div class="fstatus" :class="chatFriend.status">
            {{ chatFriend.status === 'ingame' ? `正在玩 ${chatFriend.playing}` : statusText[chatFriend.status] }}
          </div>
        </div>
        <button class="close" title="關閉" @click="closeChat">✕</button>
      </div>

      <!-- 訊息：自己的靠右金色、對方的靠左深色 -->
      <div ref="messageBox" class="messages">
        <div v-for="(m, i) in messages" :key="i" class="msg" :class="{ me: m.fromMe }">
          <div class="bubble">{{ m.text }}</div>
          <div class="time">{{ m.time }}</div>
        </div>
      </div>

      <!-- 輸入框：按 Enter 或送出按鈕送出 -->
      <form class="chat-input" @submit.prevent="send">
        <input v-model="draft" :placeholder="`傳訊息給 ${chatFriend.username}`" />
        <button type="submit" :disabled="!draft.trim()">送出</button>
      </form>
    </div>

    <!-- 標題列：點一下展開 / 收合 -->
    <button class="bar" @click="emit('toggle')">
      <span class="dot online"></span>
      <span class="bar-title">好友</span>
      <span class="bar-count">{{ onlineCount }} 人上線</span>
      <span class="arrow">{{ open ? '▾' : '▴' }}</span>
    </button>

    <!-- 展開後的內容 -->
    <div v-if="open" class="panel">
      <!-- 搜尋 + 新增好友 -->
      <div class="tools">
        <input v-model="keyword" placeholder="搜尋好友" />
        <button class="add" title="新增好友" @click="addFriend">＋</button>
      </div>

      <div class="list">
        <!-- 上線中 -->
        <div class="group">上線中（{{ onlineFriends.length }}）</div>
        <div v-for="f in onlineFriends" :key="f.memberId" class="friend" :class="f.status" @click="openChat(f)">
          <!-- 頭像：取名字第一個字 -->
          <div class="avatar">{{ f.username.charAt(0) }}<span class="dot" :class="f.status"></span></div>
          <div class="text">
            <div class="fname">{{ f.username }}</div>
            <!-- 遊戲中顯示正在玩什麼，否則顯示狀態 -->
            <div class="fstatus">{{ f.status === 'ingame' ? f.playing : statusText[f.status] }}</div>
          </div>
        </div>

        <!-- 離線 -->
        <div class="group">離線（{{ offlineFriends.length }}）</div>
        <div v-for="f in offlineFriends" :key="f.memberId" class="friend offline" @click="openChat(f)">
          <div class="avatar">{{ f.username.charAt(0) }}<span class="dot offline"></span></div>
          <div class="text">
            <div class="fname">{{ f.username }}</div>
            <div class="fstatus">離線</div>
          </div>
        </div>

        <p v-if="!filtered.length" class="none">找不到好友</p>
      </div>
    </div>
  </div>
</template>

<!-- 好友名單樣式（暗棕底 + 金色，和網站同色系） -->
<style scoped>
/* 放在 App.vue 右下角欄位的下半部（外框由 App.vue 的 .dock 畫），上面一條線和客服分隔 */
.friends {
  position: relative; width: 100%;
  background: #2a1a14; border-top: 1px solid #6b4e0f; color: #e8dcc8;
}
/* 標題列 */
.bar {
  width: 100%; display: flex; align-items: center; gap: 8px; padding: 10px 14px;
  border: none; background: linear-gradient(180deg, #3a261c, #2a1a14); color: inherit; cursor: pointer;
  font-size: 14px;
}
.bar:hover { background: #3a261c; }
.bar-title { font-weight: bold; color: #d4a64a; }
.bar-count { font-size: 12px; color: #a8988a; }
.arrow { margin-left: auto; color: #d4a64a; }

/* 展開的內容 */
.panel { border-top: 1px solid #6b4e0f; }
.tools { display: flex; gap: 6px; padding: 10px; }
.tools input {
  flex: 1; min-width: 0; padding: 6px 12px; border-radius: 14px; border: 1px solid #6b4e0f;
  background: #1b0f0d; color: #e8dcc8; font-size: 13px; outline: none;
}
.tools input:focus { border-color: #d4a64a; }
.add { width: 30px; border: none; border-radius: 50%; background: #d4a64a; color: #1b0f0d; font-size: 16px; font-weight: bold; cursor: pointer; }
.add:hover { filter: brightness(1.15); }

/* 好友清單：最高 360px，超過可捲動 */
.list { max-height: 360px; overflow-y: auto; padding-bottom: 8px; }
.group { padding: 8px 14px 4px; font-size: 11px; letter-spacing: 1px; color: #a8988a; }
.friend { display: flex; align-items: center; gap: 10px; padding: 6px 14px; cursor: pointer; }
.friend:hover { background: #3a261c; }
.friend.offline { opacity: .5; }

/* 頭像：圓形，右下角一個狀態小圓點 */
.avatar {
  position: relative; width: 34px; height: 34px; border-radius: 50%; flex-shrink: 0;
  display: flex; align-items: center; justify-content: center; font-weight: bold;
  background: linear-gradient(135deg, #6b4e0f, #d4a64a); color: #1b0f0d;
}
.avatar .dot { position: absolute; right: -1px; bottom: -1px; border: 2px solid #2a1a14; }
.text { min-width: 0; }
.fname { font-size: 14px; }
.fstatus { font-size: 12px; color: #a8988a; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
/* 遊戲中：狀態文字用綠色，像在玩遊戲 */
.friend.ingame .fstatus { color: #9bd46a; }

/* 狀態小圓點：線上藍綠、遊戲中綠、離線灰 */
.dot { width: 10px; height: 10px; border-radius: 50%; display: inline-block; }
.dot.online { background: #5fc4d8; }
.dot.ingame { background: #9bd46a; }
.dot.offline { background: #6e6058; }

.none { text-align: center; color: #a8988a; font-size: 13px; }

/* ===== 聊天視窗：貼在好友名單左邊、畫面底部 ===== */
.chat {
  position: absolute; right: calc(100% + 12px); bottom: 0; width: 320px; height: 400px;
  display: flex; flex-direction: column;
  background: #2a1a14; border: 1px solid #6b4e0f; border-bottom: none;
  border-radius: 8px 8px 0 0; box-shadow: 0 -4px 20px rgba(0, 0, 0, .6);
}
/* 標題列 */
.chat-head {
  display: flex; align-items: center; gap: 10px; padding: 10px 12px;
  background: linear-gradient(180deg, #3a261c, #2a1a14); border-bottom: 1px solid #6b4e0f;
  border-radius: 8px 8px 0 0;
}
.chat-head .fname { font-weight: bold; color: #d4a64a; }
.chat-head .fstatus.ingame { color: #9bd46a; }
.close { margin-left: auto; border: none; background: none; color: #a8988a; font-size: 16px; cursor: pointer; }
.close:hover { color: #fff; }

/* 訊息區：可捲動 */
.messages { flex: 1; overflow-y: auto; padding: 12px; display: flex; flex-direction: column; gap: 10px; background: #1b0f0d; }
.msg { display: flex; flex-direction: column; align-items: flex-start; max-width: 80%; }
.msg.me { align-self: flex-end; align-items: flex-end; }
/* 對方的訊息：深色泡泡 */
.bubble { padding: 7px 12px; border-radius: 12px 12px 12px 2px; background: #3a261c; color: #e8dcc8; font-size: 14px; word-break: break-word; }
/* 自己的訊息：金色泡泡 */
.msg.me .bubble { border-radius: 12px 12px 2px 12px; background: #d4a64a; color: #1b0f0d; }
.time { font-size: 10px; color: #6e6058; margin-top: 2px; }

/* 輸入框 */
.chat-input { display: flex; gap: 6px; padding: 10px; border-top: 1px solid #3a261c; }
.chat-input input {
  flex: 1; min-width: 0; padding: 7px 12px; border-radius: 16px; border: 1px solid #6b4e0f;
  background: #1b0f0d; color: #e8dcc8; font-size: 13px; outline: none;
}
.chat-input input:focus { border-color: #d4a64a; }
.chat-input button {
  border: none; border-radius: 16px; padding: 0 14px; font-weight: bold; cursor: pointer;
  background: #d4a64a; color: #1b0f0d;
}
.chat-input button:disabled { background: #4a3a32; color: #a8988a; cursor: default; }

/* 手機：聊天視窗改成蓋在好友名單上方 */
@media (max-width: 700px) {
  .chat { right: 0; bottom: 100%; width: calc(100vw - 16px); max-width: 320px; height: 360px; }
}
</style>
