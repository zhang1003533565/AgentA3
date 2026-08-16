<template>
  <view class="page">
    <nav-bar title="活动图结果" :showBack="true" :border="false" :fixed="true" :placeholder="true" titleAlign="center" />
    <!-- 顶部工具栏 -->
                 <view class="top-bar" style="background: linear-gradient(180deg, #dff0ff 0%, #eaf5ff 100%) !important; border-bottom: 0 !important;">
                   <view class="top-back" @tap="goBack" aria-label="返回上一页">
                     <view class="top-back-icon"></view>
                     <text class="top-back-text">返回</text>
                   </view>
                   <text class="top-title">活动图结果</text>
                   <view class="top-action" @tap="goTutorial">
        <text class="top-action-icon">📖</text>
        <text class="top-action-text">使用教程</text>
      </view>
      <view class="top-action" @tap="goHistory">
        <text class="top-action-icon">🕐</text>
        <text class="top-action-text">历史记录</text>
      </view>
    </view>

    <view class="content">
      <!-- AI生成的描述词 -->
      <view class="desc-section">
        <view class="desc-header">
          <view class="desc-title-wrapper">
            <text class="desc-title">AI 生成的描述词</text>
            <text class="desc-badge">AI 生成</text>
          </view>
          <text class="desc-hint">内容不满意？可直接编辑或重新生成</text>
        </view>
        <view class="desc-content">
          <text class="desc-text">{{ aiDescription }}</text>
        </view>
        <view class="desc-actions">
          <view class="desc-action-btn" @tap="copyDescriptionReal">
            <text class="desc-action-icon">📋</text>
            <text class="desc-action-text">复制</text>
          </view>
          <view class="desc-action-btn" @tap="editDescriptionReal">
            <text class="desc-action-icon">✏️</text>
            <text class="desc-action-text">编辑</text>
          </view>
        </view>
      </view>

      <!-- 图片预览 -->
      <view class="preview-section">
        <view class="preview-header">
          <text class="preview-title">图片预览（示例）</text>
          <text class="preview-regenerate" @tap="regenerate">效果不满意？重新生成</text>
        </view>

        <view class="diagram-container" :style="diagramStyle">
          <!-- 泳道表头 -->
          <view class="swimlane-header">
            <view class="lane-header" style="background: #B8D4F8;">
              <text class="lane-header-text">用户</text>
            </view>
            <view class="lane-header" style="background: #A8D8EA;">
              <text class="lane-header-text">系统</text>
            </view>
            <view class="lane-header" style="background: #88D8B0;">
              <text class="lane-header-text">邮件服务</text>
            </view>
          </view>

          <!-- 泳道内容 -->
          <view class="swimlane-body">
            <view class="lane-column">
              <!-- 用户泳道 -->
              <view class="node node--start"></view>
              <view class="node node--action" style="background: #B8D4F8;">
                <text class="node-text">进入注册页面</text>
              </view>
              <view class="flow-arrow">↓</view>
              <view class="node node--action" style="background: #B8D4F8;">
                <text class="node-text">填写注册信息</text>
              </view>
              <view class="flow-arrow flow-arrow--cross">→</view>
              <view class="node node--action node--error" style="background: #FFB8B8;">
                <text class="node-text">提示信息错误</text>
              </view>
              <view class="flow-arrow">↓</view>
              <view class="node node--action" style="background: #B8F0D4;">
                <text class="node-text">完成验证</text>
              </view>
              <view class="flow-arrow">↓</view>
              <view class="node node--action" style="background: #B8D4F8;">
                <text class="node-text">注册成功</text>
              </view>
              <view class="flow-arrow">↓</view>
              <view class="node node--end"></view>
            </view>

            <view class="lane-column">
              <!-- 系统泳道 -->
              <view class="node-spacer"></view>
              <view class="node-spacer"></view>
              <view class="flow-arrow flow-arrow--cross">←</view>
              <view class="node node--action" style="background: #B8D4F8;">
                <text class="node-text">验证信息</text>
              </view>
              <view class="flow-arrow">↓</view>
              <view class="node node--decision" style="background: #B8D4F8;">
                <text class="node-text">验证通过？</text>
              </view>
              <view class="flow-labels">
                <text class="flow-label flow-label--no">否</text>
                <text class="flow-label flow-label--yes">是</text>
              </view>
              <view class="node node--action" style="background: #B8D4F8;">
                <text class="node-text">创建用户账号</text>
              </view>
              <view class="flow-arrow">↓</view>
              <view class="node node--action" style="background: #B8D4F8;">
                <text class="node-text">发送验证邮件</text>
              </view>
              <view class="flow-arrow flow-arrow--cross">→</view>
              <view class="node-spacer"></view>
            </view>

            <view class="lane-column">
              <!-- 邮件服务泳道 -->
              <view class="node-spacer"></view>
              <view class="node-spacer"></view>
              <view class="node-spacer"></view>
              <view class="node-spacer"></view>
              <view class="node-spacer"></view>
              <view class="node-spacer"></view>
              <view class="node-spacer"></view>
              <view class="node node--action" style="background: #B8D4F8;">
                <text class="node-text">发送验证邮件</text>
              </view>
              <view class="flow-arrow">↓</view>
              <view class="node node--action" style="background: #B8D4F8;">
                <text class="node-text">点击邮件链接</text>
              </view>
              <view class="flow-arrow flow-arrow--cross">←</view>
            </view>
          </view>
        </view>
      </view>
    </view>

    <!-- 底部工具栏 -->
    <view class="bottom-bar">
      <view class="bottom-tool" @tap="zoomInReal">
        <text class="bottom-tool-icon">🔍</text>
        <text class="bottom-tool-text">放大</text>
      </view>
      <view class="bottom-tool" @tap="zoomOutReal">
        <text class="bottom-tool-icon"></text>
        <text class="bottom-tool-text">缩小</text>
      </view>
      <view class="bottom-tool" @tap="fitWidthReal">
        <text class="bottom-tool-icon">⬌</text>
        <text class="bottom-tool-text">适应宽度</text>
      </view>
      <view class="bottom-tool" @tap="fullscreenReal">
        <text class="bottom-tool-icon">⛶</text>
        <text class="bottom-tool-text">全屏</text>
      </view>
      <view class="bottom-divider"></view>
      <view class="bottom-download" @tap="downloadImageReal">
        <text class="bottom-download-icon">⬇</text>
        <text class="bottom-download-text">下载图片</text>
      </view>
      <view class="bottom-copy" @tap="copyImageReal">
        <text class="bottom-copy-icon">📋</text>
        <text class="bottom-copy-text">复制图片</text>
      </view>
    </view>
  </view>
</template>

<script setup>
import { computed, ref } from 'vue'
import { onLoad } from '@dcloudio/uni-app'
import NavBar from '@/components/nav-bar/nav-bar.vue'
// #ifdef H5
import { domToPng } from '../components/domToPng.js'
// #endif

const aiDescription = ref('创建一个清晰的活动图（泳道图），展示用户注册流程。包含三个泳道：用户、系统、邮件服务。流程从用户进入注册页面开始，依次包括填写注册信息、系统验证信息、判断验证结果（通过/不通过）。验证通过则创建用户账号、发送验证邮件，用户点击邮件链接完成验证，最终注册成功。使用现代风格，圆角矩形表示活动，菱形表示判断节点，箭头表示状态流流向，配色以蓝色和绿色为主，界面清晰易读。')

             const goBack = () => { uni.navigateBack({ delta: 1 }) }
             const goTutorial = () => { uni.showToast({ title: '使用教程', icon: 'none' }) }
const goHistory = () => { uni.showToast({ title: '历史记录', icon: 'none' }) }
const copyDesc = () => { uni.showToast({ title: '已复制', icon: 'success' }) }
const editDesc = () => { uni.showToast({ title: '编辑描述词', icon: 'none' }) }
const regenerate = () => { uni.showToast({ title: '重新生成', icon: 'none' }) }
const zoomIn = () => { uni.showToast({ title: '放大', icon: 'none' }) }
const zoomOut = () => { uni.showToast({ title: '缩小', icon: 'none' }) }
const fitWidth = () => { uni.showToast({ title: '适应宽度', icon: 'none' }) }
const fullscreen = () => { uni.showToast({ title: '全屏', icon: 'none' }) }
const downloadImage = () => { uni.showToast({ title: '下载图片', icon: 'none' }) }
const copyImage = () => { uni.showToast({ title: '复制图片', icon: 'none' }) }
const scale = ref(1)
const diagramStyle = computed(() => ({ transform: `scale(${scale.value})`, transformOrigin: 'top center' }))
const copyDescriptionReal = () => uni.setClipboardData({ data: aiDescription.value })
const editDescriptionReal = () => uni.showModal({ title: '编辑描述词', editable: true, content: aiDescription.value, success: r => { if (r.confirm && r.content) aiDescription.value = r.content } })
const regenerateReal = () => uni.navigateBack({ delta: 1 })
const zoomInReal = () => { scale.value = Math.min(1.8, +(scale.value + 0.1).toFixed(2)) }
const zoomOutReal = () => { scale.value = Math.max(0.6, +(scale.value - 0.1).toFixed(2)) }
const fitWidthReal = () => { scale.value = 1 }
const fullscreenReal = () => {
  // #ifdef H5
  const el = document.querySelector('.diagram-container')
  if (document.fullscreenElement) {
    document.exitFullscreen?.()
  } else if (el?.requestFullscreen) {
    el.requestFullscreen()
  } else {
    uni.showToast({ title: '当前环境不支持全屏', icon: 'none' })
  }
  // #endif
}
const downloadImageReal = () => {
  // #ifdef H5
  uni.showLoading({ title: '导出中' })
  domToPng('.diagram-container', { title: '活动图结果', filename: '活动图结果' }).finally(() => uni.hideLoading())
  // #endif
}
const copyImageReal = () => uni.showToast({ title: '请使用下载图片保存结果', icon: 'none' })
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
  position: relative;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 24rpx;
  height: 128rpx;
  padding: 40rpx 28rpx 0;
  box-sizing: border-box;
  background: linear-gradient(180deg, #dff0ff 0%, #eaf5ff 100%);
  border-bottom: 0;
}

.top-back {
  position: absolute;
  left: 24rpx;
  top: calc(50% + 20rpx);
  transform: translateY(-50%);
  display: flex;
  align-items: center;
  gap: 4rpx;
  width: 64rpx;
  height: 64rpx;
  padding: 0;
  border-radius: 999rpx;
  color: #1D1D1F;
  background: transparent;
  z-index: 2;
}

.top-back-icon {
  position: relative;
  width: 20rpx;
  height: 20rpx;
  border-left: 4rpx solid #1D1D1F;
  border-bottom: 4rpx solid #1D1D1F;
  transform: rotate(45deg);
}

.top-back-text {
  display: none;
}

.top-title {
  position: absolute;
  left: 112rpx;
  right: 112rpx;
  top: calc(50% + 20rpx);
  transform: translateY(-50%);
  text-align: center;
  color: #1D1D1F;
  font-size: 32rpx;
  font-weight: 700;
}

.top-action {
  display: none;
  align-items: center;
  gap: 8rpx;
  color: #1D1D1F;
}

.top-action-icon {
  font-size: 28rpx;
}

.top-action-text {
  font-size: 26rpx;
  color: #FFFFFF;
}

.content {
  flex: 1;
  padding: 20rpx 24rpx;
  overflow-y: auto;
}

/* 描述词区块 */
.desc-section {
  background: #FFF;
  border-radius: 16rpx;
  padding: 28rpx 24rpx;
  margin-bottom: 20rpx;
}

.desc-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 16rpx;
}

.desc-title-wrapper {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.desc-title {
  font-size: 30rpx;
  font-weight: 700;
  color: #222;
}

.desc-badge {
  font-size: 20rpx;
  padding: 4rpx 12rpx;
  background: #EEF0FF;
  color: #4D6BFE;
  border-radius: 6rpx;
  font-weight: 600;
}

.desc-hint {
  font-size: 22rpx;
  color: #999;
}

.desc-content {
  padding: 20rpx;
  background: #F8F9FA;
  border-radius: 12rpx;
  margin-bottom: 16rpx;
}

.desc-text {
  font-size: 26rpx;
  color: #555;
  line-height: 1.8;
}

.desc-actions {
  display: flex;
  justify-content: flex-end;
  gap: 16rpx;
}

.desc-action-btn {
  display: flex;
  align-items: center;
  gap: 8rpx;
  padding: 12rpx 20rpx;
  border-radius: 8rpx;
}

.desc-action-icon {
  font-size: 24rpx;
}

.desc-action-text {
  font-size: 26rpx;
  color: #4D6BFE;
}

/* 预览区块 */
.preview-section {
  background: #FFF;
  border-radius: 16rpx;
  padding: 28rpx 24rpx;
  margin-bottom: 20rpx;
}

.preview-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20rpx;
}

.preview-title {
  font-size: 30rpx;
  font-weight: 700;
  color: #222;
}

.preview-regenerate {
  font-size: 24rpx;
  color: #4D6BFE;
}

/* 泳道图 */
.diagram-container {
  border: 2rpx solid #E8E8E8;
  border-radius: 12rpx;
  overflow: hidden;
}

.swimlane-header {
  display: flex;
  border-bottom: 2rpx solid #E8E8E8;
}

.lane-header {
  flex: 1;
  padding: 20rpx 0;
  text-align: center;
  border-right: 1rpx solid rgba(255, 255, 255, 0.3);
}

.lane-header:last-child {
  border-right: none;
}

.lane-header-text {
  font-size: 26rpx;
  color: #333;
  font-weight: 600;
}

.swimlane-body {
  display: flex;
  min-height: 600rpx;
}

.lane-column {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 20rpx 12rpx;
  border-right: 1rpx dashed #E8E8E8;
  gap: 8rpx;
}

.lane-column:last-child {
  border-right: none;
}

/* 节点 */
.node {
  width: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
}

.node--start {
  width: 32rpx;
  height: 32rpx;
  background: #333;
  border-radius: 50%;
  margin: 12rpx 0;
}

.node--end {
  width: 32rpx;
  height: 32rpx;
  background: #333;
  border-radius: 50%;
  position: relative;
  margin: 12rpx 0;
}

.node--end::after {
  content: '';
  position: absolute;
  width: 24rpx;
  height: 24rpx;
  border: 3rpx solid #333;
  border-radius: 50%;
}

.node--action {
  padding: 14rpx 16rpx;
  border-radius: 8rpx;
  min-height: 56rpx;
}

.node--error {
  background: #FFB8B8 !important;
}

.node--decision {
  padding: 14rpx 16rpx;
  border-radius: 4rpx;
  transform: rotate(0deg);
  background: #B8D4F8;
  min-height: 56rpx;
}

.node-text {
  font-size: 22rpx;
  color: #333;
  text-align: center;
  line-height: 1.4;
}

.node-spacer {
  height: 56rpx;
}

/* 流程箭头 */
.flow-arrow {
  font-size: 24rpx;
  color: #999;
  line-height: 1;
}

.flow-arrow--cross {
  font-size: 28rpx;
}

.flow-labels {
  display: flex;
  justify-content: space-between;
  width: 100%;
  padding: 0 8rpx;
}

.flow-label {
  font-size: 20rpx;
}

.flow-label--no {
  color: #FF6B6B;
}

.flow-label--yes {
  color: #1DD1A1;
}

/* 底部工具栏 */
.bottom-bar {
  display: flex;
  align-items: center;
  padding: 16rpx 24rpx;
  background: #FFF;
  border-top: 1rpx solid #F0F0F0;
  gap: 8rpx;
  overflow-x: auto;
}

.bottom-tool {
  display: flex;
  align-items: center;
  gap: 6rpx;
  padding: 12rpx 16rpx;
  border-radius: 8rpx;
  border: 1rpx solid #E8E8E8;
  white-space: nowrap;
}

.bottom-tool-icon {
  font-size: 24rpx;
}

.bottom-tool-text {
  font-size: 24rpx;
  color: #555;
}

.bottom-divider {
  width: 1rpx;
  height: 32rpx;
  background: #E8E8E8;
  margin: 0 8rpx;
  flex-shrink: 0;
}

.bottom-download {
  display: flex;
  align-items: center;
  gap: 8rpx;
  padding: 14rpx 24rpx;
  background: linear-gradient(135deg, #6A8CFE 0%, #4D6BFE 100%);
  border-radius: 10rpx;
  white-space: nowrap;
}

.bottom-download-icon {
  font-size: 24rpx;
  color: #FFF;
}

.bottom-download-text {
  font-size: 24rpx;
  color: #FFF;
  font-weight: 600;
}

.bottom-copy {
  display: flex;
  align-items: center;
  gap: 8rpx;
  padding: 14rpx 20rpx;
  border-radius: 10rpx;
  border: 1rpx solid #E8E8E8;
  white-space: nowrap;
}

.bottom-copy-icon {
  font-size: 24rpx;
}

.bottom-copy-text {
  font-size: 24rpx;
  color: #555;
}
.page { background: #F6F7FB; color: #172033; }
.content { padding: 20rpx 24rpx 40rpx; }
.desc-section, .preview-section { border: 1rpx solid #E6E8F1; border-radius: 22rpx; box-shadow: 0 10rpx 28rpx rgba(20, 28, 48, 0.045); }
.desc-content, .diagram-container { border-color: #DBE1ED; background: #F7FAFE; }
.desc-badge { background: #EEF4FC; color: #123E6D; }
.desc-action-text, .preview-regenerate { color: #123E6D; }
.bottom-bar { border-top-color: #E6E8F1; background: #FFFFFF; }
.bottom-tool, .bottom-copy { border-color: #DBE1ED; }
.bottom-download { background: #123E6D; }

/* 结果页顶部与其他图表页面统一 */
.top-bar {
  height: 112rpx;
  min-height: 112rpx;
  padding: 0 28rpx;
  box-sizing: border-box;
  background: #EAF3FD !important;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
}

.top-back {
  position: absolute;
  left: 28rpx;
  top: 50%;
  transform: translateY(-50%);
  width: 48rpx;
  height: 48rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.top-back-text { display: none; }
.top-back-icon { width: 22rpx; height: 22rpx; border-left: 4rpx solid #203452; border-bottom: 4rpx solid #203452; transform: rotate(45deg); }
.top-title { color: #172033; font-size: 36rpx; line-height: 1.2; font-weight: 800; }
.top-action { display: none; }

/* 描述区采用明确的标题—提示—正文层级 */
.content { padding: 24rpx 24rpx 48rpx; }
.desc-section, .preview-section { padding: 28rpx; margin-bottom: 24rpx; background: #FFFFFF; }
.desc-header { display: block; }
.desc-title-wrapper { display: flex; align-items: center; gap: 16rpx; }
.desc-title { color: #172033; font-size: 32rpx; line-height: 1.35; font-weight: 800; }
.desc-badge { padding: 8rpx 14rpx; border-radius: 10rpx; font-size: 22rpx; font-weight: 700; }
.desc-hint { display: block; margin-top: 12rpx; color: #7C879A; font-size: 24rpx; line-height: 1.5; }
.desc-content { margin-top: 22rpx; padding: 24rpx; border-radius: 16rpx; }
.desc-text { color: #40516A; font-size: 25rpx; line-height: 1.9; }
.desc-actions { justify-content: flex-end; gap: 28rpx; margin-top: 18rpx; }
.desc-action-btn { padding: 8rpx 4rpx; }
.desc-action-icon { font-size: 22rpx; }
.desc-action-text { font-size: 24rpx; font-weight: 600; }

/* 预览区标题和画布分层，画布保持统一浅底 */
.preview-header { display: flex; align-items: center; justify-content: space-between; gap: 18rpx; margin-bottom: 20rpx; }
.preview-title { color: #172033; font-size: 30rpx; font-weight: 800; }
.preview-regenerate { flex-shrink: 0; font-size: 22rpx; }
.diagram-container { border-radius: 16rpx; overflow: hidden; }
.lane-header-text { font-size: 26rpx; font-weight: 700; }
.node-text { color: #25344D; font-size: 22rpx; }

/* 底部工具栏收敛尺寸，突出下载操作 */
.bottom-bar { padding: 12rpx 20rpx calc(14rpx + env(safe-area-inset-bottom)); gap: 8rpx; }
.bottom-tool, .bottom-copy { min-height: 64rpx; padding: 8rpx 12rpx; border-radius: 12rpx; }
.bottom-tool-text, .bottom-copy-text, .bottom-download-text { font-size: 22rpx; }
.bottom-download { min-height: 64rpx; padding: 8rpx 18rpx; border-radius: 12rpx; }

/* 强制覆盖旧版结果页样式，确保实际页面呈现统一布局 */
.activity-preview-page .top-bar,
.page .top-bar { height: 112rpx !important; min-height: 112rpx !important; padding: 0 28rpx !important; justify-content: center !important; background: #EAF3FD !important; }
.page .top-back { left: 28rpx !important; top: 50% !important; width: 48rpx !important; height: 48rpx !important; transform: translateY(-50%) !important; }
.page .top-back-icon { width: 16rpx !important; height: 16rpx !important; border-width: 3rpx !important; flex: 0 0 16rpx !important; }
.page .top-back-text, .page .top-action { display: none !important; }
.page > .top-bar { display: none !important; }
.page .top-title { font-size: 36rpx !important; font-weight: 800 !important; color: #172033 !important; }
.page .desc-header { display: block !important; }
.page .desc-title-wrapper { display: flex !important; align-items: center !important; flex-wrap: nowrap !important; }
.page .desc-hint { display: block !important; width: 100% !important; margin-top: 12rpx !important; }
.page .preview-header { display: flex !important; align-items: center !important; justify-content: space-between !important; }
.page .bottom-bar { min-height: 92rpx !important; padding: 12rpx 20rpx !important; }
.page .bottom-tool, .page .bottom-copy, .page .bottom-download { min-height: 64rpx !important; padding: 8rpx 12rpx !important; }
.page .bottom-tool-text, .page .bottom-copy-text, .page .bottom-download-text { font-size: 22rpx !important; line-height: 1.2 !important; }
</style>
