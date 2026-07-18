<template>
  <view
    class="map-page"
    :class="{
      'map-page--poi-open': !!selectedLocation,
      'map-page--poi-image': poiHasCoverImage
    }"
  >
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

      <poi-detail-card
        :visible="!!selectedLocation"
        :name="poiCardData.name"
        :distance="poiCardData.distance"
        :zone="poiCardData.zone"
        :description="poiCardData.description"
        :image-url="poiCardData.imageUrl"
        :secondary-emoji="poiCardData.secondaryEmoji"
        :secondary-label="poiCardData.secondaryLabel"
        :service-hint="poiCardData.serviceHint"
        :primary-label="poiCardData.primaryLabel"
        @secondary-click="onPoiSecondaryAction"
        @primary-click="onPoiPrimaryAction"
      />

      <view class="locate-btn-map" aria-label="回到当前位置" @click.stop="locateToCurrent">
        <view class="locate-crosshair">
          <view class="locate-dot"></view>
        </view>
      </view>

      <view class="filter-bar-map" @click.stop>
        <scroll-view class="filter-scroll" scroll-x :show-scrollbar="false">
          <view class="filter-row">
            <view
              v-for="item in categories"
              :key="item.id"
              class="filter-item-map"
              :class="{ active: selectedFacilityTypes.includes(item.id) }"
              @click="selectCategory(item.id)"
            >
              {{ item.name }}
            </view>
          </view>
        </scroll-view>
      </view>
    </view>

    <ai-float-assistant />
  </view>
</template>

<script>
import AiFloatAssistant from '@/components/ai-float-assistant/ai-float-assistant.vue'
import PoiDetailCard from '@/components/poi-detail-card/poi-detail-card.vue'
import { getFacilityTypes } from '@/api/facility'
import { getMarkerList, searchPlaces } from '@/api/map'
import {
  applyFacilityTypeLabels,
  buildFacilityDetailRoute,
  FACILITY_TYPE_OPTIONS,
  getFacilityTypeLabel,
  resolveFacilityType
} from '@/constants/facilityType'

const NAV_ACTION_LABEL = '开始导航'

const DEFAULT_MAP_CENTER = {
  longitude: 114.897014,
  latitude: 40.755502
}
const DEFAULT_MAP_SCALE = 16

export default {
  components: {
    AiFloatAssistant,
    PoiDetailCard
  },
  data() {
    return {
      statusBarHeight: 20,
      searchKeyword: '',
      selectedFacilityTypes: FACILITY_TYPE_OPTIONS.map((item) => item.value),
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
      categories: FACILITY_TYPE_OPTIONS.map((item) => ({ id: item.value, name: item.label })),
      locationList: [],
      tempSearchLocation: null,
      markerCache: {},
      mapDataRequestId: 0
    }
  },
  computed: {
    amapMarkers() {
      const markers = this.visibleLocations
        .filter((item) => item.longitude != null && item.latitude != null)
        .map((item) => this.buildMapMarker(item))
      if (this.tempSearchLocation && this.tempSearchLocation.longitude != null && this.tempSearchLocation.latitude != null) {
        markers.push(this.buildMapMarker(this.tempSearchLocation, { isSearch: true }))
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
        if (!keyword) return true
        return `${item.name} ${item.shortName} ${item.detail}`.toLowerCase().includes(keyword)
      })
    },
    poiCardData() {
      const item = this.selectedLocation
      if (!item) {
        return {
          name: '',
          distance: '',
          zone: '',
          description: '',
          imageUrl: '',
          secondaryEmoji: '',
          secondaryLabel: '',
          serviceHint: '',
          primaryLabel: ''
        }
      }
      return {
        name: item.name || '',
        distance: item.distance || '',
        zone: item.detail || '',
        description: item.description || '',
        imageUrl: item.coverImage || '',
        secondaryEmoji: this.resolveSecondaryEmoji(item),
        secondaryLabel: this.resolveSecondaryLabel(item),
        serviceHint: this.resolveServiceHint(item),
        primaryLabel: NAV_ACTION_LABEL
      }
    },
    poiHasCoverImage() {
      if (!this.selectedLocation) return false
      return !!(this.selectedLocation.coverImage || '').trim()
    }
  },
  onLoad() {
    try {
      const sys = uni.getSystemInfoSync()
      this.statusBarHeight = sys.statusBarHeight || 20
    } catch (e) {}
    this.initializeMap()
  },
  methods: {
    async initializeMap() {
      await this.loadFacilityTypes()
      await this.loadMapData({ resetViewport: true })
    },
    async loadFacilityTypes() {
      try {
        const res = await getFacilityTypes()
        const types = Array.isArray(res?.data) ? res.data : (Array.isArray(res) ? res : [])
        if (types.length) {
          applyFacilityTypeLabels(types)
          this.categories = types.map((item) => ({ id: Number(item.value), name: item.label }))
          this.selectedFacilityTypes = this.categories.map((item) => item.id)
        }
      } catch (error) {
        console.warn('加载设施类型字典失败，使用本地兜底', error)
      }
    },
    async loadMapData(options = {}) {
      const resetViewport = !!options.resetViewport
      const requestId = ++this.mapDataRequestId
      if (!this.selectedFacilityTypes.length) {
        this.locationList = []
        this.selectedLocation = null
        this.navigationPolyline = []
        return
      }
      const selected = [...this.selectedFacilityTypes].sort((a, b) => a - b)
      const allSelected = selected.length === this.categories.length
      const cacheKey = allSelected ? 'all' : selected.join(',')
      try {
        let records = this.markerCache[cacheKey]
        if (!records) {
          const params = { pageSize: 100 }
          if (!allSelected) params.facilityTypes = selected.join(',')
          const markerRes = await getMarkerList(params)
          records = markerRes?.data?.records || []
          this.markerCache[cacheKey] = records
        }
        if (requestId !== this.mapDataRequestId) return
        if (resetViewport) {
          this.mapCenter = {
            longitude: DEFAULT_MAP_CENTER.longitude,
            latitude: DEFAULT_MAP_CENTER.latitude
          }
          this.mapScale = DEFAULT_MAP_SCALE
        }
        this.locationList = records
          .map((item) => this.toMarkerItem(item))
          .filter(Boolean)
        if (resetViewport) {
          const availableTypes = new Set(this.locationList.map((item) => Number(item.facilityType)))
          this.categories = this.categories.filter((item) => availableTypes.has(item.id))
          this.selectedFacilityTypes = this.categories.map((item) => item.id)
          this.fetchCurrentLocation()
        }
        this.syncNearestLocation()
        this.refreshSelectedLocation()
      } catch (error) {
        console.error('加载地图数据失败', error)
      }
    },
    fetchCurrentLocation(options = {}) {
      const centerMap = !!options.centerMap
      const showError = !!options.showError
      uni.getLocation({
        type: 'gcj02',
        success: (res) => {
          this.currentLocation.longitude = Number(res.longitude)
          this.currentLocation.latitude = Number(res.latitude)
          this.refreshLocationDistances()
          this.syncNearestLocation()
          this.refreshSelectedLocation()
          if (centerMap) this.centerOnCurrentLocation()
        },
        fail: () => {
          this.currentLocation.longitude = null
          this.currentLocation.latitude = null
          this.refreshLocationDistances()
          this.syncNearestLocation()
          this.refreshSelectedLocation()
          if (showError) {
            uni.showToast({ title: '无法获取当前位置，请检查定位权限', icon: 'none' })
          }
        }
      })
    },
    locateToCurrent() {
      this.fetchCurrentLocation({ centerMap: true, showError: true })
    },
    centerOnCurrentLocation() {
      const longitude = this.currentLocation.longitude
      const latitude = this.currentLocation.latitude
      if (longitude == null || latitude == null) return

      this.selectedLocation = null
      this.navigationPolyline = []
      this.mapCenter = { longitude, latitude }
      this.mapScale = 17
      this.$nextTick(() => {
        try {
          const mapContext = uni.createMapContext('campusAmap', this)
          if (mapContext && typeof mapContext.moveToLocation === 'function') {
            mapContext.moveToLocation({ longitude, latitude })
          }
        } catch (error) {
          // Reactive center coordinates above remain the cross-platform fallback.
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
      const coverImage = this.getMarkerCoverImage(item)
      return {
        id: item.id,
        name: item.markerName,
        shortName: this.getShortName(item.markerName),
        icon,
        coverImage,
        facilityType: item.facilityType,
        facilityId: item.facilityId,
        category: resolveFacilityType(item.facilityType).mapCategory,
        typeClass,
        distance: this.formatDistance(longitude, latitude),
        detail: item.location || getFacilityTypeLabel(item.facilityType, item.facilityTypeName),
        description: item.description || '暂无简介',
        route,
        longitude,
        latitude,
      }
    },
    getMarkerRoute(item) {
      return buildFacilityDetailRoute(item.facilityType, item.facilityId)
    },
    getTypeClass(type, name) {
      if (name && name.includes('图书馆')) return 'library'
      return resolveFacilityType(type).typeClass
    },
    getFacilityIcon(type, name) {
      if (name && name.includes('图书馆')) return '📚'
      return resolveFacilityType(type).icon
    },
    getMarkerCoverImage(item) {
      if (!item) return ''
      if (item.thumbnailUrl) return item.thumbnailUrl
      const images = Array.isArray(item.images) ? item.images : []
      return images.length ? images[0] : ''
    },
    getMarkerIconPath(item, isSearch = false) {
      if (isSearch) return '/static/icons/lcoal/dingwei.png'
      return '/static/icons/lcoal/dingwei.png'
    },
    buildMapMarker(item, options = {}) {
      const isSearch = !!options.isSearch
      const markerId = isSearch ? -9999 : Number(item.id)
      const isSelected = !isSearch && this.selectedLocation && this.selectedLocation.id === item.id
      const marker = {
        id: markerId,
        longitude: Number(item.longitude),
        latitude: Number(item.latitude),
        iconPath: this.getMarkerIconPath(item, isSearch),
        width: isSearch ? 30 : 34,
        height: isSearch ? 38 : 42,
        alpha: isSearch ? 1 : (isSelected ? 1 : 0.92),
        callout: {
          content: item.shortName || item.name || (isSearch ? '搜索结果' : '地点'),
          display: 'ALWAYS',
          borderRadius: 14,
          padding: 8,
          fontSize: 12,
          color: isSearch ? '#9a3412' : '#1f3f7c',
          bgColor: isSearch ? '#fff7ed' : '#ffffff'
        }
      }
      return marker
    },
    getShortName(name) {
      if (!name) return '地点'
      if (name.length <= 4) return name
      return name.slice(0, 4)
    },
    getFacilityRoute(item) {
      return buildFacilityDetailRoute(item.facilityType, item.id)
    },
    toRadians(value) {
      return (value * Math.PI) / 180
    },
    calculateDistance(longitude, latitude) {
      if (longitude == null || latitude == null) return null
      if (this.currentLocation.longitude == null || this.currentLocation.latitude == null) return null
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
    refreshLocationDistances() {
      this.locationList = this.locationList.map((item) => ({
        ...item,
        distance: this.formatDistance(item.longitude, item.latitude)
      }))
      if (this.tempSearchLocation) {
        this.tempSearchLocation = {
          ...this.tempSearchLocation,
          distance: this.formatDistance(this.tempSearchLocation.longitude, this.tempSearchLocation.latitude)
        }
      }
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
    async selectCategory(categoryId) {
      if (this.selectedFacilityTypes.includes(categoryId)) {
        this.selectedFacilityTypes = this.selectedFacilityTypes.filter((id) => id !== categoryId)
      } else {
        this.selectedFacilityTypes = [...this.selectedFacilityTypes, categoryId]
      }
      this.tempSearchLocation = null
      await this.loadMapData()
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
    onMarkerTap(event) {
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
    resolveSecondaryLabel(item) {
      if (!item || item.facilityType == null) return '区域服务'
      return resolveFacilityType(item.facilityType).secondaryLabel || '区域服务'
    },
    resolveSecondaryEmoji(item) {
      if (!item || item.facilityType == null) return '📍'
      return resolveFacilityType(item.facilityType).poiEmoji || '📍'
    },
    resolveServiceHint(item) {
      if (!item || item.facilityType == null) return ''
      return resolveFacilityType(item.facilityType).serviceHint || ''
    },
    onPoiSecondaryAction() {
      const item = this.selectedLocation
      if (!item) return
      if (item.route) {
        uni.navigateTo({ url: item.route })
        return
      }
      uni.showToast({ title: '该地点暂未开通区域服务', icon: 'none' })
    },
    onPoiPrimaryAction() {
      if (!this.selectedLocation) return
      this.startNavigation(this.selectedLocation)
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

.locate-btn-map {
  position: absolute;
  right: 28rpx;
  bottom: 126rpx;
  z-index: 25;
  display: flex;
  align-items: center;
  justify-content: center;
  width: 76rpx;
  height: 76rpx;
  border: 1rpx solid rgba(29, 68, 103, 0.1);
  border-radius: 22rpx;
  background: rgba(255, 255, 255, 0.96);
  box-shadow: 0 10rpx 28rpx rgba(33, 64, 91, 0.14);
  transition: bottom 0.32s cubic-bezier(0.4, 0, 0.2, 1);
}

.locate-btn-map:active {
  background: #eef4ff;
}

.locate-crosshair {
  position: relative;
  width: 34rpx;
  height: 34rpx;
  border: 4rpx solid var(--map-blue-1);
  border-radius: 50%;
  box-sizing: border-box;
}

.locate-crosshair::before,
.locate-crosshair::after {
  content: '';
  position: absolute;
  background: var(--map-blue-1);
}

.locate-crosshair::before {
  left: 50%;
  top: -10rpx;
  width: 4rpx;
  height: 46rpx;
  transform: translateX(-50%);
}

.locate-crosshair::after {
  left: -10rpx;
  top: 50%;
  width: 46rpx;
  height: 4rpx;
  transform: translateY(-50%);
}

.locate-dot {
  position: absolute;
  left: 50%;
  top: 50%;
  z-index: 1;
  width: 10rpx;
  height: 10rpx;
  border: 3rpx solid #fff;
  border-radius: 50%;
  background: var(--map-blue-1);
  transform: translate(-50%, -50%);
}

.filter-bar-map {
  position: absolute;
  left: 28rpx;
  right: 28rpx;
  bottom: 36rpx;
  z-index: 24;
  transition: bottom 0.32s cubic-bezier(0.4, 0, 0.2, 1);
}

/*
 * 分类栏 bottom = 卡片参考顶边距屏幕底 − 视觉间距
 * 视觉间距 = 分类栏底边 与 卡片参考顶边 之间露出的地图高度（非分类栏自身高度）
 * 无图参考顶：fallback-head 顶 ≈ fallback + body + sheet 底部 safe
 * 有图参考顶：hero 顶 ≈ hero + body + sheet 底部 safe
 * 数值与 poi-detail-card.vue 对齐；无图间距约为原先一半（~72→36rpx）
 */
$map-poi-body-h: 396rpx;
$map-poi-fallback-head-h: 100rpx;
$map-poi-hero-h: 320rpx;
$map-poi-gap-visual: 36rpx;

.map-page--poi-open .filter-bar-map {
  z-index: 40;
  bottom: calc(
    #{$map-poi-fallback-head-h} + #{$map-poi-body-h} + env(safe-area-inset-bottom) - #{$map-poi-gap-visual}
  );
}

.map-page--poi-open .locate-btn-map {
  z-index: 41;
  bottom: calc(
    #{$map-poi-fallback-head-h} + #{$map-poi-body-h} + env(safe-area-inset-bottom) + 58rpx
  );
}

.map-page--poi-open.map-page--poi-image .filter-bar-map {
  bottom: calc(
    #{$map-poi-hero-h} + #{$map-poi-body-h} + env(safe-area-inset-bottom) - #{$map-poi-gap-visual}
  );
}

.map-page--poi-open.map-page--poi-image .locate-btn-map {
  bottom: calc(
    #{$map-poi-hero-h} + #{$map-poi-body-h} + env(safe-area-inset-bottom) + 58rpx
  );
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


</style>
