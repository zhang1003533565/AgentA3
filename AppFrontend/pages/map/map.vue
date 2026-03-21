<template>
  <view class="map-page">
    <view class="map-layer" :style="mapLayerStyle">
      <image class="map-bg" :src="mapPlaceholderUrl" mode="aspectFill" />
      <view class="map-dim" :style="mapDimStyle" />
    </view>

    <view class="nav-layer">
      <nav-bar title="校园地图" :showBack="false" />
    </view>

    <view class="search-floating-container" :class="{ dragging: sheet.dragging }" :style="searchContainerStyle">
      <view class="search-capsule">
        <view class="search-left">
          <text class="search-icon">🔍</text>
          <input
            class="search-input"
            type="text"
            v-model="searchKeyword"
            placeholder="搜索地点..."
            @confirm="handleSearch"
          />
        </view>
        <view class="search-right">
          <text class="search-action">🎤</text>
          <view class="search-divider" />
          <text class="search-action">📷</text>
        </view>
      </view>
    </view>

    <!-- 顶层全宽拖拽触发区，确保不被搜索框挡住 -->
    <view 
      class="drag-trigger-area" 
      :style="dragTriggerStyle"
      @touchstart="onSheetTouchStart"
      @touchmove.stop.prevent="onSheetTouchMove"
      @touchend="onSheetTouchEnd"
    />

    <view class="sheet" :class="{ dragging: sheet.dragging, expanded: sheet.currentHeightPx > sheet.midHeightPx }" :style="sheetStyle">
      <view class="sheet-handle-area" id="sheetHeader">
        <view class="sheet-handle" :class="{ hidden: sheet.currentHeightPx > sheet.midHeightPx }" :style="{ transition: 'opacity 0.3s ease-in-out' }" />
      </view>

      <view class="sheet-content">
        <scroll-view class="chips" scroll-x :show-scrollbar="false">
          <view class="chips-row">
            <view
              v-for="(item, index) in categories"
              :key="index"
              class="chip"
              :class="{ active: currentCategory === item.id }"
              @click="selectCategory(item.id)"
            >
              <text class="chip-text">{{ item.name }}</text>
            </view>
          </view>
        </scroll-view>
      </view>

      <scroll-view 
        class="sheet-body" 
        scroll-y 
        :show-scrollbar="false" 
        :style="sheetBodyStyle"
      >
        <view class="list">
          <view
            v-for="(item, index) in filteredLocations"
            :key="index"
            class="list-item"
            @click="goToLocation(item)"
          >
            <image class="thumb" :src="item.thumb" mode="aspectFill" />
            <view class="info">
              <text class="title">{{ item.name }}</text>
              <text class="subtitle">{{ item.description }}</text>
            </view>
            <view class="right">
              <text class="distance">{{ item.distance }}</text>
              <view class="nav-circle" @click.stop="goToLocation(item)">
                <text class="nav-arrow">➤</text>
              </view>
            </view>
          </view>
        </view>
      </scroll-view>
    </view>

    <view class="tabbar-safe-pad" />
    <app-tab-bar current="map" />
  </view>
</template>

<script>
import AppTabBar from '@/components/custom-tab-bar/custom-tab-bar.vue'
import NavBar from '@/components/nav-bar/nav-bar.vue'

export default {
  components: { AppTabBar, NavBar },
  data() {
    return {
      mapPlaceholderUrl: 'https://picsum.photos/seed/campusmap/1600/2400',
      searchKeyword: '',
      currentCategory: 0,
      categories: [
        { id: 0, name: '全部', icon: '📍' },
        { id: 1, name: '教学楼', icon: '🏫' },
        { id: 2, name: '食堂', icon: '🍚' },
        { id: 3, name: '图书馆', icon: '📚' },
        { id: 4, name: '宿舍', icon: '🏠' },
        { id: 5, name: '运动场', icon: '⚽' },
        { id: 6, name: '其他', icon: '🏛️' }
      ],
      locationList: [
        { id: 1, name: '教学楼A栋', icon: '🏫', description: '计算机学院、软件学院', distance: '320m', category: 1, thumb: 'https://picsum.photos/seed/buildingA/200/200' },
        { id: 2, name: '教学楼B栋', icon: '🏫', description: '经济管理学院、外国语学院', distance: '450m', category: 1, thumb: 'https://picsum.photos/seed/buildingB/200/200' },
        { id: 3, name: '第一食堂', icon: '🍚', description: '学生餐厅、教工餐厅', distance: '180m', category: 2, thumb: 'https://picsum.photos/seed/canteen1/200/200' },
        { id: 4, name: '第二食堂', icon: '🍚', description: '特色风味餐厅', distance: '350m', category: 2, thumb: 'https://picsum.photos/seed/canteen2/200/200' },
        { id: 5, name: '图书馆', icon: '📚', description: '藏书200万册，自习室开放', distance: '280m', category: 3, thumb: 'https://picsum.photos/seed/library/200/200' },
        { id: 6, name: '学生宿舍1号楼', icon: '🏠', description: '男生宿舍', distance: '520m', category: 4, thumb: 'https://picsum.photos/seed/dorm1/200/200' },
        { id: 7, name: '体育馆', icon: '🏟️', description: '篮球、羽毛球、游泳馆', distance: '600m', category: 5, thumb: 'https://picsum.photos/seed/gym/200/200' },
        { id: 8, name: '田径场', icon: '⚽', description: '400米标准跑道', distance: '550m', category: 5, thumb: 'https://picsum.photos/seed/track/200/200' }
      ],

      sheet: {
        tabBarHeightPx: 0,
        windowHeightPx: 0,
        windowWidthPx: 0,
        minHeightPx: 0,
        midHeightPx: 0,
        maxHeightPx: 0,
        currentHeightPx: 0,
        headerHeightPx: 0,
        dragging: false,
        startY: 0,
        startHeightPx: 0
      }
    }
  },
  computed: {
    filteredLocations() {
      const kw = (this.searchKeyword || '').trim().toLowerCase()
      return this.locationList.filter((item) => {
        const inCategory = this.currentCategory === 0 ? true : item.category === this.currentCategory
        if (!inCategory) return false
        if (!kw) return true
        const hay = `${item.name} ${item.description}`.toLowerCase()
        return hay.includes(kw)
      })
    },
    sheetStyle() {
      const { tabBarHeightPx, maxHeightPx, currentHeightPx } = this.sheet
      const translateYPx = Math.max(0, maxHeightPx - currentHeightPx)
      return {
        bottom: `${tabBarHeightPx}px`,
        height: `${maxHeightPx}px`,
        transform: `translateY(${translateYPx}px)`,
        overflow: 'visible'
      }
    },
    searchContainerStyle() {
      const { tabBarHeightPx, maxHeightPx, currentHeightPx } = this.sheet
      const translateYPx = Math.max(0, maxHeightPx - currentHeightPx)
      // 搜索框始终在面板顶部边缘
      const bottomPx = tabBarHeightPx + currentHeightPx
      return {
        bottom: `${bottomPx}px`,
        transform: 'translateY(50%)',
        zIndex: 35
      }
    },
    sheetBodyStyle() {
      const header = this.sheet.headerHeightPx || 0
      const h = Math.max(0, this.sheet.currentHeightPx - header)
      return {
        height: `${h}px`
      }
    },
    dragTriggerStyle() {
      const { tabBarHeightPx, currentHeightPx } = this.sheet
      return {
        position: 'fixed',
        left: 0,
        right: 0,
        bottom: `${tabBarHeightPx + currentHeightPx - 40}px`,
        height: '100px',
        zIndex: 50,
        backgroundColor: 'transparent'
      }
    },
    mapProgress() {
      const { minHeightPx, maxHeightPx, currentHeightPx } = this.sheet
      const range = Math.max(1, maxHeightPx - minHeightPx)
      const p = (currentHeightPx - minHeightPx) / range
      return Math.min(1, Math.max(0, p))
    },
    mapLayerStyle() {
      const p = this.mapProgress
      const scale = 1 - 0.03 * p
      const blur = 2 * p
      const bottom = this.sheet.tabBarHeightPx || 0
      return {
        transform: `scale(${scale})`,
        filter: `blur(${blur}px)`,
        bottom: `${bottom}px`
      }
    },
    mapDimStyle() {
      const p = this.mapProgress
      const a = 0.18 * p
      return {
        backgroundColor: `rgba(0,0,0,${a})`
      }
    }
  },
  onLoad() {
    this.initSheetMetrics()
    this.$nextTick(() => {
      this.measureSheetHeader()
    })
  },
  methods: {
    measureSheetHeader() {
      const query = uni.createSelectorQuery().in(this)
      query.select('#sheetHeader').boundingClientRect()
      query.select('.sheet-content').boundingClientRect()
      query.exec((res) => {
        const handleRect = res && res[0] ? res[0] : null
        const contentRect = res && res[1] ? res[1] : null
        const h1 = handleRect && handleRect.height ? handleRect.height : 0
        const h2 = contentRect && contentRect.height ? contentRect.height : 0
        const headerHeightPx = h1 + h2
        if (headerHeightPx > 0) {
          this.sheet.headerHeightPx = headerHeightPx
        }
      })
    },
    initSheetMetrics() {
      const info = uni.getSystemInfoSync()
      const windowHeightPx = info.windowHeight || 0
      const windowWidthPx = info.windowWidth || 0
      const tabBarHeightPx = windowWidthPx ? (250 * windowWidthPx) / 750 : 0

      const minHeightPx = windowHeightPx * 0.15
      const midHeightPx = windowHeightPx * 0.4
      const maxHeightPx = windowHeightPx * 0.7 // 强制最高只能滑到 70%，确保顶部露出地图区域

      this.sheet.windowHeightPx = windowHeightPx
      this.sheet.windowWidthPx = windowWidthPx
      this.sheet.tabBarHeightPx = tabBarHeightPx
      this.sheet.minHeightPx = minHeightPx
      this.sheet.midHeightPx = midHeightPx
      this.sheet.maxHeightPx = maxHeightPx
      this.sheet.currentHeightPx = midHeightPx
    },
    clampHeight(h) {
      return Math.max(this.sheet.minHeightPx, Math.min(this.sheet.maxHeightPx, h))
    },
    snapHeight(h) {
      const { minHeightPx, midHeightPx, maxHeightPx } = this.sheet
      const points = [minHeightPx, midHeightPx, maxHeightPx]
      let best = points[0]
      let bestDist = Math.abs(h - best)
      for (let i = 1; i < points.length; i++) {
        const d = Math.abs(h - points[i])
        if (d < bestDist) {
          bestDist = d
          best = points[i]
        }
      }
      return best
    },
    onSheetTouchStart(e) {
      const touch = (e.touches && e.touches[0]) || null
      if (!touch) return
      this.sheet.dragging = true
      this.sheet.startY = touch.clientY
      this.sheet.startHeightPx = this.sheet.currentHeightPx
    },
    onSheetTouchMove(e) {
      if (!this.sheet.dragging) return
      const touch = (e.touches && e.touches[0]) || null
      if (!touch) return
      const dy = this.sheet.startY - touch.clientY
      const next = this.clampHeight(this.sheet.startHeightPx + dy)
      this.sheet.currentHeightPx = next
    },
    onSheetTouchEnd() {
      if (!this.sheet.dragging) return
      this.sheet.dragging = false
      this.sheet.currentHeightPx = this.snapHeight(this.sheet.currentHeightPx)
      this.$nextTick(() => {
        this.measureSheetHeader()
      })
    },
    handleSearch() {
      // TODO: 搜索地点
    },
    selectCategory(categoryId) {
      this.currentCategory = categoryId
      // TODO: 筛选地点
    },
    goToLocation(item) {
      if (item.category === 2) {
        uni.navigateTo({
          url: `/subpackage_facility/restaurantDetail/restaurantDetail?id=${item.id}`
        })
        return
      }
      uni.showModal({
        title: item.name,
        content: `距离: ${item.distance}\n${item.description}`,
        confirmText: '开始导航',
        success: (res) => {
          if (res.confirm) {
            uni.showToast({ title: '导航功能开发中', icon: 'none' })
          }
        }
      })
    }
  }
}
</script>

<style lang="scss" scoped>
.map-page {
  position: fixed;
  left: 0;
  top: 0;
  right: 0;
  bottom: 0;
  height: 100vh;
  overflow: hidden;
  background-color: #fff;
}

.map-layer {
  position: fixed;
  left: 0;
  top: 0;
  right: 0;
  bottom: 0;
  overflow: hidden;
  transform-origin: center center;
  transition: transform 180ms ease, filter 180ms ease;
}

.map-bg {
  position: absolute;
  left: 0;
  top: 0;
  width: 100%;
  height: 100%;
}

.map-dim {
  position: absolute;
  left: 0;
  top: 0;
  right: 0;
  bottom: 0;
  transition: background-color 180ms ease;
}

.nav-layer {
  position: fixed;
  left: 0;
  top: 0;
  right: 0;
  z-index: 40;
}

.sheet {
  position: fixed;
  left: 0;
  right: 0;
  z-index: 30;
  background: rgba(255, 255, 255, 0.98);
  backdrop-filter: blur(20px);
  border-radius: 24px 24px 0 0;
  box-shadow: 0 -8px 32px rgba(0, 0, 0, 0.08);
  display: flex;
  flex-direction: column;
  transition: transform 350ms cubic-bezier(0.19, 1, 0.22, 1), background-color 0.3s;
  overflow: visible;
}

.sheet.expanded {
  background: #ffffff;
}

.sheet.dragging {
  transition: none;
}

.sheet-handle-area {
  padding: 8px 16px 4px 16px;
  flex-shrink: 0;
  position: relative;
  z-index: 2;
}

.sheet-content {
  padding: 18px 16px 12px 16px; /* 增加顶部内边距，给跨越的搜索框留空间 */
  flex-shrink: 0;
}

.sheet-handle {
  width: 36px;
  height: 5px;
  background: rgba(0, 0, 0, 0.1);
  border-radius: 999px;
  margin: 0 auto;
  transition: opacity 0.3s;
}

.sheet-handle.hidden {
  opacity: 0;
}

.drag-trigger-area {
  /* 仅用于捕获手势 */
  background: transparent;
}

.search-floating-container {
  position: fixed;
  left: 0;
  right: 0;
  padding: 0 16px;
  transition: bottom 350ms cubic-bezier(0.19, 1, 0.22, 1), transform 350ms cubic-bezier(0.19, 1, 0.22, 1);
}

.search-floating-container.dragging {
  transition: none;
}

.search-capsule {
  height: 48px;
  border-radius: 12px;
  background: #ffffff;
  padding: 0 12px;
  display: flex;
  align-items: center;
  justify-content: space-between;
  box-shadow: 0 8px 24px rgba(0, 0, 0, 0.12); /* 更明显的阴影，体现跨越层级 */
  border: 1px solid rgba(0, 0, 0, 0.05);
}

.search-divider {
  width: 1px;
  height: 16px;
  background: rgba(0, 0, 0, 0.06);
  margin: 0 8px;
}

.search-left {
  display: flex;
  align-items: center;
  flex: 1;
  min-width: 0;
}

.search-icon {
  font-size: 16px;
  color: rgba(0, 0, 0, 0.5);
  margin-right: 8px;
}

.search-input {
  flex: 1;
  font-size: 14px;
  color: #111;
}

.search-right {
  display: flex;
  align-items: center;
}

.search-action {
  width: 30px;
  height: 30px;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 999px;
  color: rgba(0, 0, 0, 0.55);
}

.chips {
  margin-top: 12px;
  width: 100%;
}

.chips-row {
  display: inline-flex;
  padding-right: 16px;
}

.chip {
  height: 32px;
  padding: 0 16px;
  border-radius: 999px;
  display: flex;
  align-items: center;
  justify-content: center;
  background: #f0f0f0;
  margin-right: 8px;
  flex-shrink: 0;
  width: auto;
  min-width: fit-content;
}

.chip-text {
  font-size: 13px;
  color: #111;
  white-space: nowrap;
}

.chip.active {
  background: #007aff;
}

.chip.active .chip-text {
  color: #fff;
}

.sheet-body {
  width: 100%;
  flex: 1;
  min-height: 0;
  position: relative;
  z-index: 10;
}

.list {
  padding: 12px 16px 18px 16px;
}

.list-item {
  display: flex;
  align-items: center;
  padding: 12px;
  border-radius: 16px;
  background: #fff;
  box-shadow: 0 6px 16px rgba(0, 0, 0, 0.06);
  margin-bottom: 12px;
}

.thumb {
  width: 60px;
  height: 60px;
  border-radius: 14px;
  background: #eee;
  margin-right: 12px;
}

.info {
  flex: 1;
  min-width: 0;
}

.title {
  display: block;
  font-size: 15px;
  font-weight: 700;
  color: #111;
  margin-bottom: 6px;
}

.subtitle {
  font-size: 12px;
  color: rgba(0, 0, 0, 0.5);
  display: block;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.right {
  display: flex;
  flex-direction: column;
  align-items: flex-end;
  margin-left: 10px;
}

.distance {
  font-size: 12px;
  color: rgba(0, 0, 0, 0.5);
  margin-bottom: 10px;
}

.nav-circle {
  width: 34px;
  height: 34px;
  border-radius: 999px;
  background: #007aff;
  display: flex;
  align-items: center;
  justify-content: center;
}

.nav-arrow {
  color: #fff;
  font-size: 14px;
}

.tabbar-safe-pad {
  position: fixed;
  left: 0;
  right: 0;
  bottom: 0;
  height: 250rpx;
  background: #fff;
  z-index: 25;
  pointer-events: none;
  box-shadow: 0 -2px 10px rgba(0,0,0,0.02);
}
</style>
