import { enterRealStore } from './marketplace-helper'
import { test, expect } from '@playwright/test'
test('real coupon claim, benefit quote, order cancellation and asset release',async({page,request})=>{
  test.setTimeout(90000)
  const tenant=process.env.H5_E2E_TENANT
  test.skip(!tenant,'Requires isolated H5_E2E_TENANT verification brand')
  const gateway='http://127.0.0.1:8080'
  const stores=await(await request.get(`${gateway}/api/consumer/v1/catalog/tenants/${tenant}/stores`)).json()
  const suffix=process.env.H5_E2E_SUFFIX||stores[0].storeCode.replace('A-','')
  const owner=await request.post(`${gateway}/api/auth/v1/login`,{data:{username:`139${suffix.slice(-8)}`,password:'M3-Test!2026'}})
  expect(owner.ok()).toBe(true)
  const ownerHeaders={Authorization:`Bearer ${(await owner.json()).accessToken}`}
  const id=Date.now().toString(), mobile=`135${id.slice(-8)}`
  const session=await request.post(`${gateway}/api/consumer/v1/auth/account/register`,{data:{tenantId:tenant,mobile,memberName:'权益验收',password:'Consumer-H5!2026'}})
  expect(session.ok()).toBe(true)
  const account=await session.json()
  const membership=await request.post(`${gateway}/api/consumer/v1/auth/account/enter`,{headers:{Authorization:`Bearer ${account.accessToken}`},data:{tenantId:tenant,join:true}})
  expect(membership.ok()).toBe(true)
  const token=(await membership.json()).accessToken, headers={Authorization:`Bearer ${token}`}
  const wallet=await(await request.get(`${gateway}/api/consumer/v1/wallet`,{headers})).json()
  for(const [kind,delta] of [['points',500],['stored-value',1000]]){
    const r=await request.post(`${gateway}/api/merchant/v1/members/${wallet.id}/${kind}`,{headers:ownerHeaders,data:{delta,requestId:`H5-${kind}-${id}`}})
    expect(r.ok()).toBe(true)
  }
  const couponName=`H5验收券${id}`
  const coupon=await request.post(`${gateway}/api/merchant/v1/coupon-templates`,{headers:ownerHeaders,data:{templateCode:`H5-${id}`,templateName:couponName,discountCents:300,minSpendCents:1000,totalQuantity:10,validFrom:'2026-01-01T00:00:00',validTo:'2027-12-31T23:59:59'}})
  expect(coupon.ok()).toBe(true)
  const template=await coupon.json()
  const activated=await request.put(`${gateway}/api/merchant/v1/coupon-templates/${template.id}/status`,{headers:ownerHeaders,data:{status:'ACTIVE',version:template.version}})
  expect(activated.ok()).toBe(true)
  await page.addInitScript(account=>sessionStorage.setItem('consumer.account',JSON.stringify(account)),account)
  // Keep this cancellation/release scenario pending; automatic payment is covered separately.
  await page.route('**/api/consumer/v1/orders/capabilities',r=>r.fulfill({json:{simulatedPaymentEnabled:false}}))
  await enterRealStore(page,tenant!,'account')
  await page.locator('.coupon-card').filter({hasText:couponName}).getByRole('button',{name:'领取',exact:true}).click()
  await expect(page.locator('.coupon-card').filter({hasText:couponName})).toContainText('可使用')
  await page.getByRole('button',{name:'查看积分与储值明细'}).click()
  await expect(page.getByRole('dialog',{name:'权益明细'})).toContainText('门店调整')
  await page.getByRole('button',{name:'关闭权益明细'}).click()
  await page.getByRole('navigation',{name:'店铺功能'}).getByRole('button',{name:'全部商品',exact:true}).click()
  await page.getByRole('button',{name:/^添加/}).first().click()
  await page.getByRole('button',{name:'去结算 ›'}).click()
  const option=page.getByLabel('优惠券',{exact:true}).locator('option').filter({hasText:couponName})
  const couponId=await option.getAttribute('value')
  await page.getByLabel('优惠券',{exact:true}).selectOption(couponId!)
  await page.getByLabel('使用积分',{exact:true}).fill('100')
  await page.getByLabel('使用储值（元）',{exact:true}).fill('2')
  await page.getByRole('button',{name:'确认商品并试算'}).click()
  await expect(page.getByRole('dialog',{name:'确认订单'}).locator('.total-line')).toContainText('14.00')
  const created=page.waitForResponse(r=>r.url().endsWith('/api/consumer/v1/orders')&&r.request().method()==='POST')
  await page.getByRole('button',{name:'提交订单',exact:true}).click()
  const order=await(await created).json()
  expect(order.payableAmountCents).toBe(1400)
  const frozen=await(await request.get(`${gateway}/api/consumer/v1/wallet`,{headers})).json()
  expect(frozen.availablePoints).toBe(400);expect(frozen.storedValueCents).toBe(800)
  await page.getByRole('button',{name:'取消订单',exact:true}).click()
  await expect(page.getByRole('heading',{name:'已关闭',exact:true})).toBeVisible()
  const released=await(await request.get(`${gateway}/api/consumer/v1/wallet`,{headers})).json()
  expect(released.availablePoints).toBe(500);expect(released.storedValueCents).toBe(1000)
  const coupons=await(await request.get(`${gateway}/api/consumer/v1/wallet/coupons`,{headers})).json()
  expect(coupons.find((c:any)=>String(c.id)===couponId).status).toBe('AVAILABLE')
})

