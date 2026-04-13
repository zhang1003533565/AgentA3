<template>
  <view class="promotion-page">
    <nav-bar title="优惠上新" :fixed="true" :placeholder="true" />

    <view class="search-bar" @click="openSearchPage">
      <text class="search-icon">⌕</text>
      <text class="search-placeholder">搜索优惠信息...</text>
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
        @click="openCouponDetail(item)"
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
    <ai-float-assistant />
  </view>
</template>

<script>
import AiFloatAssistant from '@/components/ai-float-assistant/ai-float-assistant.vue'
import { getPromotionCouponList } from '@/api/promotion.js'
import NavBar from '@/components/nav-bar/nav-bar.vue'

const CATEGORY_OPTIONS = [
  { value: 'all', label: '全部' },
  { value: 'coupon', label: '食堂优惠卡' },
  { value: 'card', label: '校园卡' },
  { value: 'ad', label: '代理服务' },
  { value: 'life', label: '生活服务' }
]

export default {
  components: { AiFloatAssistant, NavBar },
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
      uni.navigateTo({ url: '/subpackage_promotion/promotionSearch/promotionSearch' })
    },
    openCouponDetail(item) {
      if (!item || !item.id) {
        return
      }
      uni.navigateTo({
        url: `/subpackage_promotion/promotionDetail/promotionDetail?id=${item.id}`
      })
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
  background: #F7F7F9;
  padding-bottom: 40rpx;
}

.search-bar {
  margin: 24rpx;
  display: flex;
  align-items: center;
  gap: 12rpx;
  padding: 22rpx 24rpx;
  background: #fff;
  border-radius: 28rpx;
  border: none;
  box-shadow: 0 4rpx 24rpx rgba(0, 0, 0, 0.03);
}

.search-icon {
  font-size: 36rpx;
  color: #999;
}

.search-placeholder {
  flex: 1;
  font-size: 26rpx;
  color: #999;
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
  border: none;
  box-shadow: 0 12rpx 32rpx rgba(0, 0, 0, 0.08);
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
  border: 2rpx solid transparent;
  font-size: 24rpx;
  font-weight: 600;
  color: #666;
}

.category-pill.active {
  background: #EEF4FF;
  color: #5C7A99;
  border-color: #5C7A99;
  box-shadow: 0 8rpx 24rpx rgba(0, 122, 255, 0.1);
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
  border: none;
  display: flex;
  gap: 20rpx;
  box-shadow: 0 4rpx 16rpx rgba(0, 0, 0, 0.04);
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
  color: #333;
  line-height: 1.35;
  flex: 1;
}

.promo-card-desc {
  display: block;
  font-size: 24rpx;
  color: #666;
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
  border-top: 2rpx dashed #eee;
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
  background: linear-gradient(135deg, #5C7A99, #8BB8D9);
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
  color: #666;
  flex: 1;
}

.promo-card-time {
  font-size: 20rpx;
  color: #999;
  flex-shrink: 0;
}

.empty-state {
  padding: 80rpx 24rpx;
  text-align: center;
}

.empty-text {
  font-size: 26rpx;
  color: #999;
}
</style>
