<script setup>
import { ref } from 'vue'

// Props
defineProps({
  busy: { type: Boolean, default: false },
  placeholder: {
    type: String,
    default: '輸入關鍵字'
  },

  label: {
    type: String,
    default: '搜尋'
  }
})

// Emits
const emit = defineEmits(['search'])

// State
const text = ref('')

// Methods
function submit() {
  emit('search', text.value.trim())
}

defineExpose({ clear })

function clear() {
  text.value = ''
  submit()
}
</script>

<template>
  <form class="support-search" role="search" @submit.prevent="submit">
    <div class="support-search-row">
      <input :disabled="busy" v-model="text" type="search" :aria-label="label" :placeholder="placeholder" maxlength="100"
        @search="!text && submit()">

      <button class="secondary search-button" type="submit" :disabled="busy">
        {{ busy ? '搜尋中…' : '搜尋' }}
      </button>
      <button v-if="text" :disabled="busy" class="text-button clear-button" type="button" @click="clear" aria-label="清除搜尋">清除</button>
    </div>
  </form>
</template>