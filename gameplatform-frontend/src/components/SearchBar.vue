<script setup>
import { ref } from 'vue'

// ==============================
// Props
// ==============================

defineProps({
  placeholder: {
    type: String,
    default: '輸入關鍵字'
  },

  label: {
    type: String,
    default: '搜尋'
  }
})

// ==============================
// Emits
// ==============================

const emit = defineEmits(['search'])

// ==============================
// State
// ==============================

const text = ref('')

// ==============================
// Methods
// ==============================

function submit() {
  emit('search', text.value.trim())
}

function clear() {
  text.value = ''
  submit()
}
</script>

<template>
  <form class="support-search" role="search" @submit.prevent="submit">
    <div class="support-search-row">
      <input v-model="text" type="search" :aria-label="label" :placeholder="placeholder" maxlength="100"
        @search="!text && submit()">

      <button class="secondary search-button" type="submit">
        搜尋
      </button>
    </div>

    <button v-if="text" class="text-button clear-button" type="button" @click="clear">
      清除
    </button>
  </form>
</template>