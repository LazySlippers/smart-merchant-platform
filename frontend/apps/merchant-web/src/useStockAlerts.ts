import {onMounted,onUnmounted,ref,watch} from 'vue'
import {api} from '@smart-merchant/api'
export type StockAlert={storeId:number;storeName:string;skuId:number;skuCode:string;skuName:string;productName:string;availableQuantity:number;lowStockThreshold:number}
export function useStockAlerts(enabled:()=>boolean){
 const alerts=ref<StockAlert[]>([]),alertError=ref(''),alertUpdated=ref('')
 let timer:ReturnType<typeof setInterval>|undefined,serial=0,busy=false
 async function refreshAlerts(){
  if(!enabled()||busy)return
  const id=serial;busy=true
  try{const rows=await api<StockAlert[]>('/api/merchant/v1/inventory/low-stock-alerts');if(id===serial&&enabled()){alerts.value=rows;alertError.value='';alertUpdated.value=new Date().toLocaleTimeString('zh-CN',{hour12:false})}}
  catch(e){if(id===serial&&enabled())alertError.value='库存预警更新失败：'+(e as Error).message}
  finally{busy=false}
 }
 watch(enabled,active=>{serial++;alerts.value=[];alertError.value='';alertUpdated.value='';if(active)void refreshAlerts()},{immediate:true})
 onMounted(()=>{timer=setInterval(()=>{if(document.visibilityState==='visible')void refreshAlerts()},15000)})
 onUnmounted(()=>{serial++;if(timer)clearInterval(timer)})
 return {alerts,alertError,alertUpdated,refreshAlerts}
}
