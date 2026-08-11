<template>
  <view class="page">
    <nav-bar
      :title="chart.title || 'AI 流程图'"
      :showBack="true"
      :border="false"
      :fixed="true"
      :placeholder="true"
      titleAlign="center"
    >
      <template #right>
        <view class="nav-history-action" @tap="openHistory">
          <image class="nav-history-icon" src="/static/icons/diagram/history.svg" mode="aspectFit" />
        </view>
      </template>
    </nav-bar>

    <view v-if="loading" class="loading-state">正在加载流程图...</view>
    <movable-area v-else class="diagram-stage" scale-area>
      <movable-view
        class="diagram-movable"
        direction="all"
        :scale="true"
        :scale-min="0.5"
        :scale-max="2"
        :scale-value="zoom"
        :style="outerStyle"
        @scale="syncScale"
      >
        <view class="diagram-canvas" :style="innerStyle">
          <view
            v-for="lane in laneBands"
            :key="lane.id || lane.name"
            class="lane-band"
            :style="lane.style"
          >
            <text>{{ lane.label || lane.name }}</text>
          </view>

          <svg class="diagram-lines" :width="canvasSize.width" :height="canvasSize.height">
            <defs>
              <marker id="viewerArrow" viewBox="0 0 10 10" refX="8" refY="5" markerWidth="6.5" markerHeight="6.5" orient="auto">
                <path d="M 0 1 L 8 5 L 0 9 Z" fill="#91a6ba" />
              </marker>
            </defs>
            <path
              v-for="edge in positionedEdges"
              :key="edge.key"
              class="edge-path"
              :class="{ 'edge-path--back': edge.kind === 'back', 'edge-path--branch': edge.type === 'branch' || edge.kind === 'branch' }"
              :d="edge.path"
              marker-end="url(#viewerArrow)"
            />
          </svg>
          <text
            v-for="edge in positionedEdges.filter(item => item.label)"
            :key="`${edge.key}-label`"
            class="edge-label"
            :style="edge.labelStyle"
          >{{ edge.label }}</text>

          <view
            v-for="node in positionedNodes"
            :key="node.id"
            class="flow-node"
            :class="`flow-node--${node.type || 'process'}`"
            :style="node.style"
          >
            <text class="node-name">{{ node.name }}</text>
            <text v-if="node.condition" class="node-meta">{{ node.condition }}</text>
            <text v-else-if="node.output" class="node-meta">输出：{{ node.output }}</text>
          </view>
        </view>
      </movable-view>
    </movable-area>

    <view class="bottom-tip">{{ directionTip }} · 双指缩放 · 单指拖动查看完整流程</view>

    <!-- 底部操作栏（统一组件） -->
    <AiResultBar @export="exportImage" @optimize="openOptimizeSheet" @share="shareFlow" />

    <!-- 优化弹窗 -->
    <OptimizeMindMapSheet
      title="优化流程图"
      :visible="showOptimizeSheet"
      :currentMindMap="currentChartData"
      @close="showOptimizeSheet = false"
      @optimize="onOptimize"
    />

    <!-- AI 优化思考窗 -->
    <AiThinkWindow
      :visible="showThinkWindow"
      type="flowchart"
      :doneSub="optimizeDoneSub"
      :done="thinkDone"
      @view="onThinkView"
    />
    <canvas canvas-id="flowchartExportCanvas" class="export-canvas" :style="exportCanvasStyle" />
  </view>
</template>

<script setup>
import { computed, getCurrentInstance, onMounted, onUnmounted, ref } from 'vue'
import NavBar from '@/components/nav-bar/nav-bar.vue'
import AiResultBar from '../components/AiResultBar.vue'
import AiThinkWindow from '../components/AiThinkWindow.vue'
import OptimizeMindMapSheet from '../mindmapViewer/OptimizeMindMapSheet.vue'
import { getErrorMessage, getFlowchartDetail, getFlowchartHistory, generateFlowchart } from '@/api/aiDiagram.js'
import { layoutFlowchart, FLOW_NODE_W, FLOW_NODE_H } from '../flowchartLayout.js'
// #ifdef H5
import { domToPng } from '../components/domToPng.js'
// #endif

const NODE_WIDTH = FLOW_NODE_W
const NODE_HEIGHT = FLOW_NODE_H
const instance = getCurrentInstance()

const loading = ref(true)
const zoom = ref(0.78)
const chart = ref({ title: 'AI 流程图', type: 'FLOWCHART', lanes: [], nodes: [], edges: [] })
const resultId = ref('')

function readPageOptions() {
  const pages = getCurrentPages()
  const current = pages[pages.length - 1] || {}
  return current.options || current.$page?.options || {}
}

const laidFlow = computed(() => layoutFlowchart(chart.value))
const directionTip = computed(() => {
  return laidFlow.value.direction === 'HORIZONTAL' ? '横向流程' : '纵向流程'
})

const positionedNodes = computed(() => {
  return (laidFlow.value.nodes || []).map(node => {
    const x = Math.round(node.cx - NODE_WIDTH / 2)
    const y = Math.round(node.cy - NODE_HEIGHT / 2)
    return {
      ...node,
      x,
      y,
      w: NODE_WIDTH,
      h: NODE_HEIGHT,
      style: { left: `${x}px`, top: `${y}px`, width: `${NODE_WIDTH}px`, height: `${NODE_HEIGHT}px` }
    }
  })
})

const canvasSize = computed(() => {
  return {
    width: Math.max(760, laidFlow.value.canvasW || 0),
    height: Math.max(500, laidFlow.value.canvasH || 0)
  }
})

const canvasStyle = computed(() => ({
  width: `${canvasSize.value.width}px`,
  height: `${canvasSize.value.height}px`
}))

// movable-view 外框 = 画布 × 缩放（决定可拖动范围）；内层画布用 CSS transform 缩放（H5 可用）
const outerStyle = computed(() => ({
  width: `${Math.round(canvasSize.value.width * zoom.value)}px`,
  height: `${Math.round(canvasSize.value.height * zoom.value)}px`
}))
const innerStyle = computed(() => ({
  width: `${canvasSize.value.width}px`,
  height: `${canvasSize.value.height}px`,
  transform: `scale(${zoom.value})`
}))

const laneBands = computed(() => {
  return (laidFlow.value.lanes || []).map(lane => ({
    ...lane,
    style: {
      left: `${lane.x}px`,
      top: `${lane.y}px`,
      width: `${lane.w}px`,
      height: `${lane.h}px`
    }
  }))
})

const positionedEdges = computed(() => {
  return (laidFlow.value.edges || []).map(edge => ({
    ...edge,
    labelStyle: {
      left: `${(edge.x1 + edge.x2) / 2}px`,
      top: `${(edge.y1 + edge.y2) / 2 - 20}px`
    }
  }))
})

const exportCanvasStyle = computed(() => ({
  width: `${canvasSize.value.width}px`,
  height: `${canvasSize.value.height}px`
}))

function syncScale(event) {
  const value = Number(event.detail?.scale)
  if (Number.isFinite(value)) zoom.value = Math.max(0.5, Math.min(2, value))
}

function zoomIn() {
  zoom.value = Math.min(2, Number((zoom.value + 0.1).toFixed(2)))
}

function zoomOut() {
  zoom.value = Math.max(0.5, Number((zoom.value - 0.1).toFixed(2)))
}

// ===== H5：Ctrl/⌘ + 滚轮缩放（手机端仍用 movable-view 双指缩放） =====
function onWheel(e) {
  if (!e.ctrlKey && !e.metaKey) return
  e.preventDefault()
  const delta = e.deltaY > 0 ? -0.1 : 0.1
  zoom.value = Math.max(0.5, Math.min(2, Number((zoom.value + delta).toFixed(2))))
}
// #ifdef H5
onMounted(() => {
  if (typeof document !== 'undefined') document.addEventListener('wheel', onWheel, { passive: false })
})
onUnmounted(() => {
  if (typeof document !== 'undefined') document.removeEventListener('wheel', onWheel)
})
// #endif

function openHistory() {
  uni.navigateTo({ url: '/subpackage_ai/diagramHistory/diagramHistory?type=flowchart' })
}

function goGenerate() {
  const pages = getCurrentPages()
  const idx = pages.findIndex(p => (p.route || '').includes('flowchartGenerate/flowchartGenerate'))
  if (idx >= 0) uni.navigateBack({ delta: pages.length - 1 - idx })
  else uni.navigateTo({ url: '/subpackage_ai/flowchartGenerate/flowchartGenerate' })
}

function regenerate() {
  const payload = uni.getStorageSync('aiFlowchartPendingPayload')
  if (payload?.description) {
    uni.redirectTo({ url: '/subpackage_ai/flowchartGenerating/flowchartGenerating' })
  } else {
    goGenerate()
  }
}

function refine() {
  goGenerate()
}

// 泳道数据扁平化：SWIMLANE 时节点可能嵌套在 lanes 内，需摊平到顶层才能渲染
function flattenLanes(data) {
  if (!data) return data
  const top = Array.isArray(data.nodes) ? data.nodes : []
  if (top.length) return data
  if (!Array.isArray(data.lanes)) return data
  const flat = []
  data.lanes.forEach(lane => {
    (lane.nodes || []).forEach(n => {
      if (n && typeof n === 'object') flat.push({ ...n, lane: n.lane || lane.name })
      else flat.push({ id: String(n), name: String(n), lane: lane.name, type: 'action' })
    })
  })
  return flat.length ? { ...data, nodes: flat } : data
}

// ===== 优化（弹窗 + 思考窗 + 带指令重生成） =====
const showOptimizeSheet = ref(false)
const showThinkWindow = ref(false)
const thinkDone = ref(false)
const optimizeDoneSub = ref('结构已更新')
const optimizePending = ref(null)
const currentChartData = ref({})

function openOptimizeSheet() {
  currentChartData.value = {
    title: chart.value.title,
    type: chart.value.type,
    lanes: chart.value.lanes,
    nodes: chart.value.nodes,
    edges: chart.value.edges
  }
  showOptimizeSheet.value = true
}
function shareFlow() {
  uni.showToast({ title: '分享能力预留', icon: 'none' })
}

function describeCurrentFlow() {
  const laneText = (chart.value.lanes || [])
    .map(lane => lane.label || lane.name || lane.id)
    .filter(Boolean)
    .slice(0, 8)
    .join('、')
  const nodeText = (chart.value.nodes || [])
    .map(node => {
      const lane = node.lane || node.laneId
      return `${node.name || node.label || node.id}${lane ? `（${lane}）` : ''}`
    })
    .filter(Boolean)
    .slice(0, 24)
    .join(' -> ')
  const edgeText = (chart.value.edges || [])
    .map(edge => {
      const label = edge.label || edge.condition
      return `${edge.source} -> ${edge.target}${label ? `（${label}）` : ''}`
    })
    .filter(Boolean)
    .slice(0, 32)
    .join('；')
  return [
    laneText ? `当前泳道：${laneText}` : '',
    nodeText ? `当前节点顺序：${nodeText}` : '',
    edgeText ? `当前连线：${edgeText}` : ''
  ].filter(Boolean).join('\n')
}

function buildOptimizeDescription(base, userInstruction) {
  const original = base.content || base.description || chart.value.title || ''
  return [
    '请基于当前流程图进行优化，不要重新生成无关流程。',
    original ? `原始需求：${original}` : '',
    describeCurrentFlow(),
    userInstruction ? `优化要求：${userInstruction}` : ''
  ].filter(Boolean).join('\n\n')
}

async function onOptimize(payload) {
  showOptimizeSheet.value = false
  optimizePending.value = null
  thinkDone.value = false
  showThinkWindow.value = true
  try {
    const base = uni.getStorageSync('aiFlowchartPendingPayload') || {}
    const userInstruction = String(payload.userInstruction || '').trim()
    const optimizedDescription = buildOptimizeDescription(base, userInstruction)
    const newPayload = {
      ...base,
      content: optimizedDescription,
      description: optimizedDescription
    }
    const result = flattenLanes(await generateFlowchart(newPayload))
    if (result?.id) uni.setStorageSync(`aiFlowchartResult:${result.id}`, result)
    optimizePending.value = result
    optimizeDoneSub.value = `已更新「${result.title || '流程图'}」`
    thinkDone.value = true
  } catch (error) {
    showThinkWindow.value = false
    thinkDone.value = false
    uni.showToast({ title: getErrorMessage(error, '优化失败'), icon: 'none' })
  }
}

function onThinkView() {
  showThinkWindow.value = false
  thinkDone.value = false
  const r = optimizePending.value
  if (r) {
    chart.value = r
    resultId.value = r.id
    uni.showToast({ title: '已更新流程图', icon: 'success' })
  }
}

async function loadDiagram(id) {
  if (!id) return
  resultId.value = String(id)
  const cached = uni.getStorageSync(`aiFlowchartResult:${resultId.value}`)
  if (cached?.nodes?.length || cached?.lanes?.length) chart.value = flattenLanes(cached)
  loading.value = !(cached?.nodes?.length || cached?.lanes?.length)
  try {
    chart.value = flattenLanes(await getFlowchartDetail(resultId.value))
    uni.setStorageSync(`aiFlowchartResult:${resultId.value}`, chart.value)
  } catch (error) {
    if (!cached?.nodes?.length) {
      uni.showToast({ title: getErrorMessage(error, '加载流程图失败'), icon: 'none' })
    }
  } finally {
    loading.value = false
  }
}

function drawExportCanvas() {
  const context = uni.createCanvasContext('flowchartExportCanvas', instance?.proxy)
  const { width, height } = canvasSize.value
  context.setFillStyle('#FAFBFC')
  context.fillRect(0, 0, width, height)
  context.setFillStyle('#1F3A5F')
  context.setFontSize(22)
  context.fillText(chart.value.title || 'AI 流程图', 32, 38)
  positionedEdges.value.forEach(edge => {
    context.setStrokeStyle('#9AA8BC')
    context.setLineWidth(2)
    context.beginPath()
    context.moveTo(edge.x1, edge.y1)
    context.lineTo(edge.x2, edge.y2)
    context.stroke()
  })
  positionedNodes.value.forEach(node => {
    const color = node.type === 'decision' ? '#FFF5E5' : '#FFFFFF'
    const border = node.type === 'decision' ? '#F0A12B' : '#5081B8'
    context.setFillStyle(color)
    context.setStrokeStyle(border)
    context.setLineWidth(2)
    if (node.type === 'decision') {
      const cx = node.x + (node.w || NODE_WIDTH) / 2
      const cy = node.y + (node.h || NODE_HEIGHT) / 2
      context.beginPath()
      context.moveTo(cx, node.y - 10)
      context.lineTo(node.x + (node.w || NODE_WIDTH) - 18, cy)
      context.lineTo(cx, node.y + (node.h || NODE_HEIGHT) + 10)
      context.lineTo(node.x + 18, cy)
      context.closePath()
      context.fill()
      context.stroke()
    } else {
      context.fillRect(node.x, node.y, node.w || NODE_WIDTH, node.h || NODE_HEIGHT)
      context.strokeRect(node.x, node.y, node.w || NODE_WIDTH, node.h || NODE_HEIGHT)
    }
    context.setFillStyle('#1E344F')
    context.setFontSize(15)
    context.fillText(String(node.name || ''), node.x + 12, node.y + 32)
  })
  context.draw(false)
}

function exportImage() {
  // #ifdef H5
  if (!positionedNodes.value.length) {
    uni.showToast({ title: '暂无数据', icon: 'none' })
    return
  }
  uni.showLoading({ title: '导出中...' })
  domToPng('.diagram-canvas', {
    width: canvasSize.value.width,
    height: canvasSize.value.height,
    title: chart.value.title,
    filename: chart.value.title
  })
    .then(() => {
      uni.hideLoading()
      uni.showToast({ title: '已导出 PNG', icon: 'success' })
    })
    .catch(err => {
      uni.hideLoading()
      console.error('[导出失败]', err)
      uni.showToast({ title: '导出失败，请重试', icon: 'none' })
    })
  // #endif
  // #ifndef H5
  drawExportCanvas()
  setTimeout(() => {
    uni.canvasToTempFilePath({
      canvasId: 'flowchartExportCanvas',
      destWidth: canvasSize.value.width * 2,
      destHeight: canvasSize.value.height * 2,
      success: ({ tempFilePath }) => {
        uni.saveImageToPhotosAlbum({
          filePath: tempFilePath,
          success: () => uni.showToast({ title: '图片已保存', icon: 'success' }),
          fail: () => uni.previewImage({ urls: [tempFilePath] })
        })
      },
      fail: () => uni.showToast({ title: '图片导出失败', icon: 'none' })
    }, instance?.proxy)
  }, 80)
  // #endif
}

onMounted(() => {
  const options = readPageOptions()
  if (options.id) loadDiagram(decodeURIComponent(options.id))
  else loading.value = false
})
</script>

<style lang="scss" scoped>
.page { min-height: 100vh; display: flex; flex-direction: column; background: #fafbfc; color: #1e344f; }
.nav-history-action { display: flex; align-items: center; justify-content: center; width: 64rpx; height: 64rpx; }
.nav-history-icon { width: 34rpx; height: 34rpx; }
.diagram-stage { flex: 1; width: 100%; min-height: 0; background-color: #f7f9fb; background-image: radial-gradient(#dbe3ec 1px, transparent 1px); background-size: 22rpx 22rpx; }
.diagram-movable { width: 100%; height: 100%; }
.diagram-canvas { position: relative; margin: 36px; transform-origin: 0 0; }
.diagram-lines { position: absolute; left: 0; top: 0; z-index: 1; overflow: visible; pointer-events: none; }
.lane-band { position: absolute; display: flex; align-items: flex-start; padding: 14px 16px; box-sizing: border-box; border: 1px solid #dce6ef; border-radius: 14px; background: rgba(239, 246, 252, .68); color: #58728c; font-size: 14px; z-index: 0; }
.edge-path { stroke: #91a6ba; stroke-width: 2; fill: none; }
.edge-path--branch { stroke: #5d8ff4; }
.edge-path--back { stroke-dasharray: 6 6; }
.edge-label { position: absolute; z-index: 3; padding: 2px 6px; border-radius: 6px; background: #f7f9fb; color: #a36a14; font-size: 12px; transform: translateX(-50%); }
.flow-node { position: absolute; z-index: 2; display: flex; align-items: center; justify-content: center; flex-direction: column; padding: 8px 12px; border: 2px solid #5081b8; border-radius: 10px; background: #fff; box-sizing: border-box; color: #1e344f; text-align: center; }
.flow-node--start, .flow-node--end { border-radius: 999px; border-color: #4b9d76; background: #eefaf3; }
.flow-node--process, .flow-node--action { border-color: #5081b8; background: #fff; }
.flow-node--decision { border-color: transparent; background: transparent; overflow: visible; }
.flow-node--decision::before { content: ""; position: absolute; left: 50%; top: 50%; width: 58px; height: 58px; border: 2px solid #f0a12b; border-radius: 8px; background: #fff8ec; transform: translate(-50%, -50%) rotate(45deg); box-sizing: border-box; }
.flow-node--decision .node-name,
.flow-node--decision .node-meta { position: relative; z-index: 2; max-width: 112px; }
.flow-node--decision .node-name { font-size: 13px; line-height: 1.25; }
.flow-node--exception { border-color: #dd6b6b; background: #fff3f3; }
.flow-node--data { border-color: #7d72c8; background: #f5f2ff; }
.node-name { font-size: 15px; font-weight: 700; line-height: 1.4; }
.node-meta { margin-top: 5px; color: #74859b; font-size: 11px; line-height: 1.35; }
.loading-state { flex: 1; display: flex; align-items: center; justify-content: center; color: #8290a1; font-size: 26rpx; }
.bottom-tip { padding-top: 16rpx; box-sizing: border-box; background: #fff; border-top: 1rpx solid #e7ebf0; color: #8290a1; font-size: 20rpx; text-align: center; }
.bottom-actions { display: flex; gap: 20rpx; padding: 16rpx 28rpx 30rpx; background: #fff; }
.action-btn { flex: 1; display: flex; align-items: center; justify-content: center; height: 84rpx; border-radius: 24rpx; font-size: 28rpx; font-weight: 600; }
.action-btn--sec { background: #fff; color: #1e344f; border: 2rpx solid #e2e8ef; }
.action-btn--pri { background: #5081B8; color: #fff; }
.export-canvas { position: fixed; left: -99999px; top: -99999px; opacity: 0; pointer-events: none; }
</style>
