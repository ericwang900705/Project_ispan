export const canSupport = role => ['ADMIN', 'SUPPORT'].includes(role)
export const canReview = role => ['ADMIN', 'REVIEWER'].includes(role)
export const canUseTickets = role => ['MEMBER', 'PUBLISHER'].includes(role) || canSupport(role)
export const homeFor = role => ({ADMIN:'/admin/overview',REVIEWER:'/review',SUPPORT:'/admin',MEMBER:'/',PUBLISHER:'/publisher/overview'}[role] || '/member/login')
export const senderTypeFor = role => canSupport(role) ? 'ADMIN' : role
export const roleName = role => ({ADMIN:'管理者',REVIEWER:'審核人員',SUPPORT:'客服人員',MEMBER:'會員',PUBLISHER:'發行商'}[role] || '')
export const loginFor = role => role && !['MEMBER','PUBLISHER'].includes(role) ? '/staff/login' : '/member/login'
export function guardPage(to, user, lastArea = 'member') {
  const loginPage = ['/member/login', '/staff/login', '/login'].includes(to.path)
  const publicMemberPage = ['/member/register','/member/verification-sent','/member/verify','/member/resend-verification','/member/forgot-password','/member/reset-password'].includes(to.path)
  if (publicMemberPage) return true
  if (!user) {
    if (loginPage || to.matched?.some(route => route.path === '/')) { if (to.meta.shopMember) return '/member/login'; return true }
    if (to.meta.publisher) return '/member/login'
    return to.meta.management || to.meta.review || to.meta.support || lastArea === 'staff'
      ? '/staff/login' : '/member/login'
  }
  if (loginPage) return homeFor(user.role)
  if (to.meta.shopMember && !['MEMBER','PUBLISHER'].includes(user.role)) return homeFor(user.role)
  if (to.meta.member && !['MEMBER', 'PUBLISHER'].includes(user.role)) return homeFor(user.role)
  if (to.meta.publisher && user.role !== 'PUBLISHER') return homeFor(user.role)
  if (to.meta.management && user.role !== 'ADMIN') return homeFor(user.role)
  if (to.meta.review && !canReview(user.role)) return homeFor(user.role)
  if (to.meta.support && !canSupport(user.role)) return homeFor(user.role)
  if (to.meta.tickets && !canUseTickets(user.role)) return homeFor(user.role)
  return true
}
