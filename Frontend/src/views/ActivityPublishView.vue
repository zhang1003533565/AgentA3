<script setup>
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import AppTabBar from '../components/AppTabBar.vue'
import { createActivity, getCategoryList, publishActivity } from '../api/activity'

const router=useRouter(),categories=ref([]),saving=ref(false),error=ref('')
const form=ref({title:'',content:'',categoryId:'',location:'',maxPeople:'',startTime:'',endTime:'',signupEndTime:'',contactName:'',contactPhone:'',requiresAudit:false})
async function submit(){
  saving.value=true;error.value=''
  try{
    const payload={...form.value,categoryId:Number(form.value.categoryId),maxPeople:Number(form.value.maxPeople),startTime:form.value.startTime.replace('T',' ')+':00',endTime:form.value.endTime.replace('T',' ')+':00',signupEndTime:form.value.signupEndTime?form.value.signupEndTime.replace('T',' ')+':00':null}
    const draft=await createActivity(payload);const activity=draft.data||draft;await publishActivity(activity.id);router.push('/activities')
  }catch(cause){error.value=cause.message}finally{saving.value=false}
}
onMounted(async()=>{try{const r=await getCategoryList();categories.value=r.data||[]}catch(cause){error.value=cause.message}})
</script>
<template><div class="feature-page"><AppTabBar/><main class="publish-page"><header class="feature-heading"><div><h1>发布校园活动</h1><p>教师或管理员可创建并发布活动</p></div><button class="feature-button" @click="router.push('/activities')">返回活动列表</button></header><div v-if="error" class="feature-error">{{error}}</div><form class="feature-card feature-form publish-form" @submit.prevent="submit"><label class="wide">活动标题<input v-model="form.title" class="feature-input" required maxlength="200"/></label><label class="wide">活动内容<textarea v-model="form.content" class="feature-textarea" required></textarea></label><label>活动分类<select v-model="form.categoryId" class="feature-select" required><option value="">请选择</option><option v-for="item in categories" :key="item.id" :value="item.id">{{item.name}}</option></select></label><label>活动地点<input v-model="form.location" class="feature-input" required/></label><label>开始时间<input v-model="form.startTime" class="feature-input" type="datetime-local" required/></label><label>结束时间<input v-model="form.endTime" class="feature-input" type="datetime-local" required/></label><label>报名截止时间<input v-model="form.signupEndTime" class="feature-input" type="datetime-local"/></label><label>人数上限<input v-model="form.maxPeople" class="feature-input" type="number" min="1" required/></label><label>联系人<input v-model="form.contactName" class="feature-input"/></label><label>联系电话<input v-model="form.contactPhone" class="feature-input"/></label><label class="check wide"><input v-model="form.requiresAudit" type="checkbox"/>报名需要审核</label><div class="wide publish-actions"><button type="button" class="feature-button" @click="router.push('/activities')">取消</button><button class="feature-button feature-button--primary" :disabled="saving">{{saving?'发布中…':'发布活动'}}</button></div></form></main></div></template>
<style scoped>
.publish-page{width:min(900px,calc(100% - 32px));margin:auto;padding:27px 0 48px}.publish-form{grid-template-columns:1fr 1fr;padding:25px}.wide{grid-column:1/-1}.check{display:flex!important;grid-template-columns:auto 1fr!important;align-items:center}.publish-actions{display:flex;justify-content:flex-end;gap:10px;margin-top:8px}@media(max-width:650px){.publish-form{grid-template-columns:1fr}.wide{grid-column:1}}
</style>
