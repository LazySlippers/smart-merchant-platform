import {test,expect} from '@playwright/test'
for(const [kind,port,label] of [['platform',5173,'平台账号'],['merchant',5174,'负责人账号'],['store',5176,'门店账号']] as const){
 for(const mobile of [false,true])test(`${kind} ${mobile?'手机':'桌面'}登录布局与提交`,async({page})=>{
 await page.setViewportSize(mobile?{width:390,height:844}:{width:1440,height:960})
 let body:any
 await page.route('**/api/auth/v1/login',async route=>{body=route.request().postDataJSON();await route.fulfill({status:401,json:{message:'账号或密码错误'}})})
 await page.goto(`http://127.0.0.1:${port}`)
 await expect(page.getByRole('heading',{name:'欢迎回来'})).toBeVisible()
 const submit=page.locator('button[type=submit]');await expect(submit).toBeDisabled()
 await expect(page.getByLabel('租户编号')).toHaveCount(0)
 await page.getByLabel(label,{exact:true}).fill('test-user')
 await page.getByLabel('密码',{exact:true}).fill('test-password')
 await page.screenshot({path:test.info().outputPath(`${kind}-${mobile?'mobile':'desktop'}.png`),fullPage:true})
 await page.getByLabel('密码',{exact:true}).press('Enter')
 await expect.poll(()=>body).toEqual({username:'test-user',password:'test-password'})
 await expect(page.getByText('账号或密码错误',{exact:true})).toBeVisible()
 await expect(page.getByRole('heading',{name:'欢迎回来'})).toBeVisible()
 expect(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth)).toBeTruthy()
 })
}

test('merchant can submit an isolated tenant onboarding application',async({page})=>{
 let submitted:any
 await page.route('**/api/public/v1/plans',route=>route.fulfill({json:[{id:1001,planCode:'STANDARD',planName:'标准版',status:'ACTIVE'},{id:1002,planCode:'ADVANCED',planName:'高级版',status:'ACTIVE'}]}))
 await page.route('**/api/public/v1/tenant-applications',route=>{submitted=route.request().postDataJSON();return route.fulfill({status:201,json:{id:'900001',status:'PENDING'}})})
 await page.goto('http://127.0.0.1:5174')
 await page.getByRole('button',{name:'还没有租户？自主申请入驻'}).click()
 const dialog=page.getByRole('dialog',{name:'租户自主入驻'})
 await expect(dialog).toContainText('数据、门店、商品、会员和订单均独立隔离')
 await dialog.getByLabel('商户 / 品牌名称').fill('春风便利')
 await dialog.getByLabel('负责人姓名').fill('张店长')
 await dialog.getByLabel('负责人手机号（登录账号）').fill('13900008888')
 await dialog.locator('.el-select').click()
 await page.getByRole('option',{name:'高级版'}).click()
 await dialog.getByLabel('登录密码',{exact:true}).fill('Tenant!2026')
 await dialog.getByLabel('确认密码').fill('Tenant!2026')
 await dialog.getByText('我确认资料真实，并同意平台审核后创建独立租户空间').click()
 await dialog.getByRole('button',{name:'提交入驻申请'}).click()
 await expect(dialog.getByText('申请已提交',{exact:true})).toBeVisible()
 await expect(dialog).toContainText('申请编号 900001')
 expect(submitted).toEqual({merchantName:'春风便利',contactName:'张店长',contactMobile:'13900008888',planCode:'ADVANCED',password:'Tenant!2026'})
 expect(submitted.tenantId).toBeUndefined()
})
