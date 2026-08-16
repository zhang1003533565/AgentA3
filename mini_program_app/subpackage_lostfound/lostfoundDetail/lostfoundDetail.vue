<template>
  <view class="page-root">
    <view class="screen">
      <view class="container">
        <nav-bar
          title="商品详情"
          :fixed="true"
          :placeholder="true"
          :showBack="true"
          heightRpx="88"
        >
          <template #right>
            <view
              class="menu-dots-btn"
              @click="showMenu = true"
            >
              <view class="menu-dot"></view>
              <view class="menu-dot"></view>
              <view class="menu-dot"></view>
            </view>
          </template>
        </nav-bar>

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
            </view>
            <view class="title">{{ item.title || '未命名商品' }}</view>
            <view class="meta-row">
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
            <view class="avatar">
              <image
                v-if="item.sellerAvatar"
                class="avatar-image"
                :src="item.sellerAvatar"
                mode="aspectFill"
                @error="handleSellerAvatarError"
              />
              <text v-else class="avatar-initial">{{ sellerInitial }}</text>
            </view>
            <view class="seller-info">
              <view class="seller-name">{{ item.sellerName || '校园用户' }}</view>
              <view class="seller-time">{{ formatTime(item.createTime) }}发布</view>
            </view>
            <view class="seller-action" @click.stop="goUserHomepage">查看主页 ›</view>
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
          <button
            v-if="!isSeller"
            class="favorite-button-bottom"
            :class="{ 'favorite-button-bottom--active': item.isFavorited }"
            :disabled="favoriteLoading"
            @click="toggleFavorite"
          >
            <image class="favorite-icon-bottom" :src="item.isFavorited ? '/static/icons/star-filled.svg' : '/static/icons/line/star.svg'" mode="aspectFit" />
            <text>{{ item.isFavorited ? '已收藏' : '收藏' }}</text>
          </button>
          <button v-if="isSeller" class="contact-button contact-button--full" @click="openEditOverlay">
            <text>编辑商品</text>
          </button>
          <button v-if="!isSeller" class="contact-button contact-button--full" @click="contactSeller">
            <text>联系TA</text>
          </button>
        </view>

        <!-- 菜单弹窗 -->
        <view v-show="showMenu" class="modal-mask" @click="showMenu = false">
          <view class="menu-modal" @click.stop>
            <view v-if="isSeller" class="menu-list">
              <!-- #ifdef MP-WEIXIN -->
              <button class="menu-item menu-item--button" open-type="share">分享商品</button>
              <!-- #endif -->
              <!-- #ifdef H5 -->
              <button class="menu-item menu-item--button" @click="shareProduct">分享商品</button>
              <!-- #endif -->
              <view class="menu-item menu-item--danger" @click="handleOffline">
                <text class="menu-text">下架物品</text>
              </view>
              <view class="menu-item" @click="showMenu = false">
                <text class="menu-text">取消</text>
              </view>
            </view>
            <view v-else class="menu-list">
              <!-- #ifdef MP-WEIXIN -->
              <button class="menu-item menu-item--button" open-type="share">分享商品</button>
              <!-- #endif -->
              <!-- #ifdef H5 -->
              <button class="menu-item menu-item--button" @click="shareProduct">分享商品</button>
              <!-- #endif -->
              <view class="menu-item menu-item--danger" @click="showReportForm = true; showMenu = false">
                <text class="menu-text">举报物品</text>
              </view>
              <view class="menu-item" @click="showMenu = false">
                <text class="menu-text">取消</text>
              </view>
            </view>
          </view>
        </view>

        <!-- 举报弹窗 -->
        <view v-if="showReportForm" class="modal-mask" @click="showReportForm = false">
          <view class="report-modal" @click.stop>
            <view class="modal-title">举报物品</view>
            <view class="modal-subtitle">请填写以下信息提交举报</view>
            <view class="report-form">
              <view class="form-item">
                <text class="form-label">姓名</text>
                <input class="form-input" v-model="reportForm.reporterName" placeholder="请输入您的姓名" />
              </view>
              <view class="form-item">
                <text class="form-label">联系方式</text>
                <input class="form-input" v-model="reportForm.reporterContact" placeholder="请输入手机号或邮箱" />
              </view>
              <view class="form-item">
                <text class="form-label">详细理由</text>
                <textarea class="form-textarea" v-model="reportForm.reason" placeholder="请详细描述举报理由（必填）" />
              </view>
            </view>
            <view class="modal-actions">
              <view class="modal-btn modal-btn--cancel" @click="showReportForm = false">取消</view>
              <view class="modal-btn modal-btn--confirm" @click="submitReport">提交举报</view>
            </view>
          </view>
        </view>
      </view>
    </view>
    <market-publish-overlay
      v-if="editOverlayMounted"
      :visible="editOverlayVisible"
      :itemId="itemId"
      @close="closeEditOverlay"
    />
  </view>
</template>

<script>
import NavBar from '@/components/nav-bar/nav-bar.vue'
import MarketPublishOverlay from '@/components/market-publish-overlay/market-publish-overlay.vue'
import {
  favoriteSecondhandItem,
  getSecondhandItemDetail,
  getTradeRecords,
  offlineSecondhandItem,
  recordBrowseHistory,
  reportSecondhandItem,
  unfavoriteSecondhandItem
} from '@/api/secondhand'
import { getToken, getUserInfo } from '@/utils/storage'
import { buildDefaultAvatar, pickOtherAvatar } from '@/subpackage_lostfound/utils/avatar.js'
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
    tradeType: raw.tradeType || raw.trade_type || 'sell',
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
    userName: seller.username || raw.sellerName || raw.userName || '',
    sellerAvatar: pickOtherAvatar({
      otherAvatar: seller.avatar,
      otherAvatarUrl: seller.avatarUrl,
      otherUserAvatar: seller.userAvatar,
      sellerAvatar: raw.sellerAvatar,
      sellerAvatarUrl: raw.sellerAvatarUrl,
      userAvatar: raw.userAvatar,
      avatar: raw.avatar,
      avatarUrl: raw.avatarUrl
    }) || buildDefaultAvatar({
      username: seller.username || raw.sellerName || raw.userName || 'seller'
    }),
    isFavorited: Boolean(raw.isFavorited),
    createTime: raw.createTime || raw.ctime || ''
  }
}

export default {
  components: {
    NavBar,
    MarketPublishOverlay
  },
  data() {
    return {
      itemId: null,
      item: normalizeItem(),
      activeTrade: null,
      completedTrade: null,
      favoriteLoading: false,
      pageBodyHeight: 0,
      imageIndex: 0,
      showMenu: false,
      showReportForm: false,
      editOverlayVisible: false,
      editOverlayMounted: false,
      reportForm: {
        reporterName: '',
        reporterContact: '',
        reason: ''
      }
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
      return '联系TA'
    }
  },
  async onLoad(options) {
    this.itemId = options.id
    this.calcPageBodyHeight()
    await this.loadItem()
  },
  // #ifdef MP-WEIXIN
  onShareAppMessage() {
    const title = this.item.name || this.item.title || '校园集市商品'
    const path = `/subpackage_lostfound/lostfoundDetail/lostfoundDetail?id=${this.itemId}`
    const imageUrl = this.item.images && this.item.images.length ? this.item.images[0] : ''
    return { title, path, imageUrl }
  },
  onShareTimeline() {
    const title = this.item.name || this.item.title || '校园集市商品'
    const query = `id=${this.itemId}`
    const imageUrl = this.item.images && this.item.images.length ? this.item.images[0] : ''
    return { title, query, imageUrl }
  },
  // #endif
  methods: {
    openEditOverlay() {
      this.editOverlayMounted = true
      this.editOverlayVisible = true
    },
    closeEditOverlay() {
      this.editOverlayVisible = false
      // 等待面板滑出动画结束后移除组件，避免全屏遮罩层挡住点击
      setTimeout(() => {
        this.editOverlayMounted = false
      }, 300)
      // 刷新商品详情，显示编辑后的最新数据
      this.loadItem()
    },
    goUserHomepage() {
      if (!this.item.sellerId) {
        uni.showToast({ title: '用户信息不存在', icon: 'none' })
        return
      }
      const userName = encodeURIComponent(this.item.sellerName || '校园用户')
      const avatar = encodeURIComponent(this.item.sellerAvatar || '')
      uni.navigateTo({
        url: `/subpackage_lostfound/userHomepage/userHomepage?userId=${this.item.sellerId}&userName=${userName}&avatar=${avatar}`
      })
    },
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
        const msg = e?.data?.msg || e?.msg || (e?.statusCode === 403 ? '该物品已下架' : '商品不存在')
        uni.showToast({ title: msg, icon: 'none' })
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
    async saveBrowseHistory() {
      if (!this.item.id) return
      try {
        const current = uni.getStorageSync(BROWSE_HISTORY_KEY)
        const list = Array.isArray(current) ? current : []
        const nextItem = {
          id: this.item.id,
          title: this.item.title,
          image: this.item.images?.[0] || '',
          images: this.item.images || [],
          price: this.item.price,
          tradeType: this.item.tradeType || 'sell',
          campusName: this.item.campusName,
          tradeLocation: this.item.tradeLocation || this.item.pickupPoint,
          pickupPoint: this.item.pickupPoint || '',
          userName: this.item.userName || this.item.sellerName || '',
          ctime: this.item.createTime || '',
          viewTime: Date.now()
        }
        const next = [nextItem, ...list.filter((item) => item.id !== this.item.id)].slice(0, 50)
        uni.setStorageSync(BROWSE_HISTORY_KEY, next)
      } catch (e) {
        console.warn('保存浏览记录到本地失败', e)
      }
      try {
        const token = getToken()
        if (token) {
          await recordBrowseHistory(this.item.id)
        }
      } catch (e) {
        console.warn('保存浏览记录到服务器失败', e)
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
    handleSellerAvatarError() {
      this.item.sellerAvatar = ''
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
    },
    async handleOffline() {
      this.showMenu = false
      uni.showModal({
        title: '确认下架',
        content: '确定要下架这件商品吗？下架后商品将不再展示。',
        confirmText: '确认下架',
        confirmColor: '#ea6948',
        success: async (res) => {
          if (res.confirm) {
            try {
              await offlineSecondhandItem(this.item.id)
              uni.showToast({ title: '已下架', icon: 'none' })
              await this.loadItem()
            } catch (e) {
              console.error('下架商品失败', e)
              uni.showToast({ title: e?.data?.msg || e?.msg || '下架失败', icon: 'none' })
            }
          }
        }
      })
    },
    // #ifdef H5
    async shareProduct() {
      this.showMenu = false
      if (!this.itemId) {
        uni.showToast({ title: '商品信息缺失', icon: 'none' })
        return
      }
      const title = this.item.title || '校园集市商品'
      const shareUrl = window.location.href.split('#')[0] +
        '#/subpackage_lostfound/lostfoundDetail/lostfoundDetail?id=' +
        encodeURIComponent(this.itemId)

      // 1) 浏览器原生分享（移动端 / 支持的桌面浏览器）
      if (typeof navigator !== 'undefined' && navigator.share) {
        try {
          await navigator.share({ title, url: shareUrl })
        } catch (e) {
          if (e && e.name !== 'AbortError') {
            uni.showToast({ title: '分享失败', icon: 'none' })
          }
        }
        return
      }

      // 2) 桌面浏览器降级：复制链接到剪贴板
      let copied = false
      try {
        if (navigator.clipboard && navigator.clipboard.writeText) {
          await navigator.clipboard.writeText(shareUrl)
          copied = true
        }
      } catch (e) { copied = false }

      // 3) 旧浏览器 / 非安全上下文兜底
      if (!copied) {
        try {
          const ta = document.createElement('textarea')
          ta.value = shareUrl
          ta.style.cssText = 'position:fixed;top:-9999px;left:0;opacity:0'
          document.body.appendChild(ta)
          ta.focus()
          ta.select()
          copied = document.execCommand('copy')
          document.body.removeChild(ta)
        } catch (e) { copied = false }
      }

      uni.showToast({
        title: copied ? '链接已复制，去粘贴给好友' : '复制失败，请手动复制地址栏',
        icon: 'none',
        duration: 2500
      })
    },
    // #endif
    async submitReport() {
      if (!this.reportForm.reporterName.trim()) {
        uni.showToast({ title: '请填写姓名', icon: 'none' })
        return
      }
      if (!this.reportForm.reporterContact.trim()) {
        uni.showToast({ title: '请填写联系方式', icon: 'none' })
        return
      }
      if (!this.reportForm.reason.trim()) {
        uni.showToast({ title: '请填写举报理由', icon: 'none' })
        return
      }

      try {
        await reportSecondhandItem({
          itemId: this.item.id,
          reporterName: this.reportForm.reporterName.trim(),
          reporterContact: this.reportForm.reporterContact.trim(),
          reason: this.reportForm.reason.trim()
        })
        uni.showToast({ title: '举报已提交', icon: 'success' })
        this.showReportForm = false
        this.reportForm = {
          reporterName: '',
          reporterContact: '',
          reason: ''
        }
      } catch (e) {
        console.error('提交举报失败', e)
        uni.showToast({ title: e?.data?.msg || e?.msg || '提交失败', icon: 'none' })
      }
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

.menu-dots-btn {
  width: 56rpx;
  height: 56rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 6rpx;
  border-radius: 50%;
}

.menu-dots-btn:active {
  opacity: 0.6;
}

.menu-dot {
  width: 7rpx;
  height: 7rpx;
  border-radius: 50%;
  background: #1D1D1F;
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
  overflow: hidden;
  background: linear-gradient(135deg, #82aee0, #5f8fc4);
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 34rpx;
  font-weight: 900;
}

.avatar-image {
  width: 100%;
  height: 100%;
  display: block;
}

.avatar-initial {
  line-height: 1;
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
  padding: 16rpx 24rpx calc(16rpx + env(safe-area-inset-bottom));
  box-sizing: border-box;
  background: #fff;
  border-top: 1rpx solid rgba(132, 151, 168, 0.14);
  z-index: 20;
  display: flex;
  gap: 20rpx;
  align-items: center;
}

.favorite-button-bottom {
  flex-shrink: 0;
  min-width: 120rpx;
  height: 88rpx;
  padding: 0 24rpx;
  border-radius: 24rpx;
  background: #f5f7fa;
  color: #5a6478;
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 10rpx;
  font-size: 26rpx;
  font-weight: 700;
  box-sizing: border-box;
  border: none;
}

.favorite-button-bottom::after {
  border: none;
}

.favorite-button-bottom--active {
  background: #fff8e1;
  color: #ff9800;
}

.favorite-icon-bottom {
  width: 32rpx;
  height: 32rpx;
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

.contact-button--full {
  flex: 1;
  width: auto;
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

/* 弹窗遮罩 */
.modal-mask {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

/* 菜单弹窗 */
.menu-modal {
  width: 300rpx;
  background: #fff;
  border-radius: 20rpx;
  overflow: hidden;
  box-shadow: 0 8rpx 32rpx rgba(0, 0, 0, 0.2);
}

.menu-list {
  display: flex;
  flex-direction: column;
}

.menu-item {
  padding: 32rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  border-bottom: 1rpx solid #f0f0f0;
}

.menu-item:last-child {
  border-bottom: none;
}

.menu-item--danger .menu-text {
  color: #ea6948;
}

.menu-text {
  font-size: 30rpx;
  color: #333;
  font-weight: 500;
}

.menu-item--button {
  width: 100%;
  margin: 0;
  padding: 32rpx;
  background: transparent;
  border: none;
  border-radius: 0;
  line-height: normal;
  font-size: 30rpx;
  color: #333;
  font-weight: 500;
}

.menu-item--button::after {
  display: none;
}

/* 举报弹窗 */
.report-modal {
  width: 600rpx;
  background: #fff;
  border-radius: 24rpx;
  padding: 40rpx;
  box-shadow: 0 8rpx 32rpx rgba(0, 0, 0, 0.2);
}

.modal-title {
  font-size: 36rpx;
  font-weight: bold;
  color: #111c2b;
  text-align: center;
  margin-bottom: 12rpx;
}

.modal-subtitle {
  font-size: 26rpx;
  color: #999;
  text-align: center;
  margin-bottom: 32rpx;
}

.report-form {
  margin-bottom: 32rpx;
}

.form-item {
  margin-bottom: 24rpx;
}

.form-label {
  font-size: 28rpx;
  color: #333;
  font-weight: 500;
  margin-bottom: 12rpx;
  display: block;
}

.form-input {
  width: 100%;
  height: 72rpx;
  padding: 0 24rpx;
  border: 1rpx solid #e0e0e0;
  border-radius: 12rpx;
  font-size: 28rpx;
  box-sizing: border-box;
}

.form-picker {
  width: 100%;
}

.picker-value {
  width: 100%;
  height: 72rpx;
  padding: 0 24rpx;
  border: 1rpx solid #e0e0e0;
  border-radius: 12rpx;
  font-size: 28rpx;
  color: #333;
  display: flex;
  align-items: center;
  box-sizing: border-box;
}

.picker-placeholder {
  color: #999;
}

.form-textarea {
  width: 100%;
  height: 160rpx;
  padding: 20rpx 24rpx;
  border: 1rpx solid #e0e0e0;
  border-radius: 12rpx;
  font-size: 28rpx;
  box-sizing: border-box;
}

.modal-actions {
  display: flex;
  gap: 24rpx;
}

.modal-btn {
  flex: 1;
  height: 80rpx;
  border-radius: 12rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 30rpx;
  font-weight: 500;
}

.modal-btn--cancel {
  background: #f5f5f5;
  color: #666;
}

.modal-btn--confirm {
  background: #ea6948;
  color: #fff;
}
</style>
