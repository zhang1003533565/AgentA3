<template>
  <view class="activity-container">
    <!-- 搜索栏 -->
    <view class="search-bar">
      <view class="search-input">
        <text class="iconfont icon-search"></text>
        <input 
          type="text" 
          v-model="searchKeyword" 
          placeholder="搜索活动名称"
          @confirm="handleSearch"
        />
      </view>
    </view>

    <!-- 分类标签 -->
    <scroll-view class="category-scroll" scroll-x>
      <view class="category-list">
        <view 
          v-for="(item, index) in categories" 
          :key="index"
          class="category-item"
          :class="{ active: currentCategory === item.id }"
          @click="selectCategory(item.id)"
        >
          {{ item.name }}
        </view>
      </view>
    </scroll-view>

    <!-- 活动列表 -->
    <scroll-view 
      class="activity-list" 
      scroll-y 
      @scrolltolower="loadMore"
      refresher-enabled
      :refresher-triggered="isRefreshing"
      @refresherrefresh="onRefresh"
    >
      <view 
        v-for="(item, index) in activityList" 
        :key="index"
        class="activity-item"
        @click="goToDetail(item.id)"
      >
        <image class="activity-cover" :src="item.coverImage || '/static/logo.png'" mode="aspectFill" />
        <view class="activity-info">
          <view class="activity-title">{{ item.title }}</view>
          <view class="activity-meta">
            <text class="meta-item">
              <text class="iconfont icon-time"></text>
              {{ formatTime(item.startTime) }}
            </text>
            <text class="meta-item">
              <text class="iconfont icon-location"></text>
              {{ item.location }}
            </text>
          </view>
          <view class="activity-footer">
            <view class="activity-status" :class="getStatusClass(item.status)">
              {{ getStatusText(item.status) }}
            </view>
            <view class="activity-people">
              <text class="current">{{ item.currentPeople }}</text>
              <text class="max">/{{ item.maxPeople }}人</text>
            </view>
          </view>
        </view>
      </view>

      <!-- 加载更多 -->
      <view class="load-more">
        <text v-if="loading">加载中...</text>
        <text v-else-if="noMore">没有更多了</text>
        <text v-else-if="activityList.length === 0">暂无活动</text>
      </view>
    </scroll-view>
  </view>
</template>

<script>
export default {
  data() {
    return {
      searchKeyword: '',
      currentCategory: 0,
      categories: [
        { id: 0, name: '全部' },
        { id: 1, name: '讲座' },
        { id: 2, name: '比赛' },
        { id: 3, name: '社团' },
        { id: 4, name: '志愿' },
        { id: 5, name: '体育' }
      ],
      activityList: [],
      page: 1,
      pageSize: 10,
      loading: false,
      noMore: false,
      isRefreshing: false
    }
  },
  onLoad() {
    this.loadActivityList()
  },
  methods: {
    // 加载活动列表
    async loadActivityList() {
      if (this.loading || this.noMore) return
      
      this.loading = true
      
      // 模拟数据，后续替换为真实接口
      setTimeout(() => {
        const mockData = [
          {
            id: 1,
            title: '校园创新创业大赛',
            coverImage: '',
            startTime: '2026-03-20 14:00',
            location: '学术报告厅',
            status: 'SIGNUP',
            currentPeople: 45,
            maxPeople: 100
          },
          {
            id: 2,
            title: '春季篮球友谊赛',
            coverImage: '',
            startTime: '2026-03-22 16:00',
            location: '体育馆',
            status: 'SIGNUP',
            currentPeople: 32,
            maxPeople: 50
          },
          {
            id: 3,
            title: '人工智能前沿讲座',
            coverImage: '',
            startTime: '2026-03-18 19:00',
            location: '图书馆报告厅',
            status: 'ONGOING',
            currentPeople: 120,
            maxPeople: 200
          }
        ]
        
        if (this.page === 1) {
          this.activityList = mockData
        } else {
          this.activityList = [...this.activityList, ...mockData]
        }
        
        if (this.page >= 3) {
          this.noMore = true
        }
        
        this.loading = false
        this.isRefreshing = false
      }, 500)
    },
    
    // 搜索
    handleSearch() {
      this.page = 1
      this.noMore = false
      this.loadActivityList()
    },
    
    // 选择分类
    selectCategory(categoryId) {
      this.currentCategory = categoryId
      this.page = 1
      this.noMore = false
      this.loadActivityList()
    },
    
    // 加载更多
    loadMore() {
      if (!this.loading && !this.noMore) {
        this.page++
        this.loadActivityList()
      }
    },
    
    // 下拉刷新
    onRefresh() {
      this.isRefreshing = true
      this.page = 1
      this.noMore = false
      this.loadActivityList()
    },
    
    // 跳转到详情
    goToDetail(id) {
      uni.navigateTo({
        url: `/subpackage_activity/activityDetail/activityDetail?id=${id}`
      })
    },
    
    // 格式化时间
    formatTime(time) {
      if (!time) return ''
      return time.substring(5, 16).replace(' ', ' ')
    },
    
    // 获取状态样式
    getStatusClass(status) {
      const map = {
        'DRAFT': 'status-draft',
        'PENDING': 'status-pending',
        'SIGNUP': 'status-signup',
        'SIGNUP_END': 'status-end',
        'ONGOING': 'status-ongoing',
        'ENDED': 'status-ended',
        'CANCELLED': 'status-cancelled'
      }
      return map[status] || 'status-default'
    },
    
    // 获取状态文本
    getStatusText(status) {
      const map = {
        'DRAFT': '草稿',
        'PENDING': '待审核',
        'SIGNUP': '报名中',
        'SIGNUP_END': '报名结束',
        'ONGOING': '进行中',
        'ENDED': '已结束',
        'CANCELLED': '已取消'
      }
      return map[status] || '未知'
    }
  }
}
</script>

<style lang="scss">
.activity-container {
  min-height: 100vh;
  background-color: #f5f5f5;
}

.search-bar {
  padding: 20rpx 30rpx;
  background-color: #fff;
  
  .search-input {
    display: flex;
    align-items: center;
    height: 72rpx;
    background-color: #f5f5f5;
    border-radius: 36rpx;
    padding: 0 30rpx;
    
    .icon-search {
      font-size: 32rpx;
      color: #999;
      margin-right: 16rpx;
    }
    
    input {
      flex: 1;
      font-size: 28rpx;
      color: #333;
    }
  }
}

.category-scroll {
  background-color: #fff;
  padding: 0 20rpx 20rpx;
  white-space: nowrap;
  
  .category-list {
    display: flex;
  }
  
  .category-item {
    display: inline-block;
    padding: 12rpx 32rpx;
    margin-right: 16rpx;
    font-size: 28rpx;
    color: #666;
    background-color: #f5f5f5;
    border-radius: 32rpx;
    
    &.active {
      color: #fff;
      background-color: #4A90D9;
    }
  }
}

.activity-list {
  height: calc(100vh - 200rpx);
  padding: 20rpx;
  
  .activity-item {
    display: flex;
    background-color: #fff;
    border-radius: 16rpx;
    padding: 20rpx;
    margin-bottom: 20rpx;
    
    .activity-cover {
      width: 200rpx;
      height: 150rpx;
      border-radius: 12rpx;
      margin-right: 20rpx;
      background-color: #f0f0f0;
    }
    
    .activity-info {
      flex: 1;
      display: flex;
      flex-direction: column;
      justify-content: space-between;
      
      .activity-title {
        font-size: 32rpx;
        font-weight: bold;
        color: #333;
        line-height: 1.4;
      }
      
      .activity-meta {
        display: flex;
        flex-direction: column;
        margin-top: 12rpx;
        
        .meta-item {
          font-size: 24rpx;
          color: #666;
          margin-bottom: 8rpx;
          
          .iconfont {
            margin-right: 8rpx;
          }
        }
      }
      
      .activity-footer {
        display: flex;
        justify-content: space-between;
        align-items: center;
        margin-top: 12rpx;
        
        .activity-status {
          padding: 6rpx 16rpx;
          border-radius: 8rpx;
          font-size: 22rpx;
          
          &.status-signup {
            color: #4A90D9;
            background-color: rgba(74, 144, 217, 0.1);
          }
          
          &.status-ongoing {
            color: #52c41a;
            background-color: rgba(82, 196, 26, 0.1);
          }
          
          &.status-ended {
            color: #999;
            background-color: #f5f5f5;
          }
          
          &.status-cancelled {
            color: #ff4d4f;
            background-color: rgba(255, 77, 79, 0.1);
          }
        }
        
        .activity-people {
          font-size: 24rpx;
          
          .current {
            color: #4A90D9;
            font-weight: bold;
          }
          
          .max {
            color: #999;
          }
        }
      }
    }
  }
  
  .load-more {
    text-align: center;
    padding: 30rpx;
    color: #999;
    font-size: 26rpx;
  }
}
</style>
