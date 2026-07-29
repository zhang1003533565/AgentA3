<template>
  <view class="page-root">
    <view class="container">
      <common-page-header title="浏览记录" :fixed="true" :placeholder="true" :showBack="true">
        <template #right>
          <view class="clear-action" @click.stop="confirmClear">
            <view class="clear-icon"></view>
            <text>清空</text>
          </view>
        </template>
      </common-page-header>
      <scroll-view scroll-y class="page-body">
        <view class="filter-card">
          <view
            v-for="filter in filters"
            :key="filter.key"
            class="filter-chip"
            :class="{ active: activeFilter === filter.key }"
            @click="activeFilter = filter.key"
          >
            {{ filter.label }}
          </view>
        </view>

        <view v-if="items.length === 0" class="empty">
          <image class="empty-illustration" src="/static/illustrations/market-empty-publish.svg" mode="aspectFit" />
          <view class="empty-title">还没有浏览记录</view>
          <view class="empty-subtitle">看过的校园闲置会保存在这里</view>
        </view>

        <view v-else-if="filteredItems.length === 0" class="empty empty--compact">
          <view class="empty-title">暂无该时间段记录</view>
          <view class="empty-subtitle">切换时间范围看看其他浏览记录</view>
        </view>

        <view v-else class="history-list">
          <view v-for="section in groupedItems" :key="section.key" class="date-section">
            <view class="section-label">{{ section.label }}</view>
            <view class="history-group">
              <view
                v-for="(item, index) in section.items"
                :key="item.id"
                class="history-row"
                :class="{ 'history-row--last': index === section.items.length - 1 }"
                @click="openDetail(item.id)"
              >
                <image class="cover" :src="item.image || '/static/images/default-goods.svg'" mode="aspectFill" />
                <view class="main">
                  <view class="title">{{ item.title || '校园市集商品' }}</view>
                  <view class="meta">{{ item.campusName || item.tradeLocation || '校园市集' }}</view>
                  <view class="time">{{ formatTime(item.viewTime) }}</view>
                </view>
                <view class="more-dot">
                  <text></text>
                  <text></text>
                  <text></text>
                </view>
              </view>
            </view>
          </view>
          <view class="more-text">没有更多了</view>
        </view>
      </scroll-view>
    </view>
  </view>
</template>

<script>
import CommonPageHeader from '@/components/common-page-header/common-page-header.vue'
const HISTORY_KEY = 'market_browse_history'
const DAY = 24 * 60 * 60 * 1000
const FILTERS = [
  { key: 'all', label: '全部' },
  { key: '3days', label: '3天内' },
  { key: 'week', label: '一周内' },
  { key: 'month', label: '一个月内' },
  { key: 'older', label: '一个月外' }
]

export default {
  components: { CommonPageHeader },
  data() {
    return {
      items: [],
      activeFilter: 'all',
      filters: FILTERS
    }
  },
  computed: {
    sortedItems() {
      return [...this.items].sort((a, b) => this.getTimestamp(b.viewTime) - this.getTimestamp(a.viewTime))
    },
    filteredItems() {
      if (this.activeFilter === 'all') return this.sortedItems
      const now = Date.now()
      return this.sortedItems.filter((item) => {
        const time = this.getTimestamp(item.viewTime)
        if (!time) return false
        const age = now - time
        if (this.activeFilter === '3days') return age <= 3 * DAY
        if (this.activeFilter === 'week') return age <= 7 * DAY
        if (this.activeFilter === 'month') return age <= 30 * DAY
        if (this.activeFilter === 'older') return age > 30 * DAY
        return true
      })
    },
    groupedItems() {
      const groups = []
      const indexMap = new Map()
      this.filteredItems.forEach((item) => {
        const key = this.dateKey(item.viewTime)
        if (!indexMap.has(key)) {
          indexMap.set(key, groups.length)
          groups.push({
            key,
            label: this.dateLabel(item.viewTime),
            items: []
          })
        }
        groups[indexMap.get(key)].items.push(item)
      })
      return groups
    }
  },
  onShow() {
    this.loadHistory()
  },
  methods: {
    loadHistory() {
      try {
        const list = uni.getStorageSync(HISTORY_KEY)
        this.items = Array.isArray(list) ? list : []
      } catch (e) {
        this.items = []
      }
    },
    confirmClear() {
      if (this.items.length === 0) {
        uni.showToast({ title: '暂无可清空记录', icon: 'none' })
        return
      }
      uni.showModal({
        title: '清空浏览记录',
        content: '确定清空所有浏览记录吗？',
        confirmText: '清空',
        confirmColor: '#2F73E0',
        success: (res) => {
          if (!res.confirm) return
          uni.removeStorageSync(HISTORY_KEY)
          this.items = []
          uni.showToast({ title: '已清空', icon: 'none' })
        }
      })
    },
    openDetail(id) {
      if (id) uni.navigateTo({ url: `/subpackage_lostfound/lostfoundDetail/lostfoundDetail?id=${id}` })
    },
    getTimestamp(value) {
      if (!value) return 0
      const date = new Date(value)
      const time = date.getTime()
      return Number.isFinite(time) ? time : 0
    },
    dateKey(value) {
      const date = new Date(this.getTimestamp(value))
      return `${date.getFullYear()}-${date.getMonth() + 1}-${date.getDate()}`
    },
    dayStart(value) {
      const date = new Date(value)
      date.setHours(0, 0, 0, 0)
      return date.getTime()
    },
    dateLabel(value) {
      const time = this.getTimestamp(value)
      if (!time) return '较早'
      const start = this.dayStart(time)
      const today = this.dayStart(Date.now())
      if (start === today) return '今天'
      if (start === today - DAY) return '昨天'
      const date = new Date(time)
      return `${date.getMonth() + 1}月${date.getDate()}日`
    },
    formatTime(value) {
      if (!value) return ''
      const date = new Date(this.getTimestamp(value))
      return `${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
    }
  }
}
</script>

<style scoped>
.page-root {
  min-height: 100vh;
  background: #F7F8FA;
}

.container {
  width: 100%;
  max-width: 430px;
  min-height: 100vh;
  margin: 0 auto;
  box-sizing: border-box;
  background: #F7F8FA;
}

.clear-action {
  display: inline-flex;
  align-items: center;
  justify-content: flex-end;
  gap: 8rpx;
  min-width: 92rpx;
  height: 64rpx;
  color: #1D2430;
  font-size: 24rpx;
  font-weight: 800;
}

.clear-icon {
  position: relative;
  width: 28rpx;
  height: 30rpx;
  color: currentColor;
}

.clear-icon::before {
  content: '';
  position: absolute;
  left: 5rpx;
  top: 9rpx;
  width: 18rpx;
  height: 17rpx;
  border: 3rpx solid currentColor;
  border-top: 0;
  border-radius: 0 0 4rpx 4rpx;
  box-sizing: border-box;
}

.clear-icon::after {
  content: '';
  position: absolute;
  left: 3rpx;
  top: 5rpx;
  width: 22rpx;
  height: 3rpx;
  border-radius: 999rpx;
  background: currentColor;
  box-shadow: 7rpx -4rpx 0 -1rpx currentColor;
}

.page-body {
  height: calc(100vh - 88rpx);
  padding: 24rpx 18rpx 72rpx;
  box-sizing: border-box;
  background: #F7F8FA;
}

.filter-card {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 14rpx;
  padding: 18rpx;
  margin-bottom: 26rpx;
  border-radius: 24rpx;
  background: rgba(255, 255, 255, 0.92);
  box-shadow: 0 12rpx 30rpx rgba(73, 99, 132, 0.08);
  box-sizing: border-box;
}

.filter-chip {
  display: flex;
  align-items: center;
  justify-content: center;
  min-width: 0;
  height: 58rpx;
  border: 1rpx solid #E4EAF1;
  border-radius: 999rpx;
  background: #FFFFFF;
  color: #667085;
  font-size: 23rpx;
  font-weight: 800;
  white-space: nowrap;
  box-sizing: border-box;
}

.filter-chip.active {
  border-color: #397DFF;
  background: #397DFF;
  color: #FFFFFF;
  box-shadow: 0 10rpx 22rpx rgba(57, 125, 255, 0.24);
}

.history-list {
  padding-bottom: 12rpx;
}

.date-section {
  margin-bottom: 28rpx;
}

.section-label {
  margin: 0 0 14rpx 10rpx;
  color: #667085;
  font-size: 25rpx;
  font-weight: 900;
}

.history-group {
  overflow: hidden;
  border-radius: 18rpx;
  background: #FFFFFF;
  box-shadow: 0 10rpx 24rpx rgba(73, 99, 132, 0.06);
}

.history-row {
  position: relative;
  display: flex;
  align-items: center;
  gap: 20rpx;
  min-height: 132rpx;
  padding: 18rpx 24rpx;
  box-sizing: border-box;
}

.history-row::after {
  content: '';
  position: absolute;
  right: 24rpx;
  bottom: 0;
  left: 140rpx;
  height: 1rpx;
  background: #E8EDF3;
}

.history-row--last::after {
  display: none;
}

.cover {
  width: 92rpx;
  height: 92rpx;
  border-radius: 12rpx;
  background: #EDF3F8;
  flex-shrink: 0;
}

.main {
  flex: 1;
  min-width: 0;
}

.title {
  color: #162033;
  font-size: 28rpx;
  font-weight: 900;
  line-height: 1.2;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.meta {
  margin-top: 14rpx;
  color: #6B7788;
  font-size: 23rpx;
  font-weight: 800;
  line-height: 1.2;
}

.time {
  margin-top: 10rpx;
  color: #8994A3;
  font-size: 22rpx;
  font-weight: 600;
  line-height: 1.2;
}

.more-dot {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  gap: 5rpx;
  width: 46rpx;
  height: 46rpx;
  color: #667085;
  flex-shrink: 0;
}

.more-dot text {
  width: 5rpx;
  height: 5rpx;
  border-radius: 50%;
  background: currentColor;
}

.more-text {
  padding: 8rpx 0 20rpx;
  color: #8E99A8;
  font-size: 24rpx;
  font-weight: 700;
  text-align: center;
}

.empty {
  min-height: calc(100vh - 340rpx);
  padding: 116rpx 0 80rpx;
  text-align: center;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.empty--compact {
  min-height: 420rpx;
}

.empty-illustration {
  width: 320rpx;
  height: 252rpx;
  margin-bottom: 44rpx;
}

.empty-title {
  color: #1D2430;
  font-size: 32rpx;
  font-weight: 900;
  line-height: 1.25;
}

.empty-subtitle {
  margin-top: 18rpx;
  color: #8E99A8;
  font-size: 24rpx;
  font-weight: 600;
  line-height: 1.4;
}
</style>
