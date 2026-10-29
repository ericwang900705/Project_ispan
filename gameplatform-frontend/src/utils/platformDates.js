// 日期以台灣日期限制；datetime-local 不經本機時區轉換。
export function todayMinimum() {
  return new Intl.DateTimeFormat('sv-SE', { timeZone: 'Asia/Taipei', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date()) + 'T00:00'
}
export function sevenDaysLater(value) {
  const match = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})/.exec(value || '')
  if (!match) return ''
  const [, y, m, d, h, min] = match
  return new Date(Date.UTC(+y, +m - 1, +d + 7, +h, +min)).toISOString().slice(0, 16)
}
