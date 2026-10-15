import { fileURLToPath, URL } from 'node:url'

import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import vueDevTools from 'vite-plugin-vue-devtools'

// Vite 設定檔（官方說明：https://vite.dev/config/）
// ⚠️ 這個檔案改完，要把 npm run dev 停掉（Ctrl+C）再重新執行才會生效
export default defineConfig({
  // 外掛：vue() 讓 Vite 看得懂 .vue 檔；vueDevTools() 是瀏覽器裡的 Vue 除錯工具
  plugins: [
    vue(),
    vueDevTools(),
  ],

  // 開發伺服器（npm run dev）的設定
  server: {
    // 前端的 port：網址就是 http://localhost:5180
    // 改這個數字的話，後端 application.properties 的 app.frontend-url 也要改成一樣
    //（綠界付款完會導回那個網址）
    port: 5180,
    // true：port 被別的程式佔用就直接報錯，不會偷偷換成 5174、5175…
    strictPort: true,

    // 代理：前端呼叫 /api/... 時，自動轉送給後端（Spring Boot，port 8080）
    // 所以前端程式碼裡只要寫 axios.get('/api/cart/1')，不用寫 localhost:8080
    proxy: {
      '/api': {
        target: 'http://localhost:8080', // 後端的位置（這是後端的 port，不是前端的）
        changeOrigin: true,              // 轉送時把請求的來源改成後端自己，避免被擋
      },
    },
  },

  resolve: {
    // 路徑別名：import 時寫 '@/components/...' 就等於 'src/components/...'
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
})
