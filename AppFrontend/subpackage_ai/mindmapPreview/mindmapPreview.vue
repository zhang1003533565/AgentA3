<template>
  <view class="page">
    <!-- 顶部工具栏 -->
    <view class="top-bar">
      <text class="top-title">预览效果</text>
      <view class="top-actions">
        <view class="top-dropdown">
          <text class="top-dropdown-icon">📊</text>
          <text class="top-dropdown-text">思维导图</text>
          <text class="top-dropdown-arrow">∨</text>
        </view>
        <view class="top-fullscreen" @tap="toggleFullscreen">
          <text class="top-fullscreen-icon">⛶</text>
        </view>
      </view>
    </view>

    <!-- 思维导图画布 -->
    <view class="canvas-wrapper" :class="{ 'canvas-wrapper--fullscreen': isFullscreen }">
      <scroll-view class="canvas-scroll" scroll-x scroll-y>
        <view class="mindmap-canvas">
          <!-- 中心节点 -->
          <view class="center-node">
            <text class="center-node-text">{{ topic }}</text>
          </view>

          <!-- 分支 -->
          <view class="branches">
            <view class="branch" v-for="(branch, bi) in branches" :key="bi" :style="{ '--branch-color': branch.color }">
              <!-- 分支线 -->
              <view class="branch-line" :style="{ background: branch.color }"></view>
              <!-- 分支节点 -->
              <view class="branch-node" :style="{ background: branch.color }">
                <text class="branch-node-text">{{ branch.title }}</text>
              </view>
              <!-- 子节点 -->
              <view class="sub-branches">
                <view class="sub-branch" v-for="(sub, si) in branch.children" :key="si">
                  <view class="sub-line" :style="{ background: branch.color }"></view>
                  <view class="sub-group" v-if="sub.children && sub.children.length">
                    <text class="sub-node-text">{{ sub.title }}</text>
                    <view class="sub-sub-branches">
                      <view class="sub-sub-branch" v-for="(ss, ssi) in sub.children" :key="ssi">
                        <view class="sub-sub-line" :style="{ background: branch.color }"></view>
                        <text class="sub-sub-node-text">{{ ss }}</text>
                      </view>
                    </view>
                  </view>
                  <text class="sub-node-text" v-else>{{ sub.title }}</text>
                </view>
              </view>
            </view>
          </view>
        </view>
      </scroll-view>
    </view>

    <!-- 底部操作栏 -->
    <view class="bottom-bar">
      <view class="bottom-action" @tap="regenerate">
        <text class="bottom-action-icon">🔄</text>
        <text class="bottom-action-text">重新生成</text>
      </view>
      <view class="bottom-action" @tap="changeStyle">
        <text class="bottom-action-icon">🎨</text>
        <text class="bottom-action-text">更换风格</text>
      </view>
      <view class="bottom-action" @tap="editContent">
        <text class="bottom-action-icon">✏️</text>
        <text class="bottom-action-text">编辑内容</text>
      </view>
      <view class="bottom-export" @tap="exportImage">
        <text class="bottom-export-icon">⬇</text>
        <text class="bottom-export-text">导出图片</text>
        <text class="bottom-export-arrow">∨</text>
      </view>
    </view>
  </view>
</template>

<script setup>
import { ref } from 'vue'

const topic = ref('人工智能的发展趋势')
const isFullscreen = ref(false)

const branches = ref([
  {
    title: '技术发展',
    color: '#4D6BFE',
    children: [
      { title: '算法进步', children: ['深度学习', '强化学习', '迁移学习'] },
      { title: '算力提升', children: ['GPU/TPU', '量子计算', '边缘计算'] },
      { title: '数据驱动', children: ['大数据', '数据清洗', '数据标注'] },
    ]
  },
  {
    title: '应用领域',
    color: '#1DD1A1',
    children: [
      { title: '智能制造', children: ['工业机器人', '预测性维护'] },
      { title: '智慧医疗', children: ['辅助诊断', '药物研发'] },
      { title: '智能生活', children: ['智能家居', '自动驾驶'] },
    ]
  },
  {
    title: '产业趋势',
    color: '#FF9F43',
    children: [
      { title: '市场规模扩大' },
      { title: '企业加速布局' },
      { title: '跨界融合创新' },
      { title: '人才需求增长' },
    ]
  },
  {
    title: '挑战与未来',
    color: '#A55EEA',
    children: [
      { title: '数据隐私安全' },
      { title: '伦理道德问题' },
      { title: '技术可解释性' },
      { title: '未来发展方向' },
    ]
  },
])

const toggleFullscreen = () => { isFullscreen.value = !isFullscreen.value }
const regenerate = () => { uni.showToast({ title: '重新生成', icon: 'none' }) }
const changeStyle = () => { uni.showToast({ title: '更换风格', icon: 'none' }) }
const editContent = () => { uni.showToast({ title: '编辑内容', icon: 'none' }) }
const exportImage = () => { uni.showToast({ title: '导出图片', icon: 'none' }) }
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
  justify-content: space-between;
  padding: 20rpx 24rpx;
  background: #FFF;
  border-bottom: 1rpx solid #F0F0F0;
}

.top-title {
  font-size: 32rpx;
  font-weight: 700;
  color: #222;
}

.top-actions {
  display: flex;
  align-items: center;
  gap: 16rpx;
}

.top-dropdown {
  display: flex;
  align-items: center;
  gap: 8rpx;
  padding: 10rpx 20rpx;
  background: #F0F2F5;
  border-radius: 8rpx;
}

.top-dropdown-icon {
  font-size: 24rpx;
}

.top-dropdown-text {
  font-size: 26rpx;
  color: #555;
}

.top-dropdown-arrow {
  font-size: 20rpx;
  color: #999;
}

.top-fullscreen {
  width: 56rpx;
  height: 56rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #F0F2F5;
  border-radius: 8rpx;
}

.top-fullscreen-icon {
  font-size: 28rpx;
  color: #555;
}

/* 画布区域 */
.canvas-wrapper {
  flex: 1;
  overflow: hidden;
  padding: 40rpx 24rpx;
}

.canvas-wrapper--fullscreen {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 999;
  background: #FFF;
  padding: 20rpx;
}

.canvas-scroll {
  width: 100%;
  height: 100%;
}

/* 思维导图 */
.mindmap-canvas {
  min-width: 900rpx;
  min-height: 800rpx;
  padding: 60rpx 40rpx;
  position: relative;
}

.center-node {
  background: #2D3748;
  border-radius: 16rpx;
  padding: 24rpx 32rpx;
  display: inline-block;
  margin-bottom: 60rpx;
}

.center-node-text {
  font-size: 30rpx;
  color: #FFF;
  font-weight: 700;
  line-height: 1.5;
}

.branches {
  display: flex;
  flex-direction: column;
  gap: 48rpx;
}

.branch {
  display: flex;
  align-items: flex-start;
  gap: 20rpx;
  position: relative;
}

.branch-line {
  width: 4rpx;
  min-height: 120rpx;
  border-radius: 2rpx;
  flex-shrink: 0;
  margin-top: 20rpx;
}

.branch-node {
  border-radius: 12rpx;
  padding: 14rpx 24rpx;
  flex-shrink: 0;
  margin-top: 8rpx;
}

.branch-node-text {
  font-size: 26rpx;
  color: #FFF;
  font-weight: 600;
  white-space: nowrap;
}

.sub-branches {
  display: flex;
  flex-direction: column;
  gap: 20rpx;
  padding-left: 20rpx;
  flex: 1;
}

.sub-branch {
  display: flex;
  align-items: flex-start;
  gap: 16rpx;
  position: relative;
}

.sub-line {
  width: 3rpx;
  min-height: 60rpx;
  border-radius: 2rpx;
  flex-shrink: 0;
  margin-top: 12rpx;
  opacity: 0.5;
}

.sub-node-text {
  font-size: 26rpx;
  color: #555;
  line-height: 1.6;
  margin-top: 8rpx;
}

.sub-group {
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}

.sub-sub-branches {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
  padding-left: 16rpx;
}

.sub-sub-branch {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.sub-sub-line {
  width: 2rpx;
  height: 32rpx;
  border-radius: 1rpx;
  flex-shrink: 0;
  opacity: 0.3;
}

.sub-sub-node-text {
  font-size: 24rpx;
  color: #888;
  line-height: 1.5;
}

/* 底部操作栏 */
.bottom-bar {
  display: flex;
  align-items: center;
  gap: 12rpx;
  padding: 20rpx 24rpx;
  background: #FFF;
  border-top: 1rpx solid #F0F0F0;
}

.bottom-action {
  display: flex;
  align-items: center;
  gap: 8rpx;
  padding: 16rpx 20rpx;
  border-radius: 12rpx;
  border: 1rpx solid #E8E8E8;
  background: #FFF;
}

.bottom-action-icon {
  font-size: 28rpx;
}

.bottom-action-text {
  font-size: 26rpx;
  color: #555;
}

.bottom-export {
  display: flex;
  align-items: center;
  gap: 8rpx;
  padding: 16rpx 28rpx;
  border-radius: 12rpx;
  background: linear-gradient(135deg, #6A8CFE 0%, #4D6BFE 100%);
  margin-left: auto;
}

.bottom-export-icon {
  font-size: 26rpx;
  color: #FFF;
}

.bottom-export-text {
  font-size: 26rpx;
  color: #FFF;
  font-weight: 600;
}

.bottom-export-arrow {
  font-size: 20rpx;
  color: rgba(255, 255, 255, 0.7);
}
</style>
