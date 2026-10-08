import { ref } from 'vue'
export const publisherToast = ref('')
let timer
export function notifyPublisher(message) {
  clearTimeout(timer)
  publisherToast.value = message
  timer = setTimeout(() => { publisherToast.value = '' }, 5000)
}
