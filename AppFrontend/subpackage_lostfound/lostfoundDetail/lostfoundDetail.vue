<template>
  <view class="page-root">
    <view class="screen">
      <view class="container">
        <nav-bar title="商品详情" :fixed="true" :placeholder="true" />

        <scroll-view scroll-y class="page-body">
          <view class="hero-wrap">
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
            <view class="pickup-row" @click="contactSeller">
              <view class="pickup-main">
                <view class="pickup-icon-wrap"><image class="pickup-icon" src="/static/icons/mi--location.svg" mode="aspectFit" /></view>
                <text class="pickup-label">取货地点：</text>
                <text class="pickup-value">校内 · {{ pickupText }}</text>
              </view>
              <text class="pickup-arrow">›</text>
            </view>
          </view>

          <view class="card">
            <view class="card-title">商品描述</view>
            <view class="description">{{ item.description || '卖家暂未填写详细描述' }}</view>
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

          <view class="card seller-card" @click="contactSeller">
            <view class="avatar">{{ sellerInitial }}</view>
            <view class="seller-info">
              <view class="seller-name">{{ item.sellerName || '校园用户' }}</view>
              <view class="seller-time">{{ formatTime(item.createTime) }}发布</view>
            </view>
            <view class="seller-action">查看主页 ›</view>
          </view>

          <view class="safety-card">
            <image class="shield-icon" src="/static/icons/ant-design--safety-outlined.svg" mode="aspectFit" />
            <view class="safety-copy">
              <view class="safety-title">交易提醒</view>
              <view class="safety-desc">建议当面交易，沟通后再交换联系方式，注意财产安全</view>
            </view>
            <text class="safety-arrow">›</text>
          </view>
        </scroll-view>

        <view class="bottom-bar">
          <button class="contact-button" @click="contactSeller">
            <view class="chat-outline"></view>
            <text>联系卖家</text>
          </button>
        </view>
      </view>
    </view>
  </view>
</template>

<script>
import NavBar from '@/components/nav-bar/nav-bar.vue'
import { createOrGetChatSession, getSecondhandItemDetail } from '@/api/secondhand'
import { getMarketCategoryLabel, getMarketSubcategoryLabel } from '../utils/marketCategories'

const EMOJIS = ['📱', '💻', '📷', '🎧', '⌚', '📚', '👟', '🧥', '🪑', '🏠', '🎮', '🎸', '🖥️', '📦']
const BROWSE_HISTORY_KEY = 'market_browse_history'

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
    sellerId: raw.sellerId || seller.id || raw.userId || raw.publisherId || '',
    sellerName: seller.username || raw.sellerName || raw.userName || '',
    isFavorited: Boolean(raw.isFavorited),
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
      if (this.item.status === 5) return '沟通中'
      if (this.item.status === 3) return '已完成'
      if (this.item.status === 4) return '已下架'
      return '出售中'
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
    },
    pickupText() {
      return this.item.pickupPoint || this.item.location || '待卖家确认'
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
        this.saveBrowseHistory()
      } catch (e) {
        console.error('加载商品详情失败', e)
        uni.showToast({ title: '商品不存在', icon: 'none' })
        setTimeout(() => uni.navigateBack(), 1200)
      }
    },
    saveBrowseHistory() {
      if (!this.item.id) return
      try {
        const current = uni.getStorageSync(BROWSE_HISTORY_KEY)
        const list = Array.isArray(current) ? current : []
        const nextItem = {
          id: this.item.id,
          title: this.item.title,
          image: this.item.images?.[0] || '',
          campusName: this.item.campusName,
          tradeLocation: this.item.tradeLocation || this.item.pickupPoint,
          viewTime: Date.now()
        }
        const next = [nextItem, ...list.filter((item) => item.id !== this.item.id)].slice(0, 50)
        uni.setStorageSync(BROWSE_HISTORY_KEY, next)
      } catch (e) {
        console.warn('保存浏览记录失败', e)
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
    async contactSeller() {
      if (!this.item.id) return
      try {
        const res = await createOrGetChatSession(this.item.id)
        const sessionId = res?.data?.sessionId || res?.data?.id
        const query = sessionId ? `sessionId=${sessionId}` : `itemId=${this.item.id}`
        uni.navigateTo({ url: `/subpackage_lostfound/lostfoundChat/lostfoundChat?${query}` })
      } catch (e) {
        console.error('创建聊天失败', e)
        uni.showToast({ title: '暂时无法联系卖家', icon: 'none' })
      }
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
  background: #f5f5f5;
}

.container {
  width: 100%;
  max-width: 430px;
  min-height: 100vh;
  margin: 0 auto;
  padding: 0;
  box-sizing: border-box;
  position: relative;
}

.page-body {
  height: calc(100vh - 176rpx);
  padding: 22rpx 0 0;
  box-sizing: border-box;
}

.hero-wrap {
  width: 100%;
  aspect-ratio: 1 / 1;
  position: relative;
  overflow: hidden;
  border-radius: 22rpx;
  background: linear-gradient(135deg, #dbe7f0, #f3f7fa);
  box-shadow: 0 8rpx 22rpx rgba(31, 48, 64, 0.08);
}

.hero {
  width: 100%;
  height: 100%;
}

.hero-img {
  width: 100%;
  height: 100%;
}

.hero-empty {
  width: 100%;
  height: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 150rpx;
}

.counter {
  position: absolute;
  right: 22rpx;
  bottom: 22rpx;
  min-width: 72rpx;
  padding: 9rpx 18rpx;
  border-radius: 999rpx;
  background: rgba(26, 32, 40, 0.62);
  color: #fff;
  font-size: 23rpx;
  font-weight: 800;
  text-align: center;
  box-sizing: border-box;
}

.card,
.safety-card {
  margin-top: 22rpx;
  padding: 28rpx;
  border-radius: 22rpx;
  background: #fff;
  box-shadow: 0 8rpx 24rpx rgba(31, 48, 64, 0.08);
  border: 1rpx solid rgba(106, 126, 145, 0.08);
  box-sizing: border-box;
}

.safety-card {
  margin-bottom: 120rpx;
}

.main-card {
  padding-bottom: 0;
  overflow: hidden;
}

.price-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 24rpx;
}

.price {
  color: #ea6948;
  font-size: 56rpx;
  font-weight: 900;
  line-height: 1;
  letter-spacing: 0;
}

.price text {
  margin-right: 2rpx;
  font-size: 30rpx;
  font-weight: 900;
}

.status {
  flex-shrink: 0;
  padding: 12rpx 24rpx;
  border-radius: 999rpx;
  font-size: 25rpx;
  font-weight: 900;
}

.is-online {
  background: #dff1e8;
  color: #2f8a58;
}

.is-pending {
  background: #fff0dd;
  color: #bd711d;
}

.is-sold,
.is-offline {
  background: #edf1f5;
  color: #667584;
}

.title {
  margin-top: 22rpx;
  color: #111c2b;
  font-size: 36rpx;
  font-weight: 900;
  line-height: 1.35;
  letter-spacing: 0;
}

.meta-row {
  display: flex;
  gap: 16rpx;
  flex-wrap: wrap;
  margin-top: 22rpx;
  padding-bottom: 28rpx;
}

.meta-row text {
  padding: 9rpx 18rpx;
  border-radius: 999rpx;
  background: #edf4fb;
  color: #5f7c96;
  font-size: 23rpx;
  font-weight: 800;
}

.pickup-row {
  margin: 0 -28rpx;
  padding: 24rpx 28rpx;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18rpx;
  border-top: 1rpx solid #edf2f6;
}

.pickup-main {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  color: #172331;
  font-size: 27rpx;
  font-weight: 800;
  line-height: 1.25;
}

.pickup-icon-wrap {
  width: 42rpx;
  height: 42rpx;
  margin-right: 12rpx;
  border-radius: 13rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #edf4fb;
  flex-shrink: 0;
}

.pickup-icon {
  width: 32rpx;
  height: 32rpx;
  display: block;
}

.pickup-label {
  flex-shrink: 0;
  color: #26384a;
}

.pickup-value {
  min-width: 0;
  color: #26384a;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.pickup-arrow,
.safety-arrow {
  flex-shrink: 0;
  color: #7c8b9a;
  font-size: 42rpx;
  line-height: 1;
}

.card-title {
  margin-bottom: 18rpx;
  color: #111c2b;
  font-size: 30rpx;
  font-weight: 900;
}

.description {
  color: #3f4e5f;
  font-size: 27rpx;
  line-height: 1.7;
  white-space: pre-wrap;
}

.stats-card {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 0;
  padding: 24rpx 0;
}

.stat {
  min-height: 92rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 8rpx;
  border-right: 1rpx solid #edf2f6;
}

.stat:last-child {
  border-right: 0;
}

.stat-num {
  color: #111c2b;
  font-size: 33rpx;
  font-weight: 900;
}

.stat-label {
  color: #6f7f8f;
  font-size: 23rpx;
  font-weight: 700;
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
  background: linear-gradient(135deg, #82aee0, #5f8fc4);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 34rpx;
  font-weight: 900;
}

.seller-info {
  flex: 1;
  min-width: 0;
}

.seller-name {
  color: #111c2b;
  font-size: 30rpx;
  font-weight: 900;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.seller-time {
  margin-top: 8rpx;
  color: #788898;
  font-size: 23rpx;
  font-weight: 600;
}

.seller-action {
  flex-shrink: 0;
  padding: 12rpx 20rpx;
  border-radius: 999rpx;
  border: 1rpx solid #d8e3ec;
  color: #526579;
  font-size: 23rpx;
  font-weight: 800;
}

.safety-card {
  display: flex;
  align-items: center;
  gap: 18rpx;
  background: linear-gradient(135deg, #f7fbff, #eaf2fa);
}

.shield-icon {
  width: 42rpx;
  height: 42rpx;
  flex-shrink: 0;
  display: block;
}

.safety-copy {
  flex: 1;
  min-width: 0;
}

.safety-title {
  color: #172331;
  font-size: 28rpx;
  font-weight: 900;
}

.safety-desc {
  margin-top: 8rpx;
  color: #607284;
  font-size: 23rpx;
  line-height: 1.45;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.bottom-bar {
  position: fixed;
  left: 50%;
  bottom: 0;
  width: 100%;
  max-width: 430px;
  transform: translateX(-50%);
  padding: 12rpx 32rpx calc(12rpx + env(safe-area-inset-bottom));
  box-sizing: border-box;
  background: #fff;
  border-top: 1rpx solid rgba(132, 151, 168, 0.14);
  z-index: 20;
}
.contact-button {
  width: 100%;
  height: 96rpx;
  border-radius: 32rpx;
  background: #8ea6ba;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 14rpx;
  font-size: 30rpx;
  font-weight: 900;
  line-height: 1;
  box-shadow: 0 10rpx 22rpx rgba(85, 112, 136, 0.22);
}

.contact-button::after {
  border: none;
}

.chat-outline {
  width: 34rpx;
  height: 28rpx;
  border: 4rpx solid #fff;
  border-radius: 8rpx;
  position: relative;
  box-sizing: border-box;
}

.chat-outline::after {
  content: '';
  position: absolute;
  left: 6rpx;
  bottom: -10rpx;
  width: 12rpx;
  height: 12rpx;
  border-left: 4rpx solid #fff;
  border-bottom: 4rpx solid #fff;
  transform: rotate(-18deg);
  background: transparent;
}
</style>
