<template>
  <view class="bottom-bar" :class="{ 'bottom-bar--publishing': publishOverlayMounted }">
    <view class="bar-item" hover-class="none" :class="{ active: activeTab === 'home' }" @click="goToHome">
      <image class="bar-icon-img" src="/static/icons/home.svg" mode="aspectFit" />
      <span>首页</span>
    </view>
    <view class="bar-item" hover-class="none" :class="{ active: activeTab === 'market' }" @click="goToList">
      <image class="bar-icon-img" src="/static/icons/marketplace.svg" mode="aspectFit" />
      <span>市集</span>
    </view>
    <view class="bar-post-wrap">
      <view class="bar-post" @click="goToPublish">
        <text>＋</text>
      </view>
    </view>
    <view class="bar-item" hover-class="none" :class="{ active: activeTab === 'messages' }" @click="goToMessages">
      <view class="bar-icon-wrap">
        <image class="bar-icon-img" src="/static/icons/chat.svg" mode="aspectFit" />
        <view v-if="unreadCount > 0" class="bar-badge">{{ unreadCount > 99 ? '99+' : unreadCount }}</view>
      </view>
      <span>消息</span>
    </view>
    <view class="bar-item" hover-class="none" :class="{ active: activeTab === 'profile' }" @click="goToProfile">
      <image class="bar-icon-img" src="/static/icons/profile.svg" mode="aspectFit" />
      <span>我的</span>
    </view>
    <market-publish-overlay
      v-if="publishOverlayMounted"
      :visible="publishOverlayVisible"
      @close="closePublishOverlay"
    />
  </view>
</template>

<script>
import MarketPublishOverlay from '@/components/market-publish-overlay/market-publish-overlay.vue'
import { getMessageState, subscribeMessageStore } from '@/utils/messageStore'

export default {
  name: 'MarketBottomBar',
  components: {
    MarketPublishOverlay
  },
  props: {
    activeTab: { type: String, default: 'home' }
  },
  data() {
    return {
      unreadCount: 0,
      publishOverlayMounted: false,
      publishOverlayVisible: false,
      publishOverlayTimer: null,
      unsubscribeMessageStore: null
    }
  },
  mounted() {
    this.applyMessageState(getMessageState())
    this.unsubscribeMessageStore = subscribeMessageStore((state) => {
      this.applyMessageState(state)
    })
  },
  beforeDestroy() {
    if (this.publishOverlayTimer) {
      clearTimeout(this.publishOverlayTimer)
      this.publishOverlayTimer = null
    }
    if (this.unsubscribeMessageStore) {
      this.unsubscribeMessageStore()
      this.unsubscribeMessageStore = null
    }
  },
  methods: {
    applyMessageState(state = {}) {
      this.unreadCount = Number(state.totalUnreadCount || 0)
    },
    goToHome() {
      if (this.activeTab === 'home') return
      uni.redirectTo({ url: '/subpackage_lostfound/marketplaceHome/marketplaceHome' })
    },
    goToList() {
      if (this.activeTab === 'market') return
      uni.redirectTo({ url: '/subpackage_lostfound/lostfoundList/lostfoundList' })
    },
    goToPublish() {
      if (this.publishOverlayMounted) return
      if (this.publishOverlayTimer) {
        clearTimeout(this.publishOverlayTimer)
        this.publishOverlayTimer = null
      }
      this.publishOverlayMounted = true
      this.$nextTick(() => {
        this.publishOverlayTimer = setTimeout(() => {
          this.publishOverlayVisible = true
          this.publishOverlayTimer = null
        }, 20)
      })
    },
    closePublishOverlay() {
      if (!this.publishOverlayMounted) return
      if (this.publishOverlayTimer) {
        clearTimeout(this.publishOverlayTimer)
        this.publishOverlayTimer = null
      }
      this.publishOverlayVisible = false
      this.publishOverlayTimer = setTimeout(() => {
        this.publishOverlayMounted = false
        this.publishOverlayTimer = null
      }, 300)
    },
    goToMessages() {
      if (this.activeTab === 'messages') return
      uni.redirectTo({ url: '/pages/market/message/message' })
    },
    goToProfile() {
      if (this.activeTab === 'profile') return
      uni.redirectTo({ url: '/subpackage_lostfound/marketplaceProfile/marketplaceProfile' })
    }
  }
}
</script>

<style scoped>
.bottom-bar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  display: flex;
  align-items: flex-start;
  justify-content: space-around;
  background: #FFFFFF;
  padding: 8rpx 0 calc(6rpx + env(safe-area-inset-bottom));
  box-shadow: 0 -2rpx 12rpx rgba(0, 0, 0, 0.06);
  z-index: 60;
}

.bottom-bar--publishing {
  z-index: 2147483647;
}

.bar-item {
  flex: 1;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: flex-start;
  gap: 0;
  padding: 5rpx 0;
  border-radius: 12rpx;
  color: #888888;
  font-size: 20rpx;
  font-weight: 600;
  line-height: 26rpx;
  text-align: center;
  transition: none;
  animation: none;
  transform: none;
}

.bar-item.active {
  color: #6F98D0;
}

.bar-item span {
  display: block;
  height: 26rpx;
  margin-top: 0;
  line-height: 26rpx;
  transition: none;
  animation: none;
  transform: none;
}

.bar-item.active .bar-icon-img {
  opacity: 1;
}

.bar-icon-img {
  width: 48rpx;
  height: 48rpx;
  margin: 0 0 2rpx;
  opacity: 0.6;
  transition: none;
  animation: none;
  transform: none;
  flex-shrink: 0;
}

.bar-icon-wrap {
  position: relative;
  width: 48rpx;
  height: 50rpx;
  margin-bottom: 0;
  display: flex;
  align-items: flex-start;
  justify-content: center;
  transition: none;
  animation: none;
  transform: none;
  flex-shrink: 0;
}

.bar-icon-wrap .bar-icon-img {
  margin: 0;
}

.bar-badge {
  position: absolute;
  top: -8rpx;
  right: -14rpx;
  min-width: 32rpx;
  height: 32rpx;
  padding: 0 8rpx;
  border-radius: 999rpx;
  background: #E85D75;
  color: #fff;
  font-size: 18rpx;
  font-weight: 700;
  line-height: 32rpx;
  text-align: center;
  white-space: nowrap;
  box-sizing: border-box;
}

.bar-post-wrap {
  position: relative;
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
}

.bar-post-wrap::before {
  content: '';
  position: absolute;
  top: -57rpx;
  left: calc(50% - 77rpx);
  width: 154rpx;
  height: 154rpx;
  border-radius: 50%;
  background: #FFFFFF;
  box-shadow: none;
  pointer-events: none;
}

.bar-post {
  position: relative;
  z-index: 1;
  width: 124rpx;
  height: 124rpx;
  border-radius: 50%;
  background: linear-gradient(145deg, rgba(226, 242, 255, 0.82), rgba(124, 181, 232, 0.48));
  border: 2rpx solid rgba(255, 255, 255, 0.82);
  color: #FFFFFF;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow:
    0 24rpx 46rpx rgba(92, 122, 153, 0.28),
    0 10rpx 24rpx rgba(111, 152, 208, 0.26),
    inset 0 3rpx 0 rgba(255, 255, 255, 0.86),
    inset 0 -3rpx 0 rgba(92, 122, 153, 0.12);
  backdrop-filter: blur(26rpx) saturate(170%);
  -webkit-backdrop-filter: blur(26rpx) saturate(170%);
  line-height: 1;
  margin-top: -42rpx;
}

.bar-post text {
  color: #FFFFFF;
  font-size: 60rpx;
  font-weight: 800;
  line-height: 1;
  text-shadow: 0 2rpx 6rpx rgba(92, 122, 153, 0.28);
}
</style>
