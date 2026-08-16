<template>
  <view class="page">
    <!-- 顶部 -->
    <view v-if="pageState !== 'error'" class="header">
      <view class="header-back" @tap="goBack"><text class="header-back-icon">‹</text></view>
      <view v-if="!isCompleted" class="header-center">
        <view class="header-title-row">
          <text class="header-sparkle">✦</text>
          <text class="header-title-text">AI 正在生成架构图</text>
        </view>
        <text class="header-subtitle-text">{{ animationSubtitle }}</text>
      </view>
      <view v-else class="header-center">
        <view class="header-title-row">
          <text class="header-done-icon">✓</text>
          <text class="header-title-text header-title-text--done">架构图生成完成</text>
        </view>
        <text class="header-subtitle-text header-subtitle-text--done">分层结构已确认，可导出或继续优化</text>
      </view>
    </view>
    <view v-else class="header header--error">
      <view class="header-back" @tap="goBack"><text class="header-back-icon">‹</text></view>
    </view>

    <!-- 画布 -->
    <view v-if="pageState !== 'error'" class="canvas-area">
      <view class="g-stage" :class="{ smooth: smooth, settle: settleOn }" :style="stageStyle">
        <template v-for="(layer, li) in layers" :key="layer.key || li">
          <view class="layer" :class="{ in: layerIn[li] }" :style="layerStyle(layer)" :data-idx="li">
            <view class="layer-head">
              <view class="layer-icon" :style="{ background: layer.color }">
                <arch-icon :iconKey="layer.iconKey" color="#FFFFFF" :size="32" />
              </view>
              <text class="layer-name" :style="{ color: layer.color }">{{ layer.name }}</text>
            </view>
            <view v-if="layerHasGroups(layer)" class="layer-groups">
              <view
                v-for="(group, gi) in visibleLayerGroups(layer)"
                :key="group.id || group.name || gi"
                class="layer-group"
                :class="{ in: groupIn[li + '-' + gi] }"
                :style="{ borderColor: layer.border }"
              >
                <view class="layer-group-head">
                  <text class="layer-group-title" :style="{ color: layer.color }">{{ group.name }}</text>
                  <text v-if="group.description" class="layer-group-desc">{{ group.description }}</text>
                </view>
                <view class="layer-cards">
                  <view
                    v-for="(node, ci) in group.nodes"
                    :key="node.id || node.name || ci"
                    class="arch-card"
                    :class="{ in: cardIn[groupCardKey(li, gi, ci)] }"
                    :style="{ borderColor: layer.border }"
                  >
                    <text class="arch-card-name">{{ node.name }}</text>
                    <text v-if="node.description" class="arch-card-desc">{{ node.description }}</text>
                    <view v-if="node.tech && node.tech.length" class="arch-card-tech">
                      <text
                        v-for="t in node.tech"
                        :key="t"
                        class="tech"
                        :style="{ color: layer.color, borderColor: layer.color + '55' }"
                      >{{ t }}</text>
                    </view>
                    <view v-if="nodeChildren(node).length" class="node-children">
                      <view
                        v-for="(child, childIndex) in nodeChildren(node)"
                        :key="child.id || child.name || childIndex"
                        class="node-child"
                        :class="{ in: childIn[groupChildKey(li, gi, ci, childIndex)] }"
                        :style="{ borderColor: layer.color + '33', background: layer.bg }"
                      >
                        <text class="node-child-dot" :style="{ background: layer.color }"></text>
                        <text class="node-child-name">{{ child.name }}</text>
                      </view>
                    </view>
                  </view>
                </view>
              </view>
            </view>
            <view v-else class="layer-cards">
              <view
                v-for="(node, ci) in layer.nodes"
                :key="node.id || node.name || ci"
                class="arch-card"
                :class="{ in: cardIn[cardKey(li, ci)] }"
                :style="{ borderColor: layer.border }"
              >
                <text class="arch-card-name">{{ node.name }}</text>
                <text v-if="node.description" class="arch-card-desc">{{ node.description }}</text>
                <view v-if="node.tech && node.tech.length" class="arch-card-tech">
                  <text
                    v-for="t in node.tech"
                    :key="t"
                    class="tech"
                    :style="{ color: layer.color, borderColor: layer.color + '55' }"
                  >{{ t }}</text>
                </view>
                <view v-if="nodeChildren(node).length" class="node-children">
                  <view
                    v-for="(child, childIndex) in nodeChildren(node)"
                    :key="child.id || child.name || childIndex"
                    class="node-child"
                    :class="{ in: childIn[childKey(li, ci, childIndex)] }"
                    :style="{ borderColor: layer.color + '33', background: layer.bg }"
                  >
                    <text class="node-child-dot" :style="{ background: layer.color }"></text>
                    <text class="node-child-name">{{ child.name }}</text>
                  </view>
                </view>
              </view>
            </view>
          </view>
          <view v-if="li < layers.length - 1" class="layer-arrow" :class="{ in: arrowIn[li] }">
            <view class="layer-arrow-line"></view>
            <view class="layer-arrow-head">▼</view>
          </view>
        </template>

        <view class="third-party" :class="{ in: tpIn }">
          <text class="tp-title">第三方服务</text>
          <view class="tp-grid">
            <view v-for="(tp, ti) in thirdParty" :key="tp.name || ti" class="tp-item" :class="{ in: tpItemIn[ti] }">
              <view class="tp-icon"><arch-icon :iconKey="tp.iconKey" color="#FFFFFF" :size="24" /></view>
              <text class="tp-name">{{ tp.name }}</text>
            </view>
          </view>
        </view>

        <view class="features-row">
          <view v-for="(f, fi) in features" :key="f" class="feature-item" :class="{ in: featureIn[fi] }">
            <view class="feature-check"><text class="feature-check-icon">✓</text></view>
            <text class="feature-text">{{ f }}</text>
          </view>
        </view>
      </view>

      <view class="wait-ring" :class="{ hidden: !showRing }"><i></i><i></i><b></b></view>
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
          <text class="status-float-label">✦ AI 正在构建分层架构</text>
          <view class="status-float-dots"><view class="status-float-dot"></view><view class="status-float-dot"></view><view class="status-float-dot"></view></view>
        </view>
        <text class="status-float-msg">{{ animationWaitingText }}</text>
        <view class="status-float-meta">
          <text class="status-float-count">已生成 {{ madeCount }} 个结构项</text>
          <text class="status-float-pct">{{ animationProgressPercent }}%</text>
        </view>
        <view class="status-float-bar"><view class="status-float-bar-fill" :style="{ width: animationProgressPercent + '%' }"></view></view>
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
      <view class="done-btn done-btn--pri" @tap="viewResult"><text>查看架构图</text><text class="done-btn-arrow">→</text></view>
    </view>
  </view>
</template>

<script setup>
import { ref, computed, reactive, nextTick } from 'vue'
import { onLoad, onUnload } from '@dcloudio/uni-app'
import ArchIcon from '../architecturePreview/ArchIcon.vue'
import {
  generateArchitecture as requestGenerateArchitecture,
  mockGenerateArchitecture
} from '@/api/architecture.js'
import { getErrorMessage } from '@/api/aiDiagram.js'

const state = reactive({ result: null })
const pendingPayload = ref(null)
const isCompleted = ref(false)
const pageState = ref('loading')
const errorMessage = ref('')

const layers = computed(() => state.result?.layers || [])
const thirdParty = computed(() => state.result?.thirdParty || [])
const features = computed(() => state.result?.features || [])

const steps = [{}, {}, {}, {}, {}]
const stageIndex = ref(0)
const stageSubtitle = computed(() => ([
  '正在理解架构需求…',
  '正在规划分层骨架…',
  '正在逐层构建组件…',
  '正在接入第三方与特性…',
  '正在整理布局…'
][stageIndex.value] || ''))
const waitingText = computed(() => ([
  '解析系统类型与模块…',
  '分层骨架已生成…',
  '逐层填充组件与技术标签…',
  '接入第三方服务与特性…',
  '微调布局，准备输出…'
][stageIndex.value] || ''))
function stepStatus(idx) {
  if (idx < stageIndex.value) return 'done'
  if (idx === stageIndex.value) return 'active'
  return 'pending'
}
const progressPercent = computed(() => {
  if (isCompleted.value) return 100
  return [16, 28, 62, 86, 95][stageIndex.value] || 0
})

// 揭示状态（class 绑定，避免 v-if+v-for 优先级问题）
function normalizeRelationMode(value = 'AUTO') {
  const text = String(value || 'AUTO').trim().toUpperCase()
  if (text === 'DATA' || text === 'DATAFLOW') return 'DATA_FLOW'
  if (text === 'CALL_CHAIN' || text === 'CALLING' || text === 'DEPENDENCY') return 'CALL'
  if (['AUTO', 'MODULE', 'DATA_FLOW', 'CALL'].includes(text)) return text
  return 'AUTO'
}

const requestedRelationMode = computed(() => normalizeRelationMode(
  state.result?.requestedRelationMode ||
    pendingPayload.value?.relationMode ||
    pendingPayload.value?.relationType ||
    'AUTO'
))

const resolvedRelationMode = computed(() => {
  const resolved = normalizeRelationMode(state.result?.resolvedRelationMode || state.result?.relationMode)
  if (resolved !== 'AUTO') return resolved
  const requested = requestedRelationMode.value
  return requested === 'AUTO' ? 'MODULE' : requested
})

const RELATION_COPY = {
  MODULE: {
    subtitles: ['正在解析系统需求', '正在规划分层骨架', '正在展开层内模块', '正在建立层级关系', '正在优化架构布局'],
    waiting: ['识别系统组成与边界', '生成架构层与模块组', '填充分组、节点与子模块', '建立结构连接与上下游关系', '准备输出模块关系架构图'],
    progress: [16, 34, 62, 86, 95],
  },
  DATA_FLOW: {
    subtitles: ['正在解析数据来源', '正在规划数据层级', '正在展开处理模块', '正在连接数据路径', '正在优化箭头与路径'],
    waiting: ['识别输入、处理与存储位置', '生成数据入口、服务与存储分组', '填充数据处理节点和子模块', '建立跨层数据流向', '准备输出数据流架构图'],
    progress: [16, 32, 64, 88, 95],
  },
  CALL: {
    subtitles: ['正在识别系统服务', '正在拆解服务层级', '正在解析模块依赖', '正在建立调用方向', '正在优化服务布局'],
    waiting: ['识别 API、Service 与模块边界', '展开服务、适配器与组件', '生成调用链路与依赖关系', '标注跨层调用方向', '准备输出调用关系架构图'],
    progress: [16, 33, 63, 87, 95],
  },
}

const activeAnimationCopy = computed(() => {
  const base = RELATION_COPY[resolvedRelationMode.value] || RELATION_COPY.MODULE
  if (requestedRelationMode.value !== 'AUTO') return base
  return {
    ...base,
    subtitles: ['正在分析架构需求', '正在判断最合适的关系表达', `识别为：${relationModeLabel(resolvedRelationMode.value)}`, ...base.subtitles.slice(2)],
    waiting: ['分析需求中的模块、数据与调用线索', '选择关系表达模式', '进入对应架构生成流程', ...base.waiting.slice(2)],
  }
})

const animationSubtitle = computed(() => activeAnimationCopy.value.subtitles[stageIndex.value] || '')
const animationWaitingText = computed(() => activeAnimationCopy.value.waiting[stageIndex.value] || '')
const animationProgressPercent = computed(() => {
  if (isCompleted.value) return 100
  return activeAnimationCopy.value.progress[stageIndex.value] || progressPercent.value
})

function relationModeLabel(mode) {
  return {
    MODULE: '模块关系',
    DATA_FLOW: '数据流向',
    CALL: '调用关系',
  }[mode] || '模块关系'
}

const layerIn = reactive({})
const groupIn = reactive({})
const cardIn = reactive({})
const childIn = reactive({})
const arrowIn = reactive({})
const tpIn = ref(false)
const tpItemIn = reactive({})
const featureIn = reactive({})
const settleOn = ref(false)
const showRing = ref(true)
const madeCount = ref(0)

const smooth = ref(false)
const scale = ref(1)
const cameraY = ref(0)
const CAMERA_TRANSITION_MS = 420
const CAMERA_EPSILON = 1
const stageStyle = computed(() => ({
  transform: `translateY(${cameraY.value}px) scale(${scale.value})`,
  transformOrigin: '0 0',
  transition: smooth.value ? `transform ${CAMERA_TRANSITION_MS}ms cubic-bezier(0.22,1,0.36,1)` : 'none'
}))

function layerStyle(layer) {
  return { borderColor: layer.border, background: layer.bg }
}

function nodeChildren(node) {
  return Array.isArray(node?.children) ? node.children : []
}

function visibleLayerGroups(layer) {
  return Array.isArray(layer?.groups)
    ? layer.groups.filter(group => Array.isArray(group?.nodes) && group.nodes.length)
    : []
}

function layerHasGroups(layer) {
  return visibleLayerGroups(layer).length > 0
}

function cardKey(layerIndex, nodeIndex) {
  return `${layerIndex}-${nodeIndex}`
}

function childKey(layerIndex, nodeIndex, childIndex) {
  return `${layerIndex}-${nodeIndex}-${childIndex}`
}

function groupCardKey(layerIndex, groupIndex, nodeIndex) {
  return `${layerIndex}-g${groupIndex}-${nodeIndex}`
}

function groupChildKey(layerIndex, groupIndex, nodeIndex, childIndex) {
  return `${layerIndex}-g${groupIndex}-${nodeIndex}-${childIndex}`
}

function layerComponentCount(layer) {
  const groups = visibleLayerGroups(layer)
  if (groups.length) {
    return groups.reduce((sum, group) => (
      sum + 1 + (group.nodes || []).reduce((nodeSum, node) => nodeSum + 1 + nodeChildren(node).length, 0)
    ), 0)
  }
  return (layer.nodes || []).reduce((sum, node) => sum + 1 + nodeChildren(node).length, 0)
}

function totalComponents() {
  return layers.value.reduce((s, l) => s + layerComponentCount(l), 0) + thirdParty.value.length
}

function fitStage() {
  const sys = uni.getSystemInfoSync()
  const viewH = Math.max(360, sys.windowHeight - 320)
  uni.createSelectorQuery().select('.g-stage').boundingClientRect(rect => {
    if (rect && rect.height) {
      const fit = Math.min(viewH / rect.height, 1)
      scale.value = Math.max(0.4, Number(fit.toFixed(2)))
    }
  }).exec()
}

function getCameraTargetY(canvas, target) {
  const targetCenterY = (target.top - canvas.top) + target.height / 2
  const deltaY = canvas.height / 2 - targetCenterY
  return Math.round(cameraY.value + deltaY)
}

async function settleCameraTo(canvas, target) {
  const nextCameraY = getCameraTargetY(canvas, target)
  if (Math.abs(nextCameraY - cameraY.value) <= CAMERA_EPSILON) return
  cameraY.value = nextCameraY
  await sleep(CAMERA_TRANSITION_MS)
}

// 镜头跟随：让目标 layer 垂直中心对齐 canvas-area 垂直中心
// 只在换 layer 时调用，并等待镜头落稳后再进入下一段，避免读到过渡中坐标造成上下抖动。
async function followCamera(layerIdx) {
  await nextTick()
  return new Promise(resolve => {
    const canvasSel = '.canvas-area'
    const layerSel = `.layer[data-idx="${layerIdx}"]`
    uni.createSelectorQuery()
      .select(canvasSel).boundingClientRect()
      .select(layerSel).boundingClientRect()
      .exec(async res => {
        const canvas = res && res[0]
        const layer = res && res[1]
        if (!canvas || !layer) { resolve(); return }
        await settleCameraTo(canvas, layer)
        resolve()
      })
  })
}

// 镜头跟随到第三方服务区域
async function followCameraThirdParty() {
  await nextTick()
  return new Promise(resolve => {
    uni.createSelectorQuery()
      .select('.canvas-area').boundingClientRect()
      .select('.third-party').boundingClientRect()
      .exec(async res => {
        const canvas = res && res[0]
        const tp = res && res[1]
        if (!canvas || !tp) { resolve(); return }
        await settleCameraTo(canvas, tp)
        resolve()
      })
  })
}

let timers = []
function clearTimers() { timers.forEach(t => clearTimeout(t)); timers = [] }
function sleep(ms) { return new Promise(r => timers.push(setTimeout(r, ms))) }

async function runAnimation() {
  smooth.value = false
  scale.value = 1
  // 阶段0 等待
  stageIndex.value = 0
  showRing.value = true
  await sleep(600)

  // 阶段1 骨架
  stageIndex.value = 1
  showRing.value = false
  timers.push(setTimeout(() => { showRing.value = false }, 400))
  for (let li = 0; li < layers.value.length; li++) { layerIn[li] = true; await sleep(90) }
  await sleep(200)

  // 阶段2 逐层构建
  stageIndex.value = 2
  for (let li = 0; li < layers.value.length; li++) {
    const layer = layers.value[li]
    // 换 layer 时镜头跟随到当前 layer
    smooth.value = true
    await followCamera(li)
    await sleep(60)
    const groups = visibleLayerGroups(layer)
    if (groups.length) {
      for (let gi = 0; gi < groups.length; gi++) {
        groupIn[`${li}-${gi}`] = true
        madeCount.value += 1
        await sleep(70)
        const nodes = groups[gi].nodes || []
        for (let ci = 0; ci < nodes.length; ci++) {
          cardIn[groupCardKey(li, gi, ci)] = true
          madeCount.value += 1
          await sleep(70)
          for (let childIndex = 0; childIndex < nodeChildren(nodes[ci]).length; childIndex++) {
            childIn[groupChildKey(li, gi, ci, childIndex)] = true
            madeCount.value += 1
            await sleep(45)
          }
        }
      }
    } else {
      const nodes = layer.nodes || []
      for (let ci = 0; ci < nodes.length; ci++) {
        cardIn[cardKey(li, ci)] = true
        madeCount.value += 1
        await sleep(70)
        for (let childIndex = 0; childIndex < nodeChildren(nodes[ci]).length; childIndex++) {
          childIn[childKey(li, ci, childIndex)] = true
          madeCount.value += 1
          await sleep(45)
        }
      }
    }
    if (li < layers.value.length - 1) { arrowIn[li] = true; await sleep(60) }
  }

  // 阶段3 第三方 + 特性
  stageIndex.value = 3
  tpIn.value = true
  // 镜头跟随到第三方区域
  await followCameraThirdParty()
  await sleep(80)
  for (let ti = 0; ti < thirdParty.value.length; ti++) { tpItemIn[ti] = true; madeCount.value += 1; await sleep(70) }
  for (let fi = 0; fi < features.value.length; fi++) { featureIn[fi] = true; await sleep(60) }

  // 阶段4 布局
  stageIndex.value = 4
  settleOn.value = true
  smooth.value = true
  // 完成后镜头回到顶部，展示全景
  cameraY.value = 0
  fitStage()
  await sleep(700)

  isCompleted.value = true
}

async function run() {
  pageState.value = 'loading'
  errorMessage.value = ''
  try {
    const payload = uni.getStorageSync('aiArchitecturePendingPayload')
    pendingPayload.value = payload || null
    if (!payload || !payload.description) throw new Error('缺少架构描述')
    let result
    try {
      result = await requestGenerateArchitecture(payload)
    } catch (error) {
      if (error?.code || error?.statusCode) throw error
      result = await mockGenerateArchitecture(payload)
    }
    uni.setStorageSync(`aiArchitectureResult:${result.id}`, result)
    startAnimation(result)
  } catch (error) {
    errorMessage.value = getErrorMessage(error, '生成失败，请重试')
    pageState.value = 'error'
    uni.showToast({ title: errorMessage.value, icon: 'none', duration: 2500 })
  }
}

function startAnimation(result) {
  state.result = result
  if (!result.layers || !result.layers.length) {
    errorMessage.value = 'AI 未生成有效内容，请稍后重试'
    pageState.value = 'error'
    return
  }
  pageState.value = 'animating'
  runAnimation()
}

function revealAll() {
  layers.value.forEach((l, li) => {
    layerIn[li] = true
    const groups = visibleLayerGroups(l)
    if (groups.length) {
      groups.forEach((group, gi) => {
        groupIn[`${li}-${gi}`] = true
        ;(group.nodes || []).forEach((node, ci) => {
          cardIn[groupCardKey(li, gi, ci)] = true
          nodeChildren(node).forEach((child, childIndex) => { childIn[groupChildKey(li, gi, ci, childIndex)] = true })
        })
      })
    } else {
      ;(l.nodes || []).forEach((node, ci) => {
        cardIn[cardKey(li, ci)] = true
        nodeChildren(node).forEach((child, childIndex) => { childIn[childKey(li, ci, childIndex)] = true })
      })
    }
    if (li < layers.value.length - 1) arrowIn[li] = true
  })
  tpIn.value = true
  thirdParty.value.forEach((t, ti) => { tpItemIn[ti] = true })
  features.value.forEach((f, fi) => { featureIn[fi] = true })
  madeCount.value = totalComponents()
}

function skipAnimation() {
  clearTimers()
  revealAll()
  smooth.value = false
  cameraY.value = 0
  fitStage()
  isCompleted.value = true
}

function retry() {
  clearTimers()
  Object.keys(layerIn).forEach(k => delete layerIn[k])
  Object.keys(groupIn).forEach(k => delete groupIn[k])
  Object.keys(cardIn).forEach(k => delete cardIn[k])
  Object.keys(childIn).forEach(k => delete childIn[k])
  Object.keys(arrowIn).forEach(k => delete arrowIn[k])
  Object.keys(tpItemIn).forEach(k => delete tpItemIn[k])
  Object.keys(featureIn).forEach(k => delete featureIn[k])
  tpIn.value = false; settleOn.value = false; isCompleted.value = false
  madeCount.value = 0; stageIndex.value = 0; scale.value = 1; cameraY.value = 0
  run()
}

function viewResult() {
  const id = state.result?.id
  if (id != null) {
    uni.redirectTo({ url: `/subpackage_ai/architecturePreview/architecturePreview?recordId=${encodeURIComponent(id)}&title=${encodeURIComponent(state.result?.title || '')}` })
  }
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
.header-sparkle { font-size: 26rpx; color: #4D6BFE; }
.header-title-text { font-size: 30rpx; font-weight: 700; color: #1e344f; }
.header-title-text--done { font-size: 32rpx; }
.header-done-icon { font-size: 30rpx; color: #34C759; font-weight: 700; }
.header-subtitle-text { font-size: 24rpx; color: #8290a1; margin-top: 6rpx; }
.header-subtitle-text--done { color: #B0B3C5; }

.canvas-area { flex: 1; position: relative; overflow: hidden; }
.g-stage { position: absolute; left: 0; top: 0; width: 100%; padding: 24rpx 24rpx 40rpx; transform-origin: 0 0; }
.g-stage.smooth { transition: transform 0.42s cubic-bezier(0.22, 1, 0.36, 1); }

.layer { border: 2rpx solid #d8dee6; border-radius: 24rpx; padding: 18rpx; margin-bottom: 0; opacity: 0; transform: translateY(14px); transition: opacity 0.34s ease, transform 0.34s ease; }
.layer.in { opacity: 1; transform: translateY(0); }
.layer-head { display: flex; align-items: center; gap: 12rpx; margin-bottom: 14rpx; }
.layer-icon { width: 44rpx; height: 44rpx; border-radius: 14rpx; display: flex; align-items: center; justify-content: center; flex-shrink: 0; }
.layer-name { font-size: 26rpx; font-weight: 700; }
.layer-cards { display: flex; flex-wrap: wrap; gap: 14rpx; }
.layer-groups { display: flex; flex-direction: column; gap: 14rpx; }
.layer-group { border: 2rpx solid #e2e8ef; border-radius: 18rpx; padding: 14rpx; background: rgba(255, 255, 255, 0.58); opacity: 0; transform: translateY(10px); transition: opacity 0.3s ease, transform 0.3s ease; }
.layer-group.in { opacity: 1; transform: translateY(0); }
.layer-group-head { display: flex; align-items: baseline; gap: 10rpx; margin-bottom: 10rpx; min-width: 0; }
.layer-group-title { font-size: 22rpx; font-weight: 800; line-height: 1.25; }
.layer-group-desc { color: #64748B; font-size: 19rpx; line-height: 1.25; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.arch-card { background: #fff; border: 2rpx solid #e2e8ef; border-radius: 18rpx; padding: 12rpx 16rpx; min-width: 150rpx; opacity: 0; transform: translateY(10px) scale(0.85); transition: opacity 0.34s ease, transform 0.34s cubic-bezier(0.34, 1.56, 0.64, 1); }
.arch-card.in { opacity: 1; transform: translateY(0) scale(1); }
.arch-card-name { font-size: 24rpx; font-weight: 700; color: #1e344f; display: block; }
.arch-card-desc { font-size: 20rpx; color: #8290a1; display: block; margin-top: 4rpx; max-width: 220rpx; }
.arch-card-tech { display: flex; gap: 8rpx; margin-top: 8rpx; flex-wrap: wrap; }
.tech { font-size: 18rpx; padding: 2rpx 10rpx; border-radius: 10rpx; border: 2rpx solid; }
.node-children { width: 100%; display: flex; flex-direction: column; gap: 7rpx; margin-top: 10rpx; padding-top: 8rpx; border-top: 1rpx solid #eef2f7; }
.node-child { display: flex; align-items: center; gap: 8rpx; padding: 7rpx 9rpx; border: 1rpx solid; border-radius: 10rpx; opacity: 0; transform: translateY(6px); transition: opacity 0.24s ease, transform 0.24s ease; }
.node-child.in { opacity: 1; transform: translateY(0); }
.node-child-dot { width: 8rpx; height: 8rpx; border-radius: 50%; flex-shrink: 0; }
.node-child-name { color: #1f2937; font-size: 18rpx; line-height: 1.25; font-weight: 700; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.layer-arrow { display: flex; flex-direction: column; align-items: center; height: 36rpx; opacity: 0; transition: opacity 0.3s; }
.layer-arrow.in { opacity: 1; }
.layer-arrow-line { width: 4rpx; height: 22rpx; background: #b9c6d6; }
.layer-arrow-head { color: #b9c6d6; font-size: 18rpx; line-height: 1; margin-top: -4rpx; }

.third-party { border: 2rpx dashed #c3d0de; border-radius: 24rpx; padding: 18rpx; margin-top: 20rpx; opacity: 0; transform: translateX(20px); transition: opacity 0.34s ease, transform 0.34s ease; }
.third-party.in { opacity: 1; transform: translateX(0); }
.tp-title { font-size: 24rpx; font-weight: 700; color: #58728c; margin-bottom: 14rpx; display: block; }
.tp-grid { display: flex; flex-wrap: wrap; gap: 14rpx; }
.tp-item { display: flex; align-items: center; gap: 10rpx; background: #fff; border: 2rpx solid #e2e8ef; border-radius: 18rpx; padding: 10rpx 16rpx; opacity: 0; transform: scale(0.85); transition: opacity 0.34s ease, transform 0.34s ease; }
.tp-item.in { opacity: 1; transform: scale(1); }
.tp-icon { width: 36rpx; height: 36rpx; border-radius: 10rpx; background: #64748B; display: flex; align-items: center; justify-content: center; }
.tp-name { font-size: 22rpx; font-weight: 600; }

.features-row { display: flex; flex-wrap: wrap; gap: 16rpx; margin-top: 20rpx; justify-content: center; }
.feature-item { display: flex; align-items: center; gap: 8rpx; opacity: 0; transform: scale(0.8); transition: opacity 0.34s ease, transform 0.34s cubic-bezier(0.34, 1.56, 0.64, 1); }
.feature-item.in { opacity: 1; transform: scale(1); }
.feature-check { width: 26rpx; height: 26rpx; border-radius: 50%; background: #34C759; display: flex; align-items: center; justify-content: center; }
.feature-check-icon { color: #fff; font-size: 16rpx; }
.feature-text { font-size: 22rpx; color: #58728c; font-weight: 600; }

.g-stage.settle .arch-card, .g-stage.settle .layer { animation: archGlow 0.46s ease-in-out 1; }
@keyframes archGlow { 0%, 100% { filter: brightness(1); } 45% { filter: brightness(1.08); } }

.wait-ring { position: absolute; left: 50%; top: 40%; width: 148rpx; height: 148rpx; transform: translate(-50%, -50%); transition: opacity 0.3s; }
.wait-ring.hidden { opacity: 0; }
.wait-ring i { position: absolute; inset: 0; border: 4rpx solid rgba(77, 107, 254, 0.5); border-radius: 50%; animation: archRing 1.9s ease-out infinite; }
.wait-ring i:nth-child(2) { animation-delay: 0.65s; }
.wait-ring b { position: absolute; left: 50%; top: 50%; width: 20rpx; height: 20rpx; transform: translate(-50%, -50%); background: #4D6BFE; border-radius: 50%; animation: archCore 1.9s ease-in-out infinite; }
@keyframes archRing { 0% { transform: scale(0.25); opacity: 0.9; } 100% { transform: scale(1.15); opacity: 0; } }
@keyframes archCore { 0%, 100% { transform: translate(-50%, -50%) scale(1); opacity: 0.85; } 50% { transform: translate(-50%, -50%) scale(1.5); opacity: 1; } }

.skip-btn { position: absolute; right: 24rpx; bottom: 24rpx; z-index: 20; padding: 10rpx 24rpx; background: rgba(255, 255, 255, 0.92); border: 2rpx solid #e2e8ef; border-radius: 999rpx; font-size: 24rpx; color: #58728c; }

.bottom-steps { display: flex; align-items: center; justify-content: center; gap: 18rpx; padding: 16rpx 0 8rpx; }
.bottom-step-dot { width: 18rpx; height: 18rpx; border-radius: 50%; background: #d8dee6; display: flex; align-items: center; justify-content: center; }
.bottom-step-dot--done { background: #34C759; }
.bottom-step-check { color: #fff; font-size: 12rpx; font-weight: 700; }
.bottom-step-dot--active { background: #4D6BFE; animation: archStep 1.2s ease-in-out infinite; }
@keyframes archStep { 0%, 100% { opacity: 1; transform: scale(1); } 50% { opacity: 0.5; transform: scale(1.2); } }

.status-float { padding: 0 24rpx 20rpx; }
.status-float-inner { background: #fff; border-radius: 24rpx; padding: 24rpx 28rpx; box-shadow: 0 4rpx 24rpx rgba(77, 107, 254, 0.1); }
.status-float-top { display: flex; align-items: center; justify-content: space-between; margin-bottom: 8rpx; }
.status-float-label { font-size: 26rpx; font-weight: 700; color: #1e344f; }
.status-float-dots { display: flex; gap: 5rpx; }
.status-float-dot { width: 8rpx; height: 8rpx; border-radius: 50%; background: #4D6BFE; animation: archDot 1.4s ease-in-out infinite; }
.status-float-dot:nth-child(2) { animation-delay: 0.2s; }
.status-float-dot:nth-child(3) { animation-delay: 0.4s; }
@keyframes archDot { 0%, 80%, 100% { transform: scale(0.5); opacity: 0.3; } 40% { transform: scale(1); opacity: 1; } }
.status-float-msg { display: block; font-size: 24rpx; color: #8290a1; margin-bottom: 14rpx; }
.status-float-meta { display: flex; align-items: center; justify-content: space-between; margin-bottom: 10rpx; }
.status-float-count { font-size: 22rpx; color: #58728c; }
.status-float-pct { font-size: 22rpx; color: #3E5BD9; font-weight: 700; }
.status-float-bar { height: 10rpx; background: #eef1f6; border-radius: 5rpx; overflow: hidden; }
.status-float-bar-fill { height: 100%; background: linear-gradient(90deg, #4D6BFE, #6366F1); border-radius: 5rpx; transition: width 0.6s ease; }

.done-btns { display: flex; gap: 20rpx; padding: 0 24rpx 32rpx; }
.done-btn { flex: 1; padding: 26rpx; border-radius: 24rpx; display: flex; align-items: center; justify-content: center; gap: 8rpx; font-size: 28rpx; font-weight: 600; }
.done-btn--sec { background: #fff; color: #1e344f; border: 2rpx solid #e2e8ef; }
.done-btn--pri { background: #4D6BFE; color: #fff; }
.done-btn-arrow { font-size: 28rpx; margin-left: 4rpx; }

.error-state { flex: 1; display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 80rpx 48rpx; }
.error-icon { font-size: 96rpx; margin-bottom: 24rpx; }
.error-title { font-size: 36rpx; font-weight: 700; color: #1e344f; margin-bottom: 16rpx; }
.error-msg { font-size: 26rpx; color: #8290a1; text-align: center; line-height: 1.6; margin-bottom: 48rpx; word-break: break-all; }
.error-actions { display: flex; gap: 20rpx; width: 100%; max-width: 560rpx; }
.error-btn { flex: 1; padding: 26rpx; border-radius: 24rpx; display: flex; align-items: center; justify-content: center; font-size: 28rpx; font-weight: 600; }
.error-btn--sec { background: #fff; color: #1e344f; border: 2rpx solid #e2e8ef; }
.error-btn--pri { background: #4D6BFE; color: #fff; }
.page { background: #F6F7FB; }
.canvas-area { background: #F6F7FB; }
.header { background: linear-gradient(180deg, #DFF0FF 0%, #EAF5FF 100%); padding: 0 28rpx; min-height: 108rpx; box-sizing: content-box; }
.header { position: relative; }
.header-center { position: absolute; left: 0; right: 0; top: 20rpx; pointer-events: none; }
.header-back { left: 28rpx; top: 22rpx; width: 64rpx; height: 64rpx; }
.header-back-icon { font-size: 0; width: 20rpx; height: 20rpx; border-left: 4rpx solid #1D4F7A; border-bottom: 4rpx solid #1D4F7A; transform: rotate(45deg); border-radius: 2rpx; }
.header-title-text { color: #17466F; }
.header-subtitle-text { color: #6F8DA8; }
.done-btns { gap: 20rpx; padding: 0 24rpx 32rpx; }
.done-btn { padding: 26rpx; border-radius: 24rpx; font-size: 28rpx; font-weight: 600; }
.done-btn--sec { background: #FFFFFF; color: #1A1A2E; border: 2rpx solid #D9E2EE; }
.done-btn--pri { background: #123E6D; color: #FFFFFF; box-shadow: 0 8rpx 20rpx rgba(18, 62, 109, 0.18); }
.wait-ring b { background: #123E6D; }
.wait-ring i { border-color: rgba(18, 62, 109, 0.38); }
.bottom-step-dot--done, .bottom-step-dot--active, .status-float-dot { background: #123E6D; }
.status-float-inner { border: 1rpx solid #E6E8F1; border-radius: 22rpx; box-shadow: 0 10rpx 28rpx rgba(20, 28, 48, 0.06); }
.status-float-label, .status-float-pct { color: #123E6D; }
.status-float-bar { background: #EEF4FC; }
.status-float-bar-fill { background: #123E6D; }
.done-btns { gap: 20rpx; padding: 0 24rpx 32rpx; background: #FFFFFF; }
.done-btn { height: 88rpx; padding: 0 26rpx; border-radius: 24rpx; box-sizing: border-box; font-size: 28rpx; font-weight: 600; }
.done-btn--sec { background: #FFFFFF; color: #1A1A2E; border: 2rpx solid #D9E2EE; }
.done-btn--pri { background: #294574; color: #FFFFFF; border: 0; box-shadow: 0 8rpx 20rpx rgba(18, 62, 109, 0.18); }
</style>
