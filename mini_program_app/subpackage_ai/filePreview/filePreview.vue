<template>
  <view class="page">
    <nav-bar
      title="文件预览"
      :showBack="true"
      :border="false"
      :fixed="true"
      :placeholder="true"
      titleAlign="center"
    />

    <view class="preview-stage" v-if="previewMode === 'image' && !onlineFailed">
      <image
        class="image-preview"
        :src="previewUrl"
        mode="aspectFit"
        @error="handleOnlineError"
      />
    </view>

    <scroll-view v-else class="preview-scroll" scroll-y :show-scrollbar="false">
      <view class="file-card">
        <view class="file-mark">
          <text>{{ fileExtLabel }}</text>
        </view>
        <view class="file-main">
          <text class="file-name">{{ fileTitle }}</text>
          <text class="file-meta">{{ fileMetaText }}</text>
        </view>
      </view>

      <view v-if="hasSummary" class="content-card">
        <view class="card-title-row">
          <text class="card-title">AI 摘要</text>
          <text v-if="summaryMetaText" class="card-meta">{{ summaryMetaText }}</text>
        </view>
        <text class="summary-text">{{ previewSummary }}</text>
      </view>

      <view v-if="hasText" class="content-card content-card--body">
        <view class="card-title-row">
          <text class="card-title">解析内容</text>
          <text class="card-meta">{{ contentMetaText }}</text>
        </view>
        <view class="paragraph-list">
          <text
            v-for="(paragraph, index) in previewParagraphs"
            :key="index"
            class="paragraph"
          >{{ paragraph }}</text>
        </view>
      </view>

      <view v-if="!hasSummary && !hasText" class="fallback-panel">
        <view class="file-mark file-mark--large">
          <text>{{ fileExtLabel }}</text>
        </view>
        <text class="fallback-title">暂未获得解析内容</text>
        <text class="fallback-desc">{{ fallbackText }}</text>
      </view>

      <view class="fallback-button" @tap="openFallback">
        <text>使用系统预览</text>
      </view>
    </scroll-view>
  </view>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import NavBar from '@/components/nav-bar/nav-bar.vue'
import { fallbackPreviewUploadedDocument, normalizePreviewUrl } from '../utils/filePreview.js'

const previewInfo = ref({})
const onlineFailed = ref(false)

const IMAGE_EXTS = new Set(['png', 'jpg', 'jpeg', 'gif', 'webp', 'bmp'])

const fileTitle = computed(() => previewInfo.value.name || '文件预览')
const previewUrl = computed(() => normalizePreviewUrl(previewInfo.value.url || ''))
const fileExtLabel = computed(() => String(previewInfo.value.ext || 'FILE').toUpperCase())
const previewSummary = computed(() => normalizeText(previewInfo.value.summary || ''))
const previewText = computed(() => normalizeText(previewInfo.value.text || ''))
const hasSummary = computed(() => Boolean(previewSummary.value))
const hasText = computed(() => Boolean(previewText.value))
const previewMode = computed(() => {
  const ext = String(previewInfo.value.ext || '').toLowerCase()
  if (previewUrl.value && IMAGE_EXTS.has(ext) && !hasText.value && !hasSummary.value) return 'image'
  return 'content'
})
const previewParagraphs = computed(() => {
  const text = previewText.value || previewSummary.value
  return text
    .split(/\n{2,}/)
    .map(item => item.trim())
    .filter(Boolean)
    .slice(0, 120)
})
const fileMetaText = computed(() => {
  const parts = []
  if (fileExtLabel.value !== 'FILE') parts.push(fileExtLabel.value)
  if (previewInfo.value.size) parts.push(formatFileSize(previewInfo.value.size))
  if (previewInfo.value.textLength) parts.push(`${previewInfo.value.textLength}字`)
  return parts.join(' · ') || '已导入文件'
})
const summaryMetaText = computed(() => {
  const status = String(previewInfo.value.summaryStatus || '').toUpperCase()
  const model = String(previewInfo.value.summaryModel || '').trim()
  if (status === 'AI' && model) return model
  if (status === 'LOCAL') return '本地摘要'
  return ''
})
const contentMetaText = computed(() => `${previewParagraphs.value.length}段`)
const fallbackText = computed(() => {
  if (previewUrl.value || previewInfo.value.localPath) return '可以使用系统预览打开原文件。'
  return '当前文件没有可用的在线地址或本地路径，请重新导入后再试。'
})

function normalizeText(value = '') {
  return String(value || '')
    .replace(/\r\n/g, '\n')
    .replace(/\n{3,}/g, '\n\n')
    .trim()
}

function formatFileSize(value = 0) {
  const size = Number(value || 0)
  if (!size) return ''
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(2)} MB`
}

function readPageOptions() {
  const pages = getCurrentPages()
  const current = pages[pages.length - 1] || {}
  return current.options || current.$page?.options || {}
}

function decodeOption(value = '') {
  try {
    return decodeURIComponent(value)
  } catch (error) {
    return value
  }
}

function loadPreviewInfo() {
  const options = readPageOptions()
  const key = decodeOption(options.key || '')
  const cached = key ? uni.getStorageSync(`aiFilePreview:${key}`) : null
  previewInfo.value = cached || {
    name: decodeOption(options.name || '文件预览'),
    ext: decodeOption(options.ext || ''),
    url: decodeOption(options.url || ''),
    localPath: decodeOption(options.localPath || ''),
    text: decodeOption(options.text || ''),
    summary: decodeOption(options.summary || ''),
  }
}

function handleOnlineError() {
  onlineFailed.value = true
}

function openFallback() {
  fallbackPreviewUploadedDocument(previewInfo.value)
}

onMounted(loadPreviewInfo)
</script>

<style lang="scss" scoped>
.page {
  display: flex;
  min-height: 100vh;
  flex-direction: column;
  background: #F8FAFC;
  color: #182033;
}

.preview-stage {
  position: relative;
  flex: 1;
  min-height: 0;
  background: #F8FAFC;
}

.image-preview {
  width: 100%;
  height: 100%;
}

.preview-scroll {
  flex: 1;
  min-height: 0;
  padding: 24rpx 28rpx calc(40rpx + env(safe-area-inset-bottom));
  box-sizing: border-box;
}

.file-card,
.content-card,
.fallback-panel {
  border: 1rpx solid #E5EAF2;
  border-radius: 24rpx;
  background: #FFFFFF;
  box-shadow: 0 10rpx 32rpx rgba(38, 56, 88, 0.06);
  box-sizing: border-box;
}

.file-card {
  display: flex;
  align-items: center;
  padding: 24rpx;
}

.file-main {
  min-width: 0;
  margin-left: 20rpx;
  flex: 1;
}

.file-name {
  display: block;
  overflow: hidden;
  color: #182033;
  font-size: 30rpx;
  font-weight: 800;
  line-height: 1.35;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-meta {
  display: block;
  margin-top: 8rpx;
  color: #7C8496;
  font-size: 24rpx;
  line-height: 1.35;
}

.file-mark {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 96rpx;
  height: 112rpx;
  margin-bottom: 28rpx;
  border-radius: 16rpx;
  background: #EEF4FF;
  color: #4D6BFE;
  font-size: 24rpx;
  font-weight: 800;
  flex-shrink: 0;
}

.file-mark--large {
  margin-bottom: 24rpx;
}

.content-card {
  margin-top: 20rpx;
  padding: 28rpx 28rpx 30rpx;
}

.content-card--body {
  padding-bottom: 22rpx;
}

.card-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
  margin-bottom: 18rpx;
}

.card-title {
  color: #182033;
  font-size: 28rpx;
  font-weight: 800;
}

.card-meta {
  color: #8A94A8;
  font-size: 22rpx;
}

.summary-text,
.paragraph {
  display: block;
  color: #43506A;
  font-size: 26rpx;
  line-height: 1.75;
}

.paragraph + .paragraph {
  margin-top: 18rpx;
}

.fallback-panel {
  display: flex;
  align-items: center;
  flex-direction: column;
  margin-top: 20rpx;
  padding: 48rpx 34rpx;
}

.fallback-title {
  color: #182033;
  font-size: 32rpx;
  font-weight: 800;
}

.fallback-desc {
  margin-top: 14rpx;
  color: #7C8496;
  font-size: 25rpx;
  line-height: 1.55;
  text-align: center;
}

.fallback-button {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 88rpx;
  margin: 24rpx 0 0;
  border-radius: 44rpx;
  background: #4D6BFE;
  color: #FFFFFF;
  font-size: 28rpx;
  font-weight: 800;
}

.fallback-button:active {
  opacity: 0.72;
}
</style>
