import { createApp } from 'vue'
import { createRouter, createWebHashHistory } from 'vue-router'
import App from './App.vue'
import { akiRoutes } from './routes'
// This launcher intentionally supplies no user, credentials or example data.
const router = createRouter({history:createWebHashHistory(),routes:[...akiRoutes,{path:'/:pathMatch(.*)*',redirect:'/aki/member/support'}]})
createApp(App).use(router).mount('#app')
