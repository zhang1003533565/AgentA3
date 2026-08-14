<template>
  <view class="page">
    <view class="nav-bar">
      <view class="nav-action nav-action--left" @tap="goBack">
        <text class="nav-back">‹</text>
      </view>
      <text class="nav-title">AI 思维导图</text>
      <view class="nav-action nav-action--right" @tap="openHistory">
        <image class="nav-icon" src="/static/icons/diagram/history.svg" mode="aspectFit" />
      </view>
    </view>

    <view class="preview-area">
      <image
        v-if="generatedImageUrl"
        class="generated-image"
        :src="generatedImageUrl"
        mode="aspectFit"
      />
    </view>

    <view class="bottom-bar">
      <view class="bottom-action" @tap="saveResult">
        <image class="bottom-icon" src="/static/icons/line/clipboard.svg" mode="aspectFit" />
        <text>保存</text>
      </view>
      <view class="bottom-action" @tap="exportImage">
        <image class="bottom-icon" src="/static/icons/line/chart.svg" mode="aspectFit" />
        <text>导出图片</text>
      </view>
      <view class="bottom-action" @tap="shareResult">
        <image class="bottom-icon" src="/static/icons/line/share.svg" mode="aspectFit" />
        <text>分享</text>
      </view>
    </view>
  </view>
</template>

<script setup>
import { onMounted, ref } from 'vue'

const generatedImageUrl = ref('')
const topic = ref('')
const resultId = ref('')

function readPageOptions() {
  const pages = getCurrentPages()
  const current = pages[pages.length - 1] || {}
  return current.options || current.$page?.options || {}
}

function applyOptions(options = {}) {
  if (options.topic) topic.value = decodeURIComponent(options.topic)
  if (options.resultId) resultId.value = decodeURIComponent(options.resultId)
  if (resultId.value) {
    const cached = uni.getStorageSync(`aiMindmapResult:${resultId.value}`)
    if (cached) {
      topic.value = cached.title || topic.value
      generatedImageUrl.value = cached.imageUrl || generatedImageUrl.value
    }
  }
  const imageUrl = options.imageUrl || options.previewUrl || options.resultUrl || ''
  if (imageUrl) generatedImageUrl.value = decodeURIComponent(imageUrl)
}

const goBack = () => { uni.navigateBack() }
const openHistory = () => { uni.showToast({ title: '历史记录预留', icon: 'none' }) }
const saveResult = () => {
  uni.showToast({ title: resultId.value ? '已保存到历史记录' : '保存能力预留', icon: 'none' })
}
const exportImage = () => {
  uni.showToast({ title: generatedImageUrl.value ? '图片导出能力预留' : '暂无可导出的图片', icon: 'none' })
}
const shareResult = () => {
  uni.showToast({ title: generatedImageUrl.value ? '分享能力预留' : '暂无可分享的图片', icon: 'none' })
}

onMounted(() => {
  applyOptions(readPageOptions())
})
</script>

<style lang="scss" scoped>
.page {
  display: flex;
  min-height: 100vh;
  flex-direction: column;
  background: #FCFAFC;
  color: #1C2E48;
}

.nav-bar {
  position: relative;
  z-index: 10;
  display: flex;
  align-items: center;
  height: 96rpx;
  padding: 0 34rpx;
  border-bottom: 1rpx solid #ECEEF2;
  background: #FFFFFF;
  box-sizing: border-box;
}

.nav-action {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 46rpx;
  height: 46rpx;
}

.nav-action--left {
  margin-right: 24rpx;
}

.nav-action--right {
  margin-left: auto;
}

.nav-back {
  color: #2D4566;
  font-size: 52rpx;
  font-weight: 300;
  line-height: 1;
  transform: translateY(-2rpx);
}

.nav-title {
  color: #203757;
  font-size: 36rpx;
  font-weight: 800;
  letter-spacing: 1rpx;
}

.nav-icon {
  width: 34rpx;
  height: 34rpx;
}

.preview-area {
  position: relative;
  display: flex;
  flex: 1;
  align-items: center;
  justify-content: center;
  min-height: 0;
  padding: 32rpx;
  background: #FCFAFC;
  box-sizing: border-box;
}

.generated-image {
  width: 100%;
  height: 100%;
}

.bottom-bar {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  height: 104rpx;
  border-top: 1rpx solid #ECEEF2;
  background: #FFFFFF;
  box-sizing: border-box;
}

.bottom-action {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  color: #172A42;
  font-size: 22rpx;
  font-weight: 500;
}

.bottom-icon {
  width: 34rpx;
  height: 34rpx;
  margin-bottom: 6rpx;
}
</style>
