<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import Shop from './App.vue'
import ConsumerAuth from './ConsumerAuth.vue'
import AccountCenter from './AccountCenter.vue'
import { account, accountStorageKey, accountRequest, enterTenant, type Membership } from './account'
import Icon from './prototype/Icon.vue'
import { request } from './api'
import { readSaved, save, money, statusNames, validId, type Order } from './domain'
import './marketplace.css'
type Store={id:string;tenantId:string;storeName:string;address:string;businessHours:string;city:string;district:string;coverUrl:string;pickupEnabled:boolean;shippingEnabled:boolean;distanceKm:number|null}
type Visit={tenantId:string;id:string;storeName:string;address:string}
const tab=ref('home'),active=ref<{tenantId:string;storeId:string;page:string;orderId?:string;skuId?:string}|null>(null)
const stores=ref<Store[]>([]),busy=ref(false),error=ref(''),search=ref(''),area=ref(readSaved<string>(accountStorageKey('area'),'')),locationOpen=ref(false),areaDraft=ref('')
const coordinates=ref(readSaved<{latitude:number;longitude:number}|null>(accountStorageKey('location'),null)),locating=ref(false),locationNote=ref(''),page=ref(0),more=ref(false)
const visits=ref<Visit[]>([]),orderRows=ref<(Order&{tenantId:string;storeName:string})[]>([]),orderErrors=ref<string[]>([])
const addAccount=ref(false),authOpen=ref(false),memberships=ref<(Membership & {label:string;storeId:string})[]>([]),ordersMore=ref(false)
const orderPages=new Map<string,number>(),finishedTenants=new Set<string>()
const orderFilter=ref('ALL'),orderSearch=ref('')
const filteredOrders=computed(()=>orderRows.value.filter(o=>(orderFilter.value==='ALL'||(orderFilter.value==='PAYMENT'?o.status==='PENDING_PAYMENT':orderFilter.value==='RECEIVING'?['PICKUP_READY','PENDING_SHIPMENT','SHIPPED'].includes(o.status):orderFilter.value==='REFUND'?o.status==='REFUNDED':o.status===orderFilter.value))&&[o.storeName,o.orderNo,...o.items.map(i=>i.productName)].join(' ').includes(orderSearch.value.trim())))
function showOrders(filter='ALL'){orderFilter.value=filter;void navigate('orders')}
function login(add=false){addAccount.value=add;authOpen.value=true}
let orderGeneration=0
const groups=computed(()=>visits.value.map(v=>{const cart=readSaved<Record<string,number>>(accountStorageKey(`cart:${v.tenantId}:${v.id}`),{});return {...v,count:Object.values(cart).filter(n=>Number.isSafeInteger(n)&&n>0).reduce((a,b)=>a+b,0)}}).filter(v=>v.count>0))
let generation=0
function refreshVisits(){visits.value=readSaved<Visit[]>(accountStorageKey('visits'),[])}
async function load(append=false){const serial=++generation;busy.value=true;error.value='';const next=append?page.value+1:0;try{const params=new URLSearchParams({page:String(next),search:search.value.trim(),area:area.value});if(coordinates.value){params.set('latitude',String(coordinates.value.latitude));params.set('longitude',String(coordinates.value.longitude))}const rows=await request<Store[]>(`/catalog/stores?${params}`);if(serial!==generation)return;stores.value=append?[...stores.value,...rows]:rows;page.value=next;more.value=rows.length===20}catch(e){if(serial===generation)error.value=(e as Error).message}finally{if(serial===generation)busy.value=false}}
function open(v:Visit|Store,next='shop',orderId?:string){active.value={tenantId:v.tenantId,storeId:v.id,page:next,orderId};window.scrollTo(0,0)}
async function navigate(next:string){active.value=null;tab.value=next;refreshVisits();history.replaceState(null,'',location.pathname);document.title='邻里百货 · 发现身边好店';window.scrollTo(0,0);if(next==='home')await load();if(next==='orders')await loadOrders();if(next==='account')await loadMemberships()}
async function loadMemberships() {
  if(!account.value){memberships.value=[];return}
  const owner=account.value.accessToken
  try {
    const rows=await accountRequest<Membership[]>('/memberships')
    const result=[]
    for(const m of rows){
      let label=`商家 ${m.tenantId}`,storeId=''
      try {
        const [brand,shops]=await Promise.all([request<{displayName:string}>(`/catalog/tenants/${m.tenantId}/brand`),request<Store[]>(`/catalog/tenants/${m.tenantId}/stores`)])
        label=brand.displayName;storeId=shops[0]?.id||''
      }catch { /* Closed tenants retain their membership identity. */ }
      result.push({...m,label,storeId})
    }
    if(owner===account.value?.accessToken)memberships.value=result
  }catch(e){if(owner===account.value?.accessToken)error.value=(e as Error).message}
}
async function loadOrders(append=false) {
  const serial=++orderGeneration,owner=account.value?.accessToken
  busy.value=true;error.value='';orderErrors.value=[]
  if(!append){orderRows.value=[];orderPages.clear();finishedTenants.clear();ordersMore.value=false}
  try {
    if(!owner)return
    if(!append)await loadMemberships()
    if(owner!==account.value?.accessToken||serial!==orderGeneration)return
    const membershipByTenant=new Map(memberships.value.map(m=>[m.tenantId,m]))
    const candidates=[...new Map(visits.value.map(v=>[v.tenantId,{tenantId:v.tenantId,label:v.storeName}])).values()]
    for(const m of memberships.value)if(!candidates.some(c=>c.tenantId===m.tenantId))candidates.push({tenantId:m.tenantId,label:m.label})
    const pending=candidates.filter(m=>!finishedTenants.has(m.tenantId))
    for(let offset=0;offset<pending.length;offset+=4){
      await Promise.all(pending.slice(offset,offset+4).map(async m=>{
        try {
          const token=(await enterTenant(m.tenantId)).accessToken
          const page=orderPages.get(m.tenantId)||0
          const rows=await request<Order[]>(`/orders?page=${page}`,token)
          if(owner!==account.value?.accessToken||serial!==orderGeneration)return
          const known=new Set(orderRows.value.map(o=>`${o.tenantId}:${o.id}`))
          orderRows.value.push(...rows.filter(o=>!known.has(`${m.tenantId}:${o.id}`)).map(o=>({...o,tenantId:m.tenantId,storeName:visits.value.find(v=>v.tenantId===m.tenantId&&v.id===o.storeId)?.storeName||membershipByTenant.get(m.tenantId)?.label||m.label})))
          orderPages.set(m.tenantId,page+1)
          if(rows.length<20)finishedTenants.add(m.tenantId)
        }catch(e){if(serial===orderGeneration&&owner===account.value?.accessToken)orderErrors.value.push(`${m.label}：${(e as Error).message}`)}
      }))
      if(owner!==account.value?.accessToken||serial!==orderGeneration)return
    }
    orderRows.value.sort((a,b)=>BigInt(a.id)>BigInt(b.id)?-1:BigInt(a.id)<BigInt(b.id)?1:a.tenantId.localeCompare(b.tenantId))
    ordersMore.value=candidates.some(m=>!finishedTenants.has(m.tenantId))
  }finally{if(serial===orderGeneration)busy.value=false}
}
async function authenticated(){authOpen.value=false;await loadMemberships();if(tab.value==='orders')await loadOrders()}
function openFromAccount(tenantId:string,storeId:string,page:string,skuId?:string){active.value={tenantId,storeId,page,skuId};window.scrollTo(0,0)}
watch(()=>account.value?.accessToken,()=>{orderGeneration++;orderRows.value=[];orderErrors.value=[];memberships.value=[];ordersMore.value=false;busy.value=false;error.value='';refreshVisits();area.value=readSaved(accountStorageKey('area'),'');coordinates.value=readSaved(accountStorageKey('location'),null);search.value='';orderSearch.value='';orderFilter.value='ALL'})
function locate(){locationNote.value='';if(!navigator.geolocation){locationNote.value='浏览器暂不支持定位，请手动选择地区';locationOpen.value=true;return}locating.value=true;navigator.geolocation.getCurrentPosition(p=>{coordinates.value={latitude:p.coords.latitude,longitude:p.coords.longitude};area.value='';save(accountStorageKey('location'),coordinates.value);save(accountStorageKey('area'),'');locating.value=false;locationOpen.value=false;void load()},()=>{locating.value=false;locationNote.value='未能获取位置，可输入城市或区县继续找店';locationOpen.value=true},{timeout:10000,maximumAge:300000})}
function selectArea(){area.value=areaDraft.value.trim();coordinates.value=null;save(accountStorageKey('area'),area.value);save(accountStorageKey('location'),null);locationOpen.value=false;void load()}
onMounted(()=>{refreshVisits();const entry=new URLSearchParams(location.search);const tenant=entry.get('tenantId')||'',store=entry.get('storeId')||'';if(validId(tenant)&&(!store||validId(store))){active.value={tenantId:tenant,storeId:store,page:'shop'};return}history.replaceState(null,'',location.pathname);document.title='邻里百货 · 发现身边好店';void load()})
</script>
<template>
  <Shop v-if="active" :key="`${active.tenantId}:${active.storeId}:${active.page}:${active.orderId||''}:${active.skuId||''}`" :entry-tenant="active.tenantId" :entry-store="active.storeId" :initial-page="active.page" :initial-order="active.orderId" :initial-sku="active.skuId" @platform="navigate"/>
  <div v-else class="marketplace">
    <header v-if="tab!=='account'" class="market-header"><div class="market-brand"><span>邻</span><b>邻里百货</b><small>好店好物，在你身边</small></div><button class="market-location" @click="areaDraft=area;locationOpen=true"><Icon name="pin" :size="16"/>{{area||(coordinates?'当前位置':'选择位置')}}<span>⌄</span></button></header>
    <main>
      <template v-if="tab==='home'">
        <form class="market-search" @submit.prevent="load()"><Icon name="search" :size="20"/><input v-model="search" aria-label="搜索门店" placeholder="搜索你想逛的超市、百货店"><button>搜索</button></form>
        <section class="market-intro"><span>每一天，都有好邻居</span><h1>附近好店，<br>日常好物。</h1><p>进店慢慢选 · 自提或快递到家</p><div class="market-intro-art" aria-hidden="true"><Icon name="bag" :size="80"/><span>DAILY<br>GOODS</span></div></section>
        <div class="market-services"><span><Icon name="home" :size="17"/>门店自提</span><span><Icon name="bag" :size="17"/>付邮费寄到家</span><span><Icon name="ticket" :size="17"/>商家会员优惠</span></div>
        <section class="market-list"><div class="market-heading"><div><h2>{{coordinates?'附近入驻门店':area?`${area}的门店`:'入驻门店'}}</h2><p>{{coordinates?'按直线距离排序 · 无坐标门店排在后面':'选择位置，发现身边的百货超市'}}</p></div><button :disabled="locating" @click="locate">{{locating?'定位中…':'定位找店'}}</button></div>
          <p v-if="error" class="market-error" role="alert">{{error}} <button @click="load()">重试</button></p><p v-if="busy&&!stores.length" class="market-empty">正在寻找好店…</p>
          <div v-if="!busy&&!error&&!stores.length" class="market-empty"><Icon name="home" :size="40"/><h3>这里还没有找到门店</h3><p>试试其他地区，或换个关键词。</p><button @click="search='';area='';coordinates=null;load()">查看全部入驻门店</button></div>
          <button v-for="s in stores" :key="`${s.tenantId}:${s.id}`" class="market-store" :data-tenant-id="s.tenantId" :data-store-id="s.id" @click="open(s)"><div class="market-store-cover"><img v-if="s.coverUrl" :src="s.coverUrl" alt="" @error="($event.target as HTMLImageElement).style.display='none'"><Icon v-else name="home" :size="38"/></div><div class="market-store-copy"><h3>{{s.storeName}}</h3><p class="market-distance">{{s.distanceKm==null?'':`直线 ${s.distanceKm<1?Math.round(s.distanceKm*1000)+' m':s.distanceKm.toFixed(1)+' km'} · `}}{{s.city}} {{s.district}}</p><p class="market-store-address">{{s.address||'地址待门店完善'}}</p><div class="market-tags"><span v-if="s.pickupEnabled">到店自提</span><span v-if="s.shippingEnabled">支持邮寄</span></div><small>{{s.businessHours?`营业时间 ${s.businessHours}`:'营业时间请咨询门店'}}</small></div><span class="market-arrow">›</span></button>
          <button v-if="more" class="market-more" :disabled="busy" @click="load(true)">{{busy?'加载中…':'更多门店'}}</button>
        </section>
      </template>
      <template v-else-if="tab==='cart'"><div class="market-heading"><div><h1>购物车</h1><p>按门店保存，分别结算与计算运费</p></div></div><div v-if="!groups.length" class="market-empty"><Icon name="bag" :size="42"/><h3>购物车还空着</h3><button @click="navigate('home')">去逛附近门店</button></div><button v-for="g in groups" :key="`${g.tenantId}:${g.id}`" class="market-visit" @click="open(g,'cart')"><Icon name="home"/><div><h3>{{g.storeName}}</h3><p>已选 {{g.count}} 件 · 进店核对价格与库存</p></div><span>去结算 ›</span></button></template>
      <template v-else-if="tab==='orders'"><div class="market-heading"><div><h1>我的订单</h1><p>汇总当前账号已关联商家的订单，按新到旧排列</p></div><button :disabled="busy" @click="loadOrders()">刷新</button></div><div class="aggregate-filters"><button v-for="f in [{id:'ALL',name:'全部'},{id:'PAYMENT',name:'待付款'},{id:'RECEIVING',name:'待收货'},{id:'COMPLETED',name:'已完成'},{id:'REFUND',name:'已退款'}]" :key="f.id" :class="{selected:orderFilter===f.id}" @click="orderFilter=f.id">{{f.name}}</button></div><input v-model="orderSearch" class="aggregate-search" aria-label="搜索订单" placeholder="搜索商家、商品或订单号"><p v-if="error" class="market-error" role="alert">{{error}}</p><p v-if="busy">正在加载订单…</p><p v-for="e in orderErrors" :key="e" class="market-error">{{e}}</p><div v-if="!busy&&!filteredOrders.length" class="market-empty">{{account?(orderFilter==='ALL'&&!orderSearch?'暂无订单，可去附近门店逛逛。':'已加载订单中没有匹配结果，可调整筛选或继续加载。'):'登录一次，查看各商家订单。'}}<button v-if="!account" @click="authOpen=true">登录 / 注册</button></div><button v-for="o in filteredOrders" :key="`${o.tenantId}:${o.id}`" class="market-visit" @click="open({tenantId:o.tenantId,id:o.storeId,storeName:o.storeName,address:''},'orders',o.id)"><div><h3>{{o.storeName}}</h3><p>{{o.items[0]?.productName}} 等 {{o.totalQuantity}} 件</p><small>{{o.orderNo}}</small></div><span>{{statusNames[o.status]||o.status}}<br>¥{{money(o.payableAmountCents)}}</span></button><button v-if="ordersMore" class="market-more" :disabled="busy" @click="loadOrders(true)">加载更多 / 重试失败商家</button></template>
      <AccountCenter v-else :memberships="memberships" @login="login()" @add-account="login(true)" @orders="showOrders" @open-store="openFromAccount" @refresh-memberships="loadMemberships"/>
    </main>
    <ConsumerAuth v-if="authOpen" :add-account="addAccount" @close="authOpen=false" @authenticated="authenticated"/>
    <nav class="market-nav" aria-label="平台导航"><button v-for="n in [{id:'home',icon:'home',label:'首页'},{id:'cart',icon:'bag',label:'购物车'},{id:'orders',icon:'order',label:'订单'},{id:'account',icon:'user',label:'我的'}]" :key="n.id" :class="{active:tab===n.id}" @click="navigate(n.id)"><Icon :name="n.icon" :size="23"/><span>{{n.label}}</span></button></nav>
    <div v-if="locationOpen" class="market-overlay" @click.self="locationOpen=false"><section class="market-location-sheet" role="dialog" aria-modal="true" aria-label="选择位置"><div class="market-heading"><h2>你想逛哪里？</h2><button aria-label="关闭位置选择" @click="locationOpen=false">×</button></div><button class="market-locate-button" :disabled="locating" @click="locate"><Icon name="pin"/>{{locating?'正在定位…':'使用当前位置'}}</button><p>{{locationNote||'允许定位后按距离找店，也可以手动选择地区。'}}</p><form @submit.prevent="selectArea"><label>城市或区县<input v-model="areaDraft" maxlength="80" placeholder="例如：杭州市、西湖区" aria-label="城市或区县"></label><button class="market-locate-button">确定地区</button></form></section></div>
  </div>
</template>



