<template>
  <view class="community-container">
    <view class="page-fixed-header">
      <nav-bar title="校园活动" :showBack="true" />

      <!-- 搜索框 -->
      <view class="search-bar">
        <view class="search-input">
          <input 
            type="text" 
            v-model="searchKeyword" 
            placeholder="搜索校园活动"
            placeholder-class="search-placeholder"
            @confirm="handleSearch"
          />
          <view class="search-action" @click.stop="handleSearch">
            <image class="search-action-icon" src="/static/icons/line/search.svg" mode="aspectFit" />
          </view>
        </view>
      </view>

      <!-- 分类 Tab -->
      <view class="category-wrap">
        <scroll-view class="category-scroll" scroll-x :show-scrollbar="false">
          <view class="category-list">
            <view 
              v-for="(item, index) in categories" 
              :key="index"
              class="category-item"
              :class="{ active: currentCategory === item.id }"
              @click="selectCategory(item.id)"
            >
              <text class="category-text">{{ item.name }}</text>
              <view class="active-line" v-if="currentCategory === item.id"></view>
            </view>
          </view>
        </scroll-view>
      </view>
    </view>

    <!-- 活动列表 - 按分类分组 -->
    <scroll-view 
      class="activity-list" 
      scroll-y 
      @scrolltolower="loadMore"
      refresher-enabled
      :refresher-triggered="isRefreshing"
      @refresherrefresh="onRefresh"
    >
      <!-- 分组显示 -->
      <view v-for="(group, groupIndex) in groupedActivities" :key="groupIndex" class="activity-group">
        <!-- 分类标题 -->
        <view class="group-header">
          <view class="group-title-bar"></view>
          <text class="group-title">{{ group.categoryName }}</text>
          <text class="group-count">{{ group.activities.length }}个活动</text>
        </view>
        
        <!-- 该分类下的活动 -->
        <view class="group-content">
          <view 
            v-for="(item, index) in group.activities" 
            :key="item.id || index"
            class="activity-card"
            @click="goToDetail(item)"
          >
            <!-- 活动封面 -->
            <image class="activity-cover" :src="item.cover || defaultCover" mode="aspectFill" />
            
            <!-- 活动信息 -->
            <view class="activity-info">
              <view class="activity-header">
                <text class="activity-title">{{ item.title }}</text>
                <view class="activity-tag" :class="item.status">{{ getStatusText(item.status) }}</view>
              </view>
              
              <view class="activity-meta">
                <view class="meta-item">
                  <image class="meta-icon" src="/static/icons/line/calendar.svg" mode="aspectFit" />
                  <text class="meta-text">{{ formatDate(item.startTime) }}</text>
                </view>
                <view class="meta-item">
                  <image class="meta-icon" src="/static/icons/line/map.svg" mode="aspectFit" />
                  <text class="meta-text">{{ item.location }}</text>
                </view>
                <view class="meta-item">
                  <image class="meta-icon" src="/static/icons/line/user.svg" mode="aspectFit" />
                  <text class="meta-text">{{ item.currentPeople }}/{{ item.maxPeople }}人</text>
                </view>
              </view>
              
              <!-- 进度条 -->
              <view class="progress-wrap">
                <view class="progress-bar">
                  <view class="progress-fill" :style="{ width: progressPercent(item) }"></view>
                </view>
                <text class="progress-text">已报名 {{ item.currentPeople }} 人</text>
              </view>
              
              <!-- 主办方 -->
              <view class="organizer-wrap">
                <image class="organizer-avatar" :src="item.organizerAvatar || '/static/logo.png'" mode="aspectFill" />
                <text class="organizer-name">{{ item.organizer }}</text>
              </view>
            </view>
          </view>
        </view>
      </view>

      <!-- 加载状态 -->
      <view class="loading-more" v-if="loading || noMore || groupedActivities.length === 0">
        <text v-if="loading">加载中...</text>
        <text v-else-if="noMore">没有更多了</text>
        <text v-else-if="groupedActivities.length === 0">暂无活动</text>
      </view>
    </scroll-view>

  </view>
</template>

<script>
import NavBar from '@/components/nav-bar/nav-bar.vue'
import { getActivityList } from '@/api/activity.js'
import { getCategoryList } from '@/api/category.js'

const parseTime = (value) => (value ? new Date(value.replace(' ', 'T')) : null)

export default {
  components: { NavBar },
  data() {
    return {
      searchKeyword: '',
      currentCategory: 0,
      defaultCover: 'https://picsum.photos/seed/community/800/450',
      categories: [{ id: 0, name: '全部' }],
      activityList: [],
      page: 1,
      pageSize: 10,
      loading: false,
      noMore: false,
      isRefreshing: false
    }
  },
  computed: {
    // 按分类分组的活动
    groupedActivities() {
      if (this.currentCategory !== 0) {
        // 选中特定分类，只显示该分类
        const category = this.categories.find(c => c.id === this.currentCategory)
        const activities = this.activityList.filter(item => item.categoryId === this.currentCategory)
        return activities.length > 0 ? [{ categoryName: category.name, activities }] : []
      }
      
      // 显示全部，按分类分组
      const groups = []
      this.categories.forEach(category => {
        if (category.id === 0) return // 跳过"全部"
        const activities = this.activityList.filter(item => item.categoryId === category.id)
        if (activities.length > 0) {
          groups.push({
            categoryName: category.name,
            activities: activities
          })
        }
      })
      return groups
    }
  },
  onLoad() {
    this.loadCategories()
    this.loadActivityList(true)
  },
  methods: {
    async loadCategories() {
      try {
        const res = await getCategoryList()
        const records = res?.data || []
        this.categories = [{ id: 0, name: '全部' }, ...records.map((item) => ({
          id: item.id,
          name: item.name
        }))]
      } catch (error) {
        this.categories = [{ id: 0, name: '全部' }]
      }
    },

    async loadActivityList(refresh = false) {
      if (this.loading) return
      this.loading = true

      if (refresh) {
        this.page = 1
        this.noMore = false
      }

      try {
        const res = await getActivityList({
          page: this.page,
          size: this.pageSize,
          title: this.searchKeyword || undefined,
          categoryId: this.currentCategory || undefined,
          status: 'PUBLISHED'
        })
        const records = (res?.data?.records || []).map((item) => ({
          ...item,
          cover: item.coverImage || '',
          organizer: item.organizerName || '校园活动中心',
          status: this.getStatus(item)
        }))

        this.activityList = refresh ? records : [...this.activityList, ...records]
        this.noMore = this.activityList.length >= (res?.data?.total || 0)
      } catch (error) {
        if (refresh) {
          this.activityList = []
        }
      } finally {
        this.loading = false
        this.isRefreshing = false
      }
    },

    onRefresh() {
      this.isRefreshing = true
      this.loadActivityList(true)
    },

    loadMore() {
      if (this.noMore || this.loading) return
      this.page++
      this.loadActivityList()
    },

    handleSearch() {
      this.loadActivityList(true)
    },

    selectCategory(id) {
      this.currentCategory = id
      this.loadActivityList(true)
    },

    goToDetail(item) {
      uni.navigateTo({
        url: `/subpackage_community/communityDetail/communityDetail?id=${item.id}`
      })
    },

    formatDate(dateStr) {
      if (!dateStr) return ''
      const date = parseTime(dateStr)
      return `${date.getMonth() + 1}月${date.getDate()}日 ${date.getHours()}:${String(date.getMinutes()).padStart(2, '0')}`
    },

    getStatus(item) {
      const now = new Date()
      const startTime = parseTime(item.startTime)
      const endTime = parseTime(item.endTime)
      if (endTime && now >= endTime) return 'ended'
      if (startTime && now >= startTime) return 'ongoing'
      return 'signup'
    },

    getStatusText(status) {
      const map = {
        'signup': '报名中',
        'ongoing': '进行中',
        'ended': '已结束'
      }
      return map[status] || '未知'
    },

    progressPercent(item) {
      const max = item.maxPeople || 1
      const pct = Math.min(100, Math.round((item.currentPeople / max) * 100))
      return pct + '%'
    }
  }
}
</script>

<style lang="scss">
.community-container {
  min-height: 100vh;
  background-color: #F7F7F9;
  overflow-x: hidden;
}

.search-bar {
  display: flex;
  align-items: center;
  gap: 24rpx;
  padding: 16rpx 32rpx 24rpx;
  background-color: #FFFFFF;
}

.page-fixed-header {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  z-index: 999;
}

.search-input {
  flex: 1;
  display: flex;
  align-items: center;
  height: 72rpx;
  background-color: #F7F7F9;
  border-radius: 16rpx;
  padding: 0 24rpx;
}

.search-input input {
  flex: 1;
  font-size: 28rpx;
  color: #333;
}

.search-placeholder {
  color: #999;
}

.search-action {
  width: 64rpx;
  height: 64rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.search-action-icon {
  width: 40rpx;
  height: 40rpx;
}

.category-wrap {
  background-color: #FFFFFF;
  padding-bottom: 24rpx;
}

.category-scroll {
  white-space: nowrap;
}

.category-list {
  display: flex;
  padding: 0 32rpx;
  gap: 56rpx;
  width: max-content;
}

.category-item {
  position: relative;
  padding: 12rpx 0;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.category-text {
  font-size: 28rpx;
  color: #666;
  text-align: center;
  line-height: 1.4;
  white-space: nowrap;
}

.category-item.active .category-text {
  color: #333;
  font-weight: 600;
}

.active-line {
  position: absolute;
  bottom: 0;
  left: 50%;
  transform: translateX(-50%);
  width: 40rpx;
  height: 4rpx;
  background-color: #007AFF;
  border-radius: 2rpx;
}

.activity-list {
  width: 100%;
  padding: 280rpx 24rpx 24rpx;
  box-sizing: border-box;
}

/* 分组样式 */
.activity-group {
  margin-bottom: 32rpx;
}

.group-header {
  display: flex;
  align-items: center;
  margin-bottom: 20rpx;
  padding: 0 8rpx;
}

.group-title-bar {
  width: 6rpx;
  height: 32rpx;
  background: linear-gradient(180deg, #FF6B6B, #FF8E53);
  border-radius: 3rpx;
  margin-right: 16rpx;
}

.group-title {
  font-size: 32rpx;
  font-weight: 600;
  color: #333;
  flex: 1;
}

.group-count {
  font-size: 24rpx;
  color: #999;
}

.group-content {
  display: flex;
  flex-direction: column;
  gap: 20rpx;
  width: 100%;
}

.activity-card {
  width: 100%;
  background-color: #FFFFFF;
  border-radius: 16rpx;
  margin-bottom: 24rpx;
  overflow: hidden;
  box-sizing: border-box;
}

.activity-cover {
  width: 100%;
  height: 320rpx;
}

.activity-info {
  width: 100%;
  padding: 24rpx;
  box-sizing: border-box;
}

.activity-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16rpx;
  gap: 16rpx;
}

.activity-title {
  font-size: 32rpx;
  font-weight: 600;
  color: #333;
  flex: 1;
  min-width: 0;
}

.activity-tag {
  padding: 8rpx 16rpx;
  border-radius: 8rpx;
  font-size: 24rpx;
  margin-left: 16rpx;
}

.activity-tag.signup {
  background-color: #E6F7FF;
  color: #007AFF;
}

.activity-tag.ongoing {
  background-color: #F6FFED;
  color: #52C41A;
}

.activity-tag.ended {
  background-color: #F5F5F5;
  color: #999;
}

.activity-tag.full {
  background-color: #FFF1F0;
  color: #FF4D4F;
}

.activity-meta {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
  margin-bottom: 20rpx;
}

.meta-item {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.meta-icon {
  width: 28rpx;
  height: 28rpx;
}

.meta-text {
  font-size: 26rpx;
  color: #666;
}

.progress-wrap {
  margin-bottom: 20rpx;
}

.progress-bar {
  height: 8rpx;
  background-color: #F0F0F0;
  border-radius: 4rpx;
  overflow: hidden;
  margin-bottom: 8rpx;
}

.progress-fill {
  height: 100%;
  background: linear-gradient(90deg, #007AFF, #00C6FF);
  border-radius: 4rpx;
  transition: width 0.3s ease;
}

.progress-text {
  font-size: 24rpx;
  color: #999;
}

.organizer-wrap {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding-top: 20rpx;
  border-top: 1rpx solid #F0F0F0;
}

.organizer-avatar {
  width: 48rpx;
  height: 48rpx;
  border-radius: 24rpx;
}

.organizer-name {
  font-size: 26rpx;
  color: #666;
}

.loading-more {
  text-align: center;
  padding: 40rpx 0;
  font-size: 26rpx;
  color: #999;
}

/* 发布按钮 - 新设计 */
.fab-btn {
  position: fixed;
  right: 28rpx;
  bottom: 120rpx;
  width: 112rpx;
  height: 112rpx;
  background: linear-gradient(135deg, #FF6B6B 0%, #FF8E53 100%);
  border-radius: 50%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  box-shadow: 
    0 8rpx 32rpx rgba(255, 107, 107, 0.4),
    0 4rpx 16rpx rgba(255, 107, 107, 0.2),
    inset 0 2rpx 4rpx rgba(255, 255, 255, 0.3);
  z-index: 1200;
  transition: all 0.3s ease;
}

.fab-btn:active {
  transform: scale(0.95);
  box-shadow: 
    0 4rpx 16rpx rgba(255, 107, 107, 0.3),
    inset 0 2rpx 4rpx rgba(255, 255, 255, 0.2);
}

/* 内圈装饰 */
.fab-btn::before {
  content: '';
  position: absolute;
  width: 96rpx;
  height: 96rpx;
  border: 2rpx solid rgba(255, 255, 255, 0.3);
  border-radius: 50%;
}

.fab-icon {
  width: 44rpx;
  height: 44rpx;
  margin-bottom: 6rpx;
  filter: drop-shadow(0 2rpx 4rpx rgba(0, 0, 0, 0.1));
}

.fab-text {
  font-size: 20rpx;
  color: #FFFFFF;
  font-weight: 500;
  text-shadow: 0 2rpx 4rpx rgba(0, 0, 0, 0.1);
}
</style>
