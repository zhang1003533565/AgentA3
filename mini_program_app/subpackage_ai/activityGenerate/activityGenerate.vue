<template>
  <view class="page">
    <!-- 导航栏 -->
    <view class="nav-bar">
      <view class="nav-back" @tap="goBack">
        <text class="nav-back-icon">‹</text>
      </view>
      <view class="nav-title-wrapper">
        <text class="nav-title">生成活动图</text>
        <text class="nav-subtitle">选择生成方式，快速生成专业的活动图</text>
      </view>
    </view>

    <view class="content">
      <!-- 步骤条 -->
      <view class="steps">
        <view class="step" :class="{ 'step--active': currentStep >= 1 }">
          <view class="step-circle" :class="{ 'step-circle--active': currentStep >= 1 }">
            <text class="step-num">1</text>
          </view>
          <text class="step-label" :class="{ 'step-label--active': currentStep >= 1 }">选择生成方式</text>
        </view>
        <view class="step-line" :class="{ 'step-line--active': currentStep >= 2 }"></view>
        <view class="step" :class="{ 'step--active': currentStep >= 2 }">
          <view class="step-circle" :class="{ 'step-circle--active': currentStep >= 2 }">
            <text class="step-num">2</text>
          </view>
          <text class="step-label" :class="{ 'step-label--active': currentStep >= 2 }">生成图片预览</text>
        </view>
        <view class="step-line" :class="{ 'step-line--active': currentStep >= 3 }"></view>
        <view class="step" :class="{ 'step--active': currentStep >= 3 }">
          <view class="step-circle" :class="{ 'step-circle--active': currentStep >= 3 }">
            <text class="step-num">3</text>
          </view>
          <text class="step-label" :class="{ 'step-label--active': currentStep >= 3 }">完成</text>
        </view>
      </view>

      <!-- 选择描述词来源 -->
      <view class="form-section">
        <view class="section-title section-title-with-history"><view class="inline-history-action" @tap="openHistory"><text>历史记录</text></view><text>选择描述词来源</text></view>
        <view class="source-options">
          <view
            class="source-option"
            :class="{ 'source-option--active': descSource === 'ai' }"
            @tap="descSource = 'ai'"
          >
            <view class="source-icon source-icon--ai">
              <text class="source-icon-text">✦</text>
            </view>
            <view class="source-info">
              <text class="source-title">AI 生成描述词</text>
              <text class="source-desc">让 AI 帮你生成专业的描述词</text>
            </view>
            <view class="source-check" v-if="descSource === 'ai'">
              <text class="source-check-icon">✓</text>
            </view>
          </view>
          <view
            class="source-option"
            :class="{ 'source-option--active': descSource === 'custom' }"
            @tap="descSource = 'custom'"
          >
            <view class="source-icon source-icon--custom">
              <text class="source-icon-text">✎</text>
            </view>
            <view class="source-info">
              <text class="source-title">自定义描述词</text>
              <text class="source-desc">使用自己的描述词生成图片</text>
            </view>
            <view class="source-check" v-if="descSource === 'custom'">
              <text class="source-check-icon">✓</text>
            </view>
          </view>
        </view>
      </view>

      <!-- 活动图主题或流程描述 -->
      <view class="form-section">
        <view class="section-title">活动图主题或流程描述</view>
        <view class="textarea-wrapper">
          <textarea
            class="desc-input"
            v-model="flowDescription"
            placeholder="请描述活动图的流程，例如：用户注册流程：用户进入注册页面，填写注册信息..."
            :maxlength="500"
          />
          <text class="char-count">{{ flowDescription.length }}/500</text>
        </view>
      </view>

      <!-- 风格偏好 -->
      <view class="form-section">
        <view class="section-title">风格偏好</view>
        <view class="style-list">
          <view
            class="style-item"
            :class="{ 'style-item--active': selectedStyle === item.key }"
            v-for="item in styles"
            :key="item.key"
            @tap="selectedStyle = item.key"
          >
            <view class="style-icon-wrap" :style="{ background: item.bgColor }">
              <text class="style-icon">{{ item.icon }}</text>
            </view>
            <text class="style-label">{{ item.label }}</text>
            <view class="style-check" v-if="selectedStyle === item.key">
              <text class="style-check-icon">✓</text>
            </view>
          </view>
          <view class="style-item" @tap="showMoreStyles">
            <view class="style-icon-wrap style-icon-wrap--more">
              <text class="style-more-text">···</text>
            </view>
            <text class="style-label">更多风格</text>
          </view>
        </view>
      </view>

      <!-- 图表设置 -->
      <view class="form-section">
        <view class="section-title">图表设置 <text class="section-optional">（可选）</text></view>
        <view class="chart-settings">
          <view class="setting-row">
            <view class="setting-item">
              <text class="setting-label">图表类型</text>
              <picker :range="chartTypes" @change="onChartTypeChange">
                <view class="setting-picker">
                  <text class="setting-value">{{ selectedChartType }}</text>
                  <text class="setting-arrow">∨</text>
                </view>
              </picker>
            </view>
            <view class="setting-item">
              <text class="setting-label">配色方案</text>
              <picker :range="colorSchemes" @change="onColorSchemeChange">
                <view class="setting-picker">
                  <view class="color-dot" :style="{ background: selectedColorDot }"></view>
                  <text class="setting-value">{{ selectedColorScheme }}</text>
                  <text class="setting-arrow">∨</text>
                </view>
              </picker>
            </view>
          </view>
        </view>
      </view>

      <!-- 图片比例 -->
      <view class="form-section">
        <view class="section-title">图片比例</view>
        <view class="ratio-list">
          <view
            class="ratio-item"
            :class="{ 'ratio-item--active': selectedRatio === item }"
            v-for="item in ratios"
            :key="item"
            @tap="selectedRatio = item"
          >
            <text class="ratio-text">{{ item }}</text>
          </view>
          <view class="ratio-item ratio-item--custom" @tap="customRatio">
            <text class="ratio-text">⚙</text>
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
            <text class="advanced-item-label">泳道数量</text>
            <picker :range="laneOptions" @change="onLaneChange">
              <text class="advanced-item-value">{{ selectedLanes }} ›</text>
            </picker>
          </view>
          <view class="advanced-item">
            <text class="advanced-item-label">节点样式</text>
            <picker :range="nodeStyleOptions" @change="onNodeStyleChange">
              <text class="advanced-item-value">{{ selectedNodeStyle }} ›</text>
            </picker>
          </view>
        </view>
      </view>

      <!-- 生成按钮 -->
      <view class="recent-card">
        <view class="recent-head">
          <view class="recent-head__icon">
            <image src="/static/icons/diagram/flow-white.svg" mode="aspectFit" />
          </view>
          <text>最近生成</text>
        </view>
        <view class="recent-empty">暂无活动图历史</view>
        <view class="recent-all" @tap="openHistory">
          <text>查看全部历史</text>
          <text class="recent-arrow">›</text>
        </view>
      </view>

      <view class="generate-btn" @tap="generateDescription">
        <text class="generate-btn-text">✦ 生成描述词</text>
      </view>


    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'

const currentStep = ref(1)
const descSource = ref('ai')
const flowDescription = ref('用户注册流程：用户进入注册页面，填写注册信息，系统验证信息是否正确，验证通过后创建用户账号，发送验证邮件，用户点击邮件链接完成验证，注册成功。')
const selectedStyle = ref('modern')
const selectedChartType = ref('活动图（泳道图）')
const selectedColorScheme = ref('蓝绿系')
const selectedColorDot = ref('#4D6BFE')
const selectedRatio = ref('16:9')
const showAdvanced = ref(false)
const selectedLanes = ref('3个')
const selectedNodeStyle = ref('圆角矩形')

const styles = [
  { key: 'modern', label: '现代简约', icon: '✦', bgColor: '#EEF0FF' },
  { key: 'business', label: '商务科技', icon: '◈', bgColor: '#E8F8F0' },
  { key: 'handdrawn', label: '手绘风格', icon: '✿', bgColor: '#FFF3E0' },
  { key: 'flat', label: '扁平彩色', icon: '◉', bgColor: '#F3E8FF' },
]

const chartTypes = ['活动图（泳道图）', '活动图（单泳道）', '流程图']
const colorSchemes = ['蓝绿系', '暖橙系', '紫粉系', '灰黑系']
const colorDots = { '蓝绿系': '#4D6BFE', '暖橙系': '#FF9F43', '紫粉系': '#A55EEA', '灰黑系': '#5C7A99' }
const ratios = ['16:9', '4:3', '1:1']
const laneOptions = ['2个', '3个', '4个', '5个']
const nodeStyleOptions = ['圆角矩形', '矩形', '椭圆形']

const goBack = () => { uni.navigateBack() }
const openHistory = () => { uni.navigateTo({ url: '/subpackage_ai/diagramHistory/diagramHistory' }) }
const showMoreStyles = () => { uni.showToast({ title: '更多风格', icon: 'none' }) }
const customRatio = () => { uni.showToast({ title: '自定义比例', icon: 'none' }) }
const onChartTypeChange = (e) => { selectedChartType.value = chartTypes[e.detail.value] }
const onColorSchemeChange = (e) => {
  selectedColorScheme.value = colorSchemes[e.detail.value]
  selectedColorDot.value = colorDots[selectedColorScheme.value]
}
const onLaneChange = (e) => { selectedLanes.value = laneOptions[e.detail.value] }
const onNodeStyleChange = (e) => { selectedNodeStyle.value = nodeStyleOptions[e.detail.value] }

const generateDescription = () => {
  if (!flowDescription.value.trim()) {
    uni.showToast({ title: '请输入流程描述', icon: 'none' })
    return
  }
  // 跳转到预览页
  const activityHistory = uni.getStorageSync('aiActivityHistory') || []
  activityHistory.unshift({ id: Date.now(), title: '活动图', description: flowDescription.value, createTime: new Date().toISOString() })
  uni.setStorageSync('aiActivityHistory', activityHistory.slice(0, 20))
  uni.navigateTo({
    url: `/subpackage_ai/activityPreview/activityPreview?desc=${encodeURIComponent(flowDescription.value)}&style=${selectedStyle.value}&chartType=${encodeURIComponent(selectedChartType.value)}&colorScheme=${encodeURIComponent(selectedColorScheme.value)}&ratio=${selectedRatio.value}`
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
  position: relative;
  display: flex;
  align-items: center;
  height: 128rpx;
  padding: 40rpx 28rpx 0;
  box-sizing: border-box;
  background: linear-gradient(180deg, #dff0ff 0%, #eaf5ff 100%);
  position: sticky;
  top: 0;
  z-index: 100;
  gap: 16rpx;
  border-bottom: 0;
}

.nav-back {
  position: relative;
  width: 64rpx;
  height: 64rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.nav-back-icon {
  font-size: 0;
  line-height: 0;
  color: transparent;
}

.nav-back::before {
  content: '';
  width: 20rpx;
  height: 20rpx;
  border-left: 4rpx solid #1D1D1F;
  border-bottom: 4rpx solid #1D1D1F;
  transform: rotate(45deg);
  border-radius: 2rpx;
  box-sizing: border-box;
}

.nav-title-wrapper {
  position: absolute;
  left: 112rpx;
  right: 112rpx;
  top: calc(50% + 20rpx);
  transform: translateY(-50%);
  min-width: 0;
  text-align: center;
}

.nav-title {
  font-size: 32rpx;
  font-weight: 700;
  color: #222;
  display: block;
  line-height: 1.2;
  text-align: center;
}

.nav-subtitle {
  font-size: 22rpx;
  color: #999;
  display: block;
  margin-top: 4rpx;
  display: none;
}

.nav-placeholder {
  width: 60rpx;
  flex-shrink: 0;
}

.content {
  padding: 20rpx 24rpx 40rpx;
}

/* 步骤条 */
.steps {
  display: flex;
  align-items: center;
  padding: 20rpx 0 32rpx;
  background: #FFF;
  border-radius: 16rpx;
  padding: 24rpx;
  margin-bottom: 20rpx;
}

.step {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8rpx;
}

.step-circle {
  width: 48rpx;
  height: 48rpx;
  border-radius: 50%;
  background: #E8E8E8;
  display: flex;
  align-items: center;
  justify-content: center;
}

.step-circle--active {
  background: #4D6BFE;
}

.step-num {
  font-size: 24rpx;
  color: #FFF;
  font-weight: 700;
}

.step-label {
  font-size: 22rpx;
  color: #999;
}

.step-label--active {
  color: #4D6BFE;
  font-weight: 600;
}

.step-line {
  flex: 1;
  height: 4rpx;
  background: #E8E8E8;
  margin: 0 16rpx;
  margin-bottom: 24rpx;
}

.step-line--active {
  background: #4D6BFE;
}

/* 表单区块 */
.form-section {
  background: #FFF;
  border-radius: 16rpx;
  padding: 28rpx 24rpx;
  margin-bottom: 20rpx;
}

.section-title {
  font-size: 30rpx;
  font-weight: 700;
  color: #222;
  margin-bottom: 20rpx;
}

.section-optional {
  font-size: 24rpx;
  color: #999;
  font-weight: 400;
}

/* 描述词来源 */
.source-options {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

.source-option {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 24rpx;
  border-radius: 12rpx;
  border: 2rpx solid #F0F0F0;
  position: relative;
}

.source-option--active {
  border-color: #4D6BFE;
  background: #F8F9FF;
}

.source-icon {
  width: 72rpx;
  height: 72rpx;
  border-radius: 16rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.source-icon--ai {
  background: #EEF0FF;
}

.source-icon--custom {
  background: #E8FFF0;
}

.source-icon-text {
  font-size: 36rpx;
}

.source-info {
  flex: 1;
}

.source-title {
  font-size: 28rpx;
  color: #222;
  font-weight: 600;
  display: block;
}

.source-desc {
  font-size: 24rpx;
  color: #999;
  display: block;
  margin-top: 6rpx;
}

.source-check {
  width: 40rpx;
  height: 40rpx;
  background: #4D6BFE;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.source-check-icon {
  font-size: 22rpx;
  color: #FFF;
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
  min-width: 130rpx;
  position: relative;
}

.style-icon-wrap {
  width: 80rpx;
  height: 80rpx;
  border-radius: 16rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 2rpx solid transparent;
}

.style-item--active .style-icon-wrap {
  border-color: #4D6BFE;
}

.style-icon {
  font-size: 36rpx;
}

.style-icon-wrap--more {
  background: #F5F5F5;
}

.style-more-text {
  font-size: 28rpx;
  color: #999;
}

.style-label {
  font-size: 24rpx;
  color: #555;
  text-align: center;
}

.style-check {
  position: absolute;
  top: 0;
  right: 8rpx;
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

/* 图表设置 */
.chart-settings {
  display: flex;
  flex-direction: column;
  gap: 20rpx;
}

.setting-row {
  display: flex;
  gap: 20rpx;
}

.setting-item {
  flex: 1;
}

.setting-label {
  font-size: 26rpx;
  color: #888;
  display: block;
  margin-bottom: 12rpx;
}

.setting-picker {
  display: flex;
  align-items: center;
  gap: 8rpx;
  padding: 16rpx 20rpx;
  background: #F8F9FA;
  border-radius: 10rpx;
}

.color-dot {
  width: 24rpx;
  height: 24rpx;
  border-radius: 50%;
  flex-shrink: 0;
}

.setting-value {
  font-size: 26rpx;
  color: #333;
  flex: 1;
}

.setting-arrow {
  font-size: 20rpx;
  color: #999;
}

/* 图片比例 */
.ratio-list {
  display: flex;
  gap: 16rpx;
}

.ratio-item {
  flex: 1;
  padding: 18rpx 0;
  text-align: center;
  background: #F8F9FA;
  border-radius: 12rpx;
  border: 2rpx solid transparent;
}

.ratio-item--active {
  background: #EEF0FF;
  border-color: #4D6BFE;
}

.ratio-item--custom {
  background: #F5F5F5;
}

.ratio-text {
  font-size: 26rpx;
  color: #555;
}

.ratio-item--active .ratio-text {
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

/* 与架构图页面统一视觉基准 */
.page { background: #F6F7FB; color: #172033; }
.form-section { border: 1rpx solid #E6E8F1; border-radius: 22rpx; background: #FFFFFF; box-shadow: 0 10rpx 28rpx rgba(20, 28, 48, 0.045); }
.form-section + .form-section { margin-top: 18rpx; }
.textarea-wrapper, .setting-picker, .ratio-item { border-color: #DBE1ED; background: #FFFFFF; }
.desc-input { color: #172033; }
.section-title { color: #182033; }
.source-option, .style-item, .setting-option { border-color: #DBE1ED; }
.source-option--active, .style-item--active { border-color: #123E6D; background: #F3F7FC; }
.generate-btn { background: linear-gradient(180deg, #1D5D91 0%, #123E6D 100%); border-radius: 13rpx; box-shadow: 0 10rpx 22rpx rgba(18, 62, 109, 0.18); }
.content { padding: 20rpx 24rpx 40rpx; }
.form-section { padding: 24rpx; margin-bottom: 18rpx; }
.form-section + .form-section { margin-top: 0; }
.section-title { margin-bottom: 17rpx; font-size: 27rpx; line-height: 1.25; font-weight: 800; }
.section-title { display: flex; align-items: center; gap: 12rpx; }
.section-title::before { content: ''; display: inline-block; width: 26rpx; height: 26rpx; flex-shrink: 0; border: 3rpx solid #123E6D; border-radius: 7rpx; box-sizing: border-box; background: linear-gradient(90deg, transparent 42%, #123E6D 42%, #123E6D 58%, transparent 58%), linear-gradient(0deg, transparent 42%, #123E6D 42%, #123E6D 58%, transparent 58%); }
.content > .form-section:nth-child(2) .section-title::before { border-radius: 50%; background: radial-gradient(circle at center, #123E6D 0 28%, transparent 31%); }
.content > .form-section:nth-child(3) .section-title::before { border-radius: 5rpx; transform: rotate(-45deg); background: linear-gradient(135deg, transparent 38%, #123E6D 39% 52%, transparent 53%); }
.content > .form-section:nth-child(4) .section-title::before { border: 0; background: radial-gradient(circle at 25% 25%, #123E6D 0 12%, transparent 14%), radial-gradient(circle at 75% 25%, #123E6D 0 12%, transparent 14%), radial-gradient(circle at 25% 75%, #123E6D 0 12%, transparent 14%), radial-gradient(circle at 75% 75%, #123E6D 0 12%, transparent 14%); }
.content > .form-section:nth-child(5) .section-title::before { border-radius: 4rpx; background: linear-gradient(#123E6D 0 3rpx, transparent 3rpx 10rpx, #123E6D 10rpx 13rpx, transparent 13rpx); }
.content > .form-section:nth-child(6) .section-title::before { border: 0; background: linear-gradient(#123E6D 0 3rpx, transparent 3rpx 11rpx, #123E6D 11rpx 14rpx, transparent 14rpx 22rpx, #123E6D 22rpx 25rpx); }
.textarea-wrapper { border-radius: 13rpx; }
.desc-input { min-height: 188rpx; padding: 20rpx 22rpx; font-size: 25rpx; line-height: 1.62; }
.source-option, .style-item, .ratio-item, .setting-picker { border-color: #DBE1ED; background: #FFFFFF; }
.source-option--active, .style-item--active, .ratio-item--active { border-color: #123E6D; background: #F3F7FC; }
.source-title, .style-label, .setting-label, .advanced-title { color: #182033; font-weight: 800; }
.source-desc, .setting-value, .advanced-item-label { color: #748096; }
.style-item--active .style-label, .ratio-item--active .ratio-text { color: #123E6D; }
.step-circle--active, .step-line--active { background: #123E6D; }
.step-label--active { color: #123E6D; }
.source-option--active { border-color: #123E6D; background: #F3F7FC; }
.source-icon--ai, .source-icon--custom { background: #EEF4FC; }
.source-icon-text { color: #123E6D; }
.source-check, .style-check { background: #123E6D; }
.desc-input { background: #FFFFFF; border: 1rpx solid #DBE1ED; border-radius: 13rpx; }
.style-icon-wrap, .style-icon-wrap--more { background: #EEF4FC !important; border-color: transparent; }
.style-item--active .style-icon-wrap { border-color: #123E6D; }
.style-icon, .style-more-text, .style-label { color: #182033; }
.style-check-icon { color: #FFFFFF; }
.settings-panel, .recent-card { border-color: #E6E8F1; }
.recent-card { padding: 22rpx 0 18rpx; margin: 0 0 18rpx; border: 1rpx solid #E6E8F1; border-radius: 22rpx; background: #FFFFFF; box-shadow: 0 10rpx 28rpx rgba(20, 28, 48, 0.045); }
.recent-head { display: flex; align-items: center; gap: 14rpx; padding: 0 24rpx 18rpx; color: #182033; font-size: 27rpx; font-weight: 800; }
.recent-head__icon { width: 42rpx; height: 42rpx; display: flex; align-items: center; justify-content: center; border-radius: 12rpx; background: #123E6D; }
.recent-head__icon image { width: 26rpx; height: 26rpx; }
.recent-empty { padding: 28rpx 24rpx; border-top: 1rpx solid #EDF0F5; border-bottom: 1rpx solid #EDF0F5; text-align: center; color: #8A98AD; font-size: 25rpx; }
.recent-all { display: flex; align-items: center; justify-content: center; gap: 8rpx; margin: 16rpx 24rpx 0; height: 56rpx; border: 1rpx solid #DBE1ED; border-radius: 12rpx; color: #182033; font-size: 25rpx; font-weight: 700; }
.recent-arrow { font-size: 32rpx; line-height: 1; }
.nav-history-action { width: 64rpx; height: 64rpx; display: flex; align-items: center; justify-content: center; margin-left: auto; border-radius: 999rpx; }
.nav-history-icon { width: 35rpx; height: 35rpx; opacity: .95; }
.section-title-with-history { display: flex; align-items: center; gap: 8rpx; }
.section-title-with-history .inline-history-action { margin-right: 0; order: -1; }
.inline-history-action { min-width: 112rpx; height: 42rpx; padding: 0 12rpx; display: inline-flex; align-items: center; justify-content: center; color: #123E6D; background: #EEF4FC; border: 1rpx solid #C9D9EC; border-radius: 14rpx; box-sizing: border-box; font-size: 23rpx; font-weight: 700; white-space: nowrap; }
</style>
