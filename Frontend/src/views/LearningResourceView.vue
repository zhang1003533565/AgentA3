<script setup>
import { computed, ref } from 'vue'
import { useRoute } from 'vue-router'
import AppTabBar from '../components/AppTabBar.vue'
import { retryLearningResource, streamLearningResources } from '../api/learning'

const route = useRoute()
const topic = ref(String(route.query.topic || ''))
const selectedTypes = ref(['knowledge_note', 'mind_map', 'practice_set', 'code_lab'])
const generating = ref(false)
const error = ref('')
const state = ref({ workflowId:'', progress:0, message:'', resources:{}, errors:{}, trace:[] })
const activeResource = ref('knowledge_note')
const types = [
  ['knowledge_note','知识讲解'],['mind_map','思维导图'],
  ['practice_set','针对性练习'],['code_lab','代码实验'],
]
const resources = computed(() => state.value.resources || {})
const trace = computed(() => state.value.trace || [])
const current = computed(() => resources.value[activeResource.value] || null)
const typeLabel = (value) => types.find(([key]) => key === value)?.[1] || value
const statusLabel = (value) => value === 'failed' ? '失败' : value === 'completed' ? '已完成' : '运行中'

function toggleType(type) {
  if (selectedTypes.value.includes(type)) {
    if (selectedTypes.value.length > 1) selectedTypes.value = selectedTypes.value.filter((item) => item !== type)
  } else selectedTypes.value.push(type)
}
function applyEvent(name, payload) {
  const event = typeof payload === 'object' && payload ? payload : { message:String(payload || '') }
  if (event.workflowId) state.value.workflowId = event.workflowId
  if (event.progress != null) state.value.progress = Math.max(0, Math.min(100, Number(event.progress)))
  state.value.message = event.message || state.value.message
  const resourceType = event.resourceType || event.resource?.kind
  if (resourceType && event.resource) {
    state.value.resources = { ...state.value.resources, [resourceType]:event.resource }
    activeResource.value = resourceType
  }
  if (event.resources) state.value.resources = { ...state.value.resources, ...event.resources }
  if (event.errors) state.value.errors = { ...state.value.errors, ...event.errors }
  if (!['session','message'].includes(name)) {
    state.value.trace = [...state.value.trace, {
      sequence:state.value.trace.length+1,eventName:name,agentName:event.agentName,
      resourceType,status:/failed|error/.test(name)?'failed':/_done|done|completed/.test(name)?'completed':'running',
      message:event.message,
    }].slice(-100)
  }
}
async function generate() {
  if (!topic.value.trim() || generating.value) return
  generating.value = true
  error.value = ''
  state.value = { workflowId:'',progress:0,message:'',resources:{},errors:{},trace:[] }
  try {
    await streamLearningResources({
      courseKey:'python', topic:topic.value.trim(), requestedResourceTypes:selectedTypes.value,
    }, applyEvent)
  } catch (cause) { error.value = cause.message } finally { generating.value = false }
}
async function retry(type) {
  try {
    const snapshot = await retryLearningResource(state.value.workflowId, type)
    state.value = { ...state.value, ...snapshot, resources:{...state.value.resources,...(snapshot.resources||{})} }
  } catch (cause) { error.value = cause.message }
}
const payloadText = computed(() => {
  const value = current.value?.payload
  if (!value) return current.value?.summary || ''
  return value.markdown || value.content || value.text || value.description || current.value?.summary || ''
})
</script>

<template>
  <div class="feature-page">
    <AppTabBar />
    <main class="resource-page">
      <header class="resource-heading"><div><span>Python 学习 / 专项资源</span><h1>生成专项学习资源</h1></div><div class="progress"><label><span>生成进度</span><strong>{{ state.progress }}%</strong></label><div><i :style="{width:`${state.progress}%`}"></i></div></div></header>
      <div v-if="error" class="feature-error">{{ error }}</div>
      <div class="resource-grid">
        <aside class="feature-card config-panel">
          <label>当前知识点<input v-model="topic" class="feature-input" placeholder="输入需要攻克的知识点" /></label>
          <h3>资源类型</h3>
          <button v-for="[value,label] in types" :key="value" class="type-option" :class="{active:selectedTypes.includes(value)}" @click="toggleType(value)"><i></i><span>{{ label }}</span></button>
          <button class="feature-button feature-button--primary generate" :disabled="generating||!topic.trim()" @click="generate">{{ generating ? '多智能体协作中…' : '生成资源' }}</button>
        </aside>
        <section class="feature-card result-panel">
          <div class="resource-tabs"><button v-for="[value,label] in types" :key="value" :class="{active:activeResource===value}" @click="activeResource=value">{{ label }}<i v-if="resources[value]"></i></button></div>
          <article v-if="current" class="resource-content">
            <div><span class="feature-status feature-status--completed">已生成</span><h2>{{ current.title || typeLabel(activeResource) }}</h2><p>{{ current.summary }}</p></div>
            <div v-if="payloadText" class="resource-body">{{ payloadText }}</div>
            <div v-if="current.url || current.previewUrl" class="feature-actions"><a class="feature-button feature-button--primary" :href="current.previewUrl || current.url" target="_blank" rel="noreferrer">打开资源</a></div>
          </article>
          <div v-else class="resource-empty"><div class="empty-mark"></div><h2>尚未生成内容</h2><p>生成后将在这里查看和学习</p><button v-if="state.errors?.[activeResource]" class="feature-button" @click="retry(activeResource)">重试该资源</button></div>
        </section>
        <aside class="feature-card trace-panel">
          <div class="feature-section__head"><h2>智能体协作链路</h2><span>{{ trace.length }} 个事件</span></div>
          <div v-if="!trace.length" class="feature-empty">生成开始后显示协作过程</div>
          <div v-else class="trace-list">
            <div v-for="item in trace" :key="item.sequence" class="trace-item">
              <div class="trace-rail"><i :class="item.status"></i></div>
              <div><div class="trace-title"><strong>{{ item.agentName || item.eventName }}</strong><span :class="`feature-status feature-status--${item.status}`">{{ statusLabel(item.status) }}</span></div><p>{{ item.message || typeLabel(item.resourceType) }}</p><small v-if="item.resourceType">{{ typeLabel(item.resourceType) }}</small></div>
            </div>
          </div>
        </aside>
      </div>
    </main>
  </div>
</template>

<style scoped>
.resource-page{width:min(1500px,calc(100% - 40px));margin:auto;padding:25px 0 42px}.resource-heading{display:flex;align-items:end;justify-content:space-between;gap:30px;margin-bottom:20px}.resource-heading>div>span{color:#718096;font-size:13px}.resource-heading h1{margin:8px 0 0;color:#17233a}.progress{width:min(680px,52%)}.progress label{display:flex;justify-content:space-between;color:#52667b;font-size:13px}.progress>div{height:9px;margin-top:8px;border-radius:999px;background:#e3e9ef;overflow:hidden}.progress i{display:block;height:100%;background:#527797;transition:width .2s}.resource-grid{display:grid;grid-template-columns:280px minmax(460px,1fr) 340px;gap:18px}.config-panel,.trace-panel{padding:22px}.config-panel label{display:grid;gap:8px;color:#43576c;font-size:13px;font-weight:750}.config-panel h3{margin:25px 0 12px}.type-option{display:flex;align-items:center;gap:12px;width:100%;height:49px;margin-bottom:9px;padding:0 13px;border:1px solid #dce3ea;border-radius:8px;color:#53687c;background:#fff;text-align:left}.type-option i{width:16px;height:16px;border:1px solid #9aabba;border-radius:4px}.type-option.active{border-color:#7898b5;color:#2d526f;background:#f0f6fa}.type-option.active i{border:4px solid #527797}.generate{width:100%;margin-top:18px}.result-panel{min-height:650px;overflow:hidden}.resource-tabs{display:grid;grid-template-columns:repeat(4,1fr);border-bottom:1px solid #dfe6ec}.resource-tabs button{position:relative;height:52px;color:#65758a;background:#fff;font-weight:700}.resource-tabs button.active{color:#315f8c;background:#f4f8fb}.resource-tabs button.active:after{content:'';position:absolute;inset:auto 0 0;height:2px;background:#527797}.resource-tabs i{display:inline-block;width:6px;height:6px;margin-left:5px;border-radius:50%;background:#4d9270}.resource-content{padding:28px}.resource-content h2{margin:14px 0 6px}.resource-content p{color:#718096}.resource-body{margin-top:24px;padding:22px;border:1px solid #e4eaf0;border-radius:8px;white-space:pre-wrap;line-height:1.8}.resource-empty{display:grid;place-items:center;padding-top:190px;color:#718096;text-align:center}.resource-empty h2{margin:16px 0 3px;color:#344a60}.empty-mark{width:52px;height:42px;border:2px solid #9caab8;border-radius:6px;box-shadow:inset 0 -8px #eef2f5}.trace-panel{height:fit-content}.feature-section__head>span{color:#718096;font-size:12px}.trace-item{display:grid;grid-template-columns:22px 1fr;gap:12px;min-height:90px}.trace-rail{position:relative}.trace-rail:after{content:'';position:absolute;left:7px;top:18px;bottom:-3px;width:1px;background:#d5dee7}.trace-item:last-child .trace-rail:after{display:none}.trace-rail i{position:relative;z-index:1;display:block;width:15px;height:15px;border:3px solid #fff;border-radius:50%;background:#527797;box-shadow:0 0 0 1px #527797}.trace-rail i.completed{background:#4d9270;box-shadow:0 0 0 1px #4d9270}.trace-rail i.failed{background:#b85e56;box-shadow:0 0 0 1px #b85e56}.trace-title{display:flex;align-items:center;justify-content:space-between;gap:8px}.trace-title strong{font-size:14px}.trace-item p{margin:6px 0;color:#718096;font-size:12px;line-height:1.5}.trace-item small{color:#527797}@media(max-width:1150px){.resource-grid{grid-template-columns:240px 1fr}.trace-panel{grid-column:1/-1}.resource-heading{align-items:start;flex-direction:column}.progress{width:100%}}@media(max-width:760px){.resource-grid{grid-template-columns:1fr}.trace-panel{grid-column:1}.resource-tabs{grid-template-columns:repeat(2,1fr)}}
</style>
