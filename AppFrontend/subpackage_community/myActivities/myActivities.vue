<template>
  <view class="my-activities-page">
    <nav-bar title="我的活动" :showBack="true" fixed placeholder />

    <view class="filter-bar">
      <view
        v-for="item in filterOptions"
        :key="item.value"
        class="filter-chip"
        :class="{ active: currentFilter === item.value }"
        @click="currentFilter = item.value"
      >
        {{ item.label }}
      </view>
    </view>

    <scroll-view class="content-scroll" scroll-y refresher-enabled :refresher-triggered="refreshing" @refresherrefresh="onRefresh">
      <view v-if="filteredActivities.length" class="activity-list">
        <view
          v-for="item in filteredActivities"
          :key="item.registrationId"
          class="activity-card"
          @click="goToDetail(item.activityId)"
        >
          <image class="activity-cover" :src="item.cover || defaultCover" mode="aspectFill" />

          <view class="activity-body">
            <view class="activity-header">
              <text class="activity-title">{{ item.title }}</text>
              <view class="status-badge" :class="item.phase">
                {{ getPhaseText(item.phase) }}
              </view>
            </view>

            <view class="meta-row">
              <text class="category-chip">{{ item.categoryName || '未分类' }}</text>
              <text class="audit-text" :class="item.auditClass">{{ item.auditText }}</text>
            </view>

            <view class="info-row">
              <image class="info-icon" src="/static/icons/line/calendar.svg" mode="aspectFit" />
              <text class="info-text">{{ formatDateRange(item.startTime, item.endTime) }}</text>
            </view>
            <view class="info-row">
              <image class="info-icon" src="/static/icons/line/map.svg" mode="aspectFit" />
              <text class="info-text">{{ item.location || '地点待定' }}</text>
            </view>
            <view class="info-row">
              <image class="info-icon" src="/static/icons/line/clipboard.svg" mode="aspectFit" />
              <text class="info-text">报名时间 {{ formatDate(item.signupTime) }}</text>
            </view>

            <view class="card-footer">
              <text class="footer-text">{{ getFooterText(item) }}</text>
              <view
                v-if="canCancel(item)"
                class="action-btn"
                @click.stop="handleCancel(item)"
              >
                取消报名
              </view>
            </view>
          </view>
        </view>
      </view>

      <view v-else-if="!loading" class="empty-state">
        <image class="empty-icon" src="/static/icons/line/calendar-alt.svg" mode="aspectFit" />
        <text class="empty-title">还没有报名活动</text>
        <text class="empty-desc">去校园活动看看最近有哪些活动吧</text>
        <view class="empty-btn" @click="goToActivityList">去逛逛</view>
      </view>

      <view v-if="loading" class="loading-state">加载中...</view>
    </scroll-view>
  </view>
</template>

<script>
import NavBar from '@/components/nav-bar/nav-bar.vue'
import { getActivityDetail } from '@/api/activity.js'
import { cancelRegistration, getMyRegistrations } from '@/api/registration.js'

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
      defaultCover: 'https://picsum.photos/seed/my-activity/800/450',
      currentFilter: 'all',
      filterOptions: [
        { label: '全部', value: 'all' },
        { label: '即将开始', value: 'signup' },
        { label: '进行中', value: 'ongoing' },
        { label: '已结束', value: 'ended' }
      ],
      activityList: [],
      loading: false,
      refreshing: false
    }
  },
  computed: {
    filteredActivities() {
      const list = this.currentFilter === 'all'
        ? this.activityList
        : this.activityList.filter((item) => item.phase === this.currentFilter)

      return [...list].sort((a, b) => {
        const order = { signup: 0, ongoing: 1, ended: 2, rejected: 3 }
        const diff = (order[a.phase] ?? 99) - (order[b.phase] ?? 99)
        if (diff !== 0) return diff
        const aTime = parseTime(a.startTime)?.getTime?.() || 0
        const bTime = parseTime(b.startTime)?.getTime?.() || 0
        return aTime - bTime
      })
    }
  },
  onLoad() {
    this.loadActivities()
  },
  onShow() {
    this.loadActivities()
  },
  methods: {
    async loadActivities() {
      if (this.loading) return
      this.loading = true
      try {
        const registrationRes = await getMyRegistrations({ page: 1, size: 100 })
        const registrations = registrationRes?.data?.records || []

        const detailResults = await Promise.all(
          registrations.map(async (registration) => {
            try {
              const detailRes = await getActivityDetail(registration.activityId)
              const activity = detailRes?.data || {}
              const images = parseImageList(activity.images)
              const phase = this.getPhase(activity, registration.status)
              return {
                registrationId: registration.id,
                activityId: registration.activityId,
                registrationStatus: registration.status,
                signupTime: registration.signupTime,
                title: activity.title || '活动已失效',
                cover: activity.coverImage || images[0] || '',
                categoryName: activity.category?.categoryName || '',
                location: activity.location || '',
                startTime: activity.startTime,
                endTime: activity.endTime,
                phase,
                auditText: this.getAuditText(registration.status),
                auditClass: this.getAuditClass(registration.status)
              }
            } catch (error) {
              return {
                registrationId: registration.id,
                activityId: registration.activityId,
                registrationStatus: registration.status,
                signupTime: registration.signupTime,
                title: '活动已失效',
                cover: '',
                categoryName: '',
                location: '',
                startTime: '',
                endTime: '',
                phase: 'ended',
                auditText: this.getAuditText(registration.status),
                auditClass: this.getAuditClass(registration.status)
              }
            }
          })
        )

        this.activityList = detailResults.filter((item) => item.registrationStatus !== 'REJECTED')
      } catch (error) {
        this.activityList = []
      } finally {
        this.loading = false
        this.refreshing = false
      }
    },

    onRefresh() {
      this.refreshing = true
      this.loadActivities()
    },

    getPhase(activity, registrationStatus) {
      if (registrationStatus === 'REJECTED') return 'rejected'

      const now = new Date()
      const startTime = parseTime(activity.startTime)
      const endTime = parseTime(activity.endTime)
      if (endTime && now >= endTime) return 'ended'
      if (startTime && now >= startTime) return 'ongoing'
      return 'signup'
    },

    getPhaseText(phase) {
      const map = {
        signup: '即将开始',
        ongoing: '进行中',
        ended: '已结束',
        rejected: '未通过'
      }
      return map[phase] || '未知'
    },

    getAuditText(status) {
      const map = {
        APPROVED: '报名已通过',
        REJECTED: '报名未通过'
      }
      return map[status] || '状态未知'
    },

    getAuditClass(status) {
      const map = {
        APPROVED: 'approved',
        REJECTED: 'rejected'
      }
      return map[status] || ''
    },

    canCancel(item) {
      return item.registrationStatus !== 'REJECTED' && item.phase === 'signup'
    },

    async handleCancel(item) {
      uni.showModal({
        title: '取消报名',
        content: '确定取消当前活动报名吗？',
        success: async (res) => {
          if (!res.confirm) return
          try {
            await cancelRegistration(item.registrationId)
            uni.showToast({ title: '已取消报名', icon: 'none' })
            this.loadActivities()
          } catch (error) {}
        }
      })
    },

    goToDetail(activityId) {
      if (!activityId) return
      uni.navigateTo({
        url: `/subpackage_community/communityDetail/communityDetail?id=${activityId}`
      })
    },

    goToActivityList() {
      uni.navigateTo({
        url: '/subpackage_community/communityActivity/communityActivity'
      })
    },

    formatDate(dateStr) {
      if (!dateStr) return '--'
      const date = parseTime(dateStr)
      return `${date.getMonth() + 1}月${date.getDate()}日 ${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
    },

    formatDateRange(start, end) {
      if (!start) return '时间待定'
      if (!end) return this.formatDate(start)
      const endDate = parseTime(end)
      return `${this.formatDate(start)} - ${String(endDate.getHours()).padStart(2, '0')}:${String(endDate.getMinutes()).padStart(2, '0')}`
    },

    getFooterText(item) {
      if (item.phase === 'signup') return '活动尚未开始，记得按时参加'
      if (item.phase === 'ongoing') return '活动正在进行中'
      return '这场活动已经结束'
    }
  }
}
</script>

<style lang="scss">
.my-activities-page {
  min-height: 100vh;
  background: #F7F7F9;
}

.filter-bar {
  display: flex;
  gap: 16rpx;
  padding: 20rpx 24rpx;
  background: #FFFFFF;
  overflow-x: auto;
  white-space: nowrap;
  box-shadow: 0 8rpx 24rpx rgba(15, 23, 42, 0.04);
}

.filter-chip {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 0 24rpx;
  height: 56rpx;
  border-radius: 999rpx;
  background: #F3F5F8;
  color: #667085;
  font-size: 24rpx;
  flex-shrink: 0;
}

.filter-chip.active {
  background: #EAF2FF;
  color: #2B6FF3;
  font-weight: 600;
}

.content-scroll {
  height: calc(100vh - 220rpx);
}

.activity-list {
  padding: 24rpx;
}

.activity-card {
  margin-bottom: 24rpx;
  background: #FFFFFF;
  border-radius: 24rpx;
  overflow: hidden;
  box-shadow: 0 14rpx 34rpx rgba(15, 23, 42, 0.05);
}

.activity-cover {
  width: 100%;
  height: 260rpx;
  background: #EDF2F7;
}

.activity-body {
  padding: 24rpx;
}

.activity-header {
  display: flex;
  gap: 16rpx;
  align-items: flex-start;
  justify-content: space-between;
}

.activity-title {
  flex: 1;
  font-size: 34rpx;
  line-height: 1.4;
  color: #1F2937;
  font-weight: 700;
}

.status-badge {
  flex-shrink: 0;
  min-width: 116rpx;
  height: 48rpx;
  padding: 0 20rpx;
  border-radius: 999rpx;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 22rpx;
  font-weight: 600;
}

.status-badge.signup {
  background: #EAF5FF;
  color: #2B6FF3;
}

.status-badge.ongoing {
  background: #ECFDF3;
  color: #039855;
}

.status-badge.ended,
.status-badge.rejected {
  background: #F2F4F7;
  color: #667085;
}

.meta-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16rpx;
  margin-top: 18rpx;
}

.category-chip {
  display: inline-flex;
  align-items: center;
  height: 42rpx;
  padding: 0 18rpx;
  border-radius: 999rpx;
  background: #EEF7F1;
  color: #4C8D67;
  font-size: 22rpx;
}

.audit-text {
  font-size: 22rpx;
}

.audit-text.approved {
  color: #667085;
}

.audit-text.rejected {
  color: #98A2B3;
}

.info-row {
  display: flex;
  align-items: center;
  gap: 14rpx;
  margin-top: 18rpx;
}

.info-icon {
  width: 28rpx;
  height: 28rpx;
}

.info-text {
  font-size: 25rpx;
  color: #4B5563;
  line-height: 1.5;
}

.card-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20rpx;
  margin-top: 24rpx;
  padding-top: 20rpx;
  border-top: 1px solid #F2F4F7;
}

.footer-text {
  flex: 1;
  font-size: 22rpx;
  color: #98A2B3;
  line-height: 1.5;
}

.action-btn {
  flex-shrink: 0;
  min-width: 140rpx;
  height: 56rpx;
  padding: 0 24rpx;
  border-radius: 999rpx;
  background: #FFF1F3;
  color: #E11D48;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 24rpx;
  font-weight: 600;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 180rpx 48rpx 0;
}

.empty-icon {
  width: 96rpx;
  height: 96rpx;
  opacity: 0.5;
}

.empty-title {
  margin-top: 28rpx;
  font-size: 32rpx;
  font-weight: 700;
  color: #1F2937;
}

.empty-desc {
  margin-top: 12rpx;
  font-size: 24rpx;
  color: #98A2B3;
}

.empty-btn {
  margin-top: 36rpx;
  height: 72rpx;
  padding: 0 36rpx;
  border-radius: 999rpx;
  background: #2B6FF3;
  color: #FFFFFF;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 26rpx;
  font-weight: 600;
}

.loading-state {
  padding: 80rpx 0;
  text-align: center;
  font-size: 24rpx;
  color: #98A2B3;
}
</style>
