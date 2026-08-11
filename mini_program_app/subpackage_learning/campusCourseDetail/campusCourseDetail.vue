<template>
  <view class="page">
    <nav-bar :title="course?.name || '课程详情'" :showBack="true" fixed placeholder />

    <view v-if="loading" class="state-box">加载中...</view>
    <view v-else-if="errorMessage" class="state-box">
      <text>{{ errorMessage }}</text>
      <view class="retry-btn" @tap="loadCourse">重试</view>
    </view>

    <view v-else-if="course">
      <!-- Hero -->
      <view class="course-hero" :style="{ background: heroColor }">
        <image
          v-if="course.displayImageUrl || course.coverUrl"
          :src="course.displayImageUrl || course.coverUrl"
          mode="aspectFill"
          class="hero-bg-img"
        />
        <view class="hero-content">
          <view class="course-status" v-if="course.progressPercent > 0">
            <view class="status-dot"></view>
            <text>正在学习</text>
          </view>
          <view class="course-title">{{ course.name }}</view>
          <view class="hero-meta">
            <text class="students">{{ course.chapters.length }}章节 · {{ course.exams.length }}场考试</text>
          </view>
        </view>
      </view>

      <!-- 进度卡片 -->
      <view class="info-cards">
        <view class="progress-card">
          <view class="progress-card-header">
            <text class="progress-card-title">学习进度</text>
            <text class="progress-card-value">{{ course.progressPercent || 0 }}%</text>
          </view>
          <view class="progress-card-bar">
            <view class="progress-card-inner" :style="{ width: (course.progressPercent || 0) + '%' }"></view>
          </view>
          <view class="progress-card-meta">
            <text>已完成 {{ completedCount }} / {{ course.chapters.length }} 章</text>
          </view>
        </view>
      </view>

      <!-- Tab 栏 -->
      <view class="tabs-section">
        <view class="tab-bar">
          <view
            class="tab"
            :class="{ active: activeTab === 'chapters' }"
            @tap="activeTab = 'chapters'"
          >课程章节</view>
          <view
            class="tab"
            :class="{ active: activeTab === 'intro' }"
            @tap="activeTab = 'intro'"
          >课程详情</view>
          <view
            class="tab"
            :class="{ active: activeTab === 'exams' }"
            @tap="activeTab = 'exams'"
          >
            课程考试
            <text class="badge" v-if="course.exams.length">{{ course.exams.length }}</text>
          </view>
        </view>
      </view>

      <!-- 章节列表 -->
      <view v-if="activeTab === 'chapters'" class="tab-content">
        <view
          v-for="(chapter, index) in course.chapters"
          :key="chapter.id"
          class="chapter-item"
          @tap="goToChapter(chapter)"
        >
          <view class="chapter-status">
            <view v-if="chapter.completed" class="status-icon completed">✓</view>
            <view v-else class="status-icon locked">{{ index + 1 }}</view>
          </view>
          <view class="chapter-info">
            <text class="chapter-name">{{ chapter.title }}</text>
          </view>
          <view class="chapter-meta">
            <text class="duration">{{ chapter.estimatedMinutes || 30 }}分钟</text>
            <text class="status-text" :class="chapter.completed ? 'completed' : 'locked'">
              {{ chapter.completed ? '已完成' : '未学习' }}
            </text>
          </view>
        </view>
        <view v-if="!course.chapters.length" class="empty-card">课程暂未配置章节</view>
      </view>

      <!-- 课程详情 -->
      <view v-else-if="activeTab === 'intro'" class="tab-content">
        <view class="intro-card">
          <view class="intro-section">
            <text class="intro-heading">课程简介</text>
            <text class="intro-text" :selectable="true" user-select>{{ course.description || course.intro || '暂无课程简介' }}</text>
          </view>
          <view class="intro-section" v-if="course.ownerName">
            <text class="intro-heading">课程管理员</text>
            <view class="owner-card">
              <view class="owner-avatar">
                <text>{{ (course.ownerName || '?')[0] }}</text>
              </view>
              <view class="owner-info">
                <text class="owner-name">{{ course.ownerName }}</text>
                <text class="owner-label">{{ course.bookTitle || '课程创建者' }}</text>
              </view>
            </view>
          </view>
          <view class="intro-section">
            <text class="intro-heading">课程信息</text>
            <view class="info-grid">
              <view class="info-item">
                <text class="info-label">章节数</text>
                <text class="info-value">{{ course.chapters.length }}</text>
              </view>
              <view class="info-item">
                <text class="info-label">考试数</text>
                <text class="info-value">{{ course.exams.length }}</text>
              </view>
              <view class="info-item">
                <text class="info-label">已完成</text>
                <text class="info-value">{{ completedCount }}章</text>
              </view>
              <view class="info-item">
                <text class="info-label">进度</text>
                <text class="info-value accent">{{ course.progressPercent || 0 }}%</text>
              </view>
            </view>
          </view>
        </view>
      </view>

      <!-- 考试列表 -->
      <view v-else class="tab-content">
        <view class="exam-header">
          <text>共 {{ course.exams.length }} 场考试</text>
        </view>
        <view class="exam-list">
          <view
            v-for="exam in course.exams"
            :key="exam.id"
            class="exam-item"
          >
            <view class="exam-main">
              <view class="exam-title-row">
                <text class="exam-icon">📝</text>
                <text class="exam-name">{{ exam.title }}</text>
              </view>
              <view class="exam-meta">
                <text>📋 {{ exam.questionCount || 0 }}题</text>
                <text>⏱ {{ exam.durationMinutes || 0 }}分钟</text>
              </view>
            </view>
            <view class="exam-btn" @tap="openExam(exam)">开始考试</view>
          </view>
        </view>
        <view v-if="!course.exams.length" class="empty-card">当前课程暂无已发布考试</view>
      </view>
    </view>

    <!-- 底部栏 -->
    <view v-if="course" class="bottom-bar">
      <view class="enroll-btn" @tap="startLearning">
        {{ course.progressPercent > 0 ? '继续学习' : '开始学习' }}
      </view>
    </view>
  </view>
</template>

<script>
import NavBar from '@/components/nav-bar/nav-bar.vue'
import { getCampusCourseDetail } from '@/api/campusCourse.js'

const HERO_COLORS = [
  'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
  'linear-gradient(135deg, #4facfe 0%, #00f2fe 100%)',
  'linear-gradient(135deg, #43e97b 0%, #38f9d7 100%)',
  'linear-gradient(135deg, #4a90d9 0%, #6ba3e8 100%)'
]

export default {
  components: { NavBar },
  data() {
    return {
      courseId: '',
      course: null,
      loading: false,
      errorMessage: '',
      activeTab: 'chapters',
      heroColor: HERO_COLORS[0]
    }
  },
  computed: {
    completedCount() {
      return (this.course?.chapters || []).filter(c => c.completed).length
    }
  },
  onLoad(options) {
    this.courseId = options?.courseId || ''
    this.heroColor = HERO_COLORS[String(this.courseId).charCodeAt(0) % HERO_COLORS.length]
    this.loadCourse()
  },
  onShow() {
    if (this.course) this.loadCourse(false)
  },
  methods: {
    async loadCourse(showLoading = true) {
      if (!this.courseId) {
        this.errorMessage = '缺少课程编号'
        return
      }
      if (showLoading) this.loading = true
      this.errorMessage = ''
      try {
        const response = await getCampusCourseDetail(this.courseId)
        this.course = response?.data || null
      } catch (error) {
        if (showLoading) this.course = null
        this.errorMessage = error?.msg || error?.message || '加载失败'
      } finally {
        this.loading = false
      }
    },
    goToChapter(chapter) {
      uni.navigateTo({
        url: `/subpackage_learning/chapterLearn/chapterLearn?courseId=${encodeURIComponent(this.courseId)}&chapterId=${encodeURIComponent(chapter.id)}`
      })
    },
    openExam(exam) {
      uni.navigateTo({
        url: `/subpackage_exam/paperDetail/paperDetail?paperId=${encodeURIComponent(exam.paperId)}`
      })
    },
    startLearning() {
      const nextChapter = this.course?.chapters?.find(c => !c.completed)
      if (nextChapter) {
        this.goToChapter(nextChapter)
      } else if (this.course?.chapters?.length) {
        this.goToChapter(this.course.chapters[0])
      }
    },
    goToCourseList() {
      uni.navigateTo({ url: '/subpackage_learning/campusCourseList/campusCourseList' })
    }
  }
}
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  padding-bottom: 160rpx;
  background: #f5f7fa;
}

/* Hero */
.course-hero {
  padding: 40rpx 28rpx 50rpx;
  color: #fff;
  position: relative;
  overflow: hidden;
}

.hero-bg-img {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
}

.hero-content {
  display: flex;
  flex-direction: column;
  position: relative;
  z-index: 1;
}

.course-status {
  display: inline-flex;
  align-items: center;
  align-self: flex-start;
  gap: 10rpx;
  background: rgba(255, 255, 255, 0.22);
  padding: 10rpx 24rpx;
  border-radius: 40rpx;
  font-size: 24rpx;
  margin-bottom: 24rpx;
}

.status-dot {
  width: 10rpx;
  height: 10rpx;
  background: #4ade80;
  border-radius: 50%;
  animation: pulse 2s infinite;
}

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}

.course-title {
  font-size: 44rpx;
  font-weight: 750;
  line-height: 1.3;
}

.hero-meta {
  display: flex;
  align-items: center;
  gap: 16rpx;
  margin-top: 20rpx;
  font-size: 26rpx;
  opacity: 0.9;
}

.divider {
  opacity: 0.5;
}

/* 进度卡片 */
.info-cards {
  margin: -36rpx 24rpx 0;
  position: relative;
  z-index: 10;
}

.progress-card {
  background: #fff;
  border-radius: 20rpx;
  padding: 32rpx;
  box-shadow: 0 8rpx 32rpx rgba(0, 0, 0, 0.08);
}

.progress-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20rpx;
}

.progress-card-title {
  font-size: 30rpx;
  color: #333;
  font-weight: 650;
}

.progress-card-value {
  font-size: 40rpx;
  font-weight: 750;
  color: #4a90d9;
}

.progress-card-bar {
  width: 100%;
  height: 14rpx;
  background: #eef1f5;
  border-radius: 7rpx;
  overflow: hidden;
  margin-bottom: 18rpx;
}

.progress-card-inner {
  height: 100%;
  background: linear-gradient(90deg, #4a90d9, #6ba3e8);
  border-radius: 7rpx;
  transition: width 0.3s;
}

.progress-card-meta {
  font-size: 24rpx;
  color: #999;
}

/* Tab 栏 */
.tabs-section {
  margin-top: 32rpx;
}

.tab-bar {
  display: flex;
  background: #fff;
  margin: 0 24rpx;
  border-radius: 16rpx;
  overflow: hidden;
}

.tab {
  flex: 1;
  text-align: center;
  padding: 24rpx 0;
  font-size: 28rpx;
  color: #666;
  position: relative;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10rpx;
}

.tab.active {
  color: #4a90d9;
  font-weight: 650;
}

.tab.active::after {
  content: '';
  position: absolute;
  bottom: 0;
  left: 50%;
  transform: translateX(-50%);
  width: 56rpx;
  height: 5rpx;
  background: #4a90d9;
  border-radius: 3rpx;
}

.badge {
  background: #ff6b6b;
  color: #fff;
  font-size: 20rpx;
  padding: 4rpx 12rpx;
  border-radius: 40rpx;
}

/* 章节 */
.tab-content {
  padding: 24rpx;
}

.chapter-item {
  display: flex;
  align-items: center;
  padding: 24rpx;
  margin-bottom: 16rpx;
  background: #fff;
  border-radius: 18rpx;
  gap: 18rpx;
  box-shadow: 0 4rpx 16rpx rgba(0, 0, 0, 0.04);
  transition: transform 0.15s;
}

.chapter-item:active {
  transform: scale(0.985);
}

.chapter-status {
  flex-shrink: 0;
}

.status-icon {
  width: 48rpx;
  height: 48rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 22rpx;
  font-weight: 700;
}

.status-icon.completed {
  background: #4ade80;
  color: #fff;
}

.status-icon.locked {
  background: #e8f2fd;
  color: #4a90d9;
}

.chapter-info {
  flex: 1;
}

.chapter-name {
  font-size: 28rpx;
  color: #333;
  font-weight: 550;
}

.chapter-meta {
  display: flex;
  align-items: center;
  gap: 16rpx;
  font-size: 22rpx;
}

.duration {
  color: #999;
}

.status-text {
  padding: 6rpx 14rpx;
  border-radius: 8rpx;
  font-size: 20rpx;
}

.status-text.completed {
  color: #4ade80;
  background: #f0fdf4;
}

.status-text.locked {
  color: #4a90d9;
  background: #e8f2fd;
}

/* 考试 */
.exam-header {
  display: flex;
  justify-content: space-between;
  margin-bottom: 20rpx;
  font-size: 26rpx;
  color: #666;
}

.exam-list {
  display: flex;
  flex-direction: column;
  gap: 18rpx;
}

.exam-item {
  background: #fff;
  border-radius: 18rpx;
  padding: 26rpx;
  display: flex;
  align-items: center;
  gap: 20rpx;
  box-shadow: 0 4rpx 16rpx rgba(0, 0, 0, 0.04);
}

.exam-main {
  flex: 1;
}

.exam-title-row {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin-bottom: 14rpx;
}

.exam-icon {
  font-size: 36rpx;
}

.exam-name {
  font-size: 28rpx;
  font-weight: 650;
  color: #333;
}

.exam-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 24rpx;
  font-size: 24rpx;
  color: #999;
}

.exam-btn {
  background: linear-gradient(135deg, #4a90d9, #5b9fe0);
  color: #fff;
  padding: 16rpx 32rpx;
  border-radius: 32rpx;
  font-size: 26rpx;
  font-weight: 600;
  flex-shrink: 0;
  box-shadow: 0 4rpx 14rpx rgba(74, 144, 217, 0.28);
}

/* 底部栏 */
.bottom-bar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  background: #fff;
  padding: 20rpx 28rpx;
  padding-bottom: calc(20rpx + constant(safe-area-inset-bottom));
  padding-bottom: calc(20rpx + env(safe-area-inset-bottom));
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 -4rpx 24rpx rgba(0, 0, 0, 0.06);
  z-index: 100;
}

.enroll-btn {
  background: linear-gradient(135deg, #4a90d9, #6ba3e8);
  color: #fff;
  padding: 22rpx 80rpx;
  border-radius: 44rpx;
  font-size: 28rpx;
  font-weight: 650;
  box-shadow: 0 8rpx 28rpx rgba(74, 144, 217, 0.4);
}

/* 课程详情 */
.intro-card {
  background: #fff;
  border-radius: 20rpx;
  padding: 32rpx;
  box-shadow: 0 4rpx 20rpx rgba(0, 0, 0, 0.04);
}

.intro-section {
  margin-bottom: 36rpx;
}

.intro-section:last-child {
  margin-bottom: 0;
}

.intro-heading {
  display: block;
  font-size: 30rpx;
  font-weight: 650;
  color: #333;
  margin-bottom: 16rpx;
}

.intro-text {
  font-size: 28rpx;
  color: #666;
  line-height: 1.8;
  display: block;
}

.owner-card {
  display: flex;
  align-items: center;
  gap: 20rpx;
  padding: 24rpx;
  background: #f8f9fa;
  border-radius: 16rpx;
}

.owner-avatar {
  width: 72rpx;
  height: 72rpx;
  border-radius: 50%;
  background: linear-gradient(135deg, #4a90d9, #6ba3e8);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 32rpx;
  color: #fff;
  font-weight: 600;
  flex-shrink: 0;
}

.owner-info {
  flex: 1;
}

.owner-name {
  display: block;
  font-size: 28rpx;
  font-weight: 600;
  color: #333;
}

.owner-label {
  display: block;
  font-size: 24rpx;
  color: #999;
  margin-top: 4rpx;
}

.info-grid {
  display: flex;
  flex-wrap: wrap;
}

.info-item {
  width: 50%;
  padding: 16rpx 0;
}

.info-label {
  display: block;
  font-size: 24rpx;
  color: #999;
  margin-bottom: 6rpx;
}

.info-value {
  display: block;
  font-size: 32rpx;
  font-weight: 650;
  color: #333;
}

.info-value.accent {
  color: #4a90d9;
}

/* 通用 */
.state-box {
  padding: 160rpx 40rpx;
  text-align: center;
  color: #999;
  font-size: 28rpx;
}

.retry-btn {
  display: inline-block;
  margin-top: 20rpx;
  padding: 16rpx 36rpx;
  border-radius: 12rpx;
  border: 1px solid #4a90d9;
  color: #4a90d9;
  font-size: 26rpx;
}

.empty-card {
  padding: 60rpx 20rpx;
  text-align: center;
  color: #999;
  font-size: 26rpx;
  background: #fff;
  border-radius: 18rpx;
}
</style>
