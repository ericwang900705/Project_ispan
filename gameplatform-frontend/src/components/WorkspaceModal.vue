<script setup>
// 管理工作視窗：集中顯示明細與編輯表單，支援鍵盤焦點、捲動與忙碌狀態。
import { ref, watch, nextTick, onBeforeUnmount, useId } from 'vue'
import UiIcon from './UiIcon.vue'
const props = defineProps({ open: Boolean, title: String, busy: Boolean, wide: Boolean, publisher: Boolean, suspended: Boolean })
const emit = defineEmits(['close'])
const element = ref(null), titleId = useId()
let previousFocus, previousOverflow
const controls = () => [...(element.value?.querySelectorAll('button:not(:disabled),input:not(:disabled),select:not(:disabled),textarea:not(:disabled),a[href],[tabindex="0"]') || [])].filter(e => e.offsetParent !== null)
function keys(event) {
  if (!props.open || props.suspended) return
  if (event.key === 'Escape') { event.preventDefault(); event.stopPropagation(); if (!props.busy) emit('close') }
  if (event.key === 'Tab') { const list = controls(); if (!list.length) { event.preventDefault(); element.value?.focus() } else if (event.shiftKey && document.activeElement === list[0]) { event.preventDefault(); list.at(-1).focus() } else if (!event.shiftKey && document.activeElement === list.at(-1)) { event.preventDefault(); list[0].focus() } }
}
function cleanup() { window.removeEventListener('keydown', keys); if (previousOverflow !== undefined) { document.body.style.overflow = previousOverflow; previousOverflow = undefined }; if (previousFocus?.isConnected) previousFocus.focus() }
watch(() => props.open, async open => { if (!open) return cleanup(); previousFocus = document.activeElement; previousOverflow = document.body.style.overflow; document.body.style.overflow = 'hidden'; window.addEventListener('keydown', keys); await nextTick(); if (props.open) element.value?.focus() })
onBeforeUnmount(cleanup)
</script>
<template>
  <Teleport to="body"><div v-if="open" class="workspace-modal-backdrop support-ui" :class="{ 'publisher-modal-workspace': publisher }" @click.self="!busy && !suspended && emit('close')">
    <section ref="element" class="workspace-modal" :class="{ 'workspace-modal-wide': wide, 'publisher-workspace': publisher }" role="dialog" aria-modal="true" :aria-labelledby="titleId" tabindex="-1">
      <header class="workspace-modal-header"><div><span>{{ publisher ? 'PUBLISHER WORKSPACE' : 'ADMIN WORKSPACE' }}</span><h2 :id="titleId">{{ title }}</h2></div><button type="button" class="workspace-modal-close" :disabled="busy || suspended" aria-label="關閉視窗" @click="emit('close')"><UiIcon name="close" /></button></header>
      <div class="workspace-modal-content"><slot /></div>
    </section>
  </div></Teleport>
</template>
<style scoped>
.workspace-modal-backdrop { position: fixed; inset: 0; z-index: 1800; display: grid; place-items: center; padding: 24px; background: #0b1b35a3; backdrop-filter: blur(3px); box-sizing: border-box; }
.workspace-modal { display: flex; flex-direction: column; width: min(820px,100%); max-height: calc(100dvh - 48px); min-width: 0; background: #fff; color: #24445d; border: 1px solid #b7cad9; border-radius: 18px; box-shadow: 0 24px 90px #04132c66; outline: none; overflow: hidden; }
.workspace-modal-wide { width: min(1220px,100%); }.workspace-modal-header { display: flex; flex-shrink: 0; justify-content: space-between; align-items: flex-start; gap: 18px; padding: 22px 26px; border-bottom: 1px solid #d5e1ea; background: #f3f7fb; }.workspace-modal-header span { font-size: 10px; letter-spacing: 1.5px; font-weight: 700; color: #657f95; }.workspace-modal-header h2 { margin: 6px 0 0; font-size: 23px; line-height: 1.4; color: #223e58; overflow-wrap: anywhere; }
.workspace-modal-close { display: grid; place-items: center; min-width: 38px; height: 38px; border: 1px solid #baccd9; border-radius: 10px; color: #345873; background: white; cursor: pointer; }.workspace-modal-close:hover:not(:disabled) { background: #e6eef6; }.workspace-modal-close:disabled { opacity: .5; cursor: wait; }.workspace-modal-close:focus-visible { outline: 2px solid #258caa; outline-offset: 3px; }
.workspace-modal-content { min-height: 0; overflow-y: auto; overscroll-behavior: contain; padding: 24px 26px; }.workspace-modal-content :deep(.management-panel) { padding: 0; margin: 0; border: 0; background: transparent; box-shadow: none; }.workspace-modal-content :deep(.management-panel > h2) { display: none; }.workspace-modal-content :deep(.management-table-wrap) { max-width: 100%; overflow-x: auto; }
.publisher-modal-workspace .workspace-modal { background: #edf3f8; color: #274760; }.publisher-modal-workspace .workspace-modal-header { background: #e4edf4; }
@media(max-width:640px) { .workspace-modal-backdrop { padding: 12px; }.workspace-modal { max-height: calc(100dvh - 24px); border-radius: 13px; }.workspace-modal-header { padding: 18px; }.workspace-modal-content { padding: 18px; }.workspace-modal-header h2 { font-size: 20px; } }
</style>
