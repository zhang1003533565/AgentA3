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

      <view class="top-controls" :style="{ paddingTop: `${statusBarHeight + 12}px` }">
        <view class="top-search-row">
          <view class="back-btn-map" @click.stop="handleBack">
            <view class="back-arrow-map" aria-hidden="true"></view>
          </view>

          <view class="search-bar-map" @click.stop>
            <view class="search-box-map">
              <view class="search-icon" aria-hidden="true">
                <view class="search-icon__circle"></view>
              </view>
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
              <view v-else class="voice-icon" aria-hidden="true">
                <view class="voice-icon__head"></view>
                <view class="voice-icon__stem"></view>
              </view>
            </view>
          </view>

          <view class="notice-btn-map" aria-label="地图通知">
            <image class="bell-icon-img" src="/static/icons/mdi-light--bell.svg" mode="aspectFit" aria-hidden="true" />
            <view class="notice-dot"></view>
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

      <view
        v-if="!selectedLocation"
        class="category-drawer"
        :class="{ expanded: categoryPanelExpanded }"
        @click.stop
      >
        <view class="category-drawer-toggle" @click.stop="toggleCategoryPanel">
          <view class="category-drawer-icon" aria-hidden="true">
            <view></view>
            <view></view>
            <view></view>
          </view>
          <text>分类</text>
        </view>

        <view class="category-drawer-panel">
          <view class="filter-row">
            <view
              v-for="item in categoryTiles"
              :key="item.id"
              class="filter-item-map"
              :class="[
                'filter-item-map--' + item.typeClass,
                'filter-item-map--' + item.iconKey,
                {
                  active: !item.isMore && !item.disabled && selectedFacilityTypes.includes(item.id),
                  disabled: item.disabled
                }
              ]"
              @click="item.isMore ? showMoreCategories() : (item.disabled ? null : selectCategory(item.id))"
            >
              <view class="category-icon-map" aria-hidden="true">
                <view class="category-icon-shape">
                  <view></view>
                  <view></view>
                  <view></view>
                </view>
              </view>
              <text>{{ item.name }}</text>
            </view>
          </view>
        </view>
      </view>

      <view class="map-tool-stack">
        <view class="map-tool-btn" aria-label="图层">
          <view class="layers-icon" aria-hidden="true">
            <view></view>
            <view></view>
            <view></view>
          </view>
        </view>
        <view class="map-tool-btn" aria-label="回到当前位置" @click.stop="locateToCurrent">
          <view class="tool-target-icon tool-target-icon--small" aria-hidden="true"></view>
        </view>
      </view>

      <view v-if="shouldShowNearbySheet" class="nearby-sheet-map" @click.stop>
        <view class="nearby-sheet__handle"></view>
        <view class="nearby-sheet__head">
          <text class="nearby-sheet__title">附近地点</text>
          <view class="nearby-sheet__more" @click.stop="showNearbyMore">
            <text>更多</text>
            <view class="nearby-more-arrow"></view>
          </view>
        </view>
        <scroll-view class="nearby-list" scroll-x :show-scrollbar="false">
          <view class="nearby-row">
            <view
              v-for="item in nearbyDisplayCards"
              :key="item.id"
              class="nearby-card"
              :class="{ 'nearby-card--placeholder': item.isPlaceholder }"
              @click="item.isPlaceholder ? null : selectLocation(item)"
            >
              <image
                v-if="item.coverImage"
                class="nearby-cover"
                :src="item.coverImage"
                mode="aspectFill"
              />
              <view v-else class="nearby-cover nearby-cover--empty">
                <text>{{ item.shortName }}</text>
              </view>
              <text class="nearby-name">{{ item.name }}</text>
              <view class="nearby-distance">
                <view class="nearby-pin"></view>
                <text>{{ item.distance }}</text>
              </view>
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
import { getMarkerList, getNavigationRoute, searchPlaces } from '@/api/map'
import {
  applyFacilityTypeLabels,
  buildFacilityDetailRoute,
  FACILITY_TYPE_OPTIONS,
  getFacilityTypeLabel,
  resolveFacilityType
} from '@/constants/facilityType'

const NAV_ACTION_LABEL = '开始导航'

const CAMPUS_FALLBACK_CENTER = {
  longitude: 114.898507,
  latitude: 40.755672
}
const DEFAULT_MAP_SCALE = 16
const NAVIGATION_MODE_OPTIONS = [
  { label: '步行', mode: 'walking' },
  { label: '骑行', mode: 'bicycling' },
  { label: '驾车', mode: 'driving' }
]
const PINYIN_PHRASES = [
  ['教学楼', 'jiaoxuelou', 'jxl'],
  ['教学', 'jiaoxue', 'jx'],
  ['教室', 'jiaoshi', 'js'],
  ['食堂', 'shitang', 'st'],
  ['宿舍', 'sushe', 'ss'],
  ['运动场', 'yundongchang', 'ydc'],
  ['体育馆', 'tiyuguan', 'tyg'],
  ['图书馆', 'tushuguan', 'tsg'],
  ['综合服务', 'zonghefuwu', 'zhfw'],
  ['校内商铺', 'xiaoneishangpu', 'xnsp'],
  ['明德楼', 'mingdelou', 'mdl'],
  ['崇德楼', 'chongdelou', 'cdl'],
  ['学一食堂', 'xueyishitang', 'xyst'],
  ['学二食堂', 'xueershitang', 'xest'],
  ['学三食堂', 'xuesanshitang', 'xsst']
]
const PINYIN_CHAR_MAP = {
  教: ['jiao', 'j'],
  学: ['xue', 'x'],
  楼: ['lou', 'l'],
  食: ['shi', 's'],
  堂: ['tang', 't'],
  宿: ['su', 's'],
  舍: ['she', 's'],
  运: ['yun', 'y'],
  动: ['dong', 'd'],
  场: ['chang', 'c'],
  体: ['ti', 't'],
  育: ['yu', 'y'],
  馆: ['guan', 'g'],
  图: ['tu', 't'],
  书: ['shu', 's'],
  综: ['zong', 'z'],
  合: ['he', 'h'],
  服: ['fu', 'f'],
  务: ['wu', 'w'],
  校: ['xiao', 'x'],
  内: ['nei', 'n'],
  商: ['shang', 's'],
  铺: ['pu', 'p'],
  明: ['ming', 'm'],
  德: ['de', 'd'],
  崇: ['chong', 'c'],
  一: ['yi', 'y'],
  二: ['er', 'e'],
  三: ['san', 's'],
  四: ['si', 's'],
  五: ['wu', 'w'],
  六: ['liu', 'l'],
  七: ['qi', 'q'],
  八: ['ba', 'b'],
  九: ['jiu', 'j'],
  十: ['shi', 's']
}
const QUICK_CATEGORY_CONFIG = [
  { label: '教学楼', match: ['教学楼'], iconKey: 'teaching', typeClass: 'teaching' },
  { label: '食堂', match: ['食堂'], iconKey: 'canteen', typeClass: 'canteen' },
  { label: '宿舍', match: ['宿舍', '综合服务'], iconKey: 'dorm', typeClass: 'dorm' },
  { label: '运动场', match: ['运动场', '体育'], iconKey: 'sport', typeClass: 'sport' },
  { label: '图书馆', match: ['图书馆'], iconKey: 'library', typeClass: 'library' }
]

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
        longitude: CAMPUS_FALLBACK_CENTER.longitude,
        latitude: CAMPUS_FALLBACK_CENTER.latitude
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
      categoryPanelExpanded: false,
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
      const keyword = this.normalizeSearchToken(this.searchKeyword)
      return this.locationList.filter((item) => {
        if (!keyword) return true
        return (item.searchText || '').includes(keyword)
      })
    },
    categoryTiles() {
      const usedIds = new Set()
      const tiles = QUICK_CATEGORY_CONFIG.map((config) => {
        const matched = this.categories.find((item) => {
          if (usedIds.has(item.id)) return false
          return config.match.some((name) => `${item.name}`.includes(name))
        })
        if (!matched) {
          return {
            id: `virtual-${config.iconKey}`,
            name: config.label,
            typeClass: config.typeClass,
            iconKey: config.iconKey,
            disabled: true
          }
        }
        usedIds.add(matched.id)
        return {
          ...matched,
          name: config.label,
          typeClass: config.typeClass,
          iconKey: config.iconKey
        }
      })
      return [
        ...tiles,
        { id: 'more', name: '更多', typeClass: 'more', iconKey: 'more', isMore: true }
      ]
    },
    nearbyLocations() {
      const source = this.visibleLocations.length ? this.visibleLocations : this.locationList
      return source
        .filter((item) => item.longitude != null && item.latitude != null)
        .slice(0, 4)
    },
    nearbyDisplayCards() {
      if (this.nearbyLocations.length) return this.nearbyLocations
      return this.categoryTiles
        .filter((item) => !item.isMore)
        .slice(0, 4)
        .map((item) => ({
          id: `nearby-placeholder-${item.iconKey}`,
          name: item.name,
          shortName: item.name,
          distance: '--',
          coverImage: '',
          isPlaceholder: true
        }))
    },
    shouldShowNearbySheet() {
      const hasKeyword = !!(this.searchKeyword || '').trim()
      return !this.selectedLocation && (hasKeyword || !!this.tempSearchLocation)
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
          this.mapScale = DEFAULT_MAP_SCALE
        }
        this.locationList = records
          .map((item) => this.toMarkerItem(item))
          .filter(Boolean)
        if (resetViewport) {
          this.mapCenter = this.resolveInitialMapCenter(this.locationList)
          this.fetchCurrentLocation({ centerMap: true })
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
    resolveInitialMapCenter(locations = []) {
      const points = locations
        .filter((item) => item.longitude != null && item.latitude != null)
        .map((item) => ({
          longitude: Number(item.longitude),
          latitude: Number(item.latitude)
        }))
        .filter((item) => Number.isFinite(item.longitude) && Number.isFinite(item.latitude))
      if (!points.length) {
        return {
          longitude: CAMPUS_FALLBACK_CENTER.longitude,
          latitude: CAMPUS_FALLBACK_CENTER.latitude
        }
      }
      const center = points.reduce((result, item) => ({
        longitude: result.longitude + item.longitude,
        latitude: result.latitude + item.latitude
      }), { longitude: 0, latitude: 0 })
      return {
        longitude: Number((center.longitude / points.length).toFixed(6)),
        latitude: Number((center.latitude / points.length).toFixed(6))
      }
    },
    normalizeSearchToken(value) {
      return `${value || ''}`
        .toLowerCase()
        .replace(/[\s_\-—–,，.。:：;；/\\#（）()【】\[\]{}]+/g, '')
    },
    flattenSearchValues(value, result = []) {
      if (value == null) return result
      if (Array.isArray(value)) {
        value.forEach((item) => this.flattenSearchValues(item, result))
        return result
      }
      if (typeof value === 'object') {
        Object.values(value).forEach((item) => this.flattenSearchValues(item, result))
        return result
      }
      const text = `${value}`.trim()
      if (text) result.push(text)
      return result
    },
    buildPinyinTokens(text) {
      const source = `${text || ''}`
      const tokens = []
      PINYIN_PHRASES.forEach(([phrase, full, initials]) => {
        if (source.includes(phrase)) {
          tokens.push(full, initials)
        }
      })
      let full = ''
      let initials = ''
      Array.from(source).forEach((char) => {
        const mapped = PINYIN_CHAR_MAP[char]
        if (!mapped) return
        full += mapped[0]
        initials += mapped[1]
      })
      if (full) tokens.push(full)
      if (initials) tokens.push(initials)
      return tokens
    },
    buildLocationSearchText(rawItem, normalizedItem) {
      const values = []
      this.flattenSearchValues([
        normalizedItem.name,
        normalizedItem.shortName,
        normalizedItem.detail,
        normalizedItem.description,
        rawItem.markerName,
        rawItem.facilityName,
        rawItem.facilityTypeName,
        rawItem.location,
        rawItem.description,
        rawItem.classroom,
        rawItem.classrooms,
        rawItem.rooms,
        rawItem.roomNumber,
        rawItem.roomNo,
        rawItem.buildingNo,
        rawItem.aliases,
        rawItem.keywords
      ], values)
      const pinyinValues = values.flatMap((item) => this.buildPinyinTokens(item))
      return [...values, ...pinyinValues]
        .map((item) => this.normalizeSearchToken(item))
        .filter(Boolean)
        .join(' ')
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
        searchText: this.buildLocationSearchText(item, {
          name: item.markerName,
          shortName: this.getShortName(item.markerName),
          detail: item.location || getFacilityTypeLabel(item.facilityType, item.facilityTypeName),
          description: item.description || ''
        })
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
      return resolveFacilityType(type).icon || ''
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
        width: isSearch ? 32 : (isSelected ? 34 : 30),
        height: isSearch ? 40 : (isSelected ? 42 : 38),
        alpha: isSearch ? 1 : (isSelected ? 1 : 0.86),
        callout: {
          content: item.shortName || item.name || (isSearch ? '搜索结果' : '地点'),
          display: isSearch || isSelected ? 'ALWAYS' : 'BYCLICK',
          borderRadius: 12,
          padding: 7,
          fontSize: 12,
          color: isSearch ? '#2f4d63' : '#26323a',
          bgColor: isSearch ? '#eef4f8' : '#ffffff',
          borderWidth: 1,
          borderColor: isSelected ? '#8fa7ba' : '#e4e9ee'
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
          icon: '',
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
    showMoreCategories() {
      uni.showToast({ title: '可左右滑动查看更多分类', icon: 'none' })
    },
    showNearbyMore() {
      uni.showToast({ title: '可在地图中搜索更多地点', icon: 'none' })
    },
    toggleCategoryPanel() {
      this.categoryPanelExpanded = !this.categoryPanelExpanded
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
      if (!item || item.facilityType == null) return ''
      return resolveFacilityType(item.facilityType).poiEmoji || ''
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
    chooseNavigationMode() {
      return new Promise((resolve) => {
        uni.showActionSheet({
          itemList: NAVIGATION_MODE_OPTIONS.map((item) => item.label),
          success: (res) => {
            resolve(NAVIGATION_MODE_OPTIONS[res.tapIndex] || null)
          },
          fail: () => {
            resolve(null)
          }
        })
      })
    },
    getCurrentLocationForRoute() {
      return new Promise((resolve, reject) => {
        uni.getLocation({
          type: 'gcj02',
          success: (res) => {
            const longitude = Number(res.longitude)
            const latitude = Number(res.latitude)
            if (!Number.isFinite(longitude) || !Number.isFinite(latitude)) {
              reject(new Error('invalid_location'))
              return
            }
            this.currentLocation.longitude = longitude
            this.currentLocation.latitude = latitude
            this.refreshLocationDistances()
            this.syncNearestLocation()
            this.refreshSelectedLocation()
            resolve({ longitude, latitude })
          },
          fail: (error) => {
            reject(error)
          }
        })
      })
    },
    normalizeRoutePoint(point) {
      if (!point) return null
      const longitude = Number(point.longitude ?? point.lng)
      const latitude = Number(point.latitude ?? point.lat)
      if (!Number.isFinite(longitude) || !Number.isFinite(latitude)) return null
      return { longitude, latitude }
    },
    extractRoutePoints(routeData) {
      const route = routeData?.data || routeData || {}
      const polyline = route.polyline || route.points || route.routePoints || []
      return Array.isArray(polyline)
        ? polyline.map((point) => this.normalizeRoutePoint(point)).filter(Boolean)
        : []
    },
    focusRoute(points, target) {
      const routePoints = points.length ? points : []
      const allPoints = [
        ...routePoints,
        this.currentLocation.longitude != null && this.currentLocation.latitude != null
          ? { longitude: this.currentLocation.longitude, latitude: this.currentLocation.latitude }
          : null,
        target && target.longitude != null && target.latitude != null
          ? { longitude: Number(target.longitude), latitude: Number(target.latitude) }
          : null
      ].filter(Boolean)
      if (!allPoints.length) return
      const bounds = allPoints.reduce((result, point) => ({
        minLng: Math.min(result.minLng, point.longitude),
        maxLng: Math.max(result.maxLng, point.longitude),
        minLat: Math.min(result.minLat, point.latitude),
        maxLat: Math.max(result.maxLat, point.latitude)
      }), {
        minLng: Number.POSITIVE_INFINITY,
        maxLng: Number.NEGATIVE_INFINITY,
        minLat: Number.POSITIVE_INFINITY,
        maxLat: Number.NEGATIVE_INFINITY
      })
      this.mapCenter = {
        longitude: Number(((bounds.minLng + bounds.maxLng) / 2).toFixed(6)),
        latitude: Number(((bounds.minLat + bounds.maxLat) / 2).toFixed(6))
      }
      const span = Math.max(bounds.maxLng - bounds.minLng, bounds.maxLat - bounds.minLat)
      this.mapScale = span > 0.02 ? 14 : (span > 0.008 ? 15 : 17)
    },
    openSystemLocation(item) {
      uni.openLocation({
        latitude: Number(item.latitude),
        longitude: Number(item.longitude),
        name: item.name,
        address: item.detail || item.description || item.name,
        scale: 18,
        fail: () => {
          uni.showToast({ title: '打开系统地图失败', icon: 'none' })
        }
      })
    },
    async startNavigation(item) {
      if (item.longitude == null || item.latitude == null) {
        uni.showToast({ title: '该地点暂未配置经纬度', icon: 'none' })
        return
      }
      const modeOption = await this.chooseNavigationMode()
      if (!modeOption) return
      let from
      try {
        from = await this.getCurrentLocationForRoute()
      } catch (error) {
        uni.showToast({ title: '无法获取当前位置，不能规划路线', icon: 'none' })
        return
      }
      const toLongitude = Number(item.longitude)
      const toLatitude = Number(item.latitude)
      if (!Number.isFinite(toLongitude) || !Number.isFinite(toLatitude)) {
        uni.showToast({ title: '目标点坐标无效', icon: 'none' })
        return
      }
      this.navigationPolyline = []
      try {
        const routeRes = await getNavigationRoute({
          fromLongitude: from.longitude,
          fromLatitude: from.latitude,
          toLongitude,
          toLatitude,
          mode: modeOption.mode
        })
        const points = this.extractRoutePoints(routeRes)
        if (!points.length) {
          uni.showToast({ title: '路线暂无可用折点', icon: 'none' })
          return
        }
        this.navigationPolyline = points
        this.selectedLocation = item
        this.focusRoute(points, item)
        uni.showToast({ title: `已规划${modeOption.label}路线`, icon: 'none' })
      } catch (error) {
        console.error('路线规划失败', error)
        uni.showModal({
          title: '路线规划失败',
          content: '暂时无法在地图内绘制路线，是否打开系统地图查看位置？',
          confirmText: '打开地图',
          cancelText: '取消',
          success: (res) => {
            if (res.confirm) this.openSystemLocation(item)
          }
        })
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
  --map-blue-1: #4d86f8;
  --map-blue-2: #76a8ff;
  --map-blue-3: #dfeaff;
  --map-blue-4: #eef4ff;
  --map-blue-shadow: rgba(77, 134, 248, 0.24);
  --map-icon-gray: #66727d;
  --map-icon-gray-soft: rgba(102, 114, 125, 0.66);
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
  left: 30rpx;
  right: 30rpx;
  top: 0;
  z-index: 50;
  display: flex;
  align-items: center;
}

.top-search-row {
  display: flex;
  align-items: center;
  gap: 16rpx;
}

.back-btn-map {
  width: 72rpx;
  height: 72rpx;
  border-radius: 24rpx;
  background: rgba(255, 255, 255, 0.94);
  border: 1rpx solid rgba(102, 114, 125, 0.08);
  display: flex;
  align-items: center;
  justify-content: center;
  box-shadow: 0 14rpx 34rpx rgba(42, 72, 103, 0.1);
  backdrop-filter: blur(16rpx);
  flex-shrink: 0;
}

.back-arrow-map {
  position: relative;
  width: 34rpx;
  height: 34rpx;
}

.back-arrow-map::before,
.back-arrow-map::after {
  content: '';
  position: absolute;
  left: 8rpx;
  width: 22rpx;
  height: 4rpx;
  border-radius: 999rpx;
  background: var(--map-icon-gray);
  transform-origin: left center;
}

.back-arrow-map::before {
  top: 16rpx;
  transform: rotate(-45deg);
}

.back-arrow-map::after {
  top: 16rpx;
  transform: rotate(45deg);
}

.search-bar-map {
  flex: 1;
  min-width: 0;
}

.search-box-map {
  height: 84rpx;
  padding: 0 24rpx 0 28rpx;
  display: flex;
  align-items: center;
  border-radius: 30rpx;
  background: rgba(255, 255, 255, 0.94);
  border: 1rpx solid rgba(102, 114, 125, 0.08);
  box-shadow: 0 14rpx 38rpx rgba(42, 72, 103, 0.1);
  backdrop-filter: blur(16rpx);
}

.search-icon {
  position: relative;
  width: 30rpx;
  height: 30rpx;
  margin-right: 14rpx;
  flex-shrink: 0;
}

.search-icon__circle {
  position: absolute;
  left: 3rpx;
  top: 3rpx;
  width: 16rpx;
  height: 16rpx;
  border: 3rpx solid var(--map-icon-gray-soft);
  border-radius: 50%;
  box-sizing: border-box;
}

.search-icon__circle::after {
  content: '';
  position: absolute;
  right: -9rpx;
  bottom: -7rpx;
  width: 12rpx;
  height: 3rpx;
  border-radius: 999rpx;
  background: var(--map-icon-gray-soft);
  transform: rotate(45deg);
  transform-origin: left center;
}

.search-input-map {
  flex: 1;
  min-width: 0;
  font-size: 27rpx;
  color: #1e1e1e;
}

.search-clear {
  width: 40rpx;
  text-align: center;
  font-size: 30rpx;
  color: rgba(0, 0, 0, 0.32);
}

.voice-icon {
  position: relative;
  width: 46rpx;
  height: 46rpx;
  margin-left: 12rpx;
  flex-shrink: 0;
}

.voice-icon__head {
  position: absolute;
  left: 16rpx;
  top: 5rpx;
  width: 14rpx;
  height: 25rpx;
  border: 4rpx solid var(--map-icon-gray);
  border-radius: 999rpx;
  box-sizing: border-box;
}

.voice-icon__stem {
  position: absolute;
  left: 21rpx;
  top: 31rpx;
  width: 4rpx;
  height: 10rpx;
  border-radius: 999rpx;
  background: var(--map-icon-gray);
}

.voice-icon::before {
  content: '';
  position: absolute;
  left: 11rpx;
  top: 18rpx;
  width: 24rpx;
  height: 17rpx;
  border: 4rpx solid var(--map-icon-gray);
  border-top: none;
  border-radius: 0 0 18rpx 18rpx;
  box-sizing: border-box;
}

.voice-icon::after {
  content: '';
  position: absolute;
  left: 14rpx;
  bottom: 2rpx;
  width: 18rpx;
  height: 4rpx;
  border-radius: 999rpx;
  background: var(--map-icon-gray);
}

.notice-btn-map {
  position: relative;
  width: 72rpx;
  height: 72rpx;
  border-radius: 24rpx;
  background: rgba(255, 255, 255, 0.94);
  border: 1rpx solid rgba(102, 114, 125, 0.08);
  box-shadow: 0 14rpx 34rpx rgba(42, 72, 103, 0.1);
  backdrop-filter: blur(16rpx);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.bell-icon-img {
  width: 36rpx;
  height: 36rpx;
}

.notice-dot {
  position: absolute;
  right: 12rpx;
  top: 11rpx;
  width: 14rpx;
  height: 14rpx;
  border-radius: 50%;
  background: #ff5353;
}

.category-drawer {
  position: absolute;
  left: 30rpx;
  top: 154rpx;
  z-index: 46;
  width: 122rpx;
  max-height: 56rpx;
  overflow: hidden;
  border-radius: 999rpx;
  background: rgba(255, 255, 255, 0.92);
  border: 1rpx solid rgba(102, 114, 125, 0.08);
  box-shadow: 0 10rpx 28rpx rgba(42, 72, 103, 0.1);
  color: #3f6f9f;
  box-sizing: border-box;
  backdrop-filter: blur(16rpx);
  transition: width 0.24s cubic-bezier(0.4, 0, 0.2, 1),
    max-height 0.24s cubic-bezier(0.4, 0, 0.2, 1),
    border-radius 0.24s cubic-bezier(0.4, 0, 0.2, 1),
    background 0.24s cubic-bezier(0.4, 0, 0.2, 1);
}

.category-drawer.expanded {
  width: calc(100% - 60rpx);
  max-height: 178rpx;
  border-radius: 28rpx;
  background: rgba(255, 255, 255, 0.94);
}

.category-drawer-toggle {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 8rpx;
  height: 56rpx;
  padding: 0 14rpx;
  font-size: 22rpx;
  font-weight: 800;
  box-sizing: border-box;
}

.category-drawer-toggle text {
  line-height: 1;
  white-space: nowrap;
}

.category-drawer.expanded .category-drawer-toggle {
  justify-content: flex-start;
  padding: 0 20rpx;
}

.category-drawer-icon {
  position: relative;
  width: 25rpx;
  height: 25rpx;
  flex-shrink: 0;
  transition: transform 0.22s cubic-bezier(0.4, 0, 0.2, 1);
}

.category-drawer-icon view {
  position: absolute;
  width: 8rpx;
  height: 8rpx;
  border-radius: 3rpx;
  background: currentColor;
  transition:
    left 0.22s cubic-bezier(0.4, 0, 0.2, 1),
    top 0.22s cubic-bezier(0.4, 0, 0.2, 1),
    width 0.22s cubic-bezier(0.4, 0, 0.2, 1),
    height 0.22s cubic-bezier(0.4, 0, 0.2, 1),
    border-radius 0.22s cubic-bezier(0.4, 0, 0.2, 1);
}

.category-drawer-icon view:nth-child(1) {
  left: 0;
  top: 0;
}

.category-drawer-icon view:nth-child(2) {
  left: 15rpx;
  top: 0;
}

.category-drawer-icon view:nth-child(3) {
  left: 0;
  top: 15rpx;
}

.category-drawer.expanded .category-drawer-icon {
  transform: rotate(45deg);
}

.category-drawer.expanded .category-drawer-icon view:nth-child(1) {
  left: 1rpx;
  top: 1rpx;
  width: 8rpx;
  height: 8rpx;
}

.category-drawer.expanded .category-drawer-icon view:nth-child(2) {
  left: 15rpx;
  top: 1rpx;
  width: 8rpx;
  height: 8rpx;
}

.category-drawer.expanded .category-drawer-icon view:nth-child(3) {
  left: 15rpx;
  top: 15rpx;
  width: 8rpx;
  height: 8rpx;
}

.category-drawer-panel {
  padding: 2rpx 20rpx 20rpx;
  opacity: 0;
  transform: translateY(-8rpx);
  pointer-events: none;
  transition: opacity 0.18s ease, transform 0.22s cubic-bezier(0.4, 0, 0.2, 1);
}

.category-drawer.expanded .category-drawer-panel {
  opacity: 1;
  transform: translateY(0);
  pointer-events: auto;
}

.map-tool-stack {
  position: absolute;
  right: 26rpx;
  bottom: 268rpx;
  z-index: 30;
  display: flex;
  flex-direction: column;
  gap: 20rpx;
}

.map-tool-btn {
  width: 64rpx;
  height: 64rpx;
  min-height: 64rpx;
  padding: 0;
  border-radius: 22rpx;
  background: rgba(255, 255, 255, 0.93);
  border: 1rpx solid rgba(102, 114, 125, 0.08);
  box-shadow: 0 12rpx 30rpx rgba(42, 72, 103, 0.1);
  backdrop-filter: blur(14rpx);
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  box-sizing: border-box;
}

.layers-icon {
  position: relative;
  width: 30rpx;
  height: 28rpx;
}

.layers-icon view {
  position: absolute;
  left: 50%;
  width: 27rpx;
  height: 14rpx;
  border: 3rpx solid var(--map-icon-gray);
  border-radius: 4rpx;
  transform: translateX(-50%) rotate(45deg) skew(-8deg, -8deg);
  box-sizing: border-box;
  background: #fff;
}

.layers-icon view:nth-child(1) {
  top: 0;
}

.layers-icon view:nth-child(2) {
  top: 8rpx;
}

.layers-icon view:nth-child(3) {
  top: 16rpx;
}

.tool-target-icon {
  position: relative;
  width: 30rpx;
  height: 30rpx;
  border: 3rpx solid var(--map-icon-gray);
  border-radius: 50%;
  box-sizing: border-box;
}

.tool-target-icon::before,
.tool-target-icon::after {
  content: '';
  position: absolute;
  background: var(--map-icon-gray);
}

.tool-target-icon::before {
  left: 50%;
  top: -9rpx;
  width: 3rpx;
  height: 42rpx;
  transform: translateX(-50%);
}

.tool-target-icon::after {
  left: -9rpx;
  top: 50%;
  width: 42rpx;
  height: 3rpx;
  transform: translateY(-50%);
}

.tool-target-icon--small {
  width: 30rpx;
  height: 30rpx;
}

.filter-row {
  display: grid;
  grid-template-columns: repeat(6, 1fr);
  gap: 10rpx;
}

.filter-item-map {
  min-width: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 10rpx;
  color: #4f6478;
  font-size: 21rpx;
  font-weight: 800;
  line-height: 1.15;
  white-space: nowrap;
}

.filter-item-map.active {
  color: #2f72d6;
}

.filter-item-map.disabled {
  opacity: 0.58;
}

.category-icon-map {
  width: 52rpx;
  height: 52rpx;
  border-radius: 16rpx;
  background: #eef5ff;
  display: flex;
  align-items: center;
  justify-content: center;
}

.category-icon-shape {
  position: relative;
  width: 26rpx;
  height: 26rpx;
  color: #3f80f5;
  border: 0;
  border-radius: 0;
  box-sizing: border-box;
}

.category-icon-shape view {
  position: absolute;
  box-sizing: border-box;
}

.filter-item-map--canteen .category-icon-map {
  background: #fff1e8;
}

.filter-item-map--canteen .category-icon-shape {
  color: #f0833d;
}

.filter-item-map--dorm .category-icon-map,
.filter-item-map--teaching .category-icon-map {
  background: #eef5ff;
}

.filter-item-map--dorm .category-icon-shape {
  color: #3f80f5;
}

.filter-item-map--sport .category-icon-map {
  background: #e9fbf7;
}

.filter-item-map--sport .category-icon-shape {
  color: #23b7aa;
}

.filter-item-map--library .category-icon-map,
.filter-item-map--shop .category-icon-map {
  background: #f0efff;
}

.filter-item-map--library .category-icon-shape,
.filter-item-map--shop .category-icon-shape {
  color: #7474e8;
}

.filter-item-map--more .category-icon-map {
  background: #f7f8fa;
}

.filter-item-map--teaching .category-icon-shape {
  border: 4rpx solid currentColor;
  border-bottom-width: 6rpx;
  border-radius: 5rpx;
}

.filter-item-map--teaching .category-icon-shape view:nth-child(1),
.filter-item-map--teaching .category-icon-shape view:nth-child(2),
.filter-item-map--teaching .category-icon-shape view:nth-child(3) {
  width: 5rpx;
  height: 10rpx;
  bottom: 4rpx;
  border-radius: 999rpx;
  background: currentColor;
}

.filter-item-map--teaching .category-icon-shape view:nth-child(1) {
  left: 4rpx;
}

.filter-item-map--teaching .category-icon-shape view:nth-child(2) {
  left: 12rpx;
}

.filter-item-map--teaching .category-icon-shape view:nth-child(3) {
  right: 4rpx;
}

.filter-item-map--canteen .category-icon-shape view:nth-child(1) {
  left: 6rpx;
  top: 2rpx;
  width: 5rpx;
  height: 25rpx;
  border-radius: 999rpx;
  background: currentColor;
}

.filter-item-map--canteen .category-icon-shape view:nth-child(1)::before,
.filter-item-map--canteen .category-icon-shape view:nth-child(1)::after {
  content: '';
  position: absolute;
  top: 0;
  width: 4rpx;
  height: 12rpx;
  border-radius: 999rpx;
  background: currentColor;
}

.filter-item-map--canteen .category-icon-shape view:nth-child(1)::before {
  left: -6rpx;
}

.filter-item-map--canteen .category-icon-shape view:nth-child(1)::after {
  right: -6rpx;
}

.filter-item-map--canteen .category-icon-shape view:nth-child(2) {
  right: 6rpx;
  top: 2rpx;
  width: 6rpx;
  height: 25rpx;
  border-radius: 999rpx 999rpx 3rpx 3rpx;
  background: currentColor;
  transform: rotate(28deg);
}

.filter-item-map--dorm .category-icon-shape {
  border: 3rpx solid currentColor;
  border-top: none;
  border-radius: 6rpx;
  height: 22rpx;
  margin-top: 4rpx;
}

.filter-item-map--dorm .category-icon-shape::before {
  content: '';
  position: absolute;
  left: 50%;
  top: -10rpx;
  width: 18rpx;
  height: 18rpx;
  border-left: 3rpx solid currentColor;
  border-top: 3rpx solid currentColor;
  border-radius: 3rpx 0 0 0;
  background: transparent;
  transform: translateX(-50%) rotate(45deg);
  transform-origin: center;
}

.filter-item-map--dorm .category-icon-shape view:nth-child(1) {
  left: 8rpx;
  bottom: -3rpx;
  width: 8rpx;
  height: 11rpx;
  border: 3rpx solid currentColor;
  border-bottom: none;
  border-radius: 5rpx 5rpx 0 0;
}

.filter-item-map--sport .category-icon-shape view:nth-child(1),
.filter-item-map--sport .category-icon-shape view:nth-child(2) {
  width: 19rpx;
  height: 24rpx;
  border: 4rpx solid currentColor;
  border-radius: 999rpx;
  top: 4rpx;
}

.filter-item-map--sport .category-icon-shape view:nth-child(1) {
  left: 0;
  transform: rotate(-34deg);
}

.filter-item-map--sport .category-icon-shape view:nth-child(2) {
  right: 0;
  transform: rotate(34deg);
}

.filter-item-map--library .category-icon-shape {
  border: 4rpx solid currentColor;
  border-radius: 5rpx;
}

.filter-item-map--library .category-icon-shape::before {
  content: '';
  position: absolute;
  left: 50%;
  top: -4rpx;
  bottom: -4rpx;
  width: 4rpx;
  border-radius: 999rpx;
  background: currentColor;
  transform: translateX(-50%);
}

.filter-item-map--library .category-icon-shape view:nth-child(1),
.filter-item-map--library .category-icon-shape view:nth-child(2) {
  top: 6rpx;
  width: 8rpx;
  height: 16rpx;
  border-top: 3rpx solid currentColor;
  border-bottom: 3rpx solid currentColor;
}

.filter-item-map--library .category-icon-shape view:nth-child(1) {
  left: 5rpx;
}

.filter-item-map--library .category-icon-shape view:nth-child(2) {
  right: 5rpx;
}

.filter-item-map--more .category-icon-shape {
  background:
    radial-gradient(circle, #9aa3ad 0 3.5rpx, transparent 4.5rpx) 0 0 / 15rpx 15rpx,
    radial-gradient(circle, #9aa3ad 0 3.5rpx, transparent 4.5rpx) 0 0 / 15rpx 15rpx;
}

.nearby-sheet-map {
  position: absolute;
  left: 28rpx;
  right: 28rpx;
  bottom: 26rpx;
  z-index: 35;
  min-height: 274rpx;
  padding: 20rpx 22rpx 26rpx;
  border-radius: 32rpx;
  background: rgba(255, 255, 255, 0.92);
  border: 1rpx solid rgba(102, 114, 125, 0.08);
  box-shadow: 0 18rpx 52rpx rgba(42, 72, 103, 0.14);
  backdrop-filter: blur(20rpx);
  box-sizing: border-box;
}

.nearby-sheet__handle {
  width: 68rpx;
  height: 8rpx;
  border-radius: 999rpx;
  background: #d8dde4;
  margin: -2rpx auto 18rpx;
}

.nearby-sheet__head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 18rpx;
}

.nearby-sheet__title {
  color: #202833;
  font-size: 30rpx;
  font-weight: 900;
}

.nearby-sheet__more {
  display: flex;
  align-items: center;
  gap: 10rpx;
  color: #7a8591;
  font-size: 25rpx;
  font-weight: 700;
}

.nearby-more-arrow {
  width: 13rpx;
  height: 13rpx;
  border-right: 3rpx solid #7a8591;
  border-top: 3rpx solid #7a8591;
  transform: rotate(45deg);
}

.nearby-list {
  white-space: nowrap;
}

.nearby-row {
  display: inline-flex;
  gap: 20rpx;
  padding-right: 6rpx;
}

.nearby-card {
  width: 156rpx;
  display: flex;
  flex-direction: column;
  gap: 10rpx;
}

.nearby-cover {
  width: 156rpx;
  height: 96rpx;
  border-radius: 15rpx;
  background: #edf3f8;
  overflow: hidden;
}

.nearby-cover--empty {
  display: flex;
  align-items: center;
  justify-content: center;
  color: #5d7184;
  font-size: 23rpx;
  font-weight: 800;
}

.nearby-card--placeholder .nearby-cover--empty {
  background:
    linear-gradient(135deg, rgba(105, 157, 217, 0.14), rgba(255, 255, 255, 0.1)),
    #edf3f8;
}

.nearby-name {
  color: #202833;
  font-size: 24rpx;
  font-weight: 900;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.nearby-distance {
  display: flex;
  align-items: center;
  gap: 8rpx;
  color: #6a7683;
  font-size: 22rpx;
  font-weight: 700;
}

.nearby-pin {
  width: 15rpx;
  height: 15rpx;
  border: 4rpx solid #5aa7f2;
  border-radius: 50% 50% 50% 0;
  transform: rotate(-45deg);
  box-sizing: border-box;
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
