<template>
  <view class="page">
    <nav-bar :title="mindmap.title || 'AI 思维导图'" :showBack="true" :border="false" :fixed="true" :placeholder="true" />

    <view class="canvas-wrapper">
      <scroll-view
        ref="canvasRef"
        class="canvas"
        scroll-x
        scroll-y
        :scroll-top="scrollTop"
        :scroll-left="scrollLeft"
        :show-scrollbar="false"
        :throttle="false"
        @scroll="onScroll"
      >
        <view
          v-if="mindmap.nodes.length && layout.nodes.length"
          class="canvas-content"
          :style="contentStyle"
        >
          <view
            class="mindmap-stage"
            :style="stageStyle"
          >
            <!-- 连线层 -->
            <view
              v-for="link in layout.links"
              :key="link.id"
              class="mind-link"
              :style="linkStyle(link)"
            />

            <!-- 节点层 -->
            <view
              v-for="node in layout.nodes"
              :key="node.id"
              class="mind-node"
              :class="[
                node.depth === 0 ? 'mind-node--root' : '',
                node.depth === 1 ? 'mind-node--branch' : '',
                node.depth > 1 ? 'mind-node--sub' : '',
                node.side === 'left' ? 'mind-node--left' : 'mind-node--right',
                { 'mind-node--collapsed': isCollapsed(node) }
              ]"
              :style="nodeStyle(node)"
              @tap.stop="toggleNode(node)"
            >
              <text class="mind-node__label">{{ node.label }}</text>
              <view v-if="node.hasChildren && node.depth > 0" class="mind-node__toggle" :style="toggleStyle(node)" @tap.stop="toggleNode(node)">
                <text class="mind-node__toggle-icon">{{ getToggleIcon(node) }}</text>
              </view>
            </view>
          </view>
        </view>
      </scroll-view>

      <view v-if="loading" class="state-text">加载中...</view>
      <view v-else-if="!mindmap.nodes.length" class="state-text">暂无思维导图数据</view>

      <!-- H5 端：普通 view + position: fixed 即可 -->
      <!-- #ifdef H5 -->
      <view v-if="mindmap.nodes.length" class="expand-controls">
        <view class="expand-card" @tap="expandAll">
          <view class="expand-icon-circle">
            <text class="expand-triangle-char">▼</text>
          </view>
          <text class="expand-card-text">全部展开</text>
        </view>
        <view class="expand-card" @tap="collapseAll">
          <view class="expand-icon-circle">
            <text class="expand-triangle-char">▶</text>
          </view>
          <text class="expand-card-text">全部收起</text>
        </view>
      </view>
      <!-- #endif -->
    </view>

    <!-- 小程序端：cover-view 放在 page 层级（与 .canvas-wrapper 平级），position: fixed 相对视口
         cover-view 本身设计为覆盖在原生组件之上，放在 page 层级也能压住 scroll-view -->
    <!-- #ifdef MP-WEIXIN || MP-ALIPAY || MP-BAIDU || MP-TOUTIAO -->
    <cover-view v-if="mindmap.nodes.length" class="expand-controls">
      <cover-view class="expand-card" @tap="expandAll">
        <cover-view class="expand-icon-circle">
          <cover-view class="expand-triangle">▼</cover-view>
        </cover-view>
        <cover-view class="expand-card-text">全部展开</cover-view>
      </cover-view>
      <cover-view class="expand-card" @tap="collapseAll">
        <cover-view class="expand-icon-circle">
          <cover-view class="expand-triangle">▶</cover-view>
        </cover-view>
        <cover-view class="expand-card-text">全部收起</cover-view>
      </cover-view>
    </cover-view>
    <!-- #endif -->

    <!-- 底部操作栏：固定在屏幕底部，悬浮在 canvas 之上 -->
    <AiResultBar
      @export="saveMindmap"
      @optimize="openOptimizeSheet"
      @share="shareMindmap"
    />

    <!-- 优化弹窗 -->
    <OptimizeMindMapSheet
      :visible="showOptimizeSheet"
      :currentMindMap="currentMindMapData"
      @close="showOptimizeSheet = false"
      @optimize="onOptimize"
    />
  </view>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, reactive, ref } from 'vue'
import NavBar from '@/components/nav-bar/nav-bar.vue'
import OptimizeMindMapSheet from './OptimizeMindMapSheet.vue'
import AiResultBar from '../components/AiResultBar.vue'
import { getErrorMessage, getMindmapDetail, optimizeMindmap } from '@/api/aiDiagram.js'
import { exportMindmapAsPNG } from './mindmapExporter.js'

// 布局参数
const ROOT_WIDTH = 200
const ROOT_HEIGHT = 60
const NODE_WIDTH = 140
const NODE_HEIGHT = 44
const NODE_PADDING_X = 20
const LEVEL_GAP = 180
const ROOT_GAP = 72
const SIBLING_GAP = 24
const SIDE_GAP = 48
const STAGE_PADDING = 96
const MIN_SCALE = 0.15
const MAX_SCALE = 2.2

// 分支色彩主题（参考 XMind / ProcessOn）
const BRANCH_COLORS = [
  { main: '#4D6BFE', light: '#EEF0FF', border: '#C7D2FE' },   // 蓝
  { main: '#EF4444', light: '#FEF2F2', border: '#FECACA' },   // 红
  { main: '#10B981', light: '#ECFDF5', border: '#A7F3D0' },   // 绿
  { main: '#F59E0B', light: '#FFFBEB', border: '#FDE68A' },   // 橙
  { main: '#8B5CF6', light: '#F5F3FF', border: '#DDD6FE' },   // 紫
  { main: '#06B6D4', light: '#ECFEFF', border: '#A5F3FC' },   // 青
  { main: '#EC4899', light: '#FDF2F8', border: '#FBCFE8' },   // 粉
  { main: '#6366F1', light: '#EEF2FF', border: '#C7D2FE' },   // 靛
]

const loading = ref(false)
const resultId = ref('')
const mindmap = reactive({ title: '', nodes: [] })
const collapsed = reactive({})
const scale = ref(1)
// scroll-view 滚动位置（替代原来的 offset，由 scroll-view 原生管理滚动）
const scrollTop = ref(0)
const scrollLeft = ref(0)
const canvasSize = reactive({ width: 0, height: 0, left: 0, top: 0 })
const canvasRef = ref(null)

// 优化弹窗相关
const showOptimizeSheet = ref(false)
const currentMindMapData = ref({})

const treeData = computed(() => toTreeData(mindmap))
const layout = computed(() => buildMindMapLayout(treeData.value, collapsed))

const stageStyle = computed(() => ({
  width: `${layout.value.width}px`,
  height: `${layout.value.height}px`,
  // scroll-view 模式下，位置由滚动条管；stage 只需负责缩放
  transform: `scale(${scale.value})`,
  transformOrigin: '0 0'
}))

// canvas-content 是 scroll-view 的可滚动内容，尺寸为缩放后的布局大小
const contentStyle = computed(() => ({
  width: `${layout.value.width * scale.value}px`,
  height: `${layout.value.height * scale.value}px`,
}))

function readPageOptions() {
  const pages = getCurrentPages()
  const current = pages[pages.length - 1] || {}
  return current.options || current.$page?.options || {}
}

async function loadMindmap(id) {
  if (!id) return
  const cached = uni.getStorageSync(`aiMindmapResult:${id}`)
  if (cached?.nodes) applyMindmap(cached)
  loading.value = !cached
  try {
    const result = await getMindmapDetail(id)
    applyMindmap(result)
    uni.setStorageSync(`aiMindmapResult:${result.id}`, result)
  } catch (error) {
    if (!cached) uni.showToast({ title: getErrorMessage(error, '加载失败'), icon: 'none' })
  } finally {
    loading.value = false
  }
}

function applyMindmap(result = {}) {
  mindmap.title = result.title || 'AI 思维导图'
  mindmap.nodes = Array.isArray(result.nodes) ? result.nodes : []
  Object.keys(collapsed).forEach(key => { delete collapsed[key] })
  nextTick(() => {
    measureCanvas(() => resetView())
  })
}

function isCollapsed(node) {
  return !!collapsed[node.id]
}

function toggleNode(node) {
  if (!node?.hasChildren || node.depth === 0) return
  collapsed[node.id] = !collapsed[node.id]
  // 不重置视图，保持当前缩放和偏移
}

function expandAll() {
  Object.keys(collapsed).forEach(key => { delete collapsed[key] })
}

function collapseAll() {
  // 只收起一级分支节点（depth === 1），保留根节点展开
  layout.value.nodes.forEach(node => {
    if (node.depth === 1 && node.hasChildren) {
      collapsed[node.id] = true
    }
  })
}

function zoomIn() {
  scale.value = clamp(Number((scale.value + 0.12).toFixed(2)), MIN_SCALE, MAX_SCALE)
}

function zoomOut() {
  scale.value = clamp(Number((scale.value - 0.12).toFixed(2)), MIN_SCALE, MAX_SCALE)
}

function setActualSize() {
  scale.value = 1
  centerRoot()
}

function resetView(fit = true) {
  if (fit) scale.value = getFitScale()
  centerRoot()
}

function centerRoot() {
  const root = layout.value.root
  if (!root) { scrollLeft.value = 0; scrollTop.value = 0; return }
  const width = canvasSize.width || uni.getSystemInfoSync().windowWidth
  const height = canvasSize.height || Math.max(360, uni.getSystemInfoSync().windowHeight - 120)
  // scroll-view 滚动模式下：让根节点 (root.x * scale, root.y * scale) 落在视口中心
  scrollLeft.value = Math.round(root.x * scale.value - width / 2)
  scrollTop.value = Math.round(root.y * scale.value - height / 2)
}

function getFitScale() {
  const width = canvasSize.width || uni.getSystemInfoSync().windowWidth
  const height = canvasSize.height || Math.max(360, uni.getSystemInfoSync().windowHeight - 120)
  const mapWidth = Math.max(layout.value.width, 1)
  const mapHeight = Math.max(layout.value.height, 1)
  // 方案C：分母加 STAGE_PADDING * 2 作为安全边距，确保整个布局（含 padding）能完整显示
  const safeMargin = STAGE_PADDING * 2
  const fit = Math.min(
    width / (mapWidth + safeMargin),
    height / (mapHeight + safeMargin),
    1
  )
  // 关键修复：fit 不能再被 MIN_SCALE 卡住，否则大布局必然溢出
  // 单独用 0.05 作为兜底（再小线条完全不可见，没意义）
  return Math.max(0.05, Number(fit.toFixed(2)))
}

// scroll-view 滚动事件：把原生滚动位置同步到 ref
function onScroll(event) {
  const detail = event?.detail || {}
  if (typeof detail.scrollLeft === 'number') scrollLeft.value = detail.scrollLeft
  if (typeof detail.scrollTop === 'number') scrollTop.value = detail.scrollTop
}

function handleNativeWheel(event) {
  // 只有 Ctrl/Cmd + 滚轮 才缩放
  // 其他滚轮事件交给 scroll-view 原生处理
  const deltaY = event.deltaY
  if (!deltaY) return
  if (!(event.ctrlKey || event.metaKey)) return

  event.preventDefault()
  const nextScale = clamp(
    Number((scale.value + (deltaY > 0 ? -0.08 : 0.08)).toFixed(2)),
    MIN_SCALE, MAX_SCALE
  )
  if (nextScale === scale.value) return
  const rect = canvasRef.value?.$el?.getBoundingClientRect?.() || canvasRef.value?.getBoundingClientRect?.()
  const point = rect
    ? { x: event.clientX - rect.left, y: event.clientY - rect.top }
    : null
  if (!point) { scale.value = nextScale; return }
  // 以鼠标位置为中心缩放：保持鼠标下的 stage 局部坐标在缩放前后不变
  const beforeX = (point.x + scrollLeft.value) / scale.value
  const beforeY = (point.y + scrollTop.value) / scale.value
  scale.value = nextScale
  scrollLeft.value = Math.round(beforeX * nextScale - point.x)
  scrollTop.value = Math.round(beforeY * nextScale - point.y)
}

function measureCanvas(callback) {
  const query = uni.createSelectorQuery()
  query.select('.canvas').boundingClientRect(rect => {
    if (rect) {
      canvasSize.width = rect.width || canvasSize.width
      canvasSize.height = rect.height || canvasSize.height
      canvasSize.left = rect.left || 0
      canvasSize.top = rect.top || 0
    }
    callback?.()
  }).exec()
}

// 节点样式
function nodeStyle(node) {
  const color = node.branchColor || BRANCH_COLORS[0]
  // 左侧分支的子节点：色条/竖线放右侧；右侧分支：放左侧
  const stripeSide = node.side === 'left' ? 'right' : 'left'
  return {
    width: `${node.width}px`,
    minHeight: `${node.height}px`,
    left: `${node.x - node.width / 2}px`,
    top: `${node.y - node.height / 2}px`,
    paddingLeft: `${NODE_PADDING_X}px`,
    paddingRight: `${NODE_PADDING_X}px`,
    // 分支色 CSS 变量
    '--branch-main': color.main,
    '--branch-light': color.light,
    '--branch-border': color.border,
    '--stripe-side': stripeSide
  }
}

// 展开/收起按钮样式
function toggleStyle(node) {
  const color = node.branchColor || BRANCH_COLORS[0]
  const position = node.side === 'left'
    ? { left: '-12px', right: 'auto' }
    : { right: '-12px', left: 'auto' }
  return {
    '--toggle-bg': color.main,
    '--toggle-color': '#FFF',
    ...position
  }
}

function getToggleIcon(node) {
  // 按钮位于节点哪一侧
  // 收起时：箭头指向远离根节点的方向（提示有子节点可展开）
  // 展开时：箭头指向根节点方向（提示可收起）
  if (node.side === 'left') {
    // 按钮在节点左侧；远离根 = 向左(◀)；靠拢根 = 向右(▶)
    return isCollapsed(node) ? '◀' : '▶'
  }
  // 按钮在节点右侧；远离根 = 向右(▶)；靠拢根 = 向左(◀)
  return isCollapsed(node) ? '▶' : '◀'
}

// 连线样式（XMind 风格：分支色半透明）
function linkStyle(link) {
  const color = link.branchColor || BRANCH_COLORS[0]
  const opacity = link.depth === 1 ? 0.55 : 0.3
  return {
    left: `${link.x1}px`,
    top: `${link.y1}px`,
    width: `${link.length}px`,
    transform: `rotate(${link.angle}deg)`,
    background: color.main,
    opacity,
    // 方案C：统一改为 2px，减小 transform-origin: 0 50% 引起的 height/2 垂直偏移
    height: '2px',
  }
}

// 树数据转换
function toTreeData(source) {
  return {
    id: 'root',
    label: source.title || 'AI 思维导图',
    children: normalizeChildren(source.nodes, 'root')
  }
}

function normalizeChildren(nodes = [], parentId = 'root') {
  return nodes.map((node, index) => {
    const id = `${parentId}-${index}`
    return {
      id,
      label: String(node?.label || node?.name || `节点${index + 1}`).trim(),
      children: normalizeChildren(Array.isArray(node?.children) ? node.children : [], id)
    }
  })
}

// 布局算法（保留原有逻辑，增加分支色彩）
function buildMindMapLayout(rootSource, collapsedMap) {
  const root = cloneLayoutNode(rootSource, 0, 'center', null)
  const firstLevel = root.children || []

  // 给一级节点分配分支色彩
  firstLevel.forEach((node, index) => {
    node.branchColor = BRANCH_COLORS[index % BRANCH_COLORS.length]
  })

  const right = []
  const left = []
  firstLevel.forEach((node, index) => {
    const target = index % 2 === 0 ? right : left
    target.push(cloneLayoutNode(node, 1, index % 2 === 0 ? 'right' : 'left', node.branchColor))
  })

  root.children = [...right, ...left]
  root.width = ROOT_WIDTH
  root.height = ROOT_HEIGHT
  root.x = 0
  root.y = 0

  const nodes = [root]
  const links = []
  const rightHeight = measureSide(right, collapsedMap)
  const leftHeight = measureSide(left, collapsedMap)

  placeSide(right, 'right', -rightHeight / 2, root, nodes, links, collapsedMap)
  placeSide(left, 'left', -leftHeight / 2, root, nodes, links, collapsedMap)

  const bounds = calculateBounds(nodes)
  const shiftX = STAGE_PADDING - bounds.minX
  const shiftY = STAGE_PADDING - bounds.minY
  nodes.forEach(node => { node.x += shiftX; node.y += shiftY })
  links.forEach(link => {
    link.x1 += shiftX; link.y1 += shiftY
    link.x2 += shiftX; link.y2 += shiftY
    const geometry = lineGeometry(link.x1, link.y1, link.x2, link.y2)
    link.length = geometry.length
    link.angle = geometry.angle
  })

  return {
    width: Math.ceil(bounds.maxX - bounds.minX + STAGE_PADDING * 2),
    height: Math.ceil(bounds.maxY - bounds.minY + STAGE_PADDING * 2),
    root, nodes, links
  }
}

function cloneLayoutNode(node, depth, side, branchColor) {
  const width = depth === 0 ? ROOT_WIDTH : NODE_WIDTH
  const height = depth === 0 ? ROOT_HEIGHT : NODE_HEIGHT
  return {
    id: node.id,
    label: node.label,
    depth, side,
    width, height,
    x: 0, y: 0,
    subtreeHeight: height,
    branchColor: branchColor || (depth === 0 ? null : BRANCH_COLORS[0]),
    hasChildren: Array.isArray(node.children) && node.children.length > 0,
    children: (node.children || []).map(child =>
      cloneLayoutNode(child, depth + 1, side, branchColor)
    )
  }
}

function measureSide(nodes, collapsedMap) {
  if (!nodes.length) return 0
  let total = 0
  nodes.forEach((node, index) => {
    total += measureSubtree(node, collapsedMap)
    if (index < nodes.length - 1) total += SIBLING_GAP + 8
  })
  return total
}

function measureSubtree(node, collapsedMap) {
  node.width = node.depth === 0 ? ROOT_WIDTH : NODE_WIDTH
  node.height = node.depth === 0 ? ROOT_HEIGHT : NODE_HEIGHT
  const visibleChildren = getVisibleChildren(node, collapsedMap)
  if (!visibleChildren.length) {
    node.subtreeHeight = node.height
    return node.subtreeHeight
  }
  const childrenHeight = visibleChildren.reduce((sum, child, index) => {
    const gap = index === 0 ? 0 : SIBLING_GAP
    return sum + gap + measureSubtree(child, collapsedMap)
  }, 0)
  node.subtreeHeight = Math.max(node.height, childrenHeight)
  return node.subtreeHeight
}

function placeSide(nodes, side, startY, root, outputNodes, outputLinks, collapsedMap) {
  let cursorY = startY
  nodes.forEach(node => {
    const centerY = cursorY + node.subtreeHeight / 2
    placeSubtree(node, side, 1, centerY, outputNodes, outputLinks, collapsedMap)
    addLink(root, node, outputLinks)
    cursorY += node.subtreeHeight + SIBLING_GAP + 8
  })
}

function placeSubtree(node, side, depth, centerY, outputNodes, outputLinks, collapsedMap) {
  node.side = side
  node.depth = depth
  node.x = side === 'right'
    ? ROOT_WIDTH / 2 + ROOT_GAP + NODE_WIDTH / 2 + (depth - 1) * LEVEL_GAP
    : -(ROOT_WIDTH / 2 + ROOT_GAP + NODE_WIDTH / 2 + (depth - 1) * LEVEL_GAP)
  node.y = centerY
  outputNodes.push(node)

  const visibleChildren = getVisibleChildren(node, collapsedMap)
  if (!visibleChildren.length) return
  let cursorY = centerY - node.subtreeHeight / 2
  visibleChildren.forEach(child => {
    const childY = cursorY + child.subtreeHeight / 2
    placeSubtree(child, side, depth + 1, childY, outputNodes, outputLinks, collapsedMap)
    addLink(node, child, outputLinks)
    cursorY += child.subtreeHeight + SIBLING_GAP
  })
}

function getVisibleChildren(node, collapsedMap) {
  if (collapsedMap[node.id]) return []
  return node.children || []
}

function addLink(parent, child, outputLinks) {
  const direction = child.side === 'left' ? -1 : 1
  const x1 = parent.x + direction * parent.width / 2
  const y1 = parent.y
  const x2 = child.x - direction * child.width / 2
  const y2 = child.y
  outputLinks.push({
    id: `${parent.id}->${child.id}`,
    side: child.side,
    depth: child.depth,
    branchColor: child.branchColor,
    x1, y1, x2, y2,
    ...lineGeometry(x1, y1, x2, y2)
  })
}

function lineGeometry(x1, y1, x2, y2) {
  const dx = x2 - x1
  const dy = y2 - y1
  return {
    length: Math.sqrt(dx * dx + dy * dy),
    angle: Math.atan2(dy, dx) * 180 / Math.PI
  }
}

function calculateBounds(nodes) {
  return nodes.reduce((bounds, node) => ({
    minX: Math.min(bounds.minX, node.x - node.width / 2),
    maxX: Math.max(bounds.maxX, node.x + node.width / 2),
    minY: Math.min(bounds.minY, node.y - node.height / 2),
    maxY: Math.max(bounds.maxY, node.y + node.height / 2)
  }), {
    minX: -ROOT_WIDTH / 2, maxX: ROOT_WIDTH / 2,
    minY: -ROOT_HEIGHT / 2, maxY: ROOT_HEIGHT / 2
  })
}

function clamp(value, min, max) {
  return Math.min(max, Math.max(min, value))
}

function saveMindmap() {
  // #ifdef H5
  if (!layout.value || !layout.value.nodes || !layout.value.nodes.length) {
    uni.showToast({ title: '暂无数据', icon: 'none' })
    return
  }
  uni.showLoading({ title: '导出中...' })
  exportMindmapAsPNG(layout.value, mindmap.title)
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
  uni.showToast({ title: '请在 H5 端导出', icon: 'none' })
  // #endif
}

function regenerate() {
  uni.navigateBack()
}

function shareMindmap() {
  uni.showToast({ title: '分享能力预留', icon: 'none' })
}

// 打开优化弹窗
function openOptimizeSheet() {
  currentMindMapData.value = {
    title: mindmap.title,
    nodes: mindmap.nodes
  }
  showOptimizeSheet.value = true
}

// 处理优化提交
async function onOptimize(payload) {
  try {
    showOptimizeSheet.value = false
    uni.showLoading({ title: '优化中...' })
    const result = await optimizeMindmap(payload)
    uni.setStorageSync(`aiMindmapResult:${result.id}`, result)
    uni.navigateTo({
      url: `/subpackage_ai/mindmapGenerating/mindmapGenerating?id=${encodeURIComponent(result.id)}`
    })
  } catch (error) {
    uni.showToast({ title: getErrorMessage(error, '优化失败'), icon: 'none' })
  } finally {
    uni.hideLoading()
  }
}

onMounted(() => {
  measureCanvas()
  const options = readPageOptions()
  resultId.value = decodeURIComponent(options.id || options.resultId || '')
  loadMindmap(resultId.value)

  // 等 DOM 就绪后绑定原生 wheel 事件
  nextTick(() => {
    const query = uni.createSelectorQuery()
    query.select('.canvas').fields({ node: true, size: true }, (res) => {
      if (res?.node) {
        res.node.addEventListener('wheel', handleNativeWheel, { passive: false })
      }
    }).exec()
    // 备选：直接取 DOM 元素
    const el = document?.querySelector?.('.canvas')
    if (el) {
      el.addEventListener('wheel', handleNativeWheel, { passive: false })
    }
  })
})

onUnmounted(() => {
  const el = document?.querySelector?.('.canvas')
  if (el) {
    el.removeEventListener('wheel', handleNativeWheel)
  }
})
</script>

<style lang="scss" scoped>
.page {
  display: flex;
  min-height: 100vh;
  flex-direction: column;
  background: #FAFBFC;
  color: #1C2E48;
}

/* 画布外层：放浮动按钮、加载态文本 */
.canvas-wrapper {
  position: relative;
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  background: #FAFBFC;
  overflow: hidden;
}

/* 画布（scroll-view，可滚动） */
.canvas {
  flex: 1;
  min-height: 0;
  background: #FAFBFC;
}

/* scroll-view 的可滚动内容，尺寸 = 布局 × scale
   padding-bottom 200rpx 避让底部 AiResultBar（约 144rpx + 安全区） */
.canvas-content {
  position: relative;
  flex-shrink: 0;
  padding-bottom: 200rpx;
}

.mindmap-stage {
  position: absolute;
  left: 0; top: 0;
  overflow: visible;
  transition: transform 0.08s linear;
}

/* 节点基础样式 */
.mind-node {
  position: absolute;
  z-index: 2;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 22px;
  box-sizing: border-box;
  transition: transform 0.15s ease, box-shadow 0.15s ease;
}

.mind-node:active {
  transform: scale(0.96);
}

/* 根节点：深色大卡 */
.mind-node--root {
  border: 0;
  border-radius: 28px;
  background: #1E293B;
  box-shadow: 0 12rpx 36rpx rgba(30, 41, 59, 0.25);
  padding: 0 32rpx;
}

.mind-node--root .mind-node__label {
  color: #FFFFFF;
  font-size: 32rpx;
  font-weight: 800;
  letter-spacing: 1rpx;
}

/* 一级分支节点：彩色药丸 */
.mind-node--branch {
  border: 0;
  border-radius: 22px;
  background: var(--branch-main, #4D6BFE);
  box-shadow: 0 6rpx 20rpx rgba(0, 0, 0, 0.12);
  padding: 0 24rpx;
}

.mind-node--branch .mind-node__label {
  color: #FFFFFF;
  font-size: 26rpx;
  font-weight: 700;
}

/* 子节点：白色小卡 + 左侧色条 */
.mind-node--sub {
  border: 1rpx solid var(--branch-border, #E2E8F0);
  border-radius: 18px;
  background: #FFFFFF;
  box-shadow: 0 4rpx 12rpx rgba(0, 0, 0, 0.04);
  padding: 0 20rpx;
}

.mind-node--sub::before {
  content: '';
  position: absolute;
  top: 0; bottom: 0;
  width: 6rpx;
  background: var(--branch-main, #4D6BFE);
  border-radius: 3rpx 0 0 3rpx;
  left: 0;
  right: auto;
}

.mind-node--sub.mind-node--left::before {
  left: auto;
  right: 0;
  border-radius: 0 3rpx 3rpx 0;
}

.mind-node--sub .mind-node__label {
  color: #334155;
  font-size: 24rpx;
  font-weight: 600;
}

/* 收起状态 */
.mind-node--collapsed {
  opacity: 0.85;
}

/* 节点标签 */
.mind-node__label {
  max-width: 100%;
  line-height: 1.3;
  text-align: center;
  word-break: break-all;
}

/* 展开/收起按钮 */
.mind-node__toggle {
  position: absolute;
  right: -12px;
  top: 50%;
  transform: translateY(-50%);
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24px;
  height: 24px;
  border-radius: 50%;
  background: var(--toggle-bg, #4D6BFE);
  box-shadow: 0 2rpx 8rpx rgba(0, 0, 0, 0.18);
  z-index: 3;
}

.mind-node__toggle-icon {
  color: var(--toggle-color, #FFF);
  font-size: 11px;
  font-weight: 800;
  line-height: 1;
}

/* 连线（XMind 风格） */
.mind-link {
  position: absolute;
  z-index: 1;
  border-radius: 999px;
  transform-origin: 0 50%;
  pointer-events: none;
}

/* 状态文字 */
.state-text {
  position: absolute;
  left: 0; right: 0;
  top: 46%;
  color: #94A3B8;
  font-size: 26rpx;
  text-align: center;
}

/* 展开/收起全部浮动按钮 — absolute 相对 .canvas-wrapper 定位
   .canvas-wrapper 起点 = nav-placeholder 底部 = NavBar 实际底边
   所以 top: 16rpx 自动对齐到 NavBar 下方 16rpx，不依赖任何 NavBar 高度计算 */
.expand-controls {
  position: absolute;
  top: 16rpx;
  right: 16rpx;
  display: flex;
  flex-direction: column;
  gap: 12rpx;
  z-index: 50;
}

/* 卡片 — 白色圆角矩形 */
.expand-card {
  display: flex;
  align-items: center;
  gap: 12rpx;
  padding: 10rpx 20rpx;
  background: #FFFFFF;
  border-radius: 20rpx;
  box-shadow: 0 4rpx 20rpx rgba(0, 0, 0, 0.06);
}

.expand-card:active {
  opacity: 0.8;
}

/* 圆形图标容器 — 淡紫色背景 */
.expand-icon-circle {
  width: 44rpx;
  height: 44rpx;
  border-radius: 50%;
  background: #EEEDFF;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

/* 三角形图标 — Unicode 字符，flex 自动居中 */
.expand-triangle-char {
  color: #5B6BFE;
  font-size: 14px;
  font-weight: 700;
  line-height: 1;
}

/* 文字 */
.expand-card-text {
  font-size: 26rpx;
  color: #1E293B;
  font-weight: 700;
  letter-spacing: 1rpx;
}

/* 小程序端 cover-view 专用样式：放在 page 层级，用 position: fixed 相对视口定位 */
/* #ifdef MP-WEIXIN || MP-ALIPAY || MP-BAIDU || MP-TOUTIAO */
.expand-controls {
  position: fixed;
  /* NavBar 下方适当距离（约 88rpx + 状态栏 + 余量），用户可以微调 */
  top: calc(88rpx + env(safe-area-inset-top) + 20rpx);
  right: 16rpx;
  gap: 12rpx;
  z-index: 50;
}

.expand-card {
  gap: 8rpx;
  padding: 6rpx 16rpx;
  border-radius: 16rpx;
  /* cover-view 不支持 box-shadow，用 border 模拟立体感 */
  box-shadow: none;
  border: 1rpx solid rgba(91, 107, 254, 0.15);
}

.expand-icon-circle {
  width: 32rpx;
  height: 32rpx;
}

.expand-triangle {
  color: #5B6BFE;
  font-size: 12px;
  font-weight: 700;
  line-height: 32rpx;
  text-align: center;
}

.expand-card-text {
  font-size: 24rpx;
  color: #1E293B;
  font-weight: 700;
  letter-spacing: 1rpx;
  line-height: 32rpx;
}
/* #endif */

/* 底部操作栏样式已抽到 subpackage_ai/components/AiResultBar.vue
   这里不再写底部栏 CSS，由组件提供 */
</style>
