<template>
  <view class="page">
    <!-- 顶部 -->
    <view v-if="pageState !== 'error'" class="header">
      <view class="header-back" @tap="goBack">
        <text class="header-back-icon">‹</text>
      </view>
      <view v-if="!isCompleted" class="header-center">
        <view class="header-title-row">
          <text class="header-sparkle">✦</text>
          <text class="header-title-text">AI正在生成思维导图</text>
        </view>
        <text class="header-subtitle-text">AI 正在为您智能构建专属知识图谱</text>
      </view>
      <view v-else class="header-center">
        <view class="header-title-row">
          <text class="header-done-icon">✓</text>
          <text class="header-title-text header-title-text--done">思维导图生成完成</text>
        </view>
        <text class="header-subtitle-text header-subtitle-text--done">您的知识图谱已准备就绪</text>
      </view>
    </view>

    <!-- 错误状态头部 -->
    <view v-else class="header header--error">
      <view class="header-back" @tap="goBack">
        <text class="header-back-icon">‹</text>
      </view>
    </view>

    <!-- AI 生成日志卡片时间线 -->
    <view v-if="pageState !== 'error'" class="timeline-area">
      <MindMapGenerateTimeline
        :steps="generationSteps"
        :topic-text="topicText || centerTopic"
        :show-status="!isCompleted"
        :status-title="statusTitle"
        :status-msg="statusMsg"
        :generated-count="totalGenerated"
        :estimate-text="estimateText"
      />
    </view>

    <!-- 错误状态 -->
    <view v-if="pageState === 'error'" class="error-state">
      <view class="error-icon">⚠</view>
      <text class="error-title">生成失败</text>
      <text class="error-msg">{{ errorMessage }}</text>
      <view class="error-actions">
        <view class="error-btn error-btn--sec" @tap="goBack">
          <text>返回上页</text>
        </view>
        <view class="error-btn error-btn--pri" @tap="retry">
          <text>重新生成</text>
        </view>
      </view>
    </view>

    <!-- 完成按钮 -->
    <view v-if="isCompleted" class="done-btns">
      <view class="done-btn done-btn--sec" @tap="regenerate">
        <text>重新生成</text>
      </view>
      <view class="done-btn done-btn--pri" @tap="viewResult">
        <text>查看思维导图</text>
        <text class="done-btn-arrow">→</text>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, computed, reactive, nextTick, onUnmounted } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import MindMapGenerateTimeline from './MindMapGenerateTimeline.vue'
import { buildMindmapPayload, generateMindmap as requestGenerateMindmap, getMindmapDetail } from '@/api/aiDiagram.js'

const topicText = ref('')
const centerTopic = ref('')
const depth = ref(3)
const structure = ref('知识梳理')
const detail = ref('详细')
const sourceText = ref('')
const sourceFile = ref('')
const fileId = ref('')
const resultId = ref('')

onLoad((options) => {
  console.log('[mindmapGenerating] onLoad options:', JSON.stringify(options))
  topicText.value = decodeURIComponent(options?.topic || '')
  centerTopic.value = decodeURIComponent(options?.centerTopic || options?.topic || '')
  depth.value = Number(options?.depth || 3)
  structure.value = decodeURIComponent(options?.structure || '知识梳理')
  detail.value = decodeURIComponent(options?.detail || '详细')
  sourceText.value = decodeURIComponent(options?.sourceText || '')
  sourceFile.value = decodeURIComponent(options?.sourceFile || '')
  fileId.value = decodeURIComponent(options?.fileId || '')
  resultId.value = decodeURIComponent(options?.id || '')
  console.log('[mindmapGenerating] topicText:', topicText.value, 'centerTopic:', centerTopic.value, 'resultId:', resultId.value)
  run()
})

// ===== 状态 =====
const state = reactive({ resultData: null })
const isCompleted = ref(false)
const pageState = ref('loading') // loading | animating | completed | error
const errorMessage = ref('')

const aiFinished = ref(false)        // AI 是否返回数据
const animationFinished = ref(false) // 时间线动画是否播放完

// ===== 真实数据 =====
const realBranches = ref([]) // [{name, children:[]}]
const realRoot = ref('')
const totalNodes = ref(0)
const maxDepth = ref(0)

// ===== generationSteps 数据结构 =====
function makeStep(type, title, desc) {
  return {
    type,
    title,
    desc,
    status: 'pending',  // pending | active | done
    keywords: [],
    primaryNodes: [],
    mindMapPreview: null,
    optimizeMessages: [],
    stats: null,
  }
}

const generationSteps = ref([
  makeStep('ANALYZE', '理解需求', '正在分析您输入的主题...'),
  makeStep('EXTRACT', '提取核心知识点', '正在从主题中提取关键知识...'),
  makeStep('BUILD', '构建知识结构', '正在搭建知识层级框架...'),
  makeStep('OPTIMIZE', '优化节点关系', '正在调整结构与分类...'),
  makeStep('LAYOUT', '生成可视化布局', '正在整理最终布局...'),
])

function setStepStatus(type, status) {
  const s = generationSteps.value.find(it => it.type === type)
  if (s) s.status = status
}

function getStep(type) {
  return generationSteps.value.find(it => it.type === type)
}

// ===== 1. 关键词提取（基于用户输入动态生成，禁止写死） =====
// 策略：用停用词 + 标点作为分隔符切分，去掉"请帮我整理一份关于"等引导语，
// 保留"Java"、"思维导图"等真正的核心词，绝不按 2 字机械切分
function extractKeywords(text) {
  if (!text) return []
  const stopWords = [
    // 助词 / 虚词
    '的', '了', '是', '在', '和', '与', '或', '及', '从', '到', '把', '让', '用', '为', '被', '给', '向',
    // 代词
    '我', '你', '他', '她', '它', '们', '我们', '你们', '他们', '自己',
    // 语气 / 请求词
    '请', '帮', '麻烦', '想要', '希望', '觉得', '吧', '吗', '呢', '啊', '嘛', '哦', '嗯',
    // 常见引导短语
    '如何', '怎么', '怎样', '什么', '哪些', '一个', '一些',
    '关于', '研究', '分析', '深入', '浅出', '精通', '掌握', '了解', '熟悉',
    '整理', '总结', '分享', '包含', '加入', '方面', '梳理', '输出', '生成',
    '做', '做一份', '一份', '一下', '能够', '可以', '应该', '需要', '进行',
    '给出', '列出来', '写一份', '出一份', '学习', '路线', '入门', '教程', '指南',
    '基础', '全面', '系统', '完整', '快速',
  ]
  const stopSet = new Set(stopWords)
  // 转义正则特殊字符
  const escaped = stopWords.map(w => w.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'))
  // 合并：停用词 + 标点 + 空白
  const pattern = new RegExp(
    escaped.join('|') + '|[\\s,，、;；。.．/\\\\:：?!？\\n\\r\\t]+',
    'g'
  )
  const tokens = text.split(pattern).map(t => t.trim()).filter(Boolean)

  const result = []
  const seen = new Set()
  for (const t of tokens) {
    if (t.length < 2) continue           // 单字无意义
    if (stopSet.has(t)) continue          // 二次过滤停用词
    if (t.length > 10) continue           // 过长直接跳过，不再机械切分
    if (!seen.has(t)) {
      seen.add(t)
      result.push(t)
    }
    if (result.length >= 6) break
  }
  return result.length ? result : [text.slice(0, Math.min(6, text.length))]
}

// ===== 2. 思维导图预览（从 realBranches 构建） =====
function buildPreview() {
  // 优先用 AI 返回的根节点，没有则用通用占位，避免把用户原文直接展示
  const root = realRoot.value || 'AI 主题'
  const branches = realBranches.value.slice(0, 6).map(b => b.name).filter(Boolean)
  return { root, branches }
}

// ===== 3. 优化消息（基于真实数据动态生成） =====
function buildOptimizeMessages() {
  const messages = []
  const branchCount = realBranches.value.length
  const childTotal = realBranches.value.reduce((sum, b) => sum + (b.children?.length || 0), 0)
  // 按真实统计动态生成
  if (branchCount > 0) {
    messages.push(`已识别 ${branchCount} 个核心知识分支`)
  }
  if (childTotal > 0) {
    messages.push(`已梳理 ${childTotal} 个子知识点`)
  }
  // 找出最长的分支（动态）
  let topBranch = null
  let topCount = 0
  realBranches.value.forEach(b => {
    const c = b.children?.length || 0
    if (c > topCount) {
      topCount = c
      topBranch = b.name
    }
  })
  if (topBranch && topCount > 0) {
    messages.push(`重点强化「${topBranch}」分支`)
  }
  // 找出空分支
  const emptyBranches = realBranches.value.filter(b => !b.children || b.children.length === 0).map(b => b.name)
  if (emptyBranches.length) {
    messages.push(`已为 ${emptyBranches.length} 个分支补充子节点`)
  } else {
    messages.push('已合并重复节点，统一分类层级')
    messages.push('已优化父子节点关系')
  }
  return messages
}

// ===== 4. 布局统计 =====
function buildStats() {
  return {
    nodes: totalNodes.value,
    branches: realBranches.value.length,
    depth: Math.max(2, maxDepth.value),
  }
}

// ===== 阶段总进度文字 =====
const statusTitle = computed(() => {
  if (isCompleted.value) return 'AI 已完成思考'
  const active = generationSteps.value.find(s => s.status === 'active')
  if (!active) return 'AI 思考中...'
  return 'AI 思考中...'
})

const statusMsg = computed(() => {
  const active = generationSteps.value.find(s => s.status === 'active')
  if (!active) {
    if (isCompleted.value) return `已生成 ${totalNodes.value} 个知识节点`
    return '正在准备...'
  }
  return active.desc || active.title
})

const totalGenerated = computed(() => totalNodes.value)

const estimateText = computed(() => {
  if (isCompleted.value) return '已完成'
  return '预计 3 秒完成'
})

// ===== 计时器 =====
let timers = []
function clearTimers() {
  timers.forEach(t => clearTimeout(t))
  timers = []
}
function sleep(ms) {
  return new Promise(resolve => {
    timers.push(setTimeout(resolve, ms))
  })
}

// ===== 主流程：分阶段驱动时间线 =====
async function driveTimeline() {
  pageState.value = 'animating'

  // ===== 阶段 1：理解需求 =====
  setStepStatus('ANALYZE', 'active')
  const analyzeStep = getStep('ANALYZE')
  analyzeStep.desc = '正在分析您的主题...'
  await sleep(400)
  // 动态提取关键词
  analyzeStep.keywords = extractKeywords(topicText.value || centerTopic.value || '')
  await sleep(700)
  analyzeStep.status = 'done'

  // ===== 阶段 2：提取核心知识点 =====
  setStepStatus('EXTRACT', 'active')
  const extractStep = getStep('EXTRACT')
  // 等待 AI 数据（如果在动画进行中 AI 还没返回，使用 topicText 兜底，避免卡住）
  await sleep(500)
  extractStep.desc = '已识别核心知识点，正在提取...'

  // 等到 AI 返回数据
  await waitForAIData(8000)

  if (realBranches.value.length) {
    extractStep.primaryNodes = realBranches.value.map(b => b.name).filter(Boolean)
  } else {
    // 兜底：使用关键词作为节点
    extractStep.primaryNodes = extractKeywords(topicText.value || centerTopic.value || 'AI 主题')
  }
  await sleep(700)
  extractStep.status = 'done'

  // ===== 阶段 3：构建知识结构 =====
  setStepStatus('BUILD', 'active')
  const buildStep = getStep('BUILD')
  buildStep.desc = '正在搭建知识层级框架...'
  await sleep(400)
  // 模拟分步生成：先根节点，再一级，最后二级
  if (realBranches.value.length) {
    const preview = buildPreview()
    // 先放根节点
    buildStep.mindMapPreview = { root: preview.root, branches: [] }
    await sleep(500)
    // 逐个加入分支
    for (let i = 0; i < preview.branches.length; i++) {
      const cur = preview.branches.slice(0, i + 1)
      buildStep.mindMapPreview = { root: preview.root, branches: cur }
      await sleep(220)
    }
  } else {
    // 兜底：用提取出的关键词作为节点预览，根节点用通用占位
    buildStep.mindMapPreview = {
      root: 'AI 主题',
      branches: extractKeywords(topicText.value || centerTopic.value || 'AI 主题'),
    }
  }
  await sleep(500)
  buildStep.status = 'done'

  // ===== 阶段 4：优化节点关系 =====
  setStepStatus('OPTIMIZE', 'active')
  const optimizeStep = getStep('OPTIMIZE')
  optimizeStep.desc = 'AI 正在调整结构...'
  await sleep(300)
  // 逐条显示优化消息
  const messages = buildOptimizeMessages()
  for (let i = 0; i < messages.length; i++) {
    optimizeStep.optimizeMessages = messages.slice(0, i + 1)
    await sleep(400)
  }
  optimizeStep.status = 'done'

  // ===== 阶段 5：生成可视化布局 =====
  setStepStatus('LAYOUT', 'active')
  const layoutStep = getStep('LAYOUT')
  layoutStep.desc = '正在整理最终布局...'
  await sleep(300)
  // 计算统计
  layoutStep.stats = buildStats()
  await sleep(700)
  layoutStep.status = 'done'

  // 全部完成，等待 1.2s 让用户看到完成态
  animationFinished.value = true
  await sleep(1200)
  checkAndNavigate()
}

// 等待 AI 数据
function waitForAIData(timeout = 8000) {
  return new Promise((resolve) => {
    const start = Date.now()
    const tick = () => {
      if (aiFinished.value) {
        resolve(true)
        return
      }
      if (Date.now() - start > timeout) {
        resolve(false) // 超时也继续，避免卡住
        return
      }
      timers.push(setTimeout(tick, 100))
    }
    tick()
  })
}

// ===== AI 数据到达处理 =====
function handleAIData(result) {
  if (aiFinished.value) return
  aiFinished.value = true
  extractAnimationData(result)
}

function checkAndNavigate() {
  if (aiFinished.value && animationFinished.value && !isCompleted.value) {
    isCompleted.value = true
    // 延迟一点再显示完成按钮
    setTimeout(() => {
      // 这里不自动跳转，让用户点击查看按钮
    }, 300)
  }
}

// ===== 从 AI 结果提取数据 =====
function extractAnimationData(result) {
  if (!result) return
  const data = result.mindmap || result.data || result
  const root = data?.title || data?.root || data?.centerTopic || centerTopic.value || topicText.value || 'AI主题'
  const topNodes = data?.nodes || data?.children || []

  const branches = topNodes.map(n => ({
    name: n.name || n.label || n.title || '',
    children: (n.children || n.nodes || []).map(s => s.name || s.label || s.title || '').filter(Boolean),
  })).filter(b => b.name)

  realRoot.value = root
  realBranches.value = branches

  // 计算总节点数和最大深度
  let total = 0
  let depth = 1
  branches.forEach(b => {
    total += 1 + (b.children?.length || 0)
    if (b.children?.length) {
      b.children.forEach(c => {
        if (Array.isArray(c?.children)) depth = Math.max(depth, 2)
      })
    }
  })
  totalNodes.value = Math.max(total, branches.reduce((s, b) => s + 1, 0))
  maxDepth.value = Math.max(2, depth)
}

// ===== 主入口 =====
async function run() {
  pageState.value = 'loading'
  errorMessage.value = ''
  aiFinished.value = false
  animationFinished.value = false
  isCompleted.value = false
  realBranches.value = []
  realRoot.value = ''
  totalNodes.value = 0
  maxDepth.value = 0
  // 重置 steps
  generationSteps.value = [
    makeStep('ANALYZE', '理解需求', '正在分析您输入的主题...'),
    makeStep('EXTRACT', '提取核心知识点', '正在从主题中提取关键知识...'),
    makeStep('BUILD', '构建知识结构', '正在搭建知识层级框架...'),
    makeStep('OPTIMIZE', '优化节点关系', '正在调整结构与分类...'),
    makeStep('LAYOUT', '生成可视化布局', '正在整理最终布局...'),
  ]

  try {
    // 启动时间线动画（与 AI 调用并发）
    driveTimeline().catch(err => console.error('[mindmapGenerating] timeline error:', err))

    if (resultId.value) {
      // 从缓存或后端加载已有数据
      let result = uni.getStorageSync(`aiMindmapResult:${resultId.value}`)
      if (!result || !result.nodes) {
        result = await getMindmapDetail(resultId.value)
        if (result) {
          uni.setStorageSync(`aiMindmapResult:${resultId.value}`, result)
        }
      }
      if (!result || !result.nodes || !result.nodes.length) {
        throw new Error('未找到该思维导图数据')
      }
      state.resultData = result
      handleAIData(result)
      return
    }

    // 正常生成流程
    const payload = buildMindmapPayload({
      topic: topicText.value || centerTopic.value,
      centerTopic: centerTopic.value,
      depth: String(depth.value),
      structure: structure.value,
      detail: detail.value,
      sourceText: sourceText.value,
      sourceFile: sourceFile.value,
      fileId: fileId.value,
    })
    console.log('[mindmapGenerating] 请求 payload:', JSON.stringify(payload))
    const result = await requestGenerateMindmap(payload)
    console.log('[mindmapGenerating] API 返回:', JSON.stringify(result).slice(0, 200))
    if (result && result.id) {
      uni.setStorageSync(`aiMindmapResult:${result.id}`, result)
    }
    state.resultData = result
    handleAIData(result)
  } catch (error) {
    console.error('[mindmapGenerating] 生成失败，完整错误:', error)
    console.error('[mindmapGenerating] 错误状态码:', error?.statusCode)
    console.error('[mindmapGenerating] 错误信息:', error?.msg || error?.message)

    let msg = '生成失败，请重试'
    if (error?.statusCode === 401 || error?.code === 401) {
      msg = '请先登录后再生成'
    } else if (error?.statusCode === 404) {
      msg = 'AI 服务不可用，请确认后端已启动'
    } else if (error?.statusCode >= 500) {
      msg = '服务器处理失败，请稍后重试'
    } else if (error?.msg || error?.message) {
      msg = error.msg || error.message
    }
    errorMessage.value = msg
    pageState.value = 'error'
    clearTimers()
    uni.showToast({ title: msg, icon: 'none', duration: 2500 })
  }
}

function retry() {
  clearTimers()
  run()
}

function navigateToResult() {
  const id = state.resultData?.id
  if (id) {
    uni.redirectTo({ url: `/subpackage_ai/mindmapViewer/mindmapViewer?id=${encodeURIComponent(id)}` })
  }
}

function goBack() { clearTimers(); uni.navigateBack() }
function regenerate() { clearTimers(); uni.navigateBack() }
function viewResult() { navigateToResult() }

onUnmounted(() => {
  clearTimers()
})
</script>

<style lang="scss" scoped>
/* ===== 页面整体 ===== */
.page {
  height: 100vh;
  background: #F5F4FA;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

/* ===== 顶部 ===== */
.header {
  background: #fff;
  padding: 28rpx 24rpx 24rpx;
  position: relative;
  z-index: 10;
  flex-shrink: 0;
}
.header-back {
  position: absolute;
  left: 16rpx;
  top: 24rpx;
  width: 56rpx;
  height: 56rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 2;
}
.header-back-icon {
  font-size: 48rpx;
  color: #1a1a2e;
  font-weight: 300;
}
.header-center {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 0 80rpx;
}
.header-title-row {
  display: flex;
  align-items: center;
  gap: 8rpx;
}
.header-sparkle {
  font-size: 26rpx;
  color: #7C5FE0;
  animation: sparkleRotate 2.4s linear infinite;
  display: inline-block;
}
@keyframes sparkleRotate {
  from { transform: rotate(0deg); }
  to { transform: rotate(360deg); }
}
.header-title-text {
  font-size: 30rpx;
  font-weight: 700;
  color: #1a1a2e;
}
.header-title-text--done {
  font-size: 32rpx;
  color: #10B981;
}
.header-done-icon {
  font-size: 28rpx;
  color: #10B981;
  font-weight: 700;
}
.header-subtitle-text {
  font-size: 24rpx;
  color: #8B8FA3;
  margin-top: 6rpx;
  width: 100%;
  text-align: center;
  white-space: normal;
  word-break: break-all;
  line-height: 1.4;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.header-subtitle-text--done {
  color: #B0B3C5;
}

/* ===== 时间线区域 ===== */
.timeline-area {
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  background: #F5F4FA;
}

/* ===== 完成按钮 ===== */
.done-btns {
  flex-shrink: 0;
  display: flex;
  gap: 20rpx;
  padding: 16rpx 24rpx 32rpx;
  background: #F5F4FA;
}
.done-btn {
  flex: 1;
  padding: 26rpx;
  border-radius: 24rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8rpx;
  font-size: 28rpx;
  font-weight: 600;
}
.done-btn--sec {
  background: #fff;
  color: #1a1a2e;
  border: 2rpx solid #E8E6F0;
}
.done-btn--pri {
  background: linear-gradient(135deg, #7C5FE0, #6366F1);
  color: #fff;
}
.done-btn-arrow {
  font-size: 28rpx;
  margin-left: 4rpx;
}

/* ===== 错误状态 ===== */
.header--error {
  background: #fff;
  padding: 28rpx 24rpx 24rpx;
  position: relative;
  z-index: 10;
}
.error-state {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 80rpx 48rpx;
}
.error-icon {
  width: 120rpx;
  height: 120rpx;
  border-radius: 50%;
  background: #FEF2F2;
  color: #EF4444;
  font-size: 64rpx;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 24rpx;
}
.error-title {
  font-size: 36rpx;
  font-weight: 700;
  color: #1a1a2e;
  margin-bottom: 16rpx;
}
.error-msg {
  font-size: 26rpx;
  color: #8B8FA3;
  text-align: center;
  line-height: 1.6;
  margin-bottom: 48rpx;
  word-break: break-all;
}
.error-actions {
  display: flex;
  gap: 20rpx;
  width: 100%;
  max-width: 560rpx;
}
.error-btn {
  flex: 1;
  padding: 26rpx;
  border-radius: 24rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28rpx;
  font-weight: 600;
}
.error-btn--sec {
  background: #fff;
  color: #1a1a2e;
  border: 2rpx solid #E8E6F0;
}
.error-btn--pri {
  background: linear-gradient(135deg, #7C5FE0, #6366F1);
  color: #fff;
}
</style>
