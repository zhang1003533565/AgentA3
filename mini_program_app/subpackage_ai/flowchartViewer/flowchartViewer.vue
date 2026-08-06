<template>
  <view class="page">
    <nav-bar title="AI 流程图" :showBack="true" :border="false">
      <template #right>
        <view class="nav-history-action" @tap="openHistory">
          <image class="nav-history-icon" src="/static/icons/diagram/history.svg" mode="aspectFit" />
        </view>
      </template>
    </nav-bar>

    <view class="toolbar">
      <view class="diagram-title">
        <text>{{ chart.title }}</text>
        <text class="diagram-type">{{ chart.type }}</text>
      </view>
    </view>

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
            :key="lane.name"
            class="lane-band"
            :style="lane.style"
          >
            <text>{{ lane.name }}</text>
          </view>

          <view
            v-for="edge in positionedEdges"
            :key="edge.key"
            class="edge-line"
            :class="{ 'edge-line--back': edge.back }"
            :style="edge.lineStyle"
          />
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
            :class="`flow-node--${node.type || 'action'}`"
            :style="node.style"
          >
            <text class="node-name">{{ node.name }}</text>
            <text v-if="node.condition" class="node-meta">{{ node.condition }}</text>
            <text v-else-if="node.output" class="node-meta">输出：{{ node.output }}</text>
          </view>
        </view>
      </movable-view>
    </movable-area>

    <view class="bottom-tip">Ctrl+滚轮缩放 · 双指缩放 · 单指拖动查看完整流程</view>

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
      @view="onThinkView"
    />
    <canvas canvas-id="flowchartExportCanvas" class="export-canvas" :style="exportCanvasStyle" />
  </view>
</template>

<script setup>
import { computed, getCurrentInstance, onMounted, onUnmounted, nextTick, ref } from 'vue'
import NavBar from '@/components/nav-bar/nav-bar.vue'
import AiResultBar from '../components/AiResultBar.vue'
import AiThinkWindow from '../components/AiThinkWindow.vue'
import OptimizeMindMapSheet from '../mindmapViewer/OptimizeMindMapSheet.vue'
import { getErrorMessage, getFlowchartDetail, getFlowchartHistory, generateFlowchart } from '@/api/aiDiagram.js'
import { computeLevels } from '../flowchartLayout.js'
// #ifdef H5
import { domToPng } from '../components/domToPng.js'
// #endif

const NODE_WIDTH = 210
const NODE_HEIGHT = 78
const COLUMN_GAP = 100
const ROW_GAP = 36
const LANE_HEIGHT = 210
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

const positionedNodes = computed(() => {
  const nodes = chart.value.nodes || []
  const edges = chart.value.edges || []
  // 使用 DFS 回边感知的分层，避免"驳回/返回"等环边导致层级爆炸、节点跑出画布
  const { level } = computeLevels(nodes, edges)

  const laneNames = (chart.value.lanes || []).map(lane => lane.name).filter(Boolean)
  nodes.forEach(node => {
    if (node.lane && !laneNames.includes(node.lane)) laneNames.push(node.lane)
  })
  const laneIndex = new Map(laneNames.map((name, index) => [name, index]))
  const slots = new Map()
  return nodes.map((node, index) => {
    const currentLevel = level.get(String(node.id)) || 0
    const laneKey = node.lane || (laneNames[0] || '')
    const rowKey = `${laneKey}:${currentLevel}`
    const rowIndex = slots.get(rowKey) || 0
    slots.set(rowKey, rowIndex + 1)
    const laneOffset = laneNames.length ? (laneIndex.get(laneKey) || 0) * LANE_HEIGHT : 0
    const x = 70 + currentLevel * (NODE_WIDTH + COLUMN_GAP)
    const y = 72 + laneOffset + rowIndex * (NODE_HEIGHT + ROW_GAP)
    return {
      ...node,
      x,
      y,
      w: NODE_WIDTH,
      h: NODE_HEIGHT,
      style: { left: `${x}px`, top: `${y}px`, width: `${NODE_WIDTH}px`, minHeight: `${NODE_HEIGHT}px` }
    }
  })
})

const canvasSize = computed(() => {
  const nodes = positionedNodes.value
  const maxX = Math.max(760, ...nodes.map(node => node.x + NODE_WIDTH + 100))
  const maxY = Math.max(500, ...nodes.map(node => node.y + NODE_HEIGHT + 110))
  return { width: maxX, height: maxY }
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
  const lanes = (chart.value.lanes || []).filter(lane => lane.name)
  return lanes.map((lane, index) => ({
    name: lane.name,
    style: {
      top: `${28 + index * LANE_HEIGHT}px`,
      height: `${LANE_HEIGHT - 18}px`,
      width: `${canvasSize.value.width - 56}px`
    }
  }))
})

const positionedEdges = computed(() => {
  const nodeMap = new Map(positionedNodes.value.map(node => [String(node.id), node]))
  return (chart.value.edges || []).map((edge, index) => {
    const source = nodeMap.get(String(edge.source))
    const target = nodeMap.get(String(edge.target))
    if (!source || !target) return null
    const startX = source.x + NODE_WIDTH
    const startY = source.y + NODE_HEIGHT / 2
    const endX = target.x
    const endY = target.y + NODE_HEIGHT / 2
    const deltaX = endX - startX
    const deltaY = endY - startY
    const width = Math.sqrt(deltaX * deltaX + deltaY * deltaY)
    const angle = Math.atan2(deltaY, deltaX) * 180 / Math.PI
    const label = edge.label || edge.condition || ''
    return {
      key: `${edge.source}-${edge.target}-${index}`,
      label,
      lineStyle: {
        left: `${startX}px`,
        top: `${startY}px`,
        width: `${width}px`,
        transform: `rotate(${angle}deg)`
      },
      labelStyle: {
        left: `${(startX + endX) / 2}px`,
        top: `${(startY + endY) / 2 - 20}px`
      }
    }
  }).filter(Boolean)
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
  uni.navigateTo({ url: '/subpackage_ai/diagramHistory/diagramHistory' })
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
const optimizeDoneSub = ref('结构已更新')
const optimizePending = ref(null)
const currentChartData = ref({})

function openOptimizeSheet() {
  currentChartData.value = { title: chart.value.title, nodes: chart.value.nodes }
  showOptimizeSheet.value = true
}
function shareFlow() {
  uni.showToast({ title: '分享能力预留', icon: 'none' })
}

async function onOptimize(payload) {
  showOptimizeSheet.value = false
  optimizePending.value = null
  showThinkWindow.value = true
  try {
    const base = uni.getStorageSync('aiFlowchartPendingPayload') || {}
    const desc = base.description || chart.value.title || ''
    const newPayload = { ...base, description: payload.userInstruction ? `${desc}\n优化要求：${payload.userInstruction}` : desc }
    const result = flattenLanes(await generateFlowchart(newPayload))
    uni.setStorageSync(`aiFlowchartResult:${result.id}`, result)
    optimizePending.value = result
    optimizeDoneSub.value = `已更新「${result.title || '流程图'}」`
  } catch (error) {
    showThinkWindow.value = false
    uni.showToast({ title: getErrorMessage(error, '优化失败'), icon: 'none' })
  }
}

function onThinkView() {
  showThinkWindow.value = false
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
    const transform = edge.lineStyle.transform.match(/-?[\d.]+/)
    const angle = transform ? Number(transform[0]) * Math.PI / 180 : 0
    const startX = Number.parseFloat(edge.lineStyle.left)
    const startY = Number.parseFloat(edge.lineStyle.top)
    const length = Number.parseFloat(edge.lineStyle.width)
    context.setStrokeStyle('#9AA8BC')
    context.setLineWidth(2)
    context.beginPath()
    context.moveTo(startX, startY)
    context.lineTo(startX + Math.cos(angle) * length, startY + Math.sin(angle) * length)
    context.stroke()
  })
  positionedNodes.value.forEach(node => {
    const color = node.type === 'decision' ? '#FFF5E5' : node.type === 'exception' ? '#FFF0F0' : '#FFFFFF'
    const border = node.type === 'decision' ? '#F0A12B' : node.type === 'exception' ? '#DD6B6B' : '#5081B8'
    context.setFillStyle(color)
    context.setStrokeStyle(border)
    context.setLineWidth(2)
    context.fillRect(node.x, node.y, node.w || NODE_WIDTH, node.h || NODE_HEIGHT)
    context.strokeRect(node.x, node.y, node.w || NODE_WIDTH, node.h || NODE_HEIGHT)
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
.toolbar { display: flex; align-items: center; justify-content: space-between; gap: 16rpx; min-height: 92rpx; padding: 0 26rpx; background: #fff; border-top: 1rpx solid #edf0f4; border-bottom: 1rpx solid #e7ebf0; }
.diagram-title { display: flex; min-width: 0; flex-direction: column; gap: 4rpx; font-size: 28rpx; font-weight: 700; }
.diagram-title > text:first-child { overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.diagram-type { color: #8796a9; font-size: 18rpx; font-weight: 500; }
.toolbar-actions { display: flex; align-items: center; gap: 12rpx; flex-shrink: 0; }
.tool-button, .export-button { display: flex; align-items: center; justify-content: center; min-width: 48rpx; height: 48rpx; border: 1rpx solid #d9e1ea; border-radius: 10rpx; background: #fff; color: #31567f; font-size: 34rpx; }
.zoom-value { min-width: 72rpx; color: #63748a; font-size: 20rpx; text-align: center; }
.export-button { min-width: 112rpx; padding: 0 14rpx; border-color: #e9a13c; background: #fff9ef; color: #d97808; font-size: 21rpx; }
.diagram-stage { flex: 1; width: 100%; min-height: 0; background-color: #f7f9fb; background-image: radial-gradient(#dbe3ec 1px, transparent 1px); background-size: 22rpx 22rpx; }
.diagram-movable { width: 100%; height: 100%; }
.diagram-canvas { position: relative; margin: 36px; transform-origin: 0 0; }
.lane-band { position: absolute; left: 28px; display: flex; align-items: flex-start; padding: 16px 18px; box-sizing: border-box; border: 1px solid #dce6ef; border-radius: 14px; background: rgba(239, 246, 252, .68); color: #58728c; font-size: 14px; }
.edge-line { position: absolute; z-index: 1; height: 2px; transform-origin: left center; background: #91a6ba; }
.edge-line::after { position: absolute; top: -4px; right: -1px; width: 0; height: 0; border-top: 5px solid transparent; border-bottom: 5px solid transparent; border-left: 8px solid #91a6ba; content: ''; }
.edge-line--back { background: repeating-linear-gradient(90deg, #91a6ba 0 6px, transparent 6px 11px); }
.edge-line--back::after { border-left-color: #91a6ba; }
.edge-label { position: absolute; z-index: 3; padding: 2px 6px; border-radius: 6px; background: #f7f9fb; color: #a36a14; font-size: 12px; transform: translateX(-50%); }
.flow-node { position: absolute; z-index: 2; display: flex; align-items: center; justify-content: center; flex-direction: column; padding: 12px 14px; border: 2px solid #5081b8; border-radius: 10px; background: #fff; box-sizing: border-box; color: #1e344f; text-align: center; }
.flow-node--start, .flow-node--end { border-radius: 999px; border-color: #4b9d76; background: #eefaf3; }
.flow-node--decision { border-color: #f0a12b; background: #fff8ec; }
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
