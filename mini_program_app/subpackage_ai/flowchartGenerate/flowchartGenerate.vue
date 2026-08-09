<template>
  <view class="page">
    <nav-bar title="AI 流程图" :showBack="true" :border="false">
      <template #right>
        <view class="nav-history-action" @tap="openHistory">
          <image class="nav-history-icon" src="/static/icons/diagram/history.svg" mode="aspectFit" />
        </view>
      </template>
    </nav-bar>

    <scroll-view class="content" scroll-y :show-scrollbar="false">
      <view class="content-inner">
        <view class="input-card">
          <view class="input-head">
            <text class="input-title">描述您的流程需求</text>
            <text class="char-count">{{ flowDescription.length }}/2000</text>
          </view>
          <textarea
            class="prompt-input"
            v-model="flowDescription"
            placeholder="例如：生成请假申请流程，包含员工提交、主管审批、人事备案三个环节。如果主管拒绝，则返回修改。"
            placeholder-class="prompt-placeholder"
            :maxlength="2000"
          />
          <view class="input-tools">
            <ImportFileButton class="import-trigger" :loading="isUploading" @click="importDocument" />
            <text class="file-help">支持 PDF / Word / PPT（≤20MB）</text>
          </view>
        </view>

        <view v-if="uploadedDocument" class="file-status-card">
          <view class="file-status-main">
            <view class="file-type-icon" :class="`file-type-icon--${uploadedFileType}`">
              <view class="file-type-icon__fold"></view>
              <text>{{ uploadedFileTypeLabel }}</text>
            </view>
            <view class="file-status-info">
              <text class="file-status-name">{{ uploadedDocument.fileName || '已导入文件' }}</text>
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
        </view>

        <view class="settings-panel">
          <view class="setting-section">
            <view class="section-title">
              <image class="section-icon" src="/static/icons/diagram/app-grid.svg" mode="aspectFit" />
              <text>流程场景</text>
            </view>
            <view class="scene-row">
              <view
                v-for="item in sceneOptions"
                :key="item.key"
                class="scene-chip"
                :class="{ 'scene-chip--active': selectedScene === item.key }"
                @tap="selectedScene = item.key"
              >
                <text>{{ item.label }}</text>
              </view>
            </view>
          </view>

          <view class="setting-section">
            <view class="section-title">
              <image class="section-icon" src="/static/icons/diagram/layer.svg" mode="aspectFit" />
              <text>节点粒度</text>
            </view>
            <view class="segmented segmented--four">
              <view
                v-for="item in granularityOptions"
                :key="item.key"
                class="segmented-item"
                :class="{ 'segmented-item--active': selectedGranularity === item.key }"
                @tap="selectedGranularity = item.key"
              >
                <text class="segmented-title">{{ item.label }}</text>
                <text class="segmented-desc">{{ item.desc }}</text>
              </view>
            </view>
          </view>

          <view class="setting-section">
            <view class="section-title">
              <image class="section-icon" src="/static/icons/diagram/database.svg" mode="aspectFit" />
              <text>判断节点</text>
              <view class="section-info"><text>i</text></view>
            </view>
            <view class="segmented segmented--three">
              <view
                v-for="item in decisionOptions"
                :key="item.key"
                class="segmented-item"
                :class="{ 'segmented-item--active': selectedDecision === item.key }"
                @tap="selectedDecision = item.key"
              >
                <text class="segmented-title">{{ item.label }}</text>
                <text class="segmented-desc">{{ item.desc }}</text>
              </view>
            </view>
          </view>

          <view class="setting-section">
            <view class="section-title">
              <image class="section-icon" src="/static/icons/diagram/user-line.svg" mode="aspectFit" />
              <text>角色泳道</text>
            </view>
            <view class="segmented segmented--four">
              <view
                v-for="item in laneOptions"
                :key="item.key"
                class="segmented-item"
                :class="{ 'segmented-item--active': selectedLane === item.key }"
                @tap="selectedLane = item.key"
              >
                <text class="segmented-title">{{ item.label }}</text>
                <text class="segmented-desc">{{ item.desc }}</text>
              </view>
            </view>
          </view>
        </view>

        <view class="recent-card">
          <view class="recent-head">
            <view class="recent-head__icon">
              <image src="/static/icons/diagram/flow-white.svg" mode="aspectFit" />
            </view>
            <text>最近生成</text>
          </view>

          <view v-if="displayRecentItems.length" class="recent-list">
            <view
              class="recent-item"
              v-for="item in displayRecentItems"
              :key="item.id"
              @tap="openRecent(item)"
            >
              <view class="recent-flow-icon">
                <image src="/static/icons/diagram/flow-white.svg" mode="aspectFit" />
              </view>
              <view class="recent-info">
                <text class="recent-name">{{ item.title || '未命名流程图' }}</text>
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
        @tap="generateFlowchart"
      >
        <image class="generate-icon" src="/static/icons/diagram/spark-white.svg" mode="aspectFit" />
        <text>{{ generateButtonText }}</text>
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import NavBar from '@/components/nav-bar/nav-bar.vue'
import {
  getErrorMessage,
  getFlowchartHistory,
  uploadFlowchartFile
} from '@/api/aiDiagram.js'
import { BASE_URL } from '@/utils/config.js'
import ImportFileButton from '../components/ImportFileButton.vue'

const flowDescription = ref('')
const selectedScene = ref('ADMIN')
const selectedGranularity = ref('AUTO')
const selectedDecision = ref('AUTO')
const selectedLane = ref('AUTO')
const uploadedDocument = ref(null)
const isUploading = ref(false)
const isGenerating = ref(false)
const recentItems = ref([])

const sceneOptions = [
  { key: 'ADMIN', label: '行政流程' },
  { key: 'BUSINESS', label: '业务流程' },
  { key: 'LEARNING', label: '学习流程' },
  { key: 'LIFE', label: '生活流程' }
]

const granularityOptions = [
  { key: 'AUTO', label: '自动', desc: '智能推荐' },
  { key: 'SIMPLE', label: '简略', desc: '仅核心步骤' },
  { key: 'STANDARD', label: '标准', desc: '主要步骤+说明' },
  { key: 'DETAILED', label: '详细', desc: '完整步骤与分支' }
]

const decisionOptions = [
  { key: 'AUTO', label: '自动判断', desc: 'AI 根据内容决定' },
  { key: 'FORCE', label: '强制包含', desc: '主动包含判断节点' },
  { key: 'NONE', label: '不使用', desc: '尽量生成线性流程' }
]

const laneOptions = [
  { key: 'AUTO', label: '自动', desc: '智能推荐' },
  { key: 'NONE', label: '不显示', desc: '普通流程图' },
  { key: 'ROLE', label: '按角色', desc: '按参与角色划分' },
  { key: 'DEPARTMENT', label: '按部门', desc: '按部门划分' }
]

const displayRecentItems = computed(() => recentItems.value.slice(0, 3))
const canGenerate = computed(() => Boolean(flowDescription.value.trim() || uploadedDocument.value))
const generateButtonText = computed(() => isGenerating.value ? '正在生成流程图...' : 'AI 生成流程图')

const uploadedFileType = computed(() => {
  const ext = String(uploadedDocument.value?.fileName || '').split('.').pop()?.toLowerCase() || ''
  if (ext === 'pdf') return 'pdf'
  if (['doc', 'docx'].includes(ext)) return 'word'
  if (['ppt', 'pptx'].includes(ext)) return 'ppt'
  return 'file'
})

const uploadedFileTypeLabel = computed(() => {
  const map = { pdf: 'PDF', word: 'DOC', ppt: 'PPT', file: 'FILE' }
  return map[uploadedFileType.value] || 'FILE'
})

const uploadedFileSizeText = computed(() => formatFileSize(uploadedDocument.value?.size || uploadedDocument.value?.fileSize))

const openHistory = () => {
  uni.navigateTo({ url: '/subpackage_ai/diagramHistory/diagramHistory?type=flowchart' })
}

const chooseDocumentFile = () => {
  const extensions = ['pdf', 'doc', 'docx', 'ppt', 'pptx']
  return new Promise((resolve, reject) => {
    if (typeof uni.chooseFile === 'function') {
      uni.chooseFile({ count: 1, extension: extensions, success: res => resolve(res.tempFiles?.[0] || null), fail: reject })
      return
    }
    if (typeof uni.chooseMessageFile === 'function') {
      uni.chooseMessageFile({ count: 1, type: 'file', extension: extensions, success: res => resolve(res.tempFiles?.[0] || null), fail: reject })
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
    const fileName = file?.name || ''
    const extension = fileName.includes('.') ? fileName.split('.').pop().toLowerCase() : ''
    if (!filePath || !['pdf', 'doc', 'docx', 'ppt', 'pptx'].includes(extension)) {
      uni.showToast({ title: '仅支持 PDF、Word、PPT', icon: 'none' })
      return
    }
    if (Number(file.size || 0) > 20 * 1024 * 1024) {
      uni.showToast({ title: '文件不能超过 20MB', icon: 'none' })
      return
    }
    isUploading.value = true
    uni.showLoading({ title: '解析中...' })
    const result = await uploadFlowchartFile(filePath, fileName)
    uploadedDocument.value = {
      ...result,
      fileName: result?.fileName || fileName || '已导入文件',
      filePath,
      size: file.size || result?.size || result?.fileSize || 0
    }
    uni.showToast({ title: '文件解析完成', icon: 'none' })
  } catch (error) {
    uni.showToast({ title: getErrorMessage(error, '文件解析失败'), icon: 'none' })
  } finally {
    isUploading.value = false
    uni.hideLoading()
  }
}

const removeUploadedFile = () => {
  uploadedDocument.value = null
  uni.showToast({ title: '已移除文件', icon: 'none' })
}

const normalizePreviewUrl = (value = '') => {
  const text = String(value || '').trim()
  if (!text) return ''
  if (/^https?:\/\//i.test(text) || text.startsWith('file://')) return text
  if (text.startsWith('/')) return BASE_URL ? `${BASE_URL}${text}` : text
  return BASE_URL ? `${BASE_URL}/${text}` : text
}

const openLocalDocument = (filePath) => {
  if (!filePath || typeof uni.openDocument !== 'function') {
    uni.showToast({ title: '当前平台不支持预览', icon: 'none' })
    return
  }
  uni.openDocument({
    filePath,
    showMenu: true,
    fail: () => uni.showToast({ title: '文件暂时无法预览', icon: 'none' })
  })
}

const previewUploadedFile = () => {
  const localPath = uploadedDocument.value?.filePath
  const remoteUrl = normalizePreviewUrl(uploadedDocument.value?.sourceFile)
  if (localPath) {
    openLocalDocument(localPath)
    return
  }
  if (!remoteUrl) {
    uni.showToast({ title: '暂无可预览文件', icon: 'none' })
    return
  }
  if (!/^https?:\/\//i.test(remoteUrl)) {
    openLocalDocument(remoteUrl)
    return
  }
  uni.showLoading({ title: '打开中...' })
  uni.downloadFile({
    url: remoteUrl,
    success: res => {
      if (res.statusCode >= 200 && res.statusCode < 300) openLocalDocument(res.tempFilePath)
      else uni.showToast({ title: '文件下载失败', icon: 'none' })
    },
    fail: () => uni.showToast({ title: '文件下载失败', icon: 'none' }),
    complete: () => uni.hideLoading()
  })
}

const loadRecentItems = async () => {
  try {
    const list = await getFlowchartHistory()
    const records = Array.isArray(list) ? list : []
    recentItems.value = records.map(item => ({
      id: item.id,
      title: item.title || '未命名流程图',
      description: item.description || '',
      sceneType: item.sceneType,
      nodeGranularity: item.nodeGranularity,
      requestedSwimlaneMode: item.requestedSwimlaneMode,
      resolvedSwimlaneMode: item.resolvedSwimlaneMode,
      createTime: item.createTime || item.createdAt || ''
    }))
  } catch (error) {
    recentItems.value = []
  }
}

const openRecent = (item) => {
  if (!item || item.id == null) return
  uni.navigateTo({
    url: `/subpackage_ai/flowchartViewer/flowchartViewer?id=${encodeURIComponent(item.id)}`
  })
}

const sceneLabel = (value = '') => {
  return sceneOptions.find(item => item.key === String(value).toUpperCase())?.label || '行政流程'
}

const granularityLabel = (value = '') => {
  const map = { AUTO: '自动', SIMPLE: '简略', STANDARD: '标准', DETAILED: '详细', DETAIL: '详细' }
  return map[String(value || '').toUpperCase()] || '标准'
}

const swimlaneLabel = (value = '') => {
  const map = { AUTO: '自动泳道', NONE: '不显示泳道', ROLE: '按角色泳道', DEPARTMENT: '按部门泳道' }
  return map[String(value || '').toUpperCase()] || '自动泳道'
}

const recentMeta = (item = {}) => {
  const lane = item.resolvedSwimlaneMode || item.requestedSwimlaneMode || 'AUTO'
  return `${sceneLabel(item.sceneType)} · ${granularityLabel(item.nodeGranularity)} · ${swimlaneLabel(lane)}`
}

const formatRecentTime = (timeStr = '') => {
  if (!timeStr) return ''
  const date = new Date(timeStr)
  if (Number.isNaN(date.getTime())) return timeStr
  const pad = n => String(n).padStart(2, '0')
  const now = new Date()
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime()
  const target = new Date(date.getFullYear(), date.getMonth(), date.getDate()).getTime()
  const time = `${pad(date.getHours())}:${pad(date.getMinutes())}`
  if (target === today) return `今天 ${time}`
  if (target === today - 86400000) return `昨天 ${time}`
  if (target === today - 86400000 * 2) return `前天 ${time}`
  return `${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${time}`
}

const formatFileSize = (size = 0) => {
  const value = Number(size || 0)
  if (!value) return '已解析'
  if (value < 1024) return `${value} B`
  if (value < 1024 * 1024) return `${(value / 1024).toFixed(1)} KB`
  return `${(value / 1024 / 1024).toFixed(2)} MB`
}

const buildPayload = () => {
  const text = flowDescription.value.trim()
  const fileName = uploadedDocument.value?.fileName || ''
  const description = text || (fileName ? `根据文件《${fileName}》生成流程图` : '')
  const fileRef = uploadedDocument.value ? {
    id: uploadedDocument.value.fileId || '',
    name: uploadedDocument.value.fileName || fileName,
    url: uploadedDocument.value.sourceFile || '',
    size: Number(uploadedDocument.value.size || 0)
  } : null
  return {
    content: text,
    description,
    files: fileRef ? [fileRef] : [],
    sceneType: selectedScene.value,
    processType: selectedScene.value,
    nodeGranularity: selectedGranularity.value,
    nodeLevel: selectedGranularity.value,
    decisionMode: selectedDecision.value,
    swimlaneMode: selectedLane.value,
    swimlane: selectedLane.value,
    diagramType: selectedLane.value === 'NONE' ? 'FLOWCHART' : 'AUTO',
    displayItems: ['STEP', 'ROLE', 'INPUT_OUTPUT', 'EXCEPTION', 'DATA'],
    sourceText: uploadedDocument.value?.text || '',
    sourceFile: uploadedDocument.value?.sourceFile || '',
    fileId: uploadedDocument.value?.fileId || ''
  }
}

const generateFlowchart = () => {
  if (!canGenerate.value) {
    uni.showToast({ title: '请输入内容或导入文件', icon: 'none' })
    return
  }
  if (isGenerating.value) return
  isGenerating.value = true
  uni.setStorageSync('aiFlowchartPendingPayload', buildPayload())
  uni.navigateTo({
    url: '/subpackage_ai/flowchartGenerating/flowchartGenerating',
    fail: error => {
      isGenerating.value = false
      uni.showToast({ title: getErrorMessage(error, '生成页打开失败'), icon: 'none' })
    }
  })
}

onMounted(() => {
  loadRecentItems()
})

onShow(() => {
  isGenerating.value = false
  loadRecentItems()
})
</script>

<style lang="scss" scoped>
.page {
  height: 100vh;
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  background: #F7FAFE;
  color: #17233A;
}

.nav-history-action {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 64rpx;
  height: 64rpx;
  border-radius: 999rpx;
}

.nav-history-action:active {
  background: rgba(23, 35, 58, 0.06);
}

.nav-history-icon {
  width: 35rpx;
  height: 35rpx;
  opacity: 0.95;
}

.content {
  flex: 1;
  min-height: 0;
  box-sizing: border-box;
}

.content-inner {
  width: 100%;
  padding: 24rpx 30rpx calc(138rpx + env(safe-area-inset-bottom));
  box-sizing: border-box;
}

.input-card,
.file-status-card,
.settings-panel,
.recent-card {
  border: 1rpx solid #E4EAF3;
  border-radius: 24rpx;
  background: #FFFFFF;
  box-shadow: 0 12rpx 32rpx rgba(45, 76, 116, 0.055);
  box-sizing: border-box;
}

.input-card {
  padding: 24rpx 24rpx 26rpx;
}

.input-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20rpx;
  margin-bottom: 16rpx;
}

.input-title {
  flex: 1;
  color: #17233A;
  font-size: 28rpx;
  font-weight: 800;
  line-height: 1.25;
}

.char-count {
  flex-shrink: 0;
  color: #53657E;
  font-size: 22rpx;
  line-height: 1;
}

.prompt-input {
  display: block;
  width: 100%;
  height: 188rpx;
  padding: 20rpx 22rpx;
  border: 1rpx solid #DDE5F0;
  border-radius: 13rpx;
  background: #FFFFFF;
  color: #33445F;
  font-size: 25rpx;
  line-height: 1.62;
  box-sizing: border-box;
}

.prompt-placeholder {
  color: #8A98AD;
  font-size: 25rpx;
  line-height: 1.62;
}

.input-tools {
  display: flex;
  align-items: center;
  min-width: 0;
  gap: 18rpx;
  margin-top: 20rpx;
}

.import-trigger {
  flex-shrink: 0;
}

.import-trigger :deep(.ifb-row) {
  gap: 10rpx;
}

.import-trigger :deep(.ifb-btn) {
  height: 52rpx;
  padding: 0 24rpx;
  border: 1rpx solid #E4EEFF;
  border-radius: 999rpx;
  background: #EFF6FF;
  box-shadow: 0 4rpx 12rpx rgba(71, 126, 218, 0.06);
}

.import-trigger :deep(.ifb-icon) {
  width: 28rpx;
  height: 28rpx;
}

.import-trigger :deep(.ifb-text) {
  color: #2F78FF;
  font-size: 25rpx;
  font-weight: 800;
}

.import-trigger :deep(.ifb-mark) {
  width: 31rpx;
  height: 31rpx;
  border-color: #A8B5C8;
  background: #FFFFFF;
}

.import-trigger :deep(.ifb-mark-text) {
  color: #8B98AB;
}

.file-help {
  display: block;
  min-width: 0;
  flex: 1;
  color: #75849B;
  font-size: 23rpx;
  line-height: 1.35;
  white-space: nowrap;
}

.file-status-card {
  margin-top: 20rpx;
  padding: 22rpx 24rpx 20rpx;
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

.file-type-icon--word { background: #3B6FF0; }
.file-type-icon--ppt { background: #F0733D; }
.file-type-icon--file { background: #7C8496; }

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
  margin-left: 20rpx;
  flex-direction: column;
}

.file-status-name {
  overflow: hidden;
  color: #17233A;
  font-size: 27rpx;
  font-weight: 800;
  line-height: 1.35;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.file-status-size {
  margin-top: 4rpx;
  color: #586981;
  font-size: 22rpx;
}

.file-status-actions {
  display: flex;
  flex-shrink: 0;
  gap: 14rpx;
  margin-left: 16rpx;
}

.file-action {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 62rpx;
  height: 62rpx;
  border: 1rpx solid #D9E1EC;
  border-radius: 12rpx;
  background: #FFFFFF;
  box-sizing: border-box;
}

.file-action:active {
  background: #F3F7FD;
}

.file-action__icon {
  width: 29rpx;
  height: 29rpx;
}

.file-success {
  display: flex;
  align-items: center;
  margin-top: 18rpx;
  color: #20B465;
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
  background: #35C978;
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

.settings-panel {
  margin-top: 24rpx;
  padding: 24rpx 22rpx 28rpx;
}

.setting-section {
  margin-top: 26rpx;
}

.setting-section:first-child {
  margin-top: 0;
}

.section-title {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin-bottom: 17rpx;
  color: #17233A;
  font-size: 27rpx;
  font-weight: 800;
  line-height: 1.25;
}

.section-icon {
  width: 30rpx;
  height: 30rpx;
  opacity: 0.82;
}

.section-info {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 30rpx;
  height: 30rpx;
  border: 2rpx solid #AAB8CC;
  border-radius: 50%;
  color: #8A98AE;
  font-size: 20rpx;
  font-weight: 800;
  box-sizing: border-box;
}

.scene-row {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16rpx;
}

.scene-chip {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 62rpx;
  border: 1rpx solid #D9E2EE;
  border-radius: 999rpx;
  background: #FFFFFF;
  color: #17233A;
  font-size: 25rpx;
  font-weight: 700;
  box-sizing: border-box;
}

.scene-chip--active {
  border-color: #4B86F7;
  background: #4B86F7;
  color: #FFFFFF;
  box-shadow: 0 10rpx 22rpx rgba(75, 134, 247, 0.20);
}

.segmented {
  display: grid;
  overflow: hidden;
  border: 1rpx solid #DAE4F0;
  border-radius: 13rpx;
  background: #FFFFFF;
  box-shadow: 0 8rpx 20rpx rgba(45, 76, 116, 0.035);
}

.segmented--four {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.segmented--three {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.segmented-item {
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  min-width: 0;
  height: 86rpx;
  padding: 8rpx 6rpx;
  flex-direction: column;
  text-align: center;
  color: #17233A;
  box-sizing: border-box;
}

.segmented-item + .segmented-item::before {
  content: "";
  position: absolute;
  left: 0;
  top: 18rpx;
  bottom: 18rpx;
  width: 1rpx;
  background: #E4EAF2;
}

.segmented-item--active {
  z-index: 1;
  border: 2rpx solid #6EA0FF;
  border-radius: 12rpx;
  background: #F5F9FF;
  color: #2F78FF;
}

.segmented-item--active::before,
.segmented-item--active + .segmented-item::before {
  display: none;
}

.segmented-title {
  max-width: 100%;
  overflow: hidden;
  color: inherit;
  font-size: 24rpx;
  font-weight: 800;
  line-height: 1.25;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.segmented-desc {
  max-width: 100%;
  overflow: hidden;
  margin-top: 6rpx;
  color: #74839A;
  font-size: 20rpx;
  font-weight: 600;
  line-height: 1.25;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.segmented-item--active .segmented-desc {
  color: #2F78FF;
}

.recent-card {
  margin-top: 24rpx;
  padding: 22rpx 0 18rpx;
  overflow: hidden;
}

.recent-head {
  display: flex;
  align-items: center;
  padding: 0 24rpx 18rpx;
  color: #17233A;
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
  background: #EEF2FF;
}

.recent-head__icon image {
  width: 22rpx;
  height: 22rpx;
  filter: brightness(0) saturate(100%) invert(47%) sepia(91%) saturate(2364%) hue-rotate(221deg) brightness(102%) contrast(101%);
}

.recent-list {
  border-top: 1rpx solid #EEF2F7;
}

.recent-item {
  display: flex;
  align-items: center;
  height: 92rpx;
  padding: 0 22rpx;
  border-bottom: 1rpx solid #EEF2F7;
  box-sizing: border-box;
}

.recent-item:active {
  background: #F6FAFF;
}

.recent-flow-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  width: 58rpx;
  height: 58rpx;
  margin-right: 20rpx;
  border-radius: 12rpx;
  background: #EEF1FF;
}

.recent-flow-icon image {
  width: 32rpx;
  height: 32rpx;
  filter: brightness(0) saturate(100%) invert(47%) sepia(91%) saturate(2364%) hue-rotate(221deg) brightness(102%) contrast(101%);
}

.recent-info {
  display: flex;
  flex: 1;
  min-width: 0;
  flex-direction: column;
}

.recent-name {
  overflow: hidden;
  color: #17233A;
  font-size: 24rpx;
  font-weight: 800;
  line-height: 1.25;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.recent-meta {
  overflow: hidden;
  margin-top: 7rpx;
  color: #6F7F96;
  font-size: 20rpx;
  line-height: 1.25;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.recent-time {
  flex-shrink: 0;
  margin-left: 14rpx;
  color: #66758E;
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
  background: #5A6881;
}

.recent-empty {
  padding: 28rpx 24rpx 30rpx;
  border-top: 1rpx solid #EEF2F7;
  color: #8A98AE;
  font-size: 22rpx;
  text-align: center;
}

.recent-all {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 58rpx;
  margin: 16rpx 20rpx 0;
  border-radius: 10rpx;
  color: #2F78FF;
  font-size: 23rpx;
  font-weight: 800;
  box-sizing: border-box;
}

.recent-all:active {
  background: #F5F7FF;
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
  padding: 0 30rpx calc(16rpx + env(safe-area-inset-bottom));
  background: transparent;
  box-sizing: border-box;
}

.generate-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 96rpx;
  border-radius: 13rpx;
  background: #4B86F7;
  color: #FFFFFF;
  font-size: 29rpx;
  font-weight: 800;
  box-shadow: 0 12rpx 24rpx rgba(75, 134, 247, 0.22);
  transition: opacity 0.18s ease, transform 0.12s ease;
}

.generate-btn:not(.generate-btn--disabled):active {
  transform: translateY(1rpx) scale(0.995);
}

.generate-btn--disabled {
  background: #D9E0EB;
  color: rgba(255, 255, 255, 0.86);
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

</style>
