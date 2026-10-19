<script setup>
import { ref, watch, nextTick, onUnmounted } from 'vue'
import UiIcon from './UiIcon.vue'
const props = defineProps({ open: Boolean, title: String, description: String, busy: Boolean })
const emit = defineEmits(['close'])
const element = ref(null)
let previousFocus, previousOverflow
function controls() { return [...element.value.querySelectorAll('button:not(:disabled),input:not(:disabled),textarea:not(:disabled),a[href]')].filter(x => x.offsetParent !== null) }
function keys(event) {
  if (!props.open) return
  if (event.key === 'Escape' && !props.busy) { event.preventDefault(); emit('close') }
  if (event.key === 'Tab') {
    const list = controls()
    if (!list.length) { event.preventDefault(); element.value.focus(); return }
    if (event.shiftKey && document.activeElement === list[0]) { event.preventDefault(); list.at(-1).focus() }
    else if (!event.shiftKey && document.activeElement === list.at(-1)) { event.preventDefault(); list[0].focus() }
  }
}
function cleanup() {
  window.removeEventListener('keydown', keys)
  if (previousOverflow !== undefined) { document.body.style.overflow = previousOverflow; previousOverflow = undefined }
  if (previousFocus?.isConnected) previousFocus.focus()
}
watch(() => props.open, async open => {
  if (!open) { cleanup(); return }
  previousFocus = document.activeElement
  previousOverflow = document.body.style.overflow
  document.body.style.overflow = 'hidden'
  window.addEventListener('keydown', keys)
  await nextTick()
  if (props.open) (element.value.querySelector('textarea,input') || controls()[0] || element.value)?.focus()
})
onUnmounted(cleanup)
</script>
<template><Teleport to="body"><div v-if="open" class="publisher-modal-backdrop" @click.self="!busy && emit('close')"><section ref="element" class="publisher-modal" role="dialog" aria-modal="true" aria-labelledby="publisher-dialog-title" tabindex="-1"><header><h2 id="publisher-dialog-title">{{ title }}</h2><button :disabled="busy" aria-label="關閉對話框" @click="emit('close')"><UiIcon name="close" /></button></header><p v-if="description">{{ description }}</p><slot /><footer><slot name="actions" /></footer></section></div></Teleport></template>
