<template>
  <view class="restaurant-detail-page">
    <nav-bar :title="restaurant.name || '餐厅详情'" fixed placeholder />

    <scroll-view class="detail-scroll" scroll-y :style="{ height: `calc(100vh - ${navBarHeight}px)` }">
      <image class="banner" :src="restaurant.image" mode="aspectFill" />

      <view class="card">
        <view class="header-row">
          <text class="title">{{ restaurant.name }}</text>
          <text class="distance">{{ restaurant.distance }}</text>
        </view>
        <text class="desc">{{ restaurant.description }}</text>
        <text class="meta">营业时间：{{ restaurant.openTime }}</text>
        <text class="meta">位置：{{ restaurant.location }}</text>
      </view>

      <view class="card">
        <text class="section-title">菜系分类</text>
        <scroll-view class="cuisine-scroll" scroll-x :show-scrollbar="false">
          <view class="cuisine-row">
            <view
              v-for="(item, index) in cuisineOptions"
              :key="index"
              class="cuisine-chip"
              :class="{ active: currentCuisine === item }"
              @click="selectCuisine(item)"
            >
              <text class="cuisine-chip-text">{{ item }}</text>
            </view>
          </view>
        </scroll-view>
      </view>

      <view class="card">
        <text class="section-title">档口列表</text>
        <view v-for="(stall, index) in filteredStalls" :key="index" class="stall-item" @click="goToStallDetail(stall)">
          <view class="stall-left">
            <text class="stall-name">{{ stall.name }}</text>
            <text class="stall-type">{{ stall.cuisine }}</text>
            <view class="stall-rating-row">
              <text class="stall-score">评分 {{ stall.score.toFixed(1) }}</text>
              <text class="stall-count">{{ stall.reviewCount }}条评价</text>
            </view>
            <text class="stall-comment">{{ stall.latestComment }}</text>
          </view>
          <view class="stall-right">
            <text class="stall-time">{{ stall.openTime }}</text>
            <text class="stall-arrow">></text>
          </view>
        </view>
        <view v-if="!filteredStalls.length" class="empty-tip">暂无该菜系档口</view>
      </view>
    </scroll-view>
  </view>
</template>

<script>
import NavBar from '@/components/nav-bar/nav-bar.vue'

export default {
  components: { NavBar },
  data() {
    return {
      navBarHeight: 88,
      restaurant: {
        id: '',
        name: '',
        description: '',
        distance: '',
        location: '',
        openTime: '',
        image: '',
        stalls: []
      },
      currentCuisine: '全部'
    }
  },
  computed: {
    cuisineOptions() {
      const result = ['全部']
      const set = new Set()
      ;(this.restaurant.stalls || []).forEach((stall) => {
        if (stall.cuisine && !set.has(stall.cuisine)) {
          set.add(stall.cuisine)
          result.push(stall.cuisine)
        }
      })
      return result
    },
    filteredStalls() {
      if (this.currentCuisine === '全部') {
        return this.restaurant.stalls || []
      }
      return (this.restaurant.stalls || []).filter((stall) => stall.cuisine === this.currentCuisine)
    }
  },
  onLoad(options) {
    const sys = uni.getSystemInfoSync()
    this.navBarHeight = (sys.statusBarHeight || 0) + 44
    this.loadRestaurant(options.id)
  },
  methods: {
    loadRestaurant(id) {
      const mockData = {
        '3': {
          id: '3',
          name: '第一食堂',
          description: '学生餐厅、教工餐厅',
          distance: '180m',
          location: '生活区中轴线北侧',
          openTime: '06:30 - 21:00',
          image: 'https://picsum.photos/seed/canteen1detail/900/500',
          stalls: [
            { id: '301', name: '家常菜档口', cuisine: '中式快餐', openTime: '10:30 - 13:30 / 16:30 - 19:30', score: 4.7, reviewCount: 128, latestComment: '菜量足，出餐快，午高峰排队稍长。' },
            { id: '302', name: '面食档口', cuisine: '面条/饺子', openTime: '07:00 - 20:30', score: 4.5, reviewCount: 96, latestComment: '牛肉面口味稳定，汤底不错。' },
            { id: '303', name: '轻食档口', cuisine: '沙拉/简餐', openTime: '09:00 - 19:00', score: 4.3, reviewCount: 67, latestComment: '鸡胸肉套餐热量标识清晰。' }
          ]
        },
        '4': {
          id: '4',
          name: '第二食堂',
          description: '特色风味餐厅',
          distance: '350m',
          location: '图书馆东侧',
          openTime: '07:00 - 22:00',
          image: 'https://picsum.photos/seed/canteen2detail/900/500',
          stalls: [
            { id: '401', name: '川湘风味', cuisine: '川菜/湘菜', openTime: '10:30 - 20:30', score: 4.6, reviewCount: 142, latestComment: '辣度可选，水煮鱼很受欢迎。' },
            { id: '402', name: '烤肉饭档口', cuisine: '日韩料理', openTime: '11:00 - 21:00', score: 4.4, reviewCount: 85, latestComment: '酱汁偏甜，分量够。' },
            { id: '403', name: '夜宵档口', cuisine: '烧烤/小吃', openTime: '17:00 - 22:00', score: 4.2, reviewCount: 53, latestComment: '烤串种类多，晚间人气高。' }
          ]
        }
      }

      this.restaurant = mockData[id] || {
        id: id || '',
        name: '餐厅详情',
        description: '暂无数据',
        distance: '--',
        location: '暂无',
        openTime: '暂无',
        image: 'https://picsum.photos/seed/canteendefault/900/500',
        stalls: []
      }
      this.currentCuisine = '全部'
    },
    selectCuisine(cuisine) {
      this.currentCuisine = cuisine
    },
    goToStallDetail(stall) {
      uni.navigateTo({
        url: `/subpackage_facility/stallDetail/stallDetail?stallId=${stall.id}&restaurantId=${this.restaurant.id}`
      })
    }
  }
}
</script>

<style lang="scss" scoped>
.restaurant-detail-page {
  min-height: 100vh;
  background: #f6f7fb;
}

.detail-scroll {
  height: 100vh;
}

.banner {
  width: 100%;
  height: 360rpx;
  background: #e9ebf1;
}

.card {
  margin: 24rpx;
  padding: 28rpx;
  border-radius: 20rpx;
  background: #fff;
}

.header-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16rpx;
}

.title {
  font-size: 38rpx;
  font-weight: 700;
  color: #1f2329;
}

.distance {
  font-size: 26rpx;
  color: #86909c;
}

.desc {
  margin-top: 14rpx;
  font-size: 28rpx;
  color: #4e5969;
  display: block;
}

.meta {
  margin-top: 10rpx;
  font-size: 24rpx;
  color: #86909c;
  display: block;
}

.section-title {
  font-size: 30rpx;
  font-weight: 600;
  color: #1f2329;
  margin-bottom: 20rpx;
  display: block;
}

.cuisine-scroll {
  width: 100%;
}

.cuisine-row {
  display: inline-flex;
  padding-right: 12rpx;
}

.cuisine-chip {
  height: 56rpx;
  border-radius: 999rpx;
  background: #f2f3f5;
  padding: 0 24rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  margin-right: 16rpx;
}

.cuisine-chip.active {
  background: #165dff;
}

.cuisine-chip-text {
  font-size: 24rpx;
  color: #4e5969;
}

.cuisine-chip.active .cuisine-chip-text {
  color: #fff;
}

.stall-item {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  padding: 18rpx 0;
  border-bottom: 1rpx solid #f2f3f5;
}

.stall-item:last-child {
  border-bottom: none;
}

.stall-left {
  flex: 1;
  min-width: 0;
}

.stall-name {
  display: block;
  font-size: 28rpx;
  color: #1f2329;
  font-weight: 500;
}

.stall-type {
  display: block;
  margin-top: 6rpx;
  font-size: 24rpx;
  color: #86909c;
}

.stall-rating-row {
  margin-top: 8rpx;
  display: flex;
  align-items: center;
  gap: 12rpx;
}

.stall-score {
  font-size: 24rpx;
  color: #ff7d00;
  font-weight: 500;
}

.stall-count {
  font-size: 22rpx;
  color: #86909c;
}

.stall-comment {
  display: block;
  margin-top: 8rpx;
  font-size: 22rpx;
  color: #4e5969;
}

.stall-right {
  margin-left: 16rpx;
  display: flex;
  flex-direction: column;
  align-items: flex-end;
}

.stall-time {
  font-size: 22rpx;
  color: #165dff;
}

.stall-arrow {
  margin-top: 12rpx;
  color: #c9cdd4;
  font-size: 28rpx;
  line-height: 1;
}

.empty-tip {
  padding: 20rpx 0 6rpx;
  text-align: center;
  font-size: 24rpx;
  color: #86909c;
}
</style>
