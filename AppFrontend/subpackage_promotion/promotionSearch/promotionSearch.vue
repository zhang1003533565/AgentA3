<template>
  <view class="search-page">
    <nav-bar title="优惠券搜索" :fixed="true" :placeholder="true" />

    <view class="search-top">
      <view class="search-box">
        <text class="search-icon">⌕</text>
        <input
          v-model.trim="searchKeyword"
          class="search-input"
          placeholder="搜索优惠名称、描述或领取位置"
          confirm-type="search"
          focus
          @confirm="handleSearch"
        />
        <text v-if="searchKeyword" class="clear-btn" @click="clearSearch">×</text>
      </view>
    </view>

    <view v-if="!hasKeyword" class="search-content">
      <view class="section-card">
        <view class="section-head">
          <text class="section-title">历史搜索</text>
        </view>
        <view class="hot-list">
          <view
            v-for="item in searchHistory"
            :key="item"
            class="hot-pill history-pill"
            @click="applyHistoryKeyword(item)"
          >
            <text>{{ item }}</text>
          </view>
        </view>
      </view>

      <view class="section-card">
        <view class="section-head">
          <text class="section-title">热门搜索</text>
        </view>
        <view class="hot-list">
          <view
            v-for="item in hotKeywords"
            :key="item"
            class="hot-pill"
            @click="applyHotKeyword(item)"
          >
            <text>{{ item }}</text>
          </view>
        </view>
      </view>
    </view>

    <view v-else class="search-content">
      <view class="result-head">
        <text class="result-title">搜索结果</text>
        <text class="result-count">共 {{ filteredCoupons.length }} 条</text>
      </view>

      <view v-if="filteredCoupons.length" class="promo-grid">
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
            <text class="promo-card-title">{{ item.couponName }}</text>
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

      <view v-else class="empty-state">
        <text class="empty-text">没有找到匹配的优惠信息</text>
      </view>
    </view>
  </view>
</template>

<script>
import { getPromotionCouponList } from '@/api/promotion.js'
import NavBar from '@/components/nav-bar/nav-bar.vue'

export default {
  components: { NavBar },
  data() {
    return {
      searchKeyword: '',
      couponList: [],
      searchHistory: ['食堂满减', '咖啡第二杯', '打印折扣', '校园卡充值', '超市特价'],
      hotKeywords: ['食堂优惠', '校园卡', '超市', '打印店', '理发店', '咖啡'],
      categoryEmojiMap: {
        coupon: '🎫',
        card: '🎓',
        ad: '📦',
        life: '🧺'
      }
    }
  },
  computed: {
    hasKeyword() {
      return Boolean((this.searchKeyword || '').trim())
    },
    filteredCoupons() {
      const keyword = (this.searchKeyword || '').trim().toLowerCase()
      if (!keyword) {
        return []
      }
      return this.couponList.filter((item) => {
        const haystack = [item.couponName, item.description, item.pickupLocation]
          .filter(Boolean)
          .join(' ')
          .toLowerCase()
        return haystack.includes(keyword)
      })
    }
  },
  onLoad(options) {
    if (options && options.keyword) {
      this.searchKeyword = decodeURIComponent(options.keyword)
    }
    this.loadCouponList()
  },
  methods: {
    async loadCouponList() {
      try {
        const res = await getPromotionCouponList()
        this.couponList = Array.isArray(res.data) ? res.data : []
      } catch (error) {
        this.couponList = []
      }
    },
    handleSearch() {
      this.searchKeyword = (this.searchKeyword || '').trim()
    },
    applyHotKeyword(keyword) {
      this.searchKeyword = keyword
    },
    applyHistoryKeyword(keyword) {
      this.searchKeyword = keyword
    },
    clearSearch() {
      this.searchKeyword = ''
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
.search-page {
  min-height: 100vh;
  background: linear-gradient(145deg, #f0e9de 0%, #e5ddd0 50%, #ded3c5 100%);
}

.search-top {
  padding: 24rpx;
}

.search-box {
  flex: 1;
  display: flex;
  align-items: center;
  gap: 12rpx;
  padding: 22rpx 24rpx;
  background: #fff;
  border-radius: 28rpx;
  border: 2rpx solid #e5ded3;
}

.search-icon {
  font-size: 36rpx;
  color: #9c8b74;
}

.search-input {
  flex: 1;
  font-size: 26rpx;
  color: #6b5d4d;
}

.clear-btn {
  font-size: 34rpx;
  color: #9c8b74;
  line-height: 1;
}

.search-content {
  padding: 24rpx;
}

.section-card {
  background: rgba(255, 255, 255, 0.82);
  border: 2rpx solid #e5ded3;
  border-radius: 32rpx;
  padding: 28rpx;
  margin-bottom: 20rpx;
}

.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.section-title,
.result-title {
  font-size: 30rpx;
  font-weight: 700;
  color: #2c2416;
}

.hot-list {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
  margin-top: 20rpx;
}

.hot-pill {
  padding: 16rpx 28rpx;
  background: #fff;
  border: 2rpx solid #e5ded3;
  border-radius: 999rpx;
  font-size: 24rpx;
  color: #6b5d4d;
}

.history-pill {
  background: #f8f3eb;
}

.result-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20rpx;
}

.result-count {
  font-size: 22rpx;
  color: #9c8b74;
}

.promo-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 20rpx;
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

.promo-card-title {
  display: block;
  font-size: 30rpx;
  font-weight: 700;
  color: #2c2416;
  line-height: 1.35;
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
