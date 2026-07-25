<template>
  <view class="sports-list-page">
    <nav-bar title="运动场" />

    <scroll-view class="list-scroll" scroll-y :style="{ paddingTop: navBarHeight + 'px' }">
      <view class="section card">
        <text class="section-title">场地类型</text>
        <scroll-view class="type-scroll" scroll-x :show-scrollbar="false">
          <view class="type-row">
            <view
              v-for="(type, index) in typeOptions"
              :key="index"
              class="type-chip"
              :class="{ active: currentType === type }"
              @click="currentType = type"
            >
              <text class="type-chip-text">{{ type }}</text>
            </view>
          </view>
        </scroll-view>
      </view>

      <view class="section card">
        <text class="section-title">场地列表</text>
        <view v-for="(item, index) in filteredList" :key="index" class="field-item" @click="goDetail(item)">
          <image class="field-image" :src="item.image" mode="aspectFill" />
          <view class="field-main">
            <view class="field-head">
              <text class="field-name">{{ item.name }}</text>
              <text class="field-status" :class="item.statusClass">{{ item.statusText }}</text>
            </view>
            <text class="field-meta">{{ item.type }} · {{ item.openTime }}</text>
            <text class="field-desc">{{ item.description }}</text>
          </view>
          <text class="field-arrow">></text>
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
      currentType: '全部',
      list: [
        { id: '7', name: '体育馆', type: '综合馆', description: '篮球、羽毛球、游泳馆', openTime: '08:00 - 22:00', statusText: '可预约', statusClass: 'ok', image: 'https://picsum.photos/seed/sports7/300/220' },
        { id: '8', name: '田径场', type: '田径', description: '400米标准跑道', openTime: '06:00 - 22:00', statusText: '部分占用', statusClass: 'busy', image: 'https://picsum.photos/seed/sports8/300/220' },
        { id: '81', name: '西区篮球场', type: '篮球', description: '室外标准全场4块', openTime: '07:00 - 22:30', statusText: '维护中', statusClass: 'close', image: 'https://picsum.photos/seed/sports81/300/220' }
      ]
    }
  },
  computed: {
    typeOptions() {
      const options = ['全部']
      const set = new Set()
      this.list.forEach((item) => {
        if (!set.has(item.type)) {
          set.add(item.type)
          options.push(item.type)
        }
      })
      return options
    },
    filteredList() {
      if (this.currentType === '全部') return this.list
      return this.list.filter((item) => item.type === this.currentType)
    }
  },
  onLoad() {
    const sys = uni.getSystemInfoSync()
    this.navBarHeight = (sys.statusBarHeight || 0) + 44
  },
  methods: {
    goDetail(item) {
      uni.navigateTo({
        url: `/subpackage_sports/sportsDetail/sportsDetail?id=${item.id}`
      })
    }
  }
}
</script>

<style lang="scss" scoped>
.sports-list-page { min-height: 100vh; background: #f6f7fb; }
.list-scroll { height: 100vh; }
.card { margin: 24rpx; padding: 24rpx; border-radius: 18rpx; background: #fff; }
.section-title { font-size: 30rpx; font-weight: 700; color: #1f2329; display: block; margin-bottom: 18rpx; }
.type-scroll { width: 100%; }
.type-row { display: inline-flex; padding-right: 10rpx; }
.type-chip { height: 56rpx; border-radius: 999rpx; background: #f2f3f5; padding: 0 22rpx; display: flex; align-items: center; justify-content: center; margin-right: 14rpx; }
.type-chip.active { background: #165dff; }
.type-chip-text { font-size: 24rpx; color: #4e5969; }
.type-chip.active .type-chip-text { color: #fff; }
.field-item { display: flex; align-items: center; padding: 18rpx 0; border-bottom: 1rpx solid #f2f3f5; }
.field-item:last-child { border-bottom: none; }
.field-image { width: 132rpx; height: 98rpx; border-radius: 14rpx; background: #eee; margin-right: 16rpx; flex-shrink: 0; }
.field-main { flex: 1; min-width: 0; }
.field-head { display: flex; justify-content: space-between; align-items: center; gap: 10rpx; }
.field-name { font-size: 28rpx; font-weight: 600; color: #1f2329; }
.field-status { font-size: 22rpx; padding: 4rpx 12rpx; border-radius: 999rpx; }
.field-status.ok { color: #00b42a; background: #e8ffea; }
.field-status.busy { color: #ff7d00; background: #fff3e8; }
.field-status.close { color: #f53f3f; background: #ffece8; }
.field-meta { margin-top: 8rpx; display: block; font-size: 22rpx; color: #86909c; }
.field-desc { margin-top: 6rpx; display: block; font-size: 22rpx; color: #4e5969; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; }
.field-arrow { margin-left: 10rpx; color: #c9cdd4; font-size: 28rpx; }
.bottom-gap { height: 40rpx; }
</style>
