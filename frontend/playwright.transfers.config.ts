import {defineConfig,devices} from '@playwright/test'
export default defineConfig({
 testDir:'./tests/e2e',testMatch:'store-transfers.spec.ts',timeout:45000,
 use:{...devices['Desktop Chrome'],baseURL:'http://127.0.0.1:5176',screenshot:'only-on-failure'},
 webServer:{command:'pnpm --filter @smart-merchant/store-web dev --host 127.0.0.1',url:'http://127.0.0.1:5176',reuseExistingServer:true},
})
