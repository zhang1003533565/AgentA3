<template>
  <view class="activity-container">
    <nav-bar title="活动发现" :showBack="false" />

    <!-- 搜索框 + 右侧「我的活动」 -->
    <view class="search-bar">
      <view class="search-input">
        <text class="iconfont icon-search"></text>
        <input 
          type="text" 
          v-model="searchKeyword" 
          placeholder="搜索活动名称"
          placeholder-class="search-placeholder"
          @confirm="handleSearch"
        />
      </view>
      <view class="btn-my-activity" @click="goToMyActivity">我的活动</view>
    </view>

    <!-- 胶囊形状分类 Tab：选中品牌蓝底，未选中浅灰 -->
    <view class="category-wrap">
      <scroll-view class="category-scroll" scroll-x :show-scrollbar="false">
        <view class="category-list">
          <view 
            v-for="(item, index) in categories" 
            :key="index"
            class="category-item category-pill"
            :class="{ active: currentCategory === item.id }"
            @click="selectCategory(item.id)"
          >
            <text class="category-text">{{ item.name }}</text>
          </view>
        </view>
      </scroll-view>
    </view>

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
        :key="item.id || index"
        class="activity-item"
        @click="goToDetail(item.id)"
      >
        <view class="activity-cover-wrap">
          <image class="activity-cover" :src="item.coverImage || defaultCover" mode="aspectFill" />
          <view class="activity-status-pill" :class="getStatusClass(item.status)">{{ getStatusText(item.status) }}</view>
        </view>
        <view class="activity-body">
          <view class="activity-title">{{ item.title }}</view>
          <view class="activity-meta">
            <text class="meta-item">
              <text class="iconfont icon-time meta-icon"></text>
              {{ formatTime(item.startTime) }}
            </text>
            <text class="meta-item">
              <text class="iconfont icon-location meta-icon"></text>
              {{ item.location }}
            </text>
          </view>
          <view class="activity-tag-row">
            <view class="activity-people-wrap">
              <text class="activity-people-text">{{ item.currentPeople }}/{{ item.maxPeople }} 人</text>
              <view class="activity-progress-bar">
                <view 
                  class="activity-progress-fill" 
                  :style="{ width: progressPercent(item) }"
                ></view>
              </view>
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
    <custom-tab-bar current="activity" />
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
      defaultCover: 'https://picsum.photos/seed/campus/800/450',
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
            coverImage: 'https://picsum.photos/seed/activity1/800/450',
            startTime: '2026-03-20 14:00',
            location: '学术报告厅',
            status: 'SIGNUP',
            currentPeople: 45,
            maxPeople: 100
          },
          {
            id: 2,
            title: '春季篮球友谊赛',
            coverImage: 'https://picsum.photos/seed/activity2/800/450',
            startTime: '2026-03-22 16:00',
            location: '体育馆',
            status: 'SIGNUP',
            currentPeople: 32,
            maxPeople: 50
          },
          {
            id: 3,
            title: '人工智能前沿讲座',
            coverImage: 'https://picsum.photos/seed/activity3/800/450',
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
    // 进入「我的活动」（待参加/报名记录/已结束）
    goToMyActivity() {
      uni.reLaunch({ url: '/pages/activity/activity' })
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
    },
    // 报名进度百分比（已报名/总人数），用于进度条宽度
    progressPercent(item) {
      const max = item.maxPeople || 1
      const pct = Math.min(100, Math.round((item.currentPeople / max) * 100))
      return pct + '%'
    }
  }
}
</script>

<style lang="scss">
.activity-container {
  min-height: 100vh;
  background-color: #F7F7F9;
  padding-bottom: 120rpx;
}

.search-bar {
  display: flex;
  align-items: center;
  gap: 24rpx;
  padding: 0 $spacing-lg-rpx $spacing-block;
  background-color: #FFFFFF;
}
.search-input {
  flex: 1;
  min-width: 0;
  display: flex;
  align-items: center;
  height: 72rpx;
  background-color: #F7F7F9;
  border-radius: 16rpx;
  padding: 0 $spacing-base-rpx;
  border: none;
}
.btn-my-activity {
  flex-shrink: 0;
  padding: 0 28rpx;
  height: 72rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 28rpx;
  font-weight: 600;
  color: $color-primary;
}
.search-input .icon-search {
  font-size: 32rpx;
  color: #4A4A4A;
  margin-right: $spacing-base-rpx;
}
.search-input input {
  flex: 1;
  font-size: 28rpx;   /* 14px 三级 */
  color: #4A4A4A;
  font-weight: 400;
}
.search-placeholder {
  color: #8E8E93;
}

/* 胶囊 Tab：选中品牌蓝底，未选中浅灰 */
.category-wrap {
  background-color: #FFFFFF;
  margin-bottom: $spacing-block;
  border-bottom: 1px solid #EEEEEE;
}
.category-scroll {
  white-space: nowrap;
}
.category-list {
  display: flex;
  align-items: center;
  padding: 0 $spacing-lg-rpx;
  min-height: 88rpx;
  gap: 24rpx;
}
.category-item.category-pill {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 16rpx 32rpx;
  border-radius: 999rpx;
  flex-shrink: 0;
  background-color: #F2F2F2;
}
.category-item.category-pill.active {
  background-color: $color-primary;
}
.category-text {
  font-size: 28rpx;
  font-weight: 400;
  color: #8E8E93;
}
.category-item.active .category-text {
  font-weight: 500;
  color: #FFFFFF;
}

/* 活动列表：通栏、分隔线清晰，保证可滚动、内容不裁切 */
.activity-list {
  height: calc(100vh - 380rpx);
  padding: 0;
  padding-top: 24rpx;
  padding-bottom: 48rpx;
  background-color: #FFFFFF;
  box-sizing: border-box;
}

.activity-item {
  padding: 0 0 32rpx;
  margin-bottom: 0;
  border-bottom: 1px solid #EEEEEE;
}
.activity-item:last-child {
  border-bottom: none;
}

/* 通栏列表项：4px 步进间距，图片与标题 12px，详情与标题 4px */
.activity-cover-wrap {
  position: relative;
  width: 100%;
  height: 422rpx;
  border-radius: 8rpx;
  margin-bottom: 24rpx;   /* 12px 图与标题间距 */
  background-color: #F7F7F9;
  overflow: hidden;
}
.activity-cover {
  display: block;
  width: 100%;
  height: 422rpx;
}
/* Pill 状态标签：悬浮图片左上角，极浅底+深色字 */
.activity-status-pill {
  position: absolute;
  left: 24rpx;
  top: 24rpx;
  padding: 8rpx 20rpx;
  border-radius: 999rpx;
  font-size: 22rpx;
  font-weight: 500;
  background-color: rgba(255, 255, 255, 0.92);
  color: #1D1D1F;
}
.activity-status-pill.status-signup {
  background-color: rgba(92, 122, 153, 0.12);
  color: #4A6278;
}
.activity-status-pill.status-ongoing {
  background-color: rgba(107, 155, 122, 0.12);
  color: #4A6B57;
}
.activity-status-pill.status-ended,
.activity-status-pill.status-end,
.activity-status-pill.status-draft,
.activity-status-pill.status-pending,
.activity-status-pill.status-default {
  background-color: rgba(142, 142, 147, 0.12);
  color: #5C5C60;
}
.activity-status-pill.status-cancelled {
  background-color: rgba(166, 123, 123, 0.15);
  color: #A67B7B;
}

.activity-body {
  padding: 0 $spacing-lg-rpx;
}

.activity-title {
  font-size: 32rpx;
  font-weight: 600;
  color: #1D1D1F;
  line-height: 1.4;
  margin-bottom: 8rpx;   /* 4px 标题与详情间距 */
}

.activity-meta {
  display: flex;
  flex-direction: column;
  margin-top: 0;
  gap: 4rpx;            /* 4px 步进 */
}
.meta-item {
  font-size: 24rpx;
  font-weight: 400;
  color: #4A4A4A;
}
.meta-icon {
  margin-right: 8rpx;
  font-size: 22rpx;
  opacity: 0.9;
}

.activity-tag-row {
  display: flex;
  align-items: center;
  justify-content: flex-end;
  margin-top: 8rpx;
}
.activity-people-wrap {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  min-width: 120rpx;
}
.activity-people-text {
  font-size: 22rpx; /* 11px */
  color: #8E8E93;
  margin-bottom: 6rpx;
}
.activity-progress-bar {
  width: 100%;
  height: 6rpx;
  background-color: #EEEEEE;
  border-radius: 3rpx;
  overflow: hidden;
}
.activity-progress-fill {
  height: 100%;
  background-color: $color-primary;
  border-radius: 3rpx;
  transition: width 0.2s ease;
}

.activity-list .load-more {
  text-align: center;
  padding: $spacing-lg-rpx;
  color: $color-text-secondary;
  font-size: $font-size-body-rpx;
}
</style>
