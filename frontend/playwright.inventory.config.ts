import {defineConfig,devices} from '@playwright/test'
export default defineConfig({
 testDir:'./tests/e2e',testMatch:'tenant-inventory.spec.ts',timeout:45000,
 use:{...devices['Desktop Chrome'],screenshot:'only-on-failure'},
 webServer:[
  {command:'pnpm --filter @smart-merchant/merchant-web dev --host 127.0.0.1',url:'http://127.0.0.1:5174',reuseExistingServer:true},
  {command:'pnpm --filter @smart-merchant/store-web dev --host 127.0.0.1',url:'http://127.0.0.1:5176',reuseExistingServer:true},
 ],
})
