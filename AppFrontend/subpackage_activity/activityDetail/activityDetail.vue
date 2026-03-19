<template>
  <view class="detail-page">
    <!-- 顶部导航：返回 + 活动详情 + 分享 -->
    <view class="detail-nav" :style="{ paddingTop: statusBarHeight + 'px' }">
      <view class="detail-nav-inner">
        <view class="nav-back" @click.stop="onBack">
          <text class="nav-back-icon">‹</text>
        </view>
        <view class="nav-title-wrap">
          <text class="nav-title">活动详情</text>
        </view>
        <view class="nav-right" @click="onShare">
          <image class="nav-share-icon" src="/static/icons/line/share.svg" mode="aspectFit" />
        </view>
      </view>
    </view>

    <scroll-view class="detail-scroll" scroll-y :style="{ paddingTop: scrollPaddingTop }">
      <!-- 主图 + 状态标签（浮于图上） -->
      <view class="cover-wrap">
        <image class="cover-img" :src="detail.coverImage || detail.image" mode="aspectFill" />
        <view class="status-pill" :class="detail.statusClass">{{ detail.statusText }}</view>
      </view>

      <!-- 标题 + 主办方 白卡 -->
      <view class="card card-header">
        <text class="activity-title">{{ detail.title }}</text>
        <view class="organizer-row">
          <image class="organizer-avatar" :src="detail.organizerAvatar" mode="aspectFill" />
          <view class="organizer-info">
            <text class="organizer-name">{{ detail.organizerName }}</text>
            <text class="organizer-tag">{{ detail.organizerTag }}</text>
          </view>
          <view class="btn-follow" @click="onFollow">{{ detail.followed ? '已关注' : '关注' }}</view>
        </view>
      </view>

      <!-- 关键信息：日期、地点、剩余名额、报名截止 -->
      <view class="card card-meta">
        <view class="meta-row">
          <text class="meta-icon">📅</text>
          <view class="meta-content">
            <text class="meta-label">活动日期</text>
            <text class="meta-value">{{ detail.activityDate }}</text>
          </view>
        </view>
        <view class="meta-row">
          <text class="meta-icon">📍</text>
          <view class="meta-content">
            <text class="meta-label">地点</text>
            <text class="meta-value">{{ detail.location }}</text>
          </view>
        </view>
        <view class="meta-row">
          <text class="meta-icon">👥</text>
          <view class="meta-content">
            <text class="meta-label">剩余名额</text>
            <text class="meta-value">{{ detail.remainingSpots }}</text>
          </view>
        </view>
        <view class="meta-row">
          <text class="meta-icon">🕐</text>
          <view class="meta-content">
            <text class="meta-label">报名截止日期</text>
            <text class="meta-value">{{ detail.deadline }}</text>
          </view>
        </view>
      </view>

      <!-- 活动详情 白卡 -->
      <view class="card card-desc">
        <text class="card-heading">活动详情</text>
        <text class="desc-text">{{ detail.description }}</text>
      </view>

      <!-- 注意事项 白卡 -->
      <view class="card card-notes">
        <text class="card-heading">注意事项</text>
        <view class="notes-list">
          <text class="notes-item" v-for="(item, i) in detail.notes" :key="i">· {{ item }}</text>
        </view>
      </view>

      <view class="bottom-spacer"></view>
    </scroll-view>

    <!-- 底部固定栏：收藏、咨询、立即报名 -->
    <view class="footer-bar">
      <view class="footer-left">
        <view class="footer-btn" @click="onCollect">
          <text class="footer-btn-icon">{{ detail.collected ? '★' : '☆' }}</text>
          <text class="footer-btn-text">收藏</text>
        </view>
        <view class="footer-btn" @click="onInquire">
          <text class="footer-btn-icon">💬</text>
          <text class="footer-btn-text">咨询</text>
        </view>
      </view>
      <view class="btn-register" @click="onRegister">立即报名</view>
    </view>
  </view>
</template>

<script>
import { getActivityDetail, addFavorite, removeFavorite, checkFavoriteStatus } from '@/api/activity.js'
export default {
  data() {
    return {
      statusBarHeight: 20,
      id: '',
      detail: {
        title: '',
        coverImage: '',
        statusText: '',
        statusClass: '',
        organizerAvatar: '',
        organizerName: '',
        organizerTag: '',
        followed: false,
        activityDate: '',
        location: '',
        remainingSpots: '',
        deadline: '',
        description: '',
        notes: [],
        collected: false
      }
    }
  },
  computed: {
    scrollPaddingTop() {
      const bar = 48
      return (this.statusBarHeight + bar) + 'px'
    }
  },
  onLoad(options) {
    try {
      const sys = uni.getSystemInfoSync()
      this.statusBarHeight = sys.statusBarHeight || 20
    } catch (e) {}
    this.id = options.id || ''
    this.loadDetail()
  },
  methods: {
    async loadDetail() {
      if (!this.id) return
      try {
        const res = await getActivityDetail(this.id)
        const data = (res && res.data) ? res.data : {}

        const statusMap = {
          DRAFT: { text: '草稿', cls: 'status-default' },
          PUBLISHED: { text: '进行中', cls: 'status-ongoing' },
          COMPLETED: { text: '已结束', cls: 'status-ended' }
        }
        const status = statusMap[data.status] || { text: '未知', cls: 'status-default' }

        this.detail = {
          ...this.detail,
          title: data.title || '',
          coverImage: data.coverImage || '',
          statusText: status.text,
          statusClass: status.cls,
          organizerName: data.organizerName || '',
          organizerTag: '官方认证校园机构',
          activityDate: this.formatRangeTime(data.startTime, data.endTime),
          location: data.location || '',
          remainingSpots: `${data.currentPeople || 0}/${data.maxPeople || 0}`,
          deadline: this.formatTime(data.signupEndTime),
          description: data.content || '',
          notes: [],
          collected: false
        }

        await this.loadFavoriteStatus()
      } catch (e) {
        // request.js 会统一 toast，这里不重复提示
      }
    },
    async loadFavoriteStatus() {
      if (!this.id) return
      try {
        const res = await checkFavoriteStatus(this.id)
        this.detail.collected = !!(res && res.data)
      } catch (e) {}
    },
    onBack() {
      const pages = getCurrentPages()
      const canBack = pages && pages.length > 1
      if (canBack) {
        uni.navigateBack({ delta: 1 })
      } else {
        // 无历史栈时直接 reLaunch 到活动列表，避免 navigateBack 失败或触发 switchTab 报错
        uni.reLaunch({ url: '/subpackage_activity/activityList/activityList' })
      }
    },
    onShare() {
      uni.showToast({ title: '分享', icon: 'none' })
    },
    onFollow() {
      this.detail.followed = !this.detail.followed
      uni.showToast({ title: this.detail.followed ? '已关注' : '已取消关注', icon: 'none' })
    },
    async onCollect() {
      if (!this.id) return
      const next = !this.detail.collected
      try {
        if (next) {
          await addFavorite(this.id)
        } else {
          await removeFavorite(this.id)
        }
        this.detail.collected = next
        uni.showToast({ title: next ? '已收藏' : '已取消收藏', icon: 'none' })
      } catch (e) {}
    },
    onInquire() {
      uni.showToast({ title: '咨询', icon: 'none' })
    },
    onRegister() {
      uni.showToast({ title: '报名成功', icon: 'success' })
    },
    formatTime(time) {
      if (!time) return ''
      try {
        return time.substring(0, 16).replace('T', ' ')
      } catch (e) {
        return ''
      }
    },
    formatRangeTime(startTime, endTime) {
      const start = this.formatTime(startTime)
      const end = this.formatTime(endTime)
      if (start && end) return `${start} - ${end}`
      return start || end || ''
    }
  }
}
</script>

<style lang="scss" scoped>
.detail-page {
  min-height: 100vh;
  background-color: #F7F7F9;
  padding-bottom: 140rpx;
}

/* 顶部导航：与 nav-bar 统一，右侧分享 */
.detail-nav {
  background-color: #FFFFFF;
  border-bottom: 1px solid #E2E8F0;
  padding-left: 32rpx;
  padding-right: 32rpx;
  padding-bottom: 8rpx;
  position: fixed;
  left: 0;
  right: 0;
  top: 0;
  z-index: 100;
}
.detail-nav-inner {
  position: relative;
  height: 88rpx;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.nav-back,
.nav-right {
  width: 88rpx;
  height: 88rpx;
  display: flex;
  align-items: center;
  justify-content: flex-start;
  flex-shrink: 0;
  position: relative;
  z-index: 1;
}
.nav-right {
  justify-content: flex-end;
}
.nav-back-icon {
  font-size: 48rpx;
  color: #1D1D1F;
  line-height: 1;
  font-weight: 400;
}
.nav-title-wrap {
  position: absolute;
  left: 0;
  right: 0;
  top: 0;
  bottom: 0;
  display: flex;
  justify-content: center;
  align-items: center;
  pointer-events: none;
}
.nav-title {
  font-size: 36rpx;
  font-weight: 700;
  color: #1D1D1F;
  letter-spacing: -0.03em;
  line-height: 88rpx;
}
.nav-share-icon {
  width: 40rpx;
  height: 40rpx;
  color: #111827;
}

.detail-scroll {
  height: 100vh;
}

/* 主图 + 状态标签 */
.cover-wrap {
  position: relative;
  width: 100%;
  height: 420rpx;
  background-color: #E8E8EC;
}
.cover-img {
  width: 100%;
  height: 100%;
  display: block;
}
.status-pill {
  position: absolute;
  left: 32rpx;
  top: 24rpx;
  padding: 10rpx 24rpx;
  border-radius: 999rpx;
  font-size: 24rpx;
  font-weight: 600;
  color: #FFFFFF;
  background-color: $color-primary;
}
.status-pill.status-ongoing {
  background-color: $color-primary;
}
.status-pill.status-signup {
  background-color: $color-primary;
}
.status-pill.status-ended {
  background-color: #8E8E93;
}

/* 白卡通用 */
.card {
  background-color: #FFFFFF;
  border-radius: 24rpx;
  margin: 24rpx 32rpx;
  padding: 32rpx;
  border: none;
}
.card-header {
  margin-top: -40rpx;
  position: relative;
  z-index: 1;
}
.activity-title {
  font-size: 40rpx;
  font-weight: 800;
  color: #1D1D1F;
  line-height: 1.35;
  display: block;
  margin-bottom: 24rpx;
}
.organizer-row {
  display: flex;
  align-items: center;
  gap: 24rpx;
}
.organizer-avatar {
  width: 80rpx;
  height: 80rpx;
  border-radius: 50%;
  background-color: #F2F2F2;
  flex-shrink: 0;
}
.organizer-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 6rpx;
}
.organizer-name {
  font-size: 28rpx;
  font-weight: 600;
  color: #1D1D1F;
}
.organizer-tag {
  font-size: 24rpx;
  color: #8E8E93;
}
.btn-follow {
  padding: 12rpx 28rpx;
  border-radius: 32rpx;
  font-size: 24rpx;
  font-weight: 600;
  color: #FFFFFF;
  background-color: $color-primary;
  flex-shrink: 0;
}

.card-meta {
  display: flex;
  flex-direction: column;
  gap: 24rpx;
}
.meta-row {
  display: flex;
  align-items: flex-start;
  gap: 20rpx;
}
.meta-icon {
  font-size: 32rpx;
  flex-shrink: 0;
}
.meta-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}
.meta-label {
  font-size: 24rpx;
  color: #8E8E93;
}
.meta-value {
  font-size: 28rpx;
  font-weight: 500;
  color: #1D1D1F;
}

.card-heading {
  font-size: 32rpx;
  font-weight: 700;
  color: #1D1D1F;
  display: block;
  margin-bottom: 20rpx;
}
.desc-text {
  font-size: 28rpx;
  color: #4A4A4A;
  line-height: 1.6;
  white-space: pre-wrap;
}
.notes-list {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
}
.notes-item {
  font-size: 28rpx;
  color: #4A4A4A;
  line-height: 1.5;
}

.bottom-spacer {
  height: 40rpx;
}

/* 底部固定栏 */
.footer-bar {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  height: 120rpx;
  padding-bottom: env(safe-area-inset-bottom);
  padding-left: 32rpx;
  padding-right: 32rpx;
  padding-top: 20rpx;
  background-color: #FFFFFF;
  border-top: 1px solid #E2E8F0;
  display: flex;
  align-items: center;
  justify-content: space-between;
  z-index: 99;
}
.footer-left {
  display: flex;
  align-items: center;
  gap: 48rpx;
}
.footer-btn {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 4rpx;
}
.footer-btn-icon {
  font-size: 40rpx;
}
.footer-btn-text {
  font-size: 22rpx;
  color: #8E8E93;
}
.btn-register {
  min-width: 280rpx;
  height: 80rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 40rpx;
  font-size: 32rpx;
  font-weight: 700;
  color: #FFFFFF;
  background-color: $color-primary;
}
</style>
