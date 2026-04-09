<template>
  <view class="page">
    <nav-bar title="通知公告" :showBack="true" />
    <view class="notice-list">
      <view v-for="item in notices" :key="item.id" class="notice-item" @click="goToDetail(item.id)">
        <view class="notice-top">
          <text class="notice-tag">{{ item.tag }}</text>
          <text class="notice-time">{{ item.time }}</text>
        </view>
        <text class="notice-title">{{ item.title }}</text>
        <text class="notice-content">{{ item.content }}</text>
      </view>
      <view v-if="notices.length === 0" class="empty-state">
        <text class="empty-text">暂无公告</text>
      </view>
    </view>
  </view>
</template>

<script>
import NavBar from '@/components/nav-bar/nav-bar.vue'
import { getEnabledAnnouncements } from '@/api/notice.js'

export default {
  components: { NavBar },
  data() {
    return {
      notices: []
    }
  },
  onLoad() {
    this.fetchNotices()
  },
  onShow() {
    this.fetchNotices()
  },
  methods: {
    async fetchNotices() {
      try {
        const res = await getEnabledAnnouncements()
        if (res.code === 200 && res.data) {
          this.notices = res.data.map(item => ({
            id: item.id,
            tag: '公告',
            time: item.createTime,
            title: item.title,
            content: item.content
          }))
        }
      } catch (err) {
        console.error('获取公告列表失败:', err)
        uni.showToast({ title: '获取公告失败', icon: 'none' })
      }
    },
    goToDetail(id) {
      uni.navigateTo({
        url: `/subpackage_notice/noticeDetail/noticeDetail?id=${id}`
      })
    }
  }
}
</script>

<style lang="scss" scoped>
.page {
  min-height: 100vh;
  background: #f7f7f9;
}

.notice-list {
  padding: 24rpx;
}

.notice-item {
  padding: 28rpx;
  border-radius: 24rpx;
  background: #fff;
}

.notice-item + .notice-item {
  margin-top: 24rpx;
}

.notice-top {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.notice-tag,
.notice-time,
.notice-content {
  font-size: 24rpx;
  color: #6b7280;
}

.notice-title {
  display: block;
  margin-top: 14rpx;
  font-size: 30rpx;
  line-height: 1.5;
  color: #111827;
  font-weight: 700;
}

.notice-content {
  display: block;
  margin-top: 12rpx;
  line-height: 1.7;
}

.empty-state {
  display: flex;
  justify-content: center;
  align-items: center;
  min-height: 400rpx;
}

.empty-text {
  font-size: 28rpx;
  color: #999;
}
</style>
