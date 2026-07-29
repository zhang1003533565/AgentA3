<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import AppTabBar from '../components/AppTabBar.vue'
import { getMapPlaceList } from '../api/map'
import { getAnnouncement, getAnnouncements, getCanteenStalls, getDish, getDishes, getPromotion, getPromotions } from '../api/campusServices'

const route=useRoute()
const tab=ref(String(route.query.tab||'notices')),loading=ref(true),error=ref(''),items=ref([]),selected=ref(null)
const tabs=[['notices','通知公告'],['promotions','校园优惠'],['facilities','校园设施'],['dining','餐饮服务']]
const rows=(value)=>Array.isArray(value)?value:value?.content||value?.records||value?.list||[]
async function load(){
  loading.value=true;error.value='';selected.value=null
  try{
    if(tab.value==='notices')items.value=rows(await getAnnouncements())
    if(tab.value==='promotions')items.value=rows(await getPromotions({current:1,size:50}))
    if(tab.value==='facilities'){const r=await getMapPlaceList({page:0,size:100});items.value=rows(r.data)}
    if(tab.value==='dining')items.value=rows(await getCanteenStalls({current:1,size:50}))
  }catch(cause){error.value=cause.message}finally{loading.value=false}
}
async function open(item){
  try{
    if(tab.value==='notices')selected.value=await getAnnouncement(item.id)
    else if(tab.value==='promotions')selected.value=await getPromotion(item.id)
    else selected.value=item
  }catch(cause){error.value=cause.message}
}
watch(tab,()=>{history.replaceState(null,'',`/campus-services?tab=${tab.value}`);load()})
onMounted(load)
</script>
<template><div class="feature-page"><AppTabBar/><main class="feature-container"><header class="feature-heading"><div><h1>校园服务</h1><p>公告、优惠、设施和餐饮信息统一入口</p></div><div class="feature-tabs"><button v-for="[value,label] in tabs" :key="value" :class="{active:tab===value}" @click="tab=value">{{label}}</button></div></header><div v-if="error" class="feature-error">{{error}}</div><div class="service-layout"><section class="feature-card feature-section"><div v-if="loading" class="feature-empty">正在加载校园服务…</div><div v-else-if="!items.length" class="feature-empty">暂无可展示的信息</div><div v-else class="service-grid"><button v-for="item in items" :key="item.id" class="service-card" @click="open(item)"><img v-if="item.imageUrl||item.coverImage" :src="item.imageUrl||item.coverImage" alt=""/><div v-else class="service-mark"></div><span class="feature-status feature-status--learning">{{item.sceneType||item.categoryName||item.category||'校园服务'}}</span><h2>{{item.title||item.couponName||item.name||item.stallName}}</h2><p>{{item.description||item.content||item.locationDesc||item.location||'点击查看详细信息'}}</p><small>{{item.createTime||item.startDate||item.openingHours||''}}</small></button></div></section><aside class="feature-card feature-section service-detail"><div class="feature-section__head"><h2>详情</h2></div><div v-if="!selected" class="feature-empty">选择一项内容查看详情</div><template v-else><img v-if="selected.imageUrl||selected.coverImage" :src="selected.imageUrl||selected.coverImage" alt=""/><h2>{{selected.title||selected.couponName||selected.name||selected.stallName}}</h2><p>{{selected.content||selected.description||'暂无补充说明'}}</p><dl><div v-if="selected.pickupLocation"><dt>领取地点</dt><dd>{{selected.pickupLocation}}</dd></div><div v-if="selected.locationDesc||selected.location"><dt>位置</dt><dd>{{selected.locationDesc||selected.location}}</dd></div><div v-if="selected.startDate"><dt>有效期</dt><dd>{{selected.startDate}} 至 {{selected.endDate}}</dd></div><div v-if="selected.openingHours"><dt>开放时间</dt><dd>{{selected.openingHours}}</dd></div></dl></template></aside></div></main></div></template>
<style scoped>
.service-layout{display:grid;grid-template-columns:minmax(0,2fr) minmax(300px,1fr);gap:18px}.service-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:13px}.service-card{padding:16px;border:1px solid #e2e8ee;border-radius:8px;color:#405469;background:#fff;text-align:left}.service-card img,.service-mark{width:100%;height:130px;margin-bottom:13px;border-radius:7px;object-fit:cover;background:#edf1f4}.service-mark{position:relative}.service-mark:after{content:'';position:absolute;inset:38px calc(50% - 22px);width:44px;height:44px;border:2px solid #8fa0b0;border-radius:50%}.service-card h2{margin:11px 0 6px;color:#26384d;font-size:16px}.service-card p{display:-webkit-box;height:40px;margin:0;overflow:hidden;color:#718096;font-size:12px;line-height:1.6;-webkit-box-orient:vertical;-webkit-line-clamp:2}.service-card small{display:block;margin-top:11px;color:#8996a3}.service-detail{height:fit-content}.service-detail>img{width:100%;max-height:220px;border-radius:8px;object-fit:cover}.service-detail>h2{margin:18px 0 8px}.service-detail>p{color:#607286;line-height:1.8;white-space:pre-wrap}.service-detail dl{border-top:1px solid #e5eaf0}.service-detail dl div{display:flex;padding:11px 0}.service-detail dt{width:85px;color:#8794a1}.service-detail dd{margin:0}@media(max-width:1000px){.service-grid{grid-template-columns:repeat(2,1fr)}}@media(max-width:800px){.service-layout{grid-template-columns:1fr}}@media(max-width:520px){.service-grid{grid-template-columns:1fr}}
</style>
