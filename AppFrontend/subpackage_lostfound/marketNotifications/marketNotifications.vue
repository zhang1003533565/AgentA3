<template>
  <view class="page-root">
    <nav-bar title="系统通知" :fixed="true" :placeholder="true" :showBack="false">
      <template #left>
        <view class="header-back" @click="goBack">
          <text class="header-back-icon">‹</text>
        </view>
      </template>
    </nav-bar>

    <scroll-view scroll-y class="page-body">
      <view v-if="announcements.length === 0" class="empty">
        <image class="empty-icon" src="/static/icons/bell-off.svg" mode="aspectFit" />
        <text class="empty-text">暂无通知</text>
      </view>
      <view
        v-for="item in announcements"
        :key="item.id"
        class="notify-item"
        @click="goToDetail(item.id)"
      >
        <view class="ni-top">
          <text class="ni-tag">公告</text>
          <text class="ni-time">{{ formatTime(item.createTime) }}</text>
        </view>
        <text class="ni-title">{{ item.title }}</text>
        <text class="ni-preview">{{ item.content }}</text>
      </view>
    </scroll-view>

    <market-bottom-bar activeTab="messages" />
  </view>
</template>

<script>
import NavBar from '@/components/nav-bar/nav-bar.vue'
import MarketBottomBar from '@/components/market-bottom-bar/market-bottom-bar.vue'
import { getEnabledAnnouncements } from '@/api/notice'

export default {
  components: { NavBar, MarketBottomBar },
  data() {
    return {
      announcements: []
    }
  },
  async onLoad() {
    await this.loadAnnouncements()
  },
  methods: {
    async loadAnnouncements() {
      try {
        const res = await getEnabledAnnouncements()
        const list = Array.isArray(res?.data) ? res.data : (Array.isArray(res?.data?.records) ? res.data.records : [])
        this.announcements = list
        // 标记全部已读
        if (list.length > 0) {
          const maxId = Math.max(...list.map(a => a.id || 0))
          uni.setStorageSync('marketLastSeenAnnounceId', maxId)
        }
      } catch (e) {
        console.error('加载公告失败', e)
      }
    },
    formatTime(ts) {
      if (!ts) return ''
      const d = new Date(String(ts).replace(/-/g, '/'))
      const m = d.getMonth() + 1
      const day = d.getDate()
      const h = String(d.getHours()).padStart(2, '0')
      const min = String(d.getMinutes()).padStart(2, '0')
      return `${m}/${day} ${h}:${min}`
    },
    goToDetail(id) {
      uni.navigateTo({
        url: `/subpackage_notice/noticeDetail/noticeDetail?id=${id}`
      })
    },
    goBack() {
      uni.navigateBack({ delta: 1 })
    }
  }
}
</script>

<style lang="scss" scoped>
.page-root {
  width: 100%;
  min-height: 100vh;
  background: #F5F5F5;
  padding-bottom: 130rpx;
}

.page-body {
  width: 100%;
  overflow-y: auto;
  padding: 20rpx 24rpx;
  box-sizing: border-box;
}

/* 返回按钮（与首页一致） */
.header-back {
  width: 60rpx;
  height: 60rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-left: -8rpx;
  margin-right: 4rpx;
}

.header-back-icon {
  font-size: 56rpx;
  font-weight: 300;
  color: #111111;
  line-height: 1;
}

/* 空状态 */
.empty {
  padding: 200rpx 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.empty-icon {
  width: 100rpx;
  height: 100rpx;
  margin-bottom: 24rpx;
  opacity: 0.3;
}

.empty-text {
  font-size: 28rpx;
  color: #888888;
}

/* 通知卡片 */
.notify-item {
  padding: 28rpx 24rpx;
  background: #fff;
  border-radius: 16rpx;
  margin-bottom: 16rpx;
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.04);
}

.ni-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12rpx;
}

.ni-tag {
  font-size: 20rpx;
  color: #6F98D0;
  padding: 4rpx 14rpx;
  border-radius: 999rpx;
  background: rgba(111, 152, 208, 0.1);
}

.ni-time {
  font-size: 22rpx;
  color: #999999;
}

.ni-title {
  font-size: 28rpx;
  font-weight: 600;
  color: #111111;
  display: block;
  margin-bottom: 8rpx;
}

.ni-preview {
  font-size: 24rpx;
  color: #888888;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  line-height: 1.5;
}
</style>
