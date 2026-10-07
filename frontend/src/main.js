// 引入全域樣式
import './assets/main.css'

// createApp：建立 Vue 應用程式實例
import { createApp } from 'vue'
// createPinia：建立 Pinia 狀態管理（全域共用資料，例如購物車）
import { createPinia } from 'pinia'

// 根元件
import App from './App.vue'
// 路由設定
import router from './router'

// 以 App.vue 為根元件建立應用程式
const app = createApp(App)

// 註冊 Pinia 與 router 外掛
app.use(createPinia())
app.use(router)

// 把應用程式掛載到 index.html 中 id="app" 的元素上
app.mount('#app')
