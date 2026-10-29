import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'
export default defineConfig(({ mode }) => ({
  plugins: [vue(), {
    name: 'hero-page-title',
    transformIndexHtml: html => html.replace(/<title>.*?<\/title>/, `<title>${mode === 'staff' ? 'HERO 後台管理' : 'HERO 遊戲平台'}</title>`)
  }],
  // 兩個開發伺服器使用各自的依賴快取，避免同時啟動時互相重建。
  cacheDir: fileURLToPath(new URL(`./node_modules/.vite/${mode}`, import.meta.url)),
  resolve: { alias: {"@": fileURLToPath(new URL("./src",import.meta.url))} },
  server: {
    port: mode === 'staff' ? 5174 : 5173,
    strictPort: true,
    proxy: {
      '/ws':{target:'ws://127.0.0.1:8080',ws:true,changeOrigin:true},
      '/uploads':{target:'http://127.0.0.1:8080',changeOrigin:true},
      '/api': {
        target: 'http://127.0.0.1:8080',
        changeOrigin: true
      }
    }
  }
}))
