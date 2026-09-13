<script setup lang="ts">
import { onMounted, onBeforeUnmount, ref, watch } from 'vue'
import * as echarts from 'echarts/core'
import { LineChart, BarChart, PieChart } from 'echarts/charts'
import { GridComponent, TooltipComponent, LegendComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
echarts.use([LineChart, BarChart, PieChart, GridComponent, TooltipComponent, LegendComponent, CanvasRenderer])
const props = defineProps<{ option: any; label: string; height?: number }>()
const host = ref<HTMLElement>()
let chart: echarts.ECharts | undefined
let observer: ResizeObserver | undefined
function render() { chart?.setOption({ color: ['#3478f6', '#23b7ac', '#f3ad43', '#96a3bc', '#e57483'], textStyle: { fontFamily: 'Microsoft YaHei, sans-serif', color: '#7b879b' }, ...props.option }, true) }
onMounted(() => { chart = echarts.init(host.value!); render(); observer = new ResizeObserver(() => chart?.resize()); observer.observe(host.value!) })
watch(() => props.option, render, { deep: true })
onBeforeUnmount(() => { observer?.disconnect(); chart?.dispose() })
</script>
<template><div ref="host" role="img" :aria-label="label" :style="{ height: `${height ?? 300}px`, width: '100%' }" /></template>
