<template>
  <view class="canteen-page">
    <view class="top-bar">
      <view class="back-btn" @click="goBack">
        <text class="back-arrow">‹</text>
        <text class="back-text">返回</text>
      </view>
    </view>
    <view class="top-search" @click="openSearch">
      <text class="search-icon">⌕</text>
      <text class="search-placeholder">搜索菜品 / 档口 / 评价关键词...</text>
    </view>

    <view class="canteen-tabs">
      <view
        v-for="item in canteenList"
        :key="item.id"
        class="canteen-tab"
        :class="{ active: currentRestaurantId === item.id }"
        @click="switchRestaurant(item.id)"
      >
        <text class="canteen-tab-text">{{ item.name }}</text>
      </view>
    </view>

    <scroll-view class="page-scroll" scroll-y>
      <view class="section-block">
        <text class="section-title">人气热榜</text>
        <scroll-view class="hot-scroll" scroll-x :show-scrollbar="false">
          <view class="hot-row">
            <view
              v-for="item in hotRanking"
              :key="item.id"
              class="hot-card"
              @click="openStallDetail(item)"
            >
              <view class="hot-cover" :class="item.foodClass">
                <text class="hot-tag">{{ item.tag }}</text>
              </view>
              <view class="hot-bottom">
                <text class="hot-rate">推荐率 {{ item.recommendRate }}%</text>
                <text class="hot-name">{{ item.stallName }}</text>
                <view class="hot-meta">
                  <text>{{ item.category }}</text>
                  <text>人均 ¥{{ item.avgPrice }}</text>
                </view>
              </view>
            </view>
          </view>
          <view class="empty-hot-tip" v-if="hotRanking.length === 0">
            <text>暂无数据</text>
          </view>
        </scroll-view>
      </view>

      <view class="stall-list">
        <view
          v-for="stall in filteredStalls"
          :key="stall.id"
          class="stall-card"
          @click="openStallDetail(stall)"
        >
          <view class="stall-main">
            <view class="stall-thumb" :class="getStallFoodClass(stall.category)"></view>
            <view class="stall-content">
              <view class="stall-title-row">
                <text class="stall-name">{{ stall.stallName }}</text>
                <text class="stall-category">{{ stall.category }}</text>
              </view>
              <view class="stall-meta-row">
                <text>评价{{ stall.reviewCount }}</text>
                <text>人均¥{{ stall.avgPrice }}</text>
                <text>推荐率{{ stall.recommendRate }}%</text>
              </view>
              <view class="recommend-line">
                <text class="recommend-label">推荐率</text>
                <view class="recommend-track">
                  <view class="recommend-fill" :style="{ width: stall.recommendRate + '%' }"></view>
                </view>
              </view>
              <text class="stall-location">{{ currentRestaurant.name }} {{ stall.floor }}</text>
            </view>
          </view>

          <view class="stall-footer">
            <view class="review-snippet">
              <text class="review-icon">♨</text>
              <text class="review-text">{{ stall.description || '暂无评价' }}</text>
            </view>
            <view class="review-btn">看评价</view>
          </view>
        </view>
      </view>

      <view class="empty-tip" v-if="filteredStalls.length === 0">
        <text>暂无档口数据</text>
      </view>

      <!-- 菜品列表 -->
      <view class="dish-section" v-if="filteredDishes.length > 0">
        <text class="section-title">菜品推荐</text>
        <view class="dish-grid">
          <view
            v-for="dish in filteredDishes"
            :key="dish.id"
            class="dish-card"
            @click="openDishDetail(dish)"
          >
            <view class="dish-image" :style="{ backgroundImage: dish.imageUrl ? `url(${dish.imageUrl})` : 'none' }"></view>
            <view class="dish-info">
              <view class="dish-name-row">
                <text class="dish-name">{{ dish.name }}</text>
                <text class="dish-price">¥{{ dish.price }}</text>
              </view>
              <view class="dish-meta">
                <text class="dish-rating">★ {{ dish.rating }}</text>
                <text class="dish-sold">已售{{ dish.soldCount }}</text>
                <text class="dish-reviews">{{ getReviewCount(dish.id) }}条评价</text>
              </view>
              <view class="dish-category-tag">{{ dish.category }}</view>
            </view>
          </view>
        </view>
      </view>

      <view class="bottom-space"></view>
    </scroll-view>

    <view v-if="showSearchPanel" class="search-mask" @click="closeSearch">
      <view class="search-panel" @click.stop>
        <view class="search-panel-top">
          <view class="search-input-wrap">
            <text class="search-icon">⌕</text>
            <input
              v-model="searchQuery"
              class="search-input"
              placeholder="搜索菜品、档口、评价..."
              confirm-type="search"
              @confirm="performSearch"
            />
          </view>
          <text class="search-cancel" @click="closeSearch">取消</text>
        </view>

        <view class="panel-section" v-if="searchHistory.length">
          <view class="panel-header">
            <text class="panel-title">历史搜索</text>
            <text class="panel-action" @click="clearHistory">清除</text>
          </view>
          <view class="tag-wrap">
            <text
              v-for="item in searchHistory"
              :key="item"
              class="soft-tag"
              @click="quickSearch(item)"
            >{{ item }}</text>
          </view>
        </view>

        <view class="panel-section">
          <view class="panel-header">
            <text class="panel-title">猜你想搜</text>
          </view>
          <view class="tag-wrap">
            <text
              v-for="item in suggestKeywords"
              :key="item"
              class="soft-tag"
              @click="quickSearch(item)"
            >{{ item }}</text>
          </view>
        </view>

        <view class="panel-section">
          <view class="panel-header">
            <text class="panel-title">周边热搜</text>
          </view>
          <view class="trend-list">
            <view v-for="(item, index) in weeklyHot" :key="item.name" class="trend-item" @click="quickSearch(item.name)">
              <text class="trend-index">{{ index + 1 }}</text>
              <text class="trend-name">{{ item.name }}</text>
              <text class="trend-badge">{{ item.badge }}</text>
            </view>
          </view>
        </view>
      </view>
    </view>
  </view>
</template>

<script>
import { getCanteenStallList, getDishList, getDishReviewCount } from '@/api/dining.js'

export default {
  data() {
    return {
      showSearchPanel: false,
      searchQuery: '',
      searchHistory: ['肉包', '早餐', '面食'],
      suggestKeywords: ['黄焖米饭', '石锅拌饭', '兰州拉面', '奶茶', '麻辣烫'],
      weeklyHot: [
        { name: '包子', badge: '热' },
        { name: '麻辣烫', badge: '热' },
        { name: '自选快餐', badge: '新' },
        { name: '重庆小面', badge: '新' },
        { name: '沙县小吃', badge: '热' }
      ],
      currentRestaurantId: '1',
      // 食堂（餐厅）列表 - 对应 campus_facility 表 facility_type=1 的数据
      canteenList: [
        { id: '1', name: '第一学生餐厅' },
        { id: '2', name: '第二学生餐厅' },
        { id: '3', name: '清真餐厅' }
      ],
      // 从 API 加载的档口数据
      stallList: [],
      // 从 API 加载的菜品数据
      dishList: [],
      // 评价数量映射表
      reviewCountMap: {}
    }
  },
  computed: {
    currentRestaurant() {
      return this.canteenList.find(item => item.id === this.currentRestaurantId) || this.canteenList[0]
    },
    // 过滤当前食堂的档口
    filteredStalls() {
      return this.stallList.filter(stall => stall.restaurantId.toString() === this.currentRestaurantId)
    },
    // 人气热榜 - 按推荐率排序取前 3
    hotRanking() {
      return [...this.filteredStalls]
        .sort((a, b) => b.recommendRate - a.recommendRate)
        .slice(0, 3)
        .map((item, index) => ({
          ...item,
          tag: `${item.category} Top${index + 1}`,
          foodClass: this.getFoodClass(index)
        }))
    },
    // 过滤当前食堂的菜品
    filteredDishes() {
      // 获取当前餐厅所有档口的 ID
      const currentStallIds = this.filteredStalls.map(stall => stall.id)
      // 过滤出属于这些档口的菜品
      return this.dishList.filter(dish => currentStallIds.includes(dish.stallId))
    }
  },
  onLoad(options) {
    if (options && options.id) {
      this.currentRestaurantId = options.id
    }
    // 加载档口数据
    this.loadStalls()
    // 加载菜品数据
    this.loadDishes()
  },
  methods: {
    goBack() {
      uni.navigateBack()
    },
    // 获取食物样式类
    getFoodClass(index) {
      const classes = ['food-gold', 'food-cream', 'food-amber', 'food-sand', 'food-brown', 'food-green', 'food-red', 'food-tan']
      return classes[index % classes.length]
    },
    // 根据品类获取档口图片样式类
    getStallFoodClass(category) {
      const classMap = {
        '早餐': 'food-gold',
        '面食': 'food-cream',
        '米饭': 'food-sand',
        '小吃': 'food-brown',
        '饮品': 'food-green'
      }
      return classMap[category] || 'food-amber'
    },
    // 加载档口数据
    async loadStalls() {
      try {
        const res = await getCanteenStallList()
        this.stallList = res.data || []
      } catch (error) {
        console.error('加载档口数据失败:', error)
      }
    },
    // 加载菜品数据
    async loadDishes() {
      try {
        const res = await getDishList()
        this.dishList = res.data || []
        // 加载每个菜品的评价数量
        this.loadReviewCounts()
      } catch (error) {
        console.error('加载菜品数据失败:', error)
      }
    },
    // 加载评价数量
    async loadReviewCounts() {
      try {
        for (const dish of this.dishList) {
          const res = await getDishReviewCount({ dishId: dish.id })
          this.$set(this.reviewCountMap, dish.id, res.data)
        }
      } catch (error) {
        console.error('加载评价数量失败:', error)
      }
    },
    // 获取菜品评价数量
    getReviewCount(dishId) {
      return this.reviewCountMap[dishId] || 0
    },
    // 切换食堂
    switchRestaurant(id) {
      this.currentRestaurantId = id
    },
    // 打开档口详情
    openStallDetail(stall) {
      uni.navigateTo({
        url: `/subpackage_facility/stallDetail/stallDetail?stallId=${stall.id}&restaurantId=${stall.restaurantId}`
      })
    },
    // 打开菜品详情
    openDishDetail(dish) {
      uni.navigateTo({
        url: `/subpackage_facility/dishDetail/dishDetail?dishId=${dish.id}`
      })
    },
    openSearch() {
      this.showSearchPanel = true
    },
    closeSearch() {
      this.showSearchPanel = false
    },
    performSearch() {
      const keyword = (this.searchQuery || '').trim()
      if (!keyword) {
        uni.showToast({ title: '请输入搜索内容', icon: 'none' })
        return
      }
      this.pushHistory(keyword)
      this.showSearchPanel = false
      uni.showToast({ title: `已搜索：${keyword}`, icon: 'none' })
    },
    quickSearch(keyword) {
      this.searchQuery = keyword
      this.performSearch()
    },
    pushHistory(keyword) {
      const next = [keyword, ...this.searchHistory.filter((item) => item !== keyword)]
      this.searchHistory = next.slice(0, 6)
    },
    clearHistory() {
      this.searchHistory = []
    }
  }
}
</script>

<style lang="scss" scoped>
.canteen-page {
  min-height: 100vh;
  background: linear-gradient(180deg, #f7f2e8 0%, #f5efe4 36%, #f6f1e9 100%);
  padding: 24rpx 22rpx 0;
  box-sizing: border-box;
}

.top-bar {
  display: flex;
  align-items: center;
  height: 88rpx;
  margin-bottom: 12rpx;
}

.back-btn {
  display: flex;
  align-items: center;
  gap: 8rpx;
  padding: 12rpx 16rpx;
  border-radius: 32rpx;
  background: rgba(255, 255, 255, 0.7);
}

.back-arrow {
  font-size: 42rpx;
  color: #4a433c;
  line-height: 1;
}

.back-text {
  font-size: 24rpx;
  color: #4a433c;
}

.top-search {
  height: 72rpx;
  border-radius: 24rpx;
  background: rgba(255, 255, 255, 0.82);
  display: flex;
  align-items: center;
  padding: 0 24rpx;
  color: #b8afa3;
  box-shadow: 0 8rpx 24rpx rgba(180, 157, 122, 0.08);
}

.search-icon {
  font-size: 30rpx;
}

.search-placeholder {
  margin-left: 14rpx;
  font-size: 24rpx;
}

.canteen-tabs {
  margin-top: 18rpx;
  display: flex;
  gap: 16rpx;
}

.canteen-tab {
  flex: 1;
  height: 68rpx;
  border-radius: 20rpx;
  background: rgba(255, 255, 255, 0.55);
  display: flex;
  align-items: center;
  justify-content: center;
  color: #a49a8e;
}

.canteen-tab.active {
  background: #ffffff;
  color: #47413b;
  box-shadow: 0 10rpx 24rpx rgba(183, 160, 123, 0.14);
}

.canteen-tab-text {
  font-size: 24rpx;
  font-weight: 600;
}

.page-scroll {
  height: calc(100vh - 150rpx);
  padding-top: 24rpx;
}

.section-block {
  margin-bottom: 28rpx;
}

.section-title {
  display: block;
  font-size: 38rpx;
  font-weight: 700;
  color: #231f1b;
  margin-bottom: 20rpx;
}

.hot-scroll {
  white-space: nowrap;
}

.hot-row {
  display: inline-flex;
  gap: 18rpx;
  padding-right: 12rpx;
}

.hot-card {
  width: 292rpx;
  border-radius: 28rpx;
  overflow: hidden;
  background: rgba(255, 255, 255, 0.92);
  box-shadow: 0 18rpx 36rpx rgba(184, 160, 119, 0.12);
}

.hot-cover {
  height: 160rpx;
  padding: 18rpx;
  position: relative;
}

.hot-tag {
  position: absolute;
  left: 16rpx;
  top: 16rpx;
  padding: 8rpx 16rpx;
  border-radius: 999rpx;
  background: rgba(98, 83, 57, 0.72);
  color: #fff;
  font-size: 20rpx;
}

.hot-bottom {
  padding: 18rpx 18rpx 20rpx;
}

.hot-rate {
  display: block;
  font-size: 24rpx;
  color: #bc9356;
  font-weight: 700;
}

.hot-name {
  display: block;
  margin-top: 14rpx;
  font-size: 30rpx;
  color: #2f2924;
  font-weight: 700;
}

.hot-meta {
  margin-top: 10rpx;
  display: flex;
  justify-content: space-between;
  font-size: 22rpx;
  color: #8d8377;
}

.stall-list {
  display: flex;
  flex-direction: column;
  gap: 24rpx;
}

.stall-card {
  background: rgba(255, 255, 255, 0.95);
  border-radius: 28rpx;
  padding: 22rpx;
  box-shadow: 0 16rpx 34rpx rgba(184, 160, 119, 0.12);
}

.stall-main {
  display: flex;
  gap: 20rpx;
}

.stall-thumb {
  width: 154rpx;
  height: 154rpx;
  border-radius: 24rpx;
  flex-shrink: 0;
}

.stall-content {
  flex: 1;
  min-width: 0;
}

.stall-title-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16rpx;
}

.stall-name {
  font-size: 32rpx;
  font-weight: 700;
  color: #2e2925;
}

.stall-category {
  flex-shrink: 0;
  padding: 8rpx 16rpx;
  border-radius: 16rpx;
  background: #f3e8d5;
  color: #8f7048;
  font-size: 20rpx;
}

.stall-meta-row {
  margin-top: 10rpx;
  display: flex;
  gap: 16rpx;
  flex-wrap: wrap;
  font-size: 22rpx;
  color: #91877b;
}

.recommend-line {
  margin-top: 14rpx;
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.recommend-label {
  font-size: 22rpx;
  color: #b08b57;
  flex-shrink: 0;
}

.recommend-track {
  flex: 1;
  height: 10rpx;
  border-radius: 999rpx;
  background: #efe5d8;
  overflow: hidden;
}

.recommend-fill {
  height: 100%;
  border-radius: 999rpx;
  background: linear-gradient(90deg, #dfc18c 0%, #bf8f4f 100%);
}

.stall-location {
  display: block;
  margin-top: 14rpx;
  font-size: 20rpx;
  color: #b3ab9d;
}

.dish-preview-row {
  margin-top: 18rpx;
  display: flex;
  gap: 16rpx;
}

.dish-preview {
  flex: 1;
  min-width: 0;
}

.dish-preview-thumb {
  width: 100%;
  height: 82rpx;
  border-radius: 16rpx;
}

.dish-preview-name {
  display: block;
  margin-top: 8rpx;
  font-size: 20rpx;
  color: #6f675c;
  text-align: center;
}

.stall-footer {
  margin-top: 18rpx;
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20rpx;
}

.review-snippet {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  gap: 8rpx;
  color: #9d8e7d;
}

.review-icon {
  color: #d6a24f;
  font-size: 22rpx;
}

.review-text {
  font-size: 22rpx;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.review-btn {
  flex-shrink: 0;
  padding: 10rpx 18rpx;
  border-radius: 999rpx;
  background: #f3ece2;
  color: #8d7c68;
  font-size: 20rpx;
}

/* 菜品列表样式 */
.dish-section {
  margin-top: 36rpx;
}

.dish-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr);
  gap: 20rpx;
}

.dish-card {
  background: rgba(255, 255, 255, 0.95);
  border-radius: 24rpx;
  overflow: hidden;
  box-shadow: 0 12rpx 28rpx rgba(184, 160, 119, 0.1);
}

.dish-image {
  width: 100%;
  height: 240rpx;
  background-size: cover;
  background-position: center;
  background-color: #f5f5f5;
}

.dish-info {
  padding: 16rpx;
}

.dish-name-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 10rpx;
}

.dish-name {
  font-size: 28rpx;
  font-weight: 600;
  color: #2e2925;
}

.dish-price {
  font-size: 30rpx;
  font-weight: 700;
  color: #c9a55c;
}

.dish-meta {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin-bottom: 10rpx;
  font-size: 20rpx;
  color: #9a8f80;
}

.dish-rating {
  color: #d4a843;
  font-weight: 600;
}

.dish-sold {
  color: #9a8f80;
}

.dish-taste {
  color: #9a8f80;
}

.dish-category-tag {
  display: inline-block;
  padding: 6rpx 12rpx;
  background: #f5e9d7;
  color: #c9a55c;
  font-size: 18rpx;
  border-radius: 8rpx;
}

.bottom-space {
  height: 36rpx;
}

.search-mask {
  position: fixed;
  inset: 0;
  background: rgba(243, 238, 229, 0.96);
  z-index: 99;
}

.search-panel {
  padding: 24rpx 26rpx 40rpx;
}

.search-panel-top {
  display: flex;
  align-items: center;
  gap: 18rpx;
}

.search-input-wrap {
  flex: 1;
  height: 72rpx;
  border-radius: 22rpx;
  background: #ffffff;
  display: flex;
  align-items: center;
  padding: 0 22rpx;
}

.search-input {
  flex: 1;
  height: 72rpx;
  margin-left: 12rpx;
  font-size: 24rpx;
  color: #3e3730;
}

.search-cancel {
  font-size: 24rpx;
  color: #8d8172;
}

.panel-section {
  margin-top: 42rpx;
}

.panel-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 20rpx;
}

.panel-title {
  font-size: 28rpx;
  font-weight: 700;
  color: #2e2925;
}

.panel-action {
  font-size: 22rpx;
  color: #b4a796;
}

.tag-wrap {
  display: flex;
  flex-wrap: wrap;
  gap: 18rpx;
}

.soft-tag {
  padding: 12rpx 20rpx;
  border-radius: 999rpx;
  background: #fff;
  color: #8c8175;
  font-size: 22rpx;
}

.trend-list {
  display: flex;
  flex-direction: column;
  gap: 18rpx;
}

.trend-item {
  display: flex;
  align-items: center;
  padding: 8rpx 0;
}

.trend-index {
  width: 36rpx;
  font-size: 24rpx;
  color: #c0a06d;
}

.trend-name {
  flex: 1;
  font-size: 24rpx;
  color: #4a433c;
}

.trend-badge {
  padding: 4rpx 12rpx;
  border-radius: 999rpx;
  background: #f5e9d7;
  color: #ce8e55;
  font-size: 18rpx;
}

.food-gold {
  background: radial-gradient(circle at 30% 30%, #ead8b0 0%, #cfaf74 58%, #b58a4d 100%);
}

.food-amber {
  background: radial-gradient(circle at 30% 30%, #f7ddb0 0%, #efc46e 56%, #d69d43 100%);
}

.food-cream {
  background: radial-gradient(circle at 30% 30%, #efe2c3 0%, #dec490 56%, #c39b59 100%);
}

.food-red {
  background: radial-gradient(circle at 30% 30%, #f6b09b 0%, #e56c4d 55%, #b14630 100%);
}

.food-brown {
  background: radial-gradient(circle at 30% 30%, #efc3a8 0%, #d18e60 56%, #9e5a35 100%);
}

.food-sand {
  background: radial-gradient(circle at 30% 30%, #ecd5a9 0%, #d6b173 56%, #b3844a 100%);
}

.food-green {
  background: radial-gradient(circle at 30% 30%, #d9efc3 0%, #9bc26e 56%, #6b9341 100%);
}
</style>
