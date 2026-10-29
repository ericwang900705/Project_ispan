import { computed, onScopeDispose, ref, watch } from 'vue'

// 後端的停權期限為台灣時間；保留舊版僅提供分鐘精度的訊息相容性。
export function lockDeadline(message) {
  const match = /^帳號已停權或暫時鎖定至 (\d{4})\/(\d{2})\/(\d{2}) (\d{2}):(\d{2})(?::(\d{2}))?/.exec(message || '')
  if (!match) return null
  const [, year, month, day, hour, minute, second = '00'] = match
  const until = Date.parse(`${year}-${month}-${day}T${hour}:${minute}:${second}+08:00`)
  return Number.isFinite(until) ? { until, label: match[0].replace('帳號已停權或暫時鎖定至 ', '') } : null
}

export function countdownMessage(message, now = Date.now()) {
  const deadline = lockDeadline(message)
  if (!deadline) return message
  const remaining = Math.max(0, Math.ceil((deadline.until - now) / 1000))
  if (!remaining) return '停權／鎖定時間已到，請重新嘗試登入；登入結果以系統確認為準。'
  const days = Math.floor(remaining / 86400)
  const hours = Math.floor(remaining % 86400 / 3600)
  const minutes = Math.floor(remaining % 3600 / 60)
  const seconds = remaining % 60
  return `帳號已停權或暫時鎖定至 ${deadline.label}，剩餘 ${days ? days + ' 天 ' : ''}${hours} 小時 ${String(minutes).padStart(2, '0')} 分 ${String(seconds).padStart(2, '0')} 秒。`
}

// 只有顯示停權訊息時才啟動時鐘；更新訊息、到期及離開頁面都清除計時器。
export function useAccountLockCountdown(message) {
  const now = ref(Date.now())
  let timer
  const stop = () => { clearInterval(timer); timer = undefined }
  watch(message, value => {
    stop(); now.value = Date.now()
    const deadline = lockDeadline(value)
    if (deadline && deadline.until > now.value) timer = setInterval(() => {
      now.value = Date.now()
      if (now.value >= deadline.until) stop()
    }, 1000)
  }, { immediate: true })
  onScopeDispose(stop)
  return computed(() => countdownMessage(message.value, now.value))
}
