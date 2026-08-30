import { computed, onBeforeUnmount, onMounted, reactive, ref } from 'vue'
import { generateImage, getAiWritingModels, getImageTask, queryLeaderAgent, writeWithAi } from '../api/aiGeneration'
import { buildDiagramPayload, deleteDiagram, generateDiagram, getDiagramDetail, getDiagramHistory, optimizeMindMap, uploadDiagramFile } from '../api/diagrams'
import { AI_STUDIO_TOOLS } from '../config/aiStudioTools'
import { createDiagramSettings, diagramConfig, DIAGRAM_TYPES } from '../config/diagramTools'

const diagramTypes = new Set(DIAGRAM_TYPES)

export function useAiStudioTool(toolId) {
  const meta = AI_STUDIO_TOOLS[toolId] || AI_STUDIO_TOOLS.writing
  const active = ref(toolId)
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
  const settings = reactive(createDiagramSettings())

  const isDiagram = computed(() => diagramTypes.has(active.value))
  const isPresentation = computed(() => active.value === 'presentation')
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
    } catch {
      models.value = []
    }
  }

  async function generate() {
    if (!canGenerate.value) return
    loading.value = true
    error.value = ''
    result.value = null
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
        result.value = await queryLeaderAgent({
          input: `请围绕以下需求生成${meta.title}：${prompt.value.trim()}`,
          interactionType: 'generate_resource',
          requestedOutputType: active.value,
        })
      }
    } catch (cause) {
      error.value = cause?.message || '生成失败，请稍后重试'
    } finally {
      loading.value = false
    }
  }

  function chooseFile() { fileInput.value?.click() }

  async function handleFile(event) {
    const file = event.target.files?.[0]
    event.target.value = ''
    if (!file) return
    const extension = file.name.split('.').pop()?.toLowerCase()
    if (!['pdf', 'doc', 'docx', 'ppt', 'pptx'].includes(extension) || file.size > 20 * 1024 * 1024) {
      error.value = '仅支持 20MB 以内的 PDF、Word 或 PPT 文件'
      return
    }
    uploading.value = true
    error.value = ''
    try {
      clearLocalFileUrl()
      localFileUrl.value = URL.createObjectURL(file)
      uploadedFile.value = await uploadDiagramFile(active.value, file)
      uploadedFile.value.fileName ||= file.name
      uploadedFile.value.fileSize ||= file.size
    } catch (cause) {
      clearLocalFileUrl()
      error.value = cause?.message || '文件导入失败'
    } finally {
      uploading.value = false
    }
  }

  function clearLocalFileUrl() {
    if (localFileUrl.value) URL.revokeObjectURL(localFileUrl.value)
    localFileUrl.value = ''
  }

  function removeUploadedFile() {
    clearLocalFileUrl()
    uploadedFile.value = null
  }

  function previewUploadedFile() {
    if (localFileUrl.value) window.open(localFileUrl.value, '_blank', 'noopener,noreferrer')
  }

  function updateDiagramSettings(value) {
    settings[active.value] = value
  }

  async function loadHistory() {
    if (!isDiagram.value) return
    historyLoading.value = true
    try {
      historyItems.value = await getDiagramHistory(active.value)
    } catch {
      historyItems.value = []
    } finally {
      historyLoading.value = false
    }
  }

  async function openHistoryItem(item) {
    try {
      const detail = await getDiagramDetail(active.value, item.id)
      result.value = detail
      restoreDiagramDraft(detail)
      showHistory.value = false
    } catch (cause) {
      error.value = cause?.message || '历史记录加载失败'
    }
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
    if (!window.confirm(`确认删除“${item.title || meta.title}”这条历史记录吗？删除后无法恢复。`)) return
    try {
      await deleteDiagram(active.value, item.id)
      historyItems.value = historyItems.value.filter((record) => record.id !== item.id)
      if (result.value?.id === item.id) result.value = null
    } catch (cause) {
      error.value = cause?.message || '删除历史记录失败'
    }
  }

  function openOptimizeDialog() {
    optimizeInstruction.value = ''
    showOptimize.value = true
  }

  async function optimizeCurrentMindMap() {
    if (active.value !== 'mind_map' || !result.value || optimizing.value) return
    const instruction = optimizeInstruction.value
    if (!instruction?.trim()) return
    optimizing.value = true
    error.value = ''
    try {
      result.value = await optimizeMindMap({
        currentMindMap: result.value,
        userInstruction: instruction.trim(),
        content: prompt.value,
        sourceFile: result.value.sourceFile,
        sourceText: result.value.sourceText,
        fileId: result.value.fileId,
        fileSummary: result.value.fileSummary,
      })
      showOptimize.value = false
      await loadHistory()
    } catch (cause) {
      error.value = cause?.message || '思维导图优化失败'
    } finally {
      optimizing.value = false
    }
  }

  function openFullscreen() {
    const page = document.querySelector('.studio-result')
    if (!page) return
    if (document.fullscreenElement) document.exitFullscreen?.()
    else page.requestFullscreen?.().catch(() => {})
  }

  function exportResult() {
    if (!result.value) return
    const blob = new Blob([JSON.stringify(result.value, null, 2)], { type: 'application/json;charset=utf-8' })
    const href = URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = href
    link.download = `${meta.title}-${result.value.id || 'result'}.json`
    link.click()
    URL.revokeObjectURL(href)
  }

  onMounted(() => {
    loadModels()
    if (isDiagram.value) loadHistory()
  })
  onBeforeUnmount(clearLocalFileUrl)

  return {
    meta,
    active,
    prompt,
    tone,
    wordCount,
    loading,
    uploading,
    error,
    result,
    uploadedFile,
    historyItems,
    showHistory,
    fileInput,
    optimizing,
    optimizeInstruction,
    showOptimize,
    historyLoading,
    historyQuery,
    localFileUrl,
    models,
    selectedModel,
    settings,
    isDiagram,
    isPresentation,
    currentDiagramConfig,
    canGenerate,
    resources,
    answer,
    images,
    filteredHistory,
    uploadedFileMeta,
    generate,
    chooseFile,
    handleFile,
    removeUploadedFile,
    previewUploadedFile,
    updateDiagramSettings,
    openHistoryItem,
    removeHistoryItem,
    openOptimizeDialog,
    optimizeCurrentMindMap,
    openFullscreen,
    exportResult,
  }
}
