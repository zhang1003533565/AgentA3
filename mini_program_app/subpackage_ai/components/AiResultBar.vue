<template>
  <!-- 结果页统一底部操作栏：导出 | 优化(主) | 分享 -->
  <view class="ai-bar">
    <view class="ai-bar-pill">
      <view class="ai-bar-action" @tap="$emit('export')">
        <svg class="ai-bar-icon" viewBox="0 0 24 24"><path d="M12 3v12"/><path d="M7 10l5 5 5-5"/><path d="M5 21h14"/></svg>
        <text class="ai-bar-label">导出</text>
      </view>
      <view class="ai-bar-primary" @tap="$emit('optimize')">
        <svg viewBox="0 0 24 24"><path d="M12 3l1.8 5.2L19 10l-5.2 1.8L12 17l-1.8-5.2L5 10l5.2-1.8z"/></svg>
        <text class="ai-bar-label ai-bar-label--pri">优化</text>
      </view>
      <view class="ai-bar-action" @tap="$emit('share')">
        <svg class="ai-bar-icon" viewBox="0 0 24 24"><circle cx="6" cy="12" r="3"/><circle cx="17" cy="6" r="3"/><circle cx="17" cy="18" r="3"/><path d="M8.6 10.6l6-3.2M8.6 13.4l6 3.2"/></svg>
        <text class="ai-bar-label">分享</text>
      </view>
    </view>
  </view>
</template>

<script setup>
defineEmits(['export', 'optimize', 'share'])
</script>

<style lang="scss" scoped>
.ai-bar { position: fixed; left: 0; right: 0; bottom: 0; padding: 16rpx 40rpx calc(18rpx + env(safe-area-inset-bottom)); display: flex; justify-content: center; pointer-events: none; z-index: 90; }
.ai-bar-pill { pointer-events: auto; position: relative; display: flex; align-items: center; justify-content: space-between; width: 100%; max-width: 680rpx; height: 128rpx; background: #fff; border-radius: 64rpx; box-shadow: 0 16rpx 60rpx rgba(30, 52, 79, 0.12); padding: 0 28rpx; box-sizing: border-box; }
.ai-bar-action { flex: 1; display: flex; flex-direction: column; align-items: center; gap: 4rpx; padding: 16rpx 0; }
.ai-bar-action:active { opacity: 0.6; }
.ai-bar-icon { width: 40rpx; height: 40rpx; stroke: #58728c; fill: none; stroke-width: 2; stroke-linecap: round; stroke-linejoin: round; }
.ai-bar-label { font-size: 22rpx; color: #58728c; font-weight: 600; }
.ai-bar-primary { position: relative; flex: 0 0 auto; width: 112rpx; height: 112rpx; margin-top: -60rpx; border-radius: 50%; background: linear-gradient(135deg, #7C5FE0, #5B6BFE); display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 2rpx; box-shadow: 0 16rpx 40rpx rgba(91, 107, 254, 0.35), 0 0 0 10rpx #F5F6FA; }
.ai-bar-primary:active { transform: scale(0.95); }
.ai-bar-primary svg { width: 36rpx; height: 36rpx; stroke: #fff; fill: none; stroke-width: 2; stroke-linecap: round; stroke-linejoin: round; }
.ai-bar-label--pri { color: #fff; font-size: 20rpx; }
</style>
