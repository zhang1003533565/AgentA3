<script setup>
import { ref, onMounted } from 'vue'
import AppTabBar from '../components/AppTabBar.vue'
import { getDiscountActivityList, getDiscountActivityDetail, favoriteActivity, unfavoriteActivity } from '../api/discount'

const loading = ref(true)
const items = ref([])
const keyword = ref('')
const showDetail = ref(false)
const detail = ref(null)

const c = v => {if(Array.isArray(v))return v;if(v?.data?.records)return v.data.records;if(v?.records)return v.records;return[]}

const statusConf = {
  0:{text:'未开始',cls:'bg-slate-200 text-slate-600'},
  1:{text:'进行中',cls:'bg-blue-500/90 text-white'},
  2:{text:'已领完',cls:'bg-orange-500/90 text-white'},
  3:{text:'已结束',cls:'bg-slate-400/90 text-white'},
  4:{text:'已下架',cls:'bg-red-500/90 text-white'},
}

async function load(){
  loading.value=true
  try{items.value=c(await getDiscountActivityList({current:1,size:30,keyword:keyword.value||undefined,status:1}))}
  catch(e){items.value=[]}
  loading.value=false
}

async function openDetail(item){
  try{const res=await getDiscountActivityDetail(item.id);detail.value=res?.data||res}catch{detail.value=item}
  showDetail.value=true
}

async function toggleFav(item){
  try{
    if(item.isFavorited){await unfavoriteActivity(item.id);item.isFavorited=false}
    else{await favoriteActivity(item.id);item.isFavorited=true}
    if(showDetail.value&&detail.value)detail.value.isFavorited=item.isFavorited
  }catch(e){alert(e.message)}
}

onMounted(load)

const fmt=t=>{if(!t)return'';const d=t.replace('T',' ');return d.length>=16?d.slice(0,16):d}
const status=item=>item.status??item.activityStatus
</script>

<template>
  <AppTabBar />
  <div class="pt-[60px] min-h-screen bg-background font-sans">

    <!-- Hero -->
    <div class="bg-primary pb-6 pt-8 px-5 text-center text-white">
      <h1 class="text-[26px] font-extrabold mb-2">校园优惠</h1>
      <p class="text-sm text-white/70 mb-5">精选校园周边商家优惠券，线下领取享折扣</p>
      <div class="flex items-center gap-2 max-w-sm mx-auto bg-white/15 rounded-full px-4 py-2">
        <span class="material-symbols-outlined text-white/60 text-xl">search</span>
        <input v-model="keyword" class="flex-1 bg-transparent border-none outline-none text-white text-sm placeholder:text-white/40" placeholder="搜索优惠活动..." @keyup.enter="load" />
      </div>
    </div>

    <!-- Content -->
    <div class="max-w-4xl mx-auto px-4 py-6">
      <div v-if="loading" class="text-center py-20 text-on-surface-variant text-body-md">加载中...</div>
      <div v-else-if="!items.length" class="text-center py-20 text-on-surface-variant">
        <span class="material-symbols-outlined text-5xl block mb-3 opacity-25">local_offer</span>
        <p class="text-body-md">暂无优惠活动</p>
      </div>

      <!-- Grid: 3 per row -->
      <div v-else class="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-4">
        <div v-for="item in items" :key="item.id"
          class="bg-surface-container-lowest rounded-xl shadow-sm border border-outline-variant/10 overflow-hidden cursor-pointer hover:shadow-md transition-shadow"
          @click="openDetail(item)">
          <div class="relative h-40 bg-surface-container flex items-center justify-center overflow-hidden">
            <img v-if="item.coverImage" :src="item.coverImage" class="w-full h-full object-cover" />
            <span v-else class="material-symbols-outlined text-4xl text-outline-variant">local_offer</span>
            <span v-if="status(item)!==undefined" class="absolute top-2 right-2 px-2 py-0.5 rounded-full text-[10px] font-bold" :class="statusConf[status(item)]?.cls">{{statusConf[status(item)]?.text}}</span>
          </div>
          <div class="p-3">
            <div class="flex items-center gap-1.5 mb-1.5">
              <span class="material-symbols-outlined text-sm text-primary/60">store</span>
              <span class="text-[11px] text-on-surface-variant">{{item.merchantName||'校园商家'}}</span>
            </div>
            <h3 class="text-[14px] font-bold text-on-surface leading-snug line-clamp-2">{{item.title}}</h3>
          </div>
        </div>
      </div>
    </div>

    <!-- Detail Modal -->
    <div v-if="showDetail&&detail" class="fixed inset-0 z-[2000] flex items-end sm:items-center justify-center bg-black/50" @click.self="showDetail=false">
      <div class="bg-surface-container-lowest rounded-t-2xl sm:rounded-2xl w-full max-w-md max-h-[90vh] overflow-y-auto">
        <div class="relative h-48 bg-surface-container flex items-center justify-center overflow-hidden">
          <img v-if="detail.coverImage" :src="detail.coverImage" class="w-full h-full object-cover" />
          <span v-else class="material-symbols-outlined text-5xl text-outline-variant">local_offer</span>
          <button class="absolute top-3 left-3 w-8 h-8 bg-black/35 rounded-full flex items-center justify-center text-white" @click="showDetail=false">
            <span class="material-symbols-outlined text-xl">arrow_back</span>
          </button>
          <span v-if="status(detail)!==undefined" class="absolute top-3 right-3 px-2.5 py-1 rounded-full text-[11px] font-bold" :class="statusConf[status(detail)]?.cls">{{statusConf[status(detail)]?.text}}</span>
        </div>
        <div class="p-5">
          <div class="flex items-center gap-3 mb-4">
            <div class="w-10 h-10 rounded-xl bg-primary/10 text-primary flex items-center justify-center">
              <span class="material-symbols-outlined">store</span>
            </div>
            <div>
              <div class="text-body-md font-bold text-on-surface">{{detail.merchantName||'校园商家'}}</div>
              <div v-if="detail.merchantAddress" class="text-xs text-on-surface-variant">{{detail.merchantAddress}}</div>
            </div>
          </div>
          <h2 class="text-h3 text-on-surface mb-2">{{detail.title}}</h2>
          <p class="text-body-md text-on-surface-variant leading-relaxed mb-4">{{detail.description}}</p>
          <div class="space-y-2.5 mb-4">
            <div class="flex items-center gap-2 text-[13px] text-on-surface-variant">
              <span class="material-symbols-outlined text-lg text-primary">schedule</span>
              <span v-if="detail.startTime||detail.endTime">
                <template v-if="detail.startTime">{{fmt(detail.startTime)}}</template>
                <template v-if="detail.startTime&&detail.endTime"> ~ </template>
                <template v-if="detail.endTime">{{fmt(detail.endTime)}}</template>
              </span>
              <span v-else>时间待定</span>
            </div>
            <div v-if="detail.merchantAddress" class="flex items-center gap-2 text-[13px] text-on-surface-variant">
              <span class="material-symbols-outlined text-lg text-primary">location_on</span>
              <span>{{detail.merchantAddress}}</span>
            </div>
            <div v-if="detail.useRules" class="flex items-start gap-2 text-[13px] text-on-surface-variant bg-surface-container-low rounded-lg p-3">
              <span class="material-symbols-outlined text-lg text-primary mt-px">info</span>
              <span>{{detail.useRules}}</span>
            </div>
          </div>
        </div>
        <div class="sticky bottom-0 flex gap-3 p-4 border-t border-outline-variant/20 bg-surface-container-lowest">
          <button :class="['flex-1 py-3 rounded-xl text-body-md font-bold flex items-center justify-center gap-2',detail.isFavorited?'border-2 border-[#e74c3c] text-[#e74c3c]':'border-2 border-outline-variant/50 text-on-surface-variant']" @click="toggleFav(detail)">
            <span class="material-symbols-outlined text-xl">favorite</span>
            {{detail.isFavorited?'已收藏':'收藏'}}
          </button>
        </div>
      </div>
    </div>
  </div>
</template>
