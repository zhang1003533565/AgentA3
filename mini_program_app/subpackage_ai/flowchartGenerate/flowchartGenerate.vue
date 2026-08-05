<template>
  <view class="page">
    <nav-bar title="AI 流程图" :showBack="true" :border="false">
      <template #right>
        <view class="nav-history-action" @tap="openHistory">
          <image class="nav-history-icon" src="/static/icons/diagram/history.svg" mode="aspectFit" />
        </view>
      </template>
    </nav-bar>

    <scroll-view class="content" scroll-y>
      <!-- 描述卡片 -->
      <view class="input-card">
        <textarea
          class="prompt-input"
          v-model="flowDescription"
          placeholder="例如：生成请假申请流程，包含员工提交、主管审批、人事备案三个环节。如果主管拒绝，则返回修改。"
          placeholder-class="prompt-placeholder"
          :maxlength="500"
        />
        <view class="input-footer">
          <text class="char-count">{{ flowDescription.length }} / 500</text>
          <view class="import-btn" @tap="importDocument">
            <image class="import-icon" src="/static/icons/diagram/import-file.svg" mode="aspectFit" />
            <text>导入文档</text>
          </view>
        </view>
      </view>

      <!-- 流程场景 -->
      <view class="section-title">
        <image class="section-icon" src="/static/icons/diagram/app-grid.svg" mode="aspectFit" />
        <text>流程场景</text>
      </view>
      <view class="card">
        <view class="chip-row">
          <view
            v-for="item in sceneOptions"
            :key="item.key"
            class="chip"
            :class="{ 'chip--on': selectedScene === item.key }"
            @tap="selectedScene = item.key"
          >
            <text>{{ item.label }}</text>
          </view>
        </view>
      </view>

      <!-- 节点粒度 -->
      <view class="section-title">
        <image class="section-icon" src="/static/icons/diagram/layer.svg" mode="aspectFit" />
        <text>节点粒度</text>
      </view>
      <view class="card">
        <view class="seg">
          <view
            v-for="item in granularityOptions"
            :key="item.key"
            class="seg-item"
            :class="{ 'seg-item--on': selectedGranularity === item.key }"
            @tap="selectedGranularity = item.key"
          >
            <text>{{ item.label }}</text>
          </view>
        </view>
      </view>

      <!-- 判断节点 -->
      <view class="section-title">
        <image class="section-icon" src="/static/icons/diagram/database.svg" mode="aspectFit" />
        <text>判断节点</text>
      </view>
      <view class="card">
        <view v-for="item in judgeOptions" :key="item.key" class="radio-row" @tap="selectedJudge = item.key">
          <text class="radio-text">{{ item.label }}</text>
          <view class="radio" :class="{ 'radio--on': selectedJudge === item.key }"></view>
        </view>
      </view>

      <!-- 角色泳道 -->
      <view class="section-title">
        <image class="section-icon" src="/static/icons/diagram/user-line.svg" mode="aspectFit" />
        <text>角色泳道</text>
      </view>
      <view class="card">
        <view class="lane-grid">
          <view
            v-for="item in laneOptions"
            :key="item.key"
            class="lane-item"
            :class="{ 'lane-item--on': selectedLane === item.key }"
            @tap="selectedLane = item.key"
          >
            <image class="lane-icon" :src="item.icon" mode="aspectFit" />
            <text>{{ item.label }}</text>
          </view>
        </view>
      </view>

      <view class="ready-card">
        <image class="ready-icon" src="/static/icons/diagram/spark-blue.svg" mode="aspectFit" />
        <text>AI 已就绪，点击生成后将构建流程骨架并逐节点生长</text>
      </view>
    </scroll-view>

    <view class="bottom-bar">
      <view class="generate-btn" @tap="generateFlowchart">
        <image class="generate-icon" src="/static/icons/diagram/flow-white.svg" mode="aspectFit" />
        <text>AI 生成流程图</text>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import NavBar from '@/components/nav-bar/nav-bar.vue'
import {
  getErrorMessage,
  getFlowchartHistory,
  uploadFlowchartFile
} from '@/api/aiDiagram.js'

const flowDescription = ref('')
const selectedScene = ref('administrative')
const selectedGranularity = ref('auto')
const selectedJudge = ref('auto')
const selectedLane = ref('auto')
const uploadedDocument = ref(null)

const sceneOptions = [
  { key: 'administrative', label: '行政流程' },
  { key: 'business', label: '业务流程' },
  { key: 'study', label: '学习流程' },
  { key: 'life', label: '生活流程' }
]

const granularityOptions = [
  { key: 'auto', label: '自动' },
  { key: 'simple', label: '简略' },
  { key: 'standard', label: '标准' },
  { key: 'detail', label: '详细' }
]

const judgeOptions = [
  { key: 'auto', label: '自动识别内容' },
  { key: 'force', label: '强制包含判断框' },
  { key: 'none', label: '不包含' }
]

const laneOptions = [
  { key: 'auto', label: '自动', icon: '/static/icons/diagram/role-auto-orange.svg' },
  { key: 'hidden', label: '不显示', icon: '/static/icons/diagram/eye-off.svg' },
  { key: 'role', label: '按角色', icon: '/static/icons/diagram/user-line.svg' },
  { key: 'department', label: '按部门', icon: '/static/icons/diagram/users-line.svg' }
]

const openHistory = async () => {
  try {
    const records = await getFlowchartHistory()
    if (!records.length) {
      uni.showToast({ title: '暂无生成记录', icon: 'none' })
      return
    }
    uni.showActionSheet({
      itemList: records.slice(0, 6).map(item => `${item.title} · ${item.type || 'FLOWCHART'}`),
      success: ({ tapIndex }) => {
        const record = records[tapIndex]
        if (record?.id) {
          uni.navigateTo({
            url: `/subpackage_ai/flowchartViewer/flowchartViewer?id=${encodeURIComponent(record.id)}`
          })
        }
      }
    })
  } catch (error) {
    uni.showToast({ title: getErrorMessage(error, '加载历史失败'), icon: 'none' })
  }
}

const importDocument = () => {
  uni.chooseMessageFile({
    count: 1,
    type: 'file',
    extension: ['pdf', 'doc', 'docx', 'ppt', 'pptx', 'md', 'markdown'],
    success: async ({ tempFiles }) => {
      const file = tempFiles?.[0]
      const filePath = file?.path || file?.tempFilePath
      const fileName = file?.name || ''
      const extension = fileName.includes('.') ? fileName.split('.').pop().toLowerCase() : ''
      if (!filePath || !['pdf', 'doc', 'docx', 'ppt', 'pptx', 'md', 'markdown'].includes(extension)) {
        uni.showToast({ title: '仅支持 PDF、Word、PPT、Markdown', icon: 'none' })
        return
      }
      uni.showLoading({ title: '解析中...', mask: true })
      try {
        const result = await uploadFlowchartFile(filePath, fileName)
        uploadedDocument.value = result
        if (!flowDescription.value.trim()) {
          flowDescription.value = `根据文档《${result.fileName || fileName}》生成流程图`
        }
        uni.showToast({ title: '文档解析完成', icon: 'success' })
      } catch (error) {
        uni.showToast({ title: getErrorMessage(error, '文件解析失败'), icon: 'none' })
      } finally {
        uni.hideLoading()
      }
    }
  })
}

// 组装请求体并跳转生成动画页（由动画页负责调用 API 与播放生长动画）
const generateFlowchart = () => {
  if (!flowDescription.value.trim()) {
    uni.showToast({ title: '请输入流程描述', icon: 'none' })
    return
  }
  const payload = {
    description: flowDescription.value.trim(),
    processType: ({ administrative: 'ADMIN', business: 'BUSINESS', study: 'LEARNING', life: 'LIFE' })[selectedScene.value] || 'BUSINESS',
    diagramType: ['role', 'department'].includes(selectedLane.value) ? 'SWIMLANE' : 'AUTO',
    nodeLevel: ({ auto: 'AUTO', simple: 'SIMPLE', standard: 'STANDARD', detail: 'DETAIL' })[selectedGranularity.value] || 'AUTO',
    decisionMode: ({ auto: 'AUTO', force: 'INCLUDE_DECISION', none: 'LINEAR' })[selectedJudge.value] || 'AUTO',
    swimlane: ({ auto: 'AUTO', hidden: 'NONE', role: 'ROLE', department: 'DEPARTMENT' })[selectedLane.value] || 'AUTO',
    displayItems: ['STEP', 'ROLE', 'INPUT_OUTPUT', 'EXCEPTION', 'DATA'],
    sourceText: uploadedDocument.value?.text || '',
    sourceFile: uploadedDocument.value?.sourceFile || '',
    fileId: uploadedDocument.value?.fileId || ''
  }
  uni.setStorageSync('aiFlowchartPendingPayload', payload)
  uni.navigateTo({ url: '/subpackage_ai/flowchartGenerating/flowchartGenerating' })
}
</script>

<style lang="scss" scoped>
.page { min-height: 100vh; background: #F4F6F9; color: #1e344f; }

.nav-history-action { display: flex; align-items: center; justify-content: center; width: 64rpx; height: 64rpx; border-radius: 999rpx; }
.nav-history-icon { width: 32rpx; height: 32rpx; opacity: 0.72; }

.content { height: calc(100vh - 88rpx); padding: 24rpx 28rpx 160rpx; box-sizing: border-box; }

.input-card { background: #fff; border-radius: 28rpx; padding: 24rpx; box-shadow: 0 4rpx 20rpx rgba(30, 52, 79, 0.05); }
.prompt-input { width: 100%; height: 180rpx; color: #1e344f; font-size: 27rpx; line-height: 1.6; }
.prompt-placeholder { color: #a9b6c4; font-size: 27rpx; line-height: 1.6; }
.input-footer { display: flex; align-items: center; justify-content: space-between; margin-top: 10rpx; }
.char-count { color: #8290a1; font-size: 22rpx; }
.import-btn { display: flex; align-items: center; gap: 8rpx; font-size: 24rpx; color: #3E6A9C; padding: 10rpx 20rpx; border: 2rpx solid #d8e2ec; border-radius: 999rpx; }
.import-icon { width: 26rpx; height: 26rpx; }

.section-title { display: flex; align-items: center; gap: 10rpx; margin: 32rpx 4rpx 16rpx; font-size: 26rpx; font-weight: 700; color: #1e344f; }
.section-icon { width: 28rpx; height: 28rpx; }

.card { background: #fff; border-radius: 28rpx; padding: 24rpx; box-shadow: 0 4rpx 20rpx rgba(30, 52, 79, 0.05); }

.chip-row { display: flex; flex-wrap: wrap; gap: 16rpx; }
.chip { padding: 14rpx 28rpx; border-radius: 999rpx; background: #f1f4f8; color: #58728c; font-size: 25rpx; }
.chip--on { background: #5081B8; color: #fff; font-weight: 600; }

.seg { display: flex; background: #f1f4f8; border-radius: 20rpx; padding: 6rpx; }
.seg-item { flex: 1; display: flex; align-items: center; justify-content: center; padding: 14rpx 0; border-radius: 16rpx; font-size: 25rpx; color: #58728c; }
.seg-item--on { background: #fff; color: #3E6A9C; font-weight: 700; box-shadow: 0 2rpx 8rpx rgba(30, 52, 79, 0.12); }

.radio-row { display: flex; align-items: center; justify-content: space-between; padding: 18rpx 4rpx; }
.radio-row + .radio-row { border-top: 2rpx solid #eef1f6; }
.radio-text { font-size: 26rpx; color: #1e344f; }
.radio { width: 32rpx; height: 32rpx; border-radius: 50%; border: 4rpx solid #c6d2de; box-sizing: border-box; }
.radio--on { border-color: #5081B8; background: #fff; box-shadow: inset 0 0 0 6rpx #fff; background-color: #5081B8; }

.lane-grid { display: grid; grid-template-columns: repeat(4, 1fr); gap: 14rpx; }
.lane-item { display: flex; flex-direction: column; align-items: center; justify-content: center; padding: 18rpx 0; border: 2rpx solid transparent; border-radius: 20rpx; background: #f1f4f8; font-size: 22rpx; color: #18273F; }
.lane-item--on { border-color: #5081B8; background: #fff; color: #3E6A9C; font-weight: 600; }
.lane-icon { width: 30rpx; height: 30rpx; margin-bottom: 8rpx; }

.ready-card { display: flex; align-items: center; gap: 14rpx; margin-top: 32rpx; padding: 24rpx 28rpx; border-radius: 28rpx; background: #eef4fb; border: 2rpx solid #dbe7f3; color: #3E6A9C; font-size: 25rpx; }
.ready-icon { width: 30rpx; height: 30rpx; flex-shrink: 0; }

.bottom-bar { position: fixed; left: 0; right: 0; bottom: 0; z-index: 20; padding: 20rpx 28rpx 30rpx; background: linear-gradient(180deg, rgba(244, 246, 249, 0), #F4F6F9 30%); }
.generate-btn { display: flex; align-items: center; justify-content: center; gap: 12rpx; height: 88rpx; border-radius: 28rpx; background: #5081B8; color: #fff; font-size: 30rpx; font-weight: 700; box-shadow: 0 12rpx 32rpx rgba(80, 129, 184, 0.3); }
.generate-icon { width: 32rpx; height: 32rpx; }
</style>
