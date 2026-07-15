<template>
  <view class="page-root">
    <view class="screen">
      <view class="container">
        <nav-bar title="交易通知" :fixed="true" :placeholder="true" />

        <scroll-view scroll-y class="page-body">
          <view v-if="notifications.length === 0" class="empty">
            <view class="empty-icon">交</view>
            <view class="empty-title">暂无交易通知</view>
          </view>

          <view
            v-for="item in notifications"
            :key="item.id"
            class="notice-card"
            :class="{ unread: !item.isRead }"
            @click="openNotification(item)"
          >
            <image v-if="item.itemImage" class="item-img" :src="item.itemImage" mode="aspectFill" />
            <view v-else class="item-img item-empty">{{ item.itemTitle ? item.itemTitle[0] : '物' }}</view>

            <view class="notice-main">
              <view class="notice-top">
                <view class="item-title">{{ item.itemTitle || '商品' }}</view>
                <view class="status-pill">{{ item.tradeStatusText || statusText(item.tradeStatus) }}</view>
              </view>
              <view class="notice-content">{{ item.content }}</view>
              <view class="notice-time">{{ fmt(item.createTime) }}</view>
            </view>

            <view v-if="!item.isRead" class="unread-dot"></view>
          </view>
        </scroll-view>
        <market-bottom-bar activeTab="messages" />
      </view>
    </view>
  </view>
</template>

<script>
import NavBar from '@/components/nav-bar/nav-bar.vue'
import MarketBottomBar from '@/components/market-bottom-bar/market-bottom-bar.vue'
import { getTradeNotifications, markTradeNotificationRead } from '@/api/secondhand'

const STATUS_TEXT = {
  WAIT_CONFIRM: '待确认',
  TRADING: '交易中',
  COMPLETED: '已完成',
  CANCELLED: '已取消'
}

function normalizeNotification(item) {
  return {
    id: item.id,
    sessionId: item.sessionId,
    itemId: item.itemId,
    itemTitle: item.itemTitle || '',
    itemImage: item.itemImage || '',
    tradeId: item.tradeId,
    tradeStatus: item.tradeStatus,
    tradeStatusText: item.tradeStatusText || '',
    content: item.content || '',
    createTime: item.createTime || '',
    isRead: Boolean(item.isRead)
  }
}

export default {
  components: {
    NavBar,
    MarketBottomBar
  },
  data() {
    return {
      notifications: [],
      loading: false
    }
  },
  async onLoad() {
    await this.loadNotifications()
  },
  async onShow() {
    await this.loadNotifications()
  },
  methods: {
    async loadNotifications() {
      if (this.loading) return
      try {
        this.loading = true
        const res = await getTradeNotifications({ current: 1, size: 100 })
        const records = Array.isArray(res?.data?.records) ? res.data.records : []
        this.notifications = records.map(normalizeNotification)
      } catch (e) {
        console.error('加载交易通知失败', e)
        uni.showToast({ title: '通知加载失败', icon: 'none' })
      } finally {
        this.loading = false
      }
    },
    statusText(status) {
      return STATUS_TEXT[status] || ''
    },
    fmt(ts) {
      if (!ts) return ''
      const d = new Date(String(ts).replace(/-/g, '/'))
      const now = new Date()
      const diff = now - d
      if (diff < 60000) return '刚刚'
      if (diff < 3600000) return Math.floor(diff / 60000) + '分钟前'
      if (diff < 86400000) return Math.floor(diff / 3600000) + '小时前'
      return `${d.getMonth() + 1}/${d.getDate()}`
    },
    async openNotification(item) {
      try {
        if (!item.isRead) {
          await markTradeNotificationRead(item.id)
          item.isRead = true
        }
      } catch (e) {
        console.error('标记交易通知已读失败', e)
      }
      if (item.sessionId) {
        uni.navigateTo({
          url: `/subpackage_lostfound/lostfoundChat/lostfoundChat?sessionId=${item.sessionId}`
        })
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
  height: calc(100vh - 120rpx);
  padding: 20rpx 0 32rpx;
  box-sizing: border-box;
}

.empty {
  padding: 140rpx 0;
  text-align: center;
}

.empty-icon {
  width: 92rpx;
  height: 92rpx;
  margin: 0 auto 22rpx;
  border-radius: 28rpx;
  background: #eaf4ef;
  color: #2f8a58;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 34rpx;
  font-weight: 900;
}

.empty-title {
  color: #8aa1b2;
  font-size: 28rpx;
}

.notice-card {
  position: relative;
  display: flex;
  gap: 20rpx;
  padding: 22rpx;
  margin-bottom: 16rpx;
  border-radius: 20rpx;
  background: #fff;
  box-shadow: 0 4rpx 16rpx rgba(43, 68, 94, 0.08);
}

.notice-card.unread {
  box-shadow: 0 8rpx 24rpx rgba(47, 138, 88, 0.12);
}

.item-img {
  width: 108rpx;
  height: 108rpx;
  border-radius: 16rpx;
  background: #edf4fb;
  flex-shrink: 0;
}

.item-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  color: #5c8ab8;
  font-size: 34rpx;
  font-weight: 900;
}

.notice-main {
  flex: 1;
  min-width: 0;
}

.notice-top {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin-bottom: 10rpx;
}

.item-title {
  flex: 1;
  min-width: 0;
  color: #172331;
  font-size: 27rpx;
  font-weight: 800;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.status-pill {
  padding: 5rpx 14rpx;
  border-radius: 999rpx;
  background: rgba(242, 144, 64, 0.14);
  color: #c46b17;
  font-size: 20rpx;
  font-weight: 800;
  flex-shrink: 0;
}

.notice-content {
  color: #536b82;
  font-size: 24rpx;
  line-height: 1.45;
  margin-bottom: 10rpx;
}

.notice-time {
  color: #9ab0c0;
  font-size: 22rpx;
}

.unread-dot {
  position: absolute;
  top: 22rpx;
  right: 18rpx;
  width: 14rpx;
  height: 14rpx;
  border-radius: 50%;
  background: #e74c3c;
}
</style>
