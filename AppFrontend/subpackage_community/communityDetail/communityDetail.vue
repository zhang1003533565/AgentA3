<template>
  <view class="detail-container">
    <nav-bar title="活动详情" :showBack="true" fixed placeholder />

    <scroll-view class="detail-content" scroll-y :style="{ height: `calc(100vh - ${navBarHeight}px)` }">
      <image class="detail-cover" :src="activity.cover || defaultCover" mode="aspectFill" @click="previewCover" />

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
            <image class="meta-icon" src="/static/icons/line/calendar-alt.svg" mode="aspectFit" />
            <text class="meta-label">签到时间</text>
            <text class="meta-value">{{ formatDateRange(activity.signInStartTime, activity.signInEndTime) }}</text>
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

        <view class="organizer-section">
          <image class="organizer-avatar" :src="activity.organizerAvatar || '/static/logo.png'" mode="aspectFill" />
          <view class="organizer-info">
            <text class="organizer-name">{{ activity.organizer }}</text>
            <text class="organizer-label">活动主办方</text>
          </view>
        </view>

        <view class="signup-section">
          <view class="signup-head">
            <text class="signup-head-title">已报名</text>
            <text class="signup-head-count">({{ activity.currentPeople || 0 }}/{{ activity.maxPeople || 0 }})</text>
          </view>
          <view class="signup-user-list" v-if="displayRegistrants.length">
            <view
              class="signup-user-item"
              v-for="(user, index) in displayRegistrants"
              :key="`${user.name || 'user'}-${index}`"
            >
              <image v-if="user.avatar" class="signup-user-avatar" :src="user.avatar" mode="aspectFill" />
              <view v-else class="signup-user-avatar signup-user-avatar--fallback">
                <text class="signup-user-avatar-text">{{ (user.name || '同').slice(0, 1) }}</text>
              </view>
              <text class="signup-user-name">{{ user.maskedName }}</text>
            </view>
          </view>
          <text class="signup-empty" v-else>暂无报名</text>
        </view>
      </view>

      <view class="desc-section">
        <text class="section-title">活动详情</text>
        <text class="desc-content">{{ activity.description || '暂无活动详情' }}</text>
      </view>

      <view class="safe-area"></view>
    </scroll-view>

    <view class="bottom-bar">
      <view class="bar-right bar-right--full">
        <view
          class="join-btn"
          :class="{ disabled: !canJoin && !isJoined && !canCancelJoined && !canGoSign }"
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
import { getStudentSignInStatus } from '@/api/signin.js'

const parseTime = (value) => (value ? new Date(String(value).replace(' ', 'T')) : null)
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
      registrationStatus: '',
      isJoined: false,
      signInRecord: null,
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
      return this.activity.status === 'signup' && this.isInSignupWindow() && !this.isJoined && this.activity.currentPeople < this.activity.maxPeople
    },
    canCancelJoined() {
      return this.isJoined && this.registrationStatus === 'APPROVED' && !this.hasSigned() && this.isBeforeActivityStart()
    },
    canGoSign() {
      if (!this.isJoined || this.registrationStatus !== 'APPROVED' || this.hasSigned()) return false
      if (!this.isAfterActivityStart()) return false
      if (!this.isInSignInWindow()) return false
      return true
    },
    displayRegistrants() {
      return (this.activity.registrants || []).slice(0, 6)
    },
    joinBtnText() {
      if (this.isJoined) {
        if (this.registrationStatus === 'PENDING') return '等待审核'
        if (this.registrationStatus === 'CANCEL_PENDING') return '等待审核'
        if (this.registrationStatus === 'APPROVED') {
          if (this.hasSigned()) {
            const reviewStatus = this.signInRecord?.reviewStatus
            if (reviewStatus === 'APPROVED') return '学分已发放'
            if (reviewStatus === 'REJECTED') return '复核未通过'
            return '待复核'
          }
          if (this.canCancelJoined) return '取消报名'
          if (this.isAfterActivityStart()) {
            if (this.canGoSign) return '去签到'
            if (this.isSignInWindowPassed()) return '签到已结束'
            return '等待签到'
          }
          return '取消报名'
        }
        if (this.registrationStatus === 'REJECTED') return '审核未通过'
        return '已报名'
      }
      if (this.activity.status === 'ended') return '已结束'
      if (this.activity.currentPeople >= this.activity.maxPeople) return '已满员'
      if (!this.isInSignupWindow()) return '报名中'
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
          status: this.getStatus(item),
          registrants: this.normalizeRegistrants(item)
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
        this.registrationStatus = current?.status || ''
        if (this.isJoined) {
          try {
            const signRes = await getStudentSignInStatus(this.activityId)
            this.signInRecord = signRes?.data || null
          } catch (error) {
            this.signInRecord = null
          }
        } else {
          this.signInRecord = null
        }
      } catch (error) {
        this.isJoined = false
        this.registrationId = null
        this.registrationStatus = ''
        this.signInRecord = null
      }
    },

    async handleJoin() {
      if (this.canCancelJoined && this.registrationId) {
        await this.handleCancel()
        return
      }

      if (this.isJoined && !this.canCancelJoined) {
        if (this.registrationStatus === 'APPROVED' && this.isBeforeActivityStart() && !this.hasSigned()) {
          await this.handleCancel()
          return
        }
        if (this.canGoSign) {
          uni.navigateTo({ url: `/subpackage_signin/signIn/signIn?activityId=${this.activityId}` })
          return
        }
        uni.showToast({ title: this.joinBtnText, icon: 'none' })
        return
      }

      if (!this.canJoin) return
      if (!this.isInSignupWindow()) {
        uni.showToast({ title: '当前不在报名时间内', icon: 'none' })
        return
      }

      uni.showModal({
        title: '确认报名',
        content: '确定要报名参加该活动吗？',
        success: async (res) => {
          if (res.confirm) {
            try {
              const result = await registerActivity(this.activityId)
              this.registrationId = result?.data?.id || null
              this.registrationStatus = result?.data?.status || 'PENDING'
              this.isJoined = true
              this.signInRecord = null
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
            const result = await cancelRegistration(this.registrationId)
            const status = result?.data?.status
            if (status === 'CANCEL_PENDING') {
              this.registrationStatus = 'CANCEL_PENDING'
              this.isJoined = true
              uni.showToast({ title: '已提交取消审核', icon: 'none' })
            } else {
              this.isJoined = false
              this.registrationId = null
              this.registrationStatus = ''
              this.signInRecord = null
              this.activity.currentPeople = Math.max(0, (this.activity.currentPeople || 1) - 1)
              uni.showToast({ title: '已取消报名', icon: 'none' })
            }
          } catch (error) {}
        }
      })
    },

    isInSignupWindow() {
      const now = new Date()
      const signupStart = parseTime(this.activity.signupStartTime)
      const signupEnd = parseTime(this.activity.signupEndTime)
      const activityStart = parseTime(this.activity.startTime)
      if (signupStart && now < signupStart) return false
      if (signupEnd && now > signupEnd) return false
      if (activityStart && now >= activityStart) return false
      return true
    },

    isBeforeActivityStart() {
      const start = parseTime(this.activity.startTime)
      if (!start) return true
      return new Date() < start
    },

    isAfterActivityStart() {
      const start = parseTime(this.activity.startTime)
      if (!start) return false
      return new Date() >= start
    },

    isInSignInWindow() {
      const now = new Date()
      const signInStart = parseTime(this.activity.signInStartTime)
      const signInEnd = parseTime(this.activity.signInEndTime)
      if (signInStart && now < signInStart) return false
      if (signInEnd && now > signInEnd) return false
      return true
    },

    isSignInWindowPassed() {
      const signInEnd = parseTime(this.activity.signInEndTime)
      if (!signInEnd) return false
      return new Date() > signInEnd
    },

    hasSigned() {
      return Boolean(this.signInRecord && this.signInRecord.signInStatus === 1)
    },

    previewCover() {
      const url = this.activity.cover || this.defaultCover
      uni.previewImage({
        urls: this.activity.gallery && this.activity.gallery.length ? this.activity.gallery : [url],
        current: url
      })
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

    normalizeRegistrants(item) {
      let source = item?.registrants || item?.participants || item?.registeredUsers || item?.signupUsers || item?.attendees || []
      if (typeof source === 'string') {
        try {
          source = JSON.parse(source)
        } catch (error) {
          source = []
        }
      }
      if (!Array.isArray(source)) source = []

      const mapped = source
        .map((user, index) => {
          const name = user?.nickName || user?.nickname || user?.name || user?.userName || user?.realName || user?.username || `同学${index + 1}`
          const avatar = user?.avatar || user?.avatarUrl || user?.userAvatar || user?.profile || ''
          return { name: String(name), maskedName: this.maskName(name), avatar }
        })
        .slice(0, 6)

      if (mapped.length) return mapped
      return this.buildFallbackRegistrants(item?.currentPeople || 0)
    },

    buildFallbackRegistrants(count) {
      const total = Math.max(0, Math.min(6, Number(count) || 0))
      return Array.from({ length: total }, (_, index) => {
        const name = `同学${index + 1}`
        return { name, maskedName: name, avatar: '' }
      })
    },

    maskName(name) {
      const text = String(name || '').trim()
      if (!text) return '同学'
      return `${text.slice(0, 1)}**`
    },

    getStatusText(status) {
      const map = { signup: '报名中', ongoing: '进行中', ended: '已结束' }
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

.signup-section {
  margin-top: 28rpx;
  padding-top: 24rpx;
  border-top: 1rpx solid #F0F0F0;
}

.signup-head {
  display: flex;
  align-items: baseline;
  gap: 12rpx;
  margin-bottom: 20rpx;
}

.signup-head-title {
  font-size: 32rpx;
  font-weight: 600;
  color: #333;
}

.signup-head-count {
  font-size: 28rpx;
  color: #D88944;
}

.signup-user-list {
  display: flex;
  align-items: flex-start;
  gap: 20rpx;
  overflow: hidden;
}

.signup-user-item {
  width: 92rpx;
  flex-shrink: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
}

.signup-user-avatar {
  width: 86rpx;
  height: 86rpx;
  border-radius: 50%;
  background: #EEF1F7;
}

.signup-user-avatar--fallback {
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #FFD9A6, #FDBA8C);
}

.signup-user-avatar-text {
  font-size: 30rpx;
  color: #fff;
  font-weight: 600;
}

.signup-user-name {
  width: 100%;
  margin-top: 10rpx;
  font-size: 22rpx;
  color: #4D5562;
  text-align: center;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.signup-empty {
  font-size: 24rpx;
  color: #8B96A8;
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
