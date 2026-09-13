import { test, expect, type Page } from '@playwright/test'
const tenant = '9223372036854775800', sku = '9223372036854775701'
const stores = [{ id: '501', storeName: '绿叶生活 · 湖滨店', address: '湖滨路 18 号', businessHours: '09:00–22:00' }, { id: '502', storeName: '绿叶生活 · 城西店', address: '文一路 26 号', businessHours: '10:00–21:00' }]
const products = [{ categoryId: '1', categoryName: '日常精选', spuId: '20', productName: '鲜奶拿铁', description: '香醇鲜奶，温柔唤醒每一天', imageUrl: '', skuId: sku, skuName: '标准杯 · 热', effectivePriceCents: 1800, availableQuantity: 5, selectable: true }, { categoryId: '2', categoryName: '烘焙好物', spuId: '21', productName: '黄油可颂', description: '层层酥香', imageUrl: '', skuId: '22', skuName: '原味', effectivePriceCents: 1200, availableQuantity: 4, selectable: true }]
async function enterStore(page:Page,url='/'){await page.goto(url);if(!url.includes('tenantId='))await page.locator('.market-store').first().click();await expect(page.getByLabel('搜索商品')).toBeVisible()}

test('an address added at checkout is reused at another merchant',async({page})=>{
  await fixtures(page)
  await page.addInitScript(()=>sessionStorage.setItem('consumer.account',JSON.stringify({accessToken:'valid',mobile:'13800100001',displayName:'小叶'})))
  const addresses:any[]=[];let saves=0
  await page.route('**/api/consumer/v1/auth/account/addresses',route=>{
    if(route.request().method()==='POST'){addresses.push({...route.request().postDataJSON(),id:'1',isDefault:true});saves++}
    return route.fulfill({json:addresses})
  })
  await enterStore(page,`/?tenantId=${tenant}&storeId=501`)
  await page.getByRole('button',{name:'添加鲜奶拿铁 标准杯 · 热'}).click();await page.getByRole('button',{name:'去结算 ›'}).click()
  await page.getByRole('button',{name:'快递邮寄',exact:true}).click();await page.getByRole('button',{name:'管理 / 新增通用地址'}).click()
  await page.getByRole('button',{name:'＋ 新增收货地址'}).click()
  await page.getByLabel('收货人').fill('小叶');await page.getByLabel('手机号',{exact:true}).fill('13800100001');await page.getByLabel('省份',{exact:true}).fill('浙江省');await page.getByLabel('城市').fill('杭州市');await page.getByLabel('区县').fill('西湖区');await page.getByLabel('详细地址',{exact:true}).fill('文一路18号')
  await page.getByRole('button',{name:'保存地址'}).click();await page.getByRole('button',{name:'使用此地址'}).click()
  await expect(page.getByRole('dialog',{name:'确认订单'}).getByLabel('详细地址')).toHaveValue('杭州市西湖区文一路18号')
  await enterStore(page,'/?tenantId=62&storeId=501')
  await page.getByRole('button',{name:'添加鲜奶拿铁 标准杯 · 热'}).click();await page.getByRole('button',{name:'去结算 ›'}).click();await page.getByRole('button',{name:'快递邮寄',exact:true}).click()
  await expect(page.getByRole('dialog',{name:'确认订单'}).getByLabel('收件人',{exact:true})).toHaveValue('小叶')
  await expect(page.getByRole('dialog',{name:'确认订单'}).getByLabel('详细地址')).toHaveValue('杭州市西湖区文一路18号')
  expect(saves).toBe(1)
})
async function fixtures(page: Page, loseFirstResponse = false) {
  let status = 'PENDING_PAYMENT', creates: any[] = []
  const order = () => ({ id: '90001', storeId: '501', orderNo: 'O90001', customerName: '小叶', customerMobile: '13800100001', status, totalQuantity: 1, totalAmountCents: 1800, payableAmountCents: 1800, couponDiscountCents: 0, pointsUsed: 0, storedValueUsedCents: 0, expiresAt: '2099-01-01T12:00:00', pickupCode: status === 'PICKUP_READY' ? 'ABCD123456' : null, items: [{ skuId: sku, productName: '鲜奶拿铁', skuName: '标准杯 · 热', quantity: 1, lineAmountCents: 1800 }] })
  await page.route('**/api/consumer/v1/**', async route => {
    const url = new URL(route.request().url()), path = url.pathname, body = route.request().postDataJSON()
    let data: any
    if (path.endsWith('/brand')) data = { displayName: '绿叶生活', logoUrl: '', themeColor: '#284f3d', headline: '把喜欢的好物带进日常。', contactPhone: '' }
    else if (path.endsWith('/auth/account/login')) data = { accessToken: 'global-consumer', expiresIn: 7200, mobile: '13800100001', displayName: '小叶' }
    else if (path.endsWith('/auth/account/enter')) data = { accessToken: 'test-consumer', expiresIn: 7200, member: true }
    else if (path.endsWith('/auth/account/memberships')) data = [{ tenantId: tenant, memberId: '40', memberName: '小叶', status: 'ACTIVE' }]
    else if (path.endsWith('/auth/account/addresses')) data = []
    else if (path.endsWith('/products')) data = { store: stores[0], products }
    else if (path==='/api/consumer/v1/catalog/stores') data=stores.map(s=>({...s,tenantId:tenant,city:'杭州市',district:'西湖区',coverUrl:'',pickupEnabled:true,shippingEnabled:true,distanceKm:null}))
    else if (path.endsWith('/fulfillment')) data={pickupEnabled:true,shippingEnabled:true,firstShippingCents:800,extraShippingCents:200,excludedProvinces:[],pickupOnlySkus:[]}
    else if (path.endsWith('/stores')) data = stores
    else if (path.endsWith('/wallet')) data = { id: '40', mobile: '13800100001', memberName: '小叶', availablePoints: 100, storedValueCents: 0 }
    else if (path.endsWith('/profile')) data = { name: '小叶', notifications: true }
    else if (path.endsWith('/profile/favorites')) data = []
    else if (path.endsWith('/coupons') || path.endsWith('/offers')) data = []
    else if (path.endsWith('/capabilities')) data = { simulatedPaymentEnabled: true }
    else if (path.endsWith('/quote')) data = { payableAmountCents: 1800, couponDiscountCents: 0, pointsUsed: 0, storedValueUsedCents: 0 }
    else if (path.endsWith('/simulate-payment')) { status = 'PICKUP_READY'; data = order() }
    else if (path.endsWith('/cancel')) { status = 'CLOSED'; data = order() }
    else if (path.endsWith('/orders') && route.request().method() === 'POST') { creates.push(body); if (loseFirstResponse && creates.length === 1) return route.abort('failed'); data = order() }
    else if (path.endsWith('/orders')) data = creates.length ? [order()] : []
    else if (path.endsWith('/90001')) data = order()
    else return route.fulfill({ status: 404, json: { message: `Unhandled fixture: ${path}` } })
    await route.fulfill({ json: data })
  })
  return creates
}
test('mobile purchase, login return, quote, payment and refresh recovery', async ({ page }) => {
  const creates = await fixtures(page)
  await enterStore(page,`/?live=1&tenantId=${tenant}&storeId=501`)
  await page.getByRole('button', { name: '添加鲜奶拿铁 标准杯 · 热' }).click()
  await page.getByRole('button', { name: '去结算 ›' }).click()
  await page.getByLabel('手机号', { exact: true }).fill('13800100001')
  await page.getByLabel('密码', { exact: true }).fill('Consumer!12345')
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await expect(page.getByRole('dialog', { name: '确认订单' })).toBeVisible()
  await page.getByRole('button', { name: '确认商品并试算' }).click()
  await page.getByRole('button', { name: '下单并支付' }).click()
  await expect(page.getByRole('heading', { name: '待自提', exact: true })).toBeVisible()
  expect(creates).toHaveLength(1); expect(creates[0].tenantId).toBe(tenant); expect(creates[0].items[0].skuId).toBe(sku)
  await expect(page.getByText('ABCD123456')).toBeVisible()
  await page.reload()
  await page.getByRole('navigation',{name:'主导航'}).getByRole('button',{name:'订单'}).click()
  await page.locator('.market-visit').filter({hasText:'O90001'}).click()
  await expect(page.getByText('ABCD123456')).toBeVisible()
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  await page.screenshot({ path: 'apps/consumer-web/qa/marketplace-order-mobile.png', fullPage: true })
})

test('provider payment creates an attempt, waits for sandbox callback and shows the final result',async({page})=>{
  await fixtures(page)
  let status='PENDING_PAYMENT',created=0,completed=0
  await page.route('**/api/consumer/v1/payments/capabilities',r=>r.fulfill({json:[{code:'SANDBOX',enabled:true,displayName:'本地沙箱'},{code:'WECHAT',enabled:false,displayName:'微信支付'}]}))
  await page.route('**/api/consumer/v1/orders/90001/payments',r=>{created++;return r.fulfill({json:{id:'70001',orderId:'90001',provider:'SANDBOX',status:'PENDING',amountCents:1800,checkoutType:'SANDBOX',expiresAt:'2099-01-01T12:00:00'}})})
  await page.route('**/api/consumer/v1/payments/70001/sandbox/complete',r=>{completed++;status='PICKUP_READY';return r.fulfill({json:{result:'SUCCEEDED',paymentId:'70001'}})})
  await page.route('**/api/consumer/v1/orders/90001?**',r=>r.fulfill({json:{id:'90001',storeId:'501',orderNo:'O90001',customerName:'小叶',customerMobile:'13800100001',status,totalQuantity:1,totalAmountCents:1800,payableAmountCents:1800,couponDiscountCents:0,pointsUsed:0,storedValueUsedCents:0,expiresAt:'2099-01-01T12:00:00',pickupCode:'ABCD123456',items:[{skuId:sku,productName:'鲜奶拿铁',skuName:'标准杯 · 热',quantity:1,lineAmountCents:1800}]}}))
  await page.addInitScript(()=>sessionStorage.setItem('consumer.account',JSON.stringify({accessToken:'valid',mobile:'13800100001',displayName:'小叶'})))
  await enterStore(page,`/?tenantId=${tenant}&storeId=501`)
  await page.getByRole('button',{name:'添加鲜奶拿铁 标准杯 · 热'}).click();await page.getByRole('button',{name:'去结算 ›'}).click();await page.getByRole('button',{name:'确认商品并试算'}).click();await page.getByRole('button',{name:'下单并支付'}).click()
  await expect(page.getByRole('heading',{name:'待自提',exact:true})).toBeVisible()
  await expect(page.getByText('支付成功，请凭取货码到店自提')).toBeVisible()
  expect(created).toBe(1);expect(completed).toBe(1)
})
test('store carts survive refresh without mixing, search and narrow screen work', async ({ page }) => {
  await fixtures(page)
  await enterStore(page,`/?live=1&tenantId=${tenant}&storeId=501#shop`)
  await page.getByRole('button', { name: '添加鲜奶拿铁 标准杯 · 热' }).click()
  await page.reload()
  await expect(page.getByRole('button', { name: '去结算 ›' })).toBeVisible()
  await page.getByRole('button', { name: /绿叶生活 · 湖滨店.*切换/ }).click()
  await page.getByRole('button', { name: /绿叶生活 · 城西店/ }).click()
  await expect(page.getByRole('button', { name: '去结算 ›' })).toHaveCount(0)
  await page.getByLabel('搜索商品').fill('不存在')
  await expect(page.getByText('没有找到相关商品')).toBeVisible()
  await page.getByLabel('搜索商品').fill('')
  await page.setViewportSize({ width: 320, height: 720 })
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true)
  await page.goto('/')
  await expect(page.getByRole('heading',{name:'入驻门店',exact:true})).toBeVisible()
  await page.screenshot({ path: 'apps/consumer-web/qa/marketplace-home-mobile.png', fullPage: true })
})
test('invalid entry URLs open the platform home and remove invalid tenant parameters', async ({ page }) => {
  await fixtures(page)
  await page.goto('/?live=1&tenantId=invalid')
  await expect(page.getByRole('heading',{name:'入驻门店',exact:true})).toBeVisible()
  expect(new URL(page.url()).searchParams.has('tenantId')).toBe(false)
})

test('a lost create response reuses the same request ID on retry', async ({ page }) => {
  const creates = await fixtures(page, true)
  await enterStore(page,`/?live=1&tenantId=${tenant}&storeId=501#shop`)
  await page.getByRole('button', { name: '添加鲜奶拿铁 标准杯 · 热' }).click()
  await page.getByRole('button', { name: '去结算 ›' }).click()
  await page.getByLabel('手机号', { exact: true }).fill('13800100001')
  await page.getByLabel('密码', { exact: true }).fill('Consumer!12345')
  await page.getByRole('button', { name: '登录', exact: true }).click()
  await page.getByRole('button', { name: '确认商品并试算' }).click()
  await page.getByRole('button', { name: '下单并支付' }).click()
  await expect(page.getByRole('dialog', { name: '确认订单' }).getByRole('alert')).toBeVisible()
  await page.getByRole('button', { name: '下单并支付' }).click()
  await expect(page.getByRole('heading', { name: '待自提', exact: true })).toBeVisible()
  expect(creates).toHaveLength(2)
  expect(creates[1].requestId).toBe(creates[0].requestId)
  await expect(page.getByText('ABCD123456')).toBeVisible()
})

test('expired identity clears private views and asks for login',async({page})=>{
  await fixtures(page)
  await page.addInitScript(tenant=>sessionStorage.setItem('consumer.account',JSON.stringify({accessToken:'expired',mobile:'13800100001',displayName:'小叶'})),tenant)
  await page.route('**/api/consumer/v1/wallet',r=>r.fulfill({status:401,json:{message:'登录已过期'}}))
  await enterStore(page,`/?tenantId=${tenant}&storeId=501#account`)
  await expect(page.getByRole('dialog',{name:'消费者登录'})).toBeVisible()
  await expect(page.getByText('储值余额（元）')).toHaveCount(0)
  expect(await page.evaluate(tenant=>JSON.parse(sessionStorage.getItem('consumer.account')||'null'),tenant)).toBeNull()
})

test('checkout reconciles changed prices and stock before submission',async({page})=>{
  await fixtures(page)
  let fetches=0
  await page.route('**/api/consumer/v1/catalog/**/products',r=>{
    fetches++
    return r.fulfill({json:{store:stores[0],products:fetches===1?products:[{...products[0],availableQuantity:1,effectivePriceCents:2100}]}})
  })
  await page.addInitScript(tenant=>sessionStorage.setItem('consumer.account',JSON.stringify({accessToken:'valid',mobile:'13800100001',displayName:'小叶'})),tenant)
  await enterStore(page,`/?tenantId=${tenant}&storeId=501#shop`)
  const add=page.getByRole('button',{name:'添加鲜奶拿铁 标准杯 · 热'})
  await add.click();await add.click()
  await page.getByRole('button',{name:'去结算 ›'}).click()
  await expect(page.getByRole('dialog',{name:'确认订单'})).toContainText('¥21.00')
  await expect(page.getByText('商品价格或库存已变化，请核对后再提交')).toBeVisible()
  await expect(page.getByRole('dialog',{name:'确认订单'})).toContainText('× 1')
})

test('server payment capability cannot be enabled by query parameters',async({page})=>{
  await fixtures(page)
  await page.route('**/api/consumer/v1/payments/capabilities',r=>r.fulfill({json:[]}))
  await page.addInitScript(tenant=>sessionStorage.setItem('consumer.account',JSON.stringify({accessToken:'valid',mobile:'13800100001',displayName:'小叶'})),tenant)
  await enterStore(page,`/?tenantId=${tenant}&storeId=501&simulatePayment=1#shop`)
  await page.getByRole('button',{name:'添加鲜奶拿铁 标准杯 · 热'}).click()
  await page.getByRole('button',{name:'去结算 ›'}).click()
  await page.getByRole('button',{name:'确认商品并试算'}).click()
  await page.getByRole('button',{name:'提交订单',exact:true}).click()
  await expect(page.getByRole('heading',{name:'待支付',exact:true})).toBeVisible()
  await expect(page.getByRole('button',{name:'模拟支付',exact:true})).toHaveCount(0)
  await expect(page.getByRole('button',{name:'取消订单',exact:true})).toBeVisible()
})

test('location denial falls back to a manually selected area',async({page})=>{
  await fixtures(page)
  await page.addInitScript(()=>Object.defineProperty(navigator,'geolocation',{value:{getCurrentPosition:(_success:unknown,failure:(e:unknown)=>void)=>failure({code:1})}}))
  await page.goto('/')
  await page.getByRole('button',{name:'定位找店'}).click()
  const dialog=page.getByRole('dialog',{name:'选择位置'})
  await expect(dialog).toContainText('未能获取位置')
  await dialog.getByLabel('城市或区县').fill('西湖区')
  const query=page.waitForRequest(r=>r.url().includes('/catalog/stores?')&&new URL(r.url()).searchParams.get('area')==='西湖区')
  await dialog.getByRole('button',{name:'确定地区'}).click()
  await query
  await expect(page.getByRole('heading',{name:'西湖区的门店'})).toBeVisible()
  await page.reload()
  await expect(page.getByRole('heading',{name:'西湖区的门店'})).toBeVisible()
})

test('failed automatic payment preserves the order and retries with the same payment key',async({page})=>{
  const creates=await fixtures(page)
  const payments:any[]=[]
  await page.addInitScript(tenant=>sessionStorage.setItem('consumer.account',JSON.stringify({accessToken:'valid',mobile:'13800100001',displayName:'小叶'})),tenant)
  await page.route('**/simulate-payment',route=>{payments.push(route.request().postDataJSON());return payments.length===1?route.fulfill({status:503,json:{message:'支付暂时失败'}}):route.fallback()})
  await enterStore(page)
  await page.getByRole('button',{name:'添加鲜奶拿铁 标准杯 · 热'}).click()
  await page.getByRole('button',{name:'去结算 ›'}).click()
  await page.getByRole('button',{name:'确认商品并试算'}).click()
  await page.getByRole('button',{name:'下单并支付'}).click()
  await expect(page.getByRole('heading',{name:'待支付',exact:true})).toBeVisible()
  await expect(page.getByText('订单已保留，请查看支付状态或重试支付')).toBeVisible()
  expect(creates).toHaveLength(1)
  await page.getByRole('button',{name:'模拟支付',exact:true}).click()
  await expect(page.getByText('ABCD123456')).toBeVisible()
  expect(creates).toHaveLength(1)
  expect(payments).toHaveLength(2)
  expect(payments[1].paymentRequestId).toBe(payments[0].paymentRequestId)
})

test('changed payable amount requires review before payment',async({page})=>{
  await fixtures(page)
  let payments=0
  await page.addInitScript(tenant=>sessionStorage.setItem('consumer.account',JSON.stringify({accessToken:'valid',mobile:'13800100001',displayName:'小叶'})),tenant)
  await page.route('**/api/consumer/v1/orders/quote',r=>r.fulfill({json:{payableAmountCents:1700,shippingFeeCents:0,couponDiscountCents:100,pointsUsed:0,storedValueUsedCents:0}}))
  await page.route('**/simulate-payment',r=>{payments++;return r.fallback()})
  await enterStore(page)
  await page.getByRole('button',{name:'添加鲜奶拿铁 标准杯 · 热'}).click()
  await page.getByRole('button',{name:'去结算 ›'}).click()
  await page.getByRole('button',{name:'确认商品并试算'}).click()
  await page.getByRole('button',{name:'下单并支付'}).click()
  await expect(page.getByText('订单金额发生变化，请核对实际应付金额后支付')).toBeVisible()
  expect(payments).toBe(0)
  await expect(page.getByRole('heading',{name:'待支付',exact:true})).toBeVisible()
})

test('lost payment response recovers the paid order without creating another order',async({page})=>{
  const creates=await fixtures(page)
  let payments=0
  await page.addInitScript(tenant=>sessionStorage.setItem('consumer.account',JSON.stringify({accessToken:'valid',mobile:'13800100001',displayName:'小叶'})),tenant)
  await page.route('**/simulate-payment',r=>{payments++;return r.abort('failed')})
  await page.route('**/orders/90001?*',r=>r.fulfill({json:{id:'90001',storeId:'501',orderNo:'O90001',status:'PICKUP_READY',totalQuantity:1,totalAmountCents:1800,payableAmountCents:1800,couponDiscountCents:0,pointsUsed:0,storedValueUsedCents:0,pickupCode:'RECOVER123',expiresAt:'2099-01-01T12:00:00',items:[]}}))
  await enterStore(page)
  await page.getByRole('button',{name:'添加鲜奶拿铁 标准杯 · 热'}).click()
  await page.getByRole('button',{name:'去结算 ›'}).click()
  await page.getByRole('button',{name:'确认商品并试算'}).click()
  await page.getByRole('button',{name:'下单并支付'}).click()
  await expect(page.getByText('RECOVER123')).toBeVisible()
  await expect(page.getByText('支付成功，请凭取货码到店自提')).toBeVisible()
  expect(creates).toHaveLength(1)
  expect(payments).toBe(1)
})

test('granted location supplies coordinates and displays honest distance labels',async({page,context})=>{
  await fixtures(page)
  await context.grantPermissions(['geolocation'])
  await context.setGeolocation({latitude:30.25,longitude:120.15})
  await page.route('**/api/consumer/v1/catalog/stores?*',route=>route.fulfill({json:[{...stores[0],tenantId:tenant,city:'杭州市',district:'西湖区',pickupEnabled:true,shippingEnabled:true,distanceKm:0.45}]}))
  await page.goto('/')
  const query=page.waitForRequest(r=>new URL(r.url()).searchParams.get('latitude')==='30.25')
  await page.getByRole('button',{name:'定位找店'}).click()
  await query
  await expect(page.getByRole('heading',{name:'附近入驻门店'})).toBeVisible()
  await expect(page.getByText(/直线 450 m/)).toBeVisible()
})

test('different merchants keep carts separate and guests authenticate before checkout',async({page})=>{
  await fixtures(page)
  const second='9223372036854775799'
  await page.route('**/api/consumer/v1/catalog/stores?*',route=>route.fulfill({json:[{...stores[0],tenantId:tenant,pickupEnabled:true,shippingEnabled:true,distanceKm:null},{...stores[1],tenantId:second,pickupEnabled:true,shippingEnabled:false,distanceKm:null}]}))
  await enterStore(page)
  await page.getByRole('button',{name:'添加鲜奶拿铁 标准杯 · 热'}).click()
  await page.getByRole('button',{name:'‹ 附近门店'}).click()
  await page.locator('.market-store').nth(1).click()
  await expect(page.getByLabel('搜索商品')).toBeVisible()
  await expect(page.getByRole('button',{name:'去结算 ›'})).toHaveCount(0)
  await page.getByRole('button',{name:'添加鲜奶拿铁 标准杯 · 热'}).click()
  await page.getByRole('button',{name:'去结算 ›'}).click()
  await expect(page.getByRole('dialog',{name:'消费者登录'})).toBeVisible()
  await page.getByRole('button',{name:'关闭登录'}).click()
  await page.getByRole('navigation',{name:'主导航'}).getByRole('button',{name:'购物车'}).click()
  await expect(page.locator('.market-visit')).toHaveCount(2)
  expect(await page.evaluate(({tenant,second,sku})=>[localStorage.getItem(`consumer.owner:guest:cart:${tenant}:501`),localStorage.getItem(`consumer.owner:guest:cart:${second}:502`)].every(v=>JSON.parse(v||'{}')[sku]===1),{tenant,second,sku})).toBe(true)
})

test('shipping checkout shows freight and completes receipt without a pickup code',async({page})=>{
  await fixtures(page)
  await page.addInitScript(tenant=>sessionStorage.setItem('consumer.account',JSON.stringify({accessToken:'valid',mobile:'13800100001',displayName:'小叶'})),tenant)
  let status='PENDING_PAYMENT',body:any=null
  const order=()=>({id:'90002',storeId:'501',orderNo:'O90002',customerName:'小叶',customerMobile:'13800100001',status,totalQuantity:1,totalAmountCents:1800,payableAmountCents:2600,couponDiscountCents:0,pointsUsed:0,storedValueUsedCents:0,expiresAt:'2099-01-01T12:00:00',pickupCode:null,delivery:{...body?.delivery,shippingFeeCents:800,carrier:status==='SHIPPED'?'顺丰':'',trackingNo:status==='SHIPPED'?'SF123456789':''},items:[{skuId:sku,productName:'鲜奶拿铁',skuName:'标准杯 · 热',quantity:1,lineAmountCents:1800}]})
  await page.route('**/api/consumer/v1/orders/quote',r=>r.fulfill({json:{payableAmountCents:2600,shippingFeeCents:800,couponDiscountCents:0,pointsUsed:0,storedValueUsedCents:0}}))
  await page.route('**/api/consumer/v1/orders',r=>{body=r.request().postDataJSON();return r.fulfill({json:order()})})
  await page.route('**/api/consumer/v1/orders/90002/simulate-payment',r=>{status='PENDING_SHIPMENT';return r.fulfill({json:order()})})
  await page.route('**/api/consumer/v1/orders/90002?*',r=>r.fulfill({json:order()}))
  await page.route('**/api/consumer/v1/orders/90002/receive',r=>{status='COMPLETED';return r.fulfill({json:order()})})
  await enterStore(page)
  await page.getByRole('button',{name:'添加鲜奶拿铁 标准杯 · 热'}).click()
  await page.getByRole('button',{name:'去结算 ›'}).click()
  const checkout=page.getByRole('dialog',{name:'确认订单'})
  await checkout.getByRole('button',{name:'快递邮寄',exact:true}).click()
  await expect(checkout.getByRole('button',{name:'确认商品并试算'})).toBeDisabled()
  await checkout.getByLabel('收件人',{exact:true}).fill('收货人')
  await checkout.getByLabel('收件电话').fill('13900000000')
  await checkout.getByLabel('省份 / 地区').selectOption('浙江省')
  await checkout.getByLabel('详细地址').fill('杭州市西湖区文一路18号')
  await checkout.getByLabel('收件电话').fill('123')
  await checkout.getByRole('button',{name:'确认商品并试算'}).click()
  await expect(checkout.getByRole('alert')).toContainText('有效的收件电话')
  await checkout.getByLabel('收件电话').fill('１３９００００００００')
  await checkout.getByLabel('详细地址').fill('杭州')
  await checkout.getByRole('button',{name:'确认商品并试算'}).click()
  await expect(checkout.getByRole('alert')).toContainText('请补全详细地址')
  await checkout.getByLabel('详细地址').fill(' 浙江省杭州市西湖区文一路18号 ')
  await checkout.getByRole('button',{name:'确认商品并试算'}).click()
  await expect(checkout).toContainText('¥8.00')
  await expect(checkout).toContainText('¥26.00')
  await page.screenshot({path:'apps/consumer-web/qa/marketplace-shipping-checkout.png',fullPage:true})
  await checkout.getByRole('button',{name:'下单并支付',exact:true}).click()
  expect(body.delivery).toMatchObject({method:'SHIPPING',name:'收货人',phone:'13900000000',address:'浙江省杭州市西湖区文一路18号'})
  expect(body.shippingFeeCents).toBeUndefined()
  await expect(page.getByRole('heading',{name:'待发货',exact:true})).toBeVisible()
  await expect(page.locator('.pickup-code')).toHaveCount(0)
  status='SHIPPED'
  await page.getByRole('button',{name:'刷新状态'}).click()
  await expect(page.getByText(/SF123456789/)).toBeVisible()
  await page.getByRole('button',{name:'确认收货',exact:true}).click()
  await page.getByRole('button',{name:'已收到，确认收货'}).click()
  await expect(page.getByRole('heading',{name:'已完成',exact:true})).toBeVisible()
})


