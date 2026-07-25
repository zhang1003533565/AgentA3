<template>
  <view class="page">
    <!-- 导航栏 -->
    <view class="nav-bar">
      <view class="nav-back" @tap="goBack">
        <text class="nav-back-icon">‹</text>
      </view>
      <text class="nav-title">架构图预览</text>
      <view class="nav-actions">
        <view class="nav-action-btn" @tap="download">
          <text class="nav-action-icon"></text>
        </view>
        <view class="nav-action-btn nav-action-btn--share" @tap="share">
          <text class="nav-action-icon">↗</text>
          <text class="nav-action-text">分享</text>
        </view>
      </view>
    </view>

    <!-- Tab 切换 -->
    <view class="tab-bar">
      <view
        class="tab-item"
        :class="{ 'tab-item--active': activeTab === 'chart' }"
        @tap="activeTab = 'chart'"
      >
        <text class="tab-text">图表预览</text>
        <view class="tab-indicator" v-if="activeTab === 'chart'"></view>
      </view>
      <view
        class="tab-item"
        :class="{ 'tab-item--active': activeTab === 'code' }"
        @tap="activeTab = 'code'"
      >
        <text class="tab-text">代码视图</text>
      </view>
      <view
        class="tab-item"
        :class="{ 'tab-item--active': activeTab === 'detail' }"
        @tap="activeTab = 'detail'"
      >
        <text class="tab-text">详情信息</text>
      </view>
    </view>

    <view class="content" v-if="activeTab === 'chart'">
      <!-- 架构图 -->
      <view class="diagram-section">
        <view class="diagram-container">
          <!-- 访问层 -->
          <view class="layer-row">
            <view class="layer-label" style="background: #EEF0FF; color: #4D6BFE;">
              <text class="layer-label-text">访问层</text>
            </view>
            <view class="layer-nodes">
              <view class="node-item" style="background: #F0F4FF;">
                <text class="node-icon">🌐</text>
                <text class="node-label">移动端</text>
              </view>
              <view class="node-item" style="background: #F0F4FF;">
                <text class="node-icon">📱</text>
                <text class="node-label">移动端</text>
              </view>
              <view class="node-item" style="background: #F0FFF4;">
                <text class="node-icon">✓</text>
                <text class="node-label">小程序</text>
              </view>
            </view>
          </view>

          <view class="flow-arrow">↓</view>

          <!-- 网关层 -->
          <view class="layer-row">
            <view class="layer-label" style="background: #FFF3E0; color: #FF9F43;">
              <text class="layer-label-text">网关层</text>
            </view>
            <view class="layer-nodes layer-nodes--single">
              <view class="node-item node-item--wide" style="background: #F0F4FF;">
                <text class="node-icon">🔒</text>
                <text class="node-label">API Gateway</text>
              </view>
            </view>
          </view>

          <view class="flow-arrow">↓</view>

          <!-- 服务层 -->
          <view class="layer-row">
            <view class="layer-label" style="background: #E8FFF0; color: #1DD1A1;">
              <text class="layer-label-text">服务层</text>
            </view>
            <view class="layer-nodes">
              <view class="node-item" style="background: #F0F4FF;">
                <text class="node-icon">👤</text>
                <text class="node-label">用户服务</text>
              </view>
              <view class="node-item" style="background: #F0F4FF;">
                <text class="node-icon">🛒</text>
                <text class="node-label">商品服务</text>
              </view>
              <view class="node-item" style="background: #F0F4FF;">
                <text class="node-icon"></text>
                <text class="node-label">订单服务</text>
              </view>
              <view class="node-item" style="background: #F0F4FF;">
                <text class="node-icon">💳</text>
                <text class="node-label">支付服务</text>
              </view>
              <view class="node-item" style="background: #F0F4FF;">
                <text class="node-icon">🔔</text>
                <text class="node-label">通知服务</text>
              </view>
            </view>
          </view>

          <view class="flow-arrow">↓</view>

          <!-- 数据层 -->
          <view class="layer-row">
            <view class="layer-label" style="background: #E8FFF0; color: #1DD1A1;">
              <text class="layer-label-text">数据层</text>
            </view>
            <view class="layer-nodes">
              <view class="node-item" style="background: #F0FFF4;">
                <text class="node-icon">🗄</text>
                <text class="node-label">MySQL</text>
              </view>
              <view class="node-item" style="background: #F0FFF4;">
                <text class="node-icon">🗄</text>
                <text class="node-label">Redis</text>
              </view>
              <view class="node-item" style="background: #F0FFF4;">
                <text class="node-icon">🗄</text>
                <text class="node-label">MongoDB</text>
              </view>
              <view class="node-item" style="background: #F0FFF4;">
                <text class="node-icon">✓</text>
                <text class="node-label">对象存储</text>
              </view>
            </view>
          </view>

          <view class="flow-arrow">↓</view>

          <!-- 基础设施 -->
          <view class="layer-row">
            <view class="layer-label" style="background: #FFF3E0; color: #FF9F43;">
              <text class="layer-label-text">基础设施</text>
            </view>
            <view class="layer-nodes">
              <view class="node-item" style="background: #F0F4FF;">
                <text class="node-icon">🐳</text>
                <text class="node-label">Docker</text>
              </view>
              <view class="node-item" style="background: #F0F4FF;">
                <text class="node-icon"></text>
                <text class="node-label">Kubernetes</text>
              </view>
              <view class="node-item" style="background: #F0F4FF;">
                <text class="node-icon">📊</text>
                <text class="node-label">日志服务</text>
              </view>
              <view class="node-item" style="background: #F0F4FF;">
                <text class="node-icon">🔔</text>
                <text class="node-label">监控告警</text>
              </view>
            </view>
          </view>
        </view>
      </view>

      <!-- 操作按钮 -->
      <view class="action-bar">
        <view class="action-btn" @tap="edit">
          <text class="action-btn-icon">✏️</text>
          <text class="action-btn-text">编辑</text>
        </view>
        <view class="action-btn" @tap="beautify">
          <text class="action-btn-icon"></text>
          <text class="action-btn-text">美化</text>
        </view>
        <view class="action-btn" @tap="copy">
          <text class="action-btn-icon">📋</text>
          <text class="action-btn-text">复制</text>
        </view>
        <view class="action-btn" @tap="fullscreen">
          <text class="action-btn-icon">⛶</text>
          <text class="action-btn-text">全屏</text>
        </view>
      </view>

      <!-- 架构说明 -->
      <view class="desc-section">
        <view class="desc-title">架构说明</view>
        <text class="desc-text">该架构采用微服务设计模式，通过 API Gateway 统一入口，服务层拆分为多个独立服务支持水平扩展，数据层采用多种存储方案满足不同业务需求，基础设施使用容器化部署确保高可用性和可维护性。</text>
      </view>

      <!-- 推荐模板 -->
      <view class="template-section">
        <view class="template-header">
          <text class="template-title">推荐模板</text>
          <text class="template-more" @tap="viewMore">查看更多 ›</text>
        </view>
        <view class="template-list">
          <view class="template-item" v-for="item in templates" :key="item.key" @tap="useTemplate(item.key)">
            <view class="template-preview" :style="{ background: item.previewBg }">
              <text class="template-preview-icon">📊</text>
            </view>
            <text class="template-label">{{ item.label }}</text>
          </view>
        </view>
      </view>
    </view>

    <!-- 底部按钮 -->
    <view class="bottom-bar">
      <view class="bottom-btn bottom-btn--outline" @tap="regenerate">
        <text class="bottom-btn-icon">🔄</text>
        <text class="bottom-btn-text">重新生成</text>
      </view>
      <view class="bottom-btn bottom-btn--primary" @tap="exportImage">
        <text class="bottom-btn-icon">⬇</text>
        <text class="bottom-btn-text">导出图片</text>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'

const activeTab = ref('chart')

const templates = [
  { key: 'microservice', label: '微服务架构', previewBg: '#EEF0FF' },
  { key: 'layered', label: '分层架构', previewBg: '#E8FFF0' },
  { key: 'event', label: '事件驱动架构', previewBg: '#FFF3E0' },
  { key: 'cloudnative', label: '云原生架构', previewBg: '#F3E8FF' },
]

const goBack = () => { uni.navigateBack() }
const download = () => { uni.showToast({ title: '下载', icon: 'none' }) }
const share = () => { uni.showToast({ title: '分享', icon: 'none' }) }
const edit = () => { uni.showToast({ title: '编辑', icon: 'none' }) }
const beautify = () => { uni.showToast({ title: '美化', icon: 'none' }) }
const copy = () => { uni.showToast({ title: '复制', icon: 'none' }) }
const fullscreen = () => { uni.showToast({ title: '全屏', icon: 'none' }) }
const viewMore = () => { uni.showToast({ title: '查看更多', icon: 'none' }) }
const useTemplate = (key) => { uni.showToast({ title: '使用模板', icon: 'none' }) }
const regenerate = () => { uni.showToast({ title: '重新生成', icon: 'none' }) }
const exportImage = () => { uni.showToast({ title: '导出图片', icon: 'none' }) }
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background-color: #FAFBFC;
  display: flex;
  flex-direction: column;
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

.nav-actions {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.nav-action-btn {
  display: flex;
  align-items: center;
  gap: 6rpx;
  padding: 10rpx 16rpx;
  border-radius: 8rpx;
  border: 1rpx solid #E8E8E8;
}

.nav-action-btn--share {
  background: #FFF;
}

.nav-action-icon {
  font-size: 24rpx;
  color: #555;
}

.nav-action-text {
  font-size: 24rpx;
  color: #555;
}

/* Tab 栏 */
.tab-bar {
  display: flex;
  background: #FFF;
  border-bottom: 1rpx solid #F0F0F0;
}

.tab-item {
  flex: 1;
  text-align: center;
  padding: 20rpx 0;
  position: relative;
}

.tab-item--active .tab-text {
  color: #4D6BFE;
  font-weight: 600;
}

.tab-indicator {
  position: absolute;
  bottom: 0;
  left: 50%;
  transform: translateX(-50%);
  width: 48rpx;
  height: 4rpx;
  background: #4D6BFE;
  border-radius: 2rpx;
}

.tab-text {
  font-size: 28rpx;
  color: #888;
}

.content {
  flex: 1;
  padding: 20rpx 24rpx;
  overflow-y: auto;
}

/* 架构图 */
.diagram-section {
  background: #FFF;
  border-radius: 16rpx;
  padding: 28rpx 24rpx;
  margin-bottom: 20rpx;
}

.diagram-container {
  display: flex;
  flex-direction: column;
  gap: 16rpx;
}

.layer-row {
  display: flex;
  align-items: flex-start;
  gap: 16rpx;
}

.layer-label {
  width: 100rpx;
  padding: 12rpx 8rpx;
  border-radius: 8rpx;
  text-align: center;
  flex-shrink: 0;
}

.layer-label-text {
  font-size: 22rpx;
  font-weight: 600;
}

.layer-nodes {
  flex: 1;
  display: flex;
  gap: 12rpx;
  flex-wrap: wrap;
}

.layer-nodes--single {
  justify-content: center;
}

.node-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8rpx;
  padding: 16rpx 12rpx;
  border-radius: 10rpx;
  min-width: 100rpx;
  flex: 1;
}

.node-item--wide {
  min-width: 200rpx;
  flex-direction: row;
  gap: 12rpx;
}

.node-icon {
  font-size: 32rpx;
}

.node-label {
  font-size: 22rpx;
  color: #333;
  text-align: center;
}

.flow-arrow {
  text-align: center;
  font-size: 24rpx;
  color: #999;
  line-height: 1;
}

/* 操作按钮 */
.action-bar {
  display: flex;
  justify-content: space-around;
  padding: 20rpx 0;
  background: #FFF;
  border-radius: 16rpx;
  margin-bottom: 20rpx;
}

.action-btn {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8rpx;
}

.action-btn-icon {
  font-size: 32rpx;
}

.action-btn-text {
  font-size: 24rpx;
  color: #555;
}

/* 架构说明 */
.desc-section {
  background: #FFF;
  border-radius: 16rpx;
  padding: 28rpx 24rpx;
  margin-bottom: 20rpx;
}

.desc-title {
  font-size: 30rpx;
  font-weight: 700;
  color: #222;
  margin-bottom: 16rpx;
}

.desc-text {
  font-size: 26rpx;
  color: #555;
  line-height: 1.8;
}

/* 推荐模板 */
.template-section {
  background: #FFF;
  border-radius: 16rpx;
  padding: 28rpx 24rpx;
  margin-bottom: 20rpx;
}

.template-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20rpx;
}

.template-title {
  font-size: 30rpx;
  font-weight: 700;
  color: #222;
}

.template-more {
  font-size: 24rpx;
  color: #4D6BFE;
}

.template-list {
  display: flex;
  gap: 16rpx;
  overflow-x: auto;
  padding-bottom: 8rpx;
}

.template-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12rpx;
  min-width: 160rpx;
}

.template-preview {
  width: 160rpx;
  height: 120rpx;
  border-radius: 12rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border: 2rpx solid #F0F0F0;
}

.template-preview-icon {
  font-size: 48rpx;
}

.template-label {
  font-size: 24rpx;
  color: #555;
  text-align: center;
}

/* 底部按钮 */
.bottom-bar {
  display: flex;
  gap: 16rpx;
  padding: 20rpx 24rpx;
  background: #FFF;
  border-top: 1rpx solid #F0F0F0;
}

.bottom-btn {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8rpx;
  padding: 24rpx 0;
  border-radius: 12rpx;
}

.bottom-btn--outline {
  border: 2rpx solid #E8E8E8;
  background: #FFF;
}

.bottom-btn--primary {
  background: linear-gradient(135deg, #6A8CFE 0%, #4D6BFE 100%);
}

.bottom-btn-icon {
  font-size: 28rpx;
}

.bottom-btn-text {
  font-size: 28rpx;
  font-weight: 600;
}

.bottom-btn--outline .bottom-btn-text {
  color: #555;
}

.bottom-btn--primary .bottom-btn-icon,
.bottom-btn--primary .bottom-btn-text {
  color: #FFF;
}
</style>
