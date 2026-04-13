<template>
  <view class="map-page">
    <view class="map-fullscreen">
      <movable-area class="map-stage" @click="closePopup">
        <movable-view
          class="map-canvas"
          direction="all"
          inertia
          out-of-bounds
          scale
          :scale="mapState.scale"
          :scale-min="1"
          :scale-max="3"
          :x="mapState.x"
          :y="mapState.y"
          :style="mapCanvasStyle"
          @change="onMapChange"
          @scale="onMapScale"
        >
          <image class="map-bg-image" :src="mapImageUrl" mode="scaleToFill" />

          <view
            v-if="currentLocation.visible"
            class="user-location-map"
            :style="{ top: currentLocation.top, left: currentLocation.left }"
            @click.stop="focusUserLocation"
          >
            <view class="user-loc-pulse" />
            <view class="user-loc-dot">
              <text class="user-loc-icon">◎</text>
            </view>
            <text class="user-location-label">{{ currentLocation.name }}</text>
          </view>

          <view
            v-for="item in visibleLocations"
            :key="item.id"
            class="building-marker"
            :class="{ active: selectedLocation && selectedLocation.id === item.id }"
            :style="{ top: item.top, left: item.left }"
            @click.stop="selectLocation(item)"
          >
            <view class="marker-icon" :class="item.typeClass">
              <text class="marker-emoji">{{ item.icon }}</text>
            </view>
            <text class="marker-label">{{ item.shortName }}</text>
          </view>
        </movable-view>
      </movable-area>

      <view class="top-controls" :style="{ paddingTop: `${statusBarHeight + 10}px` }">
        <view class="back-btn-map" @click.stop="handleBack">
          <text class="back-icon">‹</text>
        </view>

        <view class="search-bar-map" @click.stop>
          <view class="search-box-map">
            <text class="search-icon">⌕</text>
            <input
              class="search-input-map"
              type="text"
              v-model="searchKeyword"
              placeholder="搜索教学楼、食堂、宿舍..."
              confirm-type="search"
              @input="handleKeywordInput"
              @confirm="handleSearch"
            />
            <text v-if="searchKeyword" class="search-clear" @click.stop="clearSearch">×</text>
          </view>
        </view>
      </view>

      <view class="compass-map" @click.stop>
        <text class="compass-text">N</text>
      </view>

      <view class="current-loc-map" @click.stop="focusUserLocation">
        <text class="current-loc-icon">◎</text>
      </view>

      <view class="filter-bar-map" @click.stop>
        <scroll-view class="filter-scroll" scroll-x :show-scrollbar="false">
          <view class="filter-row">
            <view
              v-for="item in categories"
              :key="item.id"
              class="filter-item-map"
              :class="{ active: currentCategory === item.id }"
              @click="selectCategory(item.id)"
            >
              {{ item.name }}
            </view>
          </view>
        </scroll-view>
      </view>

      <view v-if="!visibleLocations.length" class="map-empty-state">
        <text class="map-empty-title">暂无地图点位</text>
        <text class="map-empty-desc">请先在后台为建筑配置地图图片坐标后再查看。</text>
      </view>

      <view class="popup-map" :class="{ show: !!selectedLocation }" @click.stop>
        <view class="popup-handle-map" />
        <view v-if="selectedLocation">
          <view class="popup-image-map" :class="selectedLocation.typeClass">
            <view class="popup-image-mask" />
            <text class="popup-image-emoji">{{ selectedLocation.icon }}</text>
            <view class="popup-image-copy">
              <text class="popup-image-title">{{ selectedLocation.name }}</text>
              <text class="popup-image-subtitle">{{ selectedLocation.detail }}</text>
            </view>
          </view>

          <text class="popup-title-map">{{ selectedLocation.name }}</text>
          <view class="popup-detail-map">
            <text class="popup-detail-icon">📍</text>
            <text>{{ selectedLocation.distance }} · {{ selectedLocation.detail }}</text>
          </view>
          <view class="popup-desc-map">{{ selectedLocation.description }}</view>

          <view class="popup-actions-map">
            <button class="popup-btn secondary" @click="closePopup">关闭</button>
            <button class="popup-btn primary" @click="startNavigation(selectedLocation)">开始导航</button>
          </view>
        </view>
      </view>
    </view>

    <ai-float-assistant />
  </view>
</template>

<script>
import AiFloatAssistant from '@/components/ai-float-assistant/ai-float-assistant.vue'
import { getMarkerList, getMapConfig, getNavigationRoute } from '@/api/map'

export default {
  components: {
    AiFloatAssistant
  },
  data() {
    return {
      statusBarHeight: 20,
      searchKeyword: '',
      currentCategory: 0,
      selectedLocation: null,
      mapImageUrl: '/static/map.png',
      mapImageSize: {
        width: 750,
        height: 1334
      },
      mapViewport: {
        width: 375,
        height: 667
      },
      controlPoints: [],
      currentLocation: {
        name: '我的位置',
        top: '50%',
        left: '50%',
        longitude: null,
        latitude: null,
        visible: false
      },
      mapState: {
        x: 0,
        y: 0,
        scale: 1
      },
      categories: [
        { id: 0, name: '全部' },
        { id: 1, name: '教学楼' },
        { id: 2, name: '行政办公' },
        { id: 3, name: '食堂' },
        { id: 4, name: '生活服务' },
        { id: 5, name: '运动场馆' },
        { id: 6, name: '校门' }
      ],
      locationList: []
    }
  },
  computed: {
    mapCanvasStyle() {
      return `width:${this.mapImageSize.width}px;height:${this.mapImageSize.height}px;`
    },
    visibleLocations() {
      const keyword = (this.searchKeyword || '').trim().toLowerCase()
      return this.locationList.filter((item) => {
        const matchedCategory = this.currentCategory === 0 || item.category === this.currentCategory
        if (!matchedCategory) return false
        if (!keyword) return true
        return `${item.name} ${item.shortName} ${item.detail}`.toLowerCase().includes(keyword)
      })
    }
  },
  onLoad() {
    try {
      const sys = uni.getSystemInfoSync()
      this.statusBarHeight = sys.statusBarHeight || 20
      this.mapViewport = {
        width: sys.windowWidth || 375,
        height: sys.windowHeight || 667
      }
    } catch (e) {}
    this.initMapCanvas()
    this.loadMapData()
  },
  methods: {
    initMapCanvas() {
      uni.getImageInfo({
        src: this.mapImageUrl,
        success: (res) => {
          const imageWidth = res.width || 750
          const imageHeight = res.height || 1334
          const viewportWidth = this.mapViewport.width || 375
          const viewportHeight = this.mapViewport.height || 667
          const imageRatio = imageWidth / imageHeight
          const viewportRatio = viewportWidth / viewportHeight
          let canvasWidth = viewportWidth
          let canvasHeight = viewportHeight
          if (imageRatio > viewportRatio) {
            canvasHeight = viewportWidth / imageRatio
          } else {
            canvasWidth = viewportHeight * imageRatio
          }
          this.mapImageSize = {
            width: canvasWidth,
            height: canvasHeight
          }
          this.mapState.x = (viewportWidth - canvasWidth) / 2
          this.mapState.y = (viewportHeight - canvasHeight) / 2
        },
        fail: () => {
          const viewportWidth = this.mapViewport.width || 375
          const viewportHeight = this.mapViewport.height || 667
          this.mapImageSize = {
            width: viewportWidth,
            height: viewportHeight
          }
          this.mapState.x = 0
          this.mapState.y = 0
        }
      })
    },
    async loadMapData() {
      try {
        const [configRes, markerRes] = await Promise.all([
          getMapConfig(),
          getMarkerList({ pageSize: 100 })
        ])
        this.controlPoints = Array.isArray(configRes?.data?.controlPoints) ? configRes.data.controlPoints : []
        const records = markerRes?.data?.records || []
        this.locationList = records
          .map((item) => this.toMarkerItem(item))
          .filter(Boolean)
        this.fetchCurrentLocation()
        this.syncNearestLocation()
        this.refreshSelectedLocation()
      } catch (error) {
        console.error('加载地图数据失败', error)
      }
    },
    fetchCurrentLocation() {
      uni.getLocation({
        type: 'gcj02',
        success: (res) => {
          this.currentLocation.longitude = Number(res.longitude)
          this.currentLocation.latitude = Number(res.latitude)
          this.syncCurrentLocationPosition()
          this.syncNearestLocation()
        },
        fail: () => {
          this.currentLocation.visible = false
        }
      })
    },
    estimateImagePointByGeo(longitude, latitude) {
      if (longitude == null || latitude == null) return null
      const points = (this.controlPoints || [])
        .map((point) => ({
          imageX: point.imageX != null ? Number(point.imageX) : null,
          imageY: point.imageY != null ? Number(point.imageY) : null,
          longitude: point.longitude != null ? Number(point.longitude) : null,
          latitude: point.latitude != null ? Number(point.latitude) : null,
        }))
        .filter((point) => point.imageX != null && point.imageY != null && point.longitude != null && point.latitude != null)
      if (points.length < 3) return null
      const nearest = points
        .map((point) => ({
          ...point,
          distance: Math.hypot(longitude - point.longitude, latitude - point.latitude)
        }))
        .sort((a, b) => a.distance - b.distance)
      if (nearest[0] && nearest[0].distance < 1e-12) {
        return {
          imageX: nearest[0].imageX,
          imageY: nearest[0].imageY
        }
      }
      const sampled = nearest.slice(0, Math.min(6, nearest.length))
      const weighted = sampled.reduce((acc, point) => {
        const weight = 1 / Math.max(point.distance ** 2, 1e-12)
        acc.total += weight
        acc.imageX += point.imageX * weight
        acc.imageY += point.imageY * weight
        return acc
      }, { total: 0, imageX: 0, imageY: 0 })
      if (!weighted.total) return null
      return {
        imageX: weighted.imageX / weighted.total,
        imageY: weighted.imageY / weighted.total
      }
    },
    syncCurrentLocationPosition() {
      const point = this.estimateImagePointByGeo(this.currentLocation.longitude, this.currentLocation.latitude)
      if (!point) {
        this.currentLocation.visible = false
        return
      }
      this.currentLocation.top = `${(point.imageY * 100).toFixed(2)}%`
      this.currentLocation.left = `${(point.imageX * 100).toFixed(2)}%`
      this.currentLocation.visible = true
    },
    toLocationItem(item) {
      const longitude = item.longitude != null ? Number(item.longitude) : null
      const latitude = item.latitude != null ? Number(item.latitude) : null
      const imageX = item.imageX != null ? Number(item.imageX) : null
      const imageY = item.imageY != null ? Number(item.imageY) : null
      if (imageX == null || imageY == null) return null
      const typeClass = this.getTypeClass(item.facilityType, item.facilityName)
      const icon = this.getFacilityIcon(item.facilityType, item.facilityName)
      const route = this.getFacilityRoute(item)
      const top = `${(imageY * 100).toFixed(2)}%`
      const left = `${(imageX * 100).toFixed(2)}%`
      return {
        id: item.id,
        name: item.facilityName,
        shortName: this.getShortName(item.facilityName),
        icon,
        category: item.facilityType === 1 ? 3 : item.facilityType === 2 ? 5 : item.facilityType === 3 ? 1 : 4,
        typeClass,
        distance: this.formatDistance(longitude, latitude),
        detail: item.location || this.getTypeLabel(item.facilityType),
        description: item.description || '暂无简介',
        top,
        left,
        route,
        longitude,
        latitude,
      }
    },
    toMarkerItem(item) {
      const longitude = item.longitude != null ? Number(item.longitude) : null
      const latitude = item.latitude != null ? Number(item.latitude) : null
      const imageX = item.imageX != null ? Number(item.imageX) : null
      const imageY = item.imageY != null ? Number(item.imageY) : null
      if (imageX == null || imageY == null) return null
      const typeClass = this.getTypeClass(item.facilityType, item.markerName)
      const icon = this.getFacilityIcon(item.facilityType, item.markerName)
      const route = this.getMarkerRoute(item)
      const top = `${(imageY * 100).toFixed(2)}%`
      const left = `${(imageX * 100).toFixed(2)}%`
      return {
        id: item.id,
        name: item.markerName,
        shortName: this.getShortName(item.markerName),
        icon,
        category: item.facilityType === 1 ? 3 : item.facilityType === 2 ? 5 : item.facilityType === 3 ? 1 : 4,
        typeClass,
        distance: this.formatDistance(longitude, latitude),
        detail: item.location || this.getTypeLabel(item.facilityType),
        description: item.description || '暂无简介',
        top,
        left,
        route,
        longitude,
        latitude,
      }
    },
    getMarkerRoute(item) {
      if (item.facilityType === 1) {
        return `/subpackage_facility/restaurantDetail/restaurantDetail?id=${item.facilityId}`
      }
      if (item.facilityType === 2) {
        return `/subpackage_sports/sportsDetail/sportsDetail?id=${item.facilityId}`
      }
      if (item.facilityType === 3) {
        return `/subpackage_teaching/buildingDetail/buildingDetail?id=${item.facilityId}`
      }
      if (item.facilityType === 4) {
        return `/subpackage_dormitory/dormitoryDetail/dormitoryDetail?id=${item.facilityId}`
      }
      return ''
    },
    getTypeClass(type, name) {
      if (name && name.includes('图书馆')) return 'library'
      if (type === 1) return 'canteen'
      if (type === 2) return 'sport'
      if (type === 3) return 'teaching'
      if (type === 4) return 'dorm'
      return 'admin'
    },
    getFacilityIcon(type, name) {
      if (name && name.includes('图书馆')) return '📚'
      if (type === 1) return '🍚'
      if (type === 2) return '🏟'
      if (type === 3) return '🏫'
      if (type === 4) return '🏠'
      return '📍'
    },
    getShortName(name) {
      if (!name) return '地点'
      if (name.length <= 4) return name
      return name.slice(0, 4)
    },
    getTypeLabel(type) {
      const map = { 1: '食堂', 2: '运动场馆', 3: '教学楼', 4: '宿舍' }
      return map[type] || '校园地点'
    },
    getFacilityRoute(item) {
      if (item.facilityType === 1) {
        return `/subpackage_facility/restaurantDetail/restaurantDetail?id=${item.id}`
      }
      if (item.facilityType === 2) {
        return `/subpackage_sports/sportsDetail/sportsDetail?id=${item.id}`
      }
      if (item.facilityType === 3) {
        return `/subpackage_teaching/buildingDetail/buildingDetail?id=${item.id}`
      }
      if (item.facilityType === 4) {
        return `/subpackage_dormitory/dormitoryDetail/dormitoryDetail?id=${item.id}`
      }
      return ''
    },
    toRadians(value) {
      return (value * Math.PI) / 180
    },
    calculateDistance(longitude, latitude) {
      if (longitude == null || latitude == null) return null
      const earthRadius = 6371000
      const lat1 = this.toRadians(this.currentLocation.latitude)
      const lat2 = this.toRadians(latitude)
      const deltaLat = this.toRadians(latitude - this.currentLocation.latitude)
      const deltaLng = this.toRadians(longitude - this.currentLocation.longitude)
      const a = Math.sin(deltaLat / 2) * Math.sin(deltaLat / 2)
        + Math.cos(lat1) * Math.cos(lat2) * Math.sin(deltaLng / 2) * Math.sin(deltaLng / 2)
      const c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
      return earthRadius * c
    },
    formatDistance(longitude, latitude) {
      const distance = this.calculateDistance(longitude, latitude)
      if (distance == null) return '--'
      if (distance >= 1000) return `${(distance / 1000).toFixed(2)}km`
      return `${Math.round(distance)}m`
    },
    formatDuration(seconds) {
      if (!seconds && seconds !== 0) return '--'
      if (seconds < 60) return `${seconds}秒`
      const minutes = Math.round(seconds / 60)
      if (minutes < 60) return `${minutes}分钟`
      const hours = Math.floor(minutes / 60)
      const remainMinutes = minutes % 60
      return remainMinutes ? `${hours}小时${remainMinutes}分钟` : `${hours}小时`
    },
    refreshSelectedLocation() {
      if (!this.visibleLocations.length) {
        this.selectedLocation = null
        return
      }
      if (!this.selectedLocation) {
        this.selectedLocation = this.visibleLocations[0]
        return
      }
      const matched = this.visibleLocations.find((item) => item.id === this.selectedLocation.id)
      this.selectedLocation = matched || this.visibleLocations[0]
    },
    getNearestLocation() {
      let nearest = null
      let minDistance = Number.POSITIVE_INFINITY
      this.locationList.forEach((item) => {
        const distance = this.calculateDistance(item.longitude, item.latitude)
        if (distance == null) return
        if (distance < minDistance) {
          minDistance = distance
          nearest = item
        }
      })
      return nearest
    },
    syncNearestLocation() {
      if (this.currentLocation.longitude == null || this.currentLocation.latitude == null) {
        this.currentLocation.name = '我的位置'
        return
      }
      const nearest = this.getNearestLocation()
      if (!nearest) return
      this.currentLocation.name = `我的位置 · 近${nearest.shortName}`
    },
    handleBack() {
      const pages = getCurrentPages()
      if (pages.length <= 1) {
        uni.reLaunch({ url: '/pages/index/index' })
        return
      }
      uni.navigateBack({ delta: 1 })
    },
    clearSearch() {
      this.searchKeyword = ''
      if (!this.visibleLocations.length) {
        this.selectedLocation = null
        return
      }
      this.selectedLocation = this.visibleLocations[0]
    },
    handleSearch() {
      if (!this.visibleLocations.length) {
        this.selectedLocation = null
        uni.showToast({ title: '未找到匹配地点', icon: 'none' })
        return
      }
      this.selectedLocation = this.visibleLocations[0]
    },
    handleKeywordInput() {
      if (!this.visibleLocations.length) {
        this.selectedLocation = null
        return
      }
      if (!this.selectedLocation || !this.visibleLocations.some((item) => item.id === this.selectedLocation.id)) {
        this.selectedLocation = this.visibleLocations[0]
      }
    },
    selectCategory(categoryId) {
      this.currentCategory = categoryId
      if (!this.visibleLocations.length) {
        this.selectedLocation = null
        return
      }
      if (!this.selectedLocation || !this.visibleLocations.some((item) => item.id === this.selectedLocation.id)) {
        this.selectedLocation = this.visibleLocations[0]
      }
    },
    selectLocation(item) {
      this.selectedLocation = item
    },
    closePopup() {
      this.selectedLocation = null
    },
    focusUserLocation() {
      const nearest = this.getNearestLocation() || this.locationList[0]
      this.currentCategory = 0
      this.searchKeyword = ''
      if (nearest) {
        this.selectedLocation = nearest
      }
      this.mapState.scale = 1.2
      uni.showToast({
        title: this.currentLocation.longitude == null || this.currentLocation.latitude == null
          ? '当前位置获取失败'
          : nearest
          ? `已定位当前位置，附近最近是 ${nearest.name}`
          : `当前位置：${this.currentLocation.latitude}, ${this.currentLocation.longitude}`,
        icon: 'none'
      })
    },
    onMapChange(e) {
      const { x, y } = e.detail || {}
      if (typeof x === 'number') this.mapState.x = x
      if (typeof y === 'number') this.mapState.y = y
    },
    onMapScale(e) {
      const { scale, x, y } = e.detail || {}
      if (typeof scale === 'number') this.mapState.scale = scale
      if (typeof x === 'number') this.mapState.x = x
      if (typeof y === 'number') this.mapState.y = y
    },
    navigateToLocation(item) {
      if (item.route) {
        uni.navigateTo({ url: item.route })
        return
      }
      const distance = this.formatDistance(item.longitude, item.latitude)
      uni.showModal({
        title: item.name,
        content: `${item.detail}\n当前位置到目标距离 ${distance}`,
        confirmText: '开始导航',
        success: (res) => {
          if (res.confirm) {
            uni.showToast({ title: `已规划到${item.name}`, icon: 'none' })
          }
        }
      })
    },
    async startNavigation(item) {
      if (item.longitude == null || item.latitude == null) {
        uni.showToast({ title: '该地点暂未配置经纬度', icon: 'none' })
        return
      }
      uni.showLoading({ title: '规划路线中...' })
      try {
        const res = await getNavigationRoute({
          fromLongitude: this.currentLocation.longitude,
          fromLatitude: this.currentLocation.latitude,
          toLongitude: item.longitude,
          toLatitude: item.latitude,
          mode: 'walking'
        })
        const route = res?.data || {}
        const steps = Array.isArray(route.steps) ? route.steps.slice(0, 3) : []
        const routeDistanceText = route.distance != null
          ? (route.distance >= 1000 ? `${(route.distance / 1000).toFixed(2)}km` : `${Math.round(route.distance)}m`)
          : this.formatDistance(item.longitude, item.latitude)
        const contentLines = [
          `当前位置：${this.currentLocation.name}`,
          `目标地点：${item.name}`,
          `步行距离：${routeDistanceText}`,
          `预计时间：${this.formatDuration(route.duration)}`
        ]
        steps.forEach((step, index) => {
          if (step?.instruction) {
            contentLines.push(`${index + 1}. ${step.instruction}`)
          }
        })
        uni.showModal({
          title: `${item.name} 导航方案`,
          content: contentLines.join('\n'),
          confirmText: item.route ? '查看详情' : '知道了',
          success: (modalRes) => {
            if (modalRes.confirm && item.route) {
              uni.navigateTo({ url: item.route })
            }
          }
        })
      } catch (error) {
        uni.showToast({ title: error?.message || '路线规划失败', icon: 'none' })
      } finally {
        uni.hideLoading()
      }
    }
  }
}
</script>

<style lang="scss" scoped>
.map-page {
  position: fixed;
  inset: 0;
  overflow: hidden;
  background: #e8f0f8;
}

.map-fullscreen {
  position: absolute;
  inset: 0;
  overflow: hidden;
  background: #dce8f2;
}

.map-stage {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  z-index: 1;
}

.map-canvas {
  width: 100%;
  height: 100%;
}

.map-bg-image {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
}

.top-controls {
  position: absolute;
  left: 28rpx;
  right: 28rpx;
  top: 0;
  z-index: 50;
  display: flex;
  align-items: center;
}

.back-btn-map {
  width: 72rpx;
  height: 72rpx;
  border-radius: 22rpx;
  background: rgba(255, 255, 255, 0.95);
  border: 1rpx solid rgba(0, 0, 0, 0.05);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 10rpx 28rpx rgba(0, 0, 0, 0.08);
  flex-shrink: 0;
}

.back-icon {
  font-size: 42rpx;
  color: rgba(0, 0, 0, 0.55);
  line-height: 1;
}

.search-bar-map {
  flex: 1;
  margin-left: 18rpx;
}

.search-box-map {
  height: 76rpx;
  padding: 0 24rpx;
  display: flex;
  align-items: center;
  border-radius: 28rpx;
  background: rgba(255, 255, 255, 0.96);
  box-shadow: 0 12rpx 36rpx rgba(0, 0, 0, 0.1);
}

.search-icon {
  font-size: 30rpx;
  color: rgba(0, 0, 0, 0.34);
  margin-right: 14rpx;
}

.search-input-map {
  flex: 1;
  font-size: 28rpx;
  color: #1e1e1e;
}

.search-clear {
  width: 40rpx;
  text-align: center;
  font-size: 30rpx;
  color: rgba(0, 0, 0, 0.32);
}

.user-location-map {
  position: absolute;
  z-index: 18;
  transform: translate(-50%, -50%);
}

.user-loc-pulse {
  position: absolute;
  left: 50%;
  top: 50%;
  width: 96rpx;
  height: 96rpx;
  border-radius: 50%;
  background: rgba(123, 168, 212, 0.26);
  transform: translate(-50%, -50%);
  animation: pulse 2s infinite;
}

.user-loc-dot {
  width: 42rpx;
  height: 42rpx;
  border-radius: 50%;
  background: linear-gradient(145deg, #7ba8d4, #5c8ab8);
  border: 6rpx solid #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 8rpx 22rpx rgba(0, 0, 0, 0.18);
}

.user-loc-icon,
.current-loc-icon {
  font-size: 22rpx;
  color: #fff;
  line-height: 1;
}

.user-location-label {
  position: absolute;
  top: 54rpx;
  left: 50%;
  transform: translateX(-50%);
  padding: 6rpx 14rpx;
  border-radius: 999rpx;
  background: rgba(255, 255, 255, 0.92);
  color: rgba(0, 0, 0, 0.68);
  font-size: 20rpx;
  font-weight: 600;
  white-space: nowrap;
  box-shadow: 0 4rpx 14rpx rgba(0, 0, 0, 0.08);
}

@keyframes pulse {
  0% {
    transform: translate(-50%, -50%) scale(0.8);
    opacity: 1;
  }
  100% {
    transform: translate(-50%, -50%) scale(1.8);
    opacity: 0;
  }
}

.building-marker {
  position: absolute;
  z-index: 16;
  display: flex;
  flex-direction: column;
  align-items: center;
  transform: translate(-50%, -50%);
  transition: transform 0.22s ease;
}

.building-marker.active {
  transform: translate(-50%, -50%) scale(1.08);
}

.marker-icon {
  width: 88rpx;
  height: 88rpx;
  border-radius: 28rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 12rpx 28rpx rgba(0, 0, 0, 0.16);
  position: relative;
}

.marker-icon::after {
  content: '';
  position: absolute;
  inset: 0;
  border-radius: 28rpx;
  background:
    radial-gradient(circle at 28% 24%, rgba(255, 255, 255, 0.4), transparent 48%),
    radial-gradient(circle at 72% 74%, rgba(0, 0, 0, 0.18), transparent 60%);
}

.marker-icon.teaching { background: linear-gradient(145deg, #6b9eff, #4a82e8); }
.marker-icon.admin { background: linear-gradient(145deg, #8b7aff, #6b4aff); }
.marker-icon.canteen { background: linear-gradient(145deg, #ffb24b, #ff8c12); }
.marker-icon.library { background: linear-gradient(145deg, #b044ff, #9022cc); }
.marker-icon.sport { background: linear-gradient(145deg, #2fd3d8, #00b2be); }
.marker-icon.dorm { background: linear-gradient(145deg, #5dc65d, #42a542); }
.marker-icon.gate { background: linear-gradient(145deg, #4caf50, #2e7d32); }

.marker-emoji {
  position: relative;
  z-index: 1;
  font-size: 38rpx;
}

.marker-label {
  margin-top: 10rpx;
  padding: 6rpx 14rpx;
  border-radius: 999rpx;
  background: rgba(255, 255, 255, 0.9);
  color: rgba(0, 0, 0, 0.68);
  font-size: 22rpx;
  font-weight: 600;
  box-shadow: 0 4rpx 14rpx rgba(0, 0, 0, 0.08);
}

.filter-bar-map {
  position: absolute;
  left: 28rpx;
  right: 28rpx;
  bottom: 36rpx;
  z-index: 24;
}

.filter-scroll {
  white-space: nowrap;
}

.filter-row {
  display: inline-flex;
  padding-right: 20rpx;
}

.filter-item-map {
  flex-shrink: 0;
  margin-right: 14rpx;
  padding: 14rpx 26rpx;
  border-radius: 999rpx;
  background: rgba(255, 255, 255, 0.92);
  border: 1rpx solid rgba(0, 0, 0, 0.04);
  color: rgba(0, 0, 0, 0.55);
  font-size: 24rpx;
  font-weight: 700;
  box-shadow: 0 6rpx 16rpx rgba(0, 0, 0, 0.05);
}

.filter-item-map.active {
  background: #7ba8d4;
  border-color: #7ba8d4;
  color: #fff;
}

.map-empty-state {
  position: absolute;
  left: 50%;
  top: 50%;
  transform: translate(-50%, -50%);
  z-index: 26;
  width: 440rpx;
  padding: 28rpx 30rpx;
  border-radius: 28rpx;
  background: rgba(255, 255, 255, 0.92);
  box-shadow: 0 12rpx 36rpx rgba(0, 0, 0, 0.12);
  text-align: center;
}

.map-empty-title {
  display: block;
  font-size: 30rpx;
  font-weight: 700;
  color: #1e1e1e;
}

.map-empty-desc {
  display: block;
  margin-top: 12rpx;
  font-size: 24rpx;
  line-height: 1.7;
  color: rgba(0, 0, 0, 0.52);
}

.current-loc-map,
.compass-map {
  position: absolute;
  bottom: 122rpx;
  width: 84rpx;
  height: 84rpx;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 22;
}

.current-loc-map {
  right: 28rpx;
  background: rgba(255, 255, 255, 0.96);
  border: 4rpx solid #7ba8d4;
  box-shadow: 0 8rpx 20rpx rgba(0, 0, 0, 0.12);
}

.compass-map {
  left: 28rpx;
  background: linear-gradient(145deg, #fff, #f4f4f0);
  border: 4rpx solid rgba(123, 168, 212, 0.45);
  box-shadow: 0 10rpx 26rpx rgba(0, 0, 0, 0.12);
}

.compass-text {
  font-size: 30rpx;
  font-weight: 800;
  color: #5c8ab8;
}

.popup-map {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 30;
  padding: 28rpx 28rpx 30rpx;
  background: rgba(255, 255, 255, 0.98);
  border-radius: 36rpx 36rpx 0 0;
  box-shadow: 0 -12rpx 40rpx rgba(0, 0, 0, 0.16);
  transform: translateY(110%);
  opacity: 0;
  pointer-events: none;
  transition: transform 0.32s cubic-bezier(0.4, 0, 0.2, 1), opacity 0.25s ease;
}

.popup-map.show {
  transform: translateY(0);
  opacity: 1;
  pointer-events: auto;
}

.popup-handle-map {
  width: 84rpx;
  height: 10rpx;
  margin: 0 auto 24rpx;
  border-radius: 999rpx;
  background: rgba(0, 0, 0, 0.12);
}

.popup-image-map {
  position: relative;
  height: 212rpx;
  border-radius: 28rpx;
  padding: 26rpx;
  overflow: hidden;
  display: flex;
  align-items: flex-end;
  margin-bottom: 22rpx;
}

.popup-image-map.teaching { background: linear-gradient(135deg, #7ba9ff 0%, #4a82e8 100%); }
.popup-image-map.admin { background: linear-gradient(135deg, #9686ff 0%, #6b4aff 100%); }
.popup-image-map.canteen { background: linear-gradient(135deg, #ffbb5d 0%, #ff8b1e 100%); }
.popup-image-map.library { background: linear-gradient(135deg, #b45cff 0%, #8420c8 100%); }
.popup-image-map.sport { background: linear-gradient(135deg, #35dce0 0%, #00aab6 100%); }
.popup-image-map.dorm { background: linear-gradient(135deg, #77d06f 0%, #3d9b45 100%); }
.popup-image-map.gate { background: linear-gradient(135deg, #67be64 0%, #2f7f39 100%); }

.popup-image-mask {
  position: absolute;
  inset: 0;
  background:
    radial-gradient(circle at 18% 22%, rgba(255, 255, 255, 0.35), transparent 28%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.03) 0%, rgba(0, 0, 0, 0.22) 100%);
}

.popup-image-emoji {
  position: absolute;
  right: 28rpx;
  top: 22rpx;
  font-size: 88rpx;
  opacity: 0.92;
}

.popup-image-copy {
  position: relative;
  z-index: 1;
}

.popup-image-title {
  display: block;
  font-size: 38rpx;
  font-weight: 800;
  color: #fff;
}

.popup-image-subtitle {
  display: block;
  margin-top: 12rpx;
  font-size: 24rpx;
  color: rgba(255, 255, 255, 0.88);
}

.popup-title-map {
  display: block;
  font-size: 36rpx;
  font-weight: 800;
  color: #1e1e1e;
}

.popup-detail-map {
  margin-top: 10rpx;
  display: flex;
  align-items: center;
  font-size: 24rpx;
  color: rgba(0, 0, 0, 0.48);
}

.popup-detail-icon {
  margin-right: 8rpx;
}

.popup-desc-map {
  margin-top: 18rpx;
  padding: 22rpx;
  border-radius: 22rpx;
  background: rgba(0, 0, 0, 0.035);
  font-size: 25rpx;
  line-height: 1.7;
  color: rgba(0, 0, 0, 0.62);
}

.popup-actions-map {
  display: flex;
  gap: 18rpx;
  margin-top: 22rpx;
}

.popup-btn {
  flex: 1;
  height: 84rpx;
  line-height: 84rpx;
  border-radius: 24rpx;
  font-size: 28rpx;
  font-weight: 700;
  border: none;
}

.popup-btn::after {
  border: none;
}

.popup-btn.secondary {
  background: #e8f0f8;
  color: #5c8ab8;
}

.popup-btn.primary {
  background: linear-gradient(135deg, #7ba8d4 0%, #5c8ab8 100%);
  color: #fff;
  box-shadow: 0 10rpx 24rpx rgba(92, 138, 184, 0.28);
}

</style>
