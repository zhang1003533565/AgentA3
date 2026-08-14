<template>
  <view class="page-root">
    <view class="screen">
      <common-page-header :title="pageTitle" :fixed="true" :placeholder="true" :showBack="true" />

      <scroll-view
        scroll-y
        class="page-body"
        :show-scrollbar="false"
        refresher-enabled
        :refresher-triggered="refreshing"
        refresher-background="#F7F8FA"
        @refresherrefresh="refreshPage"
      >
        <view class="user-profile-card">
          <view class="avatar-wrap">
            <image
              v-if="avatarUrl"
              :src="avatarUrl"
              mode="aspectFill"
              class="avatar-img"
              @error="handleAvatarError"
            />
            <text v-else class="avatar-initial">{{ avatarInitial }}</text>
          </view>
          <view class="user-info">
            <view class="user-name">{{ userName }}</view>
            <view class="user-stats">
              <view class="stat-item">
                <text class="stat-num">{{ totalCount }}</text>
                <text class="stat-label">发布商品</text>
              </view>
            </view>
          </view>
        </view>

        <view v-if="!loading && items.length === 0" class="empty">
          <image
            class="empty-illustration"
            src="/static/illustrations/market-empty-favorites.svg"
            mode="aspectFit"
          />
          <view class="empty-title">暂无发布商品</view>
          <view class="empty-desc">TA 还没有发布任何闲置物品</view>
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

        <view class="bottom-spacer"></view>
      </scroll-view>
    </view>
  </view>
</template>

<script>
import CommonPageHeader from '@/components/common-page-header/common-page-header.vue'
import { getUserPublicItems } from '@/api/secondhand'

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

function formatTimestamp(value) {
  if (!value) return ''
  return String(value).replace('T', ' ')
}

export default {
  components: { CommonPageHeader },
  data() {
    return {
      userId: null,
      userName: '',
      avatarUrl: '',
      pageTitle: 'TA 的主页',
      totalCount: 0,
      loading: false,
      refreshing: false,
      items: []
    }
  },
  computed: {
    avatarInitial() {
      if (this.userName && this.userName.length > 0) return this.userName[0]
      return '用'
    }
  },
  onLoad(options) {
    this.userId = options.userId ? Number(options.userId) : null
    this.userName = options.userName ? decodeURIComponent(options.userName) : '校园用户'
    this.avatarUrl = options.avatar ? decodeURIComponent(options.avatar) : ''
    this.pageTitle = this.userName + '的主页'
    this.loadItems()
  },
  methods: {
    async loadItems() {
      if (!this.userId) {
        uni.showToast({ title: '用户不存在', icon: 'none' })
        return
      }
      this.loading = true
      try {
        const res = await getUserPublicItems(this.userId, { current: 1, size: 20 })
        this.items = (res.data?.records || []).map(normalize)
        this.totalCount = res.data?.total || 0
      } catch (e) {
        console.error(e)
        uni.showToast({ title: '加载失败', icon: 'none' })
      } finally {
        this.loading = false
      }
    },
    async refreshPage() {
      this.refreshing = true
      await this.loadItems()
      this.refreshing = false
    },
    handleAvatarError() {
      this.avatarUrl = ''
    },
    itemEmoji(id) {
      const i = (Number(id) || 0) % EMOJIS.length
      return EMOJIS[i]
    },
    priceText(price) {
      if (price === null || price === undefined) return '0'
      const n = Number(price)
      if (Number.isNaN(n)) return String(price)
      return Number.isInteger(n) ? n.toString() : n.toFixed(2)
    },
    fmt(value) {
      if (!value) return ''
      const s = formatTimestamp(value)
      const m = s.match(/^(\d{4})-(\d{1,2})-(\d{1,2})/)
      if (!m) return s
      return `${m[2]}/${m[3]}`
    },
    goDetail(id) {
      uni.navigateTo({
        url: `/subpackage_lostfound/lostfoundDetail/lostfoundDetail?id=${id}`
      })
    }
  }
}
</script>

<style lang="scss" scoped>
.page-root {
  min-height: 100vh;
  background: #F7F8FA;
}
.screen {
  min-height: 100vh;
  display: flex;
  flex-direction: column;
  box-sizing: border-box;
}
.page-body {
  flex: 1;
  min-height: 0;
}

/* ===== 用户信息卡 ===== */
.user-profile-card {
  margin: 12rpx;
  padding: 24rpx;
  background: #FFFFFF;
  border-radius: 16rpx;
  display: flex;
  align-items: center;
  gap: 20rpx;
}
.avatar-wrap {
  width: 88rpx;
  height: 88rpx;
  border-radius: 50%;
  overflow: hidden;
  flex-shrink: 0;
  background: rgba(92, 122, 153, 0.12);
  display: flex;
  align-items: center;
  justify-content: center;
}
.avatar-img {
  width: 100%;
  height: 100%;
}
.avatar-initial {
  font-size: 40rpx;
  font-weight: 600;
  color: #5C7A99;
}
.user-info {
  flex: 1;
  min-width: 0;
}
.user-name {
  font-size: 30rpx;
  font-weight: 600;
  color: #1F2329;
  margin-bottom: 10rpx;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.user-stats {
  display: flex;
  align-items: center;
  gap: 24rpx;
}
.stat-item {
  display: flex;
  align-items: center;
  gap: 8rpx;
}
.stat-num {
  font-size: 28rpx;
  font-weight: 600;
  color: #1F2329;
}
.stat-label {
  font-size: 22rpx;
  color: #9AA2AE;
}

/* ===== 空状态 ===== */
.empty {
  padding: 120rpx 32rpx;
  display: flex;
  flex-direction: column;
  align-items: center;
}
.empty-illustration {
  width: 280rpx;
  height: 280rpx;
  margin-bottom: 28rpx;
}
.empty-title {
  font-size: 28rpx;
  font-weight: 600;
  color: #1F2329;
  margin-bottom: 10rpx;
}
.empty-desc {
  font-size: 24rpx;
  color: #9AA2AE;
}

/* ===== 商品网格 ===== */
.item-grid {
  padding: 12rpx;
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 12rpx;
  align-items: start;
}
.item-card {
  background: #FFFFFF;
  border-radius: 16rpx;
  overflow: hidden;
}
.card-cover {
  position: relative;
  width: 100%;
  padding-top: 100%;
  background: #EDEEF0;
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
  align-items: center;
  justify-content: center;
  background: #EDEEF0;
}
.cover-emoji {
  font-size: 58rpx;
}
.card-badge-type {
  position: absolute;
  top: 12rpx;
  left: 12rpx;
  padding: 4rpx 10rpx;
  border-radius: 8rpx;
  font-size: 20rpx;
  font-weight: 600;
  color: #FFFFFF;
}
.card-badge-type--sell { background: #F77648; }
.card-badge-type--buy  { background: #3A82F4; }

.card-body {
  padding: 14rpx 16rpx 16rpx;
}
.card-title {
  font-size: 26rpx;
  font-weight: 500;
  color: #1F2329;
  line-height: 1.4;
  height: 72rpx;
  overflow: hidden;
  display: -webkit-box;
  -webkit-box-orient: vertical;
  -webkit-line-clamp: 2;
  margin-bottom: 8rpx;
}
.card-price {
  font-size: 30rpx;
  font-weight: 600;
  color: #FF5A1F;
  margin-bottom: 10rpx;
}
.card-location-row {
  display: flex;
  align-items: center;
  gap: 6rpx;
  margin-bottom: 10rpx;
}
.loc-icon {
  width: 22rpx;
  height: 22rpx;
  background-image: url('data:image/svg+xml;utf8,<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 24 24" fill="none" stroke="%239AA2AE" stroke-width="2" stroke-linecap="round" stroke-linejoin="round"><path d="M21 10c0 7-9 13-9 13s-9-6-9-13a9 9 0 0 1 18 0z"/><circle cx="12" cy="10" r="3"/></svg>');
  background-repeat: no-repeat;
  background-size: contain;
  background-position: center;
  flex-shrink: 0;
}
.card-location {
  font-size: 20rpx;
  color: #9AA2AE;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
  flex: 1;
}
.card-user {
  display: flex;
  align-items: center;
  gap: 8rpx;
}
.user-avatar {
  width: 34rpx;
  height: 34rpx;
  border-radius: 50%;
  background: rgba(92, 122, 153, 0.12);
  color: #5C7A99;
  font-size: 18rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.user-name {
  font-size: 21rpx;
  color: #666A70;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.user-time {
  font-size: 18rpx;
  color: #9AA2AE;
  margin-left: auto;
  flex-shrink: 0;
}
.bottom-spacer {
  height: 160rpx;
}
</style>
