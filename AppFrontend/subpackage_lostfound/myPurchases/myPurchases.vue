<template>
  <view class="page-root">
    <view class="container">
      <nav-bar title="我的购买" :fixed="true" :placeholder="true" />
      <scroll-view scroll-y class="page-body">
        <view v-if="items.length === 0" class="empty"><text>暂无购买记录</text></view>
        <view v-for="record in items" :key="record.id" class="record-card" @click="openChat(record)">
          <image class="cover" :src="record.itemImage || '/static/images/default-goods.png'" mode="aspectFill" />
          <view class="main">
            <view class="title">{{ record.itemTitle || '校园市集商品' }}</view>
            <view class="meta">{{ statusText(record.status) }}</view>
            <view class="time">{{ formatTime(record.createTime) }}</view>
          </view>
        </view>
      </scroll-view>
      <market-bottom-bar activeTab="profile" />
    </view>
  </view>
</template>

<script>
import NavBar from '@/components/nav-bar/nav-bar.vue'
import MarketBottomBar from '@/components/market-bottom-bar/market-bottom-bar.vue'
import { createOrGetChatSession, getTradeRecords } from '@/api/secondhand'

export default {
  components: { NavBar, MarketBottomBar },
  data() {
    return { items: [] }
  },
  onShow() {
    this.loadRecords()
  },
  methods: {
    async loadRecords() {
      try {
        const res = await getTradeRecords({ current: 1, size: 100 })
        const records = Array.isArray(res?.data?.records) ? res.data.records : (Array.isArray(res?.data) ? res.data : [])
        this.items = records.filter((item) => item.buyerId || item.buyerName || item.role === 'BUYER')
      } catch (e) {
        console.error('加载购买记录失败', e)
        this.items = []
      }
    },
    statusText(status) {
      const map = { WAIT_CONFIRM: '待卖家确认', TRADING: '交易中', COMPLETED: '已完成', CANCELLED: '已取消' }
      return map[status] || '交易记录'
    },
    async openChat(record) {
      if (!record.itemId) return
      try {
        const res = await createOrGetChatSession(record.itemId, record.sellerId)
        const sessionId = res?.data?.sessionId || res?.data?.id
        uni.navigateTo({ url: `/subpackage_lostfound/lostfoundChat/lostfoundChat?${sessionId ? `sessionId=${sessionId}` : `itemId=${record.itemId}`}` })
      } catch (e) {
        uni.navigateTo({ url: `/subpackage_lostfound/lostfoundDetail/lostfoundDetail?id=${record.itemId}` })
      }
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
.record-card { display: flex; gap: 18rpx; padding: 20rpx; margin-bottom: 18rpx; background: #fff; border-radius: 18rpx; box-shadow: 0 6rpx 18rpx rgba(43, 68, 94, 0.08); }
.cover { width: 132rpx; height: 132rpx; border-radius: 14rpx; background: #edf3f8; }
.main { flex: 1; min-width: 0; }
.title { color: #172331; font-size: 28rpx; font-weight: 900; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.meta { margin-top: 18rpx; color: #5c8ab8; font-size: 24rpx; font-weight: 800; }
.time { margin-top: 14rpx; color: #8aa1b2; font-size: 22rpx; }
</style>
