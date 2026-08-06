<template>
  <view class="ifb-wrap">
    <view class="ifb-btn" :class="{ 'is-loading': loading }" @tap="onClick">
      <image class="ifb-icon" src="/static/icons/diagram/import-file.svg" mode="aspectFit" />
      <text class="ifb-text">{{ loading ? loadingText : '导入文件' }}</text>
      <!-- 感叹号提示标记：点击仅切换气泡，不触发导入 -->
      <view class="ifb-mark" @tap.stop="toggleTip">
        <text class="ifb-mark-text">!</text>
      </view>
    </view>

    <!-- 气泡 popover：点击蒙层关闭 -->
    <view v-if="showTip" class="ifb-mask" @tap="showTip = false">
      <view class="ifb-popover" @tap.stop>
        <view class="ifb-popover-arrow"></view>
        <text class="ifb-popover-text">只支持 doc、docx、ppt、pptx、pdf、md 格式的文件</text>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'

defineProps({
  // 解析中状态：true 时按钮文案切换为 loadingText 且不可重复点击
  loading: { type: Boolean, default: false },
  loadingText: { type: String, default: '解析中...' }
})

const emit = defineEmits(['click'])

const showTip = ref(false)

function onClick() {
  // 事件名用 click，与原生按钮语义一致；父组件接收后调各自的文件选择逻辑
  emit('click')
}

function toggleTip() {
  showTip.value = !showTip.value
}
</script>

<style lang="scss" scoped>
.ifb-wrap {
  position: relative;
  display: inline-flex;
  align-items: center;
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

/* 右上角感叹号圆点 */
.ifb-mark {
  position: absolute;
  top: -8rpx;
  right: -8rpx;
  width: 28rpx;
  height: 28rpx;
  border-radius: 50%;
  background: #FF6B6B;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 2rpx 6rpx rgba(255, 107, 107, 0.4);
}

.ifb-mark-text {
  font-size: 20rpx;
  color: #fff;
  font-weight: 700;
  line-height: 1;
}

/* 气泡蒙层：全屏透明，点击关闭 */
.ifb-mask {
  position: fixed;
  inset: 0;
  z-index: 999;
}

/* 气泡 */
.ifb-popover {
  position: absolute;
  top: 48rpx;
  left: 0;
  background: #1E293B;
  border-radius: 16rpx;
  padding: 20rpx 24rpx;
  max-width: 480rpx;
  box-shadow: 0 8rpx 24rpx rgba(15, 23, 42, 0.2);
}

.ifb-popover-arrow {
  position: absolute;
  top: -10rpx;
  left: 32rpx;
  width: 0;
  height: 0;
  border-left: 12rpx solid transparent;
  border-right: 12rpx solid transparent;
  border-bottom: 12rpx solid #1E293B;
}

.ifb-popover-text {
  font-size: 24rpx;
  color: #fff;
  line-height: 1.5;
}
</style>
