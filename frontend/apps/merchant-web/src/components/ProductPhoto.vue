<script setup lang="ts">
import {ref} from 'vue'
import {api} from '@smart-merchant/api'
const props=defineProps<{modelValue:string}>()
const emit=defineEmits<{ 'update:modelValue':[value:string]; uploading:[value:boolean] }>()
const busy=ref(false),error=ref('')
async function upload(event:Event){const input=event.target as HTMLInputElement;const file=input.files?.[0];if(!file)return;error.value='';if(!['image/jpeg','image/png'].includes(file.type)||file.size>1024*1024){error.value='请选择 1 MB 以内的 JPEG 或 PNG 照片';input.value='';return}busy.value=true;emit('uploading',true);try{const body=new FormData();body.append('file',file);const result=await api<{url:string}>('/api/merchant/v1/product-photos',{method:'POST',body});emit('update:modelValue',result.url)}catch(e){error.value=(e as Error).message}finally{busy.value=false;emit('uploading',false);input.value=''}}
</script>
<template><div><img v-if="props.modelValue" :src="props.modelValue" alt="商品实物照片预览" style="display:block;width:140px;height:140px;object-fit:contain;margin-bottom:12px"/><label>{{busy?'正在上传…':'上传 / 更换实物照片'}}<input type="file" accept="image/jpeg,image/png" :disabled="busy" aria-label="上传商品实物照片" @change="upload"/></label><p>JPEG / PNG，最大 1 MB，最长边不超过 6000 像素。保存商品后在消费者商城展示。</p><p v-if="error" role="alert">{{error}}</p></div></template>
