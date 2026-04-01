<template>
  <view class="sports-detail-page">
    <nav-bar :title="field.name || '场地详情'" fixed placeholder />

    <scroll-view class="detail-scroll" scroll-y :style="{ height: `calc(100vh - ${navBarHeight}px)` }">
      <view class="hero">
        <image class="hero-image" :src="field.image" mode="aspectFill" />
        <view class="hero-mask" />
        <view class="hero-content">
          <text class="hero-name">{{ field.name }}</text>
          <text class="hero-meta">{{ field.type }} · {{ field.location }}</text>
        </view>
      </view>

      <view class="card">
        <text class="section-title">基本信息</text>
        <view class="info-row"><text class="label">开放时间</text><text class="value">{{ field.openTime }}</text></view>
        <view class="info-row"><text class="label">可容纳人数</text><text class="value">{{ field.capacity }}人</text></view>
        <view class="info-row"><text class="label">收费标准</text><text class="value">{{ field.fee }}</text></view>
        <view class="info-row"><text class="label">简介</text><text class="value">{{ field.description }}</text></view>
      </view>

      <view class="card">
        <view class="title-line">
          <text class="section-title">使用状态</text>
          <text class="refresh-btn" @click="refreshStatus">刷新</text>
        </view>
        <view class="status-grid">
          <view class="status-item"><text class="status-label">当前状态</text><text class="status-value" :class="field.statusClass">{{ field.statusText }}</text></view>
          <view class="status-item"><text class="status-label">今日预约</text><text class="status-value">{{ field.todayBooked }}场</text></view>
          <view class="status-item"><text class="status-label">高峰时段</text><text class="status-value">{{ field.peakTime }}</text></view>
          <view class="status-item"><text class="status-label">空闲率</text><text class="status-value">{{ field.freeRate }}</text></view>
        </view>
      </view>

      <view class="card">
        <text class="section-title">场地预约</text>
        <picker mode="selector" :range="dateOptions" @change="onDateChange">
          <view class="picker-line">
            <text class="picker-label">预约日期</text>
            <text class="picker-value">{{ booking.date || '请选择' }}</text>
          </view>
        </picker>
        <picker mode="selector" :range="timeSlots" @change="onTimeChange">
          <view class="picker-line">
            <text class="picker-label">预约时段</text>
            <text class="picker-value">{{ booking.time || '请选择' }}</text>
          </view>
        </picker>
        <view class="picker-line">
          <text class="picker-label">预约人数</text>
          <input class="count-input" type="number" v-model="booking.count" placeholder="请输入人数" />
        </view>
        <button class="reserve-btn" type="primary" @click="submitBooking">提交预约</button>
      </view>
      <view class="bottom-gap" />
    </scroll-view>
  </view>
</template>

<script>
import NavBar from '@/components/nav-bar/nav-bar.vue'

export default {
  components: { NavBar },
  data() {
    return {
      navBarHeight: 88,
      field: {
        id: '',
        name: '',
        type: '',
        location: '',
        openTime: '',
        capacity: 0,
        fee: '',
        description: '',
        image: '',
        statusText: '',
        statusClass: 'ok',
        todayBooked: 0,
        peakTime: '',
        freeRate: ''
      },
      dateOptions: ['今天', '明天', '后天'],
      timeSlots: ['08:00-10:00', '10:00-12:00', '14:00-16:00', '16:00-18:00', '19:00-21:00'],
      booking: {
        date: '',
        time: '',
        count: ''
      }
    }
  },
  onLoad(options) {
    const sys = uni.getSystemInfoSync()
    this.navBarHeight = (sys.statusBarHeight || 0) + 44
    this.loadField(options.id)
  },
  methods: {
    loadField(id) {
      const mockData = {
        '7': {
          id: '7',
          name: '体育馆',
          type: '综合馆',
          location: '西区文体中心',
          openTime: '08:00 - 22:00',
          capacity: 800,
          fee: '学生5元/小时',
          description: '包含篮球馆、羽毛球馆、游泳馆，可在线预约分时段使用。',
          image: 'https://picsum.photos/seed/sportsdetail7/1200/600',
          statusText: '可预约',
          statusClass: 'ok',
          todayBooked: 26,
          peakTime: '19:00-21:00',
          freeRate: '43%'
        },
        '8': {
          id: '8',
          name: '田径场',
          type: '田径',
          location: '东区操场',
          openTime: '06:00 - 22:00',
          capacity: 1200,
          fee: '免费开放',
          description: '标准400米塑胶跑道，设有足球训练区和看台。',
          image: 'https://picsum.photos/seed/sportsdetail8/1200/600',
          statusText: '部分占用',
          statusClass: 'busy',
          todayBooked: 14,
          peakTime: '17:00-20:00',
          freeRate: '58%'
        }
      }

      this.field = mockData[id] || {
        id: id || '',
        name: '场地详情',
        type: '未知类型',
        location: '未知位置',
        openTime: '暂无',
        capacity: 0,
        fee: '暂无',
        description: '暂无数据',
        image: 'https://picsum.photos/seed/sportsdefault/1200/600',
        statusText: '未知',
        statusClass: 'busy',
        todayBooked: 0,
        peakTime: '--',
        freeRate: '--'
      }
    },
    onDateChange(e) {
      this.booking.date = this.dateOptions[e.detail.value]
    },
    onTimeChange(e) {
      this.booking.time = this.timeSlots[e.detail.value]
    },
    refreshStatus() {
      uni.showToast({ title: '状态已更新', icon: 'none' })
    },
    submitBooking() {
      if (!this.booking.date || !this.booking.time || !this.booking.count) {
        uni.showToast({ title: '请完整填写预约信息', icon: 'none' })
        return
      }
      uni.showToast({ title: '预约提交成功', icon: 'success' })
    }
  }
}
</script>

<style lang="scss" scoped>
.sports-detail-page { min-height: 100vh; background: #f6f7fb; }
  .detail-scroll { min-height: 0; }
.hero { margin: 24rpx 24rpx 0; position: relative; height: 280rpx; border-radius: 22rpx; overflow: hidden; }
.hero-image { width: 100%; height: 100%; }
.hero-mask { position: absolute; left: 0; right: 0; top: 0; bottom: 0; background: linear-gradient(180deg, rgba(0,0,0,0.05) 0%, rgba(0,0,0,0.45) 100%); }
.hero-content { position: absolute; left: 24rpx; right: 24rpx; bottom: 20rpx; }
.hero-name { display: block; font-size: 38rpx; font-weight: 700; color: #fff; }
.hero-meta { margin-top: 8rpx; display: block; font-size: 24rpx; color: rgba(255,255,255,0.9); }
.card { margin: 24rpx; padding: 26rpx; border-radius: 18rpx; background: #fff; box-shadow: 0 10rpx 24rpx rgba(31, 35, 41, 0.05); }
.section-title { font-size: 30rpx; font-weight: 700; color: #1f2329; display: block; margin-bottom: 18rpx; }
.title-line { display: flex; align-items: center; justify-content: space-between; margin-bottom: 12rpx; }
.refresh-btn { font-size: 22rpx; color: #165dff; }
.info-row { display: flex; justify-content: space-between; align-items: flex-start; padding: 14rpx 0; border-bottom: 1rpx solid #f2f3f5; gap: 16rpx; }
.info-row:last-child { border-bottom: none; }
.label { font-size: 24rpx; color: #86909c; width: 150rpx; flex-shrink: 0; }
.value { font-size: 24rpx; color: #1f2329; text-align: right; line-height: 1.6; }
.status-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 14rpx; }
.status-item { background: #f7f8fa; border-radius: 14rpx; padding: 18rpx; }
.status-label { display: block; font-size: 22rpx; color: #86909c; }
.status-value { display: block; margin-top: 10rpx; font-size: 26rpx; color: #1f2329; font-weight: 600; }
.status-value.ok { color: #00b42a; }
.status-value.busy { color: #ff7d00; }
.status-value.close { color: #f53f3f; }
.picker-line { height: 82rpx; border: 1rpx solid #e5e6eb; border-radius: 12rpx; padding: 0 22rpx; display: flex; align-items: center; justify-content: space-between; margin-bottom: 16rpx; background: #fff; }
.picker-label { font-size: 24rpx; color: #4e5969; }
.picker-value { font-size: 24rpx; color: #1f2329; }
.count-input { width: 220rpx; text-align: right; font-size: 24rpx; color: #1f2329; }
.reserve-btn { margin-top: 12rpx; height: 78rpx; line-height: 78rpx; border-radius: 12rpx; font-size: 28rpx; background: linear-gradient(135deg, #4080ff 0%, #165dff 100%); }
.bottom-gap { height: 36rpx; }
</style>
