<template>
  <view class="page-root">
    <common-page-header title="搜索" :fixed="true" :placeholder="true" :showBack="true" :autoBack="false" @back="handleBack" />

    <view class="search-shell">
      <view class="search-bar">
        <image class="search-icon" src="/static/icons/search.svg" mode="aspectFit" />
        <input
          v-model="keyword"
          class="search-input"
          :placeholder="searchPlaceholder"
          placeholder-class="search-ph"
          confirm-type="search"
          :focus="true"
          @confirm="submitSearch"
        />
        <view v-if="keyword" class="clear-btn" @click="clearKeyword">×</view>
      </view>
      <view class="search-action" @click="submitSearch">搜索</view>
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
      <view v-if="!hasSearched" class="history-section">
        <view class="section-head">
          <text class="section-title">搜索历史</text>
          <text v-if="historyList.length" class="section-clear" @click="clearHistory">清空</text>
        </view>
        <view v-if="historyList.length" class="history-list">
          <view
            v-for="item in historyList"
            :key="item"
            class="history-chip"
            @click="useHistory(item)"
          >{{ item }}</view>
        </view>
        <view v-else class="history-empty">
          <text class="history-empty-title">暂无搜索历史</text>
          <text class="history-empty-desc">输入关键词查找本校闲置</text>
        </view>
      </view>

      <view v-else class="result-section">
        <view class="result-head">
          <text class="result-title">搜索结果</text>
          <view class="result-actions">
            <view class="result-filter-entry" @click="categoryFilterExpanded = !categoryFilterExpanded">
              <image class="result-filter-icon" src="/static/icons/mage-filter-fill.svg" mode="aspectFit" />
              <text>筛选</text>
            </view>
            <text class="result-count">{{ resultItems.length }} 件</text>
          </view>
        </view>
        <view class="category-filter-shell" :class="{ 'category-filter-shell--open': categoryFilterExpanded }">
          <view class="category-filter category-filter--inline">
            <scroll-view scroll-x class="category-scroll" :show-scrollbar="false">
              <view class="category-row">
                <view
                  v-for="cat in categoryLevel1Options"
                  :key="'inline-' + cat.id"
                  class="category-chip"
                  :class="{ on: selectedCategoryLevel1Id === cat.id }"
                  @click="selectCategoryLevel1(cat.id)"
                >{{ cat.name }}</view>
              </view>
            </scroll-view>
            <scroll-view v-if="categoryLevel2Options.length" scroll-x class="category-scroll category-scroll--sub" :show-scrollbar="false">
              <view class="category-row">
                <view
                  v-for="cat in categoryLevel2Options"
                  :key="'inline-sub-' + cat.id"
                  class="category-chip category-chip--sub"
                  :class="{ on: selectedCategoryLevel2Id === cat.id }"
                  @click="selectCategoryLevel2(cat.id)"
                >{{ cat.name }}</view>
              </view>
            </scroll-view>
            <view class="filter-group">
              <view class="filter-group-title">价格</view>
              <scroll-view scroll-x class="category-scroll" :show-scrollbar="false">
                <view class="category-row">
                  <view
                    v-for="item in priceOptions"
                    :key="'price-' + item.value"
                    class="category-chip category-chip--sub"
                    :class="{ on: selectedPriceRange === item.value }"
                    @click="selectedPriceRange = item.value"
                  >{{ item.label }}</view>
                </view>
              </scroll-view>
              <view class="custom-price-row">
                <input
                  v-model="customPriceMin"
                  class="custom-price-input"
                  type="number"
                  placeholder="最低价"
                  placeholder-class="custom-price-placeholder"
                />
                <text class="custom-price-separator">-</text>
                <input
                  v-model="customPriceMax"
                  class="custom-price-input"
                  type="number"
                  placeholder="最高价"
                  placeholder-class="custom-price-placeholder"
                />
                <view class="custom-price-btn" @click="applyCustomPrice">确定</view>
              </view>
            </view>
            <view class="filter-group">
              <view class="filter-group-title">商品成色</view>
              <scroll-view scroll-x class="category-scroll" :show-scrollbar="false">
                <view class="category-row">
                  <view
                    v-for="item in conditionOptions"
                    :key="'condition-' + item.value"
                    class="category-chip category-chip--sub"
                    :class="{ on: selectedCondition === item.value }"
                    @click="selectedCondition = item.value"
                  >{{ item.label }}</view>
                </view>
              </scroll-view>
            </view>
            <view class="filter-group">
              <view class="filter-group-title">发布时间</view>
              <scroll-view scroll-x class="category-scroll" :show-scrollbar="false">
                <view class="category-row">
                  <view
                    v-for="item in timeOptions"
                    :key="'time-' + item.value"
                    class="category-chip category-chip--sub"
                    :class="{ on: selectedPublishTime === item.value }"
                    @click="selectedPublishTime = item.value"
                  >{{ item.label }}</view>
                </view>
              </scroll-view>
            </view>
          </view>
        </view>
        <market-product-grid
          :items="resultItems"
          :loading="loading && items.length === 0"
          empty-title="暂无相关商品"
          empty-sub="换个关键词试试"
          @item-click="goDetail"
        />
      </view>
    </scroll-view>
  </view>
</template>

<script>
import CommonPageHeader from '@/components/common-page-header/common-page-header.vue'
import MarketProductGrid from '@/components/market-product-grid/market-product-grid.vue'
import { getSecondhandItemList } from '@/api/secondhand'
import { createDefaultMarketFilter, filterMarketItems } from '@/subpackage_lostfound/utils/marketFilter.js'
import { MARKET_CATEGORIES } from '@/subpackage_lostfound/utils/marketCategories.js'
import { normalizeSecondhandItem } from '@/subpackage_lostfound/utils/secondhandItem.js'

const HISTORY_KEY = 'market_search_history'

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

export default {
  components: {
    CommonPageHeader,
    MarketProductGrid
  },
  data() {
    return {
      keyword: '',
      activeKeyword: '',
      hasSearched: false,
      loading: false,
      refreshing: false,
      items: [],
      historyList: [],
      categories: MARKET_CATEGORIES,
      selectedCategoryLevel1Id: 'all',
      selectedCategoryLevel2Id: '',
      selectedPriceRange: 'all',
      customPriceMin: '',
      customPriceMax: '',
      selectedCondition: 'all',
      selectedPublishTime: 'all',
      priceOptions: PRICE_OPTIONS,
      conditionOptions: CONDITION_OPTIONS,
      timeOptions: TIME_OPTIONS,
      source: '',
      fromRoute: '',
      searchPlaceholder: '搜索',
      categoryFilterExpanded: false
    }
  },
  computed: {
    resultItems() {
      return filterMarketItems(this.items, createDefaultMarketFilter({
        keyword: this.activeKeyword,
        categoryLevel1Id: this.selectedCategoryLevel1Id,
        categoryLevel2Id: this.selectedCategoryLevel2Id,
        priceRange: this.selectedPriceRange,
        condition: this.selectedCondition,
        publishTime: this.selectedPublishTime,
        sortBy: 'latest'
      }))
    },
    categoryLevel1Options() {
      return [
        { id: 'all', name: '全部' },
        ...this.categories.map((item) => ({ id: item.id, name: item.name }))
      ]
    },
    categoryLevel2Options() {
      const current = this.categories.find((item) => item.id === this.selectedCategoryLevel1Id)
      return current?.children?.length ? [{ id: '', name: '全部' }, ...current.children] : []
    }
  },
  onLoad(options = {}) {
    this.source = this.safeDecode(options.source)
    this.fromRoute = this.safeDecode(options.fromRoute)
    this.searchPlaceholder = this.safeDecode(options.placeholder) || '搜索'
    this.loadHistory()
    this.loadItems()
    if (options.keyword) {
      this.keyword = decodeURIComponent(options.keyword)
      this.submitSearch()
    }
  },
  onBackPress() {
    const pages = getCurrentPages()
    if (pages.length > 1) {
      return false
    }
    this.handleBack()
    return true
  },
  methods: {
    async loadItems() {
      this.loading = true
      try {
        const res = await getSecondhandItemList({ current: 1, size: 100, sort: 'latest' })
        const records = Array.isArray(res?.data?.records) ? res.data.records : []
        this.items = records.map(normalizeSecondhandItem)
      } catch (error) {
        console.error('加载搜索商品失败', error)
        this.items = []
      } finally {
        this.loading = false
      }
    },
    async refreshPage() {
      if (this.refreshing) return
      this.refreshing = true
      try {
        this.loadHistory()
        await this.loadItems()
        if (this.hasSearched) {
          this.categoryFilterExpanded = false
        }
        uni.showToast({ title: '已刷新', icon: 'none', duration: 900 })
      } finally {
        this.refreshing = false
      }
    },
    loadHistory() {
      try {
        const list = uni.getStorageSync(HISTORY_KEY)
        this.historyList = Array.isArray(list) ? list : []
      } catch (error) {
        this.historyList = []
      }
    },
    saveHistory(value) {
      const keyword = String(value || '').trim()
      if (!keyword) return
      const next = [keyword, ...this.historyList.filter((item) => item !== keyword)].slice(0, 10)
      this.historyList = next
      uni.setStorageSync(HISTORY_KEY, next)
    },
    submitSearch() {
      const value = String(this.keyword || '').trim()
      if (!value) {
        uni.showToast({ title: '请输入搜索内容', icon: 'none' })
        return
      }
      this.activeKeyword = value
      this.hasSearched = true
      this.categoryFilterExpanded = false
      this.saveHistory(value)
    },
    useHistory(value) {
      this.keyword = value
      this.submitSearch()
    },
    clearKeyword() {
      this.keyword = ''
      this.activeKeyword = ''
      this.hasSearched = false
    },
    clearHistory() {
      this.historyList = []
      uni.removeStorageSync(HISTORY_KEY)
    },
    selectCategoryLevel1(id) {
      this.selectedCategoryLevel1Id = id
      this.selectedCategoryLevel2Id = ''
    },
    selectCategoryLevel2(id) {
      this.selectedCategoryLevel2Id = id
    },
    applyCustomPrice() {
      const min = Number(this.customPriceMin)
      const max = Number(this.customPriceMax)
      if (this.customPriceMin !== '' && Number.isNaN(min)) return
      if (this.customPriceMax !== '' && Number.isNaN(max)) return
      if (this.customPriceMin === '' && this.customPriceMax === '') {
        this.selectedPriceRange = 'all'
        return
      }
      const safeMin = this.customPriceMin === '' ? 0 : Math.max(0, min)
      const safeMax = this.customPriceMax === '' ? '' : Math.max(0, max)
      this.selectedPriceRange = safeMax === '' ? `${safeMin}-` : `${safeMin}-${safeMax}`
    },
    safeDecode(value) {
      if (!value) return ''
      try {
        return decodeURIComponent(value)
      } catch (error) {
        return String(value)
      }
    },
    isValidRoute(url) {
      return typeof url === 'string' && url.startsWith('/') && !url.startsWith('//')
    },
    handleBack() {
      const pages = getCurrentPages()
      if (pages.length > 1) {
        uni.navigateBack({ delta: 1 })
        return
      }
      if (this.isValidRoute(this.fromRoute)) {
        uni.reLaunch({ url: this.fromRoute })
        return
      }
      uni.redirectTo({ url: '/subpackage_lostfound/lostfoundList/lostfoundList' })
    },
    goDetail(id) {
      uni.navigateTo({ url: `/subpackage_lostfound/lostfoundDetail/lostfoundDetail?id=${id}` })
    }
  }
}
</script>

<style scoped>
.page-root {
  min-height: 100vh;
  background: #F7F7F9;
}

.search-shell {
  display: flex;
  align-items: center;
  gap: 18rpx;
  padding: 28rpx 28rpx 34rpx;
  background: #F7F7F9;
}

.search-bar {
  flex: 1;
  height: 82rpx;
  border-radius: 42rpx;
  background: #FFFFFF;
  border: 1rpx solid rgba(218, 228, 238, 0.9);
  box-shadow: 0 10rpx 24rpx rgba(92, 122, 153, 0.12);
  display: flex;
  align-items: center;
  gap: 12rpx;
  padding: 0 28rpx;
  box-sizing: border-box;
}

.search-icon {
  width: 38rpx;
  height: 38rpx;
  flex-shrink: 0;
  opacity: 0.58;
}

.search-input {
  flex: 1;
  min-width: 0;
  height: 82rpx;
  font-size: 27rpx;
  line-height: 82rpx;
  color: #111111;
  padding: 0;
  margin: 0;
  border: none;
  box-sizing: border-box;
}

.search-ph {
  color: #888888;
  font-weight: 500;
}

.clear-btn {
  width: 42rpx;
  height: 42rpx;
  border-radius: 50%;
  background: #E8EAED;
  color: #8E8E93;
  font-size: 28rpx;
  line-height: 38rpx;
  text-align: center;
}

.search-action {
  width: 82rpx;
  height: 82rpx;
  flex-shrink: 0;
  box-sizing: border-box;
  font-size: 26rpx;
  font-weight: 700;
  color: #4A6278;
  padding: 0;
  text-align: center;
  line-height: 82rpx;
  border-radius: 50%;
  background: #FFFFFF;
  border: 1rpx solid rgba(218, 228, 238, 0.9);
  box-shadow: 0 10rpx 24rpx rgba(92, 122, 153, 0.12);
}

.page-body {
  height: calc(100vh - 180rpx);
}

.history-section,
.result-section {
  min-height: calc(100vh - 180rpx);
  padding-top: 28rpx;
  box-sizing: border-box;
}

.category-filter-shell {
  max-height: 0;
  opacity: 0;
  overflow: hidden;
  transition: max-height 260ms ease-out, opacity 220ms ease-out;
}

.category-filter-shell--open {
  max-height: 680rpx;
  opacity: 1;
}

.category-filter {
  margin: 0 28rpx 22rpx;
  padding: 22rpx 0 24rpx;
  background: #FFFFFF;
  border-radius: 22rpx;
  box-sizing: border-box;
}

.category-filter--inline {
  border: 1rpx solid #EEEEEE;
}

.filter-group {
  margin-top: 18rpx;
}

.filter-group-title {
  padding: 0 22rpx 12rpx;
  color: #8A8F98;
  font-size: 22rpx;
  font-weight: 700;
}

.custom-price-row {
  display: flex;
  align-items: center;
  gap: 12rpx;
  padding: 14rpx 22rpx 0;
}

.custom-price-input {
  flex: 1;
  min-width: 0;
  height: 56rpx;
  padding: 0 18rpx;
  border-radius: 16rpx;
  background: #FFFFFF;
  border: 1rpx solid #EEEEEE;
  color: #1D1D1F;
  font-size: 24rpx;
  box-sizing: border-box;
}

.custom-price-placeholder {
  color: #A2A8AF;
}

.custom-price-separator {
  color: #A2A8AF;
  font-size: 24rpx;
  font-weight: 700;
}

.custom-price-btn {
  height: 56rpx;
  padding: 0 22rpx;
  border-radius: 16rpx;
  background: rgba(92, 122, 153, 0.12);
  color: #4A6278;
  font-size: 24rpx;
  font-weight: 700;
  line-height: 56rpx;
}

.category-scroll {
  width: 100%;
  white-space: nowrap;
  box-sizing: border-box;
}

.category-scroll--sub {
  margin-top: 14rpx;
}

.category-row {
  display: inline-flex;
  gap: 14rpx;
  padding: 0 22rpx;
  box-sizing: border-box;
}

.category-chip {
  height: 56rpx;
  padding: 0 24rpx;
  border-radius: 28rpx;
  background: #FFFFFF;
  border: 1rpx solid #EEEEEE;
  color: #4A4A4A;
  font-size: 24rpx;
  font-weight: 650;
  line-height: 56rpx;
  box-sizing: border-box;
}

.category-chip.on {
  background: rgba(92, 122, 153, 0.12);
  border-color: transparent;
  color: #4A6278;
}

.category-chip--sub {
  height: 52rpx;
  line-height: 52rpx;
  font-size: 23rpx;
  color: #6B6F76;
}

.section-head,
.result-head {
  display: flex;
  align-items: baseline;
  justify-content: space-between;
  padding: 0 28rpx 18rpx;
}

.section-title,
.result-title {
  font-size: 30rpx;
  font-weight: 800;
  color: #1D1D1F;
}

.section-clear,
.result-count {
  font-size: 23rpx;
  color: #8E8E93;
}

.result-actions {
  display: flex;
  align-items: center;
  gap: 18rpx;
}

.result-filter-entry {
  display: inline-flex;
  align-items: center;
  gap: 6rpx;
  color: #5C5C60;
  font-size: 23rpx;
  font-weight: 700;
}

.result-filter-icon {
  width: 26rpx;
  height: 26rpx;
  opacity: 0.55;
}

.history-list {
  display: flex;
  flex-wrap: wrap;
  gap: 16rpx;
  padding: 0 28rpx;
}

.history-chip {
  max-width: 100%;
  padding: 14rpx 22rpx;
  border-radius: 999rpx;
  background: #FFFFFF;
  border: 1rpx solid #EEEEEE;
  color: #4A4A4A;
  font-size: 24rpx;
  font-weight: 600;
  box-sizing: border-box;
}

.history-empty {
  margin: 0 28rpx;
  min-height: 260rpx;
  border-radius: 22rpx;
  border: 1rpx solid #EEEEEE;
  background: #FFFFFF;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
}

.history-empty-title {
  font-size: 28rpx;
  font-weight: 750;
  color: #1D1D1F;
}

.history-empty-desc {
  margin-top: 8rpx;
  font-size: 23rpx;
  color: #8E8E93;
}

</style>
