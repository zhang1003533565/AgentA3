<template>
  <view class="page-root">
    <view class="screen">
      <view class="container">
        <nav-bar title="我发布的" :fixed="true" :placeholder="true" />
        
        <scroll-view scroll-y class="page-body">
          <view v-if="myItems.length === 0" class="empty">
            <view class="empty-i"></view>
            <view class="empty-t">还没有发布过</view>
          </view>
          <view v-for="item in myItems" :key="item.id" class="micard">
            <view class="mi-main" @click="goToDetail(item.id)">
              <view class="miimg">
                <text v-if="item.type === 'want'">🔍</text>
                <image v-else-if="item.images && item.images.length" :src="item.images[0]" mode="aspectFill" />
                <text v-else>{{ emoji(item.id) }}</text>
              </view>
              <view class="mibody">
                <view class="miname">{{ item.name }}</view>
                <view v-if="item.type === 'sell'" class="miprice">
                  <small>¥</small>{{ item.price }}
                </view>
                <view class="mitime">{{ fmt(item.ctime) }}发布</view>
              </view>
              <button
                v-if="isCurrentUserPublisher(item)"
                class="mi-manage-btn"
                @click.stop="openManage(item)"
              >
                管理
              </button>
            </view>
            <view v-if="shouldShowActionBar(item)" class="mi-actions">
              <button
                v-if="shouldShowContactAction(item)"
                class="mi-action-btn mi-action-contact"
                :class="{ 'mi-action-disabled': !canContact(item) }"
                :disabled="!canContact(item)"
                @click.stop="openChat(item)"
              >
                {{ contactLabel(item) }}
              </button>
              <button
                v-if="showSecondaryAction(item)"
                class="mi-action-btn mi-action-status"
                @click.stop="handleSecondaryAction(item)"
              >
                {{ statusActionText(item) }}
              </button>
            </view>
          </view>
        </scroll-view>
      </view>
    </view>
  </view>
</template>

<script>
import NavBar from '@/components/nav-bar/nav-bar.vue'
import { getChatSessions, getMySecondhandItems, getTradeRecords, offlineSecondhandItem, onlineSecondhandItem, cancelTradeRecord } from '@/api/secondhand'
import { getToken, getUserInfo } from '@/utils/storage.js'

const EMOJIS = ['📱', '💻', '📷', '🎧', '⌚', '📚', '👟', '🧥', '🪑', '🏠', '🎮', '🎸', '🖥️', '📦']

function formatTimestamp(value) {
  if (!value) return ''
  return value.replace('T', ' ')
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

function normalizeItem(item) {
  const seller = item.seller || {}
  const trade = item.trade || item.tradeRecord || item.order || {}
  const buyer = trade.buyer || trade.buyerUser || {}
  const sellerId = firstValue(item.sellerId, item.userId, normalizeId(seller))
  const categoryId = item.categoryId ?? item.categoryLevel2Id ?? item.categoryLevel1Id ?? 'other'
  const categoryName = item.categoryName || item.categoryLevel2Name || item.categoryLevel1Name || ''
  const condition = item.condition || item.itemCondition || ''
  const location = item.location || item.tradeLocationText || ''
  const schoolName = item.schoolName || seller.schoolName || ''

  return {
    id: item.id,
    name: item.title,
    desc: item.description || '',
    price: item.price,
    originalPrice: item.originalPrice || item.original_price || null,
    type: 'sell',
    status: item.status === 2 ? 'online' : item.status === 5 ? 'reserved' : item.status === 4 ? 'offline' : 'sold',
    statusText: item.statusText || (item.status === 2 ? '在售' : item.status === 5 ? '交易中' : item.status === 3 ? '已售出' : '已下架'),
    images: Array.isArray(item.images) ? item.images : [],
    userId: item.userId || sellerId,
    sellerId,
    buyerId: firstValue(trade.buyerId, trade.buyerUserId, normalizeId(buyer)),
    sessionId: trade.sessionId || trade.chatSessionId,
    intentSessionId: '',
    userName: seller.username || '用户',
    userPhone: seller.phone || '',
    userAva: seller.avatar || '',
    ctime: formatTimestamp(item.createTime),
    categoryId: item.categoryId || categoryId,
    categoryName,
    categoryLevel1Id: item.categoryLevel1Id || item.categoryParentId || item.categoryId || '',
    categoryLevel1Name: item.categoryLevel1Name || item.categoryParentName || item.categoryName || '',
    categoryLevel2Id: item.categoryLevel2Id || item.categoryId || '',
    categoryLevel2Name: item.categoryLevel2Name || item.categoryName || '',
    condition,
    conditionText: item.conditionText || item.conditionName || '',
    location,
    tradeLocation: item.tradeLocation || item.trade_location || location,
    campusId: item.campusId || '',
    campusName: item.campusName || '',
    schoolId: item.schoolId || seller.schoolId || '',
    schoolName,
    college: seller.college || item.college || '',
    dormitoryArea: item.dormitoryArea || '',
    allowBargain: Boolean(item.allowBargain ?? item.allow_bargain ?? false),
    deliveryMethod: item.deliveryMethod || item.delivery_method || 'pickup',
    isFree: Boolean(item.isFree ?? Number(item.price) === 0),
    urgency: item.urgency || 'normal',
    viewCount: Number(item.viewCount || item.view_count || 0),
    favoriteCount: Number(item.favoriteCount || item.favorite_count || 0),
    distanceText: item.distanceText || '',
    distanceValue: item.distanceValue || null,
    pickupPoint: item.pickupPoint || item.pickup_point || '',
    attributes: item.attributes || {}
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
    if (!trade.itemId) return
    if (Number(trade.status) === 5) return
    const key = String(trade.itemId)
    const prev = tradeMap.get(key)
    if (!prev || Number(trade.status) === 4 || (!prev.buyerId && trade.buyerId)) {
      tradeMap.set(key, trade)
    }
  })
  return tradeMap
}

export default {
  components: {
    NavBar
  },
  data() {
    return {
      items: [],
      loading: false,
      currentUserId: ''
    }
  },
  computed: {
    myItems() {
      return this.items
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
            .filter((record) => Number(record.status) === 5 && record.sessionId)
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
    emoji(id) {
      return EMOJIS[id % EMOJIS.length]
    },
    fmt(ts) {
      const time = typeof ts === 'string' ? new Date(ts.replace(/-/g, '/')).getTime() : ts
      const d = new Date(time)
      const now = new Date()
      const diff = now - d
      if (diff < 60000) return '刚刚'
      if (diff < 3600000) return Math.floor(diff / 60000) + '分钟前'
      if (diff < 86400000) return Math.floor(diff / 3600000) + '小时前'
      return `${d.getMonth() + 1}/${d.getDate()}`
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
    shouldShowActionBar(item) {
      return this.isCurrentUserPublisher(item) || this.isPurchaseRecord(item)
    },
    shouldShowContactAction(item) {
      if (this.isCurrentUserPublisher(item)) return true
      return this.isPurchaseRecord(item)
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
    isReservedItem(item) {
      return item?.status === 'reserved'
    },
    canContact(item) {
      if (!item) return false
      if (this.isCurrentUserPublisher(item)) {
        if (this.getChatTargetUserId(item)) return true
        return !this.isSoldItem(item) && !this.isReservedItem(item) && Boolean(item.intentSessionId)
      }
      return Boolean(this.getChatTargetUserId(item))
    },
    contactLabel(item) {
      if (this.isCurrentUserPublisher(item) && !this.getChatTargetUserId(item) && !this.isSoldItem(item) && !this.isReservedItem(item) && item.intentSessionId) return '联系咨询者'
      if (this.isCurrentUserPublisher(item) && !this.canContact(item)) return '暂无买家'
      return this.isCurrentUserPublisher(item) ? '联系买家' : '联系卖家'
    },
    showSecondaryAction(item) {
      if (this.isCurrentUserPublisher(item)) {
        return ['online', 'offline', 'reserved'].includes(item?.status)
      }
      return this.isPurchaseRecord(item)
    },
    statusActionText(item) {
      if (!this.isCurrentUserPublisher(item)) return '查看订单'
      if (item.status === 'online') return '下架'
      if (item.status === 'offline') return '上架'
      if (item.status === 'reserved') return '取消交易'
      return ''
    },
    async handleSecondaryAction(item) {
      if (!item) return
      if (!this.isCurrentUserPublisher(item)) {
        this.openOrder(item)
        return
      }
      if (item.status === 'reserved') {
        await this.cancelTrade(item)
        return
      }
      await this.toggleStatus(item.id)
    },
    openManage() {
      uni.showToast({ title: '功能开发中', icon: 'none' })
    },
    openOrder() {
      uni.showToast({ title: '订单功能开发中', icon: 'none' })
    },
    async cancelTrade(item) {
      if (!item.tradeId) {
        uni.showToast({ title: '暂无交易记录', icon: 'none' })
        return
      }
      try {
        await cancelTradeRecord(item.tradeId)
        uni.showToast({ title: '交易已取消', icon: 'none' })
        await this.loadItems()
      } catch (error) {
        console.error('取消交易失败', error)
        uni.showToast({ title: '取消失败', icon: 'none' })
      }
    },
    openChat(item) {
      if (!item || !item.id) return
      const targetUserId = this.getChatTargetUserId(item)
      if (this.isCurrentUserPublisher(item) && !targetUserId && !this.isSoldItem(item) && !this.isReservedItem(item) && item.intentSessionId) {
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
  background: #F5F5F5;
}

.screen {
  width: 100%;
  background: #F5F5F5;
  min-height: 100vh;
}

.container {
  width: 100%;
  max-width: 430px;
  margin: 0 auto;
  box-sizing: border-box;
  padding: 0 16rpx;
  background: #F5F5F5;
  min-height: 100vh;
  position: relative;
}

.page {
  width: 100%;
  min-height: 100vh;
  box-sizing: border-box;
}

.page-body {
  flex: 1;
  overflow-y: auto;
  padding-top: 20rpx;
}

.empty {
  padding: 120rpx 0;
  text-align: center;
}

.empty-i {
  position: relative;
  width: 82rpx;
  height: 62rpx;
  margin: 0 auto 24rpx;
  border: 3rpx solid #8E8E93;
  border-top: 0;
  border-radius: 8rpx 8rpx 12rpx 12rpx;
  background: transparent;
  box-sizing: border-box;
}

.empty-i::before {
  content: '';
  position: absolute;
  left: -3rpx;
  top: -20rpx;
  width: 82rpx;
  height: 24rpx;
  box-sizing: border-box;
  border: 3rpx solid #8E8E93;
  border-bottom: 0;
  border-radius: 10rpx 10rpx 0 0;
}

.empty-i::after {
  content: '';
  position: absolute;
  left: 20rpx;
  right: 20rpx;
  top: 18rpx;
  height: 0;
  box-sizing: border-box;
  border-top: 3rpx solid #8E8E93;
  border-radius: 999rpx;
}

.empty-t {
  font-size: 28rpx;
  color: #888888;
}

.micard {
  display: flex;
  flex-direction: column;
  gap: 20rpx;
  padding: 22rpx;
  background: #fff;
  border-radius: 18rpx;
  margin-bottom: 20rpx;
  border: 1rpx solid #EEEEEE;
  box-shadow: 0 6rpx 18rpx rgba(92, 122, 153, 0.05);
  box-sizing: border-box;
}

.mi-main {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 22rpx;
  box-sizing: border-box;
}

.miimg {
  width: 132rpx;
  height: 132rpx;
  border-radius: 16rpx;
  background: #F1F3F5;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 48rpx;
  overflow: hidden;
  flex-shrink: 0;
}

.miimg image {
  width: 100%;
  height: 100%;
}

.mibody {
  flex: 1;
  min-width: 0;
}

.mi-manage-btn {
  width: 104rpx;
  height: 58rpx;
  margin: 0;
  padding: 0;
  border-radius: 14rpx;
  background: #F3F6F8;
  color: #4A6278;
  border: 1rpx solid #DDE6EF;
  font-size: 24rpx;
  font-weight: 700;
  line-height: 58rpx;
  text-align: center;
  flex-shrink: 0;
  box-sizing: border-box;
}

.mi-manage-btn::after {
  border: none;
}

.miname {
  font-size: 29rpx;
  font-weight: 700;
  color: #1D1D1F;
  margin-bottom: 10rpx;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.miprice {
  font-size: 34rpx;
  font-weight: 850;
  color: #1D1D1F;
  margin-bottom: 8rpx;
  line-height: 1.1;
}

.miprice small {
  font-size: 22rpx;
  font-weight: 800;
}

.mitime {
  font-size: 22rpx;
  color: #8E8E93;
}

.mi-actions {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 14rpx;
  padding-top: 18rpx;
  border-top: 1rpx solid #F0F0F0;
  box-sizing: border-box;
}

.mi-action-btn {
  flex: 1;
  height: 68rpx;
  margin: 0;
  padding: 0;
  border-radius: 14rpx;
  font-size: 25rpx;
  font-weight: 700;
  line-height: 68rpx;
  text-align: center;
  border: none;
  box-sizing: border-box;
  display: flex;
  align-items: center;
  justify-content: center;
}

.mi-action-contact {
  background: #F3F6F8;
  color: #4A6278;
}

.mi-action-status {
  background: #F7F7F9;
  color: #5C5C60;
}

.mi-action-disabled {
  background: #F5F5F5;
  color: #A6A6A6;
}

.mi-action-btn::after {
  border: none;
}
</style>
