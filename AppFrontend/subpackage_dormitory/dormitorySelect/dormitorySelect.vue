<template>
  <view class="dorm-select-page">
    <nav-bar title="宿舍选择" />

    <scroll-view class="select-scroll" scroll-y :style="{ paddingTop: navBarHeight + 'px' }">
      <view class="card">
        <text class="section-title">选择房间</text>
        <scroll-view class="room-scroll" scroll-x :show-scrollbar="false">
          <view class="room-row">
            <view
              v-for="(room, index) in rooms"
              :key="index"
              class="room-chip"
              :class="{ active: currentRoomNo === room.roomNo }"
              @click="selectRoom(room)"
            >
              <text class="room-chip-text">{{ room.roomNo }}</text>
              <text class="room-chip-sub">余{{ room.freeBeds }}床</text>
            </view>
          </view>
        </scroll-view>
      </view>

      <view class="card">
        <text class="section-title">选择床位</text>
        <view class="bed-grid">
          <view
            v-for="(bed, index) in currentBeds"
            :key="index"
            class="bed-item"
            :class="[bed.status, { active: currentBedNo === bed.bedNo }]"
            @click="selectBed(bed)"
          >
            <text class="bed-no">{{ bed.bedNo }}号床</text>
            <text class="bed-status">{{ bed.statusText }}</text>
          </view>
        </view>
      </view>

      <view class="card">
        <text class="section-title">确认信息</text>
        <view class="confirm-row"><text class="confirm-label">宿舍房间</text><text class="confirm-value">{{ currentRoomNo || '--' }}</text></view>
        <view class="confirm-row"><text class="confirm-label">选择床位</text><text class="confirm-value">{{ currentBedNo ? currentBedNo + '号床' : '--' }}</text></view>
        <button class="submit-btn" type="primary" @click="confirmSelect">确认选择</button>
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
      dormId: '',
      rooms: [
        {
          roomNo: 'D1-503',
          freeBeds: 1,
          beds: [
            { bedNo: '1', status: 'occupied', statusText: '已入住' },
            { bedNo: '2', status: 'available', statusText: '可选' },
            { bedNo: '3', status: 'occupied', statusText: '已入住' },
            { bedNo: '4', status: 'occupied', statusText: '已入住' }
          ]
        },
        {
          roomNo: 'D1-505',
          freeBeds: 2,
          beds: [
            { bedNo: '1', status: 'available', statusText: '可选' },
            { bedNo: '2', status: 'occupied', statusText: '已入住' },
            { bedNo: '3', status: 'available', statusText: '可选' },
            { bedNo: '4', status: 'occupied', statusText: '已入住' }
          ]
        }
      ],
      currentRoomNo: '',
      currentBedNo: ''
    }
  },
  computed: {
    currentBeds() {
      const room = this.rooms.find((r) => r.roomNo === this.currentRoomNo)
      return room ? room.beds : []
    }
  },
  onLoad(options) {
    const sys = uni.getSystemInfoSync()
    this.navBarHeight = (sys.statusBarHeight || 0) + 44
    this.dormId = options.dormId || ''
    this.currentRoomNo = options.roomNo || (this.rooms[0] && this.rooms[0].roomNo) || ''
    this.currentBedNo = options.bedNo || ''
  },
  methods: {
    selectRoom(room) {
      this.currentRoomNo = room.roomNo
      this.currentBedNo = ''
    },
    selectBed(bed) {
      if (bed.status !== 'available') {
        uni.showToast({ title: '该床位已被选择', icon: 'none' })
        return
      }
      this.currentBedNo = bed.bedNo
    },
    confirmSelect() {
      if (!this.currentRoomNo || !this.currentBedNo) {
        uni.showToast({ title: '请先选择房间和床位', icon: 'none' })
        return
      }
      uni.showToast({ title: '宿舍选择成功', icon: 'success' })
      setTimeout(() => {
        uni.navigateBack()
      }, 400)
    }
  }
}
</script>

<style lang="scss" scoped>
.dorm-select-page { min-height: 100vh; background: #f6f7fb; }
.select-scroll { height: 100vh; }
.card { margin: 24rpx; padding: 24rpx; border-radius: 18rpx; background: #fff; }
.section-title { font-size: 30rpx; font-weight: 700; color: #1f2329; display: block; margin-bottom: 18rpx; }
.room-scroll { width: 100%; }
.room-row { display: inline-flex; padding-right: 10rpx; }
.room-chip { min-width: 170rpx; height: 88rpx; border-radius: 14rpx; background: #f2f3f5; padding: 0 18rpx; margin-right: 14rpx; display: flex; flex-direction: column; align-items: flex-start; justify-content: center; }
.room-chip.active { background: #e8f3ff; border: 1rpx solid #165dff; }
.room-chip-text { font-size: 24rpx; color: #1f2329; font-weight: 600; }
.room-chip-sub { margin-top: 4rpx; font-size: 20rpx; color: #86909c; }
.bed-grid { display: grid; grid-template-columns: 1fr 1fr; gap: 14rpx; }
.bed-item { border-radius: 14rpx; padding: 18rpx; border: 1rpx solid #e5e6eb; }
.bed-item.available { background: #f5ffed; border-color: #95de64; }
.bed-item.occupied { background: #f7f8fa; border-color: #e5e6eb; }
.bed-item.active { border-color: #165dff; box-shadow: 0 0 0 2rpx rgba(22, 93, 255, 0.12) inset; }
.bed-no { display: block; font-size: 24rpx; color: #1f2329; font-weight: 600; }
.bed-status { margin-top: 6rpx; display: block; font-size: 22rpx; color: #86909c; }
.confirm-row { display: flex; justify-content: space-between; align-items: center; padding: 12rpx 0; border-bottom: 1rpx solid #f2f3f5; }
.confirm-row:last-of-type { border-bottom: none; margin-bottom: 14rpx; }
.confirm-label { font-size: 24rpx; color: #86909c; }
.confirm-value { font-size: 24rpx; color: #1f2329; font-weight: 600; }
.submit-btn { margin-top: 12rpx; height: 78rpx; line-height: 78rpx; border-radius: 12rpx; font-size: 28rpx; background: linear-gradient(135deg, #4080ff 0%, #165dff 100%); }
.bottom-gap { height: 40rpx; }
</style>
