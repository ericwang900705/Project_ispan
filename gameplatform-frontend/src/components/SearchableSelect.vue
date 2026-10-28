<script setup>
// 後台搜尋選單：可依名稱／編號過濾，只有點選有效項目才會送出 ID。
import { computed, nextTick, ref, useId, watch } from 'vue'

const props = defineProps({
  modelValue: [String, Number],
  options: { type: Array, default: () => [] },
  label: { type: String, required: true },
  disabled: Boolean,
  required: Boolean,
})
const emit = defineEmits(['update:modelValue'])
const id = useId()
const input = ref(null)
const search = ref('')
const opened = ref(false)
const active = ref(-1)
const selected = computed(() => props.options.find(item => String(item.id) === String(props.modelValue)))
const matches = computed(() => {
  const keyword = search.value.trim().toLocaleLowerCase()
  return props.options.filter(item => !keyword || String(item.name).toLocaleLowerCase().includes(keyword) || String(item.id).includes(keyword))
})
watch(() => [props.modelValue, props.options], () => {
  if (selected.value || !opened.value) search.value = selected.value?.name || ''
}, { immediate: true })
watch(matches, () => { active.value = -1 })
function begin() {
  opened.value = true
  if (selected.value) search.value = ''
}
function type(event) {
  search.value = event.target.value
  opened.value = true
  active.value = -1
  emit('update:modelValue', '')
}
function select(item) {
  emit('update:modelValue', item.id)
  search.value = item.name
  opened.value = false
  active.value = -1
  input.value?.focus()
}
function leave(event) {
  if (event.currentTarget.contains(event.relatedTarget)) return
  opened.value = false
  search.value = selected.value?.name || ''
}
async function keydown(event) {
  if (event.key === 'Escape') {
    event.preventDefault()
    opened.value = false
    search.value = selected.value?.name || ''
  } else if (['ArrowDown', 'ArrowUp'].includes(event.key)) {
    event.preventDefault()
    opened.value = true
    const count = matches.value.length
    active.value = count ? (active.value + (event.key === 'ArrowDown' ? 1 : count - 1) + count) % count : -1
    await nextTick()
    document.getElementById(`${id}-option-${active.value}`)?.scrollIntoView({ block: 'nearest' })
  } else if (event.key === 'Enter' && opened.value) {
    event.preventDefault()
    if (active.value >= 0 && matches.value[active.value]) select(matches.value[active.value])
  }
}
</script>

<template>
  <div class="searchable-select" @focusout="leave">
    <label :for="id">{{ label }}</label>
    <input :id="id" ref="input" :value="search" :aria-label="label" role="combobox" autocomplete="off"
      aria-autocomplete="list" :aria-expanded="opened" :aria-controls="`${id}-list`"
      :aria-activedescendant="opened && active >= 0 ? `${id}-option-${active}` : undefined"
      :placeholder="`搜尋${label}名稱或編號，再從清單選擇`" :disabled="disabled" :required="required"
      :pattern="selected ? undefined : '(?!)'" @focus="begin" @click="opened = true" @input="type" @keydown="keydown" />
    <ul v-if="opened" :id="`${id}-list`" role="listbox" :aria-label="`${label}搜尋結果`">
      <li v-for="(item, index) in matches" :id="`${id}-option-${index}`" :key="item.id" role="option"
        :aria-selected="String(item.id) === String(modelValue)" :class="{ highlighted: active === index }"
        @mousedown.prevent="select(item)">
        <span>{{ item.name }}</span><small>#{{ item.id }}</small>
      </li>
      <li v-if="!matches.length" role="presentation" class="empty">沒有符合的{{ label }}</li>
    </ul>
    <small v-if="selected" class="selection">已選擇：{{ selected.name }}（#{{ selected.id }}）</small>
    <small v-else class="selection">請從搜尋結果選擇一筆資料。</small>
  </div>
</template>

<style scoped>
.searchable-select {
  position: relative;
  min-width: 0;
  display: grid;
  gap: 8px;
  align-content: start
}

.searchable-select label {
  margin: 0;
  color: #587586;
  font-size: 13px
}

.searchable-select input {
  width: 100%;
  margin: 0
}

.searchable-select ul {
  position: absolute;
  top: 72px;
  left: 0;
  right: 0;
  z-index: 20;
  list-style: none;
  padding: 5px;
  margin: 0;
  max-height: 250px;
  overflow: auto;
  border: 1px solid #b7d0dc;
  border-radius: 10px;
  background: #fff;
  box-shadow: 0 10px 25px #1d455326;
  color: #234456
}

.searchable-select li {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  padding: 11px 12px;
  border-radius: 6px;
  cursor: pointer;
  overflow-wrap: anywhere
}

.searchable-select li:hover,
.searchable-select li.highlighted {
  background: #e9f6fa;
  color: #17647d
}

.searchable-select li[aria-selected=true] {
  font-weight: 700
}

.searchable-select li small,
.selection {
  color: #61798c;
  font-size: 12px
}

.searchable-select li.empty {
  cursor: default;
  color: #61798c
}
</style>
