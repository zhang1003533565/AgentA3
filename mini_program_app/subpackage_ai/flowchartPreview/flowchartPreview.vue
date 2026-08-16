<template>
  <view class="page">
    <!-- 顶部工具栏 -->
    <view class="top-bar">
      <view class="top-action" @tap="goGuide">
        <text class="top-action-icon">ℹ</text>
        <text class="top-action-text">使用指南</text>
      </view>
      <view class="top-action" @tap="handleExport">
        <text class="top-action-icon">↗</text>
        <text class="top-action-text">导出</text>
      </view>
    </view>

    <view class="content">
      <!-- 生成结果 -->
      <view class="result-section">
        <view class="result-header">
          <view class="result-title-wrapper">
            <view class="result-success-icon">
              <text class="result-success-text">✓</text>
            </view>
            <view class="result-info">
              <text class="result-title">生成结果</text>
              <text class="result-subtitle">已根据输入内容生成流程图</text>
            </view>
          </view>
          <view class="result-actions">
            <view class="result-action-btn" @tap="copyResult">
              <text class="result-action-icon">📋</text>
              <text class="result-action-text">复制</text>
            </view>
            <view class="result-action-btn" @tap="downloadResult">
              <text class="result-action-icon">⬇</text>
              <text class="result-action-text">下载</text>
            </view>
            <view class="result-action-btn" @tap="fullscreenResult">
              <text class="result-action-icon">⛶</text>
              <text class="result-action-text">全屏</text>
            </view>
          </view>
        </view>

        <!-- 流程图 -->
        <view class="flowchart-container">
          <view class="flowchart-content">
            <!-- 开始节点 -->
            <view class="node node--start">
              <text class="node-text">开始</text>
            </view>
            <view class="flow-arrow">↓</view>

            <!-- 步骤1 -->
            <view class="node node--action">
              <text class="node-text">1. 用户提交订单</text>
            </view>
            <view class="flow-arrow">↓</view>

            <!-- 步骤2 -->
            <view class="node node--action">
              <text class="node-text">2. 系统验证库存</text>
            </view>
            <view class="flow-arrow">↓</view>

            <!-- 判断节点 -->
            <view class="node node--decision">
              <text class="node-text">库存充足？</text>
            </view>

            <!-- 分支 -->
            <view class="branch-row">
              <view class="branch-yes">
                <text class="branch-label branch-label--yes">是</text>
                <view class="flow-arrow">↓</view>
                <view class="node node--action">
                  <text class="node-text">3. 进入支付流程</text>
                </view>
                <view class="flow-arrow">↓</view>
                <view class="node node--action">
                  <text class="node-text">4. 用户完成支付</text>
                </view>
                <view class="flow-arrow">↓</view>
                <view class="node node--action">
                  <text class="node-text">5. 生成订单并扣减库存</text>
                </view>
                <view class="flow-arrow">↓</view>
                <view class="node node--action">
                  <text class="node-text">6. 发送订单确认通知</text>
                </view>
                <view class="flow-arrow">↓</view>
                <view class="node node--end">
                  <text class="node-text">结束</text>
                </view>
              </view>
              <view class="branch-no">
                <text class="branch-label branch-label--no">否</text>
                <view class="flow-arrow flow-arrow--horizontal">→</view>
                <view class="node node--error">
                  <text class="node-text">订单失败</text>
                </view>
              </view>
            </view>
          </view>

          <!-- 缩放控制 -->
          <view class="zoom-controls">
            <view class="zoom-btn" @tap="zoomFit">
              <text class="zoom-icon">⛶</text>
            </view>
            <view class="zoom-btn" @tap="zoomIn">
              <text class="zoom-icon">+</text>
            </view>
            <view class="zoom-btn" @tap="zoomOut">
              <text class="zoom-icon">−</text>
            </view>
            <view class="zoom-btn" @tap="lockZoom">
              <text class="zoom-icon">🔒</text>
            </view>
          </view>
        </view>
      </view>

      <!-- Mermaid 代码 -->
      <view class="code-section">
        <view class="code-tabs">
          <view
            class="code-tab"
            :class="{ 'code-tab--active': activeCodeTab === 'mermaid' }"
            @tap="activeCodeTab = 'mermaid'"
          >
            <text class="code-tab-text">Mermaid 代码</text>
          </view>
          <view
            class="code-tab"
            :class="{ 'code-tab--active': activeCodeTab === 'settings' }"
            @tap="activeCodeTab = 'settings'"
          >
            <text class="code-tab-text">预览设置</text>
          </view>
        </view>

        <view class="code-content" v-if="activeCodeTab === 'mermaid'">
          <view class="code-header">
            <view class="code-copy-btn" @tap="copyCode">
              <text class="code-copy-icon">📋</text>
              <text class="code-copy-text">复制代码</text>
            </view>
          </view>
          <view class="code-block">
            <text class="code-text">flowchart TD</text>
            <text class="code-text">A[开始] --> B[1.用户提交订单]</text>
            <text class="code-text">B --> C[2.系统验证库存]</text>
            <text class="code-text">C --> D{库存充足?}</text>
            <text class="code-text">D -- 否 --> E[订单失败]</text>
            <text class="code-text">D -- 是 --> F[3.进入支付流程]</text>
            <text class="code-text">F --> G[4.用户完成支付]</text>
            <text class="code-text">G --> H[5.生成订单并扣减库存]</text>
            <text class="code-text">H --> I[6.发送订单确认通知]</text>
            <text class="code-text">I --> J[结束]</text>
          </view>
        </view>

        <view class="code-tip">
          <text class="code-tip-icon">💡</text>
          <text class="code-tip-text">提示：您可以复制上述 Mermaid 代码到支持 Mermaid 的编辑器中使用</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'

const activeCodeTab = ref('mermaid')

const goGuide = () => { uni.showToast({ title: '使用指南', icon: 'none' }) }
const handleExport = () => { uni.showToast({ title: '导出', icon: 'none' }) }
const copyResult = () => { uni.showToast({ title: '已复制', icon: 'success' }) }
const downloadResult = () => { uni.showToast({ title: '下载', icon: 'none' }) }
const fullscreenResult = () => { uni.showToast({ title: '全屏', icon: 'none' }) }
const zoomFit = () => { uni.showToast({ title: '适应', icon: 'none' }) }
const zoomIn = () => { uni.showToast({ title: '放大', icon: 'none' }) }
const zoomOut = () => { uni.showToast({ title: '缩小', icon: 'none' }) }
const lockZoom = () => { uni.showToast({ title: '锁定', icon: 'none' }) }
const copyCode = () => { uni.showToast({ title: '已复制代码', icon: 'success' }) }
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background-color: #FAFBFC;
  display: flex;
  flex-direction: column;
}

/* 顶部工具栏 */
.top-bar {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 24rpx;
  padding: 20rpx 24rpx;
  background: #FFF;
  border-bottom: 1rpx solid #F0F0F0;
}

.top-action {
  display: flex;
  align-items: center;
  gap: 8rpx;
}

.top-action-icon {
  font-size: 28rpx;
  color: #555;
}

.top-action-text {
  font-size: 26rpx;
  color: #555;
}

.content {
  flex: 1;
  padding: 20rpx 24rpx;
  overflow-y: auto;
}

/* 生成结果 */
.result-section {
  background: #FFF;
  border-radius: 16rpx;
  padding: 28rpx 24rpx;
  margin-bottom: 20rpx;
}

.result-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 24rpx;
}

.result-title-wrapper {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.result-success-icon {
  width: 40rpx;
  height: 40rpx;
  background: #1DD1A1;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.result-success-text {
  font-size: 24rpx;
  color: #FFF;
  font-weight: 700;
}

.result-info {
  display: flex;
  flex-direction: column;
}

.result-title {
  font-size: 30rpx;
  font-weight: 700;
  color: #222;
}

.result-subtitle {
  font-size: 22rpx;
  color: #999;
  margin-top: 4rpx;
}

.result-actions {
  display: flex;
  gap: 12rpx;
}

.result-action-btn {
  display: flex;
  align-items: center;
  gap: 6rpx;
  padding: 10rpx 16rpx;
  border-radius: 8rpx;
  border: 1rpx solid #E8E8E8;
}

.result-action-icon {
  font-size: 22rpx;
}

.result-action-text {
  font-size: 24rpx;
  color: #555;
}

/* 流程图 */
.flowchart-container {
  position: relative;
  padding: 20rpx;
  background: #F8F9FA;
  border-radius: 12rpx;
}

.flowchart-content {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12rpx;
}

.node {
  padding: 16rpx 32rpx;
  border-radius: 8rpx;
  min-width: 200rpx;
  text-align: center;
}

.node--start,
.node--end {
  background: #E8FFF0;
  border-radius: 24rpx;
}

.node--action {
  background: #F0F4FF;
  border: 1rpx solid #D0D8FF;
}

.node--decision {
  background: #FFF8E8;
  border: 1rpx solid #FFE0A0;
  transform: rotate(0deg);
  border-radius: 4rpx;
}

.node--error {
  background: #FFE8E8;
  border: 1rpx solid #FFB8B8;
}

.node-text {
  font-size: 26rpx;
  color: #333;
}

.flow-arrow {
  font-size: 24rpx;
  color: #999;
  line-height: 1;
}

.flow-arrow--horizontal {
  font-size: 28rpx;
}

.branch-row {
  display: flex;
  gap: 40rpx;
  align-items: flex-start;
}

.branch-yes {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12rpx;
}

.branch-no {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12rpx;
  padding-top: 40rpx;
}

.branch-label {
  font-size: 24rpx;
  font-weight: 600;
}

.branch-label--yes {
  color: #1DD1A1;
}

.branch-label--no {
  color: #FF6B6B;
}

/* 缩放控制 */
.zoom-controls {
  position: absolute;
  right: 16rpx;
  top: 50%;
  transform: translateY(-50%);
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}

.zoom-btn {
  width: 48rpx;
  height: 48rpx;
  background: #FFF;
  border-radius: 8rpx;
  border: 1rpx solid #E8E8E8;
  display: flex;
  align-items: center;
  justify-content: center;
}

.zoom-icon {
  font-size: 24rpx;
  color: #555;
}

/* Mermaid 代码 */
.code-section {
  background: #FFF;
  border-radius: 16rpx;
  padding: 28rpx 24rpx;
  margin-bottom: 20rpx;
}

.code-tabs {
  display: flex;
  gap: 24rpx;
  margin-bottom: 20rpx;
  border-bottom: 1rpx solid #F0F0F0;
}

.code-tab {
  padding: 16rpx 0;
  position: relative;
}

.code-tab--active .code-tab-text {
  color: #4D6BFE;
  font-weight: 600;
}

.code-tab--active::after {
  content: '';
  position: absolute;
  bottom: -1rpx;
  left: 0;
  right: 0;
  height: 4rpx;
  background: #4D6BFE;
  border-radius: 2rpx;
}

.code-tab-text {
  font-size: 28rpx;
  color: #888;
}

.code-content {
  position: relative;
}

.code-header {
  display: flex;
  justify-content: flex-end;
  margin-bottom: 12rpx;
}

.code-copy-btn {
  display: flex;
  align-items: center;
  gap: 6rpx;
  padding: 10rpx 16rpx;
  background: #F5F5F5;
  border-radius: 8rpx;
}

.code-copy-icon {
  font-size: 22rpx;
}

.code-copy-text {
  font-size: 24rpx;
  color: #555;
}

.code-block {
  background: #F8F9FA;
  border-radius: 12rpx;
  padding: 20rpx;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}

.code-text {
  font-size: 24rpx;
  color: #555;
  font-family: monospace;
  line-height: 1.6;
}

.code-tip {
  display: flex;
  align-items: flex-start;
  gap: 8rpx;
  padding: 16rpx;
  background: #F8F9FA;
  border-radius: 8rpx;
  margin-top: 16rpx;
}

.code-tip-icon {
  font-size: 24rpx;
  flex-shrink: 0;
}

.code-tip-text {
  font-size: 24rpx;
  color: #888;
  line-height: 1.5;
}
</style>
