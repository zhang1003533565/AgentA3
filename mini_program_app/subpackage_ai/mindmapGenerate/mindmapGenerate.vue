<template>
  <view class="page">
    <!-- 导航栏 -->
    <view class="nav-bar">
      <view class="nav-back" @tap="goBack">
        <text class="nav-back-icon">‹</text>
      </view>
      <text class="nav-title">思维导图生成</text>
      <view class="nav-placeholder"></view>
    </view>

    <view class="content">
      <!-- ① 输入主题 -->
      <view class="form-section">
        <view class="step-header">
          <view class="step-badge">1</view>
          <text class="step-title">输入主题</text>
        </view>
        <view class="textarea-wrapper">
          <textarea
            class="topic-input"
            v-model="topic"
            placeholder="请输入思维导图主题"
            :maxlength="100"
          />
          <text class="char-count">{{ topic.length }}/100</text>
        </view>
      </view>

      <!-- ② 选择场景 -->
      <view class="form-section">
        <view class="step-header">
          <view class="step-badge">2</view>
          <text class="step-title">选择场景</text>
          <text class="step-optional">（可选）</text>
        </view>
        <view class="scene-list">
          <view
            class="scene-item"
            :class="{ 'scene-item--active': selectedScene === item.key }"
            v-for="item in scenes"
            :key="item.key"
            @tap="selectedScene = item.key"
          >
            <view class="scene-icon-wrap" :style="{ background: item.bgColor }">
              <text class="scene-icon">{{ item.icon }}</text>
              <text class="scene-check" v-if="selectedScene === item.key">✓</text>
            </view>
            <text class="scene-label">{{ item.label }}</text>
          </view>
        </view>
      </view>

      <!-- ③ 结构选择 -->
      <view class="form-section">
        <view class="step-header">
          <view class="step-badge">3</view>
          <text class="step-title">结构选择</text>
          <text class="step-tag step-tag--recommend">推荐</text>
        </view>
        <view class="structure-list">
          <view
            class="structure-item"
            :class="{ 'structure-item--active': selectedStructure === item.key }"
            v-for="item in structures"
            :key="item.key"
            @tap="selectedStructure = item.key"
          >
            <view class="structure-preview">
              <view class="structure-diagram" :class="'diagram-' + item.key">
                <view class="diagram-center" :style="{ borderColor: item.color }"></view>
                <view class="diagram-branch" v-for="b in 3" :key="b" :style="{ borderColor: item.color }"></view>
              </view>
            </view>
            <text class="structure-label" :class="{ 'structure-label--active': selectedStructure === item.key }">{{ item.label }}</text>
          </view>
        </view>
      </view>

      <!-- ④ 风格选择 -->
      <view class="form-section">
        <view class="step-header">
          <view class="step-badge">4</view>
          <text class="step-title">风格选择</text>
          <text class="step-tag step-tag--new">新</text>
        </view>
        <view class="style-list">
          <view
            class="style-item"
            :class="{ 'style-item--active': selectedStyle === item.key }"
            v-for="item in styles"
            :key="item.key"
            @tap="selectedStyle = item.key"
          >
            <view class="style-preview" :style="{ background: item.previewBg }">
              <view class="style-check" v-if="selectedStyle === item.key">
                <text class="style-check-icon">✓</text>
              </view>
            </view>
            <text class="style-label" :class="{ 'style-label--active': selectedStyle === item.key }">{{ item.label }}</text>
          </view>
          <view class="style-item" @tap="showMoreStyles">
            <view class="style-preview style-preview--more">
              <text class="style-more-icon">···</text>
            </view>
            <text class="style-label">更多</text>
          </view>
        </view>
      </view>

      <!-- 高级设置 -->
      <view class="form-section">
        <view class="advanced-header" @tap="showAdvanced = !showAdvanced">
          <text class="advanced-title">高级设置</text>
          <text class="advanced-arrow" :class="{ 'advanced-arrow--open': showAdvanced }">∨</text>
        </view>
        <view class="advanced-content" v-if="showAdvanced">
          <view class="advanced-item">
            <text class="advanced-item-label">分支数量</text>
            <picker :range="branchOptions" @change="onBranchChange">
              <text class="advanced-item-value">{{ selectedBranch }} ›</text>
            </picker>
          </view>
          <view class="advanced-item">
            <text class="advanced-item-label">层级深度</text>
            <picker :range="depthOptions" @change="onDepthChange">
              <text class="advanced-item-value">{{ selectedDepth }} ›</text>
            </picker>
          </view>
        </view>
      </view>

      <!-- 生成按钮 -->
      <view class="generate-btn" @tap="generateMindmap">
        <text class="generate-btn-text">🔄 立即生成</text>
      </view>


    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'

const topic = ref('')
const selectedScene = ref('study')
const selectedStructure = ref('mindmap')
const selectedStyle = ref('fresh')
const showAdvanced = ref(false)
const selectedBranch = ref('自动')
const selectedDepth = ref('3层')

const scenes = [
  { key: 'study', label: '学习笔记', icon: '📖', bgColor: '#EEF0FF' },
  { key: 'brainstorm', label: '头脑风暴', icon: '⚡', bgColor: '#E8FFF0' },
  { key: 'project', label: '项目规划', icon: '📋', bgColor: '#FFF3E0' },
  { key: 'reading', label: '读书总结', icon: '📑', bgColor: '#F3E8FF' },
  { key: 'other', label: '其他', icon: '···', bgColor: '#F5F5F5' },
]

const structures = [
  { key: 'mindmap', label: '思维导图', color: '#4D6BFE' },
  { key: 'logic', label: '逻辑图', color: '#48DBFB' },
  { key: 'tree', label: '树状图', color: '#FE8C9A' },
  { key: 'org', label: '组织架构图', color: '#1DD1A1' },
]

const styles = [
  { key: 'fresh', label: '清新简约', previewBg: 'linear-gradient(135deg, #E8F4FD 0%, #FFF 50%, #E8F8F0 100%)' },
  { key: 'dark', label: '深色酷炫', previewBg: 'linear-gradient(135deg, #1a1a2e 0%, #16213e 50%, #0f3460 100%)' },
  { key: 'colorful', label: '彩色手绘', previewBg: 'linear-gradient(135deg, #FFF5E6 0%, #FFE8F0 50%, #F0E8FF 100%)' },
  { key: 'business', label: '商务专业', previewBg: 'linear-gradient(135deg, #F0F4F8 0%, #E2E8F0 50%, #CBD5E0 100%)' },
]

const branchOptions = ['自动', '4个', '6个', '8个', '10个']
const depthOptions = ['2层', '3层', '4层', '5层']

const goBack = () => { uni.navigateBack() }
const showMoreStyles = () => { uni.showToast({ title: '更多风格', icon: 'none' }) }
const onBranchChange = (e) => { selectedBranch.value = branchOptions[e.detail.value] }
const onDepthChange = (e) => { selectedDepth.value = depthOptions[e.detail.value] }

const generateMindmap = () => {
  if (!topic.value.trim()) {
    uni.showToast({ title: '请输入主题', icon: 'none' })
    return
  }
  uni.navigateTo({
    url: `/subpackage_ai/mindmapPreview/mindmapPreview?topic=${encodeURIComponent(topic.value)}&structure=${selectedStructure.value}&style=${selectedStyle.value}`
  })
}
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background-color: #F6F8FB;
}

/* 导航栏 */
.nav-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20rpx 24rpx;
  background: #FFF;
  position: sticky;
  top: 0;
  z-index: 100;
}

.nav-back {
  width: 60rpx;
  height: 60rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.nav-back-icon {
  font-size: 48rpx;
  color: #333;
  font-weight: 300;
}

.nav-title {
  font-size: 34rpx;
  font-weight: 700;
  color: #222;
}

.nav-placeholder {
  width: 60rpx;
}

.content {
  padding: 20rpx 24rpx 40rpx;
}

/* 表单区块 */
.form-section {
  background: #FFF;
  border-radius: 16rpx;
  padding: 28rpx 24rpx;
  margin-bottom: 20rpx;
}

.step-header {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin-bottom: 20rpx;
}

.step-badge {
  width: 44rpx;
  height: 44rpx;
  background: #4D6BFE;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 24rpx;
  color: #FFF;
  font-weight: 700;
}

.step-title {
  font-size: 30rpx;
  font-weight: 700;
  color: #222;
}

.step-optional {
  font-size: 24rpx;
  color: #999;
}

.step-tag {
  font-size: 20rpx;
  padding: 4rpx 12rpx;
  border-radius: 6rpx;
  font-weight: 600;
}

.step-tag--recommend {
  background: #EEF0FF;
  color: #4D6BFE;
}

.step-tag--new {
  background: #E8FFF0;
  color: #1DD1A1;
}

/* 输入框 */
.textarea-wrapper {
  position: relative;
}

.topic-input {
  width: 100%;
  min-height: 160rpx;
  padding: 20rpx;
  background: #F8F9FA;
  border-radius: 12rpx;
  font-size: 28rpx;
  color: #333;
  line-height: 1.6;
  box-sizing: border-box;
}

.char-count {
  position: absolute;
  right: 16rpx;
  bottom: 12rpx;
  font-size: 24rpx;
  color: #BBB;
}

/* 场景列表 */
.scene-list {
  display: flex;
  gap: 16rpx;
  overflow-x: auto;
  padding-bottom: 8rpx;
}

.scene-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12rpx;
  min-width: 130rpx;
}

.scene-icon-wrap {
  width: 90rpx;
  height: 90rpx;
  border-radius: 16rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  border: 2rpx solid transparent;
}

.scene-item--active .scene-icon-wrap {
  border-color: #4D6BFE;
}

.scene-icon {
  font-size: 40rpx;
}

.scene-check {
  position: absolute;
  top: -4rpx;
  right: -4rpx;
  width: 32rpx;
  height: 32rpx;
  background: #4D6BFE;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 18rpx;
  color: #FFF;
}

.scene-label {
  font-size: 24rpx;
  color: #555;
  text-align: center;
}

.scene-item--active .scene-label {
  color: #4D6BFE;
  font-weight: 600;
}

/* 结构列表 */
.structure-list {
  display: flex;
  gap: 16rpx;
  overflow-x: auto;
  padding-bottom: 8rpx;
}

.structure-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12rpx;
  min-width: 140rpx;
  padding: 16rpx;
  border-radius: 12rpx;
  border: 2rpx solid #F0F0F0;
}

.structure-item--active {
  border-color: #4D6BFE;
  background: #F8F9FF;
}

.structure-preview {
  width: 100rpx;
  height: 80rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.structure-diagram {
  position: relative;
  width: 80rpx;
  height: 60rpx;
}

.diagram-center {
  position: absolute;
  left: 50%;
  top: 50%;
  transform: translate(-50%, -50%);
  width: 20rpx;
  height: 20rpx;
  border-radius: 4rpx;
  border: 3rpx solid;
}

.diagram-branch {
  position: absolute;
  width: 16rpx;
  height: 16rpx;
  border-radius: 3rpx;
  border: 2rpx solid;
}

.diagram-branch:nth-child(2) { left: 4rpx; top: 4rpx; }
.diagram-branch:nth-child(3) { right: 4rpx; top: 4rpx; }
.diagram-branch:nth-child(4) { left: 50%; bottom: 4rpx; transform: translateX(-50%); }

.diagram-mindmap .diagram-center { border-radius: 50%; }
.diagram-logic .diagram-center { border-radius: 4rpx; }
.diagram-tree .diagram-branch { border-radius: 50%; }
.diagram-org .diagram-center { width: 24rpx; height: 12rpx; border-radius: 2rpx; }

.structure-label {
  font-size: 24rpx;
  color: #555;
  text-align: center;
}

.structure-label--active {
  color: #4D6BFE;
  font-weight: 600;
}

/* 风格列表 */
.style-list {
  display: flex;
  gap: 16rpx;
  overflow-x: auto;
  padding-bottom: 8rpx;
}

.style-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12rpx;
  min-width: 140rpx;
}

.style-preview {
  width: 140rpx;
  height: 90rpx;
  border-radius: 12rpx;
  position: relative;
  overflow: hidden;
  border: 2rpx solid transparent;
}

.style-item--active .style-preview {
  border-color: #4D6BFE;
}

.style-check {
  position: absolute;
  top: 6rpx;
  right: 6rpx;
  width: 32rpx;
  height: 32rpx;
  background: #4D6BFE;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.style-check-icon {
  font-size: 18rpx;
  color: #FFF;
}

.style-preview--more {
  background: #F5F5F5;
  display: flex;
  align-items: center;
  justify-content: center;
}

.style-more-icon {
  font-size: 28rpx;
  color: #999;
}

.style-label {
  font-size: 24rpx;
  color: #555;
  text-align: center;
}

.style-label--active {
  color: #4D6BFE;
  font-weight: 600;
}

/* 高级设置 */
.advanced-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8rpx 0;
}

.advanced-title {
  font-size: 30rpx;
  font-weight: 600;
  color: #222;
}

.advanced-arrow {
  font-size: 24rpx;
  color: #999;
  transition: transform 0.3s;
}

.advanced-arrow--open {
  transform: rotate(180deg);
}

.advanced-content {
  padding-top: 16rpx;
}

.advanced-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 20rpx 0;
  border-bottom: 1rpx solid #F5F5F5;
}

.advanced-item-label {
  font-size: 28rpx;
  color: #555;
}

.advanced-item-value {
  font-size: 28rpx;
  color: #999;
}

/* 生成按钮 */
.generate-btn {
  background: linear-gradient(135deg, #6A8CFE 0%, #4D6BFE 100%);
  border-radius: 16rpx;
  padding: 30rpx 0;
  text-align: center;
  margin-top: 8rpx;
}

.generate-btn-text {
  color: #FFF;
  font-size: 32rpx;
  font-weight: 700;
}

</style>
