<template>
  <view class="product-grid">
    <block v-if="loading">
      <view class="product-card product-card--skeleton" v-for="i in 4" :key="'sk' + i">
        <view class="product-img product-img--sk"></view>
        <view class="product-body">
          <view class="sk-line sk-line--1"></view>
          <view class="sk-line sk-line--2"></view>
          <view class="sk-line sk-line--3"></view>
        </view>
      </view>
    </block>

    <view v-else-if="items.length === 0" class="empty-block">
      <view class="empty-icon"></view>
      <text class="empty-title">{{ emptyTitle }}</text>
      <text class="empty-sub">{{ emptySub }}</text>
    </view>

    <block v-else>
      <view
        v-for="item in items"
        :key="item.id"
        class="product-card"
        @click="handleItemClick(item.id)"
      >
        <view class="product-img">
          <image v-if="item.images && item.images.length" class="product-img-src" :src="item.images[0]" mode="aspectFill" />
          <view v-else class="product-img-placeholder">
            <text class="product-img-emoji">{{ itemEmoji(item.id) }}</text>
          </view>
          <view class="product-badge-type" :class="'product-badge-type--' + (item.tradeType || 'sell')">
            {{ item.tradeType === 'buy' ? '收' : '出' }}
          </view>
        </view>
        <view class="product-body">
          <view class="product-title">{{ item.name }}</view>
          <view class="product-price">¥{{ priceDisplay(item).text }}</view>
          <view class="product-location-row" v-if="item.pickupPoint || item.location">
            <view class="product-loc-icon"></view>
            <text class="product-location">{{ itemLocationLabel(item) }}</text>
          </view>
          <view class="product-user">
            <view class="product-ava">{{ item.userName ? item.userName.slice(0, 1) : '同' }}</view>
            <text class="product-uname">{{ item.userName }}</text>
            <text class="product-time">{{ fmt(item.ctime) }}</text>
          </view>
        </view>
      </view>
    </block>
  </view>
</template>

<script>
import {
  formatMarketTime,
  getMarketLocationLabel,
  getMarketPriceDisplay
} from '@/subpackage_lostfound/utils/secondhandItem.js'

const EMOJIS = ['📱', '💻', '📷', '🎧', '⌚', '📚', '👟', '🧥', '🪑', '🏠', '🎮', '🎸', '🖥️', '📦']

export default {
  name: 'MarketProductGrid',
  props: {
    items: {
      type: Array,
      default: () => []
    },
    categories: {
      type: Array,
      default: () => []
    },
    loading: {
      type: Boolean,
      default: false
    },
    emptyTitle: {
      type: String,
      default: '暂无商品'
    },
    emptySub: {
      type: String,
      default: '换个关键词试试'
    }
  },
  methods: {
    fmt: formatMarketTime,
    priceDisplay: getMarketPriceDisplay,
    itemLocationLabel: getMarketLocationLabel,
    itemEmoji(id) {
      return EMOJIS[(id || 0) % EMOJIS.length]
    },
    handleItemClick(id) {
      this.$emit('item-click', id)
    }
  }
}
</script>

<style scoped>
.product-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  align-items: start;
  gap: 12rpx;
  padding: 4rpx 12rpx 200rpx;
}

.product-card {
  background: #fff;
  border-radius: 16rpx;
  overflow: hidden;
  box-shadow: 0 4rpx 16rpx rgba(30, 41, 59, 0.06);
  transition: transform 0.15s ease;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
}

.product-card:active {
  transform: scale(0.98);
}

.product-card--skeleton:active {
  transform: none;
}

.product-img {
  position: relative;
  width: 100%;
  padding-top: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #EEF2F7;
  overflow: hidden;
}

.product-img-src {
  position: absolute;
  left: 0;
  top: 0;
  width: 100%;
  height: 100%;
}

.product-img-placeholder {
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

.product-img-emoji {
  font-size: 58rpx;
  line-height: 1;
}

.product-badge-type {
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

.product-badge-type--sell {
  background: #FF6B35;
  box-shadow: 0 4rpx 10rpx rgba(255, 107, 53, 0.35);
}

.product-badge-type--buy {
  background: #4A90E2;
  box-shadow: 0 4rpx 10rpx rgba(74, 144, 226, 0.35);
}

.product-body {
  padding: 14rpx 16rpx 18rpx;
  display: flex;
  flex-direction: column;
  gap: 6rpx;
}

.product-title {
  color: #1D2430;
  font-size: 24rpx;
  font-weight: 800;
  line-height: 1.3;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.product-price {
  color: #FF4D2E;
  font-size: 28rpx;
  font-weight: 900;
  line-height: 1.2;
}

.product-location-row {
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

.product-loc-icon {
  position: relative;
  width: 18rpx;
  height: 18rpx;
  flex-shrink: 0;
}

.product-loc-icon::before {
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

.product-loc-icon::after {
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

.product-location {
  font-size: 20rpx;
  color: #8A94A6;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.product-user {
  display: flex;
  align-items: center;
  gap: 8rpx;
  padding-top: 10rpx;
  border-top: 1rpx solid #F0F2F5;
}

.product-ava {
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

.product-uname {
  font-size: 21rpx;
  color: #666A70;
  font-weight: 600;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.product-time {
  font-size: 18rpx;
  color: #9AA2AE;
  font-weight: 500;
  margin-left: auto;
}

.empty-block {
  grid-column: 1 / -1;
  min-height: 320rpx;
  padding: 76rpx 24rpx;
  border-radius: 22rpx;
  border: 1rpx solid #EEEEEE;
  background: #FFFFFF;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  box-sizing: border-box;
}

.empty-icon {
  position: relative;
  width: 82rpx;
  height: 62rpx;
  margin-bottom: 22rpx;
  border: 3rpx solid #8E8E93;
  border-top: 0;
  border-radius: 8rpx 8rpx 12rpx 12rpx;
  background: transparent;
  box-sizing: border-box;
}

.empty-icon::before {
  content: '';
  position: absolute;
  left: -3rpx;
  top: -20rpx;
  width: 82rpx;
  height: 24rpx;
  box-sizing: border-box;
  border: 3rpx solid #8E8E93;
  border-bottom: 0;
  border-radius: 10rpx 10rpx 0 0;
}

.empty-icon::after {
  content: '';
  position: absolute;
  left: 20rpx;
  right: 20rpx;
  top: 18rpx;
  height: 0;
  box-sizing: border-box;
  border-top: 3rpx solid #8E8E93;
  border-radius: 999rpx;
}

.empty-title {
  font-size: 28rpx;
  font-weight: 750;
  color: #1D1D1F;
}

.empty-sub {
  margin-top: 8rpx;
  font-size: 23rpx;
  color: #8E8E93;
}

.product-img--sk,
.sk-line {
  background: linear-gradient(90deg, #EEEEEE 25%, #F5F5F5 50%, #EEEEEE 75%);
  background-size: 200% 100%;
  animation: shimmer 1.5s infinite;
}

.sk-line {
  border-radius: 6rpx;
  margin-bottom: 12rpx;
}

.sk-line--1 { height: 28rpx; width: 85%; }
.sk-line--2 { height: 24rpx; width: 45%; }
.sk-line--3 { height: 20rpx; width: 60%; }

@keyframes shimmer {
  0% { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}
</style>
