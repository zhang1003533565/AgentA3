<template>
  <view class="page">
    <view class="nav-bar">
      <view class="nav-action nav-action--left" @tap="goBack">
        <text class="nav-back">‹</text>
      </view>
      <text class="nav-title">AI 思维导图</text>
      <view class="nav-action nav-action--right" @tap="openHistory">
        <image class="nav-history-icon" src="/static/icons/diagram/history.svg" mode="aspectFit" />
      </view>
    </view>

    <scroll-view class="content" scroll-y>
      <view class="input-card">
        <textarea
          class="prompt-input"
          v-model="topic"
          placeholder="例如：生成大学计算机课程体系的思维导图"
          placeholder-class="prompt-placeholder"
          :maxlength="2000"
        />
        <view class="input-card__footer">
          <view class="import-btn" @tap="importDocument">
            <image class="import-icon" src="/static/icons/diagram/import-file.svg" mode="aspectFit" />
            <text>导入 PPT/Word/PDF</text>
          </view>
          <text class="char-count">{{ topic.length }}/2000</text>
        </view>
      </view>

      <view class="settings-card">
        <view class="settings-title">
          <image class="settings-title__icon" src="/static/icons/diagram/settings-spark.svg" mode="aspectFit" />
          <text>专属设置</text>
        </view>

        <view class="field-group">
          <text class="field-label">中心主题</text>
          <input
            class="theme-input"
            v-model="centerTopic"
            placeholder="自动提取，可手动填写"
            placeholder-class="theme-placeholder"
          />
        </view>

        <view class="field-group">
          <text class="field-label">层级深度</text>
          <view class="option-row option-row--depth">
            <view
              class="option-chip"
              :class="{ 'option-chip--active': selectedDepth === item.key }"
              v-for="item in depthOptions"
              :key="item.key"
              @tap="selectedDepth = item.key"
            >
              {{ item.label }}
            </view>
          </view>
        </view>

        <view class="field-group">
          <text class="field-label">结构方式</text>
          <view class="option-row option-row--structure">
            <view
              class="option-chip option-chip--compact"
              :class="{ 'option-chip--active': selectedStructure === item.key }"
              v-for="item in structureOptions"
              :key="item.key"
              @tap="selectedStructure = item.key"
            >
              {{ item.label }}
            </view>
          </view>
        </view>

        <view class="field-group field-group--last">
          <text class="field-label">展开程度</text>
          <view class="option-row option-row--expand">
            <view
              class="option-chip option-chip--wide"
              :class="{ 'option-chip--active': selectedExpand === item.key }"
              v-for="item in expandOptions"
              :key="item.key"
              @tap="selectedExpand = item.key"
            >
              {{ item.label }}
            </view>
          </view>
        </view>
      </view>

      <view class="recent-section">
        <text class="recent-title">最近生成</text>
        <view class="recent-list">
          <view class="recent-item" v-for="item in recentItems" :key="item.title" @tap="openRecent(item)">
            <view class="recent-icon-wrap">
              <image class="recent-icon" src="/static/icons/diagram/mindmap-purple.svg" mode="aspectFit" />
            </view>
            <view class="recent-info">
              <text class="recent-name">{{ item.title }}</text>
              <text class="recent-meta">{{ item.meta }}</text>
            </view>
            <image class="recent-arrow" src="/static/icons/icon-forward.svg" mode="aspectFit" />
          </view>
        </view>
      </view>
    </scroll-view>

    <view class="bottom-bar">
      <view class="generate-btn" @tap="generateMindmap">
        <image class="generate-icon" src="/static/icons/diagram/spark-white.svg" mode="aspectFit" />
        <text>{{ isGenerating ? 'AI 生成中...' : 'AI 生成思维导图' }}</text>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'
import { buildMindmapPayload, generateMindmap as requestGenerateMindmap, mockGenerateMindmap } from '@/api/aiDiagram.js'

const topic = ref('')
const centerTopic = ref('')
const selectedDepth = ref('auto')
const selectedStructure = ref('auto')
const selectedExpand = ref('standard')
const isGenerating = ref(false)

const depthOptions = [
  { key: 'auto', label: '自动' },
  { key: '2', label: '2层' },
  { key: '3', label: '3层' },
  { key: '4', label: '4层' }
]

const structureOptions = [
  { key: 'auto', label: '自动' },
  { key: 'knowledge', label: '知识梳理' },
  { key: 'course', label: '课程体系' },
  { key: 'review', label: '复习提纲' },
  { key: 'project', label: '项目拆解' }
]

const expandOptions = [
  { key: 'simple', label: '简洁' },
  { key: 'standard', label: '标准' },
  { key: 'detail', label: '详细' }
]

const recentItems = [
  { title: '大学计算机课程体系', meta: '2023-11-24 · 3层 · 知识梳理' },
  { title: '中国近代史时间轴', meta: '2023-11-23 · 4层 · 复习提纲' }
]

const goBack = () => { uni.navigateBack() }
const openHistory = () => { uni.showToast({ title: '历史记录预留', icon: 'none' }) }
const importDocument = () => { uni.showToast({ title: '导入接口预留', icon: 'none' }) }

const openRecent = (item) => {
  uni.navigateTo({
    url: `/subpackage_ai/mindmapPreview/mindmapPreview?topic=${encodeURIComponent(item.title)}&structure=${selectedStructure.value}&style=fresh`
  })
}

const generateMindmap = async () => {
  const finalTopic = centerTopic.value.trim() || topic.value.trim()
  if (!finalTopic) {
    uni.showToast({ title: '请输入主题或导入文档', icon: 'none' })
    return
  }
  if (isGenerating.value) return
  isGenerating.value = true
  try {
    const payload = buildMindmapPayload({
      prompt: topic.value.trim() || finalTopic,
      centerTopic: finalTopic,
      depth: selectedDepth.value,
      structure: selectedStructure.value,
      expand: selectedExpand.value
    })
    let result
    try {
      result = await requestGenerateMindmap(payload)
    } catch (error) {
      if (error?.code || error?.statusCode) {
        throw error
      }
      console.warn('AI mindmap backend unavailable, fallback to local mock.', error)
      result = await mockGenerateMindmap(payload)
    }
    uni.setStorageSync(`aiMindmapResult:${result.id}`, result)
    uni.navigateTo({
      url: [
        '/subpackage_ai/mindmapPreview/mindmapPreview',
        `?topic=${encodeURIComponent(result.title)}`,
        `&resultId=${encodeURIComponent(result.id)}`,
        `&structure=${encodeURIComponent(selectedStructure.value)}`,
        `&depth=${encodeURIComponent(selectedDepth.value)}`,
        `&expand=${encodeURIComponent(selectedExpand.value)}`
      ].join('')
    })
  } catch (error) {
    uni.showToast({ title: error?.message || '生成失败', icon: 'none' })
  } finally {
    isGenerating.value = false
  }
}
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background: #F3F3F4;
  color: #18273F;
}

.nav-bar {
  position: relative;
  z-index: 10;
  display: flex;
  align-items: center;
  height: 88rpx;
  padding: 0 30rpx;
  background: #FFFFFF;
  box-sizing: border-box;
  box-shadow: 0 2rpx 10rpx rgba(35, 43, 58, 0.04);
}

.nav-action {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 48rpx;
  height: 48rpx;
}

.nav-action--left {
  margin-right: 12rpx;
}

.nav-action--right {
  margin-left: auto;
}

.nav-back {
  color: #405878;
  font-size: 52rpx;
  font-weight: 300;
  line-height: 1;
  transform: translateY(-2rpx);
}

.nav-title {
  color: #30425F;
  font-size: 36rpx;
  font-weight: 800;
  letter-spacing: 1rpx;
}

.nav-history-icon {
  width: 34rpx;
  height: 34rpx;
  opacity: 0.9;
}

.content {
  height: calc(100vh - 88rpx);
  box-sizing: border-box;
  padding: 30rpx 30rpx 150rpx;
}

.input-card {
  height: 396rpx;
  padding: 44rpx 34rpx 26rpx;
  border-radius: 18rpx;
  background: #FFFFFF;
  box-sizing: border-box;
}

.prompt-input {
  width: 100%;
  height: 258rpx;
  color: #2F3F58;
  font-size: 28rpx;
  line-height: 1.55;
  box-sizing: border-box;
}

.prompt-placeholder {
  color: #C0C5D2;
  font-size: 28rpx;
  line-height: 1.55;
}

.input-card__footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.import-btn {
  display: flex;
  align-items: center;
  height: 54rpx;
  padding: 0 22rpx;
  border-radius: 28rpx;
  background: #F6F5F8;
  color: #2D4566;
  font-size: 24rpx;
  font-weight: 700;
  box-sizing: border-box;
}

.import-icon {
  width: 24rpx;
  height: 24rpx;
  margin-right: 10rpx;
}

.char-count {
  color: #1F2F47;
  font-size: 20rpx;
  transform: translateY(4rpx);
}

.settings-card {
  position: relative;
  margin-top: 26rpx;
  padding: 40rpx 34rpx 34rpx 42rpx;
  border-radius: 18rpx;
  background: #FFFFFF;
  box-sizing: border-box;
  overflow: hidden;
}

.settings-card::before {
  content: "";
  position: absolute;
  left: 0;
  top: 0;
  width: 8rpx;
  height: 100%;
  background: #8257D8;
}

.settings-title {
  display: flex;
  align-items: center;
  margin-bottom: 34rpx;
  color: #7E52D5;
  font-size: 32rpx;
  font-weight: 800;
}

.settings-title__icon {
  width: 46rpx;
  height: 40rpx;
  margin-right: 16rpx;
}

.field-group {
  margin-bottom: 30rpx;
}

.field-group--last {
  margin-bottom: 0;
}

.field-label {
  display: block;
  margin-bottom: 18rpx;
  color: #1E2D42;
  font-size: 22rpx;
  font-weight: 500;
}

.theme-input {
  height: 82rpx;
  padding: 0 30rpx;
  border: 1rpx solid #DCDDE2;
  border-radius: 12rpx;
  background: #FBFAFC;
  color: #263852;
  font-size: 24rpx;
  box-sizing: border-box;
}

.theme-placeholder {
  color: #8E94A1;
}

.option-row {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
}

.option-row--depth {
  gap: 14rpx;
}

.option-row--structure {
  gap: 14rpx 12rpx;
}

.option-row--expand {
  justify-content: space-between;
}

.option-chip {
  display: flex;
  align-items: center;
  justify-content: center;
  min-width: 132rpx;
  height: 64rpx;
  padding: 0 20rpx;
  border: 1rpx solid #DDDEE3;
  border-radius: 12rpx;
  background: #FFFFFF;
  color: #1F2D40;
  font-size: 22rpx;
  font-weight: 500;
  box-sizing: border-box;
}

.option-chip--compact {
  min-width: 0;
  height: 58rpx;
  padding: 0 22rpx;
  border-radius: 28rpx;
}

.option-chip--wide {
  width: 174rpx;
  height: 64rpx;
}

.option-chip--active {
  border: 3rpx solid #8257D8;
  background: #F8F5FF;
  color: #7E52D5;
  font-weight: 800;
}

.option-chip--compact.option-chip--active {
  background: #8257D8;
  color: #FFFFFF;
}

.recent-section {
  margin-top: 24rpx;
}

.recent-title {
  display: block;
  margin: 0 0 18rpx 8rpx;
  color: #545B67;
  font-size: 24rpx;
  font-weight: 500;
}

.recent-list {
  display: flex;
  flex-direction: column;
  gap: 20rpx;
}

.recent-item {
  display: flex;
  align-items: center;
  height: 130rpx;
  padding: 0 30rpx;
  border-radius: 18rpx;
  background: #FFFFFF;
  box-sizing: border-box;
  box-shadow: 0 4rpx 12rpx rgba(35, 43, 58, 0.03);
}

.recent-icon-wrap {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 68rpx;
  height: 68rpx;
  margin-right: 24rpx;
  border-radius: 12rpx;
  background: #F0E9FA;
}

.recent-icon {
  width: 36rpx;
  height: 36rpx;
}

.recent-info {
  display: flex;
  flex: 1;
  min-width: 0;
  flex-direction: column;
}

.recent-name {
  color: #1E2B3D;
  font-size: 26rpx;
  font-weight: 500;
  line-height: 1.35;
}

.recent-meta {
  margin-top: 4rpx;
  color: #2D4664;
  font-size: 20rpx;
  line-height: 1.35;
}

.recent-arrow {
  width: 32rpx;
  height: 32rpx;
  opacity: 0.3;
}

.bottom-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 20;
  padding: 22rpx 30rpx 24rpx;
  background: rgba(243, 243, 244, 0.96);
  box-sizing: border-box;
}

.generate-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 96rpx;
  border-radius: 16rpx;
  background: #405878;
  color: #FFFFFF;
  font-size: 28rpx;
  font-weight: 800;
  box-shadow: 0 6rpx 12rpx rgba(42, 60, 84, 0.08);
}

.generate-icon {
  width: 34rpx;
  height: 34rpx;
  margin-right: 12rpx;
}
</style>
