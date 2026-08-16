<template>
  <view class="page-root">
    <view class="screen">
      <view class="container">
        <common-page-header title="市集消息" :fixed="true" :placeholder="true" :showBack="true" />
        
        <scroll-view
          scroll-y
          class="page-body"
          refresher-enabled
          :refresher-triggered="refreshing"
          refresher-background="#E8F0F8"
          @refresherrefresh="refreshPage"
        >
          <view class="section-title">通知</view>
          <view class="notify-card" @click="openSystemNotifications">
            <view class="notify-icon system">系</view>
            <view class="notify-body">
              <view class="notify-name">系统通知</view>
              <view class="notify-desc">查看校园公告和市集系统消息</view>
            </view>
          </view>
          <view class="notify-card notify-card-spaced" @click="openTradeNotifications">
            <view class="notify-icon">交</view>
            <view class="notify-body">
              <view class="notify-name">交易通知</view>
              <view class="notify-desc">查看购买意向、交易确认、联系方式和完成消息</view>
            </view>
            <view v-if="tradeUnread" class="msbadge">{{ tradeUnread }}</view>
          </view>

          <view class="section-title chat-title">聊天</view>
          <view v-if="chats.length === 0" class="empty">
            <image class="empty-icon" src="/static/icons/message-empty.svg" mode="aspectFit" />
            <view class="empty-t">暂无消息</view>
          </view>
          <view v-for="c in chats" :key="c.id" class="mscard" @click="openChat(c)">
            <view class="msava">
              <image
                v-if="c.otherAvatar"
                class="msava-img"
                :src="c.otherAvatar"
                mode="aspectFill"
                @error="handleChatAvatarError(c)"
              />
              <text v-else>{{ c.otherName[0] }}</text>
            </view>
            <view class="msbody">
              <view class="msname">{{ c.otherName }}</view>
              <view class="msitem">{{ c.itemTitle }}<text v-if="c.statusText"> · {{ c.statusText }}</text></view>
              <view class="msprev">{{ c.lastMsg }}</view>
            </view>
            <view class="msmeta">
              <view class="mstime">{{ fmt(c.lastTime) }}</view>
              <view v-if="c.unread" class="msbadge">{{ c.unread }}</view>
            </view>
          </view>
        </scroll-view>
        <market-bottom-bar activeTab="messages" />
      </view>
    </view>
  </view>
</template>

<script>
import CommonPageHeader from '@/components/common-page-header/common-page-header.vue'
import MarketBottomBar from '@/components/market-bottom-bar/market-bottom-bar.vue'
import { getMessageState, refreshChatListState, subscribeMessageStore } from '@/utils/messageStore'
import { buildDefaultAvatar, pickOtherAvatar } from '@/subpackage_lostfound/utils/avatar.js'

function normalizeSession(item) {
  const otherName = item.otherUsername || item.sellerName || '用户'
  return {
    id: item.sessionId,
    itemTitle: item.itemTitle || '商品',
    otherName,
    otherAvatar: pickOtherAvatar(item) || buildDefaultAvatar({ username: otherName || 'chat-other' }),
    lastMsg: item.lastMessage || '暂无消息',
    lastTime: item.lastTime || '',
    unread: item.unreadCount || 0,
    statusText: item.tradeStatusText || item.itemStatusText || ''
  }
}

export default {
  components: {
    CommonPageHeader,
    MarketBottomBar
  },
  data() {
    return {
      chats: [],
      tradeUnread: 0,
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
  },
  async onShow() {
    await refreshChatListState('my-messages-show')
  },
  onUnload() {
    if (this.unsubscribeMessageStore) {
      this.unsubscribeMessageStore()
      this.unsubscribeMessageStore = null
    }
  },
  methods: {
    async refreshPage() {
      if (this.refreshing) return
      this.refreshing = true
      try {
        await refreshChatListState('manual-refresh')
        uni.showToast({ title: '已刷新', icon: 'none', duration: 900 })
      } finally {
        this.refreshing = false
      }
    },
    applyMessageState(state = {}) {
      this.tradeUnread = Number(state.unreadTradeCount || 0)
      this.loadSessionsFromStore(state)
    },
    loadSessionsFromStore(state = {}) {
      if (Array.isArray(state.sessions)) {
        this.chats = state.sessions.map(normalizeSession)
      }
    },
    handleChatAvatarError(chat) {
      if (chat) chat.otherAvatar = ''
    },
    fmt(ts) {
      const d = new Date(String(ts).replace(/-/g, '/'))
      const now = new Date()
      const diff = now - d
      if (diff < 60000) return '刚刚'
      if (diff < 3600000) return Math.floor(diff / 60000) + '分钟前'
      if (diff < 86400000) return Math.floor(diff / 3600000) + '小时前'
      return `${d.getMonth() + 1}/${d.getDate()}`
    },
    openChat(session) {
      const params = [`sessionId=${session.id}`]
      if (session.otherName) params.push(`otherName=${encodeURIComponent(session.otherName)}`)
      if (session.otherAvatar) params.push(`otherAvatar=${encodeURIComponent(session.otherAvatar)}`)
      uni.navigateTo({
        url: `/subpackage_lostfound/lostfoundChat/lostfoundChat?${params.join('&')}`
      })
    },
    openSystemNotifications() {
      uni.navigateTo({
        url: '/subpackage_lostfound/marketNotifications/marketNotifications'
      })
    },
    openTradeNotifications() {
      uni.navigateTo({
        url: '/subpackage_lostfound/marketTradeNotifications/marketTradeNotifications'
      })
    }
  }
}
</script>

<style lang="scss">
.page-root {
  width: 100%;
  height: 100vh;
  min-height: 100vh;
  background: #F0F5FA;
  overflow: hidden;
}

.screen {
  width: 100%;
  background: #F0F5FA;
  height: 100vh;
  min-height: 100vh;
  overflow: hidden;
}

.container {
  width: 100%;
  max-width: 430px;
  height: 100vh;
  margin: 0 auto;
  box-sizing: border-box;
  padding: 0 16rpx;
  background: #E8F0F8;
  min-height: 100vh;
  position: relative;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.page {
  width: 100%;
  min-height: 100vh;
  box-sizing: border-box;
}

.page-body {
  flex: 1;
  min-height: 0;
  height: 0;
  overflow-y: auto;
  padding: 20rpx 0 150rpx;
}

.section-title {
  padding: 8rpx 8rpx 14rpx;
  font-size: 24rpx;
  font-weight: 800;
  color: #5c7894;
}

.chat-title {
  margin-top: 22rpx;
}

.notify-card {
  display: flex;
  align-items: center;
  gap: 22rpx;
  padding: 28rpx 24rpx;
  background: #fff;
  border-radius: 20rpx;
  box-shadow: 0 4rpx 16rpx rgba(0, 0, 0, 0.06);
}

.notify-icon {
  width: 80rpx;
  height: 80rpx;
  border-radius: 22rpx;
  background: #eaf4ef;
  color: #2f8a58;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 30rpx;
  font-weight: 900;
  flex-shrink: 0;
}

.notify-body {
  flex: 1;
  min-width: 0;
}

.notify-name {
  font-size: 28rpx;
  font-weight: 700;
  color: #2c3e50;
  margin-bottom: 8rpx;
}

.notify-desc {
  font-size: 23rpx;
  color: #8aa1b2;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

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

.empty-t {
  font-size: 28rpx;
  color: #9ab0c0;
}

.mscard {
  display: flex;
  align-items: center;
  gap: 24rpx;
  padding: 28rpx 24rpx;
  background: #fff;
  border-radius: 20rpx;
  margin-bottom: 16rpx;
  box-shadow: 0 4rpx 16rpx rgba(0, 0, 0, 0.06);
}

.msava {
  width: 80rpx;
  height: 80rpx;
  border-radius: 50%;
  background: #7ba8d4;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 32rpx;
  font-weight: 700;
  flex-shrink: 0;
  overflow: hidden;
}

.msava-img {
  width: 100%;
  height: 100%;
  display: block;
}

.msbody {
  flex: 1;
  min-width: 0;
}

.msname {
  font-size: 28rpx;
  font-weight: 600;
  color: #2c3e50;
  margin-bottom: 4rpx;
}

.msitem {
  font-size: 22rpx;
  color: #5c8ab8;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  margin-bottom: 6rpx;
}

.msprev {
  font-size: 24rpx;
  color: #9ab0c0;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.msmeta {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  gap: 8rpx;
}

.mstime {
  font-size: 22rpx;
  color: #9ab0c0;
}

.msbadge {
  padding: 4rpx 16rpx;
  border-radius: 999rpx;
  background: #e74c3c;
  color: #fff;
  font-size: 20rpx;
  font-weight: 600;
}

.notify-card-spaced {
  margin-top: 16rpx;
}

.notify-icon.system {
  background: #eef3fb;
  color: #5c8ab8;
}
</style>
