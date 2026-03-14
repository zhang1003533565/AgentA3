<template>
  <view class="page-wrap">
    <nav-bar title="个人中心" :showBack="false" />
    <view class="mine-page">
      <!-- 头部：纯白背景 -->
      <view class="header-block">
        <view class="user-row">
          <image class="avatar" :src="userAvatar" mode="aspectFill" />
          <view class="user-info">
            <text class="user-name">{{ userInfo ? userInfo.username : '未登录' }}</text>
            <text class="user-id">学号 {{ userInfo ? (userInfo.studentId || '—') : '—' }}</text>
          </view>
        </view>
        <view class="stats-row">
          <view class="stat-item" @click="onStatClick('points')">
            <text class="stat-value">{{ stats.points }}</text>
            <text class="stat-label">我的积分</text>
          </view>
          <view class="stat-item" @click="onStatClick('cert')">
            <text class="stat-value">{{ stats.certCount }}</text>
            <text class="stat-label">获得证书</text>
          </view>
          <view class="stat-item" @click="onStatClick('pending')">
            <text class="stat-value">{{ stats.pending }}</text>
            <text class="stat-label">待办申请</text>
          </view>
        </view>
      </view>
      <!-- 12px 灰色隔离条 -->
      <view class="gap-bar"></view>
      <!-- 常用功能：通栏白块 -->
      <view class="menu-block">
        <view class="cell" @click="goToSchedule">
          <view class="cell-left">
            <view class="cell-icon"><icon-line name="calendar" size="cell" /></view>
            <text class="cell-label">我的课表</text>
          </view>
          <text class="cell-arrow">›</text>
        </view>
        <view class="cell-divider"></view>
        <view class="cell" @click="goToGrade">
          <view class="cell-left">
            <view class="cell-icon"><icon-line name="award" size="cell" /></view>
            <text class="cell-label">成绩查询</text>
          </view>
          <text class="cell-arrow">›</text>
        </view>
        <view class="cell-divider"></view>
        <view class="cell" @click="goToMyActivity">
          <view class="cell-left">
            <view class="cell-icon"><icon-line name="compass" size="cell" /></view>
            <text class="cell-label">我的活动</text>
          </view>
          <text class="cell-arrow">›</text>
        </view>
      </view>
      <!-- 12px 灰色隔离条 -->
      <view class="gap-bar"></view>
      <!-- 账号设置：通栏白块 -->
      <view class="menu-block">
        <view class="cell" @click="goToChangePassword">
          <view class="cell-left">
            <view class="cell-icon"><icon-line name="lock" size="cell" /></view>
            <text class="cell-label">修改密码</text>
          </view>
          <text class="cell-arrow">›</text>
        </view>
        <view class="cell-divider"></view>
        <view class="cell" @click="logout">
          <view class="cell-left">
            <view class="cell-icon"><icon-line name="log-out" size="cell" /></view>
            <text class="cell-label">退出登录</text>
          </view>
          <text class="cell-arrow">›</text>
        </view>
      </view>
    </view>
    <custom-tab-bar current="mine" />
  </view>
</template>

<script>
import CustomTabBar from '@/components/custom-tab-bar/custom-tab-bar.vue'
import NavBar from '@/components/nav-bar/nav-bar.vue'
import IconLine from '@/components/icon-line/icon-line.vue'

export default {
  components: { CustomTabBar, NavBar, IconLine },
  data() {
    return {
      userInfo: null,
      userAvatar: '',
      stats: {
        points: 0,
        certCount: 0,
        pending: 0
      }
    }
  },
  onLoad() {
    this.loadUser()
    this.loadStats()
  },
  onShow() {
    this.loadUser()
    this.loadStats()
  },
  methods: {
    loadUser() {
      const userInfoStr = uni.getStorageSync('userInfo')
      if (userInfoStr) {
        this.userInfo = JSON.parse(userInfoStr)
        this.userAvatar = `https://api.dicebear.com/7.x/avataaars/svg?seed=${this.userInfo.username}`
      } else {
        uni.reLaunch({ url: '/pages/login/login' })
      }
    },
    loadStats() {
      // TODO: 接口获取积分、证书数、待办数
      this.stats = { points: 1280, certCount: 3, pending: 2 }
    },
    onStatClick(type) {
      uni.showToast({ title: '功能开发中', icon: 'none' })
    },
    goToSchedule() {
      uni.navigateTo({ url: '/pages/schedule/schedule' })
    },
    goToGrade() {
      uni.navigateTo({ url: '/pages/grade/grade' })
    },
    goToMyActivity() {
      uni.reLaunch({ url: '/pages/activity/activity' })
    },
    goToChangePassword() {
      uni.showToast({ title: '修改密码开发中', icon: 'none' })
    },
    logout() {
      uni.showModal({
        title: '提示',
        content: '确定要退出登录吗？',
        success: (res) => {
          if (res.confirm) {
            uni.removeStorageSync('token')
            uni.removeStorageSync('userInfo')
            uni.reLaunch({ url: '/pages/login/login' })
          }
        }
      })
    }
  }
}
</script>

<style lang="scss" scoped>
.page-wrap {
  min-height: 100vh;
  background-color: #F7F7F9;
  padding-bottom: 120rpx;
}

.mine-page {
  padding: 0;
}

/* ========== 头部：纯白 ========== */
.header-block {
  background-color: #FFFFFF;
  padding: 32rpx 32rpx 40rpx;
}
.user-row {
  display: flex;
  align-items: center;
  margin-bottom: 40rpx;
}
.avatar {
  width: 96rpx;
  height: 96rpx;
  border-radius: 50%;
  border: 1px solid #EEEEEE;
  flex-shrink: 0;
  margin-right: 24rpx;
}
.user-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}
.user-name {
  font-size: 40rpx;
  font-weight: 700;
  color: #1D1D1F;
}
.user-id {
  font-size: 24rpx;
  font-weight: 400;
  color: #8E8E93;
}
.stats-row {
  display: flex;
  align-items: stretch;
  justify-content: space-around;
  padding-top: 32rpx;
  border-top: 1px solid #F2F2F7;
}
.stat-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8rpx;
}
.stat-value {
  font-size: 40rpx;
  font-weight: 700;
  color: #1D1D1F;
}
.stat-label {
  font-size: 24rpx;
  font-weight: 400;
  color: #8E8E93;
}

/* ========== 12px 灰色隔离条 ========== */
.gap-bar {
  height: 24rpx;
  background-color: #F2F2F7;
}

/* ========== 功能列表：通栏白块，Cell 结构 ========== */
.menu-block {
  background-color: #FFFFFF;
}
.cell {
  height: 112rpx;
  padding: 0 32rpx;
  display: flex;
  align-items: center;
  justify-content: space-between;
  box-sizing: border-box;
}
.cell-left {
  display: flex;
  align-items: center;
  min-width: 0;
  flex: 1;
}
.cell-icon {
  width: 40rpx;
  height: 40rpx;
  margin-right: 24rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.cell-label {
  font-size: 28rpx;
  font-weight: 400;
  color: #1D1D1F;
}
.cell-arrow {
  font-size: 32rpx;
  font-weight: 300;
  color: #C7C7CC;
  flex-shrink: 0;
  margin-left: 16rpx;
}
.cell-divider {
  height: 1rpx;
  margin-left: 72rpx;
  margin-right: 0;
  background-color: #E5E5EA;
}
</style>
