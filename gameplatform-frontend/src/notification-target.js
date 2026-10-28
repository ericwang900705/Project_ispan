// 將通知中的舊客服路徑轉成目前入口，並保留指定聊天室、案件與遊戲。
export function notificationTarget(item, role) {
  const member = ['MEMBER', 'PUBLISHER'].includes(role)
  const raw = String(item.target_path || '')
  if (!raw.startsWith('/') || raw.startsWith('//') || /[\\\r\n]/.test(raw)) throw Error('通知目的地無效')
  const url = new URL(raw, 'https://notification.invalid')
  if (url.origin !== 'https://notification.invalid') throw Error('通知目的地無效')
  const type = item.event_type || ''
  const room = url.searchParams.get('room') || String(item.body || '').match(/聊天室\s*#(\d+)/)?.[1]
  if (/^(NEW_CHAT|CHAT_REPLY|CHAT_MESSAGE|ROOM_CLOSED)$/.test(type)) {
    const path = member ? '/member/support' : '/admin/chats'
    return room && /^\d+$/.test(room) ? `${path}?room=${room}` : path
  }
  const ticket = url.pathname.match(/^\/(?:tickets|member\/support)\/(\d+)$/)?.[1]
  if (ticket) return `${member ? '/member/support' : '/tickets'}/${ticket}`
  if (type.startsWith('GAME_') && role === 'PUBLISHER' && item.related_game_id) {
    return `/publisher/games?mode=history&game=${item.related_game_id}`
  }
  return url.pathname + url.search + url.hash
}
