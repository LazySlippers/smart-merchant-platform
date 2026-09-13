<script setup lang="ts">
import {computed,ref,watch} from 'vue'
import type {StockAlert} from '../useStockAlerts'
const props=defineProps<{alerts:StockAlert[];error:string;updated:string;tenant?:boolean;scopeKey:string}>()
defineEmits<{inventory:[];refresh:[]}>()
const opened=ref(false)
const acknowledged=ref<string[]>([])
const storageKey=computed(()=>`saas.stock-alert-ack:${props.scopeKey}`)
const alertKey=(alert:StockAlert)=>`${alert.storeId}:${alert.skuId}`
const showNotice=computed(()=>props.alerts.some(alert=>!acknowledged.value.includes(alertKey(alert))))
function persist(){try{localStorage.setItem(storageKey.value,JSON.stringify(acknowledged.value))}catch{/* Keep acknowledgement in memory if storage is unavailable. */}}
watch(storageKey,()=>{try{const saved=JSON.parse(localStorage.getItem(storageKey.value)||'[]');acknowledged.value=Array.isArray(saved)?saved.filter((v:unknown)=>typeof v==='string'):[]}catch{acknowledged.value=[]}},{immediate:true})
watch(()=>props.alerts,()=>{
 if(!props.updated||props.error)return
 const active=new Set(props.alerts.map(alertKey))
 acknowledged.value=acknowledged.value.filter(key=>active.has(key))
 persist()
})
function acknowledge(){acknowledged.value=props.alerts.map(alertKey);persist()}
</script>
<template>
 <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon/>
 <el-alert v-if="showNotice" type="warning" :closable="false" show-icon style="margin-bottom:16px">
  <template #title>{{tenant?'旗下门店':'本店'}}有 {{alerts.length}} 项低库存或缺货商品</template>
  <p>可用库存已达到预警线，租户总部与对应门店同步可见。{{updated?'最近更新 '+updated:''}}</p>
  <el-button size="small" @click="opened=true">查看预警明细</el-button><el-button size="small" type="primary" @click="$emit('inventory')">{{tenant?'查看库存 / 分配补货':'查看本店库存'}}</el-button><el-button size="small" @click="$emit('refresh')">刷新</el-button><el-button size="small" @click="acknowledge">我已知晓</el-button>
 </el-alert>
 <el-drawer v-model="opened" title="低库存提醒" size="min(780px,100vw)"><el-table :data="alerts" empty-text="库存已补足，暂无低库存提醒"><el-table-column v-if="tenant" prop="storeName" label="门店"/><el-table-column prop="productName" label="商品"/><el-table-column prop="skuName" label="规格"/><el-table-column prop="skuCode" label="SKU"/><el-table-column prop="availableQuantity" label="可用库存"/><el-table-column prop="lowStockThreshold" label="预警线"/></el-table></el-drawer>
</template>
