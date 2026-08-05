<template>
  <view class="page">
    <nav-bar
      :title="architectureData.title || 'AI 架构图'"
      :subtitle="architectureData.subtitle || ''"
      :showBack="true"
      :border="false"
      :fixed="true"
      :placeholder="true"
      titleAlign="center"
    >
      <template #right>
        <view class="nav-history-action" @tap="share">
          <text class="nav-share-text">分享</text>
        </view>
      </template>
    </nav-bar>

    <!-- 缩放控制（悬浮右上角） -->
    <view class="zoom-controls">
      <view class="zoom-btn" @tap="zoomOut"><text class="zoom-btn-text">−</text></view>
      <text class="zoom-value">{{ Math.round(scale * 100) }}%</text>
      <view class="zoom-btn" @tap="zoomIn"><text class="zoom-btn-text">＋</text></view>
      <view class="zoom-btn" @tap="zoomFit"><text class="zoom-btn-text">⤢</text></view>
    </view>

    <view class="canvas-wrapper" :class="{ 'canvas-wrapper--dragging': isDragging }">
      <scroll-view
        ref="canvasRef"
        class="canvas"
        scroll-x
        scroll-y
        :scroll-top="scrollTop"
        :scroll-left="scrollLeft"
        :show-scrollbar="false"
        :throttle="false"
        :upper-threshold="0"
        :lower-threshold="0"
        @scroll="onScroll"
      >
        <view
          class="canvas-content"
          :style="contentStyle"
        >
          <view
            class="diagram-stage"
            :style="stageStyle"
          >
            <view class="diagram-wrap">
              <!-- 主架构区 -->
              <view class="layers">
                <template v-for="(layer, layerIdx) in architectureData.layers" :key="layer.key">
                  <view class="layer">
                    <!-- 层标签 -->
                    <view class="layer-label" :style="layerLabelStyle(layer)">
                      <view class="layer-label-icon" :style="layerLabelIconStyle(layer)">
                        <arch-icon :iconKey="layer.iconKey" color="#FFFFFF" :size="56" />
                      </view>
                      <text class="layer-label-text" :style="{ color: layer.color }">{{ layer.name }}</text>
                    </view>

                    <!-- 层内卡片 -->
                    <view class="layer-cards">
                      <view
                        v-for="node in layer.nodes"
                        :key="node.name"
                        class="arch-card"
                        @tap="onCardTap(layer, node)"
                        @mousedown.stop
                      >
                        <view class="arch-card-icon" :style="{ color: layer.color }">
                          <arch-icon :iconKey="node.iconKey || defaultIconKey(layer)" :color="layer.color" :size="56" />
                        </view>
                        <text class="arch-card-name">{{ node.name }}</text>
                        <text v-if="node.description" class="arch-card-desc">{{ node.description }}</text>
                        <view v-if="node.tech && node.tech.length" class="arch-card-tech">
                          <text
                            v-for="t in node.tech"
                            :key="t"
                            class="arch-card-tech-item"
                            :style="{ color: layer.color, borderColor: layer.color + '55' }"
                          >{{ t }}</text>
                        </view>
                      </view>
                    </view>
                  </view>
                </template>
              </view>

              <!-- 右侧第三方服务 -->
              <view class="third-party" @mousedown.stop>
                <text class="third-party-title">第三方服务</text>
                <view
                  v-for="tp in architectureData.thirdParty"
                  :key="tp.name"
                  class="third-party-item"
                >
                  <view class="third-party-icon" :style="thirdPartyIconStyle(tp)">
                    <arch-icon :iconKey="tp.iconKey" color="#FFFFFF" :size="40" />
                  </view>
                  <text class="third-party-name">{{ tp.name }}</text>
                  <text v-if="tp.description" class="third-party-desc">{{ tp.description }}</text>
                </view>
              </view>
            </view>

            <!-- 底部特性展示 -->
            <view v-if="architectureData.features && architectureData.features.length" class="features-row">
              <view
                v-for="(feat, fIdx) in architectureData.features"
                :key="feat"
                class="feature-item"
              >
                <view class="feature-check">
                  <text class="feature-check-icon">✓</text>
                </view>
                <text class="feature-text">{{ feat }}</text>
                <view v-if="fIdx < architectureData.features.length - 1" class="feature-dot"></view>
              </view>
            </view>
          </view>
        </view>
      </scroll-view>
    </view>

    <!-- 底部操作栏 -->
    <AiResultBar
      @export="exportImage"
      @optimize="regenerate"
      @share="share"
    />
  </view>
</template>

<script setup>
import { computed, nextTick, onMounted, onUnmounted, ref } from 'vue'
import NavBar from '@/components/nav-bar/nav-bar.vue'
import ArchIcon from './ArchIcon.vue'
import AiResultBar from '../components/AiResultBar.vue'
import { DEFAULT_ARCHITECTURE_DATA } from './architectureData.js'
import { getArchitectureDetail, normalizeArchitectureResult } from '@/api/architecture.js'

// 缩放参数（参考 mindmapViewer）
const MIN_SCALE = 0.4
const MAX_SCALE = 2.5

// 架构数据（默认匹配截图）
const architectureData = ref(JSON.parse(JSON.stringify(DEFAULT_ARCHITECTURE_DATA)))

// 缩放/滚动状态
const scale = ref(1)
const scrollTop = ref(0)
const scrollLeft = ref(0)
const canvasSize = ref({ width: 0, height: 0 })
const canvasRef = ref(null)
const stageSize = ref({ width: 0, height: 0 })

// 鼠标拖动状态
const isDragging = ref(false)
const dragState = {
  startX: 0,
  startY: 0,
  startScrollLeft: 0,
  startScrollTop: 0,
  moved: false
}

// 各层的标签/卡片颜色
function layerLabelStyle(layer) {
  return {
    background: layer.bg,
    borderColor: layer.border,
  }
}

function layerLabelIconStyle(layer) {
  return {
    background: layer.color,
  }
}

// 各层卡片默认图标（如果节点没指定）
function defaultIconKey(layer) {
  return layer.iconKey
}

// 第三方服务图标颜色
const THIRD_PARTY_PALETTE = [
  { main: '#3B82F6', light: '#EFF6FF', border: '#BFDBFE' },
  { main: '#10B981', light: '#ECFDF5', border: '#A7F3D0' },
  { main: '#F59E0B', light: '#FFFBEB', border: '#FDE68A' },
  { main: '#8B5CF6', light: '#F5F3FF', border: '#DDD6FE' },
]

function thirdPartyIconStyle(tp) {
  const idx = (architectureData.value.thirdParty || []).findIndex(item => item.name === tp.name)
  const palette = THIRD_PARTY_PALETTE[idx % THIRD_PARTY_PALETTE.length]
  return { background: palette.main }
}

function clamp(value, min, max) {
  return Math.max(min, Math.min(max, value))
}

// stage 缩放样式
const stageStyle = computed(() => ({
  width: `${stageSize.value.width}px`,
  height: `${stageSize.value.height}px`,
  transform: `scale(${scale.value})`,
  transformOrigin: '0 0',
}))

// canvas-content 尺寸 = stage × scale（决定 scroll-view 可滚动范围）
const contentStyle = computed(() => ({
  width: `${Math.max(1, stageSize.value.width * scale.value)}px`,
  height: `${Math.max(1, stageSize.value.height * scale.value)}px`,
}))

// scroll-view 滚动同步
function onScroll(event) {
  const detail = event?.detail || {}
  if (typeof detail.scrollLeft === 'number') scrollLeft.value = detail.scrollLeft
  if (typeof detail.scrollTop === 'number') scrollTop.value = detail.scrollTop
}

// 滚轮事件：Ctrl/Cmd 缩放、Shift 横移
function handleNativeWheel(event) {
  const deltaY = event.deltaY
  const deltaX = event.deltaX
  if (!deltaY && !deltaX) return

  // Ctrl/Cmd + 滚轮 = 缩放
  if (event.ctrlKey || event.metaKey) {
    event.preventDefault()
    const nextScale = clamp(
      Number((scale.value + (deltaY > 0 ? -0.1 : 0.1)).toFixed(2)),
      MIN_SCALE, MAX_SCALE
    )
    if (nextScale === scale.value) return
    const rect = canvasRef.value?.$el?.getBoundingClientRect?.() || canvasRef.value?.getBoundingClientRect?.()
    const point = rect
      ? { x: event.clientX - rect.left, y: event.clientY - rect.top }
      : null
    if (!point) { scale.value = nextScale; return }
    // 以鼠标位置为中心缩放
    const beforeX = (point.x + scrollLeft.value) / scale.value
    const beforeY = (point.y + scrollTop.value) / scale.value
    scale.value = nextScale
    scrollLeft.value = Math.max(0, Math.round(beforeX * nextScale - point.x))
    scrollTop.value = Math.max(0, Math.round(beforeY * nextScale - point.y))
    return
  }

  // Shift + 滚轮 = 左右横移
  if (event.shiftKey && deltaY) {
    event.preventDefault()
    scrollLeft.value = Math.max(0, scrollLeft.value + deltaY)
    return
  }
}

// 左键按下：记录起点（不在卡片/第三方服务上才响应）
function handleMouseDown(event) {
  if (event.button !== 0) return
  isDragging.value = true
  dragState.startX = event.clientX
  dragState.startY = event.clientY
  dragState.startScrollLeft = scrollLeft.value
  dragState.startScrollTop = scrollTop.value
  dragState.moved = false
  document.addEventListener('mousemove', handleMouseMove)
  document.addEventListener('mouseup', handleMouseUp)
}

function handleMouseMove(event) {
  if (!isDragging.value) return
  event.preventDefault()
  const dx = event.clientX - dragState.startX
  const dy = event.clientY - dragState.startY
  if (Math.abs(dx) > 2 || Math.abs(dy) > 2) dragState.moved = true
  scrollLeft.value = Math.max(0, dragState.startScrollLeft - dx)
  scrollTop.value = Math.max(0, dragState.startScrollTop - dy)
}

function handleMouseUp(event) {
  isDragging.value = false
  document.removeEventListener('mousemove', handleMouseMove)
  document.removeEventListener('mouseup', handleMouseUp)
}

// 适配屏幕的初始缩放
function getFitScale() {
  const width = canvasSize.value.width || uni.getSystemInfoSync().windowWidth
  const height = canvasSize.value.height || 600
  const mapWidth = Math.max(stageSize.value.width, 1)
  const mapHeight = Math.max(stageSize.value.height, 1)
  const fit = Math.min(width / mapWidth, height / mapHeight, 1)
  return Math.max(0.5, Number(fit.toFixed(2)))
}

function centerStage() {
  const width = canvasSize.value.width || uni.getSystemInfoSync().windowWidth
  const height = canvasSize.value.height || 600
  const stageW = stageSize.value.width * scale.value
  const stageH = stageSize.value.height * scale.value
  scrollLeft.value = Math.max(0, Math.round((stageW - width) / 2))
  scrollTop.value = Math.max(0, Math.round((stageH - height) / 2))
}

function zoomIn() {
  scale.value = clamp(Number((scale.value + 0.1).toFixed(2)), MIN_SCALE, MAX_SCALE)
}
function zoomOut() {
  scale.value = clamp(Number((scale.value - 0.1).toFixed(2)), MIN_SCALE, MAX_SCALE)
}
function zoomFit() {
  scale.value = getFitScale()
  nextTick(centerStage)
}

// 测量画布与 stage 尺寸
function measureAll(callback) {
  const query = uni.createSelectorQuery()
  query.select('.canvas').boundingClientRect(rect => {
    if (rect) canvasSize.value = { width: rect.width, height: rect.height }
  })
  query.select('.diagram-stage').boundingClientRect(rect => {
    if (rect && rect.width && rect.height) {
      const w = Math.round(rect.width / Math.max(scale.value, 0.01))
      const h = Math.round(rect.height / Math.max(scale.value, 0.01))
      stageSize.value = { width: w, height: h }
    } else {
      const layers = architectureData.value.layers || []
      stageSize.value = {
        width: 760 + 200 + 40,
        height: Math.max(layers.length * 240, 600) + 120
      }
    }
    callback?.()
  }).exec()
}

// 卡片点击
function onCardTap(layer, node) {
  uni.showToast({
    title: `${layer.name} · ${node.name}`,
    icon: 'none',
    duration: 1500
  })
}

function share() { uni.showToast({ title: '分享能力预留', icon: 'none' }) }
function regenerate() { uni.navigateBack() }
function exportImage() { uni.showToast({ title: '导出功能预留', icon: 'none' }) }

// 加载后端数据
async function loadArchitecture() {
  try {
    const pages = getCurrentPages()
    const current = pages[pages.length - 1] || {}
    const options = current.options || current.$page?.options || {}
    const id = options.recordId ? decodeURIComponent(options.recordId) : ''

    let normalized = null
    if (id) {
      let raw = uni.getStorageSync(`aiArchitectureResult:${id}`)
      if (!raw) raw = await getArchitectureDetail(id)
      normalized = normalizeArchitectureResult(raw || {})
    }

    // 用后端数据合并默认数据，缺失的层用默认数据补全
    architectureData.value = mergeWithDefaults(normalized)
  } catch (error) {
    console.warn('[architecturePreview] 加载后端数据失败:', error)
    architectureData.value = JSON.parse(JSON.stringify(DEFAULT_ARCHITECTURE_DATA))
  } finally {
    nextTick(() => {
      measureAll(() => {
        scale.value = getFitScale()
        nextTick(centerStage)
      })
    })
  }
}

// 合并：默认结构 + 后端数据
// 1. 后端数据优先级更高
// 2. 后端缺失的层，使用默认数据补全
// 3. 第三方服务和特性用后端数据；都没有则用默认
function mergeWithDefaults(normalized) {
  const base = JSON.parse(JSON.stringify(DEFAULT_ARCHITECTURE_DATA))
  if (!normalized || typeof normalized !== 'object') return base

  const result = {
    id: normalized.id || base.id,
    title: normalized.title || base.title,
    subtitle: normalized.subtitle || base.subtitle,
    style: normalized.style || base.style,
    createTime: normalized.createTime || base.createTime,
    layers: [],
    thirdParty: [],
    features: [],
  }

  // layers 合并
  const defaultLayersMap = new Map()
  base.layers.forEach(l => defaultLayersMap.set(l.key, l))
  const normalizedLayersMap = new Map()
  if (Array.isArray(normalized.layers)) {
    normalized.layers.forEach(l => {
      if (l && l.key) normalizedLayersMap.set(l.key, l)
    })
  }
  // 每层的最小节点数（默认层节点数 = 该层期望规模）
  const MIN_NODES = { client: 3, gateway: 1, service: 4, dao: 1, storage: 2, infra: 3 }
  // 按默认顺序输出
  base.layers.forEach(defaultLayer => {
    const nl = normalizedLayersMap.get(defaultLayer.key)
    const minRequired = MIN_NODES[defaultLayer.key] || 1
    if (nl && Array.isArray(nl.nodes) && nl.nodes.length >= minRequired) {
      // 后端该层节点数达标，用后端数据
      result.layers.push({
        ...defaultLayer,
        ...nl,
        nodes: nl.nodes
      })
    } else {
      // 后端该层缺失或节点不足，用默认数据补全
      result.layers.push(JSON.parse(JSON.stringify(defaultLayer)))
    }
  })
  // 后端独有的额外层（不在默认6层中）
  normalizedLayersMap.forEach((nl, key) => {
    if (!defaultLayersMap.has(key) && Array.isArray(nl.nodes) && nl.nodes.length) {
      result.layers.push(nl)
    }
  })

  // 第三方服务：后端有则用后端，否则用默认
  if (Array.isArray(normalized.thirdParty) && normalized.thirdParty.length) {
    result.thirdParty = normalized.thirdParty
  } else {
    result.thirdParty = base.thirdParty
  }

  // 特性：后端有则用后端，否则用默认
  if (Array.isArray(normalized.features) && normalized.features.length) {
    result.features = normalized.features
  } else {
    result.features = base.features
  }

  return result
}

onMounted(() => {
  nextTick(() => {
    measureAll(() => {
      scale.value = getFitScale()
      nextTick(centerStage)
    })
    // 绑定原生 wheel 事件到 canvas（H5 才能用 preventDefault）
    const query = uni.createSelectorQuery()
    query.select('.canvas').fields({ node: true, size: true }, (res) => {
      if (res?.node) {
        res.node.addEventListener('wheel', handleNativeWheel, { passive: false })
      }
    }).exec()
    const el = document?.querySelector?.('.canvas')
    if (el) {
      el.addEventListener('wheel', handleNativeWheel, { passive: false })
      // 左键按下：开始拖动
      el.addEventListener('mousedown', handleMouseDown)
    }
  })
})

onUnmounted(() => {
  const el = document?.querySelector?.('.canvas')
  if (el) {
    el.removeEventListener('wheel', handleNativeWheel)
    el.removeEventListener('mousedown', handleMouseDown)
  }
  document.removeEventListener('mousemove', handleMouseMove)
  document.removeEventListener('mouseup', handleMouseUp)
})

loadArchitecture()
</script>

<style lang="scss" scoped>
.page {
  display: flex;
  min-height: 100vh;
  flex-direction: column;
  background: #FAFBFC;
  color: #1C2E48;
}

.nav-history-action {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 64rpx;
  height: 64rpx;
  border-radius: 999rpx;
  transition: background-color 0.18s ease, transform 0.12s ease;
}

.nav-history-action:active {
  background: rgba(15, 23, 42, 0.06);
  transform: scale(0.96);
}

.nav-share-text {
  font-size: 26rpx;
  color: #1D1D1F;
  font-weight: 600;
}

/* ===== 画布区域（可缩放/可滚动/可拖动） ===== */
.canvas-wrapper {
  position: relative;
  flex: 1;
  min-height: 0;
  display: flex;
  flex-direction: column;
  background: #FAFBFC;
  overflow: hidden;
  cursor: grab;
  user-select: none;
  -webkit-user-select: none;
}

.canvas-wrapper--dragging {
  cursor: grabbing;
}

.canvas {
  flex: 1;
  min-height: 0;
  background: #FAFBFC;
}

/* canvas-content 尺寸 = stage × scale，padding-bottom 200rpx 避让底部 AiResultBar */
.canvas-content {
  position: relative;
  flex-shrink: 0;
  padding-bottom: 200rpx;
}

.diagram-stage {
  position: absolute;
  left: 0;
  top: 0;
  transform-origin: 0 0;
  transition: transform 0.08s linear;
}

.diagram-wrap {
  position: relative;
  display: flex;
  flex-direction: column;
  align-items: stretch;
  gap: 16rpx;
  padding: 16rpx 24rpx;
  box-sizing: border-box;
  min-width: max-content;
}

/* ===== 主架构区（左侧） ===== */
.layers {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
  min-width: 760rpx;
  flex: 1;
}

.layer {
  display: flex;
  flex-direction: row;
  align-items: stretch;
  gap: 16rpx;
}

.layer-label {
  width: 100rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  border-radius: 20rpx;
  border: 2rpx solid;
  padding: 28rpx 12rpx;
  flex-shrink: 0;
  gap: 24rpx;
}

.layer-label-icon {
  width: 80rpx;
  height: 80rpx;
  border-radius: 18rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.layer-label-text {
  font-size: 30rpx;
  font-weight: 700;
  text-align: center;
  line-height: 1.2;
  letter-spacing: 4rpx;
  writing-mode: vertical-rl;
  text-orientation: upright;
}

.layer-cards {
  flex: 1;
  display: flex;
  flex-direction: row;
  flex-wrap: wrap;
  gap: 16rpx;
  min-width: 0;
  align-content: flex-start;
}

.arch-card {
  flex: 1 1 0;
  min-width: 140rpx;
  max-width: 200rpx;
  background: #FFFFFF;
  border-radius: 16rpx;
  border: 2rpx solid #E5E7EB;
  padding: 20rpx 14rpx 16rpx;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  align-items: center;
  box-shadow: 0 4rpx 12rpx rgba(15, 23, 42, 0.05);
  cursor: pointer;
}

.arch-card:active {
  transform: scale(0.98);
}

.arch-card-icon {
  width: 56rpx;
  height: 56rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 10rpx;
}

.arch-card-name {
  font-size: 26rpx;
  font-weight: 700;
  color: #111827;
  text-align: center;
  line-height: 1.3;
  margin-bottom: 6rpx;
}

.arch-card-desc {
  font-size: 20rpx;
  color: #6B7280;
  text-align: center;
  line-height: 1.5;
  white-space: pre-line;
  margin-bottom: 8rpx;
  word-break: break-all;
}

.arch-card-tech {
  display: flex;
  flex-wrap: wrap;
  justify-content: center;
  gap: 4rpx;
  margin-top: 2rpx;
}

.arch-card-tech-item {
  font-size: 18rpx;
  font-weight: 500;
  padding: 2rpx 10rpx;
  border-radius: 999rpx;
  border: 1rpx solid;
  line-height: 1.3;
}

.third-party {
  width: 100%;
  flex-shrink: 0;
  background: #FFFFFF;
  border-radius: 16rpx;
  border: 2rpx solid #E5E7EB;
  padding: 20rpx;
  box-sizing: border-box;
  display: flex;
  flex-direction: row;
  flex-wrap: wrap;
  align-items: center;
  gap: 16rpx;
  box-shadow: 0 2rpx 8rpx rgba(15, 23, 42, 0.04);
}

.third-party-title {
  width: 100%;
  font-size: 24rpx;
  font-weight: 700;
  color: #1F2937;
  margin-bottom: 4rpx;
}

.third-party-item {
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 12rpx;
  background: #F9FAFB;
  border: 1rpx solid #EEF1F4;
  border-radius: 12rpx;
  padding: 10rpx 16rpx;
}

.third-party-icon {
  width: 44rpx;
  height: 44rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.third-party-name {
  font-size: 22rpx;
  font-weight: 700;
  color: #1F2937;
  text-align: left;
  line-height: 1.3;
}

.third-party-desc {
  display: none;
}

/* ===== 底部特性标签（居中胶囊） ===== */
.features-row {
  display: flex;
  flex-direction: row;
  flex-wrap: wrap;
  align-items: center;
  justify-content: center;
  gap: 16rpx;
  padding: 8rpx 24rpx 24rpx;
}
.feature-item {
  display: flex;
  align-items: center;
  gap: 8rpx;
  background: #FFFFFF;
  border: 1rpx solid #E5E7EB;
  border-radius: 999rpx;
  padding: 8rpx 20rpx;
}
.feature-check {
  width: 30rpx;
  height: 30rpx;
  border-radius: 50%;
  background: #E8F8F0;
  display: flex;
  align-items: center;
  justify-content: center;
}
.feature-check-icon { color: #10B981; font-size: 20rpx; line-height: 1; }
.feature-text { font-size: 22rpx; color: #374151; font-weight: 600; }
.feature-dot { display: none; }

/* ===== 缩放控制（悬浮右上角） ===== */
.zoom-controls { position: fixed; top: 200rpx; right: 24rpx; z-index: 90; display: flex; flex-direction: column; align-items: center; gap: 10rpx; background: #fff; border-radius: 16rpx; padding: 12rpx 10rpx; box-shadow: 0 4rpx 20rpx rgba(0, 0, 0, 0.08); }
.zoom-btn { width: 52rpx; height: 52rpx; border-radius: 12rpx; background: #f1f4f8; display: flex; align-items: center; justify-content: center; }
.zoom-btn-text { font-size: 30rpx; color: #58728c; line-height: 1; }
.zoom-value { font-size: 20rpx; color: #8290a1; }

/* 底部操作栏样式已抽到 subpackage_ai/components/AiResultBar.vue
   这里不再写底部栏 CSS，由组件提供 */
</style>
