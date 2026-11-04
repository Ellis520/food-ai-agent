import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
export default defineConfig({plugins:[vue()],base:'./',optimizeDeps:{noDiscovery:true,include:[]},build:{rollupOptions:{output:{manualChunks:{charts:['echarts']}}}},server:{port:5173,strictPort:true,proxy:{'/api':{target:process.env.FOODMOMENT_BACKEND_URL||'http://127.0.0.1:8017',ws:true}}}})
