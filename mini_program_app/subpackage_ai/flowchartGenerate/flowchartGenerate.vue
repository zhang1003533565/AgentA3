<template>
  <view class="page">
    <!-- 导航栏 -->
    <view class="nav-bar">
      <view class="nav-back" @tap="goBack">
        <text class="nav-back-icon">‹</text>
      </view>
      <text class="nav-title">流程图生成</text>
      <view class="nav-placeholder"></view>
    </view>

    <view class="content">
      <!-- 输入内容 -->
      <view class="form-section">
        <view class="section-header">
          <view class="section-title-wrapper">
            <view class="section-icon">
              <text class="section-icon-text">📋</text>
            </view>
            <text class="section-title">输入内容</text>
          </view>
        </view>

        <!-- 步骤/流程描述 -->
        <view class="desc-header">
          <text class="desc-label">步骤/流程描述</text>
          <view class="clear-btn" @tap="clearDesc">
            <text class="clear-icon">✕</text>
            <text class="clear-text">清空</text>
          </view>
        </view>
        <text class="desc-hint">输入要整理的步骤、算法或业务过程</text>
        <view class="textarea-wrapper">
          <textarea
            class="desc-input"
            v-model="flowDescription"
            placeholder="1. 用户提交订单&#10;2. 系统验证库存&#10;3. 库存充足则进入支付流程&#10;4. 用户完成支付&#10;5. 生成订单并扣减库存&#10;6. 发送订单确认通知"
            :maxlength="1000"
          />
          <text class="char-count">{{ flowDescription.length }}/1000</text>
        </view>

        <!-- 边界说明 -->
        <view class="info-box">
          <view class="info-icon">
            <text class="info-icon-text">🛡</text>
          </view>
          <view class="info-content">
            <text class="info-title">边界说明</text>
            <text class="info-text">仅基于输入内容生成流程图，不会补充输入之外的节点或判断条件</text>
          </view>
        </view>
      </view>

      <!-- 图表类型 -->
      <view class="form-section">
        <view class="section-title">图表类型</view>
        <view class="type-list">
          <view
            class="type-item"
            :class="{ 'type-item--active': selectedType === 'flowchart' }"
            @tap="selectedType = 'flowchart'"
          >
            <view class="type-radio" :class="{ 'type-radio--active': selectedType === 'flowchart' }">
              <view class="type-radio-dot" v-if="selectedType === 'flowchart'"></view>
            </view>
            <view class="type-info">
              <text class="type-name">流程图 (flowchart)</text>
              <text class="type-desc">用于步骤顺序、业务流程</text>
            </view>
          </view>
          <view
            class="type-item"
            :class="{ 'type-item--active': selectedType === 'graph' }"
            @tap="selectedType = 'graph'"
          >
            <view class="type-radio" :class="{ 'type-radio--active': selectedType === 'graph' }">
              <view class="type-radio-dot" v-if="selectedType === 'graph'"></view>
            </view>
            <view class="type-info">
              <text class="type-name">关系图 (graph)</text>
              <text class="type-desc">用于元素关系、网络结构</text>
            </view>
          </view>
        </view>
      </view>

      <!-- 风格主题 -->
      <view class="form-section">
        <view class="section-title">风格主题</view>
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

      <!-- 方向布局 -->
      <view class="form-section">
        <view class="section-title">方向布局</view>
        <view class="direction-list">
          <view
            class="direction-item"
            :class="{ 'direction-item--active': selectedDirection === item }"
            v-for="item in directions"
            :key="item"
            @tap="selectedDirection = item"
          >
            <text class="direction-text">{{ item }}</text>
          </view>
        </view>
      </view>

      <!-- 生成按钮 -->
      <view class="generate-btn" @tap="generateFlowchart">
        <text class="generate-btn-text">✦ 生成流程图</text>
      </view>


    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'

const flowDescription = ref('1. 用户提交订单\n2. 系统验证库存\n3. 库存充足则进入支付流程\n4. 用户完成支付\n5. 生成订单并扣减库存\n6. 发送订单确认通知')
const selectedType = ref('flowchart')
const selectedStyle = ref('default')
const selectedDirection = ref('自上而下')

const styles = [
  { key: 'default', label: '默认简洁', previewBg: 'linear-gradient(135deg, #F0F4FF 0%, #FFF 50%, #F0F4FF 100%)' },
  { key: 'fresh', label: '清新明快', previewBg: 'linear-gradient(135deg, #F0FFF4 0%, #FFF 50%, #F0FFF4 100%)' },
  { key: 'business', label: '商务专业', previewBg: 'linear-gradient(135deg, #FFF8F0 0%, #FFF 50%, #FFF8F0 100%)' },
  { key: 'dark', label: '深色模式', previewBg: 'linear-gradient(135deg, #1a1a2e 0%, #16213e 50%, #0f3460 100%)' },
]

const directions = ['自上而下', '自左而右', '自下而上', '自右而左']

const goBack = () => { uni.navigateBack() }
const clearDesc = () => { flowDescription.value = '' }

const generateFlowchart = () => {
  if (!flowDescription.value.trim()) {
    uni.showToast({ title: '请输入流程描述', icon: 'none' })
    return
  }
  uni.navigateTo({
    url: `/subpackage_ai/flowchartPreview/flowchartPreview?desc=${encodeURIComponent(flowDescription.value)}&type=${selectedType.value}&style=${selectedStyle.value}&direction=${encodeURIComponent(selectedDirection.value)}`
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

.nav-placeholder {
  width: 60rpx;
  flex-shrink: 0;
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

.section-header {
  margin-bottom: 20rpx;
}

.section-title-wrapper {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.section-icon {
  width: 48rpx;
  height: 48rpx;
  background: #EEF0FF;
  border-radius: 12rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.section-icon-text {
  font-size: 24rpx;
}

.section-title {
  font-size: 30rpx;
  font-weight: 700;
  color: #222;
}

/* 描述 */
.desc-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8rpx;
}

.desc-label {
  font-size: 28rpx;
  font-weight: 600;
  color: #222;
}

.clear-btn {
  display: flex;
  align-items: center;
  gap: 6rpx;
  padding: 6rpx 12rpx;
  background: #F5F5F5;
  border-radius: 6rpx;
}

.clear-icon {
  font-size: 20rpx;
  color: #999;
}

.clear-text {
  font-size: 22rpx;
  color: #999;
}

.desc-hint {
  font-size: 24rpx;
  color: #999;
  display: block;
  margin-bottom: 16rpx;
}

.textarea-wrapper {
  position: relative;
}

.desc-input {
  width: 100%;
  min-height: 240rpx;
  padding: 20rpx;
  background: #F8F9FA;
  border-radius: 12rpx;
  font-size: 28rpx;
  color: #333;
  line-height: 1.8;
  box-sizing: border-box;
}

.char-count {
  position: absolute;
  right: 16rpx;
  bottom: 12rpx;
  font-size: 24rpx;
  color: #BBB;
}

/* 信息框 */
.info-box {
  display: flex;
  align-items: flex-start;
  gap: 12rpx;
  padding: 20rpx;
  background: #F8F9FF;
  border-radius: 12rpx;
  margin-top: 20rpx;
}

.info-icon {
  width: 40rpx;
  height: 40rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.info-icon-text {
  font-size: 24rpx;
}

.info-content {
  flex: 1;
}

.info-title {
  font-size: 26rpx;
  font-weight: 600;
  color: #4D6BFE;
  display: block;
  margin-bottom: 6rpx;
}

.info-text {
  font-size: 24rpx;
  color: #888;
  line-height: 1.5;
}

/* 图表类型 */
.type-list {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

.type-item {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 20rpx;
  border-radius: 12rpx;
  border: 2rpx solid #F0F0F0;
}

.type-item--active {
  border-color: #4D6BFE;
  background: #F8F9FF;
}

.type-radio {
  width: 36rpx;
  height: 36rpx;
  border-radius: 50%;
  border: 2rpx solid #DDD;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.type-radio--active {
  border-color: #4D6BFE;
  background: #4D6BFE;
}

.type-radio-dot {
  width: 16rpx;
  height: 16rpx;
  background: #FFF;
  border-radius: 50%;
}

.type-info {
  flex: 1;
}

.type-name {
  font-size: 28rpx;
  color: #222;
  font-weight: 600;
  display: block;
}

.type-desc {
  font-size: 24rpx;
  color: #999;
  display: block;
  margin-top: 4rpx;
}

/* 风格主题 */
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

/* 方向布局 */
.direction-list {
  display: flex;
  gap: 12rpx;
}

.direction-item {
  flex: 1;
  padding: 18rpx 0;
  text-align: center;
  background: #F8F9FA;
  border-radius: 12rpx;
  border: 2rpx solid transparent;
}

.direction-item--active {
  background: #EEF0FF;
  border-color: #4D6BFE;
}

.direction-text {
  font-size: 26rpx;
  color: #555;
}

.direction-item--active .direction-text {
  color: #4D6BFE;
  font-weight: 600;
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
