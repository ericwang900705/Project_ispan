export const canSupport = role => [
  'ADMIN',
  'SUPPORT'
].includes(role)

export const canUseTickets = role =>
  [
    'MEMBER',
    'PUBLISHER'
  ].includes(role) || canSupport(role)

export const homeFor = role => ({
  ADMIN: '/admin/overview',
  REVIEWER: '/staff/unavailable',
  SUPPORT: '/admin',
  MEMBER: '/member/support',
  PUBLISHER: '/member/support'
}[role] || '/member/login')

export const senderTypeFor = role =>
  canSupport(role) ? 'ADMIN' : role

export const roleName = role => ({
  ADMIN: '管理者',
  REVIEWER: '審核人員',
  SUPPORT: '客服人員',
  MEMBER: '會員',
  PUBLISHER: '發行商'
}[role] || '')

export const loginFor = role =>
  role && ![
    'MEMBER',
    'PUBLISHER'
  ].includes(role) ? '/staff/login' : '/member/login'

export function guardPage(to, user, lastArea = 'member') {
  const loginPage = [
    '/member/login',
    '/staff/login',
    '/login'].includes(to.path)
  if (!user) {
    if (loginPage)
      return true
    return to.meta.support || lastArea === 'staff'
      ? '/staff/login' : '/member/login'
  }
  if (loginPage)
    return homeFor(user.role)
  if (to.path === '/staff/unavailable')
    return true
  if (to.meta.management && user.role !== 'ADMIN')
    return homeFor(user.role)
  if (to.meta.member && !['MEMBER', 'PUBLISHER'].includes(user.role))
    return homeFor(user.role)
  if (to.meta.support && !canSupport(user.role))
    return homeFor(user.role)
  if (to.meta.tickets && !canUseTickets(user.role))
    return homeFor(user.role)
  return true
}
