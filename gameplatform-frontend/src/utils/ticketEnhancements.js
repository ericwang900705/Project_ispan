export const priorityLabel = (p) => ({
  URGENT: '緊急',
  HIGH: '高',
  NORMAL: '一般',
  LOW: '低'
}[p] || '一般'
)

export const waitingLabel = (t) =>
  t?.status === 'CLOSED' ? '已結案' : t?.waitingFor === 'MEMBER' ? '等待會員回覆' : '等待客服回覆'
export const elapsedLabel = (since, now = Date.now()) => {
  if (!since)
    return '尚無時間資料'

  const value = new Date(since.endsWith?.('Z') ? since : since).getTime()
  if (!Number.isFinite(value))
    return '尚無時間資料'
  const minutes = Math.max(0, Math.floor((now - value) / 60000))
  if (minutes < 1)
    return '剛剛'
  if (minutes < 60)
    return `${minutes} 分鐘`
  if (minutes < 1440)
    return `${Math.floor(minutes / 60)} 小時 ${minutes % 60} 分鐘`

  return `${Math.floor(minutes / 1440)} 天`
}
