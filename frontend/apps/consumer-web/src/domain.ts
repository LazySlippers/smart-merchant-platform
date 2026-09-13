export type Store = { id: string; storeName: string; address: string; businessHours: string }
export type Product = { categoryId: string; categoryName: string; spuId: string; productName: string; description: string; imageUrl: string; skuId: string; skuName: string; effectivePriceCents: number; availableQuantity: number; selectable: boolean }
export type Cart = Record<string, number>
export type Wallet = { id: string; mobile: string; memberName: string; availablePoints: number; storedValueCents: number }
export type Coupon = { id: string; templateId: string; name: string; discountCents: number; minSpendCents: number; status: string; validFrom: string; validTo: string }
export type Delivery = {method:string;shippingFeeCents:number;name:string;phone:string;province:string;address:string;carrier:string;trackingNo:string;shippedAt:string|null}
export type Order = { delivery?:Delivery; id: string; orderNo: string; storeId: string; customerName: string; customerMobile: string; status: string; totalQuantity: number; totalAmountCents: number; payableAmountCents: number; couponDiscountCents: number; pointsUsed: number; storedValueUsedCents: number; expiresAt: string; pickupCode: string | null; items: { skuId: string; productName: string; skuName: string; quantity: number; lineAmountCents: number }[] }
export type Quote = { shippingFeeCents:number; payableAmountCents: number; couponDiscountCents: number; pointsUsed: number; storedValueUsedCents: number }
export const money = (cents: number) => (cents / 100).toFixed(2)
export const statusNames: Record<string, string> = { PENDING_PAYMENT: '待支付', PICKUP_READY: '待自提', PENDING_SHIPMENT:'待发货', SHIPPED:'待收货', COMPLETED: '已完成', CLOSED: '已关闭', REFUNDED: '已退款' }
export const validId = (value: string | null): value is string => !!value && /^[1-9]\d{0,18}$/.test(value) && BigInt(value) <= 9223372036854775807n
export function resolveTenant(entry: string | null, configured: string | undefined, bundled: string): string {
  return [entry, configured, bundled].find(value => typeof value === 'string' && validId(value)) || ''
}
export function reconcile(cart: Cart, products: Product[]): Cart {
  const result: Cart = {}
  for (const product of products) {
    const qty = cart[product.skuId]
    if (product.selectable && Number.isSafeInteger(qty) && qty > 0 && product.availableQuantity > 0)
      result[product.skuId] = Math.min(qty, product.availableQuantity, 99)
  }
  return result
}
export function readSaved<T>(key: string, fallback: T, storage: Storage = localStorage): T {
  try { return JSON.parse(storage.getItem(key) ?? 'null') ?? fallback } catch { return fallback }
}
export function save(key: string, value: unknown, storage: Storage = localStorage) {
  try { storage.setItem(key, JSON.stringify(value)) } catch { /* Private mode / full storage: current session remains usable. */ }
}
