<template>
  <view class="building-detail-page">
    <nav-bar :title="building.name || '教学楼详情'" fixed placeholder />

    <scroll-view class="detail-scroll" scroll-y :style="{ height: `calc(100vh - ${navBarHeight}px)` }">
      <view class="hero">
        <image class="hero-image" :src="building.image" mode="aspectFill" />
        <view class="hero-mask" />
        <view class="hero-content">
          <text class="hero-name">{{ building.name }}</text>
          <text class="hero-meta">{{ building.zone }} · {{ building.floorCount }}层</text>
        </view>
      </view>

      <view class="card">
        <text class="section-title">教学楼基本信息</text>
        <view class="info-row"><text class="label">楼宇编号</text><text class="value">{{ building.buildingNo }}</text></view>
        <view class="info-row"><text class="label">教室数量</text><text class="value">{{ building.classroomCount }}间</text></view>
        <view class="info-row"><text class="label">总座位数</text><text class="value">{{ building.totalSeatCount }}</text></view>
        <view class="info-row"><text class="label">多媒体教室</text><text class="value">{{ building.smartClassroomCount }}间</text></view>
        <view class="info-row"><text class="label">开放时间</text><text class="value">{{ building.openTime }}</text></view>
      </view>

      <view class="card">
        <text class="section-title">使用状态查看</text>
        <view class="status-grid">
          <view class="status-item"><text class="status-label">当前状态</text><text class="status-value" :class="building.statusClass">{{ building.statusText }}</text></view>
          <view class="status-item"><text class="status-label">当前使用教室</text><text class="status-value">{{ building.activeClassroomCount }}间</text></view>
          <view class="status-item"><text class="status-label">实时使用率</text><text class="status-value">{{ building.usageRate }}</text></view>
          <view class="status-item"><text class="status-label">今日空闲教室</text><text class="status-value">{{ building.freeClassroomCount }}间</text></view>
        </view>
      </view>

      <view class="card">
        <view class="title-line">
          <text class="section-title">课程安排关联</text>
          <text class="date-tag">{{ currentDateLabel }}</text>
        </view>
        <view v-for="(course, index) in building.courseSchedule" :key="index" class="course-item">
          <view class="course-left">
            <text class="course-time">{{ course.time }}</text>
            <text class="course-room">{{ course.room }}</text>
          </view>
          <view class="course-main">
            <text class="course-name">{{ course.courseName }}</text>
            <text class="course-meta">{{ course.teacher }} · {{ course.className }}</text>
            <text class="course-meta">上课人数：{{ course.studentCount }} / 教室座位：{{ course.seatCount }}</text>
          </view>
          <text class="course-status" :class="course.statusClass">{{ course.statusText }}</text>
        </view>
        <view v-if="!building.courseSchedule.length" class="empty-tip">今日暂无课程安排</view>
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
      currentDateLabel: '今日课表',
      building: {
        id: '',
        name: '',
        image: '',
        buildingNo: '',
        zone: '',
        floorCount: 0,
        classroomCount: 0,
        totalSeatCount: 0,
        smartClassroomCount: 0,
        openTime: '',
        statusText: '',
        statusClass: 'ok',
        activeClassroomCount: 0,
        usageRate: '--',
        freeClassroomCount: 0,
        courseSchedule: []
      }
    }
  },
  onLoad(options) {
    const sys = uni.getSystemInfoSync()
    this.navBarHeight = (sys.statusBarHeight || 0) + 44
    this.loadBuilding(options.id)
  },
  methods: {
    loadBuilding(id) {
      const mockData = {
        '1': {
          id: '1',
          name: '教学楼A栋',
          image: 'https://picsum.photos/seed/teachingA/1200/600',
          buildingNo: 'A-01',
          zone: '北区',
          floorCount: 8,
          classroomCount: 42,
          totalSeatCount: 3120,
          smartClassroomCount: 16,
          openTime: '07:00 - 22:30',
          statusText: '使用高峰',
          statusClass: 'busy',
          activeClassroomCount: 31,
          usageRate: '74%',
          freeClassroomCount: 11,
          courseSchedule: [
            { time: '08:00-09:40', room: 'A-302', courseName: '数据结构', teacher: '陈老师', className: '计科22-1', studentCount: 82, seatCount: 100, statusText: '进行中', statusClass: 'busy' },
            { time: '10:10-11:50', room: 'A-405', courseName: '软件工程', teacher: '王老师', className: '软工22-2', studentCount: 66, seatCount: 80, statusText: '即将开始', statusClass: 'ok' },
            { time: '14:00-15:40', room: 'A-210', courseName: '大学英语', teacher: '李老师', className: '经管23-3', studentCount: 54, seatCount: 60, statusText: '待上课', statusClass: 'ok' }
          ]
        },
        '2': {
          id: '2',
          name: '教学楼B栋',
          image: 'https://picsum.photos/seed/teachingB/1200/600',
          buildingNo: 'B-02',
          zone: '北区',
          floorCount: 7,
          classroomCount: 36,
          totalSeatCount: 2680,
          smartClassroomCount: 12,
          openTime: '07:00 - 22:00',
          statusText: '空闲较多',
          statusClass: 'ok',
          activeClassroomCount: 17,
          usageRate: '47%',
          freeClassroomCount: 19,
          courseSchedule: [
            { time: '08:00-09:40', room: 'B-201', courseName: '高等数学', teacher: '张老师', className: '电信23-1', studentCount: 88, seatCount: 110, statusText: '进行中', statusClass: 'busy' },
            { time: '16:10-17:50', room: 'B-503', courseName: '概率论', teacher: '何老师', className: '统计22-1', studentCount: 58, seatCount: 70, statusText: '待上课', statusClass: 'ok' }
          ]
        }
      }

      this.building = mockData[id] || {
        id: id || '',
        name: '教学楼详情',
        image: 'https://picsum.photos/seed/teachingDefault/1200/600',
        buildingNo: '未知',
        zone: '未知',
        floorCount: 0,
        classroomCount: 0,
        totalSeatCount: 0,
        smartClassroomCount: 0,
        openTime: '暂无',
        statusText: '未知',
        statusClass: 'ok',
        activeClassroomCount: 0,
        usageRate: '--',
        freeClassroomCount: 0,
        courseSchedule: []
      }
    }
  }
}
</script>

<style lang="scss" scoped>
.building-detail-page { min-height: 100vh; background: #f6f7fb; }
.detail-scroll { min-height: 0; }
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
