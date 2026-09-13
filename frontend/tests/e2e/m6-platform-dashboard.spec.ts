import { expect, test, type APIRequestContext } from '@playwright/test'

const apiBase = process.env.M6_API_BASE_URL ?? 'http://127.0.0.1:8080'
const adminUser = process.env.BOOTSTRAP_ADMIN_USERNAME ?? 'platform-admin'
const adminPassword = process.env.BOOTSTRAP_ADMIN_PASSWORD ?? 'change-me-platform-admin'

async function login(request: APIRequestContext, username: string, password: string) {
  const response = await request.post(`${apiBase}/api/auth/v1/login`, { data: { username, password } })
  expect(response.ok()).toBeTruthy()
  const body = await response.json()
  return (body.data ?? body).accessToken as string
}

test('平台管理员可查看租户与经营汇总', async ({ page }) => {
  await page.goto('http://127.0.0.1:5173')
  await page.getByLabel('平台账号').fill(adminUser)
  await page.getByLabel('密码').fill(adminPassword)
  await page.getByRole('button', { name: '登录平台' }).click()
  await expect(page.getByRole('button', {name:/租户管理/})).toBeVisible()
  await page.getByRole('button', { name: '刷新数据', exact:true }).click()
  await expect(page.getByText(/活跃交易租户/)).toBeVisible()
})

test('新租户从平台审核后可进入后台经营总览', async ({ page, request }) => {
  const suffix = `${Date.now()}`.slice(-10)
  const mobile = `13${suffix}`
  const password = 'M6-E2E!2026'
  const applied = await request.post(`${apiBase}/api/public/v1/tenant-applications`, {
    data: { merchantName: `M6-${suffix}`, contactName: 'M6验收', contactMobile: mobile, planCode: 'STANDARD', password },
  })
  expect(applied.status()).toBe(201)
  const appliedBody = await applied.json()
  const applicationId = (appliedBody.data ?? appliedBody).id
  const adminToken = await login(request, adminUser, adminPassword)
  const approved = await request.post(`${apiBase}/api/platform/v1/tenant-applications/${applicationId}:approve`, {
    headers: { Authorization: `Bearer ${adminToken}` },
  })
  expect(approved.ok()).toBeTruthy()
  const approvedBody = await approved.json()
  const tenantId = Number((approvedBody.data ?? approvedBody).id)

  await page.goto('http://127.0.0.1:5174')
  await expect(page.getByLabel('租户编号')).toHaveCount(0)
  await page.getByLabel('负责人账号').fill(mobile)
  await page.getByLabel('密码').fill(password)
  await page.getByRole('button', { name: '进入 ERP' }).click()
  await expect(page.getByRole('heading', {name: '经营总览 经营数据中心'})).toBeVisible()
  await expect(page.getByRole('heading', {name:'营收与订单趋势',exact:true})).toBeVisible()
})
