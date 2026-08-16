<template>
  <view class="page">
    <nav-bar title="智能写作" :showBack="true" />

    <view class="content">
      <!-- Input Section -->
      <view class="input-card">
        <textarea
          class="input-area"
          v-model="prompt"
          maxlength="600"
          :placeholder="currentScenePlaceholder"
          placeholder-class="input-placeholder"
        />

        <view class="tool-row">
          <view class="history-btn" @tap="showHistory">
            <text class="history-icon">◷</text>
            <text class="history-text">历史</text>
          </view>
        </view>

        <view class="word-count-row" @tap="selectWordCount">
          <view class="word-count-left">
            <text class="word-count-label">字数</text>
            <text class="word-count-value">{{ selectedWordCount }}</text>
          </view>
          <text class="word-count-arrow">▼</text>
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
              <text class="config-value">{{ selectedModelLabel }}</text>
            </view>
          </view>
          <text class="arrow-icon">›</text>
        </view>

        <!-- 创作场景 -->
        <view class="scene-card">
          <view class="field-header">
            <text class="field-title">创作场景</text>
          </view>
          <view class="scene-grid">
            <view
              v-for="item in sceneOptions"
              :key="item.key"
              class="scene-item"
              :class="{ 'scene-item--active': selectedScene === item.key }"
              @tap="selectScene(item.key)"
            >
              <view class="scene-icon" :style="{ background: item.bgColor }">
                <text class="scene-icon-text">{{ item.icon }}</text>
              </view>
              <text class="scene-label">{{ item.label }}</text>
            </view>
          </view>
        </view>
      </view>

      <!-- Main CTA -->
      <view class="create-btn" @tap="onCreate">AI创作</view>

      <!-- Saved Works Section -->
      <view class="examples-section">
        <view class="examples-header">
          <view class="header-line"></view>
          <text class="header-text">已保存作品</text>
          <view class="header-line"></view>
        </view>

        <view v-if="savedRecords.length" class="examples-list">
          <view
            v-for="item in savedRecords"
            :key="item.id"
            class="example-item"
            @tap="openSavedRecord(item.id)"
          >
            <view class="example-header">
              <view class="tag-icon-wrapper">
                <text class="tag-icon">{{ sceneIcon(item.sceneKey) }}</text>
              </view>
              <text class="example-title">{{ item.title }}</text>
            </view>
            <view class="example-content">
              <text class="example-desc">{{ item.prompt }}</text>
            </view>
          </view>
        </view>

        <view v-else class="saved-empty">
          <text class="saved-empty-title">暂无保存的作品</text>
          <text class="saved-empty-desc">完成一次 AI 创作后，作品会显示在这里</text>
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
              v-for="model in models"
              :key="model.value"
              class="model-item"
              :class="{ active: selectedModel === model.value }"
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
              <view class="model-item-check" v-if="selectedModel === model.value">
                <text class="check-icon">✓</text>
              </view>
            </view>
            <view v-if="!models.length" class="model-empty">
              <text class="model-empty-title">暂无可用模型</text>
              <text class="model-empty-desc">请先在后台完成模型配置并测试成功</text>
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

  </view>
</template>

<script setup>
import { ref, computed } from 'vue'
import { onLoad, onShow } from '@dcloudio/uni-app'
import NavBar from '@/components/nav-bar/nav-bar.vue'
import { getAiWritingModels } from '@/api/ai.js'
import { getSmartWritingSavedWorks } from '@/utils/smartWritingHistory.js'

const prompt = ref('')
const showModelPopup = ref(false)
const showWordCountPopup = ref(false)
const selectedModel = ref('')
const selectedWordCount = ref('自动')
const selectedScene = ref('')
const savedRecords = ref([])
const defaultWritingPlaceholder = '开始你的写作之旅，选择一个创作场景后，我会帮你生成更贴合的内容...'

const wordCountOptions = ref(['自动', '200字以内', '500字左右', '800字以上', '1000字以上'])
const sceneOptions = ref([
  {
    key: 'weekly',
    label: '周报',
    icon: '周',
    bgColor: '#EEF2FF',
    placeholder: '帮我撰写一篇周报，内容包括工作内容和工作成果...'
  },
  {
    key: 'summary',
    label: '工作总结',
    icon: '总',
    bgColor: '#EAF7F0',
    placeholder: '帮我撰写一份工作总结，内容包括完成情况和工作成果...'
  },
  {
    key: 'holiday',
    label: '节日祝福',
    icon: '节',
    bgColor: '#FFF4E8',
    placeholder: '帮我写一条节日祝福，送给对象...'
  },
  {
    key: 'review',
    label: '评语',
    icon: '评',
    bgColor: '#F6ECFF',
    placeholder: '帮我写一段评语，围绕表现来写...'
  }
])

const loadSavedRecords = () => {
  savedRecords.value = getSmartWritingSavedWorks().slice(0, 6)
}

const models = ref([])

const providerIcon = (providerName = '', provider = '', model = '') => {
  const value = `${providerName} ${provider} ${model}`.toLowerCase()
  if (value.includes('deepseek')) return '/static/icons/ai create/DeepSeek.png'
  if (value.includes('豆包') || value.includes('doubao') || value.includes('volc') || value.includes('bytedance')) {
    return '/static/icons/ai create/doubao.png'
  }
  if (value.includes('通义') || value.includes('tongyi') || value.includes('qwen') || value.includes('dashscope')) {
    return '/static/icons/ai create/Tongyi-Qianwen.png'
  }
  return '/static/icons/ai create/DeepSeek.png'
}

const normalizeModelOption = (item) => {
  const model = String(item?.model || item?.displayName || '').trim()
  const providerName = String(item?.providerName || item?.provider || '文本模型').trim()
  const configPrefix = String(item?.configPrefix || '').trim()
  const value = configPrefix || model
  return {
    value,
    name: model || providerName,
    desc: providerName,
    configPrefix,
    model,
    icon: providerIcon(providerName, item?.provider, model)
  }
}

const loadModels = async () => {
  try {
    const response = await getAiWritingModels()
    const modelList = Array.isArray(response?.data) ? response.data : (Array.isArray(response) ? response : [])
    const nextModels = modelList.map(normalizeModelOption).filter(item => item.value)
    models.value = nextModels
    if (!nextModels.some(item => item.value === selectedModel.value)) {
      selectedModel.value = nextModels[0]?.value || ''
    }
  } catch (error) {
    models.value = []
    selectedModel.value = ''
  }
}

onShow(() => {
  loadSavedRecords()
  loadModels()
})

const popupHeight = computed(() => {
  const itemHeight = uni.upx2px(146) // 130rpx item + 16rpx margin
  const headerHeight = uni.upx2px(130) // 120rpx header + extra
  const paddingBottom = uni.upx2px(100) // Extra padding for safety
  const emptyHeight = uni.upx2px(260)
  const count = models.value.length
  const contentHeight = count > 0 ? itemHeight * count : emptyHeight
  const totalHeight = headerHeight + contentHeight + paddingBottom
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

const currentModelIcon = computed(() => {
  const model = models.value.find((m) => m.value === selectedModel.value)
  return model ? model.icon : '/static/icons/ai create/DeepSeek.png'
})

const selectedModelLabel = computed(() => {
  const model = models.value.find((m) => m.value === selectedModel.value)
  return model ? model.name : '暂无可用模型'
})

const selectModel = () => {
  showModelPopup.value = true
}

const closeModelPopup = () => {
  showModelPopup.value = false
}

const selectModelItem = (model) => {
  selectedModel.value = model.value
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

const showHistory = () => {
  uni.navigateTo({ url: '/subpackage_ai/smartWritingHistory/smartWritingHistory' })
}

const applyScenePlaceholder = (sceneKey) => {
  const scene = sceneOptions.value.find((item) => item.key === sceneKey)
  if (!scene) return
  selectedScene.value = sceneKey
  prompt.value = ''
}

onLoad((options) => {
  if (options?.scene) {
    selectedScene.value = decodeURIComponent(options.scene)
  }
  if (options?.prompt) {
    prompt.value = decodeURIComponent(options.prompt)
  }
})

const currentScenePlaceholder = computed(() => {
  return sceneOptions.value.find(item => item.key === selectedScene.value)?.placeholder || defaultWritingPlaceholder
})

const selectScene = (sceneKey) => {
  applyScenePlaceholder(sceneKey)
}

const sceneIcon = (sceneKey) => ({
  weekly: '周',
  summary: '总',
  holiday: '节',
  review: '评'
}[sceneKey] || '文')

const openSavedRecord = (id) => {
  uni.navigateTo({
    url: `/subpackage_ai/smartWritingDetail/smartWritingDetail?id=${encodeURIComponent(id)}&source=saved`
  })
}

const onCreate = () => {
  const content = prompt.value.trim()
  if (!content) {
    uni.showToast({ title: '请输入内容', icon: 'none' })
    return
  }
  if (!selectedScene.value) {
    uni.showToast({ title: '请选择创作场景', icon: 'none' })
    return
  }
  if (!selectedModel.value) {
    uni.showToast({ title: '暂无可用模型', icon: 'none' })
    return
  }
  uni.navigateTo({
    url: `/subpackage_ai/smartWritingGenerating/smartWritingGenerating?prompt=${encodeURIComponent(content)}&scene=${encodeURIComponent(selectedScene.value)}&wordCount=${encodeURIComponent(selectedWordCount.value)}&model=${encodeURIComponent(selectedModel.value)}`
  })
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
  justify-content: flex-end;
  margin-top: 24rpx;
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

.word-count-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-top: 24rpx;
  padding-top: 22rpx;
  border-top: 1rpx solid #eef0f7;
}

.word-count-left {
  display: flex;
  align-items: center;
  gap: 18rpx;
}

.word-count-label {
  font-size: 24rpx;
  color: #434655;
  font-weight: 600;
}

.word-count-value,
.word-count-arrow {
  font-size: 24rpx;
  color: #0052d7;
  font-weight: 700;
}

.word-count-arrow {
  font-size: 20rpx;
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

.scene-card {
  background: #ffffff;
  border-radius: 16rpx;
  padding: 28rpx 24rpx;
}

.field-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 22rpx;
}

.field-title {
  font-size: 26rpx;
  color: #191b23;
  font-weight: 700;
}

.scene-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12rpx;
}

.scene-item {
  min-width: 0;
  padding: 16rpx 4rpx;
  border: 2rpx solid transparent;
  border-radius: 12rpx;
  background: #f8f9ff;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10rpx;
}

.scene-item--active {
  border-color: #4d6bfe;
  background: #f2f4ff;
}

.scene-icon {
  width: 64rpx;
  height: 64rpx;
  border-radius: 14rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.scene-icon-text {
  font-size: 26rpx;
  color: #4d5aa7;
  font-weight: 700;
}

.scene-label {
  width: 100%;
  font-size: 20rpx;
  line-height: 1.3;
  color: #434655;
  text-align: center;
  white-space: nowrap;
}

.scene-item--active .scene-label {
  color: #3f51d9;
  font-weight: 700;
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

.saved-empty {
  display: flex;
  align-items: center;
  flex-direction: column;
  padding: 32rpx 20rpx 12rpx;
  text-align: center;
}

.saved-empty-title {
  color: #4d5360;
  font-size: 25rpx;
  font-weight: 700;
}

.saved-empty-desc {
  margin-top: 10rpx;
  color: #9aa1ae;
  font-size: 21rpx;
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

.model-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  flex-direction: column;
  min-height: 220rpx;
  padding: 56rpx 24rpx;
  text-align: center;
}

.model-empty-title {
  color: #4d5360;
  font-size: 28rpx;
  font-weight: 700;
}

.model-empty-desc {
  margin-top: 12rpx;
  color: #9aa1ae;
  font-size: 23rpx;
}
</style>
