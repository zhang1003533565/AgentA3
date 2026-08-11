<template>
  <view class="page">
    <nav-bar
      :title="fileTitle"
      :showBack="true"
      :border="false"
      :fixed="true"
      :placeholder="true"
      titleAlign="center"
    >
      <template #right>
        <view class="nav-fallback" @tap="openFallback">系统预览</view>
      </template>
    </nav-bar>

    <view class="preview-stage">
      <image
        v-if="previewMode === 'image' && !onlineFailed"
        class="image-preview"
        :src="previewUrl"
        mode="aspectFit"
        @error="handleOnlineError"
      />

      <web-view
        v-else-if="previewMode === 'web' && !onlineFailed"
        class="web-preview"
        :src="viewerUrl"
        @error="handleOnlineError"
      />

      <view v-else class="fallback-panel">
        <view class="file-mark">
          <text>{{ fileExtLabel }}</text>
        </view>
        <text class="fallback-title">在线预览暂不可用</text>
        <text class="fallback-desc">{{ fallbackText }}</text>
        <view class="fallback-button" @tap="openFallback">
          <text>使用系统预览</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import NavBar from '@/components/nav-bar/nav-bar.vue'
import { fallbackPreviewUploadedDocument, normalizePreviewUrl } from '../utils/filePreview.js'

const previewInfo = ref({})
const onlineFailed = ref(false)

const IMAGE_EXTS = new Set(['png', 'jpg', 'jpeg', 'gif', 'webp', 'bmp'])
const OFFICE_EXTS = new Set(['doc', 'docx', 'ppt', 'pptx', 'xls', 'xlsx'])

const fileTitle = computed(() => previewInfo.value.name || '文件预览')
const previewUrl = computed(() => normalizePreviewUrl(previewInfo.value.url || ''))
const fileExtLabel = computed(() => String(previewInfo.value.ext || 'FILE').toUpperCase())
const previewMode = computed(() => {
  const ext = String(previewInfo.value.ext || '').toLowerCase()
  if (!previewUrl.value) return 'fallback'
  if (IMAGE_EXTS.has(ext)) return 'image'
  return 'web'
})
const viewerUrl = computed(() => {
  const url = previewUrl.value
  const ext = String(previewInfo.value.ext || '').toLowerCase()
  if (!url) return ''
  if (OFFICE_EXTS.has(ext)) {
    return `https://view.officeapps.live.com/op/view.aspx?src=${encodeURIComponent(url)}`
  }
  return url
})
const fallbackText = computed(() => {
  if (!previewUrl.value) return '当前文件没有可用的在线地址，可以尝试使用系统预览打开本地文件。'
  if (OFFICE_EXTS.has(String(previewInfo.value.ext || '').toLowerCase())) {
    return 'Office 文档在线预览依赖远程地址可访问；如加载失败，可使用系统预览打开。'
  }
  return '当前平台或文件类型暂不支持在线展示，可使用系统预览继续查看。'
})

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

.nav-fallback {
  padding: 0 18rpx;
  color: #4D6BFE;
  font-size: 24rpx;
  font-weight: 700;
  line-height: 64rpx;
}

.preview-stage {
  position: relative;
  flex: 1;
  min-height: 0;
  background: #F8FAFC;
}

.image-preview,
.web-preview {
  width: 100%;
  height: 100%;
}

.fallback-panel {
  position: absolute;
  left: 40rpx;
  right: 40rpx;
  top: 50%;
  display: flex;
  transform: translateY(-50%);
  flex-direction: column;
  align-items: center;
  padding: 48rpx 36rpx;
  border: 1rpx solid #E5EAF2;
  border-radius: 28rpx;
  background: #FFFFFF;
  box-shadow: 0 18rpx 48rpx rgba(38, 56, 88, 0.08);
  box-sizing: border-box;
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
}

.fallback-title {
  color: #182033;
  font-size: 34rpx;
  font-weight: 800;
}

.fallback-desc {
  margin-top: 16rpx;
  color: #7C8496;
  font-size: 26rpx;
  line-height: 1.55;
  text-align: center;
}

.fallback-button {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 100%;
  height: 88rpx;
  margin-top: 34rpx;
  border-radius: 44rpx;
  background: #4D6BFE;
  color: #FFFFFF;
  font-size: 28rpx;
  font-weight: 800;
}

.fallback-button:active,
.nav-fallback:active {
  opacity: 0.72;
}
</style>
