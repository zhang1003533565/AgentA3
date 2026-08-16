<script setup>
import { computed, ref } from 'vue'
import { useRoute } from 'vue-router'
import AppTabBar from '../components/AppTabBar.vue'
import { generateImage, getImageTask, queryLeaderAgent, writeWithAi } from '../api/aiGeneration'

const route = useRoute()
const active = ref(String(route.params.tool || 'writing'))
const prompt = ref('')
const tone = ref('专业')
const wordCount = ref('800')
const loading = ref(false)
const error = ref('')
const result = ref(null)
const tools = [
  ['writing','智能写作','生成校园常用文稿'],
  ['image','AI 文生图','根据描述生成图片'],
  ['exam','试卷生成','生成结构化练习与试卷'],
  ['presentation','PPT 生成','生成演示文稿资源'],
  ['mind_map','思维导图','梳理主题和知识结构'],
  ['architecture','架构图','生成系统架构资源'],
  ['flowchart','流程图','生成业务流程资源'],
]
const currentTool = computed(() => tools.find(([key]) => key === active.value) || tools[0])
const resources = computed(() => result.value?.resources || result.value?.message?.resources || [])
const answer = computed(() => result.value?.content || result.value?.answer || result.value?.message?.content || '')
const images = computed(() => result.value?.images || [])

async function poll(taskId) {
  for (let i=0;i<20;i+=1) {
    await new Promise((resolve) => setTimeout(resolve, 2000))
    const snapshot = await getImageTask(taskId)
    result.value = snapshot
    if (['success','partial_success','failed'].includes(snapshot.status)) break
  }
}
async function generate() {
  if (!prompt.value.trim() || loading.value) return
  loading.value=true;error.value='';result.value=null
  try {
    if (active.value === 'writing') {
      result.value = await writeWithAi({ prompt:prompt.value.trim(),tone:tone.value,wordCount:wordCount.value })
    } else if (active.value === 'image') {
      result.value = await generateImage({ prompt:prompt.value.trim(),style:'clean',size:'1024x1024',count:1,returnType:'url',metadata:{source:'web_ai_studio'} })
      if (result.value.taskId && result.value.status === 'running') await poll(result.value.taskId)
    } else {
      result.value = await queryLeaderAgent({
        input:`请围绕以下需求生成${currentTool.value[1]}：${prompt.value.trim()}`,
        interactionType:'generate_resource',
        requestedOutputType:active.value,
      })
    }
  } catch(cause){error.value=cause.message}finally{loading.value=false}
}
function switchTool(value){active.value=value;result.value=null;error.value='';history.replaceState(null,'',`/ai-studio/${value}`)}
</script>
<template><div class="feature-page"><AppTabBar/><main class="studio">
  <aside class="feature-card studio-nav"><h2>AI 创作工具</h2><button v-for="[value,label,desc] in tools" :key="value" :class="{active:active===value}" @click="switchTool(value)"><i></i><span><strong>{{label}}</strong><small>{{desc}}</small></span></button></aside>
  <section class="feature-card studio-config"><header><span>AI STUDIO</span><h1>{{currentTool[1]}}</h1><p>{{currentTool[2]}}</p></header><label>创作需求<textarea v-model="prompt" class="feature-textarea" placeholder="描述需要生成的内容、用途和约束"></textarea></label><div v-if="active==='writing'" class="form-grid"><label>表达语气<select v-model="tone" class="feature-select"><option>专业</option><option>简洁</option><option>正式</option><option>亲切</option></select></label><label>目标字数<input v-model="wordCount" class="feature-input"/></label></div><button class="feature-button feature-button--primary" :disabled="loading||!prompt.trim()" @click="generate">{{loading?'生成中…':`生成${currentTool[1]}`}}</button><div v-if="error" class="feature-error">{{error}}</div></section>
  <section class="feature-card studio-result"><div class="feature-section__head"><h2>生成结果</h2><span v-if="result" class="feature-status feature-status--completed">已返回</span></div><div v-if="!result" class="feature-empty">填写需求并开始生成，结果将显示在这里</div><article v-else><div v-if="answer" class="answer">{{answer}}</div><div v-if="images.length" class="image-results"><img v-for="image in images" :key="image.url||image" :src="image.url||image" alt="AI 生成图片"/></div><div v-if="resources.length" class="feature-list"><a v-for="resource in resources" :key="resource.id" class="feature-row" :href="resource.previewUrl||resource.url" target="_blank" rel="noreferrer"><div class="feature-row__copy"><strong>{{resource.title||resource.kind}}</strong><span>{{resource.summary}}</span></div><b>打开资源</b></a></div><div v-if="!answer&&!images.length&&!resources.length" class="feature-empty">{{result.message||'任务已受理，请稍后在 AI 历史中查看结果'}}</div></article></section>
</main></div></template>
<style scoped>
.studio{display:grid;grid-template-columns:240px 340px minmax(420px,1fr);gap:18px;width:min(1440px,calc(100% - 40px));margin:auto;padding:24px 0 45px}.studio-nav,.studio-config,.studio-result{padding:22px;height:fit-content}.studio-nav h2{margin:0 0 16px}.studio-nav>button{display:flex;align-items:center;gap:11px;width:100%;padding:12px;border-radius:8px;color:#5d7083;background:transparent;text-align:left}.studio-nav>button.active{color:#294b67;background:#eaf1f6}.studio-nav i{width:16px;height:16px;border:2px solid #7890a7;border-radius:4px}.studio-nav span,.studio-nav strong,.studio-nav small{display:block}.studio-nav small{margin-top:4px;color:#83909d;font-size:11px}.studio-config header>span{color:#6f8398;font-size:11px;font-weight:800;letter-spacing:1.2px}.studio-config h1{margin:8px 0 5px}.studio-config header p{margin:0 0 25px;color:#718096}.studio-config label{display:grid;gap:8px;margin-bottom:14px;color:#42566b;font-size:13px;font-weight:700}.studio-config>button{width:100%;margin-top:6px}.form-grid{display:grid;grid-template-columns:1fr 1fr;gap:10px}.studio-result{min-height:650px}.studio-result>article{display:grid;gap:18px}.answer{padding:22px;border:1px solid #e0e7ed;border-radius:8px;white-space:pre-wrap;line-height:1.85}.image-results{display:grid;grid-template-columns:repeat(2,1fr);gap:12px}.image-results img{width:100%;border-radius:8px}.studio-result a{text-decoration:none}.studio-result a>b{color:#315f8c;font-size:12px}@media(max-width:1100px){.studio{grid-template-columns:220px 1fr}.studio-result{grid-column:1/-1}}@media(max-width:720px){.studio{grid-template-columns:1fr}.studio-result{grid-column:1}.studio-nav{display:flex;gap:5px;overflow:auto}.studio-nav h2{display:none}.studio-nav>button{min-width:150px}}
</style>
