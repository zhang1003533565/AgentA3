<template>
  <view class="page-root">
    <common-page-header title="我的" :fixed="true" :placeholder="true" :showBack="true" :autoBack="false" @back="onBackToApp" />

    <scroll-view scroll-y class="page-body">
      <!-- ========== 用户信息卡片 ========== -->
      <view class="user-card">
        <image class="uc-avatar" :src="avatarUrl" mode="aspectFill" />
        <view class="uc-info">
          <text class="uc-name">{{ displayName }}</text>
        </view>
      </view>

      <!-- ========== 我的功能 ========== -->
      <view class="section-header">
        <text class="section-title">我的功能</text>
      </view>
      <view class="menu-block">
        <view class="menu-item" @click="goToMyItems">
          <image class="menu-icon" src="/static/icons/material-symbols-light--publish-rounded.svg" mode="aspectFit" />
          <text class="menu-label">我的发布</text>
          <text class="menu-arrow">›</text>
        </view>
        <view class="menu-divider"></view>
        <view class="menu-item" @click="goToFavorites">
          <image class="menu-icon" src="/static/icons/line/star.svg" mode="aspectFit" />
          <text class="menu-label">我的收藏</text>
          <text class="menu-arrow">›</text>
        </view>
        <view class="menu-divider"></view>
        <view class="menu-item" @click="goToHistory">
          <image class="menu-icon" src="/static/icons/line/compass.svg" mode="aspectFit" />
          <text class="menu-label">浏览记录</text>
          <text class="menu-arrow">›</text>
        </view>
      </view>

      <!-- 底部安全区占位 -->
      <view class="bottom-spacer"></view>
    </scroll-view>

    <market-bottom-bar activeTab="profile" />
  </view>
</template>

<script>
import CommonPageHeader from '@/components/common-page-header/common-page-header.vue'
import MarketBottomBar from '@/components/market-bottom-bar/market-bottom-bar.vue'
import { getUserInfo } from '@/utils/storage.js'

export default {
  components: { CommonPageHeader, MarketBottomBar },
  data() {
    return {
      userInfo: null
    }
  },
  computed: {
    displayName() {
      if (!this.userInfo) return '未登录'
      return this.userInfo.username || '未登录'
    },
    avatarUrl() {
      if (this.userInfo && this.userInfo.avatar) {
        return this.userInfo.avatar
      }
      const seed = this.userInfo
        ? (this.userInfo.realName || this.userInfo.username || this.userInfo.studentId || 'market-user')
        : 'market-user'
      return `https://api.dicebear.com/7.x/avataaars/svg?seed=${encodeURIComponent(seed)}`
    }
  },
  onShow() {
    this.userInfo = getUserInfo()
  },
  methods: {
    onBackToApp() {
      uni.reLaunch({ url: '/pages/index/index' })
    },
    goToMyItems() {
      uni.navigateTo({ url: '/subpackage_lostfound/myItems/myItems' })
    },
    goToFavorites() {
      uni.navigateTo({ url: '/subpackage_lostfound/myFavorites/myFavorites' })
    },
    goToHistory() {
      uni.navigateTo({ url: '/subpackage_lostfound/marketBrowseHistory/marketBrowseHistory' })
    }
  }
}
</script>

<style scoped>
/* ========== Page Layout ========== */
.page-root {
  width: 100%;
  height: 100vh;
  background: #F7F8FA;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.page-body {
  flex: 1;
  min-height: 0;
  width: 100%;
  padding: 24rpx 28rpx 0;
  box-sizing: border-box;
}

/* ========== User Card ========== */
.user-card {
  display: flex;
  align-items: center;
  gap: 34rpx;
  padding: 50rpx 40rpx;
  background: #FFFFFF;
  border-radius: 28rpx;
  margin-bottom: 42rpx;
  box-shadow: 0 22rpx 46rpx rgba(15, 23, 42, 0.08);
}

.uc-avatar {
  width: 150rpx;
  height: 150rpx;
  border-radius: 50%;
  background: #EAF4FF;
  flex-shrink: 0;
}

.uc-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  gap: 16rpx;
}

.uc-name {
  max-width: 360rpx;
  font-size: 44rpx;
  line-height: 1.12;
  font-weight: 800;
  color: #17181A;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

/* ========== Section Header ========== */
.section-header {
  padding: 0 6rpx 20rpx;
}

.section-title {
  font-size: 32rpx;
  line-height: 1.2;
  font-weight: 700;
  color: #2B2D31;
}

/* ========== Menu Block ========== */
.menu-block {
  background: #FFFFFF;
  border-radius: 28rpx;
  overflow: hidden;
  box-shadow: 0 18rpx 42rpx rgba(15, 23, 42, 0.05);
}

.menu-item {
  display: flex;
  align-items: center;
  min-height: 112rpx;
  padding: 0 34rpx;
}

.menu-icon {
  width: 44rpx;
  height: 44rpx;
  margin-right: 30rpx;
  flex-shrink: 0;
  opacity: 0.78;
}

.menu-label {
  flex: 1;
  font-size: 32rpx;
  line-height: 1.2;
  color: #2B2D31;
  font-weight: 500;
}

.menu-arrow {
  font-size: 56rpx;
  color: #9D9FA4;
  font-weight: 200;
  line-height: 1;
  flex-shrink: 0;
}

.menu-divider {
  height: 1rpx;
  margin-left: 96rpx;
  margin-right: 34rpx;
  background: #EDEFF2;
}

/* ========== Bottom Spacer ========== */
.bottom-spacer {
  height: 160rpx;
}
</style>
