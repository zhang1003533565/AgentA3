<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppTabBar from '../components/AppTabBar.vue'
import { getActivityDetail, getSignInStatus, studentSignIn } from '../api/activity'
const route=useRoute(),router=useRouter(),activity=ref(null),status=ref(null),loading=ref(true),error=ref('')
async function load(){loading.value=true;try{const [a,s]=await Promise.all([getActivityDetail(route.params.activityId),getSignInStatus(route.params.activityId)]);activity.value=a.data;status.value=s.data}catch(cause){error.value=cause.message}finally{loading.value=false}}
async function sign(){try{await studentSignIn(route.params.activityId);await load()}catch(cause){error.value=cause.message}}
onMounted(load)
</script>
<template><div class="feature-page"><AppTabBar/><main class="signin-page"><button class="back" @click="router.push('/activities')">‹ 返回活动</button><div v-if="error" class="feature-error">{{error}}</div><div v-if="loading" class="feature-empty">正在查询签到状态…</div><section v-else class="feature-card signin-card"><span :class="`feature-status feature-status--${status?.signedIn?'completed':status?.open?'learning':'pending'}`">{{status?.signedIn?'已签到':status?.open?'签到进行中':'签到未开启'}}</span><h1>{{activity?.title}}</h1><p>{{activity?.location}} · {{activity?.startTime}}</p><div class="signin-mark"><i></i></div><button class="feature-button feature-button--primary" :disabled="!status?.open||status?.signedIn" @click="sign">{{status?.signedIn?'签到已完成':'立即签到'}}</button></section></main></div></template>
<style scoped>
.signin-page{width:min(520px,calc(100% - 32px));margin:auto;padding:30px 0}.back{margin-bottom:15px;color:#45657f;background:transparent}.signin-card{padding:34px;text-align:center}.signin-card h1{margin:20px 0 7px}.signin-card p{color:#718096}.signin-mark{display:grid;place-items:center;width:130px;height:130px;margin:35px auto;border:1px solid #d5e0e8;border-radius:50%;background:#f1f6f9}.signin-mark i{width:48px;height:65px;border:4px solid #527797;border-radius:24px 24px 9px 9px}.signin-card>button{width:100%}
</style>
