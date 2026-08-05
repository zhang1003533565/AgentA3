<template>
  <!-- AI 优化思考窗：单行滚动日志，内容按图类别区分 -->
  <view v-if="visible" class="think-mask">
    <view class="think-card" :class="{ finished: finished }">
      <view class="think-head"><view class="think-dot"></view><text class="think-title">AI 正在优化…</text></view>
      <scroll-view class="think-win" scroll-y :scroll-top="scrollTop" :show-scrollbar="false">
        <view v-for="(line, i) in shownLines" :key="i" class="think-line" :class="{ cur: i === shownLines.length - 1 && !finished }">
          <text class="think-line-mark">›</text><text>{{ line }}</text>
        </view>
      </scroll-view>
      <view class="think-foot">
        <view class="think-progress"><view class="think-progress-fill" :style="{ width: progress + '%' }"></view></view>
        <text class="think-pct">{{ progress }}%</text>
      </view>
      <view class="think-done">
        <view class="done-badge"><text class="done-badge-icon">✓</text></view>
        <text class="done-text">优化完成</text>
        <text class="done-sub">{{ doneSub }}</text>
        <view class="done-btn" @tap="$emit('view')">查看优化结果</view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, watch, computed } from 'vue'

const props = defineProps({
  visible: { type: Boolean, default: false },
  type: { type: String, default: 'mindmap' }, // mindmap | flowchart | architecture
  doneSub: { type: String, default: '结构已更新' }
})
const emit = defineEmits(['view', 'finished'])

// 不同图类别的思考过程文案
const THINK_LINES = {
  mindmap: [
    '解析当前思维导图结构…',
    '识别一级分支与节点规模…',
    '评估层级均衡度与关联度…',
    '读取优化指令…',
    '重组分支层级关系…',
    '补充遗漏知识点…',
    '合并相似节点，简化结构…',
    '校验连线与层级一致性…',
    '生成优化结果…'
  ],
  flowchart: [
    '解析当前流程图结构…',
    '识别节点与连线规模…',
    '检查判断分支完整性…',
    '读取优化指令…',
    '梳理主干流程顺序…',
    '补全异常与回退路径…',
    '规范节点类型 action/decision…',
    '校验 source/target 引用…',
    '生成优化结果…'
  ],
  architecture: [
    '解析当前架构分层…',
    '识别六层与组件规模…',
    '评估分层解耦与依赖…',
    '读取优化指令…',
    '调整服务层拆分…',
    '补充基础设施与监控…',
    '平衡第三方服务接入…',
    '校验层间调用关系…',
    '生成优化结果…'
  ]
}
const lines = computed(() => THINK_LINES[props.type] || THINK_LINES.mindmap)

const shownLines = ref([])
const finished = ref(false)
const scrollTop = ref(0)
const progress = ref(0)
let timer = null

function reset() {
  clearInterval(timer)
  shownLines.value = []
  finished.value = false
  progress.value = 0
  scrollTop.value = 0
}

watch(() => props.visible, val => {
  if (!val) { reset(); return }
  reset()
  let i = 0
  timer = setInterval(() => {
    if (i >= lines.value.length) {
      clearInterval(timer)
      setTimeout(() => { finished.value = true; emit('finished') }, 300)
      return
    }
    shownLines.value.push(lines.value[i])
    scrollTop.value = (i + 1) * 60
    progress.value = Math.round(((i + 1) / lines.value.length) * 100)
    i++
  }, 400)
})
</script>

<style lang="scss" scoped>
.think-mask { position: fixed; inset: 0; background: rgba(15, 23, 42, 0.5); display: flex; align-items: center; justify-content: center; z-index: 200; }
.think-card { width: 82%; background: #0F1B33; border-radius: 36rpx; padding: 32rpx; box-shadow: 0 40rpx 100rpx rgba(0, 0, 0, 0.4); overflow: hidden; }
.think-head { display: flex; align-items: center; gap: 16rpx; margin-bottom: 24rpx; }
.think-dot { width: 16rpx; height: 16rpx; border-radius: 50%; background: #5B6BFE; animation: thinkPulse 1s ease-in-out infinite; }
@keyframes thinkPulse { 0%, 100% { opacity: 0.4; transform: scale(0.8); } 50% { opacity: 1; transform: scale(1.1); } }
.think-title { font-size: 26rpx; font-weight: 700; color: #E2E8F0; }
.think-win { height: 264rpx; overflow-y: auto; background: rgba(255, 255, 255, 0.04); border: 1rpx solid rgba(255, 255, 255, 0.08); border-radius: 20rpx; padding: 20rpx; }
.think-line { display: flex; gap: 14rpx; font-size: 23rpx; line-height: 1.9; color: #9FB3D9; font-family: ui-monospace, Consolas, monospace; white-space: nowrap; }
.think-line-mark { color: #5B6BFE; flex-shrink: 0; }
.think-line.cur { color: #E2E8F0; }
.think-line.cur::after { content: '▍'; color: #5B6BFE; animation: thinkBlink 0.8s steps(1) infinite; }
@keyframes thinkBlink { 50% { opacity: 0; } }
.think-foot { margin-top: 24rpx; display: flex; align-items: center; }
.think-progress { flex: 1; height: 8rpx; border-radius: 4rpx; background: rgba(255, 255, 255, 0.1); overflow: hidden; margin-right: 24rpx; }
.think-progress-fill { height: 100%; background: linear-gradient(90deg, #7C5FE0, #5B6BFE); transition: width 0.3s; }
.think-pct { font-size: 22rpx; color: #9FB3D9; font-weight: 700; }
.think-done { display: none; flex-direction: column; align-items: center; gap: 20rpx; padding: 16rpx 0 4rpx; }
.think-card.finished .think-win, .think-card.finished .think-foot { display: none; }
.think-card.finished .think-done { display: flex; }
.done-badge { width: 104rpx; height: 104rpx; border-radius: 50%; background: #10B981; display: flex; align-items: center; justify-content: center; }
.done-badge-icon { color: #fff; font-size: 48rpx; font-weight: 700; }
.done-text { font-size: 28rpx; font-weight: 700; color: #E2E8F0; }
.done-sub { font-size: 22rpx; color: #9FB3D9; }
.done-btn { margin-top: 8rpx; width: 100%; height: 84rpx; border-radius: 42rpx; background: linear-gradient(135deg, #7C5FE0, #5B6BFE); color: #fff; font-size: 28rpx; font-weight: 700; display: flex; align-items: center; justify-content: center; }
</style>
