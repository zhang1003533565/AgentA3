<template>
  <view class="architecture-page">
    <nav-bar title="AI 架构图" :showBack="true" :border="false">
      <template #right>
        <view class="history-action" @click="openHistory" aria-label="历史记录">
          <image class="history-icon" src="/static/icons/diagram/history.svg" mode="aspectFit" />
        </view>
      </template>
    </nav-bar>

    <scroll-view scroll-y class="architecture-scroll" :show-scrollbar="false">
      <view class="page-shell">
        <section class="content-card input-card">
          <view class="card-title-row input-title-row">
            <view class="input-title">描述您的架构需求</view>
          </view>

          <view class="textarea-wrap">
            <textarea
              v-model="description"
              class="architecture-textarea"
              maxlength="2000"
              placeholder="例如：生成一个校园二手交易系统的整体架构图，包含核心业务流程和数据流向..."
              placeholder-class="textarea-placeholder"
              :adjust-position="false"
              :show-confirm-bar="false"
            />
          </view>

          <view class="input-tools-row">
            <ImportFileButton
              class="import-trigger"
              :loading="isUploading"
              loading-text="导入中..."
              @click="importDocument"
            />
            <view class="file-tip">
              <view class="info-dot">i</view>
              <text>支持 PDF / Word / PPT（≤ 20MB）</text>
            </view>
            <text class="count-text">{{ description.length }}/2000</text>
          </view>
        </section>

        <section v-if="uploadedFile" class="file-status-card">
          <view class="file-main">
            <view class="file-type-badge" :class="fileTypeClass">{{ fileTypeLabel }}</view>
            <view class="file-copy">
              <view class="file-name">{{ uploadedFile.fileName || uploadedFile.name || '已导入文件' }}</view>
              <view class="file-size">{{ fileSizeText }}</view>
            </view>
          </view>
          <view class="file-actions">
            <button class="file-icon-button" hover-class="none" @click="previewFile" aria-label="预览">
              <image class="file-action-icon" src="/static/icons/diagram/eye-lucide.svg" mode="aspectFit" />
            </button>
            <button class="file-icon-button" hover-class="none" @click="removeFile" aria-label="删除">
              <image class="file-action-icon" src="/static/icons/diagram/trash-2-lucide.svg" mode="aspectFit" />
            </button>
          </view>
          <view class="upload-success-row">
            <view class="success-check">✓</view>
            <text>文件上传成功，可继续输入补充要求</text>
          </view>
        </section>

        <section class="content-card settings-card">
          <view class="settings-header">
            <view class="settings-icon-wrap">
              <image class="settings-icon" src="/static/icons/diagram/settings-blue.svg" mode="aspectFit" />
            </view>
            <view>
              <view class="settings-title">架构生成设置</view>
              <view class="settings-subtitle">设置架构图的层次、重点内容与表达方式</view>
            </view>
          </view>

          <view class="setting-block compact-block">
            <view class="setting-label">系统类型</view>
            <view class="system-type-row">
              <button
                v-for="item in systemTypeOptions"
                :key="item.key"
                class="system-pill"
                :class="{ active: selectedSystemType === item.key }"
                hover-class="none"
                @click="selectedSystemType = item.key"
              >
                {{ item.label }}
              </button>
            </view>
          </view>

          <view class="setting-block">
            <view class="setting-label">架构层级（多选）</view>
            <button
              class="auto-layer-card"
              :class="{ active: autoArchitectureLayers }"
              hover-class="none"
              @click="selectAutoLayers"
            >
              <view class="auto-layer-left">
                <image class="option-icon" src="/static/icons/diagram/settings-spark.svg" mode="aspectFit" />
                <view>
                  <view class="option-title">
                    AI 自动分析
                    <text class="recommend-tag">推荐</text>
                  </view>
                  <view class="option-desc">根据需求智能判断架构层级</view>
                </view>
              </view>
              <view class="radio-mark" :class="{ checked: autoArchitectureLayers }"></view>
            </button>

            <view class="layer-list">
              <button
                v-for="item in architectureLayerOptions"
                :key="item.key"
                class="layer-option"
                :class="{ checked: isLayerChecked(item.key), disabled: autoArchitectureLayers }"
                hover-class="none"
                @click="toggleArchitectureLayer(item.key)"
              >
                <image class="option-icon" :src="item.icon" mode="aspectFit" />
                <view class="layer-text">
                  <view class="option-title">{{ item.label }}</view>
                  <view class="option-desc">{{ item.desc }}</view>
                </view>
                <view class="checkbox-mark" :class="{ checked: isLayerChecked(item.key) }"></view>
              </button>
            </view>
          </view>

          <view class="setting-block">
            <view class="section-heading">
              <image class="heading-icon" src="/static/icons/diagram/ai-pen-blue.svg" mode="aspectFit" />
              <text>重点展示</text>
            </view>
            <view class="focus-grid">
              <button
                v-for="item in focusOptions"
                :key="item.key"
                class="focus-option"
                :class="{ checked: isFocusChecked(item.key) }"
                hover-class="none"
                @click="toggleFocus(item.key)"
              >
                <view class="checkbox-tile" :class="{ checked: isFocusChecked(item.key) }">✓</view>
                <text>{{ item.label }}</text>
              </button>
            </view>
          </view>

          <view class="setting-block relation-block">
            <view class="section-heading">
              <image class="heading-icon" src="/static/icons/diagram/layer.svg" mode="aspectFit" />
              <text>关系表达</text>
            </view>
            <view class="relation-list">
              <button
                v-for="item in relationOptions"
                :key="item.key"
                class="relation-option"
                :class="{ active: selectedRelation === item.key }"
                hover-class="none"
                @click="selectedRelation = item.key"
              >
                <view class="relation-radio" :class="{ checked: selectedRelation === item.key }"></view>
                <view class="relation-copy">
                  <view class="option-title">
                    {{ item.label }}
                    <text v-if="item.badge" class="recommend-tag">{{ item.badge }}</text>
                  </view>
                  <view class="option-desc">{{ item.desc }}</view>
                </view>
                <view class="relation-right-dot" :class="{ checked: selectedRelation === item.key }"></view>
              </button>
            </view>
          </view>
        </section>

        <section class="content-card recent-card">
          <view class="recent-title-row">
            <image class="recent-title-icon" src="/static/icons/diagram/app-grid.svg" mode="aspectFit" />
            <text>最近生成</text>
          </view>
          <view v-if="recentItems.length" class="recent-list">
            <view
              v-for="item in recentItems.slice(0, 3)"
              :key="item.id"
              class="recent-item"
              @click="openRecent(item)"
            >
              <view class="recent-icon-seat">
                <image class="recent-item-icon" src="/static/icons/diagram/app-grid.svg" mode="aspectFit" />
              </view>
              <view class="recent-copy">
                <view class="recent-title">{{ item.title || '未命名架构图' }}</view>
                <view class="recent-meta">{{ recentMeta(item) }}</view>
              </view>
              <view class="recent-time">{{ formatTime(item.createTime || item.createdAt) }}</view>
              <view class="more-dots">⋮</view>
            </view>
          </view>
          <view v-else class="recent-empty">暂无架构图历史</view>
          <button class="view-all-button" hover-class="none" @click="openHistory">查看全部历史 <text>›</text></button>
        </section>
      </view>
    </scroll-view>

    <view class="bottom-generate">
      <view class="bottom-hint-row">
        <view class="hint-item">
          <image class="hint-icon" src="/static/icons/diagram/history.svg" mode="aspectFit" />
          <text>无内容时按钮置灰</text>
        </view>
        <text class="hint-arrow">→</text>
        <view class="hint-item">
          <image class="hint-icon" src="/static/icons/diagram/spark-blue.svg" mode="aspectFit" />
          <text>生成中显示进度</text>
        </view>
        <text class="hint-arrow">→</text>
        <view class="hint-item">
          <image class="hint-icon" src="/static/icons/diagram/app-grid.svg" mode="aspectFit" />
          <text>完成后跳转结果页</text>
        </view>
      </view>
      <button
        class="generate-button"
        :class="{ disabled: !canGenerate || isGenerating }"
        :disabled="!canGenerate || isGenerating"
        hover-class="none"
        @click="generateArchitecture"
      >
        <image class="button-spark" src="/static/icons/diagram/spark-white.svg" mode="aspectFit" />
        <text>{{ isGenerating ? '正在生成架构图...' : 'AI 生成架构图' }}</text>
      </button>
    </view>
  </view>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue'
import { onShow } from '@dcloudio/uni-app'
import NavBar from '@/components/nav-bar/nav-bar.vue'
import { BASE_URL } from '@/utils/config.js'
import {
  buildArchitecturePayload,
  getArchitectureHistory,
  uploadArchitectureFile,
} from '@/api/architecture.js'
import { getErrorMessage } from '@/api/aiDiagram.js'
import ImportFileButton from '../components/ImportFileButton.vue'

const description = ref('')
const selectedSystemType = ref('web')
const autoArchitectureLayers = ref(true)
const selectedArchitectureLayers = ref([])
const selectedFocusContents = ref(['frontend', 'backend', 'storage'])
const selectedRelation = ref('auto')
const isGenerating = ref(false)
const isUploading = ref(false)
const uploadedFile = ref(null)
const recentItems = ref([])

const systemTypeOptions = [
  { key: 'web', label: 'Web系统', value: 'WEB' },
  { key: 'app', label: 'APP系统', value: 'APP' },
  { key: 'mini', label: '小程序', value: 'MINI_PROGRAM' },
  { key: 'admin', label: '管理后台', value: 'ADMIN' },
]

const architectureLayerOptions = [
  {
    key: 'client',
    label: '用户层（Client/User）',
    desc: '面向最终用户的交互与展示层',
    value: 'CLIENT',
    icon: '/static/icons/diagram/user-line.svg',
  },
  {
    key: 'application',
    label: '应用层（Application）',
    desc: '业务功能与应用逻辑实现层',
    value: 'APPLICATION',
    icon: '/static/icons/diagram/app-grid.svg',
  },
  {
    key: 'service',
    label: '服务层（Service/Core）',
    desc: '核心服务、接口与业务处理层',
    value: 'SERVICE',
    icon: '/static/icons/diagram/server.svg',
  },
  {
    key: 'data',
    label: '数据层（Data Storage）',
    desc: '数据存储、缓存与持久化层',
    value: 'DATA',
    icon: '/static/icons/diagram/database.svg',
  },
]

const focusOptions = [
  { key: 'frontend', label: '前端模块', value: 'FRONTEND' },
  { key: 'backend', label: '后端服务', value: 'BACKEND' },
  { key: 'storage', label: '数据存储', value: 'DATABASE' },
  { key: 'thirdParty', label: '第三方服务', value: 'THIRD_PARTY' },
]

const relationOptions = [
  {
    key: 'auto',
    label: '自动分析',
    badge: 'AUTO',
    value: 'AUTO',
    desc: 'AI 将标注并展示合适的表达方式',
  },
  {
    key: 'module',
    label: '模块关系',
    value: 'MODULE',
    desc: '展示组件间的层级与连接关系',
  },
  {
    key: 'data',
    label: '数据流向',
    value: 'DATA_FLOW',
    desc: '着重展示信息的传递与存储路径',
  },
  {
    key: 'call',
    label: '调用关系',
    value: 'CALL',
    desc: '体现服务或模块之间的调用依赖',
  },
]

const canGenerate = computed(() => Boolean(description.value.trim() || uploadedFile.value))

const fileTypeLabel = computed(() => {
  const name = (uploadedFile.value?.fileName || uploadedFile.value?.name || '').toLowerCase()
  if (name.endsWith('.pdf')) return 'PDF'
  if (name.endsWith('.doc') || name.endsWith('.docx')) return 'DOC'
  if (name.endsWith('.ppt') || name.endsWith('.pptx')) return 'PPT'
  return 'FILE'
})

const fileTypeClass = computed(() => `type-${fileTypeLabel.value.toLowerCase()}`)

const fileSizeText = computed(() => {
  const size = uploadedFile.value?.size || uploadedFile.value?.fileSize || 0
  if (!size) return ''
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(2)} MB`
})

onMounted(loadRecentItems)
onShow(() => {
  isGenerating.value = false
})

function openHistory() {
  uni.navigateTo({
    url: '/subpackage_ai/diagramHistory/diagramHistory?type=architecture&tab=arch',
  })
}

function openRecent(item) {
  if (!item?.id) return
  uni.navigateTo({
    url: `/subpackage_ai/architecturePreview/architecturePreview?id=${item.id}`,
  })
}

async function loadRecentItems() {
  try {
    const result = await getArchitectureHistory({ page: 1, size: 3 })
    const list = result?.records || result?.list || result || []
    recentItems.value = list.slice(0, 3)
  } catch (error) {
    recentItems.value = []
    console.warn('load architecture history failed', error)
  }
}

function chooseDocumentFile() {
  const extensions = ['pdf', 'doc', 'docx', 'ppt', 'pptx']
  return new Promise((resolve, reject) => {
    if (typeof uni.chooseFile === 'function') {
      uni.chooseFile({
        count: 1,
        extension: extensions,
        success: (res) => resolve(res.tempFiles?.[0] || null),
        fail: reject,
      })
      return
    }
    if (typeof uni.chooseMessageFile === 'function') {
      uni.chooseMessageFile({
        count: 1,
        type: 'file',
        extension: extensions,
        success: (res) => resolve(res.tempFiles?.[0] || null),
        fail: reject,
      })
      return
    }
    reject(new Error('当前平台不支持文件选择'))
  })
}

function selectAutoLayers() {
  autoArchitectureLayers.value = true
  selectedArchitectureLayers.value = []
}

function isLayerChecked(key) {
  return selectedArchitectureLayers.value.includes(key)
}

function toggleArchitectureLayer(key) {
  autoArchitectureLayers.value = false
  if (isLayerChecked(key)) {
    selectedArchitectureLayers.value = selectedArchitectureLayers.value.filter((item) => item !== key)
  } else {
    selectedArchitectureLayers.value = [...selectedArchitectureLayers.value, key]
  }
}

function isFocusChecked(key) {
  return selectedFocusContents.value.includes(key)
}

function toggleFocus(key) {
  if (isFocusChecked(key)) {
    selectedFocusContents.value = selectedFocusContents.value.filter((item) => item !== key)
  } else {
    selectedFocusContents.value = [...selectedFocusContents.value, key]
  }
}

async function importDocument() {
  if (isUploading.value) return

  try {
    const file = await chooseDocumentFile()
    const filePath = file?.path || file?.tempFilePath
    if (!filePath) return
    isUploading.value = true
    uni.showLoading({ title: '解析中...' })
    const result = await uploadArchitectureFile(filePath, file.name || '')
    uploadedFile.value = {
      ...result,
      fileName: result?.fileName || file.name || '已导入文件',
      filePath,
      size: file.size || result?.size || result?.fileSize || 0,
    }
    uni.showToast({
      title: '文件解析完成',
      icon: 'none',
    })
  } catch (error) {
    uni.showToast({
      title: getErrorMessage(error, '文件解析失败'),
      icon: 'none',
    })
  } finally {
    isUploading.value = false
    uni.hideLoading()
  }
}

function previewFile() {
  if (!uploadedFile.value) return
  const localPath = uploadedFile.value.filePath
  const remoteUrl = normalizePreviewUrl(
    uploadedFile.value.sourceFile ||
      uploadedFile.value.url ||
      uploadedFile.value.fileUrl ||
      uploadedFile.value.previewUrl,
  )
  if (localPath) {
    openLocalDocument(localPath)
    return
  }
  if (!remoteUrl) {
    uni.showToast({
      title: '暂无可预览文件',
      icon: 'none',
    })
    return
  }
  if (!/^https?:\/\//i.test(remoteUrl)) {
    openLocalDocument(remoteUrl)
    return
  }
  uni.showLoading({ title: '打开中...' })
  uni.downloadFile({
    url: remoteUrl,
    success: (res) => {
      if (res.statusCode >= 200 && res.statusCode < 300) {
        openLocalDocument(res.tempFilePath)
      } else {
        uni.showToast({
          title: '文件下载失败',
          icon: 'none',
        })
      }
    },
    fail: () => {
      uni.showToast({
        title: '文件下载失败',
        icon: 'none',
      })
    },
    complete: () => uni.hideLoading(),
  })
}

function openLocalDocument(filePath) {
  if (!filePath || typeof uni.openDocument !== 'function') {
    uni.showToast({
      title: '当前平台不支持预览',
      icon: 'none',
    })
    return
  }
  uni.openDocument({
    filePath,
    showMenu: true,
    fail: () => {
      uni.showToast({
        title: '文件暂时无法预览',
        icon: 'none',
      })
    },
  })
}

function removeFile() {
  uploadedFile.value = null
}

function normalizePreviewUrl(url) {
  const text = String(url || '').trim()
  if (!text) return ''
  if (/^https?:\/\//i.test(text) || text.startsWith('file://')) return text
  if (text.startsWith('/')) return BASE_URL ? `${BASE_URL}${text}` : text
  return BASE_URL ? `${BASE_URL}/${text}` : text
}

function getSelectedRelationValue() {
  return relationOptions.find((item) => item.key === selectedRelation.value)?.value || 'AUTO'
}

function getSelectedSystemTypeValue() {
  return systemTypeOptions.find((item) => item.key === selectedSystemType.value)?.value || 'WEB'
}

function getArchitectureLayers() {
  if (autoArchitectureLayers.value) return []
  return selectedArchitectureLayers.value
    .map((key) => architectureLayerOptions.find((item) => item.key === key)?.value)
    .filter(Boolean)
}

function getFocusContents() {
  return selectedFocusContents.value
    .map((key) => focusOptions.find((item) => item.key === key)?.value)
    .filter(Boolean)
}

function buildSourceFile() {
  if (!uploadedFile.value) return null
  return {
    id: uploadedFile.value.fileId || uploadedFile.value.id,
    name: uploadedFile.value.fileName || uploadedFile.value.name,
    size: uploadedFile.value.size || uploadedFile.value.fileSize,
    url: uploadedFile.value.sourceFile || uploadedFile.value.url || uploadedFile.value.fileUrl,
  }
}

function generateArchitecture() {
  if (!canGenerate.value || isGenerating.value) return

  isGenerating.value = true
  const relationMode = getSelectedRelationValue()
  const architectureLayers = getArchitectureLayers()
  const focusContents = getFocusContents()
  const sourceFile = buildSourceFile()
  const sourceDescription = description.value.trim() || sourceFile?.name || '根据导入文件生成架构图'

  const payload = buildArchitecturePayload({
    description: sourceDescription,
    content: description.value.trim(),
    files: sourceFile ? [sourceFile] : [],
    systemType: getSelectedSystemTypeValue(),
    architectureStyle: 'AUTO',
    autoArchitectureLayers: autoArchitectureLayers.value,
    architectureLayers,
    layers: architectureLayers,
    focusContents,
    displayContent: focusContents,
    relationMode,
    relationType: relationMode,
    sourceText: description.value.trim(),
    fileId: uploadedFile.value?.fileId || uploadedFile.value?.id || null,
    sourceFile,
  })

  uni.setStorageSync('aiArchitecturePendingPayload', payload)
  uni.navigateTo({
    url: '/subpackage_ai/architectureGenerating/architectureGenerating',
    fail: () => {
      isGenerating.value = false
    },
  })
}

function systemTypeLabel(value) {
  const normalized = String(value || '').toUpperCase()
  return (
    systemTypeOptions.find((item) => item.value === normalized || item.key === value)?.label ||
    'Web系统'
  )
}

function relationLabel(value) {
  const normalized = String(value || '').toUpperCase()
  return (
    relationOptions.find((item) => item.value === normalized || item.key === value)?.label ||
    '自动分析'
  )
}

function recentMeta(item) {
  const system = systemTypeLabel(item.systemType)
  const relation = relationLabel(item.resolvedRelationMode || item.requestedRelationMode || item.relationMode || item.relationType)
  return `${system} · ${relation}`
}

function formatTime(value) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  const now = new Date()
  const dayMs = 24 * 60 * 60 * 1000
  const startToday = new Date(now.getFullYear(), now.getMonth(), now.getDate()).getTime()
  const startDate = new Date(date.getFullYear(), date.getMonth(), date.getDate()).getTime()
  const time = `${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
  if (startDate === startToday) return `今天 ${time}`
  if (startDate === startToday - dayMs) return `昨天 ${time}`
  if (startDate === startToday - dayMs * 2) return `前天 ${time}`
  return `${date.getMonth() + 1}/${date.getDate()} ${time}`
}
</script>

<style scoped>
.architecture-page {
  min-height: 100vh;
  background: #f8f8fb;
  color: #182033;
  overflow: hidden;
}

.history-action {
  width: 72rpx;
  height: 72rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.history-icon {
  width: 40rpx;
  height: 40rpx;
}

.architecture-scroll {
  height: calc(100vh - 88rpx);
}

.page-shell {
  padding: 20rpx 24rpx 236rpx;
  box-sizing: border-box;
}

.content-card,
.file-status-card {
  background: #ffffff;
  border: 1rpx solid #e6e8f1;
  border-radius: 22rpx;
  box-shadow: 0 10rpx 28rpx rgba(20, 28, 48, 0.045);
}

.content-card {
  padding: 24rpx;
  margin-bottom: 18rpx;
}

.input-card {
  padding-bottom: 20rpx;
}

.card-title-row,
.settings-header,
.recent-title-row {
  display: flex;
  align-items: center;
}

.input-title {
  font-size: 31rpx;
  line-height: 42rpx;
  font-weight: 800;
  color: #172033;
}

.textarea-wrap {
  margin-top: 18rpx;
  border: 1rpx solid #d9deea;
  border-radius: 12rpx;
  background: #ffffff;
  overflow: hidden;
}

.architecture-textarea {
  width: 100%;
  height: 176rpx;
  padding: 22rpx 24rpx;
  box-sizing: border-box;
  color: #182033;
  font-size: 27rpx;
  line-height: 39rpx;
}

.textarea-placeholder {
  color: #8b95ab;
}

.input-tools-row {
  display: flex;
  align-items: center;
  gap: 20rpx;
  margin-top: 18rpx;
  min-height: 52rpx;
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
  background: #f1eaff;
}

.import-trigger :deep(.ifb-icon) {
  width: 29rpx;
  height: 29rpx;
}

.import-trigger :deep(.ifb-text) {
  color: #7c4de8;
  font-size: 25rpx;
  font-weight: 800;
}

.import-trigger :deep(.ifb-mark) {
  display: none;
}

.file-tip {
  display: flex;
  align-items: center;
  gap: 12rpx;
  color: #7d8799;
  font-size: 24rpx;
  line-height: 32rpx;
  white-space: nowrap;
  flex: 1;
  min-width: 0;
}

.info-dot {
  width: 30rpx;
  height: 30rpx;
  border-radius: 50%;
  border: 2rpx solid #8b95ab;
  color: #6f7890;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22rpx;
  font-weight: 700;
}

.count-text {
  color: #6f7890;
  font-size: 24rpx;
  flex-shrink: 0;
}

.file-status-card {
  position: relative;
  padding: 24rpx;
  margin-bottom: 18rpx;
  min-height: 118rpx;
}

.file-main {
  display: flex;
  align-items: center;
  padding-right: 172rpx;
}

.file-type-badge {
  width: 58rpx;
  height: 66rpx;
  border-radius: 7rpx;
  background: #f04747;
  color: #ffffff;
  font-size: 21rpx;
  font-weight: 800;
  display: flex;
  align-items: flex-end;
  justify-content: center;
  padding-bottom: 11rpx;
  box-sizing: border-box;
  flex-shrink: 0;
}

.file-type-badge.type-doc {
  background: #4c73e6;
}

.file-type-badge.type-ppt {
  background: #f0743d;
}

.file-type-badge.type-file {
  background: #6d7b97;
}

.file-copy {
  margin-left: 20rpx;
  min-width: 0;
}

.file-name {
  color: #182033;
  font-size: 29rpx;
  line-height: 38rpx;
  font-weight: 800;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.file-size {
  color: #53617a;
  font-size: 24rpx;
  line-height: 32rpx;
  margin-top: 4rpx;
}

.file-actions {
  position: absolute;
  top: 22rpx;
  right: 24rpx;
  display: flex;
  gap: 18rpx;
}

.file-icon-button {
  width: 68rpx;
  height: 68rpx;
  padding: 0;
  margin: 0;
  border-radius: 11rpx;
  border: 1rpx solid #d7deeb;
  background: #ffffff;
  display: flex;
  align-items: center;
  justify-content: center;
}

.file-icon-button::after {
  border: 0;
}

.file-action-icon {
  width: 34rpx;
  height: 34rpx;
}

.upload-success-row {
  display: flex;
  align-items: center;
  gap: 10rpx;
  margin-top: 18rpx;
  color: #22b66b;
  font-size: 24rpx;
  font-weight: 700;
}

.success-check {
  width: 28rpx;
  height: 28rpx;
  border-radius: 50%;
  background: #35c778;
  color: #ffffff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20rpx;
  line-height: 1;
}

.settings-card {
  padding: 26rpx 24rpx 22rpx;
}

.settings-header {
  gap: 16rpx;
  margin-bottom: 22rpx;
}

.settings-icon-wrap {
  width: 36rpx;
  height: 36rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.settings-icon {
  width: 34rpx;
  height: 34rpx;
}

.settings-title {
  font-size: 32rpx;
  line-height: 42rpx;
  color: #182033;
  font-weight: 800;
}

.settings-subtitle {
  margin-top: 2rpx;
  color: #7c8496;
  font-size: 24rpx;
  line-height: 32rpx;
}

.setting-block {
  margin-top: 26rpx;
}

.compact-block {
  margin-top: 12rpx;
}

.setting-label,
.section-heading {
  color: #182033;
  font-size: 26rpx;
  line-height: 36rpx;
  font-weight: 800;
  margin-bottom: 13rpx;
}

.section-heading {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.heading-icon {
  width: 30rpx;
  height: 30rpx;
}

.system-type-row {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 16rpx;
}

.system-pill {
  height: 56rpx;
  padding: 0 10rpx;
  border-radius: 30rpx;
  border: 1rpx solid #dbe1ed;
  background: #ffffff;
  color: #182033;
  font-size: 25rpx;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  white-space: nowrap;
}

.system-pill::after,
.auto-layer-card::after,
.layer-option::after,
.focus-option::after,
.relation-option::after,
.view-all-button::after,
.generate-button::after {
  border: 0;
}

.system-pill.active {
  border-color: transparent;
  background: linear-gradient(90deg, #3f63f4 0%, #7355ef 100%);
  color: #ffffff;
  box-shadow: 0 8rpx 18rpx rgba(86, 88, 239, 0.22);
}

.auto-layer-card,
.layer-option,
.relation-option {
  width: 100%;
  margin: 0;
  padding: 0;
  border-radius: 12rpx;
  border: 1rpx solid #dfe5ef;
  background: #ffffff;
  text-align: left;
  display: flex;
  align-items: center;
  box-sizing: border-box;
}

.auto-layer-card {
  height: 94rpx;
  padding: 0 22rpx;
  justify-content: space-between;
}

.auto-layer-card.active,
.relation-option.active {
  border-color: #5a59f4;
  background: linear-gradient(90deg, #ffffff 0%, #f4f2ff 100%);
}

.auto-layer-left,
.layer-text,
.relation-copy {
  min-width: 0;
}

.auto-layer-left {
  display: flex;
  align-items: center;
  gap: 20rpx;
}

.option-icon {
  width: 36rpx;
  height: 36rpx;
  flex-shrink: 0;
}

.option-title {
  color: #182033;
  font-size: 27rpx;
  line-height: 36rpx;
  font-weight: 800;
}

.recommend-tag {
  display: inline-flex;
  margin-left: 14rpx;
  color: #5a59f4;
  font-size: 20rpx;
  line-height: 28rpx;
  font-weight: 800;
}

.option-desc {
  color: #7c8496;
  font-size: 23rpx;
  line-height: 31rpx;
  margin-top: 1rpx;
}

.radio-mark,
.relation-right-dot,
.relation-radio {
  border-radius: 50%;
  border: 2rpx solid #c4ccda;
  flex-shrink: 0;
  box-sizing: border-box;
}

.radio-mark,
.relation-right-dot {
  width: 28rpx;
  height: 28rpx;
}

.radio-mark.checked,
.relation-right-dot.checked {
  border: 8rpx solid #5962f4;
}

.relation-radio {
  width: 28rpx;
  height: 28rpx;
  margin-right: 20rpx;
}

.relation-radio.checked {
  border-color: #5962f4;
  position: relative;
}

.relation-radio.checked::after {
  content: '';
  width: 12rpx;
  height: 12rpx;
  border-radius: 50%;
  background: #5962f4;
  position: absolute;
  left: 6rpx;
  top: 6rpx;
}

.layer-list {
  margin-top: 12rpx;
  display: flex;
  flex-direction: column;
  gap: 10rpx;
}

.layer-option {
  height: 76rpx;
  padding: 0 22rpx;
}

.layer-option.disabled {
  opacity: 0.9;
}

.layer-option.checked {
  border-color: #5a59f4;
  background: #f6f3ff;
}

.layer-text {
  flex: 1;
  margin-left: 20rpx;
}

.checkbox-mark {
  width: 28rpx;
  height: 28rpx;
  border-radius: 6rpx;
  border: 2rpx solid #c5cfdd;
  flex-shrink: 0;
  box-sizing: border-box;
}

.checkbox-mark.checked {
  border-color: #5d61f4;
  background: #5d61f4;
  position: relative;
}

.checkbox-mark.checked::after {
  content: '';
  position: absolute;
  left: 7rpx;
  top: 2rpx;
  width: 8rpx;
  height: 15rpx;
  border-right: 3rpx solid #ffffff;
  border-bottom: 3rpx solid #ffffff;
  transform: rotate(45deg);
}

.focus-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12rpx 16rpx;
}

.focus-option {
  height: 52rpx;
  margin: 0;
  padding: 0 20rpx;
  border-radius: 8rpx;
  border: 1rpx solid #dde4ef;
  background: #ffffff;
  color: #182033;
  display: flex;
  align-items: center;
  gap: 16rpx;
  font-size: 24rpx;
  font-weight: 700;
}

.focus-option.checked {
  background: #f8f7ff;
  border-color: #dde2f6;
}

.checkbox-tile {
  width: 30rpx;
  height: 30rpx;
  border-radius: 5rpx;
  border: 2rpx solid #c5cfdd;
  color: transparent;
  font-size: 20rpx;
  line-height: 26rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  box-sizing: border-box;
}

.checkbox-tile.checked {
  color: #ffffff;
  background: #5d61f4;
  border-color: #5d61f4;
}

.relation-block {
  padding-top: 18rpx;
  border-top: 1rpx solid #edf0f6;
}

.relation-list {
  display: flex;
  flex-direction: column;
  gap: 10rpx;
}

.relation-option {
  min-height: 72rpx;
  padding: 11rpx 22rpx;
}

.relation-copy {
  flex: 1;
}

.recent-card {
  padding: 0;
  overflow: hidden;
}

.recent-title-row {
  height: 68rpx;
  padding: 0 24rpx;
  gap: 14rpx;
  font-size: 29rpx;
  line-height: 38rpx;
  font-weight: 800;
  color: #182033;
  border-bottom: 1rpx solid #edf0f6;
}

.recent-title-icon {
  width: 34rpx;
  height: 34rpx;
}

.recent-list {
  padding: 0 24rpx;
}

.recent-item {
  min-height: 78rpx;
  display: flex;
  align-items: center;
  border-bottom: 1rpx solid #edf0f6;
}

.recent-item:last-child {
  border-bottom: 0;
}

.recent-icon-seat {
  width: 54rpx;
  height: 54rpx;
  border-radius: 12rpx;
  background: #f2ecff;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 16rpx;
  flex-shrink: 0;
}

.recent-item-icon {
  width: 34rpx;
  height: 34rpx;
}

.recent-copy {
  flex: 1;
  min-width: 0;
}

.recent-title {
  color: #182033;
  font-size: 26rpx;
  line-height: 34rpx;
  font-weight: 800;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.recent-meta {
  color: #647089;
  font-size: 22rpx;
  line-height: 30rpx;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.recent-time {
  color: #53617a;
  font-size: 22rpx;
  line-height: 30rpx;
  margin-left: 16rpx;
  flex-shrink: 0;
}

.more-dots {
  width: 32rpx;
  margin-left: 8rpx;
  color: #53617a;
  font-size: 32rpx;
  line-height: 32rpx;
  text-align: center;
  flex-shrink: 0;
}

.recent-empty {
  padding: 28rpx 24rpx 12rpx;
  color: #7c8496;
  font-size: 24rpx;
  text-align: center;
}

.view-all-button {
  width: calc(100% - 48rpx);
  height: 58rpx;
  margin: 12rpx 24rpx 18rpx;
  padding: 0;
  border-radius: 10rpx;
  border: 1rpx solid #dfe5ef;
  background: #ffffff;
  color: #182033;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8rpx;
  font-size: 25rpx;
  font-weight: 800;
}

.bottom-generate {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 20;
  padding: 14rpx 24rpx calc(18rpx + env(safe-area-inset-bottom));
  background: rgba(248, 248, 251, 0.94);
  box-shadow: 0 -8rpx 24rpx rgba(18, 28, 48, 0.04);
  box-sizing: border-box;
}

.bottom-hint-row {
  height: 50rpx;
  margin-bottom: 12rpx;
  border-radius: 12rpx;
  background: rgba(255, 255, 255, 0.7);
  display: grid;
  grid-template-columns: 1fr 30rpx 1fr 30rpx 1fr;
  align-items: center;
  color: #6f7890;
  font-size: 20rpx;
}

.hint-item {
  min-width: 0;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8rpx;
}

.hint-item text {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.hint-icon {
  width: 25rpx;
  height: 25rpx;
  flex-shrink: 0;
}

.hint-arrow {
  color: #9ba5b7;
  text-align: center;
}

.generate-button {
  width: 100%;
  height: 64rpx;
  margin: 0;
  padding: 0;
  border: 0;
  border-radius: 13rpx;
  background: linear-gradient(90deg, #3f63f4 0%, #7251ee 100%);
  color: #ffffff;
  font-size: 29rpx;
  font-weight: 800;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12rpx;
  box-shadow: 0 10rpx 24rpx rgba(92, 86, 239, 0.25);
}

.generate-button.disabled {
  background: #cbd2df;
  box-shadow: none;
  color: #ffffff;
}

.button-spark {
  width: 30rpx;
  height: 30rpx;
}
</style>
