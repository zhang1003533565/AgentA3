<template>
  <view class="ifb-wrap">
    <view class="ifb-row">
      <!-- 按钮：ref 用于测量位置，气泡定位锚点 -->
      <view ref="btnRef" class="ifb-btn" :class="{ 'is-loading': loading }" @tap="onClick">
        <image class="ifb-icon" src="/static/icons/diagram/import-file.svg" mode="aspectFit" />
        <text class="ifb-text">{{ loading ? loadingText : '导入文件' }}</text>
      </view>
      <!-- 感叹号提示标记：位于按钮右侧，点击仅切换气泡，不触发导入 -->
      <view class="ifb-mark" @tap.stop="toggleTip">
        <text class="ifb-mark-text">!</text>
      </view>
    </view>

    <!-- 蒙层：全屏透明，点击关闭气泡 -->
    <view v-if="showTip" class="ifb-mask" @tap="showTip = false"></view>
    <!-- 气泡 popover：fixed 定位，通过 JS 测量按钮位置精确放在按钮正上方居中 -->
    <view
      v-if="showTip"
      class="ifb-popover"
      :style="popoverStyle"
      @tap.stop
    >
      <view class="ifb-popover-arrow" :style="arrowStyle"></view>
      <text class="ifb-popover-text">只支持 doc、docx、ppt、pptx、pdf、md 格式的文件</text>
    </view>
  </view>
</template>

<script setup>
import { ref, computed, nextTick } from 'vue'

defineProps({
  // 解析中状态：true 时按钮文案切换为 loadingText 且不可重复点击
  loading: { type: Boolean, default: false },
  loadingText: { type: String, default: '解析中...' }
})

const emit = defineEmits(['click'])

const showTip = ref(false)
const btnRef = ref(null)
// 按钮的视口坐标（px），由 toggleTip 时 getBoundingClientRect 测得
const btnRect = ref({ left: 0, top: 0, width: 0, height: 0 })

// 气泡 style：fixed 定位，水平中心对齐按钮中心，底部紧贴按钮顶部上方 12rpx
const popoverStyle = computed(() => {
  const GAP = 8 // px，气泡底边距按钮顶部的间隙
  // 气泡预估宽度 240px（max-width 480rpx ≈ 240px @750 设计稿），用于水平居中
  const POPOVER_W = 240
  const center = btnRect.value.left + btnRect.value.width / 2
  let left = center - POPOVER_W / 2
  // 防止溢出左侧视口
  if (left < 8) left = 8
  const top = btnRect.value.top - GAP
  return {
    position: 'fixed',
    left: `${left}px`,
    top: `${top}px`,
    transform: 'translateY(-100%)',
    zIndex: 999
  }
})

// 箭头 style：指向按钮中心，相对气泡底部
const arrowStyle = computed(() => {
  const center = btnRect.value.left + btnRect.value.width / 2
  const popoverLeft = parseFloat(popoverStyle.value.left) || 0
  const offset = center - popoverLeft
  return {
    left: `${offset}px`
  }
})

function onClick() {
  emit('click')
}

async function toggleTip() {
  if (!showTip.value) {
    // 打开前先测量按钮位置，确保气泡定位准确
    // #ifdef H5
    const el = btnRef.value?.$el || btnRef.value
    if (el && typeof el.getBoundingClientRect === 'function') {
      btnRect.value = el.getBoundingClientRect()
    }
    // #endif
    showTip.value = true
    await nextTick()
    // 气泡渲染后重测一次，应对内容撑开导致的偏移
    const el2 = btnRef.value?.$el || btnRef.value
    if (el2 && typeof el2.getBoundingClientRect === 'function') {
      btnRect.value = el2.getBoundingClientRect()
    }
  } else {
    showTip.value = false
  }
}
</script>

<style lang="scss" scoped>
.ifb-wrap {
  position: relative;
  display: inline-flex;
  align-items: center;
}

.ifb-row {
  position: relative;
  display: inline-flex;
  align-items: center;
  gap: 12rpx;
}

.ifb-btn {
  display: inline-flex;
  align-items: center;
  gap: 10rpx;
  padding: 12rpx 24rpx;
  border-radius: 999rpx;
  background: #F5F6FA;
  position: relative;
}

.ifb-btn.is-loading {
  opacity: 0.6;
  pointer-events: none;
}

.ifb-icon {
  width: 32rpx;
  height: 32rpx;
}

.ifb-text {
  font-size: 26rpx;
  color: #4D6BFE;
  font-weight: 600;
  line-height: 1;
}

/* 感叹号标记：位于按钮右侧，灰色 */
.ifb-mark {
  width: 32rpx;
  height: 32rpx;
  border-radius: 50%;
  border: 2rpx solid #B8BCC4;
  background: #F0F1F4;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.ifb-mark-text {
  font-size: 22rpx;
  color: #9CA0A8;
  font-weight: 700;
  line-height: 1;
}

/* 蒙层：全屏透明，点击关闭 */
.ifb-mask {
  position: fixed;
  inset: 0;
  z-index: 998;
}

/* 气泡：fixed 定位（JS 动态设置 left/top），相对视口精确定位 */
.ifb-popover {
  background: #1E293B;
  border-radius: 16rpx;
  padding: 20rpx 24rpx;
  max-width: 480rpx;
  box-shadow: 0 8rpx 24rpx rgba(15, 23, 42, 0.2);
}

/* 箭头：指向按钮中心，bottom 贴气泡底边 */
.ifb-popover-arrow {
  position: absolute;
  bottom: -10rpx;
  width: 0;
  height: 0;
  border-left: 12rpx solid transparent;
  border-right: 12rpx solid transparent;
  border-top: 12rpx solid #1E293B;
  transform: translateX(-50%);
}

.ifb-popover-text {
  font-size: 24rpx;
  color: #fff;
  line-height: 1.5;
  display: block;
  white-space: nowrap;
}
</style>
