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

        <view v-else class="history-content">
          <view v-for="section in groupedItems" :key="section.key" class="date-section">
            <view class="section-label">{{ section.label }}</view>
            <view class="history-grid">
              <view
                v-for="item in section.items"
                :key="item.id"
                class="history-card"
                @click="openDetail(item.id)"
              >
                <view class="card-cover">
                  <image v-if="item.image" class="cover-img" :src="item.image" mode="aspectFill" />
                  <view v-else class="cover-placeholder">
                    <text class="cover-emoji">{{ itemEmoji(item.id) }}</text>
                  </view>
                  <view class="card-badge-type" :class="'card-badge-type--' + (item.tradeType || 'sell')">
                    {{ item.tradeType === 'buy' ? '收' : '出' }}
                  </view>
                </view>
                <view class="card-body">
                  <view class="card-title">{{ item.title || '校园市集商品' }}</view>
                  <view class="card-price">¥{{ formatPrice(item.price) }}</view>
                  <view class="card-location-row" v-if="item.campusName || item.tradeLocation">
                    <view class="loc-icon"></view>
                    <text class="card-location">{{ item.campusName || item.tradeLocation }}</text>
                  </view>
                  <view class="card-user">
                    <view class="user-avatar">{{ (item.userName || '同').slice(0, 1) }}</view>
                    <text class="user-name">{{ item.userName || '同学' }}</text>
                    <text class="card-time">{{ formatTime(item.viewTime) }}</text>
                  </view>
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
import { getMyBrowseHistory, clearMyBrowseHistory } from '@/api/secondhand'

const DAY = 24 * 60 * 60 * 1000
const EMOJIS = ['📱', '💻', '📷', '🎧', '⌚', '📚', '👟', '🧥', '🪑', '🏠', '🎮', '🎸', '🖥️', '📦']
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
      filters: FILTERS,
      loading: false,
      currentPage: 1,
      pageSize: 20,
      hasMore: true
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
    itemEmoji(id) {
      return EMOJIS[(id || 0) % EMOJIS.length]
    },
    formatPrice(price) {
      const p = Number(price)
      if (!Number.isFinite(p) || p <= 0) return '免费'
      return p.toFixed(p % 1 === 0 ? 0 : 2)
    },
    async loadHistory() {
      if (this.loading) return
      this.loading = true
      try {
        const res = await getMyBrowseHistory({ current: 1, size: this.pageSize })
        const records = Array.isArray(res?.data?.records) ? res.data.records : []
        this.items = records.map(this.normalizeItem)
        this.hasMore = records.length >= this.pageSize
        this.currentPage = 1
      } catch (e) {
        console.error('加载浏览历史失败', e)
        this.items = []
      } finally {
        this.loading = false
      }
    },
    normalizeItem(item) {
      const images = Array.isArray(item.images) ? item.images : []
      const firstImage = images.length > 0 ? images[0] : ''
      return {
        id: item.itemId || item.id,
        title: item.title || '校园市集商品',
        image: firstImage,
        price: item.price,
        tradeType: item.tradeType || 'sell',
        status: item.status,
        campusName: item.campusName,
        tradeLocation: item.tradeLocation,
        pickupPoint: item.pickupPoint,
        userName: item.sellerName,
        userAvatar: item.sellerAvatar,
        viewTime: item.browseTime || item.createTime,
        createTime: item.createTime
      }
    },
    async confirmClear() {
      if (this.items.length === 0) {
        uni.showToast({ title: '暂无可清空记录', icon: 'none' })
        return
      }
      uni.showModal({
        title: '清空浏览历史',
        content: '确定清空所有浏览记录吗？',
        confirmText: '清空',
        confirmColor: '#2F73E0',
        success: async (res) => {
          if (!res.confirm) return
          try {
            await clearMyBrowseHistory()
            this.items = []
            uni.showToast({ title: '已清空', icon: 'none' })
          } catch (e) {
            console.error('清空失败', e)
            uni.showToast({ title: '清空失败', icon: 'none' })
          }
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
  padding: 0 12rpx;
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
  padding: 24rpx 0 72rpx;
  box-sizing: border-box;
  background: #F7F8FA;
}

.filter-card {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 14rpx;
  padding: 18rpx;
  margin: 0 12rpx 26rpx;
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

.history-content {
  padding: 0 12rpx;
}

.date-section {
  margin-bottom: 24rpx;
}

.section-label {
  margin: 0 0 14rpx 4rpx;
  color: #667085;
  font-size: 25rpx;
  font-weight: 900;
}

.history-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12rpx;
}

.history-card {
  display: flex;
  flex-direction: column;
  background: #FFFFFF;
  border-radius: 16rpx;
  overflow: hidden;
  box-shadow: 0 4rpx 16rpx rgba(30, 41, 59, 0.06);
  box-sizing: border-box;
}

.card-cover {
  position: relative;
  width: 100%;
  padding-top: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #EEF2F7;
  overflow: hidden;
}

.cover-img {
  position: absolute;
  left: 0;
  top: 0;
  width: 100%;
  height: 100%;
}

.cover-placeholder {
  position: absolute;
  left: 0;
  top: 0;
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 14rpx;
  color: #8E8E93;
}

.cover-emoji {
  font-size: 58rpx;
  line-height: 1;
}

.card-badge-type {
  position: absolute;
  left: 14rpx;
  top: 14rpx;
  min-width: 40rpx;
  height: 40rpx;
  padding: 0 8rpx;
  border-radius: 10rpx;
  color: #FFFFFF;
  font-size: 22rpx;
  font-weight: 800;
  line-height: 40rpx;
  text-align: center;
  box-shadow: 0 4rpx 10rpx rgba(0, 0, 0, 0.2);
}

.card-badge-type--sell {
  background: #FF6B35;
  box-shadow: 0 4rpx 10rpx rgba(255, 107, 53, 0.35);
}

.card-badge-type--buy {
  background: #4A90E2;
  box-shadow: 0 4rpx 10rpx rgba(74, 144, 226, 0.35);
}

.card-body {
  padding: 14rpx 16rpx 18rpx;
  display: flex;
  flex-direction: column;
  gap: 6rpx;
}

.card-title {
  color: #1D2430;
  font-size: 24rpx;
  font-weight: 800;
  line-height: 1.3;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-price {
  color: #FF4D2E;
  font-size: 28rpx;
  font-weight: 900;
  line-height: 1.2;
}

.card-location-row {
  display: flex;
  align-items: center;
  gap: 6rpx;
  color: #8A94A6;
  font-size: 20rpx;
  line-height: 1.3;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.loc-icon {
  position: relative;
  width: 18rpx;
  height: 18rpx;
  flex-shrink: 0;
}

.loc-icon::before {
  content: '';
  position: absolute;
  left: 50%;
  top: 50%;
  width: 12rpx;
  height: 12rpx;
  margin-left: -6rpx;
  margin-top: -12rpx;
  border: 2rpx solid #8A94A6;
  border-radius: 50% 50% 50% 0;
  transform: rotate(-45deg);
  box-sizing: border-box;
}

.loc-icon::after {
  content: '';
  position: absolute;
  left: 50%;
  top: 50%;
  width: 4rpx;
  height: 4rpx;
  margin-left: -2rpx;
  margin-top: -2rpx;
  background: #8A94A6;
  border-radius: 50%;
}

.card-location {
  font-size: 20rpx;
  color: #8A94A6;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-user {
  display: flex;
  align-items: center;
  gap: 8rpx;
  padding-top: 10rpx;
  border-top: 1rpx solid #F0F2F5;
}

.user-avatar {
  width: 34rpx;
  height: 34rpx;
  border-radius: 50%;
  background: rgba(92, 122, 153, 0.12);
  color: #5C7A99;
  font-size: 18rpx;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.user-name {
  font-size: 21rpx;
  color: #666A70;
  font-weight: 600;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-time {
  font-size: 18rpx;
  color: #9AA2AE;
  font-weight: 500;
  margin-left: auto;
}

.more-text {
  padding: 16rpx 0 20rpx;
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
