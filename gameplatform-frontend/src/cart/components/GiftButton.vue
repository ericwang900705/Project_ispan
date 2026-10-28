<!-- src/components/GiftButton.vue -->
<!-- 用法：<GiftButton :game-id="game.gameId" :game-name="game.gameName" /> -->
<!-- 點「送禮」→ 輸入對方帳號 → 查到會員後確認 → 以禮物加入購物車，結帳付款後遊戲會進對方的遊戲庫 -->
<script setup>
import { ref } from 'vue'
import { useCartStore } from '@/cart/stores/cart'

const props = defineProps({
  // 遊戲 ID（必填）
  gameId: { type: [String, Number], required: true },
  // 遊戲名稱（顯示在視窗標題）
  gameName: { type: String, default: '' },
})

const cart = useCartStore()
// 送禮視窗是否開啟
const open = ref(false)
// 輸入的受贈者帳號
const username = ref('')
// 查到的受贈者 { memberId, username }，還沒查就是 null
const recipient = ref(null)
// 錯誤訊息（顯示在視窗裡）
const error = ref('')
// 成功訊息
const done = ref('')
// 是否正在查詢帳號
const searching = ref(false)

function show() {
  username.value = ''
  recipient.value = null
  error.value = ''
  done.value = ''
  open.value = true
}

function close() {
  open.value = false
}

// 用帳號查受贈者
async function search() {
  error.value = ''
  recipient.value = null
  searching.value = true
  try {
    recipient.value = await cart.findMember(username.value)
  } catch (e) {
    error.value = e.message
  } finally {
    searching.value = false
  }
}

// 以禮物加入購物車（不能送自己、對方已擁有等檢查由後端負責）
async function addGift() {
  error.value = ''
  try {
    await cart.addGift(props.gameId, recipient.value.memberId)
    done.value = `已將禮物加入購物車，結帳後會送到 ${recipient.value.username} 的遊戲庫`
  } catch (e) {
    error.value = e.message
  }
}
</script>

<template>
  <button class="gift-btn" @click="show">🎁 送禮</button>

  <!-- 送禮視窗：點背景或「關閉」都可以關掉 -->
  <Teleport to="body">
    <div v-if="open" class="mask" @click.self="close">
      <div class="dialog">
        <h3>送禮：{{ gameName }}</h3>

        <template v-if="!done">
          <label for="gift-username">受贈者帳號</label>
          <form class="row" @submit.prevent="search">
            <input id="gift-username" v-model="username" placeholder="例如 test2" autofocus />
            <button type="submit" :disabled="searching || !username.trim()">查詢</button>
          </form>

          <p v-if="recipient" class="found">
            要送給 <b>{{ recipient.username }}</b>（會員 #{{ recipient.memberId }}）嗎？
          </p>
          <p v-if="error" class="error">{{ error }}</p>
        </template>
        <p v-else class="found">{{ done }}</p>

        <div class="actions">
          <button class="ghost" @click="close">{{ done ? '完成' : '取消' }}</button>
          <button v-if="!done" class="primary" :disabled="!recipient || cart.loading" @click="addGift">
            加入購物車
          </button>
        </div>
      </div>
    </div>
  </Teleport>
</template>

<style scoped>
.gift-btn {
  padding: 8px 16px;
  border-radius: 4px;
  font-size: 14px;
  cursor: pointer;
  border: 1px solid #d4a64a;
  background: transparent;
  color: #d4a64a;
}

.gift-btn:hover {
  background: rgba(212, 166, 74, 0.12);
}

.mask {
  position: fixed;
  inset: 0;
  z-index: 1000;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 16px;
  background: rgba(0, 0, 0, 0.6);
}

.dialog {
  width: 100%;
  max-width: 380px;
  padding: 20px;
  border-radius: 8px;
  background: #121a4a;
  border: 1px solid #2e4a9a;
  color: #e4e8f8;
}

.dialog h3 {
  margin: 0 0 14px;
  color: #d4a64a;
  font-size: 18px;
}

.dialog label {
  display: block;
  font-size: 13px;
  color: #98a2c8;
  margin-bottom: 6px;
}

.row {
  display: flex;
  gap: 8px;
}

.row input {
  flex: 1;
  min-width: 0;
  padding: 7px 10px;
  border-radius: 4px;
  font-size: 14px;
  border: 1px solid #2e4a9a;
  background: #0b0c32;
  color: #e4e8f8;
}

.dialog button {
  padding: 7px 14px;
  border-radius: 4px;
  font-size: 14px;
  cursor: pointer;
  border: 1px solid #2e4a9a;
  background: #1c2a66;
  color: #e4e8f8;
}

.dialog button:disabled {
  opacity: 0.5;
  cursor: default;
}

.found {
  margin: 12px 0 0;
  font-size: 14px;
}

.found b {
  color: #d4a64a;
}

.error {
  margin: 12px 0 0;
  font-size: 14px;
  color: #e06060;
}

.actions {
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  margin-top: 18px;
}

.dialog .primary {
  border: none;
  background: #d4a64a;
  color: #0b0c32;
  font-weight: bold;
}
</style>
