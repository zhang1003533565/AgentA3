<template>
  <view class="page-root">
    <view class="screen">
      <view class="container">
        <view class="page-content">
          <!-- ===== 1. Header: location + message ===== -->
          <nav-bar
            title=""
            :fixed="true"
            :placeholder="true"
            :showBack="false"
            :border="false"
            background="#FFFFFF"
          >
            <template #left>
              <view class="market-back-button" @click="onBackToApp">‹</view>
            </template>
            <template #center>
            </template>
            <template #right>
              <view class="header-right" @click="onSelectSchool">
                <image class="header-loc-icon" src="/static/icons/mi-location.svg" mode="aspectFit" />
                <text class="header-school-name">{{ schoolName }}</text>
                <text class="header-chevron">▾</text>
              </view>
            </template>
          </nav-bar>

          <scroll-view scroll-y class="page-body" :show-scrollbar="false">
            <!-- ===== 2. Search (sticky below nav) ===== -->
            <view class="search-block search-block--sticky">
              <view class="search-pill" @click="goToSearch">
                <image class="search-pill-icon" src="/static/icons/search.svg" mode="aspectFit" />
                <input class="search-pill-input" value="搜索" disabled />
              </view>
              <view class="search-scan-btn" @click="onScan">
                <image class="search-scan-icon" src="/static/icons/camera.svg" mode="aspectFit" />
              </view>
            </view>

            <!-- ===== 3. Banner ===== -->
            <view class="banner-block">
              <view
                class="banner-card"
                v-for="(b, bi) in banners"
                :key="bi"
                v-show="bi === bannerIndex"
              >
                <!-- decorative circles -->
                <view class="banner-deco banner-deco--1"></view>
                <view class="banner-deco banner-deco--2"></view>
                <view class="banner-deco banner-deco--3"></view>
                <!-- content -->
                <view class="banner-content">
                  <view class="banner-tag">{{ b.tag }}</view>
                  <text class="banner-title">{{ b.title }}</text>
                  <text class="banner-sub">{{ b.subtitle }}</text>
                </view>
                <!-- dots -->
                <view class="banner-dots">
                  <view
                    v-for="(d, di) in banners"
                    :key="di"
                    class="banner-dot"
                    :class="{ 'banner-dot--on': di === bannerIndex }"
                    @click.stop="bannerIndex = di"
                  ></view>
                </view>
              </view>
            </view>

            <!-- ===== 4. Hot Products (2-col grid) ===== -->
            <view class="section" v-if="hotLoading && hotItems.length === 0">
              <view class="section-head">
                <text class="section-title">本校热门</text>
              </view>
              <view class="hot-grid">
                <view class="hot-card hot-card--skeleton" v-for="i in 4" :key="'hsk'+i">
                  <view class="hot-img hot-img--sk"></view>
                  <view class="hot-body">
                    <view class="sk-line sk-line--1"></view>
                    <view class="sk-line sk-line--2"></view>
                    <view class="sk-line sk-line--3"></view>
                  </view>
                </view>
              </view>
            </view>

            <view class="section" v-else-if="hotItems.length > 0">
              <view class="section-head">
                <text class="section-title">本校热门</text>
                <text class="section-more" @click.stop="goToHotList">查看更多 ›</text>
              </view>
              <view class="hot-grid">
                <view
                  class="hot-card"
                  v-for="(item, i) in hotItems"
                  :key="item.id"
                  @click="goDetail(item.id)"
                >
                  <view class="hot-img" :style="{ background: productTints[i % productTints.length] }">
                    <image
                      v-if="item.images && item.images.length"
                      :src="item.images[0]"
                      mode="aspectFill"
                      class="hot-img-src"
                    />
                    <view v-else class="hot-img-placeholder">
                      <text class="hot-img-emoji">{{ itemEmoji(item.id) }}</text>
                      <text class="hot-img-placeholder-text">{{ itemCategoryLabel(item) }}</text>
                    </view>
                    <text class="hot-status-badge" :class="'hot-status-badge--' + item.status">{{ item.statusText }}</text>
                  </view>
                  <view class="hot-body">
                    <view class="hot-main-row">
                      <text class="hot-name">{{ item.title }}</text>
                      <view class="hot-price-row">
                        <text v-if="priceDisplay(item).prefix" class="hot-price-symbol">{{ priceDisplay(item).prefix }}</text>
                        <text class="hot-price" :class="{ 'hot-price-text': !priceDisplay(item).prefix }">{{ priceDisplay(item).text }}</text>
                      </view>
                    </view>
                    <view class="hot-info-row">
                      <text class="hot-info-chip">{{ itemConditionLabel(item) }}</text>
                      <text class="hot-info-chip">{{ itemCategoryLabel(item) }}</text>
                      <text v-if="isNegotiable(item)" class="hot-info-chip hot-info-chip--blue">可议价</text>
                    </view>
                    <view class="hot-location-row">
                      <text class="hot-location-label">取货地点</text>
                      <text class="hot-location">{{ itemLocationLabel(item) }}</text>
                    </view>
                    <view class="hot-user">
                      <view class="hot-ava">{{ item.userName ? item.userName.slice(0, 1) : '同' }}</view>
                      <text class="hot-uname">{{ item.userName }}</text>
                      <text class="hot-time">{{ fmt(item.ctime) }}</text>
                    </view>
                  </view>
                </view>
              </view>
            </view>

            <!-- Hot empty -->
            <view class="section" v-else>
              <view class="section-head">
                <text class="section-title">本校热门</text>
              </view>
              <view class="hot-empty-card">
                <view class="hot-empty-icon"></view>
                <text class="hot-empty-title">暂无热门商品</text>
                <text class="hot-empty-desc">快来发布校园闲置吧</text>
              </view>
            </view>

            <!-- ===== 5. Campus Zones ===== -->
            <view class="section">
              <view class="section-head">
                <text class="section-title">校园专区</text>
              </view>
              <view class="zone-list">
                <view
                  class="zone-row"
                  v-for="(z, zi) in zones"
                  :key="zi"
                  @click="onZoneClick"
                >
                  <view class="zone-icon-box">
                    <view class="zone-line-icon" :class="'zone-line-icon--' + zi">
                      <view class="zone-line-icon-mark"></view>
                    </view>
                  </view>
                  <view class="zone-text">
                    <text class="zone-title">{{ z.title }}</text>
                    <text class="zone-desc">{{ z.sub }}</text>
                  </view>
                  <text class="zone-arrow">›</text>
                </view>
              </view>
            </view>

            <view class="page-bottom-spacer"></view>
          </scroll-view>
        </view>

        <view
          class="search-transition-mask"
          :class="{ 'search-transition-mask--active': searchTransitioning }"
          :style="searchTransitionStyle"
        >
          <view class="search-transition-top-panel"></view>
          <view class="search-transition-surface" @transitionend="onSearchTransitionEnd"></view>
          <view class="search-transition-bar">
            <image class="search-transition-icon" src="/static/icons/search.svg" mode="aspectFit" />
            <input class="search-transition-input" value="搜索" disabled />
          </view>
        </view>

        <view v-if="hotTransitioning" class="hot-push-transition">
          <view class="hot-push-page"></view>
        </view>

        <market-bottom-bar activeTab="home" />
      </view>
    </view>
  </view>
</template>

<script>
import NavBar from '@/components/nav-bar/nav-bar.vue'
import MarketBottomBar from '@/components/market-bottom-bar/market-bottom-bar.vue'
import { getSecondhandItemList } from '@/api/secondhand'

const EMOJIS = ['📱', '💻', '📷', '🎧', '⌚', '📚', '👟', '🧥', '🪑', '🏠', '🎮', '🎸', '🖥️', '📦']

const ZONES = [
  { emoji: '', title: '学长学姐推荐', sub: '毕业季闲置 · 高年级教材', primary: true },
  { emoji: '', title: '急售专区',     sub: '低价急出 · 限时优惠',     primary: false },
  { emoji: '', title: '免费领取',     sub: '赠品 · 资料 · 二手好物',  primary: false },
]

const BANNERS = [
  { tag: '🔥 本校热门', title: '期末季专场',   subtitle: '教材 · 资料 · 考研真题' },
  { tag: '🆕 新生必备', title: '入学季好物',   subtitle: '宿舍用品 · 日用百货' },
  { tag: '💡 限时特惠', title: '本周精选',     subtitle: '数码设备 · 游戏装备' },
]

const PRODUCT_TINTS = ['#EBEBF0', '#E8EAED', '#EDE8F0', '#EAF0EB']

export default {
  components: { NavBar, MarketBottomBar },
  data() {
    return {
      schoolName: 'XX大学',
      unreadCount: 0,
      items: [],
      zones: ZONES,
      banners: BANNERS,
      bannerIndex: 0,
      productTints: PRODUCT_TINTS,
      bannerTimer: null,
      hotLoading: true,
      searchTransitioning: false,
      searchTransitionNavigating: false,
      searchTransitionRect: {
        left: 0,
        top: 0,
        width: 0,
        height: 0
      },
      hotTransitioning: false,
      hotTransitionTimer: null,
    }
  },
  computed: {
    hotItems() {
      return this.items.slice(0, 4)
    },
    searchTransitionStyle() {
      const rect = this.searchTransitionRect
      if (!rect.width || !rect.height) return {}
      return {
        '--search-transition-start-left': `${rect.left}px`,
        '--search-transition-start-top': `${rect.top}px`,
        '--search-transition-start-width': `${rect.width}px`,
        '--search-transition-start-height': `${rect.height}px`,
        '--search-transition-surface-width': `${rect.surfaceWidth || rect.width}px`,
        '--search-transition-surface-top': `${rect.surfaceTop || rect.top + rect.height}px`,
        '--search-transition-end-x': `${-rect.left}px`,
        '--search-transition-end-y': `${-rect.top}px`
      }
    }
  },
  onLoad() {
    this.loadSchool()
    this.loadItems()
    this.startBannerAuto()
  },
  onShow() {
    this.loadItems()
    this.searchTransitioning = false
    this.searchTransitionNavigating = false
  },
  onUnload() {
    if (this.bannerTimer) {
      clearInterval(this.bannerTimer)
      this.bannerTimer = null
    }
    if (this.hotTransitionTimer) {
      clearTimeout(this.hotTransitionTimer)
      this.hotTransitionTimer = null
    }
  },
  methods: {
    loadSchool() {
      try {
        const info = uni.getStorageSync('userInfo')
        if (info && info.schoolName) {
          this.schoolName = info.schoolName
        }
      } catch (e) {}
    },
    async loadItems() {
      this.hotLoading = true
      try {
        const res = await getSecondhandItemList({ current: 1, size: 20, sort: 'hot' })
        const records = Array.isArray(res?.data?.records) ? res.data.records : []
        this.items = records.map(r => {
          const seller = r.seller || {}
          const categoryId = r.categoryId ?? r.categoryLevel2Id ?? r.categoryLevel1Id ?? 'other'
          const categoryName = r.categoryName || r.categoryLevel2Name || r.categoryLevel1Name || ''
          const location = r.location || r.tradeLocationText || ''
          return {
            id: r.id,
            title: r.title,
            description: r.description || '',
            price: r.price,
            originalPrice: r.originalPrice || r.original_price || null,
            condition: r.condition || r.itemCondition || null,
            conditionText: r.conditionText || r.conditionName || '',
            images: Array.isArray(r.images) ? r.images : [],
            categoryId,
            categoryName,
            categoryLevel1Id: r.categoryLevel1Id || r.categoryParentId || r.categoryId || '',
            categoryLevel1Name: r.categoryLevel1Name || r.categoryParentName || r.categoryName || '',
            categoryLevel2Id: r.categoryLevel2Id || r.categoryId || '',
            categoryLevel2Name: r.categoryLevel2Name || r.categoryName || '',
            location,
            tradeLocation: r.tradeLocation || r.trade_location || location,
            campusId: r.campusId || '',
            campusName: r.campusName || '',
            schoolId: r.schoolId || seller.schoolId || '',
            schoolName: r.schoolName || seller.schoolName || '',
            dormitoryArea: r.dormitoryArea || '',
            allowBargain: Boolean(r.allowBargain ?? r.allow_bargain ?? false),
            deliveryMethod: r.deliveryMethod || r.delivery_method || 'pickup',
            isFree: Boolean(r.isFree ?? Number(r.price) === 0),
            status: r.status === 2 ? 'online' : r.status === 5 ? 'reserved' : r.status === 4 ? 'offline' : 'sold',
            statusText: r.statusText || (r.status === 2 ? '在售' : r.status === 5 ? '交易中' : r.status === 3 ? '已售出' : '已下架'),
            urgency: r.urgency || 'normal',
            viewCount: Number(r.viewCount || r.view_count || 0),
            favoriteCount: Number(r.favoriteCount || r.favorite_count || 0),
            distanceText: r.distanceText || '',
            distanceValue: r.distanceValue || null,
            pickupPoint: r.pickupPoint || r.pickup_point || '',
            inquiryCount: Number(r.inquiryCount || r.inquiry_count || 0),
            heatScore: Number(r.heatScore || r.heat_score || 0),
            attributes: r.attributes || {},
            userName: seller.username || '同学',
            ctime: (r.createTime || '').replace('T', ' '),
          }
        })
      } catch (e) {
        console.error('loadItems', e)
      } finally {
        this.hotLoading = false
      }
    },
    itemEmoji(id) {
      return EMOJIS[(id || 0) % EMOJIS.length]
    },
    itemCategoryLabel(item) {
      return item.categoryName || item.categoryLevel2Name || item.categoryLevel1Name || '闲置'
    },
    itemConditionLabel(item) {
      if (item.conditionText) return item.conditionText
      const condition = String(item.condition || '')
      if (condition === 'new') return '全新'
      if (condition === 'like-new' || condition === 'like_new') return '九成新'
      const text = `${item.title || ''} ${item.description || ''}`
      if (text.includes('全新') || text.includes('未拆')) return '全新'
      if (text.includes('九成新') || text.includes('很少用')) return '九成新'
      return '正常使用'
    },
    itemLocationLabel(item) {
      const parts = [item.campusName, item.tradeLocation, item.pickupPoint || item.location]
        .filter(Boolean)
      return parts.length ? parts.join(' · ') : '校内自提'
    },
    isNegotiable(item) {
      return Boolean(item.allowBargain || String(item.description || '').includes('价格可议'))
    },
    priceDisplay(item) {
      const desc = String(item.description || '')
      if (desc.includes('免费赠送')) return { prefix: '', text: '免费' }
      if (desc.includes('价格面议')) return { prefix: '', text: '面议' }
      const price = Number(item.price)
      if (!price) return { prefix: '', text: '免费' }
      return { prefix: '¥', text: String(item.price) }
    },
    fmt(ts) {
      if (!ts) return ''
      const time = typeof ts === 'string' ? new Date(ts.replace(/-/g, '/')).getTime() : ts
      const diff = Date.now() - time
      if (diff < 60000) return '刚刚'
      if (diff < 3600000) return Math.floor(diff / 60000) + '分钟前'
      if (diff < 86400000) return Math.floor(diff / 3600000) + '小时前'
      const d = new Date(time)
      return `${d.getMonth() + 1}/${d.getDate()}`
    },
    startBannerAuto() {
      if (this.bannerTimer) clearInterval(this.bannerTimer)
      this.bannerTimer = setInterval(() => {
        this.bannerIndex = (this.bannerIndex + 1) % this.banners.length
      }, 4000)
    },
    onSelectSchool() {
      uni.showToast({ title: '学校选择功能开发中', icon: 'none' })
    },
    onZoneClick() {
      uni.showToast({ title: '校园专区即将开放', icon: 'none' })
    },
    goToSearch() {
      if (this.searchTransitioning) return
      uni.createSelectorQuery()
        .in(this)
        .select('.search-pill')
        .boundingClientRect()
        .select('.search-block--sticky')
        .boundingClientRect()
        .exec((res) => {
          const rect = res && res[0]
          const headerRect = res && res[1]
          if (!rect) {
            this.searchTransitioning = true
            return
          }
          const startRect = {
            left: rect.left || 0,
            top: rect.top || 0,
            width: rect.width || 0,
            height: rect.height || 0,
            surfaceWidth: rect.width || 0,
            surfaceTop: headerRect && headerRect.bottom ? headerRect.bottom : (rect.top || 0) + (rect.height || 0)
          }
          try {
            const sys = uni.getSystemInfoSync()
            const windowWidth = sys.windowWidth || 0
            const minSurfaceWidth = windowWidth ? Math.ceil((windowWidth + 2) / 1.22) : 0
            startRect.surfaceWidth = Math.max(startRect.width, minSurfaceWidth)
          } catch (e) {}
          this.searchTransitionRect = startRect
          console.log('[HOME SEARCH START RECT]', startRect)
          this.$nextTick(() => {
            this.searchTransitioning = true
            this.$nextTick(() => {
              uni.createSelectorQuery()
                .in(this)
                .select('.search-transition-bar')
                .boundingClientRect()
                .exec((barRes) => {
                  const bar = barRes && barRes[0]
                  if (!bar) return
                  const transitionRect = {
                    left: bar.left || 0,
                    top: bar.top || 0,
                    width: bar.width || 0,
                    height: bar.height || 0
                  }
                  console.log('[HOME SEARCH TRANSITION RECT]', {
                    searchPill: startRect,
                    transitionBar: transitionRect,
                    diff: {
                      top: transitionRect.top - startRect.top,
                      left: transitionRect.left - startRect.left,
                      width: transitionRect.width - startRect.width,
                      height: transitionRect.height - startRect.height
                    },
                    surfaceWidth: startRect.surfaceWidth
                  })
                })
            })
          })
        })
    },
    onSearchTransitionEnd() {
      if (!this.searchTransitioning || this.searchTransitionNavigating) return
      this.searchTransitionNavigating = true
      uni.navigateTo({
        url: '/subpackage_lostfound/marketSearch/marketSearch',
        animationType: 'none',
        animationDuration: 0,
        complete: () => {
          this.searchTransitioning = false
          this.searchTransitionNavigating = false
        }
      })
    },
    goToHotList() {
      if (this.hotTransitioning) return
      this.hotTransitioning = true
      this.hotTransitionTimer = setTimeout(() => {
        this.hotTransitionTimer = null
        uni.navigateTo({
          url: '/subpackage_lostfound/marketHotList/marketHotList',
          animationType: 'none',
          animationDuration: 0,
          complete: () => {
            this.hotTransitioning = false
          }
        })
      }, 300)
    },
    goToMessages() {
      uni.showToast({ title: '消息功能待后续迁移', icon: 'none' })
    },
    goToProfile() {
      uni.showToast({ title: '我的页面待后续迁移', icon: 'none' })
    },
    goToHome() {},
    onBackToApp() {
      uni.reLaunch({ url: '/pages/index/index' })
    },
    goDetail(id) {
      uni.navigateTo({ url: `/subpackage_lostfound/lostfoundDetail/lostfoundDetail?id=${id}` })
    },
    onScan() {
      uni.scanCode({
        success: (res) => {
          const qr = res.result
          if (qr) {
            uni.navigateTo({ url: `/subpackage_lostfound/lostfoundDetail/lostfoundDetail?id=${qr}` })
          } else {
            uni.showToast({ title: '未识别到二维码', icon: 'none' })
          }
        },
        fail: () => {
          uni.showToast({ title: '请在小程序或 App 中使用扫码', icon: 'none' })
        }
      })
    },
  }
}
</script>

<style scoped>
/* ===== Page ===== */
.page-root {
  width: 100%;
  min-height: 100vh;
  background: #F5F5F5;
  padding-bottom: 130rpx;
}

.page-content {
  /* 入场动画已移除 */
}

.screen { width: 100%; }
.container { width: 100%; }

.page-body {
  height: calc(100vh - 88rpx);
}

/* ===== Section shared ===== */
.section {
  padding: 0 28rpx;
  margin-bottom: 36rpx;
}

.section-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  margin-bottom: 20rpx;
}

.section-title {
  font-size: 34rpx;
  font-weight: 700;
  color: #111111;
  line-height: 1.3;
  letter-spacing: -0.5rpx;
}

.section-more {
  font-size: 24rpx;
  color: #888888;
}

/* ===== 1. Header ===== */
.header-right {
  display: flex;
  align-items: center;
  gap: 4rpx;
}

.market-back-button {
  width: 64rpx;
  height: 64rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #1D1D1F;
  font-size: 48rpx;
  font-weight: 500;
  line-height: 1;
}

.header-loc-icon {
  width: 28rpx;
  height: 28rpx;
  flex-shrink: 0;
}

.header-school-name {
  font-size: 32rpx;
  font-weight: 700;
  color: #111111;
  max-width: 160rpx;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  letter-spacing: -0.5rpx;
}

.header-chevron {
  font-size: 20rpx;
  color: #888888;
}

/* ===== 2. Search ===== */
.search-block {
  padding: 12rpx 28rpx 20rpx;
  display: flex;
  align-items: center;
  gap: 16rpx;
}

.search-block--sticky {
  position: sticky;
  top: 0;
  z-index: 10;
  background: #FFFFFF;
}

.search-pill {
  flex: 1;
  display: flex;
  align-items: center;
  height: 76rpx;
  padding: 0 28rpx;
  background: #F5F5F5;
  border-radius: 38rpx;
  gap: 12rpx;
}

.search-pill-icon {
  width: 36rpx;
  height: 36rpx;
  flex-shrink: 0;
  opacity: 0.7;
}

.search-pill-input {
  flex: 1;
  min-width: 0;
  height: 76rpx;
  font-size: 26rpx;
  line-height: 76rpx;
  color: #888888;
  font-weight: 500;
  padding: 0;
  margin: 0;
  border: none;
  box-sizing: border-box;
  background: transparent;
  opacity: 1;
  -webkit-text-fill-color: #888888;
  pointer-events: none;
}

.search-scan-btn {
  width: 76rpx;
  height: 76rpx;
  border-radius: 50%;
  background: #F5F5F5;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.search-scan-icon {
  width: 36rpx;
  height: 36rpx;
}

.search-transition-mask {
  position: fixed;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  z-index: 9999;
  pointer-events: none;
  overflow: hidden;
}

.search-transition-top-panel {
  position: fixed;
  top: 0;
  right: 0;
  left: 0;
  height: var(--search-transition-surface-top, calc(var(--status-bar-height) + 196rpx));
  background: #FFFFFF;
  opacity: 0;
  z-index: 2;
  transition: opacity 260ms ease-out;
  will-change: opacity;
}

.search-transition-surface {
  position: fixed;
  left: 0;
  right: 0;
  top: var(--search-transition-surface-top, calc(var(--status-bar-height) + 196rpx));
  bottom: 0;
  width: 100%;
  height: auto;
  border-radius: 0;
  background: #F7F7F9;
  overflow: hidden;
  box-shadow: none;
  transform-origin: left top;
  opacity: 0;
  transform: translate3d(0, 0, 0) scale3d(1, 0.01, 1);
  transition: transform 320ms ease-out, opacity 320ms ease-out;
  will-change: transform, opacity;
}

.search-transition-bar {
  position: fixed;
  left: var(--search-transition-start-left, 28rpx);
  top: var(--search-transition-start-top, calc(var(--status-bar-height) + 104rpx));
  width: var(--search-transition-start-width, calc(100vw - 132rpx));
  height: var(--search-transition-start-height, 76rpx);
  display: flex;
  align-items: center;
  gap: 12rpx;
  padding: 0 28rpx;
  border-radius: 38rpx;
  background: #F5F5F5;
  border: 1rpx solid #EEEEEE;
  box-sizing: border-box;
  z-index: 3;
  opacity: 0;
  transition: opacity 160ms ease-out;
}

.search-transition-icon {
  width: 36rpx;
  height: 36rpx;
  opacity: 0.7;
}

.search-transition-input {
  flex: 1;
  min-width: 0;
  height: 76rpx;
  font-size: 26rpx;
  line-height: 76rpx;
  color: #888888;
  font-weight: 500;
  padding: 0;
  margin: 0;
  border: none;
  box-sizing: border-box;
  background: transparent;
  opacity: 1;
  -webkit-text-fill-color: #888888;
  pointer-events: none;
}

.search-transition-mask--active .search-transition-surface {
  opacity: 1;
  transform: translate3d(0, 0, 0) scale3d(1, 1, 1);
}

.search-transition-mask--active .search-transition-top-panel {
  opacity: 1;
}

.search-transition-mask--active .search-transition-bar {
  opacity: 1;
}

.hot-push-transition {
  position: fixed;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  z-index: 230;
  pointer-events: none;
  overflow: hidden;
  background: transparent;
}

.hot-push-page {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  min-height: 100vh;
  background: #FFFFFF;
  transform: translate3d(100%, 0, 0);
  will-change: transform;
  animation: hotPushPageIn 300ms ease-out forwards;
}

.hot-push-nav {
  height: calc(var(--status-bar-height) + 88rpx);
  padding-top: var(--status-bar-height);
  background: #FFFFFF;
  border-bottom: 1rpx solid #EEEEEE;
  display: flex;
  align-items: center;
  justify-content: center;
  box-sizing: border-box;
}

.hot-push-title {
  font-size: 32rpx;
  font-weight: 750;
  color: #1D1D1F;
}

.hot-push-toolbar {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 18rpx 28rpx 20rpx;
  background: #FFFFFF;
  border-bottom: 1rpx solid #EEEEEE;
}

.hot-push-search {
  flex: 1;
  height: 76rpx;
  border-radius: 38rpx;
  background: #F7F7F9;
  border: 1rpx solid #EEEEEE;
}

.hot-push-filter {
  width: 76rpx;
  height: 76rpx;
  border-radius: 50%;
  background: #F7F7F9;
  border: 1rpx solid #EEEEEE;
}

.hot-push-list-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 28rpx 28rpx 18rpx;
}

.hot-push-line {
  height: 28rpx;
  border-radius: 14rpx;
  background: #E8EAED;
}

.hot-push-line--title {
  width: 148rpx;
}

.hot-push-line--count {
  width: 62rpx;
}

.hot-push-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 18rpx;
  padding: 4rpx 24rpx 0;
}

.hot-push-card {
  height: 420rpx;
  border-radius: 22rpx;
  background: #FFFFFF;
  border: 1rpx solid #EEEEEE;
  box-shadow: 0 6rpx 18rpx rgba(92, 122, 153, 0.05);
}

@keyframes hotPushPageIn {
  0% {
    transform: translate3d(100%, 0, 0);
  }
  100% {
    transform: translate3d(0, 0, 0);
  }
}

/* ===== Scroll top spacer (keeps content below fixed search) ===== */
.scroll-top-spacer {
  height: 104rpx;
  flex-shrink: 0;
}

/* ===== 3. Banner ===== */
.banner-block {
  padding: 16rpx 28rpx 0;
  margin-bottom: 36rpx;
}

.banner-card {
  position: relative;
  height: 288rpx;
  border-radius: 24rpx;
  background: linear-gradient(135deg, #1C1C1E 0%, #38383A 100%);
  overflow: hidden;
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
  padding: 36rpx;
}

.banner-deco {
  position: absolute;
  border-radius: 50%;
  background: rgba(255, 255, 255, 0.04);
  pointer-events: none;
}

.banner-deco--1 {
  top: -48rpx;
  right: -48rpx;
  width: 240rpx;
  height: 240rpx;
}

.banner-deco--2 {
  top: 32rpx;
  right: 80rpx;
  width: 160rpx;
  height: 160rpx;
}

.banner-deco--3 {
  bottom: -24rpx;
  right: 36rpx;
  width: 100rpx;
  height: 100rpx;
  background: rgba(111, 152, 208, 0.18);
}

.banner-content {
  position: relative;
  z-index: 1;
}

.banner-tag {
  display: inline-flex;
  align-items: center;
  background: #6F98D0;
  border-radius: 20rpx;
  padding: 6rpx 18rpx;
  font-size: 20rpx;
  font-weight: 700;
  color: #fff;
  letter-spacing: 0.5rpx;
  margin-bottom: 16rpx;
}

.banner-title {
  display: block;
  font-size: 40rpx;
  font-weight: 800;
  color: #fff;
  letter-spacing: -1rpx;
  line-height: 1.2;
}

.banner-sub {
  display: block;
  font-size: 24rpx;
  color: rgba(255, 255, 255, 0.5);
  margin-top: 8rpx;
}

.banner-dots {
  position: absolute;
  bottom: 20rpx;
  right: 28rpx;
  display: flex;
  gap: 8rpx;
  align-items: center;
  z-index: 2;
}

.banner-dot {
  width: 10rpx;
  height: 10rpx;
  border-radius: 5rpx;
  background: rgba(255, 255, 255, 0.35);
  transition: all 0.25s ease;
}

.banner-dot--on {
  width: 32rpx;
  background: #fff;
}

/* ===== 4. Hot Products Grid ===== */
.hot-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 18rpx;
}

.hot-card {
  background: #fff;
  border-radius: 22rpx;
  overflow: hidden;
  border: 1rpx solid #EEEEEE;
  box-shadow: 0 6rpx 18rpx rgba(92, 122, 153, 0.05);
}

.hot-img {
  position: relative;
  width: 100%;
  aspect-ratio: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  overflow: hidden;
  border-radius: 18rpx;
  background: #F1F3F5;
}

.hot-img-src {
  width: 100%;
  height: 100%;
  border-radius: 18rpx;
}

.hot-img-placeholder {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 14rpx;
  color: #8E8E93;
}

.hot-img-emoji {
  font-size: 58rpx;
  line-height: 1;
}

.hot-img-placeholder-text {
  font-size: 22rpx;
  font-weight: 700;
}

.hot-status-badge {
  position: absolute;
  top: 12rpx;
  left: 12rpx;
  padding: 6rpx 12rpx;
  border-radius: 999rpx;
  background: rgba(29, 29, 31, 0.72);
  color: #FFFFFF;
  font-size: 19rpx;
  font-weight: 800;
  line-height: 1;
}

.hot-status-badge--reserved {
  background: rgba(92, 122, 153, 0.88);
}

.hot-status-badge--sold,
.hot-status-badge--offline {
  background: rgba(142, 142, 147, 0.88);
}

.hot-body {
  padding: 18rpx 18rpx 20rpx;
}

.hot-main-row {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
  margin-bottom: 12rpx;
}

.hot-name {
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
  font-size: 27rpx;
  font-weight: 700;
  color: #1D1D1F;
  line-height: 1.35;
  min-height: 0;
}

.hot-price-row {
  display: flex;
  align-items: baseline;
  gap: 2rpx;
}

.hot-price-symbol {
  font-size: 21rpx;
  font-weight: 800;
  color: #1D1D1F;
  line-height: 1;
}

.hot-price {
  font-size: 33rpx;
  font-weight: 850;
  color: #1D1D1F;
  line-height: 1;
}

.hot-price-text {
  font-size: 30rpx;
  color: #4A6278;
}

.hot-info-row {
  display: flex;
  flex-wrap: wrap;
  gap: 8rpx;
  margin-bottom: 14rpx;
}

.hot-info-chip {
  max-width: 128rpx;
  padding: 5rpx 10rpx;
  border-radius: 10rpx;
  background: #F5F6F8;
  color: #6B6F76;
  font-size: 18rpx;
  font-weight: 700;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.hot-info-chip--blue {
  background: rgba(92, 122, 153, 0.08);
  color: #4A6278;
}

.hot-location-row {
  display: flex;
  flex-direction: column;
  gap: 4rpx;
  margin-bottom: 14rpx;
}

.hot-location-label {
  font-size: 19rpx;
  color: #A2A8AF;
  font-weight: 600;
}

.hot-location {
  font-size: 22rpx;
  color: #5C5C60;
  font-weight: 500;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.hot-user {
  display: flex;
  align-items: center;
  gap: 8rpx;
  padding-top: 14rpx;
  border-top: 1rpx solid #F0F0F0;
}

.hot-ava {
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
  flex-shrink: 0;
}

.hot-uname {
  font-size: 21rpx;
  color: #666A70;
  flex: 1;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.hot-time {
  font-size: 19rpx;
  color: #A2A8AF;
  flex-shrink: 0;
}

/* Hot skeleton */
.hot-img--sk {
  background: linear-gradient(90deg, #EEEEEE 25%, #F5F5F5 50%, #EEEEEE 75%);
  background-size: 200% 100%;
  animation: shimmer 1.5s infinite;
}

.sk-line {
  border-radius: 6rpx;
  background: linear-gradient(90deg, #EEEEEE 25%, #F5F5F5 50%, #EEEEEE 75%);
  background-size: 200% 100%;
  animation: shimmer 1.5s infinite;
  margin-bottom: 12rpx;
}

.sk-line--1 { height: 28rpx; width: 85%; }
.sk-line--2 { height: 24rpx; width: 45%; }
.sk-line--3 { height: 20rpx; width: 60%; }

@keyframes shimmer {
  0%   { background-position: 200% 0; }
  100% { background-position: -200% 0; }
}

.hot-empty-card {
  width: 100%;
  box-sizing: border-box;
  min-height: 260rpx;
  padding: 44rpx 28rpx;
  border-radius: 22rpx;
  border: 1rpx solid #EEEEEE;
  background: #FFFFFF;
  box-shadow: 0 6rpx 18rpx rgba(92, 122, 153, 0.05);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  text-align: center;
}

.hot-empty-icon {
  position: relative;
  width: 82rpx;
  height: 62rpx;
  margin-bottom: 22rpx;
  border: 3rpx solid #8E8E93;
  border-top: 0;
  border-radius: 8rpx 8rpx 12rpx 12rpx;
  background: transparent;
  box-sizing: border-box;
}

.hot-empty-icon::before {
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

.hot-empty-icon::after {
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

.hot-empty-title {
  font-size: 28rpx;
  font-weight: 750;
  color: #1D1D1F;
  line-height: 1.35;
}

.hot-empty-desc {
  margin-top: 8rpx;
  font-size: 23rpx;
  font-weight: 500;
  color: #8E8E93;
  line-height: 1.4;
}

/* ===== 5. Campus Zones ===== */
.zone-list {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
}

.zone-row {
  display: flex;
  align-items: center;
  gap: 18rpx;
  background: #fff;
  border-radius: 16rpx;
  padding: 22rpx 24rpx;
  border: 1rpx solid #EEEEEE;
  box-shadow: 0 4rpx 14rpx rgba(92, 122, 153, 0.04);
}

.zone-icon-box {
  width: 58rpx;
  height: 58rpx;
  border-radius: 16rpx;
  background: #F7F7F9;
  border: 1rpx solid #EEEEEE;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.zone-line-icon {
  width: 32rpx;
  height: 32rpx;
  position: relative;
  box-sizing: border-box;
  color: #4A4A4A;
}

.zone-line-icon::before,
.zone-line-icon::after,
.zone-line-icon-mark,
.zone-line-icon-mark::before,
.zone-line-icon-mark::after {
  content: '';
  position: absolute;
  box-sizing: border-box;
  border-color: currentColor;
}

.zone-line-icon--0::before {
  left: 4rpx;
  top: 10rpx;
  width: 24rpx;
  height: 12rpx;
  border: 3rpx solid currentColor;
  border-bottom: none;
  transform: skewX(-18deg);
}

.zone-line-icon--0::after {
  left: 14rpx;
  top: 21rpx;
  width: 4rpx;
  height: 8rpx;
  border-left: 3rpx solid currentColor;
}

.zone-line-icon--0 .zone-line-icon-mark {
  left: 2rpx;
  top: 9rpx;
  width: 28rpx;
  border-top: 3rpx solid currentColor;
  transform: rotate(18deg);
}

.zone-line-icon--1::before {
  left: 5rpx;
  top: 7rpx;
  width: 22rpx;
  height: 18rpx;
  border: 3rpx solid currentColor;
  border-radius: 7rpx;
  transform: rotate(-12deg);
}

.zone-line-icon--1::after {
  right: 8rpx;
  top: 12rpx;
  width: 5rpx;
  height: 5rpx;
  border: 3rpx solid currentColor;
  border-radius: 50%;
}

.zone-line-icon--1 .zone-line-icon-mark {
  left: 11rpx;
  top: 15rpx;
  width: 10rpx;
  border-top: 3rpx solid currentColor;
  transform: rotate(-12deg);
}

.zone-line-icon--2::before {
  left: 5rpx;
  top: 12rpx;
  width: 22rpx;
  height: 15rpx;
  border: 3rpx solid currentColor;
  border-radius: 5rpx;
}

.zone-line-icon--2::after {
  left: 3rpx;
  top: 8rpx;
  width: 26rpx;
  height: 7rpx;
  border: 3rpx solid currentColor;
  border-radius: 5rpx;
  background: #F7F7F9;
}

.zone-line-icon--2 .zone-line-icon-mark {
  left: 15rpx;
  top: 8rpx;
  height: 19rpx;
  border-left: 3rpx solid currentColor;
}

.zone-text {
  flex: 1;
  min-width: 0;
}

.zone-title {
  display: block;
  font-size: 27rpx;
  font-weight: 750;
  color: #1D1D1F;
  line-height: 1.3;
}

.zone-desc {
  display: block;
  font-size: 23rpx;
  color: #8E8E93;
  margin-top: 6rpx;
  line-height: 1.35;
}

.zone-arrow {
  font-size: 32rpx;
  color: #C7C7CC;
  line-height: 1;
  flex-shrink: 0;
}

.page-bottom-spacer { height: 48rpx; }
</style>
