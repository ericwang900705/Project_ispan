import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { fileURLToPath, URL } from 'node:url'
export default defineConfig({
  plugins : [vue()], resolve: { alias: { '@cart': fileURLToPath(new URL('./src/cart', import.meta.url)) } }, server : {
    port : 5173, strictPort : true, proxy : {
      '/uploads' : { target: 'http://127.0.0.1:8080', changeOrigin: true },
      '/api' : {
        target : 'http://127.0.0.1:8080', changeOrigin : true
      }
    }
  }
})
