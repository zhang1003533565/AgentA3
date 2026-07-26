<template>
  <view class="page-root" :class="{ 'page-root--content-visible': contentVisible }">
    <common-page-header title="本校热门" :fixed="true" :placeholder="true" :showBack="true" />

    <view class="hot-list-content" :class="{ 'hot-list-content--visible': contentVisible }">
      <view class="toolbar" :class="{ 'toolbar--transitioning': searchTransitioning }">
        <market-search-entry
          class="hot-list-search"
          source="hotlist"
          target-url="/subpackage_lostfound/marketSearch/marketSearch?placeholder=%E6%90%9C%E7%B4%A2%E6%9C%AC%E6%A0%A1%E5%95%86%E5%93%81"
          bar-motion="rise"
          @transition-change="searchTransitioning = $event"
          @overlay-change="handleSearchOverlayChange"
        />
        <view class="filter-btn" :class="{ on: hasActiveFilter }" @click="openFilter">
          <image class="filter-icon" src="/static/icons/mage-filter-fill.svg" mode="aspectFit" />
          <view v-if="hasActiveFilter" class="filter-dot"></view>
        </view>
      </view>

      <scroll-view scroll-y class="page-body" :show-scrollbar="false">
        <view class="list-head">
          <text class="list-title">热门商品</text>
          <text class="list-count">{{ filteredItems.length }} 件</text>
        </view>
        <market-product-grid
          :items="filteredItems"
          :loading="loading && items.length === 0"
          empty-title="暂无热门商品"
          empty-sub="快来发布校园闲置吧"
          @item-click="goDetail"
        />
      </scroll-view>
    </view>

    <view v-if="filterVisible" class="filter-overlay" @click="closeFilter">
      <view class="filter-panel" @click.stop>
        <view class="filter-handle"></view>
        <view class="filter-header">
          <text class="filter-title">筛选</text>
          <text class="filter-close" @click="closeFilter">×</text>
        </view>

        <scroll-view scroll-y class="filter-body" :show-scrollbar="false">
          <view class="filter-group">
            <view class="filter-group-title">价格区间</view>
            <view class="filter-options">
              <view
                v-for="o in priceOptions"
                :key="o.value"
                class="filter-opt"
                :class="{ on: filterForm.priceRange === o.value }"
                @click="filterForm.priceRange = o.value"
              >{{ o.label }}</view>
            </view>
          </view>

          <view class="filter-group">
            <view class="filter-group-title">发布时间</view>
            <view class="filter-options">
              <view
                v-for="o in timeOptions"
                :key="o.value"
                class="filter-opt"
                :class="{ on: filterForm.publishTime === o.value }"
                @click="filterForm.publishTime = o.value"
              >{{ o.label }}</view>
            </view>
          </view>

          <view class="filter-group">
            <view class="filter-group-title">商品状态</view>
            <view class="filter-options">
              <view
                v-for="o in conditionOptions"
                :key="o.value"
                class="filter-opt"
                :class="{ on: filterForm.condition === o.value }"
                @click="filterForm.condition = o.value"
              >{{ o.label }}</view>
            </view>
          </view>

          <view class="filter-group">
            <view class="filter-group-title">交易位置</view>
            <view class="filter-options">
              <view
                v-for="o in locationOptions"
                :key="o.value"
                class="filter-opt"
                :class="{ on: filterForm.location === o.value }"
                @click="filterForm.location = o.value"
              >{{ o.label }}</view>
            </view>
          </view>
        </scroll-view>

        <view class="filter-footer">
          <view class="filter-footer-btn reset" @click="resetFilter">重置</view>
          <view class="filter-footer-btn confirm" @click="confirmFilter">确认筛选</view>
        </view>
      </view>
    </view>

    <view
      v-if="searchOverlayVisible"
      class="market-search-root-overlay"
      :class="{ 'market-search-root-overlay--active': searchOverlayActive }"
    >
      <view
        class="market-search-root-surface"
        :style="searchOverlaySurfaceStyle"
      ></view>
      <view
        class="market-search-root-bar"
        :style="searchOverlayBarStyle"
      >
        <image class="market-search-root-icon" src="/static/icons/search.svg" mode="aspectFit" />
        <input class="market-search-root-text" value="搜索" disabled />
      </view>
    </view>
  </view>
</template>

<script>
import CommonPageHeader from '@/components/common-page-header/common-page-header.vue'
import MarketProductGrid from '@/components/market-product-grid/market-product-grid.vue'
import MarketSearchEntry from '@/components/market-search-entry/market-search-entry.vue'
import { getSecondhandItemList } from '@/api/secondhand'
import { createDefaultMarketFilter, filterMarketItems } from '@/subpackage_lostfound/utils/marketFilter.js'
import { normalizeSecondhandItem } from '@/subpackage_lostfound/utils/secondhandItem.js'

const PRICE_OPTIONS = [
  { value: 'all', label: '不限' },
  { value: '0-50', label: '0-50元' },
  { value: '50-200', label: '50-200元' },
  { value: '200+', label: '200元以上' }
]

const TIME_OPTIONS = [
  { value: 'all', label: '不限' },
  { value: 'today', label: '今天' },
  { value: '3days', label: '最近三天' },
  { value: 'week', label: '最近一周' }
]

const CONDITION_OPTIONS = [
  { value: 'all', label: '不限' },
  { value: 'new', label: '全新' },
  { value: 'like-new', label: '九成新' },
  { value: 'used', label: '二手' }
]

const LOCATION_OPTIONS = [
  { value: 'all', label: '不限' },
  { value: 'campus', label: '校内' },
  { value: 'dorm', label: '宿舍区' },
  { value: 'nearby', label: '附近' }
]

function defaultFilter() {
  return {
    priceRange: 'all',
    publishTime: 'all',
    condition: 'all',
    location: 'all'
  }
}

export default {
  components: {
    CommonPageHeader,
    MarketProductGrid,
    MarketSearchEntry
  },
  data() {
    return {
      loading: false,
      items: [],
      contentVisible: false,
      contentRevealTimer: null,
      searchTransitioning: false,
      searchOverlayVisible: false,
      searchOverlayActive: false,
      searchOverlayRect: {
        left: 0,
        top: 0,
        width: 0,
        height: 0
      },
      searchOverlayWindow: {
        width: 375,
        height: 667
      },
      searchOverlayBarMotion: 'expand',
      searchOverlayBarTargetTop: 0,
      filterVisible: false,
      filterForm: defaultFilter(),
      activeFilterForm: defaultFilter(),
      priceOptions: PRICE_OPTIONS,
      timeOptions: TIME_OPTIONS,
      conditionOptions: CONDITION_OPTIONS,
      locationOptions: LOCATION_OPTIONS
    }
  },
  computed: {
    filteredItems() {
      return filterMarketItems(this.items, createDefaultMarketFilter({
        priceRange: this.activeFilterForm.priceRange,
        publishTime: this.activeFilterForm.publishTime,
        condition: this.activeFilterForm.condition,
        tradeLocation: this.activeFilterForm.location,
        sortBy: 'hot'
      }))
    },
    hasActiveFilter() {
      const f = this.activeFilterForm
      return f.priceRange !== 'all' || f.publishTime !== 'all' || f.condition !== 'all' || f.location !== 'all'
    },
    searchOverlaySurfaceStyle() {
      const rect = this.searchOverlayRect
      const width = rect.width || 1
      const height = rect.height || 1
      const scaleX = Math.max((this.searchOverlayWindow.width + 24) / width, 1)
      const scaleY = Math.max((this.searchOverlayWindow.height + 24) / height, 1)
      return {
        left: `${rect.left}px`,
        top: `${rect.top}px`,
        width: `${width}px`,
        height: `${height}px`,
        transform: this.searchOverlayActive
          ? `translate3d(${-rect.left}px, ${-rect.top}px, 0) scale3d(${scaleX}, ${scaleY}, 1)`
          : 'translate3d(0, 0, 0) scale3d(1, 1, 1)'
      }
    },
    searchOverlayBarStyle() {
      const rect = this.searchOverlayRect
      const width = rect.width || 1
      const height = rect.height || 1
      const transform = this.searchOverlayBarMotion === 'rise'
        ? `translate3d(0, ${this.searchOverlayBarTargetTop - (rect.top || 0)}px, 0)`
        : `translate3d(${-rect.left}px, ${-rect.top}px, 0)`
      return {
        left: `${rect.left}px`,
        top: `${rect.top}px`,
        width: `${width}px`,
        height: `${height}px`,
        transform: this.searchOverlayActive ? transform : 'translate3d(0, 0, 0)'
      }
    }
  },
  onLoad() {
    this.startContentReveal()
    this.loadItems()
  },
  onUnload() {
    if (this.contentRevealTimer) {
      clearTimeout(this.contentRevealTimer)
      this.contentRevealTimer = null
    }
  },
  methods: {
    startContentReveal() {
      this.contentVisible = false
      if (this.contentRevealTimer) {
        clearTimeout(this.contentRevealTimer)
      }
      this.contentRevealTimer = setTimeout(() => {
        this.contentRevealTimer = null
        this.contentVisible = true
      }, 300)
    },
    async loadItems() {
      this.loading = true
      try {
        const res = await getSecondhandItemList({ current: 1, size: 100, sort: 'hot' })
        const records = Array.isArray(res?.data?.records) ? res.data.records : []
        this.items = records.map(normalizeSecondhandItem)
      } catch (error) {
        console.error('加载热门商品失败', error)
        this.items = []
      } finally {
        this.loading = false
      }
    },
    goDetail(id) {
      uni.navigateTo({ url: `/subpackage_lostfound/lostfoundDetail/lostfoundDetail?id=${id}` })
    },
    openFilter() {
      this.filterForm = { ...this.activeFilterForm }
      this.filterVisible = true
    },
    closeFilter() {
      this.filterVisible = false
    },
    resetFilter() {
      this.filterForm = defaultFilter()
    },
    confirmFilter() {
      this.activeFilterForm = { ...this.filterForm }
      this.filterVisible = false
    },
    handleSearchOverlayChange(payload = {}) {
      if (!payload.active) {
        this.searchOverlayActive = false
        this.searchOverlayVisible = false
        return
      }
      this.searchOverlayRect = payload.rect || this.searchOverlayRect
      this.searchOverlayWindow = payload.windowSize || this.searchOverlayWindow
      this.searchOverlayBarMotion = payload.barMotion || 'expand'
      this.searchOverlayBarTargetTop = payload.barTargetTop || 0
      this.searchOverlayVisible = true
      this.searchOverlayActive = false
      this.$nextTick(() => {
        this.searchOverlayActive = true
      })
    }
  }
}
</script>

<style scoped>
.page-root {
  min-height: 100vh;
  background: #FFFFFF;
  transition: background-color 180ms ease-out;
}

.page-root--content-visible {
  background: #F7F7F9;
}

.hot-list-content {
  min-height: 100vh;
  opacity: 0;
  pointer-events: none;
  transition: opacity 180ms ease-out;
}

.hot-list-content--visible {
  opacity: 1;
  pointer-events: auto;
}

.toolbar {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 18rpx 28rpx 20rpx;
  background: #F7F7F9;
  border-bottom: 1rpx solid #EEEEEE;
}

.hot-list-search {
  flex: 1;
  min-width: 0;
}

.toolbar--transitioning .filter-btn {
  opacity: 0;
}

.market-search-root-overlay {
  position: fixed;
  top: 0;
  right: 0;
  bottom: 0;
  left: 0;
  width: 100vw;
  height: 100vh;
  z-index: 99999;
  pointer-events: none;
  overflow: hidden;
}

.market-search-root-surface {
  position: fixed;
  border-radius: 38rpx;
  background: #F7F7F9;
  overflow: hidden;
  box-shadow: 0 16rpx 48rpx rgba(29, 29, 31, 0.10);
  transform-origin: left top;
  opacity: 0;
  transition: transform 320ms ease-out, opacity 320ms ease-out, border-radius 320ms ease-out;
  will-change: transform, opacity;
}

.market-search-root-overlay--active .market-search-root-surface {
  opacity: 1;
  border-radius: 0;
}

.market-search-root-bar {
  position: fixed;
  z-index: 100000;
  height: 76rpx;
  display: flex;
  align-items: center;
  gap: 12rpx;
  padding: 0 28rpx;
  border-radius: 38rpx;
  background: #F5F5F5;
  box-sizing: border-box;
  opacity: 0;
  transform-origin: left top;
  transition: transform 320ms ease-out, opacity 160ms ease-out;
  will-change: transform, opacity;
}

.market-search-root-overlay--active .market-search-root-bar {
  opacity: 1;
}

.market-search-root-icon {
  width: 36rpx;
  height: 36rpx;
  flex-shrink: 0;
  opacity: 0.7;
}

.market-search-root-text {
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

.filter-btn {
  width: 76rpx;
  height: 76rpx;
  border-radius: 50%;
  background: #F7F7F9;
  border: 1rpx solid #EEEEEE;
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  box-sizing: border-box;
}

.filter-btn.on {
  background: rgba(92, 122, 153, 0.08);
  border-color: rgba(92, 122, 153, 0.18);
}

.filter-icon {
  width: 34rpx;
  height: 34rpx;
  opacity: 0.55;
}

.filter-dot {
  position: absolute;
  top: 14rpx;
  right: 14rpx;
  width: 12rpx;
  height: 12rpx;
  border-radius: 50%;
  background: #5C7A99;
  border: 2rpx solid #FFFFFF;
}

.page-body {
  height: calc(100vh - 180rpx);
}

.list-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  padding: 28rpx 28rpx 18rpx;
}

.list-title {
  font-size: 30rpx;
  font-weight: 800;
  color: #1D1D1F;
}

.list-count {
  font-size: 23rpx;
  color: #8E8E93;
}

.filter-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.26);
  z-index: 100;
  display: flex;
  align-items: flex-end;
}

.filter-panel {
  width: 100%;
  max-height: 70vh;
  background: #FFFFFF;
  border-radius: 34rpx 34rpx 0 0;
  display: flex;
  flex-direction: column;
  box-shadow: 0 -18rpx 54rpx rgba(29, 29, 31, 0.12);
}

.filter-handle {
  width: 72rpx;
  height: 8rpx;
  border-radius: 999rpx;
  background: #D7DEE8;
  margin: 18rpx auto 0;
}

.filter-header {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 20rpx 32rpx 16rpx;
  position: relative;
  flex-shrink: 0;
}

.filter-title {
  font-size: 30rpx;
  font-weight: 700;
  color: #111111;
}

.filter-close {
  position: absolute;
  right: 32rpx;
  top: 18rpx;
  font-size: 28rpx;
  color: #8E8E93;
  width: 48rpx;
  height: 48rpx;
  display: flex;
  align-items: center;
  justify-content: center;
}

.filter-body {
  flex: 1;
  max-height: 50vh;
  padding: 0 32rpx 18rpx;
  box-sizing: border-box;
}

.filter-group {
  margin-bottom: 28rpx;
}

.filter-group-title {
  font-size: 24rpx;
  font-weight: 600;
  color: rgba(0, 0, 0, 0.55);
  margin-bottom: 14rpx;
}

.filter-options {
  display: flex;
  flex-wrap: wrap;
  gap: 14rpx;
}

.filter-opt {
  padding: 12rpx 28rpx;
  border-radius: 28rpx;
  background: #FFFFFF;
  border: 1rpx solid #E9EDF2;
  font-size: 24rpx;
  font-weight: 600;
  color: #4A4A4A;
  box-sizing: border-box;
}

.filter-opt.on {
  background: #EAF3FF;
  border-color: #AFC9EA;
  color: #4F7FB8;
}

.filter-footer {
  display: flex;
  gap: 20rpx;
  padding: 20rpx 32rpx calc(20rpx + env(safe-area-inset-bottom));
  border-top: 1rpx solid #E9EDF2;
  flex-shrink: 0;
}

.filter-footer-btn {
  flex: 1;
  height: 80rpx;
  border-radius: 40rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28rpx;
  font-weight: 700;
}

.filter-footer-btn.reset {
  background: #FFFFFF;
  border: 1rpx solid #E9EDF2;
  color: #4A4A4A;
}

.filter-footer-btn.confirm {
  background: #6F98D0;
  color: #FFFFFF;
  box-shadow: 0 8rpx 20rpx rgba(111, 152, 208, 0.24);
}
</style>
