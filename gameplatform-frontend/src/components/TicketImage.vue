<script setup>
import { ref, watch, onMounted, onUnmounted } from 'vue'
import { apiImage, session } from '../api'

const props = defineProps({ ticketId: { type: Number, required: true }, attachment: { type: Object, required: true } })
const container = ref(null), dialog = ref(null), url = ref(''), error = ref(''), loading = ref(false)
let observer, controller, version = 0

function cleanup() {
  version++
  observer?.disconnect()
  controller?.abort()
  dialog.value?.close()
  if (url.value) URL.revokeObjectURL(url.value)
  url.value = ''
  error.value = ''
  loading.value = false
}
async function load() {
  if (loading.value || url.value || !session.user) return
  observer?.disconnect()
  const v = version
  controller = new AbortController()
  loading.value = true
  error.value = ''
  try {
    const blob = await apiImage(`/tickets/${props.ticketId}/attachments/${props.attachment.id}`, { signal: controller.signal })
    if (v === version) url.value = URL.createObjectURL(blob)
  } catch (e) {
    if (v === version && e.name !== 'AbortError') error.value = e.message
  } finally {
    if (v === version) loading.value = false
  }
}
function start() {
  if (!session.user || !container.value) return
  if (!('IntersectionObserver' in window)) return load()
  observer = new IntersectionObserver(entries => {
    if (entries.some(entry => entry.isIntersecting)) load()
  }, { rootMargin: '150px' })
  observer.observe(container.value)
}
watch(() => [props.ticketId, props.attachment.id, session.user?.id, session.user?.role], () => {
  cleanup()
  start()
})
onMounted(start)
onUnmounted(cleanup)
</script>

<template>
  <figure ref="container" class="ticket-image">
    <button v-if="url" type="button" class="ticket-image-open" :aria-label="`放大圖片：${attachment.fileName}`" @click="dialog.showModal()">
      <img :src="url" :alt="attachment.fileName" :width="attachment.width" :height="attachment.height" loading="lazy">
      <span>點擊查看大圖</span>
    </button>
    <div v-else class="ticket-image-placeholder">
      <template v-if="error">
        <p role="alert">{{ error }}</p>
        <button type="button" class="text-button" @click="load">重新載入圖片</button>
      </template>
      <span v-else>{{ loading ? '圖片載入中…' : '圖片' }}</span>
    </div>
    <figcaption>{{ attachment.fileName }}</figcaption>
    <dialog ref="dialog" class="ticket-image-dialog" aria-label="案件圖片預覽" @click="e => { if (e.target === dialog) dialog.close() }">
      <div class="ticket-image-dialog-bar">
        <span>{{ attachment.fileName }}</span>
        <button type="button" class="secondary" aria-label="關閉圖片預覽" @click="dialog.close()">關閉 ✕</button>
      </div>
      <img v-if="url" :src="url" :alt="attachment.fileName">
    </dialog>
  </figure>
</template>

<style scoped>
.ticket-image { margin: 0; min-width: 0; max-width: 280px; }
.ticket-image-open { display: block; width: 100%; padding: 0; border: 1px solid #cbd5e1; border-radius: 12px; overflow: hidden; background: #f8fafc; color: #334155; cursor: zoom-in; }
.ticket-image-open img { display: block; width: 100%; height: auto; max-height: 200px; object-fit: contain; }
.ticket-image-open span { display: block; font-size: 11px; padding: 7px; }
.ticket-image-placeholder { display: grid; place-items: center; min-height: 100px; padding: 14px; background: #f1f5f9; border-radius: 10px; color: #475569; font-size: 12px; }
figcaption { overflow-wrap: anywhere; font-size: 11px; opacity: .75; margin-top: 5px; }
.ticket-image-dialog { width: min(1000px, 94vw); max-height: 92vh; padding: 16px; border: 0; border-radius: 16px; background: #fff; color: #0f172a; }
.ticket-image-dialog::backdrop { background: #0f172abd; }
.ticket-image-dialog-bar { display: flex; justify-content: space-between; align-items: center; gap: 16px; margin-bottom: 12px; overflow-wrap: anywhere; }
.ticket-image-dialog-bar button { flex-shrink: 0; }
.ticket-image-dialog img { display: block; width: 100%; max-height: 76vh; object-fit: contain; }
</style>
