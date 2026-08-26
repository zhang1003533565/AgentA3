<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import AppTabBar from '../components/AppTabBar.vue'
import DiagramWorkspace from '../components/DiagramWorkspace.vue'
import DiagramGenerationStatus from '../components/DiagramGenerationStatus.vue'
import DiagramSettingsPanel from '../components/DiagramSettingsPanel.vue'
import { generateImage, getAiWritingModels, getImageTask, queryLeaderAgent, writeWithAi } from '../api/aiGeneration'
import { buildDiagramPayload, deleteDiagram, generateDiagram, getDiagramDetail, getDiagramHistory, optimizeMindMap, uploadDiagramFile } from '../api/diagrams'
import { createDiagramSettings, diagramConfig, DIAGRAM_TYPES } from '../config/diagramTools'

const route = useRoute()
const router = useRouter()
const active = ref(String(route.params.tool || 'writing'))
const prompt = ref('')
const tone = ref('专业')
const wordCount = ref('800')
const loading = ref(false)
const uploading = ref(false)
const error = ref('')
const result = ref(null)
const uploadedFile = ref(null)
const historyItems = ref([])
const showHistory = ref(false)
const fileInput = ref(null)
const optimizing = ref(false)
const optimizeInstruction = ref('')
const showOptimize = ref(false)
const historyLoading = ref(false)
const historyQuery = ref('')
const localFileUrl = ref('')
const models = ref([])
const selectedModel = ref('')

const tools = [
  ['writing', '智能写作', '生成校园常用文稿'], ['image', 'AI 文生图', '根据描述生成图片'],
  ['exam', '试卷生成', '生成结构化练习与试卷'], ['presentation', 'PPT 生成', '生成演示文稿资源'],
  ['mind_map', '思维导图', '梳理主题和知识结构'], ['architecture', '架构图', '生成系统架构资源'],
  ['flowchart', '流程图', '生成业务流程资源'],
]
const diagramTypes = new Set(DIAGRAM_TYPES)
const settings = reactive(createDiagramSettings())

const currentTool = computed(() => tools.find(([key]) => key === active.value) || tools[0])
const isDiagram = computed(() => diagramTypes.has(active.value))
const currentDiagramConfig = computed(() => diagramConfig(active.value))
const canGenerate = computed(() => Boolean(prompt.value.trim() || uploadedFile.value) && !loading.value && !uploading.value)
const resources = computed(() => result.value?.resources || result.value?.message?.resources || [])
const answer = computed(() => result.value?.content || result.value?.answer || result.value?.message?.content || '')
const images = computed(() => result.value?.images || [])
const filteredHistory = computed(() => {
  const keyword = historyQuery.value.trim().toLowerCase()
  if (!keyword) return historyItems.value
  return historyItems.value.filter((item) => [item.title, item.description, item.content, item.preview].some((value) => String(value || '').toLowerCase().includes(keyword)))
})
const uploadedFileMeta = computed(() => {
  if (!uploadedFile.value) return ''
  const pages = uploadedFile.value.pageCount || uploadedFile.value.pages || uploadedFile.value.slideCount
  const summary = uploadedFile.value.summary || uploadedFile.value.fileSummary
  return [pages ? `${pages}${uploadedFile.value.slideCount ? '页幻灯片' : '页'}` : '', summary].filter(Boolean).join(' · ')
})

watch(() => route.params.tool, (tool) => {
  const next = String(tool || 'writing')
  if (tools.some(([key]) => key === next)) active.value = next
}, { immediate: true })
watch(active, async () => {
  clearLocalFileUrl(); result.value = null; error.value = ''; prompt.value = ''; uploadedFile.value = null; showHistory.value = false; historyQuery.value = ''
  if (isDiagram.value) await loadHistory()
})

async function poll(taskId) {
  for (let index = 0; index < 20; index += 1) {
    await new Promise((resolve) => setTimeout(resolve, 2000))
    const snapshot = await getImageTask(taskId)
    result.value = snapshot
    if (['success', 'partial_success', 'failed'].includes(snapshot.status)) break
  }
}

async function loadModels() {
  try {
    const list = await getAiWritingModels()
    models.value = (Array.isArray(list) ? list : []).map((item) => {
      const model = String(item?.model || item?.displayName || '').trim()
      const providerName = String(item?.providerName || item?.provider || '').trim()
      const configPrefix = String(item?.configPrefix || '').trim()
      return { value: configPrefix || model, label: item?.displayName || model || providerName || '未命名模型' }
    }).filter((item) => item.value)
    if (models.value.length && !models.value.some((item) => item.value === selectedModel.value)) selectedModel.value = models.value[0].value
  } catch { models.value = [] }
}

async function generate() {
  if (!canGenerate.value) return
  loading.value = true; error.value = ''; result.value = null
  try {
    if (isDiagram.value) {
      const payload = buildDiagramPayload(active.value, { content: prompt.value, file: uploadedFile.value, settings: settings[active.value] })
      result.value = await generateDiagram(active.value, payload)
      await loadHistory()
    } else if (active.value === 'writing') {
      result.value = await writeWithAi({ prompt: prompt.value.trim(), tone: tone.value, wordCount: wordCount.value, modelName: selectedModel.value })
    } else if (active.value === 'image') {
      result.value = await generateImage({ prompt: prompt.value.trim(), style: 'clean', size: '1024x1024', count: 1, returnType: 'url', metadata: { source: 'web_ai_studio' } })
      if (result.value.taskId && result.value.status === 'running') await poll(result.value.taskId)
    } else {
      result.value = await queryLeaderAgent({ input: `请围绕以下需求生成${currentTool.value[1]}：${prompt.value.trim()}`, interactionType: 'generate_resource', requestedOutputType: active.value })
    }
  } catch (cause) { error.value = cause?.message || '生成失败，请稍后重试' } finally { loading.value = false }
}

function switchTool(value) { router.push(`/ai-studio/${value}`) }
function chooseFile() { fileInput.value?.click() }
async function handleFile(event) {
  const file = event.target.files?.[0]; event.target.value = ''
  if (!file) return
  const extension = file.name.split('.').pop()?.toLowerCase()
  if (!['pdf', 'doc', 'docx', 'ppt', 'pptx'].includes(extension) || file.size > 20 * 1024 * 1024) { error.value = '仅支持 20MB 以内的 PDF、Word 或 PPT 文件'; return }
  uploading.value = true; error.value = ''
  try {
    clearLocalFileUrl(); localFileUrl.value = URL.createObjectURL(file)
    uploadedFile.value = await uploadDiagramFile(active.value, file)
    uploadedFile.value.fileName ||= file.name
    uploadedFile.value.fileSize ||= file.size
  } catch (cause) { clearLocalFileUrl(); error.value = cause?.message || '文件导入失败' } finally { uploading.value = false }
}
function clearLocalFileUrl() { if (localFileUrl.value) URL.revokeObjectURL(localFileUrl.value); localFileUrl.value = '' }
function removeUploadedFile() { clearLocalFileUrl(); uploadedFile.value = null }
function previewUploadedFile() { if (localFileUrl.value) window.open(localFileUrl.value, '_blank', 'noopener,noreferrer') }
function updateDiagramSettings(value) { settings[active.value] = value }
async function loadHistory() {
  if (!isDiagram.value) return
  historyLoading.value = true
  try { historyItems.value = await getDiagramHistory(active.value) } catch { historyItems.value = [] } finally { historyLoading.value = false }
}
async function openHistoryItem(item) {
  try {
    const detail = await getDiagramDetail(active.value, item.id)
    result.value = detail; restoreDiagramDraft(detail); showHistory.value = false
  } catch (cause) { error.value = cause?.message || '历史记录加载失败' }
}
function restoreDiagramDraft(item) {
  prompt.value = item.description || item.content || item.topic || prompt.value
  const historyFile = Array.isArray(item.files) ? item.files[0] : null
  if (historyFile || item.sourceFile || item.fileId) {
    clearLocalFileUrl()
    uploadedFile.value = {
      ...(historyFile || {}),
      fileName: historyFile?.fileName || historyFile?.name || String(item.sourceFile || '').split('/').pop() || '历史导入文件',
      sourceFile: item.sourceFile || historyFile?.sourceFile || historyFile?.url || '',
      sourceText: item.sourceText || historyFile?.sourceText || historyFile?.text || '',
      fileId: item.fileId || historyFile?.fileId || historyFile?.id || '',
      fileSummary: item.fileSummary || historyFile?.fileSummary || historyFile?.summary || '',
    }
  }
  if (active.value === 'flowchart') {
    settings.flowchart.scene = item.sceneType || settings.flowchart.scene
    settings.flowchart.granularity = item.nodeGranularity || settings.flowchart.granularity
    settings.flowchart.direction = item.requestedLayoutDirection || item.resolvedLayoutDirection || settings.flowchart.direction
    settings.flowchart.decision = item.requestedDecisionMode || item.resolvedDecisionMode || settings.flowchart.decision
    settings.flowchart.lane = item.requestedSwimlaneMode || item.resolvedSwimlaneMode || settings.flowchart.lane
  } else if (active.value === 'architecture') {
    settings.architecture.systemType = item.systemType || settings.architecture.systemType
    settings.architecture.autoLayers = item.autoArchitectureLayers ?? settings.architecture.autoLayers
    settings.architecture.layers = item.architectureLayers || settings.architecture.layers
    settings.architecture.focus = item.focusContents || settings.architecture.focus
    settings.architecture.relation = item.requestedRelationMode || item.resolvedRelationMode || settings.architecture.relation
  } else {
    settings.mind_map.centerTopic = item.resolvedCenterTopic || item.centerTopic || settings.mind_map.centerTopic
    settings.mind_map.depth = item.requestedDepth || settings.mind_map.depth
    settings.mind_map.structure = item.requestedStructure || settings.mind_map.structure
    settings.mind_map.detail = item.detailLevel || settings.mind_map.detail
  }
}
async function removeHistoryItem(item) {
  if (!window.confirm(`确认删除“${item.title || currentTool.value[1]}”这条历史记录吗？删除后无法恢复。`)) return
  try {
    await deleteDiagram(active.value, item.id)
    historyItems.value = historyItems.value.filter((record) => record.id !== item.id)
    if (result.value?.id === item.id) result.value = null
  } catch (cause) { error.value = cause?.message || '删除历史记录失败' }
}
function openOptimizeDialog() { optimizeInstruction.value = ''; showOptimize.value = true }
async function optimizeCurrentMindMap() {
  if (active.value !== 'mind_map' || !result.value || optimizing.value) return
  const instruction = optimizeInstruction.value
  if (!instruction?.trim()) return
  optimizing.value = true; error.value = ''
  try {
    result.value = await optimizeMindMap({ currentMindMap: result.value, userInstruction: instruction.trim(), content: prompt.value, sourceFile: result.value.sourceFile, sourceText: result.value.sourceText, fileId: result.value.fileId, fileSummary: result.value.fileSummary })
    showOptimize.value = false
    await loadHistory()
  } catch (cause) { error.value = cause?.message || '思维导图优化失败' } finally { optimizing.value = false }
}
function openFullscreen() {
  const page = document.querySelector('.studio-result')
  if (!page) return
  if (document.fullscreenElement) document.exitFullscreen?.(); else page.requestFullscreen?.().catch(() => {})
}
function exportResult() {
  if (!result.value) return
  const blob = new Blob([JSON.stringify(result.value, null, 2)], { type: 'application/json;charset=utf-8' })
  const href = URL.createObjectURL(blob); const link = document.createElement('a')
  link.href = href; link.download = `${currentTool.value[1]}-${result.value.id || 'result'}.json`; link.click(); URL.revokeObjectURL(href)
}
onMounted(() => { loadModels(); if (isDiagram.value) loadHistory() })
onBeforeUnmount(clearLocalFileUrl)
</script>

<template>
  <div class="feature-page">
    <AppTabBar />
    <main class="studio" :class="{ 'studio--diagram': isDiagram }">
      <aside class="feature-card studio-nav">
        <h2>AI 创作工具</h2>
        <button v-for="[value,label,desc] in tools" :key="value" type="button" :class="{ active:active===value }" @click="switchTool(value)">
          <i></i><span><strong>{{ label }}</strong><small>{{ desc }}</small></span>
        </button>
        <button type="button" @click="router.push('/convert')"><i></i><span><strong>格式转换</strong><small>PDF / Word / PPT 互转</small></span></button>
      </aside>

      <section class="feature-card studio-config">
        <header class="studio-config__head">
          <div><span>AI STUDIO</span><h1>{{ currentTool[1] }}</h1><p>{{ isDiagram ? currentDiagramConfig.subtitle : currentTool[2] }}</p></div>
          <button v-if="isDiagram" type="button" class="history-button" @click="showHistory=true">历史记录</button>
        </header>

        <label class="studio-field">
          {{ isDiagram ? currentDiagramConfig.inputLabel : '创作需求' }}
          <textarea v-model="prompt" class="feature-textarea" :maxlength="isDiagram ? 2000 : undefined" :placeholder="isDiagram ? currentDiagramConfig.placeholder : '描述需要生成的内容、用途和约束'" />
          <small v-if="isDiagram" class="count">{{ prompt.length }}/2000</small>
        </label>

        <template v-if="isDiagram">
          <input ref="fileInput" class="file-input" type="file" accept=".pdf,.doc,.docx,.ppt,.pptx" @change="handleFile" />
          <div class="file-import"><button type="button" class="feature-button" :disabled="uploading" @click="chooseFile">{{ uploading ? '正在导入…' : '导入文件' }}</button><span>支持 PDF / Word / PPT（≤20MB）</span></div>
          <div v-if="uploadedFile" class="uploaded-file">
            <div><strong>{{ uploadedFile.fileName || '已导入文件' }}</strong><small>{{ uploadedFileMeta || '文件已解析，可作为生成依据' }}</small></div>
            <div><button v-if="localFileUrl" type="button" @click="previewUploadedFile">预览</button><button type="button" class="danger" @click="removeUploadedFile">移除</button></div>
          </div>
          <DiagramSettingsPanel :type="active" :model-value="settings[active]" @update:model-value="updateDiagramSettings" />
        </template>
        <div v-else-if="active==='writing'" class="form-grid"><label>选择模型<select v-model="selectedModel" class="feature-select"><option v-if="!models.length" value="">暂无可用模型</option><option v-for="model in models" :key="model.value" :value="model.value">{{ model.label }}</option></select></label><label>表达语气<select v-model="tone" class="feature-select"><option>专业</option><option>简洁</option><option>正式</option><option>亲切</option></select></label><label>目标字数<input v-model="wordCount" class="feature-input" /></label></div>

        <section v-if="isDiagram" class="recent-history">
          <div><h2>最近生成</h2><button type="button" @click="showHistory=true">查看全部</button></div>
          <button v-for="item in historyItems.slice(0,3)" :key="item.id" type="button" class="recent-history__item" @click="openHistoryItem(item)"><strong>{{ item.title }}</strong><span>{{ item.createTime || '查看结果与恢复配置' }}</span></button>
          <p v-if="historyLoading">正在加载历史记录…</p><p v-else-if="!historyItems.length">暂无生成记录，完成一次生成后会显示在这里。</p>
        </section>
        <button class="feature-button feature-button--primary generate-button" :disabled="!canGenerate" @click="generate">{{ loading ? '生成中…' : `生成${currentTool[1]}` }}</button>
        <div v-if="error" class="feature-error"><strong>生成失败</strong><span>{{ error }}</span><button v-if="isDiagram && canGenerate" type="button" @click="generate">重新生成</button></div>
      </section>

      <section class="feature-card studio-result">
        <div class="feature-section__head"><div><h2>生成结果</h2><p v-if="isDiagram">结果支持缩放、节点查看、全屏预览和数据导出</p></div><div class="result-actions" v-if="result&&isDiagram"><button v-if="active==='mind_map'" :disabled="optimizing" @click="openOptimizeDialog">{{ optimizing ? '优化中…' : '优化导图' }}</button><span class="feature-status feature-status--completed">已完成</span></div><span v-else-if="result" class="feature-status feature-status--completed">已返回</span></div>
        <div v-if="!result&&!loading" class="feature-empty result-empty"><i></i><strong>等待生成</strong><span>填写需求或导入文件，生成结果会显示在这里</span></div>
        <DiagramGenerationStatus v-else-if="loading&&isDiagram" :type="active" :active="loading" />
        <div v-else-if="loading" class="feature-empty">AI 正在生成，请稍候…</div>
        <DiagramWorkspace v-else-if="isDiagram" :type="active" :result="result" @export="exportResult" @fullscreen="openFullscreen" />
        <article v-else><div v-if="answer" class="answer">{{answer}}</div><div v-if="images.length" class="image-results"><img v-for="image in images" :key="image.url||image" :src="image.url||image" alt="AI 生成图片" /></div><div v-if="resources.length" class="feature-list"><a v-for="resource in resources" :key="resource.id" class="feature-row" :href="resource.previewUrl||resource.url" target="_blank" rel="noreferrer"><div class="feature-row__copy"><strong>{{resource.title||resource.kind}}</strong><span>{{resource.summary}}</span></div><b>打开资源</b></a></div><div v-if="!answer&&!images.length&&!resources.length" class="feature-empty">{{result.message||'任务已受理，请稍后在 AI 历史中查看结果'}}</div></article>
      </section>
    </main>

    <div v-if="showHistory" class="feature-modal-mask" @click.self="showHistory=false">
      <section class="feature-modal history-modal">
        <header class="feature-modal__head"><div><h2>{{ currentTool[1] }}历史记录</h2><p>查看结果时会同步恢复当时的生成配置</p></div><button class="feature-modal__close" @click="showHistory=false">×</button></header>
        <input v-model="historyQuery" class="history-search" placeholder="搜索标题或生成内容" />
        <div v-if="filteredHistory.length" class="history-list"><div v-for="item in filteredHistory" :key="item.id" class="history-row"><button class="history-row__main" @click="openHistoryItem(item)"><strong>{{item.title}}</strong><span>{{item.preview||item.description||item.content||'点击查看生成结果'}}</span><small>{{item.createTime}}</small></button><button class="history-row__delete" title="删除本条历史记录" @click="removeHistoryItem(item)">删除</button></div></div>
        <div v-else class="feature-empty">{{ historyLoading ? '正在加载历史记录…' : '暂无匹配记录' }}</div>
      </section>
    </div>

    <div v-if="showOptimize" class="feature-modal-mask" @click.self="showOptimize=false">
      <section class="feature-modal optimize-modal">
        <header class="feature-modal__head"><div><h2>优化思维导图</h2><p>保留当前内容并按你的要求重新整理结构</p></div><button class="feature-modal__close" @click="showOptimize=false">×</button></header>
        <div class="quick-tags"><button v-for="tag in ['补充关键知识点','精简重复内容','调整层级结构','突出复习重点']" :key="tag" type="button" @click="optimizeInstruction=tag">{{ tag }}</button></div>
        <textarea v-model="optimizeInstruction" maxlength="500" placeholder="例如：补充复习重点，并将相近主题合并到同一分支" />
        <footer><button type="button" @click="showOptimize=false">取消</button><button type="button" class="primary" :disabled="!optimizeInstruction.trim()||optimizing" @click="optimizeCurrentMindMap">{{ optimizing ? '正在优化…' : '开始优化' }}</button></footer>
      </section>
    </div>
  </div>
</template>

<style scoped>
.studio{display:grid;grid-template-columns:260px minmax(350px,.9fr) minmax(520px,1.65fr);align-items:start;gap:18px;width:min(1680px,calc(100% - 40px));margin:auto;padding:24px 0 45px}.studio-nav,.studio-config,.studio-result{padding:22px;height:fit-content}.studio-nav h2{margin:0 0 16px}.studio-nav>button{display:flex;align-items:center;gap:11px;width:100%;padding:12px;border-radius:8px;color:#5d7083;background:transparent;text-align:left}.studio-nav>button.active{color:#294b67;background:#eaf1f6}.studio-nav i{width:16px;height:16px;border:2px solid #7890a7;border-radius:4px}.studio-nav span,.studio-nav strong,.studio-nav small{display:block}.studio-nav small{margin-top:4px;color:#83909d;font-size:11px}.studio-config__head{display:flex;align-items:flex-start;justify-content:space-between;gap:10px}.studio-config header>div>span{color:#6f8398;font-size:11px;font-weight:800;letter-spacing:1.2px}.studio-config h1{margin:8px 0 5px}.studio-config header p{margin:0 0 20px;color:#718096}.history-button{padding:6px 9px;border:1px solid #d8e3ec;border-radius:6px;color:#41617f;background:#f8fbfd;font-size:12px;font-weight:700}.studio-field{position:relative;display:grid;gap:8px;margin-bottom:14px;color:#42566b;font-size:13px;font-weight:700}.count{position:absolute;right:9px;top:145px;color:#77899d;font-size:11px;font-weight:500}.file-input{display:none}.file-import{display:flex;align-items:center;gap:10px;margin:-2px 0 12px}.file-import .feature-button{min-height:34px;padding:0 12px;font-size:13px}.file-import span{color:#718096;font-size:12px}.uploaded-file{display:flex;align-items:center;justify-content:space-between;gap:8px;margin-bottom:14px;padding:8px 10px;border:1px solid #bfe2d3;border-radius:7px;color:#35725d;background:#f0faf5;font-size:12px}.uploaded-file button{color:#a54239;background:transparent}.diagram-settings{display:grid;gap:9px;padding-top:4px}.diagram-settings h2{margin:8px 0 0;color:#344f6a;font-size:13px}.choice-grid{display:grid;grid-template-columns:repeat(4,minmax(0,1fr));gap:6px}.choice-grid--wide{grid-template-columns:repeat(5,minmax(0,1fr))}.choice-grid--three{grid-template-columns:repeat(3,minmax(0,1fr))}.choice-grid button,.check-list button,.select-row{min-height:34px;padding:7px 8px;border:1px solid #d6e1ea;border-radius:6px;color:#62758a;background:#fff;font-size:12px;font-weight:700}.choice-grid button.selected,.check-list button.selected,.select-row.selected{border-color:#4f779e;color:#294f74;background:#edf4fa}.select-row{display:grid;gap:2px;text-align:left}.select-row small{color:#718399;font-size:11px;font-weight:500}.check-list{display:grid;grid-template-columns:1fr;gap:5px}.check-list--two{grid-template-columns:repeat(2,minmax(0,1fr))}.check-list button{text-align:left}.generate-button{width:100%;margin-top:18px}.form-grid{display:grid;grid-template-columns:1fr 1fr;gap:10px}.form-grid label{display:grid;gap:8px;margin-bottom:14px;color:#42566b;font-size:13px;font-weight:700}.studio-result{min-height:670px}.studio-result>article{display:grid;gap:18px}.answer{padding:22px;border:1px solid #e0e7ed;border-radius:8px;white-space:pre-wrap;line-height:1.85}.image-results{display:grid;grid-template-columns:repeat(2,1fr);gap:12px}.image-results img{width:100%;border-radius:8px}.studio-result a{text-decoration:none}.studio-result a>b{color:#315f8c;font-size:12px}.history-row{display:grid;gap:4px;width:100%;padding:13px 14px;border:1px solid #e3e9ef;border-radius:8px;color:#344f6a;background:#fff;text-align:left}.history-row span{color:#7b8ba0;font-size:12px}@media(max-width:1250px){.studio{grid-template-columns:240px minmax(360px,1fr)}.studio-result{grid-column:1/-1}}@media(max-width:760px){.studio{grid-template-columns:1fr;width:min(100% - 24px,680px)}.studio-result{grid-column:1}.studio-nav{display:flex;gap:5px;overflow:auto}.studio-nav h2{display:none}.studio-nav>button{min-width:150px}.choice-grid--wide{grid-template-columns:repeat(3,1fr)}.choice-grid{grid-template-columns:repeat(2,1fr)}.choice-grid--three{grid-template-columns:repeat(3,1fr)}}
.recent-history{display:grid;gap:6px;margin-top:16px;padding:12px;border:1px solid #e0e9f0;border-radius:8px;background:#fbfcfe}.recent-history>div{display:flex;align-items:center;justify-content:space-between}.recent-history h2{margin:0;color:#405b75;font-size:13px}.recent-history>div button{color:#3d6d98;background:transparent;font-size:12px}.recent-history__item{display:grid;gap:3px;width:100%;padding:7px 8px;border-radius:6px;color:#425e77;background:#fff;text-align:left}.recent-history__item:hover{background:#f0f6fa}.recent-history__item strong,.recent-history__item span{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.recent-history__item strong{font-size:12px}.recent-history__item span,.recent-history p{margin:0;color:#8091a2;font-size:11px}.result-actions{display:flex;align-items:center;gap:8px}.result-actions>button{padding:5px 8px;border:1px solid #cbdce9;border-radius:6px;color:#446b8c;background:#f8fbfd;font-size:12px;font-weight:700}.history-help{margin:0 0 10px;color:#788a9b;font-size:12px}.history-row{display:flex;align-items:stretch;gap:8px;padding:8px;border:1px solid #e3e9ef;border-radius:8px;color:#344f6a;background:#fff}.history-row__main{display:grid;flex:1;gap:4px;min-width:0;padding:5px;color:#344f6a;background:transparent;text-align:left}.history-row__main strong,.history-row__main span{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.history-row__main span,.history-row__main small{color:#7b8ba0;font-size:12px}.history-row__main small{font-size:11px}.history-row__delete{padding:0 5px;color:#a4534a;background:transparent;font-size:12px}
</style>

<style scoped>
.studio--diagram{grid-template-columns:220px minmax(390px,520px) minmax(600px,1fr);width:min(1780px,calc(100% - 32px));gap:16px}.studio--diagram .studio-nav{position:sticky;top:92px}.studio-config{max-height:calc(100vh - 112px);overflow:auto;scrollbar-width:thin}.studio-config__head h1{color:#1f3852}.studio-config__head p{font-size:13px}.feature-textarea{min-height:132px;resize:vertical}.count{top:auto;bottom:10px}.file-import{margin:0 0 10px}.uploaded-file{align-items:center;padding:10px 11px}.uploaded-file>div:first-child{display:grid;gap:3px;min-width:0}.uploaded-file strong{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.uploaded-file small{color:#6e887e;font-weight:500}.uploaded-file>div:last-child{display:flex;gap:8px}.uploaded-file button{color:#416d87}.uploaded-file button.danger{color:#a54239}.recent-history{display:grid;gap:6px;margin-top:20px;padding:13px;border:1px solid #e2e9ef;border-radius:8px;background:#fafcfd}.recent-history>div{display:flex;align-items:center;justify-content:space-between}.recent-history h2{margin:0;color:#425970;font-size:13px}.recent-history>div button{color:#416e93;background:transparent;font-size:11px}.recent-history__item{display:flex;align-items:center;justify-content:space-between;gap:12px;padding:7px 9px;border-radius:5px;color:#486076;background:#fff;text-align:left}.recent-history__item:hover{background:#f0f5f8}.recent-history__item strong{overflow:hidden;text-overflow:ellipsis;white-space:nowrap;font-size:12px}.recent-history__item span,.recent-history p{color:#8493a2;font-size:10px}.recent-history p{margin:4px 0}.generate-button{position:sticky;bottom:-1px;z-index:3;min-height:48px;border-radius:8px;box-shadow:0 -8px 16px rgba(255,255,255,.92)}.feature-error{display:grid;gap:6px;margin-top:10px;padding:12px;border:1px solid #edc9c3;border-radius:8px;color:#a23f34;background:#fff8f7;font-size:12px;line-height:1.5}.feature-error strong{font-size:13px}.feature-error button{justify-self:start;color:#8a392f;background:transparent;font-weight:700}.studio-result{min-height:calc(100vh - 112px);padding:18px}.studio-result .feature-section__head{margin-bottom:12px}.feature-section__head>div>h2{margin-bottom:3px}.feature-section__head p{margin:0;color:#8391a0;font-size:11px}.result-actions{display:flex;align-items:center;gap:8px}.result-actions button{padding:7px 11px;border:1px solid #d7e1e9;border-radius:6px;color:#3e6180;background:#fff;font-size:12px;font-weight:700}.result-empty{display:grid;place-items:center;align-content:center;gap:9px;min-height:520px}.result-empty i{width:48px;height:40px;border:2px solid #cad7e2;border-radius:8px;background:linear-gradient(#fff 0 12px,#edf3f7 12px 15px,#fff 15px 23px,#edf3f7 23px 26px,#fff 26px)}.result-empty strong{color:#536a80}.result-empty span{color:#8a98a7;font-size:12px}.history-modal{width:min(680px,calc(100% - 32px));max-height:min(720px,calc(100vh - 48px));overflow:auto}.feature-modal__head>div p{margin:3px 0 0;color:#8493a2;font-size:12px}.history-search{width:100%;height:40px;margin:4px 0 14px;padding:0 12px;border:1px solid #d9e3eb;border-radius:7px;outline:none}.history-search:focus{border-color:#6084a4}.history-list{display:grid;gap:8px}.history-row{grid-template-columns:minmax(0,1fr) auto;align-items:center}.history-row__main{display:grid;gap:4px;min-width:0;color:#344f6a;background:transparent;text-align:left}.history-row__main strong,.history-row__main span{overflow:hidden;text-overflow:ellipsis;white-space:nowrap}.history-row__main small{color:#98a4b0}.history-row__delete{padding:5px 8px;border-radius:5px;color:#a24a41;background:#fff5f4;font-size:11px}.optimize-modal{width:min(560px,calc(100% - 32px))}.quick-tags{display:flex;flex-wrap:wrap;gap:7px;margin:14px 0}.quick-tags button{padding:7px 9px;border:1px solid #d7e2ea;border-radius:999px;color:#536d84;background:#f8fbfd;font-size:11px}.optimize-modal textarea{width:100%;min-height:130px;padding:12px;border:1px solid #d8e2ea;border-radius:8px;resize:vertical;outline:none}.optimize-modal textarea:focus{border-color:#5d81a2}.optimize-modal footer{display:flex;justify-content:flex-end;gap:9px;margin-top:14px}.optimize-modal footer button{min-width:88px;height:38px;border:1px solid #d6e0e8;border-radius:7px;color:#53697d;background:#fff}.optimize-modal footer .primary{border-color:#326994;color:#fff;background:#326994}.optimize-modal footer button:disabled{opacity:.5}@media(max-width:1380px){.studio--diagram{grid-template-columns:190px minmax(380px,470px) minmax(560px,1fr)}}@media(max-width:1120px){.studio--diagram{grid-template-columns:210px minmax(420px,1fr)}.studio--diagram .studio-result{grid-column:1/-1;min-height:680px}.studio-config{max-height:none}.studio--diagram .studio-nav{position:static}}@media(max-width:760px){.studio--diagram{grid-template-columns:1fr;width:min(100% - 20px,680px)}.studio--diagram .studio-result{grid-column:1}.studio-config{overflow:visible}.result-empty{min-height:340px}}
</style>
