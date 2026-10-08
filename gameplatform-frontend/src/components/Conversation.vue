<script setup>
import { senderTypeFor } from '../permissions'
import { ref, watch, onMounted, onUnmounted, nextTick } from 'vue'
import { api, session, timeText } from '../api'
import TicketImage from './TicketImage.vue'

// --- Props 定義 ---
const props = defineProps({
  kind: {
    type: String,
    required: true
  },
  id: {
    type: Number,
    required: true
  },
  canSend: Boolean,
  closedText: {
    type: String,
    default: '這段對話目前無法回覆。'
  }
})

// --- 狀態定義 ---
const messages = ref([])
const more = ref(false)
const draft = ref('')
const busy = ref(false)
const error = ref('')
const loading = ref(false)
const box = ref(null)

const selectedImages = ref([])
const fileInput = ref(null)

let version = 0
let timer = null
let working = false
let pending = false

const endpoint = () => `/${props.kind}/${props.id}/messages`

// --- 圖片處理方法 ---
function clearImages() {
  selectedImages.value.forEach(image => URL.revokeObjectURL(image.url))
  selectedImages.value = []
  if (fileInput.value) fileInput.value.value = ''
}

function removeImage(index) {
  URL.revokeObjectURL(selectedImages.value[index].url)
  selectedImages.value.splice(index, 1)
}

function chooseImages(event) {
  const files = Array.from(event.target.files || [])
  event.target.value = ''

  if (busy.value || !props.canSend || props.kind !== 'tickets') return

  if (selectedImages.value.length + files.length > 3) {
    error.value = '每則訊息最多 3 張圖片。'
    return
  }

  for (const file of files) {
    if (!['image/jpeg', 'image/png'].includes(file.type) || file.size === 0 || file.size > 5 * 1024 * 1024) {
      error.value = '請選擇 JPG、PNG 圖片，每張最多 5 MB。'
      return
    }
  }

  error.value = ''
  selectedImages.value.push(...files.map(file => ({ file, url: URL.createObjectURL(file) })))
}

// --- 資料合併與同步方法 ---
function merge(items) {
  const m = new Map(messages.value.map(x => [x.id, x]))
  items.forEach(x => m.set(x.id, x))
  messages.value = [...m.values()].sort((a, b) => a.id - b.id)
}

async function refresh(initial = false) {
  if (working) {
    pending = true
    return
  }
  working = true

  const v = version
  const base = endpoint()
  loading.value = initial

  try {
    if (initial || !messages.value.length) {
      const page = await api(base)
      if (v !== version) return
      messages.value = page.items
      more.value = page.hasMore
    } else {
      let extra = true
      while (extra) {
        const after = messages.value.at(-1)?.id || 0
        const page = await api(`${base}?after=${after}`)
        if (v !== version) return
        merge(page.items)
        extra = page.hasMore
      }
    }

    error.value = ''
    await nextTick()
    if (initial && box.value) {
      box.value.scrollTop = box.value.scrollHeight
    }
  } catch (e) {
    if (v === version) error.value = e.message
  } finally {
    working = false
    loading.value = false
    if (pending) {
      pending = false
      refresh()
    }
  }
}

async function older() {
  const v = version
  const base = endpoint()

  try {
    const page = await api(`${base}?before=${messages.value[0].id}`)
    if (v !== version) return
    merge(page.items)
    more.value = page.hasMore
  } catch (e) {
    error.value = e.message
  }
}

async function send() {
  if (busy.value || (!draft.value.trim() && !selectedImages.value.length) || !props.canSend) return

  busy.value = true
  error.value = ''
  const text = draft.value
  const v = version

  try {
    let body = { content: text }
    if (props.kind === 'tickets' && selectedImages.value.length) {
      body = new FormData()
      body.append('content', text)
      selectedImages.value.forEach(image => body.append('images', image.file))
    }

    const item = await api(endpoint(), {
      method: 'POST',
      body
    })

    if (v === version) {
      merge([item])
      draft.value = ''
      clearImages()
      await nextTick()
      if (box.value) {
        box.value.scrollTop = box.value.scrollHeight
      }
    }

    window.dispatchEvent(new Event('support-refresh'))
  } catch (e) {
    if (v === version) error.value = e.message
  } finally {
    if (v === version) busy.value = false
  }
}

function keyboard(e) {
  if (e.key === 'Enter' && !e.shiftKey && !e.isComposing) {
    e.preventDefault()
    send()
  }
}

function eventRefresh() {
  refresh()
}

// --- 偵聽器與生命週期 ---
watch(() => [props.kind, props.id], () => {
  version++
  messages.value = []
  more.value = false
  draft.value = ''
  error.value = ''
  busy.value = false
  clearImages()
  refresh(true)
}, {
  immediate: true
})

onMounted(() => {
  window.addEventListener('support-refresh', eventRefresh)
  timer = setInterval(() => {
    if (document.visibilityState === 'visible') {
      refresh()
    }
  }, 5000)
})

onUnmounted(() => {
  version++
  clearImages()
  window.removeEventListener('support-refresh', eventRefresh)
  clearInterval(timer)
})
</script>

<template>
  <div class="conversation">
    <!-- 訊息列表區塊 -->
    <div ref="box" class="messages" aria-live="polite">
      <button v-if="more" class="text-button older" @click="older">
        載入更早訊息
      </button>

      <p v-if="loading" class="empty">
        載入對話中…
      </p>

      <div v-else-if="!messages.length" class="empty">
        <span class="empty-symbol">✦</span>
        <p>
          把問題告訴我們，<br>
          客服會在這裡回覆你。
        </p>
      </div>

      <article v-for="m in messages" :key="m.id" class="message" :class="{ mine: m.senderType === senderTypeFor(session.user?.role) }">
        <small>
          {{ m.senderType === 'ADMIN' ? '客服' : m.senderName }} ·
          {{ timeText(m.sentAt) }}
        </small>
        <div class="message-bubble">
          <span v-if="m.content">{{ m.content }}</span>
          <div v-if="kind === 'tickets' && m.attachments?.length" class="message-images">
            <TicketImage v-for="attachment in m.attachments" :key="attachment.id" :ticket-id="id"
              :attachment="attachment" />
          </div>
        </div>
      </article>
    </div>

    <!-- 錯誤提示 -->
    <p v-if="error" class="error inline-error" role="alert">
      {{ error }}
    </p>

    <!-- 訊息輸入表單 -->
    <form v-if="canSend" class="composer" @submit.prevent="send">
      <!-- 待傳送圖片預覽 -->
      <section v-if="selectedImages.length" class="selected-images" aria-label="待傳送圖片">
        <figure v-for="(image, index) in selectedImages" :key="image.url">
          <img :src="image.url" :alt="image.file.name">
          <button type="button" :disabled="busy" :aria-label="`移除圖片：${image.file.name}`"
            @click="removeImage(index)">✕</button>
          <figcaption>{{ image.file.name }}</figcaption>
        </figure>
      </section>

      <textarea v-model="draft" :disabled="busy" aria-label="輸入訊息"
        :placeholder="kind === 'tickets' ? '輸入訊息，也可以附上問題截圖…' : '輸入訊息…'" maxlength="4000" rows="2"
        @keydown="keyboard"></textarea>

      <div>
        <input v-if="kind === 'tickets'" ref="fileInput" type="file" accept="image/jpeg,image/png" multiple hidden
          @change="chooseImages">
        <button v-if="kind === 'tickets'" type="button" class="text-button"
          :disabled="busy || selectedImages.length >= 3" @click="fileInput.click()">
          上傳圖片
        </button>

        <small>
          {{ draft.length }} / 4000 · Shift＋Enter 換行
        </small>

        <button class="primary" :disabled="busy || (!draft.trim() && !selectedImages.length)">
          {{ busy ? '傳送中' : '送出 ↗' }}
        </button>
      </div>

      <p v-if="kind === 'tickets'" class="image-upload-hint">
        JPG、PNG · 每張最多 5 MB · 每則最多 3 張 · 可只傳圖片
      </p>
    </form>

    <!-- 關閉提示 -->
    <div v-else class="closed-message">
      {{ closedText }}
    </div>
  </div>
</template>

