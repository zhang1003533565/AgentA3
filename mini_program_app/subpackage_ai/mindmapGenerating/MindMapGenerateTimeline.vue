<template>
  <view class="timeline-page">
    <scroll-view
      class="timeline-scroll"
      scroll-y
      :scroll-into-view="activeAnchor"
      :scroll-with-animation="true"
      :throttle="false"
      :show-scrollbar="false"
    >
      <view class="timeline-spacer"></view>

      <view
        v-for="(step, idx) in steps"
        :key="step.type"
        :id="'step-' + step.type"
        class="timeline-row"
        :class="['timeline-row--' + step.status, step.status === 'active' ? 'timeline-row--highlight' : '']"
        :style="{ animationDelay: (idx * 80) + 'ms' }"
      >
        <!-- 左侧时间线 -->
        <view class="timeline-rail">
          <view class="rail-line rail-line--top" v-if="idx > 0"></view>
          <view
            class="rail-dot"
            :class="{
              'rail-dot--done': step.status === 'done',
              'rail-dot--active': step.status === 'active',
              'rail-dot--pending': step.status === 'pending'
            }"
          >
            <text v-if="step.status === 'done'" class="rail-check">✓</text>
            <text v-else class="rail-num">{{ idx + 1 }}</text>
          </view>
          <view class="rail-line rail-line--bottom" v-if="idx < steps.length - 1"></view>
        </view>

        <!-- 右侧卡片 -->
        <view class="timeline-card">
          <!-- 头部：图标 + 标题 + 状态徽章（与描述分开，让图标与标题单行居中） -->
          <view class="card-head">
            <view class="card-icon-box" :class="'card-icon-box--' + step.status">
              <text class="card-icon">{{ stepIcon(step) }}</text>
            </view>
            <view class="card-head-main">
              <text class="card-title">{{ step.title }}</text>
            </view>
            <view v-if="step.status === 'active'" class="card-badge">当前步骤</view>
            <view v-else-if="step.status === 'done'" class="card-badge card-badge--done">已完成</view>
          </view>
          <text class="card-desc">{{ step.desc }}</text>

          <!-- 1. 关键词列表 -->
          <view v-if="step.keywords && step.keywords.length" class="card-keywords">
            <text class="card-keywords-label">提取关键词</text>
            <view class="card-keywords-list">
              <view
                v-for="(kw, ki) in step.keywords"
                :key="ki"
                class="keyword-chip"
                :style="{ animationDelay: (ki * 60) + 'ms' }"
              >
                {{ kw }}
              </view>
            </view>
          </view>

          <!-- 2. 一级节点列表 -->
          <view v-if="step.primaryNodes && step.primaryNodes.length" class="card-primary">
            <text class="card-primary-label">已发现 {{ step.primaryNodes.length }} 个一级知识点</text>
            <view class="card-primary-list">
              <view
                v-for="(node, ni) in step.primaryNodes"
                :key="ni"
                class="primary-pill"
                :style="{ animationDelay: (ni * 50) + 'ms' }"
              >
                <view class="primary-dot"></view>
                <text class="primary-text">{{ node }}</text>
              </view>
            </view>
          </view>

          <!-- 3. 迷你思维导图预览（树状布局：根左 + 分支右） -->
          <view v-if="step.mindMapPreview" class="card-preview">
            <view class="preview-canvas" :class="'preview-canvas--' + step.type">
              <!-- 根节点：左侧中部 -->
              <view class="preview-root-side">
                <view class="preview-root">{{ step.mindMapPreview.root }}</view>
              </view>
              <!-- 分支节点：右侧竖直排列 -->
              <view class="preview-branches-side">
                <view
                  v-for="(branch, bi) in displayBranches(step.mindMapPreview)"
                  :key="'b'+bi"
                  class="preview-branch"
                  :style="{
                    animationDelay: (bi * 90) + 'ms',
                  }"
                >
                  <text class="preview-branch-text">{{ branch }}</text>
                </view>
              </view>
              <!-- SVG 贝塞尔曲线连线层：viewBox 跟 canvas 实测尺寸同步，1:1 无拉伸 -->
              <svg
                class="preview-svg"
                :viewBox="`0 0 ${canvasSize.w} ${canvasSize.h}`"
                width="100%"
                height="100%"
                preserveAspectRatio="none"
              >
                <path
                  v-for="(line, li) in linesOf(step)"
                  :key="'l'+li"
                  :d="line"
                  stroke="#7C5FE0"
                  stroke-width="3"
                  fill="none"
                  stroke-linecap="round"
                  opacity="0.9"
                />
              </svg>
            </view>
          </view>

          <!-- 4. 优化消息 -->
          <view v-if="step.optimizeMessages && step.optimizeMessages.length" class="card-optimize">
            <view
              v-for="(msg, oi) in step.optimizeMessages"
              :key="oi"
              class="optimize-item"
              :style="{ animationDelay: (oi * 100) + 'ms' }"
            >
              <text class="optimize-bullet">·</text>
              <text class="optimize-text">{{ msg }}</text>
            </view>
          </view>

          <!-- 5. 布局统计 -->
          <view v-if="step.stats" class="card-stats">
            <view class="stat-item">
              <text class="stat-value">{{ step.stats.nodes }}</text>
              <text class="stat-label">个节点</text>
            </view>
            <view class="stat-divider"></view>
            <view class="stat-item">
              <text class="stat-value">{{ step.stats.branches }}</text>
              <text class="stat-label">个分支</text>
            </view>
            <view class="stat-divider"></view>
            <view class="stat-item">
              <text class="stat-value">{{ step.stats.depth }}</text>
              <text class="stat-label">层深度</text>
            </view>
          </view>

          <!-- 加载中动画 -->
          <view v-if="step.status === 'active' && !step.keywords && !step.primaryNodes && !step.mindMapPreview && !step.optimizeMessages && !step.stats" class="card-loading">
            <view class="loading-dots">
              <view class="loading-dot"></view>
              <view class="loading-dot"></view>
              <view class="loading-dot"></view>
            </view>
            <text class="loading-text">处理中...</text>
          </view>
        </view>
      </view>

      <view class="timeline-spacer"></view>
    </scroll-view>

    <!-- 底部 AI 状态卡片 -->
    <view v-if="showStatus" class="status-card">
      <view class="status-card-inner">
        <view class="status-avatar">
          <view class="status-avatar-pulse"></view>
          <view class="status-avatar-core">
            <text class="status-avatar-emoji">🤖</text>
          </view>
        </view>
        <view class="status-content">
          <view class="status-row1">
            <text class="status-title">{{ statusTitle }}</text>
            <view class="status-typing">
              <view class="typing-dot"></view>
              <view class="typing-dot"></view>
              <view class="typing-dot"></view>
            </view>
          </view>
          <text class="status-msg">{{ statusMsg }}</text>
          <view class="status-row3">
            <text class="status-count">已生成 {{ generatedCount }} 个知识节点</text>
            <text class="status-estimate">{{ estimateText }}</text>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed, ref, onMounted, nextTick, watch } from 'vue'

const props = defineProps({
  steps: { type: Array, required: true },
  topicText: { type: String, default: '' },
  showStatus: { type: Boolean, default: true },
  statusTitle: { type: String, default: 'AI 思考中...' },
  statusMsg: { type: String, default: '' },
  generatedCount: { type: Number, default: 0 },
  estimateText: { type: String, default: '预计 3 秒完成' },
})

// canvas 实际像素尺寸（仅测一次）
const canvasSize = ref({ w: 600, h: 360 })

// 用唯一 class 选当前 step 自己的 canvas
function canvasSel() {
  const last = [...props.steps].reverse().find(s => s.mindMapPreview && (s.mindMapPreview.branches || []).length)
  return last ? `.preview-canvas--${last.type}` : ''
}

// 只测 canvas 尺寸（唯一一次 querySelector 调用）
function measureCanvasSize() {
  const sel = canvasSel()
  if (!sel) return
  // #ifdef H5
  if (typeof document !== 'undefined') {
    const el = document.querySelector(sel)
    if (el) {
      const r = el.getBoundingClientRect()
      if (r.width && r.height) canvasSize.value = { w: r.width, h: r.height }
    }
    return
  }
  // #endif
  uni.createSelectorQuery().select(sel).boundingClientRect(rect => {
    if (rect && rect.width && rect.height) canvasSize.value = { w: rect.width, h: rect.height }
  }).exec()
}

onMounted(() => {
  nextTick(() => measureCanvasSize())
})

// 切换到新 preview 时重测
watch(() => {
  for (const s of props.steps) {
    if (s.mindMapPreview && (s.mindMapPreview.branches || []).length) return s.type
  }
  return ''
}, () => {
  nextTick(() => measureCanvasSize())
}, { flush: 'post' })

// 锚点 id：让 scroll-view 自动滚到当前激活卡片
// 全部完成时定位到最后一项
const activeAnchor = computed(() => {
  const active = props.steps.find(s => s.status === 'active')
  if (active) return `step-${active.type}`
  // 找最后一个 done 的
  const dones = props.steps.filter(s => s.status === 'done')
  if (dones.length === props.steps.length && props.steps.length) {
    return `step-${props.steps[props.steps.length - 1].type}`
  }
  return ''
})

const branchColors = [
  { main: '#4D6BFE', rgb: '77,107,254' },
  { main: '#8B5CF6', rgb: '139,92,246' },
  { main: '#EC4899', rgb: '236,72,153' },
  { main: '#6366F1', rgb: '99,102,241' },
  { main: '#7C5FE0', rgb: '124,95,224' },
]
function branchColor(i) {
  return branchColors[i % branchColors.length].main
}
function branchBg(i, alpha = 0.08) {
  const c = branchColors[i % branchColors.length]
  return `rgba(${c.rgb},${alpha})`
}
function branchBorder(i, alpha = 0.35) {
  const c = branchColors[i % branchColors.length]
  return `rgba(${c.rgb},${alpha})`
}

function stepIcon(step) {
  // 使用 unicode 几何符号，避免 emoji 风格
  const map = {
    ANALYZE: '◐',
    EXTRACT: '☰',
    BUILD: '◇',
    OPTIMIZE: '◎',
    LAYOUT: '▦',
  }
  return map[step.type] || '○'
}

// 树状预览：取前 N 个分支作为右侧显示
function displayBranches(preview) {
  return (preview?.branches || []).slice(0, 6)
}

// 根据当前 step 的 preview 计算贝塞尔曲线（纯 CSS 公式，不用 querySelector 测节点）
// canvas CSS 宽 = 606rpx（= 750 - timeline-scroll 24*2 - timeline-card 28*2 - card-preview 20*2）
// 根节点：left 20rpx, 垂直居中, 宽 ≈ 200rpx (min 180 + max 220 居中)
// 分支容器：right 20rpx, top 16rpx, bottom 16rpx, width 220rpx
// 分支：flex space-around 布局, 每行高 ≈ 48rpx (padding 8*2 + font 22*1.2 + border 2*2)
function buildLinesFor(step) {
  if (!step || !step.mindMapPreview) return []
  const list = displayBranches(step.mindMapPreview)
  if (!list.length) return []
  const W = canvasSize.value.w
  const H = canvasSize.value.h
  if (!W || !H) return []
  const rpx = W / 606
  // 根右边缘 + 垂直中心
  const rootX = (20 + 200) * rpx
  const rootY = H / 2
  // 分支左边缘
  const branchLeft = W - (20 + 220) * rpx
  // 贝塞尔控制点 x
  const cpX = (rootX + branchLeft) / 2
  // 分支容器内 space-around 公式
  const n = list.length
  const branchH = 48 * rpx
  const branchTop = 16 * rpx
  const branchArea = H - 32 * rpx
  const u = (branchArea - n * branchH) / (n + 1)
  const lines = []
  for (let i = 0; i < n; i++) {
    const y = branchTop + u + branchH / 2 + i * (branchH + u)
    const path = `M ${rootX} ${rootY} C ${cpX} ${rootY}, ${cpX} ${y}, ${branchLeft} ${y}`
    lines.push(path)
  }
  return lines
}

// 模板用的包装
const linesMap = computed(() => {
  const _w = canvasSize.value.w
  const _h = canvasSize.value.h
  const map = {}
  for (const s of props.steps) {
    if (s.mindMapPreview) {
      map[s.type] = buildLinesFor(s)
    }
  }
  return map
})

function linesOf(step) {
  return linesMap.value[step.type] || []
}
</script>

<style lang="scss" scoped>
.timeline-page {
  position: relative;
  display: flex;
  flex-direction: column;
  height: 100%;
  background: #F5F4FA;
}

.timeline-scroll {
  flex: 1;
  height: 0;
  min-height: 0;
  padding: 0 24rpx;
  box-sizing: border-box;
}

.timeline-spacer {
  height: 24rpx;
}

/* ===== 时间线条目 ===== */
.timeline-row {
  display: flex;
  align-items: flex-start;  /* 圆点对齐到卡片顶部，而不是垂直居中整行 */
  margin-bottom: 24rpx;
  position: relative;
  animation: rowSlideIn 0.5s cubic-bezier(0.22, 1, 0.36, 1) both;
}

@keyframes rowSlideIn {
  from {
    opacity: 0;
    transform: translateY(40rpx);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}

.timeline-row--pending {
  opacity: 0.55;
  transition: opacity 0.4s ease;
}

.timeline-row--done {
  opacity: 0.78;
  transition: opacity 0.4s ease;
}

.timeline-row--active {
  opacity: 1;
}

/* 当前步骤呼吸效果 */
.timeline-row--highlight .timeline-card {
  border: 2rpx solid #7C5FE0;
  box-shadow: 0 8rpx 32rpx rgba(124, 95, 224, 0.18);
  animation: cardBreathe 2.4s ease-in-out infinite;
}

@keyframes cardBreathe {
  0%, 100% {
    box-shadow: 0 8rpx 32rpx rgba(124, 95, 224, 0.18);
  }
  50% {
    box-shadow: 0 12rpx 48rpx rgba(124, 95, 224, 0.32);
  }
}

/* ===== 左侧时间线 ===== */
.timeline-rail {
  position: relative;
  width: 56rpx;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.rail-line {
  width: 2rpx;
  flex: 1;
  background: #D8DAE4;
}

.rail-dot {
  width: 48rpx;
  height: 48rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22rpx;
  font-weight: 700;
  flex-shrink: 0;
  z-index: 1;
  margin-top: 4rpx;  /* 微调：与 card-head 中心对齐 */
  transition: all 0.3s ease;
}

.rail-dot--pending {
  background: #FFFFFF;
  color: #B0B3C5;
  border: 2rpx solid #D8DAE4;
}

.rail-dot--active {
  background: #7C5FE0;
  color: #FFFFFF;
  box-shadow: 0 0 0 6rpx rgba(124, 95, 224, 0.18);
  animation: dotPulse 1.6s ease-in-out infinite;
}

.rail-dot--done {
  background: #34C759;
  color: #FFFFFF;
}

@keyframes dotPulse {
  0%, 100% {
    box-shadow: 0 0 0 6rpx rgba(124, 95, 224, 0.18);
  }
  50% {
    box-shadow: 0 0 0 14rpx rgba(124, 95, 224, 0.04);
  }
}

.rail-num { line-height: 1; }
.rail-check { font-size: 26rpx; line-height: 1; }

/* ===== 右侧卡片 ===== */
.timeline-card {
  flex: 1;
  background: #FFFFFF;
  border-radius: 24rpx;
  padding: 24rpx 28rpx;
  margin-left: 8rpx;
  box-shadow: 0 2rpx 12rpx rgba(28, 46, 72, 0.04);
  border: 2rpx solid transparent;
  transition: all 0.3s ease;
}

.card-head {
  display: flex;
  align-items: center;
  margin-bottom: 16rpx;
}

.card-icon-box {
  width: 56rpx;
  height: 56rpx;
  border-radius: 18rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  background: #F0EEF8;
  margin-right: 16rpx;
}

.card-icon-box--active {
  background: linear-gradient(135deg, #7C5FE0, #6366F1);
  box-shadow: 0 4rpx 12rpx rgba(124, 95, 224, 0.3);
}

/* 已完成：紫色实色背景 + 白色图标（与参考图统一） */
.card-icon-box--done {
  background: #7C5FE0;
}

/* 待办：浅紫色背景 */
.card-icon-box--pending {
  background: #F0EEF8;
}

.card-icon {
  font-size: 32rpx;
  color: #7C5FE0;
  font-weight: 700;
}

.card-icon-box--active .card-icon { color: #FFFFFF; }
.card-icon-box--done .card-icon { color: #FFFFFF; }
.card-icon-box--pending .card-icon { color: #C4B5E8; }

.card-head-main {
  flex: 1;
  display: flex;
  align-items: center;
  min-width: 0;
}

.card-title {
  font-size: 30rpx;
  font-weight: 700;
  color: #1A1A2E;
  line-height: 1.3;
}

.card-desc {
  display: block;
  font-size: 24rpx;
  color: #8B8FA3;
  margin-top: 4rpx;
  margin-left: 72rpx;  /* 与标题对齐（icon 框 56 + margin 16） */
  line-height: 1.4;
  word-break: break-all;
}

.card-badge {
  flex-shrink: 0;
  padding: 4rpx 16rpx;
  border-radius: 16rpx;
  background: linear-gradient(135deg, #7C5FE0, #6366F1);
  color: #FFFFFF;
  font-size: 20rpx;
  font-weight: 600;
  margin-left: 12rpx;
}

.card-badge--done {
  background: #F0EEF8;
  color: #7C5FE0;
}

/* ===== 关键词 ===== */
.card-keywords {
  margin-top: 16rpx;
}

.card-keywords-label {
  display: block;
  font-size: 22rpx;
  color: #8B8FA3;
  margin-bottom: 12rpx;
}

.card-keywords-list {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
}

.keyword-chip {
  padding: 8rpx 20rpx;
  background: #F0EEF8;
  color: #7C5FE0;
  border-radius: 20rpx;
  font-size: 24rpx;
  font-weight: 600;
  animation: chipIn 0.4s cubic-bezier(0.22, 1, 0.36, 1) both;
}

@keyframes chipIn {
  from {
    opacity: 0;
    transform: scale(0.7) translateY(8rpx);
  }
  to {
    opacity: 1;
    transform: scale(1) translateY(0);
  }
}

/* ===== 一级节点 ===== */
.card-primary {
  margin-top: 16rpx;
}

.card-primary-label {
  display: block;
  font-size: 22rpx;
  color: #8B8FA3;
  margin-bottom: 12rpx;
}

.card-primary-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10rpx;
}

.primary-pill {
  display: flex;
  align-items: center;
  padding: 6rpx 16rpx;
  background: #F7F8FB;
  border-radius: 18rpx;
  font-size: 24rpx;
  color: #334155;
  font-weight: 500;
  animation: pillIn 0.4s cubic-bezier(0.22, 1, 0.36, 1) both;
}

@keyframes pillIn {
  from {
    opacity: 0;
    transform: scale(0.8);
  }
  to {
    opacity: 1;
    transform: scale(1);
  }
}

.primary-dot {
  width: 12rpx;
  height: 12rpx;
  border-radius: 50%;
  margin-right: 8rpx;
  flex-shrink: 0;
  background: #7C5FE0;
}

.primary-text {
  line-height: 1.2;
}

/* ===== 迷你思维导图预览（横向树状：根左 + 分支右） ===== */
.card-preview {
  margin-top: 16rpx;
  padding: 20rpx;
  background: #FAFBFC;
  border-radius: 18rpx;
  overflow: hidden;
  position: relative;
}

.preview-canvas {
  position: relative;
  width: 100%;
  display: flex;
  align-items: stretch;
  min-height: 360rpx;
}

/* SVG 曲线层：覆盖在节点上，pointer-events 穿透 */
.preview-svg {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  z-index: 1;
  pointer-events: none;
  overflow: visible;
}

/* 左侧：根节点贴左，垂直居中 */
.preview-root-side {
  position: absolute;
  left: 20rpx;
  top: 50%;
  transform: translateY(-50%);
  z-index: 2;
  display: flex;
  justify-content: flex-start;
}

.preview-root {
  display: inline-block;
  padding: 12rpx 18rpx;
  border: 2rpx solid #7C5FE0;
  border-radius: 16rpx;
  background: #FFFFFF;
  color: #7C5FE0;
  font-size: 24rpx;
  font-weight: 700;
  min-width: 180rpx;
  max-width: 220rpx;
  white-space: nowrap;
  text-align: center;
  overflow: hidden;
  text-overflow: ellipsis;
  animation: rootIn 0.4s cubic-bezier(0.22, 1, 0.36, 1) both;
  box-shadow: 0 2rpx 8rpx rgba(124, 95, 224, 0.08);
}

@keyframes rootIn {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}

/* 右侧：分支节点贴右，竖直 space-around 排列，所有节点左对齐 */
.preview-branches-side {
  position: absolute;
  right: 20rpx;
  top: 16rpx;
  bottom: 16rpx;
  width: 220rpx;
  z-index: 2;
  display: flex;
  flex-direction: column;
  justify-content: space-around;
  align-items: flex-start;
}

/* 每行分支节点：左对齐布局 + 自带水平连线（从节点左边缘向左延伸） */
.preview-branch-row {
  position: relative;
  display: flex;
  align-items: center;
  height: 50rpx;
  animation: branchIn 0.4s cubic-bezier(0.22, 1, 0.36, 1) both;
}

/* 水平连线：从分支节点左边缘向左延伸到根节点右边缘附近 */
.preview-branch-line {
  position: absolute;
  right: 100%;
  top: 50%;
  transform: translateY(-50%);
  /* 横向长度：根节点右边缘 220rpx 到分支节点左边缘 360rpx ≈ 140rpx */
  width: 140rpx;
  height: 2rpx;
  background: linear-gradient(270deg, rgba(124, 95, 224, 0) 0%, #7C5FE0 100%);
  border-radius: 2rpx;
  opacity: 0;
  animation: lineIn 0.5s ease-out 0.1s both;
}

@keyframes lineIn {
  from { opacity: 0; transform: translate(20rpx, -50%); }
  to { opacity: 0.65; transform: translate(0, -50%); }
}

.preview-branch {
  display: inline-block;
  padding: 8rpx 16rpx;
  border: 2rpx solid #C4B5E8;
  border-radius: 14rpx;
  background: #FFFFFF;
  font-size: 22rpx;
  color: #1A1A2E;
  font-weight: 600;
  text-align: center;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 100%;
  box-shadow: 0 2rpx 6rpx rgba(0, 0, 0, 0.04);
}

@keyframes branchIn {
  from {
    opacity: 0;
  }
  to {
    opacity: 1;
  }
}

.preview-branch-text {
  line-height: 1.2;
}

/* ===== 优化消息 ===== */
.card-optimize {
  margin-top: 16rpx;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}

.optimize-item {
  display: flex;
  align-items: flex-start;
  font-size: 24rpx;
  color: #5A5D7E;
  line-height: 1.5;
  animation: msgIn 0.4s ease both;
}

@keyframes msgIn {
  from {
    opacity: 0;
    transform: translateX(-12rpx);
  }
  to {
    opacity: 1;
    transform: translateX(0);
  }
}

.optimize-bullet {
  color: #7C5FE0;
  font-weight: 700;
  margin-right: 8rpx;
  line-height: 1.5;
}

.optimize-text {
  flex: 1;
}

/* ===== 布局统计 ===== */
.card-stats {
  margin-top: 16rpx;
  display: flex;
  align-items: center;
  background: #F7F8FB;
  border-radius: 18rpx;
  padding: 18rpx 12rpx;
}

.stat-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.stat-value {
  font-size: 36rpx;
  font-weight: 800;
  color: #1A1A2E;
  line-height: 1.2;
}

.stat-label {
  font-size: 20rpx;
  color: #8B8FA3;
  margin-top: 4rpx;
}

.stat-divider {
  width: 1rpx;
  height: 40rpx;
  background: #E2E8F0;
  flex-shrink: 0;
}

/* ===== 加载中 ===== */
.card-loading {
  display: flex;
  align-items: center;
  margin-top: 16rpx;
}

.loading-dots {
  display: flex;
  gap: 8rpx;
  margin-right: 12rpx;
}

.loading-dot {
  width: 12rpx;
  height: 12rpx;
  border-radius: 50%;
  background: #7C5FE0;
  animation: dotBounce 1.2s ease-in-out infinite;
}

.loading-dot:nth-child(2) { animation-delay: 0.15s; }
.loading-dot:nth-child(3) { animation-delay: 0.3s; }

@keyframes dotBounce {
  0%, 80%, 100% {
    transform: scale(0.5);
    opacity: 0.4;
  }
  40% {
    transform: scale(1);
    opacity: 1;
  }
}

.loading-text {
  font-size: 24rpx;
  color: #8B8FA3;
}

/* ===== 底部 AI 状态卡片 ===== */
.status-card {
  flex-shrink: 0;
  padding: 16rpx 24rpx calc(20rpx + env(safe-area-inset-bottom));
  background: transparent;
}

.status-card-inner {
  display: flex;
  align-items: center;
  background: #FFFFFF;
  border-radius: 24rpx;
  padding: 20rpx 24rpx;
  box-shadow: 0 4rpx 24rpx rgba(124, 95, 224, 0.12);
}

.status-avatar {
  position: relative;
  width: 72rpx;
  height: 72rpx;
  flex-shrink: 0;
  margin-right: 20rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.status-avatar-pulse {
  position: absolute;
  inset: 0;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(124, 95, 224, 0.35) 0%, transparent 70%);
  animation: avatarPulse 2s ease-in-out infinite;
}

@keyframes avatarPulse {
  0%, 100% {
    transform: scale(0.8);
    opacity: 0.6;
  }
  50% {
    transform: scale(1.1);
    opacity: 1;
  }
}

.status-avatar-core {
  position: relative;
  width: 60rpx;
  height: 60rpx;
  border-radius: 50%;
  background: linear-gradient(135deg, #7C5FE0, #6366F1);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1;
}

.status-avatar-emoji {
  font-size: 32rpx;
}

.status-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-width: 0;
}

.status-row1 {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 4rpx;
}

.status-title {
  font-size: 28rpx;
  font-weight: 700;
  color: #1A1A2E;
}

.status-typing {
  display: flex;
  gap: 5rpx;
}

.typing-dot {
  width: 8rpx;
  height: 8rpx;
  border-radius: 50%;
  background: #7C5FE0;
  animation: dotBounce 1.4s ease-in-out infinite;
}

.typing-dot:nth-child(2) { animation-delay: 0.2s; }
.typing-dot:nth-child(3) { animation-delay: 0.4s; }

.status-msg {
  font-size: 24rpx;
  color: #8B8FA3;
  margin-bottom: 8rpx;
  line-height: 1.4;
}

.status-row3 {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.status-count {
  font-size: 22rpx;
  color: #5A5D7E;
  font-weight: 500;
}

.status-estimate {
  font-size: 22rpx;
  color: #7C5FE0;
  font-weight: 600;
}
</style>
