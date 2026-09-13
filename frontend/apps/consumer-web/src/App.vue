<script setup lang="ts">
import { computed, onMounted, onUnmounted, ref, watch } from 'vue'
import { ApiError, request as apiRequest } from './api'
import ConsumerAuth from './ConsumerAuth.vue'
import GlobalAddressBook from './GlobalAddressBook.vue'
import { account, accountStorageKey, accountAddresses, enterTenant, recordBrowse, signOut, type GlobalAddress } from './account'
import Icon from './prototype/Icon.vue'

import './style.css'
import './connected.css'
import './mall.css'
import './marketplace.css'
import { money, readSaved, reconcile, save, statusNames, validId, type Cart, type Coupon, type Order, type Product, type Quote, type Store, type Wallet } from './domain'

const props=defineProps<{entryTenant:string;entryStore:string;initialPage?:string;initialOrder?:string;initialSku?:string}>()
const emit=defineEmits<{platform:[page:string]}>()
const tenant=props.entryTenant
const validEntry = validId(tenant)

const token = ref('')
const isMember = ref(false)
async function request<T>(path:string,access='',body?:unknown):Promise<T>{const owner=account.value?.accessToken;try{const result=await apiRequest<T>(path,access,body);if(access&&(access!==token.value||owner!==account.value?.accessToken))throw new ApiError('账号已切换，请重试',409);return result}catch(e){if(access&&(access!==token.value||owner!==account.value?.accessToken))throw new ApiError('账号已切换，请重试',409);throw e}}
const addressBookOpen=ref(false)
let guestCheckout:Cart|null=null
function selectAddress(address:GlobalAddress){selectedAddressId.value=address.id||'';applyGlobalAddress();addressBookOpen.value=false}
function addressesChanged(rows:GlobalAddress[]){globalAddresses.value=rows;if(!rows.some(a=>a.id===selectedAddressId.value)){selectedAddressId.value=(rows.find(a=>a.isDefault)||rows[0])?.id||'';applyGlobalAddress()}}
const page = ref(props.initialPage || 'shop')
type Brand = { displayName: string; logoUrl: string; themeColor: string; headline: string; contactPhone: string }
const brand = ref<Brand | null>(null)
const stores = ref<Store[]>([]), products = ref<Product[]>([]), storeId = ref('')
const cart = ref<Cart>({}), search = ref(''), category = ref('all')
const busy = ref(false), loading = ref(false), error = ref(''), notice = ref('')
const storePicker = ref(false), cartOpen = ref(false), authOpen = ref(false), checkoutOpen = ref(false)
const mobile = ref('')
const wallet = ref<Wallet | null>(null), coupons = ref<Coupon[]>([]), offers = ref<Coupon[]>([])
const orders = ref<Order[]>([]), selectedOrder = ref<Order | null>(null), orderPage = ref(0), hasMore = ref(false)
const quote = ref<Quote | null>(null), couponId = ref(''), points = ref(0), stored = ref(0)
type Fulfillment={pickupEnabled:boolean;shippingEnabled:boolean;firstShippingCents:number;extraShippingCents:number;excludedProvinces:string[];pickupOnlySkus:string[]}
const deliverySettings=ref<Fulfillment|null>(null),deliveryMethod=ref('PICKUP'),recipient=ref({name:'',phone:'',province:'',address:''}),receiveOpen=ref(false)
const globalAddresses=ref<GlobalAddress[]>([]),selectedAddressId=ref('')
const provinces='北京市 天津市 河北省 山西省 内蒙古自治区 辽宁省 吉林省 黑龙江省 上海市 江苏省 浙江省 安徽省 福建省 江西省 山东省 河南省 湖北省 湖南省 广东省 广西壮族自治区 海南省 重庆市 四川省 贵州省 云南省 西藏自治区 陕西省 甘肃省 青海省 宁夏回族自治区 新疆维吾尔自治区 香港特别行政区 澳门特别行政区 台湾省'.split(' ')
const pickupOnlyInCart=computed(()=>lines.value.some(p=>deliverySettings.value?.pickupOnlySkus.includes(p.skuId)))
async function receive(){await act(async()=>{if(selectedOrder.value)selectedOrder.value=await request<Order>(`/orders/${selectedOrder.value.id}/receive`,token.value,{});receiveOpen.value=false})}
type PaymentCapability={code:string;enabled:boolean;displayName:string}
type PaymentView={id:string;orderId:string;provider:string;status:string;amountCents:number;checkoutType:string;checkoutPayload:string;expiresAt:string}
const simulated = ref(false), legacySimulation=ref(false), paymentProviders=ref<PaymentCapability[]>([]), selectedProvider=ref(''), afterLogin = ref<'checkout' | 'account'>('account')
const requestMembership=ref(false)
const currentStore = computed(() => stores.value.find(s => s.id === storeId.value))
const categories = computed(() => [...new Map(products.value.map(p => [p.categoryId, p.categoryName])).entries()])
const uniqueProducts = computed(() => [...new Map([...products.value].reverse().map(p=>[p.spuId,p])).values()].reverse())
const visibleProducts = computed(() => uniqueProducts.value.filter(p => (category.value === 'all' || p.categoryId === category.value) && products.value.some(s=>s.spuId===p.spuId && `${s.productName} ${s.skuName}`.includes(search.value.trim()))))
const lines = computed(() => products.value.filter(p => cart.value[p.skuId] > 0).map(p => ({ ...p, quantity: cart.value[p.skuId] })))
const total = computed(() => lines.value.reduce((sum, p) => sum + p.effectivePriceCents * p.quantity, 0))
const count = computed(() => lines.value.reduce((sum, p) => sum + p.quantity, 0))
const availableCoupons = computed(() => coupons.value.filter(c => c.status === 'AVAILABLE' && c.minSpendCents <= total.value && new Date(c.validFrom) <= new Date() && new Date(c.validTo) > new Date()))
type Refund = { reason: string; status: string; reply: string; createdAt: string }
type Ledger = { id: string; kind: string; action: string; delta: number; balance: number; createdAt: string }
const refund = ref<Refund | null>(null), refundReason = ref(''), refundOpen = ref(false)
const ledgerOpen = ref(false), ledgers = ref<Ledger[]>([]), ledgerPage = ref(0), ledgerMore = ref(false)
const orderFilter = ref('ALL')
const shownOrders = computed(() => orders.value.filter(o => orderFilter.value === 'ALL' || o.status === orderFilter.value))
const tabs = [{ id: 'home', icon: '⌂', label: '首页' }, { id: 'cart', icon: '▦', label: '购物车' }, { id: 'orders', icon: '▤', label: '订单' }, { id: 'account', icon: '○', label: '我的' }]
let catalogueGeneration = 0

function go(next: string) { if(next==='home'){emit('platform','home');return} if(next==='me')next='account'; location.hash = next; page.value = next; selectedOrder.value = null; window.scrollTo(0, 0) }
function hashChanged() { const next = location.hash.slice(1) || 'shop'; if (page.value !== next) { page.value = next; selectedOrder.value = null } }
function cartKey() { return accountStorageKey(`cart:${tenant}:${storeId.value}`) }
function showError(e: unknown) {
  error.value = e instanceof Error ? e.message : '操作未完成，请重试'
  if (e instanceof ApiError && e.status === 401) { logout(false); authOpen.value = true }
}
async function act(action: () => Promise<void>) {
  if (busy.value) return
  busy.value = true; error.value = ''; notice.value = ''
  try { await action() } catch (e) { showError(e) } finally { busy.value = false }
}
async function loadStores() {
  if (!validEntry) return
  loading.value = true; error.value = ''
  try {
    brand.value = await request<Brand>(`/catalog/tenants/${tenant}/brand`)
    document.title = `${brand.value.displayName} · 门店商城`
    stores.value = await request<Store[]>(`/catalog/tenants/${tenant}/stores`)
    const desired = props.entryStore || readSaved<string>(accountStorageKey(`store:${tenant}`), '')
    if (desired && stores.value.some(s => s.id === desired)) await selectStore(desired)
    else if(desired) throw new Error('该门店已下架或不可用，请返回首页选择其他门店'); else storePicker.value = true
  } catch (e) { showError(e) } finally { loading.value = false }
}
async function selectStore(id: string) {
  const generation = ++catalogueGeneration
  loading.value = true; error.value = ''
  try {
    const data = await request<{ store: Store; products: Product[] }>(`/catalog/tenants/${tenant}/stores/${id}/products`)
    const fulfillment=await request<Fulfillment>(`/catalog/tenants/${tenant}/stores/${id}/fulfillment`)
    if (generation !== catalogueGeneration) return
    deliverySettings.value=fulfillment
    storeId.value = id; products.value = data.products; category.value = 'all'; search.value = ''
    const old = readSaved<Cart>(cartKey(), {})
    cart.value = reconcile(old, data.products)
    if (JSON.stringify(old) !== JSON.stringify(cart.value)) notice.value = '购物车已根据最新可售商品和库存更新'
    save(cartKey(), cart.value); save(accountStorageKey(`store:${tenant}`), id)
    const visits=readSaved<{tenantId:string;id:string;storeName:string;address:string}[]>(accountStorageKey('visits'),[])
    save(accountStorageKey('visits'),[{tenantId:tenant,id,storeName:data.store.storeName,address:data.store.address},...visits.filter(v=>v.tenantId!==tenant||v.id!==id)].slice(0,100))
    storePicker.value = false; quote.value = null
  } catch (e) { showError(e) } finally { if (generation === catalogueGeneration) loading.value = false }
}
function quantity(p: Product, delta: number) {
  const next = Math.min(99, p.availableQuantity, Math.max(0, (cart.value[p.skuId] || 0) + delta))
  if (!p.selectable && delta > 0) return
  const updated = { ...cart.value }; if (next) updated[p.skuId] = next; else delete updated[p.skuId]
  cart.value = updated; save(cartKey(), updated); quote.value = null
}
async function loadWallet() {
  if (!token.value) return
  const activeToken = token.value
  const capabilities=await request<PaymentCapability[]|{simulatedPaymentEnabled:boolean}>('/payments/capabilities', token.value)
  if(activeToken!==token.value)return
  legacySimulation.value=!Array.isArray(capabilities)&&!!capabilities.simulatedPaymentEnabled
  paymentProviders.value=Array.isArray(capabilities)?capabilities.filter(p=>p.enabled):[];selectedProvider.value=paymentProviders.value[0]?.code||(legacySimulation.value?'SANDBOX':'');simulated.value=selectedProvider.value==='SANDBOX'
  if(!isMember.value){wallet.value=null;coupons.value=[];offers.value=[];return}
  const result = await Promise.all([request<Wallet>('/wallet', token.value), request<Coupon[]>('/wallet/coupons', token.value), request<Coupon[]>('/wallet/offers', token.value)])
  if (activeToken !== token.value) return
  await loadProfile(); if (activeToken !== token.value) return; wallet.value = result[0]; mobile.value = result[0].mobile; coupons.value = result[1]; offers.value = result[2]
}
async function authenticated(scopedToken: string, member=false) {
  if(guestCheckout){cart.value=reconcile(guestCheckout,products.value);save(cartKey(),cart.value);guestCheckout=null}
  token.value = scopedToken; isMember.value=member;requestMembership.value=false;authOpen.value = false
  await act(async () => {
    await loadWallet()
    if (afterLogin.value === 'checkout') await checkout()
    else if (page.value === 'orders') await loadOrders()
  })
}
function logout(navigate = true) {
  signOut(); clearAccountState()
  if (navigate) go('home')
}
function clearAccountState() {
  token.value = ''; isMember.value=false; wallet.value = null; orders.value = []; selectedOrder.value = null; coupons.value = []; offers.value = []; quote.value = null; checkoutOpen.value = false; refundOpen.value = false; refund.value = null; ledgerOpen.value = false; ledgers.value = []; addresses.value=[]; favoriteIds.value=[]; feedbackRows.value=[]; extra.value='';globalAddresses.value=[];selectedAddressId.value='';recipient.value={name:'',phone:'',province:'',address:''};addressBookOpen.value=false;couponId.value='';points.value=0;stored.value=0;detailProduct.value=null;cart.value=reconcile(readSaved<Cart>(cartKey(),{}),products.value)
}
watch(() => account.value?.accessToken, clearAccountState)
async function checkout() {
  if (!count.value) return
  if (!token.value) { if(!account.value){guestCheckout={...cart.value};afterLogin.value = 'checkout';authOpen.value = true;return}const session=await enterTenant(tenant);token.value=session.accessToken;isMember.value=session.member }
  const before = JSON.stringify(lines.value.map(p => [p.skuId, p.quantity, p.effectivePriceCents]))
  await selectStore(storeId.value)
  if (error.value) return
  await loadWallet()
  if (!count.value) return
  if (before !== JSON.stringify(lines.value.map(p => [p.skuId, p.quantity, p.effectivePriceCents]))) notice.value = '商品价格或库存已变化，请核对后再提交'
  couponId.value = ''; points.value = 0; stored.value = 0; quote.value = null
  deliveryMethod.value=deliverySettings.value?.pickupEnabled?'PICKUP':'SHIPPING'
  globalAddresses.value=await accountAddresses()
  const preferred=globalAddresses.value.find(a=>a.isDefault)||globalAddresses.value[0]
  selectedAddressId.value=preferred?.id||''
  applyGlobalAddress()
  cartOpen.value = false; checkoutOpen.value = true
}
function applyGlobalAddress(){const value=globalAddresses.value.find(a=>a.id===selectedAddressId.value);recipient.value=value?{name:value.name,phone:value.phone,province:value.province,address:`${value.city}${value.district}${value.detail}`}:{name:wallet.value?.memberName||account.value?.displayName||'',phone:wallet.value?.mobile||account.value?.mobile||'',province:'',address:''}}
function shippingRecipient() {
  const name = recipient.value.name.trim(), phone = recipient.value.phone.normalize('NFKC').trim()
  const province = recipient.value.province, detail = recipient.value.address.trim()
  const address = detail.startsWith(province) ? detail : province + detail
  if (!name) throw new Error('请填写收件人姓名')
  if (!/^[0-9+ -]{7,32}$/.test(phone)) throw new Error('请填写有效的收件电话（7–32位数字，可含 +、空格或短横线）')
  if (!provinces.includes(province)) throw new Error('请选择收货省份 / 地区')
  if (address.length < province.length + 4) throw new Error('请补全详细地址，填写城市、区县、街道和门牌号')
  return { name, phone, province, address }
}
function payload(requestId: string) {
  return { tenantId: tenant, storeId: storeId.value, customerName: wallet.value?.memberName||account.value?.displayName, customerMobile: wallet.value?.mobile||account.value?.mobile, requestId, delivery: {method:deliveryMethod.value,...(deliveryMethod.value==='SHIPPING'?shippingRecipient():{})}, items: lines.value.map(p => ({ skuId: p.skuId, quantity: p.quantity })), benefits: isMember.value ? { couponId: couponId.value || null, pointsToUse: Number(points.value), storedValueToUseCents: Math.round(Number(stored.value) * 100) } : null }
}
async function calculate() { await act(async () => { quote.value = await request<Quote>('/orders/quote', token.value, payload('preview')) }) }
async function submit() {
  await act(async () => {
    if (!quote.value || !lines.value.length) return
    const confirmedAmount = quote.value.payableAmountCents
    const key = `consumer.pending:${tenant}:${account.value?.mobile}`
    const data = payload('')
    const signature = JSON.stringify(data)
    const previous = readSaved<{ signature: string; requestId: string } | null>(key, null, sessionStorage)
    const requestId = previous?.signature === signature ? previous.requestId : crypto.randomUUID()
    save(key, { signature, requestId }, sessionStorage)
    const order = await request<Order>('/orders', token.value, { ...data, requestId })
    save(key, null, sessionStorage); cart.value = {}; save(cartKey(), {}); checkoutOpen.value = false
    go('orders'); selectedOrder.value = order
    if (!selectedProvider.value) { notice.value = '订单已创建，当前没有可用支付方式'; return }
    if (order.payableAmountCents !== confirmedAmount) { notice.value = '订单金额发生变化，请核对实际应付金额后支付'; return }
    notice.value = '订单已创建，正在支付…'
    try { await paySelectedOrder() }
    catch (e) { notice.value = '订单已保留，请查看支付状态或重试支付'; throw e }
  })
}
async function loadOrders(more = false) {
  if (!token.value) return
  const activeToken = token.value
  const next = more ? orderPage.value + 1 : 0
  const result = await request<Order[]>(`/orders?page=${next}`, token.value)
  if (activeToken !== token.value) return
  orders.value = more ? [...orders.value, ...result] : result; orderPage.value = next; hasMore.value = result.length === 20
}
async function refreshOrder() {
  if (!selectedOrder.value || !wallet.value) return
  selectedOrder.value = await request<Order>(`/orders/${selectedOrder.value.id}?tenantId=${tenant}&mobile=${encodeURIComponent(wallet.value.mobile)}`, token.value)
}
async function pay() {
  await act(paySelectedOrder)
}
async function paySelectedOrder() {
  if (!selectedOrder.value || !selectedProvider.value) return
  const id = selectedOrder.value.id, activeToken = token.value, customerMobile = wallet.value?.mobile
  let paid: Order
  try {
    if(legacySimulation.value)paid=await request<Order>(`/orders/${id}/simulate-payment`,activeToken,{tenantId:tenant,paymentRequestId:`h5-pay-${id}`})
    else {const payment=await request<PaymentView>(`/orders/${id}/payments`,activeToken,{provider:selectedProvider.value,requestId:`h5-pay-${id}`});notice.value='支付请求已创建，正在等待渠道确认…';if(payment.provider==='SANDBOX')await request(`/payments/${payment.id}/sandbox/complete`,activeToken,{});if(!customerMobile)throw new Error('无法确认订单状态');paid=await request<Order>(`/orders/${id}?tenantId=${tenant}&mobile=${encodeURIComponent(customerMobile)}`,activeToken)}
  }
  catch (paymentError) {
    if (!customerMobile) throw paymentError
    // A timeout may have occurred after payment committed. Read the same order before retrying.
    paid = await request<Order>(`/orders/${id}?tenantId=${tenant}&mobile=${encodeURIComponent(customerMobile)}`, activeToken)
    if (selectedOrder.value?.id === id) selectedOrder.value = paid
    if (!['PICKUP_READY', 'PENDING_SHIPMENT', 'SHIPPED', 'COMPLETED', 'REFUNDED'].includes(paid.status)) throw paymentError
  }
  if (selectedOrder.value?.id === id) selectedOrder.value = paid
  notice.value = paid.status === 'PICKUP_READY' ? '支付成功，请凭取货码到店自提' : paid.status==='PENDING_SHIPMENT'?'支付成功，商家将尽快发货':'支付处理中，请稍后刷新订单状态'
  await loadWallet()
}
async function cancel() { await act(async () => { if (selectedOrder.value) selectedOrder.value = await request<Order>(`/orders/${selectedOrder.value.id}/cancel`, token.value, {}); await loadWallet() }) }
async function claim(coupon: Coupon) { await act(async () => { await request(`/coupons/${coupon.templateId}/claim`, token.value, { tenantId: tenant, mobile: wallet.value?.mobile, requestId: `claim-${coupon.templateId}-${wallet.value?.id}` }); await loadWallet(); notice.value = '优惠券已放入你的账户' }) }
async function joinMembership(){
  await act(async()=>{
    if(!account.value){afterLogin.value='account';requestMembership.value=true;authOpen.value=true;return}
    try{const session=await enterTenant(tenant,undefined,true);token.value=session.accessToken;isMember.value=true;await loadWallet();notice.value='已加入商家会员'}
    catch(e){if(e instanceof ApiError&&e.status===409){afterLogin.value='account';requestMembership.value=true;authOpen.value=true;return}throw e}
  })
}
async function openRefund() { await act(async () => { if (!selectedOrder.value) return; const rows = await request<Refund[]>(`/orders/${selectedOrder.value.id}/refund-request`, token.value); refund.value = rows[0] ?? null; refundReason.value = ''; refundOpen.value = true }) }
async function applyRefund() { await act(async () => { if (!selectedOrder.value || !refundReason.value.trim()) return; refund.value = await request<Refund>(`/orders/${selectedOrder.value.id}/refund-request`, token.value, { reason: refundReason.value.trim() }) }) }
async function loadLedgers(more = false) { await act(async () => { const next = more ? ledgerPage.value + 1 : 0; const rows = await request<Ledger[]>(`/wallet/ledgers?page=${next}`, token.value); ledgers.value = more ? [...ledgers.value, ...rows] : rows; ledgerPage.value = next; ledgerMore.value = rows.length === 20; ledgerOpen.value = true }) }
function reloadPage(){window.location.reload()}
function imageFailed(event: Event) { (event.target as HTMLImageElement).style.display = 'none' }
watch([couponId, points, stored,deliveryMethod,()=>JSON.stringify(recipient.value)], () => { quote.value = null })
watch(page, async next => { if (!token.value) return; try { if (next === 'orders') await loadOrders(); if (next === 'account') await loadWallet() } catch (e) { showError(e) } })
onMounted(async () => { window.addEventListener('hashchange', hashChanged); await loadStores(); if(props.initialSku){const product=products.value.find(p=>p.skuId===props.initialSku);if(product)openProduct(product);else notice.value='该商品已下架或当前门店不再销售，可查看店内其他商品'} if (account.value) { try { const session=await enterTenant(tenant);token.value=session.accessToken;isMember.value=session.member } catch (e) { showError(e) } } if (token.value) { try { await loadWallet(); if (page.value === 'orders') {await loadOrders();if(props.initialOrder)selectedOrder.value=await request<Order>(`/orders/${props.initialOrder}?tenantId=${tenant}&mobile=${encodeURIComponent(account.value?.mobile||'')}`,token.value)} } catch (e) { showError(e) } } })
onUnmounted(() => {window.removeEventListener('hashchange', hashChanged);token.value='';catalogueGeneration++})

type Address = { id?: string; name: string; phone: string; address: string; label: string; isDefault: boolean }
type Feedback = { id: string; content: string; status: string; createdAt: string }
const extra=ref(''), profileName=ref(''), notifications=ref(true), addresses=ref<Address[]>([]), favoriteIds=ref<string[]>([]), feedbackRows=ref<Feedback[]>([]), feedbackText=ref('')
const addressDraft=ref<Address|null>(null), detailProduct=ref<Product|null>(null), detailQty=ref(1)
const storeSearch=ref('')
const variants=computed(()=>products.value.filter(p=>p.spuId===detailProduct.value?.spuId))
const savedProducts=computed(()=>products.value.filter(p=>favoriteIds.value.includes(p.skuId)))
async function loadProfile(){
  const activeToken=token.value
  const [profile, saved]=await Promise.all([request<{name:string;notifications:boolean}>('/profile',activeToken),request<string[]>('/profile/favorites',activeToken)])
  if(activeToken!==token.value)return
  profileName.value=profile.name;notifications.value=profile.notifications;favoriteIds.value=saved
}
async function openExtra(target:string){
  if(!token.value && !['help'].includes(target)){authOpen.value=true;afterLogin.value='account';return}
  extra.value=target;addressDraft.value=null
  await act(async()=>{
    if(target==='addresses')addresses.value=await request<Address[]>('/profile/addresses',token.value)
    if(target==='help'&&token.value)feedbackRows.value=await request<Feedback[]>('/profile/feedback',token.value)
    if(target==='settings')await loadProfile()
    if(target==='messages')await loadOrders()
  })
}
async function saveProfile(){await act(async()=>{await request('/profile',token.value,{name:profileName.value,notifications:notifications.value});await loadWallet();notice.value='账户设置已保存';extra.value=''})}
function editAddress(a?:Address){addressDraft.value=a?{...a}:{name:wallet.value?.memberName||'',phone:wallet.value?.mobile||'',address:'',label:'家',isDefault:!addresses.value.length}}
async function saveAddress(){await act(async()=>{addresses.value=await request<Address[]>('/profile/addresses',token.value,addressDraft.value);addressDraft.value=null;notice.value='地址已保存'})}
async function deleteAddress(a:Address){await act(async()=>{addresses.value=await request<Address[]>(`/profile/addresses/${a.id}/delete`,token.value,{});notice.value='地址已删除'})}
async function toggleFavorite(p:Product){if(!token.value){detailProduct.value=null;authOpen.value=true;return}await act(async()=>{favoriteIds.value=await request<string[]>('/profile/favorites',token.value,{skuId:p.skuId,saved:!favoriteIds.value.includes(p.skuId)})})}
async function sendFeedback(){await act(async()=>{feedbackRows.value=await request<Feedback[]>('/profile/feedback',token.value,{content:feedbackText.value});feedbackText.value='';notice.value='反馈已保存，可在此查看提交记录'})}
function openProduct(p:Product){detailProduct.value=p;detailQty.value=1;if(account.value&&currentStore.value)void recordBrowse({tenantId:tenant,storeId:storeId.value,skuId:p.skuId,merchantName:brand.value?.displayName||currentStore.value.storeName,storeName:currentStore.value.storeName,productName:p.productName,skuName:p.skuName,imageUrl:p.imageUrl||'',priceCents:p.effectivePriceCents}).catch(()=>{})}
function addDetail(){if(!detailProduct.value)return;quantity(detailProduct.value,detailQty.value);detailProduct.value=null;notice.value='已加入购物袋'}
async function again(){if(!selectedOrder.value)return;const order=selectedOrder.value;await act(async()=>{await selectStore(order.storeId);if(error.value)return;for(const item of order.items){const p=products.value.find(p=>p.skuId===item.skuId);if(p)quantity(p,item.quantity)}go('shop');notice.value='已按当前商品价格和库存加入购物袋，请核对'})}
const hasOverlay=computed(()=>!!(addressBookOpen.value||receiveOpen.value||extra.value||detailProduct.value||storePicker.value||cartOpen.value||authOpen.value||checkoutOpen.value||refundOpen.value||ledgerOpen.value))
watch(hasOverlay,value=>{document.body.style.overflow=value?'hidden':''})
function closeOverlay(){if(addressBookOpen.value)addressBookOpen.value=false;else if(receiveOpen.value)receiveOpen.value=false;else if(authOpen.value)authOpen.value=false;else if(detailProduct.value)detailProduct.value=null;else if(extra.value)extra.value='';else if(refundOpen.value)refundOpen.value=false;else if(ledgerOpen.value)ledgerOpen.value=false;else if(checkoutOpen.value)checkoutOpen.value=false;else if(cartOpen.value)cartOpen.value=false;else storePicker.value=false}
function escapeKey(e:KeyboardEvent){if(e.key==='Escape')closeOverlay()}
onMounted(()=>window.addEventListener('keydown',escapeKey))
onUnmounted(()=>{window.removeEventListener('keydown',escapeKey);document.body.style.overflow=''})
</script>

<template>
  <div v-if="addressBookOpen" class="market-overlay" @click.self="addressBookOpen=false"><section class="market-location-sheet account-sheet" role="dialog" aria-modal="true" aria-label="通用收货地址"><div class="account-sheet-head"><button aria-label="返回结算" @click="addressBookOpen=false"><Icon name="back"/></button><h2>通用收货地址</h2><span/></div><GlobalAddressBook :key="account?.mobile" selectable @select="selectAddress" @change="addressesChanged"/></section></div>
  <div class="app-shell" :style="brand ? { '--brand-color': brand.themeColor } : {}">
    <div class="store-return"><button @click="emit('platform','home')">‹ 附近门店</button><strong>{{currentStore?.storeName}}</strong></div>
    <header class="topbar"><a class="wordmark" href="#home" @click="go('home')"><span class="brand-mark"><img v-if="brand?.logoUrl" :src="brand.logoUrl" alt="" @error="imageFailed"><template v-else>{{ brand?.displayName.slice(0, 1) || '叶' }}</template></span> {{ brand?.displayName || '门店商城' }}<span class="wordmark-sub">好物 · 就在身边</span></a><button class="header-bell" aria-label="消息中心" @click="openExtra('messages')"><Icon name="bell"/></button></header>
    <nav class="shop-local-nav" aria-label="店铺功能"><button :class="{active:page==='shop'}" @click="go('shop')">全部商品</button><button :class="{active:page==='orders'}" @click="go('orders')">本店订单</button><button :class="{active:page==='account'}" @click="go('account')">商家会员</button></nav>
    <main v-if="!validEntry" class="entry-empty"><span class="big-symbol">⌂</span><h1>商城暂未就绪</h1><p>品牌配置暂不可用，请稍后重试。</p><button class="primary" @click="reloadPage">重新加载</button></main>
    <template v-else>
      <div v-if="error" class="message error" role="alert">{{ error }}<button aria-label="关闭提示" @click="error = ''">×</button></div>
      <div v-if="notice" class="message" role="status">{{ notice }}<button aria-label="关闭提示" @click="notice = ''">×</button></div>
      <div v-if="loading" class="loading" role="status">正在加载门店商品…</div>
      <button class="store-bar" @click="storePicker = true"><span class="store-pin">⌖</span><span><strong>{{ currentStore?.storeName || '选择门店' }}</strong><small>{{ currentStore?.businessHours ? `营业时间 ${currentStore.businessHours}` : '选好门店，发现身边好物' }}</small></span><span class="store-change">切换 ›</span></button>

      <template v-if="page === 'shop'">
        <div class="search-wrap"><span>⌕</span><input v-model="search" aria-label="搜索商品" placeholder="搜索想买的好物"><button v-if="search" aria-label="清空搜索" @click="search = ''">×</button></div>
        <div class="catalog-layout"><aside class="categories" aria-label="商品分类"><button :class="{ active: category === 'all' }" @click="category = 'all'">全部商品</button><button v-for="[id, label] in categories" :key="id" :class="{ active: category === id }" @click="category = id">{{ label }}</button></aside><section class="product-list"><div class="list-heading">{{ category === 'all' ? '门店精选' : categories.find(c => c[0] === category)?.[1] }}<span>{{ visibleProducts.length }} 款好物</span></div><div v-if="!visibleProducts.length" class="empty">{{ search ? '没有找到相关商品' : '暂无可售商品' }}</div><article v-for="p in visibleProducts" :key="p.skuId" class="product-row"><div class="product-image"><div class="generic-product-art"><Icon name="bag" :size="52"/><small>商品图片待上传</small></div><img v-if="p.imageUrl" :src="p.imageUrl" alt="" loading="lazy" @error="imageFailed"></div><div class="product-info"><button class="product-title-button" @click="openProduct(p)">{{ p.productName }}</button><p>{{ p.skuName }}<small v-if="deliverySettings?.pickupOnlySkus.includes(p.skuId)"> · 仅自提</small></p><small v-if="p.description">{{ p.description }}</small><div class="product-bottom"><strong>¥{{ money(p.effectivePriceCents) }}</strong><div class="stepper"><button v-if="cart[p.skuId]" :aria-label="`减少${p.productName}`" @click="quantity(p, -1)">−</button><span v-if="cart[p.skuId]">{{ cart[p.skuId] }}</span><button :aria-label="`添加${p.productName} ${p.skuName}`" :disabled="!p.selectable || (cart[p.skuId] || 0) >= Math.min(p.availableQuantity, 99)" @click="quantity(p, 1)">{{ p.selectable ? '+' : '售罄' }}</button></div></div></div></article></section></div>
        <div v-if="count" class="cart-dock"><button class="cart-summary" @click="cartOpen = true"><span class="cart-count">{{ count }}</span><span><strong>¥{{ money(total) }}</strong><small>自提 / 邮寄 · 共 {{ count }} 件</small></span></button><button class="checkout-button" :disabled="busy || loading" @click="act(checkout)">去结算 ›</button></div>
      </template>

      <section v-else-if="page === 'cart'" class="section"><h1>本店购物车</h1><p>{{currentStore?.storeName}} · 按本店价格和库存结算</p><div v-if="!lines.length" class="empty">还没有选购商品<button class="primary" @click="go('shop')">去选购</button></div><div v-for="p in lines" :key="p.skuId" class="receipt-line"><span>{{p.productName}}<small>{{p.skuName}} · ¥{{money(p.effectivePriceCents)}}</small></span><div class="stepper"><button @click="quantity(p,-1)">−</button><span>{{p.quantity}}</span><button :disabled="p.quantity>=Math.min(99,p.availableQuantity)" @click="quantity(p,1)">+</button></div></div><button v-if="lines.length" class="primary full" :disabled="busy||loading" @click="act(checkout)">去结算 · ¥{{money(total)}}</button></section>
      <section v-else-if="page === 'orders'" class="section orders-section">
        <template v-if="selectedOrder"><button class="text-button" @click="selectedOrder = null; act(() => loadOrders())">‹ 全部订单</button><div class="order-heading"><span class="eyebrow">YOUR ORDER</span><h1>{{ statusNames[selectedOrder.status] || selectedOrder.status }}</h1><p v-if="selectedOrder.status === 'PENDING_PAYMENT'">请于 {{ selectedOrder.expiresAt.replace('T', ' ') }} 前完成支付</p><p v-else-if="selectedOrder.status === 'PICKUP_READY'">请到订单门店出示取货码</p></div><div v-if="selectedOrder.delivery?.method==='SHIPPING'" class="delivery-details"><h3>快递邮寄</h3><p>{{selectedOrder.delivery.name}} · {{selectedOrder.delivery.phone}}</p><p>{{selectedOrder.delivery.address}}</p><p>运费 ¥{{money(selectedOrder.delivery.shippingFeeCents)}}（已含在实付金额）</p><p v-if="selectedOrder.delivery.trackingNo">{{selectedOrder.delivery.carrier}} · {{selectedOrder.delivery.trackingNo}}</p><p v-if="selectedOrder.status==='PENDING_SHIPMENT'">商家正在准备发货</p><button v-if="selectedOrder.status==='SHIPPED'" class="primary" :disabled="busy" @click="receiveOpen=true">确认收货</button></div><div v-if="selectedOrder.status === 'PICKUP_READY'" class="pickup-code"><span>取货码</span><strong>{{ selectedOrder.pickupCode }}</strong><small>仅向门店工作人员出示</small></div><div class="panel"><h3>{{ stores.find(s => s.id === selectedOrder?.storeId)?.storeName || '订单门店' }}</h3><p>{{ stores.find(s => s.id === selectedOrder?.storeId)?.address }}</p><div v-for="item in selectedOrder.items" :key="item.skuId" class="receipt-line"><span>{{ item.productName }}<small>{{ item.skuName }} × {{ item.quantity }}</small></span><strong>¥{{ money(item.lineAmountCents) }}</strong></div><div class="receipt-line"><span>商品金额</span><span>¥{{ money(selectedOrder.totalAmountCents) }}</span></div><div class="receipt-line"><span>优惠券 / 积分 / 储值抵扣</span><span>−¥{{ money(selectedOrder.couponDiscountCents + selectedOrder.pointsUsed + selectedOrder.storedValueUsedCents) }}</span></div><div class="receipt-line total-line"><span>应付金额</span><strong>¥{{ money(selectedOrder.payableAmountCents) }}</strong></div><p class="order-number">订单号 {{ selectedOrder.orderNo }}</p></div><div class="order-actions"><button class="secondary" :disabled="busy" @click="again">再来一单</button><button v-if="['COMPLETED','REFUNDED'].includes(selectedOrder.status)" class="secondary" :disabled="busy" @click="openRefund">退款 / 售后</button><button class="secondary" :disabled="busy" @click="act(refreshOrder)">刷新状态</button><template v-if="selectedOrder.status === 'PENDING_PAYMENT'"><button class="secondary" :disabled="busy" @click="cancel">取消订单</button><button v-if="selectedProvider" class="primary" :disabled="busy" @click="pay">{{legacySimulation?'模拟支付':'立即支付'}}</button></template></div><p v-if="selectedOrder.status === 'PENDING_PAYMENT'" class="muted">{{ selectedProvider ? (legacySimulation ? '当前为兼容联调模式，不会产生真实扣款。' : '本地沙箱不会真实扣款；正式渠道将以异步通知为准。') : '当前没有可用支付方式，你可以稍后刷新或取消订单。' }}</p></template>
        <template v-else><div class="section-title"><h1>我的订单</h1><button v-if="token" class="text-button" :disabled="busy" @click="act(() => loadOrders())">刷新</button></div><div v-if="!token" class="empty"><span class="big-symbol">▤</span><h2>每一次喜欢，都在这里</h2><p>登录后查看你的订单和取货码</p><button class="primary" @click="afterLogin = 'account'; authOpen = true">登录查看</button></div><template v-else><div class="order-filters"><button v-for="[key, label] in [['ALL','全部'],['PENDING_PAYMENT','待支付'],['PICKUP_READY','待自提'],['PENDING_SHIPMENT','待发货'],['SHIPPED','待收货'],['COMPLETED','已完成']]" :key="key" :class="{ active: orderFilter === key }" @click="orderFilter = key">{{ label }}</button></div><div v-if="!shownOrders.length" class="empty">还没有相关订单，去选点喜欢的吧</div><button v-for="o in shownOrders" :key="o.id" class="order-card" @click="selectedOrder = o"><div><strong>{{ stores.find(s => s.id === o.storeId)?.storeName || '门店订单' }} ›</strong><span>{{ statusNames[o.status] || o.status }}</span></div><p>{{ o.items.map(i => i.productName).join('、') }}</p><footer>共 {{ o.totalQuantity }} 件<strong>¥{{ money(o.payableAmountCents) }}</strong></footer></button><button v-if="hasMore" class="secondary full" :disabled="busy" @click="act(() => loadOrders(true))">加载更多订单</button></template></template>
      </section>

      <section v-else class="section account-section"><div class="account-heading"><div class="avatar">{{ wallet?.memberName.slice(0, 1) || '客' }}</div><div><span class="eyebrow">MERCHANT MEMBERSHIP</span><h1>{{ wallet?.memberName || '商家会员' }}</h1><p>{{ brand?.displayName }}会员权益仅在本商家使用</p></div></div><button v-if="!account" class="primary full" @click="afterLogin = 'account'; authOpen = true">登录 H5 账号</button><div v-else-if="!isMember" class="empty compact"><h2>是否加入，由你决定</h2><p>不加入会员也可以逛店、下单和支付；加入后可使用本商家的积分、优惠券和储值。</p><button class="primary" :disabled="busy" @click="joinMembership">自愿加入商家会员</button></div><template v-else-if="wallet"><div class="wallet-grid"><div><strong>{{ wallet.availablePoints }}</strong><span>可用积分</span></div><div><strong>{{ coupons.filter(c => c.status === 'AVAILABLE').length }}</strong><span>优惠券</span></div><div><strong>{{ money(wallet.storedValueCents) }}</strong><span>储值余额（元）</span></div></div><button class="secondary full" :disabled="busy" @click="loadLedgers()">查看积分与储值明细</button><div class="section-title"><h2>我的优惠券</h2><button class="text-button" :disabled="busy" @click="act(loadWallet)">刷新</button></div><div v-if="!coupons.length" class="empty compact">暂无优惠券，看看下方可领取的福利</div><article v-for="c in coupons" :key="c.id" class="coupon-card"><strong>¥{{ money(c.discountCents) }}</strong><div><h3>{{ c.name }}</h3><p>满 ¥{{ money(c.minSpendCents) }} 可用</p><small>有效期至 {{ c.validTo.replace('T', ' ') }}</small></div><span>{{ ({ AVAILABLE: '可使用', FROZEN: '已锁定', USED: '已使用', EXPIRED: '已过期' } as Record<string,string>)[c.status] || c.status }}</span></article><h2>领券中心</h2><div v-if="!offers.length" class="empty compact">暂时没有可领取的优惠券</div><article v-for="c in offers" :key="c.id" class="coupon-card"><strong>¥{{ money(c.discountCents) }}</strong><div><h3>{{ c.name }}</h3><p>满 ¥{{ money(c.minSpendCents) }} 可用</p></div><button class="primary" :disabled="busy" @click="claim(c)">领取</button></article></template><div class="service-grid"><button v-for="item in [{id:'favorites',icon:'heart',name:'本店收藏'},{id:'messages',icon:'bell',name:'订单消息'},{id:'help',icon:'help',name:'商家客服'}]" :key="item.id" @click="openExtra(item.id)"><Icon :name="item.icon"/><span>{{ item.name }}</span></button><button @click="storePicker=true"><Icon name="search"/><span>切换门店</span></button></div><div class="help-card"><a v-if="brand?.contactPhone" class="primary" :href="`tel:${brand.contactPhone.replace(/[^0-9+]/g, '')}`">联系商家 {{ brand.contactPhone }}</a><h3>会员资产说明</h3><p>积分、余额和优惠券由当前商家管理；统一地址和账号安全设置请回到邻里百货“我的”。</p></div></section>

      <nav class="bottom-nav" aria-label="主导航"><button v-for="tab in tabs" :key="tab.id" :class="{ active: page === tab.id }" @click="emit('platform',tab.id)"><Icon :name="({home:'home',cart:'bag',orders:'order',account:'user'} as Record<string,string>)[tab.id]" :size="24"/>{{ tab.label }}</button></nav>
    </template>

    <div v-if="storePicker && validEntry" class="overlay" @click.self="storePicker = false"><section class="sheet" role="dialog" aria-modal="true" aria-label="选择自提门店"><div class="sheet-title"><h2>选择自提门店</h2><button aria-label="关闭门店选择" @click="storePicker = false">×</button></div><p class="muted">购物车按门店保存，切换后重新校验价格与库存。</p><div v-if="!stores.length" class="empty"><p>{{ loading ? '正在寻找门店…' : '暂无可用门店' }}</p><button class="secondary" @click="loadStores">重新加载</button></div><input v-model="storeSearch" aria-label="搜索门店" placeholder="搜索门店名称或地址"><p v-if="stores.length && !stores.some(s=>(s.storeName+s.address).includes(storeSearch))">没有找到门店</p><button v-for="s in stores.filter(s=>(s.storeName+s.address).includes(storeSearch))" :key="s.id" class="store-option" :class="{ selected: storeId === s.id }" :disabled="loading" @click="selectStore(s.id)"><strong>{{ s.storeName }} <span v-if="storeId === s.id">✓</span></strong><p>{{ s.address || '地址以门店告知为准' }}</p><small>营业时间 {{ s.businessHours || '请咨询门店' }}</small></button></section></div>
    <div v-if="receiveOpen" class="overlay" @click.self="receiveOpen=false"><section class="sheet" role="dialog" aria-modal="true" aria-label="确认收货"><h2>已收到全部商品？</h2><p>确认后订单将完成，请收到并核对商品后操作。</p><button class="primary full" :disabled="busy" @click="receive">已收到，确认收货</button><button class="secondary full" @click="receiveOpen=false">暂不确认</button></section></div>
    <div v-if="cartOpen" class="overlay" @click.self="cartOpen = false"><section class="sheet" role="dialog" aria-modal="true" aria-label="购物车"><div class="sheet-title"><h2>已选好物（{{ count }}）</h2><button aria-label="关闭购物车" @click="cartOpen = false">×</button></div><div v-for="p in lines" :key="p.skuId" class="receipt-line"><span>{{ p.productName }}<small>{{ p.skuName }} · ¥{{ money(p.effectivePriceCents) }}</small></span><div class="stepper"><button :aria-label="`减少${p.productName}`" @click="quantity(p, -1)">−</button><span>{{ p.quantity }}</span><button :aria-label="`增加${p.productName}`" :disabled="p.quantity >= Math.min(p.availableQuantity, 99)" @click="quantity(p, 1)">+</button></div></div><button class="primary full" :disabled="!count || busy" @click="act(checkout)">去结算 · ¥{{ money(total) }}</button></section></div>
    <ConsumerAuth v-if="authOpen" :tenant-id="tenant" :join-membership="requestMembership" @close="authOpen=false;requestMembership=false" @authenticated="authenticated"/>
    <div v-if="refundOpen" class="overlay" @click.self="refundOpen = false"><section class="sheet" role="dialog" aria-modal="true" aria-label="退款售后"><div class="sheet-title"><h2>退款 / 售后</h2><button aria-label="关闭售后" @click="refundOpen = false">×</button></div><template v-if="refund"><h3>{{ ({ PENDING: '等待门店处理', APPROVED: '退款已处理', REJECTED: '申请未通过' } as Record<string,string>)[refund.status] }}</h3><p class="muted">申请时间 {{ refund.createdAt.replace('T', ' ') }}</p><p>{{ refund.reason }}</p><p v-if="refund.reply" class="muted">门店回复：{{ refund.reply }}</p></template><template v-else-if="selectedOrder?.status === 'COMPLETED'"><p class="muted">当前支持已完成订单的整单退款，由门店审核后处理。</p><label>退款原因<input v-model="refundReason" maxlength="500" placeholder="请描述遇到的问题"></label><button class="primary full" :disabled="busy || !refundReason.trim()" @click="applyRefund">提交退款申请</button></template><p v-else>订单已退款，请查看订单状态及权益变动。</p><p v-if="error" class="inline-error">{{ error }}</p></section></div>
    <div v-if="ledgerOpen" class="overlay" @click.self="ledgerOpen = false"><section class="sheet" role="dialog" aria-modal="true" aria-label="权益明细"><div class="sheet-title"><h2>积分与储值明细</h2><button aria-label="关闭权益明细" @click="ledgerOpen = false">×</button></div><p v-if="!ledgers.length" class="empty">暂无变动记录</p><div v-for="entry in ledgers" :key="`${entry.kind}:${entry.id}`" class="receipt-line"><span>{{ entry.kind === 'POINTS' ? '积分' : '储值' }} · {{ ({ MANUAL: '门店调整', FREEZE: '订单冻结', CONFIRM: '支付确认', RELEASE: '订单释放', REFUND: '退款返还' } as Record<string,string>)[entry.action] || entry.action }}<small>{{ entry.createdAt.replace('T', ' ') }}</small></span><span>{{ entry.delta > 0 ? '+' : '' }}{{ entry.kind === 'POINTS' ? entry.delta : money(entry.delta) }}<small>余额 {{ entry.kind === 'POINTS' ? entry.balance : money(entry.balance) }}</small></span></div><button v-if="ledgerMore" class="secondary full" :disabled="busy" @click="loadLedgers(true)">加载更多</button></section></div>
    <div v-if="checkoutOpen && !addressBookOpen" class="overlay" @click.self="checkoutOpen = false"><section class="sheet checkout-sheet" role="dialog" aria-modal="true" aria-label="确认订单"><div class="sheet-title"><h2>确认订单</h2><button aria-label="关闭结算" @click="checkoutOpen = false">×</button></div><div class="checkout-store"><span class="eyebrow">{{deliveryMethod==='SHIPPING'?'快递邮寄':'到店自提'}}</span><h3>{{ currentStore?.storeName }}</h3><p>{{ currentStore?.address }}</p><small>{{ wallet?.memberName }} · {{ wallet?.mobile }}</small></div><div class="delivery-tabs"><button v-if="deliverySettings?.pickupEnabled" :class="{active:deliveryMethod==='PICKUP'}" @click="deliveryMethod='PICKUP'">到店自提</button><button v-if="deliverySettings?.shippingEnabled" :class="{active:deliveryMethod==='SHIPPING'}" @click="deliveryMethod='SHIPPING'">快递邮寄</button></div><div v-if="deliveryMethod==='SHIPPING'" class="shipping-form"><p>运费由你支付：首件 ¥{{money(deliverySettings?.firstShippingCents||0)}}，每续件 ¥{{money(deliverySettings?.extraShippingCents||0)}}。</p><label v-if="globalAddresses.length">选择收货地址<select v-model="selectedAddressId" @change="applyGlobalAddress"><option v-for="a in globalAddresses" :key="a.id" :value="a.id">{{a.isDefault?'[默认] ':''}}{{a.name}} · {{a.province}}{{a.city}}{{a.district}}{{a.detail}}</option></select></label><p v-else class="muted">添加通用地址后，其他商家结算也可直接选择。</p><button class="outline-wide" @click="addressBookOpen=true">管理 / 新增通用地址</button><label>收件人<input v-model="recipient.name" maxlength="64" autocomplete="name"></label><label>收件电话<input v-model="recipient.phone" maxlength="32" type="tel" autocomplete="tel"></label><label>省份 / 地区<select v-model="recipient.province"><option value="">请选择省份</option><option v-for="p in provinces" :key="p" :value="p" :disabled="deliverySettings?.excludedProvinces.includes(p)">{{p}}{{deliverySettings?.excludedProvinces.includes(p)?'（不配送）':''}}</option></select></label><label>详细地址<textarea v-model="recipient.address" maxlength="450" placeholder="城市、区县、街道和门牌号" autocomplete="street-address"/></label><p v-if="pickupOnlyInCart" class="inline-error">购物车含仅自提商品，请改为自提或移除后分开购买。</p></div><div v-for="p in lines" :key="p.skuId" class="receipt-line"><span>{{ p.productName }}<small>{{ p.skuName }} × {{ p.quantity }}</small></span><strong>¥{{ money(p.effectivePriceCents * p.quantity) }}</strong></div><label>优惠券<select v-model="couponId" aria-label="优惠券"><option value="">不使用优惠券</option><option v-for="c in availableCoupons" :key="c.id" :value="c.id">{{ c.name }} · 减 ¥{{ money(c.discountCents) }}</option></select></label><div class="benefit-inputs"><label>使用积分<input v-model="points" type="number" min="0" :max="wallet?.availablePoints" step="1" inputmode="numeric"></label><label>使用储值（元）<input v-model="stored" type="number" min="0" :max="(wallet?.storedValueCents || 0) / 100" step="0.01" inputmode="decimal"></label></div><div class="receipt-line"><span>运费（消费者支付）</span><strong>{{deliveryMethod==='PICKUP'?'免运费':quote?'¥'+money(quote.shippingFeeCents):'填写地址后试算'}}</strong></div><div class="receipt-line total-line"><span>{{ quote ? '试算应付' : '商品小计' }}</span><strong>¥{{ money(quote?.payableAmountCents ?? total) }}</strong></div><p v-if="quote" class="muted">优惠券 ¥{{ money(quote.couponDiscountCents) }} · 积分 {{ quote.pointsUsed }} · 储值 ¥{{ money(quote.storedValueUsedCents) }}</p><p class="muted">{{ simulated ? '确认金额后下单即自动模拟支付，不会产生真实扣款。' : '支付暂未开放，提交后保留待支付订单。' }}</p><button v-if="!quote" class="primary full" :disabled="busy || !count || (deliveryMethod==='SHIPPING' && (pickupOnlyInCart||!recipient.name.trim()||!recipient.phone.trim()||!recipient.province||!recipient.address.trim()))" @click="calculate">{{ busy ? '正在试算…' : '确认商品并试算' }}</button><button v-else class="primary full" :disabled="busy" @click="submit">{{ busy ? '正在下单支付…' : simulated ? '下单并支付' : '提交订单' }}</button><p v-if="error" class="inline-error" role="alert">{{ error }}</p></section></div>
    <div v-if="detailProduct" class="overlay" @click.self="detailProduct=null"><section class="sheet drink-detail" role="dialog" aria-modal="true" aria-label="商品规格"><div class="sheet-title"><h2>{{ detailProduct.productName }}</h2><button aria-label="关闭规格" @click="detailProduct=null">×</button></div><div class="detail-visual"><div class="generic-product-art"><Icon name="bag" :size="52"/><small>商品图片待上传</small></div><img v-if="detailProduct.imageUrl" :src="detailProduct.imageUrl" alt="商品图片" @error="imageFailed"></div><p>{{ detailProduct.description }}</p><div class="sheet-title"><strong>¥{{ money(detailProduct.effectivePriceCents) }}</strong><button class="favorite-button" :class="{ active: favoriteIds.includes(detailProduct.skuId) }" :aria-label="favoriteIds.includes(detailProduct.skuId)?'取消收藏':'收藏商品'" :aria-pressed="favoriteIds.includes(detailProduct.skuId)" :disabled="busy" @click="toggleFavorite(detailProduct)"><Icon name="heart" :size="22"/></button></div><h3>选择规格</h3><div class="sku-options"><button v-for="p in variants" :key="p.skuId" :class="{active:p.skuId===detailProduct.skuId}" :disabled="!p.selectable" @click="detailProduct=p">{{ p.skuName }} · ¥{{ money(p.effectivePriceCents) }} {{ p.selectable?'':'售罄' }}</button></div><p class="muted">规格、价格和库存以当前门店配置为准。</p><label>数量<input v-model.number="detailQty" type="number" min="1" :max="Math.min(99,detailProduct.availableQuantity)" step="1"></label><button class="primary full" :disabled="!detailProduct.selectable || !Number.isInteger(detailQty) || detailQty<1 || detailQty>Math.min(99,detailProduct.availableQuantity)" @click="addDetail">加入购物袋</button></section></div>
    <div v-if="extra" class="overlay" @click.self="extra=''"><section class="sheet extras-sheet" role="dialog" aria-modal="true" :aria-label="extra"><div class="sheet-title"><h2>{{ ({addresses:'收货地址',favorites:'我的收藏',settings:'账户设置',messages:'订单消息',help:'帮助与反馈',delivery:'外送到家',gift:'礼品卡',topup:'在线充值'} as Record<string,string>)[extra] }}</h2><button aria-label="关闭功能面板" @click="extra=''">×</button></div>
      <template v-if="extra==='addresses'"><p class="muted">地址保存到当前品牌的会员账户。外送服务开通后可用于配送。</p><form v-if="addressDraft" @submit.prevent="saveAddress"><label>收货人<input v-model="addressDraft.name" maxlength="40" required></label><label>联系电话<input v-model="addressDraft.phone" type="tel" pattern="1[3-9][0-9]{9}" maxlength="11" required></label><label>详细地址<textarea v-model="addressDraft.address" minlength="5" maxlength="240" required/></label><label>地址标签<select v-model="addressDraft.label"><option>家</option><option>公司</option><option>其他</option></select></label><label class="check-label"><input v-model="addressDraft.isDefault" type="checkbox">设为默认地址</label><button class="primary full" :disabled="busy">保存地址</button><button type="button" class="secondary full" @click="addressDraft=null">取消编辑</button></form><template v-else><p v-if="!addresses.length" class="empty">还没有收货地址</p><article v-for="a in addresses" :key="a.id" class="address-row"><h3>{{ a.address }}</h3><p>{{ a.name }} · {{ a.phone }}</p><small>{{ a.label }} {{ a.isDefault?'· 默认地址':'' }}</small><div class="order-actions"><button class="secondary" :disabled="busy" @click="editAddress(a)">编辑地址</button><button class="secondary" :disabled="busy" @click="deleteAddress(a)">删除地址</button></div></article><button class="primary full" :disabled="busy" @click="editAddress()">新增收货地址</button></template></template>
      <template v-else-if="extra==='favorites'"><p class="muted">展示当前门店可查询到的收藏商品，切换门店可查看其他商品。</p><p v-if="!savedProducts.length" class="empty">当前门店暂无收藏商品</p><article v-for="p in savedProducts" :key="p.skuId" class="receipt-line"><span>{{ p.productName }}<small>{{ p.skuName }} · ¥{{ money(p.effectivePriceCents) }}</small></span><button class="secondary" @click="extra='';openProduct(p)">选规格</button><button class="text-button" :disabled="busy" @click="toggleFavorite(p)">取消收藏</button></article></template>
      <form v-else-if="extra==='settings'" @submit.prevent="saveProfile"><label>昵称<input v-model="profileName" maxlength="40" required></label><p>登录手机号：{{ wallet?.mobile }}</p><label class="check-label"><input v-model="notifications" type="checkbox">显示订单消息</label><p class="muted">此设置控制站内订单消息，不发送短信或系统推送。</p><button class="primary full" :disabled="busy">保存设置</button></form>
      <template v-else-if="extra==='messages'"><p v-if="!notifications" class="empty">订单消息已关闭，可在账户设置中开启。</p><template v-else><p v-if="!orders.length" class="empty">暂无订单消息</p><button v-for="o in orders" :key="o.id" class="order-card" @click="extra='';go('orders');selectedOrder=o"><strong>{{ statusNames[o.status] }}</strong><p>{{ o.items.map(i=>i.productName).join('、') }}</p><small>{{ o.orderNo }}</small></button><button v-if="hasMore" class="secondary full" :disabled="busy" @click="act(()=>loadOrders(true))">加载更多消息</button></template></template>
      <template v-else-if="extra==='help'"><details open><summary>如何取货或收货？</summary><p>自提订单支付后出示取货码，由订单门店核销。邮寄订单由商家发货，快递公司和单号可在订单详情查看；收到商品后确认收货。</p></details><details><summary>如何退款？</summary><p>已完成订单可申请整单售后，门店审核后恢复适用权益。邮寄订单整单退款包含原订单运费，退货寄回方式请先联系商家。</p></details><a v-if="brand?.contactPhone" class="primary full" :href="`tel:${brand.contactPhone.replace(/[^0-9+]/g,'')}`">联系品牌客服 {{ brand.contactPhone }}</a><template v-if="token"><form @submit.prevent="sendFeedback"><label>意见反馈<textarea v-model="feedbackText" maxlength="1000" required placeholder="写下你的建议或遇到的问题"/></label><button class="primary full" :disabled="busy||!feedbackText.trim()">提交反馈</button></form><p class="muted">反馈保存在品牌系统，订单售后请使用订单中的退款申请入口。</p><article v-for="f in feedbackRows" :key="f.id" class="address-row"><p>{{ f.content }}</p><small>{{ f.status==='RESOLVED'?'已处理':'已提交' }} · {{ f.createdAt.replace('T',' ') }}</small></article></template><button v-else class="primary full" @click="extra='';authOpen=true">登录后反馈</button></template>
      <template v-else><div class="unavailable-service"><Icon :name="extra==='delivery'?'bike':extra==='gift'?'gift':'wallet'" :size="48"/><h3>服务尚未开通</h3><p>{{ extra==='delivery'?'当前门店支持到店自提，外送服务正在筹备。':extra==='gift'?'线上礼品卡尚未开售，欢迎到店咨询。':'在线充值暂未开放，现有储值余额可在结算时使用。' }}</p><button class="primary full" @click="extra='';go(extra==='topup'?'account':'shop')">继续逛逛</button></div></template>
      <p v-if="error" class="inline-error" role="alert">{{ error }}</p>
    </section></div>

  </div>
</template>

