<template>
  <view class="page">
    <nav-bar title="我的课程" :showBack="true" fixed placeholder />

    <!-- 搜索栏 -->
    <view class="header">
      <view class="search-bar">
        <input
          type="text"
          placeholder="搜索课程、老师..."
          placeholder-class="search-placeholder"
          v-model="searchKeyword"
        />
      </view>
    </view>

    <!-- 校园课程入口 -->
    <view class="campus-entry" @tap="goToCourseList">
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
            <view class="tab" :class="{ active: activeCourseTab === 'inProgress' }" @tap="selectCourseStatus('inProgress')">进行中</view>
            <view class="tab" :class="{ active: activeCourseTab === 'completed' }" @tap="selectCourseStatus('completed')">已完成</view>
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
                <text class="teacher course-meta-line">
                  {{ course.teacherName || course.ownerName || '暂无教师' }} · {{ course.chapterCount || 0 }}章 · {{ course.examCount ? course.examCount + '场考试' : '无考试' }}
                </text>
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
          <view v-if="false" class="recommend-header">
            <text class="recommend-header-title">为你推荐</text>
            <text class="recommend-link" @tap="goToCourseList">浏览全部 →</text>
          </view>
          <view v-if="recommendLoading" class="state-box">加载中...</view>
          <view v-else-if="favoriteChapters.length === 0" class="state-box">
            <text>暂无可推荐的课程</text>
          </view>
          <view v-else class="recommend-list">
            <view
              v-for="course in favoriteChapters"
              :key="course.id"
              class="recommend-item favorite-course-item"
              @tap="goToFavoriteChapter(course)"
            >
              <view class="recommend-cover favorite-course-cover" :style="{ background: getCoverColor(course.courseId || course.id) }">
                <image
                  v-if="course.coverUrl || course.imageUrl"
                  :src="course.coverUrl || course.imageUrl"
                  mode="aspectFill"
                  class="recommend-cover-img"
                />
                <text v-else class="course-emoji">♥</text>
              </view>
              <view class="recommend-info">
                <view class="recommend-name">{{ course.courseName || '课程名称' }}</view>
                <text class="recommend-meta favorite-chapter-meta">
                  {{ course.teacherName || course.ownerName || '暂无教师' }} · {{ course.chapterCount || 0 }}章 · {{ course.examCount ? course.examCount + '场考试' : '无考试' }}
                </text>
                <text class="favorite-chapter-number">第 {{ course.chapterNumber || '?' }} 章</text>
                <view class="recommend-footer">
                  <view class="recommend-footer-right">
                    <view
                      class="add-btn"
                      @tap.stop="addToMyCourses(course)"
                    >
                      <text>{{ enrollingId === course.id ? '处理中...' : '添加' }}</text>
                    </view>
                  </view>
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
import { getMyCourses, getCampusCoursesPage, enrollCourse } from '@/api/campusCourse.js'

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
      activeCourseTab: 'inProgress',
      myCourses: [],
    recommendCourses: [],
    favoriteChapters: [],
      loading: false,
      recommendLoading: false,
      errorMessage: '',
      enrollingId: null
    }
  },
  computed: {
    filteredMyCourses() {
      let courses = this.myCourses.filter(c => this.activeCourseTab === 'completed'
        ? (c.progressPercent || 0) >= 100
        : (c.progressPercent || 0) < 100)
      if (!this.searchKeyword) return courses
      const kw = this.searchKeyword.toLowerCase()
      return courses.filter(c =>
        (c.name || '').toLowerCase().includes(kw) ||
        (c.bookTitle || '').toLowerCase().includes(kw) ||
        (c.ownerName || '').toLowerCase().includes(kw) ||
        (c.teacherName || '').toLowerCase().includes(kw)
      )
    }
  },
  onLoad() {
    this.loadMyCourses()
  },
  onShow() {
    this.loadMyCourses(false)
    this.favoriteChapters = uni.getStorageSync('chapter-favorites') || []
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
        const response = await getCampusCoursesPage(1, 8)
        const allCourses = response?.data?.list || []
        const myIds = new Set(this.myCourses.map(c => c.id))
        this.recommendCourses = allCourses.filter(c => !myIds.has(c.id))
      } catch (error) {
        this.recommendCourses = []
      } finally {
        this.recommendLoading = false
      }
    },
    selectCourseStatus(status) {
      this.activeCourseTab = status
      this.loadMyCourses(false)
    },
    async addToMyCourses(course) {
      if (this.enrollingId) return
      this.enrollingId = course.id
      try {
        await enrollCourse(course.id)
        this.recommendCourses = this.recommendCourses.filter(c => c.id !== course.id)
        await this.loadMyCourses(false)
        uni.showToast({ title: '已加入我的课程', icon: 'success' })
      } catch (error) {
        uni.showToast({ title: error?.msg || error?.message || '添加失败', icon: 'none' })
      } finally {
        this.enrollingId = null
      }
    },
    goToDetail(course) {
      uni.navigateTo({
        url: `/subpackage_learning/campusCourseDetail/campusCourseDetail?courseId=${encodeURIComponent(course.id)}`
      })
    },
    goToFavoriteChapter(chapter) {
      uni.navigateTo({
        url: `/subpackage_learning/chapterLearn/chapterLearn?courseId=${encodeURIComponent(chapter.courseId)}&chapterId=${encodeURIComponent(chapter.id)}`
      })
    },
    goToCourseList() {
      uni.navigateTo({ url: '/subpackage_learning/campusCourseList/campusCourseList' })
    },
    switchToRecommend() {
      this.activePageTab = 'recommend'
      this.favoriteChapters = uni.getStorageSync('chapter-favorites') || []
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
  background: rgba(0, 0, 0, 0.04);
  border: 1px solid rgba(0, 0, 0, 0.06);
  border-radius: 18rpx;
  padding: 18rpx 28rpx;
}

.search-bar input {
  border: none;
  outline: none;
  flex: 1;
  font-size: 28rpx;
  color: #333;
  background: transparent;
}

.search-placeholder {
  color: #999;
}

.campus-entry {
  margin: 20rpx 24rpx 0;
  background: linear-gradient(135deg, #e8f2fd 0%, #f0f7ff 100%);
  border-radius: 20rpx;
  height: 176rpx;
  box-sizing: border-box;
  padding: 24rpx 28rpx;
  display: flex;
  align-items: center;
  gap: 18rpx;
  box-shadow: 0 4rpx 16rpx rgba(0, 0, 0, 0.06);
  transition: transform 0.15s;
}

.campus-entry:active {
  transform: scale(0.97);
}

.campus-entry-info {
  flex: 1;
}

.campus-entry-title {
  display: block;
  color: #333;
  font-size: 30rpx;
  font-weight: 700;
}

.campus-entry-desc {
  display: block;
  color: #999;
  font-size: 24rpx;
  margin-top: 6rpx;
}

.campus-entry-arrow {
  color: #ccc;
  font-size: 0;
  flex-shrink: 0;
  display: flex;
  align-items: center;
}

.campus-entry-arrow::before {
  content: '进入选课';
  padding: 16rpx 30rpx;
  border-radius: 32rpx;
  background: linear-gradient(135deg, #4a90d9, #5b9fe0);
  color: #fff;
  font-size: 24rpx;
  font-weight: 600;
  line-height: 1.2;
  white-space: nowrap;
  box-shadow: 0 6rpx 18rpx rgba(74, 144, 217, 0.28);
}

.banner {
  display: none;
  margin: 24rpx 24rpx 0;
  background: linear-gradient(135deg, #e8f2fd 0%, #f0f7ff 100%);
  border-radius: 24rpx;
  height: 176rpx;
  box-sizing: border-box;
  padding: 24rpx 28rpx;
  display: flex;
  justify-content: space-between;
  align-items: center;
  box-shadow: 0 4rpx 20rpx rgba(74, 144, 217, 0.08);
}

.banner-content {
  flex: 1;
  min-width: 0;
  display: grid;
  grid-template-columns: auto minmax(80rpx, 1fr) auto;
  align-items: center;
  column-gap: 12rpx;
}

.banner-title {
  font-size: 28rpx;
  font-weight: 700;
  color: #333;
  margin-bottom: 8rpx;
  grid-column: 1 / -1;
}

.banner-course {
  font-size: 26rpx;
  font-weight: 600;
  color: #333;
  margin: 0;
  display: block;
  grid-column: 1;
  grid-row: 2;
  max-width: 150rpx;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.progress-bar {
  width: auto;
  grid-column: 2;
  grid-row: 2;
  margin: 0;
  height: 8rpx;
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
  grid-column: 3;
  grid-row: 2;
}

.continue-btn {
  background: linear-gradient(135deg, #4a90d9, #5b9fe0);
  color: #fff;
  border-radius: 32rpx;
  padding: 16rpx 30rpx;
  font-size: 24rpx;
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
  min-height: 200rpx;
  height: auto;
  box-sizing: border-box;
  box-shadow: 0 4rpx 24rpx rgba(0, 0, 0, 0.06);
  transition: transform 0.15s;
}

.course-card:active {
  transform: scale(0.98);
}

.course-cover {
  width: 160rpx;
  height: 200rpx;
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
  min-width: 0;
  width: 0;
  padding: 20rpx 20rpx;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}

.course-name {
  font-size: 30rpx;
  font-weight: 700;
  color: #333;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  max-width: 100%;
}

.teacher {
  font-size: 24rpx;
  color: #999;
  margin-top: 8rpx;
  display: block;
}

.page-tabs .page-tab:last-child {
  font-size: 0;
}

.page-tabs .page-tab:last-child::after {
  content: '收藏内容';
  font-size: 28rpx;
}

.course-meta-line {
  display: inline;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.course-card .course-info {
  display: block;
}

.course-card .course-name {
  display: block;
}

.course-card .mini-progress {
  margin-top: 14rpx;
}

.course-level-line {
  display: none;
}

.course-level-inline {
  display: inline;
  margin-left: 8rpx;
  color: #999;
  font-size: 24rpx;
  white-space: nowrap;
}

.course-meta {
  display: flex;
  gap: 20rpx;
  font-size: 22rpx;
  color: #666;
  margin: 8rpx 0;
  white-space: nowrap;
  overflow: hidden;
}

.mini-progress {
  display: flex;
  align-items: center;
  gap: 14rpx;
  margin-top: 8rpx;
}

.mini-progress-bar {
  flex: 1;
  min-width: 0;
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
  height: 200rpx;
  box-sizing: border-box;
  padding: 20rpx;
  display: flex;
  gap: 22rpx;
  box-shadow: 0 4rpx 24rpx rgba(0, 0, 0, 0.06);
}

.recommend-cover {
  width: 160rpx;
  height: 160rpx;
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
  min-width: 0;
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
  margin: 6rpx 0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.favorite-chapter-meta {
  display: none;
}

.favorite-chapter-number {
  display: block;
  color: #999;
  font-size: 24rpx;
  white-space: nowrap;
}

.favorite-course-item {
  height: 200rpx;
  padding: 0;
  overflow: hidden;
}

.favorite-course-cover {
  width: 160rpx;
  height: 200rpx;
  border-radius: 20rpx 0 0 20rpx;
}

.favorite-course-item .recommend-info {
  padding: 20rpx;
  justify-content: center;
}

.favorite-course-item .recommend-name {
  font-size: 30rpx;
  font-weight: 700;
  color: #333;
}

.favorite-course-item .favorite-chapter-number {
  margin-top: 10rpx;
  line-height: 1.3;
}


.recommend-footer {
  display: flex;
  justify-content: flex-end;
  align-items: center;
  font-size: 22rpx;
}

.recommend-footer {
  display: none;
}

.students {
  color: #999;
}

.recommend-footer-right {
  display: flex;
  align-items: center;
  gap: 14rpx;
}

.level {
  color: #4a90d9;
  background: #e8f2fd;
  padding: 6rpx 16rpx;
  border-radius: 8rpx;
}

.add-btn {
  background: linear-gradient(135deg, #4a90d9, #5b9fe0);
  color: #fff;
  padding: 10rpx 24rpx;
  border-radius: 26rpx;
  font-size: 22rpx;
  font-weight: 600;
  white-space: nowrap;
  transition: all 0.2s;
}

.add-btn:active {
  transform: scale(0.92);
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
