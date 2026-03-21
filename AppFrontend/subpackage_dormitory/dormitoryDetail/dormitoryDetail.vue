<template>
  <view class="dorm-detail-page">
    <nav-bar :title="dorm.name || '宿舍详情'" />

    <scroll-view class="detail-scroll" scroll-y :style="{ paddingTop: navBarHeight + 'px' }">
      <view class="hero">
        <image class="hero-image" :src="dorm.image" mode="aspectFill" />
        <view class="hero-mask" />
        <view class="hero-content">
          <text class="hero-name">{{ dorm.name }}</text>
          <text class="hero-meta">{{ dorm.genderLabel }} · {{ dorm.floorCount }}层</text>
        </view>
      </view>

      <view class="card">
        <text class="section-title">宿舍楼基本信息</text>
        <view class="info-row"><text class="label">楼栋编号</text><text class="value">{{ dorm.buildingNo }}</text></view>
        <view class="info-row"><text class="label">宿舍房间数</text><text class="value">{{ dorm.roomCount }}间</text></view>
        <view class="info-row"><text class="label">总床位数</text><text class="value">{{ dorm.totalBeds }}</text></view>
        <view class="info-row"><text class="label">空余床位</text><text class="value">{{ dorm.freeBeds }}</text></view>
        <view class="info-row"><text class="label">管理员电话</text><text class="value">{{ dorm.managerPhone }}</text></view>
      </view>

      <view class="card">
        <view class="title-line">
          <text class="section-title">我的宿舍位置</text>
          <text class="action-link" @click="goSelect">选择/调整</text>
        </view>
        <view v-if="myRoom.roomNo" class="my-room-box">
          <text class="my-room-main">{{ myRoom.roomNo }} · {{ myRoom.bedNo }}号床</text>
          <text class="my-room-sub">楼层：{{ myRoom.floor }}层 · 入住时间：{{ myRoom.checkInDate }}</text>
        </view>
        <view v-else class="empty-tip">你还未选择宿舍床位</view>
      </view>

      <view class="card">
        <text class="section-title">室友查看</text>
        <view v-for="(mate, index) in roommates" :key="index" class="mate-item">
          <view class="avatar">{{ mate.name.slice(0, 1) }}</view>
          <view class="mate-main">
            <text class="mate-name">{{ mate.name }}</text>
            <text class="mate-meta">{{ mate.major }} · {{ mate.grade }}</text>
          </view>
          <text class="mate-bed">{{ mate.bedNo }}号床</text>
        </view>
        <view v-if="!roommates.length" class="empty-tip">暂无室友信息</view>
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
      dorm: {
        id: '',
        name: '',
        image: '',
        genderLabel: '',
        floorCount: 0,
        buildingNo: '',
        roomCount: 0,
        totalBeds: 0,
        freeBeds: 0,
        managerPhone: ''
      },
      myRoom: {
        roomNo: '',
        floor: '',
        bedNo: '',
        checkInDate: ''
      },
      roommates: []
    }
  },
  onLoad(options) {
    const sys = uni.getSystemInfoSync()
    this.navBarHeight = (sys.statusBarHeight || 0) + 44
    this.loadDormitory(options.id)
  },
  methods: {
    loadDormitory(id) {
      const mockData = {
        '6': {
          dorm: {
            id: '6',
            name: '学生宿舍1号楼',
            image: 'https://picsum.photos/seed/dormdetail1/1200/600',
            genderLabel: '男生宿舍',
            floorCount: 10,
            buildingNo: 'D1',
            roomCount: 240,
            totalBeds: 960,
            freeBeds: 36,
            managerPhone: '0755-0000-1001'
          },
          myRoom: { roomNo: 'D1-503', floor: '5', bedNo: '2', checkInDate: '2025-09-01' },
          roommates: [
            { name: '张同学', major: '计算机科学与技术', grade: '2023级', bedNo: '1' },
            { name: '王同学', major: '软件工程', grade: '2023级', bedNo: '3' },
            { name: '李同学', major: '人工智能', grade: '2023级', bedNo: '4' }
          ]
        },
        '61': {
          dorm: {
            id: '61',
            name: '学生宿舍2号楼',
            image: 'https://picsum.photos/seed/dormdetail2/1200/600',
            genderLabel: '女生宿舍',
            floorCount: 11,
            buildingNo: 'D2',
            roomCount: 260,
            totalBeds: 1040,
            freeBeds: 18,
            managerPhone: '0755-0000-1002'
          },
          myRoom: { roomNo: '', floor: '', bedNo: '', checkInDate: '' },
          roommates: []
        }
      }

      const data = mockData[id] || mockData['6']
      this.dorm = data.dorm
      this.myRoom = data.myRoom
      this.roommates = data.roommates
    },
    goSelect() {
      uni.navigateTo({
        url: `/subpackage_dormitory/dormitorySelect/dormitorySelect?dormId=${this.dorm.id}&roomNo=${this.myRoom.roomNo || ''}&bedNo=${this.myRoom.bedNo || ''}`
      })
    }
  }
}
</script>

<style lang="scss" scoped>
.dorm-detail-page { min-height: 100vh; background: #f6f7fb; }
.detail-scroll { height: 100vh; }
.hero { margin: 24rpx 24rpx 0; position: relative; height: 280rpx; border-radius: 22rpx; overflow: hidden; }
.hero-image { width: 100%; height: 100%; }
.hero-mask { position: absolute; left: 0; right: 0; top: 0; bottom: 0; background: linear-gradient(180deg, rgba(0,0,0,0.05) 0%, rgba(0,0,0,0.45) 100%); }
.hero-content { position: absolute; left: 24rpx; right: 24rpx; bottom: 20rpx; }
.hero-name { display: block; font-size: 38rpx; font-weight: 700; color: #fff; }
.hero-meta { margin-top: 8rpx; display: block; font-size: 24rpx; color: rgba(255,255,255,0.9); }
.card { margin: 24rpx; padding: 26rpx; border-radius: 18rpx; background: #fff; box-shadow: 0 10rpx 24rpx rgba(31, 35, 41, 0.05); }
.section-title { font-size: 30rpx; font-weight: 700; color: #1f2329; display: block; margin-bottom: 18rpx; }
.title-line { display: flex; justify-content: space-between; align-items: center; margin-bottom: 8rpx; }
.action-link { font-size: 22rpx; color: #165dff; }
.info-row { display: flex; justify-content: space-between; align-items: flex-start; padding: 14rpx 0; border-bottom: 1rpx solid #f2f3f5; gap: 16rpx; }
.info-row:last-child { border-bottom: none; }
.label { font-size: 24rpx; color: #86909c; width: 170rpx; flex-shrink: 0; }
.value { font-size: 24rpx; color: #1f2329; text-align: right; line-height: 1.6; }
.my-room-box { background: #f7f8fa; border-radius: 14rpx; padding: 18rpx; }
.my-room-main { display: block; font-size: 28rpx; color: #1f2329; font-weight: 600; }
.my-room-sub { margin-top: 8rpx; display: block; font-size: 22rpx; color: #86909c; }
.mate-item { display: flex; align-items: center; padding: 16rpx 0; border-bottom: 1rpx solid #f2f3f5; }
.mate-item:last-child { border-bottom: none; }
.avatar { width: 54rpx; height: 54rpx; border-radius: 999rpx; background: #d9e6ff; color: #165dff; display: flex; align-items: center; justify-content: center; font-size: 24rpx; font-weight: 600; margin-right: 14rpx; }
.mate-main { flex: 1; min-width: 0; }
.mate-name { display: block; font-size: 26rpx; color: #1f2329; font-weight: 600; }
.mate-meta { margin-top: 6rpx; display: block; font-size: 22rpx; color: #86909c; }
.mate-bed { font-size: 22rpx; color: #165dff; }
.empty-tip { padding: 20rpx 0 6rpx; text-align: center; font-size: 24rpx; color: #86909c; }
.bottom-gap { height: 40rpx; }
</style>
