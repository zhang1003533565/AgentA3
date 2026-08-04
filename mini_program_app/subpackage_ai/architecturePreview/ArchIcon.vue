<template>
  <view class="arch-icon" :style="iconWrapStyle">
    <view class="arch-icon-svg" :style="svgStyle">
      <svg
        :width="pixelSize"
        :height="pixelSize"
        viewBox="0 0 24 24"
        fill="none"
        :stroke="color"
        stroke-width="1.8"
        stroke-linecap="round"
        stroke-linejoin="round"
        xmlns="http://www.w3.org/2000/svg"
      >
        <path :d="path" />
      </svg>
    </view>
  </view>
</template>

<script setup>
import { computed } from 'vue'
import { ARCH_ICONS } from './architectureData.js'

const props = defineProps({
  iconKey: { type: String, default: '' },
  color: { type: String, default: '#4D6BFE' },
  // 视觉尺寸（rpx）
  size: { type: Number, default: 44 },
})

// 兜底图标：通用方块
const FALLBACK = 'M3 3h18v18H3z'

const path = computed(() => {
  if (props.iconKey && ARCH_ICONS[props.iconKey]) {
    return ARCH_ICONS[props.iconKey]
  }
  return FALLBACK
})

// 容器尺寸用 rpx
const iconWrapStyle = computed(() => ({
  width: props.size + 'rpx',
  height: props.size + 'rpx',
}))

// SVG 使用 px（按 1rpx ≈ 0.5px 的换算）—— 兼容多端
const pixelSize = computed(() => Math.round(props.size / 2))

const svgStyle = computed(() => ({
  width: '100%',
  height: '100%',
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'center',
}))
</script>

<style lang="scss" scoped>
.arch-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.arch-icon-svg {
  width: 100%;
  height: 100%;
}
</style>
