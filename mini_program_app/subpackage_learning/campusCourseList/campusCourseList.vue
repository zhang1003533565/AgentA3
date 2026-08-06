<template>
  <view class="page">
    <nav-bar title="校园课程" :showBack="true" fixed placeholder />

    <!-- 搜索栏 -->
    <view class="header">
      <view class="search-bar">
        <text class="search-icon">🔍</text>
        <input type="text" placeholder="搜索课程名称、老师、关键词" v-model="searchKeyword" />
      </view>
    </view>

    <!-- 课程分类 -->
    <view class="category-section">
      <view class="section-header">
        <view class="section-title">课程分类</view>
      </view>
      <view class="category-grid">
        <view
          class="category-item"
          v-for="category in categories"
          :key="category.id"
          :class="{ active: selectedCategory === category.id }"
          @tap="selectedCategory = category.id"
        >
          <view class="category-icon" :style="{ background: category.bg }">
            <text>{{ category.icon }}</text>
          </view>
          <text class="category-name">{{ category.name }}</text>
        </view>
      </view>
    </view>

    <!-- 筛选区 -->
    <view class="filter-section">
      <view class="filter-chips">
        <view
          class="chip"
          :class="{ active: selectedLevel === '' }"
          @tap="selectedLevel = ''"
        >全部</view>
        <view
          class="chip"
          v-for="level in levels"
          :key="level"
          :class="{ active: selectedLevel === level }"
          @tap="selectedLevel = level"
        >{{ level }}</view>
      </view>
      <view class="sort-bar">
        <text class="sort-label">排序：</text>
        <view class="sort-options">
          <text
            class="sort-option"
            :class="{ active: sortBy === 'hot' }"
            @tap="sortBy = 'hot'"
          >热门</text>
          <text
            class="sort-option"
            :class="{ active: sortBy === 'new' }"
            @tap="sortBy = 'new'"
          >最新</text>
          <text
            class="sort-option"
            :class="{ active: sortBy === 'name' }"
            @tap="sortBy = 'name'"
          >名称</text>
        </view>
      </view>
    </view>

    <!-- 课程列表 -->
    <view class="course-section">
      <view class="section-header">
        <view class="section-title">全部课程</view>
        <text class="count">共 {{ filteredCourses.length }} 门</text>
      </view>

      <!-- 加载/错误状态 -->
      <view v-if="loading" class="state-box">加载中...</view>
      <view v-else-if="errorMessage" class="state-box">
        <text>{{ errorMessage }}</text>
        <view class="retry-btn" @tap="loadCourses">重试</view>
      </view>

      <!-- 课程卡片 -->
      <view v-else class="course-grid">
        <view
          v-for="course in filteredCourses"
          :key="course.id"
          class="course-item"
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
            <view class="course-badge" v-if="isHot(course)">🔥 热门</view>
          </view>
          <view class="course-info">
            <view class="course-title">{{ course.name }}</view>
            <text class="teacher">{{ course.ownerName || course.bookTitle || '课程管理员' }}</text>
            <view class="course-tags">
              <text class="tag level-tag">{{ course.level || '初级' }}</text>
              <text class="tag duration-tag" v-if="course.chapterCount">{{ course.chapterCount }}章节</text>
            </view>
            <view class="course-footer">
              <text class="students" v-if="course.examCount">{{ course.examCount }}场考试</text>
              <view class="footer-right">
                <view
                  class="add-btn"
                  :class="{ added: isEnrolled(course.id) }"
                  @tap.stop="toggleEnroll(course)"
                >
                  <text v-if="enrollingId === course.id">处理中...</text>
                  <text v-else>{{ isEnrolled(course.id) ? '✓ 已加入' : '+ 加入' }}</text>
                </view>
              </view>
            </view>
          </view>
        </view>
        <view v-if="filteredCourses.length === 0 && !loading" class="state-box">
          暂无课程
        </view>
      </view>
    </view>
  </view>
</template>

<script>
import NavBar from '@/components/nav-bar/nav-bar.vue'
import { getCampusCourses, getMyCourses, enrollCourse, unenrollCourse } from '@/api/campusCourse.js'

const COVER_COLORS = [
  'linear-gradient(135deg, #667eea 0%, #764ba2 100%)',
  'linear-gradient(135deg, #4facfe 0%, #00f2fe 100%)',
  'linear-gradient(135deg, #43e97b 0%, #38f9d7 100%)',
  'linear-gradient(135deg, #fa709a 0%, #fee140 100%)',
  'linear-gradient(135deg, #a18cd1 0%, #fbc2eb 100%)',
  'linear-gradient(135deg, #f093fb 0%, #f5576c 100%)',
  'linear-gradient(135deg, #4a90d9 0%, #6ba3e8 100%)',
  'linear-gradient(135deg, #ffecd2 0%, #fcb69f 100%)'
]
const COVER_EMOJIS = ['📚', '💻', '🎨', '🎯', '🗣️', '📊', '⚛️', '🔬', '📝', '🌐']

export default {
  components: { NavBar },
  data() {
    return {
      searchKeyword: '',
      selectedCategory: 0,
      selectedLevel: '',
      sortBy: 'hot',
      courses: [],
      myCourseIds: [],
      loading: false,
      errorMessage: '',
      enrollingId: null,
      categories: [
        { id: 0, name: '全部', icon: '📚', bg: '#e8f2fd' },
        { id: 1, name: '编程', icon: '💻', bg: '#e3f2fd' },
        { id: 2, name: '设计', icon: '🎨', bg: '#fce4ec' },
        { id: 3, name: '产品', icon: '🎯', bg: '#fff3e0' },
        { id: 4, name: '外语', icon: '🗣️', bg: '#f3e5f5' },
        { id: 5, name: '数据', icon: '📊', bg: '#e8f5e9' }
      ],
      levels: ['初级', '中级', '高级']
    }
  },
  computed: {
    filteredCourses() {
      let list = this.courses.slice()
      if (this.searchKeyword) {
        const kw = this.searchKeyword.toLowerCase()
        list = list.filter(c =>
          (c.name || '').toLowerCase().includes(kw) ||
          (c.ownerName || '').toLowerCase().includes(kw) ||
          (c.bookTitle || '').toLowerCase().includes(kw)
        )
      }
      if (this.selectedLevel) {
        list = list.filter(c => (c.level || '初级') === this.selectedLevel)
      }
      if (this.sortBy === 'name') {
        list.sort((a, b) => (a.name || '').localeCompare(b.name || ''))
      }
      return list
    }
  },
  onLoad() {
    this.loadCourses()
  },
  onShow() {
    this.loadMyCourseIds()
  },
  methods: {
    async loadCourses() {
      this.loading = true
      this.errorMessage = ''
      try {
        const response = await getCampusCourses()
        this.courses = response?.data || []
        await this.loadMyCourseIds()
      } catch (error) {
        this.courses = []
        this.errorMessage = error?.msg || error?.message || '加载课程失败'
      } finally {
        this.loading = false
      }
    },
    async loadMyCourseIds() {
      try {
        const response = await getMyCourses()
        const list = response?.data || []
        this.myCourseIds = list.map(c => c.id)
      } catch (e) {
        // 静默失败
      }
    },
    isEnrolled(courseId) {
      return this.myCourseIds.includes(courseId)
    },
    async toggleEnroll(course) {
      if (this.enrollingId) return
      this.enrollingId = course.id
      try {
        if (this.isEnrolled(course.id)) {
          await unenrollCourse(course.id)
          this.myCourseIds = this.myCourseIds.filter(id => id !== course.id)
          uni.showToast({ title: '已移出', icon: 'none' })
        } else {
          await enrollCourse(course.id)
          this.myCourseIds.push(course.id)
          uni.showToast({ title: '已加入', icon: 'success' })
        }
      } catch (error) {
        uni.showToast({ title: error?.msg || error?.message || '操作失败', icon: 'none' })
      } finally {
        this.enrollingId = null
      }
    },
    goToDetail(course) {
      uni.navigateTo({
        url: `/subpackage_learning/campusCourseDetail/campusCourseDetail?courseId=${encodeURIComponent(course.id)}`
      })
    },
    getCoverColor(id) {
      return COVER_COLORS[String(id).charCodeAt(0) % COVER_COLORS.length]
    },
    getCourseEmoji(id) {
      return COVER_EMOJIS[String(id).charCodeAt(0) % COVER_EMOJIS.length]
    },
    isHot(course) {
      return (course.progressPercent || 0) > 80 || (course.examCount || 0) > 3
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
  background: linear-gradient(180deg, #4a90d9 0%, #6ba3e8 100%);
  padding: 30rpx 24rpx 24rpx;
}

.search-bar {
  display: flex;
  align-items: center;
  background: #fff;
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
  color: #333;
}

.category-section {
  padding: 28rpx 24rpx 0;
}

.section-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 24rpx;
}

.section-title {
  font-size: 32rpx;
  font-weight: 700;
  color: #333;
}

.count {
  font-size: 24rpx;
  color: #999;
}

.category-grid {
  display: flex;
  justify-content: space-between;
  gap: 8rpx;
}

.category-item {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 12rpx;
  padding: 12rpx 4rpx;
  border-radius: 16rpx;
  transition: background 0.2s;
  min-width: 100rpx;
}

.category-item.active {
  background: #e8f2fd;
}

.category-icon {
  width: 80rpx;
  height: 80rpx;
  border-radius: 20rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 36rpx;
}

.category-name {
  font-size: 22rpx;
  color: #666;
}

.category-item.active .category-name {
  color: #4a90d9;
  font-weight: 600;
}

.filter-section {
  padding: 28rpx 24rpx;
}

.filter-chips {
  display: flex;
  gap: 16rpx;
  margin-bottom: 24rpx;
  flex-wrap: wrap;
}

.chip {
  padding: 12rpx 28rpx;
  background: #fff;
  border-radius: 40rpx;
  font-size: 26rpx;
  color: #666;
  transition: all 0.2s;
  border: 2rpx solid #eef1f5;
}

.chip.active {
  background: #4a90d9;
  color: #fff;
  border-color: #4a90d9;
}

.sort-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.sort-label {
  font-size: 26rpx;
  color: #666;
}

.sort-options {
  display: flex;
  gap: 36rpx;
}

.sort-option {
  font-size: 26rpx;
  color: #999;
  position: relative;
  padding: 6rpx 0;
}

.sort-option.active {
  color: #4a90d9;
  font-weight: 600;
}

.sort-option.active::after {
  content: '';
  position: absolute;
  bottom: -2rpx;
  left: 0;
  right: 0;
  height: 4rpx;
  background: #4a90d9;
  border-radius: 2rpx;
}

.course-section {
  padding: 0 24rpx;
}

.course-grid {
  display: flex;
  flex-direction: column;
  gap: 20rpx;
}

.course-item {
  background: #fff;
  border-radius: 20rpx;
  overflow: hidden;
  display: flex;
  box-shadow: 0 4rpx 24rpx rgba(0, 0, 0, 0.06);
  transition: transform 0.15s;
}

.course-item:active {
  transform: scale(0.98);
}

.course-cover {
  width: 180rpx;
  min-height: 200rpx;
  display: flex;
  flex-direction: column;
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
  font-size: 60rpx;
}

.course-badge {
  position: absolute;
  top: 14rpx;
  left: 14rpx;
  background: rgba(255, 255, 255, 0.92);
  color: #ff6b6b;
  font-size: 20rpx;
  padding: 4rpx 12rpx;
  border-radius: 8rpx;
  font-weight: 600;
}

.course-info {
  flex: 1;
  padding: 22rpx;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
}

.course-title {
  font-size: 30rpx;
  font-weight: 700;
  color: #333;
  line-height: 1.4;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
}

.teacher {
  font-size: 24rpx;
  color: #999;
  margin-top: 10rpx;
}

.course-tags {
  display: flex;
  gap: 12rpx;
  margin-top: 14rpx;
}

.tag {
  font-size: 22rpx;
  padding: 6rpx 14rpx;
  border-radius: 8rpx;
}

.level-tag {
  background: #e8f2fd;
  color: #4a90d9;
}

.duration-tag {
  background: #f5f5f5;
  color: #666;
}

.course-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-top: 14rpx;
}

.students {
  font-size: 22rpx;
  color: #999;
}

.footer-right {
  display: flex;
  align-items: center;
  gap: 16rpx;
}

.add-btn {
  background: linear-gradient(135deg, #4a90d9, #5b9fe0);
  color: #fff;
  border: none;
  padding: 10rpx 28rpx;
  border-radius: 28rpx;
  font-size: 24rpx;
  font-weight: 600;
  transition: all 0.2s;
  white-space: nowrap;
}

.add-btn:active {
  transform: scale(0.92);
}

.add-btn.added {
  background: #e8f2fd;
  color: #4a90d9;
}

.state-box {
  padding: 100rpx 40rpx;
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
</style>
