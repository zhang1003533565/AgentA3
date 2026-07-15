<template>
  <view class="page-root">
    <view class="screen">
      <view class="container">
        <nav-bar title="商品详情" :fixed="true" :placeholder="true" />

        <scroll-view scroll-y class="page-body">
          <swiper
            v-if="item.images.length"
            class="hero"
            :current="imageIndex"
            @change="onImageChange"
          >
            <swiper-item v-for="img in item.images" :key="img">
              <image class="hero-img" :src="img" mode="aspectFill" @click="previewImage(img)" />
            </swiper-item>
          </swiper>
          <view v-else class="hero hero-empty">
            <text>{{ emoji(item.id) }}</text>
          </view>
          <view v-if="item.images.length > 1" class="counter">
            {{ imageIndex + 1 }}/{{ item.images.length }}
          </view>

          <view class="card main-card">
            <view class="price-row">
              <view class="price"><text>¥</text>{{ priceText }}</view>
              <view class="status" :class="statusClass">{{ statusText }}</view>
            </view>
            <view class="title">{{ item.title || '未命名商品' }}</view>
            <view class="meta-row">
              <text>{{ categoryText }}</text>
              <text>{{ conditionText }}</text>
            </view>
          </view>

          <view class="card">
            <view class="card-title">商品描述</view>
            <view class="description">{{ item.description || '卖家暂未填写详细描述' }}</view>
          </view>

          <view class="card">
            <view class="card-title">交易地点</view>
            <view class="info-list">
              <view class="info-row">
                <text class="info-label">校区</text>
                <text class="info-value">{{ item.campusName || '校内' }}</text>
              </view>
              <view class="info-row">
                <text class="info-label">交易区域</text>
                <text class="info-value">{{ item.tradeLocation || item.location || '校内自提' }}</text>
              </view>
              <view class="info-row">
                <text class="info-label">取货点</text>
                <text class="info-value">{{ item.pickupPoint || item.location || '待卖家确认' }}</text>
              </view>
            </view>
          </view>

          <view class="card stats-card">
            <view class="stat">
              <text class="stat-num">{{ item.heatScore || 0 }}</text>
              <text class="stat-label">热度</text>
            </view>
            <view class="stat">
              <text class="stat-num">{{ item.inquiryCount || 0 }}</text>
              <text class="stat-label">咨询</text>
            </view>
            <view class="stat">
              <text class="stat-num">{{ item.viewCount || 0 }}</text>
              <text class="stat-label">浏览</text>
            </view>
          </view>

          <view class="card seller-card">
            <view class="avatar">{{ sellerInitial }}</view>
            <view class="seller-info">
              <view class="seller-name">{{ item.sellerName || '校园用户' }}</view>
              <view class="seller-time">{{ formatTime(item.createTime) }}发布</view>
            </view>
          </view>
        </scroll-view>

        <view class="bottom-bar">
          <view class="bottom-status">
            <text>{{ statusText }}</text>
            <text>{{ item.pickupPoint || item.tradeLocation || item.campusName || '校内自提' }}</text>
          </view>
          <button class="bottom-btn" disabled>聊天与交易后续迁移</button>
        </view>
      </view>
    </view>
  </view>
</template>

<script>
import NavBar from '@/components/nav-bar/nav-bar.vue'
import { getSecondhandItemDetail } from '@/api/secondhand'
import { getMarketCategoryLabel, getMarketSubcategoryLabel } from '../utils/marketCategories'

const EMOJIS = ['📱', '💻', '📷', '🎧', '⌚', '📚', '👟', '🧥', '🪑', '🏠', '🎮', '🎸', '🖥️', '📦']
const CONDITION_LABELS = {
  1: '全新',
  2: '几乎全新',
  3: '轻微使用',
  4: '明显使用',
  5: '功能正常'
}

function normalizeItem(raw = {}) {
  const seller = raw.seller || {}
  return {
    id: raw.id,
    title: raw.title || raw.name || '',
    description: raw.description || raw.desc || '',
    price: raw.price,
    images: Array.isArray(raw.images) ? raw.images : [],
    categoryId: raw.categoryId || raw.category?.id || raw.category,
    subcategoryId: raw.subcategoryId || raw.subCategoryId,
    condition: raw.condition,
    location: raw.location || '',
    campusId: raw.campusId || '',
    campusName: raw.campusName || '',
    tradeLocation: raw.tradeLocation || '',
    pickupPoint: raw.pickupPoint || '',
    status: Number(raw.status ?? 0),
    heatScore: Number(raw.heatScore || 0),
    inquiryCount: Number(raw.inquiryCount || 0),
    viewCount: Number(raw.viewCount || 0),
    sellerName: seller.username || raw.sellerName || raw.userName || '',
    createTime: raw.createTime || raw.ctime || ''
  }
}

export default {
  components: {
    NavBar
  },
  data() {
    return {
      itemId: null,
      item: normalizeItem(),
      imageIndex: 0
    }
  },
  computed: {
    priceText() {
      const value = Number(this.item.price)
      return Number.isFinite(value) ? value.toFixed(value % 1 === 0 ? 0 : 2) : '--'
    },
    statusText() {
      if (this.item.status === 5) return '交易中'
      if (this.item.status === 3) return '已售出'
      if (this.item.status === 4) return '已下架'
      return '在售'
    },
    statusClass() {
      if (this.item.status === 5) return 'is-pending'
      if (this.item.status === 3) return 'is-sold'
      if (this.item.status === 4) return 'is-offline'
      return 'is-online'
    },
    categoryText() {
      const primary = getMarketCategoryLabel(this.item.categoryId)
      const secondary = getMarketSubcategoryLabel(this.item.categoryId, this.item.subcategoryId)
      return secondary && secondary !== primary ? `${primary} / ${secondary}` : primary
    },
    conditionText() {
      return CONDITION_LABELS[this.item.condition] || '成色待确认'
    },
    sellerInitial() {
      return (this.item.sellerName || '校').slice(0, 1)
    }
  },
  async onLoad(options) {
    this.itemId = options.id
    await this.loadItem()
  },
  methods: {
    async loadItem() {
      if (!this.itemId) {
        uni.showToast({ title: '缺少商品信息', icon: 'none' })
        return
      }
      try {
        const res = await getSecondhandItemDetail(this.itemId)
        this.item = normalizeItem(res?.data || {})
      } catch (e) {
        console.error('加载商品详情失败', e)
        uni.showToast({ title: '商品不存在', icon: 'none' })
        setTimeout(() => uni.navigateBack(), 1200)
      }
    },
    emoji(id) {
      return EMOJIS[(Number(id) || 0) % EMOJIS.length]
    },
    onImageChange(event) {
      this.imageIndex = event.detail.current || 0
    },
    previewImage(src) {
      uni.previewImage({
        urls: this.item.images,
        current: src
      })
    },
    formatTime(value) {
      if (!value) return ''
      const time = typeof value === 'string' ? new Date(value.replace(/-/g, '/')).getTime() : value
      const diff = Date.now() - time
      if (!Number.isFinite(diff)) return ''
      if (diff < 60000) return '刚刚'
      if (diff < 3600000) return `${Math.floor(diff / 60000)}分钟前`
      if (diff < 86400000) return `${Math.floor(diff / 3600000)}小时前`
      const date = new Date(time)
      return `${date.getMonth() + 1}/${date.getDate()}`
    }
  }
}
</script>

<style scoped>
.page-root,
.screen {
  width: 100%;
  min-height: 100vh;
  background: #f0f5fa;
}

.container {
  width: 100%;
  max-width: 430px;
  min-height: 100vh;
  margin: 0 auto;
  padding: 0 16rpx;
  box-sizing: border-box;
  background: #e8f0f8;
  position: relative;
}

.page-body {
  height: calc(100vh - 176rpx);
  padding-bottom: 148rpx;
  box-sizing: border-box;
}

.hero {
  width: 100%;
  height: 560rpx;
  border-radius: 0 0 24rpx 24rpx;
  overflow: hidden;
  background: linear-gradient(135deg, rgba(123, 168, 212, 0.35), rgba(92, 138, 184, 0.35));
  position: relative;
}

.hero-img {
  width: 100%;
  height: 100%;
}

.hero-empty {
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 144rpx;
}

.counter {
  position: absolute;
  top: 520rpx;
  right: 40rpx;
  padding: 8rpx 18rpx;
  border-radius: 999rpx;
  background: rgba(0, 0, 0, 0.58);
  color: #fff;
  font-size: 22rpx;
}

.card {
  margin: 24rpx 0;
  padding: 28rpx;
  border-radius: 20rpx;
  background: #fff;
  box-shadow: 0 6rpx 18rpx rgba(43, 68, 94, 0.08);
}

.main-card {
  margin-top: 28rpx;
}

.price-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20rpx;
}

.price {
  color: #f26a42;
  font-size: 52rpx;
  font-weight: 900;
  line-height: 1;
}

.price text {
  font-size: 30rpx;
}

.status {
  flex-shrink: 0;
  padding: 10rpx 20rpx;
  border-radius: 999rpx;
  font-size: 24rpx;
  font-weight: 700;
}

.is-online {
  background: rgba(75, 154, 104, 0.14);
  color: #2f8a58;
}

.is-pending {
  background: rgba(242, 144, 64, 0.16);
  color: #c46b17;
}

.is-sold,
.is-offline {
  background: rgba(84, 99, 116, 0.14);
  color: #546374;
}

.title {
  margin-top: 20rpx;
  color: #172331;
  font-size: 36rpx;
  font-weight: 800;
  line-height: 1.4;
}

.meta-row {
  display: flex;
  gap: 16rpx;
  flex-wrap: wrap;
  margin-top: 18rpx;
}

.meta-row text {
  padding: 8rpx 16rpx;
  border-radius: 999rpx;
  background: #edf4fb;
  color: #5c7894;
  font-size: 22rpx;
  font-weight: 700;
}

.card-title {
  margin-bottom: 18rpx;
  color: #172331;
  font-size: 28rpx;
  font-weight: 800;
}

.description {
  color: #546374;
  font-size: 28rpx;
  line-height: 1.7;
  white-space: pre-wrap;
}

.info-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 24rpx;
  padding: 16rpx 0;
  border-bottom: 1rpx solid rgba(84, 99, 116, 0.1);
}

.info-row:last-child {
  border-bottom: 0;
}

.info-label {
  color: #7d8c9c;
  font-size: 26rpx;
}

.info-value {
  flex: 1;
  color: #172331;
  font-size: 26rpx;
  font-weight: 700;
  text-align: right;
}

.stats-card {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 16rpx;
}

.stat {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8rpx;
}

.stat-num {
  color: #172331;
  font-size: 32rpx;
  font-weight: 900;
}

.stat-label {
  color: #7d8c9c;
  font-size: 22rpx;
}

.seller-card {
  display: flex;
  align-items: center;
  gap: 20rpx;
}

.avatar {
  width: 88rpx;
  height: 88rpx;
  border-radius: 50%;
  background: linear-gradient(135deg, #7ba8d4, #5c8ab8);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 34rpx;
  font-weight: 800;
}

.seller-name {
  color: #172331;
  font-size: 28rpx;
  font-weight: 800;
}

.seller-time {
  margin-top: 6rpx;
  color: #7d8c9c;
  font-size: 22rpx;
}

.bottom-bar {
  position: fixed;
  left: 50%;
  bottom: 0;
  width: 100%;
  max-width: 430px;
  transform: translateX(-50%);
  display: flex;
  align-items: center;
  gap: 20rpx;
  padding: 22rpx 32rpx 48rpx;
  box-sizing: border-box;
  background: rgba(232, 240, 248, 0.96);
  border-top: 1rpx solid rgba(84, 99, 116, 0.12);
}

.bottom-status {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 6rpx;
}

.bottom-status text:first-child {
  color: #172331;
  font-size: 26rpx;
  font-weight: 800;
}

.bottom-status text:last-child {
  color: #7d8c9c;
  font-size: 22rpx;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.bottom-btn {
  width: 280rpx;
  height: 80rpx;
  border-radius: 22rpx;
  background: #b7c7d7;
  color: #fff;
  font-size: 26rpx;
  font-weight: 800;
}

.bottom-btn::after {
  border: none;
}
</style>
