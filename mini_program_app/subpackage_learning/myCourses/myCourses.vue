<template>
  <view class="page">
    <nav-bar title="我的课程" :showBack="true" fixed placeholder />

    <!-- 搜索栏 -->
    <view class="header">
      <view class="search-bar">
        <text class="search-icon">🔍</text>
        <input type="text" placeholder="搜索课程、老师..." v-model="searchKeyword" />
      </view>
    </view>

    <!-- 校园课程入口 -->
    <view class="campus-entry" @tap="goToCourseList">
      <view class="campus-entry-icon">
        <text>📚</text>
      </view>
      <view class="campus-entry-info">
        <text class="campus-entry-title">校园课程</text>
        <text class="campus-entry-desc">浏览全部校园公开课程</text>
      </view>
      <text class="campus-entry-arrow">›</text>
    </view>

    <!-- 继续学习 Banner -->
    <view v-if="continueCourse" class="banner" @tap="goToDetail(continueCourse)">
      <view class="banner-content">
        <view class="banner-title">继续学习</view>
        <text class="banner-course">{{ continueCourse.name }}</text>
        <view class="progress-bar">
          <view class="progress" :style="{ width: (continueCourse.progressPercent || 0) + '%' }"></view>
        </view>
        <text class="progress-text">进度 {{ continueCourse.progressPercent || 0 }}%</text>
      </view>
      <view class="continue-btn">继续学</view>
    </view>

    <!-- 加载/错误状态 -->
    <view v-if="loading" class="state-box">加载中...</view>
    <view v-else-if="errorMessage" class="state-box">
      <text>{{ errorMessage }}</text>
      <view class="retry-btn" @tap="loadMyCourses">重试</view>
    </view>

    <!-- 主内容区 -->
    <template v-else>
      <!-- Tab 切换：我的课程 / 推荐课程 -->
      <view class="section">
        <view class="page-tabs">
          <view
            class="page-tab"
            :class="{ active: activePageTab === 'my' }"
            @tap="activePageTab = 'my'"
          >我的课程</view>
          <view
            class="page-tab"
            :class="{ active: activePageTab === 'recommend' }"
            @tap="switchToRecommend"
          >推荐课程</view>
        </view>

        <!-- 我的课程 -->
        <view v-if="activePageTab === 'my'">
          <view class="course-tabs">
            <view class="tab active">进行中</view>
          </view>
          <view v-if="filteredMyCourses.length === 0" class="state-box">
            <text class="empty-text">暂未添加任何课程</text>
            <text class="empty-hint">去推荐课程中探索吧</text>
          </view>
          <view v-else class="course-list">
            <view
              v-for="course in filteredMyCourses"
              :key="course.id"
              class="course-card"
              @tap="goToDetail(course)"
            >
              <view class="course-cover" :style="{ background: getCoverColor(course.id) }">
                <image
                  v-if="course.coverUrl || course.imageUrl"
                  :src="course.coverUrl || course.imageUrl"
                  mode="aspectFill"
                  class="course-cover-img"
                />
                <text v-else class="course-emoji">{{ getCourseEmoji(course.id) }}</text>
              </view>
              <view class="course-info">
                <view class="course-name">{{ course.name }}</view>
                <text class="teacher">{{ course.bookTitle || course.ownerName || '' }}</text>
                <view class="course-meta">
                  <text class="chapter-count">{{ course.chapterCount }}章</text>
                  <text class="duration" v-if="course.examCount">{{ course.examCount }}场考试</text>
                </view>
                <view class="mini-progress">
                  <view class="mini-progress-bar">
                    <view class="mini-progress-inner" :style="{ width: (course.progressPercent || 0) + '%' }"></view>
                  </view>
                  <text class="progress-num">{{ course.progressPercent || 0 }}%</text>
                </view>
              </view>
            </view>
          </view>
        </view>

        <!-- 推荐课程 -->
        <view v-else>
          <view class="recommend-header">
            <text class="recommend-header-title">为你推荐</text>
            <text class="recommend-link" @tap="goToCourseList">浏览全部 →</text>
          </view>
          <view v-if="recommendLoading" class="state-box">加载中...</view>
          <view v-else-if="recommendCourses.length === 0" class="state-box">
            <text>暂无可推荐的课程</text>
          </view>
          <view v-else class="recommend-list">
            <view
              v-for="course in recommendCourses"
              :key="course.id"
              class="recommend-item"
              @tap="goToDetail(course)"
            >
              <view class="recommend-cover" :style="{ background: getCoverColor(course.id) }">
                <image
                  v-if="course.coverUrl || course.imageUrl"
                  :src="course.coverUrl || course.imageUrl"
                  mode="aspectFill"
                  class="recommend-cover-img"
                />
                <text v-else class="course-emoji">{{ getCourseEmoji(course.id) }}</text>
              </view>
              <view class="recommend-info">
                <view class="recommend-name">{{ course.name }}</view>
                <text class="recommend-meta">{{ course.ownerName || '' }} · {{ course.chapterCount }}章</text>
                <view class="recommend-footer">
                  <text class="students" v-if="course.examCount">{{ course.examCount }}场考试</text>
                  <text class="level">{{ course.level || '初级' }}</text>
                </view>
              </view>
            </view>
          </view>
        </view>
      </view>
    </template>
  </view>
</template>

<script>
import NavBar from '@/components/nav-bar/nav-bar.vue'
import { getMyCourses, getCampusCourses } from '@/api/campusCourse.js'

const COVER_COLORS = [
  'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
  'linear-gradient(135deg, #4facfe 0%, #00f2fe 100%)',
  'linear-gradient(135deg, #43e97b 0%, #38f9d7 100%)',
  'linear-gradient(135deg, #fa709a 0%, #fee140 100%)',
  'linear-gradient(135deg, #a18cd1 0%, #fbc2eb 100%)',
  'linear-gradient(135deg, #4a90d9 0%, #6ba3e8 100%)'
]
const COVER_EMOJIS = ['📚', '💻', '🎨', '🎯', '🗣️', '📊', '⚛️', '🔬']

export default {
  components: { NavBar },
  data() {
    return {
      searchKeyword: '',
      activePageTab: 'my',
      myCourses: [],
      recommendCourses: [],
      loading: false,
      recommendLoading: false,
      errorMessage: ''
    }
  },
  computed: {
    continueCourse() {
      const inProgress = this.myCourses.find(c => (c.progressPercent || 0) > 0 && (c.progressPercent || 0) < 100)
      return inProgress || (this.myCourses.length > 0 ? this.myCourses[0] : null)
    },
    filteredMyCourses() {
      if (!this.searchKeyword) return this.myCourses
      const kw = this.searchKeyword.toLowerCase()
      return this.myCourses.filter(c =>
        (c.name || '').toLowerCase().includes(kw) ||
        (c.bookTitle || '').toLowerCase().includes(kw) ||
        (c.ownerName || '').toLowerCase().includes(kw)
      )
    }
  },
  onLoad() {
    this.loadMyCourses()
  },
  onShow() {
    this.loadMyCourses(false)
  },
  methods: {
    async loadMyCourses(showLoading = true) {
      if (showLoading) this.loading = true
      this.errorMessage = ''
      try {
        const response = await getMyCourses()
        this.myCourses = response?.data || []
      } catch (error) {
        if (showLoading) this.myCourses = []
        this.errorMessage = error?.msg || error?.message || '加载失败'
      } finally {
        this.loading = false
      }
    },
    async loadRecommendCourses() {
      if (this.activePageTab === 'recommend' && this.recommendCourses.length) return
      this.recommendLoading = true
      try {
        const response = await getCampusCourses()
        const allCourses = response?.data || []
        const myIds = new Set(this.myCourses.map(c => c.id))
        this.recommendCourses = allCourses.filter(c => !myIds.has(c.id))
      } catch (error) {
        this.recommendCourses = []
      } finally {
        this.recommendLoading = false
      }
    },
    goToDetail(course) {
      uni.navigateTo({
        url: `/subpackage_learning/campusCourseDetail/campusCourseDetail?courseId=${encodeURIComponent(course.id)}`
      })
    },
    goToCourseList() {
      uni.navigateTo({ url: '/subpackage_learning/campusCourseList/campusCourseList' })
    },
    switchToRecommend() {
      this.activePageTab = 'recommend'
      this.loadRecommendCourses()
    },
    getCoverColor(id) {
      return COVER_COLORS[String(id).charCodeAt(0) % COVER_COLORS.length]
    },
    getCourseEmoji(id) {
      return COVER_EMOJIS[String(id).charCodeAt(0) % COVER_EMOJIS.length]
    }
  }
}
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  padding-bottom: 80rpx;
  background: #f5f7fa;
}

.header {
  background: #fff;
  padding: 20rpx 24rpx;
  border-bottom: 1px solid #f0f0f0;
}

.search-bar {
  display: flex;
  align-items: center;
  background: #f5f5f5;
  border-radius: 40rpx;
  padding: 18rpx 28rpx;
  gap: 14rpx;
}

.search-icon {
  font-size: 28rpx;
}

.search-bar input {
  border: none;
  outline: none;
  flex: 1;
  font-size: 28rpx;
  background: transparent;
}

.campus-entry {
  margin: 20rpx 24rpx 0;
  background: linear-gradient(135deg, #4a90d9, #6ba3e8);
  border-radius: 20rpx;
  padding: 28rpx;
  display: flex;
  align-items: center;
  gap: 18rpx;
  box-shadow: 0 6rpx 22rpx rgba(74, 144, 217, 0.28);
  transition: transform 0.15s;
}

.campus-entry:active {
  transform: scale(0.97);
}

.campus-entry-icon {
  width: 64rpx;
  height: 64rpx;
  border-radius: 16rpx;
  background: rgba(255,255,255,.22);
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 32rpx;
  flex-shrink: 0;
}

.campus-entry-info {
  flex: 1;
}

.campus-entry-title {
  display: block;
  color: #fff;
  font-size: 30rpx;
  font-weight: 700;
}

.campus-entry-desc {
  display: block;
  color: rgba(255,255,255,.72);
  font-size: 24rpx;
  margin-top: 6rpx;
}

.campus-entry-arrow {
  color: rgba(255,255,255,.72);
  font-size: 32rpx;
  flex-shrink: 0;
}

.banner {
  margin: 24rpx 24rpx 0;
  background: linear-gradient(135deg, #e8f2fd 0%, #f0f7ff 100%);
  border-radius: 24rpx;
  padding: 32rpx;
  display: flex;
  justify-content: space-between;
  align-items: center;
  box-shadow: 0 4rpx 20rpx rgba(74, 144, 217, 0.08);
}

.banner-content {
  flex: 1;
}

.banner-title {
  font-size: 30rpx;
  font-weight: 700;
  color: #333;
  margin-bottom: 12rpx;
}

.banner-course {
  font-size: 28rpx;
  font-weight: 600;
  color: #333;
  margin-bottom: 18rpx;
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.progress-bar {
  width: 320rpx;
  height: 10rpx;
  background: #dbe4ee;
  border-radius: 5rpx;
  overflow: hidden;
  margin-bottom: 10rpx;
}

.progress {
  height: 100%;
  background: linear-gradient(90deg, #4a90d9, #6ba3e8);
  border-radius: 5rpx;
}

.progress-text {
  font-size: 22rpx;
  color: #666;
}

.continue-btn {
  background: linear-gradient(135deg, #4a90d9, #5b9fe0);
  color: #fff;
  border-radius: 32rpx;
  padding: 18rpx 36rpx;
  font-size: 26rpx;
  font-weight: 600;
  box-shadow: 0 6rpx 18rpx rgba(74, 144, 217, 0.28);
}

.section {
  padding: 0 24rpx 24rpx;
}

.page-tabs {
  display: flex;
  background: #fff;
  border-radius: 16rpx;
  padding: 6rpx;
  margin: 24rpx 0;
  box-shadow: 0 4rpx 16rpx rgba(0, 0, 0, 0.04);
}

.page-tab {
  flex: 1;
  text-align: center;
  padding: 18rpx 0;
  font-size: 28rpx;
  color: #666;
  border-radius: 12rpx;
  transition: all 0.2s;
}

.page-tab.active {
  background: linear-gradient(135deg, #4a90d9, #5b9fe0);
  color: #fff;
  font-weight: 600;
  box-shadow: 0 4rpx 14rpx rgba(74, 144, 217, 0.28);
}

.course-tabs {
  display: flex;
  gap: 36rpx;
  margin-bottom: 24rpx;
}

.tab {
  font-size: 28rpx;
  color: #666;
  padding-bottom: 14rpx;
  position: relative;
}

.tab.active {
  color: #4a90d9;
  font-weight: 600;
}

.tab.active::after {
  content: '';
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  height: 5rpx;
  background: #4a90d9;
  border-radius: 3rpx;
}

.course-list {
  display: flex;
  flex-direction: column;
  gap: 20rpx;
}

.course-card {
  background: #fff;
  border-radius: 20rpx;
  overflow: hidden;
  display: flex;
  box-shadow: 0 4rpx 24rpx rgba(0, 0, 0, 0.06);
  transition: transform 0.15s;
}

.course-card:active {
  transform: scale(0.98);
}

.course-cover {
  width: 180rpx;
  min-height: 180rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  position: relative;
  overflow: hidden;
}

.course-cover-img {
  width: 100%;
  height: 100%;
  position: absolute;
  top: 0;
  left: 0;
}

.course-emoji {
  font-size: 56rpx;
}

.course-info {
  flex: 1;
  padding: 22rpx;
}

.course-name {
  font-size: 30rpx;
  font-weight: 700;
  color: #333;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.teacher {
  font-size: 24rpx;
  color: #999;
  margin-top: 8rpx;
  display: block;
}

.course-meta {
  display: flex;
  gap: 20rpx;
  font-size: 22rpx;
  color: #666;
  margin: 12rpx 0;
}

.mini-progress {
  display: flex;
  align-items: center;
  gap: 14rpx;
  margin-top: 14rpx;
}

.mini-progress-bar {
  flex: 1;
  height: 8rpx;
  background: #eee;
  border-radius: 4rpx;
  overflow: hidden;
}

.mini-progress-inner {
  height: 100%;
  background: linear-gradient(90deg, #4a90d9, #6ba3e8);
  border-radius: 4rpx;
}

.progress-num {
  font-size: 24rpx;
  color: #4a90d9;
  font-weight: 600;
}

.recommend-list {
  display: flex;
  flex-direction: column;
  gap: 20rpx;
}

.recommend-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 18rpx;
}

.recommend-header-title {
  font-size: 28rpx;
  font-weight: 600;
  color: #333;
}

.recommend-link {
  font-size: 24rpx;
  color: #4a90d9;
}

.recommend-item {
  background: #fff;
  border-radius: 20rpx;
  padding: 22rpx;
  display: flex;
  gap: 22rpx;
  box-shadow: 0 4rpx 24rpx rgba(0, 0, 0, 0.06);
}

.recommend-cover {
  width: 140rpx;
  height: 140rpx;
  border-radius: 18rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  position: relative;
  overflow: hidden;
}

.recommend-cover-img {
  width: 100%;
  height: 100%;
  position: absolute;
  top: 0;
  left: 0;
}

.recommend-info {
  flex: 1;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}

.recommend-name {
  font-size: 30rpx;
  font-weight: 700;
  color: #333;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.recommend-meta {
  font-size: 24rpx;
  color: #999;
  margin: 8rpx 0;
}

.recommend-footer {
  display: flex;
  justify-content: space-between;
  font-size: 22rpx;
}

.students {
  color: #999;
}

.level {
  color: #4a90d9;
  background: #e8f2fd;
  padding: 6rpx 16rpx;
  border-radius: 8rpx;
}

.state-box {
  padding: 120rpx 40rpx;
  text-align: center;
  color: #999;
  font-size: 28rpx;
}

.empty-text {
  font-size: 32rpx;
  color: #333;
  font-weight: 600;
  display: block;
}

.empty-hint {
  font-size: 24rpx;
  color: #999;
  margin-top: 12rpx;
  display: block;
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
</style>
