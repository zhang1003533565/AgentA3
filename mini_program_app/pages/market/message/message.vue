<template>
  <view class="page-root">
    <common-page-header title="消息" :fixed="true" :placeholder="true" :showBack="true" :autoBack="false" @back="onBackToApp" />

    <view class="screen">
      <view class="container">
        <scroll-view
          scroll-y
          class="page-body"
          :show-scrollbar="false"
          refresher-enabled
          :refresher-triggered="refreshing"
          refresher-background="#F7F8FA"
          @refresherrefresh="refreshPage"
        >
          <!-- 通知卡片（固定第一行，不参与排序） -->
          <view class="notify-card" @click="goToNotifications">
            <view class="notify-left">
              <image class="notify-icon" src="/static/icons/notification.svg" mode="aspectFit" />
              <text class="notify-label">通知</text>
            </view>
            <view class="notify-right">
              <view v-if="unreadAnnounceCount > 0" class="notify-badge">{{ unreadAnnounceCount > 99 ? '99+' : unreadAnnounceCount }}</view>
              <text class="notify-arrow">›</text>
            </view>
          </view>

          <!-- 交易通知入口（与系统通知保持同级，不展示具体交易记录） -->
          <view class="notify-card" @click="goToTradeNotifications">
            <view class="notify-left">
              <image class="notify-icon" src="/static/icons/trade-notification.svg" mode="aspectFit" />
              <text class="notify-label">交易通知</text>
            </view>
            <view class="notify-right">
              <view v-if="unreadTradeCount > 0" class="notify-badge">{{ unreadTradeCount > 99 ? '99+' : unreadTradeCount }}</view>
              <text class="notify-arrow">›</text>
            </view>
          </view>

          <view class="section-title chat-section-title">
            <view class="section-title-mark"></view>
            <text>聊天消息</text>
          </view>

          <!-- 聊天会话列表 -->
          <view v-if="sessions.length === 0" class="empty">
            <image class="empty-icon" src="/static/icons/message-empty.svg" mode="aspectFit" />
            <text class="empty-text">暂无消息</text>
          </view>
          <view v-for="s in sessions" :key="s.id" class="session-card" @click="openChat(s)">
            <view class="sess-avatar">
              <image
                v-if="s.otherAvatar"
                class="sess-avatar-img"
                :src="s.otherAvatar"
                mode="aspectFill"
                @error="handleSessionAvatarError(s)"
              />
              <text v-else>{{ s.otherName ? s.otherName[0] : '用' }}</text>
            </view>
            <view class="sess-body">
              <view class="sess-top-row">
                <text class="sess-name">{{ s.otherName || '用户' }}</text>
                <text class="sess-time">{{ fmt(s.lastTime) }}</text>
              </view>
              <view class="sess-mid-row">
                <text class="sess-product">[{{ s.itemTitle || '商品' }}]</text>
              </view>
              <view class="sess-bottom-row">
                <text class="sess-preview">{{ s.lastMsg || '' }}</text>
                <view v-if="s.unread > 0" class="sess-badge">{{ s.unread > 99 ? '99+' : s.unread }}</view>
              </view>
            </view>
          </view>
        </scroll-view>
      </view>
    </view>

    <market-bottom-bar activeTab="messages" />
  </view>
</template>

<script>
import CommonPageHeader from '@/components/common-page-header/common-page-header.vue'
import MarketBottomBar from '@/components/market-bottom-bar/market-bottom-bar.vue'
import { getChatSessions, getTradeNotificationUnreadCount } from '@/api/secondhand'
import { getEnabledAnnouncements } from '@/api/notice'
import { getMessageState, refreshMessageState, subscribeMessageStore } from '@/utils/messageStore'
import { buildDefaultAvatar, pickOtherAvatar } from '@/subpackage_lostfound/utils/avatar.js'

function normalizeSession(item) {
  const otherName = item.otherUsername || item.sellerName || '用户'
  return {
    id: item.sessionId,
    itemId: item.itemId,
    itemTitle: item.itemTitle || '',
    otherName,
    otherAvatar: pickOtherAvatar(item) || buildDefaultAvatar({ username: otherName || 'chat-other' }),
    lastMsg: item.lastMessage || '',
    lastTime: item.lastTime || '',
    unread: item.unreadCount || 0
  }
}

export default {
  components: { CommonPageHeader, MarketBottomBar },
  data() {
    return {
      sessions: [],
      unreadAnnounceCount: 0,
      unreadTradeCount: 0,
      refreshing: false,
      unsubscribeMessageStore: null
    }
  },
  async onLoad() {
    this.applyMessageState(getMessageState())
    this.unsubscribeMessageStore = subscribeMessageStore((state, reason) => {
      this.applyMessageState(state)
      if (reason !== 'subscribe') {
        this.loadSessionsFromStore(state)
      }
    })
    await this.loadData()
  },
  async onShow() {
    await refreshMessageState('message-page-show')
  },
  onUnload() {
    if (this.unsubscribeMessageStore) {
      this.unsubscribeMessageStore()
      this.unsubscribeMessageStore = null
    }
  },
  methods: {
    onBackToApp() {
      uni.reLaunch({ url: '/pages/index/index' })
    },
    async loadData() {
      await Promise.all([
        this.loadSessions(),
        this.loadTradeRecords(),
        this.checkAnnouncements()
      ])
      await refreshMessageState('message-page-load')
    },
    async refreshPage() {
      if (this.refreshing) return
      this.refreshing = true
      try {
        await this.loadData()
        uni.showToast({ title: '已刷新', icon: 'none', duration: 900 })
      } finally {
        this.refreshing = false
      }
    },
    applyMessageState(state = {}) {
      this.unreadTradeCount = Number(state.unreadTradeCount || 0)
      this.loadSessionsFromStore(state)
    },
    loadSessionsFromStore(state = {}) {
      if (Array.isArray(state.sessions)) {
        this.sessions = state.sessions.map(normalizeSession)
      }
    },
    handleSessionAvatarError(session) {
      if (session) session.otherAvatar = ''
    },
    async loadSessions() {
      try {
        const res = await getChatSessions({ current: 1, size: 100 })
        const records = Array.isArray(res?.data?.records) ? res.data.records : []
        this.sessions = records.map(normalizeSession)
      } catch (e) {
        console.error('加载消息列表失败', e)
      }
    },
    async checkAnnouncements() {
      try {
        const res = await getEnabledAnnouncements()
        const list = Array.isArray(res?.data) ? res.data : (Array.isArray(res?.data?.records) ? res.data.records : [])
        const lastSeenId = uni.getStorageSync('marketLastSeenAnnounceId') || 0
        this.unreadAnnounceCount = list.filter(a => (a.id || 0) > lastSeenId).length
      } catch (e) {
        console.error('加载公告失败', e)
      }
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
    async loadTradeRecords() {
      try {
        const res = await getTradeNotificationUnreadCount()
        this.unreadTradeCount = Number(res?.data || 0)
      } catch (e) {
        console.error('加载交易记录失败', e)
      }
    },
    openChat(session) {
      const params = [`sessionId=${session.id}`]
      if (session.otherName) params.push(`otherName=${encodeURIComponent(session.otherName)}`)
      if (session.otherAvatar) params.push(`otherAvatar=${encodeURIComponent(session.otherAvatar)}`)
      uni.navigateTo({
        url: `/subpackage_lostfound/lostfoundChat/lostfoundChat?${params.join('&')}`
      })
    },
    goToNotifications() {
      uni.navigateTo({
        url: '/subpackage_lostfound/marketNotifications/marketNotifications'
      })
    },
    goToTradeNotifications() {
      uni.navigateTo({
        url: '/subpackage_lostfound/marketTradeNotifications/marketTradeNotifications'
      })
    }
  }
}
</script>

<style lang="scss" scoped>
.page-root {
  width: 100%;
  height: 100vh;
  min-height: 100vh;
  background: #F7F8FA;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.screen {
  width: 100%;
  flex: 1;
  min-height: 0;
  background: #F7F8FA;
  overflow: hidden;
}

.container {
  width: 100%;
  max-width: 430px;
  height: 100%;
  margin: 0 auto;
  box-sizing: border-box;
  padding: 0 24rpx;
  background: #F7F8FA;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.page-body {
  flex: 1;
  min-height: 0;
  height: 0;
  overflow-y: auto;
  padding: 20rpx 0 calc(120rpx + env(safe-area-inset-bottom));
  box-sizing: border-box;
  scrollbar-width: none;
  -ms-overflow-style: none;
}

.page-body::-webkit-scrollbar {
  width: 0;
  height: 0;
  display: none;
}

/* 通知卡片 */
.notify-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 28rpx 24rpx;
  background: #fff;
  border-radius: 16rpx;
  margin-bottom: 20rpx;
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.04);
}

.notify-left {
  display: flex;
  align-items: center;
  gap: 20rpx;
}

.notify-icon {
  width: 44rpx;
  height: 44rpx;
  opacity: 0.7;
}

.notify-label {
  font-size: 28rpx;
  font-weight: 600;
  color: #111111;
}

.notify-right {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.notify-badge {
  padding: 4rpx 14rpx;
  border-radius: 999rpx;
  background: #E85D75;
  color: #fff;
  font-size: 20rpx;
  font-weight: 600;
  min-width: 32rpx;
  text-align: center;
}

.notify-arrow {
  font-size: 32rpx;
  color: #C7C7CC;
}

.section-title {
  padding: 8rpx 4rpx 18rpx;
  color: #111111;
  font-size: 28rpx;
  font-weight: 700;
  line-height: 1.2;
}

.chat-section-title {
  display: flex;
  align-items: center;
  gap: 14rpx;
  padding: 10rpx 4rpx 18rpx;
  color: #1D1D1F;
  font-size: 30rpx;
  font-weight: 900;
}

.section-title-mark {
  width: 8rpx;
  height: 34rpx;
  border-radius: 999rpx;
  background: #6F98D0;
  flex-shrink: 0;
}

/* 空状态 */
.empty {
  padding: 120rpx 0;
  text-align: center;
}

.empty-icon {
  display: block;
  width: 120rpx;
  height: 120rpx;
  margin: 0 auto 24rpx;
  opacity: 0.45;
}

.empty-text {
  font-size: 28rpx;
  color: #888888;
}

/* 会话卡片 */
.session-card {
  display: flex;
  align-items: center;
  gap: 24rpx;
  padding: 28rpx 24rpx;
  background: #fff;
  border-radius: 16rpx;
  margin-bottom: 16rpx;
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.04);
}

.sess-avatar {
  width: 88rpx;
  height: 88rpx;
  border-radius: 50%;
  background: #6F98D0;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 34rpx;
  font-weight: 700;
  flex-shrink: 0;
  overflow: hidden;
}

.sess-avatar-img {
  width: 100%;
  height: 100%;
  display: block;
}

.sess-body {
  flex: 1;
  min-width: 0;
}

.sess-top-row {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 6rpx;
}

.sess-name {
  font-size: 28rpx;
  font-weight: 600;
  color: #111111;
  max-width: 300rpx;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sess-time {
  font-size: 22rpx;
  color: #999999;
  flex-shrink: 0;
  margin-left: 16rpx;
}

.sess-mid-row {
  margin-bottom: 6rpx;
}

.sess-product {
  font-size: 24rpx;
  color: #6F98D0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sess-bottom-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.sess-preview {
  font-size: 24rpx;
  color: #999999;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.sess-badge {
  padding: 4rpx 14rpx;
  border-radius: 999rpx;
  background: #E85D75;
  color: #fff;
  font-size: 20rpx;
  font-weight: 600;
  min-width: 32rpx;
  text-align: center;
  flex-shrink: 0;
  margin-left: 16rpx;
}
</style>
