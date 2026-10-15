import { createApp, h } from 'vue'
import { createRouter, createWebHashHistory } from 'vue-router'
import { session, restore, frontendArea } from './api'
import { homeFor, guardPage } from './permissions'
import App from './App.vue'
import './styles/core.css'
import './styles/layout.css'
import './styles/support.css'
import './styles/theme.css'

const router = createRouter({
  history: createWebHashHistory(),
  routes: [
    {
      path: '/',
      redirect: () =>
        session.user ? homeFor(session.user.role) : (frontendArea === 'staff' ? '/staff/login' : '/member/login'
        )
    },
    {
      path: '/staff/login',
      component: () =>
        import('./views/StaffLogin.vue'
        )
    },
    {
      path: '/staff/unavailable', component: {
        render: () => h('section',
          { class: 'page' },
          [h('h1', '此版本未包含審核工作區'),
          h('p', '請使用小組完整版進行遊戲審核，或由管理員調整你的職務權限。')]
        )
      }
    },
    {
      path: '/member/login',
      component: () => import('./views/MemberLogin.vue')
    },
    {
      path: '/member/support',
      component: () => import('./views/MemberSupportHome.vue'),
      meta: { member: true }
    },
    {
      path: '/admin/overview',
      component: () => import('./views/PlatformDashboard.vue'),
      meta: { management: true }
    },
    {
      path: '/admin',
      component: () =>
        import('./views/AdminDashboard.vue'),
      meta: { support: true }
    },
    {
      path: '/admin/chats',
      component: () => import('./views/Admin.vue'),
      meta: { support: true }
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
      path: '/guide',
      redirect: () => homeFor(session.user?.role)
    },
    {
      path: '/:pathMatch(.*)*',
      redirect: '/'
    }
  ]
})

await restore()
router.beforeEach(to => {
  const loginPath = frontendArea === 'staff' ? '/staff/login' : '/member/login'

  if (['/member/login', '/staff/login'].includes(to.path) && to.path !== loginPath) return loginPath

  if (!session.user && to.path !== loginPath) return loginPath
  return guardPage(to, session.user, frontendArea)
})

window.addEventListener('auth-expired',
  event =>
    router.push(event.detail?.loginPath || '/staff/login')
)

createApp(App).use(router).mount('#app')
export { router }
