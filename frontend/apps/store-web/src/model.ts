export type Store = { id:number; storeCode:string; storeName:string; address:string; businessHours:string; status:string; version:number }
export type Order = { delivery?:{method:string;shippingFeeCents:number;name:string;phone:string;address:string;carrier:string;trackingNo:string;shippedAt:string|null}; id:number; orderNo:string; storeId:number; customerName:string; customerMobile:string; status:string; directTransferId?:number; fulfillmentStoreId?:number; totalQuantity:number; totalAmountCents:number; payableAmountCents:number; couponDiscountCents:number; pointsUsed:number; storedValueUsedCents:number; paidAt:string|null; verifiedAt:string|null; closedAt:string|null; expiresAt:string; items:{skuId:number;lineNo:number; productName:string; skuName:string; skuCode:string; unitPriceCents:number; quantity:number; lineAmountCents:number}[] }
export type Dashboard = { summary:{paidOrders:number; grossRevenueCents:number; refundCents:number; netRevenueCents:number; averageOrderCents:number}; trend:{date:string; orders:number; netRevenueCents:number}[]; products:{productName:string; categoryName:string; quantity:number; amountCents:number}[]; stores:{storeId:number; orders:number; netRevenueCents:number}[]; members:{newMembers:number; repeatMembers:number; couponsClaimed:number; couponsRedeemed:number; couponRedemptionRateBasisPoints:number} }
export const money = (c:number) => `¥${((c || 0)/100).toLocaleString('zh-CN', { minimumFractionDigits:2, maximumFractionDigits:2 })}`
export const dateText = (v:string|null|undefined) => v ? v.replace('T',' ').slice(0,19) : '—'
export const localDate = (date:Date) => `${date.getFullYear()}-${String(date.getMonth()+1).padStart(2,'0')}-${String(date.getDate()).padStart(2,'0')}`
export const statusLabels:Record<string,string> = {PENDING_PAYMENT:'待支付', PAID:'已支付', PICKUP_READY:'待自提', PENDING_SHIPMENT:'待发货', SHIPPED:'待收货', COMPLETED:'已完成', CLOSED:'已关闭', REFUNDING:'退款中', REFUNDED:'已退款'}
export const orderStatus = (status:string) => statusLabels[status] ?? status
export function exportCsv(name:string, rows:(string|number)[][]) {
  const cell = (v:string|number) => { const text = String(v); return `"${(/^[=+@\-\t\r]/.test(text) ? "'"+text : text).replaceAll('"','""')}"` }
  const url = URL.createObjectURL(new Blob(['\uFEFF'+rows.map(row=>row.map(cell).join(',')).join('\r\n')],{type:'text/csv;charset=utf-8'}))
  const link = document.createElement('a'); link.href=url; link.download=name; link.click(); setTimeout(()=>URL.revokeObjectURL(url),1000)
}
