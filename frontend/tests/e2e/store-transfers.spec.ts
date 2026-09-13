import {test,expect,type Page} from '@playwright/test'
const source={id:11,storeCode:'A',storeName:'供货一店',address:'上海市测试路 11 号',status:'ACTIVE',version:0,businessHours:'09:00-21:00'}
const target={...source,id:12,storeCode:'B',storeName:'申请二店',address:'上海市测试路 12 号'}
const item={skuId:21,lineNo:1,skuCode:'SKU21',skuName:'标准款',productName:'共享商品',quantity:2,unitPriceCents:1000,lineAmountCents:2000}
const order={id:900,orderNo:'O900',storeId:12,customerName:'测试客户',customerMobile:'13800000000',status:'PICKUP_READY',totalQuantity:2,totalAmountCents:2000,payableAmountCents:2000,paidAt:'2026-09-06T10:00:00',items:[item],couponDiscountCents:0,pointsUsed:0,storedValueUsedCents:0}
async function setup(page:Page,supplying=false){
 const requests:{path:string;body:any}[]=[]
 const transfer={id:700,transferNo:'DB700',sourceStoreId:11,targetStoreId:12,sourceStoreName:source.storeName,targetStoreName:target.storeName,status:'REQUESTED',deliveryMode:'STORE',deliveryAddress:target.address,createdAt:'2026-09-06T10:00:00',items:[item]}
 await page.addInitScript(()=>localStorage.setItem('saas.accessToken','test-token'))
 await page.route('**/api/merchant/**',async route=>{
  const request=route.request(),url=new URL(request.url()),path=url.pathname
  if(request.method()==='POST'){requests.push({path,body:request.postDataJSON()});return route.fulfill({json:{...transfer,status:path.endsWith('/ship')?'IN_TRANSIT':'REQUESTED'}})}
  let data:any=[]
  if(path.endsWith('/me'))data={displayName:'门店员工',dataScope:'STORE_SELF',permissions:['merchant:store:view','merchant:inventory:view','merchant:transfer:manage','merchant:order:view','merchant:product:view']}
  else if(path.endsWith('/stores'))data=[supplying?source:target]
  else if(path.endsWith('/shared-stock'))data=[{storeId:11,storeName:source.storeName,address:source.address,...item,availableQuantity:8}]
  else if(path.endsWith('/orders'))data=supplying?[]:[order]
  else if(path.endsWith('/transfers'))data=supplying?[transfer]:[]
  else if(path.endsWith('/transfers/700'))data=transfer
  await route.fulfill({json:data})
 })
 await page.goto('http://127.0.0.1:5176')
 await page.getByRole('complementary').getByRole('button',{name:/调拨收发/}).click()
 await expect(page.getByRole('heading',{name:'同租户调拨收发'})).toBeVisible()
 return requests
}
async function selectSupplier(page:Page){
 await page.locator('.el-form-item').filter({has:page.getByText('选择供货门店',{exact:true})}).locator('.el-select').click()
 await page.getByRole('option',{name:source.storeName,exact:true}).click()
}
test('门店查询同租户库存并自主申请调入本店',async({page})=>{
 const requests=await setup(page)
 await page.getByRole('button',{name:'查询共享库存 / 申请调拨'}).click()
 await expect(page.getByRole('cell',{name:'共享商品',exact:true})).toBeVisible()
 await selectSupplier(page)
 await page.getByRole('button',{name:'添加',exact:true}).click()
 await page.getByRole('button',{name:'提交调拨申请'}).click()
 await expect.poll(()=>requests.length).toBe(1)
 expect(requests[0].body).toMatchObject({sourceStoreId:11,targetStoreId:12,deliveryMode:'STORE',orderId:null,items:[{skuId:21,quantity:1}]})
})
test('直寄客户必须关联订单与详细地址并复用整单数量',async({page})=>{
 const requests=await setup(page)
 await page.getByRole('button',{name:'查询共享库存 / 申请调拨'}).click()
 await selectSupplier(page)
 await page.getByText('关联订单，直寄客户',{exact:true}).click()
 await page.locator('.el-form-item').filter({has:page.getByText('关联本店已支付待履约订单（整单直寄）',{exact:true})}).locator('.el-select').click()
 await page.getByRole('option',{name:/O900/}).click()
 await page.getByRole('button',{name:'提交调拨申请'}).click()
 await expect(page.getByText('请填写收件人、电话和详细地址（至少 5 个字）',{exact:true})).toBeVisible()
 expect(requests).toHaveLength(0)
 await page.locator('.el-form-item').filter({has:page.getByText('详细收货地址（省市区、街道、门牌）',{exact:true})}).locator('textarea').fill('上海市徐汇区测试路 88 号 101 室')
 await page.screenshot({path:test.info().outputPath('direct-request.png'),fullPage:true})
 await page.getByRole('button',{name:'提交调拨申请'}).click()
 await expect.poll(()=>requests.length).toBe(1)
 expect(requests[0].body).toMatchObject({orderId:900,sourceStoreId:11,targetStoreId:12,deliveryMode:'CUSTOMER',items:[{skuId:21,quantity:2}],recipientName:'测试客户'})
 expect(requests[0].body).not.toHaveProperty('collectionAmountCents')
})
test('供货门店看到申请地址与商品并确认出库',async({page})=>{
 const requests=await setup(page,true)
 await page.getByRole('button',{name:'明细 / 处理'}).click()
 await expect(page.getByText(target.address,{exact:true})).toBeVisible()
 await page.getByRole('button',{name:'确认调拨并出库'}).click()
 await page.getByRole('button',{name:'确认',exact:true}).click()
 await expect.poll(()=>requests.length).toBe(1)
 expect(requests[0].path).toBe('/api/merchant/v1/transfers/700/ship')
})

 test('商品与订单支持显式搜索并可恢复选择',async({page})=>{
 await setup(page)
 await page.getByRole('button',{name:'查询共享库存 / 申请调拨'}).click();await selectSupplier(page)
 const product=page.getByPlaceholder('搜索供货店商品名称、规格或 SKU')
 await product.fill('不存在');await page.getByRole('button',{name:'搜索商品',exact:true}).click()
 await expect(page.getByRole('button',{name:'添加',exact:true})).toHaveCount(0)
 await product.fill('SKU21');await page.getByRole('button',{name:'搜索商品',exact:true}).click()
 await expect(page.getByRole('button',{name:'添加',exact:true})).toBeVisible()
 await page.getByText('关联订单，直寄客户',{exact:true}).click()
 const search=page.getByPlaceholder('输入订单号、客户、手机号或商品搜索')
 await search.fill('不存在');await page.getByRole('button',{name:'搜索订单',exact:true}).click()
 await expect(page.getByText('没有匹配的订单，请更换搜索关键词。',{exact:true})).toBeVisible()
 await search.fill('共享商品');await page.getByRole('button',{name:'搜索订单',exact:true}).click()
 await page.locator('.el-form-item').filter({has:page.getByText('关联本店已支付待履约订单（整单直寄）',{exact:true})}).locator('.el-select').click()
 await page.getByRole('option',{name:/O900/}).click()
 await expect(page.getByRole('spinbutton')).toHaveValue('2')
 })
