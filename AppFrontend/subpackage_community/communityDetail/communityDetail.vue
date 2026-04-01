<template>
  <view class="detail-container">
    <nav-bar title="活动详情" :showBack="true" fixed placeholder />
    
    <scroll-view class="detail-content" scroll-y :style="{ height: `calc(100vh - ${navBarHeight}px)` }">
      <!-- 活动封面 -->
      <image class="detail-cover" :src="activity.cover || defaultCover" mode="aspectFill" />
      
      <!-- 活动信息 -->
      <view class="info-section">
        <view class="detail-header">
          <text class="detail-title">{{ activity.title }}</text>
          <view class="detail-tag" :class="activity.status">{{ getStatusText(activity.status) }}</view>
        </view>
        
        <view class="detail-meta">
          <view class="meta-row">
            <image class="meta-icon" src="/static/icons/line/calendar.svg" mode="aspectFit" />
            <text class="meta-label">活动时间</text>
            <text class="meta-value">{{ formatDate(activity.startTime) }}</text>
          </view>
          <view class="meta-row">
            <image class="meta-icon" src="/static/icons/line/map.svg" mode="aspectFit" />
            <text class="meta-label">活动地点</text>
            <text class="meta-value">{{ activity.location }}</text>
          </view>
          <view class="meta-row">
            <image class="meta-icon" src="/static/icons/line/user.svg" mode="aspectFit" />
            <text class="meta-label">报名人数</text>
            <text class="meta-value">{{ activity.currentPeople }}/{{ activity.maxPeople }}人</text>
          </view>
        </view>
        
        <!-- 主办方 -->
        <view class="organizer-section">
          <image class="organizer-avatar" :src="activity.organizerAvatar || '/static/logo.png'" mode="aspectFill" />
          <view class="organizer-info">
            <text class="organizer-name">{{ activity.organizer }}</text>
            <text class="organizer-label">活动主办方</text>
          </view>
        </view>
      </view>
      
      <!-- 活动详情 -->
      <view class="desc-section">
        <text class="section-title">活动详情</text>
        <text class="desc-content">{{ activity.description || '暂无活动详情' }}</text>
      </view>
      
      <!-- 底部安全区域 -->
      <view class="safe-area"></view>
    </scroll-view>
    
    <!-- 底部操作栏 -->
    <view class="bottom-bar">
      <view class="bar-left">
        <view class="action-btn" @click="shareActivity">
          <image class="action-icon" src="/static/icons/line/share.svg" mode="aspectFit" />
          <text class="action-text">分享</text>
        </view>
        <view class="action-btn" @click="collectActivity">
          <image class="action-icon" :src="isCollected ? '/static/icons/line/star-fill.svg' : '/static/icons/line/star.svg'" mode="aspectFit" />
          <text class="action-text">收藏</text>
        </view>
      </view>
      <view class="bar-right">
        <view 
          class="join-btn" 
          :class="{ disabled: !canJoin }"
          @click="handleJoin"
        >
          {{ joinBtnText }}
        </view>
      </view>
    </view>
  </view>
</template>

<script>
import NavBar from '@/components/nav-bar/nav-bar.vue'

export default {
  components: { NavBar },
  data() {
    return {
      navBarHeight: 88,
      activityId: null,
      defaultCover: 'https://picsum.photos/seed/community/800/450',
      activity: {},
      isCollected: false,
      isJoined: false
    }
  },
  onLoad(options) {
    const sys = uni.getSystemInfoSync()
    this.navBarHeight = (sys.statusBarHeight || 0) + 44
    this.activityId = options.id
    this.loadActivityDetail()
  },
  computed: {
    canJoin() {
      return this.activity.status === 'signup' && !this.isJoined && this.activity.currentPeople < this.activity.maxPeople
    },
    joinBtnText() {
      if (this.isJoined) return '已报名'
      if (this.activity.status === 'ended') return '已结束'
      if (this.activity.status === 'full') return '已满员'
      if (this.activity.currentPeople >= this.activity.maxPeople) return '已满员'
      return '立即报名'
    }
  },
  methods: {
    loadActivityDetail() {
      // 模拟加载详情
      this.activity = {
        id: this.activityId,
        title: '社区义务植树活动',
        cover: '',
        status: 'signup',
        startTime: '2024-04-15 09:00',
        location: '社区公园东门广场',
        currentPeople: 45,
        maxPeople: 50,
        organizer: '社区居委会',
        organizerAvatar: '',
        description: '为美化社区环境，增强居民环保意识，社区居委会特组织本次义务植树活动。欢迎广大居民积极参与，共同为社区增添绿色！\n\n活动内容：\n1. 植树知识讲解\n2. 分组植树实践\n3. 合影留念\n\n注意事项：\n- 请穿着舒适的服装和鞋子\n- 活动提供植树工具\n- 建议自带饮用水'
      }
    },
    
    handleJoin() {
      if (!this.canJoin) return
      
      uni.showModal({
        title: '确认报名',
        content: '确定要报名参加该活动吗？',
        success: (res) => {
          if (res.confirm) {
            this.isJoined = true
            this.activity.currentPeople++
            uni.showToast({
              title: '报名成功',
              icon: 'success'
            })
          }
        }
      })
    },
    
    collectActivity() {
      this.isCollected = !this.isCollected
      uni.showToast({
        title: this.isCollected ? '收藏成功' : '取消收藏',
        icon: 'none'
      })
    },
    
    shareActivity() {
      uni.showShareMenu({
        withShareTicket: true
      })
    },
    
    formatDate(dateStr) {
      if (!dateStr) return ''
      const date = new Date(dateStr)
      return `${date.getMonth() + 1}月${date.getDate()}日 ${date.getHours()}:${String(date.getMinutes()).padStart(2, '0')}`
    },
    
    getStatusText(status) {
      const map = {
        'signup': '报名中',
        'ongoing': '进行中',
        'ended': '已结束',
        'full': '已满员'
      }
      return map[status] || '未知'
    }
  }
}
</script>

<style lang="scss">
.detail-container {
  min-height: 100vh;
  background-color: #F7F7F9;
  padding-bottom: 120rpx;
}

.detail-content {
  min-height: 0;
}

.detail-cover {
  width: 100%;
  height: 400rpx;
}

.info-section {
  background-color: #FFFFFF;
  padding: 32rpx;
  margin-bottom: 24rpx;
}

.detail-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 32rpx;
}

.detail-title {
  font-size: 36rpx;
  font-weight: 600;
  color: #333;
  flex: 1;
  line-height: 1.4;
}

.detail-tag {
  padding: 8rpx 20rpx;
  border-radius: 8rpx;
  font-size: 24rpx;
  margin-left: 20rpx;
  flex-shrink: 0;
}

.detail-tag.signup {
  background-color: #E6F7FF;
  color: #007AFF;
}

.detail-tag.ongoing {
  background-color: #F6FFED;
  color: #52C41A;
}

.detail-tag.ended {
  background-color: #F5F5F5;
  color: #999;
}

.detail-meta {
  display: flex;
  flex-direction: column;
  gap: 24rpx;
  margin-bottom: 32rpx;
}

.meta-row {
  display: flex;
  align-items: center;
}

.meta-icon {
  width: 32rpx;
  height: 32rpx;
  margin-right: 16rpx;
}

.meta-label {
  font-size: 28rpx;
  color: #999;
  width: 140rpx;
}

.meta-value {
  font-size: 28rpx;
  color: #333;
  flex: 1;
}

.organizer-section {
  display: flex;
  align-items: center;
  padding-top: 32rpx;
  border-top: 1rpx solid #F0F0F0;
}

.organizer-avatar {
  width: 80rpx;
  height: 80rpx;
  border-radius: 40rpx;
  margin-right: 20rpx;
}

.organizer-info {
  display: flex;
  flex-direction: column;
}

.organizer-name {
  font-size: 30rpx;
  color: #333;
  font-weight: 500;
  margin-bottom: 8rpx;
}

.organizer-label {
  font-size: 24rpx;
  color: #999;
}

.desc-section {
  background-color: #FFFFFF;
  padding: 32rpx;
  margin-bottom: 24rpx;
}

.section-title {
  font-size: 32rpx;
  font-weight: 600;
  color: #333;
  margin-bottom: 24rpx;
  display: block;
}

.desc-content {
  font-size: 28rpx;
  color: #666;
  line-height: 1.8;
  white-space: pre-wrap;
}

.safe-area {
  height: 40rpx;
}

.bottom-bar {
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  height: 120rpx;
  background-color: #FFFFFF;
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 32rpx;
  box-shadow: 0 -4rpx 20rpx rgba(0, 0, 0, 0.05);
}

.bar-left {
  display: flex;
  gap: 48rpx;
}

.action-btn {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 8rpx;
}

.action-icon {
  width: 40rpx;
  height: 40rpx;
}

.action-text {
  font-size: 22rpx;
  color: #666;
}

.join-btn {
  padding: 24rpx 80rpx;
  background: linear-gradient(135deg, #007AFF, #00C6FF);
  border-radius: 40rpx;
  font-size: 30rpx;
  color: #FFFFFF;
  font-weight: 500;
}

.join-btn.disabled {
  background: #CCCCCC;
}
</style>
