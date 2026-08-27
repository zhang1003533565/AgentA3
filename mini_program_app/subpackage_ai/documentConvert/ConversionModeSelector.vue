<template>
  <view class="mode-selector">
    <view
      class="mode-card"
      :class="{
        'mode-card--active': modelValue === mode.value,
        'mode-card--disabled': mode.disabled
      }"
      v-for="mode in modes"
      :key="mode.value"
      @tap="selectMode(mode)"
    >
      <view class="mode-main">
        <text class="mode-name">{{ mode.label }}</text>
        <text v-if="mode.badge" class="mode-badge">{{ mode.badge }}</text>
      </view>
      <view class="mode-help" @tap.stop="showHelp(mode)">
        <text class="mode-help-icon">?</text>
      </view>
    </view>
  </view>
</template>

<script setup>
const props = defineProps({
  modes: { type: Array, default: () => [] },
  modelValue: { type: String, default: '' }
})

const emit = defineEmits(['update:modelValue'])

const selectMode = (mode) => {
  if (mode.disabled) return
  emit('update:modelValue', mode.value)
}

const showHelp = (mode) => {
  uni.showModal({
    title: mode.label || '转换模式',
    content: mode.description || '暂无说明',
    showCancel: false
  })
}
</script>

<style lang="scss" scoped>
.mode-selector {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

.mode-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
  background: #FFFFFF;
  border: 2rpx solid #EEEEEE;
  border-radius: 20rpx;
  padding: 24rpx;
}

.mode-card--active {
  border-color: #5C7A99;
  background: rgba(92, 122, 153, 0.05);
}

.mode-card--disabled {
  opacity: 0.6;
}

.mode-main {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 16rpx;
}

.mode-name {
  font-size: 28rpx;
  font-weight: 600;
  color: #1D1D1F;
}

.mode-badge {
  flex-shrink: 0;
  font-size: 20rpx;
  color: #8E8E93;
  background: rgba(142, 142, 147, 0.10);
  border-radius: 999rpx;
  padding: 4rpx 14rpx;
}

.mode-help {
  flex-shrink: 0;
  width: 36rpx;
  height: 36rpx;
  border-radius: 50%;
  border: 2rpx solid #C9D2DC;
  display: flex;
  align-items: center;
  justify-content: center;
}

.mode-help-icon {
  font-size: 24rpx;
  color: #5C7A99;
  font-weight: 600;
  line-height: 1;
}
</style>
