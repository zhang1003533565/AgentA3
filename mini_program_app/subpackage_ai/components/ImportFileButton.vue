<template>
  <view class="ifb-wrap">
    <view class="ifb-row">
      <view class="ifb-btn" :class="{ 'is-loading': loading }" @tap="onClick">
        <image class="ifb-icon" src="/static/icons/diagram/import-file.svg" mode="aspectFit" />
        <text class="ifb-text">{{ loading ? loadingText : '导入文件' }}</text>
      </view>
      <!-- 感叹号提示标记：位于按钮右侧，点击仅切换气泡，不触发导入 -->
      <view class="ifb-mark" @tap.stop="toggleTip">
        <text class="ifb-mark-text">!</text>
      </view>

      <!-- 蒙层：全屏透明，仅负责点击关闭气泡；先渲染，z-index 低 -->
      <view v-if="showTip" class="ifb-mask" @tap="showTip = false"></view>
      <!-- 气泡 popover：定位到按钮正上方，后渲染，z-index 高确保可交互 -->
      <view v-if="showTip" class="ifb-popover" @tap.stop>
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

/* 按钮与感叹号的横向容器：气泡以它为定位锚点，居中对齐按钮区域 */
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

/* 气泡蒙层：全屏透明，点击关闭；z-index 低于气泡，确保气泡可交互 */
.ifb-mask {
  position: fixed;
  inset: 0;
  z-index: 998;
}

/* 气泡：相对 ifb-row 定位，bottom:100% 紧贴按钮上方 */
.ifb-popover {
  position: absolute;
  bottom: 100%;
  left: 0;
  margin-bottom: 12rpx;
  background: #1E293B;
  border-radius: 16rpx;
  padding: 20rpx 24rpx;
  max-width: 480rpx;
  box-shadow: 0 8rpx 24rpx rgba(15, 23, 42, 0.2);
  z-index: 999;
}

/* 箭头：指向按钮中心。按钮宽度约 = icon(32+10gap) + 文案 + padding(48)，中心约在 120rpx 处 */
.ifb-popover-arrow {
  position: absolute;
  bottom: -10rpx;
  left: 120rpx;
  width: 0;
  height: 0;
  border-left: 12rpx solid transparent;
  border-right: 12rpx solid transparent;
  border-top: 12rpx solid #1E293B;
}

.ifb-popover-text {
  font-size: 24rpx;
  color: #fff;
  line-height: 1.5;
}
</style>
