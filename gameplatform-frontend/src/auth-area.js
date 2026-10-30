// 合併部署仍按前台／後台選擇獨立會話；未知的後台網址也屬於後台。
export const isMemberRole = role => ['MEMBER', 'PUBLISHER'].includes(role)
export const isStaffRole = role => ['ADMIN', 'STAFF', 'REVIEWER', 'SUPPORT'].includes(role)
export const isStaffPath = path => /^\/(?:staff|admin|reviewer)(?:\/|$)/i.test(path || '')
export function areaForRoute(to, current = 'member', mode = 'combined') {
  if (mode === 'staff' || mode === 'member') return mode
  if (isStaffPath(to.path)) return 'staff'
  // 保留舊 /tickets 客服入口；前台正式入口為 /member/support。
  if (/^\/tickets(?:\/|$)/.test(to.path || '')) return current === 'staff' ? 'staff' : 'member'
  if (to.meta?.staff || to.meta?.management || to.meta?.support || to.meta?.review || to.meta?.dashboard) return 'staff'
  return 'member'
}
