<template>
  <view class="page">
    <!-- 导航栏 -->
    <view class="nav-bar">
      <view class="nav-back" @tap="goBack">
        <text class="nav-back-icon">‹</text>
      </view>
      <text class="nav-title">方案预览</text>
      <view class="nav-regenerate" @tap="regenerate">
        <text class="nav-regenerate-icon">🔄</text>
        <text class="nav-regenerate-text">重新生成</text>
      </view>
    </view>

    <view class="content">
      <!-- 提示信息 -->
      <view class="info-banner">
        <text class="info-icon">ⓘ</text>
        <view class="info-text-wrapper">
          <text class="info-title">当前为方案预览阶段，确认后将生成 PPT 文件</text>
          <text class="info-desc">您可以查看各智能体输出的结果，确认无误后再生成</text>
        </view>
      </view>

      <!-- Tab 切换 -->
      <view class="tab-bar">
        <view
          class="tab-item"
          :class="{ 'tab-item--active': activeTab === item.key }"
          v-for="item in tabs"
          :key="item.key"
          @tap="activeTab = item.key"
        >
          <text class="tab-icon">{{ item.icon }}</text>
          <text class="tab-label">{{ item.label }}</text>
        </view>
      </view>

      <!-- 大纲结构预览 -->
      <view class="section" v-if="activeTab === 'outline'">
        <view class="section-header">
          <text class="section-title">大纲结构预览</text>
          <text class="section-info">共 {{ outlineList.length }} 页（含封面）</text>
        </view>

        <view class="outline-list">
          <view
            class="outline-item"
            v-for="(item, index) in visibleOutlines"
            :key="index"
            @tap="toggleOutlineDetail(index)"
          >
            <view class="outline-number">{{ item.pageNum }}</view>
            <view class="outline-content">
              <text class="outline-title">{{ item.title }}</text>
              <text class="outline-desc">{{ item.desc }}</text>
            </view>
            <text class="outline-arrow">›</text>
          </view>
          <view class="outline-ellipsis" v-if="outlineList.length > 6">
            <text>...</text>
          </view>
          <view
            class="outline-item"
            v-if="outlineList.length > 6"
            @tap="toggleOutlineDetail(outlineList.length - 1)"
          >
            <view class="outline-number">{{ outlineList[outlineList.length - 1].pageNum }}</view>
            <view class="outline-content">
              <text class="outline-title">{{ outlineList[outlineList.length - 1].title }}</text>
              <text class="outline-desc">{{ outlineList[outlineList.length - 1].desc }}</text>
            </view>
            <text class="outline-arrow">›</text>
          </view>
        </view>
      </view>

      <!-- 本阶段输出内容 -->
      <view class="section">
        <view class="section-header">
          <text class="section-title">本阶段输出内容</text>
        </view>

        <view class="output-cards">
          <view class="output-card output-card--outline">
            <text class="output-card-title">大纲文档</text>
            <text class="output-card-value">12页</text>
          </view>
          <view class="output-card output-card--layout">
            <text class="output-card-title">布局方案</text>
            <text class="output-card-value">12页</text>
          </view>
          <view class="output-card output-card--image">
            <text class="output-card-title">图片提示词</text>
            <text class="output-card-value">28项</text>
          </view>
          <view class="output-card output-card--review">
            <text class="output-card-title">审查报告</text>
            <text class="output-card-value">8项建议</text>
          </view>
        </view>
      </view>

      <!-- 底部按钮 -->
      <view class="bottom-actions">
        <view class="btn-back" @tap="goBack">
          <text class="btn-back-text"> 返回修改</text>
        </view>
        <view class="btn-confirm" @tap="confirmGenerate">
          <text class="btn-confirm-text">📄 确认并生成PPT</text>
        </view>
      </view>

      <text class="bottom-hint">将基于以上方案生成可下载的 PPT 文件</text>
    </view>
  </view>
</template>

<script setup>
import { ref, computed } from 'vue'

const activeTab = ref('outline')

const tabs = [
  { key: 'outline', label: '大纲方案', icon: '📋' },
  { key: 'layout', label: '布局方案', icon: '' },
  { key: 'image', label: '图片方案', icon: '🎨' },
  { key: 'review', label: '审查建议', icon: '📝' },
]

const outlineList = ref([
  { pageNum: 1, title: '封面', desc: '人工智能的发展趋势与应用场景' },
  { pageNum: 2, title: '目录', desc: '章节目录与内容概览' },
  { pageNum: 3, title: '人工智能的定义与发展历程', desc: '概念、起源与关键里程碑' },
  { pageNum: 4, title: '人工智能的核心技术', desc: '机器学习、深度学习等关键技术' },
  { pageNum: 5, title: '人工智能的应用场景', desc: '行业应用与实际案例分析' },
  { pageNum: 6, title: '人工智能的挑战与风险', desc: '伦理问题、数据安全与技术局限' },
  { pageNum: 7, title: '人工智能的未来趋势', desc: '技术演进与产业发展方向' },
  { pageNum: 8, title: '总结与展望', desc: '未来趋势与发展方向' },
  { pageNum: 9, title: '参考文献', desc: '相关学术资料与引用' },
  { pageNum: 10, title: '致谢', desc: '感谢观看' },
  { pageNum: 11, title: '附录A', desc: '技术术语表' },
  { pageNum: 12, title: '附录B', desc: '数据来源说明' },
])

const visibleOutlines = computed(() => {
  return outlineList.value.slice(0, 5)
})

const goBack = () => { uni.navigateBack() }
const regenerate = () => { uni.showToast({ title: '重新生成', icon: 'none' }) }
const toggleOutlineDetail = (idx) => { uni.showToast({ title: outlineList.value[idx]?.title || '', icon: 'none' }) }

const confirmGenerate = () => {
  uni.showModal({
    title: '确认生成',
    content: '将基于以上方案生成可下载的 PPT 文件，是否继续？',
    success: (res) => {
      if (res.confirm) {
        uni.showToast({ title: 'PPT生成中...', icon: 'loading' })
      }
    }
  })
}
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background-color: #F6F8FB;
  padding-bottom: 40rpx;
}

/* 导航栏 */
.nav-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 20rpx 24rpx;
  background: #FFF;
  position: sticky;
  top: 0;
  z-index: 100;
}

.nav-back {
  width: 60rpx;
  height: 60rpx;
  display: flex;
  align-items: center;
  justify-content: center;
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
}

.nav-regenerate {
  display: flex;
  align-items: center;
  gap: 8rpx;
  padding: 12rpx 20rpx;
  background: #F0F2F5;
  border-radius: 999rpx;
}

.nav-regenerate-icon {
  font-size: 24rpx;
}

.nav-regenerate-text {
  font-size: 24rpx;
  color: #666;
}

.content {
  padding: 20rpx 24rpx;
}

/* 提示信息 */
.info-banner {
  display: flex;
  align-items: flex-start;
  gap: 16rpx;
  padding: 24rpx;
  background: #F0F0FF;
  border-radius: 12rpx;
  margin-bottom: 24rpx;
}

.info-icon {
  font-size: 36rpx;
  color: #4D6BFE;
  flex-shrink: 0;
}

.info-text-wrapper {
  flex: 1;
}

.info-title {
  font-size: 28rpx;
  color: #4D6BFE;
  font-weight: 600;
  line-height: 1.5;
}

.info-desc {
  font-size: 24rpx;
  color: #8888CC;
  line-height: 1.5;
  margin-top: 8rpx;
}

/* Tab 切换 */
.tab-bar {
  display: flex;
  gap: 12rpx;
  margin-bottom: 24rpx;
  overflow-x: auto;
}

.tab-item {
  display: flex;
  align-items: center;
  gap: 8rpx;
  padding: 16rpx 24rpx;
  background: #FFF;
  border-radius: 999rpx;
  border: 2rpx solid #F0F0F0;
  white-space: nowrap;
}

.tab-item--active {
  background: #EEF0FF;
  border-color: #4D6BFE;
}

.tab-icon {
  font-size: 28rpx;
}

.tab-label {
  font-size: 26rpx;
  color: #555;
}

.tab-item--active .tab-label {
  color: #4D6BFE;
  font-weight: 600;
}

/* 区块 */
.section {
  background: #FFF;
  border-radius: 16rpx;
  padding: 28rpx 24rpx;
  margin-bottom: 20rpx;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20rpx;
}

.section-title {
  font-size: 30rpx;
  font-weight: 700;
  color: #222;
}

.section-info {
  font-size: 24rpx;
  color: #999;
}

/* 大纲列表 */
.outline-list {
  display: flex;
  flex-direction: column;
}

.outline-item {
  display: flex;
  align-items: center;
  gap: 20rpx;
  padding: 24rpx 0;
  border-bottom: 1rpx solid #F5F5F5;
}

.outline-item:last-child {
  border-bottom: none;
}

.outline-number {
  width: 52rpx;
  height: 52rpx;
  background: #4D6BFE;
  border-radius: 12rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 26rpx;
  color: #FFF;
  font-weight: 700;
  flex-shrink: 0;
}

.outline-content {
  flex: 1;
  min-width: 0;
}

.outline-title {
  font-size: 28rpx;
  color: #222;
  font-weight: 600;
  line-height: 1.4;
}

.outline-desc {
  font-size: 24rpx;
  color: #999;
  line-height: 1.4;
  margin-top: 6rpx;
}

.outline-arrow {
  font-size: 36rpx;
  color: #CCC;
  flex-shrink: 0;
}

.outline-ellipsis {
  text-align: center;
  padding: 16rpx 0;
  color: #CCC;
  font-size: 28rpx;
}

/* 输出卡片 */
.output-cards {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 16rpx;
}

.output-card {
  padding: 24rpx 20rpx;
  border-radius: 12rpx;
  display: flex;
  flex-direction: column;
  gap: 12rpx;
}

.output-card--outline {
  background: #F0F4FF;
}

.output-card--layout {
  background: #F0F0FF;
}

.output-card--image {
  background: #FFF8F0;
}

.output-card--review {
  background: #F0FFF4;
}

.output-card-title {
  font-size: 28rpx;
  font-weight: 600;
}

.output-card--outline .output-card-title { color: #4D6BFE; }
.output-card--layout .output-card-title { color: #6B5BFE; }
.output-card--image .output-card-title { color: #FE8C4D; }
.output-card--review .output-card-title { color: #4DB87A; }

.output-card-value {
  font-size: 24rpx;
  color: #999;
}

/* 底部按钮 */
.bottom-actions {
  display: flex;
  gap: 16rpx;
  margin-top: 24rpx;
}

.btn-back {
  flex: 1;
  padding: 28rpx 0;
  text-align: center;
  background: #FFF;
  border: 2rpx solid #E0E0E0;
  border-radius: 16rpx;
}

.btn-back-text {
  font-size: 30rpx;
  color: #666;
  font-weight: 600;
}

.btn-confirm {
  flex: 1.5;
  padding: 28rpx 0;
  text-align: center;
  background: linear-gradient(135deg, #6A8CFE 0%, #4D6BFE 100%);
  border-radius: 16rpx;
}

.btn-confirm-text {
  font-size: 30rpx;
  color: #FFF;
  font-weight: 700;
}

.bottom-hint {
  display: block;
  text-align: center;
  font-size: 24rpx;
  color: #BBB;
  margin-top: 20rpx;
}
</style>
