<template>
  <view class="page-root">
    <view class="container">
      <common-page-header title="我的收藏" :fixed="true" :placeholder="true" :showBack="true" />
      <scroll-view
        scroll-y
        class="page-body"
        :class="{ 'is-empty': !loading && items.length === 0 }"
        refresher-enabled
        :refresher-triggered="refreshing"
        @refresherrefresh="refresh"
      >
        <view v-if="!loading && items.length === 0" class="empty">
          <image
            class="empty-illustration"
            src="/static/illustrations/market-empty-favorites.svg"
            mode="aspectFit"
          />
          <view class="empty-title">还没有收藏商品</view>
          <view class="empty-desc">遇到喜欢的商品，记得点击收藏哦</view>
          <button class="empty-action" hover-class="empty-action-hover" @click="goBrowse">去逛逛</button>
        </view>
        <view v-else class="item-grid">
          <view v-for="item in items" :key="item.id" class="item-card" @click="goDetail(item.id)">
            <view class="card-cover">
              <image v-if="item.images && item.images.length" :src="item.images[0]" mode="aspectFill" class="cover-img" />
              <view v-else class="cover-placeholder">
                <text class="cover-emoji">{{ itemEmoji(item.id) }}</text>
              </view>
              <view class="card-badge-type" :class="'card-badge-type--' + (item.tradeType || 'sell')">
                {{ item.tradeType === 'buy' ? '收' : '出' }}
              </view>
            </view>
            <view class="card-body">
              <view class="card-title">{{ item.title }}</view>
              <view class="card-price">¥{{ priceText(item.price) }}</view>
              <view class="card-location-row" v-if="item.pickupPoint || item.location">
                <view class="loc-icon"></view>
                <text class="card-location">{{ item.pickupPoint || item.location }}</text>
              </view>
              <view class="card-user">
                <view class="user-avatar">{{ item.sellerName ? item.sellerName[0] : '同' }}</view>
                <text class="user-name">{{ item.sellerName || '同学' }}</text>
                <text class="user-time">{{ fmt(item.ctime) }}</text>
              </view>
            </view>
          </view>
        </view>
      </scroll-view>
    </view>
  </view>
</template>

<script>
import CommonPageHeader from '@/components/common-page-header/common-page-header.vue'
import { getMyFavorites } from '@/api/secondhand'

const EMOJIS = ['📱', '💻', '📷', '🎧', '⌚', '📚', '👟', '🧥', '🪑', '🏠', '🎮', '🎸', '🖥️', '📦']

function normalize(raw = {}) {
  const seller = raw.seller || {}
  return {
    id: raw.id,
    title: raw.title || raw.name || '',
    price: raw.price,
    images: Array.isArray(raw.images) ? raw.images : [],
    tradeType: raw.tradeType || raw.trade_type || 'sell',
    pickupPoint: raw.pickupPoint || '',
    location: raw.location || '',
    sellerName: raw.sellerName || seller.username || seller.nickname || raw.userName || '',
    ctime: raw.createTime || raw.ctime || ''
  }
}

export default {
  components: { CommonPageHeader },
  data() { return { loading: false, refreshing: false, items: [] } },
  onShow() { this.loadItems() },
  methods: {
    itemEmoji(id) {
      return EMOJIS[(id || 0) % EMOJIS.length]
    },
    async refresh() { this.refreshing = true; await this.loadItems(); this.refreshing = false },
    async loadItems() {
      try {
        this.loading = true
        const res = await getMyFavorites({ current: 1, size: 100 })
        const records = Array.isArray(res?.data?.records) ? res.data.records : []
        this.items = records.map(normalize)
      } catch (e) {
        console.error('加载收藏失败', e)
        uni.showToast({ title: '加载失败', icon: 'none' })
      } finally { this.loading = false }
    },
    priceText(value) { const price = Number(value); return Number.isFinite(price) ? price.toFixed(price % 1 === 0 ? 0 : 2) : '--' },
    fmt(value) {
      if (!value) return ''
      const str = String(value).replace('T', ' ')
      return str.split(' ')[0] || ''
    },
    goDetail(id) { uni.navigateTo({ url: `/subpackage_lostfound/lostfoundDetail/lostfoundDetail?id=${id}` }) },
    goBrowse() {
      uni.redirectTo({ url: '/subpackage_lostfound/lostfoundList/lostfoundList' })
    }
  }
}
</script>

<style scoped>
.page-root { min-height: 100vh; background: #F7F8FA; }
.container { width: 100%; max-width: 430px; min-height: 100vh; margin: 0 auto; padding: 0 12rpx; box-sizing: border-box; background: #F7F8FA; }
.page-body { height: calc(100vh - 88rpx); padding: 26rpx 12rpx 34rpx; box-sizing: border-box; }
.page-body.is-empty { padding: 0; }

.empty {
  min-height: calc(100vh - 88rpx);
  padding: 280rpx 0 120rpx;
  box-sizing: border-box;
  text-align: center;
  color: #8aa1b2;
}
.empty-illustration { width: 350rpx; height: 276rpx; margin: 0 auto 42rpx; display: block; }
.empty-title { color: #1D2430; font-size: 34rpx; font-weight: 900; line-height: 1.25; }
.empty-desc { margin-top: 22rpx; color: #9AA2AE; font-size: 25rpx; font-weight: 500; line-height: 1.4; }
.empty-action { width: 236rpx; height: 72rpx; margin: 58rpx auto 0; padding: 0; border-radius: 999rpx; background: #4D77F3; color: #FFFFFF; font-size: 26rpx; font-weight: 800; line-height: 72rpx; box-shadow: 0 14rpx 30rpx rgba(77, 119, 243, 0.26); }
.empty-action::after { border: none; }
.empty-action-hover { opacity: 0.88; }

.item-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12rpx;
}

.item-card {
  display: flex;
  flex-direction: column;
  background: #FFFFFF;
  border-radius: 16rpx;
  overflow: hidden;
  box-shadow: 0 4rpx 16rpx rgba(30, 41, 59, 0.06);
  box-sizing: border-box;
}

.card-cover {
  position: relative;
  width: 100%;
  padding-top: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #EEF2F7;
  overflow: hidden;
}

.cover-img {
  position: absolute;
  left: 0;
  top: 0;
  width: 100%;
  height: 100%;
}

.cover-placeholder {
  position: absolute;
  left: 0;
  top: 0;
  width: 100%;
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 14rpx;
  color: #8E8E93;
}

.cover-emoji {
  font-size: 58rpx;
  line-height: 1;
}

.card-badge-type {
  position: absolute;
  left: 14rpx;
  top: 14rpx;
  min-width: 40rpx;
  height: 40rpx;
  padding: 0 8rpx;
  border-radius: 10rpx;
  color: #FFFFFF;
  font-size: 22rpx;
  font-weight: 800;
  line-height: 40rpx;
  text-align: center;
  box-shadow: 0 4rpx 10rpx rgba(0, 0, 0, 0.2);
}

.card-badge-type--sell {
  background: #FF6B35;
  box-shadow: 0 4rpx 10rpx rgba(255, 107, 53, 0.35);
}

.card-badge-type--buy {
  background: #4A90E2;
  box-shadow: 0 4rpx 10rpx rgba(74, 144, 226, 0.35);
}

.card-body {
  padding: 14rpx 16rpx 18rpx;
  display: flex;
  flex-direction: column;
  gap: 6rpx;
}

.card-title {
  color: #1D2430;
  font-size: 24rpx;
  font-weight: 800;
  line-height: 1.3;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-price {
  color: #FF4D2E;
  font-size: 28rpx;
  font-weight: 900;
  line-height: 1.2;
}

.card-location-row {
  display: flex;
  align-items: center;
  gap: 6rpx;
  color: #8A94A6;
  font-size: 20rpx;
  line-height: 1.3;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.loc-icon {
  position: relative;
  width: 18rpx;
  height: 18rpx;
  flex-shrink: 0;
}

.loc-icon::before {
  content: '';
  position: absolute;
  left: 50%;
  top: 50%;
  width: 12rpx;
  height: 12rpx;
  margin-left: -6rpx;
  margin-top: -12rpx;
  border: 2rpx solid #8A94A6;
  border-radius: 50% 50% 50% 0;
  transform: rotate(-45deg);
  box-sizing: border-box;
}

.loc-icon::after {
  content: '';
  position: absolute;
  left: 50%;
  top: 50%;
  width: 4rpx;
  height: 4rpx;
  margin-left: -2rpx;
  margin-top: -2rpx;
  background: #8A94A6;
  border-radius: 50%;
}

.card-location {
  font-size: 20rpx;
  color: #8A94A6;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-user {
  display: flex;
  align-items: center;
  gap: 8rpx;
  padding-top: 10rpx;
  border-top: 1rpx solid #F0F2F5;
}

.user-avatar {
  width: 34rpx;
  height: 34rpx;
  border-radius: 50%;
  background: rgba(92, 122, 153, 0.12);
  color: #5C7A99;
  font-size: 18rpx;
  font-weight: 700;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.user-name {
  font-size: 21rpx;
  color: #666A70;
  font-weight: 600;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.user-time {
  font-size: 18rpx;
  color: #9AA2AE;
  font-weight: 500;
  margin-left: auto;
}
</style>
