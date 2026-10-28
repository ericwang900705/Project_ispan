import { ref, computed } from 'vue'
import { defineStore } from 'pinia'

// 計數器 store（Vue 專案範本自帶的範例，目前專案未使用）
export const useCounterStore = defineStore('counter', () => {
  // 目前計數
  const count = ref(0)
  // 計算屬性：計數的兩倍，count 改變時會自動更新
  const doubleCount = computed(() => count.value * 2)
  // 計數加 1
  function increment() {
    count.value++
  }

  return { count, doubleCount, increment }
})
