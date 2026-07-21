<template>
  <view class="page">
    <!-- 导航栏 -->
    <view class="nav-bar">
      <view class="nav-back" @tap="goBack">
        <text class="nav-back-icon">‹</text>
      </view>
      <text class="nav-title">架构图设计</text>
      <view class="nav-history" @tap="goHistory">
        <text class="nav-history-icon">🕐</text>
        <text class="nav-history-text">历史记录</text>
      </view>
    </view>

    <view class="content">
      <!-- 头部卡片 -->
      <view class="header-card">
        <view class="header-info">
          <text class="header-title">AI 架构图智能生成</text>
          <text class="header-subtitle">输入需求，自动生成专业架构图</text>
        </view>
        <view class="header-icon">
          <text class="header-icon-text">🧊</text>
        </view>
      </view>

      <!-- 架构描述 -->
      <view class="form-section">
        <view class="section-header">
          <text class="section-title">架构描述</text>
          <view class="ai-write-btn" @tap="aiWrite">
            <text class="ai-write-icon">✦</text>
            <text class="ai-write-text">AI 帮你写</text>
          </view>
        </view>
        <view class="textarea-wrapper">
          <textarea
            class="desc-input"
            v-model="description"
            placeholder="请输入你的系统架构描述，例如：&#10;一个电商系统的微服务架构，包含用户服务、商品服务、订单服务等"
            :maxlength="500"
          />
          <text class="char-count">{{ description.length }}/500</text>
        </view>
      </view>

      <!-- 架构类型 -->
      <view class="form-section">
        <view class="section-title">架构类型</view>
        <view class="type-list">
          <view
            class="type-item"
            :class="{ 'type-item--active': selectedType === item.key }"
            v-for="item in archTypes"
            :key="item.key"
            @tap="selectedType = item.key"
          >
            <view class="type-icon-wrap" :style="{ background: item.bgColor }">
              <text class="type-icon">{{ item.icon }}</text>
            </view>
            <text class="type-label" :class="{ 'type-label--active': selectedType === item.key }">{{ item.label }}</text>
          </view>
        </view>
      </view>

      <!-- 风格选择 -->
      <view class="form-section">
        <view class="section-title">风格选择</view>
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
        </view>
      </view>

      <!-- 复杂度 -->
      <view class="form-section">
        <view class="section-title">复杂度</view>
        <view class="complexity-list">
          <view
            class="complexity-item"
            :class="{ 'complexity-item--active': selectedComplexity === item.key }"
            v-for="item in complexities"
            :key="item.key"
            @tap="selectedComplexity = item.key"
          >
            <view class="complexity-icon">
              <text class="complexity-icon-text">{{ item.icon }}</text>
            </view>
            <view class="complexity-info">
              <text class="complexity-title">{{ item.label }}</text>
              <text class="complexity-desc">{{ item.desc }}</text>
            </view>
          </view>
        </view>
      </view>

      <!-- 输出设置 -->
      <view class="form-section">
        <view class="section-title">输出设置</view>
        <view class="output-list">
          <view
            class="output-item"
            :class="{ 'output-item--active': selectedOutput === item.key }"
            v-for="item in outputs"
            :key="item.key"
            @tap="selectedOutput = item.key"
          >
            <view class="output-icon">
              <text class="output-icon-text">{{ item.icon }}</text>
            </view>
            <view class="output-info">
              <text class="output-title">{{ item.label }}</text>
              <text class="output-desc">{{ item.desc }}</text>
            </view>
          </view>
        </view>
      </view>

      <!-- 生成按钮 -->
      <view class="generate-btn" @tap="generateArchitecture">
        <text class="generate-btn-text">✦ 开始生成架构图</text>
      </view>


    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'

const description = ref('')
const selectedType = ref('system')
const selectedStyle = ref('modern')
const selectedComplexity = ref('medium')
const selectedOutput = ref('auto')

const archTypes = [
  { key: 'system', label: '系统架构', icon: '🖥', bgColor: '#EEF0FF' },
  { key: 'business', label: '业务架构', icon: '', bgColor: '#E8FFF0' },
  { key: 'tech', label: '技术架构', icon: '⚙', bgColor: '#FFF3E0' },
  { key: 'data', label: '数据架构', icon: '🗄', bgColor: '#F3E8FF' },
  { key: 'network', label: '网络架构', icon: '🌐', bgColor: '#FFE8F0' },
]

const styles = [
  { key: 'modern', label: '简约现代', previewBg: 'linear-gradient(135deg, #F8F9FA 0%, #FFF 50%, #F0F2F5 100%)' },
  { key: 'tech', label: '科技蓝', previewBg: 'linear-gradient(135deg, #1a3a5c 0%, #2d5a87 50%, #1a3a5c 100%)' },
  { key: 'flat', label: '扁平卡片', previewBg: 'linear-gradient(135deg, #FFF5E6 0%, #FFE8CC 50%, #FFF0DB 100%)' },
  { key: 'handdrawn', label: '手绘风格', previewBg: 'linear-gradient(135deg, #F0FFF0 0%, #E8FFE8 50%, #F5FFF5 100%)' },
  { key: 'dark', label: '深色模式', previewBg: 'linear-gradient(135deg, #1a1a2e 0%, #16213e 50%, #0f3460 100%)' },
]

const complexities = [
  { key: 'simple', label: '简单', desc: '1-2 层结构', icon: '📦' },
  { key: 'medium', label: '中等', desc: '3-5 层结构', icon: '📊' },
  { key: 'complex', label: '复杂', desc: '5 层以上结构', icon: '🏗' },
]

const outputs = [
  { key: 'auto', label: '自动布局', desc: '智能优化布局', icon: '🔧' },
  { key: 'custom', label: '自定义布局', desc: '自由拖拽调整', icon: '' },
]

const goBack = () => { uni.navigateBack() }
const goHistory = () => { uni.showToast({ title: '历史记录', icon: 'none' }) }
const aiWrite = () => { uni.showToast({ title: 'AI帮你写', icon: 'none' }) }

const generateArchitecture = () => {
  if (!description.value.trim()) {
    uni.showToast({ title: '请输入架构描述', icon: 'none' })
    return
  }
  uni.navigateTo({
    url: `/subpackage_ai/architecturePreview/architecturePreview?desc=${encodeURIComponent(description.value)}&type=${selectedType.value}&style=${selectedStyle.value}&complexity=${selectedComplexity.value}`
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
  padding: 20rpx 24rpx;
  background: #FFF;
  position: sticky;
  top: 0;
  z-index: 100;
  gap: 16rpx;
}

.nav-back {
  width: 60rpx;
  height: 60rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
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
  flex: 1;
  text-align: center;
}

.nav-history {
  display: flex;
  align-items: center;
  gap: 6rpx;
  padding: 10rpx 16rpx;
  background: #F5F5F5;
  border-radius: 8rpx;
}

.nav-history-icon {
  font-size: 24rpx;
}

.nav-history-text {
  font-size: 24rpx;
  color: #555;
}

.content {
  padding: 20rpx 24rpx 40rpx;
}

/* 头部卡片 */
.header-card {
  background: linear-gradient(135deg, #EEF0FF 0%, #F8F9FF 100%);
  border-radius: 16rpx;
  padding: 32rpx 28rpx;
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20rpx;
}

.header-info {
  flex: 1;
}

.header-title {
  font-size: 34rpx;
  font-weight: 700;
  color: #222;
  display: block;
  margin-bottom: 8rpx;
}

.header-subtitle {
  font-size: 24rpx;
  color: #888;
  display: block;
}

.header-icon {
  width: 100rpx;
  height: 100rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.header-icon-text {
  font-size: 64rpx;
}

/* 表单区块 */
.form-section {
  background: #FFF;
  border-radius: 16rpx;
  padding: 28rpx 24rpx;
  margin-bottom: 20rpx;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20rpx;
}

.section-title {
  font-size: 30rpx;
  font-weight: 700;
  color: #222;
}

.ai-write-btn {
  display: flex;
  align-items: center;
  gap: 6rpx;
  padding: 8rpx 16rpx;
  background: #EEF0FF;
  border-radius: 8rpx;
}

.ai-write-icon {
  font-size: 22rpx;
  color: #4D6BFE;
}

.ai-write-text {
  font-size: 24rpx;
  color: #4D6BFE;
  font-weight: 600;
}

/* 输入框 */
.textarea-wrapper {
  position: relative;
}

.desc-input {
  width: 100%;
  min-height: 200rpx;
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

/* 架构类型 */
.type-list {
  display: flex;
  gap: 16rpx;
  overflow-x: auto;
  padding-bottom: 8rpx;
}

.type-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12rpx;
  min-width: 130rpx;
}

.type-icon-wrap {
  width: 80rpx;
  height: 80rpx;
  border-radius: 16rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 2rpx solid transparent;
}

.type-item--active .type-icon-wrap {
  border-color: #4D6BFE;
}

.type-icon {
  font-size: 36rpx;
}

.type-label {
  font-size: 24rpx;
  color: #555;
  text-align: center;
}

.type-label--active {
  color: #4D6BFE;
  font-weight: 600;
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

.style-label {
  font-size: 24rpx;
  color: #555;
  text-align: center;
}

.style-label--active {
  color: #4D6BFE;
  font-weight: 600;
}

/* 复杂度 */
.complexity-list {
  display: flex;
  gap: 16rpx;
}

.complexity-item {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 12rpx;
  padding: 20rpx 16rpx;
  border-radius: 12rpx;
  border: 2rpx solid #F0F0F0;
}

.complexity-item--active {
  border-color: #4D6BFE;
  background: #F8F9FF;
}

.complexity-icon {
  width: 56rpx;
  height: 56rpx;
  background: #F5F5F5;
  border-radius: 12rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.complexity-item--active .complexity-icon {
  background: #EEF0FF;
}

.complexity-icon-text {
  font-size: 28rpx;
}

.complexity-info {
  flex: 1;
}

.complexity-title {
  font-size: 28rpx;
  color: #222;
  font-weight: 600;
  display: block;
}

.complexity-desc {
  font-size: 22rpx;
  color: #999;
  display: block;
  margin-top: 4rpx;
}

/* 输出设置 */
.output-list {
  display: flex;
  gap: 16rpx;
}

.output-item {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 12rpx;
  padding: 20rpx 16rpx;
  border-radius: 12rpx;
  border: 2rpx solid #F0F0F0;
}

.output-item--active {
  border-color: #4D6BFE;
  background: #F8F9FF;
}

.output-icon {
  width: 56rpx;
  height: 56rpx;
  background: #F5F5F5;
  border-radius: 12rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.output-item--active .output-icon {
  background: #EEF0FF;
}

.output-icon-text {
  font-size: 28rpx;
}

.output-info {
  flex: 1;
}

.output-title {
  font-size: 28rpx;
  color: #222;
  font-weight: 600;
  display: block;
}

.output-desc {
  font-size: 22rpx;
  color: #999;
  display: block;
  margin-top: 4rpx;
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
