<script setup>
import { ref, computed, onMounted } from 'vue'
import axios from 'axios'

// 所有正常顯示中的評論（最新的在前面）
const reviews = ref([])
// 篩選：all（全部）、unreplied（還沒回覆）、negative（不推薦）
const filter = ref('all')
// 正在編輯回覆的評論編號，和回覆框的文字
const editingId = ref(null)
const draft = ref('')
const busy = ref(false)
const error = ref('')

async function load() {
  // GET /api/developer/reviews
  const { data } = await axios.get('/api/developer/reviews')
  reviews.value = data
}
onMounted(load)

const shown = computed(() => reviews.value.filter((r) =>
  filter.value === 'unreplied' ? !r.developerReply
    : filter.value === 'negative' ? !r.recommended
      : true))
const unrepliedCount = computed(() => reviews.value.filter((r) => !r.developerReply).length)

function startReply(r) {
  editingId.value = r.id
  draft.value = r.developerReply ?? ''
  error.value = ''
}

// 送出回覆；空白送出等於刪除回覆
async function sendReply(r) {
  busy.value = true
  error.value = ''
  try {
    // PUT /api/developer/reviews/{id}/reply
    const { data } = await axios.put(`/api/developer/reviews/${r.id}/reply`, { reply: draft.value })
    Object.assign(r, data)
    editingId.value = null
  } catch (e) {
    error.value = e.response?.data?.message ?? '回覆失敗'
  } finally {
    busy.value = false
  }
}

// 2026-10-13T15:03:21 → 2026-10-13
const day = (v) => v?.slice(0, 10)
</script>

<template>
  <section class="dev-reviews">
    <h1>評論管理 <small>（{{ reviews.length }} 則，{{ unrepliedCount }} 則未回覆）</small></h1>

    <div class="tabs">
      <button :class="{ on: filter === 'all' }" @click="filter = 'all'">全部</button>
      <button :class="{ on: filter === 'unreplied' }" @click="filter = 'unreplied'">未回覆</button>
      <button :class="{ on: filter === 'negative' }" @click="filter = 'negative'">不推薦</button>
    </div>

    <p v-if="!shown.length" class="empty">沒有符合的評論</p>

    <article v-for="r in shown" :key="r.id" class="review">
      <div class="verdict" :class="{ no: !r.recommended }">{{ r.recommended ? '👍 推薦' : '👎 不推薦' }}</div>
      <div class="body">
        <div class="meta">
          <b>{{ r.gameName }}</b>
          <span>{{ r.username }}</span>
          <span class="stars">{{ '★'.repeat(r.rating) }}<i>{{ '★'.repeat(5 - r.rating) }}</i></span>
          <span class="date">{{ day(r.createdAt) }}</span>
        </div>
        <p v-if="r.content" class="content">{{ r.content }}</p>

        <!-- 已經回覆過：顯示回覆內容 -->
        <div v-if="r.developerReply && editingId !== r.id" class="reply">
          <span class="reply-label">開發商回覆 · {{ day(r.repliedAt) }}</span>
          <p>{{ r.developerReply }}</p>
        </div>

        <!-- 回覆框 -->
        <div v-if="editingId === r.id" class="editor">
          <textarea v-model="draft" rows="3" maxlength="1000" placeholder="回覆會顯示在遊戲頁面的這則評論下面（清空送出等於刪除回覆）"></textarea>
          <p v-if="error" class="error">{{ error }}</p>
          <div class="editor-actions">
            <button class="send" :disabled="busy" @click="sendReply(r)">送出回覆</button>
            <button class="cancel" @click="editingId = null">取消</button>
          </div>
        </div>
        <button v-else class="reply-btn" @click="startReply(r)">{{ r.developerReply ? '修改回覆' : '回覆' }}</button>
      </div>
    </article>
  </section>
</template>

<style scoped>
.dev-reviews h1 { margin: 0 0 12px; font-size: 24px; }
.dev-reviews h1 small { font-size: 14px; color: #98a2c8; font-weight: normal; }
.tabs { display: flex; gap: 8px; margin-bottom: 14px; }
.tabs button {
  padding: 4px 14px; font-size: 13px; border-radius: 14px; cursor: pointer;
  border: 1px solid #2e4a9a; background: #121a4a; color: #98a2c8;
}
.tabs button.on { border-color: #d4a64a; color: #e4e8f8; }
.empty { color: #98a2c8; text-align: center; padding: 32px; }

.review {
  display: flex; margin-bottom: 12px; border-radius: 8px; overflow: hidden;
  background: rgba(18, 26, 74, .85); border: 1px solid #2e4a9a;
}
.verdict {
  width: 92px; flex-shrink: 0; display: flex; align-items: center; justify-content: center;
  font-size: 13px; color: #66c0f4; background: rgba(102, 192, 244, .1);
}
.verdict.no { color: #e06060; background: rgba(224, 96, 96, .1); }
.body { flex: 1; min-width: 0; padding: 12px 16px; }
.meta { display: flex; align-items: baseline; flex-wrap: wrap; gap: 4px 12px; font-size: 13px; color: #98a2c8; }
.meta b { color: #e4e8f8; font-size: 14px; }
.stars { color: #f0c060; letter-spacing: 1px; }
.stars i { font-style: normal; color: #3a4680; }
.content { margin: 8px 0 0; font-size: 14px; white-space: pre-wrap; word-break: break-word; }
.reply { margin-top: 10px; padding: 8px 12px; border-left: 3px solid #d4a64a; background: #121a4a; border-radius: 0 6px 6px 0; }
.reply-label { font-size: 12px; color: #d4a64a; }
.reply p { margin: 4px 0 0; font-size: 14px; white-space: pre-wrap; word-break: break-word; }
.editor { margin-top: 10px; }
.editor textarea {
  width: 100%; box-sizing: border-box; padding: 8px 12px; font: inherit; font-size: 14px; border-radius: 6px;
  border: 1px solid #2e4a9a; background: #121a4a; color: #e4e8f8; outline: none; resize: vertical;
}
.editor textarea:focus { border-color: #d4a64a; }
.error { margin: 6px 0 0; color: #ff8a6a; font-size: 13px; }
.editor-actions { display: flex; gap: 8px; margin-top: 8px; }
.send { padding: 6px 18px; border: none; border-radius: 6px; font-weight: bold; cursor: pointer; color: #1b0f0d; background: linear-gradient(to bottom, #e8be63, #b8892f); }
.send:disabled { opacity: .6; }
.cancel, .reply-btn {
  padding: 4px 14px; font-size: 13px; border-radius: 6px; cursor: pointer;
  border: 1px solid #2e4a9a; background: #121a4a; color: #e4e8f8;
}
.reply-btn { margin-top: 10px; }
.reply-btn:hover, .cancel:hover { border-color: #d4a64a; }

@media (max-width: 600px) {
  .verdict { width: 64px; }
}
</style>
