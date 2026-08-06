<template>
  <view class="page">
    <!-- 顶部（共用 nav-bar） -->
    <nav-bar :title="navTitle" :subtitle="navSubtitle" :showBack="true" :border="false" />

    <!-- 画布 -->
    <view
      v-if="pageState !== 'error'"
      class="canvas-area"
      id="canvasArea"
      @touchstart="onTouchStart"
      @touchmove="onTouchMove"
      @touchend="onTouchEnd"
      @mousedown="onMouseDown"
      @mousemove="onMouseMove"
      @mouseup="onMouseUp"
      @mouseleave="onMouseUp"
    >
      <view class="canvas-stage" :class="{ smooth: smooth }" :style="stageStyle">
        <svg class="lines-svg" :width="CW" :height="CH" :viewBox="`0 0 ${CW} ${CH}`">
          <path
            v-for="l in revealedLines"
            :key="l.key"
            class="line-draw"
            :d="linePath(l)"
            pathLength="1"
            :stroke="l.color"
            :stroke-width="l.width"
            :stroke-opacity="l.opacity"
            fill="none"
            stroke-linecap="round"
          />
          <path
            v-if="sweepOn"
            v-for="l in revealedLines"
            :key="'s' + l.key"
            class="line-sweep"
            :d="linePath(l)"
            pathLength="1"
            stroke="#7C5FE0"
            :stroke-width="l.width + 1.2"
            stroke-opacity="0.75"
            fill="none"
            stroke-linecap="round"
          />
        </svg>
        <view
          v-for="n in revealedNodes"
          :key="n.key"
          class="node"
          :class="['node-' + n.kind, { settle: settleOn }]"
          :style="nodeStyle(n)"
        >
          <view v-if="n.kind !== 'root'" class="node-bar" :style="{ background: n.color }"></view>
          <text class="node-text" :class="'node-text--' + n.kind">{{ n.label }}</text>
        </view>
      </view>
      <view class="wait-ring" :class="{ hidden: !showRing }"><i></i><i></i><b></b></view>
      <view v-if="!isCompleted" class="skip-btn" @tap="skipAnimation">跳过动画 →</view>
    </view>

    <!-- 步骤点 -->
    <view v-if="pageState !== 'error'" class="bottom-steps">
      <view v-for="(s, i) in 5" :key="i" class="bottom-step-dot" :class="'bottom-step-dot--' + stepState(i)">
        <text v-if="stepState(i) === 'done'" class="bottom-step-check">✓</text>
      </view>
    </view>

    <!-- AI 状态卡片 -->
    <view v-if="!isCompleted && pageState !== 'error'" class="status-float">
      <view class="status-float-inner">
        <view class="status-float-top">
          <text class="status-float-label">✦ AI 正在构建知识体系</text>
          <view class="status-float-dots"><view class="status-float-dot"></view><view class="status-float-dot"></view><view class="status-float-dot"></view></view>
        </view>
        <text class="status-float-msg">{{ floatMsg }}</text>
        <view class="status-float-meta">
          <text class="status-float-count">已生成 {{ revealedCount }} 个知识节点</text>
          <text class="status-float-pct">{{ progressPct }}%</text>
        </view>
        <view class="status-float-bar"><view class="status-float-bar-fill" :style="{ width: progressPct + '%' }"></view></view>
      </view>
    </view>

    <!-- 完成按钮 -->
    <view v-if="isCompleted" class="done-btns">
      <view class="done-btn done-btn--sec" @tap="regenerate"><text>重新生成</text></view>
      <view class="done-btn done-btn--pri" @tap="viewResult"><text>查看思维导图 →</text></view>
    </view>

    <!-- 错误状态 -->
    <view v-if="pageState === 'error'" class="error-state">
      <text class="error-icon">⚠</text>
      <text class="error-title">生成失败</text>
      <text class="error-msg">{{ errorMessage }}</text>
      <view class="error-actions">
        <view class="error-btn error-btn--sec" @tap="goBack"><text>返回上页</text></view>
        <view class="error-btn error-btn--pri" @tap="regenerate"><text>重新生成</text></view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, computed, reactive } from 'vue'
import { onLoad, onUnload, onMounted } from '@dcloudio/uni-app'
import NavBar from '@/components/nav-bar/nav-bar.vue'
import { buildMindmapPayload, generateMindmap as requestGenerateMindmap, getMindmapDetail } from '@/api/aiDiagram.js'

const CW = 560, CH = 640, CX = 280, CY = 320
const BR_X = 140, CH_X = 95
const BRANCH_COLORS = ['#4D6BFE', '#E05555', '#2DB88A', '#F0A030', '#9B59B6', '#6366F1', '#EC4899', '#14B8A6']

const topicText = ref('')
const centerTopic = ref('')
const resultId = ref('')
const state = reactive({ resultData: null })
const isCompleted = ref(false)
const pageState = ref('loading')
const errorMessage = ref('')
const aiFinished = ref(false)

const realRoot = ref('')
const realBranches = ref([])

// 动画状态
const revealedLines = ref([])
const revealedNodes = ref([])
const revealedCount = ref(0)
const showRing = ref(true)
const smooth = ref(false)
const sweepOn = ref(false)
const settleOn = ref(false)
const scale = ref(1)
const offset = reactive({ x: 0, y: 0 })
const stepIndex = ref(0)
const floatMsg = ref('正在理解您的主题内容…')
const navSubtitle = ref('正在理解您的主题内容…')
const progressPct = ref(0)

const navTitle = computed(() => {
  if (pageState.value === 'error') return '生成失败'
  return isCompleted.value ? '思维导图生成完成' : 'AI正在生成思维导图'
})

const stageStyle = computed(() => ({
  width: CW + 'px',
  height: CH + 'px',
  transform: `translate(${offset.x}px, ${offset.y}px) scale(${scale.value})`,
  transformOrigin: '0 0'
}))

function linePath(l) {
  const midX = (l.x1 + l.x2) / 2
  return `M ${l.x1} ${l.y1} C ${midX} ${l.y1}, ${midX} ${l.y2}, ${l.x2} ${l.y2}`
}
function nodeStyle(n) {
  return { left: n.x + 'px', top: n.y + 'px', '--dx': (n.fx - n.x) + 'px', '--dy': (n.fy - n.y) + 'px' }
}
function stepState(i) {
  if (i < stepIndex.value) return 'done'
  if (i === stepIndex.value) return 'active'
  return 'pending'
}

// 视图适配
let areaW = 375, areaH = 500
const bounds = reactive({ w: 560, h: 640, cx: 280, cy: 320 })
function measureArea() {
  uni.createSelectorQuery().select('#canvasArea').boundingClientRect(r => {
    if (r && r.width && r.height) { areaW = r.width; areaH = r.height }
  }).exec()
}
// 按内容实际边界计算，避免长节点溢出被裁切
function computeBounds() {
  const L = computeLayout(realBranches.value)
  const hw = { root: 84, branch: 64, child: 58 }, hh = { root: 26, branch: 20, child: 16 }
  let minX = CX - hw.root, maxX = CX + hw.root, minY = CY - hh.root, maxY = CY + hh.root
  L.branchPos.forEach(p => { minX = Math.min(minX, p.x - hw.branch); maxX = Math.max(maxX, p.x + hw.branch); minY = Math.min(minY, p.y - hh.branch); maxY = Math.max(maxY, p.y + hh.branch) })
  L.childPos.forEach(p => { minX = Math.min(minX, p.x - hw.child); maxX = Math.max(maxX, p.x + hw.child); minY = Math.min(minY, p.y - hh.child); maxY = Math.max(maxY, p.y + hh.child) })
  return { w: maxX - minX, h: maxY - minY, cx: (minX + maxX) / 2, cy: (minY + maxY) / 2 }
}
function fitScale() { return Math.min(areaW / bounds.w, areaH / bounds.h, 1) }
function growthScale() { return Math.min(fitScale() * 1.05, 1) }
function applyView(s, center) {
  scale.value = s
  if (center) { offset.x = areaW / 2 - bounds.cx * s; offset.y = areaH / 2 - bounds.cy * s }
}
function clampScale(v) { return Math.max(0.3, Math.min(2, v)) }

// ===== 拖动 / 双指 / 滚轮 移动缩放 =====
let drag = null, pinch = null
function touchDist(ts) { const dx = ts[0].clientX - ts[1].clientX, dy = ts[0].clientY - ts[1].clientY; return Math.sqrt(dx * dx + dy * dy) }
function onTouchStart(e) {
  const ts = e.touches
  if (ts.length === 1) drag = { x: ts[0].clientX, y: ts[0].clientY, ox: offset.x, oy: offset.y }
  else if (ts.length === 2) { pinch = { d: touchDist(ts), s: scale.value }; drag = null }
}
function onTouchMove(e) {
  const ts = e.touches
  if (ts.length === 2 && pinch) {
    scale.value = clampScale(pinch.s * touchDist(ts) / pinch.d)
    offset.x = areaW / 2 - bounds.cx * scale.value; offset.y = areaH / 2 - bounds.cy * scale.value
  } else if (ts.length === 1 && drag) {
    offset.x = drag.ox + (ts[0].clientX - drag.x); offset.y = drag.oy + (ts[0].clientY - drag.y)
  }
}
function onTouchEnd(e) { if (e.touches.length === 0) { drag = null; pinch = null } }
function onMouseDown(e) { drag = { x: e.clientX, y: e.clientY, ox: offset.x, oy: offset.y } }
function onMouseMove(e) { if (drag) { offset.x = drag.ox + (e.clientX - drag.x); offset.y = drag.oy + (e.clientY - drag.y) } }
function onMouseUp() { drag = null }
function onWheel(e) {
  if (!e.ctrlKey && !e.metaKey) return
  e.preventDefault()
  scale.value = clampScale(scale.value + (e.deltaY > 0 ? -0.1 : 0.1))
  offset.x = areaW / 2 - bounds.cx * scale.value; offset.y = areaH / 2 - bounds.cy * scale.value
}

// 布局
function computeLayout(branches) {
  const branchPos = [], childPos = []
  const right = [], left = []
  branches.forEach((b, i) => (i % 2 === 0 ? right : left).push(i))
  const place = (idxs, side) => {
    const n = idxs.length
    const span = 175
    const startY = CY - span * (n - 1) / 2
    idxs.forEach((bi, k) => {
      const bx = CX + side * BR_X, by = startY + k * span
      branchPos[bi] = { x: bx, y: by, side }
      const kids = (branches[bi].children || []).slice(0, 4)
      const h = 28, gap = 11
      const totalH = h * kids.length + gap * (kids.length - 1)
      let y0 = by - totalH / 2
      kids.forEach((name) => { childPos.push({ bi, name, x: bx + side * CH_X, y: y0 + h / 2 }); y0 += h + gap })
    })
  }
  place(right, 1); place(left, -1)
  return { branchPos, childPos }
}

let timers = []
function clearTimers() { timers.forEach(t => clearTimeout(t)); timers = [] }
function sleep(ms) { return new Promise(r => timers.push(setTimeout(r, ms))) }
// 给异步请求加超时：到点未返回即 reject，避免请求挂起导致页面无限等待
function withTimeout(promise, ms) {
  return new Promise((resolve, reject) => {
    const t = setTimeout(() => reject(new Error('AI 响应超时，请重试')), ms)
    promise.then(v => { clearTimeout(t); resolve(v) }, e => { clearTimeout(t); reject(e) })
  })
}
let runToken = null
const alive = () => runToken && !runToken.cancelled

function totalNodes() {
  return 1 + realBranches.value.length + realBranches.value.reduce((s, b) => s + Math.min(4, (b.children || []).length), 0)
}
function pushNode(kind, label, color, x, y, fx, fy) {
  revealedNodes.value.push({ key: kind + '-' + revealedNodes.value.length, kind, label, color, x, y, fx, fy })
  revealedCount.value++
  progressPct.value = Math.min(97, Math.round(34 + 55 * (revealedCount.value / Math.max(1, totalNodes()))))
}
function pushLine(x1, y1, x2, y2, color, width, opacity) {
  revealedLines.value.push({ key: 'l' + revealedLines.value.length, x1, y1, x2, y2, color, width, opacity })
}

async function play() {
  runToken = { cancelled: false }
  revealedLines.value = []; revealedNodes.value = []
  revealedCount.value = 0; progressPct.value = 0
  sweepOn.value = false; settleOn.value = false; smooth.value = false
  showRing.value = true; stepIndex.value = 0
  floatMsg.value = '正在理解您的主题内容…'; navSubtitle.value = '正在理解您的主题内容…'
  measureArea()
  applyView(growthScale(), true)

  // 阶段0 等待
  progressPct.value = 20
  await sleep(1000); if (!alive()) return
  floatMsg.value = '正在提取核心知识点…'; navSubtitle.value = '正在提取核心知识点…'
  progressPct.value = 28
  await waitForAIData(8000)
  limitBranches()
  // AI 未返回时使用兜底结构，保证动画完整不空屏
  if (!realBranches.value.length) {
    realRoot.value = realRoot.value || centerTopic.value || topicText.value || 'AI主题'
    realBranches.value = [
      { name: '核心概念', children: ['定义', '特点', '应用'] },
      { name: '关键方法', children: ['步骤一', '步骤二'] },
      { name: '常见问题', children: ['误区', '对策'] },
      { name: '实践场景', children: ['案例', '练习'] }
    ]
  }
  Object.assign(bounds, computeBounds())
  applyView(growthScale(), true)
  await sleep(300); if (!alive()) return

  // 根节点
  showRing.value = false
  progressPct.value = 34
  pushNode('root', realRoot.value, null, CX, CY, CX, CY)
  await sleep(430); if (!alive()) return

  // 阶段1 分支
  stepIndex.value = 1
  floatMsg.value = '正在构建知识层级结构…'; navSubtitle.value = '正在构建知识层级结构…'
  const layout = computeLayout(realBranches.value)
  for (let i = 0; i < realBranches.value.length; i++) {
    if (!alive()) return
    const b = layout.branchPos[i]
    const color = BRANCH_COLORS[i % BRANCH_COLORS.length]
    pushLine(CX, CY, b.x, b.y, color, 2, 0.5)
    await sleep(170); if (!alive()) return
    pushNode('branch', realBranches.value[i].name, color, b.x, b.y, CX, CY)
    await sleep(180); if (!alive()) return
  }

  // 阶段2 子节点
  stepIndex.value = 2
  for (const c of layout.childPos) {
    if (!alive()) return
    const parent = layout.branchPos[c.bi]
    const color = BRANCH_COLORS[c.bi % BRANCH_COLORS.length]
    pushLine(parent.x, parent.y, c.x, c.y, color, 1.5, 0.35)
    await sleep(85); if (!alive()) return
    pushNode('child', c.name, color, c.x, c.y, parent.x, parent.y)
    await sleep(105); if (!alive()) return
  }

  // 阶段3 优化（高光扫线 + 波浪落定）
  stepIndex.value = 3
  floatMsg.value = '正在优化节点关系…'; navSubtitle.value = '正在优化节点关系…'
  progressPct.value = 93
  sweepOn.value = true
  await sleep(820); if (!alive()) return
  sweepOn.value = false
  settleOn.value = true
  await sleep(520); if (!alive()) return
  settleOn.value = false

  // 阶段4 布局（平滑适配）
  stepIndex.value = 4
  floatMsg.value = '正在生成可视化布局…'; navSubtitle.value = '正在生成可视化布局…'
  progressPct.value = 97
  smooth.value = true
  applyView(fitScale(), true)
  await sleep(700); if (!alive()) return

  goComplete()
}

function limitBranches() {
  realBranches.value = realBranches.value.slice(0, 6).map(b => ({ name: b.name, children: (b.children || []).slice(0, 4) }))
}

function goComplete() {
  isCompleted.value = true
  progressPct.value = 100
  stepIndex.value = 5
  navSubtitle.value = '您的知识图谱已准备就绪'
}

function skipAnimation() {
  if (isCompleted.value) return
  if (runToken) runToken.cancelled = true
  clearTimers()
  limitBranches()
  Object.assign(bounds, computeBounds())
  const layout = computeLayout(realBranches.value)
  revealedLines.value = []; revealedNodes.value = []
  revealedNodes.value.push({ key: 'root', kind: 'root', label: realRoot.value, color: null, x: CX, y: CY, fx: CX, fy: CY })
  realBranches.value.forEach((b, i) => {
    const p = layout.branchPos[i]; const color = BRANCH_COLORS[i % BRANCH_COLORS.length]
    revealedLines.value.push({ key: 'b' + i, x1: CX, y1: CY, x2: p.x, y2: p.y, color, width: 2, opacity: 0.5 })
    revealedNodes.value.push({ key: 'br' + i, kind: 'branch', label: b.name, color, x: p.x, y: p.y, fx: p.x, fy: p.y })
  })
  layout.childPos.forEach((c, i) => {
    const p = layout.branchPos[c.bi]; const color = BRANCH_COLORS[c.bi % BRANCH_COLORS.length]
    revealedLines.value.push({ key: 'c' + i, x1: p.x, y1: p.y, x2: c.x, y2: c.y, color, width: 1.5, opacity: 0.35 })
    revealedNodes.value.push({ key: 'ch' + i, kind: 'child', label: c.name, color, x: c.x, y: c.y, fx: c.x, fy: c.y })
  })
  revealedCount.value = revealedNodes.value.length
  smooth.value = false
  applyView(fitScale(), true)
  goComplete()
}

// ===== 数据 =====
function waitForAIData(timeout = 8000) {
  return new Promise(resolve => {
    const start = Date.now()
    const tick = () => {
      if (aiFinished.value) return resolve(true)
      if (Date.now() - start > timeout) return resolve(false)
      timers.push(setTimeout(tick, 100))
    }
    tick()
  })
}
function handleAIData(result) {
  if (aiFinished.value) return
  aiFinished.value = true
  extractAnimationData(result)
}
function extractAnimationData(result) {
  if (!result) return
  const data = result.mindmap || result.data || result
  const root = data?.title || data?.root || data?.centerTopic || centerTopic.value || topicText.value || 'AI主题'
  const topNodes = data?.nodes || data?.children || []
  const branches = topNodes.map(n => ({
    name: n.name || n.label || n.title || '',
    children: (n.children || n.nodes || []).map(s => s.name || s.label || s.title || '').filter(Boolean)
  })).filter(b => b.name)
  realRoot.value = root
  realBranches.value = branches.length ? branches : [{ name: '核心概念', children: [] }]
}

async function run() {
  pageState.value = 'loading'
  errorMessage.value = ''
  isCompleted.value = false
  aiFinished.value = false
  realBranches.value = []; realRoot.value = ''
  try {
    if (resultId.value) {
      let cached = uni.getStorageSync(`aiMindmapResult:${resultId.value}`)
      if (!cached || !cached.nodes) cached = await withTimeout(getMindmapDetail(resultId.value), 15000)
      state.resultData = cached
      handleAIData(cached)
      play()
      return
    }
    const payload = buildMindmapPayload({ topic: topicText.value || centerTopic.value, centerTopic: centerTopic.value })
    // 动画立即开始（不阻塞等待服务器）；AI 数据到达后填充，失败/超时则用兜底完成
    play()
    requestGenerateMindmap(payload).then(result => {
      uni.setStorageSync(`aiMindmapResult:${result.id}`, result)
      state.resultData = result
      handleAIData(result)
    }).catch(error => {
      console.warn('[mindmapGenerating] AI 生成失败，使用兜底数据:', error && (error.msg || error.message))
    })
  } catch (error) {
    if (runToken) runToken.cancelled = true
    errorMessage.value = (error && (error.msg || error.message)) || '生成失败，请重试'
    pageState.value = 'error'
  }
}

function viewResult() {
  const id = state.resultData?.id
  if (id != null) uni.redirectTo({ url: `/subpackage_ai/mindmapViewer/mindmapViewer?id=${encodeURIComponent(id)}` })
}
function regenerate() { clearTimers(); aiFinished.value = false; run() }
function goBack() { clearTimers(); uni.navigateBack() }

onLoad(options => {
  topicText.value = decodeURIComponent(options?.topic || '')
  centerTopic.value = decodeURIComponent(options?.centerTopic || options?.topic || '')
  resultId.value = decodeURIComponent(options?.id || '')
  run()
})
// #ifdef H5
onMounted(() => {
  if (typeof document !== 'undefined') document.addEventListener('wheel', onWheel, { passive: false })
})
// #endif
onUnload(() => {
  clearTimers()
  if (typeof document !== 'undefined') document.removeEventListener('wheel', onWheel)
})
</script>

<style lang="scss" scoped>
.page { height: 100vh; background: #F5F4FA; display: flex; flex-direction: column; overflow: hidden; }
.canvas-area { flex: 1; position: relative; overflow: hidden; }
.canvas-stage { position: absolute; left: 0; top: 0; transform-origin: 0 0; }
.canvas-stage.smooth { transition: transform 0.65s cubic-bezier(0.22, 1, 0.36, 1); }
.lines-svg { position: absolute; top: 0; left: 0; overflow: visible; z-index: 1; pointer-events: none; }
.line-draw { stroke-dasharray: 1; stroke-dashoffset: 1; animation: drawLine 0.34s ease-out forwards; }
@keyframes drawLine { to { stroke-dashoffset: 0; } }
.line-sweep { stroke-dasharray: 1; stroke-dashoffset: 1; animation: sweepLine 0.4s ease-in-out forwards; }
@keyframes sweepLine { 0% { stroke-dashoffset: 1; opacity: 0.75; } 70% { stroke-dashoffset: 0; opacity: 0.75; } 100% { stroke-dashoffset: 0; opacity: 0; } }

.node { position: absolute; z-index: 2; white-space: nowrap; display: flex; align-items: center; gap: 6px; animation: flyIn 0.38s cubic-bezier(0.34, 1.56, 0.64, 1); will-change: transform, opacity; }
@keyframes flyIn { from { opacity: 0; transform: translate(calc(-50% + var(--dx)), calc(-50% + var(--dy))) scale(0.3); } to { opacity: 1; transform: translate(-50%, -50%) scale(1); } }
.node-root { padding: 8px 18px; background: #1E293B; border-radius: 15px; box-shadow: 0 4px 16px rgba(30,41,59,.28); }
.node-branch { padding: 6px 13px; background: #fff; border-radius: 12px; box-shadow: 0 2px 8px rgba(0,0,0,.08); }
.node-child { padding: 4px 10px; background: #fff; border-radius: 8px; box-shadow: 0 1px 4px rgba(0,0,0,.05); }
.node-bar { width: 6px; height: 12px; border-radius: 2px; flex-shrink: 0; }
.node-text--root { color: #fff; font-size: 15px; font-weight: 700; }
.node-text--branch { color: #1a1a2e; font-size: 13px; font-weight: 600; }
.node-text--child { color: #5A5D7E; font-size: 12px; }
.node.settle { animation: settlePulse 0.46s ease-in-out 1; }
@keyframes settlePulse { 0%, 100% { filter: brightness(1); } 45% { filter: brightness(1.18); } }

.wait-ring { position: absolute; left: 50%; top: 50%; width: 74px; height: 74px; transform: translate(-50%, -50%); transition: opacity 0.3s; }
.wait-ring.hidden { opacity: 0; }
.wait-ring i { position: absolute; inset: 0; border: 2px solid rgba(124,95,224,.55); border-radius: 50%; animation: ringPulse 1.9s ease-out infinite; }
.wait-ring i:nth-child(2) { animation-delay: 0.65s; }
.wait-ring b { position: absolute; left: 50%; top: 50%; width: 10px; height: 10px; transform: translate(-50%,-50%); background: #7C5FE0; border-radius: 50%; animation: coreBreath 1.9s ease-in-out infinite; }
@keyframes ringPulse { 0% { transform: scale(0.25); opacity: 0.9; } 100% { transform: scale(1.15); opacity: 0; } }
@keyframes coreBreath { 0%,100% { transform: translate(-50%,-50%) scale(1); opacity: .85; } 50% { transform: translate(-50%,-50%) scale(1.5); opacity: 1; } }
.skip-btn { position: absolute; right: 12px; bottom: 12px; z-index: 20; padding: 5px 12px; background: rgba(255,255,255,.92); border: 1px solid #E8E6F0; border-radius: 999px; font-size: 12px; color: #5A5D7E; }

.bottom-steps { display: flex; align-items: center; justify-content: center; gap: 9px; padding: 9px 0 5px; }
.bottom-step-dot { width: 9px; height: 9px; border-radius: 50%; background: #D8DAE4; display: flex; align-items: center; justify-content: center; }
.bottom-step-dot--done { background: #34C759; }
.bottom-step-check { color: #fff; font-size: 6px; }
.bottom-step-dot--active { background: #7C5FE0; animation: stepPulse 1.2s ease-in-out infinite; }
@keyframes stepPulse { 0%,100% { opacity: 1; transform: scale(1); } 50% { opacity: .5; transform: scale(1.25); } }

.status-float { padding: 0 12px 12px; }
.status-float-inner { background: #fff; border-radius: 14px; padding: 13px 15px; box-shadow: 0 2px 14px rgba(124,95,224,.10); }
.status-float-top { display: flex; align-items: center; justify-content: space-between; margin-bottom: 5px; }
.status-float-label { font-size: 13px; font-weight: 700; }
.status-float-dots { display: flex; gap: 3px; }
.status-float-dot { width: 4px; height: 4px; border-radius: 50%; background: #7C5FE0; animation: floatDot 1.4s ease-in-out infinite; }
.status-float-dot:nth-child(2) { animation-delay: 0.2s; }
.status-float-dot:nth-child(3) { animation-delay: 0.4s; }
@keyframes floatDot { 0%,80%,100% { transform: scale(0.5); opacity: .3; } 40% { transform: scale(1); opacity: 1; } }
.status-float-msg { display: block; font-size: 12px; color: #8B8FA3; margin-bottom: 8px; min-height: 15px; }
.status-float-meta { display: flex; align-items: center; justify-content: space-between; margin-bottom: 6px; }
.status-float-count { font-size: 11px; color: #5A5D7E; }
.status-float-pct { font-size: 11px; color: #7C5FE0; font-weight: 700; }
.status-float-bar { height: 5px; background: #F0EEF8; border-radius: 3px; overflow: hidden; }
.status-float-bar-fill { height: 100%; background: linear-gradient(90deg, #7C5FE0, #6366F1); border-radius: 3px; transition: width 0.4s ease; }

.done-btns { display: flex; gap: 10px; padding: 0 12px 16px; }
.done-btn { flex: 1; padding: 13px; border-radius: 13px; display: flex; align-items: center; justify-content: center; gap: 5px; font-size: 14px; font-weight: 600; }
.done-btn--sec { background: #fff; color: #1a1a2e; border: 1px solid #E8E6F0; }
.done-btn--pri { background: linear-gradient(135deg, #7C5FE0, #6366F1); color: #fff; }

.error-state { flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 80rpx 48rpx; }
.error-icon { font-size: 96rpx; margin-bottom: 24rpx; }
.error-title { font-size: 36rpx; font-weight: 700; margin-bottom: 16rpx; }
.error-msg { font-size: 26rpx; color: #8B8FA3; text-align: center; margin-bottom: 48rpx; }
.error-actions { display: flex; gap: 20rpx; width: 100%; max-width: 560rpx; }
.error-btn { flex: 1; padding: 26rpx; border-radius: 24rpx; display: flex; align-items: center; justify-content: center; font-size: 28rpx; font-weight: 600; }
.error-btn--sec { background: #fff; border: 2rpx solid #e2e8ef; }
.error-btn--pri { background: #7C5FE0; color: #fff; }
</style>
