<template>
  <view class="detail-page">
    <!-- 顶部导航：返回 + 活动详情 + 分享 -->
    <view class="detail-nav" :style="{ paddingTop: statusBarHeight + 'px' }">
      <view class="detail-nav-inner">
        <view class="nav-back" @click.stop="onBack">
          <view class="nav-btn-inner">
            <text class="nav-back-icon">‹</text>
          </view>
        </view>
        <view class="nav-title-wrap">
          <text class="nav-title">活动详情</text>
        </view>
        <view class="nav-right" @click="onShare">
          <view class="nav-btn-inner nav-btn-share">
            <image class="nav-share-icon" src="/static/icons/icon-forward.svg" mode="aspectFit" />
          </view>
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
export default {
  data() {
    return {
      statusBarHeight: 20,
      id: '',
      detail: {
        title: '首届校园草地音乐节：夏日回响',
        coverImage: 'https://picsum.photos/seed/lawn/800/450',
        statusText: '进行中',
        statusClass: 'status-ongoing',
        organizerAvatar: 'https://api.dicebear.com/7.x/identicon/svg?seed=arts',
        organizerName: '学生会文艺部',
        organizerTag: '官方认证校园机构',
        followed: false,
        activityDate: '2023年10月25日 19:00',
        location: '校本部中心草坪',
        remainingSpots: '42/200',
        deadline: '10月23日 23:59',
        description: '这是一场为全体师生准备的视听盛宴，让我们共同见证音乐的力量。\n\n汇聚校内10支知名乐队，涵盖流行、民谣、摇滚、电子等多种风格；创意市集与美食补给站，打造沉浸式校园文化体验。',
        notes: [
          '请务必携带学生证入场',
          '为了环保，请尽量自带水杯',
          '如遇天气原因，活动将延期或移至室内礼堂'
        ],
        collected: false
      }
    }
  },
  computed: {
    scrollPaddingTop() {
      const bar = 34
      return (this.statusBarHeight + bar) + 'px'
    }
  },
  onLoad(options) {
    try {
      const sys = uni.getSystemInfoSync()
      this.statusBarHeight = sys.statusBarHeight || 20
    } catch (e) {}
    this.id = options.id || ''
    // TODO: 根据 this.id 请求详情接口，替换 detail
  },
  methods: {
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
    onCollect() {
      this.detail.collected = !this.detail.collected
      uni.showToast({ title: this.detail.collected ? '已收藏' : '已取消收藏', icon: 'none' })
    },
    onInquire() {
      uni.showToast({ title: '咨询', icon: 'none' })
    },
    onRegister() {
      uni.showToast({ title: '报名成功', icon: 'success' })
    }
  }
}
</script>

<style lang="scss" scoped>
@import "../../theme.scss";

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
  height: 60rpx;
  display: flex;
  align-items: center;
}
.nav-back,
.nav-right {
  display: flex;
  align-items: center;
  justify-content: center;
  margin-left: -16rpx;
}
.nav-right {
  margin-left: 0;
  margin-right: -16rpx;
}
.nav-btn-inner {
  width: 64rpx;
  height: 64rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}
.nav-back-icon {
  font-size: 40rpx;
  color: #1D1D1F;
  line-height: 1;
  font-weight: 600;
  margin-left: -4rpx;
}
.nav-btn-share .nav-share-icon {
  width: 36rpx;
  height: 36rpx;
}
.nav-title-wrap {
  flex: 1;
  min-width: 0;
  display: flex;
  justify-content: center;
  align-items: center;
}
.nav-title {
  font-size: 36rpx;
  font-weight: 700;
  color: #1D1D1F;
  letter-spacing: -0.03em;
}
.nav-share-icon {
  width: 36rpx;
  height: 36rpx;
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
