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
          <text class="header-title-text">AI 正在生成流程图</text>
        </view>
        <text class="header-subtitle-text">{{ stageSubtitle }}</text>
      </view>
      <view v-else class="header-center">
        <view class="header-title-row">
          <text class="header-done-icon">✓</text>
          <text class="header-title-text header-title-text--done">流程图生成完成</text>
        </view>
        <text class="header-subtitle-text header-subtitle-text--done">流程结构已确认，可导出或继续优化</text>
      </view>
    </view>

    <!-- 错误头部 -->
    <view v-else class="header header--error">
      <view class="header-back" @tap="goBack">
        <text class="header-back-icon">‹</text>
      </view>
    </view>

    <!-- 画布 -->
    <view v-if="pageState !== 'error'" class="canvas-area">
      <view class="canvas-inner" :style="canvasStyle">
        <view class="canvas-stage" :style="stageStyle">
          <!-- 连线层 -->
          <svg class="lines-svg" :width="svgW" :height="svgH">
            <defs>
              <marker id="fcArrow" viewBox="0 0 10 10" refX="8" refY="5" markerWidth="6.5" markerHeight="6.5" orient="auto">
                <path d="M 0 1 L 8 5 L 0 9 Z" fill="#91a6ba" />
              </marker>
            </defs>
            <!-- 骨架虚线 -->
            <path
              v-for="edge in ghostEdgesList"
              :key="'g' + edge.key"
              class="ghost-edge"
              :d="edge.path"
            />
            <!-- 实线（描线生长） -->
            <path
              v-for="edge in solidEdgesList"
              :key="'e' + edge.key"
              class="edge-draw"
              :class="{ 'edge-draw--back': edge.kind === 'back' }"
              :d="edge.path"
              pathLength="1"
              :marker-end="isH5 && edge.kind !== 'back' ? 'url(#fcArrow)' : ''"
            />
          </svg>

          <!-- 骨架占位 -->
          <view
            v-for="node in ghostNodesList"
            :key="'gn' + node.id"
            class="ghost"
            :class="{ 'ghost--pill': isTerminal(node) }"
            :style="nodeBox(node)"
          ></view>

          <!-- 流程节点 -->
          <view
            v-for="node in inkedNodesList"
            :key="'n' + node.id"
            class="fn fn-in"
            :class="['fn--' + nodeType(node), { settle: settleOn }]"
            :style="nodeBox(node)"
            :data-level="node.level"
          >
            <text class="fn-name">{{ node.name }}</text>
          </view>

          <!-- 边标签 -->
          <view
            v-for="edge in labelEdgesList"
            :key="'l' + edge.key"
            class="edge-label"
            :style="labelPos(edge)"
          >
            <text>{{ edge.label }}</text>
          </view>
        </view>
      </view>
      <view class="wait-ring" :class="{ hidden: !showRing }"><i></i><i></i><b></b></view>
      <view v-if="!isCompleted" class="skip-btn" @tap="skipAnimation">跳过动画 →</view>
    </view>

    <!-- 步骤点 -->
    <view v-if="!isCompleted && pageState !== 'error'" class="bottom-steps">
      <view v-for="(s, idx) in steps" :key="idx" class="bottom-step-dot" :class="'bottom-step-dot--' + stepStatus(idx)">
        <text v-if="stepStatus(idx) === 'done'" class="bottom-step-check">✓</text>
      </view>
    </view>

    <!-- AI 状态卡片 -->
    <view v-if="!isCompleted && pageState !== 'error'" class="status-float">
      <view class="status-float-inner">
        <view class="status-float-top">
          <text class="status-float-label">✦ AI 正在构建流程</text>
          <view class="status-float-dots"><view class="status-float-dot"></view><view class="status-float-dot"></view><view class="status-float-dot"></view></view>
        </view>
        <text class="status-float-msg">{{ waitingText }}</text>
        <view class="status-float-meta">
          <text class="status-float-count">已生成 {{ inkedCount }} 个节点</text>
          <text class="status-float-pct">{{ progressPercent }}%</text>
        </view>
        <view class="status-float-bar"><view class="status-float-bar-fill" :style="{ width: progressPercent + '%' }"></view></view>
      </view>
    </view>

    <!-- 错误状态 -->
    <view v-if="pageState === 'error'" class="error-state">
      <view class="error-icon">⚠️</view>
      <text class="error-title">生成失败</text>
      <text class="error-msg">{{ errorMessage }}</text>
      <view class="error-actions">
        <view class="error-btn error-btn--sec" @tap="goBack"><text>返回上页</text></view>
        <view class="error-btn error-btn--pri" @tap="retry"><text>重新生成</text></view>
      </view>
    </view>

    <!-- 完成按钮 -->
    <view v-if="isCompleted" class="done-btns">
      <view class="done-btn done-btn--sec" @tap="goBack"><text>重新描述</text></view>
      <view class="done-btn done-btn--pri" @tap="viewResult"><text>查看流程图</text><text class="done-btn-arrow">→</text></view>
    </view>
  </view>
</template>

<script setup>
import { ref, computed, reactive } from 'vue'
import { onLoad, onUnload } from '@dcloudio/uni-app'
import { generateFlowchart as requestGenerateFlowchart, getErrorMessage } from '@/api/aiDiagram.js'
import { layoutFlowchart, inkSequence, FLOW_NODE_W, FLOW_NODE_H } from '../flowchartLayout.js'

const state = reactive({ resultData: null, laid: null, seq: [] })
const isCompleted = ref(false)
const isH5 = ref(false)
const pageState = ref('loading')
const errorMessage = ref('')

const steps = [{}, {}, {}, {}, {}]
const stageIndex = ref(0)
const stageSubtitle = computed(() => ([
  '正在理解流程描述…',
  '正在构建流程骨架…',
  '正在填充流程节点…',
  '正在优化分支与回流…',
  '正在整理布局…'
][stageIndex.value] || ''))
const waitingText = computed(() => ([
  '解析角色与审批环节…',
  '蓝图已生成，开始逐节点墨实…',
  '按流程顺序生长节点与连线…',
  '展开判断分支，连接退回路径…',
  '微调位置，准备输出…'
][stageIndex.value] || ''))
function stepStatus(idx) {
  if (idx < stageIndex.value) return 'done'
  if (idx === stageIndex.value) return 'active'
  return 'pending'
}
const progressPercent = computed(() => {
  if (isCompleted.value) return 100
  return [18, 32, 62, 92, 97][stageIndex.value] || 0
})

// ===== 布局数据 =====
const laidNodes = computed(() => state.laid?.nodes || [])
const laidEdges = computed(() => state.laid?.edges || [])
const skeletonOn = ref(false)
const settleOn = ref(false)
const showRing = ref(true)
const inkedIds = reactive({})
const drawnKeys = reactive({})
const inkedCount = ref(0)

// 过滤列表（避免 v-if 与 v-for 同元素的优先级问题）
const ghostNodesList = computed(() => laidNodes.value.filter(n => skeletonOn.value && !inkedIds[n.id]))
const ghostEdgesList = computed(() => laidEdges.value.filter(e => skeletonOn.value && !drawnKeys[e.key]))
const solidEdgesList = computed(() => laidEdges.value.filter(e => drawnKeys[e.key]))
const inkedNodesList = computed(() => laidNodes.value.filter(n => inkedIds[n.id]))
const labelEdgesList = computed(() => laidEdges.value.filter(e => e.label && drawnKeys[e.key]))

// ===== 画布尺寸 =====
const systemInfo = uni.getSystemInfoSync()
const SCREEN_W = systemInfo.windowWidth || 375
const SCREEN_H = systemInfo.windowHeight || 667
const rpx2px = SCREEN_W / 750
const canvasW = ref(Math.round(SCREEN_W * 1.4))
const canvasH = ref(Math.round((SCREEN_H - 480 * rpx2px) * 1.4))
const svgW = computed(() => canvasW.value)
const svgH = computed(() => canvasH.value)
const canvasStyle = computed(() => ({ width: canvasW.value + 'px', height: canvasH.value + 'px' }))

const scale = ref(1)
const offset = reactive({ x: 0, y: 0 })
const cameraY = ref(0)
const smooth = ref(false)
const stageStyle = computed(() => ({
  width: canvasW.value + 'px',
  height: canvasH.value + 'px',
  transform: `translate(${offset.x}px, ${cameraY.value}px) scale(${scale.value})`,
  transformOrigin: '0 0',
  transition: smooth.value ? 'transform 0.6s cubic-bezier(0.22,1,0.36,1)' : 'none'
}))

function nodeBox(node) {
  return {
    left: node.cx + 'px',
    top: node.cy + 'px',
    width: FLOW_NODE_W + 'px',
    height: FLOW_NODE_H + 'px'
  }
}
function labelPos(edge) {
  return { left: (edge.x1 + edge.x2) / 2 + 'px', top: ((edge.y1 + edge.y2) / 2 - 14) + 'px' }
}
function isTerminal(node) { return nodeType(node) === 'start' || nodeType(node) === 'end' }
function nodeType(node) { return node.type || 'action' }

function centerOn() {
  offset.x = Math.round(canvasW.value / 2 * (1 - scale.value))
  cameraY.value = Math.round(canvasH.value / 2 * (1 - scale.value))
}
function fitToView() {
  const view = { w: SCREEN_W, h: Math.max(360, SCREEN_H - 400 * rpx2px) }
  const fit = Math.min(view.w / canvasW.value, view.h / canvasH.value, 1)
  scale.value = Math.max(0.3, Math.min(fit, 1))
  centerOn()
}

// 镜头跟随：让目标 level 的节点行对齐 canvas-area 垂直中心偏上 1/3 位
// 只在换 level 时调用，避免同层节点生成时频繁抖动
function followCameraLevel(level) {
  return new Promise(resolve => {
    const nodeSel = `.fn[data-level="${level}"]`
    uni.createSelectorQuery()
      .select('.canvas-area').boundingClientRect()
      .select(nodeSel).boundingClientRect()
      .exec(res => {
        const canvas = res && res[0]
        const node = res && res[1]
        if (!canvas || !node) { resolve(); return }
        // 节点中心相对 canvas-area 顶部的偏移（已含 scale）
        const nodeCenterY = (node.top - canvas.top) + node.height / 2
        // 目标：节点中心对齐 canvas-area 垂直 1/3 位（偏上，露出下一行）
        const targetCameraY = canvas.height / 3 - nodeCenterY
        cameraY.value = targetCameraY
        resolve()
      })
  })
}

// ===== 动画流程 =====
let timers = []
function clearTimers() { timers.forEach(t => clearTimeout(t)); timers = [] }
function sleep(ms) { return new Promise(r => timers.push(setTimeout(r, ms))) }

async function runAnimation() {
  const laid = state.laid
  canvasW.value = Math.max(canvasW.value, laid.canvasW)
  canvasH.value = Math.max(canvasH.value, laid.canvasH)
  smooth.value = false
  scale.value = Math.min(1, 1.0)
  centerOn()

  // 阶段0 等待
  stageIndex.value = 0
  showRing.value = true
  await sleep(600)

  // 阶段1 骨架
  stageIndex.value = 1
  showRing.value = false
  skeletonOn.value = true
  await sleep(700)

  // 阶段2 墨实
  stageIndex.value = 2
  let lastFollowLevel = -1
  for (const item of state.seq) {
    if (item.node) {
      inkedIds[item.node.id] = true
      inkedCount.value += 1
      if (inkedCount.value / laidNodes.value.length > 0.5) stageIndex.value = 2
      // 换 level 时镜头跟随到当前节点行
      if (item.node.level !== lastFollowLevel) {
        smooth.value = true
        await followCameraLevel(item.node.level)
        lastFollowLevel = item.node.level
        await sleep(120)
      }
      await sleep(170)
    } else {
      drawnKeys[item.edge.key] = true
      await sleep(200)
    }
  }

  // 阶段3 优化
  stageIndex.value = 3
  settleOn.value = true
  await sleep(600)

  // 阶段4 布局
  stageIndex.value = 4
  smooth.value = true
  // 镜头回到全景
  fitToView()
  await sleep(650)

  isCompleted.value = true
}

// ===== 主流程 =====
async function run() {
  pageState.value = 'loading'
  errorMessage.value = ''
  try {
    const payload = uni.getStorageSync('aiFlowchartPendingPayload')
    if (!payload || !payload.description) throw new Error('缺少流程描述')
    const result = await requestGenerateFlowchart(payload)
    uni.setStorageSync(`aiFlowchartResult:${result.id}`, result)
    startAnimation(result)
  } catch (error) {
    errorMessage.value = getErrorMessage(error, '生成失败，请重试')
    pageState.value = 'error'
    uni.showToast({ title: errorMessage.value, icon: 'none', duration: 2500 })
  }
}

function startAnimation(result) {
  state.resultData = result
  // #ifdef H5
  isH5.value = true
  // #endif
  const laid = layoutFlowchart(result)
  if (!laid.nodes.length) {
    errorMessage.value = 'AI 未生成有效内容，请稍后重试'
    pageState.value = 'error'
    return
  }
  state.laid = laid
  state.seq = inkSequence(laid)
  pageState.value = 'animating'
  runAnimation()
}

function skipAnimation() {
  clearTimers()
  skeletonOn.value = false
  settleOn.value = false
  laidNodes.value.forEach(n => { if (!inkedIds[n.id]) { inkedIds[n.id] = true; inkedCount.value += 1 } })
  laidEdges.value.forEach(e => { drawnKeys[e.key] = true })
  smooth.value = false
  fitToView()
  isCompleted.value = true
}

function retry() {
  clearTimers()
  Object.keys(inkedIds).forEach(k => delete inkedIds[k])
  Object.keys(drawnKeys).forEach(k => delete drawnKeys[k])
  inkedCount.value = 0
  skeletonOn.value = false
  settleOn.value = false
  isCompleted.value = false
  stageIndex.value = 0
  run()
}

function viewResult() {
  const id = state.resultData?.id
  if (id) uni.redirectTo({ url: `/subpackage_ai/flowchartViewer/flowchartViewer?id=${encodeURIComponent(id)}` })
}
function goBack() { clearTimers(); uni.navigateBack() }

onLoad(() => { run() })
onUnload(() => { clearTimers() })
</script>

<style lang="scss" scoped>
.page { height: 100vh; background: #F4F6F9; display: flex; flex-direction: column; overflow: hidden; }

.header { background: #fff; padding: 28rpx 24rpx 24rpx; position: relative; z-index: 10; }
.header--error { background: #fff; padding: 28rpx 24rpx 24rpx; position: relative; z-index: 10; }
.header-back { position: absolute; left: 16rpx; top: 24rpx; width: 56rpx; height: 56rpx; display: flex; align-items: center; justify-content: center; }
.header-back-icon { font-size: 48rpx; color: #1e344f; font-weight: 300; }
.header-center { display: flex; flex-direction: column; align-items: center; }
.header-title-row { display: flex; align-items: center; gap: 8rpx; }
.header-sparkle { font-size: 26rpx; color: #5081B8; }
.header-title-text { font-size: 30rpx; font-weight: 700; color: #1e344f; }
.header-title-text--done { font-size: 32rpx; }
.header-done-icon { font-size: 30rpx; color: #34C759; font-weight: 700; }
.header-subtitle-text { font-size: 24rpx; color: #8290a1; margin-top: 6rpx; }
.header-subtitle-text--done { color: #B0B3C5; }

.canvas-area { flex: 1; position: relative; overflow: hidden; display: flex; align-items: center; justify-content: center; }
.canvas-inner { position: absolute; left: 50%; top: 50%; transform: translate(-50%, -50%); overflow: visible; }
.canvas-stage { position: relative; transform-origin: 0 0; overflow: visible; }
.lines-svg { position: absolute; top: 0; left: 0; width: 100%; height: 100%; pointer-events: none; z-index: 1; overflow: visible; }

.ghost-edge { stroke: #c3d0de; stroke-width: 1.5; stroke-dasharray: 4 5; fill: none; opacity: 0.6; }
.edge-draw { stroke: #91a6ba; stroke-width: 2; fill: none; stroke-dasharray: 1; stroke-dashoffset: 1; animation: fcDraw 0.32s ease-out forwards; }
.edge-draw--back { stroke-dasharray: 0.06 0.05; animation: fcFade 0.4s ease forwards; }
@keyframes fcDraw { to { stroke-dashoffset: 0; } }
@keyframes fcFade { from { opacity: 0; } to { opacity: 1; } }

.ghost { position: absolute; z-index: 1; border: 2rpx dashed #b9c6d6; border-radius: 20rpx; opacity: 0.55; transform: translate(-50%, -50%); box-sizing: border-box; }
.ghost--pill { border-radius: 999rpx; }

.fn { position: absolute; z-index: 2; display: flex; align-items: center; justify-content: center; border: 3rpx solid; box-sizing: border-box; transform: translate(-50%, -50%); }
.fn-name { font-size: 24rpx; font-weight: 700; text-align: center; padding: 0 12rpx; }
.fn-in { animation: fcNodeIn 0.36s cubic-bezier(0.34, 1.56, 0.64, 1); }
@keyframes fcNodeIn { from { opacity: 0; transform: translate(-50%, -50%) translateY(-46px) scale(0.5); } to { opacity: 1; transform: translate(-50%, -50%); } }
.fn--start, .fn--end { border-color: #4b9d76; background: #eefaf3; border-radius: 999rpx; color: #22684c; }
.fn--action { border-color: #5081B8; background: #fff; border-radius: 20rpx; color: #1e344f; }
.fn--decision { border-color: #f0a12b; background: #fff8ec; border-radius: 20rpx; color: #7a5210; }
.fn--exception { border-color: #dd6b6b; background: #fff3f3; border-radius: 20rpx; color: #8c3a3a; }
.fn--data { border-color: #7d72c8; background: #f5f2ff; border-radius: 20rpx; color: #4a4380; }
.settle { animation: fcSettle 0.46s ease-in-out 1; }
@keyframes fcSettle { 0%, 100% { filter: brightness(1); } 45% { filter: brightness(1.12); } }

.edge-label { position: absolute; z-index: 3; padding: 4rpx 14rpx; border-radius: 12rpx; background: #f7f9fb; transform: translate(-50%, -50%); }
.edge-label text { font-size: 22rpx; color: #a36a14; font-weight: 600; }

.wait-ring { position: absolute; left: 50%; top: 42%; width: 148rpx; height: 148rpx; transform: translate(-50%, -50%); transition: opacity 0.3s; }
.wait-ring.hidden { opacity: 0; }
.wait-ring i { position: absolute; inset: 0; border: 4rpx solid rgba(80, 129, 184, 0.5); border-radius: 50%; animation: fcRing 1.9s ease-out infinite; }
.wait-ring i:nth-child(2) { animation-delay: 0.65s; }
.wait-ring b { position: absolute; left: 50%; top: 50%; width: 20rpx; height: 20rpx; transform: translate(-50%, -50%); background: #5081B8; border-radius: 50%; animation: fcCore 1.9s ease-in-out infinite; }
@keyframes fcRing { 0% { transform: scale(0.25); opacity: 0.9; } 100% { transform: scale(1.15); opacity: 0; } }
@keyframes fcCore { 0%, 100% { transform: translate(-50%, -50%) scale(1); opacity: 0.85; } 50% { transform: translate(-50%, -50%) scale(1.5); opacity: 1; } }

.skip-btn { position: absolute; right: 24rpx; bottom: 24rpx; z-index: 20; padding: 10rpx 24rpx; background: rgba(255, 255, 255, 0.92); border: 2rpx solid #e2e8ef; border-radius: 999rpx; font-size: 24rpx; color: #58728c; }

.bottom-steps { display: flex; align-items: center; justify-content: center; gap: 18rpx; padding: 16rpx 0 8rpx; }
.bottom-step-dot { width: 18rpx; height: 18rpx; border-radius: 50%; background: #d8dee6; display: flex; align-items: center; justify-content: center; }
.bottom-step-dot--done { background: #34C759; }
.bottom-step-check { color: #fff; font-size: 12rpx; font-weight: 700; }
.bottom-step-dot--active { background: #5081B8; animation: fcStep 1.2s ease-in-out infinite; }
@keyframes fcStep { 0%, 100% { opacity: 1; transform: scale(1); } 50% { opacity: 0.5; transform: scale(1.2); } }

.status-float { padding: 0 24rpx 20rpx; }
.status-float-inner { background: #fff; border-radius: 24rpx; padding: 24rpx 28rpx; box-shadow: 0 4rpx 24rpx rgba(80, 129, 184, 0.1); }
.status-float-top { display: flex; align-items: center; justify-content: space-between; margin-bottom: 8rpx; }
.status-float-label { font-size: 26rpx; font-weight: 700; color: #1e344f; }
.status-float-dots { display: flex; gap: 5rpx; }
.status-float-dot { width: 8rpx; height: 8rpx; border-radius: 50%; background: #5081B8; animation: fcDot 1.4s ease-in-out infinite; }
.status-float-dot:nth-child(2) { animation-delay: 0.2s; }
.status-float-dot:nth-child(3) { animation-delay: 0.4s; }
@keyframes fcDot { 0%, 80%, 100% { transform: scale(0.5); opacity: 0.3; } 40% { transform: scale(1); opacity: 1; } }
.status-float-msg { display: block; font-size: 24rpx; color: #8290a1; margin-bottom: 14rpx; }
.status-float-meta { display: flex; align-items: center; justify-content: space-between; margin-bottom: 10rpx; }
.status-float-count { font-size: 22rpx; color: #58728c; }
.status-float-pct { font-size: 22rpx; color: #3E6A9C; font-weight: 700; }
.status-float-bar { height: 10rpx; background: #eef1f6; border-radius: 5rpx; overflow: hidden; }
.status-float-bar-fill { height: 100%; background: linear-gradient(90deg, #5081B8, #3E6A9C); border-radius: 5rpx; transition: width 0.6s ease; }

.done-btns { display: flex; gap: 20rpx; padding: 0 24rpx 32rpx; }
.done-btn { flex: 1; padding: 26rpx; border-radius: 24rpx; display: flex; align-items: center; justify-content: center; gap: 8rpx; font-size: 28rpx; font-weight: 600; }
.done-btn--sec { background: #fff; color: #1e344f; border: 2rpx solid #e2e8ef; }
.done-btn--pri { background: #5081B8; color: #fff; }
.done-btn-arrow { font-size: 28rpx; margin-left: 4rpx; }

.error-state { flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 80rpx 48rpx; }
.error-icon { font-size: 96rpx; margin-bottom: 24rpx; }
.error-title { font-size: 36rpx; font-weight: 700; color: #1e344f; margin-bottom: 16rpx; }
.error-msg { font-size: 26rpx; color: #8290a1; text-align: center; line-height: 1.6; margin-bottom: 48rpx; word-break: break-all; }
.error-actions { display: flex; gap: 20rpx; width: 100%; max-width: 560rpx; }
.error-btn { flex: 1; padding: 26rpx; border-radius: 24rpx; display: flex; align-items: center; justify-content: center; font-size: 28rpx; font-weight: 600; }
.error-btn--sec { background: #fff; color: #1e344f; border: 2rpx solid #e2e8ef; }
.error-btn--pri { background: #5081B8; color: #fff; }
</style>
