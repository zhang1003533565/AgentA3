<template>
  <view class="map-container">
    <nav-bar title="校园地图" :showBack="false" />
    
    <!-- 搜索栏 -->
    <view class="search-bar">
      <view class="search-input">
        <text class="search-icon">🔍</text>
        <input 
          type="text" 
          v-model="searchKeyword" 
          placeholder="搜索地点..."
          @confirm="handleSearch"
        />
      </view>
    </view>

    <!-- 地图区域 -->
    <view class="map-area">
      <image 
        class="map-image" 
        src="https://picsum.photos/seed/campusmap/800/600" 
        mode="aspectFit"
      />
      <view class="map-placeholder">
        <text class="placeholder-text">校园地图加载中...</text>
        <text class="placeholder-tip">实际项目中可接入腾讯/高德地图SDK</text>
      </view>
    </view>

    <!-- 地点分类 -->
    <view class="category-section">
      <scroll-view class="category-scroll" scroll-x>
        <view class="category-list">
          <view 
            v-for="(item, index) in categories" 
            :key="index"
            class="category-item"
            :class="{ active: currentCategory === item.id }"
            @click="selectCategory(item.id)"
          >
            <text class="category-icon">{{ item.icon }}</text>
            <text class="category-name">{{ item.name }}</text>
          </view>
        </view>
      </scroll-view>
    </view>

    <!-- 地点列表 -->
    <view class="location-list">
      <view 
        v-for="(item, index) in locationList" 
        :key="index"
        class="location-item"
        @click="goToLocation(item)"
      >
        <view class="location-icon">{{ item.icon }}</view>
        <view class="location-info">
          <text class="location-name">{{ item.name }}</text>
          <text class="location-desc">{{ item.description }}</text>
        </view>
        <view class="location-distance">
          <text class="distance-text">{{ item.distance }}</text>
          <text class="nav-btn">导航</text>
        </view>
      </view>
    </view>

    <custom-tab-bar current="map" />
  </view>
</template>

<script>
import CustomTabBar from '@/components/custom-tab-bar/custom-tab-bar.vue'
import NavBar from '@/components/nav-bar/nav-bar.vue'

export default {
  components: { CustomTabBar, NavBar },
  data() {
    return {
      searchKeyword: '',
      currentCategory: 0,
      categories: [
        { id: 0, name: '全部', icon: '📍' },
        { id: 1, name: '教学楼', icon: '🏫' },
        { id: 2, name: '食堂', icon: '🍚' },
        { id: 3, name: '图书馆', icon: '📚' },
        { id: 4, name: '宿舍', icon: '🏠' },
        { id: 5, name: '运动场', icon: '⚽' },
        { id: 6, name: '其他', icon: '🏛️' }
      ],
      locationList: [
        { id: 1, name: '教学楼A栋', icon: '🏫', description: '计算机学院、软件学院', distance: '320m', category: 1 },
        { id: 2, name: '教学楼B栋', icon: '🏫', description: '经济管理学院、外国语学院', distance: '450m', category: 1 },
        { id: 3, name: '第一食堂', icon: '🍚', description: '学生餐厅、教工餐厅', distance: '180m', category: 2 },
        { id: 4, name: '第二食堂', icon: '🍚', description: '特色风味餐厅', distance: '350m', category: 2 },
        { id: 5, name: '图书馆', icon: '📚', description: '藏书200万册，自习室开放', distance: '280m', category: 3 },
        { id: 6, name: '学生宿舍1号楼', icon: '🏠', description: '男生宿舍', distance: '520m', category: 4 },
        { id: 7, name: '体育馆', icon: '🏟️', description: '篮球、羽毛球、游泳馆', distance: '600m', category: 5 },
        { id: 8, name: '田径场', icon: '⚽', description: '400米标准跑道', distance: '550m', category: 5 }
      ]
    }
  },
  methods: {
    handleSearch() {
      // TODO: 搜索地点
    },
    selectCategory(categoryId) {
      this.currentCategory = categoryId
      // TODO: 筛选地点
    },
    goToLocation(item) {
      uni.showModal({
        title: item.name,
        content: `距离: ${item.distance}\n${item.description}`,
        confirmText: '开始导航',
        success: (res) => {
          if (res.confirm) {
            uni.showToast({ title: '导航功能开发中', icon: 'none' })
          }
        }
      })
    }
  }
}
</script>

<style lang="scss" scoped>
.map-container {
  min-height: 100vh;
  background-color: #F7F7F9;
  padding-bottom: 120rpx;
}

.search-bar {
  padding: 20rpx 30rpx;
  background-color: #FFFFFF;
  
  .search-input {
    display: flex;
    align-items: center;
    height: 72rpx;
    background-color: #F5F5F7;
    border-radius: 36rpx;
    padding: 0 30rpx;
    
    .search-icon {
      font-size: 28rpx;
      margin-right: 16rpx;
    }
    
    input {
      flex: 1;
      font-size: 28rpx;
      color: #333;
    }
  }
}

.map-area {
  position: relative;
  width: 100%;
  height: 400rpx;
  background-color: #E8E8E8;
  
  .map-image {
    width: 100%;
    height: 100%;
  }
  
  .map-placeholder {
    position: absolute;
    top: 0;
    left: 0;
    right: 0;
    bottom: 0;
    display: flex;
    flex-direction: column;
    align-items: center;
    justify-content: center;
    background-color: rgba(255, 255, 255, 0.9);
    
    .placeholder-text {
      font-size: 32rpx;
      color: #1D1D1F;
      font-weight: 600;
    }
    
    .placeholder-tip {
      font-size: 24rpx;
      color: #8E8E93;
      margin-top: 12rpx;
    }
  }
}

.category-section {
  background-color: #FFFFFF;
  padding: 20rpx 0;
  border-bottom: 1rpx solid #EEEEEE;
  
  .category-scroll {
    white-space: nowrap;
  }
  
  .category-list {
    display: flex;
    padding: 0 20rpx;
  }
  
  .category-item {
    display: flex;
    flex-direction: column;
    align-items: center;
    padding: 16rpx 28rpx;
    margin-right: 16rpx;
    border-radius: 16rpx;
    background-color: #F5F5F7;
    
    &.active {
      background-color: rgba(92, 122, 153, 0.15);
    }
    
    .category-icon {
      font-size: 36rpx;
      margin-bottom: 8rpx;
    }
    
    .category-name {
      font-size: 24rpx;
      color: #4A4A4A;
    }
  }
}

.location-list {
  padding: 20rpx;
  
  .location-item {
    display: flex;
    align-items: center;
    padding: 24rpx;
    background-color: #FFFFFF;
    border-radius: 16rpx;
    margin-bottom: 16rpx;
    
    .location-icon {
      width: 72rpx;
      height: 72rpx;
      display: flex;
      align-items: center;
      justify-content: center;
      font-size: 36rpx;
      background-color: #F5F5F7;
      border-radius: 50%;
      margin-right: 20rpx;
    }
    
    .location-info {
      flex: 1;
      min-width: 0;
      
      .location-name {
        display: block;
        font-size: 30rpx;
        font-weight: 600;
        color: #1D1D1F;
        margin-bottom: 6rpx;
      }
      
      .location-desc {
        font-size: 24rpx;
        color: #8E8E93;
      }
    }
    
    .location-distance {
      display: flex;
      flex-direction: column;
      align-items: flex-end;
      
      .distance-text {
        font-size: 24rpx;
        color: #8E8E93;
        margin-bottom: 8rpx;
      }
      
      .nav-btn {
        font-size: 24rpx;
        color: #5C7A99;
        padding: 8rpx 20rpx;
        background-color: rgba(92, 122, 153, 0.1);
        border-radius: 20rpx;
      }
    }
  }
}
</style>
