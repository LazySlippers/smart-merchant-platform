import { defineConfig, devices } from '@playwright/test'
export default defineConfig({
  testDir: './tests/consumer', timeout: 30000, fullyParallel: false,
  use: { ...devices['iPhone 13'], browserName: 'chromium', baseURL: 'http://127.0.0.1:5177', screenshot: 'only-on-failure', trace: 'retain-on-failure' },
  webServer: { command: 'pnpm --filter @smart-merchant/consumer-web dev --host 127.0.0.1', url: 'http://127.0.0.1:5177', reuseExistingServer: true },
})
