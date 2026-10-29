import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { createRouter, createWebHashHistory } from 'vue-router'
import { session, restore, frontendArea, refreshUser, logout } from './api'
import { homeFor, guardPage, hasPermission } from './permissions'
import { backofficeModules } from './backoffice-modules'
import { cartRoutes } from './cart/router/index'

import App from './App.vue'

import './styles/shared.js'

if (frontendArea !== 'staff') {
    await import('./cart/assets/main.css')
    await import('./styles/publisher.css')
    await import('./styles/cart-integration.css')
    await import('./styles/cart-theme.css')
}
const storeRoutes = cartRoutes.map(route => ({
    ...route,
    meta: {
        public: !['ShoppingCart', 'Orders', 'MemberGames', 'Coupons', 'PlayGame'].includes(route.name),
        member: ['ShoppingCart', 'Orders', 'MemberGames', 'Coupons', 'PlayGame'].includes(route.name)
    }
}))
// 前後台分頁名稱；合併模式依目前路由區分。
const updatePageTitle = route => {
    const staffPage = frontendArea === 'staff' || (frontendArea === 'combined' && /^\/(admin|staff|support)(?:\/|$)/.test(route.path))
    document.title = staffPage ? 'HERO 後台管理' : 'HERO 遊戲平台'
}
const routes = [
    { path: '/admin/comments', component: () => import('./views/AdminOperations.vue'), meta: { staff: true, permission: 'COMMENT_REVIEW', workspaceModule: 'comments' } },
    { path: '/admin/reviews', redirect: '/admin/comments?view=reviews' },
    { path: '/admin/review-reports', redirect: '/admin/comments' },
    { path: '/admin/refund-requests', component: () => import('./views/RefundRequests.vue'), meta: { staff: true, permission: 'REFUND_MANAGE' } },
    // 舊合併紀錄網址轉回有權限的原分類頁，不再提供額外的全部紀錄頁面。
    { path: '/admin/system-logs', redirect: () => hasPermission(session.user, 'OPERATION_LOG_VIEW') ? '/admin/operations' : '/admin/login-logs' },
    { path: '/faq', component: () => import('./views/SupportFaq.vue'), meta: { public: true } },
    { path: '/admin/support/reply-templates', component: () => import('./views/SupportContentManagement.vue'), meta: { staff: true, management: true, supportContent: 'reply-templates' } },
    { path: '/admin/support/faqs', component: () => import('./views/SupportContentManagement.vue'), meta: { staff: true, management: true, supportContent: 'faqs' } },
    ...Object.entries(backofficeModules).filter(([area]) => !['reviews', 'review-reports'].includes(area)).map(([area, config]) => ({
        path: '/admin/' + area, component: () =>
            import('./views/AdminOperations.vue'),
        meta: {
            staff: true,
            permission: config.permission,
            anyPermissions: config.anyPermissions,
            workspaceModule: area
        }
    })),
    ...storeRoutes.filter(route => route.name !== 'NotFound'),

    //Member
    {
        path: '/member/login',
        component: () => import('./views/MemberLogin.vue'),
        meta: { public: true }
    },
    {
        path: '/member/register',
        component: () => import('./views/MemberAccount.vue'),
        meta: { public: true }
    },
    {
        path: '/member/verify',
        component: () => import('./views/MemberAccount.vue'),
        meta: { public: true }
    },
    {
        path: '/member/verification-sent',
        component: () => import('./views/MemberAccount.vue'),
        meta: { public: true }
    },
    {
        path: '/member/resend-verification',
        redirect: '/member/verification-sent',
        meta: { public: true }
    },
    {
        path: '/member/forgot-password',
        component: () => import('./views/MemberAccount.vue'),
        meta: { public: true }
    },
    {
        path: '/member/reset-password',
        component: () => import('./views/MemberAccount.vue'),
        meta: { public: true }
    },
    {
        path: '/member/profile',
        component: () => import('./views/MemberAccount.vue'),
        meta: { member: true }
    },
    {
        path: '/member/change-password',
        component: () => import('./views/MemberAccount.vue'),
        meta: { member: true }
    },
    {
        path: '/member/support',
        component: () => import('./views/Tickets.vue'),
        meta: { tickets: true }
    },
    {
        path: '/member/support/:id',
        component: () => import('./views/TicketDetail.vue'),
        props: true,
        meta: { tickets: true }
    },

    //Admin

    // Admin：最高管理員主頁
    {
        path: '/admin',
        component: () => import('./views/PlatformDashboard.vue'),
        meta: { dashboard: true }
    },
    {
        path: '/admin/overview',
        redirect: '/admin'
    },

    // Support：客服人員主頁
    {
        path: '/admin/support',
        component: () => import('./views/AdminDashboard.vue'),
        meta: { support: true }
    },
    {
        path: '/admin/chats',
        component: () => import('./views/Admin.vue'),
        meta: { support: true }
    },

    // Reviewer：審核人員主頁
    {
        path: '/reviewer',
        component: () => import('./views/StaffHome.vue'),
        meta: { review: true }
    },
    {
        path: '/admin/games',
        component: () => import('./views/AdminGames.vue'),
        meta: { review: true }
    },

    {
        path: '/admin/members', component: () => import('./views/AdminMembers.vue'),
        meta: { staff: true, permission: 'MEMBER_SUSPEND' }
    },
    {
        path: '/admin/publishers', component: () => import('./views/AdminPublishers.vue'),
        meta: { staff: true, permission: 'PUBLISHER_REVIEW' }
    },

    // 管理員專用：後台人員管理
    {
        path: '/admin/staff',
        component: () => import('./views/StaffManagement.vue'),
        meta: { management: true }
    },


    {
        path: '/staff/home',
        component: () => import('./views/StaffHome.vue'),
        beforeEnter: () => session.user && session.user.role !== 'STAFF' ? homeFor(session.user.role) : true,
        meta: { staff: true }
    },

    //staff
    {
        path: '/staff/register',
        component: () => import('./views/StaffRegister.vue'),
        meta: { public: true }
    },
    {
        path: '/staff/login',
        component: () => import('./views/StaffLogin.vue'),
        meta: { public: true }
    },

    { path: '/member/apply-publisher', component: () => import('./views/MemberAccount.vue'), meta: { member: true } },

    //publisher
    {
        path: '/publisher/discounts',
        component: () => import('./views/PublisherDiscounts.vue'),
        meta: { publisher: true }
    },
    {
        path: '/publisher/overview',
        component: () => import('./views/PublisherHome.vue'),
        meta: { publisher: true }
    },
    {
        path: '/publisher/games',
        component: () => import('./views/PublisherGames.vue'),
        meta: { publisher: true }
    },
    {
        path: '/publisher/sales',
        component: () => import('./views/PublisherSales.vue'),
        meta: { publisher: true }
    },


    {
        path: '/tickets',
        component: () => import('./views/Tickets.vue'),
        meta: { tickets: true }
    },
    {
        path: '/tickets/:id',
        component: () => import('./views/TicketDetail.vue'),
        props: true,
        meta: { tickets: true }
    },
    {
        path: '/login',
        redirect: () => frontendArea === 'staff' ? '/staff/login' : '/member/login'
    },
    {
        path: '/:pathMatch(.*)*',
        component: () => import('./cart/views/404.vue'),
        meta: { public: true }
    }
]
const router = createRouter({
    history: createWebHashHistory(),
    routes
})
await restore()
router.afterEach(updatePageTitle)
router.beforeEach(async to => {
    if (session.user && !['MEMBER', 'PUBLISHER'].includes(session.user.role)) {
        try { await refreshUser() }
        catch { await logout(); return '/staff/login' }
    }
    // 會員舊案件網址自動導向新網址
    if (['MEMBER', 'PUBLISHER'].includes(session.user?.role)) {
        if (to.path === '/tickets') {
            return '/member/support'
        }

        if (to.path.startsWith('/tickets/')) {
            return '/member/support/' + to.params.id
        }
    }
    if (frontendArea === 'staff' && !session.user && !['/staff/login', '/staff/register'].includes(to.path))
        return '/staff/login'
    if (frontendArea === 'member' && (to.path.startsWith('/staff') || to.path.startsWith('/admin')))
        return '/'
    if (frontendArea === 'staff' && to.meta.public && !['/staff/login', '/staff/register'].includes(to.path))
        return session.user ? homeFor(session.user.role) : '/staff/login'
    if (to.meta.public && !['/member/login', '/staff/login'].includes(to.path))
        return true
    return guardPage(to, session.user, frontendArea)
})
window.addEventListener(
    'auth-expired',
    event => router.push(event.detail?.loginPath || '/member/login')
)
const app = createApp(App).use(createPinia()).use(router)
// 等待初次路由權限判斷，避免遊戲分頁先短暫掛載商城與社交視窗。
await router.isReady()
app.mount('#app')
export { router }
