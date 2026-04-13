<template>
  <view class="promotion-detail-page">
    <nav-bar
      :title="coupon.couponName || '优惠详情'"
      :fixed="true"
      :placeholder="true"
    />

    <scroll-view class="detail-scroll" scroll-y>
      <view class="hero-card">
        <view class="hero-card__media" :class="coupon.category || 'coupon'">
          <image
            v-if="coupon.imageUrl"
            class="hero-card__image"
            :src="coupon.imageUrl"
            mode="aspectFill"
          />
          <text v-else class="hero-card__emoji">{{ categoryEmojiMap[coupon.category] || '🎫' }}</text>
        </view>

        <view class="hero-card__content">
          <view class="hero-card__title-row">
            <text class="hero-card__title">{{ coupon.couponName || '优惠券' }}</text>
            <text v-if="coupon.tagType" class="hero-card__tag">{{ formatTag(coupon.tagType) }}</text>
          </view>
          <text class="hero-card__desc">{{ coupon.description || '暂无优惠说明' }}</text>

          <view class="hero-card__chips">
            <text v-if="coupon.category" class="hero-card__chip">{{ formatCategory(coupon.category) }}</text>
            <text v-if="coupon.startDate || coupon.endDate" class="hero-card__chip">{{ formatDateRange(coupon) }}</text>
          </view>
        </view>
      </view>

      <view class="detail-section">
        <text class="detail-section__title">使用信息</text>
        <view class="detail-table">
          <view v-for="item in detailItems" :key="item.label" class="detail-row">
            <text class="detail-row__label">{{ item.label }}</text>
            <text class="detail-row__value">{{ item.value }}</text>
          </view>
        </view>
      </view>

      <view v-if="coupon.description" class="detail-section">
        <text class="detail-section__title">优惠说明</text>
        <view class="detail-rich-text">
          <text>{{ coupon.description }}</text>
        </view>
      </view>

      <view class="bottom-space"></view>
    </scroll-view>
  </view>
</template>

<script>
import { getPromotionCouponDetail } from '@/api/promotion.js'
import NavBar from '@/components/nav-bar/nav-bar.vue'

export default {
  components: { NavBar },
  data() {
    return {
      couponId: '',
      coupon: {},
      categoryEmojiMap: {
        coupon: '🎫',
        card: '🎓',
        ad: '📦',
        life: '🧺'
      }
    }
  },
  computed: {
    detailItems() {
      const items = []
      if (this.coupon.facilityName) {
        items.push({ label: '所属食堂', value: this.coupon.facilityName })
      }
      if (this.coupon.stallName) {
        items.push({ label: '所属档口', value: this.coupon.stallName })
      }
      if (this.coupon.merchantName) {
        items.push({ label: '所属商户', value: this.coupon.merchantName })
      }
      if (this.coupon.pickupLocation) {
        items.push({ label: '领取地点', value: this.coupon.pickupLocation })
      }
      if (this.coupon.startDate || this.coupon.endDate) {
        items.push({ label: '有效时间', value: this.formatDateRange(this.coupon) })
      }
      if (this.coupon.totalQuantity !== undefined && this.coupon.totalQuantity !== null) {
        items.push({ label: '发放总量', value: String(this.coupon.totalQuantity) })
      }
      return items
    }
  },
  onLoad(options) {
    if (options && options.id) {
      this.couponId = options.id
      this.loadCouponDetail()
    }
  },
  methods: {
    async loadCouponDetail() {
      try {
        uni.showLoading({ title: '加载中...' })
        const res = await getPromotionCouponDetail(this.couponId)
        this.coupon = res.data || {}
      } catch (error) {
        uni.showToast({ title: error?.msg || '加载失败', icon: 'none' })
      } finally {
        uni.hideLoading()
      }
    },
    formatTag(tagType) {
      const map = {
        hot: '热门',
        new: '上新',
        recommend: '推荐'
      }
      return map[tagType] || tagType
    },
    formatCategory(category) {
      const map = {
        coupon: '食堂优惠',
        card: '校园卡',
        ad: '代理服务',
        life: '生活服务'
      }
      return map[category] || category
    },
    formatDateRange(item) {
      if (!item.startDate && !item.endDate) {
        return '长期有效'
      }
      const start = item.startDate ? String(item.startDate).replace(/-/g, '.') : '即日'
      const end = item.endDate ? String(item.endDate).replace(/-/g, '.') : '长期'
      return `${start} - ${end}`
    }
  }
}
</script>

<style lang="scss" scoped>
.promotion-detail-page {
  min-height: 100vh;
  background: linear-gradient(180deg, #f3f7f6 0%, #eef3f4 100%);
}

.detail-scroll {
  min-height: 100vh;
  padding: 24rpx;
  box-sizing: border-box;
}

.hero-card {
  background: #ffffff;
  border-radius: 32rpx;
  overflow: hidden;
  box-shadow: 0 18rpx 42rpx rgba(24, 61, 74, 0.08);
}

.hero-card__media {
  height: 260rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #0f5d73, #1f8f84);
}

.hero-card__media.card {
  background: linear-gradient(135deg, #4361ee, #5fa8ff);
}

.hero-card__media.ad {
  background: linear-gradient(135deg, #ff8a00, #ffb347);
}

.hero-card__media.life {
  background: linear-gradient(135deg, #4caf50, #81c784);
}

.hero-card__image {
  width: 100%;
  height: 100%;
}

.hero-card__emoji {
  font-size: 108rpx;
}

.hero-card__content {
  padding: 28rpx;
}

.hero-card__title-row {
  display: flex;
  align-items: center;
  gap: 14rpx;
  flex-wrap: wrap;
}

.hero-card__title {
  flex: 1;
  min-width: 0;
  font-size: 34rpx;
  font-weight: 700;
  color: #20323c;
}

.hero-card__tag {
  padding: 8rpx 16rpx;
  border-radius: 999rpx;
  background: #e7f4f1;
  color: #1c6f67;
  font-size: 22rpx;
}

.hero-card__desc {
  display: block;
  margin-top: 18rpx;
  font-size: 26rpx;
  line-height: 1.7;
  color: #5f6f79;
}

.hero-card__chips {
  display: flex;
  flex-wrap: wrap;
  gap: 12rpx;
  margin-top: 18rpx;
}

.hero-card__chip {
  padding: 10rpx 16rpx;
  border-radius: 999rpx;
  background: #f3f8f7;
  color: #3e5d66;
  font-size: 22rpx;
}

.detail-section {
  margin-top: 24rpx;
  padding: 28rpx;
  background: rgba(255, 255, 255, 0.92);
  border-radius: 28rpx;
}

.detail-section__title {
  display: block;
  margin-bottom: 18rpx;
  font-size: 28rpx;
  font-weight: 700;
  color: #20323c;
}

.detail-table {
  display: flex;
  flex-direction: column;
  gap: 14rpx;
}

.detail-row {
  display: flex;
  align-items: flex-start;
  gap: 18rpx;
}

.detail-row__label {
  flex: 0 0 120rpx;
  font-size: 24rpx;
  color: #6b7b82;
}

.detail-row__value {
  flex: 1;
  font-size: 24rpx;
  line-height: 1.6;
  color: #334751;
  word-break: break-all;
}

.detail-rich-text {
  font-size: 26rpx;
  line-height: 1.8;
  color: #4f6168;
}

.bottom-space {
  height: 32rpx;
}
</style>
