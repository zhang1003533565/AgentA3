<template>
  <view class="dorm-list-page">
    <nav-bar title="宿舍楼" />

    <scroll-view class="list-scroll" scroll-y :style="{ paddingTop: navBarHeight + 'px' }">
      <view class="card">
        <text class="section-title">筛选条件</text>
        <view class="chips-row">
          <view
            v-for="(item, index) in genderOptions"
            :key="index"
            class="chip"
            :class="{ active: currentGender === item.value }"
            @click="currentGender = item.value"
          >
            <text class="chip-text">{{ item.label }}</text>
          </view>
        </view>
      </view>

      <view class="card">
        <text class="section-title">宿舍楼列表</text>
        <view v-for="(item, index) in filteredDorms" :key="index" class="dorm-item" @click="goDetail(item)">
          <image class="dorm-image" :src="item.image" mode="aspectFill" />
          <view class="dorm-main">
            <view class="dorm-head">
              <text class="dorm-name">{{ item.name }}</text>
              <text class="dorm-tag">{{ item.genderLabel }}</text>
            </view>
            <text class="dorm-meta">{{ item.floorCount }}层 · {{ item.roomCount }}间房</text>
            <text class="dorm-meta">空余床位：{{ item.freeBeds }}</text>
          </view>
          <text class="dorm-arrow">></text>
        </view>
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
      currentGender: 'all',
      genderOptions: [
        { label: '全部', value: 'all' },
        { label: '男生宿舍', value: 'male' },
        { label: '女生宿舍', value: 'female' }
      ],
      dormList: [
        { id: '6', name: '学生宿舍1号楼', gender: 'male', genderLabel: '男生宿舍', floorCount: 10, roomCount: 240, freeBeds: 36, image: 'https://picsum.photos/seed/dormA/300/220' },
        { id: '61', name: '学生宿舍2号楼', gender: 'female', genderLabel: '女生宿舍', floorCount: 11, roomCount: 260, freeBeds: 18, image: 'https://picsum.photos/seed/dormB/300/220' }
      ]
    }
  },
  computed: {
    filteredDorms() {
      if (this.currentGender === 'all') return this.dormList
      return this.dormList.filter((item) => item.gender === this.currentGender)
    }
  },
  onLoad() {
    const sys = uni.getSystemInfoSync()
    this.navBarHeight = (sys.statusBarHeight || 0) + 44
  },
  methods: {
    goDetail(item) {
      uni.navigateTo({
        url: `/subpackage_dormitory/dormitoryDetail/dormitoryDetail?id=${item.id}`
      })
    }
  }
}
</script>

<style lang="scss" scoped>
.dorm-list-page { min-height: 100vh; background: #f6f7fb; }
.list-scroll { height: 100vh; }
.card { margin: 24rpx; padding: 24rpx; border-radius: 18rpx; background: #fff; }
.section-title { font-size: 30rpx; font-weight: 700; color: #1f2329; display: block; margin-bottom: 18rpx; }
.chips-row { display: flex; gap: 14rpx; }
.chip { height: 56rpx; border-radius: 999rpx; background: #f2f3f5; padding: 0 22rpx; display: flex; align-items: center; justify-content: center; }
.chip.active { background: #165dff; }
.chip-text { font-size: 24rpx; color: #4e5969; }
.chip.active .chip-text { color: #fff; }
.dorm-item { display: flex; align-items: center; padding: 18rpx 0; border-bottom: 1rpx solid #f2f3f5; }
.dorm-item:last-child { border-bottom: none; }
.dorm-image { width: 132rpx; height: 98rpx; border-radius: 14rpx; margin-right: 16rpx; background: #eee; flex-shrink: 0; }
.dorm-main { flex: 1; min-width: 0; }
.dorm-head { display: flex; justify-content: space-between; align-items: center; gap: 10rpx; }
.dorm-name { font-size: 28rpx; font-weight: 600; color: #1f2329; }
.dorm-tag { font-size: 22rpx; color: #165dff; background: #eef3ff; border-radius: 999rpx; padding: 4rpx 12rpx; }
.dorm-meta { margin-top: 8rpx; display: block; font-size: 22rpx; color: #86909c; }
.dorm-arrow { margin-left: 10rpx; color: #c9cdd4; font-size: 28rpx; }
.bottom-gap { height: 40rpx; }
</style>
