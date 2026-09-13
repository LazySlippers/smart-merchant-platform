<script setup lang="ts">
import {ref,watch,computed,reactive} from 'vue'
import {api} from '@smart-merchant/api'
import {ElMessage,ElMessageBox} from 'element-plus'
const props=defineProps<{store:{id:number;storeName:string;storeCode?:string}|null}>()
const emit=defineEmits<{close:[];saved:[]}>()
type Account={userId:number;username:string;displayName:string;permissions:string[]}
const accounts=ref<Account[]>([]),selected=ref<number>(),checked=ref<string[]>([]),loading=ref(false),saving=ref(false),error=ref('')
const form=reactive({username:'',displayName:'',password:''})
const creating=computed(()=>!loading.value&&!error.value&&!accounts.value.length)
const prefix='merchant:'
const options=[
 {code:'product:view',title:'商品查看',description:'查看本店商品、规格与价格'},
 {code:'inventory:view',title:'库存查询',description:'查看本店库存、库存流水和盘点单'},
 {code:'inventory:manage',title:'库存调整与盘点',description:'调整数量、创建并完成盘点'},
 {code:'transfer:manage',title:'调拨收发',description:'查询同租户库存、自主申请、确认出库及收货'},
 {code:'order:view',title:'销售订单',description:'查看本店订单与销售统计'},
 {code:'order:verify',title:'自提核销',description:'核销顾客在本店的自提订单'},
 {code:'finance:view',title:'财务流水',description:'查看、筛选并导出本店收支明细'}
]
const current=computed(()=>accounts.value.find(a=>a.userId===selected.value))
function selectAccount(){checked.value=current.value?.permissions.filter(p=>options.some(o=>prefix+o.code===p))??[]}
function preset(full:boolean){checked.value=full?options.map(o=>prefix+o.code):[prefix+'order:view',prefix+'order:verify']}
function normalize(){const codes=new Set(checked.value);if(codes.has(prefix+'inventory:manage')||codes.has(prefix+'transfer:manage'))codes.add(prefix+'inventory:view');if(codes.has(prefix+'inventory:view'))codes.add(prefix+'product:view');if(codes.has(prefix+'order:verify'))codes.add(prefix+'order:view');return ['merchant:store:view',...codes]}
watch(()=>props.store,async store=>{accounts.value=[];selected.value=undefined;checked.value=[];error.value='';form.password='';if(!store)return;form.username=`store-${store.id}-admin`;form.displayName=`${store.storeName}管理员`;loading.value=true;try{accounts.value=await api(`/api/merchant/v1/stores/${store.id}/access-accounts`);selected.value=accounts.value[0]?.userId;selectAccount();if(!accounts.value.length)preset(true)}catch(e){error.value=(e as Error).message}finally{loading.value=false}},{immediate:true})
async function save(){
 if(!props.store||loading.value||error.value)return;
 const isCreate=creating.value;
 if(isCreate&&(!form.username.trim()||!form.displayName.trim()||form.password.length<8)){ElMessage.warning('请填写登录账号、账号名称和至少 8 位密码');return}
 if(!isCreate&&!current.value)return;
 try{
  if(!isCreate)await ElMessageBox.confirm(`将更新「${current.value!.displayName}（${current.value!.username}）」的门店功能权限，仅可访问 ${props.store.storeName}。其他账号不受影响。`,'确认账号授权',{confirmButtonText:'保存授权',cancelButtonText:'取消'});
  saving.value=true;const permissions=normalize();
  if(isCreate){await api(`/api/merchant/v1/stores/${props.store.id}/account`,{method:'POST',body:JSON.stringify({username:form.username.trim(),displayName:form.displayName.trim(),password:form.password,permissions})});form.password='';ElMessage.success('门店账号已创建、绑定并授权，可使用刚设置的账号密码登录门店端')}
  else{await api(`/api/merchant/v1/stores/${props.store.id}/access-accounts/${current.value!.userId}/permissions`,{method:'PUT',body:JSON.stringify({permissions})});ElMessage.success('已保存，请门店端点击「同步权限」或重新登录')}
  emit('saved');emit('close');
 }catch(e){if(e!=='cancel'&&e!=='close')ElMessage.error((e as Error).message)}finally{saving.value=false}
}
</script>
<template>
 <el-drawer :model-value="Boolean(store)" :title="`${store?.storeName??''} · 账号权限`" size="min(560px, 100vw)" @close="emit('close')">
  <div v-loading="loading">
   <p class="drawer-tip">统一管理本店独立账号与已有店员账号。授权始终限制在本门店，无法查看其他门店。</p>
   <el-alert v-if="error" :title="error" type="error" :closable="false"/>
   <template v-if="creating">
    <el-alert title="门店档案已创建，请在这里设置登录账号和权限，即可启用门店 ERP。" type="info" :closable="false"/>
    <el-form label-position="top" style="margin-top:18px" :disabled="saving"><el-form-item label="门店登录账号" required><el-input v-model="form.username" autocomplete="off"/></el-form-item><el-form-item label="账号名称" required><el-input v-model="form.displayName"/></el-form-item><el-form-item label="登录密码" required><el-input v-model="form.password" type="password" show-password autocomplete="new-password" placeholder="至少 8 位，由门店负责人使用"/></el-form-item></el-form>
   </template>
   <template v-if="!loading&&!error">
    <el-form v-if="accounts.length" label-position="top"><el-form-item label="选择要授权的账号"><el-select v-model="selected" style="width:100%" :disabled="saving" @change="selectAccount"><el-option v-for="a in accounts" :key="a.userId" :value="a.userId" :label="`${a.displayName} · ${a.username}`"/></el-select></el-form-item></el-form>
    <div class="permission-presets"><el-button type="primary" plain :disabled="saving" @click="preset(true)">完整门店 ERP</el-button><el-button :disabled="saving" @click="preset(false)">仅订单与核销</el-button><el-button :disabled="saving" @click="checked=[]">仅经营入口</el-button></div>
    <el-alert title="门店查看为基础权限；调整、盘点和调拨自动包含库存查询，核销自动包含订单查看。" type="info" :closable="false"/>
    <el-checkbox-group v-model="checked" class="permission-list" :disabled="saving"><div v-for="o in options" :key="o.code" class="permission-item"><el-checkbox :value="prefix+o.code">{{o.title}}</el-checkbox><small>{{o.description}}</small></div></el-checkbox-group>
    <p class="drawer-tip">保存后按本页勾选项设置该账号，不修改共用角色或其他店员。门店端点击「同步权限」或重新登录后使用新权限。</p>

   </template>
  </div>
  <template #footer><el-button :disabled="saving" @click="emit('close')">取消</el-button><el-button type="primary" :disabled="(!current&&!creating)||loading||Boolean(error)" :loading="saving" @click="save">{{creating?'创建账号并授权':'保存授权'}}</el-button></template>
 </el-drawer>
</template>
<style scoped>
.permission-presets{display:flex;flex-wrap:wrap;gap:8px;margin-bottom:18px}.permission-presets .el-button{margin:0}.permission-list{display:grid;gap:10px;margin:18px 0}.permission-item{padding:9px 14px;background:#f6f8fc;border:1px solid #e7edf6;border-radius:8px;display:block}.permission-item small{display:block;color:#8390a5;margin:0 0 5px 24px;font-size:12px}
</style>

