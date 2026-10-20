import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'
export default defineConfig(({ mode }) => ({
  plugins: [vue()],
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
