export const canSupport = role => ['ADMIN','SUPPORT'].includes(role)
export const canReview = role => ['ADMIN','REVIEWER'].includes(role)
export const canUseTickets = role => role === 'MEMBER' || canSupport(role)
export const homeFor = role => ({ADMIN:'/aki/admin',SUPPORT:'/aki/admin',REVIEWER:'/aki/review',MEMBER:'/aki/member/support',PUBLISHER:'/aki/publisher/overview'}[role] || '/aki')
export const senderTypeFor = role => canSupport(role) ? 'ADMIN' : role
export const roleName = role => ({ADMIN:'管理者',SUPPORT:'客服人員',REVIEWER:'審核人員',MEMBER:'會員',PUBLISHER:'發行商'}[role] || '')
