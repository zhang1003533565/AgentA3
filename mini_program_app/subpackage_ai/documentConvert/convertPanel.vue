<template>
  <view class="convert-panel">
    <!-- 文件选择 -->
    <view class="section-card">
      <view class="section-title">选择文件</view>
      <view class="file-picker" @tap="chooseFile">
        <template v-if="!selectedFile">
          <text class="picker-plus">+</text>
          <text class="picker-text">点击选择文件</text>
          <text class="picker-hint">支持 {{ acceptText }} 格式，不超过 25MB</text>
        </template>
        <template v-else>
          <text class="file-name">{{ selectedFile.name }}</text>
          <text class="file-size">{{ formatSize(selectedFile.size) }}</text>
          <text class="file-change">重新选择</text>
        </template>
      </view>
    </view>

    <!-- 开始转换 -->
    <view class="section-card action-card">
      <view
        class="convert-btn"
        :class="{ 'convert-btn--disabled': !canStart, 'convert-btn--loading': processing }"
        @tap="startConvert"
      >
        <text>{{ processing ? (STATUS_TEXT[taskStatus] || '处理中') : '开始转换' }}</text>
      </view>
    </view>

    <!-- 转换进度 -->
    <view class="section-card" v-if="showProgress">
      <view class="section-title">转换进度</view>
      <view class="progress-status">
        <text class="progress-text">{{ statusText }}</text>
        <text class="progress-percent">{{ progress }}%</text>
      </view>
      <view class="progress-track">
        <view class="progress-bar" :style="{ width: progress + '%' }"></view>
      </view>
      <view class="progress-message">{{ progressMessage }}</view>

      <!-- 成功结果 -->
      <view class="result-box" v-if="taskStatus === 'SUCCEEDED'">
        <view class="result-icon-wrap">
          <text class="result-icon">✓</text>
        </view>
        <view class="result-info">
          <text class="result-name">{{ result.fileName }}</text>
          <text class="result-size">{{ formatSize(result.fileSize) }}</text>
        </view>
        <view class="result-actions">
          <view class="result-action" @tap="openFile(taskId, result.fileName)">打开</view>
          <view class="result-action result-action--primary" @tap="saveFile(taskId, result.fileName)">下载</view>
        </view>
      </view>

      <!-- 失败 -->
      <view class="error-box" v-if="taskStatus === 'FAILED'">
        <text class="error-text">{{ errorMessage || '转换失败，请重试' }}</text>
        <view class="error-retry" @tap="resetAndRetry">重新转换</view>
      </view>
    </view>

    <!-- 历史记录 -->
    <view class="section-card">
      <view class="section-title-row">
        <text class="section-title">历史记录</text>
        <view class="history-batch-btn" @tap="batchDelete">批量删除</view>
      </view>
      <view v-if="historyList.length === 0" class="history-empty">
        <text>暂无转换记录</text>
      </view>
      <view v-else class="history-list">
        <view class="history-item" v-for="item in historyList" :key="item.taskId">
          <view
            class="history-check"
            :class="{ 'history-check--checked': isSelected(item.taskId) }"
            @tap.stop="toggleSelect(item.taskId)"
          >
            <text v-if="isSelected(item.taskId)" class="history-check-mark">✓</text>
          </view>
          <view class="history-main">
            <view class="history-line">
              <text class="history-name">{{ item.resultFileName || item.sourceFileName }}</text>
              <text
                v-if="item.status !== 'SUCCEEDED'"
                class="history-status"
                :class="'history-status--' + String(item.status || '').toLowerCase()"
              >
                {{ STATUS_TEXT[item.status] || item.status }}
              </text>
            </view>
            <text class="history-time">{{ formatTime(item.createTime) }}</text>
          </view>
          <view class="history-actions" v-if="item.status === 'SUCCEEDED'">
            <view class="result-action" @tap.stop="openFile(item.taskId, item.resultFileName)">打开</view>
            <view class="result-action result-action--primary" @tap.stop="saveFile(item.taskId, item.resultFileName)">下载</view>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { createTask, getTask, getHistory, downloadResult, deleteConvertTasks } from '@/api/documentConvert.js'

const props = defineProps({
  convertType: { type: String, required: true },
  typeLabel: { type: String, default: '' },
  acceptExtensions: { type: Array, default: () => [] }
})

const STATUS_TEXT = {
  QUEUED: '等待处理',
  RUNNING: '正在转换',
  SUCCEEDED: '转换完成',
  FAILED: '转换失败'
}

const selectedFile = ref(null)
const taskId = ref('')
const taskStatus = ref('')
const progress = ref(0)
const progressMessage = ref('')
const errorMessage = ref('')
const result = ref({ fileName: '', fileSize: 0 })
const historyList = ref([])
const selectedIds = ref([])
const processing = ref(false)

let pollTimer = null
let submitLocked = false
let pollingErrorCount = 0

const acceptText = computed(() => {
  return (props.acceptExtensions || []).map(item => String(item).replace(/^\./, '').toUpperCase()).join(' / ')
})

const canStart = computed(() => !!selectedFile.value && !processing.value)
const showProgress = computed(() => processing.value || ['SUCCEEDED', 'FAILED'].includes(taskStatus.value))
const statusText = computed(() => STATUS_TEXT[taskStatus.value] || '等待处理')

const chooseFile = () => {
  const choose = uni.chooseMessageFile || uni.chooseFile
  if (!choose) {
    uni.showToast({ title: '当前端不支持文件选择', icon: 'none' })
    return
  }
  const extension = (props.acceptExtensions || []).map(item => String(item).replace(/^\./, ''))
  choose({
    count: 1,
    type: 'file',
    extension,
    success: (res) => {
      const file = (res.tempFiles || [])[0]
      if (!file) return
      selectedFile.value = {
        path: file.path || file.tempFilePath || '',
        name: file.name || String(file.path || '').split(/[\\/]/).pop() || '已选择文件',
        size: file.size || 0
      }
      errorMessage.value = ''
      taskStatus.value = ''
      progress.value = 0
      progressMessage.value = ''
      result.value = { fileName: '', fileSize: 0 }
    }
  })
}

const startConvert = async () => {
  if (!selectedFile.value) {
    uni.showToast({ title: '请先选择文件', icon: 'none' })
    return
  }
  if (processing.value || submitLocked) return
  submitLocked = true

  taskId.value = ''
  taskStatus.value = 'QUEUED'
  progress.value = 0
  progressMessage.value = '等待处理'
  errorMessage.value = ''
  result.value = { fileName: '', fileSize: 0 }
  processing.value = true

  try {
    const res = await createTask(selectedFile.value, props.convertType)
    const data = res.data || {}
    if (!data.taskId) {
      throw new Error('创建任务失败')
    }
    taskId.value = data.taskId
    taskStatus.value = data.status || 'QUEUED'
    progress.value = data.progress || 0
    progressMessage.value = data.message || '等待处理'
    startPolling(data.taskId)
  } catch (error) {
    processing.value = false
    taskStatus.value = 'FAILED'
    errorMessage.value = error?.msg || error?.message || '创建任务失败'
  } finally {
    submitLocked = false
  }
}

const startPolling = (id) => {
  stopPolling()
  pollTimer = setInterval(async () => {
    try {
      const res = await getTask(id)
      pollingErrorCount = 0
      const data = res.data || {}
      taskStatus.value = data.status || taskStatus.value
      progress.value = data.progress || 0
      progressMessage.value = data.message || STATUS_TEXT[data.status] || ''

      if (data.status === 'SUCCEEDED') {
        stopPolling()
        processing.value = false
        progress.value = 100
        result.value = {
          fileName: data.resultFileName || '',
          fileSize: data.resultFileSize || 0
        }
        loadHistory()
      } else if (data.status === 'FAILED') {
        stopPolling()
        processing.value = false
        errorMessage.value = data.errorMessage || '转换失败'
      }
    } catch (error) {
      pollingErrorCount += 1
      if (pollingErrorCount >= 5) {
        stopPolling()
        processing.value = false
        uni.showToast({ title: '网络暂时异常，请稍后刷新查看任务状态', icon: 'none' })
      }
    }
  }, 1500)
}

const stopPolling = () => {
  if (pollTimer) {
    clearInterval(pollTimer)
    pollTimer = null
  }
}

const loadHistory = async () => {
  try {
    const res = await getHistory(props.convertType, 1, 20)
    historyList.value = (res.data && res.data.records) || []
    selectedIds.value = []
  } catch (error) {
    historyList.value = []
    selectedIds.value = []
  }
}

const isSelected = (taskId) => selectedIds.value.includes(taskId)

const toggleSelect = (taskId) => {
  const index = selectedIds.value.indexOf(taskId)
  if (index >= 0) {
    selectedIds.value.splice(index, 1)
  } else {
    selectedIds.value.push(taskId)
  }
}

const batchDelete = () => {
  if (selectedIds.value.length === 0) {
    uni.showToast({ title: '请选择要删除的记录', icon: 'none' })
    return
  }
  uni.showModal({
    title: '提示',
    content: '确定删除选中的转换记录吗？删除后无法恢复',
    success: async (res) => {
      if (!res.confirm) return
      try {
        await deleteConvertTasks(selectedIds.value)
        uni.showToast({ title: '删除成功', icon: 'success' })
        selectedIds.value = []
        loadHistory()
      } catch (error) {
        uni.showToast({ title: error?.msg || error?.message || '删除失败', icon: 'none' })
      }
    }
  })
}

/**
 * 打开文件：下载到临时目录后调用系统文件打开能力（类似微信打开文件）
 * 结果区与历史记录区共用，不区分转换类型。
 */
const openFile = async (taskId, fileName) => {
  if (!taskId) return
  uni.showLoading({ title: '打开中...' })
  try {
    const filePath = await downloadResult(taskId)
    // #ifdef H5
    uni.hideLoading()
    window.open(filePath, '_blank')
    // #endif
    // #ifndef H5
    uni.hideLoading()
    uni.openDocument({
      filePath,
      showMenu: true,
      fail: () => uni.showToast({ title: '文件打开失败，请使用下载保存', icon: 'none' })
    })
    // #endif
  } catch (error) {
    uni.hideLoading()
    uni.showToast({ title: error?.msg || error?.message || '打开失败', icon: 'none' })
  }
}

/**
 * 保存文件：真正保存到本地；环境不支持 saveFile 时退回打开，保持兼容。
 * 结果区与历史记录区共用，不区分转换类型。
 */
const saveFile = async (taskId, fileName) => {
  if (!taskId) return
  uni.showLoading({ title: '下载中...' })
  try {
    const filePath = await downloadResult(taskId)
    // #ifdef H5
    uni.hideLoading()
    const link = document.createElement('a')
    link.href = filePath
    link.download = fileName || 'convert-result'
    link.click()
    uni.showToast({ title: '已开始下载', icon: 'none' })
    // #endif
    // #ifndef H5
    uni.hideLoading()
    if (typeof uni.saveFile === 'function') {
      uni.saveFile({
        tempFilePath: filePath,
        success: () => uni.showToast({ title: '文件已保存', icon: 'none' }),
        fail: () => uni.showToast({ title: '保存失败，请使用打开后另存', icon: 'none' })
      })
    } else {
      uni.openDocument({
        filePath,
        showMenu: true,
        fail: () => uni.showToast({ title: '文件操作失败', icon: 'none' })
      })
    }
    // #endif
  } catch (error) {
    uni.hideLoading()
    uni.showToast({ title: error?.msg || error?.message || '下载失败', icon: 'none' })
  }
}

const resetAndRetry = () => {
  taskStatus.value = ''
  progress.value = 0
  progressMessage.value = ''
  errorMessage.value = ''
  result.value = { fileName: '', fileSize: 0 }
  taskId.value = ''
  processing.value = false
}

const formatSize = (bytes) => {
  if (bytes === null || bytes === undefined) return ''
  if (bytes < 1024) return bytes + ' B'
  if (bytes < 1024 * 1024) return (bytes / 1024).toFixed(1) + ' KB'
  return (bytes / 1024 / 1024).toFixed(2) + ' MB'
}

const formatTime = (time) => {
  if (!time) return ''
  return String(time).replace('T', ' ').slice(0, 19)
}

onMounted(() => {
  loadHistory()
})

onUnmounted(() => {
  stopPolling()
})
</script>

<style lang="scss" scoped>
.convert-panel {
  display: flex;
  flex-direction: column;
  gap: 24rpx;
}

.section-card {
  background: #FFFFFF;
  border-radius: 24rpx;
  border: 1rpx solid #EEEEEE;
  padding: 28rpx;
}

.section-title-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20rpx;
}

.section-title {
  font-size: 28rpx;
  font-weight: 600;
  color: #1D1D1F;
  margin-bottom: 20rpx;
}

.section-title-row .section-title {
  margin-bottom: 0;
}

.section-subtitle {
  font-size: 22rpx;
  color: #8E8E93;
}

.file-picker {
  border: 2rpx dashed #D5DAE1;
  border-radius: 20rpx;
  background: #FAFBFC;
  min-height: 200rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10rpx;
  padding: 24rpx;
}

.picker-plus {
  font-size: 56rpx;
  color: #5C7A99;
  line-height: 1;
  font-weight: 300;
}

.picker-text {
  font-size: 28rpx;
  color: #4A4A4A;
}

.picker-hint {
  font-size: 22rpx;
  color: #8E8E93;
}

.file-name {
  font-size: 28rpx;
  font-weight: 600;
  color: #1D1D1F;
  text-align: center;
  word-break: break-all;
}

.file-size {
  font-size: 22rpx;
  color: #8E8E93;
}

.file-change {
  font-size: 22rpx;
  color: #5C7A99;
  margin-top: 6rpx;
}

.action-card {
  padding: 24rpx;
}

.convert-btn {
  height: 92rpx;
  border-radius: 24rpx;
  background: #5C7A99;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #FFFFFF;
  font-size: 30rpx;
  font-weight: 600;
}

.convert-btn--disabled {
  background: #C9D2DC;
  pointer-events: none;
}

.convert-btn--loading {
  background: #7A93AD;
}

.progress-status {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16rpx;
}

.progress-text {
  font-size: 28rpx;
  font-weight: 600;
  color: #1D1D1F;
}

.progress-percent {
  font-size: 28rpx;
  font-weight: 600;
  color: #5C7A99;
}

.progress-track {
  height: 12rpx;
  border-radius: 999rpx;
  background: #EEF0F3;
  overflow: hidden;
}

.progress-bar {
  height: 100%;
  border-radius: 999rpx;
  background: #5C7A99;
  transition: width 0.3s ease;
}

.progress-message {
  margin-top: 16rpx;
  font-size: 24rpx;
  color: #8E8E93;
}

.result-box {
  margin-top: 24rpx;
  display: flex;
  align-items: center;
  gap: 20rpx;
  background: rgba(107, 155, 122, 0.08);
  border-radius: 20rpx;
  padding: 24rpx;
}

.result-icon-wrap {
  width: 64rpx;
  height: 64rpx;
  border-radius: 50%;
  background: #6B9B7A;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.result-icon {
  color: #FFFFFF;
  font-size: 30rpx;
  font-weight: 700;
}

.result-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 6rpx;
  min-width: 0;
}

.result-name {
  font-size: 26rpx;
  font-weight: 600;
  color: #1D1D1F;
  word-break: break-all;
}

.result-size {
  font-size: 22rpx;
  color: #8E8E93;
}

.result-actions {
  flex-shrink: 0;
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 16rpx;
}

.result-action {
  background: #6B9B7A;
  color: #FFFFFF;
  font-size: 24rpx;
  font-weight: 600;
  border-radius: 999rpx;
  padding: 12rpx 28rpx;
  text-align: center;
}

.result-action--primary {
  background: #6B9B7A;
}

.result-action:not(.result-action--primary) {
  background: rgba(92, 122, 153, 0.12);
  color: #5C7A99;
}

.error-box {
  margin-top: 24rpx;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
  background: rgba(166, 123, 123, 0.08);
  border-radius: 20rpx;
  padding: 24rpx;
}

.error-text {
  flex: 1;
  font-size: 24rpx;
  color: #A67B7B;
  word-break: break-all;
}

.error-retry {
  flex-shrink: 0;
  background: #A67B7B;
  color: #FFFFFF;
  font-size: 24rpx;
  font-weight: 600;
  border-radius: 999rpx;
  padding: 12rpx 28rpx;
}

.history-empty {
  text-align: center;
  color: #8E8E93;
  font-size: 24rpx;
  padding: 40rpx 0;
}

.history-list {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

.history-item {
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 16rpx;
  padding: 20rpx 0;
  border-bottom: 1rpx solid #F0F1F3;
}

.history-item:last-child {
  border-bottom: none;
}

.history-main {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 6rpx;
}

.history-line {
  display: flex;
  align-items: center;
  gap: 12rpx;
  min-width: 0;
}

.history-name {
  flex: 1;
  min-width: 0;
  font-size: 26rpx;
  color: #1D1D1F;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.history-time {
  font-size: 22rpx;
  color: #8E8E93;
}

.history-status {
  flex-shrink: 0;
  font-size: 22rpx;
  border-radius: 999rpx;
  padding: 8rpx 18rpx;
}

.history-actions {
  display: flex;
  flex-direction: row;
  align-items: center;
  gap: 16rpx;
  flex-shrink: 0;
}

.history-check {
  width: 36rpx;
  height: 36rpx;
  border-radius: 8rpx;
  border: 2rpx solid #C9D2DC;
  box-sizing: border-box;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.history-check--checked {
  background: #5C7A99;
  border-color: #5C7A99;
}

.history-check-mark {
  color: #FFFFFF;
  font-size: 22rpx;
  font-weight: 700;
  line-height: 1;
}

.history-batch-btn {
  background: rgba(92, 122, 153, 0.10);
  color: #5C7A99;
  font-size: 22rpx;
  font-weight: 600;
  border-radius: 999rpx;
  padding: 10rpx 22rpx;
}

.history-status--queued,
.history-status--running {
  color: #4A6278;
  background: rgba(92, 122, 153, 0.10);
}

.history-status--failed {
  color: #7A5C5C;
  background: rgba(166, 123, 123, 0.10);
}
</style>
