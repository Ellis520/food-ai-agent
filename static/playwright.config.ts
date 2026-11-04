import {defineConfig} from '@playwright/test'
const python=process.env.FOODMOMENT_PYTHON||'python'
// Keep test providers and database separate from the configured 8017 preview.
const backend='http://127.0.0.1:8018'
export default defineConfig({
 testDir:'tests/e2e',timeout:30000,
 use:{baseURL:'http://127.0.0.1:5173',browserName:'chromium',channel:'msedge',headless:true},
 workers:1,
 webServer:[
  {command:`"${python}" -m uvicorn main:app --app-dir .. --port 8018 --no-access-log`,url:backend+'/api/health',env:{FOODMOMENT_DB:'../data/e2e.sqlite',DASHSCOPE_API_KEY:'',AMAP_WEB_SERVICE_KEY:'',FUNASR_WS_URL:''},reuseExistingServer:false},
  {command:'npm run dev',url:'http://127.0.0.1:5173',env:{VITE_DATA_MODE:'demo',FOODMOMENT_BACKEND_URL:backend},reuseExistingServer:false},
  {command:'npm run dev -- --port 5174',url:'http://127.0.0.1:5174',env:{VITE_DATA_MODE:'api',FOODMOMENT_BACKEND_URL:backend},reuseExistingServer:false}
 ],reporter:'list'
})
