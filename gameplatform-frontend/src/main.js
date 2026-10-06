import { createApp } from 'vue'
import { createRouter, createWebHashHistory } from 'vue-router'
import App from './App.vue'
import Login from './views/Login.vue'
import Tickets from './views/Tickets.vue'
import TicketDetail from './views/TicketDetail.vue'
import Admin from './views/Admin.vue'
import AdminDashboard from './views/AdminDashboard.vue'
import { session, restore } from './api'
import './style.css'
const router = createRouter({
  history : createWebHashHistory(), routes : [
  {
    path : '/login', component : Login
  }, {
    path : '/', redirect : () => session.user?.role === 'ADMIN' ? '/admin' : '/tickets'
  }, {
    path : '/guide', redirect : '/tickets'
  }, {
    path : '/tickets', component : Tickets
  }, {
    path : '/tickets/:id', component : TicketDetail, props : true
  }, {
    path : '/admin', component : AdminDashboard
  }, {
    path : '/admin/chats', component : Admin
  }, {
    path : '/:pathMatch(.*)*', redirect : '/'
  }
  ]
})
await restore()
router.beforeEach(to => {
  if (!session.user && to.path !== '/login') return '/login';if (session.user && to.path === '/login') return session.user.role === 'ADMIN' ? '/admin' : '/tickets';if (to.path.startsWith('/admin') && session.user?.role !== 'ADMIN') return '/tickets';if (session.user?.role === 'ADMIN' && ['/', '/guide'].includes(to.path)) return '/admin'
})
window.addEventListener('auth-expired', () => router.push('/login'))
createApp(App).use(router).mount('#app')
