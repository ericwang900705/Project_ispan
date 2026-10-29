<script setup>
// 分頁保留在原清單下方；以固定三欄和清單留白維持換頁時的位置。
import { computed, ref, onMounted, onUpdated, onBeforeUnmount } from 'vue'
const props = defineProps({ page: { type: Number, default: 0 }, total: { type: Number, default: null }, size: { type: Number, default: 20 }, more: Boolean, busy: Boolean, dock: { type: Boolean, default: true } })
const emit = defineEmits(['change'])
const root = ref(null), reserved = ref(0)
const pages = computed(() => props.total == null ? null : Math.max(1, Math.ceil(props.total / props.size)))
const canNext = computed(() => pages.value == null ? props.more : props.page + 1 < pages.value)
let observer, frame, maxOffset = 0, previousWidth = 0
function measure() {
  // 聊天室清單已由外層固定高度與獨立捲動維持位置，不另外加留白。
  if (!props.dock || !root.value?.parentElement) return
  const parent = root.value.parentElement.getBoundingClientRect()
  if (Math.abs(parent.width - previousWidth) > 1) { maxOffset = 0; previousWidth = parent.width }
  const offset = root.value.getBoundingClientRect().top - parent.top
  if (!props.busy) maxOffset = Math.max(maxOffset, offset)
  const height = Math.max(0, maxOffset - offset)
  if (Math.abs(height - reserved.value) > .5) reserved.value = height
}
function schedule() { cancelAnimationFrame(frame); frame = requestAnimationFrame(measure) }
function change(delta) { if (!props.busy && (delta < 0 ? props.page > 0 : canNext.value)) emit('change', props.page + delta) }
onMounted(() => { measure(); observer = new ResizeObserver(schedule); if (root.value?.parentElement) observer.observe(root.value.parentElement); window.addEventListener('resize', schedule) })
onUpdated(measure)
onBeforeUnmount(() => { observer?.disconnect(); cancelAnimationFrame(frame); window.removeEventListener('resize', schedule) })
</script>
<template>
  <div ref="root" class="workspace-pager-wrap" :class="{ 'workspace-pager-compact': !dock }">
    <div v-if="reserved" class="workspace-pager-reserve" :style="{ height: reserved + 'px' }" aria-hidden="true"></div>
    <nav class="workspace-pager" aria-label="列表分頁" :aria-busy="busy">
      <button type="button" :disabled="busy || page === 0" @click="change(-1)">上一頁</button>
      <span aria-live="polite" aria-atomic="true">第 {{ page + 1 }}<template v-if="pages != null"> / {{ pages }}</template> 頁</span>
      <button type="button" :disabled="busy || !canNext" @click="change(1)">下一頁</button>
    </nav>
  </div>
</template>
<style scoped>
.workspace-pager-wrap { display: flow-root; width: 100%; min-width: 0; flex: none; }
.workspace-pager-reserve { pointer-events: none; }
.workspace-pager { display: grid; grid-template-columns: 88px minmax(100px,160px) 88px; align-items: center; justify-content: center; gap: 16px; box-sizing: border-box; min-height: 72px; margin: 20px 0 12px; padding: 14px 10px; background: transparent; color: #345570; font-family: inherit; }
.workspace-pager span { text-align: center; font-size: 14px; font-variant-numeric: tabular-nums; white-space: nowrap; }
.workspace-pager button { appearance: none; width: 88px; height: 42px; margin: 0; padding: 8px 12px; border: 1px solid #b8cddd; border-radius: 9px; background: #fff; color: #315672; font-size: 14px; line-height: 1.3; font-family: inherit; cursor: pointer; }
.workspace-pager button:hover:not(:disabled) { background: #e7f2fa; border-color: #76a6c4; }.workspace-pager button:disabled { opacity: .4; cursor: not-allowed; }.workspace-pager button:focus-visible { outline: 2px solid #278bab; outline-offset: 3px; }
.workspace-pager-compact .workspace-pager { grid-template-columns: 72px minmax(40px,1fr) 72px; gap: 8px; margin: 20px 0 12px; padding: 12px 6px; background: transparent; }
.workspace-pager-compact .workspace-pager button { width: 72px; }
body.cart-portal .workspace-pager { color: #e8eaf5; }
body.cart-portal .workspace-pager button { background: #202d59; color: #f0d59b; border-color: #50628f; }
body.cart-portal .workspace-pager button:hover:not(:disabled) { background: #304173; }
@media(max-width:420px) { .workspace-pager { grid-template-columns: 76px minmax(76px,1fr) 76px; gap: 6px; padding-inline: 0; }.workspace-pager button { width: 76px; } }
</style>
