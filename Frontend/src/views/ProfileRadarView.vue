<script setup>
import { computed, onMounted, ref } from 'vue'
import AppTabBar from '../components/AppTabBar.vue'
import { getProfileRadar } from '../api/aiGeneration'
const loading=ref(true),error=ref(''),profile=ref({})
const dimensions=computed(()=>profile.value.dimensions||profile.value.items||[])
const points=computed(()=>{const n=Math.max(dimensions.value.length,3);return dimensions.value.map((item,index)=>{const angle=-Math.PI/2+index*Math.PI*2/n;const score=Math.max(0,Math.min(100,Number(item.score||item.value||0)))/100;return `${150+Math.cos(angle)*110*score},${150+Math.sin(angle)*110*score}`}).join(' ')})
onMounted(async()=>{try{profile.value=await getProfileRadar()||{}}catch(cause){error.value=cause.message}finally{loading.value=false}})
</script>
<template><div class="feature-page"><AppTabBar/><main class="feature-container"><header class="feature-heading"><div><h1>个人画像</h1><p>基于可验证的学习与使用证据生成</p></div></header><div v-if="error" class="feature-error">{{error}}</div><div v-if="loading" class="feature-empty">正在加载个人画像…</div><div v-else class="feature-grid"><section class="feature-card radar"><svg viewBox="0 0 300 300"><circle v-for="r in [35,70,105]" :key="r" cx="150" cy="150" :r="r"/><line v-for="(_,i) in dimensions" :key="i" x1="150" y1="150" :x2="150+Math.cos(-Math.PI/2+i*Math.PI*2/dimensions.length)*110" :y2="150+Math.sin(-Math.PI/2+i*Math.PI*2/dimensions.length)*110"/><polygon :points="points"/></svg></section><aside class="feature-card feature-section"><div class="feature-section__head"><h2>画像维度</h2></div><div v-if="!dimensions.length" class="feature-empty">暂无画像证据</div><div class="feature-list"><div v-for="item in dimensions" :key="item.key||item.name" class="feature-row"><div class="feature-row__copy"><strong>{{item.label||item.name||item.key}}</strong><span>{{item.summary||item.description}}</span></div><b>{{Math.round(item.score||item.value||0)}}</b></div></div></aside></div></main></div></template>
<style scoped>
.radar{display:grid;place-items:center;min-height:520px}.radar svg{width:min(450px,90%)}.radar circle,.radar line{fill:none;stroke:#dce4eb;stroke-width:1}.radar polygon{fill:rgba(82,119,151,.18);stroke:#527797;stroke-width:2}
</style>
