<template>
  <view class="page">
    <view class="nav-bar">
      <view class="nav-action nav-action--left" @tap="goBack">
        <text class="nav-back">‹</text>
      </view>
      <text class="nav-title">AI 流程图</text>
      <view class="nav-action nav-action--right" @tap="openHistory">
        <image class="nav-history-icon" src="/static/icons/diagram/history.svg" mode="aspectFit" />
      </view>
    </view>

    <scroll-view class="content" scroll-y>
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

      <view class="section-title">
        <image class="section-icon" src="/static/icons/diagram/settings-orange.svg" mode="aspectFit" />
        <text>专属生成设置</text>
      </view>

      <view class="field-block field-block--plain">
        <text class="field-label">流程场景</text>
        <view class="chip-row">
          <view
            class="pill-chip"
            :class="{ 'pill-chip--active': selectedScene === item.key }"
            v-for="item in sceneOptions"
            :key="item.key"
            @tap="selectedScene = item.key"
          >
            {{ item.label }}
          </view>
        </view>
      </view>

      <view class="setting-card">
        <view class="card-head">
          <text class="field-label field-label--head">节点粒度</text>
          <text class="auto-badge">自动</text>
        </view>
        <view class="segment-row">
          <view
            class="segment-item"
            :class="{ 'segment-item--active': selectedGranularity === item.key }"
            v-for="item in granularityOptions"
            :key="item.key"
            @tap="selectedGranularity = item.key"
          >
            {{ item.label }}
          </view>
        </view>
      </view>

      <view class="setting-card">
        <view class="card-head">
          <text class="field-label field-label--head">判断节点</text>
          <text class="help-icon">?</text>
        </view>
        <view class="radio-list">
          <view
            class="radio-row"
            v-for="item in judgeOptions"
            :key="item.key"
            @tap="selectedJudge = item.key"
          >
            <text>{{ item.label }}</text>
            <view class="radio" :class="{ 'radio--active': selectedJudge === item.key }"></view>
          </view>
        </view>
      </view>

      <view class="setting-card">
        <text class="field-label field-label--standalone">角色泳道</text>
        <view class="role-grid">
          <view
            class="role-item"
            :class="{ 'role-item--active': selectedLane === item.key }"
            v-for="item in laneOptions"
            :key="item.key"
            @tap="selectedLane = item.key"
          >
            <image class="role-icon" :src="item.icon" mode="aspectFit" />
            <text>{{ item.label }}</text>
          </view>
        </view>
      </view>

      <view class="thinking-card">
        <image class="thinking-icon" src="/static/icons/diagram/spark-orange.svg" mode="aspectFit" />
        <text>AI 已就绪，正在准备逻辑构建</text>
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

const flowDescription = ref('')
const selectedScene = ref('administrative')
const selectedGranularity = ref('auto')
const selectedJudge = ref('auto')
const selectedLane = ref('auto')

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

const goBack = () => { uni.navigateBack() }
const openHistory = () => { uni.showToast({ title: '历史记录预留', icon: 'none' }) }
const importDocument = () => { uni.showToast({ title: '导入文档接口预留', icon: 'none' }) }

const generateFlowchart = () => {
  if (!flowDescription.value.trim()) {
    uni.showToast({ title: '请输入流程描述', icon: 'none' })
    return
  }
  uni.navigateTo({
    url: `/subpackage_ai/flowchartPreview/flowchartPreview?desc=${encodeURIComponent(flowDescription.value)}&type=${selectedScene.value}&style=orange&direction=${encodeURIComponent(selectedGranularity.value)}&judge=${selectedJudge.value}&lane=${selectedLane.value}`
  })
}
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background: #FAF8FA;
  color: #111827;
}

.nav-bar {
  position: relative;
  z-index: 10;
  display: flex;
  align-items: center;
  height: 88rpx;
  padding: 0 40rpx;
  background: #FFFFFF;
  box-sizing: border-box;
}

.nav-action {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 44rpx;
  height: 44rpx;
}

.nav-action--left {
  margin-right: 88rpx;
}

.nav-action--right {
  margin-left: auto;
}

.nav-back {
  color: #7A8799;
  font-size: 48rpx;
  font-weight: 300;
  line-height: 1;
  transform: translateY(-2rpx);
}

.nav-title {
  color: #2E3C56;
  font-size: 34rpx;
  font-weight: 800;
}

.nav-history-icon {
  width: 32rpx;
  height: 32rpx;
  opacity: 0.72;
}

.content {
  height: calc(100vh - 88rpx);
  padding: 30rpx 40rpx 156rpx;
  box-sizing: border-box;
}

.input-card {
  height: 296rpx;
  padding: 30rpx 26rpx 18rpx;
  border: 1rpx solid #E2E4EA;
  border-radius: 14rpx;
  background: #FFFFFF;
  box-sizing: border-box;
}

.prompt-input {
  width: 100%;
  height: 204rpx;
  color: #2F3B4E;
  font-size: 24rpx;
  line-height: 1.45;
}

.prompt-placeholder {
  color: #BDC4D2;
  font-size: 24rpx;
  line-height: 1.45;
}

.input-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.char-count {
  color: #A2ABB9;
  font-size: 20rpx;
}

.import-btn {
  display: flex;
  align-items: center;
  height: 46rpx;
  padding: 0 18rpx;
  border-radius: 10rpx;
  background: #F1EFF4;
  color: #172236;
  font-size: 22rpx;
  font-weight: 700;
}

.import-icon {
  width: 24rpx;
  height: 24rpx;
  margin-right: 8rpx;
}

.section-title {
  display: flex;
  align-items: center;
  margin: 48rpx 0 24rpx;
  color: #111827;
  font-size: 30rpx;
  font-weight: 800;
}

.section-icon {
  width: 34rpx;
  height: 30rpx;
  margin-right: 12rpx;
}

.field-block--plain {
  margin-bottom: 26rpx;
}

.field-label {
  display: block;
  color: #1F2937;
  font-size: 20rpx;
  font-weight: 600;
}

.field-label--head {
  margin-bottom: 0;
}

.field-label--standalone {
  margin-bottom: 22rpx;
}

.chip-row {
  display: flex;
  gap: 12rpx;
  margin-top: 16rpx;
}

.pill-chip {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 50rpx;
  padding: 0 24rpx;
  border: 1rpx solid #DEE2E8;
  border-radius: 28rpx;
  background: #FFFFFF;
  color: #273244;
  font-size: 22rpx;
}

.pill-chip--active {
  border-color: #FF9F1C;
  background: #FFF7EB;
  color: #FF9F1C;
}

.setting-card {
  margin-bottom: 26rpx;
  padding: 26rpx;
  border: 1rpx solid #E1E4EA;
  border-radius: 16rpx;
  background: #FFFFFF;
  box-sizing: border-box;
}

.card-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24rpx;
}

.auto-badge {
  padding: 6rpx 12rpx;
  border-radius: 6rpx;
  background: #FFF4E1;
  color: #FF9F1C;
  font-size: 18rpx;
}

.help-icon {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 24rpx;
  height: 24rpx;
  border: 2rpx solid #C7CDD6;
  border-radius: 50%;
  color: #9AA4B2;
  font-size: 16rpx;
  font-weight: 800;
}

.segment-row,
.role-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 14rpx;
}

.segment-item {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 52rpx;
  border-radius: 8rpx;
  background: #F0EEF1;
  color: #273244;
  font-size: 22rpx;
  font-weight: 600;
}

.segment-item--active {
  background: #FF9F1C;
  color: #FFFFFF;
}

.radio-list {
  border: 1rpx solid #E4E6EB;
  border-radius: 10rpx;
  overflow: hidden;
}

.radio-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  height: 68rpx;
  padding: 0 22rpx;
  border-bottom: 1rpx solid #E4E6EB;
  color: #111827;
  font-size: 24rpx;
  box-sizing: border-box;
}

.radio-row:last-child {
  border-bottom: 0;
}

.radio {
  width: 24rpx;
  height: 24rpx;
  border: 2rpx solid #CDD3DD;
  border-radius: 50%;
  box-sizing: border-box;
}

.radio--active {
  border: 8rpx solid #FF9F1C;
}

.role-item {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  height: 104rpx;
  border: 1rpx solid transparent;
  border-radius: 10rpx;
  background: #F0EEF1;
  color: #18273F;
  font-size: 20rpx;
  font-weight: 600;
  box-sizing: border-box;
}

.role-item--active {
  border: 3rpx solid #FF9F1C;
  background: #FFFFFF;
  color: #FF9F1C;
}

.role-icon {
  width: 30rpx;
  height: 30rpx;
  margin-bottom: 8rpx;
}

.thinking-card {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  height: 204rpx;
  margin: 34rpx 0 0;
  border: 1rpx solid #E7E3E7;
  border-radius: 18rpx;
  background: #F2EFF2;
  color: #A3A0A8;
  font-size: 22rpx;
}

.thinking-icon {
  width: 54rpx;
  height: 54rpx;
  margin-bottom: 12rpx;
  opacity: 0.72;
}

.bottom-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 20;
  padding: 22rpx 40rpx 30rpx;
  background: #FAF8FA;
  box-sizing: border-box;
}

.generate-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 84rpx;
  border-radius: 14rpx;
  background: #FF9F1C;
  color: #FFFFFF;
  font-size: 30rpx;
  font-weight: 800;
}

.generate-icon {
  width: 32rpx;
  height: 32rpx;
  margin-right: 12rpx;
}
</style>
