<template>
  <view class="page-wrap">
    <nav-bar title="个人中心" :showBack="false" />
    <view class="mine-page">
      <!-- 头部：纯白背景 -->
      <view class="header-block">
        <view class="user-row">
          <image class="avatar" :src="userAvatar" mode="aspectFill" @click="changeAvatar" />
          <view class="user-info">
            <text class="user-name">{{ userInfo ? userInfo.username : '未登录' }}</text>
            <text class="user-id">学号 {{ userInfo ? (userInfo.studentId || '—') : '—' }}</text>
          </view>
        </view>
      </view>
      <!-- 12px 灰色隔离条 -->
      <view class="gap-bar"></view>
      <view class="menu-block">
        <view class="cell" @click="goToMessageCenter">
          <view class="cell-left">
            <view class="cell-icon"><image class="cell-icon-img" src="/static/icons/line/message-circle.svg" mode="aspectFit" /></view>
            <text class="cell-label">我的消息</text>
          </view>
          <view class="cell-right">
            <text v-if="messageUnreadCount > 0" class="message-badge">{{ messageUnreadCount > 99 ? '99+' : messageUnreadCount }}</text>
            <text class="cell-arrow">›</text>
          </view>
        </view>
      </view>
      <view class="gap-bar"></view>
      <!-- 常用功能：通栏白块 -->
      <view class="menu-block">
        <view class="cell" @click="goToSchedule">
          <view class="cell-left">
            <view class="cell-icon"><image class="cell-icon-img" src="/static/icons/line/calendar.svg" mode="aspectFit" /></view>
            <text class="cell-label">我的课表</text>
          </view>
          <text class="cell-arrow">›</text>
        </view>
        <view class="cell-divider"></view>
        <view class="cell" @click="goToMyCourses">
          <view class="cell-left">
            <view class="cell-icon"><image class="cell-icon-img" src="/static/icons/line/book-open.svg" mode="aspectFit" /></view>
            <text class="cell-label">我的课程</text>
          </view>
          <text class="cell-arrow">›</text>
        </view>
        <view class="cell-divider"></view>
        <view class="cell" @click="goToMeetingSchedule">
          <view class="cell-left">
            <view class="cell-icon"><image class="cell-icon-img" src="/static/icons/line/calendar.svg" mode="aspectFit" /></view>
            <text class="cell-label">会议日程</text>
          </view>
          <text class="cell-arrow">›</text>
        </view>
        <view class="cell-divider"></view>
        <view class="cell" @click="goToMyActivity">
          <view class="cell-left">
            <view class="cell-icon"><image class="cell-icon-img" src="/static/icons/line/compass.svg" mode="aspectFit" /></view>
            <text class="cell-label">我的活动</text>
          </view>
          <text class="cell-arrow">›</text>
        </view>
        <view class="cell-divider"></view>
        <view class="cell" @click="goToAiHistory">
          <view class="cell-left">
            <view class="cell-icon"><image class="cell-icon-img" src="/static/icons/line/brain.svg" mode="aspectFit" /></view>
            <text class="cell-label">AI 会话历史</text>
          </view>
          <text class="cell-arrow">›</text>
        </view>
        <view class="cell-divider"></view>
        <view class="cell" @click="goToProfileRadar">
          <view class="cell-left">
            <view class="cell-icon"><image class="cell-icon-img" src="/static/icons/line/chart.svg" mode="aspectFit" /></view>
            <text class="cell-label">个人画像雷达图</text>
          </view>
          <text class="cell-arrow">›</text>
        </view>
        <view class="cell-divider"></view>
        <view class="cell" @click="goToExamPapers">
          <view class="cell-left">
            <view class="cell-icon"><image class="cell-icon-img" src="/static/icons/line/clipboard.svg" mode="aspectFit" /></view>
            <text class="cell-label">我的试卷</text>
          </view>
          <text class="cell-arrow">›</text>
        </view>
        <view class="cell-divider"></view>
        <view class="cell" @click="goToMyQuestionBank">
          <view class="cell-left">
            <view class="cell-icon"><image class="cell-icon-img" src="/static/icons/line/book-open.svg" mode="aspectFit" /></view>
            <view class="cell-text">
              <text class="cell-label">我的题库</text>
              <text class="cell-desc">公共题库与私有题库统一入口</text>
            </view>
          </view>
          <text class="cell-arrow">›</text>
        </view>
      </view>
      <!-- 12px 灰色隔离条 -->
      <view class="gap-bar"></view>
      <!-- 账号设置：通栏白块 -->
      <view class="menu-block">
        <view class="cell" @click="goToChangePassword">
          <view class="cell-left">
            <view class="cell-icon"><image class="cell-icon-img" src="/static/icons/line/lock.svg" mode="aspectFit" /></view>
            <text class="cell-label">修改密码</text>
          </view>
          <text class="cell-arrow">›</text>
        </view>
        <view class="cell-divider"></view>
        <view class="cell" @click="logout">
          <view class="cell-left">
            <view class="cell-icon"><image class="cell-icon-img" src="/static/icons/line/log-out.svg" mode="aspectFit" /></view>
            <text class="cell-label">退出登录</text>
          </view>
          <text class="cell-arrow">›</text>
        </view>
      </view>
    </view>
    <app-main-tab-bar current="mine" />
  </view>
</template>

<script>
import AppMainTabBar from '@/components/app-main-tab-bar/app-main-tab-bar.vue'
import NavBar from '@/components/nav-bar/nav-bar.vue'
import { getUserInfo, setUserInfo } from '@/utils/storage.js'
import { updateAvatar } from '@/api/user.js'
import { getUploadErrorMessage, uploadImage } from '@/utils/upload.js'
import { getMessageState, refreshMessageState, stopMessageSync, subscribeMessageStore } from '@/utils/messageStore.js'

export default {
  components: { AppMainTabBar, NavBar },
  data() {
    return {
      userInfo: null,
      userAvatar: '',
      messageUnreadCount: 0,
      unsubscribeMessageStore: null
    }
  },
  onLoad() {
    this.loadUser()
    this.applyMessageState(getMessageState())
    this.unsubscribeMessageStore = subscribeMessageStore((state) => {
      this.applyMessageState(state)
    })
    refreshMessageState('mine-load')
  },
  onShow() {
    this.loadUser()
    refreshMessageState('mine-show')
  },
  onUnload() {
    if (this.unsubscribeMessageStore) {
      this.unsubscribeMessageStore()
      this.unsubscribeMessageStore = null
    }
  },
  methods: {
    loadUser() {
      const info = getUserInfo()
      if (!info) {
        uni.reLaunch({ url: '/pages/login/login' })
        return
      }
      this.userInfo = info
      if (info.avatar) {
        this.userAvatar = info.avatar
      } else {
        const seed = info.realName || info.username || info.studentId || 'mine-user'
        this.userAvatar = `https://api.dicebear.com/7.x/avataaars/svg?seed=${encodeURIComponent(seed)}`
      }
    },
    applyMessageState(state = {}) {
      this.messageUnreadCount = Number(state.unreadLostFoundAppCount || 0)
        + Number(state.unreadExamCount || 0)
    },
    changeAvatar() {
      uni.chooseImage({
        count: 1,
        sizeType: ['compressed'],
        sourceType: ['album', 'camera'],
        success: async (res) => {
          const filePath = res.tempFilePaths[0]
          uni.showLoading({ title: '上传中...' })
          try {
            const url = await uploadImage(filePath)
            await updateAvatar(url)
            // 更新本地存储
            const info = getUserInfo()
            info.avatar = url
            setUserInfo(info)
            this.userInfo = info
            this.userAvatar = url
            uni.hideLoading()
            uni.showToast({ title: '头像已更新', icon: 'success' })
          } catch (e) {
            uni.hideLoading()
            uni.showToast({ title: getUploadErrorMessage(e), icon: 'none' })
          }
        }
      })
    },
    goToMessageCenter() {
      uni.navigateTo({ url: '/subpackage_message/messageCenter/messageCenter' })
    },
    goToSchedule() {
      uni.navigateTo({ url: '/subpackage_schedule/schedule/schedule' })
    },
    goToMyCourses() {
      uni.navigateTo({ url: '/subpackage_learning/myCourses/myCourses' })
    },
    goToMeetingSchedule() {
      uni.navigateTo({ url: '/subpackage_meeting/meetingSchedule/meetingSchedule' })
    },
    goToMyActivity() {
      uni.navigateTo({ url: '/subpackage_community/myActivities/myActivities' })
    },
    goToAiHistory() {
      uni.navigateTo({ url: '/subpackage_ai/aiHistory/aiHistory' })
    },
    goToProfileRadar() {
      uni.navigateTo({ url: '/subpackage_ai/profileRadar/profileRadar' })
    },
    goToExamPapers() {
      uni.navigateTo({ url: '/subpackage_exam/paperList/paperList' })
    },
    goToMyQuestionBank() {
      uni.navigateTo({ url: '/subpackage_exam/myQuestionBank/myQuestionBank' })
    },
    goToChangePassword() {
      uni.navigateTo({ url: '/pages/changePassword/changePassword' })
    },
    logout() {
      uni.showModal({
        title: '提示',
        content: '确定要退出登录吗？',
        success: (res) => {
          if (res.confirm) {
            stopMessageSync()
            uni.removeStorageSync('token')
            uni.removeStorageSync('userInfo')
            uni.reLaunch({ url: '/pages/login/login' })
          }
        }
      })
    }
  }
}
</script>

<style lang="scss" scoped>
.page-wrap {
  scrollbar-width: none;
  -ms-overflow-style: none;
}

.page-wrap::-webkit-scrollbar {
  width: 0;
  height: 0;
  display: none;
}

.page-wrap {
  min-height: 100vh;
  background-color: #F7F7F9;
  padding-bottom: 120rpx;
}

.mine-page {
  padding: 0;
}

/* ========== 头部：纯白 ========== */
.header-block {
  background-color: #FFFFFF;
  padding: 32rpx 32rpx 40rpx;
}
.user-row {
  display: flex;
  align-items: center;
  margin-bottom: 40rpx;
}
.avatar {
  width: 96rpx;
  height: 96rpx;
  border-radius: 50%;
  border: 1px solid #EEEEEE;
  flex-shrink: 0;
  margin-right: 24rpx;
  active-opacity: 0.7;
}
.cell-icon-img {
  width: 40rpx;
  height: 40rpx;
}
.user-info {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 8rpx;
}
.user-name {
  font-size: 40rpx;
  font-weight: 700;
  color: #1D1D1F;
}
.user-id {
  font-size: 24rpx;
  font-weight: 400;
  color: #8E8E93;
}

/* ========== 12px 灰色隔离条 ========== */
.gap-bar {
  height: 24rpx;
  background-color: #F2F2F7;
}

/* ========== 功能列表：通栏白块，Cell 结构 ========== */
.menu-block {
  background-color: #FFFFFF;
}
.cell {
  min-height: 112rpx;
  padding: 0 32rpx;
  display: flex;
  align-items: center;
  justify-content: space-between;
  box-sizing: border-box;
}
.cell-left {
  display: flex;
  align-items: center;
  min-width: 0;
  flex: 1;
}
.cell-icon {
  width: 40rpx;
  height: 40rpx;
  margin-right: 24rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}
.cell-text {
  display: flex;
  flex-direction: column;
  min-width: 0;
  gap: 6rpx;
  padding: 20rpx 0;
}
.cell-label {
  font-size: 28rpx;
  font-weight: 400;
  color: #1D1D1F;
}
.cell-desc {
  font-size: 22rpx;
  font-weight: 400;
  color: #8E8E93;
  line-height: 1.3;
}
.cell-arrow {
  font-size: 32rpx;
  font-weight: 300;
  color: #C7C7CC;
  flex-shrink: 0;
  margin-left: 16rpx;
}
.cell-right {
  display: flex;
  align-items: center;
  flex-shrink: 0;
}
.message-badge {
  min-width: 34rpx;
  height: 34rpx;
  padding: 0 10rpx;
  border-radius: 999rpx;
  background-color: #D95D5D;
  color: #FFFFFF;
  font-size: 20rpx;
  font-weight: 700;
  line-height: 34rpx;
  text-align: center;
  box-sizing: border-box;
}
.cell-divider {
  height: 1rpx;
  margin-left: 72rpx;
  margin-right: 0;
  background-color: #E5E5EA;
}
</style>
