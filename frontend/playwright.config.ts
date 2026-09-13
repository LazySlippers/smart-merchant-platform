import { defineConfig, devices } from '@playwright/test'

export default defineConfig({
  testDir: './tests/e2e',
  timeout: 60_000,
  expect: { timeout: 10_000 },
  fullyParallel: false,
  retries: process.env.CI ? 2 : 0,
  reporter: process.env.CI ? [['html', { open: 'never' }], ['list']] : 'list',
  use: { trace: 'retain-on-failure', screenshot: 'only-on-failure' },
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],
  webServer: [
    { command: 'pnpm --filter @smart-merchant/store-web dev --host 127.0.0.1', url: 'http://127.0.0.1:5176', reuseExistingServer: !process.env.CI },
    { command: 'pnpm --filter @smart-merchant/platform-web dev --host 127.0.0.1', url: 'http://127.0.0.1:5173', reuseExistingServer: !process.env.CI },
    { command: 'pnpm --filter @smart-merchant/merchant-web dev --host 127.0.0.1', url: 'http://127.0.0.1:5174', reuseExistingServer: !process.env.CI },
  ],
})
