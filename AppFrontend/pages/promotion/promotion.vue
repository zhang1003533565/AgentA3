<template>
  <view class="promotion-page">
    <view class="header">
      <view class="header-row">
        <view class="back-btn" @click="goBack">
          <text class="back-icon">‹</text>
        </view>
        <view class="header-title-wrap">
          <text class="header-title">优惠上新</text>
          <text class="header-subtitle">发现校园周边优惠福利</text>
        </view>
      </view>

      <view class="search-bar" @click="openSearchPage">
        <text class="search-icon">⌕</text>
        <text class="search-placeholder">搜索优惠信息...</text>
      </view>
    </view>

    <view class="category-scroll">
      <view v-if="bannerImages.length" class="banner-wrap">
        <swiper
          class="banner-swiper"
          circular
          autoplay
          interval="3500"
          duration="500"
          indicator-dots
          indicator-color="rgba(255, 255, 255, 0.45)"
          indicator-active-color="#ffffff"
        >
          <swiper-item v-for="(image, index) in bannerImages" :key="`${image}-${index}`">
            <image class="banner-image" :src="image" mode="aspectFill" />
          </swiper-item>
        </swiper>
      </view>

      <scroll-view class="category-list" scroll-x :show-scrollbar="false">
        <view class="category-list-inner">
          <view
            v-for="item in categories"
            :key="item.value"
            class="category-pill"
            :class="{ active: activeCategory === item.value }"
            @click="switchCategory(item.value)"
          >
            <text>{{ item.label }}</text>
          </view>
        </view>
      </scroll-view>
    </view>

    <view class="promo-grid" v-if="filteredCoupons.length">
      <view
        v-for="item in filteredCoupons"
        :key="item.id"
        class="promo-card"
      >
        <view class="promo-card-left" :class="item.category || 'coupon'">
          <image
            v-if="item.imageUrl"
            class="promo-card-image"
            :src="item.imageUrl"
            mode="aspectFill"
          />
          <text v-else>{{ categoryEmojiMap[item.category] || '🎫' }}</text>
        </view>

        <view class="promo-card-right">
          <view class="promo-card-header">
            <text class="promo-card-title">{{ item.couponName }}</text>
          </view>

          <text class="promo-card-desc">{{ item.description || '暂无说明' }}</text>

          <view class="promo-card-footer">
            <view class="promo-card-contact">
              <view class="promo-card-contact-icon">
                <text class="promo-card-contact-symbol">⌂</text>
              </view>
              <text class="promo-card-contact-text">{{ formatPickupLocation(item) }}</text>
            </view>

            <view class="promo-card-time">
              <text>{{ formatDateRange(item) }}</text>
            </view>
          </view>
        </view>
      </view>
    </view>

    <view v-else-if="!isLoading" class="empty-state">
      <text class="empty-text">暂无匹配的优惠信息</text>
    </view>
  </view>
</template>

<script>
import { getPromotionCouponList } from '@/api/promotion.js'

const CATEGORY_OPTIONS = [
  { value: 'all', label: '全部' },
  { value: 'coupon', label: '食堂优惠卡' },
  { value: 'card', label: '校园卡' },
  { value: 'ad', label: '代理服务' },
  { value: 'life', label: '生活服务' }
]

export default {
  data() {
    return {
      categories: CATEGORY_OPTIONS,
      activeCategory: 'all',
      couponList: [],
      isLoading: false,
      categoryEmojiMap: {
        coupon: '🎫',
        card: '🎓',
        ad: '📦',
        life: '🧺'
      }
    }
  },
  computed: {
    bannerImages() {
      return this.couponList
        .map((item) => item.imageUrl)
        .filter(Boolean)
        .slice(0, 5)
    },
    filteredCoupons() {
      return this.couponList.filter((item) => {
        const matchCategory = this.activeCategory === 'all' || item.category === this.activeCategory
        return matchCategory
      })
    }
  },
  onLoad() {
    this.loadPageData()
  },
  onPullDownRefresh() {
    this.loadPageData(false)
  },
  methods: {
    async loadPageData(showLoading = true) {
      this.isLoading = true
      if (showLoading) {
        uni.showLoading({ title: '加载中...' })
      }
      try {
        const couponRes = await getPromotionCouponList()
        this.couponList = Array.isArray(couponRes.data) ? couponRes.data : []
      } catch (error) {
      } finally {
        this.isLoading = false
        if (showLoading) {
          uni.hideLoading()
        }
        uni.stopPullDownRefresh()
      }
    },
    switchCategory(category) {
      this.activeCategory = category
    },
    openSearchPage() {
      uni.navigateTo({ url: '/pages/promotion/search' })
    },
    goBack() {
      const pages = getCurrentPages()
      if (pages.length <= 1) {
        uni.reLaunch({ url: '/pages/index/index' })
        return
      }
      uni.navigateBack({ delta: 1 })
    },
    formatPickupLocation(item) {
      return item.pickupLocation || item.stallName || item.merchantName || item.facilityName || '线下咨询'
    },
    formatDateRange(item) {
      if (!item.startDate && !item.endDate) {
        return '长期有效'
      }
      const start = item.startDate ? String(item.startDate).replace(/-/g, '.') : '即日'
      const end = item.endDate ? String(item.endDate).replace(/-/g, '.') : '长期'
      return `${start}-${end}`
    }
  }
}
</script>

<style lang="scss" scoped>
.promotion-page {
  min-height: 100vh;
  background: linear-gradient(145deg, #f0e9de 0%, #e5ddd0 50%, #ded3c5 100%);
  padding-bottom: 40rpx;
}

.header {
  padding: 24rpx 24rpx 12rpx;
  background: linear-gradient(180deg, #f5efe6 60%, rgba(245, 239, 230, 0) 100%);
}

.header-row {
  display: flex;
  align-items: center;
  gap: 18rpx;
}

.back-btn {
  width: 72rpx;
  height: 72rpx;
  border-radius: 24rpx;
  background: #fff;
  border: 2rpx solid #e5ded3;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 4rpx 16rpx rgba(0, 0, 0, 0.03);
}

.back-icon {
  font-size: 40rpx;
  color: #6b5d4d;
  line-height: 1;
}

.header-title-wrap {
  flex: 1;
}

.header-title {
  display: block;
  font-size: 44rpx;
  font-weight: 700;
  color: #2c2416;
}

.header-subtitle {
  display: block;
  margin-top: 4rpx;
  font-size: 24rpx;
  color: #9c8b74;
}

.search-bar {
  margin-top: 24rpx;
  display: flex;
  align-items: center;
  gap: 12rpx;
  padding: 22rpx 24rpx;
  background: #fff;
  border-radius: 28rpx;
  border: 2rpx solid #e5ded3;
  box-shadow: 0 4rpx 24rpx rgba(0, 0, 0, 0.03);
}

.search-icon {
  font-size: 36rpx;
  color: #9c8b74;
}

.search-placeholder {
  flex: 1;
  font-size: 26rpx;
  color: #9c8b74;
}

.category-scroll {
  padding: 0 24rpx;
  margin-top: 28rpx;
}

.banner-wrap {
  margin-bottom: 24rpx;
}

.banner-swiper {
  height: 280rpx;
  border-radius: 32rpx;
  overflow: hidden;
  border: 2rpx solid #e5ded3;
  box-shadow: 0 12rpx 32rpx rgba(44, 36, 22, 0.08);
}

.banner-image {
  width: 100%;
  height: 100%;
}

.category-list {
  white-space: nowrap;
}

.category-list-inner {
  display: inline-flex;
  gap: 16rpx;
  padding-right: 8rpx;
}

.category-pill {
  flex-shrink: 0;
  padding: 16rpx 30rpx;
  border-radius: 999rpx;
  background: #fff;
  border: 2rpx solid #e5ded3;
  font-size: 24rpx;
  font-weight: 600;
  color: #6b5d4d;
}

.category-pill.active {
  background: #2c2416;
  color: #fff;
  border-color: #2c2416;
  box-shadow: 0 8rpx 24rpx rgba(44, 36, 22, 0.24);
}

.promo-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 20rpx;
  padding: 24rpx;
}

.promo-card {
  background: #fff;
  border-radius: 36rpx;
  padding: 28rpx;
  border: 2rpx solid #e5ded3;
  display: flex;
  gap: 20rpx;
}

.promo-card-left {
  width: 120rpx;
  height: 120rpx;
  border-radius: 28rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 48rpx;
  flex-shrink: 0;
  overflow: hidden;
}

.promo-card-left.coupon {
  background: linear-gradient(145deg, #f5e6d3, #e8d5be);
}

.promo-card-left.card {
  background: linear-gradient(145deg, #e8e3f5, #d5d0e8);
}

.promo-card-left.ad {
  background: linear-gradient(145deg, #e5f5f0, #d0e8e2);
}

.promo-card-left.life {
  background: linear-gradient(145deg, #f8e8e4, #f1d3c8);
}

.promo-card-image {
  width: 100%;
  height: 100%;
}

.promo-card-right {
  flex: 1;
  min-width: 0;
}

.promo-card-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12rpx;
}

.promo-card-title {
  font-size: 30rpx;
  font-weight: 700;
  color: #2c2416;
  line-height: 1.35;
  flex: 1;
}

.promo-card-desc {
  display: block;
  font-size: 24rpx;
  color: #6b5d4d;
  margin-top: 12rpx;
  line-height: 1.55;
}

.promo-card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12rpx;
  margin-top: 20rpx;
  padding-top: 20rpx;
  border-top: 2rpx dashed #e5ded3;
}

.promo-card-contact {
  display: flex;
  align-items: center;
  gap: 10rpx;
  min-width: 0;
  flex: 1;
}

.promo-card-contact-icon {
  width: 48rpx;
  height: 48rpx;
  border-radius: 12rpx;
  background: linear-gradient(145deg, #c4a35a, #a68b45);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.promo-card-contact-symbol {
  color: #fff;
  font-size: 24rpx;
}

.promo-card-contact-text {
  font-size: 22rpx;
  color: #9c8b74;
  flex: 1;
}

.promo-card-time {
  font-size: 20rpx;
  color: #9c8b74;
  flex-shrink: 0;
}

.empty-state {
  padding: 80rpx 24rpx;
  text-align: center;
}

.empty-text {
  font-size: 26rpx;
  color: #9c8b74;
}
</style>
