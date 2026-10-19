export const staffRoles = ['SUPERADMIN', 'ADMIN', 'REVIEWER', 'SUPPORT']
export const isSuperAdmin = user => user?.role === 'SUPERADMIN'
export const permissionOptions = role => ({
  ADMIN: ['GAME_REVIEW', 'PUBLISHER_REVIEW', 'MEMBER_MANAGE', 'SUPPORT_CASES'],
  REVIEWER: ['GAME_REVIEW', 'PUBLISHER_REVIEW', 'MEMBER_MANAGE'],
  SUPPORT: ['SUPPORT_CASES']
}[role] || [])
export const permissionNames = {
  GAME_REVIEW: '遊戲審核', PUBLISHER_REVIEW: '發行商審核',
  MEMBER_MANAGE: '會員管理', SUPPORT_CASES: '客服案件'
}
export const hasPermission = (user, code) => isSuperAdmin(user) ||
  (permissionOptions(user?.role).includes(code) && user?.permissions?.includes(code))
// Role eligibility for shared view formatting; API and route access require grants.
export const canSupport = role => ['SUPERADMIN', 'ADMIN', 'SUPPORT'].includes(role)
export const canUseTickets = role => ['MEMBER', 'PUBLISHER'].includes(role) || canSupport(role)
export const homeFor = role => ({
  SUPERADMIN: '/admin', ADMIN: '/staff/home', SUPPORT: '/staff/home', REVIEWER: '/staff/home',
  MEMBER: '/', PUBLISHER: '/publisher/overview'
}[role] || '/member/login')
export const senderTypeFor = role => staffRoles.includes(role) ? 'ADMIN' : role
export const roleName = role => ({
  SUPERADMIN: '最高管理員', ADMIN: '管理員', REVIEWER: '審核人員',
  SUPPORT: '客服人員', PENDING: '待授權', MEMBER: '會員', PUBLISHER: '發行商'
}[role] || '')
export const loginFor = role => role && !['MEMBER', 'PUBLISHER'].includes(role) ? '/staff/login' : '/member/login'
export function guardPage(to, user, lastArea = 'member') {
  const loginPage = ['/member/login', '/staff/login', '/login'].includes(to.path)
  if (!user) {
    if (loginPage) return true
    return { path: to.meta.support || to.meta.management || to.meta.review || to.meta.staff || lastArea === 'staff' ? '/staff/login' : '/member/login', query: { redirect: to.fullPath } }
  }
  if (loginPage) return homeFor(user.role)
  if (to.meta.staff && !staffRoles.includes(user.role)) return homeFor(user.role)
  if (to.meta.publisher && user.role !== 'PUBLISHER') return homeFor(user.role)
  if (to.meta.review && !hasPermission(user, 'GAME_REVIEW')) return homeFor(user.role)
  if (to.meta.management && !isSuperAdmin(user)) return homeFor(user.role)
  if (to.meta.member && !['MEMBER', 'PUBLISHER'].includes(user.role)) return homeFor(user.role)
  if (to.meta.support && !hasPermission(user, 'SUPPORT_CASES')) return homeFor(user.role)
  if (to.meta.tickets && !['MEMBER', 'PUBLISHER'].includes(user.role) && !hasPermission(user, 'SUPPORT_CASES')) return homeFor(user.role)
  return true
}
