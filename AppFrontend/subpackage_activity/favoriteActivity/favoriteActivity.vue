<template>
  <view class="my-activity-container">
    <nav-bar title="我的收藏" :showBack="true" />

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
        <text v-else-if="activityList.length === 0">暂无收藏</text>
      </view>
    </scroll-view>
  </view>
</template>

<script>
import NavBar from '@/components/nav-bar/nav-bar.vue'
import { getMyFavoriteList } from '@/api/activity.js'

export default {
  components: { NavBar },
  data() {
    return {
      defaultCover: 'https://picsum.photos/seed/campus/800/450',
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
    async loadList() {
      if (this.loading || this.noMore) return
      this.loading = true
      try {
        const res = await getMyFavoriteList({ page: this.page, size: this.pageSize })
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
      try {
        return time.substring(5, 16).replace(' ', ' ')
      } catch (e) {
        return ''
      }
    },
    getStatusClass(status) {
      const map = {
        'DRAFT': 'status-draft',
        'PUBLISHED': 'status-ongoing',
        'COMPLETED': 'status-ended'
      }
      return map[status] || 'status-default'
    },
    getStatusText(status) {
      const map = {
        'DRAFT': '草稿',
        'PUBLISHED': '进行中',
        'COMPLETED': '已结束'
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

.activity-list {
  height: calc(100vh - 160rpx);
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
