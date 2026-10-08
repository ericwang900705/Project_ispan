<script setup>
import { ref, watch, onMounted, onUnmounted } from 'vue'
import { apiImage, session } from '../api'

// --- Props 定義 ---
const props = defineProps({
  ticketId: {
    type: Number,
    required: true
  },
  attachment: {
    type: Object,
    required: true
  }
})

// --- 狀態定義 ---
const container = ref(null)
const dialog = ref(null)
const url = ref('')
const error = ref('')
const loading = ref(false)

let observer = null
let controller = null
let version = 0

// --- 清理與重置 ---
function cleanup() {
  version++
  observer?.disconnect()
  controller?.abort()
  dialog.value?.close()
  if (url.value) {
    URL.revokeObjectURL(url.value)
  }
  url.value = ''
  error.value = ''
  loading.value = false
}

// --- 載入圖片 ---
async function load() {
  if (loading.value || url.value || !session.user) return

  observer?.disconnect()
  const v = version
  controller = new AbortController()
  loading.value = true
  error.value = ''

  try {
    const blob = await apiImage(`/aki/tickets/${props.ticketId}/attachments/${props.attachment.id}`, {
      signal: controller.signal
    })
    if (v === version) {
      url.value = URL.createObjectURL(blob)
    }
  } catch (e) {
    if (v === version && e.name !== 'AbortError') {
      error.value = e.message
    }
  } finally {
    if (v === version) {
      loading.value = false
    }
  }
}

// --- 啟動 IntersectionObserver 懶載入 ---
function start() {
  if (!session.user || !container.value) return
  if (!('IntersectionObserver' in window)) {
    return load()
  }

  observer = new IntersectionObserver((entries) => {
    if (entries.some(entry => entry.isIntersecting)) {
      load()
    }
  }, { rootMargin: '150px' })

  observer.observe(container.value)
}

// --- 偵聽器與生命週期 ---
watch(() => [props.ticketId, props.attachment.id, session.user?.id, session.user?.role], () => {
  cleanup()
  start()
})

onMounted(start)
onUnmounted(cleanup)
</script>

<template>
  <figure ref="container" class="ticket-image">
    <!-- 圖片載入成功：顯示縮圖與大圖按鈕 -->
    <button v-if="url" type="button" class="ticket-image-open" :aria-label="`放大圖片：${attachment.fileName}`"
      @click="dialog.showModal()">
      <img :src="url" :alt="attachment.fileName" :width="attachment.width" :height="attachment.height" loading="lazy">
      <span>點擊查看大圖</span>
    </button>

    <!-- 圖片載入中或發生錯誤：佔位區塊 -->
    <div v-else class="ticket-image-placeholder">
      <template v-if="error">
        <p role="alert">{{ error }}</p>
        <button type="button" class="text-button" @click="load">重新載入圖片</button>
      </template>
      <span v-else>{{ loading ? '圖片載入中…' : '圖片' }}</span>
    </div>

    <!-- 檔案名稱 -->
    <figcaption>{{ attachment.fileName }}</figcaption>

    <!-- 燈箱大圖預覽 Dialog -->
    <dialog ref="dialog" class="ticket-image-dialog" aria-label="案件圖片預覽"
      @click="e => { if (e.target === dialog) dialog.close() }">
      <div class="ticket-image-dialog-bar">
        <span>{{ attachment.fileName }}</span>
        <button type="button" class="secondary" aria-label="關閉圖片預覽" @click="dialog.close()">
          關閉 ✕
        </button>
      </div>
      <img v-if="url" :src="url" :alt="attachment.fileName">
    </dialog>
  </figure>
</template>

