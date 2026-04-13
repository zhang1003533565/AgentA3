<template>
  <view class="page-root">
    <view class="screen">
      <view class="container">
        <nav-bar title="我的消息" :fixed="true" :placeholder="true" />
        
        <scroll-view scroll-y class="page-body">
          <view v-if="chats.length === 0" class="empty">
            <view class="empty-i">💬</view>
            <view class="empty-t">暂无消息</view>
          </view>
          <view v-for="c in chats" :key="c.id" class="mscard" @click="openChat(c.id)">
            <view class="msava">{{ c.otherName[0] }}</view>
            <view class="msbody">
              <view class="msname">{{ c.otherName }}</view>
              <view class="msprev">{{ c.lastMsg }}</view>
            </view>
            <view class="msmeta">
              <view class="mstime">{{ fmt(c.lastTime) }}</view>
              <view v-if="c.unread" class="msbadge">{{ c.unread }}</view>
            </view>
          </view>
        </scroll-view>
      </view>
    </view>
  </view>
</template>

<script>
import NavBar from '@/components/nav-bar/nav-bar.vue'
import { getChatSessions } from '@/api/secondhand'

function normalizeSession(item) {
  return {
    id: item.sessionId,
    otherName: item.otherUsername || item.sellerName || '用户',
    lastMsg: item.lastMessage || '暂无消息',
    lastTime: item.lastTime || '',
    unread: item.unreadCount || 0
  }
}

export default {
  components: {
    NavBar
  },
  data() {
    return {
      chats: []
    }
  },
  async onLoad() {
    await this.loadSessions()
  },
  async onShow() {
    await this.loadSessions()
  },
  methods: {
    async loadSessions() {
      try {
        const res = await getChatSessions({ current: 1, size: 100 })
        const records = Array.isArray(res?.data?.records) ? res.data.records : []
        this.chats = records.map(normalizeSession)
      } catch (e) {
        console.error('加载数据失败', e)
      }
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
    openChat(id) {
      uni.navigateTo({
        url: `/subpackage_lostfound/lostfoundChat/lostfoundChat?sessionId=${id}`
      })
    }
  }
}
</script>

<style lang="scss">
.page-root {
  width: 100%;
  min-height: 100vh;
  background: #F0F5FA;
}

.screen {
  width: 100%;
  background: #F0F5FA;
  min-height: 100vh;
}

.container {
  width: 100%;
  max-width: 430px;
  margin: 0 auto;
  box-sizing: border-box;
  padding: 0 16rpx;
  background: #E8F0F8;
  min-height: 100vh;
  position: relative;
}

.page {
  width: 100%;
  min-height: 100vh;
  box-sizing: border-box;
}

.page-body {
  flex: 1;
  overflow-y: auto;
  padding: 20rpx 0;
}

.empty {
  padding: 120rpx 0;
  text-align: center;
}

.empty-i {
  font-size: 80rpx;
  margin-bottom: 24rpx;
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
}

.msbody {
  flex: 1;
  min-width: 0;
}

.msname {
  font-size: 28rpx;
  font-weight: 600;
  color: #2c3e50;
  margin-bottom: 8rpx;
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
</style>
