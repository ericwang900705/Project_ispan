import { ref, watch } from 'vue'
import { defineStore } from 'pinia'
export const useModeStore = defineStore('mode', () => {
    const mode = ref(localStorage.getItem('mode') ?? 'player')
    watch(mode, (value) => localStorage.setItem('mode', value))

    return { mode }
})