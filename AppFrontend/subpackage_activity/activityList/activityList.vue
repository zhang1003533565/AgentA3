<template>
  <view class="activity-container">
    <nav-bar title="校园活动" :showBack="true" />

    <!-- 搜索框独占一行 -->
    <view class="search-bar">
      <view class="search-input">
        <input 
          type="text" 
          v-model="searchKeyword" 
          placeholder="搜索活动名称"
          placeholder-class="search-placeholder"
          @confirm="handleSearch"
        />
        <view class="search-action" @click.stop="handleSearch">
          <image class="search-action-icon" src="/static/icons/line/search.svg" mode="aspectFit" />
        </view>
      </view>
    </view>

    <!-- 分类 Tab + 我的活动入口 -->
    <view class="nav-secondary-wrap">
      <view class="category-container">
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
      
      <view class="divider-line"></view>
      
      <view class="btn-my-activity-wrap" @click="goToMyActivity">
        <image class="my-activity-icon" src="/static/icons/line/calendar.svg" mode="aspectFit" />
        <text class="my-activity-text">我的活动</text>
      </view>
    </view>

    <!-- 活动列表 -->
    <scroll-view 
      class="activity-list" 
      scroll-y 
      @scrolltolower="loadMore"
      refresher-enabled
      :refresher-triggered="isRefreshing"
      @refresherrefresh="onRefresh"
    >
      <view 
        v-for="(item, index) in activityList" 
        :key="item.id || index"
        class="activity-item"
        @click="goToDetail(item.id)"
      >
        <view class="activity-cover-wrap">
          <image class="activity-cover" :src="item.coverImage || defaultCover" mode="aspectFill" />
          <view class="activity-status-pill" :class="getStatusClass(item.status)">{{ getStatusText(item.status) }}</view>
        </view>
        <view class="activity-body">
          <view class="activity-title">{{ item.title }}</view>
          <view class="activity-meta">
            <text class="meta-item">
              <text class="iconfont icon-time meta-icon"></text>
              {{ formatTime(item.startTime) }}
            </text>
            <text class="meta-item">
              <text class="iconfont icon-location meta-icon"></text>
              {{ item.location }}
            </text>
          </view>
          <view class="activity-tag-row">
            <view class="activity-people-wrap">
              <text class="activity-people-text">{{ item.currentPeople }}/{{ item.maxPeople }} 人</text>
              <view class="activity-progress-bar">
                <view 
                  class="activity-progress-fill" 
                  :style="{ width: progressPercent(item) }"
                ></view>
              </view>
            </view>
          </view>
        </view>
      </view>

      <!-- 加载更多 -->
      <view class="load-more">
        <text v-if="loading">加载中...</text>
        <text v-else-if="noMore">没有更多了</text>
        <text v-else-if="activityList.length === 0">暂无活动</text>
      </view>
    </scroll-view>
  </view>
</template>

<script>
import NavBar from '@/components/nav-bar/nav-bar.vue'
import { getActivityList, searchActivities, filterActivities } from '@/api/activity.js'
import { getCategoryList } from '@/api/category.js'
export default {
  components: { NavBar },
  data() {
    return {
      searchKeyword: '',
      currentCategory: 0,
      defaultCover: 'https://picsum.photos/seed/campus/800/450',
      categories: [
        { id: 0, name: '全部' }
      ],
      activityList: [],
      page: 1,
      pageSize: 10,
      loading: false,
      noMore: false,
      isRefreshing: false
    }
  },
  async onLoad() {
    await this.loadCategories()
    this.loadActivityList()
  },
  methods: {
    async loadCategories() {
      try {
        const res = await getCategoryList()
        const list = (res && res.data) ? res.data : []

        const categories = [{ id: 0, name: '全部' }]
        for (const item of list) {
          if (!item) continue
          if (item.status !== undefined && item.status !== null && Number(item.status) !== 1) continue
          categories.push({
            id: item.id,
            name: item.name || item.categoryName || ''
          })
        }

        this.categories = categories
        if (!this.categories.some((c) => c.id === this.currentCategory)) {
          this.currentCategory = 0
        }
      } catch (e) {
        // request.js 会统一 toast，这里不重复提示
      }
    },
    // 加载活动列表
    async loadActivityList() {
      if (this.loading || this.noMore) return
      
      this.loading = true

      try {
        let res
        const page = this.page
        const size = this.pageSize

        if (this.searchKeyword && this.searchKeyword.trim()) {
          res = await searchActivities({ page, size, keyword: this.searchKeyword.trim() })
        } else if (this.currentCategory && this.currentCategory !== 0) {
          res = await filterActivities({ page, size, categoryId: this.currentCategory, status: 'PUBLISHED' })
        } else {
          res = await getActivityList({ page, size, status: 'PUBLISHED' })
        }

        const records = (res && res.data && res.data.records) ? res.data.records : []
        const total = (res && res.data && typeof res.data.total === 'number') ? res.data.total : (res && res.data && res.data.total) || 0

        if (this.page === 1) {
          this.activityList = records
        } else {
          this.activityList = [...this.activityList, ...records]
        }

        const loadedCount = this.activityList.length
        this.noMore = loadedCount >= (total || 0) || records.length < this.pageSize
      } finally {
        this.loading = false
        this.isRefreshing = false
      }
    },
    
    // 搜索
    handleSearch() {
      this.page = 1
      this.noMore = false
      this.loadActivityList()
    },
    
    // 选择分类
    selectCategory(categoryId) {
      this.currentCategory = categoryId
      this.page = 1
      this.noMore = false
      this.loadActivityList()
    },
    
    // 加载更多
    loadMore() {
      if (!this.loading && !this.noMore) {
        this.page++
        this.loadActivityList()
      }
    },
    
    // 下拉刷新
    onRefresh() {
      this.isRefreshing = true
      this.page = 1
      this.noMore = false
      this.loadActivityList()
    },
    
    // 跳转到详情
    goToDetail(id) {
      uni.navigateTo({
        url: `/subpackage_activity/activityDetail/activityDetail?id=${id}`
      })
    },
    // 进入「我的活动」（待参加/报名记录/已结束）
    goToMyActivity() {
      uni.navigateTo({ url: '/subpackage_activity/myActivity/myActivity' })
    },
    
    // 格式化时间
    formatTime(time) {
      if (!time) return ''
      return time.substring(5, 16).replace(' ', ' ')
    },
    
    // 获取状态样式
    getStatusClass(status) {
      const map = {
        'DRAFT': 'status-draft',
        'PUBLISHED': 'status-ongoing',
        'COMPLETED': 'status-ended'
      }
      return map[status] || 'status-default'
    },
    
    // 获取状态文本
    getStatusText(status) {
      const map = {
        'DRAFT': '草稿',
        'PUBLISHED': '进行中',
        'COMPLETED': '已结束'
      }
      return map[status] || '未知'
    },
    // 报名进度百分比（已报名/总人数），用于进度条宽度
    progressPercent(item) {
      const max = item.maxPeople || 1
      const pct = Math.min(100, Math.round((item.currentPeople / max) * 100))
      return pct + '%'
    }
  }
}
</script>

<style lang="scss">
.activity-container {
  min-height: 100vh;
  background-color: #F7F7F9;
  padding-bottom: 120rpx;
}

.search-bar {
  display: flex;
  align-items: center;
  gap: 24rpx;
  padding: 16rpx $spacing-lg-rpx $spacing-block;
  background-color: #FFFFFF;
}
.search-input {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  height: 72rpx;
  background-color: #F7F7F9;
  border-radius: 16rpx;
  padding: 0 $spacing-base-rpx;
  border: none;
}
.search-action {
  flex-shrink: 0;
  width: 64rpx;
  height: 64rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 12rpx;
  margin-left: 8rpx;
}
.search-action-icon {
  width: 32rpx;
  height: 32rpx;
}
.search-input input {
  flex: 1;
  font-size: 28rpx;   /* 14px 三级 */
  color: #4A4A4A;
  font-weight: 400;
}
.search-placeholder {
  color: #8E8E93;
}

/* 分类 Tab + 我的活动：文字样式，去除胶囊 */
.nav-secondary-wrap {
  display: flex;
  align-items: center;
  background-color: #FFFFFF;
  padding: 0 0 0 $spacing-lg-rpx;
  margin-bottom: $spacing-block;
  border-bottom: 1px solid #F2F2F2;
}
.category-container {
  flex: 1;
  min-width: 0;
}
.category-scroll {
  white-space: nowrap;
}
.category-list {
  display: flex;
  align-items: center;
  min-height: 80rpx;
  gap: 40rpx; /* 增加呼吸感 */
}
.category-item {
  position: relative;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 20rpx 0;
  flex-shrink: 0;
}
.category-text {
  font-size: 26rpx; /* 稍微缩小 */
  font-weight: 400;
  color: #8E8E93;
  transition: all 0.2s ease;
}
.category-item.active .category-text {
  font-weight: 600;
  color: #1D1D1F;
}
.active-line {
  position: absolute;
  bottom: 0;
  left: 50%;
  transform: translateX(-50%);
  width: 32rpx;
  height: 4rpx;
  background-color: $color-primary;
  border-radius: 2rpx;
}

/* 独立我的活动入口 */
.divider-line {
  width: 1px;
  height: 40rpx;
  background-color: #EEEEEE;
  margin: 0 12rpx;
}
.btn-my-activity-wrap {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  padding: 0 20rpx;
  gap: 4rpx;
  flex-shrink: 0;
}
.my-activity-icon {
  width: 32rpx;
  height: 32rpx;
}
.my-activity-text {
  font-size: 20rpx; /* 小文字 */
  color: #8E8E93;
  font-weight: 500;
}

/* 活动列表：通栏、分隔线清晰，保证可滚动、内容不裁切 */
.activity-list {
  height: calc(100vh - 380rpx);
  padding: 0;
  padding-top: 24rpx;
  padding-bottom: 48rpx;
  background-color: #FFFFFF;
  box-sizing: border-box;
}

.activity-item {
  padding: 0 0 32rpx;
  margin-bottom: 0;
  border-bottom: 1px solid #EEEEEE;
}
.activity-item:last-child {
  border-bottom: none;
}

/* 通栏列表项：4px 步进间距，图片与标题 12px，详情与标题 4px */
.activity-cover-wrap {
  position: relative;
  width: 100%;
  height: 422rpx;
  border-radius: 8rpx;
  margin-bottom: 24rpx;   /* 12px 图与标题间距 */
  background-color: #F7F7F9;
  overflow: hidden;
}
.activity-cover {
  display: block;
  width: 100%;
  height: 422rpx;
}
/* Pill 状态标签：悬浮图片左上角，极浅底+深色字 */
.activity-status-pill {
  position: absolute;
  left: 24rpx;
  top: 24rpx;
  padding: 8rpx 20rpx;
  border-radius: 999rpx;
  font-size: 22rpx;
  font-weight: 500;
  background-color: rgba(255, 255, 255, 0.92);
  color: #1D1D1F;
}
.activity-status-pill.status-signup {
  background-color: rgba(92, 122, 153, 0.12);
  color: #4A6278;
}
.activity-status-pill.status-ongoing {
  background-color: rgba(107, 155, 122, 0.12);
  color: #4A6B57;
}
.activity-status-pill.status-ended,
.activity-status-pill.status-end,
.activity-status-pill.status-draft,
.activity-status-pill.status-pending,
.activity-status-pill.status-default {
  background-color: rgba(142, 142, 147, 0.12);
  color: #5C5C60;
}
.activity-status-pill.status-cancelled {
  background-color: rgba(166, 123, 123, 0.15);
  color: #A67B7B;
}

.activity-body {
  padding: 0 $spacing-lg-rpx;
}

.activity-title {
  font-size: 32rpx;
  font-weight: 600;
  color: #1D1D1F;
  line-height: 1.4;
  margin-bottom: 8rpx;   /* 4px 标题与详情间距 */
}

.activity-meta {
  display: flex;
  flex-direction: column;
  margin-top: 0;
  gap: 4rpx;            /* 4px 步进 */
}
.meta-item {
  font-size: 24rpx;
  font-weight: 400;
  color: #4A4A4A;
}
.meta-icon {
  margin-right: 8rpx;
  font-size: 22rpx;
  opacity: 0.9;
}

.activity-tag-row {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  margin-top: 8rpx;
}
.activity-people-wrap {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  min-width: 120rpx;
}
.activity-people-text {
  font-size: 22rpx; /* 11px */
  color: #8E8E93;
  margin-bottom: 6rpx;
}
.activity-progress-bar {
  width: 100%;
  height: 6rpx;
  background-color: #EEEEEE;
  border-radius: 3rpx;
  overflow: hidden;
}
.activity-progress-fill {
  height: 100%;
  background-color: $color-primary;
  border-radius: 3rpx;
  transition: width 0.2s ease;
}

.activity-list .load-more {
  text-align: center;
  padding: $spacing-lg-rpx;
  color: $color-text-secondary;
  font-size: $font-size-body-rpx;
}
</style>
