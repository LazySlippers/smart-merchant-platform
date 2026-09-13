<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { account, accountAddresses, saveAccountAddress, deleteAccountAddress, type GlobalAddress } from './account'
const props=defineProps<{selectable?:boolean}>()
const emit=defineEmits<{select:[address:GlobalAddress];change:[addresses:GlobalAddress[]]}>()
const rows=ref<GlobalAddress[]>([]),draft=ref<GlobalAddress|null>(null),error=ref(''),busy=ref(false)
const pendingDelete=ref('')
const owner=account.value?.accessToken
async function run(action:()=>Promise<GlobalAddress[]>){if(busy.value)return;busy.value=true;error.value='';try{const result=await action();if(owner!==account.value?.accessToken)return;rows.value=result;draft.value=null;emit('change',result)}catch(e){if(owner===account.value?.accessToken)error.value=(e as Error).message}finally{busy.value=false}}
function edit(value?:GlobalAddress){draft.value=value?{...value}:{name:account.value?.displayName||'',phone:account.value?.mobile||'',province:'',city:'',district:'',detail:'',label:'家',isDefault:!rows.value.length}}
onMounted(()=>run(accountAddresses))
</script>
<template>
  <p v-if="error" class="market-error" role="alert">{{error}}</p>
  <p v-if="busy" class="sheet-note" role="status">正在加载地址…</p>
  <form v-if="draft" class="account-form" @submit.prevent="run(()=>saveAccountAddress(draft!))">
    <label>收货人<input v-model="draft.name" maxlength="40" required autocomplete="name"></label>
    <label>手机号<input v-model="draft.phone" type="tel" pattern="1[3-9][0-9]{9}" maxlength="11" required autocomplete="tel"></label>
    <div class="address-grid"><label>省份<input v-model="draft.province" maxlength="64" placeholder="如浙江省" required></label><label>城市<input v-model="draft.city" maxlength="64" required></label><label>区县<input v-model="draft.district" maxlength="64" required></label></div>
    <label>详细地址<textarea v-model="draft.detail" minlength="3" maxlength="240" required placeholder="街道、小区、楼栋和门牌号"/></label>
    <label>标签<select v-model="draft.label"><option>家</option><option>公司</option><option>学校</option><option>其他</option></select></label>
    <label class="check-label"><input v-model="draft.isDefault" type="checkbox">设为默认地址</label>
    <p class="sheet-note">保存至当前账号，所有商家结算时均可使用。</p>
    <button class="account-submit" :disabled="busy">保存地址</button><button type="button" class="outline-wide" @click="draft=null">取消</button>
  </form>
  <template v-else>
    <div v-if="!busy&&!rows.length" class="account-empty"><b>还没有收货地址</b><p>新增一次，在所有商家结算时直接使用。</p><button v-if="error" @click="run(accountAddresses)">重新加载</button></div>
    <article v-for="item in rows" :key="item.id" class="address-card"><div><b>{{item.name}} · {{item.phone}}</b><span v-if="item.isDefault">默认</span></div><p>{{item.province}}{{item.city}}{{item.district}}{{item.detail}}</p><small>{{item.label}}</small><footer><button v-if="props.selectable" @click="emit('select',item)">使用此地址</button><button :disabled="busy" @click="edit(item)">编辑</button><template v-if="pendingDelete===item.id"><button @click="pendingDelete=''">取消删除</button><button :disabled="busy" @click="run(()=>deleteAccountAddress(item.id!))">确认删除</button></template><button v-else :disabled="busy" @click="pendingDelete=item.id!">删除</button></footer></article>
    <button class="account-submit sticky-action" :disabled="busy" @click="edit()">＋ 新增收货地址</button>
  </template>
</template>
