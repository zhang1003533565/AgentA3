<script setup>
import { computed, h, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'

import AppTabBar from '../components/AppTabBar.vue'
import ChatMarkdown from '../components/ChatMarkdown.vue'
import { deleteLeaderSession, getLeaderSessionDetail, getLeaderSessions, queryLeaderAgent, streamLeaderAgent } from '../api/aiGeneration'
import { API_BASE_URL } from '../api/request'
import { AI_RESOURCE_ACCEPT, uploadAiResource } from '../api/upload'
import { getToken } from '../utils/auth'
import pdfIcon from '../assets/file-icons/pdf.png'
import pptIcon from '../assets/file-icons/ppt.png'
import excelIcon from '../assets/file-icons/excel.png'
import wordIcon from '../assets/file-icons/word.png'
import markdownIcon from '../assets/file-icons/markdown.png'
import zipIcon from '../assets/file-icons/zip.png'

const IconLine = (props) => {
  const paths = {
    logo: 'M12 2a4 4 0 0 0-4 4v2H6a4 4 0 0 0-4 4v4a4 4 0 0 0 4 4h2v2h8v-2h2a4 4 0 0 0 4-4v-4a4 4 0 0 0-4-4h-2V6a4 4 0 0 0-4-4Zm-2 6V6a2 2 0 0 1 4 0v2h-4Zm-2 2h8v8H8v-8Zm-4 2a2 2 0 0 1 2-2v8a2 2 0 0 1-2-2v-4Zm14-2a2 2 0 0 1 2 2v4a2 2 0 0 1-2 2v-8Z',
    chat: 'M21 15a4 4 0 0 1-4 4H8l-5 3V7a4 4 0 0 1 4-4h10a4 4 0 0 1 4 4v8Z M8 9h8 M8 13h5',
    pen: 'M12 20h9 M16.5 3.5a2.12 2.12 0 0 1 3 3L8 18l-4 1 1-4L16.5 3.5Z',
    meeting: 'M16 21v-2a4 4 0 0 0-4-4H6a4 4 0 0 0-4 4v2 M9 11a4 4 0 1 0 0-8 4 4 0 0 0 0 8Z M22 21v-2a4 4 0 0 0-3-3.87 M16 3.13a4 4 0 0 1 0 7.75',
    plus: 'M12 5v14 M5 12h14',
    search: 'm21 21-4.35-4.35 M19 11a8 8 0 1 1-16 0 8 8 0 0 1 16 0Z',
    bell: 'M18 8a6 6 0 0 0-12 0c0 7-3 7-3 9h18c0-2-3-2-3-9 M10 21h4',
    sun: 'M12 3V1 M12 23v-2 M4.22 4.22 2.8 2.8 M21.2 21.2l-1.42-1.42 M3 12H1 M23 12h-2 M4.22 19.78 2.8 21.2 M21.2 2.8l-1.42 1.42 M17 12a5 5 0 1 1-10 0 5 5 0 0 1 10 0Z',
    moon: 'M21 12.8A9 9 0 1 1 11.2 3 7 7 0 0 0 21 12.8Z',
    user: 'M20 21a8 8 0 0 0-16 0 M12 13a5 5 0 1 0 0-10 5 5 0 0 0 0 10Z',
    logout: 'M10 17l5-5-5-5 M15 12H3 M21 19V5a2 2 0 0 0-2-2h-6',
    send: 'm22 2-7 20-4-9-9-4 20-7Z M22 2 11 13',
    mic: 'M12 2a3 3 0 0 0-3 3v7a3 3 0 0 0 6 0V5a3 3 0 0 0-3-3Z M19 10v2a7 7 0 0 1-14 0v-2 M12 19v3 M8 22h8',
    globe: 'M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20Z M2 12h20 M12 2a15 15 0 0 1 0 20 M12 2a15 15 0 0 0 0 20',
    brain: 'M9.5 4A3.5 3.5 0 0 0 6 7.5v.6A3.5 3.5 0 0 0 4 14v.5A3.5 3.5 0 0 0 7.5 18H9 M14.5 4A3.5 3.5 0 0 1 18 7.5v.6a3.5 3.5 0 0 1 2 5.9v.5a3.5 3.5 0 0 1-3.5 3.5H15 M9 4a3 3 0 0 1 6 0v16a2 2 0 0 1-3 1.73A2 2 0 0 1 9 20V4Z',
    copy: 'M8 8h11a2 2 0 0 1 2 2v9a2 2 0 0 1-2 2H8a2 2 0 0 1-2-2V10a2 2 0 0 1 2-2Z M16 8V5a2 2 0 0 0-2-2H5a2 2 0 0 0-2 2v9a2 2 0 0 0 2 2h1',
    refresh: 'M20 7h-6V1 M20 7a9 9 0 1 0 1 8',
    like: 'M7 10v11H3V10h4Zm0 9 4 2h6a2 2 0 0 0 2-1.6l1.5-7A2 2 0 0 0 18.5 10H14l1-5a2 2 0 0 0-2-2l-6 7',
    dislike: 'M7 14V3H3v11h4Zm0-9 4-2h6a2 2 0 0 1 2 1.6l1.5 7a2 2 0 0 1-2 2.4H14l1 5a2 2 0 0 1-2 2l-6-7',
    file: 'M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8Z M14 2v6h6 M8 13h8 M8 17h6',
    download: 'M12 3v12 M7 10l5 5 5-5 M5 21h14',
    upload: 'M12 21V9 M7 14l5-5 5 5 M5 3h14',
    calendar: 'M3 9h18 M7 3v4 M17 3v4 M5 5h14a2 2 0 0 1 2 2v14H3V7a2 2 0 0 1 2-2Z',
    clock: 'M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20Z M12 6v6l4 2',
    pin: 'M20 10c0 5-8 12-8 12S4 15 4 10a8 8 0 1 1 16 0Z M12 13a3 3 0 1 0 0-6 3 3 0 0 0 0 6Z',
    chevron: 'm9 18 6-6-6-6',
    check: 'm20 6-11 11-5-5',
    home: 'M3 11 12 3l9 8 M5 10v11h14V10 M9 21v-7h6v7',
    x: 'M18 6 6 18 M6 6l12 12',
  }

  return h('svg', {
    viewBox: '0 0 24 24',
    width: props.size || 20,
    height: props.size || 20,
    fill: props.name === 'logo' ? 'currentColor' : 'none',
    stroke: 'currentColor',
    'stroke-width': props.name === 'logo' ? 0 : 1.8,
    'stroke-linecap': 'round',
    'stroke-linejoin': 'round',
    'aria-hidden': 'true',
  }, [h('path', { d: paths[props.name] || paths.file })])
}
IconLine.props = ['name', 'size']

const activeModule = ref('chat')
const sidebarCollapsed = ref(false)
const darkMode = ref(false)
const userMenuOpen = ref(false)
const noticeOpen = ref(false)
const toast = ref('')
const searchText = ref('')
const router = useRouter()

// AI 页面只有默认对话，不再提供模块切换入口。
const pageTitle = computed(() => '默认对话')

function showToast(message) {
  toast.value = message
  window.setTimeout(() => {
    if (toast.value === message) toast.value = ''
  }, 1800)
}

function selectModule(id) {
  activeModule.value = id
  userMenuOpen.value = false
  noticeOpen.value = false
}

function returnHome() {
  void router.push('/home')
}

// 智能问答
const conversations = ref([])
const activeConversationId = ref('')
const conversationSessionIds = ref({})
const historyLoading = ref(false)
const historyError = ref('')
const chatDraft = ref('')
const chatBusy = ref(false)
const resourceInput = ref(null)
const pendingResources = ref([])
const onlineSearch = ref(true)
const deepThinking = ref(false)
const messageList = ref(null)
const timelineProgress = ref(0)
const timelineHoverIndex = ref(-1)
const timelineDragging = ref(false)
const quickPrompts = ['查课表', '图书馆时间', '奖学金申请', '校园卡补办']
const feedback = ref({})
let activeStreamTask = null

const workflowStageLabels = {
  request_submitted: '请求已提交', session_ready: '会话已建立', leader_route: 'Leader 意图识别与路由',
  leader_plan: 'Leader 制定执行计划', tool_start: '工具开始执行', tool_call: '调用工具',
  tool_result_summary: '工具结果汇总', prompt_agent: '内容格式整理智能体', vision_agent: '图片识别智能体',
  image_generation_tool: '图片生成工具', agent_answer: '智能体生成结果', direct_agent: '专业智能体处理',
  generate_sql: '生成查询语句', retrieval: '检索相关资料', generation_start: '开始生成内容', completed: '处理完成',
  input_pipeline: '附件输入预处理', input_image_collected: '收集上传图片',
  input_attachment_skipped: '跳过不可读取附件', file_content_extraction: '文件内容提取工具',
  file_content_extraction_skipped: '跳过文件内容提取', file_content_extraction_failed: '文件内容提取失败',
  image_grouping: '图片分组', image_stitching_tool: '图片拼接工具',
  image_stitching_skipped: '单张图片直接识别', multimodal_context_merged: '多模态内容汇总', failed: '执行失败',
}

const workflowTriggerLabels = {
  system: '系统自动触发', leader: 'Leader 协调触发', rule_direct: '规则直接触发',
  workflow_dependency: '工作流依赖触发',
}

const workflowFailureLocationLabels = {
  java_proxy: 'Java 代理层',
  web_client: '前端请求',
  python_runtime: 'Python 执行层',
}

function workflowDetailText(detail, fallback = '') {
  if (typeof detail === 'string') return detail
  if (!detail || typeof detail !== 'object') return fallback
  return detail.message
    || detail.failureReason
    || detail.rawMessage
    || detail.routeReason
    || detail.reason
    || detail.summary
    || detail.toolDisplayName
    || fallback
}

function buildWorkflowFailureDescription(entry, detail, fallback = '') {
  const lines = []
  const failurePhase = entry?.failurePhase || detail?.failurePhase || detail?.failureStage || entry?.failureStage || ''
  const failureLocation = entry?.failureLocation || detail?.failureLocation || ''
  const toolName = detail?.toolDisplayName || detail?.toolName || entry?.toolDisplayName || entry?.toolName || ''
  const agentName = detail?.agentDisplayName || detail?.agentName || detail?.failedAgent || detail?.targetAgent || entry?.agentName || ''
  const stage = entry?.stage || detail?.stage || ''
  const message = workflowDetailText(detail, entry?.message || fallback)
  const locationLabel = workflowFailureLocationLabels[failureLocation] || failureLocation
  if (locationLabel) lines.push(`失败位置：${locationLabel}`)
  if (failurePhase) lines.push(`失败阶段：${failurePhase}`)
  if (stage && stage !== 'failed') lines.push(`步骤：${workflowStageLabels[stage] || stage}`)
  if (toolName) lines.push(`工具：${toolName}`)
  if (agentName) lines.push(`智能体：${agentName}`)
  if (message) lines.push(message)
  return lines.join('\n')
}

function normalizeWorkflowStep(entry, index, status = 'completed') {
  const stage = String(entry?.stage || entry?.event || 'processing')
  const detail = entry?.detail && typeof entry.detail === 'object' ? entry.detail : entry
  const toolName = detail?.toolDisplayName || detail?.toolName || detail?.tool || entry?.toolDisplayName || entry?.toolName || ''
  const agentName = detail?.agentDisplayName || detail?.agentName || detail?.targetAgent || detail?.executedAgent || detail?.failedAgent || entry?.agentName || ''
  const intent = detail?.intent || entry?.intent || ''
  const failurePhase = detail?.failurePhase || entry?.failurePhase || detail?.failureStage || entry?.failureStage || ''
  const isFailed = status === 'failed' || stage === 'failed'
  const title = isFailed
    ? `执行失败${failurePhase ? ` · ${failurePhase}` : ''}`
    : (workflowStageLabels[stage] || toolName || agentName || '执行处理')
  const description = isFailed
    ? buildWorkflowFailureDescription(entry, detail, entry?.message || '')
    : workflowDetailText(entry?.detail, entry?.message || '')
  return {
    id: `${stage}-${index}-${toolName || agentName}`,
    stage,
    title,
    description,
    meta: [
      workflowTriggerLabels[detail?.triggerType || entry?.triggerType],
      failurePhase && `阶段：${failurePhase}`,
      toolName && `工具：${toolName}`,
      agentName && `智能体：${agentName}`,
      intent && `意图：${intent}`,
      detail?.routeReason || entry?.routeReason,
    ].filter(Boolean),
    status,
  }
}

function traceWorkflowSteps(trace) {
  return (Array.isArray(trace) ? trace : []).map((entry, index) => normalizeWorkflowStep(entry, index))
}

function appendWorkflowStep(messageId, entry, status = 'running') {
  const target = messages.value.find((item) => item.id === messageId)
  if (!target) return
  const current = (target.workflowSteps || []).map((step) => step.status === 'running' ? { ...step, status: 'completed' } : step)
  target.workflowSteps = [...current, normalizeWorkflowStep(entry, current.length, status)]
}

const messages = ref([
  {
    id: 1,
    role: 'assistant',
    content: '你好，我是校园 AI 助手。你可以直接询问校园服务、学习安排或日常事务。',
  },
])

const isFreshChat = computed(() => messages.value.length === 1
  && messages.value[0]?.role === 'assistant'
  && messages.value[0]?.content === '你好，我是校园 AI 助手。你可以直接询问校园服务、学习安排或日常事务。')

function normalizeConversation(item) {
  const sessionId = String(item?.sessionId || '').trim()
  return {
    id: sessionId,
    sessionId,
    title: item?.title || item?.lastMessage || '未命名会话',
    lastMessage: item?.lastMessage || '',
    messageCount: Number(item?.messageCount || 0),
    updateTime: item?.updateTime || item?.createTime || '',
  }
}

function normalizeHistoryMessage(item, index) {
  const role = item?.role === 'user' ? 'user' : 'assistant'
  const trace = Array.isArray(item?.trace) ? item.trace : []
  return {
    id: item?.id || `${role}-${index}`,
    role,
    content: item?.content || item?.answer || '',
    answerType: item?.answerType || 'text',
    outputType: item?.outputType || '',
    agentName: item?.agentName || 'leader_agent',
    resources: Array.isArray(item?.resources) ? item.resources : [],
    attachments: Array.isArray(item?.attachments) ? item.attachments : [],
    workflowSteps: traceWorkflowSteps(trace),
    workflowExpanded: false,
    exportSessionId: item?.sessionId || '',
    exportMessageId: item?.messageId || item?.id || '',
    workflowStatus: 'completed',
    streaming: false,
  }
}

async function openConversation(id) {
  const sessionId = String(id || '').trim()
  if (!sessionId) return
  activeStreamTask?.abort?.('conversation_changed')
  activeConversationId.value = sessionId
  historyError.value = ''
  try {
    const response = await getLeaderSessionDetail(sessionId)
    const data = response || {}
    conversationSessionIds.value = { ...conversationSessionIds.value, [sessionId]: sessionId }
    messages.value = (data.messages || []).map((item, index) => ({
      ...normalizeHistoryMessage(item, index),
      exportSessionId: sessionId,
    }))
    await scrollMessages()
  } catch (cause) {
    historyError.value = cause.message || '加载会话内容失败'
  }
}

async function loadConversationHistory({ openFirst = true } = {}) {
  historyLoading.value = true
  historyError.value = ''
  try {
    const response = await getLeaderSessions({ pageNum: 1, pageSize: 50 })
    const records = Array.isArray(response?.records) ? response.records : []
    const items = records.map(normalizeConversation).filter((item) => item.sessionId)
    conversations.value = items
    conversationSessionIds.value = Object.fromEntries(items.map((item) => [item.id, item.sessionId]))
    if (openFirst && items.length && !items.some((item) => item.id === activeConversationId.value)) {
      await openConversation(items[0].id)
    }
  } catch (cause) {
    historyError.value = cause.message || '加载 AI 会话历史失败'
  } finally {
    historyLoading.value = false
  }
}

async function removeConversation(item) {
  const sessionId = String(item?.sessionId || '').trim()
  if (!sessionId || !window.confirm('确定删除这个历史会话吗？删除后无法恢复。')) return
  try {
    await deleteLeaderSession(sessionId)
    conversations.value = conversations.value.filter((conversation) => conversation.sessionId !== sessionId)
    if (activeConversationId.value === sessionId) {
      activeConversationId.value = ''
      messages.value = [{
        id: `assistant-${Date.now()}`,
        role: 'assistant',
        content: '已删除该会话。你可以点击“新建对话”开始新的对话。',
      }]
    }
    showToast('历史会话已删除')
  } catch (cause) {
    showToast(cause.message || '删除历史会话失败')
  }
}

onMounted(() => { void loadConversationHistory() })

function isCodeQuestion(question) {
  return /代码|编程|程序|开发|报错|bug|函数|算法|接口|api|python|javascript|typescript|\bjava\b|vue|react|css|html|sql|c\+\+|c#|shell|npm|vite/i.test(question)
}

function buildChatReply(question) {
  if (isCodeQuestion(question)) {
    let code = `// 请围绕以下需求补充具体语言和运行环境：\n// ${question}`
    if (/vue/i.test(question)) {
      code = `import { ref } from 'vue'

const result = ref('')`
    } else if (/python/i.test(question)) {
      code = `def solve(data):
    return data`
    } else if (/javascript|typescript|\bjs\b|\bts\b/i.test(question)) {
      code = `function solve(data) {
  return data
}`
    }
    return {
      question,
      content: `这是一个代码相关问题。我会只保留与“${question}”直接相关的说明和代码，不附加无关内容。`,
      code,
    }
  }

  const campusReplies = [
    {
      test: /课表/,
      content: '课表通常可以在学校教务系统的“我的课表”中查询。建议同时核对教学周、上课地点和临时调课通知。',
    },
    {
      test: /图书馆|开放时间/,
      content: '图书馆开放时间可能随校历、节假日和考试周调整，请以学校图书馆当天公告或官方服务平台为准。',
    },
    {
      test: /奖学金/,
      content: '奖学金申请通常包括查看通知、确认资格、准备成绩与证明材料、在线提交和院系审核。请优先核对本学年的正式通知和截止时间。',
    },
    {
      test: /校园卡|补办/,
      content: '校园卡遗失后建议先立即挂失，再携带有效身份证明前往校园卡服务中心补办；具体费用和办理地点以学校最新通知为准。',
    },
  ]
  const matched = campusReplies.find((item) => item.test.test(question))
  return {
    question,
    content: matched?.content || `我会围绕“${question}”直接回答，不附加代码、固定示例或与问题无关的内容。`,
  }
}

async function scrollMessages() {
  await nextTick()
  messageList.value?.scrollTo({ top: messageList.value.scrollHeight, behavior: 'smooth' })
}

function syncTimelineProgress() {
  const element = messageList.value
  if (!element) return
  const maximum = Math.max(0, element.scrollHeight - element.clientHeight)
  timelineProgress.value = maximum ? element.scrollTop / maximum : 0
}

function scrollToTimelineProgress(progress) {
  const element = messageList.value
  if (!element) return
  const maximum = Math.max(0, element.scrollHeight - element.clientHeight)
  element.scrollTop = maximum * Math.max(0, Math.min(1, progress))
}

function timelineProgressFromPointer(event) {
  const track = event.currentTarget?.closest?.('.conversation-timeline') || document.querySelector('.conversation-timeline')
  if (!track) return 0
  const bounds = track.getBoundingClientRect()
  return (event.clientY - bounds.top) / bounds.height
}

function beginTimelineDrag(event) {
  timelineDragging.value = true
  scrollToTimelineProgress(timelineProgressFromPointer(event))
  window.addEventListener('pointermove', moveTimelineDrag)
  window.addEventListener('pointerup', endTimelineDrag, { once: true })
}

function moveTimelineDrag(event) {
  if (!timelineDragging.value) return
  const track = document.querySelector('.conversation-timeline')
  if (!track) return
  const bounds = track.getBoundingClientRect()
  scrollToTimelineProgress((event.clientY - bounds.top) / bounds.height)
}

function endTimelineDrag() {
  timelineDragging.value = false
  window.removeEventListener('pointermove', moveTimelineDrag)
}

function jumpToMessage(index) {
  const target = messageList.value?.querySelector(`[data-message-index="${index}"]`)
  target?.scrollIntoView({ behavior: 'smooth', block: 'center' })
}

onBeforeUnmount(() => {
  window.removeEventListener('pointermove', moveTimelineDrag)
  window.removeEventListener('pointerup', endTimelineDrag)
})

function createConversation() {
  activeStreamTask?.abort?.('conversation_changed')
  activeConversationId.value = ''
  messages.value = [{
    id: Date.now(),
    role: 'assistant',
    content: '你好，我是校园 AI 助手。你可以直接询问校园服务、学习安排或日常事务。',
  }]
  chatDraft.value = ''
  nextTick(() => document.querySelector('.chat-input')?.focus())
}

function switchConversation(id) {
  void openConversation(id)
}

function resourceEntry(file) {
  const isImage = file.type?.startsWith('image/') || /\.(jpe?g|png|webp|gif)$/i.test(file.name)
  return {
    localId: `${Date.now()}-${Math.random().toString(36).slice(2)}`,
    file,
    name: file.name,
    size: file.size,
    previewUrl: isImage ? URL.createObjectURL(file) : '',
    status: 'uploading',
    resource: null,
    error: '',
  }
}

function fileContentBase64(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(String(reader.result || '').split(',', 2)[1] || '')
    reader.onerror = () => reject(new Error(`读取文件失败：${file.name}`))
    reader.readAsDataURL(file)
  })
}

function patchPendingResource(localId, patch) {
  const index = pendingResources.value.findIndex((item) => item.localId === localId)
  if (index < 0) return
  pendingResources.value[index] = { ...pendingResources.value[index], ...patch }
}

async function uploadEntry(entry) {
  patchPendingResource(entry.localId, { status: 'uploading', error: '' })
  try {
    const [resource, contentBase64] = await Promise.all([
      uploadAiResource(entry.file),
      fileContentBase64(entry.file),
    ])
    patchPendingResource(entry.localId, {
      resource: { ...resource, contentBase64 },
      status: 'success',
    })
  } catch (cause) {
    patchPendingResource(entry.localId, {
      status: 'error',
      error: cause.message || '上传失败',
    })
  }
}

function chooseResources() {
  resourceInput.value?.click()
}

function handleResourceSelect(event) {
  const remaining = 8 - pendingResources.value.length
  const files = Array.from(event.target.files || []).slice(0, Math.max(0, remaining))
  if (!files.length) return
  const entries = files.map(resourceEntry)
  pendingResources.value.push(...entries)
  entries.forEach((entry) => void uploadEntry(entry))
  event.target.value = ''
}

function removeResource(localId) {
  const item = pendingResources.value.find((entry) => entry.localId === localId)
  if (item?.previewUrl) URL.revokeObjectURL(item.previewUrl)
  pendingResources.value = pendingResources.value.filter((entry) => entry.localId !== localId)
}

function isImageAttachment(item) {
  return String(item?.type || '').toLowerCase() === 'image'
    || String(item?.mimeType || '').toLowerCase().startsWith('image/')
    || /\.(jpe?g|png|webp|gif)$/i.test(String(item?.name || item?.fileName || item?.title || item?.url || ''))
}

function looksLikeImageResultJson(content) {
  const text = String(content || '').trim()
  if (!text.startsWith('{')) return false
  try {
    const payload = JSON.parse(text)
    return Array.isArray(payload?.images)
  } catch {
    return false
  }
}

function displayAssistantContent(message) {
  const content = String(message?.content || '').trim()
  if (!content) return ''
  if (message?.answerType === 'image_generation' && looksLikeImageResultJson(content)) {
    return '已根据你的需求生成图片，请查看下方预览。'
  }
  return content
}

function attachmentUrl(item, message = null) {
  const direct = item?.previewUrl || item?.url || item?.sourceUrl || item?.downloadUrl || ''
  if (direct) return direct.startsWith('/') ? `${API_BASE_URL}${direct}` : direct
  const storageKey = String(item?.storageKey || '').trim()
  const sessionId = String(message?.exportSessionId || message?.sessionId || activeConversationId.value || '').trim()
  const messageId = message?.exportMessageId || message?.messageId
  if (storageKey && sessionId && messageId) {
    return `${API_BASE_URL}/api/ai/leader/sessions/${encodeURIComponent(sessionId)}/messages/${messageId}/exports/${encodeURIComponent(storageKey)}`
  }
  return ''
}

function attachmentName(item) {
  return item?.name || item?.fileName || item?.title || '上传图片'
}

async function loadAttachmentBlob(item, message = null) {
  const source = attachmentUrl(item, message)
  if (!source) throw new Error('该附件缺少可用地址')
  const token = getToken()
  const response = await fetch(source.startsWith('/') ? `${API_BASE_URL}${source}` : source, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  })
  if (!response.ok) throw new Error(`读取附件失败（${response.status}）`)
  return response.blob()
}

async function openAttachment(item, message = null) {
  const previewWindow = window.open('about:blank', '_blank')
  try {
    const objectUrl = URL.createObjectURL(await loadAttachmentBlob(item, message))
    if (previewWindow) {
      previewWindow.opener = null
      previewWindow.location.href = objectUrl
    } else {
      window.open(objectUrl, '_blank', 'noopener,noreferrer')
    }
    window.setTimeout(() => URL.revokeObjectURL(objectUrl), 60000)
  } catch (cause) {
    previewWindow?.close()
    showToast(cause.message || '打开附件失败')
  }
}

async function downloadAttachment(item, message = null) {
  try {
    const objectUrl = URL.createObjectURL(await loadAttachmentBlob(item, message))
    const link = document.createElement('a')
    link.href = objectUrl
    link.download = attachmentName(item)
    document.body.appendChild(link)
    link.click()
    link.remove()
    URL.revokeObjectURL(objectUrl)
  } catch (cause) {
    showToast(cause.message || '下载附件失败')
  }
}

function fileExtension(item) {
  const name = attachmentName(item)
  const match = String(name).match(/\.([a-z0-9]+)$/i)
  return (match?.[1] || 'file').toUpperCase()
}

function fileIconClass(item) {
  const extension = fileExtension(item).toLowerCase()
  if (extension === 'pdf') return 'pdf'
  if (['ppt', 'pptx'].includes(extension)) return 'ppt'
  if (['xls', 'xlsx', 'csv'].includes(extension)) return 'excel'
  if (['doc', 'docx'].includes(extension)) return 'word'
  if (['md', 'mmd', 'txt'].includes(extension)) return 'text'
  if (['zip', 'rar', '7z'].includes(extension)) return 'archive'
  return 'generic'
}

function fileIconAsset(item) {
  const extension = fileExtension(item).toLowerCase()
  if (extension === 'pdf') return pdfIcon
  if (['ppt', 'pptx'].includes(extension)) return pptIcon
  if (['xls', 'xlsx', 'csv'].includes(extension)) return excelIcon
  if (['doc', 'docx'].includes(extension)) return wordIcon
  if (['md', 'mmd', 'txt'].includes(extension)) return markdownIcon
  if (['zip', 'rar', '7z'].includes(extension)) return zipIcon
  return ''
}

function formatFileSize(size) {
  if (!size) return '0 KB'
  return size >= 1024 * 1024
    ? `${(size / 1024 / 1024).toFixed(1)} MB`
    : `${Math.ceil(size / 1024)} KB`
}

function syncConversationSession(conversationId, sessionId) {
  const value = String(sessionId || '').trim()
  if (!value) return
  conversationSessionIds.value = {
    ...conversationSessionIds.value,
    [conversationId]: value,
  }
  if (!conversationId && !activeConversationId.value) activeConversationId.value = value
}

function updateChatMessage(id, patch) {
  const target = messages.value.find((item) => item.id === id)
  if (target) Object.assign(target, patch)
  return target
}

async function sendMessage(text) {
  const hasExplicitText = typeof text === 'string'
  const value = String(hasExplicitText ? text : chatDraft.value).trim()
  const uploading = pendingResources.value.some((item) => item.status === 'uploading')
  const attachments = hasExplicitText
    ? []
    : pendingResources.value
      .filter((item) => item.status === 'success')
      .map((item) => item.resource)
  if ((!value && !attachments.length) || chatBusy.value || uploading) return

  const requestText = value
  messages.value.push({ id: Date.now(), role: 'user', content: requestText, attachments })
  const conversation = conversations.value.find((item) => item.id === activeConversationId.value)
  if (conversation?.title === '新对话' && requestText) conversation.title = requestText.slice(0, 18)
  if (!hasExplicitText) {
    chatDraft.value = ''
    pendingResources.value = []
  }

  const assistantMessageId = Date.now() + 1
  const requestConversationId = activeConversationId.value
  messages.value.push({
    id: assistantMessageId,
    role: 'assistant',
    content: attachments.length ? '正在读取并分析上传的资源…' : '正在思考…',
    streaming: true,
    workflowSteps: [normalizeWorkflowStep({
      stage: 'request_submitted',
      message: attachments.length ? `已提交请求，包含 ${attachments.length} 个附件` : '已提交给智能助手，等待分析',
    }, 0, 'running')],
    workflowExpanded: false,
    workflowStatus: 'running',
  })
  chatBusy.value = true
  void scrollMessages()

  const requestPayload = {
    sessionId: conversationSessionIds.value[requestConversationId] || undefined,
    input: requestText,
    ...(attachments.length ? { attachments } : {}),
    metadata: {
      onlineSearch: onlineSearch.value,
      deepThinking: deepThinking.value,
      source: 'web_ai_assistant',
    },
  }
  let streamTouched = false
  let streamCompleted = false

  try {
    const streamTask = streamLeaderAgent(requestPayload, {
      onEvent(eventName, payload) {
        streamTouched = true
        syncConversationSession(requestConversationId, payload?.sessionId)
        const current = messages.value.find((item) => item.id === assistantMessageId)
        if (eventName === 'workflow_step') {
          appendWorkflowStep(assistantMessageId, payload)
        } else if (eventName === 'generation_start') {
          appendWorkflowStep(assistantMessageId, { stage: 'generation_start', message: payload?.answer || '已进入内容生成阶段' })
          updateChatMessage(assistantMessageId, {
            content: payload?.answer || current?.content || '',
            streaming: true,
          })
        } else if (eventName === 'tool_start') {
          appendWorkflowStep(assistantMessageId, { stage: 'tool_start', ...payload })
          updateChatMessage(assistantMessageId, {
            content: current?.receivedDelta ? current.content : (payload?.message || current?.content || ''),
            streaming: true,
            workflowExpanded: true,
          })
        } else if (eventName === 'error') {
          const failureContent = buildWorkflowFailureDescription(payload, payload, payload?.message || '执行失败')
          appendWorkflowStep(assistantMessageId, { stage: 'failed', ...payload }, 'failed')
          updateChatMessage(assistantMessageId, {
            content: failureContent,
            streaming: false,
            workflowExpanded: true,
            workflowStatus: 'failed',
          })
        }
        void scrollMessages()
      },
      onSession(payload) {
        streamTouched = true
        syncConversationSession(requestConversationId, payload?.sessionId)
        const current = messages.value.find((item) => item.id === assistantMessageId)
        appendWorkflowStep(assistantMessageId, {
          stage: 'session_ready',
          message: `会话已建立${payload?.agentName ? `，由 ${payload.agentName} 处理` : ''}${payload?.model ? `，模型 ${payload.model}` : ''}`,
        })
        updateChatMessage(assistantMessageId, { workflowExpanded: current?.workflowExpanded || false })
      },
      onSearch(payload) {
        streamTouched = true
        const current = messages.value.find((item) => item.id === assistantMessageId)
        if (current?.streaming) {
          appendWorkflowStep(assistantMessageId, {
            stage: 'retrieval',
            message: payload?.searchKeyword ? `检索关键词：${payload.searchKeyword}` : '正在检索相关资料',
          })
          updateChatMessage(assistantMessageId, {
            content: payload?.searchKeyword
              ? `正在检索“${payload.searchKeyword}”…`
              : '正在检索相关资料…',
          })
        }
        void scrollMessages()
      },
      onDelta(content) {
        if (!content) return
        streamTouched = true
        const current = messages.value.find((item) => item.id === assistantMessageId)
        const previous = current?.receivedDelta ? current.content : ''
        updateChatMessage(assistantMessageId, {
          content: `${previous}${content}`,
          receivedDelta: true,
          streaming: true,
        })
        void scrollMessages()
      },
      onDone(payload) {
        streamTouched = true
        streamCompleted = true
        syncConversationSession(requestConversationId, payload?.sessionId)
        const current = messages.value.find((item) => item.id === assistantMessageId)
        const finalContent = String(payload?.answer || current?.content || '').trim()
        const finalWorkflow = traceWorkflowSteps(payload?.trace)
        const workflowSteps = finalWorkflow.length
          ? finalWorkflow
          : [...(current?.workflowSteps || []).map((step) => ({ ...step, status: 'completed' })), normalizeWorkflowStep({ stage: 'completed', message: '所有处理步骤已完成' }, (current?.workflowSteps || []).length)]
        updateChatMessage(assistantMessageId, {
          content: finalContent || 'AI 已完成本次资源分析。',
          attachments: payload?.attachments || [],
          resources: payload?.resources || [],
          streaming: false,
          receivedDelta: false,
          answerType: payload?.answerType || 'text',
          exportSessionId: payload?.sessionId || conversationSessionIds.value[requestConversationId] || requestConversationId || '',
          exportMessageId: payload?.messageId || '',
          workflowSteps,
          workflowStatus: 'completed',
        })
        void scrollMessages()
      },
      onError(payload) {
        const failureContent = buildWorkflowFailureDescription(payload, payload, payload?.message || '流式请求失败')
        appendWorkflowStep(assistantMessageId, { stage: 'failed', ...payload }, 'failed')
        updateChatMessage(assistantMessageId, {
          content: failureContent,
          streaming: false,
          workflowExpanded: true,
          workflowStatus: 'failed',
        })
        const error = new Error(payload?.message || '流式请求失败')
        error.payload = payload
        error.handled = true
        throw error
      },
    })
    activeStreamTask = streamTask
    await streamTask
    await loadConversationHistory({ openFirst: false })

    if (!streamCompleted) {
      const current = messages.value.find((item) => item.id === assistantMessageId)
      if (current?.receivedDelta && String(current.content || '').trim()) {
        updateChatMessage(assistantMessageId, { streaming: false, receivedDelta: false })
      } else {
        throw new Error('AI 未返回完整结果')
      }
    }
  } catch (cause) {
    if (cause?.name === 'AbortError') {
      const current = messages.value.find((item) => item.id === assistantMessageId)
      updateChatMessage(assistantMessageId, {
        content: current?.receivedDelta && current.content
          ? `${current.content}\n\n已停止生成。`
          : '已停止生成。',
        streaming: false,
        receivedDelta: false,
        workflowStatus: 'stopped',
      })
    } else if (cause?.fallbackToNormalRequest && !streamTouched) {
      try {
        const response = await queryLeaderAgent(requestPayload)
        syncConversationSession(requestConversationId, response?.sessionId)
        updateChatMessage(assistantMessageId, {
          content: response?.answer || response?.content || 'AI 已完成本次资源分析。',
          attachments: response?.attachments || [],
          resources: response?.resources || [],
          streaming: false,
          workflowSteps: traceWorkflowSteps(response?.trace).length
            ? traceWorkflowSteps(response.trace)
            : (messages.value.find((item) => item.id === assistantMessageId)?.workflowSteps || []).map((step) => ({ ...step, status: 'completed' })),
          workflowStatus: 'completed',
        })
      } catch (fallbackError) {
        appendWorkflowStep(assistantMessageId, { stage: 'failed', message: fallbackError.message || 'AI 请求失败' }, 'failed')
        updateChatMessage(assistantMessageId, {
          content: fallbackError.message || 'AI 请求失败，请稍后重试。',
          streaming: false,
          workflowStatus: 'failed',
        })
      }
    } else {
      if (!cause?.handled) {
        appendWorkflowStep(assistantMessageId, {
          stage: 'failed',
          message: cause.message || 'AI 请求失败',
          failurePhase: cause?.payload?.failurePhase || '前端请求',
          failureLocation: cause?.payload?.failureLocation || 'web_client',
          ...cause?.payload,
        }, 'failed')
      }
      const failureContent = cause?.handled
        ? (messages.value.find((item) => item.id === assistantMessageId)?.content || cause.message || 'AI 请求失败，请稍后重试。')
        : buildWorkflowFailureDescription(cause?.payload || {}, {}, cause.message || 'AI 请求失败，请稍后重试。')
      updateChatMessage(assistantMessageId, {
        content: failureContent,
        streaming: false,
        workflowExpanded: true,
        workflowStatus: 'failed',
      })
    }
  } finally {
    activeStreamTask = null
    chatBusy.value = false
    void scrollMessages()
  }
}

function stopGeneration() {
  activeStreamTask?.abort?.('user_cancelled')
}

function regenerate(message) {
  if (message.question) Object.assign(message, buildChatReply(message.question))
  showToast('回答已重新生成')
}

async function copyText(text) {
  try {
    await navigator.clipboard.writeText(text)
    showToast('已复制到剪贴板')
  } catch {
    showToast('复制失败，请手动复制')
  }
}

function setFeedback(id, value) {
  feedback.value[id] = feedback.value[id] === value ? '' : value
}

// AI 创作
const writingTypes = ['论文大纲', '演讲稿', '活动策划', '邮件模板', '请假条', '实验报告']
const writingType = ref('论文大纲')
const writingLength = ref('中')
const writingStyle = ref('学术')
const writingLanguage = ref('中文')
const writingTopic = ref('')
const writingBusy = ref(false)
const hasGeneratedWriting = ref(false)
const writingSamples = {
  论文大纲: `# 人工智能如何改善校园学习体验

## 一、研究背景
人工智能正在逐步进入课程学习、资料检索与校园服务场景，为个性化学习提供新的可能。

## 二、核心观点
- 建立适合学生的个性化学习路径
- 降低校园信息检索与办事成本
- 在隐私保护前提下提升服务效率

## 三、写作建议
结合真实校园案例，并从便利性、准确性和数据安全三个角度展开论证。`,
  演讲稿: `# 让人工智能成为学习的好伙伴

## 开场
各位老师、同学，大家好。今天我想和大家分享人工智能如何帮助我们更高效地学习。

## 主要内容
- 用 AI 快速梳理课程重点
- 根据薄弱环节制定复习计划
- 正确核验 AI 生成的信息

## 结束语
工具的价值取决于使用方式。希望我们保持思考，让人工智能真正服务于成长。`,
  活动策划: `# 校园人工智能体验日活动策划

## 活动目标
帮助同学了解常用 AI 工具的能力边界和安全使用方法。

## 活动安排
- 主题分享：校园学习中的 AI 应用
- 互动体验：提示词设计与内容核验
- 小组讨论：隐私保护与学术规范

## 执行准备
确认场地、设备、主讲人、报名方式和现场分工，并提前完成流程彩排。`,
  邮件模板: `# 关于校园 AI 分享活动的邀请邮件

## 邮件主题
邀请参加校园人工智能学习分享会

## 邮件正文
尊敬的老师：

您好！我们计划开展校园人工智能学习分享活动，诚邀您担任指导嘉宾。活动时间、地点及具体流程可在确认后进一步沟通。

感谢您的关注，期待您的回复。

此致
敬礼`,
  请假条: `# 请假条

尊敬的老师：

因个人原因，我需要申请请假。请假期间将主动了解课程进度，并按时补交相关学习任务。

## 请假信息
- 请假时间：待填写
- 请假原因：待填写
- 联系方式：待填写

恳请批准。`,
  实验报告: `# 人工智能辅助学习效果实验报告

## 一、实验目的
观察使用 AI 辅助整理学习资料前后的效率变化。

## 二、实验方法
- 选择相同难度的两组学习材料
- 分别采用传统整理和 AI 辅助整理
- 记录完成时间、准确率和复习效果

## 三、实验结果
填写实验数据，并对结果进行表格化比较。

## 四、结论
根据实验数据分析 AI 辅助方式的优势、局限及适用条件。`,
}
const writingResult = ref(writingSamples['论文大纲'])

function selectWritingType(type) {
  writingType.value = type
  writingResult.value = writingSamples[type]
  hasGeneratedWriting.value = false
}

function buildWritingResult(topic, refine) {
  const note = refine
    ? '已在原有结果基础上补充细节并优化表达。'
    : '生成内容为页面演示，请结合实际情况核对后使用。'
  const intro = `> ${writingLength.value}篇幅 · ${writingStyle.value}风格 · ${writingLanguage.value}\n\n`
  const templates = {
    论文大纲: `# ${topic}

## 一、研究背景
说明“${topic}”的现实背景、研究价值和校园应用场景。

## 二、核心问题
- 明确研究对象与主要问题
- 梳理相关观点和论证依据
- 分析实施过程中的风险与边界

## 三、研究结论
总结主要发现，并提出可继续验证的方向。`,
    演讲稿: `# ${topic}

## 开场
各位老师、同学，大家好。今天我演讲的主题是“${topic}”。

## 正文
首先介绍主题背景，其次结合校园中的实际场景说明它带来的变化，最后分享三条可以立即实践的建议。

## 结束语
希望今天的分享能带来新的思考。谢谢大家！`,
    活动策划: `# ${topic}活动策划

## 活动目标
明确活动希望解决的问题、服务的参与对象和预期成果。

## 时间与地点
- 活动时间：待确认
- 活动地点：待确认
- 参与对象：校内师生

## 活动流程
- 前期报名与宣传
- 主题分享与互动体验
- 成果展示与活动反馈

## 保障安排
提前确认人员分工、物料设备、安全预案和现场秩序。`,
    邮件模板: `# ${topic}

## 邮件主题
关于“${topic}”的沟通与邀请

## 收件人
尊敬的老师 / 同学：

您好！现就“${topic}”与您联系。相关背景、时间安排和需要协助的事项如下，请您审阅。

如有疑问，欢迎随时沟通。感谢您的支持！

此致
敬礼`,
    请假条: `# 请假条

尊敬的老师：

因“${topic}”，本人申请请假，并承诺及时了解课程安排、补齐学习内容。

## 请假信息
- 请假时间：待填写
- 课程 / 活动：待填写
- 联系方式：待填写

恳请批准。`,
    实验报告: `# ${topic}实验报告

## 一、实验目的
明确本次实验需要验证的问题和预期目标。

## 二、实验材料与方法
- 实验材料：待填写
- 实验环境：待填写
- 操作步骤：按顺序记录

## 三、实验结果
整理观察数据、异常现象和结果对比。

## 四、分析与结论
根据数据解释实验结果，说明误差来源并提出改进建议。`,
  }
  return `${templates[writingType.value]}\n\n${intro}${note}`
}

const markdownBlocks = computed(() => writingResult.value.split('\n').filter(Boolean).map((line) => {
  if (line.startsWith('## ')) return { type: 'h2', text: line.slice(3) }
  if (line.startsWith('# ')) return { type: 'h1', text: line.slice(2) }
  if (line.startsWith('- ')) return { type: 'li', text: line.slice(2) }
  if (line.startsWith('> ')) return { type: 'quote', text: line.slice(2) }
  return { type: 'p', text: line }
}))

function generateWriting(refine = false) {
  const topic = writingTopic.value.trim()
  if (!topic) {
    showToast('请先输入主题或关键词')
    return
  }
  writingBusy.value = true
  window.setTimeout(() => {
    writingResult.value = buildWritingResult(topic, refine)
    hasGeneratedWriting.value = true
    writingBusy.value = false
    showToast(refine ? '内容已继续完善' : '内容已生成')
  }, 650)
}

function exportWord() {
  const html = `<html><meta charset="utf-8"><body><pre style="font-family:Arial;white-space:pre-wrap">${writingResult.value}</pre></body></html>`
  const url = URL.createObjectURL(new Blob([html], { type: 'application/msword;charset=utf-8' }))
  const link = document.createElement('a')
  link.href = url
  link.download = `${writingType.value}-${writingTopic.value || '校园AI创作'}.doc`
  link.click()
  URL.revokeObjectURL(url)
  showToast('Word 文档已导出')
}

function exportPdf() {
  showToast('请在打印窗口选择“另存为 PDF”')
  window.setTimeout(() => window.print(), 300)
}

// 会议助手
const uploadInput = ref(null)
let meetingSeed = 3
const meetings = ref([
  {
    id: 1,
    name: '示例 · 项目设计讨论',
    time: '今天 14:00 - 15:00',
    date: '2026-07-23',
    status: '进行中',
    location: '创新楼 302',
    attendees: ['主持人', '产品同学', '开发同学'],
    agenda: ['确认本周进度', '讨论页面方案', '明确后续分工'],
  },
  {
    id: 2,
    name: '示例 · 社团活动复盘',
    time: '昨天 19:30 - 20:20',
    date: '2026-07-22',
    status: '已结束',
    location: '线上会议',
    attendees: ['负责人', '宣传组', '执行组'],
    agenda: ['活动数据回顾', '问题总结', '下次活动建议'],
  },
  {
    id: 3,
    name: '示例 · 实验小组周会',
    time: '明天 10:00 - 10:45',
    date: '2026-07-24',
    status: '未开始',
    location: '实验楼 B206',
    attendees: ['小组成员'],
    agenda: ['实验进度同步', '下周计划'],
  },
])
const activeMeetingId = ref(1)
const activeMeeting = computed(() => meetings.value.find((item) => item.id === activeMeetingId.value) || meetings.value[0])
const meetingTodos = ref([
  { id: 1, title: '整理页面需求清单', owner: '产品同学', due: '2026-07-25', done: false },
  { id: 2, title: '完成交互原型评审', owner: '开发同学', due: '2026-07-26', done: false },
])
const transcript = ref([
  { speaker: '主持人', time: '14:08', text: '先确认一下本周的核心任务和当前进度。' },
  { speaker: '产品同学', time: '14:10', text: 'AI 助手页面的三个模块已经完成信息架构梳理。' },
  { speaker: '开发同学', time: '14:12', text: '建议先完成响应式布局，再对接实际接口。' },
])
const micState = ref('idle')
const micMessage = ref('')
const interimTranscript = ref('')
let microphoneStream = null
let speechRecognition = null

const micStatusText = computed(() => {
  const statusMap = {
    idle: '麦克风未开启',
    requesting: '正在请求麦克风权限…',
    recording: '麦克风录音中',
    denied: '麦克风权限被拒绝',
    unsupported: '当前浏览器不支持麦克风',
    error: '麦克风启动失败',
  }
  return statusMap[micState.value] || statusMap.idle
})

function addAttendee() {
  activeMeeting.value.attendees.push('新参会人')
}

function removeAttendee(index) {
  activeMeeting.value.attendees.splice(index, 1)
}

function addAgendaItem() {
  activeMeeting.value.agenda.push('新会议议程')
}

function removeAgendaItem(index) {
  activeMeeting.value.agenda.splice(index, 1)
}

function selectMeeting(id) {
  if (id !== activeMeetingId.value && micState.value === 'recording') stopMicrophone()
  activeMeetingId.value = id
}

function deleteMeeting(meeting) {
  if (!window.confirm(`确定删除会议“${meeting.name}”吗？删除后无法恢复。`)) return

  const index = meetings.value.findIndex((item) => item.id === meeting.id)
  if (index < 0) return
  if (activeMeetingId.value === meeting.id && micState.value === 'recording') stopMicrophone()

  meetings.value.splice(index, 1)
  if (activeMeetingId.value === meeting.id) {
    const nextMeeting = meetings.value[index] || meetings.value[index - 1] || null
    activeMeetingId.value = nextMeeting?.id ?? null
  }
  showToast('会议已删除')
}

function stopMicrophone() {
  if (speechRecognition) {
    speechRecognition.onend = null
    try {
      speechRecognition.stop()
    } catch {
      // 识别服务可能已经停止。
    }
    speechRecognition = null
  }
  if (microphoneStream) {
    microphoneStream.getTracks().forEach((track) => track.stop())
    microphoneStream = null
  }
  interimTranscript.value = ''
  if (micState.value === 'recording' || micState.value === 'requesting') {
    micState.value = 'idle'
    micMessage.value = '录音已停止'
  }
}

async function startMicrophone() {
  if (!navigator.mediaDevices?.getUserMedia) {
    micState.value = 'unsupported'
    micMessage.value = '请使用支持麦克风权限的现代浏览器，并通过 localhost 或 HTTPS 访问。'
    return
  }

  micState.value = 'requesting'
  micMessage.value = ''
  try {
    microphoneStream = await navigator.mediaDevices.getUserMedia({
      audio: {
        echoCancellation: true,
        noiseSuppression: true,
        autoGainControl: true,
      },
    })
    micState.value = 'recording'
    micMessage.value = '麦克风已开启，正在记录现场会议。'

    const Recognition = window.SpeechRecognition || window.webkitSpeechRecognition
    if (!Recognition) {
      micMessage.value = '麦克风已开启；当前浏览器不支持内置语音识别，可继续接入后端转写服务。'
      return
    }

    speechRecognition = new Recognition()
    speechRecognition.lang = 'zh-CN'
    speechRecognition.continuous = true
    speechRecognition.interimResults = true
    speechRecognition.onresult = (event) => {
      let interim = ''
      for (let index = event.resultIndex; index < event.results.length; index += 1) {
        const result = event.results[index]
        const text = result[0]?.transcript?.trim()
        if (!text) continue
        if (result.isFinal) {
          const now = new Date()
          transcript.value.push({
            speaker: '现场发言',
            time: `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`,
            text,
          })
        } else {
          interim += text
        }
      }
      interimTranscript.value = interim
    }
    speechRecognition.onerror = (event) => {
      if (event.error === 'not-allowed' || event.error === 'service-not-allowed') {
        micState.value = 'denied'
        micMessage.value = '请在浏览器地址栏的权限设置中允许使用麦克风。'
        stopMicrophone()
      } else if (event.error !== 'no-speech' && event.error !== 'aborted') {
        micMessage.value = `语音识别暂时不可用：${event.error}`
      }
    }
    speechRecognition.onend = () => {
      if (micState.value !== 'recording' || !speechRecognition) return
      window.setTimeout(() => {
        try {
          speechRecognition?.start()
        } catch {
          // 防止浏览器识别服务重启时重复调用。
        }
      }, 250)
    }
    speechRecognition.start()
  } catch (error) {
    const denied = error?.name === 'NotAllowedError' || error?.name === 'PermissionDeniedError'
    micState.value = denied ? 'denied' : 'error'
    micMessage.value = denied
      ? '麦克风权限未授予，请允许后再次点击开始录音。'
      : `无法开启麦克风：${error?.message || '未知错误'}`
    if (microphoneStream) {
      microphoneStream.getTracks().forEach((track) => track.stop())
      microphoneStream = null
    }
  }
}

onBeforeUnmount(stopMicrophone)
onBeforeUnmount(() => activeStreamTask?.abort?.('page_unload'))

function createMeeting() {
  const item = {
    id: ++meetingSeed,
    name: '未命名会议',
    time: '时间待设置',
    date: '',
    status: '未开始',
    location: '地点待设置',
    attendees: [],
    agenda: ['添加会议议程'],
  }
  meetings.value.unshift(item)
  activeMeetingId.value = item.id
  showToast('已新建会议')
}

function handleUpload(event) {
  const file = event.target.files?.[0]
  if (file) showToast(`已选择录音：${file.name}`)
  event.target.value = ''
}
</script>

<template>
  <AppTabBar />
  <div class="campus-ai" :data-theme="darkMode ? 'dark' : 'light'">
    <aside :class="['side-nav', { collapsed: sidebarCollapsed }]">
      <button
        class="sidebar-toggle"
        type="button"
        :title="sidebarCollapsed ? '展开侧边栏' : '收起侧边栏'"
        :aria-label="sidebarCollapsed ? '展开侧边栏' : '收起侧边栏'"
        @click="sidebarCollapsed = !sidebarCollapsed"
      >
        <IconLine name="chevron" :size="17" />
      </button>
      <div class="brand">
        <span class="brand-mark"><IconLine name="logo" :size="22" /></span>
        <span>
          <strong>校园 AI</strong>
          <small>智能学习工作台</small>
        </span>
      </div>

      <section class="history-section">
        <div class="section-heading">
          <span>对话</span>
          <button type="button" title="新建对话" aria-label="新建对话" @click="createConversation(); selectModule('chat')">
            <IconLine name="plus" :size="15" />
          </button>
        </div>
        <p v-if="historyError" class="history-error">{{ historyError }}</p>
        <div v-if="historyLoading && !conversations.length" class="history-empty">正在加载历史会话…</div>
        <div class="history-list">
          <button
            v-for="item in conversations"
            :key="item.id"
            :class="{ active: activeModule === 'chat' && activeConversationId === item.id }"
            type="button"
            @click="switchConversation(item.id); selectModule('chat')"
          >
            <IconLine name="chat" :size="15" />
            <span>{{ item.title }}</span>
            <span
              class="history-delete"
              role="button"
              tabindex="0"
              title="删除会话"
              @click.stop="removeConversation(item)"
              @keydown.enter.stop="removeConversation(item)"
            >×</span>
          </button>
        </div>
      </section>

      <button class="back-home" type="button" title="返回主页面" @click="returnHome">
        <IconLine name="home" :size="19" />
        <span>返回主页面</span>
      </button>

      <button class="sidebar-user" type="button" @click="userMenuOpen = !userMenuOpen">
        <span class="avatar">U</span>
        <span><strong>校园用户</strong><small>个人工作区</small></span>
        <IconLine name="chevron" :size="16" />
      </button>
    </aside>

    <section :class="['app-area', { 'sidebar-collapsed': sidebarCollapsed }]">
      <header :class="['global-header', { 'sidebar-collapsed': sidebarCollapsed }]">
        <div class="mobile-brand">
          <span class="brand-mark"><IconLine name="logo" :size="19" /></span>
          <strong>{{ pageTitle }}</strong>
        </div>
        <label class="global-search">
          <IconLine name="search" :size="18" />
          <input v-model="searchText" type="search" placeholder="搜索对话、文档或会议" />
          <kbd>⌘ K</kbd>
        </label>
        <div class="header-actions">
          <button type="button" :title="darkMode ? '切换浅色模式' : '切换深色模式'" @click="darkMode = !darkMode">
            <IconLine :name="darkMode ? 'sun' : 'moon'" />
          </button>
          <div class="popover-anchor">
            <button type="button" title="通知" @click="noticeOpen = !noticeOpen; userMenuOpen = false">
              <IconLine name="bell" />
              <i class="notice-dot"></i>
            </button>
            <div v-if="noticeOpen" class="popover notice-popover">
              <strong>通知</strong>
              <p>暂无新的校园通知</p>
            </div>
          </div>
          <div class="popover-anchor desktop-avatar">
            <button class="avatar-button" type="button" @click="userMenuOpen = !userMenuOpen; noticeOpen = false">U</button>
            <div v-if="userMenuOpen" class="popover user-popover">
              <button type="button"><IconLine name="user" :size="17" />个人中心</button>
              <button type="button"><IconLine name="logout" :size="17" />退出</button>
            </div>
          </div>
        </div>
      </header>

      <main class="main-content">
        <!-- 智能问答 -->
        <section v-if="activeModule === 'chat'" class="module-page chat-page page-enter">
          <div ref="messageList" class="chat-scroll" @scroll="syncTimelineProgress">
            <div v-if="isFreshChat" class="empty-chat-state">
              <span class="empty-chat-logo"><IconLine name="logo" :size="28" /></span>
              <h1>你想让校园 AI 帮你做什么？</h1>
            </div>

            <div v-else class="message-stream">
              <article
                v-for="(message, messageIndex) in messages"
                :key="message.id"
                :class="['message-row', message.role]"
                :data-message-index="messageIndex"
              >
                <span v-if="message.role === 'assistant'" class="ai-avatar"><IconLine name="logo" :size="17" /></span>
                <div class="message-wrap">
                  <div class="message-bubble">
                    <div v-if="message.role === 'assistant' && message.workflowSteps?.length" class="workflow-panel">
                      <button class="workflow-summary" type="button" :aria-expanded="message.workflowExpanded" @click="message.workflowExpanded = !message.workflowExpanded">
                        <span :class="['workflow-state-dot', { running: message.streaming, failed: message.workflowStatus === 'failed' }]"></span>
                        <span class="workflow-summary-copy">
                          <strong>{{ message.streaming ? '正在执行' : message.workflowStatus === 'failed' ? '执行流程失败' : message.workflowStatus === 'stopped' ? '执行已停止' : '执行流程已完成' }}</strong>
                          <small>{{ message.workflowSteps[message.workflowSteps.length - 1]?.title }} · 共 {{ message.workflowSteps.length }} 步</small>
                        </span>
                        <span class="workflow-toggle">{{ message.workflowExpanded ? '收起' : '展开详情' }} <IconLine name="chevron" :size="13" /></span>
                      </button>
                      <div v-if="message.workflowExpanded" class="workflow-details">
                        <div v-for="(step, stepIndex) in message.workflowSteps" :key="step.id || `${message.id}-workflow-${stepIndex}`" :class="['workflow-step', step.status]">
                          <span class="workflow-step-index">{{ stepIndex + 1 }}</span>
                          <div class="workflow-step-copy">
                            <strong>{{ step.title }}</strong>
                            <p v-if="step.description">{{ step.description }}</p>
                            <div v-if="step.meta?.length" class="workflow-step-meta"><span v-for="meta in step.meta" :key="meta">{{ meta }}</span></div>
                          </div>
                          <span class="workflow-step-status">{{ step.status === 'running' ? '进行中' : step.status === 'failed' ? '失败' : '已完成' }}</span>
                        </div>
                      </div>
                    </div>
                    <ChatMarkdown
                      v-if="message.role === 'assistant' && displayAssistantContent(message)"
                      :content="displayAssistantContent(message)"
                      :streaming="Boolean(message.streaming)"
                    />
                    <p v-else-if="message.content">{{ message.content }}</p>
                    <div v-if="message.attachments?.length" class="message-attachments">
                      <template v-for="item in message.attachments" :key="item.id || item.url || item.name">
                        <div :class="['message-attachment-card', { 'is-image': isImageAttachment(item) }]">
                          <img
                            v-if="isImageAttachment(item) && attachmentUrl(item, message)"
                            class="message-image"
                            :src="attachmentUrl(item, message)"
                            :alt="attachmentName(item)"
                            loading="lazy"
                            @click="openAttachment(item, message)"
                          />
                          <div v-else class="message-file">
                            <img v-if="fileIconAsset(item)" class="file-type-image" :src="fileIconAsset(item)" :alt="fileExtension(item)" />
                            <i v-else :class="['file-type-icon', `file-type-icon--${fileIconClass(item)}`]">{{ fileExtension(item) }}</i>
                            <span :title="attachmentName(item)">{{ attachmentName(item) }}</span>
                          </div>
                          <div class="message-attachment-actions">
                            <button type="button" @click="openAttachment(item, message)"><IconLine name="file" :size="14" />打开</button>
                            <button type="button" @click="downloadAttachment(item, message)"><IconLine name="download" :size="14" />下载</button>
                          </div>
                        </div>
                      </template>
                    </div>
                    <pre v-if="message.code"><code>{{ message.code }}</code></pre>
                    <div v-if="message.table?.rows?.length" class="response-table">
                        <table>
                          <thead><tr><th v-for="column in message.table.columns" :key="column">{{ column }}</th></tr></thead>
                          <tbody>
                            <tr v-for="(row, rowIndex) in message.table.rows" :key="rowIndex">
                              <td v-for="(cell, cellIndex) in row" :key="cellIndex">{{ cell }}</td>
                            </tr>
                          </tbody>
                        </table>
                      </div>
                      <div v-if="message.sources?.length" class="sources">
                        <strong>参考来源</strong>
                        <span v-for="(source, sourceIndex) in message.sources" :key="source">{{ `[${sourceIndex + 1}] ${source}` }}</span>
                      </div>
                  </div>
                  <div v-if="message.role === 'assistant'" class="message-actions">
                    <button type="button" title="复制" @click="copyText([message.content, message.code].filter(Boolean).join('\n\n'))"><IconLine name="copy" :size="16" /></button>
                    <button type="button" title="重新生成" @click="regenerate(message)"><IconLine name="refresh" :size="16" /></button>
                    <button :class="{ selected: feedback[message.id] === 'like' }" type="button" title="有帮助" @click="setFeedback(message.id, 'like')"><IconLine name="like" :size="16" /></button>
                    <button :class="{ selected: feedback[message.id] === 'dislike' }" type="button" title="需改进" @click="setFeedback(message.id, 'dislike')"><IconLine name="dislike" :size="16" /></button>
                  </div>
                </div>
              </article>
            </div>
          </div>

          <nav v-if="!isFreshChat && messages.length > 1" class="conversation-timeline" aria-label="对话时间轴" @pointerdown.prevent="beginTimelineDrag">
            <button
              v-for="(message, index) in messages"
              :key="`timeline-${message.id}`"
              class="timeline-mark"
              type="button"
              :style="{ top: `${messages.length === 1 ? 0 : index / (messages.length - 1) * 100}%` }"
              :aria-label="`跳转到第 ${index + 1} 条消息`"
              @pointerdown.stop
              @click.stop="jumpToMessage(index)"
              @mouseenter="timelineHoverIndex = index"
              @mouseleave="timelineHoverIndex = -1"
            ></button>
            <span class="timeline-thumb" :class="{ dragging: timelineDragging }" :style="{ top: `${timelineProgress * 100}%` }"></span>
            <aside
              v-if="timelineHoverIndex >= 0"
              class="timeline-preview"
              :style="{ top: `${messages.length === 1 ? 0 : timelineHoverIndex / (messages.length - 1) * 100}%` }"
            >
              <small>{{ messages[timelineHoverIndex]?.role === 'user' ? '你' : '校园 AI' }}</small>
              <strong>{{ messages[timelineHoverIndex]?.content?.slice(0, 34) || '附件消息' }}</strong>
            </aside>
          </nav>

          <div class="composer-zone">
            <div v-if="pendingResources.length" class="upload-queue">
              <div v-for="item in pendingResources" :key="item.localId" class="upload-item">
                <img v-if="item.previewUrl" class="upload-preview" :src="item.previewUrl" :alt="item.name" />
                <img v-if="fileIconAsset(item)" class="file-type-image upload-file-icon" :src="fileIconAsset(item)" :alt="fileExtension(item)" />
                <i v-else :class="['file-type-icon', `file-type-icon--${fileIconClass(item)}`]">{{ fileExtension(item) }}</i>
                <span>
                  <strong>{{ item.name }}</strong>
                  <small>{{ formatFileSize(item.size) }} · {{ item.status === 'uploading' ? '上传中' : item.status === 'error' ? item.error : '已就绪' }}</small>
                </span>
                <button v-if="item.status === 'error'" type="button" @click="uploadEntry(item)">重试</button>
                <button type="button" title="移除" @click="removeResource(item.localId)"><IconLine name="x" :size="15" /></button>
              </div>
            </div>
            <div class="composer-tools">
              <button :class="{ active: onlineSearch }" type="button" @click="onlineSearch = !onlineSearch">
                <IconLine name="globe" :size="16" />联网搜索
                <span class="mini-switch"><i></i></span>
              </button>
              <button :class="{ active: deepThinking }" type="button" @click="deepThinking = !deepThinking">
                <IconLine name="brain" :size="16" />深度思考
                <span class="mini-switch"><i></i></span>
              </button>
            </div>
            <form class="chat-composer" @submit.prevent="sendMessage()">
              <input ref="resourceInput" class="resource-input" type="file" multiple :accept="AI_RESOURCE_ACCEPT" @change="handleResourceSelect" />
              <textarea
                v-model="chatDraft"
                class="chat-input"
                rows="2"
                placeholder="给校园 AI 助手发送消息…"
                @keydown.enter.exact.prevent="sendMessage()"
              ></textarea>
              <div class="composer-actions">
                <button class="icon-button" type="button" title="上传资源" :disabled="chatBusy || pendingResources.length >= 8" @click="chooseResources"><IconLine name="plus" /></button>
                <button class="icon-button" type="button" title="语音输入"><IconLine name="mic" /></button>
                <button v-if="chatBusy" class="send-button stop-button" type="button" title="停止生成" @click="stopGeneration">
                  <IconLine name="x" :size="18" />
                </button>
                <button v-else class="send-button" type="submit" :disabled="(!chatDraft.trim() && !pendingResources.some(item => item.status === 'success')) || pendingResources.some(item => item.status === 'uploading')" title="发送">
                  <IconLine name="send" :size="19" />
                </button>
              </div>
            </form>
            <small>AI 生成内容可能存在误差，请核对重要信息。</small>
          </div>
        </section>

        <!-- AI 创作 -->
        <section v-else-if="activeModule === 'writing'" class="module-page writing-page page-enter">
          <header class="page-heading">
            <div><span class="eyebrow">AI WRITING</span><h1>AI 创作</h1><p>选择文档类型和表达方式，快速生成校园常用内容。</p></div>
          </header>

          <div class="type-tabs">
            <button
              v-for="type in writingTypes"
              :key="type"
              :class="{ active: writingType === type }"
              type="button"
              @click="selectWritingType(type)"
            >{{ type }}</button>
          </div>

          <div class="writing-workspace">
            <aside class="parameter-panel surface">
              <div class="panel-title"><IconLine name="pen" :size="18" /><strong>创作参数</strong></div>
              <div class="option-group">
                <label>字数</label>
                <div class="segmented">
                  <button v-for="item in ['短', '中', '长']" :key="item" :class="{ active: writingLength === item }" type="button" @click="writingLength = item">{{ item }}</button>
                </div>
              </div>
              <div class="option-group">
                <label>风格</label>
                <div class="choice-grid">
                  <button v-for="item in ['正式', '活泼', '学术', '简洁']" :key="item" :class="{ active: writingStyle === item }" type="button" @click="writingStyle = item">{{ item }}</button>
                </div>
              </div>
              <div class="option-group">
                <label>语言</label>
                <div class="segmented">
                  <button v-for="item in ['中文', '英文']" :key="item" :class="{ active: writingLanguage === item }" type="button" @click="writingLanguage = item">{{ item }}</button>
                </div>
              </div>
            </aside>

            <section class="result-panel surface">
              <div class="result-heading">
                <div>
                  <IconLine name="file" :size="18" />
                  <strong>{{ writingBusy ? '正在生成' : hasGeneratedWriting ? '创作结果' : '示例预览' }}</strong>
                </div>
                <span>{{ writingBusy ? 'AI 正在组织内容' : hasGeneratedWriting ? 'Markdown 渲染' : `${writingType}示例` }}</span>
              </div>
              <div v-if="!hasGeneratedWriting && !writingBusy" class="sample-tip">
                以下内容为结构示例。输入主题或关键词并开始生成后，正式结果将在这里替换示例。
              </div>
              <div :class="['markdown-preview', { loading: writingBusy, 'is-sample': !hasGeneratedWriting }]">
                <div v-if="writingBusy" class="writing-loading"><i></i><i></i><i></i><span>正在组织内容</span></div>
                <template v-else>
                  <component :is="block.type === 'quote' ? 'blockquote' : block.type === 'li' ? 'div' : block.type" v-for="(block, index) in markdownBlocks" :key="index" :class="{ 'markdown-list-item': block.type === 'li' }">
                    <span v-if="block.type === 'li'">•</span>{{ block.text }}
                  </component>
                </template>
              </div>
              <div v-if="hasGeneratedWriting" class="result-actions">
                <button type="button" @click="generateWriting()"><IconLine name="refresh" :size="16" />重新生成</button>
                <button type="button" @click="generateWriting(true)"><IconLine name="pen" :size="16" />继续完善</button>
                <button type="button" @click="exportWord"><IconLine name="download" :size="16" />导出 Word</button>
                <button type="button" @click="exportPdf"><IconLine name="download" :size="16" />导出 PDF</button>
                <button type="button" @click="copyText(writingResult)"><IconLine name="copy" :size="16" />复制全文</button>
              </div>
            </section>
          </div>

          <form class="writing-composer surface" @submit.prevent="generateWriting()">
            <div>
              <label for="writing-topic">主题 / 关键词</label>
              <input id="writing-topic" v-model="writingTopic" placeholder="描述你想创作的内容…" />
            </div>
            <button type="submit" :disabled="writingBusy"><IconLine name="pen" :size="18" />{{ writingBusy ? '生成中…' : '开始生成' }}</button>
          </form>
        </section>

        <!-- 会议助手 -->
        <section v-else class="module-page meeting-page page-enter">
          <header class="page-heading meeting-heading">
            <div><span class="eyebrow">MEETING COPILOT</span><h1>会议助手</h1><p>集中管理会议记录、摘要、关键决策与后续待办。</p></div>
            <div class="meeting-header-actions">
              <button class="secondary-action" type="button" @click="uploadInput?.click()"><IconLine name="upload" :size="17" />上传录音</button>
              <button class="primary-action" type="button" @click="createMeeting"><IconLine name="plus" :size="17" />新建会议</button>
              <input ref="uploadInput" class="hidden-input" type="file" accept="audio/*" @change="handleUpload" />
            </div>
          </header>

          <div class="meeting-cards" aria-label="会议列表">
            <article
              v-for="meeting in meetings"
              :key="meeting.id"
              :class="['meeting-card', { active: activeMeetingId === meeting.id }]"
            >
              <button class="meeting-select" type="button" @click="selectMeeting(meeting.id)">
                <span :class="['status', meeting.status]">{{ meeting.status }}</span>
                <strong>{{ meeting.name }}</strong>
                <small><IconLine name="clock" :size="14" />{{ meeting.time }}</small>
              </button>
              <button class="meeting-delete" type="button" :aria-label="`删除会议 ${meeting.name}`" title="删除会议" @click="deleteMeeting(meeting)">
                <IconLine name="x" :size="15" />
              </button>
            </article>
            <div v-if="meetings.length === 0" class="meeting-list-empty">
              暂无会议，点击“新建会议”开始创建。
            </div>
          </div>

          <div v-if="activeMeeting" class="meeting-detail">
            <aside class="meeting-info surface">
              <div class="panel-title"><IconLine name="calendar" :size="18" /><strong>会议信息</strong></div>
              <div class="meeting-fields">
                <label>
                  <span>主题</span>
                  <input v-model="activeMeeting.name" type="text" placeholder="请输入会议主题" />
                </label>
                <label>
                  <span>时间</span>
                  <input v-model="activeMeeting.time" type="text" placeholder="例如：今天 14:00 - 15:00" />
                </label>
                <label>
                  <span>地点</span>
                  <input v-model="activeMeeting.location" type="text" placeholder="请输入会议地点" />
                </label>
              </div>
              <div class="info-block">
                <div class="editable-heading">
                  <label>参会人</label>
                  <button type="button" @click="addAttendee"><IconLine name="plus" :size="14" />添加</button>
                </div>
                <div v-if="activeMeeting.attendees.length" class="editable-list">
                  <div v-for="(person, index) in activeMeeting.attendees" :key="index">
                    <i>{{ index + 1 }}</i>
                    <input v-model="activeMeeting.attendees[index]" type="text" :aria-label="`参会人 ${index + 1}`" />
                    <button type="button" title="移除参会人" @click="removeAttendee(index)"><IconLine name="x" :size="14" /></button>
                  </div>
                </div>
                <p v-else class="empty-text">暂未添加参会人</p>
              </div>
              <div class="info-block">
                <div class="editable-heading">
                  <label>会议议程</label>
                  <button type="button" @click="addAgendaItem"><IconLine name="plus" :size="14" />添加</button>
                </div>
                <div class="editable-list agenda-list">
                  <div v-for="(item, index) in activeMeeting.agenda" :key="index">
                    <i>{{ index + 1 }}</i>
                    <input v-model="activeMeeting.agenda[index]" type="text" :aria-label="`会议议程 ${index + 1}`" />
                    <button type="button" title="移除议程" @click="removeAgendaItem(index)"><IconLine name="x" :size="14" /></button>
                  </div>
                </div>
              </div>
            </aside>

            <section class="meeting-output surface">
              <template v-if="activeMeeting.status === '进行中'">
                <div class="live-heading">
                  <div><i :class="{ active: micState === 'recording' }"></i><strong>实时转写</strong></div>
                  <div class="record-controls">
                    <span>{{ micStatusText }}</span>
                    <button
                      :class="{ recording: micState === 'recording' }"
                      type="button"
                      :disabled="micState === 'requesting'"
                      @click="micState === 'recording' ? stopMicrophone() : startMicrophone()"
                    >
                      <IconLine :name="micState === 'recording' ? 'x' : 'mic'" :size="15" />
                      {{ micState === 'recording' ? '停止录音' : micState === 'requesting' ? '请求权限中' : '开始录音' }}
                    </button>
                  </div>
                </div>
                <p v-if="micMessage" :class="['mic-message', micState]">{{ micMessage }}</p>
                <div class="transcript">
                  <article v-for="item in transcript" :key="`${item.time}-${item.text}`">
                    <div><strong>{{ item.speaker }}</strong><time>{{ item.time }}</time></div>
                    <p>{{ item.text }}</p>
                  </article>
                  <article v-if="interimTranscript" class="interim-row">
                    <div><strong>现场发言</strong><time>识别中</time></div>
                    <p>{{ interimTranscript }}</p>
                  </article>
                </div>
                <div class="summary-box">
                  <span>AI 实时摘要</span>
                  <p>团队已确认先完成 AI 助手页面的响应式布局，随后按模块逐步接入实际业务接口。</p>
                </div>
                <div class="todo-section">
                  <div class="subheading"><strong>自动提取待办</strong><span>{{ meetingTodos.length }} 项</span></div>
                  <label v-for="todo in meetingTodos" :key="todo.id" class="todo-row">
                    <input v-model="todo.done" type="checkbox" />
                    <span>{{ todo.title }}</span>
                    <small>{{ todo.owner }} · {{ todo.due }}</small>
                  </label>
                </div>
              </template>

              <template v-else-if="activeMeeting.status === '已结束'">
                <div class="result-heading"><div><IconLine name="file" :size="18" /><strong>完整会议纪要</strong></div><span>已自动整理</span></div>
                <div class="minutes-content">
                  <h2>会议概述</h2>
                  <p>本次示例会议完成了活动执行过程复盘，并汇总了协作、宣传和现场流程中的改进方向。</p>
                  <div class="decision-box">
                    <strong>关键决策</strong>
                    <p>下次活动提前一周完成物料确认，并在活动前增加一次全流程检查。</p>
                  </div>
                  <h2>待办清单</h2>
                  <div v-for="todo in meetingTodos" :key="todo.id" class="editable-todo">
                    <label><input v-model="todo.done" type="checkbox" />{{ todo.title }}</label>
                    <input v-model="todo.owner" aria-label="负责人" />
                    <input v-model="todo.due" type="date" aria-label="截止日期" />
                  </div>
                </div>
                <div class="result-actions">
                  <button type="button" @click="exportWord"><IconLine name="download" :size="16" />导出 Word</button>
                  <button type="button" @click="exportPdf"><IconLine name="download" :size="16" />导出 PDF</button>
                  <button type="button" @click="copyText('会议纪要：' + activeMeeting.name)"><IconLine name="copy" :size="16" />复制纪要</button>
                </div>
              </template>

              <div v-else class="meeting-empty">
                <span><IconLine name="calendar" :size="28" /></span>
                <h2>会议尚未开始</h2>
                <p>会议开始后，这里将显示实时转写、AI 摘要和自动提取的待办事项。</p>
              </div>
            </section>
          </div>
          <div v-else class="meeting-empty-state surface">
            <span><IconLine name="calendar" :size="28" /></span>
            <h2>暂无会议</h2>
            <p>新建会议后，可以编辑会议信息、使用实时转写并管理待办。</p>
            <button type="button" @click="createMeeting"><IconLine name="plus" :size="16" />新建会议</button>
          </div>

          <div class="mobile-meeting-actions">
            <button type="button" @click="createMeeting"><IconLine name="plus" :size="17" />新建会议</button>
            <button type="button" @click="uploadInput?.click()"><IconLine name="upload" :size="17" />上传录音转文字</button>
          </div>
        </section>
      </main>
    </section>

    <transition name="toast">
      <div v-if="toast" class="toast-message"><IconLine name="check" :size="17" />{{ toast }}</div>
    </transition>
  </div>
</template>

<style scoped>
.campus-ai {
  --primary: #1e3a5f;
  --primary-hover: #284d78;
  --primary-soft: #edf3f8;
  --accent: #356c9f;
  --bg: #f4f7fa;
  --surface: #ffffff;
  --surface-soft: #f8fafc;
  --text: #172033;
  --muted: #6b788a;
  --subtle: #94a0af;
  --line: #dfe6ee;
  --line-strong: #cbd5e1;
  --shadow: 0 10px 28px rgba(30, 58, 95, 0.08);
  min-width: 320px;
  min-height: 100vh;
  padding-top: 60px;
  color: var(--text);
  background: var(--bg);
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", "Microsoft YaHei", sans-serif;
  transition: color .25s ease, background .25s ease;
}

.campus-ai[data-theme="dark"] {
  --primary: #8eb9e3;
  --primary-hover: #a8cdf0;
  --primary-soft: #1a2a3d;
  --accent: #75a8d8;
  --bg: #0e1621;
  --surface: #141f2c;
  --surface-soft: #182534;
  --text: #edf3f9;
  --muted: #a4b1c0;
  --subtle: #7c8b9d;
  --line: #29384a;
  --line-strong: #3a4c60;
  --shadow: 0 12px 34px rgba(0, 0, 0, .22);
}

.campus-ai :deep(*) { box-sizing: border-box; }
.campus-ai button,
.campus-ai input,
.campus-ai textarea { font: inherit; }
.campus-ai button { color: inherit; }

.side-nav {
  position: fixed;
  inset: 60px auto 0 0;
  z-index: 30;
  display: flex;
  width: 286px;
  flex-direction: column;
  padding: 20px 12px 14px;
  border-right: 1px solid var(--line);
  background: var(--surface);
  transition: width .24s ease, padding .24s ease;
}

.side-nav.collapsed { width: 76px; }
.sidebar-toggle {
  position: absolute;
  top: 26px;
  right: -15px;
  z-index: 2;
  display: grid;
  width: 30px;
  height: 30px;
  place-items: center;
  border: 1px solid var(--line);
  border-radius: 50%;
  color: var(--muted);
  background: var(--surface);
  box-shadow: 0 4px 12px rgba(30, 58, 95, .1);
  transition: color .18s ease, background .18s ease, transform .24s ease;
}
.sidebar-toggle:hover { color: var(--primary); background: var(--primary-soft); }
.side-nav:not(.collapsed) .sidebar-toggle svg { transform: rotate(180deg); }
.sidebar-toggle svg { transition: transform .24s ease; }

.brand { display: flex; align-items: center; gap: 10px; padding: 0 8px 22px; }
.brand-mark {
  display: grid;
  width: 38px;
  height: 38px;
  place-items: center;
  flex: none;
  border-radius: 11px;
  color: #fff;
  background: #1e3a5f;
}
.brand > span:last-child { display: grid; gap: 2px; }
.brand strong { font-size: 16px; letter-spacing: .02em; }
.brand small, .sidebar-user small { color: var(--muted); font-size: 11px; }
.side-nav.collapsed .brand { justify-content: center; padding-right: 0; padding-left: 0; }
.side-nav.collapsed .brand > span:last-child,
.side-nav.collapsed .module-nav button span,
.side-nav.collapsed .history-section,
.side-nav.collapsed .back-home span,
.side-nav.collapsed .sidebar-user > span:nth-child(2),
.side-nav.collapsed .sidebar-user > svg { display: none; }

.module-nav { display: grid; gap: 6px; }
.module-nav button {
  display: flex;
  align-items: center;
  gap: 12px;
  min-height: 44px;
  padding: 0 13px;
  border-radius: 10px;
  color: var(--muted);
  background: transparent;
  font-weight: 650;
  text-align: left;
  transition: transform .18s ease, color .18s ease, background .18s ease;
}
.module-nav button:hover { color: var(--primary); background: var(--primary-soft); transform: scale(1.015); }
.module-nav button.active { color: var(--primary); background: var(--primary-soft); }
.side-nav.collapsed .module-nav button { justify-content: center; padding: 0; }

.history-section { display: flex; min-height: 0; flex: 1; flex-direction: column; margin-top: 4px; overflow: hidden; }
.section-heading { display: flex; flex: none; align-items: center; justify-content: space-between; padding: 0 8px 8px; color: var(--subtle); font-size: 11px; font-weight: 700; }
.section-heading button { display: grid; width: 26px; height: 26px; place-items: center; border-radius: 7px; background: transparent; }
.section-heading button:hover { color: var(--primary); background: var(--primary-soft); }
.history-list {
  display: grid;
  min-height: 0;
  align-content: start;
  flex: 1;
  gap: 3px;
  overflow-x: hidden;
  overflow-y: scroll;
  padding-right: 8px;
  scrollbar-color: #b9bec5 transparent;
  scrollbar-width: thin;
}
.history-list::-webkit-scrollbar { width: 7px; }
.history-list::-webkit-scrollbar-track { background: transparent; }
.history-list::-webkit-scrollbar-thumb { border: 2px solid transparent; border-radius: 999px; background: #b9bec5; background-clip: padding-box; }
.history-list::-webkit-scrollbar-thumb:hover { background: #969da6; background-clip: padding-box; }
.history-list button {
  display: flex;
  align-items: center;
  gap: 9px;
  min-width: 0;
  min-height: 36px;
  padding: 0 9px;
  border-radius: 7px;
  color: var(--muted);
  background: transparent;
  text-align: left;
}
.history-list button:hover { color: var(--text); background: #f1f1f1; }
.history-list button.active { color: var(--text); background: #e8e8e8; }
.history-list button > span:nth-child(2) { min-width: 0; flex: 1; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.history-delete {
  display: grid;
  width: 22px;
  height: 22px;
  flex: none;
  place-items: center;
  border-radius: 6px;
  color: var(--muted);
  opacity: 0;
  font-size: 18px;
  line-height: 1;
  transition: opacity .18s ease, color .18s ease, background .18s ease;
}
.history-list button:hover .history-delete, .history-list button.active .history-delete { opacity: 1; }
.history-delete:hover { color: #b42318; background: #fee4e2; }

.back-home {
  display: flex;
  min-height: 42px;
  align-items: center;
  gap: 10px;
  flex: none;
  margin: 8px 0 2px;
  padding: 0 11px;
  border-radius: 9px;
  color: var(--muted);
  background: transparent;
  font-weight: 650;
  text-align: left;
  transition: color .18s ease, background .18s ease, transform .18s ease;
}
.back-home:hover { color: var(--primary); background: var(--primary-soft); transform: scale(1.015); }
.side-nav.collapsed .back-home { justify-content: center; padding: 0; }

.sidebar-user {
  display: grid;
  grid-template-columns: 36px minmax(0, 1fr) 16px;
  align-items: center;
  gap: 10px;
  padding: 10px 8px;
  border-top: 1px solid var(--line);
  background: transparent;
  text-align: left;
  flex: none;
}
.sidebar-user > span:nth-child(2) { display: grid; gap: 2px; }
.sidebar-user strong { font-size: 13px; }
.side-nav.collapsed .sidebar-user {
  display: flex;
  justify-content: center;
  padding-right: 0;
  padding-left: 0;
}
.avatar, .avatar-button {
  display: grid;
  place-items: center;
  border-radius: 50%;
  color: #fff !important;
  background: #476b8f !important;
  font-weight: 750;
}
.avatar { width: 34px; height: 34px; }

.app-area { min-height: calc(100vh - 60px); margin-left: 286px; transition: margin-left .24s ease; }
.app-area.sidebar-collapsed { margin-left: 76px; }
.global-header {
  position: fixed;
  inset: 60px 0 auto 286px;
  z-index: 20;
  display: none;
  height: 68px;
  align-items: center;
  justify-content: center;
  padding: 0 28px;
  border-bottom: 1px solid var(--line);
  background: color-mix(in srgb, var(--surface) 92%, transparent);
  backdrop-filter: blur(14px);
  transition: left .24s ease;
}
.global-header.sidebar-collapsed { left: 76px; }
.mobile-brand { display: none; }
.global-search {
  display: flex;
  width: min(460px, 48vw);
  height: 40px;
  align-items: center;
  gap: 9px;
  padding: 0 11px;
  border: 1px solid var(--line);
  border-radius: 10px;
  color: var(--subtle);
  background: var(--surface-soft);
}
.global-search:focus-within { border-color: var(--accent); box-shadow: 0 0 0 3px color-mix(in srgb, var(--accent) 12%, transparent); }
.global-search input { min-width: 0; flex: 1; border: 0; outline: 0; color: var(--text); background: transparent; }
.global-search kbd { padding: 2px 6px; border: 1px solid var(--line); border-radius: 5px; color: var(--subtle); background: var(--surface); font-size: 10px; }
.header-actions { position: absolute; right: 28px; display: flex; align-items: center; gap: 7px; }
.header-actions > button, .popover-anchor > button {
  position: relative;
  display: grid;
  width: 38px;
  height: 38px;
  place-items: center;
  border-radius: 10px;
  color: var(--muted);
  background: transparent;
  transition: transform .18s ease, background .18s ease;
}
.header-actions button:hover { color: var(--primary); background: var(--primary-soft); transform: scale(1.04); }
.notice-dot { position: absolute; top: 8px; right: 8px; width: 6px; height: 6px; border: 1px solid var(--surface); border-radius: 50%; background: #d85b5b; }
.avatar-button { width: 34px !important; height: 34px !important; font-size: 12px; }
.popover-anchor { position: relative; }
.popover {
  position: absolute;
  top: 45px;
  right: 0;
  z-index: 50;
  width: 180px;
  padding: 8px;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: var(--surface);
  box-shadow: var(--shadow);
}
.notice-popover { padding: 14px; }
.notice-popover strong { font-size: 13px; }
.notice-popover p { margin: 7px 0 0; color: var(--muted); font-size: 12px; }
.user-popover button { display: flex; width: 100%; align-items: center; gap: 9px; padding: 9px; border-radius: 7px; background: transparent; text-align: left; font-size: 13px; }
.user-popover button:hover { background: var(--primary-soft); }

.main-content { min-height: calc(100vh - 60px); padding-top: 0; }
.module-page { min-height: calc(100vh - 60px); }
.page-enter { animation: page-fade .28s ease both; }
@keyframes page-fade { from { opacity: 0; transform: translateY(5px); } to { opacity: 1; transform: none; } }

.chat-page { position: relative; height: calc(100vh - 60px); min-height: 600px; overflow: hidden; background: var(--surface); }
.chat-scroll { height: 100%; overflow-y: auto; padding: 48px clamp(24px, 6vw, 88px) 220px; }
.conversation-timeline {
  position: absolute;
  z-index: 8;
  top: 82px;
  bottom: 188px;
  left: 18px;
  width: 24px;
  cursor: ns-resize;
  touch-action: none;
  user-select: none;
}
.conversation-timeline::before {
  position: absolute;
  top: 0;
  bottom: 0;
  left: 7px;
  width: 1px;
  background: repeating-linear-gradient(to bottom, var(--line-strong) 0 3px, transparent 3px 10px);
  content: '';
}
.timeline-mark {
  position: absolute;
  left: 4px;
  width: 7px;
  height: 1px;
  padding: 0;
  border: 0;
  background: var(--subtle) !important;
  transform: translateY(-50%);
  transition: width .15s ease, background .15s ease;
}
.timeline-mark:hover { width: 14px; background: var(--text) !important; }
.timeline-thumb {
  position: absolute;
  left: 0;
  width: 16px;
  height: 3px;
  border-radius: 999px;
  background: var(--text);
  box-shadow: 0 0 0 4px var(--surface);
  transform: translateY(-50%);
  transition: height .15s ease;
  pointer-events: none;
}
.timeline-thumb.dragging { height: 5px; }
.timeline-preview {
  position: absolute;
  left: 30px;
  display: grid;
  width: 280px;
  gap: 7px;
  padding: 13px 14px;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: var(--surface);
  box-shadow: 0 10px 30px rgba(15, 23, 42, .12);
  transform: translateY(-50%);
  pointer-events: none;
}
.timeline-preview small { color: var(--subtle); font-size: 11px; }
.timeline-preview strong { overflow: hidden; color: var(--text); font-size: 13px; font-weight: 600; line-height: 1.55; }
.empty-chat-state { display: flex; min-height: 100%; align-items: center; justify-content: center; flex-direction: column; gap: 24px; padding: 0 0 90px; }
.empty-chat-logo { display: grid; width: 52px; height: 52px; place-items: center; border: 2px solid var(--line-strong); border-radius: 18px; color: var(--muted); }
.empty-chat-state h1 { margin: 0; color: var(--text); font-size: clamp(24px, 2.4vw, 32px); font-weight: 500; letter-spacing: -.025em; }
.welcome-block { width: min(860px, 100%); margin: 0 auto 38px; }
.eyebrow { color: var(--accent); font-size: 11px; font-weight: 800; letter-spacing: .13em; }
.welcome-block h1, .page-heading h1 { margin: 8px 0 7px; color: var(--text); font-size: clamp(27px, 3vw, 38px); letter-spacing: -.025em; }
.welcome-block p, .page-heading p { margin: 0; color: var(--muted); line-height: 1.65; }
.quick-prompts { display: flex; flex-wrap: wrap; gap: 9px; margin-top: 20px; }
.quick-prompts button {
  min-height: 34px;
  padding: 0 14px;
  border: 1px solid var(--line);
  border-radius: 999px;
  color: var(--muted);
  background: var(--surface);
  transition: .18s ease;
}
.quick-prompts button:hover { border-color: var(--accent); color: var(--primary); background: var(--primary-soft); transform: scale(1.025); }
.message-stream { width: min(760px, 100%); margin: 0 auto; }
.message-row { display: flex; align-items: flex-start; gap: 11px; margin: 26px 0; }
.message-row.user { justify-content: flex-end; }
.ai-avatar { display: grid; width: 32px; height: 32px; place-items: center; flex: none; border: 1px solid var(--line); border-radius: 10px; color: var(--text); background: var(--surface); }
.message-wrap { max-width: min(720px, 82%); }
.message-bubble { padding: 4px 2px; border: 0; border-radius: 0; background: transparent; box-shadow: none; line-height: 1.72; }
.message-row.user .message-bubble { max-width: 620px; padding: 11px 15px; border: 1px solid var(--line); border-radius: 18px; color: var(--text); background: #f1f1f1; }
.workflow-panel { width: min(620px, 100%); margin-bottom: 10px; border: 1px solid var(--line); border-radius: 10px; overflow: hidden; background: var(--surface-soft); }
.workflow-summary { display: flex; width: 100%; align-items: center; gap: 9px; padding: 9px 11px; color: var(--text); background: transparent; text-align: left; }
.workflow-state-dot { width: 8px; height: 8px; flex: none; border-radius: 50%; background: #3a8a62; box-shadow: 0 0 0 3px color-mix(in srgb, #3a8a62 14%, transparent); }
.workflow-state-dot.running { background: var(--accent); box-shadow: 0 0 0 3px color-mix(in srgb, var(--accent) 14%, transparent); animation: workflow-pulse 1.4s ease-in-out infinite; }
.workflow-state-dot.failed { background: #c44f4f; box-shadow: 0 0 0 3px color-mix(in srgb, #c44f4f 14%, transparent); }
.workflow-summary-copy { display: flex; min-width: 0; flex: 1; flex-direction: column; }
.workflow-summary-copy strong { font-size: 12px; }
.workflow-summary-copy small { overflow: hidden; color: var(--muted); font-size: 10px; text-overflow: ellipsis; white-space: nowrap; }
.workflow-toggle { display: inline-flex; flex: none; align-items: center; gap: 2px; color: var(--primary); font-size: 10px; }
.workflow-summary[aria-expanded='true'] .workflow-toggle svg { transform: rotate(180deg); }
.workflow-details { padding: 3px 10px 10px 15px; border-top: 1px solid var(--line); background: var(--surface); }
.workflow-step { position: relative; display: grid; grid-template-columns: 22px minmax(0, 1fr) auto; gap: 8px; padding: 10px 0; }
.workflow-step:not(:last-child)::after { position: absolute; top: 31px; bottom: -3px; left: 10px; width: 1px; background: var(--line-strong); content: ''; }
.workflow-step-index { display: grid; z-index: 1; width: 21px; height: 21px; place-items: center; border: 1px solid var(--line-strong); border-radius: 50%; color: var(--muted); background: var(--surface); font-size: 9px; }
.workflow-step.running .workflow-step-index { border-color: var(--accent); color: var(--accent); }
.workflow-step-copy { min-width: 0; }
.workflow-step-copy strong { display: block; font-size: 11px; }
.workflow-step-copy p { margin: 2px 0 0; color: var(--muted); font-size: 10px; line-height: 1.45; white-space: pre-line; }
.workflow-step-meta { display: flex; flex-wrap: wrap; gap: 4px; margin-top: 5px; }
.workflow-step-meta span { padding: 2px 5px; border-radius: 4px; color: var(--muted); background: var(--surface-soft); font-size: 9px; }
.workflow-step-status { align-self: start; color: #3a8a62; font-size: 9px; }
.workflow-step.running .workflow-step-status { color: var(--accent); }
.workflow-step.failed .workflow-step-index, .workflow-step.failed .workflow-step-status { border-color: #c44f4f; color: #c44f4f; }
@keyframes workflow-pulse { 50% { opacity: .45; } }
.message-bubble > p { margin: 0; }
.message-bubble pre { overflow-x: auto; margin: 15px 0; padding: 15px; border-radius: 10px; color: #dce8f4; background: #15283e; font: 12px/1.65 Consolas, monospace; }
.response-table { overflow-x: auto; margin-top: 15px; }
.response-table table { width: 100%; border-collapse: collapse; font-size: 13px; }
.response-table th, .response-table td { padding: 9px 11px; border: 1px solid var(--line); text-align: left; }
.response-table th { color: var(--primary); background: var(--primary-soft); }
.sources { display: grid; gap: 5px; margin-top: 15px; padding-top: 13px; border-top: 1px solid var(--line); color: var(--muted); font-size: 12px; }
.sources strong { color: var(--text); }
.message-actions { display: flex; gap: 3px; margin-top: 6px; }
.message-actions button { display: grid; width: 30px; height: 30px; place-items: center; border-radius: 7px; color: var(--subtle); background: transparent; }
.message-actions button:hover, .message-actions button.selected { color: var(--primary); background: var(--primary-soft); transform: scale(1.06); }
.typing-bubble { display: flex; gap: 5px; padding: 14px 17px; }
.typing-bubble i, .writing-loading i { width: 6px; height: 6px; border-radius: 50%; background: var(--accent); animation: typing 1s infinite; }
.typing-bubble i:nth-child(2), .writing-loading i:nth-child(2) { animation-delay: .14s; }
.typing-bubble i:nth-child(3), .writing-loading i:nth-child(3) { animation-delay: .28s; }
@keyframes typing { 50% { opacity: .3; transform: translateY(-3px); } }

.composer-zone {
  position: absolute;
  inset: auto 0 0;
  padding: 34px 28px 14px;
  background: linear-gradient(0deg, var(--surface) 78%, color-mix(in srgb, var(--surface) 0%, transparent));
}
.composer-tools { display: flex; width: min(740px, 100%); gap: 8px; margin: 0 auto 8px; }
.composer-tools > button { display: flex; align-items: center; gap: 6px; min-height: 30px; padding: 0 9px; border-radius: 8px; color: var(--muted); background: var(--surface); font-size: 12px; }
.composer-tools > button.active { color: var(--primary); background: var(--primary-soft); }
.mini-switch { width: 24px; height: 14px; padding: 2px; border-radius: 99px; background: var(--line-strong); transition: .2s ease; }
.mini-switch i { display: block; width: 10px; height: 10px; border-radius: 50%; background: #fff; transition: transform .2s ease; }
.composer-tools button.active .mini-switch { background: #356c9f; }
.composer-tools button.active .mini-switch i { transform: translateX(10px); }
.resource-input { display: none; }
.upload-queue { display: flex; width: min(860px, 100%); gap: 8px; margin: 0 auto 8px; overflow-x: auto; }
.upload-item { display: flex; min-width: 210px; max-width: 280px; align-items: center; gap: 8px; padding: 9px 10px; border: 1px solid var(--line); border-radius: 10px; background: var(--surface); }
.upload-preview { width: 48px; height: 48px; flex: none; border-radius: 7px; object-fit: cover; background: var(--surface-soft); }
.file-type-icon { display: inline-grid; width: 30px; height: 34px; flex: none; place-items: center; border-radius: 6px; color: #fff; font-size: 8px; font-style: normal; font-weight: 800; letter-spacing: -.03em; line-height: 1; }
.file-type-icon--pdf { background: #e05252; }
.file-type-icon--ppt { background: #e87532; }
.file-type-icon--excel { background: #2f9b68; }
.file-type-icon--word { background: #3d76c8; }
.file-type-icon--text { background: #64748b; }
.file-type-icon--archive { background: #8b5fc7; }
.file-type-icon--generic { background: #718096; }
.file-type-image { display: block; width: 30px; height: 34px; flex: none; object-fit: contain; }
.upload-file-icon { width: 48px; height: 48px; }
.upload-item > span { min-width: 0; flex: 1; }
.upload-item strong, .upload-item small { display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.upload-item strong { font-size: 12px; }
.upload-item small { margin-top: 3px; color: var(--muted); font-size: 10px; }
.upload-item > button { color: var(--primary); background: transparent; font-size: 11px; }
.message-attachments { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 9px; }
.message-attachment-card { display: flex; min-width: 250px; max-width: 430px; align-items: center; gap: 7px; padding: 5px 7px; border: 1px solid var(--line); border-radius: 8px; background: var(--surface); }
.message-attachment-card.is-image { display: block; max-width: 360px; padding: 0; overflow: hidden; }
.message-image { display: block; width: min(346px, 100%); max-height: 300px; border-radius: 12px; object-fit: cover; cursor: zoom-in; }
.message-file { display: inline-flex; min-width: 0; flex: 1; align-items: center; gap: 5px; padding: 2px; font-size: 11px; }
.message-file > span { min-width: 0; max-width: 290px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.message-file .file-type-icon { width: 24px; height: 28px; font-size: 7px; }
.message-file .file-type-image { width: 30px; height: 34px; }
.message-attachment-actions { display: inline-flex; align-items: center; gap: 3px; }
.message-attachment-card.is-image .message-attachment-actions { justify-content: flex-end; padding: 5px 7px; border-top: 1px solid var(--line); }
.message-attachment-actions button { display: inline-flex; align-items: center; gap: 3px; padding: 4px 6px; border-radius: 5px; color: var(--primary); background: transparent; font-size: 10px; }
.message-attachment-actions button:hover { background: var(--primary-soft); }
.message-row.user .message-image { border: 1px solid rgba(255, 255, 255, .35); }
.chat-composer {
  display: flex;
  width: min(740px, 100%);
  align-items: flex-end;
  gap: 10px;
  margin: 0 auto;
  padding: 10px 10px 10px 14px;
  border: 1px solid var(--line-strong);
  border-radius: 20px;
  background: var(--surface);
  box-shadow: 0 8px 26px rgba(0, 0, 0, .08);
}
.chat-composer:focus-within { border-color: var(--accent); box-shadow: var(--shadow), 0 0 0 3px color-mix(in srgb, var(--accent) 10%, transparent); }
.chat-input { min-height: 46px; max-height: 120px; flex: 1; resize: none; border: 0; outline: 0; color: var(--text); background: transparent; line-height: 1.55; }
.composer-actions { display: flex; align-items: center; gap: 6px; }
.icon-button, .send-button { display: grid; width: 39px; height: 39px; place-items: center; border-radius: 10px; }
.icon-button { color: var(--muted); background: transparent; }
.icon-button:hover { background: var(--primary-soft); transform: scale(1.04); }
.send-button { color: #fff !important; background: #1e3a5f; }
.stop-button { background: #40546b; }
.send-button:disabled { cursor: not-allowed; opacity: .4; }
.composer-zone > small { display: block; margin-top: 7px; color: var(--subtle); font-size: 10px; text-align: center; }
.campus-ai[data-theme="dark"] .history-list button:hover { background: #1d2937; }
.campus-ai[data-theme="dark"] .history-list button.active { background: #263445; }
.campus-ai[data-theme="dark"] .message-row.user .message-bubble { background: #263445; }

.writing-page, .meeting-page { padding: 34px clamp(22px, 4vw, 56px) 44px; }
.page-heading { display: flex; align-items: flex-end; justify-content: space-between; gap: 20px; }
.page-heading h1 { font-size: 30px; }
.type-tabs { display: flex; gap: 7px; margin: 28px 0 18px; overflow-x: auto; padding-bottom: 3px; }
.type-tabs button { min-width: max-content; min-height: 38px; padding: 0 15px; border: 1px solid var(--line); border-radius: 9px; color: var(--muted); background: var(--surface); }
.type-tabs button:hover { transform: scale(1.025); }
.type-tabs button.active { border-color: #1e3a5f; color: #fff; background: #1e3a5f; }
.surface { border: 1px solid var(--line); border-radius: 14px; background: var(--surface); box-shadow: var(--shadow); }
.writing-workspace { display: grid; grid-template-columns: 230px minmax(0, 1fr); gap: 18px; }
.parameter-panel { padding: 20px; }
.panel-title { display: flex; align-items: center; gap: 8px; padding-bottom: 15px; border-bottom: 1px solid var(--line); }
.option-group { margin-top: 20px; }
.option-group > label, .info-block > label { display: block; margin-bottom: 9px; color: var(--muted); font-size: 12px; font-weight: 700; }
.segmented { display: grid; grid-template-columns: repeat(3, 1fr); padding: 3px; border-radius: 9px; background: var(--surface-soft); }
.segmented button { min-height: 32px; border-radius: 7px; color: var(--muted); background: transparent; font-size: 12px; }
.segmented button.active { color: var(--primary); background: var(--surface); box-shadow: 0 2px 8px rgba(30, 58, 95, .08); }
.choice-grid { display: grid; grid-template-columns: repeat(2, 1fr); gap: 7px; }
.choice-grid button { min-height: 35px; border: 1px solid var(--line); border-radius: 8px; color: var(--muted); background: transparent; font-size: 12px; }
.choice-grid button.active { border-color: var(--accent); color: var(--primary); background: var(--primary-soft); }
.result-panel { min-width: 0; overflow: hidden; }
.result-heading, .live-heading { display: flex; min-height: 58px; align-items: center; justify-content: space-between; padding: 0 20px; border-bottom: 1px solid var(--line); }
.result-heading > div, .live-heading > div { display: flex; align-items: center; gap: 8px; }
.result-heading > span, .live-heading > span { color: var(--subtle); font-size: 11px; }
.sample-tip {
  margin: 18px 24px 0;
  padding: 10px 12px;
  border: 1px solid color-mix(in srgb, var(--accent) 22%, var(--line));
  border-radius: 9px;
  color: var(--muted);
  background: var(--primary-soft);
  font-size: 11px;
  line-height: 1.55;
}
.markdown-preview { min-height: 390px; max-height: 520px; overflow-y: auto; padding: 26px 30px; line-height: 1.75; }
.markdown-preview.is-sample { color: color-mix(in srgb, var(--text) 88%, var(--muted)); }
.markdown-preview h1 { margin: 0 0 22px; font-size: 25px; }
.markdown-preview h2 { margin: 22px 0 8px; color: var(--primary); font-size: 17px; }
.markdown-preview p { margin: 7px 0; color: var(--muted); }
.markdown-list-item { display: flex; gap: 9px; padding: 4px 0 4px 8px; color: var(--muted); }
.markdown-list-item span { color: var(--accent); }
.markdown-preview blockquote { margin: 20px 0 0; padding: 12px 15px; border-left: 3px solid var(--accent); color: var(--muted); background: var(--primary-soft); }
.writing-loading { display: flex; min-height: 320px; align-items: center; justify-content: center; gap: 6px; color: var(--muted); }
.writing-loading span { margin-left: 8px; font-size: 13px; }
.result-actions { display: flex; flex-wrap: wrap; gap: 7px; padding: 14px 18px; border-top: 1px solid var(--line); }
.result-actions button { display: flex; align-items: center; gap: 6px; min-height: 34px; padding: 0 11px; border: 1px solid var(--line); border-radius: 8px; color: var(--muted); background: var(--surface); font-size: 12px; }
.result-actions button:hover { color: var(--primary); background: var(--primary-soft); transform: scale(1.025); }
.writing-composer { display: flex; align-items: flex-end; gap: 14px; margin-top: 18px; padding: 14px; }
.writing-composer > div { min-width: 0; flex: 1; }
.writing-composer label { display: block; margin: 0 0 6px 2px; color: var(--muted); font-size: 11px; font-weight: 700; }
.writing-composer input { width: 100%; height: 43px; padding: 0 13px; border: 1px solid var(--line); border-radius: 9px; outline: 0; color: var(--text); background: var(--surface-soft); }
.writing-composer input:focus { border-color: var(--accent); }
.writing-composer > button, .primary-action {
  display: flex;
  min-height: 43px;
  align-items: center;
  justify-content: center;
  gap: 8px;
  padding: 0 18px;
  border-radius: 9px;
  color: #fff !important;
  background: #1e3a5f;
  font-weight: 700;
}
.writing-composer > button:hover, .primary-action:hover { transform: scale(1.025); background: #284d78; }
.writing-composer > button:disabled { opacity: .55; }

.meeting-header-actions { display: flex; gap: 8px; }
.secondary-action {
  display: flex;
  min-height: 42px;
  align-items: center;
  gap: 7px;
  padding: 0 14px;
  border: 1px solid var(--line);
  border-radius: 9px;
  color: var(--primary);
  background: var(--surface);
  font-weight: 650;
}
.secondary-action:hover { transform: scale(1.025); background: var(--primary-soft); }
.hidden-input { display: none; }
.meeting-cards { display: grid; grid-template-columns: repeat(3, minmax(210px, 1fr)); gap: 12px; margin: 26px 0 18px; overflow-x: auto; padding-bottom: 3px; }
.meeting-card {
  position: relative;
  min-width: 210px;
  border: 1px solid var(--line);
  border-radius: 12px;
  background: var(--surface);
  transition: .18s ease;
}
.meeting-card:hover { transform: translateY(-2px); box-shadow: var(--shadow); }
.meeting-card.active { border-color: var(--accent); box-shadow: 0 0 0 2px color-mix(in srgb, var(--accent) 10%, transparent); }
.meeting-select { display: grid; width: 100%; gap: 10px; padding: 16px 44px 16px 16px; border-radius: 12px; background: transparent; text-align: left; }
.meeting-delete {
  position: absolute;
  top: 11px;
  right: 11px;
  display: grid;
  width: 29px;
  height: 29px;
  place-items: center;
  border-radius: 8px;
  color: var(--subtle);
  background: transparent;
}
.meeting-delete:hover { color: #c44f4f; background: color-mix(in srgb, #c44f4f 10%, transparent); transform: scale(1.05); }
.meeting-cards small { display: flex; align-items: center; gap: 5px; color: var(--muted); }
.meeting-list-empty { grid-column: 1 / -1; padding: 22px; border: 1px dashed var(--line-strong); border-radius: 12px; color: var(--muted); background: var(--surface); text-align: center; }
.status { width: max-content; padding: 3px 7px; border-radius: 99px; font-size: 10px; font-weight: 700; }
.status.进行中 { color: #176b50; background: #e8f7f1; }
.status.已结束 { color: #5e6b7c; background: #edf1f5; }
.status.未开始 { color: #8a621c; background: #fbf3df; }
.campus-ai[data-theme="dark"] .status.进行中 { color: #8bd8bd; background: #18382f; }
.campus-ai[data-theme="dark"] .status.已结束 { color: #b6c2d0; background: #253142; }
.campus-ai[data-theme="dark"] .status.未开始 { color: #e4c177; background: #3b3020; }
.meeting-detail { display: grid; grid-template-columns: minmax(220px, 29%) minmax(0, 1fr); gap: 18px; align-items: start; }
.meeting-info { padding: 20px; }
.meeting-fields { display: grid; gap: 12px; margin: 18px 0 24px; }
.meeting-fields label { display: grid; gap: 6px; }
.meeting-fields label > span { color: var(--subtle); font-size: 11px; font-weight: 700; }
.meeting-fields input,
.editable-list input {
  width: 100%;
  min-width: 0;
  height: 36px;
  padding: 0 10px;
  border: 1px solid var(--line);
  border-radius: 8px;
  outline: 0;
  color: var(--text);
  background: var(--surface-soft);
  font-size: 12px;
  transition: border-color .18s ease, box-shadow .18s ease;
}
.meeting-fields input:focus,
.editable-list input:focus {
  border-color: var(--accent);
  box-shadow: 0 0 0 3px color-mix(in srgb, var(--accent) 10%, transparent);
}
.info-block { margin-top: 21px; }
.editable-heading { display: flex; align-items: center; justify-content: space-between; margin-bottom: 9px; }
.editable-heading > label { color: var(--muted); font-size: 12px; font-weight: 700; }
.editable-heading > button {
  display: flex;
  min-height: 27px;
  align-items: center;
  gap: 4px;
  padding: 0 8px;
  border-radius: 7px;
  color: var(--primary);
  background: var(--primary-soft);
  font-size: 11px;
}
.editable-heading > button:hover { transform: scale(1.03); }
.editable-list { display: grid; gap: 7px; }
.editable-list > div { display: grid; grid-template-columns: 23px minmax(0, 1fr) 28px; align-items: center; gap: 6px; }
.editable-list i {
  display: grid;
  width: 23px;
  height: 23px;
  place-items: center;
  border-radius: 50%;
  color: #fff;
  background: #5d7895;
  font-style: normal;
  font-size: 9px;
}
.editable-list > div > button { display: grid; width: 28px; height: 28px; place-items: center; border-radius: 7px; color: var(--subtle); background: transparent; }
.editable-list > div > button:hover { color: #c44f4f; background: color-mix(in srgb, #c44f4f 10%, transparent); }
.attendees { display: flex; flex-wrap: wrap; gap: 7px; }
.attendees span { display: flex; align-items: center; gap: 6px; padding: 5px 8px 5px 5px; border-radius: 99px; color: var(--muted); background: var(--surface-soft); font-size: 11px; }
.attendees i { display: grid; width: 20px; height: 20px; place-items: center; border-radius: 50%; color: #fff; background: #5d7895; font-style: normal; font-size: 9px; }
.info-block ol { display: grid; gap: 8px; margin: 0; padding-left: 20px; color: var(--muted); font-size: 13px; line-height: 1.55; }
.empty-text { color: var(--subtle); font-size: 12px; }
.meeting-output { min-height: 500px; overflow: hidden; }
.live-heading > div:first-child > i { width: 8px; height: 8px; border-radius: 50%; background: var(--line-strong); }
.live-heading > div:first-child > i.active { background: #30a378; box-shadow: 0 0 0 5px rgba(48, 163, 120, .12); animation: live-pulse 1.6s infinite; }
@keyframes live-pulse { 50% { box-shadow: 0 0 0 8px rgba(48, 163, 120, 0); } }
.record-controls { justify-content: flex-end; }
.record-controls > span { color: var(--subtle); font-size: 11px; }
.record-controls > button {
  display: flex;
  min-height: 32px;
  align-items: center;
  gap: 5px;
  padding: 0 10px;
  border: 1px solid var(--line);
  border-radius: 8px;
  color: var(--primary);
  background: var(--surface);
  font-size: 11px;
  font-weight: 700;
}
.record-controls > button:hover { background: var(--primary-soft); transform: scale(1.02); }
.record-controls > button.recording { border-color: color-mix(in srgb, #c44f4f 35%, var(--line)); color: #c44f4f; background: color-mix(in srgb, #c44f4f 8%, var(--surface)); }
.record-controls > button:disabled { cursor: wait; opacity: .6; }
.mic-message { margin: 12px 20px 0; padding: 9px 11px; border-radius: 8px; color: var(--muted); background: var(--primary-soft); font-size: 11px; line-height: 1.5; }
.mic-message.denied,
.mic-message.error { color: #b24646; background: color-mix(in srgb, #c44f4f 9%, var(--surface)); }
.transcript { padding: 8px 22px; }
.transcript article { display: grid; grid-template-columns: 110px minmax(0, 1fr); gap: 14px; padding: 14px 0; border-bottom: 1px solid var(--line); }
.transcript article > div { display: grid; align-content: start; gap: 4px; }
.transcript strong { color: var(--primary); font-size: 12px; }
.transcript time { color: var(--subtle); font-size: 10px; }
.transcript p { margin: 0; color: var(--muted); line-height: 1.65; }
.transcript .interim-row p { color: var(--subtle); font-style: italic; }
.summary-box { margin: 12px 20px; padding: 15px; border: 1px solid color-mix(in srgb, var(--accent) 28%, var(--line)); border-radius: 10px; background: var(--primary-soft); }
.summary-box span { color: var(--primary); font-size: 11px; font-weight: 800; }
.summary-box p { margin: 7px 0 0; color: var(--muted); font-size: 13px; line-height: 1.65; }
.todo-section { padding: 10px 20px 20px; }
.subheading { display: flex; justify-content: space-between; padding: 8px 0; }
.subheading span { color: var(--subtle); font-size: 11px; }
.todo-row { display: grid; grid-template-columns: 18px 1fr auto; align-items: center; gap: 8px; padding: 10px 0; border-top: 1px solid var(--line); font-size: 13px; }
.todo-row small { color: var(--subtle); }
.minutes-content { padding: 22px; }
.minutes-content h2 { margin: 3px 0 9px; color: var(--primary); font-size: 16px; }
.minutes-content > p { margin: 0 0 22px; color: var(--muted); line-height: 1.7; }
.decision-box { margin-bottom: 22px; padding: 14px; border-left: 3px solid #d5a843; border-radius: 0 8px 8px 0; background: color-mix(in srgb, #d5a843 10%, var(--surface)); }
.decision-box strong { color: #9a6f18; font-size: 12px; }
.decision-box p { margin: 6px 0 0; color: var(--muted); line-height: 1.6; }
.editable-todo { display: grid; grid-template-columns: minmax(180px, 1fr) 110px 140px; gap: 8px; margin-top: 8px; }
.editable-todo label { display: flex; align-items: center; gap: 8px; font-size: 12px; }
.editable-todo > input { min-width: 0; height: 34px; padding: 0 8px; border: 1px solid var(--line); border-radius: 7px; color: var(--text); background: var(--surface-soft); font-size: 11px; }
.meeting-empty { display: grid; min-height: 500px; place-content: center; justify-items: center; padding: 30px; text-align: center; }
.meeting-empty > span { display: grid; width: 56px; height: 56px; place-items: center; border-radius: 16px; color: var(--primary); background: var(--primary-soft); }
.meeting-empty h2 { margin: 17px 0 7px; font-size: 20px; }
.meeting-empty p { max-width: 420px; margin: 0; color: var(--muted); line-height: 1.65; }
.meeting-empty-state { display: grid; min-height: 420px; place-content: center; justify-items: center; padding: 30px; text-align: center; }
.meeting-empty-state > span { display: grid; width: 56px; height: 56px; place-items: center; border-radius: 16px; color: var(--primary); background: var(--primary-soft); }
.meeting-empty-state h2 { margin: 16px 0 7px; font-size: 20px; }
.meeting-empty-state p { max-width: 420px; margin: 0; color: var(--muted); line-height: 1.65; }
.meeting-empty-state button { display: flex; min-height: 38px; align-items: center; gap: 6px; margin-top: 18px; padding: 0 14px; border-radius: 9px; color: #fff; background: #1e3a5f; font-weight: 700; }
.mobile-meeting-actions { display: none; }

.mobile-tabs { display: none; }
.toast-message {
  position: fixed;
  z-index: 100;
  right: 26px;
  bottom: 24px;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 11px 15px;
  border: 1px solid var(--line);
  border-radius: 10px;
  color: var(--text);
  background: var(--surface);
  box-shadow: var(--shadow);
  font-size: 13px;
}
.toast-enter-active, .toast-leave-active { transition: .2s ease; }
.toast-enter-from, .toast-leave-to { opacity: 0; transform: translateY(8px); }

@media (max-width: 1050px) {
  .side-nav { width: 220px; }
  .side-nav.collapsed { width: 76px; }
  .app-area { margin-left: 220px; }
  .global-header { left: 220px; }
  .app-area.sidebar-collapsed { margin-left: 76px; }
  .global-header.sidebar-collapsed { left: 76px; }
  .writing-workspace { grid-template-columns: 205px minmax(0, 1fr); }
  .meeting-detail { grid-template-columns: 240px minmax(0, 1fr); }
}

@media (max-width: 760px) {
  .side-nav { display: none; }
  .app-area { margin-left: 0; }
  .global-header { left: 0; display: flex; height: 58px; justify-content: space-between; padding: 0 14px; }
  .app-area.sidebar-collapsed { margin-left: 0; }
  .global-header.sidebar-collapsed { left: 0; }
  .mobile-brand { display: flex; align-items: center; gap: 8px; }
  .mobile-brand .brand-mark { width: 30px; height: 30px; border-radius: 9px; }
  .mobile-brand strong { font-size: 14px; }
  .global-search { display: none; }
  .header-actions { position: static; }
  .desktop-avatar { display: none; }
  .main-content { padding-top: 58px; padding-bottom: 66px; }
  .module-page { min-height: calc(100vh - 184px); }
  .mobile-tabs {
    position: fixed;
    inset: auto 0 0;
    z-index: 40;
    display: grid;
    grid-template-columns: repeat(3, 1fr);
    height: 66px;
    padding: 5px 8px max(5px, env(safe-area-inset-bottom));
    border-top: 1px solid var(--line);
    background: color-mix(in srgb, var(--surface) 94%, transparent);
    backdrop-filter: blur(14px);
  }
  .mobile-tabs button { display: grid; place-items: center; align-content: center; gap: 3px; border-radius: 9px; color: var(--subtle); background: transparent; font-size: 10px; }
  .mobile-tabs button.active { color: var(--primary); background: var(--primary-soft); }
  .chat-page { height: calc(100vh - 184px); min-height: 520px; }
  .chat-scroll { padding: 25px 14px 212px; }
  .conversation-timeline { display: none; }
  .welcome-block { margin-bottom: 28px; }
  .welcome-block h1 { font-size: 26px; }
  .quick-prompts { gap: 7px; }
  .quick-prompts button { padding: 0 11px; font-size: 12px; }
  .message-wrap { max-width: 88%; }
  .message-bubble { font-size: 13px; }
  .composer-zone { padding: 20px 12px 9px; }
  .composer-tools { overflow-x: auto; }
  .composer-tools > button { min-width: max-content; }
  .chat-input { font-size: 13px; }
  .writing-page, .meeting-page { padding: 23px 14px 28px; }
  .page-heading { align-items: flex-start; }
  .page-heading h1 { font-size: 26px; }
  .type-tabs { margin-top: 22px; }
  .writing-workspace { grid-template-columns: 1fr; }
  .parameter-panel { display: grid; grid-template-columns: repeat(3, 1fr); gap: 10px; padding: 14px; }
  .parameter-panel .panel-title { grid-column: 1 / -1; }
  .option-group { margin-top: 0; }
  .choice-grid { grid-template-columns: 1fr 1fr; }
  .markdown-preview { min-height: 330px; padding: 20px; }
  .writing-composer { position: sticky; bottom: 72px; }
  .writing-composer > button { padding: 0 13px; }
  .meeting-heading .meeting-header-actions { display: none; }
  .meeting-cards { grid-template-columns: none; grid-auto-flow: column; grid-auto-columns: 78%; }
  .meeting-detail { grid-template-columns: 1fr; }
  .meeting-info { order: 1; }
  .meeting-output { min-height: 430px; }
  .live-heading { align-items: flex-start; gap: 10px; padding: 12px 14px; }
  .record-controls { flex-wrap: wrap; }
  .record-controls > span { width: 100%; text-align: right; }
  .transcript article { grid-template-columns: 80px 1fr; }
  .editable-todo { grid-template-columns: 1fr 1fr; }
  .editable-todo label { grid-column: 1 / -1; }
  .mobile-meeting-actions { display: grid; grid-template-columns: 1fr 1fr; gap: 8px; margin-top: 14px; }
  .mobile-meeting-actions button { display: flex; min-height: 42px; align-items: center; justify-content: center; gap: 7px; border: 1px solid var(--line); border-radius: 9px; color: var(--primary); background: var(--surface); font-size: 12px; font-weight: 700; }
  .toast-message { right: 14px; bottom: 78px; left: 14px; justify-content: center; }
}

@media print {
  .side-nav, .global-header, .mobile-tabs, .parameter-panel, .type-tabs, .writing-composer, .result-actions, .page-heading { display: none !important; }
  .app-area { margin: 0; }
  .main-content { padding: 0; }
  .writing-page { padding: 0; }
  .writing-workspace { display: block; }
  .result-panel { border: 0; box-shadow: none; }
  .markdown-preview { max-height: none; overflow: visible; }
}
</style>
