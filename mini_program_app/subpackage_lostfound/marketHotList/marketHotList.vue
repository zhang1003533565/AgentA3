<template>
  <view class="page-root" :class="{ 'page-root--content-visible': contentVisible }">
    <common-page-header title="本校热门" :fixed="true" :placeholder="true" :showBack="true" />

    <view class="hot-list-content" :class="{ 'hot-list-content--visible': contentVisible }">
      <view class="toolbar" :class="{ 'toolbar--transitioning': searchTransitioning }">
        <view class="hot-list-search" @click="goToSearch">
          <view class="market-search-pill">
            <image class="market-search-pill-icon" src="/static/icons/search.svg" mode="aspectFit" />
            <input class="market-search-pill-input" value="搜索本校商品" disabled />
          </view>
        </view>
        <view class="search-filter-btn" :class="{ on: hasActiveFilter }" @click="openFilter">
          <image class="filter-icon" src="/static/icons/mage-filter-fill.svg" mode="aspectFit" />
          <view v-if="hasActiveFilter" class="filter-dot"></view>
        </view>
      </view>

      <scroll-view
        scroll-y
        class="page-body"
        :show-scrollbar="false"
        refresher-enabled
        :refresher-triggered="refreshing"
        refresher-background="#F7F7F9"
        @refresherrefresh="refreshPage"
      >
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

        <view v-if="selectedFilterSummaries.length" class="selected-filter-strip">
          <view
            v-for="item in selectedFilterSummaries"
            :key="item.key"
            class="selected-filter-chip"
          >{{ item.label }}</view>
        </view>

        <scroll-view scroll-y class="filter-body" :show-scrollbar="false">
          <view class="filter-group">
            <view class="filter-group-title">商品分类</view>
            <scroll-view scroll-x class="filter-chip-scroll" :show-scrollbar="false">
              <view class="filter-options filter-options--inline">
                <view
                  v-for="cat in categories"
                  :key="'hot-filter-cat-' + cat.key"
                  class="filter-opt filter-opt--chip"
                  :class="{ on: filterForm.categoryLevel1Id === cat.key }"
                  @click="selectFilterCategoryLevel1(cat.key)"
                >{{ cat.label }}</view>
              </view>
            </scroll-view>
          </view>

          <view v-if="currentFilterCategoryChildren.length" class="filter-group">
            <view class="filter-group-title">细分分类</view>
            <scroll-view scroll-x class="filter-chip-scroll" :show-scrollbar="false">
              <view class="filter-options filter-options--inline">
                <view
                  class="filter-opt filter-opt--chip"
                  :class="{ on: !filterForm.categoryLevel2Id }"
                  @click="filterForm.categoryLevel2Id = ''"
                >全部</view>
                <view
                  v-for="cat in currentFilterCategoryChildren"
                  :key="'hot-filter-sub-cat-' + cat.key"
                  class="filter-opt filter-opt--chip"
                  :class="{ on: filterForm.categoryLevel2Id === cat.key }"
                  @click="filterForm.categoryLevel2Id = cat.key"
                >{{ cat.label }}</view>
              </view>
            </scroll-view>
          </view>

          <view class="filter-group">
            <view class="filter-group-title">价格区间</view>
            <view class="filter-options">
              <view
                v-for="o in priceOptions"
                :key="o.value"
                class="filter-opt"
                :class="{ on: filterForm.priceRange === o.value }"
                @click="selectFilterPrice(o.value)"
              >{{ o.label }}</view>
            </view>
            <view class="filter-price-custom">
              <input
                v-model="filterForm.customPriceMin"
                class="filter-price-input"
                type="number"
                placeholder="￥ 最低价"
                placeholder-class="filter-price-placeholder"
              />
              <text class="filter-price-separator">-</text>
              <input
                v-model="filterForm.customPriceMax"
                class="filter-price-input"
                type="number"
                placeholder="￥ 最高价"
                placeholder-class="filter-price-placeholder"
              />
            </view>
          </view>

          <view class="filter-group">
            <view class="filter-group-title">商品成色</view>
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

          <view class="filter-more">
            <view class="filter-more-head" @click="moreFilterExpanded = !moreFilterExpanded">
              <view>
                <view class="filter-more-title">更多筛选</view>
                <view class="filter-more-sub">{{ moreFilterSummary }}</view>
              </view>
              <view class="filter-more-chevron" :class="{ open: moreFilterExpanded }"></view>
            </view>

            <view v-if="moreFilterExpanded" class="filter-more-body">
              <view class="filter-group filter-group--compact">
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

              <view class="filter-group filter-group--compact">
                <view class="filter-group-title">取货地点</view>
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
            </view>
          </view>
        </scroll-view>

        <view class="filter-footer">
          <view class="filter-btn reset" @click="resetFilter">重置</view>
          <view class="filter-btn confirm" @click="confirmFilter">确认筛选</view>
        </view>
      </view>
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
        <input class="search-transition-input" value="搜索本校商品" disabled />
      </view>
    </view>
  </view>
</template>

<script>
import CommonPageHeader from '@/components/common-page-header/common-page-header.vue'
import MarketProductGrid from '@/components/market-product-grid/market-product-grid.vue'
import { getSecondhandItemList } from '@/api/secondhand'
import { createDefaultMarketFilter, filterMarketItems, buildItemListParams } from '@/subpackage_lostfound/utils/marketFilter.js'
import { createMarketCategoryOptions, getMarketCategoryChildren } from '@/subpackage_lostfound/utils/marketCategories.js'
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
  { value: '1', label: '全新' },
  { value: '2', label: '很新' },
  { value: '3', label: '正常使用' },
  { value: '4', label: '明显使用' },
  { value: '5', label: '配件/零件' }
]

const LOCATION_OPTIONS = [
  { value: 'all', label: '不限' },
  { value: 'campus', label: '校内' },
  { value: 'dorm', label: '宿舍区' },
  { value: 'nearby', label: '附近' }
]

function defaultFilter() {
  return {
    categoryLevel1Id: 'all',
    categoryLevel2Id: '',
    priceRange: 'all',
    customPriceMin: '',
    customPriceMax: '',
    publishTime: 'all',
    condition: 'all',
    location: 'all'
  }
}

export default {
  components: {
    CommonPageHeader,
    MarketProductGrid
  },
  data() {
    return {
      loading: false,
      refreshing: false,
      items: [],
      contentVisible: false,
      contentRevealTimer: null,
      searchTransitioning: false,
      searchTransitionNavigating: false,
      searchTransitionRect: {
        left: 0,
        top: 0,
        width: 0,
        height: 0
      },
      filterVisible: false,
      moreFilterExpanded: false,
      filterForm: defaultFilter(),
      activeFilterForm: defaultFilter(),
      categories: createMarketCategoryOptions(),
      priceOptions: PRICE_OPTIONS,
      timeOptions: TIME_OPTIONS,
      conditionOptions: CONDITION_OPTIONS,
      locationOptions: LOCATION_OPTIONS
    }
  },
  computed: {
    filteredItems() {
      return filterMarketItems(this.items, createDefaultMarketFilter({
        categoryLevel1Id: this.activeFilterForm.categoryLevel1Id,
        categoryLevel2Id: this.activeFilterForm.categoryLevel2Id,
        priceRange: this.activeFilterForm.priceRange,
        publishTime: this.activeFilterForm.publishTime,
        condition: this.activeFilterForm.condition,
        tradeLocation: this.activeFilterForm.location,
        sortBy: 'hot'
      }))
    },
    hasActiveFilter() {
      const f = this.activeFilterForm
      return f.categoryLevel1Id !== 'all' || Boolean(f.categoryLevel2Id) || f.priceRange !== 'all' || f.publishTime !== 'all' || f.condition !== 'all' || f.location !== 'all'
    },
    currentFilterCategoryChildren() {
      return getMarketCategoryChildren(this.categories, this.filterForm.categoryLevel1Id)
    },
    selectedFilterSummaries() {
      const form = this.filterForm || {}
      const items = []
      const optionLabel = (options, value) => {
        const matched = options.find((item) => String(item.value) === String(value))
        return matched ? matched.label : ''
      }

      if (form.categoryLevel1Id && form.categoryLevel1Id !== 'all') {
        const matched = this.categories.find((cat) => String(cat.key) === String(form.categoryLevel1Id))
        if (matched) items.push({ key: 'categoryLevel1Id', label: matched.label })
      }
      if (form.categoryLevel2Id) {
        const matched = getMarketCategoryChildren(this.categories, form.categoryLevel1Id)
          .find((cat) => String(cat.key) === String(form.categoryLevel2Id))
        if (matched) items.push({ key: 'categoryLevel2Id', label: matched.label })
      }
      if (form.priceRange && form.priceRange !== 'all') {
        items.push({ key: 'priceRange', label: optionLabel(this.priceOptions, form.priceRange) || `${form.priceRange}元` })
      }
      if (form.condition && form.condition !== 'all') {
        items.push({ key: 'condition', label: optionLabel(this.conditionOptions, form.condition) })
      }
      if (form.publishTime && form.publishTime !== 'all') {
        items.push({ key: 'publishTime', label: optionLabel(this.timeOptions, form.publishTime) })
      }
      if (form.location && form.location !== 'all') {
        items.push({ key: 'location', label: optionLabel(this.locationOptions, form.location) })
      }

      return items.filter((item) => item.label)
    },
    moreFilterSummary() {
      const form = this.filterForm || {}
      const summary = []
      const optionLabel = (options, value) => {
        const matched = options.find((item) => String(item.value) === String(value))
        return matched ? matched.label : ''
      }

      if (form.publishTime && form.publishTime !== 'all') {
        summary.push(optionLabel(this.timeOptions, form.publishTime))
      }
      if (form.location && form.location !== 'all') {
        summary.push(optionLabel(this.locationOptions, form.location))
      }

      return summary.length ? `已选：${summary.join(' / ')}` : '发布时间 / 取货地点'
    },
    searchTransitionStyle() {
      const rect = this.searchTransitionRect
      if (!rect.width || !rect.height) return {}
      return {
        '--search-transition-start-left': `${rect.left}px`,
        '--search-transition-start-top': `${rect.top}px`,
        '--search-transition-start-width': `${rect.width}px`,
        '--search-transition-start-height': `${rect.height}px`,
        '--search-transition-surface-top': `${rect.surfaceTop || rect.top + rect.height}px`
      }
    }
  },
  onLoad() {
    this.startContentReveal()
    this.loadItems()
  },
  onShow() {
    this.searchTransitioning = false
    this.searchTransitionNavigating = false
  },
  onUnload() {
    if (this.contentRevealTimer) {
      clearTimeout(this.contentRevealTimer)
      this.contentRevealTimer = null
    }
  },
  created() {
    this._loadSeq = 0
  },
  watch: {
    activeFilterForm: {
      deep: true,
      handler() {
        this.loadItems({ clear: true })
      }
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
    async loadItems({ clear = false } = {}) {
      this.loading = true
      if (clear) this.items = []
      const seq = ++this._loadSeq
      try {
        const params = buildItemListParams({
          ...this.activeFilterForm,
          sort: 'hot'
        })
        const res = await getSecondhandItemList(params)
        if (seq !== this._loadSeq) return
        const records = Array.isArray(res?.data?.records) ? res.data.records : []
        this.items = records.map(normalizeSecondhandItem)
      } catch (error) {
        console.error('加载热门商品失败', error)
        if (seq !== this._loadSeq) return
        this.items = []
      } finally {
        if (seq === this._loadSeq) this.loading = false
      }
    },
    async refreshPage() {
      if (this.refreshing) return
      this.refreshing = true
      try {
        await this.loadItems()
        uni.showToast({ title: '已刷新', icon: 'none', duration: 900 })
      } finally {
        this.refreshing = false
      }
    },
    goDetail(id) {
      uni.navigateTo({ url: `/subpackage_lostfound/lostfoundDetail/lostfoundDetail?id=${id}` })
    },
    openFilter() {
      this.filterForm = {
        categoryLevel1Id: this.activeFilterForm.categoryLevel1Id || 'all',
        categoryLevel2Id: this.activeFilterForm.categoryLevel2Id || '',
        priceRange: this.activeFilterForm.priceRange || 'all',
        customPriceMin: '',
        customPriceMax: '',
        publishTime: this.activeFilterForm.publishTime || 'all',
        condition: this.activeFilterForm.condition || 'all',
        location: this.activeFilterForm.location || 'all'
      }
      this.moreFilterExpanded = false
      this.filterVisible = true
    },
    closeFilter() {
      this.filterVisible = false
    },
    resetFilter() {
      this.filterForm = defaultFilter()
      this.moreFilterExpanded = false
    },
    confirmFilter() {
      const priceRange = this.normalizeCustomPriceRange(this.filterForm)
      this.activeFilterForm = {
        categoryLevel1Id: this.filterForm.categoryLevel1Id || 'all',
        categoryLevel2Id: this.filterForm.categoryLevel2Id || '',
        priceRange,
        publishTime: this.filterForm.publishTime || 'all',
        condition: this.filterForm.condition || 'all',
        location: this.filterForm.location || 'all'
      }
      this.filterVisible = false
    },
    selectFilterCategoryLevel1(value) {
      this.filterForm = {
        ...this.filterForm,
        categoryLevel1Id: value,
        categoryLevel2Id: ''
      }
    },
    selectFilterPrice(value) {
      this.filterForm = {
        ...this.filterForm,
        priceRange: value,
        customPriceMin: '',
        customPriceMax: ''
      }
    },
    normalizeCustomPriceRange(form = {}) {
      const minText = String(form.customPriceMin ?? '').trim()
      const maxText = String(form.customPriceMax ?? '').trim()
      if (!minText && !maxText) return form.priceRange || 'all'
      const min = minText ? Math.max(0, Number(minText)) : 0
      const max = maxText ? Math.max(0, Number(maxText)) : ''
      if (Number.isNaN(min) || Number.isNaN(max)) return form.priceRange || 'all'
      return max === '' ? `${min}-` : `${min}-${max}`
    },
    goToSearch() {
      if (this.searchTransitioning || this.searchTransitionNavigating) return
      uni.createSelectorQuery()
        .in(this)
        .select('.market-search-pill')
        .boundingClientRect()
        .select('.toolbar')
        .boundingClientRect()
        .exec((res) => {
          const rect = res && res[0]
          const toolbarRect = res && res[1]
          const targetUrl = '/subpackage_lostfound/marketSearch/marketSearch?source=hotlist&placeholder=%E6%90%9C%E7%B4%A2%E6%9C%AC%E6%A0%A1%E5%95%86%E5%93%81'
          if (!rect) {
            uni.navigateTo({
              url: targetUrl,
              animationType: 'none',
              animationDuration: 0
            })
            return
          }
          this.searchTransitionRect = {
            left: rect.left || 0,
            top: rect.top || 0,
            width: rect.width || 0,
            height: rect.height || 0,
            surfaceTop: toolbarRect && toolbarRect.bottom ? toolbarRect.bottom : (rect.top || 0) + (rect.height || 0)
          }
          this.$nextTick(() => {
            this.searchTransitioning = true
          })
        })
    },
    onSearchTransitionEnd() {
      if (!this.searchTransitioning || this.searchTransitionNavigating) return
      this.searchTransitionNavigating = true
      uni.navigateTo({
        url: '/subpackage_lostfound/marketSearch/marketSearch?source=hotlist&placeholder=%E6%90%9C%E7%B4%A2%E6%9C%AC%E6%A0%A1%E5%95%86%E5%93%81',
        animationType: 'none',
        animationDuration: 0,
        complete: () => {
          this.searchTransitioning = false
          this.searchTransitionNavigating = false
        }
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
  gap: 18rpx;
  padding: 28rpx 28rpx 24rpx;
  background: #F7F7F9;
  border-bottom: 0;
}

.hot-list-search {
  flex: 1;
  min-width: 0;
}

.market-search-pill {
  display: flex;
  align-items: center;
  gap: 12rpx;
  padding: 0 28rpx;
  height: 82rpx;
  border-radius: 42rpx;
  background: #FFFFFF;
  box-shadow: 0 10rpx 24rpx rgba(92, 122, 153, 0.12);
  border: 1rpx solid rgba(218, 228, 238, 0.9);
  box-sizing: border-box;
}

.market-search-pill-icon {
  width: 38rpx;
  height: 38rpx;
  flex-shrink: 0;
  opacity: 0.58;
}

.market-search-pill-input {
  flex: 1;
  min-width: 0;
  height: 82rpx;
  line-height: 82rpx;
  font-size: 27rpx;
  font-weight: 500;
  color: #8C929A;
  -webkit-text-fill-color: #8C929A;
  padding: 0;
  margin: 0;
  border: none;
  box-sizing: border-box;
  background: transparent;
  opacity: 1;
  pointer-events: none;
}

.search-transition-mask {
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

.search-transition-top-panel {
  position: fixed;
  top: 0;
  right: 0;
  left: 0;
  height: var(--search-transition-surface-top, 180rpx);
  background: transparent;
  opacity: 0;
  z-index: 2;
  transition: none;
}

.search-transition-surface {
  position: fixed;
  left: 0;
  right: 0;
  top: var(--search-transition-surface-top, 180rpx);
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
  transition:
    transform 340ms cubic-bezier(0.22, 1, 0.36, 1),
    opacity 220ms ease-out;
  will-change: transform, opacity;
}

.search-transition-mask--active .search-transition-surface {
  opacity: 1;
  transform: translate3d(0, 0, 0) scale3d(1, 1, 1);
}

.search-transition-bar {
  position: fixed;
  left: var(--search-transition-start-left, 28rpx);
  top: var(--search-transition-start-top, 120rpx);
  width: var(--search-transition-start-width, calc(100vw - 132rpx));
  height: var(--search-transition-start-height, 82rpx);
  z-index: 100000;
  display: flex;
  align-items: center;
  gap: 12rpx;
  padding: 0 28rpx;
  border-radius: 42rpx;
  background: #FFFFFF;
  border: 1rpx solid rgba(218, 228, 238, 0.9);
  box-shadow: 0 10rpx 24rpx rgba(92, 122, 153, 0.12);
  box-sizing: border-box;
  opacity: 0;
  transform: translate3d(0, 8rpx, 0) scale3d(0.985, 0.985, 1);
  transform-origin: center center;
  transition:
    opacity 180ms ease-out,
    transform 340ms cubic-bezier(0.22, 1, 0.36, 1),
    border-radius 340ms cubic-bezier(0.22, 1, 0.36, 1),
    box-shadow 340ms ease-out;
  will-change: transform, opacity;
}

.search-transition-mask--active .search-transition-bar {
  opacity: 1;
  transform: translate3d(0, 0, 0) scale3d(1, 1, 1);
}

.search-transition-icon {
  width: 38rpx;
  height: 38rpx;
  flex-shrink: 0;
  opacity: 0.58;
}

.search-transition-input {
  flex: 1;
  min-width: 0;
  height: 82rpx;
  line-height: 82rpx;
  font-size: 27rpx;
  font-weight: 500;
  color: #8C929A;
  -webkit-text-fill-color: #8C929A;
  padding: 0;
  margin: 0;
  border: none;
  box-sizing: border-box;
  background: transparent;
  opacity: 1;
  pointer-events: none;
}

.search-filter-btn {
  width: 82rpx;
  height: 82rpx;
  border-radius: 50%;
  background: #FFFFFF;
  border: 1rpx solid rgba(218, 228, 238, 0.9);
  box-shadow: 0 10rpx 24rpx rgba(92, 122, 153, 0.12);
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  box-sizing: border-box;
}

.search-filter-btn.on {
  background: rgba(92, 122, 153, 0.08);
  border-color: rgba(92, 122, 153, 0.18);
}

.filter-icon {
  width: 36rpx;
  height: 36rpx;
  opacity: 0.82;
}

.filter-dot {
  position: absolute;
  top: 8rpx;
  right: 6rpx;
  width: 12rpx;
  height: 12rpx;
  border-radius: 50%;
  background: #E85D75;
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
  background: rgba(17, 24, 39, 0.18);
  z-index: 100;
  display: flex;
  align-items: flex-end;
  animation: filterFadeIn 0.2s ease-out;
}

.filter-panel {
  width: 100%;
  height: 56vh;
  min-height: 50vh;
  max-height: 60vh;
  background: #FFFFFF;
  border-radius: 42rpx 42rpx 0 0;
  display: flex;
  flex-direction: column;
  box-shadow: 0 -16rpx 48rpx rgba(31, 41, 55, 0.1);
  animation: filterSlideUp 0.3s cubic-bezier(0.22, 1, 0.36, 1);
  overflow: hidden;
}

.filter-handle {
  width: 82rpx;
  height: 9rpx;
  border-radius: 999rpx;
  background: #D0D5DD;
  margin: 18rpx auto 4rpx;
}

.filter-header {
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 18rpx 40rpx 20rpx;
  position: relative;
  flex-shrink: 0;
}

.filter-title {
  font-size: 34rpx;
  font-weight: 850;
  color: #1D1D1F;
  line-height: 1.2;
}

.filter-close {
  position: absolute;
  right: 34rpx;
  top: 18rpx;
  font-size: 44rpx;
  color: #6B7280;
  width: 64rpx;
  height: 64rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  line-height: 1;
}

.selected-filter-strip {
  display: flex;
  gap: 12rpx;
  padding: 0 32rpx 14rpx;
  overflow-x: auto;
  white-space: nowrap;
  scrollbar-width: none;
  -ms-overflow-style: none;
  flex-shrink: 0;
}

.selected-filter-strip::-webkit-scrollbar {
  display: none;
}

.selected-filter-chip {
  display: inline-flex;
  align-items: center;
  height: 44rpx;
  padding: 0 18rpx;
  border-radius: 999rpx;
  background: #EAF3FF;
  border: 1rpx solid #C8DAF0;
  color: #4F7FB8;
  font-size: 21rpx;
  font-weight: 700;
  flex-shrink: 0;
}

.filter-body {
  flex: 1;
  min-height: 0;
  overflow-y: auto;
  padding: 0 32rpx 18rpx;
  box-sizing: border-box;
}

.filter-group {
  margin-bottom: 30rpx;
  box-sizing: border-box;
}

.filter-group--compact {
  margin-bottom: 28rpx;
}

.filter-group-title {
  position: relative;
  padding-left: 22rpx;
  font-size: 28rpx;
  font-weight: 850;
  color: #2B2F36;
  margin-bottom: 18rpx;
  line-height: 1.25;
}

.filter-group-title::before {
  content: '';
  position: absolute;
  left: 0;
  top: 4rpx;
  width: 7rpx;
  height: 32rpx;
  border-radius: 999rpx;
  background: #3F73C8;
}

.filter-options {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
  max-width: 100%;
  box-sizing: border-box;
}

.filter-chip-scroll {
  width: 100%;
  white-space: nowrap;
}

.filter-options--inline {
  display: inline-flex;
  flex-wrap: nowrap;
  min-width: 100%;
  padding-bottom: 2rpx;
}

.filter-price-custom {
  display: flex;
  align-items: center;
  gap: 16rpx;
  margin-top: 18rpx;
}

.filter-price-input {
  flex: 1;
  min-width: 0;
  height: 66rpx;
  padding: 0 22rpx;
  border-radius: 17rpx;
  background: #FFFFFF;
  border: 1rpx solid #DDE2EA;
  color: #1D1D1F;
  font-size: 25rpx;
  box-sizing: border-box;
}

.filter-price-placeholder {
  color: #A8AFB9;
}

.filter-price-separator {
  color: #1D1D1F;
  font-size: 28rpx;
  font-weight: 700;
}

.filter-opt {
  width: calc((100% - 54rpx) / 4);
  height: 58rpx;
  padding: 0 10rpx;
  border-radius: 17rpx;
  background: #FFFFFF;
  border: 1rpx solid #DDE2EA;
  font-size: 24rpx;
  font-weight: 750;
  color: #252A31;
  box-sizing: border-box;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: transform 0.16s ease, background-color 0.16s ease, border-color 0.16s ease, color 0.16s ease;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.filter-opt--chip {
  width: auto;
  min-width: 122rpx;
  padding: 0 24rpx;
  flex-shrink: 0;
}

.filter-opt:active {
  transform: scale(0.96);
}

.filter-opt.on {
  background: #F1F6FF;
  border-color: #A8C3F0;
  color: #2F6FC8;
}

.filter-more {
  margin: 4rpx 0 10rpx;
  border: 1rpx solid #E6EBF2;
  border-radius: 24rpx;
  background: #FBFCFE;
  overflow: hidden;
}

.filter-more-head {
  min-height: 92rpx;
  padding: 18rpx 22rpx;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18rpx;
  box-sizing: border-box;
}

.filter-more-title {
  font-size: 27rpx;
  font-weight: 850;
  color: #20252C;
  line-height: 1.25;
}

.filter-more-sub {
  margin-top: 8rpx;
  font-size: 22rpx;
  font-weight: 600;
  color: #8A94A3;
  line-height: 1.25;
}

.filter-more-chevron {
  width: 18rpx;
  height: 18rpx;
  border-right: 3rpx solid #8A94A3;
  border-bottom: 3rpx solid #8A94A3;
  transform: rotate(45deg);
  transition: transform 0.2s ease;
  flex-shrink: 0;
}

.filter-more-chevron.open {
  transform: rotate(225deg);
}

.filter-more-body {
  padding: 0 18rpx 18rpx;
  animation: filterMoreReveal 0.22s ease-out;
}

.filter-footer {
  display: flex;
  gap: 20rpx;
  padding: 18rpx 32rpx calc(18rpx + env(safe-area-inset-bottom));
  border-top: 1rpx solid #E5E7EB;
  background: rgba(255, 255, 255, 0.98);
  box-shadow: 0 -8rpx 20rpx rgba(31, 41, 55, 0.04);
  flex-shrink: 0;
}

.filter-btn {
  flex: 1;
  height: 74rpx;
  border-radius: 22rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28rpx;
  font-weight: 850;
}

.filter-btn.reset {
  background: #FFFFFF;
  border: 1rpx solid #C8D0DA;
  color: #1D1D1F;
}

.filter-btn.confirm {
  background: #4B7DCE;
  color: #fff;
  box-shadow: 0 10rpx 24rpx rgba(75, 125, 206, 0.24);
}

@keyframes filterFadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}

@keyframes filterSlideUp {
  from { transform: translateY(100%); }
  to { transform: translateY(0); }
}

@keyframes filterMoreReveal {
  from {
    opacity: 0;
    transform: translateY(-8rpx);
  }
  to {
    opacity: 1;
    transform: translateY(0);
  }
}
</style>
