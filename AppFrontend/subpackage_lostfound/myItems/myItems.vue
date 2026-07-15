<template>
  <view class="page-root">
    <view class="screen">
      <view class="container">
        <nav-bar title="我的发布" :fixed="true" :placeholder="true" />

        <scroll-view scroll-y class="page-body" refresher-enabled :refresher-triggered="refreshing" @refresherrefresh="refresh">
          <view class="summary">
            <view>
              <text class="summary-num">{{ items.length }}</text>
              <text class="summary-label">件商品</text>
            </view>
            <button class="publish-btn" @click="goPublish">发布商品</button>
          </view>

          <view v-if="!loading && items.length === 0" class="empty">
            <view class="empty-icon">📦</view>
            <view class="empty-title">还没有发布商品</view>
            <view class="empty-sub">发布后的校园市集商品会显示在这里</view>
          </view>

          <view v-for="item in items" :key="item.id" class="item-card">
            <view class="cover" @click="goDetail(item.id)">
              <image v-if="item.images.length" :src="item.images[0]" mode="aspectFill" />
              <text v-else>{{ emoji(item.id) }}</text>
            </view>
            <view class="body">
              <view class="title-row">
                <view class="title">{{ item.title || '未命名商品' }}</view>
                <view class="status" :class="statusClass(item.status)">{{ statusText(item.status) }}</view>
              </view>
              <view class="price">¥{{ priceText(item.price) }}</view>
              <view class="meta">
                <text>{{ item.campusName || '校内' }}</text>
                <text>{{ item.pickupPoint || item.tradeLocation || item.location || '校内自提' }}</text>
              </view>
              <view class="metrics">
                <text>热度 {{ item.heatScore || 0 }}</text>
                <text>咨询 {{ item.inquiryCount || 0 }}</text>
                <text>{{ formatTime(item.createTime) }}</text>
              </view>
              <view class="actions">
                <button class="ghost" @click="goDetail(item.id)">查看</button>
                <button
                  v-if="canOffline(item.status)"
                  class="ghost"
                  @click="setOffline(item.id)"
                >
                  下架
                </button>
                <button
                  v-else-if="canOnline(item.status)"
                  class="ghost primary"
                  @click="setOnline(item.id)"
                >
                  上架
                </button>
                <button v-else class="ghost disabled" disabled>不可操作</button>
              </view>
            </view>
          </view>
        </scroll-view>
      </view>
    </view>
  </view>
</template>

<script>
import NavBar from '@/components/nav-bar/nav-bar.vue'
import { getMySecondhandItems, offlineSecondhandItem, onlineSecondhandItem } from '@/api/secondhand'

const EMOJIS = ['📱', '💻', '📷', '🎧', '⌚', '📚', '👟', '🧥', '🪑', '🏠', '🎮', '🎸', '🖥️', '📦']

function normalizeItem(raw = {}) {
  return {
    id: raw.id,
    title: raw.title || raw.name || '',
    price: raw.price,
    images: Array.isArray(raw.images) ? raw.images : [],
    status: Number(raw.status ?? 0),
    campusName: raw.campusName || '',
    tradeLocation: raw.tradeLocation || '',
    pickupPoint: raw.pickupPoint || '',
    location: raw.location || '',
    heatScore: Number(raw.heatScore || 0),
    inquiryCount: Number(raw.inquiryCount || 0),
    createTime: raw.createTime || raw.ctime || ''
  }
}

export default {
  components: {
    NavBar
  },
  data() {
    return {
      loading: false,
      refreshing: false,
      items: []
    }
  },
  async onLoad() {
    await this.loadItems()
  },
  async onShow() {
    await this.loadItems()
  },
  methods: {
    async refresh() {
      this.refreshing = true
      await this.loadItems()
      this.refreshing = false
    },
    async loadItems() {
      try {
        this.loading = true
        const res = await getMySecondhandItems({ current: 1, size: 100 })
        const records = Array.isArray(res?.data?.records) ? res.data.records : Array.isArray(res?.data) ? res.data : []
        this.items = records.map(normalizeItem)
      } catch (e) {
        console.error('加载我的发布失败', e)
        uni.showToast({ title: '加载失败', icon: 'none' })
      } finally {
        this.loading = false
      }
    },
    statusText(status) {
      if (Number(status) === 5) return '交易中'
      if (Number(status) === 3) return '已售出'
      if (Number(status) === 4) return '已下架'
      return '在售'
    },
    statusClass(status) {
      if (Number(status) === 5) return 'is-pending'
      if (Number(status) === 3) return 'is-sold'
      if (Number(status) === 4) return 'is-offline'
      return 'is-online'
    },
    canOffline(status) {
      return [0, 1, 2].includes(Number(status))
    },
    canOnline(status) {
      return Number(status) === 4
    },
    priceText(value) {
      const price = Number(value)
      return Number.isFinite(price) ? price.toFixed(price % 1 === 0 ? 0 : 2) : '--'
    },
    emoji(id) {
      return EMOJIS[(Number(id) || 0) % EMOJIS.length]
    },
    formatTime(value) {
      if (!value) return ''
      const time = typeof value === 'string' ? new Date(value.replace(/-/g, '/')).getTime() : value
      const diff = Date.now() - time
      if (!Number.isFinite(diff)) return ''
      if (diff < 60000) return '刚刚'
      if (diff < 3600000) return `${Math.floor(diff / 60000)}分钟前`
      if (diff < 86400000) return `${Math.floor(diff / 3600000)}小时前`
      const date = new Date(time)
      return `${date.getMonth() + 1}/${date.getDate()}`
    },
    goDetail(id) {
      uni.navigateTo({
        url: `/subpackage_lostfound/lostfoundDetail/lostfoundDetail?id=${id}`
      })
    },
    goPublish() {
      uni.navigateTo({
        url: '/subpackage_lostfound/lostfoundPublish/lostfoundPublish'
      })
    },
    async setOffline(id) {
      try {
        await offlineSecondhandItem(id)
        uni.showToast({ title: '已下架', icon: 'none' })
        await this.loadItems()
      } catch (e) {
        console.error('下架失败', e)
        uni.showToast({ title: '操作失败', icon: 'none' })
      }
    },
    async setOnline(id) {
      try {
        await onlineSecondhandItem(id)
        uni.showToast({ title: '已上架', icon: 'none' })
        await this.loadItems()
      } catch (e) {
        console.error('上架失败', e)
        uni.showToast({ title: '操作失败', icon: 'none' })
      }
    }
  }
}
</script>

<style scoped>
.page-root,
.screen {
  width: 100%;
  min-height: 100vh;
  background: #f0f5fa;
}

.container {
  width: 100%;
  max-width: 430px;
  min-height: 100vh;
  margin: 0 auto;
  padding: 0 16rpx;
  box-sizing: border-box;
  background: #e8f0f8;
}

.page-body {
  height: calc(100vh - 88rpx);
  padding: 24rpx 0 56rpx;
  box-sizing: border-box;
}

.summary {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24rpx;
  padding: 28rpx;
  border-radius: 20rpx;
  background: #fff;
  box-shadow: 0 6rpx 18rpx rgba(43, 68, 94, 0.08);
}

.summary-num {
  color: #172331;
  font-size: 44rpx;
  font-weight: 900;
}

.summary-label {
  margin-left: 10rpx;
  color: #7d8c9c;
  font-size: 24rpx;
}

.publish-btn {
  height: 64rpx;
  padding: 0 24rpx;
  border-radius: 18rpx;
  background: #5c8ab8;
  color: #fff;
  font-size: 24rpx;
  font-weight: 800;
}

.publish-btn::after,
.ghost::after {
  border: none;
}

.empty {
  padding: 120rpx 32rpx;
  text-align: center;
  color: #8a99a8;
}

.empty-icon {
  font-size: 80rpx;
}

.empty-title {
  margin-top: 18rpx;
  color: #172331;
  font-size: 30rpx;
  font-weight: 800;
}

.empty-sub {
  margin-top: 8rpx;
  font-size: 24rpx;
}

.item-card {
  display: flex;
  gap: 20rpx;
  margin-bottom: 20rpx;
  padding: 22rpx;
  border-radius: 20rpx;
  background: #fff;
  box-shadow: 0 6rpx 18rpx rgba(43, 68, 94, 0.08);
}

.cover {
  width: 144rpx;
  height: 144rpx;
  border-radius: 18rpx;
  overflow: hidden;
  flex-shrink: 0;
  background: linear-gradient(135deg, rgba(123, 168, 212, 0.3), rgba(92, 138, 184, 0.3));
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 52rpx;
}

.cover image {
  width: 100%;
  height: 100%;
}

.body {
  flex: 1;
  min-width: 0;
}

.title-row {
  display: flex;
  align-items: flex-start;
  gap: 12rpx;
}

.title {
  flex: 1;
  min-width: 0;
  color: #172331;
  font-size: 28rpx;
  font-weight: 800;
  line-height: 1.35;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.status {
  flex-shrink: 0;
  padding: 6rpx 12rpx;
  border-radius: 999rpx;
  font-size: 20rpx;
  font-weight: 800;
}

.is-online {
  background: rgba(75, 154, 104, 0.14);
  color: #2f8a58;
}

.is-pending {
  background: rgba(242, 144, 64, 0.16);
  color: #c46b17;
}

.is-sold,
.is-offline {
  background: rgba(84, 99, 116, 0.14);
  color: #546374;
}

.price {
  margin-top: 10rpx;
  color: #f26a42;
  font-size: 32rpx;
  font-weight: 900;
}

.meta,
.metrics {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
  margin-top: 8rpx;
  color: #7d8c9c;
  font-size: 21rpx;
}

.actions {
  display: flex;
  gap: 12rpx;
  margin-top: 16rpx;
}

.ghost {
  height: 52rpx;
  margin: 0;
  padding: 0 20rpx;
  border-radius: 999rpx;
  background: #f1f6fa;
  color: #5c7894;
  font-size: 22rpx;
  font-weight: 800;
  line-height: 52rpx;
}

.ghost.primary {
  background: #e9f3ed;
  color: #2f8a58;
}

.ghost.disabled {
  color: #a4b0bb;
}
</style>
