<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { api } from '@smart-merchant/api'
import BusinessChart from './BusinessChart.vue'
import { type Store, type Order, type Dashboard, money, localDate, exportCsv } from '../model'
const props = defineProps<{ stores:Store[]; orders:Order[]; storeId:number; revision:number; permitted:boolean; lowStock:number }>()
const emit = defineEmits<{ orders:[]; stores:[] }>()
const today = new Date(), start = new Date(); start.setDate(today.getDate()-29)
const range = ref<[string,string]>([localDate(start),localDate(today)])
const period=ref('30'), data=ref<Dashboard|null>(null), busy=ref(false), error=ref('')
let request=0
async function refresh(){
  const id=++request; data.value=null; error.value=''
  if(!props.permitted){error.value='当前账号没有经营分析权限';return}
  if(!range.value?.[0]||!range.value?.[1])return
  if((new Date(range.value[1]).getTime()-new Date(range.value[0]).getTime())/86400000>365){error.value='单次查询请限定在 366 天内';busy.value=false;return}
  busy.value=true
  try{const result=await api<Dashboard>(`/api/merchant/v1/analytics/dashboard?from=${range.value[0]}&to=${range.value[1]}${props.storeId?`&storeId=${props.storeId}`:''}`);if(id===request)data.value=result}
  catch(e){if(id===request)error.value=(e as Error).message}
  finally{if(id===request)busy.value=false}
}
function setPeriod(value:string){period.value=value;const end=new Date(),from=new Date();if(value==='month')from.setDate(1);else from.setDate(end.getDate()-Number(value)+1);range.value=[localDate(from),localDate(end)]}
watch(()=>[props.storeId,props.revision,props.permitted,range.value],refresh,{immediate:true,deep:true})
const dates=computed(()=>{const result:string[]=[];if(!range.value)return result;const d=new Date(range.value[0]+'T00:00:00'),end=new Date(range.value[1]+'T00:00:00');while(d<=end&&result.length<366){result.push(localDate(d));d.setDate(d.getDate()+1)}return result})
const trend=computed(()=>({tooltip:{trigger:'axis'},legend:{bottom:0,itemWidth:16,itemHeight:8},grid:{left:54,right:44,top:30,bottom:60},xAxis:{type:'category',boundaryGap:false,data:dates.value.map(x=>x.slice(5)),axisLine:{lineStyle:{color:'#e9edf4'}},axisTick:{show:false}},yAxis:[{type:'value',name:'金额 / 元',splitLine:{lineStyle:{color:'#edf1f7',type:'dashed'}}},{type:'value',name:'订单 / 笔',minInterval:1,splitLine:{show:false}}],series:[{name:'净收入',type:'line',smooth:true,showSymbol:dates.value.length<10,data:dates.value.map(date=>(data.value?.trend.find(x=>x.date===date)?.netRevenueCents??0)/100),lineStyle:{width:3},areaStyle:{color:'#3478f61a'}},{name:'支付订单',type:'line',yAxisIndex:1,smooth:true,showSymbol:false,data:dates.value.map(date=>data.value?.trend.find(x=>x.date===date)?.orders??0),lineStyle:{width:2,type:'dashed'}}]}))
const scopedOrders=computed(()=>props.orders.filter(o=>(!props.storeId||o.storeId===props.storeId)&&o.paidAt&&o.paidAt.slice(0,10)>=range.value?.[0]&&o.paidAt.slice(0,10)<=range.value?.[1]))
const distribution=computed(()=>({tooltip:{trigger:'item',formatter:'{b}：{c} 笔 ({d}%)'},legend:{bottom:0},series:[{type:'pie',radius:['52%','72%'],center:['50%','43%'],label:{show:false},itemStyle:{borderColor:'#fff',borderWidth:4,borderRadius:5},data:[{name:'已完成',value:scopedOrders.value.filter(x=>x.status==='COMPLETED').length},{name:'待自提',value:scopedOrders.value.filter(x=>x.status==='PICKUP_READY'||x.status==='PAID').length},{name:'退款中',value:scopedOrders.value.filter(x=>x.status==='REFUNDING').length},{name:'已退款',value:scopedOrders.value.filter(x=>x.status==='REFUNDED').length}]}]}))
const ranking=computed(()=>({tooltip:{trigger:'axis',axisPointer:{type:'shadow'}},grid:{left:120,right:35,top:10,bottom:30},xAxis:{type:'value',splitLine:{lineStyle:{color:'#edf1f7',type:'dashed'}}},yAxis:{type:'category',inverse:true,data:data.value?.stores.slice(0,6).map(x=>props.stores.find(s=>s.id===x.storeId)?.storeName??`门店 ${x.storeId}`),axisLine:{show:false},axisTick:{show:false},axisLabel:{width:105,overflow:'truncate'}},series:[{type:'bar',barWidth:14,itemStyle:{borderRadius:[0,5,5,0]},data:data.value?.stores.slice(0,6).map(x=>x.netRevenueCents/100)}]}))
function download(){if(!data.value)return;exportCsv('经营日报.csv',[['日期','支付订单','净收入（元）'],...dates.value.map(date=>{const x=data.value!.trend.find(t=>t.date===date);return[date,x?.orders??0,((x?.netRevenueCents??0)/100).toFixed(2)]})])}
</script>
<template>
  <div class="page-heading"><div><div class="eyebrow">BUSINESS OVERVIEW</div><h1>经营总览 <span class="heading-badge">经营数据中心</span></h1><p>经营全貌，一屏掌握。关注收入变化，发现门店增长机会。</p></div><el-button :disabled="!data" @click="download">导出经营报表</el-button></div>
  <div class="filter-bar"><div class="segmented"><button v-for="item in [['1','今日'],['7','近7天'],['30','近30天'],['month','本月']]" :key="item[0]" :class="{selected:period===item[0]}" @click="setPeriod(item[0])">{{item[1]}}</button></div><el-date-picker v-model="range" type="daterange" value-format="YYYY-MM-DD" :clearable="false" start-placeholder="开始日期" end-placeholder="结束日期" range-separator="至" @change="period='custom'"/><el-button :loading="busy" @click="refresh">刷新</el-button></div>
  <el-alert v-if="error" :title="error" type="error" :closable="false" show-icon/>
  <div v-loading="busy">
    <section class="metric-grid">
      <article class="metric primary"><div>净收入 <span>¥</span></div><strong>{{data?money(data.summary.netRevenueCents):'—'}}</strong><p>实收金额扣除关联订单退款</p></article>
      <article class="metric"><div>实收金额 <span class="blue">↗</span></div><strong>{{data?money(data.summary.grossRevenueCents):'—'}}</strong><p>已支付订单累计收款</p></article>
      <article class="metric"><div>支付订单 <span class="teal">▤</span></div><strong>{{data?.summary.paidOrders??'—'}} <small>笔</small></strong><p>平均净客单价 {{data?money(data.summary.averageOrderCents):'—'}}</p></article>
      <article class="metric"><div>退款金额 <span class="amber">↶</span></div><strong>{{data?money(data.summary.refundCents):'—'}}</strong><p>按所选支付订单归集退款</p></article>
    </section>
    <section class="chart-grid"><article class="panel"><div class="panel-heading"><div><h2>营收与订单趋势</h2><p>每日净收入及支付订单变化</p></div><span class="subtle">{{range?.[0]}} — {{range?.[1]}}</span></div><BusinessChart v-if="data" :option="trend" label="所选日期每日净收入和支付订单曲线" :height="300"/><el-empty v-else description="暂无可用经营数据" :image-size="90"/><p v-if="data&&!data.trend.length" class="chart-note">当前时段暂无交易，曲线按零值展示</p></article><article class="panel"><div class="panel-heading"><div><h2>支付订单分布</h2><p>所选支付期间订单的当前状态</p></div></div><BusinessChart v-if="scopedOrders.length" :option="distribution" label="订单状态环形图" :height="300"/><el-empty v-else description="所选时段暂无支付订单" :image-size="90"/></article></section>
    <section class="insight-strip"><div><span class="blue">▦</span><p><b>{{stores.filter(s=>(!storeId||s.id===storeId)&&s.status==='ACTIVE').length}}</b><small>营业门店</small></p></div><div><span class="teal">♧</span><p><b>{{data?.members.repeatMembers??'—'}}</b><small>期间复购会员</small></p></div><div><span class="blue">◇</span><p><b>{{data?`${(data.members.couponRedemptionRateBasisPoints/100).toFixed(1)}%`:'—'}}</b><small>品牌券核销 / 领取事件比</small></p></div><button @click="emit('stores')"><span class="amber">!</span><p><b>{{lowStock}} 项</b><small>低库存预警 · 查看门店 →</small></p></button></section>
    <section class="chart-grid equal"><article class="panel"><div class="panel-heading"><div><h2>门店业绩排行</h2><p>按净收入排序 · 单位：元</p></div><el-button link type="primary" @click="emit('stores')">门店管理 →</el-button></div><BusinessChart v-if="data?.stores.length" :option="ranking" label="门店净收入排行条形图" :height="260"/><el-empty v-else description="暂无门店成交数据" :image-size="80"/></article><article class="panel"><div class="panel-heading"><div><h2>热销商品 TOP 5</h2><p>按支付订单商品金额排序，未扣商品退款</p></div><span class="heading-badge">销售榜</span></div><div v-for="(item,index) in data?.products.slice(0,5)" :key="item.productName+item.categoryName" class="rank-row"><span :class="['rank-number',{top:index<3}]">{{String(index+1).padStart(2,'0')}}</span><div><b>{{item.productName}}</b><small>{{item.categoryName}} · {{item.quantity}} 件</small></div><strong>{{money(item.amountCents)}}</strong></div><el-empty v-if="!data?.products.length" description="暂无商品成交数据" :image-size="80"/></article></section>
    <div class="panel footer-note"><span>统计口径：按支付日期筛选；净收入扣除这些订单的累计退款。领券与核销事件为品牌汇总，单店筛选时仍展示品牌券指标。</span><el-button link type="primary" @click="emit('orders')">查看订单明细 →</el-button></div>
  </div>
</template>
