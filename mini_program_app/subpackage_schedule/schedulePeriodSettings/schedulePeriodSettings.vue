<template>
  <view class="period-page">
    <nav-bar title="节次时间设置" :showBack="true" fixed placeholder />

    <view class="period-shell">
      <view class="intro-card">
        <view>
          <text class="intro-card__title">节次时间</text>
          <text class="intro-card__desc">修改后会同步到课表左侧时间和课程详情</text>
        </view>
        <text class="intro-badge">{{ periods.length }} 节</text>
      </view>

      <view class="period-card">
        <view class="period-card__head">
          <text class="period-card__title">节次列表</text>
          <text class="period-card__count">开始 - 结束</text>
        </view>

        <view v-for="(period, index) in periods" :key="period.index" class="period-row">
          <view class="period-row__main">
            <text class="period-row__title">第 {{ period.index }} 节</text>
            <text class="period-row__desc">{{ period.start }} - {{ period.end }}</text>
          </view>
          <view class="period-row__time">
            <picker mode="time" :value="period.start" @change="handleTimeChange($event, index, 'start')">
              <view class="time-pill">{{ period.start }}</view>
            </picker>
            <text class="time-separator">-</text>
            <picker mode="time" :value="period.end" @change="handleTimeChange($event, index, 'end')">
              <view class="time-pill">{{ period.end }}</view>
            </picker>
          </view>
        </view>
      </view>

      <view class="tips-card">
        <text class="tips-card__title">说明</text>
        <text class="tips-card__line">这里只修改时间显示，不改变课程属于第几节。</text>
        <text class="tips-card__line">每节课的开始时间必须早于结束时间。</text>
      </view>

      <button class="reset-btn" :disabled="saving" @click="resetToDefault">恢复默认时间</button>
      <button class="save-btn" :loading="saving" @click="savePeriods">保存节次时间</button>
    </view>
  </view>
</template>

<script>
import NavBar from '@/components/nav-bar/nav-bar.vue'
import { getSchedulePeriods, updateSchedulePeriods } from '@/api/schedule.js'

const DEFAULT_PERIODS = [
  { index: 1, start: '08:00', end: '08:45' },
  { index: 2, start: '08:55', end: '09:40' },
  { index: 3, start: '10:00', end: '10:45' },
  { index: 4, start: '10:55', end: '11:40' },
  { index: 5, start: '14:30', end: '15:15' },
  { index: 6, start: '15:25', end: '16:10' },
  { index: 7, start: '16:20', end: '17:05' },
  { index: 8, start: '17:15', end: '18:00' },
  { index: 9, start: '18:30', end: '19:15' },
  { index: 10, start: '19:25', end: '20:10' }
]

const cloneDefaultPeriods = () => DEFAULT_PERIODS.map((item) => ({ ...item }))

const normalizePeriods = (periods) => {
  const source = Array.isArray(periods) && periods.length ? periods : DEFAULT_PERIODS
  return source
    .map((item, index) => ({
      index: Number(item.index || item.periodIndex || index + 1),
      start: item.start || item.startTime || '',
      end: item.end || item.endTime || ''
    }))
    .filter((item) => item.index && item.start && item.end)
    .sort((a, b) => a.index - b.index)
}

const minutesOf = (value) => {
  const [hour, minute] = String(value || '').split(':').map((item) => Number(item))
  if (!Number.isFinite(hour) || !Number.isFinite(minute)) return NaN
  return hour * 60 + minute
}

export default {
  components: { NavBar },
  data() {
    return {
      saving: false,
      periods: cloneDefaultPeriods()
    }
  },
  onLoad() {
    this.loadPeriods()
  },
  methods: {
    async loadPeriods() {
      try {
        uni.showLoading({ title: '加载中...' })
        const res = await getSchedulePeriods()
        this.periods = normalizePeriods(res.data)
      } catch (error) {
        uni.showToast({ title: error?.msg || error?.message || '加载失败', icon: 'none' })
      } finally {
        uni.hideLoading()
      }
    },
    handleTimeChange(e, index, field) {
      const list = [...this.periods]
      list[index] = {
        ...list[index],
        [field]: e.detail.value
      }
      this.periods = list
    },
    validatePeriods() {
      for (const period of this.periods) {
        if (!period.start || !period.end) {
          uni.showToast({ title: `请补全第 ${period.index} 节时间`, icon: 'none' })
          return false
        }
        if (!(minutesOf(period.start) < minutesOf(period.end))) {
          uni.showToast({ title: `第 ${period.index} 节开始时间需早于结束时间`, icon: 'none' })
          return false
        }
      }
      return true
    },
    resetToDefault() {
      uni.showModal({
        title: '恢复默认时间',
        content: '会把 1-10 节恢复为系统默认作息时间。',
        confirmText: '恢复',
        success: (res) => {
          if (res.confirm) {
            this.periods = cloneDefaultPeriods()
          }
        }
      })
    },
    async savePeriods() {
      if (!this.validatePeriods()) return
      this.saving = true
      try {
        const res = await updateSchedulePeriods({
          periods: this.periods.map((item) => ({
            periodIndex: Number(item.index),
            startTime: item.start,
            endTime: item.end
          }))
        })
        this.periods = normalizePeriods(res.data)
        uni.showToast({ title: '保存成功', icon: 'success' })
      } catch (error) {
        uni.showToast({ title: error?.msg || error?.message || '保存失败', icon: 'none' })
      } finally {
        this.saving = false
      }
    }
  }
}
</script>

<style lang="scss" scoped>
.period-page {
  min-height: 100vh;
  background: linear-gradient(180deg, #eef7ff 0%, #f8fbff 52%, #f7f9fb 100%);
}

.period-shell {
  padding: 30rpx 24rpx 44rpx;
  box-sizing: border-box;
}

.intro-card,
.period-card,
.tips-card {
  background: rgba(255, 255, 255, 0.96);
  border-radius: 24rpx;
  box-shadow: 0 14rpx 32rpx rgba(65, 102, 153, 0.08);
}

.intro-card {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20rpx;
  padding: 26rpx 28rpx;
  margin-bottom: 22rpx;
  border: 2rpx solid rgba(220, 233, 251, 0.78);
  box-sizing: border-box;
}

.intro-card__title,
.period-card__title,
.tips-card__title {
  display: block;
  font-size: 30rpx;
  font-weight: 700;
  color: #21334d;
}

.intro-card__desc {
  display: block;
  margin-top: 8rpx;
  font-size: 22rpx;
  color: #7890ad;
}

.intro-badge {
  flex-shrink: 0;
  padding: 9rpx 16rpx;
  border-radius: 999rpx;
  background: #eaf8f3;
  color: #168b6a;
  font-size: 22rpx;
  font-weight: 700;
  white-space: nowrap;
}

.period-card {
  padding: 28rpx;
}

.period-card__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 20rpx;
  margin-bottom: 18rpx;
}

.period-card__count {
  font-size: 22rpx;
  color: #8b98a8;
}

.period-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 18rpx;
  min-height: 96rpx;
  padding: 18rpx 0;
  border-top: 1rpx solid #edf2f7;
  box-sizing: border-box;
}

.period-row:first-of-type {
  border-top: none;
}

.period-row__main {
  flex: 1;
  min-width: 0;
}

.period-row__title {
  display: block;
  font-size: 28rpx;
  font-weight: 700;
  color: #21334d;
}

.period-row__desc {
  display: block;
  margin-top: 8rpx;
  font-size: 22rpx;
  color: #7890ad;
}

.period-row__time {
  flex-shrink: 0;
  display: flex;
  align-items: center;
  gap: 10rpx;
}

.time-pill {
  min-width: 112rpx;
  height: 64rpx;
  padding: 0 16rpx;
  border-radius: 16rpx;
  background: #f7fbff;
  border: 2rpx solid #dce9fb;
  display: flex;
  align-items: center;
  justify-content: center;
  box-sizing: border-box;
  color: #21334d;
  font-size: 24rpx;
  font-weight: 700;
  white-space: nowrap;
}

.time-separator {
  color: #8b98a8;
  font-size: 24rpx;
  font-weight: 700;
}

.tips-card {
  padding: 26rpx 28rpx;
  margin-top: 22rpx;
}

.tips-card__line {
  display: block;
  margin-top: 12rpx;
  font-size: 24rpx;
  line-height: 1.7;
  color: #5d738c;
}

.reset-btn,
.save-btn {
  display: flex;
  align-items: center;
  justify-content: center;
  border: none;
  padding: 0;
  box-sizing: border-box;
  white-space: nowrap;
}

.reset-btn::after,
.save-btn::after {
  border: none;
}

.reset-btn {
  margin-top: 28rpx;
  height: 84rpx;
  border-radius: 18rpx;
  background: #fff2eb;
  color: #d95b37;
  font-size: 28rpx;
  font-weight: 700;
}

.save-btn {
  margin-top: 20rpx;
  height: 92rpx;
  border-radius: 999rpx;
  background: linear-gradient(135deg, #3f7df2, #23b08a);
  color: #fff;
  font-size: 30rpx;
  font-weight: 700;
  box-shadow: 0 18rpx 32rpx rgba(63, 125, 242, 0.18);
}
</style>
