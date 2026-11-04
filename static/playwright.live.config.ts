import {defineConfig} from '@playwright/test'
export default defineConfig({testDir:'tests/live',outputDir:'test-results-live',timeout:60000,use:{baseURL:'http://127.0.0.1:8017',browserName:'chromium',channel:'msedge',headless:true},workers:1,reporter:'list'})
