import { expect, type Page } from '@playwright/test'
export async function enterRealStore(page:Page,tenant:string,section='shop'){
  const response=await page.request.get(`http://127.0.0.1:8080/api/consumer/v1/catalog/tenants/${tenant}/stores`)
  expect(response.ok()).toBe(true)
  const stores=await response.json()
  expect(stores.length).toBeGreaterThan(0)
  await page.goto('/')
  await page.getByLabel('搜索门店').fill(stores[0].storeName)
  await page.getByRole('button',{name:'搜索',exact:true}).click()
  await page.locator(`.market-store[data-tenant-id="${tenant}"][data-store-id="${stores[0].id}"]`).click()
  await expect(page.getByLabel('搜索商品')).toBeVisible()
  if(section!=='shop')await page.getByRole('navigation',{name:'店铺功能'}).getByRole('button',{name:section==='account'?'商家会员':'本店订单'}).click()
}
