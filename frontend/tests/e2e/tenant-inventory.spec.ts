import {test,expect,type Page} from '@playwright/test'
const stores=[{id:71,storeCode:'A',storeName:'一店',address:'上海测试路 1 号',status:'ACTIVE',businessHours:'09:00-21:00',version:0},{id:72,storeCode:'B',storeName:'二店',address:'上海测试路 2 号',status:'ACTIVE',businessHours:'09:00-21:00',version:0}]
function fixture(){return {rows:stores.map((s,i)=>({storeId:s.id,storeName:s.storeName,storeStatus:'ACTIVE',skuId:91,skuCode:'COFFEE',skuName:'标准杯',productName:'租户咖啡',actualQuantity:3-i,availableQuantity:3-i,reservedQuantity:0,lowStockThreshold:5})),history:[] as any[],requests:[] as {path:string;body:any}[],products:[{spu:{id:81,productName:'租户咖啡',status:'ACTIVE'},skus:[{sku:{id:91,skuCode:'COFFEE',skuName:'标准杯',status:'ACTIVE',basePriceCents:1800,version:0,allowStorePrice:false,barcode:''},sellable:true,effectivePriceCents:1800}]}]}}
async function install(page:Page,state:ReturnType<typeof fixture>,store=false){
 await page.addInitScript(()=>localStorage.setItem('saas.accessToken','test-token'))
 await page.route('**/api/merchant/**',async route=>{
  const req=route.request(),url=new URL(req.url()),path=url.pathname
  if(req.method()!=='GET'){
   const body=req.postDataJSON();state.requests.push({path,body})
   if(path.endsWith('/inventory/allocations'))for(const line of body.items){const row=state.rows.find(r=>r.storeId===line.storeId)!;row.actualQuantity+=line.quantity;row.availableQuantity+=line.quantity;state.history.push({id:101,...row,quantity:line.quantity,reason:body.reason,createdAt:'2026-09-07T10:00:00'})}
   if(path.endsWith('/inventory/threshold'))state.rows.find(r=>r.storeId===body.storeId)!.lowStockThreshold=body.lowStockThreshold
   if(path.endsWith('/products/with-sku'))state.products.push({spu:{id:82,productName:body.productName,status:'ACTIVE'},skus:[{sku:{id:92,skuCode:body.skuCode,skuName:body.skuName,status:'ACTIVE',basePriceCents:body.priceCents,version:0,allowStorePrice:false,barcode:body.barcode},sellable:false,effectivePriceCents:body.priceCents}]})
   await route.fulfill({json:{id:101}});return
  }
  let data:any=[]
  if(path.endsWith('/me'))data={tenantId:7,displayName:store?'门店员工':'租户负责人',dataScope:store?'STORE_SELF':'TENANT_ALL',permissions:['merchant:store:view','merchant:inventory:view','merchant:inventory:manage','merchant:product:view',...(store?[]:['merchant:product:manage'])]}
  else if(path.endsWith('/stores/quota'))data={used:2,limit:10,canCreate:true}
  else if(path.endsWith('/stores'))data=store?[stores[0]]:stores
  else if(path.endsWith('/inventory/low-stock-alerts'))data=state.rows.filter(r=>(!store||r.storeId===71)&&r.availableQuantity<=r.lowStockThreshold)
  else if(path.endsWith('/inventory/tenant-stock'))data=state.rows
  else if(path.endsWith('/inventory/allocations'))data=state.history
  else if(path.endsWith('/inventory'))data=state.rows.filter(r=>r.storeId===Number(url.searchParams.get('storeId')))
  else if(path.endsWith('/products'))data=state.products
  else if(path.endsWith('/categories'))data=[{id:61,categoryName:'饮品'}]
  await route.fulfill({json:data})
 })
 await page.goto(store?'http://127.0.0.1:5176':'http://127.0.0.1:5174')
}
const formItem=(page:Page,label:string)=>page.locator('.el-form-item').filter({has:page.getByText(label,{exact:true})})
async function inventory(page:Page){await page.getByRole('complementary').getByRole('button',{name:'▦ 商品与库存',exact:true}).click();await expect(page.getByRole('heading',{name:'商品与门店库存'})).toBeVisible()}

test('总部按店分配数量且两端预警自动解除',async({page,context})=>{
 const state=fixture();await install(page,state)
 const storePage=await context.newPage();await storePage.clock.install();await install(storePage,state,true)
 await expect(page.getByText('旗下门店有 2 项低库存或缺货商品',{exact:true})).toBeVisible()
 await expect(storePage.getByText('本店有 1 项低库存或缺货商品',{exact:true})).toBeVisible()
 await inventory(page)
 await page.getByRole('button',{name:'分配门店入库',exact:true}).click()
 await formItem(page,'租户商品').locator('.el-select').click();await page.getByRole('option',{name:/租户咖啡/}).click()
 const drawer=page.locator('.el-drawer:visible')
 await drawer.getByRole('spinbutton').nth(0).fill('10');await drawer.getByRole('spinbutton').nth(1).fill('20')
 await formItem(page,'分配原因 / 入库说明').locator('textarea').fill('总部统一补货')
 await page.screenshot({path:test.info().outputPath('tenant-allocation.png'),fullPage:true})
 await page.getByRole('button',{name:'确认分配入库',exact:true}).click();await page.getByRole('button',{name:'确认入库',exact:true}).click()
 await expect.poll(()=>state.requests.length).toBe(1)
 expect(state.requests[0].body.items).toEqual([{storeId:71,skuId:91,quantity:10},{storeId:72,skuId:91,quantity:20}])
 await expect(page.getByText('旗下门店有 2 项低库存或缺货商品',{exact:true})).toHaveCount(0)
 await storePage.bringToFront();await storePage.clock.fastForward(16000)
 await expect(storePage.getByText('本店有 1 项低库存或缺货商品',{exact:true})).toHaveCount(0)
 await page.getByRole('tab',{name:'分配入库记录',exact:true}).click();await expect(page.getByRole('cell',{name:'总部统一补货',exact:true})).toHaveCount(2)
})
test('租户统一设置门店预警线，门店不能手工填写入库正数',async({page,context})=>{
 const state=fixture();await install(page,state);await inventory(page)
 await page.getByRole('button',{name:'预警线',exact:true}).first().click()
 await page.getByRole('dialog',{name:'设置门店商品预警线'}).getByRole('spinbutton').fill('0')
 await page.getByRole('button',{name:'保存预警线',exact:true}).click()
 await expect.poll(()=>state.requests.length).toBe(1);expect(state.requests[0].body).toMatchObject({storeId:71,skuId:91,lowStockThreshold:0})
 await expect(page.getByText('旗下门店有 1 项低库存或缺货商品',{exact:true})).toBeVisible()
 const storePage=await context.newPage();await install(storePage,state,true)
 await expect(storePage.getByText(/本店有 .* 项低库存或缺货商品/)).toHaveCount(0)
 await storePage.getByRole('complementary').getByRole('button',{name:'▦ 商品与库存',exact:true}).click()
 await storePage.getByRole('button',{name:'库存调整',exact:true}).click()
 await expect(storePage.locator('.el-drawer:visible').getByRole('spinbutton')).toHaveAttribute('aria-valuemax','-1')
})
test('租户可以一次创建启用商品，未分配商品不触发门店缺货预警',async({page})=>{
 const state=fixture();await install(page,state);await inventory(page)
 await page.getByRole('button',{name:'新增租户商品',exact:true}).click()
 for(const [label,value] of [['商品编码','BREAD'],['商品名称','租户面包'],['SKU 编码','BREAD-S'],['规格名称','小份']])await formItem(page,label).locator('input').fill(value)
 await page.getByRole('button',{name:'创建并启用商品',exact:true}).click()
 await expect.poll(()=>state.requests.length).toBe(1);expect(state.requests[0].path).toBe('/api/merchant/v1/products/with-sku')
 await page.getByRole('tab',{name:'租户商品目录',exact:true}).click();await expect(page.getByRole('cell',{name:'租户面包',exact:true})).toBeVisible()
 await expect(page.getByText('旗下门店有 2 项低库存或缺货商品',{exact:true})).toBeVisible()
})

for(const store of [false,true])test(`${store?'门店':'租户'}知晓预警后刷新仍隐藏，恢复后再次缺货重新提醒`,async({page})=>{
 const state=fixture();await install(page,state,store)
 const notice=page.getByRole('button',{name:'我已知晓',exact:true})
 await expect(notice).toBeVisible();await notice.click();await expect(notice).toHaveCount(0)
 await page.reload();await expect(page.getByRole('button',{name:'刷新数据',exact:true})).toBeVisible();await expect(notice).toHaveCount(0)
 state.rows.forEach(row=>row.availableQuantity=20)
 await page.getByRole('button',{name:'刷新数据',exact:true}).click()
 await expect.poll(()=>page.evaluate(()=>Object.entries(localStorage).filter(([key])=>key.startsWith('saas.stock-alert-ack:')).map(([,value])=>value))).toEqual(['[]'])
 state.rows[0].availableQuantity=1
 await page.getByRole('button',{name:'刷新数据',exact:true}).click();await expect(notice).toBeVisible()
})
