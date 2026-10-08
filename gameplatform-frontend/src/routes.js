import { defineAsyncComponent, defineComponent, h } from 'vue'
import AkiBoundary from './components/AkiBoundary.vue'
const page = (feature,label,roles,load) => defineComponent({ setup() { const content = defineAsyncComponent(load); return () => h(AkiBoundary,{feature,label,roles},{default: () => h(content)}) } })
export const akiRoutes = [
  { path:'/aki/member/support', component:page('support','會員客服',['MEMBER'],() => import('./views/MemberSupportHome.vue')) },
  { path:'/aki/admin', component:page('support','客服工作區',['ADMIN','SUPPORT'],() => import('./views/AdminDashboard.vue')) },
  { path:'/aki/admin/chats', component:page('support','即時客服',['ADMIN','SUPPORT'],() => import('./views/Admin.vue')) },
  { path:'/aki/tickets', component:page('support','客服案件',['MEMBER','ADMIN','SUPPORT'],() => import('./views/Tickets.vue')) },
  { path:'/aki/tickets/:id', component:page('support','案件詳情',['MEMBER','ADMIN','SUPPORT'],() => import('./views/TicketDetail.vue')) },
  { path:'/aki/review', component:page('gameReview','遊戲上架審核',['ADMIN','REVIEWER'],() => import('./views/Review.vue')) },
  { path:'/aki/review/comments', component:page('commentReview','評論審核',['ADMIN','REVIEWER'],() => import('./views/CommentReview.vue')) },
  { path:'/aki/admin/games', component:page('adminGames','管理員遊戲查詢與強制下架',['ADMIN'],() => import('./views/AdminGameSearch.vue')) },
  { path:'/aki/publisher/overview', component:page('publisher','發行商主頁',['PUBLISHER'],() => import('./views/PublisherHome.vue')) },
  { path:'/aki/publisher/games', component:page('publisher','發行商遊戲管理',['PUBLISHER'],() => import('./views/PublisherGames.vue')) },
  { path:'/aki/publisher/sales', component:page('publisher','銷售統計',['PUBLISHER'],() => import('./views/PublisherSales.vue')) },
  { path:'/aki/publisher/notifications', component:page('publisher','審核通知',['PUBLISHER'],() => import('./views/PublisherNotifications.vue')) }
]
