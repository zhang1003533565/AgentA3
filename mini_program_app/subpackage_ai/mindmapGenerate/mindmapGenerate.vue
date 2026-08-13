<template>
  <view class="page">
    <nav-bar
      title="AI 思维导图"
      :showBack="true"
      :border="false"
    >
      <template #right>
        <view class="nav-history-action" @tap="openHistory">
          <image class="nav-history-icon" src="/static/icons/diagram/history.svg" mode="aspectFit" />
        </view>
      </template>
    </nav-bar>

    <scroll-view class="content" scroll-y :show-scrollbar="false">
      <view class="content-inner">
        <view class="section-card input-card">
          <view class="card-head">
            <view class="card-head__title">
              <view class="title-dot"></view>
              <text>输入内容</text>
            </view>
            <text class="char-count">{{ topic.length }}/2000</text>
          </view>

          <textarea
            class="prompt-input"
            v-model="topic"
            placeholder="请输入你想生成思维导图的内容或要求..."
            placeholder-class="prompt-placeholder"
            :maxlength="2000"
          />

          <view class="input-tools">
            <ImportFileButton class="import-trigger" :loading="isUploading" @click="importDocument" />
            <view class="file-help">
              <view class="file-help__icon">i</view>
              <text class="file-help__text">支持 PDF / Word / PPT（≤ 20MB）</text>
            </view>
          </view>
        </view>

        <view v-if="uploadedFile" class="section-card file-status-card">
          <view class="file-status-main">
            <view class="file-type-icon" :class="`file-type-icon--${uploadedFileType}`">
              <view class="file-type-icon__fold"></view>
              <text>{{ uploadedFileTypeLabel }}</text>
            </view>
            <view class="file-status-info">
              <text class="file-status-name">{{ uploadedFile.fileName || '已导入文件' }}</text>
              <text class="file-status-size">{{ uploadedFileSizeText }}</text>
            </view>
            <view class="file-status-actions">
              <view class="file-action" @tap.stop="previewUploadedFile">
                <image class="file-action__icon" src="/static/icons/diagram/eye-lucide.svg" mode="aspectFit" />
              </view>
              <view class="file-action" @tap.stop="removeUploadedFile">
                <image class="file-action__icon" src="/static/icons/diagram/trash-2-lucide.svg" mode="aspectFit" />
              </view>
            </view>
          </view>
          <view class="file-success">
            <view class="file-success__icon"></view>
            <text>文件上传成功，可继续输入补充要求</text>
          </view>
          <view v-if="uploadedFile.summary" class="file-summary">
            <text class="file-summary__label">AI 总结</text>
            <text class="file-summary__text">{{ uploadedFile.summary }}</text>
            <text v-if="parsedMetaText(uploadedFile)" class="file-summary__meta">{{ parsedMetaText(uploadedFile) }}</text>
          </view>
        </view>

        <view class="section-card settings-card">
          <view class="settings-content">
            <view class="settings-title">
              <image class="settings-title__icon" src="/static/icons/diagram/settings-spark.svg" mode="aspectFit" />
              <view class="settings-title__text">
                <text class="settings-title__main">专属设置</text>
                <text class="settings-title__sub">设置导图的结构与展示风格</text>
              </view>
            </view>

            <view class="field-group">
              <text class="field-label">中心主题（可选）</text>
              <view class="theme-input-wrap">
                <input
                  class="theme-input"
                  v-model="centerTopic"
                  @input="onCenterTopicInput"
                  placeholder="不填写将由 AI 自动提取"
                  placeholder-class="theme-placeholder"
                  :maxlength="10"
                />
                <text class="theme-count">{{ centerTopic.length }}/10</text>
              </view>
              <text class="field-tip">设置导图的中心主题名称，留空将由 AI 自动提取</text>
            </view>

            <view class="field-group">
              <text class="field-label">层级深度</text>
              <view class="choice-grid choice-grid--depth">
                <view
                  class="choice-card"
                  :class="{ 'choice-card--active': selectedDepth === item.key }"
                  v-for="item in depthOptions"
                  :key="item.key"
                  @tap="selectedDepth = item.key"
                >
                  <text class="choice-card__title">{{ item.label }}</text>
                  <text class="choice-card__desc">{{ item.desc }}</text>
                </view>
              </view>
              <text class="field-tip">推荐合适的层级深度，平衡结构完整性与阅读体验</text>
            </view>

            <view class="field-group">
              <text class="field-label">结构方式</text>
              <view class="choice-grid choice-grid--structure">
                <view
                  class="choice-card"
                  :class="{ 'choice-card--active': selectedStructure === item.key }"
                  v-for="item in structureOptions"
                  :key="item.key"
                  @tap="selectedStructure = item.key"
                >
                  <text class="choice-card__title">{{ item.label }}</text>
                  <text class="choice-card__desc">{{ item.desc }}</text>
                </view>
              </view>
              <text class="field-tip">选择导图的结构组织方式</text>
            </view>

            <view class="field-group field-group--last">
              <text class="field-label">展开程度</text>
              <view class="choice-grid choice-grid--expand">
                <view
                  class="choice-card"
                  :class="{ 'choice-card--active': selectedExpand === item.key }"
                  v-for="item in expandOptions"
                  :key="item.key"
                  @tap="selectedExpand = item.key"
                >
                  <text class="choice-card__title">{{ item.label }}</text>
                  <text class="choice-card__desc">{{ item.desc }}</text>
                </view>
              </view>
              <text class="field-tip">控制每个节点的展开内容多少</text>
            </view>
          </view>
        </view>

        <view class="section-card recent-card">
          <view class="recent-head">
            <view class="recent-head__icon">
              <image src="/static/icons/diagram/mindmap-purple.svg" mode="aspectFit" />
            </view>
            <text>最近生成</text>
          </view>

          <view v-if="displayRecentItems.length" class="recent-list">
            <view
              class="recent-item"
              v-for="(item, index) in displayRecentItems"
              :key="item.id"
              @tap="openRecent(item)"
            >
              <view class="recent-node-icon" :class="`recent-node-icon--${index % 3}`">
                <svg class="recent-node-svg" viewBox="0 0 24 24" fill="none">
                  <path d="M12 7.5v5M12 12.5 7.5 17M12 12.5 16.5 17" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"/>
                  <circle cx="12" cy="6" r="2.5" fill="currentColor"/>
                  <circle cx="7" cy="18" r="2.5" fill="currentColor"/>
                  <circle cx="17" cy="18" r="2.5" fill="currentColor"/>
                </svg>
              </view>
              <view class="recent-info">
                <text class="recent-name">{{ item.title || '未命名思维导图' }}</text>
                <text class="recent-meta">{{ recentMeta(item) }}</text>
              </view>
              <text class="recent-time">{{ formatRecentTime(item.createTime) }}</text>
              <view class="recent-more">
                <text></text>
                <text></text>
                <text></text>
              </view>
            </view>
          </view>

          <view v-else class="recent-empty">暂无生成记录</view>

          <view class="recent-all" @tap="openHistory">
            <text>查看全部历史</text>
            <view class="recent-all__arrow"></view>
          </view>
        </view>
      </view>
    </scroll-view>

    <view class="bottom-bar">
      <view
        class="generate-btn"
        :class="{ 'generate-btn--disabled': !canGenerate, 'generate-btn--loading': isGenerating }"
        @tap="generateMindmap"
      >
        <image class="generate-icon" src="/static/icons/diagram/spark-white.svg" mode="aspectFit" />
        <text>{{ generateButtonText }}</text>
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import NavBar from '@/components/nav-bar/nav-bar.vue'
import { getErrorMessage, getMindmapHistory, uploadMindmapFile } from '@/api/aiDiagram.js'
import ImportFileButton from '../components/ImportFileButton.vue'
import { previewUploadedDocument } from '../utils/filePreview.js'
import { extractMindmapCenterTopic } from '../utils/mindmapTopicExtractor.js'

const topic = ref('')
const centerTopic = ref('')
const centerTopicEdited = ref(false)
const selectedDepth = ref('auto')
const selectedStructure = ref('auto')
const selectedExpand = ref('standard')
const isGenerating = ref(false)
const isUploading = ref(false)
const uploadedFile = ref(null)
const recentItems = ref([])
const RESTORE_KEY = 'aiMindmapRestoreDraft'

const depthOptions = [
  { key: 'auto', label: '自动', desc: '智能推荐' },
  { key: '2', label: '2层', desc: '简洁' },
  { key: '3', label: '3层', desc: '适中' },
  { key: '4', label: '4层', desc: '详细' }
]

const structureOptions = [
  { key: 'auto', label: '自动', desc: '智能选择' },
  { key: 'knowledge', label: '知识梳理', desc: '提炼关键点' },
  { key: 'course', label: '课程体系', desc: '按模块组织' },
  { key: 'review', label: '复习提纲', desc: '重点回顾' },
  { key: 'project', label: '项目拆解', desc: '分解任务' }
]

const expandOptions = [
  { key: 'simple', label: '简洁', desc: '只保留核心' },
  { key: 'standard', label: '标准', desc: '平衡展示' },
  { key: 'detail', label: '详细', desc: '完整展开' }
]

const displayRecentItems = computed(() => recentItems.value.slice(0, 3))
const canGenerate = computed(() => Boolean(topic.value.trim() || uploadedFile.value))
const generateButtonText = computed(() => isGenerating.value ? '正在生成思维导图...' : 'AI 生成思维导图')

const uploadedFileType = computed(() => {
  const name = String(uploadedFile.value?.fileName || '')
  const ext = name.split('.').pop()?.toLowerCase() || ''
  if (ext === 'pdf') return 'pdf'
  if (['doc', 'docx'].includes(ext)) return 'word'
  if (['ppt', 'pptx'].includes(ext)) return 'ppt'
  return 'file'
})

const uploadedFileTypeLabel = computed(() => {
  const map = { pdf: 'PDF', word: 'DOC', ppt: 'PPT', file: 'FILE' }
  return map[uploadedFileType.value] || 'FILE'
})

const uploadedFileSizeText = computed(() => formatFileSize(uploadedFile.value?.size || uploadedFile.value?.fileSize))
const aiSuggestedCenterTopic = computed(() => {
  const status = String(uploadedFile.value?.centerTopicStatus || '').toUpperCase()
  if (status !== 'AI') return ''
  return normalizeCenterTopicText(uploadedFile.value?.centerTopic || uploadedFile.value?.aiCenterTopic || '')
})
const localSuggestedCenterTopic = computed(() => extractMindmapCenterTopic({
  userText: topic.value,
  fileName: uploadedFile.value?.fileName || '',
  text: uploadedFile.value?.text || ''
}))
const suggestedCenterTopic = computed(() => aiSuggestedCenterTopic.value || localSuggestedCenterTopic.value)

const openHistory = () => { uni.navigateTo({ url: '/subpackage_ai/diagramHistory/diagramHistory' }) }

function restoreUploadedFile(draft = {}) {
  const file = Array.isArray(draft.files) ? draft.files[0] : null
  const sourceFile = draft.sourceFile || file?.url || file?.sourceFile || ''
  const fileId = draft.fileId || file?.id || ''
  const fileName = file?.name || file?.fileName || (sourceFile ? '已导入文件' : '')
  const sourceText = draft.sourceText || file?.text || ''
  if (!sourceFile && !fileId && !fileName && !sourceText) return null
  return {
    ...file,
    fileId,
    fileName,
    sourceFile,
    text: sourceText,
    summary: draft.summary || draft.fileSummary || file?.summary || '',
    size: file?.size || 0,
    textLength: file?.textLength || 0,
    truncated: Boolean(file?.truncated),
    pageCount: file?.pageCount || 0,
    slideCount: file?.slideCount || 0,
    paragraphCount: file?.paragraphCount || 0
  }
}

function stripMindmapContent(value = '') {
  const text = String(value || '')
  const beforeFile = text.split(/文件解析内容[:：]/)[0]
  return beforeFile
    .replace(/建议中心主题[:：][^\n]*\n?/g, '')
    .replace(/用户输入要求[:：]/g, '')
    .trim()
}

function restoreFromHistoryDraft(options = {}) {
  if (String(options.restore || '') !== '1') return
  const draft = uni.getStorageSync(RESTORE_KEY) || {}
  uni.removeStorageSync(RESTORE_KEY)
  topic.value = draft.topic || stripMindmapContent(draft.content || draft.description || draft.preview || '')
  centerTopic.value = draft.centerTopic || draft.resolvedCenterTopic || ''
  centerTopicEdited.value = String(draft.centerTopicMode || draft.requestedCenterTopicMode || '').toUpperCase() === 'USER_DEFINED'
  const depthValue = String(draft.depth || draft.requestedDepth || 'auto').toLowerCase()
  selectedDepth.value = /^[234]$/.test(depthValue) ? depthValue : 'auto'
  const structureMap = { AUTO: 'auto', KNOWLEDGE: 'knowledge', COURSE: 'course', REVIEW: 'review', PROJECT: 'project' }
  selectedStructure.value = structureMap[String(draft.structure || draft.requestedStructure || 'AUTO').toUpperCase()] || 'auto'
  const detailMap = { SIMPLE: 'simple', STANDARD: 'standard', DETAILED: 'detail', DETAIL: 'detail' }
  selectedExpand.value = detailMap[String(draft.detail || draft.detailLevel || 'STANDARD').toUpperCase()] || 'standard'
  uploadedFile.value = restoreUploadedFile(draft)
  syncSuggestedCenterTopic()
}

const syncSuggestedCenterTopic = () => {
  if (centerTopicEdited.value) return
  centerTopic.value = suggestedCenterTopic.value
}

const currentCenterTopicMode = () => {
  return centerTopicEdited.value && centerTopic.value.trim() ? 'USER_DEFINED' : 'AUTO'
}

const onCenterTopicInput = (event) => {
  const value = String(event?.detail?.value ?? centerTopic.value ?? '').trim()
  centerTopicEdited.value = Boolean(value)
  if (!value) {
    syncSuggestedCenterTopic()
  }
}

const chooseDocumentFile = () => {
  const extensions = ['pdf', 'doc', 'docx', 'ppt', 'pptx']
  return new Promise((resolve, reject) => {
    if (typeof uni.chooseFile === 'function') {
      uni.chooseFile({
        count: 1,
        extension: extensions,
        success: (res) => resolve(res.tempFiles?.[0] || null),
        fail: reject
      })
      return
    }
    if (typeof uni.chooseMessageFile === 'function') {
      uni.chooseMessageFile({
        count: 1,
        type: 'file',
        extension: extensions,
        success: (res) => resolve(res.tempFiles?.[0] || null),
        fail: reject
      })
      return
    }
    reject(new Error('当前平台不支持文件选择'))
  })
}

const importDocument = async () => {
  if (isUploading.value) return
  try {
    const file = await chooseDocumentFile()
    const filePath = file?.path || file?.tempFilePath
    if (!filePath) return
    isUploading.value = true
    uni.showLoading({ title: '解析中...' })
    const result = await uploadMindmapFile(filePath, file.name || '')
    uploadedFile.value = {
      ...result,
      fileName: result?.fileName || file.name || '已导入文件',
      filePath,
      size: file.size || result?.size || result?.fileSize || 0
    }
    syncSuggestedCenterTopic()
    uni.showToast({ title: '文件解析完成', icon: 'none' })
  } catch (error) {
    uni.showToast({ title: getErrorMessage(error, '文件解析失败'), icon: 'none' })
  } finally {
    isUploading.value = false
    uni.hideLoading()
  }
}

const removeUploadedFile = () => {
  uploadedFile.value = null
  uni.showToast({ title: '已移除文件', icon: 'none' })
}

const previewUploadedFile = () => {
  previewUploadedDocument(uploadedFile.value)
}

const openRecent = (item) => {
  if (!item || item.id == null) return
  uni.navigateTo({
    url: `/subpackage_ai/mindmapViewer/mindmapViewer?id=${encodeURIComponent(item.id)}`
  })
}

const loadRecentItems = async () => {
  try {
    const list = await getMindmapHistory()
    const records = Array.isArray(list) ? list : []
    recentItems.value = records.map(item => ({
      id: item.id,
      title: item.title || '未命名思维导图',
      preview: item.preview || item.description || '',
      requestedDepth: item.requestedDepth || item.depth || '',
      resolvedDepth: item.resolvedDepth || '',
      requestedStructure: item.requestedStructure || item.structure || '',
      resolvedStructure: item.resolvedStructure || item.structureType || '',
      detailLevel: item.detailLevel || item.detail || '',
      createTime: item.createTime || item.createdAt || ''
    }))
  } catch (error) {
    recentItems.value = []
  }
}

const formatRecentTime = (timeStr = '') => {
  if (!timeStr) return ''
  const date = new Date(timeStr)
  if (Number.isNaN(date.getTime())) return timeStr
  const pad = (n) => String(n).padStart(2, '0')
  const now = new Date()
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime()
  const target = new Date(date.getFullYear(), date.getMonth(), date.getDate()).getTime()
  const time = `${pad(date.getHours())}:${pad(date.getMinutes())}`
  if (target === today) return `今天 ${time}`
  if (target === today - 86400000) return `昨天 ${time}`
  return `${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${time}`
}

const formatFileSize = (size = 0) => {
  const value = Number(size || 0)
  if (!value) return '已解析'
  if (value < 1024) return `${value} B`
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`
  return `${(value / 1024 / 1024).toFixed(2)} MB`
}

const formatCount = (value = 0) => {
  const number = Number(value || 0)
  return number > 9999 ? `${(number / 10000).toFixed(1)}万` : `${number}`
}

function normalizeCenterTopicText(value = '', maxLength = 10) {
  return String(value || '')
    .replace(/^(中心主题|主题|标题|摘要)[:：]/, '')
    .replace(/[\s"'“”‘’`·。！？；：，、,.!?;；()（）【】《》<>]+/g, '')
    .replace(/[\[\]]/g, '')
    .trim()
    .slice(0, maxLength)
}

const parsedMetaText = (file = {}) => {
  const parts = []
  if (Number(file.pageCount || 0) > 0) parts.push(`${file.pageCount}页`)
  if (Number(file.slideCount || 0) > 0) parts.push(`${file.slideCount}张幻灯片`)
  if (Number(file.paragraphCount || 0) > 0) parts.push(`${file.paragraphCount}段文本`)
  if (Number(file.textLength || 0) > 0) parts.push(`${formatCount(file.textLength)}字`)
  if (file.truncated) parts.push('已截取前12万字')
  return parts.join(' · ')
}

const recentMeta = (item = {}) => {
  const structureMap = {
    AUTO: '自动',
    KNOWLEDGE: '知识梳理',
    COURSE: '课程体系',
    REVIEW: '复习提纲',
    PROJECT: '项目拆解',
    auto: '自动',
    knowledge: '知识梳理',
    course: '课程体系',
    review: '复习提纲',
    project: '项目拆解'
  }
  const detailMap = {
    SIMPLE: '简洁',
    STANDARD: '标准',
    DETAILED: '详细',
    simple: '简洁',
    standard: '标准',
    detail: '详细',
    detailed: '详细'
  }
  const structureKey = item.resolvedStructure || item.requestedStructure || 'AUTO'
  const detailKey = item.detailLevel || 'STANDARD'
  const requestedDepth = String(item.requestedDepth || '').trim()
  const depthText = item.resolvedDepth
    ? `${item.resolvedDepth}层`
    : (/^[234]$/.test(requestedDepth) ? `${requestedDepth}层` : '自动')
  return `${structureMap[structureKey] || structureKey} · ${depthText} · ${detailMap[detailKey] || '标准'}`
}

const generateMindmap = async () => {
  const centerTopicMode = currentCenterTopicMode()
  const finalCenterTopic = centerTopic.value.trim() || suggestedCenterTopic.value
  const finalTopic = topic.value.trim() || finalCenterTopic || uploadedFile.value?.fileName || ''
  const sourceText = uploadedFile.value?.text || ''
  if (!canGenerate.value || (!finalTopic && !sourceText)) {
    uni.showToast({ title: '请输入内容或导入文件', icon: 'none' })
    return
  }
  if (isGenerating.value) return
  isGenerating.value = true
  uni.navigateTo({
    url: `/subpackage_ai/mindmapGenerating/mindmapGenerating?topic=${encodeURIComponent(finalTopic)}&centerTopic=${encodeURIComponent(finalCenterTopic)}&centerTopicMode=${encodeURIComponent(centerTopicMode)}&depth=${selectedDepth.value}&structure=${encodeURIComponent(selectedStructure.value)}&detail=${encodeURIComponent(selectedExpand.value)}&sourceText=${encodeURIComponent(sourceText)}&sourceFile=${encodeURIComponent(uploadedFile.value?.sourceFile || '')}&fileId=${encodeURIComponent(uploadedFile.value?.fileId || '')}`,
    fail: error => {
      isGenerating.value = false
      uni.showToast({ title: getErrorMessage(error, '生成页打开失败'), icon: 'none' })
    }
  })
}

onMounted(() => {
  loadRecentItems()
})

onLoad(restoreFromHistoryDraft)

watch(suggestedCenterTopic, syncSuggestedCenterTopic)

onShow(() => {
  isGenerating.value = false
})
</script>

<style lang="scss" scoped>
.page {
  height: 100vh;
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: #FAF9FC;
  color: #182033;
}

.nav-history-action {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 64rpx;
  height: 64rpx;
  border-radius: 999rpx;
  transition: background-color 0.18s ease, transform 0.12s ease;
}

.nav-history-action:active {
  background: rgba(24, 32, 51, 0.06);
  transform: scale(0.96);
}

.nav-history-icon {
  width: 34rpx;
  height: 34rpx;
  opacity: 0.95;
}

.content {
  flex: 1;
  min-height: 0;
  box-sizing: border-box;
}

.content-inner {
  width: 100%;
  box-sizing: border-box;
  padding: 24rpx 30rpx calc(168rpx + env(safe-area-inset-bottom));
}

.section-card {
  border: 1rpx solid rgba(229, 226, 240, 0.82);
  border-radius: 22rpx;
  background: #FFFFFF;
  box-shadow: 0 12rpx 34rpx rgba(31, 35, 68, 0.045);
  box-sizing: border-box;
}

.section-card + .section-card {
  margin-top: 22rpx;
}

.input-card {
  padding: 26rpx 30rpx 28rpx;
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 18rpx;
}

.card-head__title {
  display: flex;
  align-items: center;
  min-width: 0;
  color: #182033;
  font-size: 27rpx;
  font-weight: 800;
}

.title-dot {
  width: 18rpx;
  height: 18rpx;
  margin-right: 14rpx;
  border: 4rpx solid #EEE8FF;
  border-radius: 50%;
  background: #7C4DE8;
  box-sizing: border-box;
}

.char-count {
  flex-shrink: 0;
  color: #45536B;
  font-size: 22rpx;
  line-height: 1;
}

.prompt-input {
  width: 100%;
  height: 186rpx;
  padding: 22rpx 24rpx;
  border: 1rpx solid #DADDE8;
  border-radius: 12rpx;
  background: #FFFFFF;
  color: #2C364A;
  font-size: 25rpx;
  line-height: 1.55;
  box-sizing: border-box;
}

.prompt-placeholder {
  color: #A7ADBB;
  font-size: 25rpx;
  line-height: 1.55;
}

.input-tools {
  display: flex;
  align-items: center;
  gap: 22rpx;
  margin-top: 24rpx;
  min-width: 0;
}

.import-trigger {
  flex-shrink: 0;
}

.import-trigger :deep(.ifb-row) {
  gap: 0;
}

.import-trigger :deep(.ifb-btn) {
  height: 58rpx;
  padding: 0 24rpx;
  border-radius: 999rpx;
  background: #F1EAFF;
}

.import-trigger :deep(.ifb-btn.is-loading) {
  opacity: 0.72;
}

.import-trigger :deep(.ifb-icon) {
  width: 29rpx;
  height: 29rpx;
}

.import-trigger :deep(.ifb-text) {
  color: #7C4DE8;
  font-size: 25rpx;
  font-weight: 800;
}

.import-trigger :deep(.ifb-mark) {
  display: none;
}

.file-help {
  display: flex;
  align-items: center;
  min-width: 0;
  color: #6F798E;
}

.file-help__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  width: 30rpx;
  height: 30rpx;
  margin-right: 12rpx;
  border: 3rpx solid #748096;
  border-radius: 50%;
  color: #748096;
  font-size: 18rpx;
  font-weight: 800;
  line-height: 1;
  box-sizing: border-box;
}

.file-help__text {
  min-width: 0;
  overflow: hidden;
  color: #6F798E;
  font-size: 22rpx;
  line-height: 1.3;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-status-card {
  padding: 24rpx 28rpx 22rpx;
}

.file-status-main {
  display: flex;
  align-items: center;
}

.file-type-icon {
  position: relative;
  display: flex;
  align-items: flex-end;
  justify-content: center;
  flex-shrink: 0;
  width: 54rpx;
  height: 62rpx;
  padding-bottom: 9rpx;
  border-radius: 7rpx;
  background: #EF3B45;
  color: #FFFFFF;
  font-size: 17rpx;
  font-weight: 800;
  line-height: 1;
  box-sizing: border-box;
  overflow: hidden;
}

.file-type-icon--word {
  background: #3B6FF0;
}

.file-type-icon--ppt {
  background: #F0733D;
}

.file-type-icon--file {
  background: #7C8496;
}

.file-type-icon__fold {
  position: absolute;
  top: 0;
  right: 0;
  width: 16rpx;
  height: 16rpx;
  background: rgba(255, 255, 255, 0.8);
  clip-path: polygon(0 0, 100% 100%, 100% 0);
}

.file-status-info {
  display: flex;
  flex: 1;
  min-width: 0;
  margin-left: 22rpx;
  flex-direction: column;
}

.file-status-name {
  overflow: hidden;
  color: #182033;
  font-size: 27rpx;
  font-weight: 800;
  line-height: 1.35;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-status-size {
  margin-top: 5rpx;
  color: #536179;
  font-size: 22rpx;
  line-height: 1.25;
}

.file-status-actions {
  display: flex;
  flex-shrink: 0;
  gap: 18rpx;
  margin-left: 18rpx;
}

.file-action {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 66rpx;
  height: 66rpx;
  border: 1rpx solid #D9DDE8;
  border-radius: 12rpx;
  background: #FFFFFF;
  box-sizing: border-box;
}

.file-action:active {
  background: #F6F4FC;
}

.file-action__icon {
  width: 30rpx;
  height: 30rpx;
}

.file-success {
  display: flex;
  align-items: center;
  margin-top: 20rpx;
  color: #22B763;
  font-size: 22rpx;
  font-weight: 700;
  line-height: 1.35;
}

.file-success__icon {
  position: relative;
  flex-shrink: 0;
  width: 24rpx;
  height: 24rpx;
  margin-right: 12rpx;
  border-radius: 50%;
  background: #36C977;
}

.file-success__icon::after {
  content: "";
  position: absolute;
  left: 7rpx;
  top: 5rpx;
  width: 8rpx;
  height: 12rpx;
  border-right: 3rpx solid #FFFFFF;
  border-bottom: 3rpx solid #FFFFFF;
  transform: rotate(45deg);
}

.file-summary {
  margin-top: 18rpx;
  padding: 16rpx 18rpx;
  border: 1rpx solid rgba(124, 77, 232, 0.12);
  border-radius: 14rpx;
  background: #FAF8FF;
  box-sizing: border-box;
}

.file-summary__label {
  display: block;
  color: #7C4DE8;
  font-size: 21rpx;
  font-weight: 800;
  line-height: 1.25;
}

.file-summary__text {
  display: block;
  margin-top: 8rpx;
  color: #2C364A;
  font-size: 23rpx;
  line-height: 1.5;
}

.file-summary__meta {
  display: block;
  margin-top: 8rpx;
  color: #7C8496;
  font-size: 20rpx;
  line-height: 1.35;
}

.settings-card {
  position: relative;
  overflow: hidden;
}

.settings-content {
  padding: 30rpx 28rpx 28rpx;
}

.settings-title {
  display: flex;
  align-items: flex-start;
  margin-bottom: 34rpx;
}

.settings-title__icon {
  width: 43rpx;
  height: 43rpx;
  margin: 2rpx 16rpx 0 0;
  flex-shrink: 0;
}

.settings-title__text {
  display: flex;
  min-width: 0;
  flex-direction: column;
}

.settings-title__main {
  color: #7C4DE8;
  font-size: 30rpx;
  font-weight: 800;
  line-height: 1.2;
}

.settings-title__sub {
  margin-top: 11rpx;
  color: #7C8496;
  font-size: 22rpx;
  line-height: 1.35;
}

.field-group {
  margin-bottom: 32rpx;
}

.field-group--last {
  margin-bottom: 0;
}

.field-label {
  display: block;
  margin-bottom: 16rpx;
  color: #182033;
  font-size: 25rpx;
  font-weight: 800;
  line-height: 1.3;
}

.theme-input-wrap {
  display: flex;
  align-items: center;
  height: 60rpx;
  padding: 0 18rpx 0 22rpx;
  border: 1rpx solid #DADDE8;
  border-radius: 9rpx;
  background: #FFFFFF;
  box-sizing: border-box;
}

.theme-input {
  flex: 1;
  min-width: 0;
  height: 100%;
  color: #2C364A;
  font-size: 24rpx;
}

.theme-placeholder {
  color: #9CA3B4;
}

.theme-count {
  flex-shrink: 0;
  margin-left: 14rpx;
  color: #4D5B73;
  font-size: 19rpx;
}

.field-tip {
  display: block;
  margin-top: 14rpx;
  color: #7C8496;
  font-size: 21rpx;
  line-height: 1.45;
}

.choice-grid {
  display: grid;
  gap: 14rpx;
}

.choice-grid--depth,
.choice-grid--structure {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.choice-grid--expand {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.choice-card {
  display: flex;
  align-items: center;
  justify-content: center;
  min-width: 0;
  min-height: 74rpx;
  padding: 9rpx 6rpx;
  border: 1rpx solid #DDE1EA;
  border-radius: 10rpx;
  background: #FFFFFF;
  color: #182033;
  flex-direction: column;
  text-align: center;
  box-sizing: border-box;
}

.choice-card--active {
  border: 3rpx solid #7C4DE8;
  background: #F7F1FF;
  color: #7C4DE8;
  box-shadow: 0 8rpx 18rpx rgba(124, 77, 232, 0.08);
}

.choice-card__title {
  max-width: 100%;
  overflow: hidden;
  color: inherit;
  font-size: 24rpx;
  font-weight: 800;
  line-height: 1.25;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.choice-card__desc {
  max-width: 100%;
  overflow: hidden;
  margin-top: 5rpx;
  color: #68748A;
  font-size: 19rpx;
  font-weight: 600;
  line-height: 1.25;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.choice-card--active .choice-card__desc {
  color: #7C4DE8;
}

.choice-grid--structure .choice-card {
  min-height: 74rpx;
}

.recent-card {
  padding: 22rpx 0 18rpx;
  overflow: hidden;
}

.recent-head {
  display: flex;
  align-items: center;
  padding: 0 24rpx 20rpx;
  color: #182033;
  font-size: 27rpx;
  font-weight: 800;
}

.recent-head__icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 34rpx;
  height: 34rpx;
  margin-right: 14rpx;
  border-radius: 50%;
  background: #F0E8FF;
}

.recent-head__icon image {
  width: 23rpx;
  height: 23rpx;
}

.recent-list {
  border-top: 1rpx solid #EEF0F6;
}

.recent-item {
  display: flex;
  align-items: center;
  height: 82rpx;
  padding: 0 22rpx;
  border-bottom: 1rpx solid #EEF0F6;
  box-sizing: border-box;
}

.recent-item:active {
  background: #F8F7FC;
}

.recent-node-icon {
  position: relative;
  flex-shrink: 0;
  width: 54rpx;
  height: 54rpx;
  margin-right: 18rpx;
  border-radius: 12rpx;
  background: #F2ECFF;
  color: #7C4DE8;
}

.recent-node-icon--1 {
  background: #EAF8F0;
  color: #1FB36C;
}

.recent-node-icon--2 {
  background: #ECF4FF;
  color: #4387F4;
}

.recent-node-svg {
  position: absolute;
  left: 12rpx;
  top: 11rpx;
  width: 30rpx;
  height: 32rpx;
}

.recent-info {
  display: flex;
  flex: 1;
  min-width: 0;
  flex-direction: column;
}

.recent-name {
  overflow: hidden;
  color: #182033;
  font-size: 24rpx;
  font-weight: 800;
  line-height: 1.25;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.recent-meta {
  overflow: hidden;
  margin-top: 7rpx;
  color: #6D778D;
  font-size: 20rpx;
  line-height: 1.25;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.recent-time {
  flex-shrink: 0;
  margin-left: 16rpx;
  color: #536179;
  font-size: 20rpx;
  line-height: 1;
  white-space: nowrap;
}

.recent-more {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  width: 26rpx;
  height: 44rpx;
  margin-left: 10rpx;
  flex-direction: column;
  gap: 5rpx;
}

.recent-more text {
  width: 4rpx;
  height: 4rpx;
  border-radius: 50%;
  background: #52617B;
}

.recent-empty {
  padding: 28rpx 24rpx 30rpx;
  border-top: 1rpx solid #EEF0F6;
  color: #8A93A6;
  font-size: 22rpx;
  text-align: center;
}

.recent-all {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 58rpx;
  margin: 16rpx 20rpx 0;
  border: 1rpx solid #E2E5ED;
  border-radius: 10rpx;
  color: #182033;
  font-size: 23rpx;
  font-weight: 800;
  box-sizing: border-box;
}

.recent-all:active {
  background: #F8F7FC;
}

.recent-all__arrow {
  width: 13rpx;
  height: 13rpx;
  margin-left: 12rpx;
  border-top: 3rpx solid currentColor;
  border-right: 3rpx solid currentColor;
  transform: rotate(45deg);
  box-sizing: border-box;
}

.bottom-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 20;
  padding: 0 30rpx calc(20rpx + env(safe-area-inset-bottom));
  background: transparent;
  box-sizing: border-box;
}

.generate-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 96rpx;
  border-radius: 13rpx;
  background: linear-gradient(180deg, #875BF0 0%, #7546DF 100%);
  color: #FFFFFF;
  font-size: 29rpx;
  font-weight: 800;
  box-shadow: 0 10rpx 22rpx rgba(124, 77, 232, 0.18);
  transition: opacity 0.18s ease, transform 0.12s ease;
}

.generate-btn:not(.generate-btn--disabled):active {
  transform: translateY(1rpx) scale(0.995);
}

.generate-btn--disabled {
  background: #D9D5E4;
  color: rgba(255, 255, 255, 0.82);
  box-shadow: none;
}

.generate-btn--loading {
  opacity: 0.88;
}

.generate-icon {
  width: 31rpx;
  height: 31rpx;
  margin-right: 13rpx;
}

.generate-btn--disabled .generate-icon {
  opacity: 0.76;
}
</style>
