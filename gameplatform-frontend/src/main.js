import { createApp } from 'vue'
import { createPinia } from 'pinia'
import { createRouter, createWebHashHistory } from 'vue-router'
import { session, restore } from './api'

import App from './App.vue'
import MemberLogin from './views/MemberLogin.vue'
const MemberAccount = () => import('./views/MemberAccount.vue')
const StaffLogin = () => import('./views/StaffLogin.vue')
const Tickets = () => import('./views/Tickets.vue')
const TicketDetail = () => import('./views/TicketDetail.vue')
const Admin = () => import('./views/Admin.vue')
const AdminDashboard = () => import('./views/AdminDashboard.vue')

// HERO styles: base → layout/features → theme (final overrides)
import './styles/core.css'
import './styles/layout.css'
import './styles/publisher.css'
import './styles/support.css'
import './styles/theme.css'
const PublisherSales = () => import('./views/PublisherSales.vue')
const PublisherNotifications = () => import('./views/PublisherNotifications.vue')
const PublisherHome = () => import('./views/PublisherHome.vue')
const PublisherGames = () => import('./views/PublisherGames.vue')
const PublisherOffers = () => import('./views/PublisherOffers.vue')
const MemberSupportHome = () => import('./views/MemberSupportHome.vue')
const PlatformDashboard = () => import('./views/PlatformDashboard.vue')
const AdminGameSearch = () => import('./views/AdminGameSearch.vue')
const CommentReview = () => import('./views/CommentReview.vue')
const Review = () => import('./views/Review.vue')
import { homeFor, guardPage } from './permissions'

// --- 路由設定 ---
const StoreLayout = () => import('./cart/StoreLayout.vue')
const cartView = name => () => import(`./cart/views/${name}.vue`)

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    { path: '/', component: StoreLayout, children: [
      { path: '', component: cartView('GamesHome') },
      { path: 'games/all', component: cartView('Games') },
      { path: 'games/:id', component: cartView('GameDetail') },
      { path: 'orders', component: cartView('Orders'), meta: { shopMember: true } },
      { path: 'shopping-cart', component: cartView('ShoppingCart'), meta: { shopMember: true } },
      { path: 'point-store', component: cartView('PointStore') },
      { path: 'member-games', component: cartView('MemberGames'), meta: { shopMember: true } },
      { path: 'about', component: cartView('AboutView') }
    ] },
    {path:'/member/support',component:MemberSupportHome,meta:{member:true}},
    {path:'/publisher/sales',component:PublisherSales,meta:{publisher:true}},
    {path:'/publisher/notifications',component:PublisherNotifications,meta:{publisher:true}},
    {path:'/publisher/games',component:PublisherGames,meta:{publisher:true}},
    {path:'/publisher/offers',component:PublisherOffers,meta:{publisher:true}},
    {path:'/publisher/overview', component:PublisherHome, meta:{publisher:true}},
    {path:'/admin/overview', component:PlatformDashboard, meta:{management:true}},
    {path:'/admin/games', component:AdminGameSearch, meta:{management:true}},
    {path:'/member/login',component:MemberLogin},
    {path:'/member/register',component:MemberAccount},
    {path:'/member/verify',component:MemberAccount},
    {path:'/member/verification-sent',component:MemberAccount},
    {path:'/member/resend-verification',redirect:'/member/verification-sent'},
    {path:'/member/forgot-password',component:MemberAccount},
    {path:'/member/reset-password',component:MemberAccount},
    {path:'/member/profile',component:MemberAccount,meta:{member:true}},
    {path:'/staff/login',component:StaffLogin},
    {path:'/review/comments', component:CommentReview, meta:{review:true}},
    {path:'/review', component:Review, meta:{review:true}},
    {
      path: '/login',
      redirect: '/member/login'
    },
    {
      path: '/guide',
      redirect: () => homeFor(session.user?.role)
    },
    {
      path: '/tickets',
      meta: {tickets:true}, component: Tickets
    },
    {
      path: '/tickets/:id',
      meta: {tickets:true}, component: TicketDetail,
      props: true
    },
    {
      path: '/admin',
      meta: {support:true}, component: AdminDashboard
    },
    {
      path: '/admin/chats',
      meta: {support:true}, component: Admin
    },
    {
      path: '/:pathMatch(.*)*',
      redirect: '/'
    }
  ]
})

// --- 初始化會話狀態再掛載應用 ---
await restore()

// --- 全域路由守衛 (權限控管) ---
router.beforeEach(to => guardPage(to, session.user, session.loginArea))

// --- 監聽登入逾期事件 ---
window.addEventListener('auth-expired', event => {
  router.push(event.detail?.loginPath || '/member/login')
})

// --- 建立 Vue 應用並掛載 ---
createApp(App).use(createPinia()).use(router).mount('#app')
export { router }
