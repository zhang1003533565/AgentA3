<template>
  <view class="signin-container">
    <nav-bar title="活动签到" />

    <view class="signin-content" :style="{ paddingTop: navBarHeight + 'px' }">
      <view class="signin-card block-white">
        <view class="activity-info">
          <text class="label">当前活动</text>
          <text class="activity-title">{{ activity.title || '暂无活动' }}</text>
          <view class="info-row">
            <image class="info-icon" src="/static/icons/line/calendar.svg" mode="aspectFit" />
            <text class="info-text">{{ activity.time || '-' }}</text>
          </view>
          <view class="info-row">
            <image class="info-icon" src="/static/icons/line/map.svg" mode="aspectFit" />
            <text class="info-text">{{ activity.location || '-' }}</text>
          </view>
        </view>

        <view class="divider"></view>

        <view class="window-tip" v-if="activity.signInWindow">
          <text class="window-tip-text">签到窗口：{{ activity.signInWindow }}</text>
        </view>

        <view class="status-section">
          <view class="status-badge" :class="signInStatus">{{ getStatusText() }}</view>
        </view>

        <view class="action-section">
          <view class="scan-box" @click="handleSignIn" v-if="signInStatus === 'pending'">
            <view class="scan-icon-wrap">
              <image class="scan-icon" src="/static/icons/line/search.svg" mode="aspectFit" />
            </view>
            <text class="scan-tip">点击扫码签到</text>
          </view>

          <view class="success-box" v-else-if="signInStatus === 'success'">
            <image class="success-icon" src="/static/icons/line/award.svg" mode="aspectFit" />
            <text class="success-time">签到时间：{{ signInTime }}</text>
            <button class="done-btn" @click="goBack">返回首页</button>
          </view>
        </view>
      </view>

      <view class="notice-card block-white">
        <view class="notice-header">
          <image class="notice-icon" src="/static/icons/line/message-circle.svg" mode="aspectFit" />
          <text class="notice-title">签到说明</text>
        </view>
        <view class="notice-body">
          <text class="notice-item">1. 活动开始当天可签到</text>
          <text class="notice-item">2. 报名待审核或未通过时无法签到</text>
          <text class="notice-item">3. 签到后将进入待复核，复核通过后发放学分</text>
        </view>
      </view>
    </view>
  </view>
</template>

<script>
import NavBar from '@/components/nav-bar/nav-bar.vue'
import { getActivityDetail } from '@/api/activity.js'
import { studentSignIn, getStudentSignInStatus, getSignInOpenStatus } from '@/api/signin.js'

const parseTime = (value) => (value ? new Date(String(value).replace(' ', 'T')) : null)

export default {
  components: { NavBar },
  data() {
    return {
      navBarHeight: 88,
      activityId: null,
      signInStatus: 'pending',
      signInTime: '',
      activity: {
        title: '',
        time: '',
        location: '',
        signInWindow: ''
      },
      signInOpen: false,
      loading: false
    }
  },
  async onLoad(options) {
    const sys = uni.getSystemInfoSync()
    this.navBarHeight = (sys.statusBarHeight || 20) + 44
    this.activityId = options.activityId || options.id || null
    await this.loadPageData()
  },
  async onShow() {
    if (!this.activityId) return
    await this.loadSignStatus()
  },
  methods: {
    async loadPageData() {
      if (!this.activityId) return
      try {
        const [detailRes, openRes] = await Promise.all([
          getActivityDetail(this.activityId),
          getSignInOpenStatus(this.activityId)
        ])

        const item = detailRes?.data || {}
        this.signInOpen = Boolean(openRes?.data)
        this.activity = {
          title: item.title || '',
          time: this.formatDateRange(item.startTime, item.endTime),
          location: item.location || '',
          signInWindow: this.formatDateRange(item.signInStartTime, item.signInEndTime)
        }
      } catch (error) {
        uni.showToast({ title: '活动信息加载失败', icon: 'none' })
      } finally {
        await this.loadSignStatus()
      }
    },

    async loadSignStatus() {
      if (!this.activityId) return
      try {
        const res = await getStudentSignInStatus(this.activityId)
        const sign = res?.data
        if (sign && sign.signInStatus === 1) {
          this.signInStatus = 'success'
          this.signInTime = this.formatOnlyTime(sign.signInTime)
        } else {
          this.signInStatus = 'pending'
          this.signInTime = ''
        }
      } catch (error) {
        this.signInStatus = 'pending'
      }
    },

    getStatusText() {
      const texts = {
        pending: this.signInOpen ? '等待签到' : '签到未开启',
        success: '签到成功',
        fail: '签到失败'
      }
      return texts[this.signInStatus] || '未知状态'
    },

    async handleSignIn() {
      if (!this.activityId) return
      if (this.loading) return
      if (!this.signInOpen) {
        uni.showToast({ title: '签到尚未开启', icon: 'none' })
        return
      }

      uni.scanCode({
        success: async () => {
          this.loading = true
          uni.showLoading({ title: '签到中...' })
          try {
            await studentSignIn(this.activityId)
            const now = new Date()
            this.signInStatus = 'success'
            this.signInTime = `${String(now.getHours()).padStart(2, '0')}:${String(now.getMinutes()).padStart(2, '0')}`
            uni.showToast({ title: '签到成功，等待复核', icon: 'success' })
          } catch (error) {
            this.signInStatus = 'fail'
          } finally {
            this.loading = false
            uni.hideLoading()
          }
        }
      })
    },

    goBack() {
      uni.reLaunch({ url: '/pages/index/index' })
    },

    formatDateRange(start, end) {
      if (!start && !end) return '-'
      if (!start) return this.formatDate(end)
      if (!end) return this.formatDate(start)
      return `${this.formatDate(start)} - ${this.formatDate(end)}`
    },

    formatDate(value) {
      const date = parseTime(value)
      if (!date) return '-'
      return `${date.getMonth() + 1}月${date.getDate()}日 ${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
    },

    formatOnlyTime(value) {
      const date = parseTime(value)
      if (!date) return ''
      return `${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
    }
  }
}
</script>

<style lang="scss">
.signin-container {
  min-height: 100vh;
  background-color: #f7f7f9;
}

.signin-content {
  padding: 24rpx 32rpx;
}

.signin-card {
  padding: 40rpx 32rpx;
  background-color: #ffffff;
  border-radius: 24rpx;
  margin-bottom: 24rpx;
}

.activity-info {
  .label {
    font-size: 24rpx;
    color: #8e8e93;
    margin-bottom: 12rpx;
    display: block;
  }
  .activity-title {
    font-size: 36rpx;
    font-weight: 700;
    color: #1d1d1f;
    margin-bottom: 24rpx;
    display: block;
  }
  .info-row {
    display: flex;
    align-items: center;
    gap: 12rpx;
    margin-bottom: 8rpx;
    color: #4a4a4a;
    font-size: 28rpx;

    .info-icon {
      width: 32rpx;
      height: 32rpx;
      flex-shrink: 0;
    }
  }
}

.divider {
  height: 1px;
  background-color: #eeeeee;
  margin: 40rpx 0;
}

.window-tip {
  margin-top: -16rpx;
  margin-bottom: 24rpx;
}

.window-tip-text {
  font-size: 24rpx;
  color: #8e8e93;
}

.status-section {
  display: flex;
  justify-content: center;
  margin-bottom: 60rpx;
}

.status-badge {
  padding: 8rpx 24rpx;
  border-radius: 30rpx;
  font-size: 26rpx;
  font-weight: 600;

  &.pending {
    background-color: rgba(255, 149, 0, 0.1);
    color: #ff9500;
  }
  &.success {
    background-color: rgba(52, 199, 89, 0.1);
    color: #34c759;
  }
  &.fail {
    background-color: rgba(255, 59, 48, 0.1);
    color: #ff3b30;
  }
}

.action-section {
  display: flex;
  flex-direction: column;
  align-items: center;
}

.scan-box {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 24rpx;

  &:active .scan-icon-wrap {
    transform: scale(0.95);
    background-color: #e5e5e5;
  }
}

.scan-icon-wrap {
  width: 160rpx;
  height: 160rpx;
  background-color: #f2f2f2;
  border-radius: 40rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  color: $color-primary;
  transition: all 0.2s;
}

.scan-icon {
  width: 48rpx;
  height: 48rpx;
}

.scan-tip {
  font-size: 28rpx;
  color: #8e8e93;
}

.success-box {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 20rpx;
}

.success-icon {
  width: 48rpx;
  height: 48rpx;
}

.success-time {
  font-size: 30rpx;
  color: #1d1d1f;
  font-weight: 500;
}

.done-btn {
  margin-top: 40rpx;
  width: 300rpx;
  height: 80rpx;
  line-height: 80rpx;
  background-color: $color-primary;
  color: #ffffff;
  border-radius: 40rpx;
  font-size: 28rpx;
  border: none;
  &::after {
    border: none;
  }
}

.notice-card {
  padding: 32rpx;
  background-color: #ffffff;
  border-radius: 24rpx;
}

.notice-header {
  display: flex;
  align-items: center;
  gap: 12rpx;
  margin-bottom: 20rpx;
  color: #1d1d1f;

  .notice-icon {
    width: 32rpx;
    height: 32rpx;
    flex-shrink: 0;
  }

  .notice-title {
    font-size: 30rpx;
    font-weight: 600;
  }
}

.notice-body {
  display: flex;
  flex-direction: column;
  gap: 12rpx;
}

.notice-item {
  font-size: 26rpx;
  color: #8e8e93;
  line-height: 1.5;
}
</style>
