<template>
  <view class="page-root">
    <view class="container">
      <common-page-header title="我的购买" :fixed="true" :placeholder="true" :showBack="true" />
      <scroll-view scroll-y class="page-body">
        <view v-if="items.length === 0" class="empty">
          <image class="empty-illustration" src="/static/illustrations/market-empty-publish.svg" mode="aspectFit" />
          <view class="empty-title">还没有购买记录</view>
          <view class="empty-subtitle">看中的校园闲置会在这里留下交易记录</view>
          <button class="empty-primary-btn" @click="goToMarket">去逛市集</button>
          <view class="empty-link" @click="goToHotList">
            <text>看看热门商品</text>
            <view class="empty-link-arrow"></view>
          </view>
        </view>
        <view v-for="record in items" :key="record.id" class="record-card" @click="openChat(record)">
          <image class="cover" :src="record.itemImage || '/static/images/default-goods.svg'" mode="aspectFill" />
          <view class="main">
            <view class="title">{{ record.itemTitle || '校园市集商品' }}</view>
            <view class="meta">{{ statusText(record.status) }}</view>
            <view class="time">{{ formatTime(record.createTime) }}</view>
          </view>
        </view>
      </scroll-view>
      <market-bottom-bar activeTab="profile" />
    </view>
  </view>
</template>

<script>
import CommonPageHeader from '@/components/common-page-header/common-page-header.vue'
import MarketBottomBar from '@/components/market-bottom-bar/market-bottom-bar.vue'
import { createOrGetChatSession, getTradeRecords } from '@/api/secondhand'
import { getToken, getUserInfo } from '@/utils/storage.js'

function firstValue(...values) {
  return values.find((value) => value !== undefined && value !== null && value !== '')
}

function normalizeId(value) {
  if (value === undefined || value === null || value === '') return ''
  if (typeof value !== 'object') return String(value)
  const id = firstValue(
    value?.id,
    value?.userId,
    value?.buyerId,
    value?.sellerId,
    value?.user_id,
    value?.buyer_id,
    value?.seller_id,
    value?.uid
  )
  return id === undefined || id === null ? '' : String(id)
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

function getRecordBuyerId(record) {
  const buyer = record?.buyer || record?.buyerUser || {}
  return normalizeId(firstValue(record?.buyerId, record?.buyerUserId, record?.buyer_id, buyer))
}

export default {
  components: { CommonPageHeader, MarketBottomBar },
  data() {
    return {
      items: [],
      currentUserId: ''
    }
  },
  onShow() {
    this.loadCurrentUser()
    this.loadRecords()
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
    async loadRecords() {
      try {
        const res = await getTradeRecords({ current: 1, size: 100, role: 'buyer' })
        const records = Array.isArray(res?.data?.records) ? res.data.records : (Array.isArray(res?.data) ? res.data : [])
        this.items = records
      } catch (e) {
        console.error('加载购买记录失败', e)
        this.items = []
      }
    },
    statusText(status) {
      const map = { WAIT_CONFIRM: '等待对方确认', TRADING: '双方已确认', COMPLETED: '已完成', CANCELLED: '已取消' }
      return map[status] || '交易记录'
    },
    async openChat(record) {
      if (!record.itemId) return
      try {
        const res = await createOrGetChatSession(record.itemId, record.sellerId)
        const sessionId = res?.data?.sessionId || res?.data?.id
        uni.navigateTo({ url: `/subpackage_lostfound/lostfoundChat/lostfoundChat?${sessionId ? `sessionId=${sessionId}` : `itemId=${record.itemId}`}` })
      } catch (e) {
        uni.navigateTo({ url: `/subpackage_lostfound/lostfoundDetail/lostfoundDetail?id=${record.itemId}` })
      }
    },
    goToMarket() {
      uni.redirectTo({
        url: '/subpackage_lostfound/lostfoundList/lostfoundList',
        animationType: 'none',
        animationDuration: 0,
        fail: () => {
          uni.navigateTo({
            url: '/subpackage_lostfound/lostfoundList/lostfoundList',
            animationType: 'none',
            animationDuration: 0
          })
        }
      })
    },
    goToHotList() {
      uni.navigateTo({
        url: '/subpackage_lostfound/marketHotList/marketHotList'
      })
    },
    formatTime(value) {
      if (!value) return ''
      const date = new Date(String(value).replace(/-/g, '/'))
      return `${date.getMonth() + 1}/${date.getDate()} ${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
    }
  }
}
</script>

<style scoped>
.page-root { min-height: 100vh; background: #F7F8FA; }
.container { width: 100%; max-width: 430px; min-height: 100vh; margin: 0 auto; box-sizing: border-box; background: #F7F8FA; }
.page-body { height: calc(100vh - 88rpx); padding: 24rpx 16rpx 150rpx; box-sizing: border-box; background: #F7F8FA; }
.empty {
  min-height: calc(100vh - 320rpx - var(--status-bar-height, 0px));
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
.record-card { display: flex; gap: 18rpx; padding: 20rpx; margin-bottom: 18rpx; background: #fff; border-radius: 18rpx; box-shadow: 0 6rpx 18rpx rgba(43, 68, 94, 0.08); }
.cover { width: 132rpx; height: 132rpx; border-radius: 14rpx; background: #edf3f8; }
.main { flex: 1; min-width: 0; }
.title { color: #172331; font-size: 28rpx; font-weight: 900; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.meta { margin-top: 18rpx; color: #5c8ab8; font-size: 24rpx; font-weight: 800; }
.time { margin-top: 14rpx; color: #8aa1b2; font-size: 22rpx; }
</style>
