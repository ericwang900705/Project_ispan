<!-- src/components/CustomerService.vue -->
<!-- 右下角客服（暫代版本：只有畫面，資料與功能由隊友負責） -->
<!-- 已放在 App.vue 右下角容器，疊在好友名單上面，所有頁面都會出現 -->
<!--
  ★ 隊友要接的地方（搜尋「TODO 隊友」就能找到）：
    1. fetchMessages()    ：打開客服視窗時，向後端取得和客服的歷史訊息
    2. sendMessage()      ：送出訊息時，把訊息傳到後端
  訊息資料格式：
    { fromMe: true / false, text: '訊息內容', time: '15:03' }
-->
<script setup>
import { ref, nextTick, watch } from 'vue'

// 客服視窗是否打開：由 App.vue 控制（和好友名單同時只能開一個）
const props = defineProps({ open: Boolean })
const emit = defineEmits(['toggle'])
// 聊天視窗裡的訊息
const messages = ref([])
// 聊天輸入框文字
const draft = ref('')
// 訊息區塊（用來自動捲到最下面）
const messageBox = ref(null)

// TODO 隊友：改成向後端取得和客服的歷史訊息，例如
//   const { data } = await axios.get(`/api/support/messages/${myId}`)
//   messages.value = data
// 目前先放一句招呼語，讓畫面看得到效果
async function fetchMessages() {
  messages.value = [{ fromMe: false, text: '您好，這裡是客服中心，請問有什麼可以幫您？', time: nowTime() }]
}

// TODO 隊友：改成把訊息送到後端，例如
//   await axios.post('/api/support/messages', { text })
// 目前只放進畫面，重新整理就會不見
async function sendMessage(text) {
  messages.value.push({ fromMe: true, text, time: nowTime() })
}

// ===== 以下是畫面用的，隊友不用改 =====

// 現在時間，例如 15:03
function nowTime() {
  return new Date().toLocaleTimeString('zh-TW', { hour: '2-digit', minute: '2-digit', hour12: false })
}

// 捲到最新的訊息
async function scrollToBottom() {
  await nextTick()
  if (messageBox.value) messageBox.value.scrollTop = messageBox.value.scrollHeight
}

// 打開時：第一次才載入訊息，並捲到最下面
watch(() => props.open, async (isOpen) => {
  if (!isOpen) return
  if (!messages.value.length) await fetchMessages()
  scrollToBottom()
})

// 按送出或 Enter：空白訊息不送
async function send() {
  const text = draft.value.trim()
  if (!text) return
  draft.value = ''
  await sendMessage(text)
  scrollToBottom()
}
</script>

<template>
  <div class="support" :class="{ open }">
    <!-- 標題列：點一下展開 / 收合 -->
    <button class="bar" @click="emit('toggle')">
      <span class="avatar">客<span class="dot"></span></span>
      <span class="bar-text">
        <span class="bar-title">客服</span>
        <span class="bar-sub">有問題找我們</span>
      </span>
      <span class="arrow">{{ open ? '▾' : '▴' }}</span>
    </button>

    <!-- 展開後的聊天內容 -->
    <template v-if="open">
      <!-- 訊息：自己的靠右金色、客服的靠左深色 -->
      <div ref="messageBox" class="messages">
        <div v-for="(m, i) in messages" :key="i" class="msg" :class="{ me: m.fromMe }">
          <div class="bubble">{{ m.text }}</div>
          <div class="time">{{ m.time }}</div>
        </div>
      </div>

      <!-- 輸入框：按 Enter 或送出按鈕送出 -->
      <form class="chat-input" @submit.prevent="send">
        <input v-model="draft" placeholder="輸入想詢問的問題" />
        <button type="submit" :disabled="!draft.trim()">送出</button>
      </form>
    </template>
  </div>
</template>

<!-- 客服樣式（暗棕底 + 金色，和好友名單同色系） -->
<style scoped>
/* 放在 App.vue 右下角欄位的上半部（外框由 App.vue 的 .dock 畫），寬度跟著容器 */
.support {
  width: 100%; display: flex; flex-direction: column;
  background: #2a1a14; border-radius: 7px 7px 0 0; color: #e8dcc8; overflow: hidden;
}
/* 打開時往上長高，當成聊天視窗 */
.support.open { height: 360px; }

/* 標題列 */
.bar {
  width: 100%; display: flex; align-items: center; gap: 10px; padding: 8px 12px;
  border: none; background: linear-gradient(180deg, #3a261c, #2a1a14); color: inherit; cursor: pointer;
  text-align: left;
}
.bar:hover { background: #3a261c; }
.support.open .bar { border-bottom: 1px solid #6b4e0f; }
.bar-text { display: flex; flex-direction: column; min-width: 0; }
.bar-title { font-size: 14px; font-weight: bold; color: #d4a64a; }
.bar-sub { font-size: 12px; color: #a8988a; }
.arrow { margin-left: auto; color: #d4a64a; }

/* 頭像：圓形，右下角一個線上小圓點 */
.avatar {
  position: relative; width: 30px; height: 30px; border-radius: 50%; flex-shrink: 0;
  display: flex; align-items: center; justify-content: center; font-weight: bold; font-size: 14px;
  background: linear-gradient(135deg, #6b4e0f, #d4a64a); color: #1b0f0d;
}
.dot {
  position: absolute; right: -1px; bottom: -1px; width: 10px; height: 10px; border-radius: 50%;
  background: #5fc4d8; border: 2px solid #2a1a14;
}

/* 訊息區：可捲動 */
.messages { flex: 1; overflow-y: auto; padding: 12px; display: flex; flex-direction: column; gap: 10px; background: #1b0f0d; }
.msg { display: flex; flex-direction: column; align-items: flex-start; max-width: 80%; }
.msg.me { align-self: flex-end; align-items: flex-end; }
/* 客服的訊息：深色泡泡 */
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

/* 手機：只留頭像和標題，打開時矮一點 */
@media (max-width: 700px) {
  .bar-sub { display: none; }
  .support.open { height: 300px; }
}
</style>
