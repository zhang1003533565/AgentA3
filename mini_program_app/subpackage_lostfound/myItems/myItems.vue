<template>
  <view class="page-root">
    <view class="screen">
      <common-page-header title="我的发布" :fixed="true" :placeholder="true" :showBack="true" :autoBack="false" @back="goBack" />

      <view class="filter-tabs">
        <view
          v-for="filter in filters"
          :key="filter.key"
          class="filter-tab"
          :class="{ active: activeFilter === filter.key }"
          @click="activeFilter = filter.key"
        >
          <text>{{ filter.label }}</text>
        </view>
      </view>

      <scroll-view
        scroll-y
        class="page-body"
        :show-scrollbar="false"
        refresher-enabled
        :refresher-triggered="refreshing"
        refresher-background="#F7F8FA"
        @refresherrefresh="refreshPage"
      >
        <view class="page-body-inner">
        <view v-if="myItems.length === 0" class="empty">
          <image class="empty-illustration" src="/static/illustrations/market-empty-publish.svg" mode="aspectFit" />
          <view class="empty-title">{{ emptyTitle }}</view>
          <view class="empty-subtitle">{{ emptySubtitle }}</view>
          <button class="empty-primary-btn" @click="goToPublish">去发布商品</button>
          <view class="empty-link" @click="goToHotList">
            <text>看看热门商品</text>
            <view class="empty-link-arrow"></view>
          </view>
        </view>

        <view v-for="item in myItems" :key="item.id" class="publish-card" @click="goToDetail(item.id)">
          <view class="card-cover">
            <image v-if="item.images && item.images.length" :src="item.images[0]" mode="aspectFill" class="cover-img" />
            <view v-else class="cover-placeholder">
              <text class="cover-emoji">{{ itemEmoji(item.id) }}</text>
            </view>
            <view class="card-badge-type" :class="'card-badge-type--' + (item.tradeType || 'sell')">
              {{ item.tradeType === 'buy' ? '收' : '出' }}
            </view>
            <view v-if="item.status === 'offline'" class="card-badge-offline">已下架</view>
          </view>
          <view class="card-body">
            <view class="card-title">{{ item.name }}</view>
            <view class="card-price">¥{{ priceText(item.price) }}</view>
            <view class="card-location-row" v-if="item.pickupPoint">
              <view class="loc-icon"></view>
              <text class="card-location">校内·{{ item.pickupPoint }}</text>
            </view>
            <view class="card-seller">
              <view class="seller-avatar">{{ item.sellerName ? item.sellerName[0] : '我' }}</view>
              <text class="seller-name">{{ item.sellerName || '我' }}</text>
              <text class="card-time">{{ fmt(item.ctime) }}</text>
            </view>
            <view v-if="item.status === 'offline'" class="card-actions" @click.stop>
              <view class="card-action-btn card-action-btn--primary" @click="relistItem(item.id)">重新上架</view>
              <view class="card-action-btn card-action-btn--danger" @click="confirmDelete(item)">删除商品</view>
            </view>
            <view v-else-if="item.status === 'online'" class="card-actions" @click.stop>
              <view class="card-action-btn card-action-btn--danger" @click="offlineItem(item)">下架商品</view>
            </view>
          </view>
        </view>
        </view>
      </scroll-view>
    </view>
    <market-publish-overlay
      v-if="publishOverlayMounted"
      :visible="publishOverlayVisible"
      @close="closePublishOverlay"
    />
  </view>
</template>

<script>
import CommonPageHeader from '@/components/common-page-header/common-page-header.vue'
import MarketPublishOverlay from '@/components/market-publish-overlay/market-publish-overlay.vue'
import { deleteSecondhandItem, getMySecondhandItems, offlineSecondhandItem, onlineSecondhandItem } from '@/api/secondhand'
import { getToken, getUserInfo } from '@/utils/storage.js'

const FILTERS = [
  { key: 'all', label: '全部' },
  { key: 'sell', label: '在出物' },
  { key: 'buy', label: '在收物' },
  { key: 'offline', label: '已下架' }
]

const EMOJIS = ['📱', '💻', '📷', '🎧', '⌚', '📚', '👟', '🧥', '🪑', '🏠', '🎮', '🎸', '🖥️', '📦']

const ACTIVE_TRADE_STATUS = ['WAIT_CONFIRM', 'TRADING']

function formatTimestamp(value) {
  if (!value) return ''
  return String(value).replace('T', ' ')
}

function firstValue(...values) {
  return values.find((value) => value !== undefined && value !== null && value !== '')
}

function normalizeId(value) {
  const id = firstValue(
    value?.id,
    value?.userId,
    value?.sellerId,
    value?.buyerId,
    value?.user_id,
    value?.seller_id,
    value?.buyer_id,
    value?.uid
  )
  return id === undefined ? '' : String(id)
}

function decodeBase64Url(value) {
  const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/='
  const input = `${value || ''}`.replace(/-/g, '+').replace(/_/g, '/')
  let output = ''
  let buffer = 0
  let bits = 0
  for (let i = 0; i < input.length; i += 1) {
    const char = input.charAt(i)
    if (char === '=') break
    const index = chars.indexOf(char)
    if (index < 0) continue
    buffer = (buffer << 6) | index
    bits += 6
    if (bits >= 8) {
      bits -= 8
      output += String.fromCharCode((buffer >> bits) & 0xff)
    }
  }
  return output
}

function decodeTokenPayload(token) {
  if (!token) return {}
  try {
    const payload = token.split('.')[1]
    if (!payload) return {}
    return JSON.parse(decodeBase64Url(payload))
  } catch (e) {
    return {}
  }
}

function isCancelledTrade(status) {
  return String(status) === '5' || String(status).toUpperCase() === 'CANCELLED'
}

function isActiveTrade(status) {
  const text = String(status || '').toUpperCase()
  return ACTIVE_TRADE_STATUS.includes(text) || text === '4'
}

function normalizeItem(item) {
  const seller = item.seller || {}
  const trade = item.trade || item.tradeRecord || item.order || {}
  const buyer = trade.buyer || trade.buyerUser || {}
  const sellerId = firstValue(item.sellerId, item.userId, normalizeId(seller))
  const statusNumber = Number(item.status)

  return {
    id: item.id,
    name: item.title || item.name || '',
    desc: item.description || '',
    price: item.price,
    tradeType: item.tradeType || item.trade_type || 'sell',
    type: item.tradeType || item.trade_type || 'sell',
    status: statusNumber === 4 ? 'offline' : statusNumber === 3 ? 'sold' : 'online',
    statusText: item.statusText || (statusNumber === 3 ? '已售' : statusNumber === 4 ? '已下架' : '在售'),
    images: Array.isArray(item.images) ? item.images : [],
    userId: item.userId || sellerId,
    sellerId,
    sellerName: firstValue(item.sellerName, seller.nickname, seller.name, item.nickname, item.username, ''),
    sellerAvatar: firstValue(item.sellerAvatar, seller.avatar, seller.avatarUrl, item.avatar, item.avatarUrl, ''),
    buyerId: firstValue(trade.buyerId, trade.buyerUserId, normalizeId(buyer)),
    sessionId: trade.sessionId || trade.chatSessionId,
    intentSessionId: '',
    ctime: formatTimestamp(item.createTime),
    viewCount: Number(item.viewCount || item.view_count || 0),
    favoriteCount: Number(item.favoriteCount || item.favorite_count || 0),
    inquiryCount: Number(item.inquiryCount || item.inquiry_count || 0),
    pickupPoint: item.pickupPoint || item.location || '',
    categoryName: item.categoryName || item.category_name || ''
  }
}

function normalizeTradeRecord(item) {
  const buyer = item.buyer || {}
  const seller = item.seller || {}
  return {
    id: item.id,
    itemId: item.itemId || item.secondhandItemId || item.productId || item.goodsId,
    sessionId: item.sessionId || item.chatSessionId,
    buyerId: firstValue(item.buyerId, item.buyerUserId, item.buyer_id, normalizeId(buyer)),
    sellerId: firstValue(item.sellerId, item.sellerUserId, item.seller_id, normalizeId(seller)),
    status: item.status
  }
}

function normalizeIntentSession(item) {
  return {
    itemId: item.itemId || item.secondhandItemId || item.productId || item.goodsId,
    sessionId: item.sessionId || item.chatSessionId
  }
}

function createIntentSessionMap(records = [], cancelledSessionIds = new Set()) {
  const sessionMap = new Map()
  records.map(normalizeIntentSession).forEach((session) => {
    if (!session.itemId || !session.sessionId) return
    if (cancelledSessionIds.has(String(session.sessionId))) return
    const key = String(session.itemId)
    if (!sessionMap.has(key)) {
      sessionMap.set(key, session)
    }
  })
  return sessionMap
}

function createTradeMap(records = []) {
  const tradeMap = new Map()
  records.map(normalizeTradeRecord).forEach((trade) => {
    if (!trade.itemId || isCancelledTrade(trade.status)) return
    const key = String(trade.itemId)
    const prev = tradeMap.get(key)
    if (!prev || isActiveTrade(trade.status) || (!prev.buyerId && trade.buyerId)) {
      tradeMap.set(key, trade)
    }
  })
  return tradeMap
}

export default {
  components: {
    CommonPageHeader,
    MarketPublishOverlay
  },
  data() {
    return {
      items: [],
      loading: false,
      refreshing: false,
      currentUserId: '',
      activeFilter: 'all',
      filters: FILTERS,
      publishOverlayMounted: false,
      publishOverlayVisible: false,
      publishOverlayTimer: null
    }
  },
  computed: {
    myItems() {
      return this.items
    },
    emptyText() {
      const current = this.filters.find((item) => item.key === this.activeFilter)
      return this.activeFilter === 'all' ? '还没有发布过' : `暂无${current?.label || ''}商品`
    },
    emptyTitle() {
      return this.items.length === 0 ? '还没有发布过' : this.emptyText
    },
    emptySubtitle() {
      return this.items.length === 0 ? '发布闲置好物，快速找到需要它的人' : '切换分类或稍后再看看'
    }
  },
  watch: {
    activeFilter() {
      this.loadItems()
    }
  },
  async onLoad() {
    uni.$on('secondhand:item:published', this.handleItemPublished)
  },
  async onShow() {
    this.loadCurrentUser()
    await this.loadItems()
  },
  onUnload() {
    uni.$off('secondhand:item:published', this.handleItemPublished)
    if (this.publishOverlayTimer) {
      clearTimeout(this.publishOverlayTimer)
      this.publishOverlayTimer = null
    }
  },
  methods: {
    goBack() {
      const pages = getCurrentPages()
      if (pages.length > 1) {
        uni.navigateBack({ delta: 1 })
        return
      }
      uni.redirectTo({ url: '/subpackage_lostfound/marketplaceProfile/marketplaceProfile' })
    },
    loadCurrentUser() {
      let userInfo = getUserInfo() || {}
      if (!userInfo.id && !userInfo.userId) {
        const raw = uni.getStorageSync('userInfo')
        if (raw) {
          if (typeof raw === 'string') {
            try {
              userInfo = JSON.parse(raw)
            } catch (e) {
              userInfo = {}
            }
          } else if (typeof raw === 'object') {
            userInfo = raw
          }
        }
      }
      const nestedUser = userInfo.user || userInfo.profile || userInfo.data || {}
      const tokenPayload = decodeTokenPayload(getToken())
      this.currentUserId = normalizeId(userInfo) || normalizeId(nestedUser) || normalizeId(tokenPayload)
    },
    async loadItems() {
      try {
        this.loading = true
        const params = { current: 1, size: 100 }
        if (this.activeFilter === 'sell') {
          params.tradeType = 'sell'
          params.status = 2
        } else if (this.activeFilter === 'buy') {
          params.tradeType = 'buy'
          params.status = 2
        } else if (this.activeFilter === 'offline') {
          params.status = 4
        }
        const res = await getMySecondhandItems(params)
        const itemRecords = Array.isArray(res?.data?.records) ? res.data.records : []

        const userInfo = getUserInfo() || {}
        const nestedUser = userInfo.user || userInfo.profile || userInfo.data || {}
        const currentUserName = firstValue(userInfo.nickname, userInfo.name, userInfo.username, nestedUser.nickname, nestedUser.name, nestedUser.username, '我')
        const currentUserAvatar = firstValue(userInfo.avatar, userInfo.avatarUrl, nestedUser.avatar, nestedUser.avatarUrl, '')

        this.items = itemRecords.map((record) => {
          const item = normalizeItem(record)
          if (!item.sellerName) item.sellerName = currentUserName
          if (!item.sellerAvatar) item.sellerAvatar = currentUserAvatar
          return item
        })
      } catch (e) {
        console.error('加载数据失败', e)
      } finally {
        this.loading = false
      }
    },
    async refreshPage() {
      if (this.refreshing) return
      this.refreshing = true
      try {
        this.loadCurrentUser()
        await this.loadItems()
        uni.showToast({ title: '已刷新', icon: 'none', duration: 900 })
      } finally {
        this.refreshing = false
      }
    },
    fmt(ts) {
      const time = typeof ts === 'string' ? new Date(ts.replace(/-/g, '/')).getTime() : ts
      const d = new Date(time)
      const now = new Date()
      const diff = now - d
      if (!time || Number.isNaN(time)) return ''
      if (diff < 60000) return '刚刚'
      if (diff < 3600000) return Math.floor(diff / 60000) + '分钟前'
      if (diff < 86400000) return Math.floor(diff / 3600000) + '小时前'
      return `${d.getMonth() + 1}/${d.getDate()}`
    },
    priceText(value) {
      const price = Number(value)
      if (!Number.isFinite(price)) return '0.00'
      return price.toFixed(2)
    },
    itemEmoji(id) {
      return EMOJIS[(id || 0) % EMOJIS.length]
    },
    isTradingItem(item) {
      return isActiveTrade(item?.tradeStatus)
    },
    itemDisplayStatus(item) {
      if (item?.status === 'offline') return { key: 'offline', label: '已下架' }
      if (item?.status === 'sold') return { key: 'sold', label: '已售' }
      if (this.isTradingItem(item)) return { key: 'trading', label: '交易中' }
      return { key: 'online', label: '在售' }
    },
    async toggleStatus(id) {
      const item = this.items.find(i => i.id === id)
      if (!item) return
      try {
        if (item.status === 'online') {
          await offlineSecondhandItem(id)
          uni.showToast({ title: '已下架', icon: 'none' })
        } else if (item.status === 'offline') {
          await onlineSecondhandItem(id)
          uni.showToast({ title: '已上架', icon: 'none' })
        } else {
          uni.showToast({ title: '已售出商品不能重新操作', icon: 'none' })
          return
        }
        await this.loadItems()
      } catch (error) {
        console.error('更新状态失败', error)
      }
    },
    async relistItem(id) {
      try {
        await onlineSecondhandItem(id)
        uni.showToast({ title: '已重新上架', icon: 'success' })
        await this.loadItems()
      } catch (error) {
        console.error('重新上架失败', error)
        uni.showToast({ title: error?.data?.msg || error?.msg || '操作失败', icon: 'none' })
      }
    },
    offlineItem(item) {
      if (!item || !item.id) return
      uni.showModal({
        title: '确认下架',
        content: `确定下架"${item.name || '该商品'}"吗？下架后将不再展示。`,
        confirmText: '下架',
        confirmColor: '#FF4D2E',
        success: (res) => {
          if (!res.confirm) return
          this.doOffline(item.id)
        }
      })
    },
    async doOffline(id) {
      try {
        await offlineSecondhandItem(id)
        uni.showToast({ title: '已下架', icon: 'success' })
        await this.loadItems()
      } catch (error) {
        console.error('下架失败', error)
        uni.showToast({ title: error?.data?.msg || error?.msg || '下架失败', icon: 'none' })
      }
    },
    confirmDelete(item) {
      if (!item || !item.id) return
      uni.showModal({
        title: '删除商品',
        content: `确定删除"${item.name || '该商品'}"吗？删除后不可恢复。`,
        confirmText: '删除',
        confirmColor: '#FF4D2E',
        success: (res) => {
          if (!res.confirm) return
          this.deleteItem(item.id)
        }
      })
    },
    async deleteItem(id) {
      try {
        await deleteSecondhandItem(id)
        uni.showToast({ title: '已删除', icon: 'success' })
        await this.loadItems()
      } catch (error) {
        console.error('删除失败', error)
        uni.showToast({ title: error?.data?.msg || error?.msg || '删除失败', icon: 'none' })
      }
    },
    isCurrentUserPublisher(item) {
      return Boolean(item && item.sellerId && this.currentUserId && String(item.sellerId) === String(this.currentUserId))
    },
    isPurchaseRecord(item) {
      if (!item || this.isCurrentUserPublisher(item)) return false
      if (item.buyerId && this.currentUserId && String(item.buyerId) === String(this.currentUserId)) return true
      return Boolean(item.sessionId || item.tradeStatus)
    },
    getChatTargetUserId(item) {
      if (!item) return ''
      const targetId = this.isCurrentUserPublisher(item) ? item.buyerId : item.sellerId
      if (!targetId) return ''
      if (this.currentUserId && String(targetId) === String(this.currentUserId)) return ''
      return String(targetId)
    },
    isSoldItem(item) {
      return item?.status === 'sold'
    },
    canContact(item) {
      if (!item) return false
      if (this.isCurrentUserPublisher(item)) {
        if (this.getChatTargetUserId(item)) return true
        return !this.isSoldItem(item) && Boolean(item.intentSessionId)
      }
      return Boolean(this.getChatTargetUserId(item))
    },
    async handleSecondaryAction(item) {
      if (!item) return
      if (!this.isCurrentUserPublisher(item)) {
        this.openOrder(item)
        return
      }
      await this.toggleStatus(item.id)
    },
    openManage(item) {
      if (!item) return
      uni.showToast({ title: '功能开发中', icon: 'none' })
    },
    openOrder() {
      uni.showToast({ title: '请在聊天中查看交易沟通', icon: 'none' })
    },
    openChat(item) {
      if (!item || !item.id) return
      const targetUserId = this.getChatTargetUserId(item)
      if (this.isCurrentUserPublisher(item) && !targetUserId && !this.isSoldItem(item) && item.intentSessionId) {
        uni.navigateTo({
          url: `/subpackage_lostfound/lostfoundChat/lostfoundChat?sessionId=${item.intentSessionId}`
        })
        return
      }
      if (!targetUserId) {
        uni.showToast({ title: '暂无买家', icon: 'none' })
        return
      }
      const query = item.sessionId
        ? `sessionId=${item.sessionId}&targetUserId=${targetUserId}`
        : `itemId=${item.id}&targetUserId=${targetUserId}`
      uni.navigateTo({
        url: `/subpackage_lostfound/lostfoundChat/lostfoundChat?${query}`
      })
    },
    goToDetail(id) {
      uni.navigateTo({
        url: `/subpackage_lostfound/lostfoundDetail/lostfoundDetail?id=${id}`
      })
    },
    goToPublish() {
      if (this.publishOverlayMounted) return
      if (this.publishOverlayTimer) {
        clearTimeout(this.publishOverlayTimer)
        this.publishOverlayTimer = null
      }
      this.publishOverlayMounted = true
      this.$nextTick(() => {
        this.publishOverlayTimer = setTimeout(() => {
          this.publishOverlayVisible = true
          this.publishOverlayTimer = null
        }, 20)
      })
    },
    closePublishOverlay() {
      if (!this.publishOverlayMounted) return
      if (this.publishOverlayTimer) {
        clearTimeout(this.publishOverlayTimer)
        this.publishOverlayTimer = null
      }
      this.publishOverlayVisible = false
      this.publishOverlayTimer = setTimeout(() => {
        this.publishOverlayMounted = false
        this.publishOverlayTimer = null
      }, 300)
    },
    async handleItemPublished() {
      await this.loadItems()
    },
    goToHotList() {
      uni.navigateTo({
        url: '/subpackage_lostfound/marketHotList/marketHotList'
      })
    }
  }
}
</script>

<style lang="scss">
.page-root {
  width: 100%;
  height: 100vh;
  min-height: 100vh;
  background: #F7F8FA;
  overflow: hidden;
}

.screen {
  width: 100%;
  max-width: 430px;
  height: 100vh;
  min-height: 100vh;
  margin: 0 auto;
  background: #F7F8FA;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.filter-tabs {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  align-items: center;
  height: 112rpx;
  margin: 20rpx 12rpx 0;
  padding: 12rpx 14rpx;
  background: #FFFFFF;
  border-radius: 24rpx;
  box-shadow: 0 16rpx 40rpx rgba(48, 71, 112, 0.08);
  box-sizing: border-box;
}

.filter-tab {
  position: relative;
  height: 72rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 999rpx;
  color: #343A46;
  font-size: 27rpx;
  font-weight: 800;
  box-sizing: border-box;
}

.filter-tab.active {
  background: #F0F5FF;
  color: #416FF0;
}

.filter-tab.active::after {
  content: '';
  position: absolute;
  left: 50%;
  bottom: 4rpx;
  width: 32rpx;
  height: 6rpx;
  border-radius: 999rpx;
  background: #416FF0;
  transform: translateX(-50%);
}

.page-body {
  flex: 1;
  min-height: 0;
  height: 0;
  background: #F7F8FA;
}

.page-body-inner {
  padding: 26rpx 12rpx 34rpx;
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12rpx;
}

.empty {
  grid-column: 1 / -1;
  min-height: calc(100vh - 380rpx - var(--status-bar-height, 0px));
  padding: 118rpx 0 80rpx;
  text-align: center;
  box-sizing: border-box;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.empty-illustration {
  width: 350rpx;
  height: 276rpx;
  margin-bottom: 48rpx;
}

.empty-title {
  color: #1D2430;
  font-size: 34rpx;
  font-weight: 900;
  line-height: 1.25;
}

.empty-subtitle {
  margin-top: 22rpx;
  color: #9AA2AE;
  font-size: 25rpx;
  font-weight: 500;
  line-height: 1.4;
}

.empty-primary-btn {
  width: 236rpx;
  height: 72rpx;
  margin: 58rpx 0 0;
  padding: 0;
  border-radius: 999rpx;
  background: #4D77F3;
  color: #FFFFFF;
  font-size: 26rpx;
  font-weight: 800;
  line-height: 72rpx;
  box-shadow: 0 14rpx 30rpx rgba(77, 119, 243, 0.26);
}

.empty-primary-btn::after {
  border: none;
}

.empty-link {
  display: inline-flex;
  align-items: center;
  gap: 12rpx;
  margin-top: 44rpx;
  color: #4D77F3;
  font-size: 24rpx;
  font-weight: 700;
}

.empty-link-arrow {
  width: 12rpx;
  height: 12rpx;
  border-top: 3rpx solid currentColor;
  border-right: 3rpx solid currentColor;
  border-radius: 2rpx;
  transform: rotate(45deg);
}

.publish-card {
  display: flex;
  flex-direction: column;
  background: #FFFFFF;
  border-radius: 16rpx;
  overflow: hidden;
  box-shadow: 0 4rpx 16rpx rgba(30, 41, 59, 0.06);
  box-sizing: border-box;
}

.card-cover {
  position: relative;
  width: 100%;
  padding-top: 100%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #EEF2F7;
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
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 14rpx;
  color: #8E8E93;
}

.cover-emoji {
  font-size: 58rpx;
  line-height: 1;
}

.card-badge-type {
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

.card-badge-type--sell {
  background: #FF6B35;
  box-shadow: 0 4rpx 10rpx rgba(255, 107, 53, 0.35);
}

.card-badge-type--buy {
  background: #4A90E2;
  box-shadow: 0 4rpx 10rpx rgba(74, 144, 226, 0.35);
}

.card-body {
  padding: 14rpx 16rpx 18rpx;
  display: flex;
  flex-direction: column;
  gap: 6rpx;
}

.card-title {
  color: #1D2430;
  font-size: 24rpx;
  font-weight: 800;
  line-height: 1.3;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-price {
  color: #FF4D2E;
  font-size: 28rpx;
  font-weight: 900;
  line-height: 1.2;
}

.card-location-row {
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

.loc-icon {
  position: relative;
  width: 18rpx;
  height: 18rpx;
  flex-shrink: 0;
}

.loc-icon::before {
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

.loc-icon::after {
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

.card-location {
  font-size: 20rpx;
  color: #8A94A6;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-seller {
  display: flex;
  align-items: center;
  gap: 8rpx;
  padding-top: 10rpx;
  border-top: 1rpx solid #F0F2F5;
}

.seller-avatar {
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
  overflow: hidden;
  flex-shrink: 0;
}

.seller-name {
  font-size: 21rpx;
  color: #666A70;
  flex: 1;
  font-weight: 600;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.card-time {
  font-size: 18rpx;
  color: #9AA2AE;
  font-weight: 500;
  flex-shrink: 0;
  margin-left: auto;
}

.card-badge-offline {
  position: absolute;
  right: 14rpx;
  top: 14rpx;
  min-width: 40rpx;
  height: 40rpx;
  padding: 0 10rpx;
  border-radius: 10rpx;
  background: rgba(0, 0, 0, 0.55);
  color: #FFFFFF;
  font-size: 20rpx;
  font-weight: 700;
  line-height: 40rpx;
  text-align: center;
}

.card-actions {
  display: flex;
  gap: 12rpx;
  margin-top: 12rpx;
  padding-top: 12rpx;
  border-top: 1rpx solid #F0F2F5;
}

.card-action-btn {
  flex: 1;
  height: 56rpx;
  border-radius: 10rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 23rpx;
  font-weight: 700;
}

.card-action-btn--primary {
  background: #F0F5FF;
  color: #416FF0;
}

.card-action-btn--danger {
  background: #FFF0ED;
  color: #FF4D2E;
}
</style>
