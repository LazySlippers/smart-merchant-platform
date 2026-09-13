<script setup lang="ts">
import { ref, onMounted, onBeforeUnmount } from 'vue'
import { api } from '@smart-merchant/api'
import { ElMessage } from 'element-plus'
const props=defineProps<{storeId:number}>()
const password=ref(''),busy=ref(false)
let generation=0,timer:ReturnType<typeof setTimeout>|undefined
function hide(){generation++;password.value='';clearTimeout(timer)}
async function toggle(){if(password.value){hide();return}const request=++generation;busy.value=true;try{const result=await api<{available:boolean;password?:string;message?:string}>(`/api/merchant/v1/stores/${props.storeId}/account/reveal`,{method:'POST',cache:'no-store'});if(request!==generation)return;if(!result.available){ElMessage.info(result.message??'请先重新设置一次密码');return}password.value=result.password??'';timer=setTimeout(hide,30000)}catch(e){if(request===generation)ElMessage.error((e as Error).message)}finally{busy.value=false}}
onMounted(()=>{window.addEventListener('blur',hide);document.addEventListener('visibilitychange',hide)})
onBeforeUnmount(()=>{hide();window.removeEventListener('blur',hide);document.removeEventListener('visibilitychange',hide)})
</script>
<template><span style="display:flex;align-items:center;gap:8px"><span style="overflow-wrap:anywhere">{{password||'••••••••'}}</span><el-button link :loading="busy" :disabled="busy" :aria-label="password?'隐藏密码':'查看密码'" :title="password?'隐藏密码':'查看密码（30秒后自动隐藏）'" @click="toggle"><svg v-if="!busy" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.7" aria-hidden="true"><path d="M2 12s3.5-7 10-7 10 7 10 7-3.5 7-10 7S2 12 2 12Z"/><circle cx="12" cy="12" r="3"/><path v-if="password" d="m3 3 18 18"/></svg></el-button></span></template>
