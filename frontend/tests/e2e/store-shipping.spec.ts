import {test,expect} from '@playwright/test'
test('所属门店在订单详情录入快递单号并显示待收货',async({page})=>{
  const store={id:12,storeCode:'B',storeName:'百货二店',address:'杭州市文一路',status:'ACTIVE',version:0,businessHours:'09:00-21:00'}
  let order={id:901,storeId:12,orderNo:'O901',customerName:'买家',customerMobile:'13800000000',status:'PENDING_SHIPMENT',totalQuantity:1,totalAmountCents:1800,payableAmountCents:2600,couponDiscountCents:0,pointsUsed:0,storedValueUsedCents:0,paidAt:'2026-09-09T12:00:00',delivery:{method:'SHIPPING',shippingFeeCents:800,name:'收件人',phone:'13900000000',address:'浙江省杭州市文一路18号',carrier:'',trackingNo:'',shippedAt:''},items:[{lineNo:1,skuId:21,productName:'日用好物',skuName:'标准款',unitPriceCents:1800,quantity:1,lineAmountCents:1800}]}
  let submitted:any=null
  await page.addInitScript(()=>localStorage.setItem('saas.accessToken','worker-token'))
  await page.route('**/api/merchant/**',async route=>{
    const path=new URL(route.request().url()).pathname
    let data:any=[]
    if(path.endsWith('/me'))data={displayName:'店员',dataScope:'STORE_SELF',permissions:['merchant:store:view','merchant:order:view','merchant:order:verify']}
    else if(path.endsWith('/stores'))data=[store]
    else if(path.endsWith('/orders'))data=[order]
    else if(path.endsWith('/ship')){submitted=route.request().postDataJSON();order={...order,status:'SHIPPED',delivery:{...order.delivery,...submitted,shippedAt:'2026-09-09T13:00:00'}};data=order}
    else if(path.endsWith('/orders/901'))data=order
    await route.fulfill({json:data})
  })
  await page.goto('http://127.0.0.1:5176')
  await page.getByRole('complementary').getByRole('button',{name:/销售订单/}).click()
  await page.getByRole('button',{name:'详情',exact:true}).click()
  const drawer=page.getByRole('dialog',{name:'订单详情'})
  await expect(drawer).toContainText('浙江省杭州市文一路18号')
  await expect(drawer.getByRole('button',{name:'确认发货'})).toBeDisabled()
  await drawer.getByLabel('快递公司',{exact:true}).fill('顺丰')
  await drawer.getByLabel('快递单号',{exact:true}).fill('SF123456789')
  await drawer.getByRole('button',{name:'确认发货'}).click()
  await expect.poll(()=>submitted).toEqual({carrier:'顺丰',trackingNo:'SF123456789'})
  await expect(drawer).toContainText('待收货')
  await expect(drawer).toContainText('SF123456789')
  await expect(drawer.getByRole('button',{name:'确认发货'})).toHaveCount(0)
})
