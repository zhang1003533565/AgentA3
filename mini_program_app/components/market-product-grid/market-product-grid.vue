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
            <text class="product-img-placeholder-text">{{ itemCategoryLabel(item) }}</text>
          </view>
          <text class="product-status-badge" :class="'product-status-badge--' + item.status">{{ item.statusText }}</text>
        </view>
        <view class="product-body">
          <view class="product-main-row">
            <text class="product-name">{{ item.name }}</text>
            <view class="product-price-row">
              <text v-if="priceDisplay(item).prefix" class="product-price-symbol">{{ priceDisplay(item).prefix }}</text>
              <text class="product-price-num" :class="{ 'product-price-text': !priceDisplay(item).prefix }">{{ priceDisplay(item).text }}</text>
            </view>
          </view>
          <view class="product-info-row">
            <text class="product-info-chip">{{ itemConditionLabel(item) }}</text>
            <text class="product-info-chip">{{ itemCategoryLabel(item) }}</text>
            <text v-if="isNegotiable(item)" class="product-info-chip product-info-chip--blue">可议价</text>
          </view>
          <view class="product-location-row">
            <text class="product-location-label">取货地点</text>
            <text class="product-location">{{ itemLocationLabel(item) }}</text>
          </view>
          <view class="product-metrics-row">
            <text class="product-metric">热度 {{ itemHeatScore(item) }}</text>
            <text class="product-metric">咨询 {{ itemInquiryCount(item) }}</text>
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
  getMarketConditionLabel,
  getMarketLocationLabel,
  getMarketPriceDisplay
} from '@/subpackage_lostfound/utils/secondhandItem.js'
import { getMarketCategoryLabel } from '@/subpackage_lostfound/utils/marketCategories.js'

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
    itemConditionLabel: getMarketConditionLabel,
    itemLocationLabel: getMarketLocationLabel,
    itemCategoryLabel: getMarketCategoryLabel,
    isNegotiable(item) {
      return Boolean(item.allowBargain || String(item.desc || '').includes('价格可议'))
    },
    itemHeatScore(item) {
      return Number(item.heatScore || item.heat_score || 0)
    },
    itemInquiryCount(item) {
      return Number(item.inquiryCount || item.inquiry_count || 0)
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
  grid-template-columns: repeat(2, 1fr);
  align-items: start;
  gap: 18rpx;
  padding: 4rpx 24rpx 200rpx;
}

.product-card {
  background: #fff;
  border-radius: 22rpx;
  overflow: hidden;
  border: 1rpx solid #EEEEEE;
  box-shadow: 0 6rpx 18rpx rgba(92, 122, 153, 0.05);
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
  aspect-ratio: 1 / 1;
  background: #F1F3F5;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  border-radius: 18rpx;
}

.product-img-src {
  width: 100%;
  height: 100%;
  border-radius: 18rpx;
}

.product-img-placeholder {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #8E8E93;
}

.product-img-placeholder-text {
  font-size: 24rpx;
  font-weight: 700;
}

.product-status-badge {
  position: absolute;
  top: 12rpx;
  left: 12rpx;
  padding: 6rpx 12rpx;
  border-radius: 999rpx;
  background: rgba(29, 29, 31, 0.72);
  color: #FFFFFF;
  font-size: 19rpx;
  font-weight: 800;
  line-height: 1;
}

.product-status-badge--reserved {
  background: rgba(92, 122, 153, 0.88);
}

.product-status-badge--sold,
.product-status-badge--offline {
  background: rgba(142, 142, 147, 0.88);
}

.product-body {
  padding: 18rpx 18rpx 20rpx;
}

.product-main-row {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
  margin-bottom: 12rpx;
}

.product-name {
  font-size: 27rpx;
  font-weight: 700;
  color: #1D1D1F;
  line-height: 1.35;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  min-height: 0;
}

.product-price-row {
  display: flex;
  align-items: baseline;
  gap: 2rpx;
}

.product-price-symbol {
  font-size: 21rpx;
  font-weight: 800;
  color: #1D1D1F;
  line-height: 1;
}

.product-price-num {
  font-size: 33rpx;
  font-weight: 850;
  color: #1D1D1F;
  line-height: 1;
}

.product-price-text {
  font-size: 30rpx;
  color: #4A6278;
}

.product-info-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8rpx;
  margin-bottom: 14rpx;
}

.product-info-chip {
  max-width: 128rpx;
  padding: 5rpx 10rpx;
  border-radius: 10rpx;
  background: #F5F6F8;
  color: #6B6F76;
  font-size: 18rpx;
  font-weight: 700;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.product-info-chip--blue {
  background: rgba(92, 122, 153, 0.08);
  color: #4A6278;
}

.product-location-row {
  display: flex;
  flex-direction: column;
  gap: 4rpx;
  margin-bottom: 14rpx;
}

.product-location-label {
  font-size: 19rpx;
  color: #A2A8AF;
  font-weight: 600;
}

.product-location {
  font-size: 22rpx;
  color: #5C5C60;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.product-user {
  display: flex;
  align-items: center;
  gap: 8rpx;
  padding-top: 14rpx;
  border-top: 1rpx solid #F0F0F0;
}

.product-metrics-row {
  display: flex;
  align-items: center;
  gap: 8rpx;
  margin: 0 0 14rpx;
}

.product-metric {
  max-width: 130rpx;
  padding: 5rpx 10rpx;
  border-radius: 10rpx;
  background: rgba(92, 122, 153, 0.08);
  color: #5C7A99;
  font-size: 18rpx;
  font-weight: 700;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
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
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.product-time {
  font-size: 19rpx;
  color: #A2A8AF;
  flex-shrink: 0;
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
