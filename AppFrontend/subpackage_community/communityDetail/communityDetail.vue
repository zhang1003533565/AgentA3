<template>
  <view class="detail-container">
    <nav-bar title="活动详情" :showBack="true" fixed placeholder />
    
    <scroll-view class="detail-content" scroll-y :style="{ height: `calc(100vh - ${navBarHeight}px)` }">
      <!-- 活动封面 -->
      <image class="detail-cover" :src="activity.cover || defaultCover" mode="aspectFill" @click="previewCover" />
      
      <!-- 活动信息 -->
      <view class="info-section">
        <view class="detail-header">
          <text class="detail-title">{{ activity.title }}</text>
          <view class="detail-tag" :class="activity.status">{{ getStatusText(activity.status) }}</view>
        </view>

        <view class="detail-submeta">
          <text class="category-chip">{{ activity.categoryName || '未分类' }}</text>
          <text class="signup-deadline" v-if="activity.signupEndTime">报名截止 {{ formatDate(activity.signupEndTime) }}</text>
        </view>
        
        <view class="detail-meta">
          <view class="meta-row">
            <image class="meta-icon" src="/static/icons/line/calendar.svg" mode="aspectFit" />
            <text class="meta-label">活动时间</text>
            <text class="meta-value">{{ formatDateRange(activity.startTime, activity.endTime) }}</text>
          </view>
          <view class="meta-row">
            <image class="meta-icon" src="/static/icons/line/map.svg" mode="aspectFit" />
            <text class="meta-label">活动地点</text>
            <text class="meta-value">{{ activity.location }}</text>
          </view>
          <view class="meta-row">
            <image class="meta-icon" src="/static/icons/line/calendar-alt.svg" mode="aspectFit" />
            <text class="meta-label">报名时间</text>
            <text class="meta-value">{{ formatDateRange(activity.signupStartTime, activity.signupEndTime) }}</text>
          </view>
          <view class="meta-row">
            <image class="meta-icon" src="/static/icons/line/user.svg" mode="aspectFit" />
            <text class="meta-label">报名人数</text>
            <text class="meta-value">{{ activity.currentPeople }}/{{ activity.maxPeople }}人</text>
          </view>
          <view class="meta-row">
            <image class="meta-icon" src="/static/icons/line/message-circle.svg" mode="aspectFit" />
            <text class="meta-label">联系人</text>
            <text class="meta-value">{{ activity.contactName || '-' }}</text>
          </view>
          <view class="meta-row">
            <image class="meta-icon" src="/static/icons/line/key.svg" mode="aspectFit" />
            <text class="meta-label">联系电话</text>
            <text class="meta-value">{{ activity.contactPhone || '-' }}</text>
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

      <view class="gallery-section" v-if="activity.gallery && activity.gallery.length">
        <view class="gallery-header">
          <text class="section-title">活动图片</text>
          <text class="gallery-counter">{{ galleryCurrent + 1 }}/{{ activity.gallery.length }}</text>
        </view>
        <swiper
          class="gallery-swiper"
          circular
          indicator-dots
          indicator-color="rgba(255,255,255,0.45)"
          indicator-active-color="#ffffff"
          :current="galleryCurrent"
          @change="handleGalleryChange"
        >
          <swiper-item v-for="(item, index) in activity.gallery" :key="`${item}-${index}`">
            <image
              class="gallery-slide-image"
              :src="item"
              mode="aspectFill"
              @click="previewGallery(index)"
            />
          </swiper-item>
        </swiper>
      </view>
      
      <!-- 底部安全区域 -->
      <view class="safe-area"></view>
    </scroll-view>
    
    <!-- 底部操作栏 -->
    <view class="bottom-bar">
      <view class="bar-right bar-right--full">
        <view 
          class="join-btn" 
          :class="{ disabled: !canJoin && !isJoined }"
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
import { getActivityDetail } from '@/api/activity.js'
import { getMyRegistrations, registerActivity, cancelRegistration } from '@/api/registration.js'

const parseTime = (value) => (value ? new Date(value.replace(' ', 'T')) : null)
const parseImageList = (images) => {
  if (Array.isArray(images)) return images.filter(Boolean)
  if (!images) return []
  if (typeof images === 'string') {
    try {
      const parsed = JSON.parse(images)
      return Array.isArray(parsed) ? parsed.filter(Boolean) : []
    } catch (error) {
      return []
    }
  }
  return []
}

export default {
  components: { NavBar },
  data() {
    return {
      navBarHeight: 88,
      activityId: null,
      defaultCover: 'https://picsum.photos/seed/community/800/450',
      activity: {},
      registrationId: null,
      isJoined: false,
      galleryCurrent: 0
    }
  },
  async onLoad(options) {
    const sys = uni.getSystemInfoSync()
    this.navBarHeight = (sys.statusBarHeight || 0) + 44
    this.activityId = options.id
    await this.loadActivityDetail()
    await this.loadRegistrationState()
  },
  async onShow() {
    if (!this.activityId) return
    await this.loadActivityDetail()
    await this.loadRegistrationState()
  },
  computed: {
    canJoin() {
      return this.activity.status === 'signup' && !this.isJoined && this.activity.currentPeople < this.activity.maxPeople
    },
    joinBtnText() {
      if (this.isJoined) return '取消报名'
      if (this.activity.status === 'ended') return '已结束'
      if (this.activity.currentPeople >= this.activity.maxPeople) return '已满员'
      return '立即报名'
    }
  },
  methods: {
    async loadActivityDetail() {
      try {
        const res = await getActivityDetail(this.activityId)
        const item = res?.data || {}
        const gallery = parseImageList(item.images)
        this.activity = {
          ...item,
          cover: item.coverImage || gallery[0] || '',
          gallery,
          organizer: item.organizerName || '校园活动中心',
          categoryName: item.category?.categoryName || '',
          description: item.content || '',
          status: this.getStatus(item)
        }
        this.galleryCurrent = 0
      } catch (error) {
        uni.showToast({ title: '活动详情加载失败', icon: 'none' })
      }
    },

    async loadRegistrationState() {
      try {
        const res = await getMyRegistrations({ page: 1, size: 100 })
        const records = res?.data?.records || []
        const current = records.find((item) => String(item.activityId) === String(this.activityId))
        this.isJoined = Boolean(current)
        this.registrationId = current?.id || null
      } catch (error) {
        this.isJoined = false
        this.registrationId = null
      }
    },

    async handleJoin() {
      if (this.isJoined && this.registrationId) {
        await this.handleCancel()
        return
      }
      if (!this.canJoin) return

      uni.showModal({
        title: '确认报名',
        content: '确定要报名参加该活动吗？',
        success: async (res) => {
          if (res.confirm) {
            try {
              const result = await registerActivity(this.activityId)
              this.registrationId = result?.data?.id || null
              this.isJoined = true
              this.activity.currentPeople = (this.activity.currentPeople || 0) + 1
              uni.showToast({ title: '报名成功', icon: 'success' })
            } catch (error) {}
          }
        }
      })
    },

    async handleCancel() {
      uni.showModal({
        title: '取消报名',
        content: '确定取消当前活动报名吗？',
        success: async (res) => {
          if (!res.confirm || !this.registrationId) return
          try {
            await cancelRegistration(this.registrationId)
            this.isJoined = false
            this.registrationId = null
            this.activity.currentPeople = Math.max(0, (this.activity.currentPeople || 1) - 1)
            uni.showToast({ title: '已取消报名', icon: 'none' })
          } catch (error) {}
        }
      })
    },

    previewCover() {
      const url = this.activity.cover || this.defaultCover
      uni.previewImage({
        urls: this.activity.gallery && this.activity.gallery.length ? this.activity.gallery : [url],
        current: url
      })
    },

    previewGallery(index) {
      const urls = this.activity.gallery && this.activity.gallery.length ? this.activity.gallery : [this.activity.cover || this.defaultCover]
      uni.previewImage({
        urls,
        current: urls[index] || urls[0]
      })
    },

    handleGalleryChange(event) {
      this.galleryCurrent = event.detail.current || 0
    },

    formatDate(dateStr) {
      if (!dateStr) return ''
      const date = parseTime(dateStr)
      return `${date.getMonth() + 1}月${date.getDate()}日 ${date.getHours()}:${String(date.getMinutes()).padStart(2, '0')}`
    },

    formatDateRange(start, end) {
      if (!start && !end) return '-'
      if (!start) return this.formatDate(end)
      if (!end) return this.formatDate(start)
      return `${this.formatDate(start)} - ${this.formatDate(end)}`
    },

    getStatus(item) {
      const now = new Date()
      const startTime = parseTime(item.startTime)
      const endTime = parseTime(item.endTime)
      if (endTime && now >= endTime) return 'ended'
      if (startTime && now >= startTime) return 'ongoing'
      return 'signup'
    },

    getStatusText(status) {
      const map = {
        signup: '报名中',
        ongoing: '进行中',
        ended: '已结束'
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

.detail-submeta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
  margin-bottom: 28rpx;
}

.category-chip {
  display: inline-flex;
  align-items: center;
  padding: 8rpx 16rpx;
  border-radius: 999rpx;
  background: #eef6f2;
  color: #2c7a67;
  font-size: 22rpx;
  font-weight: 600;
}

.signup-deadline {
  font-size: 22rpx;
  color: #8b96a8;
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

.gallery-section {
  background-color: #FFFFFF;
  padding: 32rpx;
  margin-bottom: 24rpx;
}

.gallery-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 24rpx;
}

.section-title {
  font-size: 32rpx;
  font-weight: 600;
  color: #333;
  display: block;
}

.gallery-counter {
  font-size: 24rpx;
  color: #8b96a8;
}

.desc-content {
  font-size: 28rpx;
  color: #666;
  line-height: 1.8;
  white-space: pre-wrap;
}

.gallery-swiper {
  width: 100%;
  height: 420rpx;
}

.gallery-slide-image {
  width: 100%;
  height: 420rpx;
  border-radius: 20rpx;
  background: #f3f4f6;
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

.bar-right {
  display: flex;
  justify-content: center;
}

.bar-right--full {
  width: 100%;
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
