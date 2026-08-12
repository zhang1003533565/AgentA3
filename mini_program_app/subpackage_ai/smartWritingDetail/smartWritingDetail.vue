<template>
  <view class="detail-page">
    <nav-bar title="作品详情页" :showBack="true" fixed placeholder />

    <view v-if="record" class="detail-card">
      <view class="detail-head">
        <text class="detail-title">{{ record.title }}</text>
        <text class="detail-meta">{{ record.sceneLabel || '智能写作' }} | {{ formatTime(record.createdAt) }}</text>
        <view class="detail-stats">
          <text class="detail-stat">字数 {{ contentWordCount }} 字</text>
        </view>
      </view>

      <scroll-view class="detail-content" scroll-y>
        <text class="content-text">{{ record.content || record.prompt }}</text>
      </scroll-view>
    </view>

    <view v-if="record" class="detail-actions detail-actions--floating">
      <view
        class="detail-action"
        :class="primaryActionClass"
        @tap="handlePrimaryAction"
      >
        <text class="action-icon">{{ primaryActionIcon }}</text>
        <text>{{ primaryActionText }}</text>
      </view>
      <view class="detail-action" :class="secondaryActionClass" @tap="handleSecondaryAction">
        <text class="action-icon">{{ secondaryActionIcon }}</text>
        <text>{{ secondaryActionText }}</text>
      </view>
      <view class="detail-action" @tap="exportContent">
        <text class="action-icon">导</text>
        <text>导出</text>
      </view>
      <view class="detail-action detail-action--primary" @tap="continueWriting">
        <text class="action-icon">续</text>
        <text>AI续写</text>
      </view>
    </view>

    <view v-if="showExportPanel" class="export-panel-mask" @tap="closeExportPanel">
      <view class="export-panel" @tap.stop>
        <view class="export-option" @tap="handleExportOption('txt')">
          <text class="export-option__icon">文</text>
          <text class="export-option__label">纯文本</text>
        </view>
        <view class="export-option" @tap="handleExportOption('md')">
          <text class="export-option__icon">MD</text>
          <text class="export-option__label">Markdown</text>
        </view>
        <view class="export-option" @tap="handleExportOption('word')">
          <text class="export-option__icon">W</text>
          <text class="export-option__label">Word</text>
        </view>
      </view>
    </view>

    <view v-if="!record" class="empty-state">
      <text class="empty-title">作品不存在</text>
      <text class="empty-desc">这条作品可能已经被删除</text>
    </view>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import NavBar from '@/components/nav-bar/nav-bar.vue'
import { exportSmartWritingAsWord } from '@/api/ai.js'
import {
  clearSmartWritingDraft,
  deleteSmartWritingHistory,
  deleteSmartWritingSavedWork,
  getSmartWritingDraft,
  getSmartWritingHistory,
  getSmartWritingSavedWorks,
  saveSmartWritingSavedWork
} from '@/utils/smartWritingHistory.js'

const SMART_WRITING_CONTINUE_CONTEXT_KEY = 'smartWritingContinueContext'
const record = ref(null)
const detailSource = ref('history')
const isSavedWork = ref(false)
const showExportPanel = ref(false)
let recordId = ''

onLoad((options) => {
  recordId = options?.id ? decodeURIComponent(options.id) : ''
  const source = options?.source ? decodeURIComponent(options.source) : ''
  const savedRecord = getSmartWritingSavedWorks().find(item => item.id === recordId) || null
  const historyRecord = getSmartWritingHistory().find(item => item.id === recordId) || null
  const draftRecord = getSmartWritingDraft()

  if (source === 'saved' && savedRecord) {
    record.value = savedRecord
    detailSource.value = 'saved'
    isSavedWork.value = true
    return
  }
  if (source === 'history' && historyRecord) {
    record.value = historyRecord
    detailSource.value = 'history'
    isSavedWork.value = Boolean(savedRecord)
    return
  }
  if (savedRecord) {
    record.value = savedRecord
    detailSource.value = 'saved'
    isSavedWork.value = true
    return
  }
  if (historyRecord) {
    record.value = historyRecord
    detailSource.value = 'history'
    isSavedWork.value = Boolean(savedRecord)
    return
  }
  record.value = draftRecord?.id === recordId ? draftRecord : null
  detailSource.value = 'draft'
  isSavedWork.value = Boolean(savedRecord)
})

const formatTime = (value) => {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value).replace('T', ' ').slice(0, 16)
  const pad = (number) => String(number).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

const contentWordCount = computed(() => {
  const content = String(record.value?.content || record.value?.prompt || '')
  return content.replace(/\s/g, '').length
})

const copyContent = () => {
  if (!record.value) return
  uni.setClipboardData({
    data: record.value.content || record.value.prompt || '',
    success: () => uni.showToast({ title: '内容已复制', icon: 'success' })
  })
}

const formatExportTime = (value = new Date()) => {
  const date = value instanceof Date ? value : new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  const pad = (number) => String(number).padStart(2, '0')
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

const getSafeExportFilename = (title, extension) => {
  const safeTitle = String(title || '智能写作')
    .replace(/[\\/:*?"<>|]/g, '')
    .replace(/\s+/g, '-')
    .slice(0, 28) || '智能写作'
  return `${safeTitle}-${Date.now()}.${extension}`
}

const buildExportData = () => {
  const title = record.value?.title || '智能写作作品'
  const sceneLabel = record.value?.sceneLabel || '智能写作'
  const createdAt = formatTime(record.value?.createdAt) || '未记录'
  const exportedAt = formatExportTime()
  const model = record.value?.modelConfigPrefix || record.value?.model || '未记录'
  const content = String(record.value?.content || record.value?.prompt || '').trim()
  return {
    title,
    text: [
      `标题：${title}`,
      `类型：${sceneLabel}`,
      `生成时间：${createdAt}`,
      `导出时间：${exportedAt}`,
      `模型：${model}`,
      '',
      '正文：',
      content
    ].join('\n'),
    markdown: [
      `# ${title}`,
      '',
      `- 类型：${sceneLabel}`,
      `- 生成时间：${createdAt}`,
      `- 导出时间：${exportedAt}`,
      `- 模型：${model}`,
      '',
      '## 正文',
      '',
      content
    ].join('\n')
  }
}

const downloadTextContent = (content, filename, successTitle) => {
  if (typeof window !== 'undefined' && typeof document !== 'undefined' && typeof Blob !== 'undefined') {
    const blob = new Blob([`\uFEFF${content}`], { type: 'text/plain;charset=utf-8' })
    const url = window.URL.createObjectURL(blob)
    const link = document.createElement('a')
    link.href = url
    link.download = filename
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.URL.revokeObjectURL(url)
    uni.showToast({ title: successTitle, icon: 'success' })
    return
  }
  uni.showToast({ title: '当前环境暂不支持下载文件', icon: 'none' })
}

const exportContent = () => {
  if (!record.value) return
  const content = String(record.value.content || record.value.prompt || '').trim()
  if (!content) {
    uni.showToast({ title: '暂无可导出内容', icon: 'none' })
    return
  }
  showExportPanel.value = true
}

const closeExportPanel = () => {
  showExportPanel.value = false
}

const handleExportOption = (type) => {
  const exportData = buildExportData()
  closeExportPanel()
  if (type === 'txt') {
    downloadTextContent(exportData.text, getSafeExportFilename(exportData.title, 'txt'), '已导出文本')
  } else if (type === 'md') {
    downloadTextContent(exportData.markdown, getSafeExportFilename(exportData.title, 'md'), '已导出 Markdown')
  } else if (type === 'word') {
    downloadWordDocument(exportData)
  }
}

const downloadWordDocument = (exportData) => {
  uni.showLoading({ title: '正在生成文档...' })
  const payload = {
    title: exportData.title,
    sceneLabel: record.value?.sceneLabel || '',
    generatedAt: formatTime(record.value?.createdAt) || '',
    model: record.value?.modelConfigPrefix || record.value?.model || '',
    content: String(record.value?.content || record.value?.prompt || '').trim()
  }
  exportSmartWritingAsWord(payload)
    .then(blob => {
      if (typeof window !== 'undefined' && typeof document !== 'undefined' && typeof URL !== 'undefined') {
        const url = window.URL.createObjectURL(blob)
        const link = document.createElement('a')
        link.href = url
        link.download = getSafeExportFilename(exportData.title, 'docx')
        document.body.appendChild(link)
        link.click()
        document.body.removeChild(link)
        window.URL.revokeObjectURL(url)
        uni.hideLoading()
        uni.showToast({ title: '已导出 Word 文档', icon: 'success' })
      } else {
        uni.hideLoading()
        uni.showToast({ title: '当前环境暂不支持下载文件', icon: 'none' })
      }
    })
    .catch(error => {
      uni.hideLoading()
      const msg = error?.message || '导出失败'
      uni.showToast({ title: msg, icon: 'none' })
    })
}

const primaryActionText = computed(() => {
  if (detailSource.value === 'saved') return '删除'
  return isSavedWork.value ? '已保存' : '保存'
})

const primaryActionIcon = computed(() => {
  if (detailSource.value === 'saved') return '删'
  return '存'
})

const primaryActionClass = computed(() => {
  if (detailSource.value === 'saved') return 'detail-action--danger'
  return isSavedWork.value ? 'detail-action--muted' : 'detail-action--save'
})

const secondaryActionText = computed(() => {
  return detailSource.value === 'history' ? '删除' : '复制'
})

const secondaryActionIcon = computed(() => {
  return detailSource.value === 'history' ? '删' : '复'
})

const secondaryActionClass = computed(() => {
  return detailSource.value === 'history' ? 'detail-action--danger' : ''
})

const handlePrimaryAction = () => {
  if (detailSource.value === 'saved') {
    removeRecord()
    return
  }
  if (isSavedWork.value) {
    uni.showToast({ title: '已在保存作品中', icon: 'none' })
    return
  }
  saveRecord()
}

const handleSecondaryAction = () => {
  if (detailSource.value === 'history') {
    removeRecord()
    return
  }
  copyContent()
}

const removeRecord = () => {
  if (!record.value) return
  const isSavedSource = detailSource.value === 'saved'
  const label = isSavedSource ? '已保存作品' : '历史记录'
  uni.showModal({
    title: `删除${label}`,
    content: `删除后将无法在${label}中查看，确定删除吗？`,
    success: ({ confirm }) => {
      if (!confirm) return
      if (isSavedSource) {
        deleteSmartWritingSavedWork(record.value.id)
      } else if (detailSource.value === 'history') {
        deleteSmartWritingHistory(record.value.id)
      } else {
        clearSmartWritingDraft(record.value.id)
      }
      uni.showToast({ title: '已删除', icon: 'success' })
      setTimeout(() => uni.navigateBack(), 350)
    }
  })
}

const saveRecord = () => {
  if (!record.value) return
  const savedRecord = saveSmartWritingSavedWork(record.value)
  clearSmartWritingDraft(savedRecord.id)
  record.value = savedRecord
  recordId = savedRecord.id
  isSavedWork.value = true
  uni.showToast({ title: '已保存', icon: 'success' })
}

const trimContinueSource = (content, maxLength = 3000) => {
  const text = String(content || '').trim()
  if (text.length <= maxLength) return text
  return text.slice(-maxLength)
}

const resolveContinueModel = () => {
  const value = String(record.value?.modelConfigPrefix || record.value?.model || '').trim()
  if (!value) return ''
  return value.startsWith('ai.service.') ? value : `ai.service.text.${value}`
}

const continueWriting = () => {
  if (!record.value) return
  const sourceContent = String(record.value.content || record.value.prompt || '').trim()
  if (!sourceContent) {
    uni.showToast({ title: '暂无可续写内容', icon: 'none' })
    return
  }
  const title = record.value.title || '智能写作作品'
  const continueSource = trimContinueSource(sourceContent)
  const requestText = [
    '请基于下面这篇作品继续续写，保持原有主题、语气和表达风格。',
    '续写内容要自然衔接原文，不要重复原文，不要解释过程，直接输出续写正文。',
    sourceContent.length > continueSource.length ? '原文较长，下面提供的是靠近结尾的部分，请优先承接结尾续写。' : '',
    '',
    `作品标题：${title}`,
    '',
    '原文：',
    continueSource
  ].filter(item => item !== '').join('\n')
  uni.setStorageSync(SMART_WRITING_CONTINUE_CONTEXT_KEY, {
    requestText,
    displayText: sourceContent,
    displayMode: 'smart-writing-source',
    llmModel: resolveContinueModel(),
    createdAt: Date.now()
  })
  uni.navigateTo({
    url: '/subpackage_ai/aiConversation/aiConversation?smartWritingContinue=1'
  })
}
</script>

<style lang="scss" scoped>
.detail-page {
  min-height: 100vh;
  padding: 24rpx 28rpx 180rpx;
  background: #f5f6fa;
  box-sizing: border-box;
}

.detail-card {
  min-height: calc(100vh - 150rpx);
  padding: 30rpx 28rpx 40rpx;
  border-radius: 22rpx;
  background: #ffffff;
  box-sizing: border-box;
}

.detail-head {
  padding-bottom: 26rpx;
  border-bottom: 1rpx solid #eef0f5;
}

.detail-title,
.detail-meta {
  display: block;
}

.detail-title {
  color: #232631;
  font-size: 32rpx;
  font-weight: 750;
}

.detail-meta {
  margin-top: 14rpx;
  color: #a0a6b3;
  font-size: 22rpx;
}

.detail-stats {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin-top: 18rpx;
}

.detail-stat {
  display: inline-flex;
  align-items: center;
  min-height: 42rpx;
  padding: 0 16rpx;
  border-radius: 999rpx;
  background: #f3f6fb;
  color: #667085;
  font-size: 21rpx;
  font-weight: 600;
}

.detail-content {
  height: calc(100vh - 370rpx);
  margin-top: 26rpx;
}

.content-text {
  color: #303541;
  font-size: 27rpx;
  line-height: 1.85;
  white-space: pre-wrap;
}

.detail-actions {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding-top: 22rpx;
  border-top: 1rpx solid #eef0f5;
}

.detail-actions--floating {
  position: fixed !important;
  left: 0;
  right: 0;
  bottom: 0 !important;
  z-index: 999;
  padding: 28rpx 54rpx 34rpx;
  border-radius: 34rpx 34rpx 0 0;
  background: rgba(255, 255, 255, 0.98);
  box-shadow: 0 -18rpx 44rpx rgba(20, 25, 40, 0.12);
  backdrop-filter: blur(10px);
  -webkit-backdrop-filter: blur(10px);
  border-top: none;
  box-sizing: border-box;
  padding-bottom: calc(34rpx + env(safe-area-inset-bottom));
}

.detail-action {
  display: flex;
  align-items: center;
  flex-direction: column;
  gap: 7rpx;
  color: #687180;
  font-size: 20rpx;
}

.action-icon {
  display: flex;
  width: 48rpx;
  height: 48rpx;
  align-items: center;
  justify-content: center;
  border-radius: 14rpx;
  background: #edf0f6;
  color: #697386;
  font-size: 20rpx;
  font-weight: 700;
}

.detail-action--danger {
  color: #d25a6a;
}

.detail-action--danger .action-icon {
  background: #fff0f2;
  color: #d25a6a;
}

.detail-action--save {
  color: #1f8a5b;
}

.detail-action--save .action-icon {
  background: #eaf8f1;
  color: #1f8a5b;
}

.detail-action--muted {
  color: #8c95a3;
}

.detail-action--muted .action-icon {
  background: #edf0f6;
  color: #8c95a3;
}

.detail-action--primary {
  color: #5a4ce0;
}

.detail-action--primary .action-icon {
  background: #ece9ff;
  color: #5a4ce0;
}

.export-panel-mask {
  position: fixed;
  left: 0;
  right: 0;
  top: 0;
  bottom: 0;
  z-index: 998;
  background: rgba(17, 24, 39, 0.18);
}

.export-panel {
  position: fixed;
  left: 32rpx;
  right: 32rpx;
  bottom: calc(166rpx + env(safe-area-inset-bottom));
  z-index: 1000;
  display: flex;
  align-items: center;
  justify-content: space-around;
  gap: 16rpx;
  padding: 22rpx 24rpx;
  border-radius: 28rpx;
  background: rgba(255, 255, 255, 0.98);
  box-shadow: 0 18rpx 48rpx rgba(20, 25, 40, 0.16);
  box-sizing: border-box;
}

.export-option {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10rpx;
  min-height: 72rpx;
  border-radius: 18rpx;
  color: #4B5563;
  font-size: 24rpx;
  font-weight: 700;
}

.export-option__icon {
  width: 44rpx;
  height: 44rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 13rpx;
  background: #EEF4FF;
  color: #2F6FE4;
  font-size: 19rpx;
  font-weight: 800;
}

.export-option:active {
  background: #F3F6FB;
}

.empty-state {
  display: flex;
  align-items: center;
  flex-direction: column;
  padding: 220rpx 30rpx;
  text-align: center;
}

.empty-title {
  color: #4d5360;
  font-size: 30rpx;
  font-weight: 700;
}

.empty-desc {
  margin-top: 12rpx;
  color: #9aa1ae;
  font-size: 23rpx;
}
</style>
