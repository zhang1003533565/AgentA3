<template>
  <view class="building-detail-page">
    <nav-bar :title="building.name || '教学楼详情'" fixed placeholder />

    <scroll-view class="detail-scroll" scroll-y :style="{ height: `calc(100vh - ${navBarHeight}px)` }">
      <view v-if="building.image" class="hero">
        <image class="hero-image" :src="building.image" mode="aspectFill" />
        <view class="hero-mask" />
        <view class="hero-content">
          <text class="hero-name">{{ building.name }}</text>
          <text class="hero-meta">{{ building.zone }} · {{ building.floorCount }}层</text>
        </view>
      </view>
      <view v-else class="hero-plain">
        <text class="hero-name">{{ building.name }}</text>
        <text class="hero-meta">{{ building.zone }} · {{ building.floorCount }}层</text>
      </view>

      <view class="card">
        <text class="section-title">教学楼基本信息</text>
        <view class="info-row"><text class="label">教室数量</text><text class="value">{{ building.classroomCount }}间</text></view>
        <view class="info-row"><text class="label">总座位数</text><text class="value">{{ building.totalSeatCount }}</text></view>
        <view class="info-row"><text class="label">多媒体教室</text><text class="value">{{ building.smartClassroomCount }}间</text></view>
      </view>

      <view class="card">
        <text class="section-title">使用状态查看</text>
        <view class="status-grid">
          <view class="status-item"><text class="status-label">当前状态</text><text class="status-value" :class="building.statusClass">{{ building.statusText }}</text></view>
          <view class="status-item"><text class="status-label">当前使用教室</text><text class="status-value">{{ building.activeClassroomCount }}间</text></view>
          <view class="status-item"><text class="status-label">当前使用率</text><text class="status-value">{{ usageRate }}</text></view>
          <view class="status-item"><text class="status-label">今日空闲教室</text><text class="status-value">{{ building.freeClassroomCount }}间</text></view>
        </view>
      </view>

      <view class="card">
        <view class="title-line">
          <text class="section-title">教室列表</text>
          <text class="date-tag">{{ building.classroomCount }} 间</text>
        </view>
        <view v-for="room in building.classrooms" :key="room.id" class="course-item">
          <view class="course-left">
            <text class="course-time">{{ room.floorNo }} 层</text>
            <text class="course-room">{{ room.roomNo }}</text>
          </view>
          <view class="course-main">
            <text class="course-name">{{ room.smart ? '多媒体教室' : '普通教室' }}</text>
            <text class="course-meta">座位数：{{ room.seatCount }}</text>
            <text v-if="room.openTime" class="course-meta">开放时间：{{ room.openTime }}</text>
          </view>
          <text class="course-status" :class="room.statusClass">{{ room.statusText }}</text>
        </view>
        <view v-if="!building.classrooms.length" class="empty-tip">暂无教室数据</view>
      </view>
      <view class="bottom-gap" />
    </scroll-view>
  </view>
</template>

<script>
import NavBar from '@/components/nav-bar/nav-bar.vue'
import { getTeachingBuilding } from '@/api/teaching.js'

export default {
  components: { NavBar },
  data() {
    return {
      navBarHeight: 88,
      building: {
        id: '',
        name: '',
        image: '',
        zone: '',
        floorCount: 0,
        classroomCount: 0,
        totalSeatCount: 0,
        smartClassroomCount: 0,
        statusText: '',
        statusClass: 'ok',
        activeClassroomCount: 0,
        freeClassroomCount: 0,
        classrooms: []
      }
    }
  },
  computed: {
    usageRate() {
      if (!this.building.classroomCount) return '--'
      return `${Math.round((this.building.activeClassroomCount / this.building.classroomCount) * 100)}%`
    }
  },
  onLoad(options) {
    const sys = uni.getSystemInfoSync()
    this.navBarHeight = (sys.statusBarHeight || 0) + 44
    this.loadBuilding(options.id)
  },
  methods: {
    async loadBuilding(id) {
      try {
        const res = await getTeachingBuilding(id)
        const data = res?.data
        if (!data) return
        this.building = {
          ...this.building,
          ...data,
          zone: data.zone || '未设置区域',
          statusText: this.getBuildingStatusText(data.status),
          statusClass: this.getBuildingStatusClass(data.status),
          classrooms: (data.classrooms || []).map((room) => ({
            ...room,
            statusText: this.getRoomStatusText(room.status),
            statusClass: room.status === 1 ? 'ok' : (room.status === 2 ? 'busy' : 'warn')
          }))
        }
      } catch (error) {
        console.error('加载教学楼详情失败', error)
      }
    },
    getBuildingStatusText(status) {
      return ({ 1: '正常开放', 2: '维护中', 3: '已关闭' })[status] || '状态未知'
    },
    getBuildingStatusClass(status) {
      return status === 1 ? 'ok' : (status === 2 ? 'warn' : 'busy')
    },
    getRoomStatusText(status) {
      return ({ 1: '空闲', 2: '使用中', 3: '维护中' })[status] || '未知'
    }
  }
}
</script>

<style lang="scss" scoped>
.building-detail-page { min-height: 100vh; background: #f6f7fb; }
.detail-scroll { min-height: 0; }
.hero-plain {
  margin: 24rpx 24rpx 0;
  padding: 28rpx;
  border-radius: 22rpx;
  background: linear-gradient(135deg, #7aa8ff 0%, #4b84f6 100%);
}
.hero-plain .hero-name,
.hero-plain .hero-meta {
  color: #fff;
}
.hero-plain .hero-meta {
  display: block;
  margin-top: 8rpx;
  font-size: 24rpx;
}
.hero { margin: 24rpx 24rpx 0; position: relative; height: 280rpx; border-radius: 22rpx; overflow: hidden; }
.hero-image { width: 100%; height: 100%; }
.hero-mask { position: absolute; left: 0; right: 0; top: 0; bottom: 0; background: linear-gradient(180deg, rgba(0,0,0,0.05) 0%, rgba(0,0,0,0.45) 100%); }
.hero-content { position: absolute; left: 24rpx; right: 24rpx; bottom: 20rpx; }
.hero-name { display: block; font-size: 38rpx; font-weight: 700; color: #fff; }
.hero-meta { margin-top: 8rpx; display: block; font-size: 24rpx; color: rgba(255,255,255,0.9); }
.card { margin: 24rpx; padding: 26rpx; border-radius: 18rpx; background: #fff; box-shadow: 0 10rpx 24rpx rgba(31, 35, 41, 0.05); }
.section-title { font-size: 30rpx; font-weight: 700; color: #1f2329; display: block; margin-bottom: 18rpx; }
.title-line { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8rpx; }
.date-tag { font-size: 22rpx; color: #165dff; background: #eef3ff; padding: 4rpx 12rpx; border-radius: 999rpx; }
.info-row { display: flex; justify-content: space-between; align-items: flex-start; padding: 14rpx 0; border-bottom: 1rpx solid #f2f3f5; gap: 16rpx; }
.info-row:last-child { border-bottom: none; }
.label { font-size: 24rpx; color: #86909c; width: 170rpx; flex-shrink: 0; }
.value { font-size: 24rpx; color: #1f2329; text-align: right; line-height: 1.6; }
.status-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 14rpx; }
.status-item { background: #f7f8fa; border-radius: 14rpx; padding: 18rpx; }
.status-label { display: block; font-size: 22rpx; color: #86909c; }
.status-value { display: block; margin-top: 10rpx; font-size: 26rpx; color: #1f2329; font-weight: 600; }
.status-value.ok { color: #00b42a; }
.status-value.busy { color: #ff7d00; }
.status-value.warn { color: #f53f3f; }
.course-item { display: flex; align-items: flex-start; padding: 18rpx 0; border-bottom: 1rpx solid #f2f3f5; }
.course-item:last-child { border-bottom: none; }
.course-left { width: 170rpx; flex-shrink: 0; }
.course-time { display: block; font-size: 22rpx; color: #1f2329; font-weight: 600; }
.course-room { display: block; margin-top: 6rpx; font-size: 22rpx; color: #86909c; }
.course-main { flex: 1; min-width: 0; }
.course-name { display: block; font-size: 26rpx; color: #1f2329; font-weight: 600; }
.course-meta { display: block; margin-top: 6rpx; font-size: 22rpx; color: #86909c; }
.course-status { margin-left: 10rpx; font-size: 22rpx; padding: 4rpx 12rpx; border-radius: 999rpx; }
.course-status.ok { color: #00b42a; background: #e8ffea; }
.course-status.busy { color: #ff7d00; background: #fff3e8; }
.empty-tip { padding: 20rpx 0 6rpx; text-align: center; font-size: 24rpx; color: #86909c; }
.bottom-gap { height: 40rpx; }
</style>
