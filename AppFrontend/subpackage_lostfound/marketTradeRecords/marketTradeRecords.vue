<template>
  <view class="page-root">
    <view class="container">
      <common-page-header title="交易记录" :fixed="true" :placeholder="true" :showBack="true" />
      <scroll-view scroll-y class="page-body">
        <view v-if="records.length === 0" class="empty"><text>暂无交易记录</text></view>
        <view v-for="record in records" :key="record.id" class="record-card" @click="openDetail(record.itemId)">
          <view class="status" :class="statusClass(record.status)">{{ statusText(record.status) }}</view>
          <view class="title">{{ record.itemTitle || '校园市集商品' }}</view>
          <view class="meta">买家：{{ record.buyerName || record.buyerId || '-' }} · 卖家：{{ record.sellerName || record.sellerId || '-' }}</view>
          <view class="time">{{ formatTime(record.updateTime || record.createTime) }}</view>
        </view>
      </scroll-view>
      <market-bottom-bar activeTab="profile" />
    </view>
  </view>
</template>

<script>
import CommonPageHeader from '@/components/common-page-header/common-page-header.vue'
import MarketBottomBar from '@/components/market-bottom-bar/market-bottom-bar.vue'
import { getTradeRecords } from '@/api/secondhand'

export default {
  components: { CommonPageHeader, MarketBottomBar },
  data() {
    return { records: [] }
  },
  onShow() {
    this.loadRecords()
  },
  methods: {
    async loadRecords() {
      try {
        const res = await getTradeRecords({ current: 1, size: 100 })
        this.records = Array.isArray(res?.data?.records) ? res.data.records : (Array.isArray(res?.data) ? res.data : [])
      } catch (e) {
        console.error('加载交易记录失败', e)
        this.records = []
      }
    },
    statusText(status) {
      const map = { WAIT_CONFIRM: '待确认', TRADING: '交易中', COMPLETED: '已完成', CANCELLED: '已取消' }
      return map[status] || '交易记录'
    },
    statusClass(status) {
      return `is-${String(status || '').toLowerCase().replace('_', '-')}`
    },
    openDetail(itemId) {
      if (itemId) uni.navigateTo({ url: `/subpackage_lostfound/lostfoundDetail/lostfoundDetail?id=${itemId}` })
    },
    formatTime(value) {
      if (!value) return ''
      const date = new Date(String(value).replace(/-/g, '/'))
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
.record-card { position: relative; padding: 24rpx; margin-bottom: 18rpx; background: #fff; border-radius: 18rpx; box-shadow: 0 6rpx 18rpx rgba(43, 68, 94, 0.08); }
.status { display: inline-flex; padding: 8rpx 16rpx; border-radius: 999rpx; background: #eef5fc; color: #5c8ab8; font-size: 22rpx; font-weight: 900; }
.status.is-completed { background: #e8f7ee; color: #2d8a55; }
.status.is-cancelled { background: #f3f5f8; color: #8a96a3; }
.title { margin-top: 16rpx; color: #172331; font-size: 29rpx; font-weight: 900; }
.meta, .time { margin-top: 12rpx; color: #7d8c9c; font-size: 23rpx; }
</style>
