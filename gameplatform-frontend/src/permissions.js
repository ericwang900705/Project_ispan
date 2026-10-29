export const staffRoles = [
  'ADMIN',
  'STAFF',
  'REVIEWER',
  'SUPPORT'
]

export const isHighestAdmin = user =>
  user?.role === 'ADMIN'

// Management capabilities can only be granted by a highest administrator.
export const permissionCatalog = [
  {
    "code": "DASHBOARD_VIEW",
    "name": "平台主頁",
    "group": "平台概況",
    "description": "查看平台會員、遊戲、訂單與營收統計",
    "status": "full",
    "reserved": false
  },
  {
    "code": "STAFF_MANAGE",
    "name": "後台人員管理",
    "group": "後台人員",
    "description": "查看後台人員與新增待授權帳號",
    "status": "reserved",
    "reserved": true
  },
  {
    "code": "MEMBER_SUSPEND",
    "name": "會員管理",
    "group": "會員",
    "description": "管理會員狀態與處理會員資料",
    "status": "full",
    "reserved": false
  },
  {
    "code": "PUBLISHER_REVIEW",
    "name": "發行商管理與審核",
    "group": "發行商",
    "description": "查詢發行商申請與處理審核",
    "status": "full",
    "reserved": false
  },
  {
    "code": "ORDER_VIEW",
    "name": "訂單列表",
    "group": "訂單",
    "description": "查詢平台訂單與訂單明細",
    "status": "full",
    "reserved": false
  },
  {
    "code": "REFUND_MANAGE",
    "name": "退款訂單查詢",
    "group": "訂單",
    "description": "處理退款申請與查詢退款訂單；正式金流需核對實際退款紀錄",
    "status": "partial",
    "reserved": false
  },
  {
    "code": "GAME_REVIEW",
    "name": "遊戲管理與審核",
    "group": "遊戲",
    "description": "查詢遊戲、處理上架下架審核與紀錄",
    "status": "full",
    "reserved": false
  },
  {
    "code": "TAG_MANAGE",
    "name": "平台標籤維護",
    "group": "遊戲",
    "description": "維護平台遊戲標籤",
    "status": "full",
    "reserved": false
  },
  {
    "code": "TICKET_HANDLE",
    "name": "客服管理",
    "group": "客服",
    "description": "工作總覽、即時聊天室與客服案件",
    "status": "full",
    "reserved": false
  },
  {
    "code": "COMMENT_REVIEW",
    "name": "評論審核",
    "group": "評論",
    "description": "查詢與處理評論審核",
    "status": "full",
    "reserved": false
  },
  {
    "code": "PROMOTION_MANAGE",
    "name": "優惠活動列表",
    "group": "優惠活動",
    "description": "查看發行商的遊戲折扣、活動時間及冷卻狀態；折扣由發行商建立",
    "status": "full",
    "reserved": false
  },
  {
    "code": "COUPON_MANAGE",
    "name": "優惠券管理",
    "group": "優惠活動",
    "description": "建立與管理平台優惠券",
    "status": "full",
    "reserved": false
  },
  {
    "code": "PERMISSION_MANAGE",
    "name": "管理員權限管理",
    "group": "授權",
    "description": "僅最高管理員可設定 STAFF 的個別業務權限",
    "status": "reserved",
    "reserved": true
  },
  {
    "code": "OPERATION_LOG_VIEW",
    "name": "操作紀錄",
    "group": "系統紀錄",
    "description": "查看標籤、優惠券與評論管理操作紀錄",
    "status": "full",
    "reserved": false
  },
  {
    "code": "LOGIN_LOG_VIEW",
    "name": "登入紀錄",
    "group": "系統紀錄",
    "description": "查看會員密碼與 Google 登入紀錄",
    "status": "full",
    "reserved": false
  }
]

export const permissionNames =
  Object.fromEntries(permissionCatalog.map(
    item => [
      item.code,
      item.name
    ]
  ))

export const grantableCodes = permissionCatalog
  .filter(item => !item.reserved)
  .map(item => item.code)

export const fixedPermissions = {
  REVIEWER: [
    'GAME_REVIEW',
    'PUBLISHER_REVIEW',
    'MEMBER_SUSPEND',
    'COMMENT_REVIEW'
  ],
  SUPPORT: ['TICKET_HANDLE']
}

export const permissionOptions = role =>
  role === 'ADMIN'
    ? permissionCatalog.map(item => item.code)
    : role === 'STAFF'
      ? grantableCodes
      : fixedPermissions[role] || []

export const canManageStaff = user => isHighestAdmin(user)

export const hasPermission = (user, code) => isHighestAdmin(user) ||
  (permissionOptions(user?.role).includes(code) && user?.permissions?.includes(code))

// Role eligibility for shared view formatting; API and route access require grants.
export const canSupport = role => staffRoles.includes(role)

export const canUseTickets = role =>
  ['MEMBER', 'PUBLISHER'].includes(role) ||
  canSupport(role)

export const homeFor = role => ({
  ADMIN: '/admin',
  STAFF: '/staff/home',
  SUPPORT: '/admin/support',
  REVIEWER: '/reviewer',
  MEMBER: '/',
  PUBLISHER: '/publisher/overview'
}[role] || '/member/login')

export const senderTypeFor = role =>
  staffRoles.includes(role) ? 'ADMIN' : ['MEMBER', 'PUBLISHER'].includes(role) ? 'MEMBER' : role

export const roleName = role => ({
  ADMIN: '最高管理員',
  STAFF: 'Staff 人員',
  REVIEWER: '審核人員',
  SUPPORT: '客服人員',
  PENDING: '待授權',
  MEMBER: '會員',
  PUBLISHER: '發行商'
}[role] || '')

export const loginFor = role => role && !['MEMBER', 'PUBLISHER'].includes(role) ? '/staff/login' : '/member/login'

export function guardPage(to, user, lastArea = 'member') {
  const loginPage = [
    '/member/login',
    '/staff/login',
    '/login'
  ].includes(to.path)
  if (!user) {
    if (loginPage) return true
    return {
      path: to.meta.support ||
        to.meta.management ||
        to.meta.review ||
        to.meta.dashboard ||
        to.meta.staff ||
        lastArea === 'staff' ? '/staff/login' : '/member/login',
      query: { redirect: to.fullPath }
    }
  }

  if (loginPage)
    return homeFor(user.role)
  if (to.meta.anyPermissions && !to.meta.anyPermissions.some(code => hasPermission(user, code))) return homeFor(user.role)
  if (to.meta.permission && !hasPermission(user, to.meta.permission))
    return homeFor(user.role)
  if (to.meta.staff && !staffRoles.includes(user.role))
    return homeFor(user.role)
  if (to.meta.publisher && user.role !== 'PUBLISHER')
    return homeFor(user.role)
  if (to.meta.review && !hasPermission(user, 'GAME_REVIEW'))
    return homeFor(user.role)
  if (to.meta.dashboard && !hasPermission(user, 'DASHBOARD_VIEW'))
    return homeFor(user.role)
  if (to.meta.management && !canManageStaff(user))
    return homeFor(user.role)
  if (to.meta.member && !['MEMBER', 'PUBLISHER'].includes(user.role))
    return homeFor(user.role)
  if (to.meta.support && !hasPermission(user, 'TICKET_HANDLE'))
    return homeFor(user.role)
  if (to.meta.tickets && !['MEMBER', 'PUBLISHER'].includes(user.role) && !hasPermission(user, 'TICKET_HANDLE'))
    return homeFor(user.role)
  return true
}

// Role suggestions never save grants automatically. REVIEWER/SUPPORT are fixed roles.
export const roleDefaultPermissions = role =>
  role === 'STAFF' ? ['DASHBOARD_VIEW']
    : [...(fixedPermissions[role] || [])]
