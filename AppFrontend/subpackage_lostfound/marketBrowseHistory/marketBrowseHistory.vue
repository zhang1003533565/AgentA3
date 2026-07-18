<template>
  <view class="page-root">
    <view class="container">
      <common-page-header title="浏览记录" :fixed="true" :placeholder="true" :showBack="true" />
      <scroll-view scroll-y class="page-body">
        <view v-if="items.length === 0" class="empty"><text>暂无浏览记录</text></view>
        <view v-for="item in items" :key="item.id" class="item-card" @click="openDetail(item.id)">
          <image class="cover" :src="item.image || '/static/images/default-goods.svg'" mode="aspectFill" />
          <view class="main">
            <view class="title">{{ item.title }}</view>
            <view class="meta">{{ item.campusName || item.tradeLocation || '校园市集' }}</view>
            <view class="time">{{ formatTime(item.viewTime) }}</view>
          </view>
        </view>
      </scroll-view>
      <market-bottom-bar activeTab="profile" />
    </view>
  </view>
</template>

<script>
import CommonPageHeader from '@/components/common-page-header/common-page-header.vue'
import MarketBottomBar from '@/components/market-bottom-bar/market-bottom-bar.vue'
const HISTORY_KEY = 'market_browse_history'

export default {
  components: { CommonPageHeader, MarketBottomBar },
  data() {
    return { items: [] }
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
    openDetail(id) {
      if (id) uni.navigateTo({ url: `/subpackage_lostfound/lostfoundDetail/lostfoundDetail?id=${id}` })
    },
    formatTime(value) {
      if (!value) return ''
      const date = new Date(value)
      return `${date.getMonth() + 1}/${date.getDate()} ${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
    }
  }
}
</script>

<style scoped>
.page-root { min-height: 100vh; background: #f0f5fa; }
.container { width: 100%; max-width: 430px; min-height: 100vh; margin: 0 auto; padding: 0 16rpx; box-sizing: border-box; background: #e8f0f8; }
.page-body { height: calc(100vh - 88rpx); padding: 24rpx 0 150rpx; box-sizing: border-box; }
.empty { padding: 180rpx 0; text-align: center; color: #8aa1b2; font-size: 27rpx; }
.item-card { display: flex; gap: 18rpx; padding: 20rpx; margin-bottom: 18rpx; background: #fff; border-radius: 18rpx; box-shadow: 0 6rpx 18rpx rgba(43, 68, 94, 0.08); }
.cover { width: 132rpx; height: 132rpx; border-radius: 14rpx; background: #edf3f8; }
.main { flex: 1; min-width: 0; }
.title { color: #172331; font-size: 28rpx; font-weight: 900; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.meta { margin-top: 18rpx; color: #5c8ab8; font-size: 24rpx; font-weight: 800; }
.time { margin-top: 14rpx; color: #8aa1b2; font-size: 22rpx; }
</style>
