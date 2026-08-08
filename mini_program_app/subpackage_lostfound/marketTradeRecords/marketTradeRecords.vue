<template>
  <view class="page-root">
    <view class="container">
      <common-page-header title="交易记录" :fixed="true" :placeholder="true" :showBack="true" />

      <view class="status-tabs">
        <view
          v-for="tab in tabs"
          :key="tab.value"
          class="status-tab"
          :class="{ active: activeStatus === tab.value }"
          @click="activeStatus = tab.value"
        >
          {{ tab.label }}
        </view>
      </view>

      <scroll-view scroll-y class="page-body">
        <view v-if="filteredRecords.length === 0" class="empty">
          <text>暂无交易记录</text>
        </view>

        <view v-for="record in filteredRecords" :key="record.id" class="record-card" @click="openDetail(record.itemId)">
          <view class="status" :class="statusClass(record.status)">{{ statusText(record.status) }}</view>

          <view class="record-title">交易编号 {{ record.id || '-' }}</view>

          <view class="record-meta">
            <view class="meta-item">
              <view class="meta-icon buyer-icon"></view>
              <text class="meta-label">买家</text>
              <text class="meta-name">{{ displayBuyerName(record) }}</text>
            </view>
            <view class="meta-item">
              <view class="meta-icon seller-icon"></view>
              <text class="meta-label">卖家</text>
              <text class="meta-name">{{ displaySellerName(record) }}</text>
            </view>
          </view>

          <view class="record-time">
            <view class="time-icon"></view>
            <text>{{ formatTime(record.updateTime || record.createTime) }}</text>
          </view>

          <view class="record-arrow"></view>
        </view>
      </scroll-view>

      <market-bottom-bar activeTab="profile" />
    </view>
  </view>
</template>

<script>
import CommonPageHeader from '@/components/common-page-header/common-page-header.vue'
import MarketBottomBar from '@/components/market-bottom-bar/market-bottom-bar.vue'
import { getTradeRecords } from '@/api/secondhand'
import { getToken, getUserInfo } from '@/utils/storage.js'

function firstValue(...values) {
  return values.find((value) => value !== undefined && value !== null && value !== '')
}

function normalizeText(value) {
  if (value === undefined || value === null) return ''
  return String(value).trim()
}

function decodeBase64Url(value) {
  const chars = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/='
  const input = `${value || ''}`.replace(/-/g, '+').replace(/_/g, '/')
  let output = ''
  let buffer = 0
  let bits = 0
  for (let i = 0; i < input.length; i += 1) {
    const index = chars.indexOf(input.charAt(i))
    if (input.charAt(i) === '=') break
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
    return payload ? JSON.parse(decodeBase64Url(payload)) : {}
  } catch (e) {
    return {}
  }
}

function getUserDisplayName(userInfo = {}, tokenPayload = {}) {
  const nestedUser = userInfo.user || userInfo.profile || userInfo.data || {}
  return normalizeText(firstValue(
    userInfo.nickname,
    userInfo.nickName,
    userInfo.username,
    userInfo.realName,
    nestedUser.nickname,
    nestedUser.nickName,
    nestedUser.username,
    nestedUser.realName,
    tokenPayload.nickname,
    tokenPayload.nickName,
    tokenPayload.username,
    tokenPayload.sub
  ))
}

function getNestedDisplayName(value) {
  if (!value || typeof value !== 'object') return ''
  return normalizeText(firstValue(
    value.nickname,
    value.nickName,
    value.username,
    value.realName,
    value.name
  ))
}

export default {
  components: { CommonPageHeader, MarketBottomBar },
  data() {
    return {
      activeStatus: 'ALL',
      records: [],
      currentUserName: '',
      tabs: [
        { label: '全部', value: 'ALL' },
        { label: '待确认', value: 'WAIT_CONFIRM' },
        { label: '交易中', value: 'TRADING' },
        { label: '已完成', value: 'COMPLETED' },
        { label: '已取消', value: 'CANCELLED' }
      ]
    }
  },
  computed: {
    filteredRecords() {
      if (this.activeStatus === 'ALL') return this.records
      return this.records.filter((record) => record.status === this.activeStatus)
    }
  },
  onShow() {
    this.loadCurrentUserName()
    this.loadRecords()
  },
  methods: {
    loadCurrentUserName() {
      let userInfo = getUserInfo() || {}
      const raw = uni.getStorageSync('userInfo')
      if (raw && typeof raw === 'object') {
        userInfo = raw
      } else if (raw && typeof raw === 'string') {
        try {
          userInfo = JSON.parse(raw)
        } catch (e) {}
      }
      this.currentUserName = getUserDisplayName(userInfo, decodeTokenPayload(getToken()))
    },
    async loadRecords() {
      try {
        const res = await getTradeRecords({ current: 1, size: 100 })
        this.records = Array.isArray(res?.data?.records) ? res.data.records : (Array.isArray(res?.data) ? res.data : [])
      } catch (e) {
        console.error('加载交易记录失败', e)
        this.records = []
      }
    },
    statusText(status) {
      const map = { WAIT_CONFIRM: '待确认', TRADING: '交易中', COMPLETED: '已完成', CANCELLED: '已取消' }
      return map[status] || '交易记录'
    },
    statusClass(status) {
      return `is-${String(status || '').toLowerCase().replace(/_/g, '-')}`
    },
    displayBuyerName(record = {}) {
      return normalizeText(firstValue(
        record.buyerName,
        record.buyerUsername,
        record.buyerNickname,
        getNestedDisplayName(record.buyer),
        getNestedDisplayName(record.buyerUser),
        record.isSeller ? record.otherUsername : this.currentUserName,
        record.buyerId
      )) || '-'
    },
    displaySellerName(record = {}) {
      return normalizeText(firstValue(
        record.sellerName,
        record.sellerUsername,
        record.sellerNickname,
        getNestedDisplayName(record.seller),
        getNestedDisplayName(record.sellerUser),
        record.isSeller ? this.currentUserName : record.otherUsername,
        record.sellerId
      )) || '-'
    },
    openDetail(itemId) {
      if (itemId) uni.navigateTo({ url: `/subpackage_lostfound/lostfoundDetail/lostfoundDetail?id=${itemId}` })
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
.page-root {
  width: 100%;
  height: 100vh;
  min-height: 100vh;
  background: #F6F8FB;
  overflow: hidden;
}

.container {
  width: 100%;
  max-width: 430px;
  height: 100vh;
  min-height: 100vh;
  margin: 0 auto;
  box-sizing: border-box;
  background: #F6F8FB;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.status-tabs {
  display: flex;
  align-items: center;
  gap: 18rpx;
  padding: 34rpx 26rpx 36rpx;
  box-sizing: border-box;
  background: #FFFFFF;
  flex-shrink: 0;
}

.status-tab {
  width: 126rpx;
  height: 58rpx;
  border-radius: 999rpx;
  border: 1rpx solid #E5E9EF;
  background: #FFFFFF;
  color: #808896;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 27rpx;
  font-weight: 700;
  line-height: 58rpx;
  box-sizing: border-box;
  box-shadow: 0 3rpx 10rpx rgba(24, 38, 56, 0.03);
}

.status-tab:first-child {
  width: 120rpx;
}

.status-tab.active {
  border-color: #3D7EFF;
  background: #3D7EFF;
  color: #FFFFFF;
  font-weight: 900;
  box-shadow: 0 12rpx 22rpx rgba(61, 126, 255, 0.22);
}

.page-body {
  flex: 1;
  min-height: 0;
  height: 0;
  padding: 30rpx 28rpx 170rpx;
  box-sizing: border-box;
  background: #F6F8FB;
}

.empty {
  padding: 180rpx 0;
  text-align: center;
  color: #8A96A3;
  font-size: 27rpx;
}

.record-card {
  position: relative;
  min-height: 216rpx;
  padding: 30rpx 72rpx 32rpx 28rpx;
  margin-bottom: 22rpx;
  background: #FFFFFF;
  border-radius: 18rpx;
  box-shadow: 0 14rpx 34rpx rgba(29, 43, 62, 0.06);
  box-sizing: border-box;
  overflow: hidden;
}

.status {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 98rpx;
  height: 46rpx;
  padding: 0 18rpx;
  border-radius: 999rpx;
  background: #EAF2FF;
  color: #2876E8;
  font-size: 24rpx;
  font-weight: 900;
  line-height: 46rpx;
  box-sizing: border-box;
}

.status.is-completed {
  background: #E5F7EA;
  color: #2C8E54;
}

.status.is-cancelled {
  background: #EEF1F4;
  color: #818996;
}

.status.is-wait-confirm,
.status.is-trading {
  background: #EAF2FF;
  color: #2876E8;
}

.record-title {
  margin-top: 26rpx;
  color: #172331;
  font-size: 31rpx;
  font-weight: 900;
  line-height: 1.2;
}

.record-meta {
  display: flex;
  align-items: center;
  flex-direction: column;
  gap: 14rpx;
  margin-top: 20rpx;
  color: #667282;
  font-size: 24rpx;
  line-height: 1.25;
}

.meta-item {
  width: 100%;
  display: flex;
  align-items: center;
  gap: 10rpx;
  min-width: 0;
}

.meta-label {
  flex-shrink: 0;
  width: 58rpx;
  color: #667282;
  font-size: 24rpx;
  font-weight: 800;
}

.meta-name {
  flex: 1;
  min-width: 0;
  color: #475467;
  font-size: 24rpx;
  font-weight: 800;
  line-height: 1.35;
  overflow-wrap: anywhere;
  word-break: break-all;
}

.meta-icon {
  position: relative;
  width: 30rpx;
  height: 30rpx;
  color: #2F7EEA;
  flex-shrink: 0;
  box-sizing: border-box;
}

.buyer-icon::before {
  content: '';
  position: absolute;
  left: 9rpx;
  top: 3rpx;
  width: 12rpx;
  height: 12rpx;
  border: 3rpx solid currentColor;
  border-radius: 50%;
  box-sizing: border-box;
}

.buyer-icon::after {
  content: '';
  position: absolute;
  left: 5rpx;
  bottom: 3rpx;
  width: 20rpx;
  height: 12rpx;
  border: 3rpx solid currentColor;
  border-top-left-radius: 14rpx;
  border-top-right-radius: 14rpx;
  border-bottom: 0;
  box-sizing: border-box;
}

.seller-icon::before {
  content: '';
  position: absolute;
  left: 5rpx;
  bottom: 5rpx;
  width: 20rpx;
  height: 15rpx;
  border: 3rpx solid currentColor;
  border-radius: 3rpx;
  box-sizing: border-box;
}

.seller-icon::after {
  content: '';
  position: absolute;
  left: 3rpx;
  top: 5rpx;
  width: 24rpx;
  height: 10rpx;
  border: 3rpx solid currentColor;
  border-bottom: 0;
  border-radius: 8rpx 8rpx 2rpx 2rpx;
  box-sizing: border-box;
}

.record-time {
  display: flex;
  align-items: center;
  gap: 10rpx;
  margin-top: 18rpx;
  color: #7D8895;
  font-size: 24rpx;
  line-height: 1.25;
}

.time-icon {
  position: relative;
  width: 30rpx;
  height: 30rpx;
  border: 3rpx solid #8C98A6;
  border-radius: 50%;
  box-sizing: border-box;
  flex-shrink: 0;
}

.time-icon::before {
  content: '';
  position: absolute;
  left: 12rpx;
  top: 6rpx;
  width: 3rpx;
  height: 8rpx;
  border-radius: 999rpx;
  background: #8C98A6;
}

.time-icon::after {
  content: '';
  position: absolute;
  left: 12rpx;
  top: 13rpx;
  width: 8rpx;
  height: 3rpx;
  border-radius: 999rpx;
  background: #8C98A6;
}

.record-arrow {
  position: absolute;
  right: 36rpx;
  top: 50%;
  width: 22rpx;
  height: 22rpx;
  border-top: 5rpx solid #8B96A3;
  border-right: 5rpx solid #8B96A3;
  transform: translateY(-50%) rotate(45deg);
  box-sizing: border-box;
}
</style>
