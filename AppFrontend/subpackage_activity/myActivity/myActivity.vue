<template>
  <view class="my-activity-container">
    <nav-bar title="我的活动" :showBack="true" />

    <!-- Tab：待参加 / 报名记录 / 已结束 -->
    <view class="category-wrap">
      <view class="category-list">
        <view
          v-for="(item, index) in tabs"
          :key="index"
          class="category-item category-pill"
          :class="{ active: currentTab === item.id }"
          @click="selectTab(item.id)"
        >
          <text class="category-text">{{ item.name }}</text>
        </view>
      </view>
    </view>

    <!-- 我的活动列表 -->
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

      <view class="load-more">
        <text v-if="loading">加载中...</text>
        <text v-else-if="noMore">没有更多了</text>
        <text v-else-if="activityList.length === 0">暂无相关活动</text>
      </view>
    </scroll-view>
  </view>
</template>

<script>
import NavBar from '@/components/nav-bar/nav-bar.vue'
export default {
  components: { NavBar },
  data() {
    return {
      currentTab: 'pending', // pending | signed | ended
      defaultCover: 'https://picsum.photos/seed/campus/800/450',
      tabs: [
        { id: 'pending', name: '待参加' },
        { id: 'signed', name: '报名记录' },
        { id: 'ended', name: '已结束' }
      ],
      activityList: [],
      page: 1,
      pageSize: 10,
      loading: false,
      noMore: false,
      isRefreshing: false
    }
  },
  onLoad() {
    this.loadList()
  },
  methods: {
    selectTab(tabId) {
      this.currentTab = tabId
      this.page = 1
      this.noMore = false
      this.loadList()
    },
    async loadList() {
      if (this.loading || this.noMore) return
      this.loading = true

      // 模拟数据，后续替换为真实接口
      setTimeout(() => {
        const mockData = [
          {
            id: 1,
            title: '校园创新创业大赛',
            coverImage: 'https://picsum.photos/seed/activity1/800/450',
            startTime: '2026-03-20 14:00',
            location: '学术报告厅',
            status: 'SIGNUP',
            currentPeople: 45,
            maxPeople: 100
          },
          {
            id: 2,
            title: '春季篮球友谊赛',
            coverImage: 'https://picsum.photos/seed/activity2/800/450',
            startTime: '2026-03-22 16:00',
            location: '体育馆',
            status: 'SIGNUP',
            currentPeople: 32,
            maxPeople: 50
          }
        ]
        if (this.page === 1) {
          this.activityList = mockData
        } else {
          this.activityList = [...this.activityList, ...mockData]
        }
        if (this.page >= 2) this.noMore = true
        this.loading = false
        this.isRefreshing = false
      }, 300)
    },
    loadMore() {
      if (!this.loading && !this.noMore) {
        this.page++
        this.loadList()
      }
    },
    onRefresh() {
      this.isRefreshing = true
      this.page = 1
      this.noMore = false
      this.loadList()
    },
    goToDetail(id) {
      uni.navigateTo({
        url: `/subpackage_activity/activityDetail/activityDetail?id=${id}`
      })
    },
    formatTime(time) {
      if (!time) return ''
      return time.substring(5, 16).replace(' ', ' ')
    },
    getStatusClass(status) {
      const map = {
        'DRAFT': 'status-draft',
        'PENDING': 'status-pending',
        'SIGNUP': 'status-signup',
        'SIGNUP_END': 'status-end',
        'ONGOING': 'status-ongoing',
        'ENDED': 'status-ended',
        'CANCELLED': 'status-cancelled'
      }
      return map[status] || 'status-default'
    },
    getStatusText(status) {
      const map = {
        'DRAFT': '草稿',
        'PENDING': '待审核',
        'SIGNUP': '报名中',
        'SIGNUP_END': '报名结束',
        'ONGOING': '进行中',
        'ENDED': '已结束',
        'CANCELLED': '已取消'
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

<style lang="scss" scoped>
.my-activity-container {
  min-height: 100vh;
  background-color: #F7F7F9;
  padding-bottom: 120rpx;
}

.category-wrap {
  background-color: #FFFFFF;
  margin-bottom: $spacing-block;
  border-bottom: 1px solid #EEEEEE;
}
.category-list {
  display: flex;
  align-items: center;
  padding: 0 $spacing-lg-rpx;
  min-height: 88rpx;
  gap: 24rpx;
}
.category-item.category-pill {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 16rpx 32rpx;
  border-radius: 999rpx;
  flex-shrink: 0;
  background-color: #F2F2F2;
}
.category-item.category-pill.active {
  background-color: $color-primary;
}
.category-text {
  font-size: 28rpx;
  font-weight: 400;
  color: #8E8E93;
}
.category-item.active .category-text {
  font-weight: 500;
  color: #FFFFFF;
}

.activity-list {
  height: calc(100vh - 280rpx);
  padding: 0;
  padding-top: 24rpx;
  padding-bottom: 48rpx;
  background-color: #FFFFFF;
  box-sizing: border-box;
}

.activity-item {
  padding: 0 $spacing-lg-rpx 32rpx;
  margin-bottom: 0;
  border-bottom: 1px solid #EEEEEE;
}
.activity-item:last-child {
  border-bottom: none;
}

.activity-cover-wrap {
  position: relative;
  width: 100%;
  height: 422rpx;
  border-radius: 8rpx;
  margin-bottom: 24rpx;
  background-color: #F7F7F9;
  overflow: hidden;
}
.activity-cover {
  display: block;
  width: 100%;
  height: 422rpx;
}
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
  padding: 0;
}

.activity-title {
  font-size: 32rpx;
  font-weight: 600;
  color: #1D1D1F;
  line-height: 1.4;
  margin-bottom: 8rpx;
}

.activity-meta {
  display: flex;
  flex-direction: column;
  gap: 4rpx;
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
  margin-top: 8rpx;
}
.activity-people-wrap {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  min-width: 120rpx;
}
.activity-people-text {
  font-size: 22rpx;
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

.load-more {
  text-align: center;
  padding: $spacing-lg-rpx;
  color: $color-text-secondary;
  font-size: $font-size-body-rpx;
}
</style>
