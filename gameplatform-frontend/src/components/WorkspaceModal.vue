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
/* 發行商草稿編輯視窗沿用工作區深藍底，表單與預覽各有獨立層次。 */
.publisher-modal-workspace .workspace-modal { background: #101930; color: #e4ebfa; border-color: #465a85; }
.publisher-modal-workspace .workspace-modal-header { background: #172442; border-bottom-color: #3a4d73; }
.publisher-modal-workspace .workspace-modal-header h2 { color: #f1e0bb; }.publisher-modal-workspace .workspace-modal-header span { color: #a8bad7; }
.publisher-modal-workspace .workspace-modal-close { background: #263756; color: #e4edff; border-color: #4c628a; }
.publisher-modal-workspace .workspace-modal-content { padding: 24px; }
.publisher-modal-workspace :deep(.publisher-store-layout) { gap: 24px; align-items: start; }
.publisher-modal-workspace :deep(.publisher-store-fields) { min-width: 0; background: transparent; border: 0; padding: 0; }
.publisher-modal-workspace :deep(.publisher-edit-section) { padding: 22px; margin-bottom: 18px; border: 1px solid #354c76; border-radius: 13px; background: #172541; }
.publisher-modal-workspace :deep(.publisher-edit-section h3) { display: flex; justify-content: space-between; flex-wrap: wrap; gap: 10px; margin: 0 0 20px; color: #e7d1a4; font-size: 17px; }
.publisher-modal-workspace :deep(.publisher-draft-label) { padding: 4px 10px; border-radius: 20px; color: #b8e7da; background: #224b4c; font-size: 11px; }
.publisher-modal-workspace :deep(.publisher-edit-section label) { color: #dbe5f7; line-height: 1.6; }
.publisher-modal-workspace :deep(.publisher-edit-section input:not([type=checkbox]):not([type=radio]):not([type=file])),
.publisher-modal-workspace :deep(.publisher-edit-section textarea), .publisher-modal-workspace :deep(.publisher-edit-section select) { min-height: 44px; border: 1px solid #4c6389; border-radius: 9px; background: #0d1931; color: #eef3ff; padding: 11px 13px; box-sizing: border-box; color-scheme: dark; }
.publisher-modal-workspace :deep(.publisher-edit-section input::placeholder), .publisher-modal-workspace :deep(.publisher-edit-section textarea::placeholder) { color: #92a6c9; }
.publisher-modal-workspace :deep(.muted), .publisher-modal-workspace :deep(.publisher-description-footer), .publisher-modal-workspace :deep(.publisher-media-note) { color: #aebfda; }
.publisher-modal-workspace :deep(.publisher-live-preview) { overflow: hidden; padding: 22px; border: 1px solid #41587e; border-radius: 13px; background: #14213d; color: #e8eefc; }
.publisher-modal-workspace :deep(.publisher-live-preview h2), .publisher-modal-workspace :deep(.publisher-preview-description h3) { color: #e5d2ad; }
.publisher-modal-workspace :deep(.publisher-preview-summary), .publisher-modal-workspace :deep(.publisher-preview-description p) { color: #c9d7ed; }
.publisher-modal-workspace :deep(.publisher-image-placeholder) { background: #0b1730; color: #a8b9d5; border-color: #3d5379; }
.publisher-modal-workspace :deep(.publisher-editor-actions) { gap: 12px; padding: 16px 0 0; }
.publisher-modal-workspace :deep(.publisher-editor-actions button) { min-height: 46px; padding: 12px 20px; border-radius: 9px; }
.publisher-modal-workspace :deep(.publisher-editor-actions .primary) { background: #dbb975; color: #19223a; border: 1px solid #dbb975; }
.publisher-modal-workspace :deep(.publisher-editor-actions .secondary) { background: #273a5c; color: #e4ecfa; border: 1px solid #52698f; }
.publisher-modal-workspace :deep(.hero-calendar label) { color: #24445d; }
@media(max-width:640px) { .workspace-modal-backdrop { padding: 12px; }.workspace-modal { max-height: calc(100dvh - 24px); border-radius: 13px; }.workspace-modal-header { padding: 18px; }.workspace-modal-content { padding: 18px; }.workspace-modal-header h2 { font-size: 20px; } }
</style>
