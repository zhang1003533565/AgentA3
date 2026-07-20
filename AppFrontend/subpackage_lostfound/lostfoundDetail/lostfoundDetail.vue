<template>
  <view class="page-root">
    <view class="screen">
      <view class="container">
        <common-page-header title="商品详情" :fixed="true" :placeholder="true" :showBack="true" />

        <scroll-view scroll-y class="page-body" :show-scrollbar="false" :style="{ height: pageBodyHeight + 'px' }">
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
              <view class="meta-tags">
                <text>{{ categoryText }}</text>
                <text>{{ conditionText }}</text>
              </view>
              <button
                class="favorite-button"
                :class="{ 'favorite-button--active': item.isFavorited }"
                :disabled="favoriteLoading"
                @click.stop="toggleFavorite"
              >
                <image class="favorite-icon" src="/static/icons/line/star.svg" mode="aspectFit" />
                <text>{{ item.isFavorited ? '已收藏' : '收藏' }}</text>
              </button>
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
          <view v-if="isSeller" class="seller-actions">
            <button class="manage-button" @click="handleSellerPrimaryAction">{{ sellerPrimaryButtonText }}</button>
            <button class="offline-button" :disabled="sellerSecondaryDisabled" @click="handleSellerSecondaryAction">{{ sellerSecondaryButtonText }}</button>
          </view>
          <button v-else-if="isCompletedTradeBuyer" class="contact-button" @click="openTradeRecords">查看交易记录</button>
          <view v-else-if="isItemCompleted" class="trade-locked-button">已售出</view>
          <view v-else-if="isCurrentTradeBuyer" class="buyer-trade-actions">
            <view class="trade-status-button">交易进行中</view>
            <button class="contact-button contact-button--compact" @click="contactSeller">
              <view class="chat-outline"></view>
              <text>联系卖家</text>
            </button>
          </view>
          <button v-else class="contact-button" @click="contactSeller">
            <view class="chat-outline"></view>
            <text>{{ contactButtonText }}</text>
          </button>
        </view>
      </view>
    </view>
  </view>
</template>

<script>
import CommonPageHeader from '@/components/common-page-header/common-page-header.vue'
import {
  favoriteSecondhandItem,
  getSecondhandItemDetail,
  getTradeRecords,
  offlineSecondhandItem,
  unfavoriteSecondhandItem
} from '@/api/secondhand'
import { getToken, getUserInfo } from '@/utils/storage'
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

function firstValue(...values) {
  return values.find((value) => value !== undefined && value !== null && value !== '')
}

function normalizeId(value) {
  if (value === undefined || value === null || value === '') return ''
  if (typeof value !== 'object') return String(value)
  return normalizeId(firstValue(
    value.id,
    value.userId,
    value.uid,
    value.ownerId,
    value.sellerId,
    value.publisherId
  ))
}

function parseStoredUserInfo() {
  const userInfo = getUserInfo()
  if (userInfo) return userInfo
  try {
    const raw = uni.getStorageSync('userInfo')
    if (!raw) return {}
    return typeof raw === 'string' ? JSON.parse(raw) : raw
  } catch {
    return {}
  }
}

function decodeTokenPayload(token) {
  if (!token || typeof atob !== 'function') return {}
  try {
    const payload = token.split('.')[1]
    if (!payload) return {}
    const base64 = payload.replace(/-/g, '+').replace(/_/g, '/')
    const decoded = atob(base64)
    try {
      const json = decodeURIComponent(decoded.split('').map((char) => {
        return `%${(`00${char.charCodeAt(0).toString(16)}`).slice(-2)}`
      }).join(''))
      return JSON.parse(json)
    } catch {
      return JSON.parse(decoded)
    }
  } catch {
    return {}
  }
}

function getCurrentUserId() {
  const userInfo = parseStoredUserInfo()
  const nestedUser = userInfo.user || userInfo.profile || userInfo.data || {}
  const tokenPayload = decodeTokenPayload(getToken())
  return normalizeId(userInfo) || normalizeId(nestedUser) || normalizeId(tokenPayload)
}

function normalizeItem(raw = {}) {
  const seller = raw.seller || raw.user || raw.owner || raw.publisher || {}
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
    sellerId: normalizeId(raw.sellerId) || normalizeId(seller) || normalizeId(raw.userId) || normalizeId(raw.ownerId) || normalizeId(raw.publisherId),
    sellerName: seller.username || raw.sellerName || raw.userName || '',
    isFavorited: Boolean(raw.isFavorited),
    createTime: raw.createTime || raw.ctime || ''
  }
}

export default {
  components: {
    CommonPageHeader
  },
  data() {
    return {
      itemId: null,
      item: normalizeItem(),
      activeTrade: null,
      completedTrade: null,
      favoriteLoading: false,
      pageBodyHeight: 0,
      imageIndex: 0
    }
  },
  computed: {
    priceText() {
      const value = Number(this.item.price)
      return Number.isFinite(value) ? value.toFixed(value % 1 === 0 ? 0 : 2) : '--'
    },
    statusText() {
      if (this.activeTrade && this.item.status === 2) return '交易中'
      if (this.item.status === 3) return '已售'
      if (this.item.status === 4) return '已下架'
      return '在售'
    },
    statusClass() {
      if (this.activeTrade && this.item.status === 2) return 'is-trading'
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
    },
    isSeller() {
      return Boolean(this.currentUserId && this.item.sellerId && String(this.currentUserId) === String(this.item.sellerId))
    },
    currentUserId() {
      return getCurrentUserId()
    },
    isCurrentTradeBuyer() {
      return Boolean(
        this.activeTrade &&
        this.currentUserId &&
        String(this.activeTrade.buyerId) === String(this.currentUserId)
      )
    },
    isItemCompleted() {
      return Number(this.item.status) === 3 || this.completedTrade?.status === 'COMPLETED'
    },
    isCompletedTradeBuyer() {
      return Boolean(
        this.isItemCompleted &&
        this.completedTrade &&
        this.currentUserId &&
        String(this.completedTrade.buyerId) === String(this.currentUserId)
      )
    },
    canOfflineItem() {
      return Number(this.item.status) === 2
    },
    sellerPrimaryButtonText() {
      if (this.isItemCompleted) return '交易已完成'
      return this.activeTrade ? '查看交易' : '管理商品'
    },
    sellerSecondaryButtonText() {
      if (this.isItemCompleted) return '管理商品'
      return this.activeTrade ? '管理商品' : '下架商品'
    },
    sellerSecondaryDisabled() {
      return !this.isItemCompleted && !this.activeTrade && !this.canOfflineItem
    },
    contactButtonText() {
      return '联系卖家'
    }
  },
  async onLoad(options) {
    this.itemId = options.id
    this.calcPageBodyHeight()
    await this.loadItem()
  },
  methods: {
    calcPageBodyHeight() {
      this.$nextTick(() => {
        const query = uni.createSelectorQuery().in(this)
        query.select('.page-body').boundingClientRect()
        query.select('.bottom-bar').boundingClientRect()
        query.exec((res) => {
          if (!res || !res[0]) return
          const bodyTop = res[0] ? res[0].top : 0
          const barHeight = res[1] ? res[1].height : 0
          const sysInfo = uni.getSystemInfoSync()
          const vh = sysInfo.windowHeight || 0
          this.pageBodyHeight = Math.max(0, vh - bodyTop - barHeight)
        })
      })
    },
    async loadItem() {
      if (!this.itemId) {
        uni.showToast({ title: '缺少商品信息', icon: 'none' })
        return
      }
      try {
        const res = await getSecondhandItemDetail(this.itemId)
        this.item = normalizeItem(res?.data || {})
        console.log('[DETAIL AUTH]', {
          currentUserId: this.currentUserId,
          sellerId: this.item.sellerId,
          item: this.item
        })
        this.saveBrowseHistory()
        await this.loadActiveTrade()
      } catch (e) {
        console.error('加载商品详情失败', e)
        uni.showToast({ title: '商品不存在', icon: 'none' })
        setTimeout(() => uni.navigateBack(), 1200)
      }
    },
    async loadActiveTrade() {
      this.activeTrade = null
      this.completedTrade = null
      if (!this.item.id) return
      if (!this.currentUserId) return
      try {
        const res = await getTradeRecords({ current: 1, size: 100 })
        const records = Array.isArray(res?.data?.records) ? res.data.records : (Array.isArray(res?.data) ? res.data : [])
        const itemRecords = records.filter((record) => Number(record.itemId) === Number(this.item.id))
        this.activeTrade = itemRecords.find((record) => {
          return ['WAIT_CONFIRM', 'TRADING'].includes(record.status)
        }) || null
        this.completedTrade = itemRecords.find((record) => {
          return Number(record.itemId) === Number(this.item.id) &&
            record.status === 'COMPLETED'
        }) || null
      } catch (e) {
        console.warn('查询交易记录失败', e)
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
    async toggleFavorite() {
      if (!this.item.id || this.favoriteLoading) return
      this.favoriteLoading = true
      const nextFavorited = !this.item.isFavorited
      try {
        if (nextFavorited) {
          await favoriteSecondhandItem(this.item.id)
        } else {
          await unfavoriteSecondhandItem(this.item.id)
        }
        this.item = {
          ...this.item,
          isFavorited: nextFavorited
        }
        uni.showToast({
          title: nextFavorited ? '已收藏' : '已取消收藏',
          icon: 'none'
        })
      } catch (e) {
        console.error('更新收藏失败', e)
        uni.showToast({ title: e?.data?.msg || e?.msg || '操作失败', icon: 'none' })
      } finally {
        this.favoriteLoading = false
      }
    },
    async contactSeller() {
      if (!this.item.id) return
      if (this.isSeller) {
        uni.showToast({ title: '这是您发布的商品', icon: 'none' })
        return
      }
      if (this.isItemCompleted) {
        if (this.isCompletedTradeBuyer) {
          this.openTradeRecords()
          return
        }
        uni.showToast({ title: '商品已售出', icon: 'none' })
        return
      }
      const sellerParam = this.item.sellerId ? `&sellerId=${this.item.sellerId}` : ''
      uni.navigateTo({ url: `/subpackage_lostfound/lostfoundChat/lostfoundChat?itemId=${this.item.id}${sellerParam}` })
    },
    openTradeRecords() {
      uni.navigateTo({ url: '/subpackage_lostfound/marketTradeRecords/marketTradeRecords' })
    },
    openTradeChat() {
      if (!this.activeTrade || !this.item.id) return
      const targetUserId = this.isSeller ? this.activeTrade.buyerId : this.item.sellerId
      const targetParam = targetUserId ? `&targetUserId=${targetUserId}` : ''
      uni.navigateTo({ url: `/subpackage_lostfound/lostfoundChat/lostfoundChat?itemId=${this.item.id}${targetParam}` })
    },
    handleSellerPrimaryAction() {
      if (this.isItemCompleted) {
        this.openTradeRecords()
        return
      }
      if (this.activeTrade) {
        this.openTradeChat()
        return
      }
      this.manageItem()
    },
    handleSellerSecondaryAction() {
      if (this.activeTrade) {
        this.manageItem()
        return
      }
      this.offlineItem()
    },
    manageItem() {
      uni.showToast({ title: '商品管理功能开发中', icon: 'none' })
    },
    async offlineItem() {
      if (!this.isSeller || !this.item.id) return
      if (!this.canOfflineItem) {
        uni.showToast({ title: '当前商品不可下架', icon: 'none' })
        return
      }
      try {
        await offlineSecondhandItem(this.item.id)
        uni.showToast({ title: '已下架', icon: 'none' })
        await this.loadItem()
      } catch (e) {
        console.error('下架商品失败', e)
        uni.showToast({ title: e?.data?.msg || e?.msg || '下架失败', icon: 'none' })
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

.is-trading {
  background: #edf4fb;
  color: #5f7890;
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
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
  margin-top: 22rpx;
  padding-bottom: 28rpx;
}

.meta-tags {
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 16rpx;
  flex-wrap: wrap;
}

.meta-tags text {
  padding: 9rpx 18rpx;
  border-radius: 999rpx;
  background: #edf4fb;
  color: #5f7c96;
  font-size: 23rpx;
  font-weight: 800;
}

.favorite-button {
  flex-shrink: 0;
  min-width: 132rpx;
  height: 56rpx;
  margin: 0;
  padding: 0 18rpx;
  border-radius: 999rpx;
  border: 1rpx solid #d8e3ec;
  background: #ffffff;
  color: #5f7c96;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8rpx;
  font-size: 23rpx;
  font-weight: 800;
  line-height: 1;
  box-sizing: border-box;
}

.favorite-button::after {
  border: none;
}

.favorite-button--active {
  background: #edf4fb;
  border-color: #d8e6f2;
  color: #4f7598;
}

.favorite-icon {
  width: 28rpx;
  height: 28rpx;
  display: block;
  opacity: 0.76;
}

.favorite-button--active .favorite-icon {
  opacity: 0.95;
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

.seller-actions {
  display: flex;
  gap: 18rpx;
}

.buyer-trade-actions {
  display: flex;
  gap: 18rpx;
}

.manage-button,
.offline-button {
  flex: 1;
  height: 96rpx;
  border-radius: 32rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 30rpx;
  font-weight: 900;
  line-height: 1;
}

.manage-button {
  background: #f2f6fa;
  color: #526579;
}

.offline-button {
  background: #8ea6ba;
  color: #fff;
  box-shadow: 0 10rpx 22rpx rgba(85, 112, 136, 0.18);
}

.offline-button[disabled] {
  background: #edf1f5;
  color: #9aa8b5;
  box-shadow: none;
}

.manage-button::after,
.offline-button::after {
  border: none;
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

.contact-button--compact {
  flex: 1;
  width: auto;
}

.contact-button::after {
  border: none;
}

.trade-status-button,
.trade-locked-button {
  height: 96rpx;
  border-radius: 32rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 30rpx;
  font-weight: 900;
  line-height: 1;
  box-sizing: border-box;
}

.trade-status-button {
  flex: 1;
  background: #edf4fb;
  color: #5f7890;
  border: 1rpx solid rgba(95, 120, 144, 0.18);
}

.trade-locked-button {
  width: 100%;
  background: #f1f3f5;
  color: #7b8792;
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
