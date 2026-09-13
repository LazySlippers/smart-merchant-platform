<script setup lang="ts">
import { computed, onMounted, onUnmounted, nextTick, ref, watch } from 'vue'
import Icon from './prototype/Icon.vue'
import GlobalAddressBook from './GlobalAddressBook.vue'
import { account, accounts, activateAccount, accountRequest, browseHistory, clearBrowseHistory, needsReverification, reverifyAccount, signOut, updateActiveAccount, type BrowseItem, type Membership } from './account'
import { money } from './domain'
defineProps<{memberships:(Membership&{label:string;storeId:string})[]}>()
const emit=defineEmits<{login:[];addAccount:[];openStore:[tenantId:string,storeId:string,page:string,skuId?:string];orders:[filter?:string];refreshMemberships:[]}>()
const panel=ref(''),error=ref(''),busy=ref(false),history=ref<BrowseItem[]>([]),search=ref('')
const switchPassword=ref(''),switchMobile=ref(''),newName=ref(''),currentPassword=ref(''),newPassword=ref('')
const historyPage=ref(0),historyMore=ref(false),historyDays=ref(0),historyEnabled=ref(true),notice=ref('')
const dialog=ref<HTMLElement|null>(null)
let generation=0,returnFocus:HTMLElement|null=null
watch(()=>account.value?.accessToken,()=>{generation++;confirmClear.value=false;history.value=[];switchPassword.value='';currentPassword.value='';newPassword.value='';search.value='';historyPage.value=0;historyMore.value=false;historyDays.value=0;historyEnabled.value=true;newName.value='';switchMobile.value='';notice.value='';panel.value='';error.value='';busy.value=false})
watch(panel,async value=>{document.body.style.overflow=value?'hidden':'';if(value){returnFocus=document.activeElement as HTMLElement;await nextTick();dialog.value?.focus()}else returnFocus?.focus()})
function onKey(event:KeyboardEvent){if(!panel.value)return;if(event.key==='Escape'){event.preventDefault();close()}if(event.key==='Tab'){const nodes=dialog.value?.querySelectorAll<HTMLElement>('button:not(:disabled), input:not(:disabled), select, textarea, summary');if(!nodes?.length)return;const first=nodes[0],last=nodes[nodes.length-1];if(event.shiftKey&&(document.activeElement===first||document.activeElement===dialog.value)){event.preventDefault();last.focus()}else if(!event.shiftKey&&document.activeElement===last){event.preventDefault();first.focus()}}}
onMounted(()=>window.addEventListener('keydown',onKey))
onUnmounted(()=>{window.removeEventListener('keydown',onKey);document.body.style.overflow=''})
const initials=computed(()=>account.value?.displayName.slice(0,1)||'邻')
const masked=computed(()=>account.value?.mobile.replace(/(\d{3})\d{4}(\d{4})/,'$1****$2')||'登录后管理账户')
async function run(action:()=>Promise<void>){if(busy.value)return;const serial=generation;busy.value=true;error.value='';try{await action()}catch(e){if(serial===generation)error.value=(e as Error).message}finally{if(serial===generation)busy.value=false}}
async function openPanel(next:string){if(!account.value&&next!=='accounts'){emit('login');return}panel.value=next;error.value='';if(next==='history')await loadHistory();if(next==='profile')newName.value=account.value?.displayName||'';if(next==='privacy')await run(async()=>{historyEnabled.value=(await accountRequest<{historyEnabled:boolean}>('/privacy')).historyEnabled})}
async function loadHistory(append=false){await run(async()=>{const next=append?historyPage.value+1:0;const rows=await browseHistory(search.value,next,historyDays.value);history.value=append?[...history.value,...rows]:rows;historyPage.value=next;historyMore.value=rows.length===30})}
async function updatePrivacy(){await run(async()=>{await accountRequest('/privacy',{historyEnabled:historyEnabled.value});notice.value='隐私设置已保存'})}
async function chooseAccount(mobile:string){if(account.value?.mobile===mobile){close();return}await run(async()=>{if(await activateAccount(mobile)){panel.value='';emit('refreshMemberships')}else{switchMobile.value=mobile;switchPassword.value='';panel.value='verify'}})}
async function verifySwitch(){await run(async()=>{await reverifyAccount(switchMobile.value,switchPassword.value);switchPassword.value='';panel.value='';emit('refreshMemberships')})}
async function updateProfile(){await run(async()=>{await accountRequest('/me',{displayName:newName.value});updateActiveAccount(newName.value.trim());panel.value=''})}
async function changePassword(){await run(async()=>{await accountRequest('/password',{currentPassword:currentPassword.value,newPassword:newPassword.value});currentPassword.value='';newPassword.value='';signOut();panel.value='';notice.value='密码已更新，请使用新密码重新登录';emit('login')})}
const confirmClear=ref(false)
async function clearHistory(){confirmClear.value=false;await run(async()=>{await clearBrowseHistory();history.value=[];historyMore.value=false;notice.value='浏览足迹已清空'})}
function close(){confirmClear.value=false;panel.value='';error.value='';switchPassword.value='';currentPassword.value='';newPassword.value=''}
onMounted(()=>{if(account.value)emit('refreshMemberships')})
</script>
<template>
  <section class="account-center"><div class="account-brand-line">邻里百货 <span>我的</span></div>
    <div class="account-hero">
      <button class="avatar-button" aria-label="管理登录账号" @click="openPanel('accounts')"><span>{{initials}}</span><i v-if="account" aria-label="已登录">●</i></button>
      <div class="account-identity"><h1>{{account?.displayName||'登录邻里百货'}}</h1><p>{{masked}}</p><button v-if="account" @click="openPanel('accounts')">切换账号 <span aria-hidden="true">⌄</span></button></div>
      <button class="hero-settings" aria-label="账户设置" @click="openPanel('settings')"><Icon name="settings"/></button>
    </div>
    <button v-if="!account" class="account-login" @click="emit('login')">登录 / 注册</button>

    <p v-if="notice" class="account-notice" role="status">{{notice}}</p><div class="account-order-card">
      <button class="account-section-title" @click="emit('orders')"><b>我的订单</b><span>全部订单 ›</span></button>
      <div class="order-shortcuts"><button v-for="item in [{icon:'wallet',label:'待付款',filter:'PAYMENT'},{icon:'bag',label:'待收货',filter:'RECEIVING'},{icon:'check',label:'已完成',filter:'COMPLETED'},{icon:'help',label:'已退款',filter:'REFUND'}]" :key="item.label" @click="emit('orders',item.filter)"><Icon :name="item.icon"/><span>{{item.label}}</span></button></div>
    </div>

    <div class="account-menu-card">
      <button @click="openPanel('addresses')"><Icon name="pin"/><span><b>收货地址</b><small>全平台商家通用</small></span><i>›</i></button>
      <button @click="openPanel('history')"><Icon name="clock"/><span><b>浏览足迹</b><small>最近看过的商家和商品</small></span><i>›</i></button>
      <button @click="openPanel('memberships')"><Icon name="gift"/><span><b>商家会员</b><small>{{memberships.length?`已关联 ${memberships.length} 家`:'积分、余额与优惠券'}}</small></span><i>›</i></button>
      <button @click="openPanel('settings')"><Icon name="settings"/><span><b>账号与安全</b><small>个人资料、密码和登录账号</small></span><i>›</i></button>
    </div>

    <div class="account-menu-card minor"><button @click="openPanel('service')"><Icon name="help"/><span><b>帮助与客服</b><small>订单问题与使用帮助</small></span><i>›</i></button></div>
  </section>

  <div v-if="panel" class="market-overlay" @click.self="close"><section ref="dialog" tabindex="-1" class="market-location-sheet account-sheet" role="dialog" aria-modal="true" :aria-label="panel">
    <div class="account-sheet-head"><button aria-label="返回" @click="close"><Icon name="back"/></button><h2>{{({accounts:'切换账号',verify:'验证身份',addresses:'收货地址',history:'浏览足迹',memberships:'商家会员',settings:'账号与安全',profile:'个人资料',password:'修改密码',service:'帮助与客服',privacy:'隐私与数据'} as Record<string,string>)[panel]}}</h2><span/></div>
    <p v-if="error" class="market-error" role="alert">{{error}}</p><p v-if="busy" class="sheet-note" role="status">正在处理…</p>
    <template v-if="panel==='accounts'">
      <p class="sheet-note">最多保留 8 个账号。超过 30 分钟未使用，再次切换需要验证密码。</p>
      <button v-for="item in accounts" :key="item.mobile" class="account-row" @click="chooseAccount(item.mobile)"><span class="mini-avatar">{{item.displayName.slice(0,1)}}</span><span><b>{{item.displayName}}</b><small>{{item.mobile.replace(/(\d{3})\d{4}(\d{4})/,'$1****$2')}}{{needsReverification(item)?' · 需验证':''}}</small></span><i>{{account?.mobile===item.mobile?'当前':'切换'}}</i></button>
      <button class="outline-wide" @click="close();emit('addAccount')">＋ 添加账号</button><button v-if="account" class="text-danger" @click="signOut(true);close()">从本机移除当前账号</button>
    </template>
    <form v-else-if="panel==='verify'" @submit.prevent="verifySwitch"><p class="sheet-note">为了保护账号数据，请验证 {{switchMobile.replace(/(\d{3})\d{4}(\d{4})/,'$1****$2')}} 的密码。</p><label>账号密码<input v-model="switchPassword" type="password" autocomplete="current-password" required></label><button class="account-submit" :disabled="busy">验证并切换</button></form>
    <GlobalAddressBook v-else-if="panel==='addresses'" :key="account?.mobile"/>
    <template v-else-if="panel==='history'"><form class="history-search" @submit.prevent="loadHistory()"><Icon name="search"/><input v-model="search" placeholder="搜索商家或商品" aria-label="搜索浏览足迹"><button>搜索</button></form><div class="history-tools"><select v-model="historyDays" aria-label="足迹时间范围" @change="loadHistory()"><option :value="0">全部足迹</option><option :value="7">近 7 天</option><option :value="30">近 30 天</option><option :value="90">近 90 天</option></select><span>最多 500 条</span><button v-if="history.length" @click="confirmClear=true">清空</button></div><div v-if="confirmClear" class="clear-history-confirm"><p>清空当前账号的所有浏览足迹？此操作无法恢复。</p><button @click="confirmClear=false">取消</button><button :disabled="busy" @click="clearHistory">确认清空</button></div><div v-if="!history.length" class="account-empty"><Icon name="clock" :size="42"/><b>暂无浏览足迹</b><p>查看商品详情后会记录在这里。</p></div><button v-for="item in history" :key="`${item.tenantId}:${item.storeId}:${item.skuId}`" class="history-row" @click="emit('openStore',item.tenantId,item.storeId,'shop',item.skuId);close()"><span class="history-image"><img v-if="item.imageUrl" :src="item.imageUrl" alt=""><Icon v-else name="bag"/></span><span><b>{{item.productName}}</b><small>{{item.merchantName}} · {{item.storeName}}</small><em>浏览时 ¥{{money(item.priceCents)}} · {{item.viewedAt.replace('T',' ').slice(0,16)}}</em></span><i>›</i></button><button v-if="historyMore" class="outline-wide" :disabled="busy" @click="loadHistory(true)">加载更多足迹</button></template>
    <template v-else-if="panel==='memberships'"><p class="sheet-note">各商家的积分、余额和优惠券互不共享。</p><div v-if="!memberships.length" class="account-empty">暂未加入商家会员；进入店铺后可自主选择是否加入。</div><button v-for="item in memberships" :key="item.tenantId" class="account-row" @click="emit('openStore',item.tenantId,item.storeId,'account');close()"><span class="mini-avatar"><Icon name="home"/></span><span><b>{{item.label}}</b><small>{{item.memberName}} · {{item.status==='ACTIVE'?'正常':'已停用'}}</small></span><i>›</i></button></template>
    <template v-else-if="panel==='settings'"><button class="settings-row" @click="openPanel('profile')"><span>个人资料<small>{{account?.displayName}}</small></span><i>›</i></button><button class="settings-row" @click="openPanel('password')"><span>登录密码<small>定期更换密码保护账号</small></span><i>›</i></button><button class="settings-row" @click="openPanel('accounts')"><span>登录账号管理<small>{{accounts.length}} 个账号保存在本机</small></span><i>›</i></button><button class="settings-row" @click="openPanel('privacy')"><span>隐私与数据<small>地址、足迹按账号隔离</small></span><i>›</i></button><button class="text-danger" @click="signOut(false);close()">退出当前账号</button></template>
    <form v-else-if="panel==='profile'" class="account-form" @submit.prevent="updateProfile"><label>昵称<input v-model="newName" maxlength="40" required></label><label>登录手机号<input :value="account?.mobile" disabled></label><button class="account-submit" :disabled="busy">保存资料</button></form>
    <form v-else-if="panel==='password'" class="account-form" @submit.prevent="changePassword"><label>当前密码<input v-model="currentPassword" type="password" autocomplete="current-password" required></label><label>新密码<input v-model="newPassword" type="password" autocomplete="new-password" minlength="10" maxlength="64" required></label><p class="sheet-note">新密码至少 10 位。修改后所有设备需要使用新密码重新登录。</p><button class="account-submit" :disabled="busy">确认修改</button></form>
    <template v-else-if="panel==='privacy'"><p class="sheet-note">收货地址和浏览足迹仅属于当前账号。商家仅获得订单所需的收件信息。</p><label class="check-label"><input v-model="historyEnabled" type="checkbox" @change="updatePrivacy">记录我的商品浏览足迹</label><p class="sheet-note">关闭后停止新增记录，已有足迹可在浏览足迹中查看或清空。</p><button class="outline-wide" @click="openPanel('history')">管理浏览足迹</button></template>
    <template v-else-if="panel==='service'"><details open><summary>地址为什么所有商家通用？</summary><p>地址归邻里百货账号管理。结算时选择地址，商家只会收到当前订单的收件信息。</p></details><details><summary>商家会员数据会合并吗？</summary><p>不会。积分、余额、优惠券和订单一直归对应商家。</p></details><details><summary>如何保护多账号？</summary><p>账号切换超过 30 分钟未使用时会要求重新输入该账号密码。</p></details></template>
  </section></div>
</template>
