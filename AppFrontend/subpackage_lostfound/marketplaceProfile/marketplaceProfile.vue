<template>
  <view class="page-root">
    <nav-bar title="我的" :fixed="true" :placeholder="true" :showBack="false" />

    <scroll-view scroll-y class="page-body">
      <!-- ========== 用户信息卡片 ========== -->
      <view class="user-card">
        <image class="uc-avatar" :src="avatarUrl" mode="aspectFill" />
        <view class="uc-info">
          <view class="uc-name-row">
            <text class="uc-name">{{ displayName }}</text>
            <view v-if="isVerified" class="uc-badge">
              <text>已认证</text>
            </view>
          </view>
          <text class="uc-school">{{ schoolName || '未设置' }}</text>
          <text class="uc-student-id">学号 {{ studentId || '未设置' }}</text>
        </view>
      </view>

      <!-- ========== 数据统计 ========== -->
      <view class="stats-row">
        <view class="stat-item">
          <text class="stat-num">{{ stats.myItems }}</text>
          <text class="stat-label">我的发布</text>
        </view>
        <view class="stat-divider"></view>
        <view class="stat-item">
          <text class="stat-num">{{ stats.myFavorites }}</text>
          <text class="stat-label">我的收藏</text>
        </view>
        <view class="stat-divider"></view>
        <view class="stat-item">
          <text class="stat-num">{{ stats.completedTrades }}</text>
          <text class="stat-label">成交数量</text>
        </view>
      </view>

      <!-- ========== 交易管理 ========== -->
      <view class="section-header">
        <text class="section-title">交易管理</text>
      </view>
      <view class="menu-block">
        <view class="menu-item" @click="goToMyItems">
          <image class="menu-icon" src="/static/icons/publish.svg" mode="aspectFit" />
          <text class="menu-label">我的发布</text>
          <text class="menu-arrow">›</text>
        </view>
        <view class="menu-divider"></view>
        <view class="menu-item" @click="goToMyPurchases">
          <image class="menu-icon" src="/static/icons/line/credit-card.svg" mode="aspectFit" />
          <text class="menu-label">我的购买</text>
          <text class="menu-arrow">›</text>
        </view>
        <view class="menu-divider"></view>
        <view class="menu-item" @click="goToTradeRecords">
          <image class="menu-icon" src="/static/icons/line/clipboard.svg" mode="aspectFit" />
          <text class="menu-label">交易记录</text>
          <text class="menu-arrow">›</text>
        </view>
      </view>

      <!-- ========== 其他 ========== -->
      <view class="section-header">
        <text class="section-title">其他</text>
      </view>
      <view class="menu-block">
        <view class="menu-item" @click="goToFavorites">
          <image class="menu-icon" src="/static/icons/line/award.svg" mode="aspectFit" />
          <text class="menu-label">我的收藏</text>
          <text class="menu-arrow">›</text>
        </view>
        <view class="menu-divider"></view>
        <view class="menu-item" @click="goToHistory">
          <image class="menu-icon" src="/static/icons/line/compass.svg" mode="aspectFit" />
          <text class="menu-label">浏览记录</text>
          <text class="menu-arrow">›</text>
        </view>
        <view class="menu-divider"></view>
        <view class="menu-item" @click="goToSettings">
          <image class="menu-icon" src="/static/icons/line/tool.svg" mode="aspectFit" />
          <text class="menu-label">设置</text>
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
import NavBar from '@/components/nav-bar/nav-bar.vue'
import MarketBottomBar from '@/components/market-bottom-bar/market-bottom-bar.vue'
import { getUserInfo, getToken } from '@/utils/storage.js'
import { getMySecondhandItems, getMyFavorites, getTradeRecords } from '@/api/secondhand'

export default {
  components: { NavBar, MarketBottomBar },
  data() {
    return {
      userInfo: null,
      stats: {
        myItems: 0,
        myFavorites: 0,
        completedTrades: 0
      }
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
    },
    schoolName() {
      if (!this.userInfo) return ''
      return this.userInfo.college || ''
    },
    studentId() {
      if (!this.userInfo) return ''
      return this.userInfo.studentId || ''
    },
    isVerified() {
      if (!this.userInfo) return false
      return !!(this.userInfo.studentId || this.userInfo.personalNumber)
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
      this.userInfo = getUserInfo()
    },
    async loadStats() {
      try {
        const [itemsRes, favRes, tradeRes] = await Promise.all([
          getMySecondhandItems({ current: 1, size: 1 }),
          getMyFavorites({ current: 1, size: 1 }),
          getTradeRecords({ current: 1, size: 100 })
        ])
        this.stats.myItems = itemsRes?.data?.total ?? 0
        this.stats.myFavorites = favRes?.data?.total ?? 0
        const records = Array.isArray(tradeRes?.data?.records)
          ? tradeRes.data.records
          : []
        this.stats.completedTrades = records.filter(r => r.status === 'COMPLETED').length
      } catch (e) {
        console.error('加载统计数据失败', e)
      }
    },
    goToMyItems() {
      uni.navigateTo({ url: '/subpackage_lostfound/myItems/myItems' })
    },
    goToMyPurchases() {
      uni.navigateTo({ url: '/subpackage_lostfound/myPurchases/myPurchases' })
    },
    goToTradeRecords() {
      uni.navigateTo({ url: '/subpackage_lostfound/marketTradeRecords/marketTradeRecords' })
    },
    goToFavorites() {
      uni.navigateTo({ url: '/subpackage_lostfound/myFavorites/myFavorites' })
    },
    goToHistory() {
      uni.navigateTo({ url: '/subpackage_lostfound/marketBrowseHistory/marketBrowseHistory' })
    },
    goToSettings() {
      uni.showToast({ title: '功能开发中', icon: 'none' })
    }
  }
}
</script>

<style scoped>
/* ========== Page Layout ========== */
.page-root {
  width: 100%;
  min-height: 100vh;
  background: #F5F5F5;
  padding-bottom: 130rpx;
}

.page-body {
  width: 100%;
  padding: 20rpx 24rpx;
  box-sizing: border-box;
}

/* ========== User Card ========== */
.user-card {
  display: flex;
  align-items: center;
  gap: 24rpx;
  padding: 32rpx 28rpx;
  background: #FFFFFF;
  border-radius: 16rpx;
  margin-bottom: 20rpx;
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.04);
}

.uc-avatar {
  width: 104rpx;
  height: 104rpx;
  border-radius: 50%;
  background: #F0F0F0;
  flex-shrink: 0;
}

.uc-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}

.uc-name-row {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.uc-name {
  font-size: 34rpx;
  font-weight: 700;
  color: #111111;
}

.uc-badge {
  padding: 2rpx 14rpx;
  border-radius: 999rpx;
  background: #F5F5F5;
  border: 1rpx solid #DDDDDD;
}

.uc-badge text {
  font-size: 20rpx;
  color: #666666;
  font-weight: 500;
}

.uc-school {
  font-size: 24rpx;
  color: #888888;
}

.uc-student-id {
  font-size: 22rpx;
  color: #999999;
}

/* ========== Stats Row ========== */
.stats-row {
  display: flex;
  align-items: center;
  padding: 28rpx 0;
  background: #FFFFFF;
  border-radius: 16rpx;
  margin-bottom: 28rpx;
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.04);
}

.stat-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 6rpx;
}

.stat-num {
  font-size: 40rpx;
  font-weight: 800;
  color: #111111;
  line-height: 1.2;
}

.stat-label {
  font-size: 22rpx;
  color: #888888;
}

.stat-divider {
  width: 1rpx;
  height: 48rpx;
  background: #EEEEEE;
}

/* ========== Section Header ========== */
.section-header {
  padding: 0 4rpx 16rpx 4rpx;
}

.section-title {
  font-size: 24rpx;
  font-weight: 600;
  color: #999999;
}

/* ========== Menu Block ========== */
.menu-block {
  background: #FFFFFF;
  border-radius: 16rpx;
  overflow: hidden;
  margin-bottom: 28rpx;
  box-shadow: 0 2rpx 12rpx rgba(0, 0, 0, 0.04);
}

.menu-item {
  display: flex;
  align-items: center;
  padding: 28rpx 28rpx;
}

.menu-icon {
  width: 36rpx;
  height: 36rpx;
  margin-right: 24rpx;
  flex-shrink: 0;
  opacity: 0.5;
}

.menu-label {
  flex: 1;
  font-size: 28rpx;
  color: #111111;
}

.menu-arrow {
  font-size: 32rpx;
  color: #C7C7CC;
  flex-shrink: 0;
}

.menu-divider {
  height: 1rpx;
  margin-left: 84rpx;
  background: #EEEEEE;
}

/* ========== Bottom Spacer ========== */
.bottom-spacer {
  height: 40rpx;
}
</style>
