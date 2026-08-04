<template>
  <view class="page page--architecture">
    <view class="nav-bar">
      <view class="nav-action nav-action--left" @tap="goBack">
        <text class="nav-back">‹</text>
      </view>
      <text class="nav-title">AI 架构图</text>
      <view class="nav-action nav-action--right" @tap="openHistory">
        <image class="nav-history-icon" src="/static/icons/diagram/history.svg" mode="aspectFit" />
      </view>
    </view>

    <scroll-view class="content" scroll-y>
      <view class="input-card">
        <text class="input-label">描述您的架构需求</text>
        <textarea
          class="prompt-input"
          v-model="description"
          placeholder="例如：生成一个校园二手交易系统的整体架构图，包含核心业务流程和数据流向..."
          placeholder-class="prompt-placeholder"
          :maxlength="500"
        />
        <view class="input-footer">
          <view class="voice-import" @tap="importVoice">
            <image class="voice-icon" src="/static/icons/diagram/import-file.svg" mode="aspectFit" />
            <text>导入文档/语音</text>
          </view>
          <text class="char-count">{{ description.length }} / 500</text>
        </view>
      </view>

      <view class="section-title">
        <image class="section-icon" src="/static/icons/diagram/settings-blue.svg" mode="aspectFit" />
        <text>架构生成设置</text>
      </view>

      <view class="field-block">
        <text class="field-label">系统类型</text>
        <view class="chip-row">
          <view
            class="pill-chip"
            :class="{ 'pill-chip--active': selectedSystemType === item.key }"
            v-for="item in systemTypes"
            :key="item.key"
            @tap="selectedSystemType = item.key"
          >
            {{ item.label }}
          </view>
        </view>
      </view>

      <view class="field-block">
        <text class="field-label">架构层级（多选）</text>
        <view class="layer-list">
          <view
            class="layer-row"
            :class="{ 'layer-row--active': selectedLayer === item.key }"
            v-for="item in layerOptions"
            :key="item.key"
            @tap="selectedLayer = item.key"
          >
            <image class="layer-icon" :src="item.icon" mode="aspectFit" />
            <view class="layer-copy">
              <view class="layer-title-line">
                <text class="layer-title">{{ item.label }}</text>
                <text class="ai-tag" v-if="item.tag">{{ item.tag }}</text>
              </view>
              <text class="layer-desc" v-if="item.desc">{{ item.desc }}</text>
            </view>
            <view class="choice-circle" :class="{ 'choice-circle--active': selectedLayer === item.key }"></view>
          </view>
        </view>
      </view>

      <view class="field-block">
        <text class="field-label">展示内容</text>
        <view class="checkbox-grid">
          <view
            class="checkbox-item"
            v-for="item in contentOptions"
            :key="item.key"
            @tap="toggleContent(item.key)"
          >
            <view class="checkbox" :class="{ 'checkbox--active': selectedContents.includes(item.key) }"></view>
            <text>{{ item.label }}</text>
          </view>
        </view>
      </view>

      <view class="field-block">
        <text class="field-label">关系表达</text>
        <view class="relation-list">
          <view
            class="relation-row"
            :class="{ 'relation-row--active': selectedRelation === item.key }"
            v-for="item in relationOptions"
            :key="item.key"
            @tap="selectedRelation = item.key"
          >
            <view class="relation-radio" :class="{ 'relation-radio--active': selectedRelation === item.key }"></view>
            <view>
              <view class="relation-title-line">
                <text class="relation-title">{{ item.label }}</text>
                <text class="ai-tag" v-if="item.tag">{{ item.tag }}</text>
              </view>
              <text class="relation-desc">{{ item.desc }}</text>
            </view>
          </view>
        </view>
      </view>
    </scroll-view>

    <view class="bottom-bar">
      <view class="generate-btn" @tap="generateArchitecture">
        <image class="generate-icon" src="/static/icons/diagram/spark-blue.svg" mode="aspectFit" />
        <text>AI 生成架构图</text>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'

const description = ref('')
const selectedSystemType = ref('web')
const selectedLayer = ref('auto')
const selectedContents = ref(['frontend', 'backend'])
const selectedRelation = ref('auto')

const systemTypes = [
  { key: 'web', label: 'Web系统' },
  { key: 'app', label: 'APP系统' },
  { key: 'mini', label: '小程序' },
  { key: 'admin', label: '管理后台' }
]

const layerOptions = [
  { key: 'auto', label: '自动分析', tag: 'AI', desc: '将根据需求智能判断架构层级', icon: '/static/icons/diagram/ai-pen-blue.svg' },
  { key: 'client', label: '用户层 (Client/User)', icon: '/static/icons/diagram/layer.svg' },
  { key: 'application', label: '应用层 (Application)', icon: '/static/icons/diagram/app-grid.svg' },
  { key: 'service', label: '服务层 (Service/Core)', icon: '/static/icons/diagram/server.svg' },
  { key: 'data', label: '数据层 (Data Storage)', icon: '/static/icons/diagram/database.svg' }
]

const contentOptions = [
  { key: 'frontend', label: '前端模块' },
  { key: 'backend', label: '后端服务' },
  { key: 'storage', label: '数据存储' },
  { key: 'thirdParty', label: '第三方服务' }
]

const relationOptions = [
  { key: 'auto', label: '自动分析', tag: 'AUTO', desc: 'AI 将标注并展示合适的表达方式' },
  { key: 'module', label: '模块关系', desc: '展示组件间的层级与连接关系' },
  { key: 'data', label: '数据流向', desc: '着重展示信息的传递与存储路径' }
]

const goBack = () => { uni.navigateBack() }
const openHistory = () => { uni.showToast({ title: '历史记录预留', icon: 'none' }) }
const importVoice = () => { uni.showToast({ title: '导入接口预留', icon: 'none' }) }

const toggleContent = (key) => {
  if (selectedContents.value.includes(key)) {
    selectedContents.value = selectedContents.value.filter(item => item !== key)
    return
  }
  selectedContents.value = [...selectedContents.value, key]
}

const generateArchitecture = () => {
  if (!description.value.trim()) {
    uni.showToast({ title: '请输入架构描述', icon: 'none' })
    return
  }
  uni.navigateTo({
    url: `/subpackage_ai/architecturePreview/architecturePreview?desc=${encodeURIComponent(description.value)}&type=${selectedSystemType.value}&style=blue&complexity=${selectedLayer.value}&relation=${selectedRelation.value}&contents=${encodeURIComponent(selectedContents.value.join(','))}`
  })
}
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background: #FCFAFC;
  color: #15233A;
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
  color: #2A3A52;
  font-size: 52rpx;
  font-weight: 300;
  line-height: 1;
  transform: translateY(-2rpx);
}

.nav-title {
  color: #1E304B;
  font-size: 36rpx;
  font-weight: 800;
}

.nav-history-icon {
  width: 34rpx;
  height: 34rpx;
}

.content {
  height: calc(100vh - 88rpx);
  padding: 30rpx 30rpx 150rpx;
  box-sizing: border-box;
}

.input-card {
  height: 396rpx;
  padding: 34rpx 34rpx 24rpx;
  border: 1rpx solid #DFE3EA;
  border-radius: 18rpx;
  background: #FFFFFF;
  box-sizing: border-box;
}

.input-label {
  display: block;
  margin-bottom: 14rpx;
  color: #3A4657;
  font-size: 22rpx;
  font-weight: 600;
}

.prompt-input {
  width: 100%;
  height: 270rpx;
  color: #28364C;
  font-size: 24rpx;
  line-height: 1.5;
}

.prompt-placeholder {
  color: #1F2E44;
  font-size: 24rpx;
  line-height: 1.5;
}

.input-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.voice-import {
  display: flex;
  align-items: center;
  color: #2394F2;
  font-size: 22rpx;
  font-weight: 700;
}

.voice-icon {
  width: 24rpx;
  height: 24rpx;
  margin-right: 8rpx;
}

.char-count {
  color: #778397;
  font-size: 20rpx;
}

.section-title {
  display: flex;
  align-items: center;
  margin: 36rpx 0 26rpx;
  color: #1C2E48;
  font-size: 30rpx;
  font-weight: 800;
}

.section-icon {
  width: 34rpx;
  height: 30rpx;
  margin-right: 12rpx;
}

.field-block {
  margin-bottom: 28rpx;
}

.field-label {
  display: block;
  margin-bottom: 18rpx;
  color: #344155;
  font-size: 22rpx;
  font-weight: 600;
}

.chip-row {
  display: flex;
  gap: 14rpx;
}

.pill-chip {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 50rpx;
  min-width: 118rpx;
  padding: 0 22rpx;
  border: 1rpx solid #DDE3EB;
  border-radius: 28rpx;
  background: #FFFFFF;
  color: #1F2C3F;
  font-size: 22rpx;
  box-sizing: border-box;
}

.pill-chip--active {
  border-color: #38A6F4;
  background: #3AA3F5;
  color: #FFFFFF;
  font-weight: 700;
}

.layer-list,
.relation-list {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

.layer-row {
  display: flex;
  align-items: center;
  height: 96rpx;
  padding: 0 26rpx;
  border: 1rpx solid #DEE3EB;
  border-radius: 14rpx;
  background: #FFFFFF;
  box-sizing: border-box;
}

.layer-row--active {
  height: 108rpx;
  border: 2rpx solid #3AA3F5;
  background: #EAF6FF;
}

.layer-icon {
  width: 34rpx;
  height: 34rpx;
  margin-right: 22rpx;
}

.layer-copy {
  flex: 1;
  min-width: 0;
}

.layer-title-line,
.relation-title-line {
  display: flex;
  align-items: center;
}

.layer-title,
.relation-title {
  color: #1F2C3F;
  font-size: 24rpx;
  font-weight: 800;
}

.layer-desc,
.relation-desc {
  display: block;
  margin-top: 6rpx;
  color: #7C8797;
  font-size: 18rpx;
  line-height: 1.25;
}

.ai-tag {
  margin-left: 12rpx;
  color: #3AA3F5;
  font-size: 16rpx;
  font-weight: 800;
}

.choice-circle {
  width: 26rpx;
  height: 26rpx;
  border: 2rpx solid #B8C2D0;
  border-radius: 50%;
  box-sizing: border-box;
}

.choice-circle--active {
  border: 7rpx solid #3AA3F5;
}

.checkbox-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16rpx;
}

.checkbox-item {
  display: flex;
  align-items: center;
  height: 64rpx;
  padding: 0 20rpx;
  border: 1rpx solid #E1E5EC;
  border-radius: 10rpx;
  background: #FFFFFF;
  color: #1F2C3F;
  font-size: 22rpx;
  box-sizing: border-box;
}

.checkbox {
  position: relative;
  width: 24rpx;
  height: 24rpx;
  margin-right: 12rpx;
  border: 1rpx solid #CAD2DE;
  border-radius: 4rpx;
  box-sizing: border-box;
}

.checkbox--active {
  border-color: #3AA3F5;
  background: #3AA3F5;
}

.checkbox--active::after {
  content: "";
  position: absolute;
  left: 6rpx;
  top: 2rpx;
  width: 7rpx;
  height: 13rpx;
  border-right: 2rpx solid #FFFFFF;
  border-bottom: 2rpx solid #FFFFFF;
  transform: rotate(45deg);
}

.relation-row {
  display: flex;
  align-items: center;
  min-height: 100rpx;
  padding: 18rpx 26rpx;
  border: 1rpx solid #DEE3EB;
  border-radius: 14rpx;
  background: #FFFFFF;
  box-sizing: border-box;
}

.relation-row--active {
  border: 2rpx solid #3AA3F5;
}

.relation-radio {
  width: 26rpx;
  height: 26rpx;
  margin-right: 20rpx;
  border: 2rpx solid #B8C2D0;
  border-radius: 50%;
  box-sizing: border-box;
}

.relation-radio--active {
  border: 8rpx solid #3AA3F5;
}

.bottom-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 20;
  padding: 22rpx 30rpx 24rpx;
  background: #FCFAFC;
  box-sizing: border-box;
}

.generate-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 96rpx;
  border-radius: 16rpx;
  background: #3AA3F5;
  color: #FFFFFF;
  font-size: 28rpx;
  font-weight: 800;
}

.generate-icon {
  width: 34rpx;
  height: 34rpx;
  margin-right: 12rpx;
}
</style>
