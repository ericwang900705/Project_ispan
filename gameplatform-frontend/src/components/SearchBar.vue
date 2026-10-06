<script setup>
import { ref } from 'vue'
defineProps({
  placeholder : {
    type : String, default : '輸入關鍵字'
  }, label : {
    type : String, default : '搜尋'
  }
})
const emit = defineEmits(['search']), text = ref('')
function submit() {
  emit('search', text.value.trim())
}
function clear() {
  text.value = '';
  submit()
}
</script>

<template>
  <form class="support-search" role="search" @submit.prevent="submit">
    <input v-model="text" type="search" :aria-label="label" :placeholder="placeholder" maxlength="100" @search="!text&&submit()">
    <button class="secondary" type="submit">
      搜尋
    </button>
    <button class="text-button" type="button" @click="clear">
      清除
    </button>
  </form>
</template>
