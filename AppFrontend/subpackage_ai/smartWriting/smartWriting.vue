<template>
  <view class="page">
    <nav-bar title="智能创作" :showBack="true" :showWechatCapsule="true" />

    <view class="content">
      <!-- Input Section -->
      <view class="input-card">
        <textarea
          class="input-area"
          v-model="prompt"
          maxlength="600"
          placeholder="请输入您的文章需求，例如，请写一篇关于春天的文章，800字左右。"
          placeholder-class="input-placeholder"
        />

        <view class="tool-row">
          <view class="tool-btns">
            <view class="tool-btn">
              <image class="tool-icon-svg" src="/static/icons/line/globe.svg" mode="aspectFit" />
              <text class="tool-text">深度思考</text>
            </view>
            <view class="tool-btn">
              <image class="tool-icon-svg" src="/static/icons/line/atom.svg" mode="aspectFit" />
              <text class="tool-text">联网搜索</text>
            </view>
          </view>
          <view class="history-btn" @tap="showHistory">
            <text class="history-icon">◷</text>
            <text class="history-text">历史</text>
          </view>
        </view>
      </view>

      <!-- Configuration Section -->
      <view class="config-section">
        <!-- Model Select -->
        <view class="config-card model-card" @tap="selectModel">
          <view class="config-left">
            <view class="model-icon-wrapper">
              <image class="model-icon-img" :src="currentModelIcon" mode="aspectFit" />
            </view>
            <view class="model-info">
              <text class="config-label">选择模型</text>
              <text class="config-value">{{ selectedModel }}</text>
            </view>
          </view>
          <text class="arrow-icon">›</text>
        </view>

        <!-- Parameters Grid -->
        <view class="params-grid">
          <view class="param-card" @tap="selectWordCount">
            <view class="param-left">
              <text class="param-icon">▣</text>
              <text class="param-label">字数</text>
            </view>
            <view class="param-right">
              <text class="param-value">{{ selectedWordCount }}</text>
              <text class="param-arrow">▼</text>
            </view>
          </view>

          <view class="param-card" @tap="selectTone">
            <view class="param-left">
              <text class="param-icon">♫</text>
              <text class="param-label">语气</text>
            </view>
            <view class="param-right">
              <text class="param-value">{{ selectedTone }}</text>
              <text class="param-arrow">▼</text>
            </view>
          </view>
        </view>
      </view>

      <!-- Main CTA -->
      <view class="create-btn" @tap="onCreate">AI创作</view>

      <!-- Examples Section -->
      <view class="examples-section">
        <view class="examples-header">
          <view class="header-line"></view>
          <text class="header-text">点击使用示例</text>
          <view class="header-line"></view>
        </view>

        <view class="examples-list">
          <!-- Example 1: 新闻稿 -->
          <view class="example-item">
            <view class="example-header">
              <view class="tag-icon-wrapper">
                <text class="tag-icon">✦</text>
              </view>
              <text class="example-title">新闻稿</text>
            </view>
            <view class="example-content">
              <text class="example-desc">苹果公司宣布取消造车计划</text>
            </view>
          </view>

          <!-- Example 2: 知识科普 -->
          <view class="example-item">
            <view class="example-header">
              <view class="tag-icon-wrapper">
                <text class="tag-icon">✦</text>
              </view>
              <text class="example-title">知识科普</text>
            </view>
            <view class="example-content">
              <text class="example-desc">春季流感如何防治</text>
            </view>
          </view>

          <!-- Example 3: 产品种草 -->
          <view class="example-item">
            <view class="example-header">
              <view class="tag-icon-wrapper">
                <text class="tag-icon">✦</text>
              </view>
              <text class="example-title">产品种草</text>
            </view>
            <view class="example-content">
              <text class="example-desc">兰蔻小黑瓶</text>
            </view>
          </view>
        </view>
      </view>
    </view>

    <!-- Model Selection Popup -->
    <view class="model-popup-mask" v-if="showModelPopup" @tap="closeModelPopup">
      <view class="model-popup" :style="{ height: popupHeight }" @tap.stop>
        <view class="popup-header">
          <text class="popup-title">选择模型</text>
          <view class="popup-close" @tap="closeModelPopup">
            <text class="close-icon">×</text>
          </view>
        </view>
        <scroll-view class="popup-scroll" scroll-y>
          <view class="popup-content">
            <view
              v-for="(model, index) in models"
              :key="index"
              class="model-item"
              :class="{ active: selectedModel === model.name }"
              @tap="selectModelItem(model)"
            >
              <view class="model-item-left">
                <view class="model-item-icon-wrapper">
                  <image class="model-item-icon" :src="model.icon" mode="aspectFit" />
                </view>
                <view class="model-item-info">
                  <text class="model-item-name">{{ model.name }}</text>
                  <text class="model-item-desc">{{ model.desc }}</text>
                </view>
              </view>
              <view class="model-item-check" v-if="selectedModel === model.name">
                <text class="check-icon">✓</text>
              </view>
            </view>
          </view>
        </scroll-view>
      </view>
    </view>

    <!-- Word Count Selection Popup -->
    <view class="model-popup-mask" v-if="showWordCountPopup" @tap="closeWordCountPopup">
      <view class="model-popup" :style="{ height: wordCountPopupHeight }" @tap.stop>
        <view class="popup-header">
          <text class="popup-title">选择字数</text>
          <view class="popup-close" @tap="closeWordCountPopup">
            <text class="close-icon">×</text>
          </view>
        </view>
        <scroll-view class="popup-scroll" scroll-y>
          <view class="popup-content">
            <view
              v-for="(item, index) in wordCountOptions"
              :key="index"
              class="model-item"
              :class="{ active: selectedWordCount === item }"
              @tap="selectWordCountItem(item)"
            >
              <view class="model-item-left">
                <view class="model-item-info">
                  <text class="model-item-name">{{ item }}</text>
                </view>
              </view>
              <view class="model-item-check" v-if="selectedWordCount === item">
                <text class="check-icon">✓</text>
              </view>
            </view>
          </view>
        </scroll-view>
      </view>
    </view>

    <!-- Tone Selection Popup -->
    <view class="model-popup-mask" v-if="showTonePopup" @tap="closeTonePopup">
      <view class="model-popup" :style="{ height: tonePopupHeight }" @tap.stop>
        <view class="popup-header">
          <text class="popup-title">选择语气</text>
          <view class="popup-close" @tap="closeTonePopup">
            <text class="close-icon">×</text>
          </view>
        </view>
        <scroll-view class="popup-scroll" scroll-y>
          <view class="popup-content">
            <view
              v-for="(item, index) in toneOptions"
              :key="index"
              class="model-item"
              :class="{ active: selectedTone === item }"
              @tap="selectToneItem(item)"
            >
              <view class="model-item-left">
                <view class="model-item-info">
                  <text class="model-item-name">{{ item }}</text>
                </view>
              </view>
              <view class="model-item-check" v-if="selectedTone === item">
                <text class="check-icon">✓</text>
              </view>
            </view>
          </view>
        </scroll-view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import NavBar from '@/components/nav-bar/nav-bar.vue'

const prompt = ref('')
const showModelPopup = ref(false)
const showWordCountPopup = ref(false)
const showTonePopup = ref(false)
const selectedModel = ref('DeepSeek')
const selectedWordCount = ref('自动')
const selectedTone = ref('正式')

const wordCountOptions = ref(['自动', '200字以内', '500字左右', '800字以上', '1000字以上'])
const toneOptions = ref(['正式', '幽默', '严谨', '感性', '专业'])

const models = ref([
  {
    name: 'DeepSeek',
    desc: '深度求索，擅长逻辑推理',
    icon: '/static/icons/ai create/DeepSeek.png'
  },
  {
    name: '豆包',
    desc: '字节跳动，多模态能力强',
    icon: '/static/icons/ai create/doubao.png'
  },
  {
    name: '通义千问',
    desc: '阿里巴巴，综合能力出色',
    icon: '/static/icons/ai create/Tongyi-Qianwen.png'
  }
])

const popupHeight = computed(() => {
  const itemHeight = uni.upx2px(146) // 130rpx item + 16rpx margin
  const headerHeight = uni.upx2px(130) // 120rpx header + extra
  const paddingBottom = uni.upx2px(100) // Extra padding for safety
  const count = models.value.length
  const totalHeight = headerHeight + itemHeight * count + paddingBottom
  const systemInfo = uni.getSystemInfoSync()
  const maxHeight = Math.floor(systemInfo.windowHeight * 0.85)
  return Math.min(totalHeight, maxHeight) + 'px'
})

const wordCountPopupHeight = computed(() => {
  const itemHeight = uni.upx2px(112) // Smaller item height for simple list
  const headerHeight = uni.upx2px(130)
  const paddingBottom = uni.upx2px(100)
  const count = wordCountOptions.value.length
  const totalHeight = headerHeight + itemHeight * count + paddingBottom
  const systemInfo = uni.getSystemInfoSync()
  const maxHeight = Math.floor(systemInfo.windowHeight * 0.85)
  return Math.min(totalHeight, maxHeight) + 'px'
})

const tonePopupHeight = computed(() => {
  const itemHeight = uni.upx2px(112)
  const headerHeight = uni.upx2px(130)
  const paddingBottom = uni.upx2px(100)
  const count = toneOptions.value.length
  const totalHeight = headerHeight + itemHeight * count + paddingBottom
  const systemInfo = uni.getSystemInfoSync()
  const maxHeight = Math.floor(systemInfo.windowHeight * 0.85)
  return Math.min(totalHeight, maxHeight) + 'px'
})

const currentModelIcon = computed(() => {
  const model = models.value.find((m) => m.name === selectedModel.value)
  return model ? model.icon : ''
})

const selectModel = () => {
  showModelPopup.value = true
}

const closeModelPopup = () => {
  showModelPopup.value = false
}

const selectModelItem = (model) => {
  selectedModel.value = model.name
  showModelPopup.value = false
}

const selectWordCount = () => {
  showWordCountPopup.value = true
}

const closeWordCountPopup = () => {
  showWordCountPopup.value = false
}

const selectWordCountItem = (item) => {
  selectedWordCount.value = item
  showWordCountPopup.value = false
}

const selectTone = () => {
  showTonePopup.value = true
}

const closeTonePopup = () => {
  showTonePopup.value = false
}

const selectToneItem = (item) => {
  selectedTone.value = item
  showTonePopup.value = false
}

const showHistory = () => {
  uni.showToast({ title: '历史记录', icon: 'none' })
}

const onCreate = () => {
  if (!prompt.value.trim()) {
    uni.showToast({ title: '请输入内容', icon: 'none' })
    return
  }
  uni.showToast({ title: '开始创作', icon: 'success' })
}
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background: linear-gradient(180deg, #f2f3ff 0%, #faf8ff 100%);
}

.content {
  padding: 32rpx 32rpx 64rpx;
}

/* Input Card */
.input-card {
  background: #ffffff;
  border-radius: 16rpx;
  padding: 32rpx;
  box-shadow: 0 20rpx 40rpx rgba(25, 27, 35, 0.04);
}

.input-area {
  width: 100%;
  min-height: 180rpx;
  font-size: 28rpx;
  color: #191b23;
  line-height: 1.6;
  background: transparent;
}

.input-placeholder {
  color: #737686;
}

.tool-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 24rpx;
}

.tool-btns {
  display: flex;
  gap: 12rpx;
}

.tool-btn {
  height: 48rpx;
  padding: 0 16rpx;
  border-radius: 999rpx;
  background: #f2f3ff;
  display: inline-flex;
  align-items: center;
  gap: 8rpx;
}

.tool-icon {
  font-size: 20rpx;
  color: #434655;
}

.tool-icon-svg {
  width: 28rpx;
  height: 28rpx;
}

.tool-text {
  font-size: 22rpx;
  color: #434655;
  font-weight: 500;
}

.history-btn {
  display: inline-flex;
  align-items: center;
  gap: 4rpx;
}

.history-icon {
  font-size: 20rpx;
  color: #0052d7;
}

.history-text {
  font-size: 22rpx;
  color: #0052d7;
  font-weight: 500;
}

/* Configuration Section */
.config-section {
  margin-top: 32rpx;
  display: flex;
  flex-direction: column;
  gap: 24rpx;
}

.config-card {
  background: #ffffff;
  border-radius: 16rpx;
  padding: 32rpx 40rpx 32rpx 32rpx;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.model-card {
  background: #f2f3ff;
}

.config-left {
  display: flex;
  align-items: center;
  gap: 20rpx;
}

.model-icon-wrapper {
  width: 56rpx;
  height: 56rpx;
  border-radius: 50%;
  background: rgba(0, 82, 215, 0.1);
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.model-icon-img {
  width: 36rpx;
  height: 36rpx;
}

.config-label {
  font-size: 22rpx;
  color: #737686;
  font-weight: 500;
  display: block;
}

.config-value {
  font-size: 30rpx;
  color: #191b23;
  font-weight: 700;
  display: block;
  margin-top: 4rpx;
}

.arrow-icon {
  font-size: 36rpx;
  color: #737686;
}

/* Parameters Grid */
.params-grid {
  display: flex;
  gap: 24rpx;
}

.param-card {
  flex: 1;
  background: #ffffff;
  border-radius: 16rpx;
  padding: 32rpx 40rpx 32rpx 32rpx;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.param-left {
  display: flex;
  align-items: center;
  gap: 16rpx;
}

.param-icon {
  font-size: 32rpx;
  color: rgba(67, 70, 85, 0.7);
}

.param-label {
  font-size: 26rpx;
  color: #191b23;
  font-weight: 500;
}

.param-right {
  display: flex;
  align-items: center;
  gap: 8rpx;
}

.param-value {
  font-size: 26rpx;
  color: #0052d7;
  font-weight: 700;
}

.param-arrow {
  font-size: 22rpx;
  color: #0052d7;
}

/* Create Button */
.create-btn {
  margin-top: 40rpx;
  height: 96rpx;
  border-radius: 999rpx;
  background: linear-gradient(180deg, #2f6cf6 0%, #0052d7 100%);
  color: #ffffff;
  font-size: 32rpx;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 8rpx 24rpx rgba(0, 82, 215, 0.2);
}

.create-btn:active {
  transform: scale(0.95);
}

/* Examples Section */
.examples-section {
  margin-top: 64rpx;
  background: #ffffff;
  border-radius: 16rpx 16rpx 0 0;
  padding: 40rpx 32rpx 48rpx;
}

.examples-header {
  display: flex;
  align-items: center;
  gap: 24rpx;
  margin-bottom: 32rpx;
}

.header-line {
  flex: 1;
  height: 2rpx;
  background: linear-gradient(90deg, transparent, rgba(195, 198, 215, 0.3));
}

.header-line:last-child {
  background: linear-gradient(270deg, transparent, rgba(195, 198, 215, 0.3));
}

.header-text {
  font-size: 20rpx;
  color: #737686;
  font-weight: 600;
  letter-spacing: 4rpx;
}

.examples-list {
  display: flex;
  flex-direction: column;
  gap: 24rpx;
}

.example-item {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
}

.example-header {
  display: flex;
  align-items: center;
  gap: 8rpx;
}

.tag-icon-wrapper {
  width: 28rpx;
  height: 28rpx;
  border-radius: 50%;
  background: #dbeafe;
  display: flex;
  align-items: center;
  justify-content: center;
}

.tag-icon {
  font-size: 18rpx;
  color: #3b82f6;
}

.example-title {
  font-size: 26rpx;
  color: #191b23;
  font-weight: 700;
}

.example-content {
  background: #f8faff;
  border-radius: 16rpx;
  padding: 24rpx;
  border: 1rpx solid rgba(59, 130, 246, 0.05);
}

.example-desc {
  font-size: 24rpx;
  color: #434655;
}

/* Model Popup */
.model-popup-mask {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  z-index: 999;
  display: flex;
  align-items: flex-end;
}

.model-popup {
  width: 100%;
  background: #ffffff;
  border-radius: 32rpx 32rpx 0 0;
  display: flex;
  flex-direction: column;
  overflow: hidden;
  box-sizing: border-box;
}

.popup-header {
  width: 100%;
  box-sizing: border-box;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 40rpx 32rpx 32rpx;
  border-bottom: 1rpx solid #f0f0f0;
}

.popup-title {
  font-size: 36rpx;
  font-weight: 700;
  color: #191b23;
}

.popup-close {
  width: 56rpx;
  height: 56rpx;
  border-radius: 50%;
  background: #f2f3ff;
  display: flex;
  align-items: center;
  justify-content: center;
}

.close-icon {
  font-size: 36rpx;
  color: #737686;
  line-height: 1;
}

.popup-scroll {
  flex: 1;
  width: 100%;
  box-sizing: border-box;
  overflow: hidden;
}

.popup-content {
  padding: 16rpx 32rpx 64rpx;
  width: 100%;
  box-sizing: border-box;
}

.model-item {
  width: 100%;
  box-sizing: border-box;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 32rpx;
  border-radius: 20rpx;
  margin-bottom: 16rpx;
  background: #f8f9ff;
}

.model-item.active {
  background: #e8edff;
}

.model-item-left {
  display: flex;
  align-items: center;
  gap: 20rpx;
}

.model-item-icon-wrapper {
  width: 72rpx;
  height: 72rpx;
  border-radius: 50%;
  background: rgba(0, 82, 215, 0.1);
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
}

.model-item-icon {
  width: 48rpx;
  height: 48rpx;
}

.model-item-info {
  display: flex;
  flex-direction: column;
  gap: 6rpx;
}

.model-item-name {
  font-size: 30rpx;
  font-weight: 700;
  color: #191b23;
}

.model-item-desc {
  font-size: 24rpx;
  color: #737686;
}

.model-item-check {
  width: 44rpx;
  height: 44rpx;
  border-radius: 50%;
  background: #0052d7;
  display: flex;
  align-items: center;
  justify-content: center;
}

.check-icon {
  font-size: 24rpx;
  color: #ffffff;
}
</style>
