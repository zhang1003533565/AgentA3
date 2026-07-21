<template>
  <view class="building-list-page">
    <nav-bar title="教学楼" />

    <scroll-view class="list-scroll" scroll-y :style="{ paddingTop: navBarHeight + 'px' }">
      <view class="card">
        <text class="section-title">楼宇分区</text>
        <scroll-view class="zone-scroll" scroll-x :show-scrollbar="false">
          <view class="zone-row">
            <view
              v-for="(zone, index) in zoneOptions"
              :key="index"
              class="zone-chip"
              :class="{ active: currentZone === zone }"
              @click="currentZone = zone"
            >
              <text class="zone-chip-text">{{ zone }}</text>
            </view>
          </view>
        </scroll-view>
      </view>

      <view class="card">
        <text class="section-title">楼宇列表</text>
        <view v-for="item in filteredBuildings" :key="item.id" class="building-item" @click="goDetail(item)">
          <image v-if="item.image" class="building-image" :src="item.image" mode="aspectFill" />
          <view v-else class="building-image building-image--empty">楼</view>
          <view class="building-main">
            <view class="building-head">
              <text class="building-name">{{ item.name }}</text>
              <text class="building-status" :class="item.statusClass">{{ item.statusText }}</text>
            </view>
            <text class="building-meta">{{ item.zone }} · 共{{ item.classroomCount }}间教室</text>
            <text class="building-meta">总座位数：{{ item.totalSeatCount }}</text>
          </view>
          <text class="building-arrow">></text>
        </view>
        <view v-if="!filteredBuildings.length" class="empty-tip">暂无教学楼数据</view>
      </view>
      <view class="bottom-gap" />
    </scroll-view>
  </view>
</template>

<script>
import NavBar from '@/components/nav-bar/nav-bar.vue'
import { getTeachingBuildings } from '@/api/teaching.js'

export default {
  components: { NavBar },
  data() {
    return {
      navBarHeight: 88,
      currentZone: '全部',
      buildingList: []
    }
  },
  computed: {
    zoneOptions() {
      const options = ['全部']
      const set = new Set()
      this.buildingList.forEach((item) => {
        if (!set.has(item.zone)) {
          set.add(item.zone)
          options.push(item.zone)
        }
      })
      return options
    },
    filteredBuildings() {
      if (this.currentZone === '全部') return this.buildingList
      return this.buildingList.filter((item) => item.zone === this.currentZone)
    }
  },
  onLoad() {
    const sys = uni.getSystemInfoSync()
    this.navBarHeight = (sys.statusBarHeight || 0) + 44
    this.loadBuildings()
  },
  methods: {
    async loadBuildings() {
      try {
        const res = await getTeachingBuildings()
        const records = Array.isArray(res?.data) ? res.data : []
        this.buildingList = records.map((item) => ({
          ...item,
          zone: item.zone || '未设置区域',
          statusText: this.getStatusText(item.status),
          statusClass: this.getStatusClass(item.status)
        }))
      } catch (error) {
        console.error('加载教学楼失败', error)
        this.buildingList = []
      }
    },
    getStatusText(status) {
      return ({ 1: '正常开放', 2: '维护中', 3: '已关闭' })[status] || '状态未知'
    },
    getStatusClass(status) {
      return status === 1 ? 'ok' : (status === 2 ? 'warn' : 'busy')
    },
    goDetail(item) {
      uni.navigateTo({
        url: `/subpackage_teaching/buildingDetail/buildingDetail?id=${item.id}`
      })
    }
  }
}
</script>

<style lang="scss" scoped>
.building-list-page { min-height: 100vh; background: #f6f7fb; }
.list-scroll { height: 100vh; }
.card { margin: 24rpx; padding: 24rpx; border-radius: 18rpx; background: #fff; }
.section-title { font-size: 30rpx; font-weight: 700; color: #1f2329; display: block; margin-bottom: 18rpx; }
.zone-scroll { width: 100%; }
.zone-row { display: inline-flex; padding-right: 10rpx; }
.zone-chip { height: 56rpx; border-radius: 999rpx; background: #f2f3f5; padding: 0 22rpx; display: flex; align-items: center; justify-content: center; margin-right: 14rpx; }
.zone-chip.active { background: #165dff; }
.zone-chip-text { font-size: 24rpx; color: #4e5969; }
.zone-chip.active .zone-chip-text { color: #fff; }
.building-item { display: flex; align-items: center; padding: 18rpx 0; border-bottom: 1rpx solid #f2f3f5; }
.building-item:last-child { border-bottom: none; }
.building-image { width: 132rpx; height: 98rpx; border-radius: 14rpx; background: #eee; margin-right: 16rpx; flex-shrink: 0; }
.building-image--empty { display: flex; align-items: center; justify-content: center; color: #86909c; font-size: 34rpx; }
.building-main { flex: 1; min-width: 0; }
.building-head { display: flex; justify-content: space-between; align-items: center; gap: 10rpx; }
.building-name { font-size: 28rpx; font-weight: 600; color: #1f2329; }
.building-status { font-size: 22rpx; padding: 4rpx 12rpx; border-radius: 999rpx; }
.building-status.ok { color: #00b42a; background: #e8ffea; }
.building-status.busy { color: #ff7d00; background: #fff3e8; }
.building-status.warn { color: #f53f3f; background: #ffece8; }
.building-meta { margin-top: 8rpx; display: block; font-size: 22rpx; color: #86909c; }
.building-arrow { margin-left: 10rpx; color: #c9cdd4; font-size: 28rpx; }
.bottom-gap { height: 40rpx; }
.empty-tip { padding: 36rpx 0; text-align: center; font-size: 24rpx; color: #86909c; }
</style>
