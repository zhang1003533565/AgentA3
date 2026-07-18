<template>
  <view class="page-root">
    <common-page-header title="我的" :fixed="true" :placeholder="true" :showBack="false">
      <template #left>
        <view class="market-back-button" @click="onBackToApp">‹</view>
      </template>
    </common-page-header>

    <scroll-view scroll-y class="page-body">
      <!-- ========== 用户信息卡片 ========== -->
      <view class="user-card">
        <image class="uc-avatar" :src="avatarUrl" mode="aspectFill" />
        <view class="uc-info">
          <text class="uc-name">{{ displayName }}</text>
          <view class="uc-badge">
            <image class="uc-badge-icon" src="/static/icons/line/book-open.svg" mode="aspectFit" />
            <text>校园用户</text>
          </view>
          <view class="uc-meta-row">
            <image class="uc-meta-icon" src="/static/icons/ant-design--safety-outlined.svg" mode="aspectFit" />
            <text class="uc-student-id">学号{{ studentId || '未设置' }}</text>
          </view>
        </view>
        <text class="uc-arrow">›</text>
      </view>

      <!-- ========== 数据统计 ========== -->
      <view class="stats-row">
        <view class="stat-item">
          <text class="stat-num">{{ stats.myItems }}</text>
          <view class="stat-label-row">
            <image class="stat-icon" src="/static/icons/publish.svg" mode="aspectFit" />
            <text class="stat-label">我的发布</text>
          </view>
        </view>
        <view class="stat-divider"></view>
        <view class="stat-item">
          <text class="stat-num">{{ stats.activeTrades }}</text>
          <view class="stat-label-row">
            <image class="stat-icon" src="/static/icons/line/credit-card.svg" mode="aspectFit" />
            <text class="stat-label">进行中交易</text>
          </view>
        </view>
        <view class="stat-divider"></view>
        <view class="stat-item">
          <text class="stat-num">{{ stats.myFavorites }}</text>
          <view class="stat-label-row">
            <image class="stat-icon" src="/static/icons/line/award.svg" mode="aspectFit" />
            <text class="stat-label">我的收藏</text>
          </view>
        </view>
      </view>

      <!-- ========== 交易中心 ========== -->
      <view class="section-header">
        <text class="section-title">交易中心</text>
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
          <text class="menu-label">我的交易</text>
          <text class="menu-arrow">›</text>
        </view>
        <view class="menu-divider"></view>
        <view class="menu-item" @click="goToTradeRecords">
          <image class="menu-icon" src="/static/icons/line/clipboard.svg" mode="aspectFit" />
          <text class="menu-label">交易记录</text>
          <text class="menu-arrow">›</text>
        </view>
      </view>

      <!-- ========== 更多服务 ========== -->
      <view class="section-header">
        <text class="section-title">更多服务</text>
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
import CommonPageHeader from '@/components/common-page-header/common-page-header.vue'
import MarketBottomBar from '@/components/market-bottom-bar/market-bottom-bar.vue'
import { getUserInfo, getToken } from '@/utils/storage.js'
import { getMySecondhandItems, getMyFavorites, getTradeRecords } from '@/api/secondhand'

export default {
  components: { CommonPageHeader, MarketBottomBar },
  data() {
    return {
      userInfo: null,
      stats: {
        myItems: 0,
        myFavorites: 0,
        activeTrades: 0
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
    studentId() {
      if (!this.userInfo) return ''
      return this.userInfo.studentId || ''
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
    onBackToApp() {
      uni.reLaunch({ url: '/pages/index/index' })
    },
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
        this.stats.activeTrades = records.filter(r => ['WAIT_CONFIRM', 'TRADING'].includes(r.status)).length
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
  background: #F7F8FA;
  padding-bottom: 130rpx;
}

.page-body {
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
  margin-bottom: 28rpx;
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

.uc-badge {
  height: 44rpx;
  padding: 0 18rpx;
  border-radius: 14rpx;
  background: #EEF5FF;
  display: inline-flex;
  align-items: center;
  gap: 8rpx;
}

.uc-badge text {
  font-size: 26rpx;
  color: #5578AF;
  font-weight: 600;
}

.uc-badge-icon {
  width: 30rpx;
  height: 30rpx;
  opacity: 0.72;
}

.uc-meta-row {
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.uc-meta-icon {
  width: 32rpx;
  height: 32rpx;
  opacity: 0.46;
}

.uc-student-id {
  font-size: 28rpx;
  line-height: 1.2;
  color: #8B8F96;
}

.uc-arrow {
  flex-shrink: 0;
  color: #9A9A9A;
  font-size: 68rpx;
  font-weight: 200;
  line-height: 1;
}

/* ========== Stats Row ========== */
.stats-row {
  display: flex;
  align-items: center;
  padding: 38rpx 0 34rpx;
  background: #FFFFFF;
  border-radius: 28rpx;
  margin-bottom: 42rpx;
  box-shadow: 0 18rpx 42rpx rgba(15, 23, 42, 0.06);
}

.stat-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 22rpx;
  min-width: 0;
}

.stat-num {
  font-size: 48rpx;
  font-weight: 800;
  color: #17181A;
  line-height: 1;
}

.stat-label-row {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10rpx;
  min-width: 0;
}

.stat-icon {
  width: 34rpx;
  height: 34rpx;
  opacity: 0.72;
}

.stat-label {
  font-size: 26rpx;
  line-height: 1.2;
  color: #8B8F96;
  white-space: nowrap;
}

.stat-divider {
  width: 1rpx;
  height: 80rpx;
  background: #E4E6EA;
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
  margin-bottom: 42rpx;
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
  height: 64rpx;
}
</style>
