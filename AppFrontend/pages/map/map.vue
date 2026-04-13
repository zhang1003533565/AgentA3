<template>
  <view class="map-page">
    <view class="map-fullscreen">
      <map
        id="campusAmap"
        class="amap-native"
        :latitude="mapCenter.latitude"
        :longitude="mapCenter.longitude"
        :scale="mapScale"
        :markers="amapMarkers"
        :polyline="amapPolylines"
        :show-location="true"
        :enable-rotate="false"
        :show-compass="false"
        :show-scale="false"
        @markertap="onMarkerTap"
        @callouttap="onCalloutTap"
        @tap="closePopup"
      />

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

      <view v-if="!visibleLocations.length && !tempSearchLocation" class="map-empty-state">
        <text class="map-empty-title">暂无地图点位</text>
        <text class="map-empty-desc">请先在后台为设施补全经纬度后再查看。</text>
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
import { getMarkerList, searchPlaces } from '@/api/map'

const DEFAULT_MAP_CENTER = {
  longitude: 114.897014,
  latitude: 40.755502
}
const DEFAULT_MAP_SCALE = 16

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
      mapScale: DEFAULT_MAP_SCALE,
      mapCenter: {
        longitude: DEFAULT_MAP_CENTER.longitude,
        latitude: DEFAULT_MAP_CENTER.latitude
      },
      navigationPolyline: [],
      currentLocation: {
        name: '我的位置',
        longitude: null,
        latitude: null
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
      locationList: [],
      tempSearchLocation: null
    }
  },
  computed: {
    amapMarkers() {
      const markers = this.visibleLocations
        .filter((item) => item.longitude != null && item.latitude != null)
        .map((item) => ({
          id: Number(item.id),
          longitude: Number(item.longitude),
          latitude: Number(item.latitude),
          iconPath: this.getMarkerIconPath(item),
          width: 34,
          height: 42,
          alpha: this.selectedLocation && this.selectedLocation.id === item.id ? 1 : 0.92,
          callout: {
            content: item.shortName || item.name,
            display: this.selectedLocation && this.selectedLocation.id === item.id ? 'ALWAYS' : 'BYCLICK',
            borderRadius: 14,
            padding: 8,
            fontSize: 12,
            color: '#1f3f7c',
            bgColor: '#ffffff'
          }
        }))
      if (this.tempSearchLocation && this.tempSearchLocation.longitude != null && this.tempSearchLocation.latitude != null) {
        markers.push({
          id: -9999,
          longitude: Number(this.tempSearchLocation.longitude),
          latitude: Number(this.tempSearchLocation.latitude),
          iconPath: this.getMarkerIconPath(this.tempSearchLocation, true),
          width: 30,
          height: 38,
          alpha: 1,
          callout: {
            content: this.tempSearchLocation.shortName || this.tempSearchLocation.name || '搜索结果',
            display: 'ALWAYS',
            borderRadius: 14,
            padding: 8,
            fontSize: 12,
            color: '#9a3412',
            bgColor: '#fff7ed'
          }
        })
      }
      return markers
    },
    amapPolylines() {
      return this.navigationPolyline.length
        ? [{
            points: this.navigationPolyline,
            color: '#4d86f8',
            width: 8,
            dottedLine: false,
            arrowLine: true,
            borderColor: '#9cc0ff',
            borderWidth: 2
          }]
        : []
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
    } catch (e) {}
    this.loadMapData()
  },
  methods: {
    async loadMapData() {
      try {
        const markerRes = await getMarkerList({ pageSize: 100 })
        this.mapCenter = {
          longitude: DEFAULT_MAP_CENTER.longitude,
          latitude: DEFAULT_MAP_CENTER.latitude
        }
        this.mapScale = DEFAULT_MAP_SCALE
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
          this.syncNearestLocation()
        },
        fail: () => {
          this.currentLocation.longitude = null
          this.currentLocation.latitude = null
        }
      })
    },
    toMarkerItem(item) {
      const longitude = item.longitude != null ? Number(item.longitude) : null
      const latitude = item.latitude != null ? Number(item.latitude) : null
      if (longitude == null || latitude == null) return null
      const typeClass = this.getTypeClass(item.facilityType, item.markerName)
      const icon = this.getFacilityIcon(item.facilityType, item.markerName)
      const route = this.getMarkerRoute(item)
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
    getMarkerIconPath(item, isSearch = false) {
      if (isSearch) return '/static/icons/lcoal/dingwei.png'
      return '/static/icons/lcoal/dingwei.png'
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
    refreshSelectedLocation() {
      if (!this.visibleLocations.length) {
        this.selectedLocation = null
        return
      }
      if (!this.selectedLocation) return
      const matched = this.visibleLocations.find((item) => item.id === this.selectedLocation.id)
      this.selectedLocation = matched || null
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
      this.tempSearchLocation = null
      this.refreshSelectedLocation()
    },
    async handleSearch() {
      const keyword = (this.searchKeyword || '').trim()
      if (!keyword) {
        this.tempSearchLocation = null
        this.refreshSelectedLocation()
        return
      }
      if (this.visibleLocations.length) {
        this.tempSearchLocation = null
        const firstVisible = this.visibleLocations[0]
        this.mapCenter = {
          longitude: Number(firstVisible.longitude),
          latitude: Number(firstVisible.latitude)
        }
        this.mapScale = 17
        return
      }
      try {
        const res = await searchPlaces({
          keyword,
          latitude: this.mapCenter.latitude,
          longitude: this.mapCenter.longitude,
          radius: 5000
        })
        const first = res?.data?.pois?.[0]
        if (!first || first.longitude == null || first.latitude == null) {
          this.tempSearchLocation = null
          this.selectedLocation = null
          uni.showToast({ title: '未找到匹配地点', icon: 'none' })
          return
        }
        const item = {
          id: 'temp-search-poi',
          name: first.title || keyword,
          shortName: this.getShortName(first.title || keyword),
          icon: '📍',
          category: 0,
          typeClass: 'admin',
          distance: first.distance != null ? `${first.distance}m` : '--',
          detail: first.address || first.district || first.typeDesc || '搜索结果',
          description: first.typeDesc || '地图搜索结果',
          top: '50%',
          left: '50%',
          route: '',
          longitude: Number(first.longitude),
          latitude: Number(first.latitude)
        }
        this.tempSearchLocation = item
        this.selectedLocation = null
        this.mapCenter = {
          longitude: Number(item.longitude),
          latitude: Number(item.latitude)
        }
        this.mapScale = 17
      } catch (error) {
        this.tempSearchLocation = null
        this.selectedLocation = null
        uni.showToast({ title: '地点搜索失败', icon: 'none' })
      }
    },
    handleKeywordInput() {
      this.tempSearchLocation = null
      this.refreshSelectedLocation()
    },
    selectCategory(categoryId) {
      this.currentCategory = categoryId
      this.refreshSelectedLocation()
    },
    selectLocation(item) {
      this.selectedLocation = item
      if (item && item.longitude != null && item.latitude != null) {
        this.mapCenter = {
          longitude: Number(item.longitude),
          latitude: Number(item.latitude)
        }
        this.mapScale = 17
      }
    },
    closePopup() {
      this.selectedLocation = null
      this.navigationPolyline = []
    },
    focusUserLocation() {
      const nearest = this.getNearestLocation() || this.locationList[0]
      this.currentCategory = 0
      this.searchKeyword = ''
      this.selectedLocation = null
      this.tempSearchLocation = null
      if (this.currentLocation.longitude != null && this.currentLocation.latitude != null) {
        this.mapCenter = {
          longitude: Number(this.currentLocation.longitude),
          latitude: Number(this.currentLocation.latitude)
        }
        this.mapScale = 17
      }
      uni.showToast({
        title: this.currentLocation.longitude == null || this.currentLocation.latitude == null
          ? '当前位置获取失败'
          : nearest
          ? `已定位当前位置，附近最近是 ${nearest.name}`
          : `当前位置：${this.currentLocation.latitude}, ${this.currentLocation.longitude}`,
        icon: 'none'
      })
    },
    onMarkerTap(event) {
      const markerId = Number(event?.detail?.markerId ?? event?.detail?.id)
      if (markerId === -9999 && this.tempSearchLocation) {
        this.mapCenter = {
          longitude: Number(this.tempSearchLocation.longitude),
          latitude: Number(this.tempSearchLocation.latitude)
        }
        this.mapScale = 17
        this.selectedLocation = null
        return
      }
      const marker = this.visibleLocations.find((item) => Number(item.id) === markerId)
      if (marker) {
        this.mapCenter = {
          longitude: Number(marker.longitude),
          latitude: Number(marker.latitude)
        }
        this.mapScale = 17
        this.selectedLocation = null
      }
    },
    onCalloutTap(event) {
      const markerId = Number(event?.detail?.markerId ?? event?.detail?.id)
      if (markerId === -9999 && this.tempSearchLocation) {
        this.selectLocation(this.tempSearchLocation)
        return
      }
      const marker = this.visibleLocations.find((item) => Number(item.id) === markerId)
      if (marker) {
        this.selectLocation(marker)
      }
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
      this.navigationPolyline = []
      uni.openLocation({
        latitude: Number(item.latitude),
        longitude: Number(item.longitude),
        name: item.name,
        address: item.detail || item.description || item.name,
        scale: 18,
        fail: () => {
          uni.showToast({ title: '打开导航失败', icon: 'none' })
        }
      })
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
  --map-blue-1: #4d86f8;
  --map-blue-2: #76a8ff;
  --map-blue-3: #dfeaff;
  --map-blue-4: #eef4ff;
  --map-blue-shadow: rgba(77, 134, 248, 0.24);
}

.map-fullscreen {
  position: absolute;
  inset: 0;
  overflow: hidden;
  background: #dce8f2;
}

.amap-native {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  z-index: 1;
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

.user-loc-icon,
.current-loc-icon {
  font-size: 22rpx;
  color: #fff;
  line-height: 1;
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
  background: linear-gradient(135deg, var(--map-blue-2), var(--map-blue-1));
  border-color: var(--map-blue-1);
  color: #fff;
  box-shadow: 0 8rpx 20rpx var(--map-blue-shadow);
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

.current-loc-map {
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
  border: 4rpx solid var(--map-blue-2);
  box-shadow: 0 10rpx 24rpx rgba(77, 134, 248, 0.18);
}

.popup-map {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  z-index: 30;
  padding: 28rpx 28rpx 30rpx;
  background: linear-gradient(180deg, rgba(250, 252, 255, 0.98), rgba(241, 247, 255, 0.98));
  border-radius: 36rpx 36rpx 0 0;
  box-shadow: 0 -12rpx 40rpx rgba(55, 94, 171, 0.18);
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

.popup-image-map.teaching { background: linear-gradient(135deg, #7aa8ff 0%, #4b84f6 100%); }
.popup-image-map.admin { background: linear-gradient(135deg, #8daeff 0%, #5b84ef 100%); }
.popup-image-map.canteen { background: linear-gradient(135deg, #86b5ff 0%, #4d86f8 100%); }
.popup-image-map.library { background: linear-gradient(135deg, #97b9ff 0%, #668ff6 100%); }
.popup-image-map.sport { background: linear-gradient(135deg, #75bcff 0%, #3f92ef 100%); }
.popup-image-map.dorm { background: linear-gradient(135deg, #8bc1ff 0%, #5f97ef 100%); }
.popup-image-map.gate { background: linear-gradient(135deg, #8bb4ff 0%, #4d82ea 100%); }

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
  color: #1f3f7c;
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
  background: var(--map-blue-4);
  font-size: 25rpx;
  line-height: 1.7;
  color: rgba(34, 55, 96, 0.72);
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
  background: var(--map-blue-3);
  color: var(--map-blue-1);
}

.popup-btn.primary {
  background: linear-gradient(135deg, var(--map-blue-2) 0%, var(--map-blue-1) 100%);
  color: #fff;
  box-shadow: 0 10rpx 24rpx var(--map-blue-shadow);
}

</style>
