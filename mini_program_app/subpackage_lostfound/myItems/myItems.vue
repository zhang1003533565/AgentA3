<template>
  <view class="page-root">
    <view class="screen">
      <common-page-header title="我的发布" :subtitle="publishSummary" :fixed="true" :placeholder="true" :showBack="true" :autoBack="false" @back="goBack" />

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

      <scroll-view scroll-y class="page-body" :show-scrollbar="false">
        <view v-if="myItems.length === 0" class="empty">
          <view class="empty-i"></view>
          <view class="empty-t">{{ emptyText }}</view>
        </view>

        <view v-for="item in myItems" :key="item.id" class="publish-card">
          <view class="item-cover" @click="goToDetail(item.id)">
            <image v-if="item.images && item.images.length" :src="item.images[0]" mode="aspectFill" />
            <view v-else class="cover-placeholder"></view>
          </view>

          <view class="item-main">
            <view class="item-title" @click="goToDetail(item.id)">{{ item.name }}</view>
            <view class="item-price">¥{{ priceText(item.price) }}</view>
            <view class="item-time">{{ fmt(item.ctime) }}发布</view>

            <view class="item-operate">
              <view class="status-pill" :class="'status-pill--' + itemDisplayStatus(item).key">
                {{ itemDisplayStatus(item).label }}
              </view>
              <view class="manage-link" @click.stop="openManage(item)">
                <text>管理</text>
                <view class="chevron-icon"></view>
              </view>
            </view>

            <view class="item-metrics">
              <view class="metric">
                <view class="metric-icon eye-icon"></view>
                <text>浏览 {{ item.viewCount || 0 }}</text>
              </view>
              <view class="metric">
                <image class="metric-icon metric-icon-img" src="/static/icons/line/star.svg" mode="aspectFit" />
                <text>收藏 {{ item.favoriteCount || 0 }}</text>
              </view>
              <view class="metric">
                <view class="metric-icon chat-icon"></view>
                <text>咨询 {{ item.inquiryCount || 0 }}</text>
              </view>
            </view>
          </view>
        </view>
      </scroll-view>
    </view>
  </view>
</template>

<script>
import CommonPageHeader from '@/components/common-page-header/common-page-header.vue'
import { getChatSessions, getMySecondhandItems, getTradeRecords, offlineSecondhandItem, onlineSecondhandItem } from '@/api/secondhand'
import { getToken, getUserInfo } from '@/utils/storage.js'

const FILTERS = [
  { key: 'all', label: '全部' },
  { key: 'online', label: '在售' },
  { key: 'trading', label: '交易中' },
  { key: 'sold', label: '已售' },
  { key: 'offline', label: '已下架' }
]

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
    type: 'sell',
    status: statusNumber === 4 ? 'offline' : statusNumber === 3 ? 'sold' : 'online',
    statusText: item.statusText || (statusNumber === 3 ? '已售' : statusNumber === 4 ? '已下架' : '在售'),
    images: Array.isArray(item.images) ? item.images : [],
    userId: item.userId || sellerId,
    sellerId,
    buyerId: firstValue(trade.buyerId, trade.buyerUserId, normalizeId(buyer)),
    sessionId: trade.sessionId || trade.chatSessionId,
    intentSessionId: '',
    ctime: formatTimestamp(item.createTime),
    viewCount: Number(item.viewCount || item.view_count || 0),
    favoriteCount: Number(item.favoriteCount || item.favorite_count || 0),
    inquiryCount: Number(item.inquiryCount || item.inquiry_count || 0)
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
    CommonPageHeader
  },
  data() {
    return {
      items: [],
      loading: false,
      currentUserId: '',
      activeFilter: 'all',
      filters: FILTERS
    }
  },
  computed: {
    myItems() {
      if (this.activeFilter === 'all') return this.items
      return this.items.filter((item) => {
        if (this.activeFilter === 'trading') return this.isTradingItem(item)
        if (this.activeFilter === 'online') return item.status === 'online' && !this.isTradingItem(item)
        return item.status === this.activeFilter
      })
    },
    tradingCount() {
      return this.items.filter((item) => this.isTradingItem(item)).length
    },
    publishSummary() {
      return `${this.items.length}件商品 · ${this.tradingCount}件交易中`
    },
    emptyText() {
      const current = this.filters.find((item) => item.key === this.activeFilter)
      return this.activeFilter === 'all' ? '还没有发布过' : `暂无${current?.label || ''}商品`
    }
  },
  async onLoad() {
    this.loadCurrentUser()
    await this.loadItems()
  },
  async onShow() {
    this.loadCurrentUser()
    await this.loadItems()
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
        const [itemsRes, tradeRes, sessionRes] = await Promise.all([
          getMySecondhandItems({ current: 1, size: 100 }),
          getTradeRecords({ current: 1, size: 100 }),
          getChatSessions({ current: 1, size: 100 })
        ])
        const itemRecords = Array.isArray(itemsRes?.data?.records) ? itemsRes.data.records : []
        const tradeRecords = Array.isArray(tradeRes?.data?.records) ? tradeRes.data.records : []
        const sessionRecords = Array.isArray(sessionRes?.data?.records) ? sessionRes.data.records : []
        const cancelledSessionIds = new Set(
          tradeRecords
            .filter((record) => isCancelledTrade(record.status) && record.sessionId)
            .map((record) => String(record.sessionId))
        )
        const tradeMap = createTradeMap(tradeRecords)
        const intentSessionMap = createIntentSessionMap(sessionRecords, cancelledSessionIds)
        this.items = itemRecords.map((record) => {
          const item = normalizeItem(record)
          const trade = tradeMap.get(String(item.id))
          const intentSession = intentSessionMap.get(String(item.id))
          if (!trade) {
            return {
              ...item,
              intentSessionId: intentSession?.sessionId || ''
            }
          }
          return {
            ...item,
            buyerId: item.buyerId || trade.buyerId,
            sellerId: item.sellerId || trade.sellerId,
            sessionId: item.sessionId || trade.sessionId,
            tradeStatus: trade.status,
            tradeId: trade.id,
            intentSessionId: intentSession?.sessionId || ''
          }
        })
      } catch (e) {
        console.error('加载数据失败', e)
      } finally {
        this.loading = false
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
    }
  }
}
</script>

<style lang="scss">
.page-root {
  width: 100%;
  min-height: 100vh;
  background: #F7F8FA;
}

.screen {
  width: 100%;
  max-width: 430px;
  min-height: 100vh;
  margin: 0 auto;
  background: #F7F8FA;
}

.filter-tabs {
  display: grid;
  grid-template-columns: repeat(5, 1fr);
  align-items: center;
  height: 138rpx;
  padding: 20rpx 24rpx 22rpx;
  background: #FFFFFF;
  box-sizing: border-box;
}

.filter-tab {
  position: relative;
  height: 72rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 999rpx;
  color: #6E788A;
  font-size: 28rpx;
  font-weight: 800;
  box-sizing: border-box;
}

.filter-tab.active {
  background: #EAF3FF;
  color: #2F73E0;
}

.filter-tab.active::after {
  content: '';
  position: absolute;
  left: 50%;
  bottom: -4rpx;
  width: 38rpx;
  height: 6rpx;
  border-radius: 999rpx;
  background: #2F73E0;
  transform: translateX(-50%);
}

.page-body {
  height: calc(100vh - 226rpx - var(--status-bar-height));
  padding: 26rpx 18rpx 34rpx;
  box-sizing: border-box;
  background: #F7F8FA;
}

.empty {
  padding: 120rpx 0;
  text-align: center;
}

.empty-i {
  position: relative;
  width: 86rpx;
  height: 64rpx;
  margin: 0 auto 24rpx;
  border: 3rpx solid #A5AFBF;
  border-top: 0;
  border-radius: 8rpx 8rpx 14rpx 14rpx;
  box-sizing: border-box;
}

.empty-i::before {
  content: '';
  position: absolute;
  left: -3rpx;
  top: -20rpx;
  width: 86rpx;
  height: 24rpx;
  border: 3rpx solid #A5AFBF;
  border-bottom: 0;
  border-radius: 12rpx 12rpx 0 0;
  box-sizing: border-box;
}

.empty-i::after {
  content: '';
  position: absolute;
  left: 22rpx;
  right: 22rpx;
  top: 20rpx;
  border-top: 3rpx solid #A5AFBF;
}

.empty-t {
  color: #8A94A6;
  font-size: 27rpx;
}

.publish-card {
  display: grid;
  grid-template-columns: 160rpx 1fr;
  gap: 20rpx;
  margin-bottom: 18rpx;
  padding: 16rpx;
  border: 1rpx solid #E8ECF2;
  border-radius: 18rpx;
  background: #FFFFFF;
  box-shadow: 0 6rpx 18rpx rgba(30, 41, 59, 0.045);
  box-sizing: border-box;
}

.item-cover {
  width: 160rpx;
  height: 160rpx;
  border-radius: 14rpx;
  overflow: hidden;
  background: #EEF2F7;
}

.item-cover image {
  width: 100%;
  height: 100%;
  display: block;
}

.cover-placeholder {
  position: relative;
  width: 100%;
  height: 100%;
}

.cover-placeholder::before {
  content: '';
  position: absolute;
  left: 62rpx;
  top: 58rpx;
  width: 86rpx;
  height: 66rpx;
  border: 5rpx solid #A5AFBF;
  border-radius: 12rpx;
  box-sizing: border-box;
}

.cover-placeholder::after {
  content: '';
  position: absolute;
  left: 76rpx;
  top: 116rpx;
  width: 62rpx;
  height: 34rpx;
  border-left: 5rpx solid #A5AFBF;
  border-bottom: 5rpx solid #A5AFBF;
  transform: skew(-28deg);
}

.item-main {
  min-width: 0;
  display: grid;
  grid-template-rows: auto auto auto 1fr auto;
}

.item-title {
  color: #111827;
  font-size: 25rpx;
  font-weight: 900;
  line-height: 1.35;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.item-price {
  margin-top: 16rpx;
  color: #111827;
  font-size: 32rpx;
  font-weight: 900;
  line-height: 1;
}

.item-time {
  margin-top: 12rpx;
  color: #66738A;
  font-size: 22rpx;
  line-height: 1.2;
}

.item-operate {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12rpx;
  margin-top: 8rpx;
}

.status-pill {
  min-width: 66rpx;
  height: 34rpx;
  padding: 0 14rpx;
  border-radius: 999rpx;
  font-size: 22rpx;
  font-weight: 800;
  line-height: 34rpx;
  text-align: center;
  box-sizing: border-box;
}

.status-pill--online {
  background: #DCF8EA;
  color: #13B566;
}

.status-pill--trading {
  background: #E6F0FF;
  color: #2F73E0;
}

.status-pill--sold,
.status-pill--offline {
  background: #F0F2F5;
  color: #737D8C;
}

.manage-link {
  display: inline-flex;
  align-items: center;
  gap: 10rpx;
  color: #66738A;
  font-size: 23rpx;
  font-weight: 700;
  flex-shrink: 0;
}

.chevron-icon {
  width: 13rpx;
  height: 13rpx;
  border-right: 3rpx solid currentColor;
  border-top: 3rpx solid currentColor;
  transform: rotate(45deg);
  border-radius: 2rpx;
}

.item-metrics {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 8rpx;
  margin-top: 14rpx;
  padding-top: 11rpx;
  border-top: 1rpx solid #E7EAF0;
}

.metric {
  display: flex;
  align-items: center;
  gap: 7rpx;
  color: #66738A;
  font-size: 21rpx;
  white-space: nowrap;
}

.metric-icon {
  position: relative;
  width: 23rpx;
  height: 23rpx;
  color: #66738A;
  flex-shrink: 0;
}

.metric-icon-img {
  display: block;
  opacity: 0.78;
}

.eye-icon::before {
  content: '';
  position: absolute;
  left: 1rpx;
  top: 6rpx;
  width: 21rpx;
  height: 13rpx;
  border: 2.5rpx solid currentColor;
  border-radius: 50%;
  box-sizing: border-box;
}

.eye-icon::after {
  content: '';
  position: absolute;
  left: 9rpx;
  top: 10rpx;
  width: 5rpx;
  height: 5rpx;
  border-radius: 50%;
  background: currentColor;
}

.chat-icon::before {
  content: '';
  position: absolute;
  left: 1rpx;
  top: 4rpx;
  width: 21rpx;
  height: 16rpx;
  border: 2.5rpx solid currentColor;
  border-radius: 8rpx;
  box-sizing: border-box;
}

.chat-icon::after {
  content: '';
  position: absolute;
  left: 7rpx;
  bottom: 1rpx;
  width: 7rpx;
  height: 7rpx;
  border-left: 2.5rpx solid currentColor;
  border-bottom: 2.5rpx solid currentColor;
  transform: rotate(-18deg);
}
</style>
