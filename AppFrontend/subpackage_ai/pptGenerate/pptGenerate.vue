<template>
  <view class="page">
    <!-- 导航栏 -->
    <view class="nav-bar">
      <view class="nav-back" @tap="goBack">
        <text class="nav-back-icon">‹</text>
      </view>
      <text class="nav-title">PPT生成</text>
      <view class="nav-history" @tap="goHistory">
        <text class="nav-history-icon">🕐</text>
        <text class="nav-history-text">历史记录</text>
      </view>
    </view>

    <view class="content">
      <!-- 步骤条 -->
      <view class="steps">
        <view class="step" v-for="(s, idx) in steps" :key="idx">
          <view class="step-circle" :class="{ 'step-circle--active': currentStep >= idx + 1, 'step-circle--done': currentStep > idx + 1 }">
            <text class="step-num" v-if="currentStep <= idx + 1">{{ idx + 1 }}</text>
            <text class="step-done-icon" v-else>✓</text>
          </view>
          <text class="step-label" :class="{ 'step-label--active': currentStep >= idx + 1 }">{{ s }}</text>
          <view class="step-line" v-if="idx < steps.length - 1" :class="{ 'step-line--active': currentStep > idx + 1 }"></view>
        </view>
      </view>

      <!-- 步骤1: 输入主题 -->
      <view class="form-section" v-if="currentStep === 1">
        <!-- PPT主题 -->
        <view class="field-header">
          <text class="field-title">PPT主题</text>
          <text class="field-ai-link" @tap="aiWriteTopic">AI 帮你写</text>
        </view>
        <view class="textarea-wrapper">
          <textarea
            class="topic-input"
            v-model="pptTopic"
            placeholder="请输入PPT主题"
            :maxlength="200"
          />
          <text class="char-count">{{ pptTopic.length }}/200</text>
        </view>

        <!-- 补充说明 -->
        <view class="field-header" style="margin-top: 32rpx;">
          <text class="field-title">补充说明 <text class="field-optional">（可选）</text></text>
        </view>
        <view class="textarea-wrapper">
          <textarea
            class="topic-input"
            v-model="supplement"
            placeholder="例如：面向对象、使用场景、重点内容、风格偏好等"
            :maxlength="200"
          />
          <text class="char-count">{{ supplement.length }}/200</text>
        </view>

        <!-- 场景选择 -->
        <view class="field-header" style="margin-top: 32rpx;">
          <text class="field-title">场景选择</text>
          <text class="field-more" @tap="showMoreScenes">更多 ›</text>
        </view>
        <view class="scene-list">
          <view
            class="scene-item"
            :class="{ 'scene-item--active': selectedScene === item.key }"
            v-for="item in scenes"
            :key="item.key"
            @tap="selectedScene = item.key"
          >
            <view class="scene-icon" :style="{ background: item.bgColor }">
              <text class="scene-icon-text">{{ item.icon }}</text>
            </view>
            <text class="scene-label">{{ item.label }}</text>
          </view>
        </view>

        <!-- 风格选择 -->
        <view class="field-header" style="margin-top: 32rpx;">
          <text class="field-title">风格选择</text>
          <text class="field-more" @tap="showMoreStyles">更多 ›</text>
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
            <text class="style-label">{{ item.label }}</text>
          </view>
        </view>

        <!-- 页数范围 -->
        <view class="field-header" style="margin-top: 32rpx;">
          <text class="field-title">页数范围</text>
        </view>
        <view class="page-range-list">
          <view
            class="page-range-item"
            :class="{ 'page-range-item--active': selectedPageRange === item }"
            v-for="item in pageRanges"
            :key="item"
            @tap="selectedPageRange = item"
          >
            <text class="page-range-text">{{ item }}</text>
          </view>
        </view>

        <!-- 高级设置 -->
        <view class="advanced-settings" @tap="showAdvanced = !showAdvanced">
          <text class="advanced-title">高级设置</text>
          <text class="advanced-arrow" :class="{ 'advanced-arrow--open': showAdvanced }">∨</text>
        </view>
        <view class="advanced-content" v-if="showAdvanced">
          <view class="advanced-item">
            <text class="advanced-item-label">字体</text>
            <picker :range="fontOptions" @change="onFontChange">
              <text class="advanced-item-value">{{ selectedFont }} ›</text>
            </picker>
          </view>
          <view class="advanced-item">
            <text class="advanced-item-label">比例</text>
            <picker :range="ratioOptions" @change="onRatioChange">
              <text class="advanced-item-value">{{ selectedRatio }} ›</text>
            </picker>
          </view>
        </view>

        <!-- 生成按钮 -->
        <view class="generate-btn" @tap="generatePlan">
          <text class="generate-btn-text">✦ 开始生成方案</text>
        </view>


      </view>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'

const currentStep = ref(1)
const steps = ['输入主题', '方案预览', '内容生成', '完成导出']

const pptTopic = ref('')
const supplement = ref('')
const selectedScene = ref('general')
const selectedStyle = ref('business')
const selectedPageRange = ref('10页以内')
const showAdvanced = ref(false)
const selectedFont = ref('默认字体')
const selectedRatio = ref('16:9')

const scenes = [
  { key: 'general', label: '通用演示', icon: '🎯', bgColor: '#EEF0FF' },
  { key: 'work', label: '工作汇报', icon: '📊', bgColor: '#E8F8F0' },
  { key: 'teaching', label: '教学课件', icon: '📋', bgColor: '#FFF3E0' },
  { key: 'product', label: '产品介绍', icon: '', bgColor: '#F3E8FF' },
  { key: 'business', label: '商业计划', icon: '📈', bgColor: '#FFE8F0' },
]

const styles = [
  { key: 'business', label: '简约商务', previewBg: 'linear-gradient(135deg, #667eea 0%, #764ba2 100%)' },
  { key: 'tech', label: '科技蓝', previewBg: 'linear-gradient(135deg, #0c3483 0%, #a2b6df 100%)' },
  { key: 'fresh', label: '清新文艺', previewBg: 'linear-gradient(135deg, #f093fb 0%, #f5576c 100%)' },
  { key: 'creative', label: '创意时尚', previewBg: 'linear-gradient(135deg, #4facfe 0%, #00f2fe 100%)' },
]

const pageRanges = ['10页以内', '10-20页', '20-30页', '30页以上']
const fontOptions = ['默认字体', '微软雅黑', '思源黑体', '苹方']
const ratioOptions = ['16:9', '4:3', 'A4']

const goBack = () => { uni.navigateBack() }
const goHistory = () => { uni.showToast({ title: '历史记录', icon: 'none' }) }
const aiWriteTopic = () => { uni.showToast({ title: 'AI帮你写', icon: 'none' }) }
const showMoreScenes = () => { uni.showToast({ title: '更多场景', icon: 'none' }) }
const showMoreStyles = () => { uni.showToast({ title: '更多风格', icon: 'none' }) }
const onFontChange = (e) => { selectedFont.value = fontOptions[e.detail.value] }
const onRatioChange = (e) => { selectedRatio.value = ratioOptions[e.detail.value] }

const generatePlan = () => {
  if (!pptTopic.value.trim()) {
    uni.showToast({ title: '请输入PPT主题', icon: 'none' })
    return
  }
  // 跳转到方案预览页
  uni.navigateTo({
    url: `/subpackage_ai/pptPreview/pptPreview?topic=${encodeURIComponent(pptTopic.value)}&scene=${selectedScene.value}&style=${selectedStyle.value}&pages=${encodeURIComponent(selectedPageRange.value)}`
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

.nav-history {
  display: flex;
  align-items: center;
  gap: 8rpx;
  padding: 12rpx 20rpx;
  background: #F0F2F5;
  border-radius: 999rpx;
}

.nav-history-icon {
  font-size: 28rpx;
}

.nav-history-text {
  font-size: 24rpx;
  color: #666;
}

.content {
  padding: 20rpx 24rpx 40rpx;
}

/* 步骤条 */
.steps {
  display: flex;
  align-items: flex-start;
  padding: 20rpx 0 40rpx;
}

.step {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  position: relative;
}

.step-circle {
  width: 52rpx;
  height: 52rpx;
  border-radius: 50%;
  background: #E8E8E8;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-bottom: 12rpx;
}

.step-circle--active {
  background: #4D6BFE;
}

.step-circle--done {
  background: #4D6BFE;
}

.step-num {
  font-size: 26rpx;
  color: #FFF;
  font-weight: 600;
}

.step-done-icon {
  font-size: 24rpx;
  color: #FFF;
}

.step-label {
  font-size: 22rpx;
  color: #999;
  text-align: center;
}

.step-label--active {
  color: #4D6BFE;
  font-weight: 600;
}

.step-line {
  position: absolute;
  top: 26rpx;
  left: 50%;
  width: 100%;
  height: 4rpx;
  background: #E8E8E8;
  z-index: -1;
}

.step-line--active {
  background: #4D6BFE;
}

/* 表单区域 */
.form-section {
  background: #FFF;
  border-radius: 16rpx;
  padding: 28rpx 24rpx;
}

.field-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16rpx;
}

.field-title {
  font-size: 30rpx;
  font-weight: 700;
  color: #222;
}

.field-optional {
  font-size: 24rpx;
  color: #999;
  font-weight: 400;
}

.field-ai-link {
  font-size: 26rpx;
  color: #4D6BFE;
}

.field-more {
  font-size: 24rpx;
  color: #999;
}

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

/* 场景选择 */
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
  min-width: 140rpx;
  padding: 20rpx 16rpx;
  border-radius: 12rpx;
  border: 2rpx solid #F0F0F0;
}

.scene-item--active {
  border-color: #4D6BFE;
  background: #F8F9FF;
}

.scene-icon {
  width: 80rpx;
  height: 80rpx;
  border-radius: 16rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.scene-icon-text {
  font-size: 40rpx;
}

.scene-label {
  font-size: 24rpx;
  color: #555;
  text-align: center;
}

/* 风格选择 */
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
  min-width: 160rpx;
}

.style-preview {
  width: 160rpx;
  height: 100rpx;
  border-radius: 12rpx;
  position: relative;
  overflow: hidden;
}

.style-check {
  position: absolute;
  top: 8rpx;
  right: 8rpx;
  width: 36rpx;
  height: 36rpx;
  background: #4D6BFE;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.style-check-icon {
  font-size: 22rpx;
  color: #FFF;
}

.style-label {
  font-size: 24rpx;
  color: #555;
}

/* 页数范围 */
.page-range-list {
  display: flex;
  gap: 16rpx;
}

.page-range-item {
  flex: 1;
  padding: 18rpx 0;
  text-align: center;
  background: #F8F9FA;
  border-radius: 12rpx;
  border: 2rpx solid transparent;
}

.page-range-item--active {
  background: #EEF0FF;
  border-color: #4D6BFE;
}

.page-range-text {
  font-size: 26rpx;
  color: #555;
}

.page-range-item--active .page-range-text {
  color: #4D6BFE;
  font-weight: 600;
}

/* 高级设置 */
.advanced-settings {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 24rpx 0;
  border-top: 1rpx solid #F0F0F0;
  margin-top: 24rpx;
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
  padding: 16rpx 0;
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
  margin-top: 32rpx;
}

.generate-btn-text {
  color: #FFF;
  font-size: 32rpx;
  font-weight: 700;
}


</style>
