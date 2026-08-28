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
        provider="amap"
        :latitude="mapCenter.latitude"
        :longitude="mapCenter.longitude"
        :scale="mapScale"
        :markers="amapMarkers"
        :polyline="amapPolylines"
        :polygons="amapPolygons"
        :show-location="true"
        :enable-poi="false"
        :enable-building="true"
        :enable-rotate="false"
        :show-compass="false"
        :show-scale="false"
        @markertap="onMarkerTap"
        @callouttap="onCalloutTap"
        @labeltap="onMarkerTap"
        @tap="handleMapTap"
        @regionchange="onRegionChange"
        @updated="onMapUpdated"
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
                  placeholder="搜索教学楼、食堂、体育场馆..."
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
              <view v-if="searchSuggestions.length" class="search-suggest-panel">
                <view
                  v-for="(item, index) in searchSuggestions"
                  :key="item.id"
                  class="search-suggest-item"
                  :class="{ active: index === searchHighlightIndex }"
                  @click.stop="selectSuggestion(item)"
                >
                  <view class="search-suggest-name">{{ item.markerName || item.name }}</view>
                  <view v-if="item.location || item.detail" class="search-suggest-desc">{{ item.location || item.detail }}</view>
                </view>
              </view>
            </view>
          </view>
      </view>

      <poi-detail-card
        :visible="!!selectedLocation"
        :expand-level="sheetExpandLevel"
        :name="poiCardData.name"
        :category="poiCardData.category"
        :walk-text="poiCardData.walkText"
        :address="poiCardData.address"
        :description="poiCardData.description"
        :status-text="poiCardData.statusText"
        :business-hours="poiCardData.businessHours"
        :avg-price-text="poiCardData.avgPriceText"
        :floor-count="poiCardData.floorCount"
        :images="poiCardData.images"
        :image-url="poiCardData.imageUrl"
        :active-panel="activePanel"
        :show-floor-panel="showFloorPanel"
        :floors="placeFloors"
        :active-floor-id="activeFloorId"
        :floor-plan-url="activeFloorPlanUrl"
        :floor-plan-loading="floorPlanLoading"
        :floor-plan-error="floorPlanError"
        :indoor-points="indoorPoints"
        :indoor-loading="indoorLoading"
        :selected-indoor="selectedIndoorPoint"
        @map-click="onPoiMapAction"
        @category-click="onPoiCategoryAction"
        @toggle-expand="toggleSheetExpand"
        @panel-change="onPanelChange"
        @floor-change="onFloorChange"
        @indoor-select="onIndoorSelect"
        @indoor-close="selectedIndoorPoint = null"
        @plan-error="onFloorPlanImageError"
      />

      <view
        v-if="!selectedLocation && !shouldShowClusterSheet"
        class="category-bar"
        @click.stop
      >
        <view
          v-for="item in categoryTiles"
          :key="item.id"
          class="category-bar__item"
          :class="[
            'category-bar__item--' + item.iconKey,
            { active: selectedCategoryKeys.includes(item.id) }
          ]"
          @click="selectCategory(item.id)"
        >
          <view class="category-bar__icon" aria-hidden="true">
            <view class="category-bar__glyph"></view>
          </view>
          <text class="category-bar__label">{{ item.name }}</text>
        </view>
      </view>

      <view v-if="shouldShowClusterSheet" class="nearby-sheet-map cluster-sheet-map" @click.stop>
        <view class="nearby-sheet__handle"></view>
        <view class="nearby-sheet__head">
          <text class="nearby-sheet__title">该区域 {{ clusterPickerItems.length }} 个地点</text>
          <view class="nearby-sheet__more" @click.stop="closeClusterPicker">
            <text>收起</text>
            <view class="nearby-more-arrow nearby-more-arrow--down"></view>
          </view>
        </view>
        <scroll-view class="cluster-list" scroll-y :show-scrollbar="false">
          <view
            v-for="item in clusterPickerItems"
            :key="item.id"
            class="cluster-item"
            @click.stop="selectClusterItem(item)"
          >
            <view class="cluster-item__pin" :class="'cluster-item__pin--' + (item.typeClass || 'admin')"></view>
            <view class="cluster-item__body">
              <text class="cluster-item__name">{{ item.name }}</text>
              <text v-if="item.detail || item.placeTypeName" class="cluster-item__desc">{{ item.placeTypeName || item.detail }}</text>
            </view>
          </view>
        </scroll-view>
      </view>

      <view v-if="shouldShowNearbySheet" class="nearby-sheet-map" @click.stop>
        <view class="nearby-sheet__handle"></view>
        <view class="nearby-sheet__head">
          <text class="nearby-sheet__title">{{ showNearbyPanel ? '附近地点' : '搜索结果' }}</text>
          <view v-if="showNearbyPanel" class="nearby-sheet__more" @click.stop="toggleNearbyPanel(false)">
            <text>收起</text>
            <view class="nearby-more-arrow nearby-more-arrow--down"></view>
          </view>
          <view v-else class="nearby-sheet__more" @click.stop="showNearbyMore">
            <text>更多</text>
            <view class="nearby-more-arrow"></view>
          </view>
        </view>
        <view v-if="nearbyLoading" class="nearby-sheet__loading">
          <text>正在加载附近地点...</text>
        </view>
        <view v-else-if="showNearbyPanel && !nearbyDisplayCards.length" class="nearby-sheet__loading">
          <text>附近暂无设施数据</text>
        </view>
        <scroll-view v-else class="nearby-list" scroll-x :show-scrollbar="false">
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

      <view v-if="shouldShowHistorySheet" class="nearby-sheet-map history-sheet-map" @click.stop>
        <view class="nearby-sheet__handle"></view>
        <view class="nearby-sheet__head">
          <text class="nearby-sheet__title">导航历史</text>
          <view class="nearby-sheet__more" @click.stop="toggleHistoryPanel(false)">
            <text>收起</text>
            <view class="nearby-more-arrow nearby-more-arrow--down"></view>
          </view>
        </view>
        <view v-if="historyLoading" class="nearby-sheet__loading">
          <text>正在加载导航历史...</text>
        </view>
        <view v-else-if="!navigationHistory.length" class="nearby-sheet__loading">
          <text>暂无导航历史记录</text>
        </view>
        <scroll-view v-else class="history-list" scroll-y :show-scrollbar="false">
          <view
            v-for="item in navigationHistory"
            :key="item.id"
            class="history-item"
            @click="selectHistoryItem(item)"
          >
            <view class="history-item__icon" aria-hidden="true">
              <view class="history-item__pin"></view>
            </view>
            <view class="history-item__body">
              <text class="history-item__name">{{ item.name }}</text>
              <view class="history-item__meta">
                <text v-if="item.time">{{ item.time }}</text>
                <text v-if="item.distance" class="history-item__dot">·</text>
                <text v-if="item.distance">{{ item.distance }}</text>
                <text v-if="item.duration" class="history-item__dot">·</text>
                <text v-if="item.duration">{{ item.duration }}</text>
              </view>
            </view>
            <view class="history-item__status" :class="`history-item__status--${item.status}`">
              <text>{{ item.status }}</text>
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
import {
  formatFloorTabLabel,
  getFloorPlan,
  getIndoorPositions,
  getMarkerDetail,
  getNavigationRoute,
  getNearbyFacilities,
  getNavigationHistory,
  getPlaceChildren,
  getPlaceTypeLabel,
  isBuildingPlaceType,
  reverseGeocode,
  searchFacilities,
  startNavigationRecord,
  toMapPlaceMarker
} from '@/api/map'
import { request } from '@/utils/request'
import { BASE_URL, getApiBaseUrl } from '@/utils/config'
import {
  applyFacilityTypeLabels,
  buildFacilityDetailRoute,
  FACILITY_TYPE_OPTIONS,
  getFacilityTypeLabel,
  resolveFacilityType
} from '@/constants/facilityType'

const CAMPUS_FALLBACK_CENTER = {
  longitude: 114.898507,
  latitude: 40.755672
}
const DEFAULT_MAP_SCALE = 16
const CLUSTER_SCALE_MIN = 8
const CLUSTER_SCALE_MAX = 16
const CLUSTER_ID_BASE = 800000
const MARKER_LABEL_CHARS_PER_LINE = 6
const WALK_METERS_PER_MINUTE = 80
const placeDetailCache = {}
const placeDetailInflight = {}
const placeChildrenCache = {}
const floorContentCache = {}
const floorContentInflight = {}
let mapPlacesInflight = null
let mapPlacesCache = null
const POI_MARKER_STYLE = {
  LANDSCAPE: { icon: '/static/icons/map/pin-infra.png', color: '#6B7C8D' },
  ADMIN_BUILDING: { icon: '/static/icons/map/pin-infra.png', color: '#6B7C8D' },
  HOSPITAL: { icon: '/static/icons/map/pin-infra.png', color: '#6B7C8D' },
  CANTEEN: { icon: '/static/icons/map/pin-canteen.png', color: '#C9864D' },
  SPORTS_GROUND: { icon: '/static/icons/map/pin-sport.png', color: '#4E8A69' },
  TEACHING_BUILDING: { icon: '/static/icons/map/pin-teaching.png', color: '#4D6F8F' },
  DORMITORY_BUILDING: { icon: '/static/icons/map/pin-infra.png', color: '#6B7C8D' },
  DEFAULT: { icon: '/static/icons/map/pin-teaching.png', color: '#4D6F8F' },
  SEARCH: { icon: '/static/icons/map/pin-search.png', color: '#5A6E82' },
  ACTIVE: { icon: '/static/icons/map/pin-active.png', color: '#3D5A78' }
}
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
  { id: 'teaching', label: '教学楼', facilityTypes: [3], iconKey: 'teaching', typeClass: 'teaching' },
  { id: 'canteen', label: '食堂', facilityTypes: [1], iconKey: 'canteen', typeClass: 'canteen' },
  { id: 'infra', label: '基础设施', facilityTypes: [4, 5, 99], iconKey: 'infra', typeClass: 'infra' },
  { id: 'sport', label: '体育场馆', facilityTypes: [2], iconKey: 'sport', typeClass: 'sport' }
]

const FENCE_STYLE_BY_SCENE = {
  CANTEEN: { strokeColor: '#E86060', fillColor: '#E8606033' },
  SPORTS: { strokeColor: '#38A85A', fillColor: '#38A85A33' },
  TEACHING: { strokeColor: '#2F6BFF', fillColor: '#2F6BFF33' },
  DORMITORY: { strokeColor: '#8E8E93', fillColor: '#8E8E9333' },
  OTHER: { strokeColor: '#8E8E93', fillColor: '#8E8E9333' }
}

const FENCE_STYLE_BY_PLACE = {
  HOSPITAL: { strokeColor: '#2F6BFF', fillColor: '#2F6BFF33' },
  ADMIN_BUILDING: { strokeColor: '#2F6BFF', fillColor: '#2F6BFF33' },
  LANDSCAPE: { strokeColor: '#8E8E93', fillColor: '#8E8E9333' },
  TEACHING_BUILDING: { strokeColor: '#2F6BFF', fillColor: '#2F6BFF33' },
  CANTEEN: { strokeColor: '#E86060', fillColor: '#E8606033' },
  SPORTS_GROUND: { strokeColor: '#38A85A', fillColor: '#38A85A33' }
}

function toFenceCoordinate(value) {
  const num = Number(value)
  return Number.isFinite(num) ? num : null
}

function parsePlaceFence(fence) {
  if (!fence?.geometryData) return null
  try {
    const geometry = typeof fence.geometryData === 'string'
      ? JSON.parse(fence.geometryData)
      : fence.geometryData
    const geometryType = String(fence.geometryType || geometry?.type || '').toUpperCase()
    const coordinates = geometryType === 'POLYGON'
      ? geometry?.coordinates?.[0]
      : geometry?.coordinates
    if (!Array.isArray(coordinates)) return null
    const path = coordinates
      .map((point) => ({
        longitude: toFenceCoordinate(point?.[0]),
        latitude: toFenceCoordinate(point?.[1])
      }))
      .filter((point) => point.longitude != null && point.latitude != null)
    if (geometryType === 'POLYGON' && path.length >= 3) return { geometryType, path }
    if (geometryType === 'LINESTRING' && path.length >= 2) return { geometryType, path }
    return null
  } catch (error) {
    return null
  }
}

export default {
  components: {
    AiFloatAssistant,
    PoiDetailCard
  },
  data() {
    return {
      statusBarHeight: 20,
      searchKeyword: '',
      // 空数组 = 不过滤，默认直接展示全部后端点位
      selectedFacilityTypes: [],
      selectedCategoryKeys: [],
      selectedLocation: null,
      selectedFencePolygons: [],
      selectedFencePolylines: [],
      sheetExpandLevel: 'half',
      activePanel: 'info',
      placeFloors: [],
      activeFloorId: null,
      activeFloorPlan: null,
      indoorPoints: [],
      selectedIndoorPoint: null,
      floorPlanLoading: false,
      indoorLoading: false,
      floorPlanError: '',
      floorRequestId: 0,
      mapScale: DEFAULT_MAP_SCALE,
      viewScale: DEFAULT_MAP_SCALE,
      mapCenter: {
        longitude: CAMPUS_FALLBACK_CENTER.longitude,
        latitude: CAMPUS_FALLBACK_CENTER.latitude
      },
      viewCenter: {
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
      markerCache: {},
      mapDataRequestId: 0,
      searchSuggestions: [],
      searchHighlightIndex: -1,
      searchDebounceTimer: null,
      searchRequestId: 0,
      showNearbyPanel: false,
      nearbyFacilities: [],
      nearbyLoading: false,
      showHistoryPanel: false,
      navigationHistory: [],
      historyLoading: false,
      droppedPin: null,
      reverseGeocodeLoading: false,
      reverseGeocodeRequestId: 0,
      poiDetailRequestId: 0,
      mapLoadDebug: '',
      markerRenderToken: 0,
      clusterPickerItems: [],
      suppressMapTapUntil: 0
    }
  },
  computed: {
    campusMarkerGroups() {
      this.markerRenderToken
      return this.buildCampusMarkerGroups()
    },
    amapMarkers() {
      // 依赖 token，关闭详情时强制重建 markers，避免原生 title 气泡残留
      this.markerRenderToken
      const markers = []
      const singleItems = this.campusMarkerGroups
        .filter((group) => group.items.length === 1)
        .map((group) => group.items[0])
      const labelLayouts = this.assignMarkerLabelLayouts(singleItems)
      this.campusMarkerGroups.forEach((group) => {
        if (group.items.length === 1) {
          const item = group.items[0]
          const marker = this.buildMapMarker(item, { labelLayout: labelLayouts[item.id] })
          if (marker) markers.push(marker)
          return
        }
        const clusterMarker = this.buildClusterMarker(group)
        if (clusterMarker) markers.push(clusterMarker)
      })
      if (this.tempSearchLocation && this.tempSearchLocation.longitude != null && this.tempSearchLocation.latitude != null) {
        const searchMarker = this.buildMapMarker(this.tempSearchLocation, { isSearch: true })
        if (searchMarker) markers.push(searchMarker)
      }
      if (this.droppedPin && this.droppedPin.longitude != null && this.droppedPin.latitude != null) {
        const pinMarker = this.buildMapMarker(this.droppedPin, { isPin: true })
        if (pinMarker) markers.push(pinMarker)
      }
      return markers
    },
    shouldShowClusterSheet() {
      return this.clusterPickerItems.length > 1 && !this.selectedLocation
    },
    amapPolylines() {
      const lines = []
      if (this.navigationPolyline.length) {
        lines.push({
          points: this.navigationPolyline,
          color: '#4d86f8',
          width: 8,
          dottedLine: false,
          arrowLine: true,
          borderColor: '#9cc0ff',
          borderWidth: 2
        })
      }
      this.buildAllFenceOverlays().polylines.forEach((line) => lines.push(line))
      return lines
    },
    amapPolygons() {
      return this.buildAllFenceOverlays().polygons
    },
    visibleLocations() {
      const keyword = this.normalizeSearchToken(this.searchKeyword)
      if (!keyword) return this.locationList
      return this.locationList.filter((item) => {
        if (!item._searchText) {
          item._searchText = this.buildLocationSearchText(item._rawItem || item, {
            name: item.name,
            shortName: item.shortName,
            detail: item.detail,
            description: item.description
          })
        }
        return item._searchText.includes(keyword)
      })
    },
    categoryTiles() {
      return QUICK_CATEGORY_CONFIG.map((config) => ({
        id: config.id,
        name: config.label,
        typeClass: config.typeClass,
        iconKey: config.iconKey,
        facilityTypes: config.facilityTypes
      }))
    },
    nearbyLocations() {
      const source = this.visibleLocations.length ? this.visibleLocations : this.locationList
      return source
        .filter((item) => item.longitude != null && item.latitude != null)
        .slice(0, 4)
    },
    nearbyDisplayCards() {
      if (this.showNearbyPanel && this.nearbyFacilities.length) {
        return this.nearbyFacilities
      }
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
      if (this.shouldShowClusterSheet) return false
      if (this.showHistoryPanel) return false
      if (this.showNearbyPanel) return !this.selectedLocation
      const hasKeyword = !!(this.searchKeyword || '').trim()
      return !this.selectedLocation && (hasKeyword || !!this.tempSearchLocation)
    },
    shouldShowHistorySheet() {
      return this.showHistoryPanel && !this.selectedLocation && !this.shouldShowClusterSheet
    },
    poiCardData() {
      const item = this.selectedLocation
      if (!item) {
        return {
          name: '',
          category: '',
          walkText: '',
          address: '',
          description: '',
          statusText: '',
          businessHours: '',
          avgPriceText: '',
          floorCount: 0,
          images: [],
          imageUrl: ''
        }
      }
      const images = this.resolvePoiImages(item)
      const raw = item._rawItem || item
      return {
        name: item.name || '',
        category: getPlaceTypeLabel(item.placeType, item.placeTypeName || getFacilityTypeLabel(item.facilityType)),
        walkText: this.formatWalkSummary(item.longitude, item.latitude),
        address: item.detail || raw.locationDesc || raw.location || '',
        description: item.description || raw.description || '',
        statusText: this.formatPlaceStatus(raw.status || item.status),
        businessHours: raw.businessHours || item.businessHours || '',
        avgPriceText: this.formatAvgPrice(raw.avgPrice ?? item.avgPrice, item.placeType),
        floorCount: this.placeFloors.length,
        images,
        imageUrl: item.coverImage || images[0] || ''
      }
    },
    poiHasCoverImage() {
      if (!this.selectedLocation) return false
      return this.resolvePoiImages(this.selectedLocation).length > 0
        || !!(this.selectedLocation.coverImage || '').trim()
    },
    showFloorPanel() {
      if (!this.selectedLocation) return false
      if (this.placeFloors.length) return true
      return isBuildingPlaceType(this.selectedLocation.placeType)
    },
    activeFloorPlanUrl() {
      return this.activeFloorPlan?.imageUrl || ''
    }
  },
  watch: {
    mapScale(value) {
      const scale = Number(value)
      if (Number.isFinite(scale)) this.viewScale = scale
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
      // 先保持校园点位视野，定位成功后不强制跳走
      this.fetchCurrentLocation({ centerMap: false })
    },
    async loadFacilityTypes() {
      try {
        const res = await getFacilityTypes()
        const types = Array.isArray(res?.data) ? res.data : (Array.isArray(res) ? res : [])
        if (types.length) {
          applyFacilityTypeLabels(types)
          this.categories = types.map((item) => ({ id: Number(item.value), name: item.label }))
          // 保持不过滤状态，默认展示全部点位
          this.selectedFacilityTypes = []
        }
      } catch (error) {
        console.warn('加载设施类型字典失败，使用本地兜底', error)
      }
    },
    async loadMapData(options = {}) {
      const resetViewport = !!options.resetViewport
      const forceRefresh = !!options.forceRefresh
      const requestId = ++this.mapDataRequestId
      const selected = [...this.selectedFacilityTypes].sort((a, b) => a - b)
      const hasActiveFilter = selected.length > 0
      const apiBase = getApiBaseUrl() || BASE_URL
      const requestUrl = `${apiBase}/api/v1/map-places`
      this.mapLoadDebug = `请求中 ${requestUrl}`
      try {
        let records = (!forceRefresh && Array.isArray(this.markerCache.all) && this.markerCache.all.length)
          ? this.markerCache.all
          : null
        if (!records) {
          const rawRes = await this.fetchEnabledMapPlaces(forceRefresh)
          const placeList = Array.isArray(rawRes?.data) ? rawRes.data : []
          records = placeList
            .filter((item) => item && item.mapVisible !== false)
            .map((item) => toMapPlaceMarker(item))
            .filter(Boolean)
            .filter((item) => item.longitude != null && item.latitude != null)
          this.markerCache = { all: records }
        }
        if (requestId !== this.mapDataRequestId) return
        const filtered = hasActiveFilter
          ? records.filter((item) => selected.includes(Number(item.facilityType)))
          : records
        this.markerCache[cacheKeySafe(hasActiveFilter, selected)] = filtered
        this.locationList = filtered
          .map((item) => this.toMarkerItem(item))
          .filter(Boolean)
        if (resetViewport) {
          const nextCenter = this.resolveInitialMapCenter(this.locationList)
          this.setMapViewport({
            longitude: nextCenter.longitude,
            latitude: nextCenter.latitude,
            scale: DEFAULT_MAP_SCALE
          })
        }
        this.syncNearestLocation()
        this.refreshSelectedLocation()
        const seedHit = this.locationList
          .filter((item) => [1, 2, 3].includes(Number(item.id)))
          .map((item) => item.name)
        const sampleSource = seedHit.length
          ? seedHit
          : this.locationList.slice(0, 3).map((item) => item.name)
        const sample = sampleSource.filter(Boolean).join('、')
        this.mapLoadDebug = `API ${apiBase} · 原始${records.length} · 展示${this.locationList.length}${sample ? ` · ${sample}` : ''}`
        console.info('[campus-map]', this.mapLoadDebug, {
          markers: this.amapMarkers?.length,
          seedIds: this.locationList.filter((item) => [1, 2, 3].includes(Number(item.id))).map((item) => item.id)
        })
        if (!this.locationList.length) {
          uni.showToast({ title: '暂无校园点位数据', icon: 'none' })
        } else if (resetViewport) {
          uni.showToast({ title: `已加载 ${this.locationList.length} 个校园点位`, icon: 'none', duration: 1800 })
        }
      } catch (error) {
        console.error('加载地图数据失败', apiBase, error)
        this.markerCache = {}
        this.locationList = []
        const raw = typeof error === 'string'
          ? error
          : `${error?.message || error?.msg || error?.errMsg || ''}`
        const hint = /request:fail|connect|timeout|无法连接|ECONNREFUSED/i.test(raw)
          ? `后端未启动（${apiBase}）`
          : (/<!doctype|<html|网页而非 JSON/i.test(raw) ? 'API 指向了前端页面，请确认后端端口' : (raw || '请求失败'))
        this.mapLoadDebug = `失败 ${apiBase} · ${hint}（点此重试）`
        uni.showToast({ title: `点位加载失败：${hint}`, icon: 'none', duration: 3000 })
      }

      function cacheKeySafe(hasFilter, selectedTypes) {
        return hasFilter ? selectedTypes.join(',') : 'all'
      }
    },
    fetchEnabledMapPlaces(forceRefresh = false) {
      if (!forceRefresh && mapPlacesCache) return Promise.resolve(mapPlacesCache)
      if (!mapPlacesInflight) {
        mapPlacesInflight = request({
          url: '/api/v1/map-places',
          method: 'GET',
          params: { status: 'ENABLED' },
          showError: false
        }).then((res) => {
          mapPlacesCache = res
          return res
        }).finally(() => {
          mapPlacesInflight = null
        })
      }
      return mapPlacesInflight
    },
    setMapViewport({ longitude, latitude, scale } = {}) {
      const nextScale = Number(scale)
      if (Number.isFinite(nextScale) && Math.abs(nextScale - Number(this.mapScale)) >= 0.25) {
        this.mapScale = nextScale
        this.viewScale = nextScale
      }
      const lng = Number(longitude)
      const lat = Number(latitude)
      if (!Number.isFinite(lng) || !Number.isFinite(lat)) return
      const curLng = Number(this.mapCenter?.longitude)
      const curLat = Number(this.mapCenter?.latitude)
      if (Math.abs(lng - curLng) <= 1e-5 && Math.abs(lat - curLat) <= 1e-5) {
        this.viewCenter = { longitude: lng, latitude: lat }
        return
      }
      this.mapCenter = { longitude: lng, latitude: lat }
      this.viewCenter = { longitude: lng, latitude: lat }
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
      const displayName = item.name || item.markerName || item.facilityName || ''
      const typeClass = this.getTypeClass(item.facilityType, displayName)
      const icon = this.getFacilityIcon(item.facilityType, displayName)
      const route = this.getMarkerRoute(item)
      const coverImage = this.getMarkerCoverImage(item)
      const shortName = displayName || '地点'
      const detail = item.locationDesc || item.location
        || getFacilityTypeLabel(item.facilityType, item.facilityTypeName)
      return {
        id: item.id,
        name: displayName,
        shortName,
        icon,
        coverImage,
        facilityType: item.facilityType,
        facilityId: item.facilityId != null ? item.facilityId : item.id,
        placeType: item.placeType || '',
        placeTypeName: item.facilityTypeName || item.placeType || '',
        sceneType: item.sceneType || '',
        category: resolveFacilityType(item.facilityType).mapCategory,
        typeClass,
        distance: this.formatDistance(longitude, latitude),
        detail,
        description: item.description || '',
        status: item.status || '',
        businessHours: item.businessHours || '',
        avgPrice: item.avgPrice,
        updatedAt: item.updatedAt || '',
        fence: item.fence || null,
        children: item.children || [],
        floorPlan: item.floorPlan || null,
        indoorPosition: item.indoorPosition || null,
        route,
        longitude,
        latitude,
        _rawItem: item,
        _searchText: ''
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
      if (item.imageUrl) return item.imageUrl
      if (item.coverImage) return item.coverImage
      if (item.thumbnailUrl) return item.thumbnailUrl
      const images = item.images
      if (Array.isArray(images) && images.length) {
        const first = images[0]
        return typeof first === 'string' ? first : (first?.imageUrl || '')
      }
      if (typeof images === 'string' && images.trim()) {
        try {
          const parsed = JSON.parse(images)
          if (Array.isArray(parsed) && parsed.length) return parsed[0]
        } catch (error) {
          return ''
        }
      }
      return ''
    },
    resolvePoiImages(item) {
      if (!item) return []
      const urls = []
      const push = (url) => {
        const value = (url || '').trim()
        if (value && !urls.includes(value)) urls.push(value)
      }
      push(item.coverImage)
      push(item.imageUrl)
      push(item.thumbnailUrl)
      push(item._rawItem?.imageUrl)
      const images = item.images || item._rawItem?.images || []
      if (Array.isArray(images)) {
        images.forEach((entry) => push(typeof entry === 'string' ? entry : entry?.imageUrl))
      } else if (typeof images === 'string' && images.trim()) {
        try {
          const parsed = JSON.parse(images)
          if (Array.isArray(parsed)) parsed.forEach((entry) => push(entry))
        } catch (error) {
          // ignore invalid image json
        }
      }
      return urls
    },
    buildPoiFooterText(item) {
      if (!item) return ''
      const parts = []
      if (item.placeTypeName) parts.push(item.placeTypeName)
      const desc = (item.description || '').trim()
      if (desc) parts.push(desc)
      else if (item.detail) parts.push(item.detail)
      return parts.filter(Boolean).join(' | ')
    },
    resolveMarkerStyle(item, options = {}) {
      if (options.isSearch || options.isPin) return POI_MARKER_STYLE.SEARCH
      const placeType = item?.placeType || item?._rawItem?.placeType
      if (placeType && POI_MARKER_STYLE[placeType]) return POI_MARKER_STYLE[placeType]
      const facilityType = Number(item?.facilityType)
      if (facilityType === 1) return POI_MARKER_STYLE.CANTEEN
      if (facilityType === 2) return POI_MARKER_STYLE.SPORTS_GROUND
      if (facilityType === 3) return POI_MARKER_STYLE.TEACHING_BUILDING
      if (facilityType === 4) return POI_MARKER_STYLE.DORMITORY_BUILDING
      return POI_MARKER_STYLE.DEFAULT
    },
    wrapMarkerLabel(name, maxPerLine = MARKER_LABEL_CHARS_PER_LINE) {
      const text = `${name || ''}`.trim()
      if (!text) return '地点'
      if (text.length <= maxPerLine) return text
      const second = text.slice(maxPerLine, maxPerLine * 2)
      if (text.length <= maxPerLine * 2) return `${text.slice(0, maxPerLine)}\n${second}`
      return `${text.slice(0, maxPerLine)}\n${text.slice(maxPerLine, maxPerLine * 2 - 1)}…`
    },
    getMarkerIconPath(item, options = {}) {
      const style = this.resolveMarkerStyle(item, options)
      const localPath = style.icon || '/static/icons/map/pin-teaching.png'
      // H5：用当前站点绝对路径加载 static 图标，避免相对路径/超大 base64 导致加载超时
      if (typeof window !== 'undefined' && window.location?.origin) {
        return `${window.location.origin}${localPath}`
      }
      return localPath
    },
    resolveMarkerLabelFontSize(scale = this.viewScale) {
      const value = Number(scale)
      if (!Number.isFinite(value)) return 11
      if (value >= 18) return 13
      if (value >= 16.5) return 12
      if (value >= 15.5) return 11
      return 10
    },
    shouldShowCampusMarkers(scale = this.viewScale) {
      const value = Number(scale)
      return !Number.isFinite(value) || value >= CLUSTER_SCALE_MIN
    },
    shouldShowMarkerLabel(scale = this.viewScale) {
      const value = Number(scale)
      if (!Number.isFinite(value)) return false
      return value >= 16
    },
    resolveClusterDistanceMeters(scale = this.viewScale) {
      const value = Number(scale)
      if (!Number.isFinite(value) || value > CLUSTER_SCALE_MAX || value < CLUSTER_SCALE_MIN) return 0
      // 远距阈值加大，避免屏幕上已重叠的聚合点因贪心分组而拆成两团
      if (value >= 16) return 90
      if (value >= 15) return 160
      if (value >= 14) return 280
      if (value >= 13) return 420
      if (value >= 12) return 650
      if (value >= 11) return 900
      if (value >= 10) return 1200
      if (value >= 9) return 1700
      return 2400
    },
    metersPerPixel(scale = this.viewScale) {
      const zoom = Number(scale)
      const lat = Number(this.viewCenter?.latitude || this.mapCenter?.latitude) || CAMPUS_FALLBACK_CENTER.latitude
      const safeZoom = Number.isFinite(zoom) ? zoom : DEFAULT_MAP_SCALE
      return (156543.03392 * Math.cos(this.toRadians(lat))) / (2 ** safeZoom)
    },
    assignMarkerLabelLayouts(items) {
      const slots = [
        { anchorX: 18, anchorY: -16 },
        { anchorX: -18, anchorY: -16 },
        { anchorX: 0, anchorY: 8 },
        { anchorX: 24, anchorY: 6 },
        { anchorX: -24, anchorY: 6 },
        { anchorX: 0, anchorY: -36 }
      ]
      const placed = []
      const layouts = {}
      const mpp = this.metersPerPixel()
      items.forEach((item) => {
        const lng = Number(item.longitude)
        const lat = Number(item.latitude)
        if (!Number.isFinite(lng) || !Number.isFinite(lat)) return
        let best = slots[0]
        let bestDist = -1
        slots.forEach((slot) => {
          const dLng = (slot.anchorX * mpp) / (111320 * Math.cos(this.toRadians(lat)))
          const dLat = (-slot.anchorY * mpp) / 110540
          const labelLng = lng + dLng
          const labelLat = lat + dLat
          const nearest = placed.reduce((min, point) => {
            const dist = this.distanceBetween(labelLng, labelLat, point.lng, point.lat)
            return dist < min ? dist : min
          }, Infinity)
          if (nearest > bestDist) {
            best = slot
            bestDist = nearest
          }
        })
        const hide = placed.length > 0 && bestDist < 28
        const dLng = (best.anchorX * mpp) / (111320 * Math.cos(this.toRadians(lat)))
        const dLat = (-best.anchorY * mpp) / 110540
        if (!hide) placed.push({ lng: lng + dLng, lat: lat + dLat })
        layouts[item.id] = { ...best, hide }
      })
      return layouts
    },
    distanceBetween(lng1, lat1, lng2, lat2) {
      const earthRadius = 6371000
      const rLat1 = this.toRadians(Number(lat1))
      const rLat2 = this.toRadians(Number(lat2))
      const dLat = this.toRadians(Number(lat2) - Number(lat1))
      const dLng = this.toRadians(Number(lng2) - Number(lng1))
      const a = Math.sin(dLat / 2) ** 2 + Math.cos(rLat1) * Math.cos(rLat2) * Math.sin(dLng / 2) ** 2
      return earthRadius * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
    },
    refreshGroupCentroid(group) {
      const count = group.items.length
      if (!count) return
      group.longitude = group.items.reduce((sum, member) => sum + Number(member.longitude), 0) / count
      group.latitude = group.items.reduce((sum, member) => sum + Number(member.latitude), 0) / count
    },
    groupsShouldMerge(left, right, maxDist) {
      if (!left?.items?.length || !right?.items?.length) return false
      const centroidDist = this.distanceBetween(left.longitude, left.latitude, right.longitude, right.latitude)
      if (centroidDist <= maxDist) return true
      return left.items.some((a) => right.items.some((b) => (
        this.distanceBetween(a.longitude, a.latitude, b.longitude, b.latitude) <= maxDist
      )))
    },
    mergeNearbyMarkerGroups(groups, maxDist) {
      const result = groups.map((group) => ({
        ...group,
        items: [...group.items]
      }))
      let merged = true
      while (merged) {
        merged = false
        for (let i = 0; i < result.length; i += 1) {
          const left = result[i]
          if (left.locked) continue
          for (let j = i + 1; j < result.length; j += 1) {
            const right = result[j]
            if (right.locked) continue
            if (!this.groupsShouldMerge(left, right, maxDist)) continue
            left.items.push(...right.items)
            this.refreshGroupCentroid(left)
            result.splice(j, 1)
            merged = true
            break
          }
          if (merged) break
        }
      }
      return result
    },
    buildCampusMarkerGroups() {
      const points = this.visibleLocations.filter((item) => item.longitude != null && item.latitude != null)
      const selectedId = this.selectedLocation && this.isPersistedMapPlace(this.selectedLocation)
        ? this.selectedLocation.id
        : null
      const maxDist = this.resolveClusterDistanceMeters()
      if (!maxDist || !this.shouldShowCampusMarkers()) {
        return points.map((item, index) => ({
          markerId: Number(item.id),
          items: [item],
          longitude: Number(item.longitude),
          latitude: Number(item.latitude),
          index
        }))
      }
      const groups = []
      points.forEach((item) => {
        const longitude = Number(item.longitude)
        const latitude = Number(item.latitude)
        if (selectedId != null && item.id === selectedId) {
          groups.push({
            markerId: Number(item.id),
            items: [item],
            longitude,
            latitude,
            locked: true
          })
          return
        }
        let best = null
        let bestDist = Infinity
        groups.forEach((group) => {
          if (group.locked) return
          const nearest = group.items.reduce((min, member) => {
            const dist = this.distanceBetween(longitude, latitude, member.longitude, member.latitude)
            return dist < min ? dist : min
          }, Infinity)
          if (nearest <= maxDist && nearest < bestDist) {
            best = group
            bestDist = nearest
          }
        })
        if (best) {
          best.items.push(item)
          this.refreshGroupCentroid(best)
          return
        }
        groups.push({
          items: [item],
          longitude,
          latitude
        })
      })
      return this.mergeNearbyMarkerGroups(groups, maxDist).map((group, index) => {
        const count = group.items.length
        return {
          markerId: count === 1 ? Number(group.items[0].id) : (CLUSTER_ID_BASE + index),
          items: group.items,
          longitude: count === 1 ? Number(group.items[0].longitude) : group.longitude,
          latitude: count === 1 ? Number(group.items[0].latitude) : group.latitude,
          index
        }
      })
    },
    getClusterIconPath(count) {
      const value = Math.floor(Number(count))
      const file = Number.isFinite(value) && value >= 2
        ? `/static/icons/map/pin-cluster-${Math.min(20, value)}.png`
        : '/static/icons/map/pin-cluster.png'
      if (typeof window !== 'undefined' && window.location?.origin) {
        return `${window.location.origin}${file}`
      }
      return file
    },
    buildClusterMarker(group) {
      const count = group.items.length
      const size = count >= 10 ? 52 : (count >= 5 ? 46 : 42)
      return {
        id: group.markerId,
        longitude: group.longitude,
        latitude: group.latitude,
        iconPath: this.getClusterIconPath(count),
        width: size,
        height: size,
        anchor: { x: 0.5, y: 0.5 }
      }
    },
    openClusterPicker(items) {
      this.suppressMapTapUntil = Date.now() + 500
      this.clusterPickerItems = items || []
      this.showNearbyPanel = false
      this.showHistoryPanel = false
    },
    closeClusterPicker() {
      this.clusterPickerItems = []
    },
    selectClusterItem(item) {
      this.closeClusterPicker()
      this.selectLocation(item)
    },
    buildMapMarker(item, options = {}) {
      const isSearch = !!options.isSearch
      const isPin = !!options.isPin
      const markerId = isPin ? -8888 : (isSearch ? -9999 : Number(item.id))
      if (!Number.isFinite(markerId)) return null
      const isSelected = !isSearch && !isPin && this.selectedLocation && this.selectedLocation.id === item.id
      const fullName = item.name || item.shortName || (isPin ? '地图点位' : (isSearch ? '搜索结果' : '地点'))
      const markerWidth = isSelected ? 30 : 26
      const markerHeight = isSelected ? 40 : 35
      const marker = {
        id: markerId,
        longitude: Number(item.longitude),
        latitude: Number(item.latitude),
        // 不要设 title：点击后会弹出原生白底气泡且可能残留不消失
        iconPath: this.getMarkerIconPath(item, { isSearch, isPin }),
        width: markerWidth,
        height: markerHeight,
        anchor: { x: 0.5, y: 1 }
      }
      const labelLayout = options.labelLayout || { anchorX: 16, anchorY: -16, hide: false }
      if (!isSelected && !labelLayout.hide && this.shouldShowMarkerLabel()) {
        marker.label = {
          content: this.wrapMarkerLabel(fullName),
          color: '#202833',
          fontSize: this.resolveMarkerLabelFontSize(),
          bgColor: '#ffffff',
          borderWidth: 1,
          borderColor: '#e6ebf0',
          borderRadius: 8,
          padding: 4,
          textAlign: 'center',
          anchorX: labelLayout.anchorX,
          anchorY: labelLayout.anchorY
        }
      }
      return marker
    },
    onRegionChange(event) {
      const raw = event?.detail || event || {}
      const nested = raw.detail
      const detail = nested && typeof nested === 'object' && (nested.scale != null || nested.centerLocation)
        ? nested
        : raw
      const eventType = detail.type || raw.type || event?.type
      if (eventType === 'begin' || eventType === 'start') return
      if (eventType && eventType !== 'end') return
      // 手势缩放只更新聚类用的视野，不回写 :scale / :center，避免已显示点被地图组件再次拉取
      const scale = Number(detail.scale)
      if (Number.isFinite(scale) && Math.abs(scale - Number(this.viewScale)) >= 0.25) {
        this.viewScale = scale
      }
      const center = detail.centerLocation || detail.center || {}
      const lng = Number(center.longitude ?? detail.longitude)
      const lat = Number(center.latitude ?? detail.latitude)
      if (Number.isFinite(lng) && Number.isFinite(lat)) {
        this.viewCenter = { longitude: lng, latitude: lat }
      }
    },
    onMapUpdated() {
      this.mapUpdatedOnce = true
    },
    getShortName(name) {
      if (!name) return '地点'
      return name
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
    formatWalkSummary(longitude, latitude) {
      const distance = this.calculateDistance(longitude, latitude)
      if (distance == null) return '步行距离待定位'
      const minutes = Math.max(1, Math.round(distance / WALK_METERS_PER_MINUTE))
      if (distance >= 1000) {
        const km = (distance / 1000).toFixed(distance >= 10000 ? 0 : 1)
        return `步行 ${km} 公里 ${minutes} 分钟`
      }
      return `步行 ${Math.round(distance)} 米 ${minutes} 分钟`
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
      if (!this.selectedLocation) return
      if (!this.visibleLocations.length) {
        this.closePopup()
        return
      }
      const matched = this.visibleLocations.find((item) => item.id === this.selectedLocation.id)
      if (!matched) {
        this.closePopup()
        return
      }
      this.selectedLocation = {
        ...matched,
        ...this.selectedLocation,
        distance: matched.distance || this.selectedLocation.distance
      }
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
      this.closeSuggestPanel()
      this.refreshSelectedLocation()
    },
    async handleSearch() {
      const keyword = (this.searchKeyword || '').trim()
      if (!keyword) {
        this.tempSearchLocation = null
        this.closeSuggestPanel()
        this.refreshSelectedLocation()
        return
      }
      // 优先用建议列表中高亮或第一项
      const suggestion = this.searchSuggestions[this.searchHighlightIndex >= 0 ? this.searchHighlightIndex : 0]
      if (suggestion) {
        this.selectSuggestion(suggestion, { keepKeyword: true })
        return
      }
      // 本地过滤命中，直接定位第一个
      if (this.visibleLocations.length) {
        this.tempSearchLocation = null
        this.closeSuggestPanel()
        const firstVisible = this.visibleLocations[0]
        this.mapCenter = {
          longitude: Number(firstVisible.longitude),
          latitude: Number(firstVisible.latitude)
        }
        this.mapScale = 17
        return
      }
      // 本地未匹配，调后端校内搜索
      uni.showLoading({ title: '搜索中...', mask: true })
      try {
        const searchRes = await searchFacilities({ keyword, limit: 20 }, { showError: false })
        const searchList = Array.isArray(searchRes?.data) ? searchRes.data : (Array.isArray(searchRes) ? searchRes : [])
        const matched = searchList.find((item) => item.longitude != null && item.latitude != null)
        uni.hideLoading()
        if (matched) {
          this.selectSuggestion(matched, { keepKeyword: true })
          return
        }
        this.tempSearchLocation = null
        this.selectedLocation = null
        uni.showToast({ title: '未找到匹配地点', icon: 'none' })
      } catch (error) {
        uni.hideLoading()
        this.tempSearchLocation = null
        this.selectedLocation = null
        const reason = this.resolveSearchErrorMessage(error)
        uni.showToast({ title: reason, icon: 'none' })
      }
    },
    resolveSearchErrorMessage(error) {
      if (!error) return '地点搜索失败'
      if (typeof error === 'string') return error
      const msg = error.msg || error.message
      if (msg) return msg
      if (error.statusCode) return `搜索失败(${error.statusCode})`
      return '地点搜索失败'
    },
    handleKeywordInput() {
      this.tempSearchLocation = null
      this.refreshSelectedLocation()
      const keyword = (this.searchKeyword || '').trim()
      if (!keyword) {
        this.closeSuggestPanel()
        return
      }
      if (this.showNearbyPanel || this.showHistoryPanel) {
        this.showNearbyPanel = false
        this.showHistoryPanel = false
        this.nearbyFacilities = []
        this.navigationHistory = []
      }
      if (this.searchDebounceTimer) {
        clearTimeout(this.searchDebounceTimer)
      }
      this.searchDebounceTimer = setTimeout(() => {
        this.fetchSearchSuggestions(keyword)
      }, 300)
    },
    async fetchSearchSuggestions(keyword) {
      const requestId = ++this.searchRequestId
      try {
        const res = await searchFacilities({ keyword, limit: 8 })
        if (requestId !== this.searchRequestId) return
        const list = Array.isArray(res?.data) ? res.data : (Array.isArray(res) ? res : [])
        this.searchSuggestions = list.filter((item) => item.longitude != null && item.latitude != null)
        this.searchHighlightIndex = this.searchSuggestions.length ? 0 : -1
      } catch (error) {
        if (requestId === this.searchRequestId) {
          this.searchSuggestions = []
          this.searchHighlightIndex = -1
        }
      }
    },
    selectSuggestion(item, options = {}) {
      if (!item) return
      const keepKeyword = !!options.keepKeyword
      this.searchKeyword = keepKeyword ? this.searchKeyword : (item.markerName || item.name || '')
      this.searchSuggestions = []
      this.searchHighlightIndex = -1
      this.tempSearchLocation = null
      this.clearDroppedPin()
      const target = {
        id: item.id || item.markerId || item.facilityId,
        name: item.markerName || item.name,
        shortName: this.getShortName(item.markerName || item.name),
        icon: '',
        coverImage: '',
        facilityType: item.facilityType,
        facilityId: item.facilityId,
        category: 0,
        typeClass: 'search',
        distance: item.distance != null ? this.formatDistance(Number(item.longitude), Number(item.latitude)) : '--',
        detail: item.location || '',
        description: item.description || '',
        route: item.facilityId ? buildFacilityDetailRoute(item.facilityType, item.facilityId) : '',
        longitude: Number(item.longitude),
        latitude: Number(item.latitude)
      }
      if (this.isPersistedMapPlace(target)) {
        const existing = this.visibleLocations.find((item) => Number(item.id) === Number(target.id))
        this.selectLocation(existing || target)
        return
      }
      this.selectedLocation = target
      this.mapCenter = { longitude: target.longitude, latitude: target.latitude }
      this.mapScale = 17
    },
    closeSuggestPanel() {
      this.searchSuggestions = []
      this.searchHighlightIndex = -1
    },
    showNearbyMore() {
      this.toggleNearbyPanel(true)
    },
    async toggleNearbyPanel(forceOpen = false) {
      if (forceOpen) {
        this.showNearbyPanel = true
      } else {
        this.showNearbyPanel = !this.showNearbyPanel
      }
      if (this.showNearbyPanel) {
        this.showHistoryPanel = false
        await this.fetchNearbyFacilities()
      } else {
        this.nearbyFacilities = []
      }
    },
    async fetchNearbyFacilities() {
      let longitude = this.currentLocation.longitude
      let latitude = this.currentLocation.latitude
      if (longitude == null || latitude == null) {
        longitude = this.mapCenter.longitude
        latitude = this.mapCenter.latitude
      }
      if (longitude == null || latitude == null) return
      this.nearbyLoading = true
      try {
        const res = await getNearbyFacilities({
          longitude,
          latitude,
          radius: 1500,
          limit: 12,
          sortBy: 'distance'
        })
        const data = res?.data || res || {}
        const list = Array.isArray(data.list) ? data.list : (Array.isArray(data) ? data : [])
        this.nearbyFacilities = list
          .filter((item) => item.longitude != null && item.latitude != null)
          .map((item) => ({
            id: item.id || item.markerId || item.facilityId,
            name: item.markerName || item.name || '未命名地点',
            shortName: this.getShortName(item.markerName || item.name || '地点'),
            coverImage: '',
            facilityType: item.facilityType,
            facilityId: item.facilityId,
            category: 0,
            typeClass: this.getTypeClass(item.facilityType, item.markerName),
            distance: this.formatNearbyDistance(item.distance),
            detail: item.location || item.facilityTypeName || '',
            description: item.description || '暂无简介',
            route: item.facilityId ? buildFacilityDetailRoute(item.facilityType, item.facilityId) : '',
            longitude: Number(item.longitude),
            latitude: Number(item.latitude)
          }))
      } catch (error) {
        uni.showToast({ title: '附近地点加载失败', icon: 'none' })
        this.nearbyFacilities = []
      } finally {
        this.nearbyLoading = false
      }
    },
    formatNearbyDistance(distance) {
      if (distance == null) return '--'
      const value = Number(distance)
      if (!Number.isFinite(value)) return '--'
      if (value >= 1000) return `${(value / 1000).toFixed(2)}km`
      return `${Math.round(value)}m`
    },
    async toggleHistoryPanel(forceOpen = false) {
      if (forceOpen) {
        this.showHistoryPanel = true
      } else {
        this.showHistoryPanel = !this.showHistoryPanel
      }
      if (this.showHistoryPanel) {
        this.showNearbyPanel = false
        await this.loadNavigationHistory()
      } else {
        this.navigationHistory = []
      }
    },
    async loadNavigationHistory() {
      this.historyLoading = true
      try {
        const res = await getNavigationHistory({ pageNum: 1, pageSize: 20 })
        const data = res?.data || res || {}
        const records = Array.isArray(data.records) ? data.records : (Array.isArray(data) ? data : [])
        this.navigationHistory = records.map((item) => ({
          id: item.id,
          name: item.toMarkerName || '历史目的地',
          shortName: this.getShortName(item.toMarkerName || '目的地'),
          longitude: item.toLongitude != null ? Number(item.toLongitude) : null,
          latitude: item.toLatitude != null ? Number(item.toLatitude) : null,
          distance: this.formatNearbyDistance(item.distance),
          duration: this.formatDuration(item.duration),
          status: this.formatHistoryStatus(item.status),
          time: this.formatHistoryTime(item.createTime || item.arriveTime)
        }))
      } catch (error) {
        uni.showToast({ title: '导航历史加载失败', icon: 'none' })
        this.navigationHistory = []
      } finally {
        this.historyLoading = false
      }
    },
    formatDuration(seconds) {
      if (seconds == null) return '--'
      const value = Number(seconds)
      if (!Number.isFinite(value)) return '--'
      if (value < 60) return `${Math.round(value)}秒`
      const minutes = Math.floor(value / 60)
      if (minutes < 60) return `${minutes}分钟`
      const hours = Math.floor(minutes / 60)
      return `${hours}小时${minutes % 60}分钟`
    },
    formatHistoryStatus(status) {
      const map = { 1: '进行中', 2: '已到达', 3: '已取消' }
      return map[Number(status)] || '未知'
    },
    formatHistoryTime(time) {
      if (!time) return ''
      const str = `${time}`
      const match = str.match(/(\d{4}-\d{2}-\d{2})[T\s](\d{2}:\d{2})/)
      if (match) return `${match[1]} ${match[2]}`
      return str.slice(0, 16)
    },
    selectHistoryItem(item) {
      if (item.longitude == null || item.latitude == null) {
        uni.showToast({ title: '该记录缺少坐标信息', icon: 'none' })
        return
      }
      this.showHistoryPanel = false
      this.navigationHistory = []
      const target = {
        id: `history-${item.id}`,
        name: item.name,
        shortName: item.shortName,
        icon: '',
        coverImage: '',
        category: 0,
        typeClass: 'search',
        distance: item.distance,
        detail: `导航历史 · ${item.status}`,
        description: `${item.time ? item.time + ' · ' : ''}${item.duration || ''}`,
        route: '',
        longitude: item.longitude,
        latitude: item.latitude
      }
      this.selectedLocation = target
      this.mapCenter = { longitude: item.longitude, latitude: item.latitude }
      this.mapScale = 17
    },
    async selectCategory(categoryKey) {
      if (this.selectedCategoryKeys.includes(categoryKey)) {
        this.selectedCategoryKeys = this.selectedCategoryKeys.filter((key) => key !== categoryKey)
      } else {
        this.selectedCategoryKeys = [...this.selectedCategoryKeys, categoryKey]
      }
      const typeSet = new Set()
      this.selectedCategoryKeys.forEach((key) => {
        const config = QUICK_CATEGORY_CONFIG.find((item) => item.id === key)
        ;(config?.facilityTypes || []).forEach((type) => typeSet.add(Number(type)))
      })
      this.selectedFacilityTypes = [...typeSet]
      this.tempSearchLocation = null
      await this.loadMapData()
    },
    isPersistedMapPlace(item) {
      const id = Number(item?.id)
      return Number.isFinite(id) && id > 0
    },
    resolveFenceStyle(item) {
      const placeType = item?.placeType || item?._rawItem?.placeType
      if (placeType && FENCE_STYLE_BY_PLACE[placeType]) return FENCE_STYLE_BY_PLACE[placeType]
      const sceneType = item?.sceneType || item?._rawItem?.sceneType
      return FENCE_STYLE_BY_SCENE[sceneType] || FENCE_STYLE_BY_SCENE.OTHER
    },
    applySelectedFence(item) {
      const overlays = this.buildFenceOverlay(item, { selected: true })
      this.selectedFencePolygons = overlays.polygons
      this.selectedFencePolylines = overlays.polylines
    },
    clearSelectedFence() {
      this.selectedFencePolygons = []
      this.selectedFencePolylines = []
    },
    buildFenceOverlay(item, options = {}) {
      const fence = parsePlaceFence(item?.fence || item?._rawItem?.fence)
      if (!fence) return { polygons: [], polylines: [] }
      const style = this.resolveFenceStyle(item)
      const selected = !!options.selected
      if (fence.geometryType === 'POLYGON') {
        return {
          polygons: [{
            points: fence.path,
            strokeWidth: selected ? 3 : 2,
            strokeColor: style.strokeColor,
            fillColor: selected ? style.fillColor : this.softenFenceFill(style.fillColor),
            zIndex: selected ? 4 : 1
          }],
          polylines: []
        }
      }
      return {
        polygons: [],
        polylines: [{
          points: fence.path,
          color: style.strokeColor,
          width: selected ? 5 : 3,
          dottedLine: false,
          arrowLine: false
        }]
      }
    },
    softenFenceFill(color) {
      if (!color || color.length !== 9) return color
      return `${color.slice(0, 7)}1A`
    },
    buildAllFenceOverlays() {
      const polygons = []
      const polylines = []
      const selectedId = this.selectedLocation && this.isPersistedMapPlace(this.selectedLocation)
        ? this.selectedLocation.id
        : null
      ;(this.locationList || []).forEach((item) => {
        const overlay = this.buildFenceOverlay(item, { selected: selectedId != null && item.id === selectedId })
        overlay.polygons.forEach((polygon) => polygons.push(polygon))
        overlay.polylines.forEach((line) => polylines.push(line))
      })
      return { polygons, polylines }
    },
    async selectLocation(item) {
      if (!item) return
      this.clearDroppedPin()
      this.resetPlaceDetailState()
      this.selectedLocation = item
      this.sheetExpandLevel = 'half'
      this.activePanel = 'info'
      this.markerRenderToken += 1
      this.applySelectedFence(item)
      if (item.longitude != null && item.latitude != null) {
        this.mapCenter = {
          longitude: Number(item.longitude),
          latitude: Number(item.latitude)
        }
        this.mapScale = 17
      }
      if (!this.isPersistedMapPlace(item)) return
      const requestId = ++this.poiDetailRequestId
      try {
        const detail = await this.fetchPlaceDetail(item.id)
        if (requestId !== this.poiDetailRequestId) return
        if (!detail) return
        const merged = this.toMarkerItem({
          ...item._rawItem,
          ...detail,
          longitude: detail.longitude != null ? detail.longitude : item.longitude,
          latitude: detail.latitude != null ? detail.latitude : item.latitude
        })
        if (!merged) return
        if (item.distance) merged.distance = item.distance
        this.selectedLocation = merged
        this.applySelectedFence(merged)
      } catch (error) {
        console.warn('加载点位详情失败，使用列表数据展示', error)
      }
    },
    closePopup() {
      this.selectedLocation = null
      this.navigationPolyline = []
      this.clearSelectedFence()
      this.resetPlaceDetailState()
      this.closeClusterPicker()
      this.closeSuggestPanel()
      this.clearDroppedPin()
      this.markerRenderToken = (this.markerRenderToken || 0) + 1
    },
    resetPlaceDetailState() {
      this.sheetExpandLevel = 'half'
      this.activePanel = 'info'
      this.placeFloors = []
      this.activeFloorId = null
      this.activeFloorPlan = null
      this.indoorPoints = []
      this.selectedIndoorPoint = null
      this.floorPlanLoading = false
      this.indoorLoading = false
      this.floorPlanError = ''
      this.floorRequestId += 1
    },
    handleMapTap(event) {
      if (Date.now() < this.suppressMapTapUntil) return
      const detail = event?.detail || event || {}
      const longitude = Number(detail.longitude)
      const latitude = Number(detail.latitude)
      const clusterGroup = this.findNearestCampusGroup(longitude, latitude, this.clusterHitRadiusMeters())
      if (clusterGroup && clusterGroup.items.length > 1) {
        this.openClusterPicker(clusterGroup.items)
        return
      }
      const hasOpenPopup = !!this.selectedLocation
        || this.searchSuggestions.length > 0
        || this.navigationPolyline.length > 0
        || !!this.tempSearchLocation
        || !!this.droppedPin
        || this.clusterPickerItems.length > 0
      if (hasOpenPopup) {
        this.closePopup()
        this.tempSearchLocation = null
        return
      }
      if (longitude == null || latitude == null || !Number.isFinite(longitude) || !Number.isFinite(latitude)) return
      this.dropPinAt(longitude, latitude)
    },
    async dropPinAt(longitude, latitude) {
      if (!Number.isFinite(longitude) || !Number.isFinite(latitude)) return
      this.clearDroppedPin()
      const requestId = ++this.reverseGeocodeRequestId
      const fallbackName = '地图点位'
      this.droppedPin = {
        id: 'dropped-pin',
        name: fallbackName,
        shortName: '点位',
        icon: '',
        coverImage: '',
        category: 0,
        typeClass: 'search',
        distance: this.formatDistance(longitude, latitude),
        detail: `${longitude.toFixed(6)}, ${latitude.toFixed(6)}`,
        description: '正在解析地址...',
        route: '',
        longitude,
        latitude
      }
      this.selectedLocation = this.droppedPin
      this.reverseGeocodeLoading = true
      try {
        const res = await reverseGeocode(longitude, latitude)
        if (requestId !== this.reverseGeocodeRequestId) return
        const data = res?.data || res || {}
        const address = data.formattedAddress
          || [data.province, data.city, data.district, data.street, data.streetNumber]
            .filter(Boolean)
            .join('')
        if (address) {
          this.droppedPin = {
            ...this.droppedPin,
            name: address,
            shortName: this.getShortName(address),
            detail: data.district || data.street || address,
            description: address
          }
          this.selectedLocation = this.droppedPin
        }
      } catch (error) {
        if (requestId === this.reverseGeocodeRequestId) {
          this.droppedPin = {
            ...this.droppedPin,
            description: '无法解析该点地址，可直接导航'
          }
          this.selectedLocation = this.droppedPin
        }
      } finally {
        if (requestId === this.reverseGeocodeRequestId) {
          this.reverseGeocodeLoading = false
        }
      }
    },
    clearDroppedPin() {
      this.droppedPin = null
      this.reverseGeocodeLoading = false
    },
    resolveTappedCampusGroup(markerId) {
      return this.campusMarkerGroups.find((group) => Number(group.markerId) === Number(markerId)) || null
    },
    clusterHitRadiusMeters() {
      const scale = Number(this.viewScale)
      const lat = Number(this.mapCenter?.latitude) || CAMPUS_FALLBACK_CENTER.latitude
      const zoom = Number.isFinite(scale) ? scale : DEFAULT_MAP_SCALE
      const metersPerPx = (156543.03392 * Math.cos(this.toRadians(lat))) / (2 ** zoom)
      return Math.max(32, metersPerPx * 30)
    },
    findNearestCampusGroup(longitude, latitude, maxMeters) {
      if (!Number.isFinite(longitude) || !Number.isFinite(latitude) || !Number.isFinite(maxMeters)) return null
      let best = null
      let bestDist = Infinity
      this.campusMarkerGroups.forEach((group) => {
        const dist = this.distanceBetween(longitude, latitude, group.longitude, group.latitude)
        if (dist < bestDist) {
          best = group
          bestDist = dist
        }
      })
      return bestDist <= maxMeters ? best : null
    },
    resolveMarkerTapId(event) {
      const detail = event?.detail || event || {}
      return Number(detail.markerId ?? detail.id ?? event?.markerId)
    },
    handleCampusMarkerTap(markerId, tapPoint = null) {
      this.suppressMapTapUntil = Date.now() + 500
      if (markerId === -8888 && this.droppedPin) {
        this.selectLocation(this.droppedPin)
        return
      }
      if (markerId === -9999 && this.tempSearchLocation) {
        this.selectLocation(this.tempSearchLocation)
        return
      }
      let group = Number.isFinite(markerId) ? this.resolveTappedCampusGroup(markerId) : null
      if (!group && tapPoint) {
        group = this.findNearestCampusGroup(tapPoint.longitude, tapPoint.latitude, this.clusterHitRadiusMeters())
      }
      if (group && group.items.length > 1) {
        this.openClusterPicker(group.items)
        return
      }
      const marker = (group && group.items[0])
        || this.visibleLocations.find((item) => Number(item.id) === markerId)
      if (marker) this.selectLocation(marker)
    },
    onMarkerTap(event) {
      const detail = event?.detail || event || {}
      this.handleCampusMarkerTap(this.resolveMarkerTapId(event), {
        longitude: Number(detail.longitude),
        latitude: Number(detail.latitude)
      })
    },
    onCalloutTap(event) {
      const detail = event?.detail || event || {}
      this.handleCampusMarkerTap(this.resolveMarkerTapId(event), {
        longitude: Number(detail.longitude),
        latitude: Number(detail.latitude)
      })
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
      if (!item) return ''
      if (item.placeTypeName) return item.placeTypeName
      if (item.facilityType == null) return ''
      return resolveFacilityType(item.facilityType).secondaryLabel || ''
    },
    resolveSecondaryEmoji() {
      return ''
    },
    resolveServiceHint(item) {
      if (!item) return ''
      if (item.detail) return item.detail
      if (item.facilityType == null) return ''
      return resolveFacilityType(item.facilityType).serviceHint || ''
    },
    onPoiMapAction() {
      if (!this.selectedLocation) return
      this.startNavigation(this.selectedLocation)
    },
    onPoiCategoryAction() {
      if (!this.selectedLocation) return
      this.sheetExpandLevel = 'full'
      this.activePanel = 'info'
    },
    toggleSheetExpand() {
      this.sheetExpandLevel = this.sheetExpandLevel === 'full' ? 'half' : 'full'
    },
    onPanelChange(panel) {
      this.activePanel = panel === 'floor' ? 'floor' : 'info'
      if (this.activePanel === 'floor') {
        this.sheetExpandLevel = 'full'
        this.openFloorPanel()
      }
    },
    formatPlaceStatus(status) {
      if (status === 'ENABLED') return '开放'
      if (status === 'DISABLED') return '停用'
      return status || '未知'
    },
    formatAvgPrice(value, placeType) {
      if (value == null || value === '') return ''
      if (!['CANTEEN', 'CANTEEN_STALL', 'DINING_AREA'].includes(placeType)) return ''
      const amount = Number(value)
      if (!Number.isFinite(amount)) return ''
      return `约 ¥${amount}`
    },
    formatDateTime(value) {
      if (!value) return ''
      const text = `${value}`
      const match = text.match(/(\d{4}-\d{2}-\d{2})[T\s](\d{2}:\d{2})/)
      if (match) return `${match[1]} ${match[2]}`
      return text.slice(0, 16)
    },
    formatFenceStatus(fence) {
      const type = `${fence?.geometryType || ''}`.toUpperCase()
      if (type === 'POLYGON') return 'Polygon'
      if (type === 'LINESTRING') return 'LineString'
      return '无'
    },
    normalizeFloorItem(floor) {
      return {
        id: floor.id,
        name: floor.name,
        label: formatFloorTabLabel(floor.name, floor.sortOrder),
        sortOrder: floor.sortOrder || 0,
        placeType: floor.placeType,
        floorPlan: floor.floorPlan || null,
        children: floor.children || []
      }
    },
    fetchPlaceDetail(placeId) {
      const id = Number(placeId)
      if (!Number.isFinite(id)) return Promise.resolve(null)
      if (placeDetailCache[id]) return Promise.resolve(placeDetailCache[id])
      if (placeDetailInflight[id]) return placeDetailInflight[id]
      // 详情默认不带楼层树；平面图/室内点位在点开楼层面板后按楼层拉取
      const task = getMarkerDetail(id, { includeChildren: false })
        .then((res) => {
          const detail = res?.data || null
          if (detail) placeDetailCache[id] = detail
          return detail
        })
        .finally(() => {
          delete placeDetailInflight[id]
        })
      placeDetailInflight[id] = task
      return task
    },
    applyFloorsFromPlace(place) {
      const floors = (place?.children || place?._rawItem?.children || [])
        .filter((item) => item.placeType === 'FLOOR')
      this.placeFloors = floors
        .map((item) => this.normalizeFloorItem(item))
        .sort((left, right) => left.sortOrder - right.sortOrder || left.id - right.id)
    },
    async ensurePlaceFloors(place, requestId = this.poiDetailRequestId) {
      if (!place) return
      const cacheKey = Number(place.id)
      if (Number.isFinite(cacheKey) && placeChildrenCache[cacheKey]) {
        this.placeFloors = placeChildrenCache[cacheKey]
        return
      }
      try {
        const childRes = await getPlaceChildren(place.id, { placeType: 'FLOOR', showError: false })
        if (requestId !== this.poiDetailRequestId) return
        const floors = (childRes?.data || [])
          .map((item) => this.normalizeFloorItem({
            ...item,
            floorPlan: null,
            children: []
          }))
          .sort((left, right) => left.sortOrder - right.sortOrder || left.id - right.id)
        if (Number.isFinite(cacheKey)) placeChildrenCache[cacheKey] = floors
        this.placeFloors = floors
      } catch (error) {
        this.placeFloors = []
      }
    },
    async openFloorPanel() {
      const place = this.selectedLocation
      if (!place) return
      const requestId = this.poiDetailRequestId
      await this.ensurePlaceFloors(place, requestId)
      if (requestId !== this.poiDetailRequestId) return
      const floorId = this.activeFloorId || this.placeFloors[0]?.id
      if (floorId) await this.loadFloorContent(floorId, requestId)
    },
    async onFloorChange(floorId) {
      await this.loadFloorContent(floorId)
    },
    mapIndoorPoints(remote, floor) {
      const childMap = Object.fromEntries((floor.children || []).map((item) => [item.id, item]))
      return (Array.isArray(remote) ? remote : []).map((point) => {
        const child = childMap[point.placeId] || {}
        return {
          id: point.id || point.placeId,
          placeId: point.placeId,
          name: point.name || child.name || '室内点位',
          placeType: point.placeType || child.placeType || '',
          placeTypeName: getPlaceTypeLabel(point.placeType || child.placeType, ''),
          description: point.description || child.description || '',
          status: point.status || child.status || '',
          stallStatus: point.stallStatus ?? child.stallStatus,
          locationDesc: point.locationDesc || child.locationDesc || child.location || '',
          businessHours: point.businessHours || child.businessHours || '',
          avgPrice: point.avgPrice ?? child.avgPrice,
          imageUrl: point.imageUrl || child.imageUrl || '',
          floorName: point.floorName || floor.name || '',
          xRatio: Number(point.xRatio),
          yRatio: Number(point.yRatio)
        }
      }).filter((point) => Number.isFinite(point.xRatio) && Number.isFinite(point.yRatio))
    },
    applyFloorContent(payload) {
      this.activeFloorPlan = payload.plan || null
      this.indoorPoints = payload.points || []
      this.floorPlanError = payload.error || ''
      this.floorPlanLoading = false
      this.indoorLoading = false
    },
    mapIndoorPointsFromChildren(floor) {
      return this.mapIndoorPoints((floor.children || []).map((child) => {
        const pos = child.indoorPosition || {}
        return {
          id: pos.id || child.id,
          placeId: child.id,
          name: child.name,
          placeType: child.placeType,
          description: child.description,
          status: child.status,
          stallStatus: child.stallStatus,
          locationDesc: child.locationDesc,
          businessHours: child.businessHours,
          avgPrice: child.avgPrice,
          imageUrl: child.imageUrl,
          floorName: floor.name,
          xRatio: pos.xRatio,
          yRatio: pos.yRatio
        }
      }), floor)
    },
    async loadFloorContent(floorId, requestId = this.poiDetailRequestId) {
      const floor = this.placeFloors.find((item) => item.id === floorId)
      if (!floor) return
      this.activeFloorId = floorId
      this.selectedIndoorPoint = null
      const cached = floorContentCache[floorId]
      if (cached) {
        this.applyFloorContent(cached)
        return
      }
      if (floorContentInflight[floorId]) {
        await floorContentInflight[floorId]
        if (requestId !== this.poiDetailRequestId) return
        if (floorContentCache[floorId]) this.applyFloorContent(floorContentCache[floorId])
        return
      }
      const floorReqId = ++this.floorRequestId
      this.floorPlanError = ''
      this.activeFloorPlan = null
      this.indoorPoints = []
      this.floorPlanLoading = true
      this.indoorLoading = true
      const task = (async () => {
        // 每个楼层单独拉平面图与室内点位，不复用详情里的整树数据
        const planRes = await getFloorPlan(floorId)
        const plan = planRes?.data || null
        floor.floorPlan = plan
        if (!plan?.id) {
          return { plan, points: [], error: '' }
        }
        const posRes = await getIndoorPositions(plan.id)
        return {
          plan,
          points: this.mapIndoorPoints(posRes?.data, floor),
          error: ''
        }
      })()
        .then((payload) => {
          floorContentCache[floorId] = payload
          return payload
        })
        .catch(() => {
          const payload = { plan: null, points: [], error: '平面图或室内点位加载失败' }
          return payload
        })
        .finally(() => {
          delete floorContentInflight[floorId]
        })
      floorContentInflight[floorId] = task
      const payload = await task
      if (requestId !== this.poiDetailRequestId || floorReqId !== this.floorRequestId) return
      this.applyFloorContent(payload)
    },
    onIndoorSelect(point) {
      this.selectedIndoorPoint = point
      this.sheetExpandLevel = 'full'
    },
    onFloorPlanImageError() {
      this.floorPlanError = '平面图加载失败'
    },
    onPoiSecondaryAction() {
      this.onPoiCategoryAction()
    },
    onPoiPrimaryAction() {
      this.onPoiMapAction()
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
        this.recordNavigationIfNeeded(item, from)
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
    },
    recordNavigationIfNeeded(item, from) {
      const markerId = Number(item.id)
      if (!Number.isFinite(markerId) || markerId <= 0) return
      startNavigationRecord({
        fromLongitude: from.longitude,
        fromLatitude: from.latitude,
        toMarkerId: markerId
      }).catch(() => {})
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

.search-suggest-panel {
  position: absolute;
  left: 0;
  right: 0;
  top: calc(100% + 8rpx);
  z-index: 60;
  max-height: 560rpx;
  overflow-y: auto;
  border-radius: 24rpx;
  background: rgba(255, 255, 255, 0.98);
  border: 1rpx solid rgba(102, 114, 125, 0.08);
  box-shadow: 0 18rpx 44rpx rgba(42, 72, 103, 0.14);
  backdrop-filter: blur(16rpx);
}

.search-suggest-item {
  padding: 20rpx 28rpx;
  border-bottom: 1rpx solid rgba(102, 114, 125, 0.06);
  display: flex;
  flex-direction: column;
  gap: 6rpx;
}

.search-suggest-item:last-child {
  border-bottom: none;
}

.search-suggest-item.active {
  background: rgba(77, 134, 248, 0.08);
}

.search-suggest-name {
  font-size: 28rpx;
  color: #1d1d1f;
  line-height: 1.4;
}

.search-suggest-desc {
  font-size: 22rpx;
  color: rgba(102, 114, 125, 0.9);
  line-height: 1.4;
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

.category-bar {
  position: absolute;
  left: 24rpx;
  right: 24rpx;
  bottom: 24rpx;
  z-index: 26;
  display: flex;
  flex-direction: row;
  align-items: stretch;
  justify-content: space-between;
  gap: 6rpx;
  padding: 18rpx 12rpx 16rpx;
  border-radius: 28rpx;
  background: rgba(255, 255, 255, 0.98);
  border: 1rpx solid #eceff3;
}

.category-bar__item {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  gap: 10rpx;
  padding: 4rpx 2rpx;
}

.category-bar__item.active .category-bar__label {
  color: #1d1d1f;
  font-weight: 700;
}

.category-bar__icon {
  width: 80rpx;
  height: 80rpx;
  border-radius: 26rpx;
  display: flex;
  align-items: center;
  justify-content: center;
  transition: transform 0.15s ease, box-shadow 0.15s ease;
}

.category-bar__item:active .category-bar__icon {
  transform: scale(0.95);
}

.category-bar__item--teaching .category-bar__icon {
  background: #eaf1ff;
}

.category-bar__item--canteen .category-bar__icon {
  background: #ffe8e4;
}

.category-bar__item--infra .category-bar__icon {
  background: #eef1f5;
}

.category-bar__item--sport .category-bar__icon {
  background: #e5f7ea;
}

.category-bar__item.active .category-bar__icon {
  box-shadow: inset 0 0 0 2rpx rgba(47, 107, 255, 0.24);
}

.category-bar__glyph {
  width: 44rpx;
  height: 44rpx;
  background-repeat: no-repeat;
  background-position: center;
  background-size: 40rpx 40rpx;
}

.category-bar__item--teaching .category-bar__glyph {
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 40 40' fill='none'%3E%3Cpath d='M8 18L20 9l12 9v14H8V18z' fill='%232F6BFF'/%3E%3Cpath d='M6 18.5L20 7.5l14 11' stroke='%235B8CFF' stroke-width='2.4' stroke-linecap='round' stroke-linejoin='round'/%3E%3Crect x='16.5' y='23' width='7' height='9' rx='1.2' fill='%23FFFFFF'/%3E%3Crect x='11' y='21' width='4.5' height='4.5' rx='0.8' fill='%23DCE8FF'/%3E%3Crect x='24.5' y='21' width='4.5' height='4.5' rx='0.8' fill='%23DCE8FF'/%3E%3C/svg%3E");
}

.category-bar__item--canteen .category-bar__glyph {
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 40 40' fill='none'%3E%3Cpath d='M8 20c0 8 5.4 13 12 13s12-5 12-13H8z' fill='%23E86060'/%3E%3Cpath d='M7 18.5h26' stroke='%23F08A7A' stroke-width='3' stroke-linecap='round'/%3E%3Cpath d='M15 8.5l1.2 8' stroke='%23C94A4A' stroke-width='2.2' stroke-linecap='round'/%3E%3Cpath d='M20 7.5v9' stroke='%23C94A4A' stroke-width='2.2' stroke-linecap='round'/%3E%3Cpath d='M25 8.5l-1.2 8' stroke='%23C94A4A' stroke-width='2.2' stroke-linecap='round'/%3E%3C/svg%3E");
}

.category-bar__item--infra .category-bar__glyph {
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 40 40' fill='none'%3E%3Crect x='5' y='14' width='11' height='19' rx='2' fill='%236B7280'/%3E%3Crect x='14' y='8' width='13' height='25' rx='2' fill='%238B93A0'/%3E%3Crect x='25' y='17' width='10' height='16' rx='2' fill='%23A0A8B4'/%3E%3Crect x='7.2' y='17.5' width='2.8' height='2.8' rx='0.5' fill='%23E5E7EB'/%3E%3Crect x='7.2' y='23' width='2.8' height='2.8' rx='0.5' fill='%23E5E7EB'/%3E%3Crect x='17.2' y='12' width='2.8' height='2.8' rx='0.5' fill='%23F3F4F6'/%3E%3Crect x='21.5' y='12' width='2.8' height='2.8' rx='0.5' fill='%23F3F4F6'/%3E%3Crect x='17.2' y='17.5' width='2.8' height='2.8' rx='0.5' fill='%23F3F4F6'/%3E%3Crect x='21.5' y='17.5' width='2.8' height='2.8' rx='0.5' fill='%23F3F4F6'/%3E%3Crect x='27.2' y='20.5' width='2.6' height='2.6' rx='0.5' fill='%23E5E7EB'/%3E%3Crect x='31' y='20.5' width='2.6' height='2.6' rx='0.5' fill='%23E5E7EB'/%3E%3C/svg%3E");
}

.category-bar__item--sport .category-bar__glyph {
  background-image: url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' viewBox='0 0 40 40' fill='none'%3E%3Ccircle cx='20' cy='20' r='13' stroke='%2338A85A' stroke-width='2.8'/%3E%3Cpath d='M20 7c3.4 3.2 5.4 7.4 5.4 13S23.4 29.8 20 33c-3.4-3.2-5.4-7.4-5.4-13S16.6 10.2 20 7z' stroke='%2363C27D' stroke-width='2'/%3E%3Cpath d='M8.2 15.5h23.6M8.2 24.5h23.6' stroke='%2338A85A' stroke-width='2' stroke-linecap='round'/%3E%3C/svg%3E");
}

.category-bar__label {
  font-size: 22rpx;
  line-height: 1.2;
  color: #526b7e;
  text-align: center;
  white-space: nowrap;
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

.cluster-sheet-map {
  max-height: 560rpx;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.62), rgba(244, 248, 252, 0.46));
  border: 1rpx solid rgba(255, 255, 255, 0.7);
  box-shadow: 0 18rpx 48rpx rgba(42, 72, 103, 0.14), inset 0 1rpx 0 rgba(255, 255, 255, 0.86);
  backdrop-filter: blur(28rpx) saturate(180%);
  -webkit-backdrop-filter: blur(28rpx) saturate(180%);
}

.cluster-list {
  max-height: 420rpx;
}

.cluster-item {
  display: flex;
  align-items: center;
  gap: 16rpx;
  padding: 18rpx 6rpx;
  border-bottom: 1rpx solid rgba(102, 114, 125, 0.06);
}

.cluster-item:last-child {
  border-bottom: none;
}

.cluster-item__pin {
  width: 18rpx;
  height: 18rpx;
  border: 4rpx solid #fff;
  border-radius: 50% 50% 50% 0;
  background: #4d6f8f;
  transform: rotate(-45deg);
  box-sizing: border-box;
  flex-shrink: 0;
}

.cluster-item__pin--canteen {
  background: #c9864d;
}

.cluster-item__pin--sport {
  background: #4e8a69;
}

.cluster-item__body {
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4rpx;
}

.cluster-item__name {
  font-size: 28rpx;
  font-weight: 800;
  color: #202833;
}

.cluster-item__desc {
  font-size: 22rpx;
  color: #7a8591;
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

.nearby-more-arrow--down {
  transform: rotate(135deg);
  margin-top: -4rpx;
}

.nearby-sheet__loading {
  padding: 30rpx 10rpx;
  text-align: center;
  color: #7a8591;
  font-size: 24rpx;
  font-weight: 600;
}

.history-sheet-map {
  max-height: 560rpx;
  display: flex;
  flex-direction: column;
}

.history-list {
  max-height: 460rpx;
  flex: 1;
}

.history-item {
  display: flex;
  align-items: center;
  gap: 18rpx;
  padding: 20rpx 6rpx;
  border-bottom: 1rpx solid rgba(102, 114, 125, 0.06);
}

.history-item:last-child {
  border-bottom: none;
}

.history-item__icon {
  width: 44rpx;
  height: 44rpx;
  border-radius: 14rpx;
  background: #eef5ff;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.history-item__pin {
  width: 16rpx;
  height: 16rpx;
  border: 3rpx solid #5aa7f2;
  border-radius: 50% 50% 50% 0;
  transform: rotate(-45deg);
  box-sizing: border-box;
}

.history-item__body {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 6rpx;
}

.history-item__name {
  color: #202833;
  font-size: 26rpx;
  font-weight: 800;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.history-item__meta {
  display: flex;
  align-items: center;
  gap: 8rpx;
  color: #7a8591;
  font-size: 21rpx;
  font-weight: 600;
}

.history-item__dot {
  opacity: 0.6;
}

.history-item__status {
  padding: 6rpx 14rpx;
  border-radius: 999rpx;
  font-size: 20rpx;
  font-weight: 800;
  flex-shrink: 0;
}

.history-item__status--进行中 {
  background: rgba(77, 134, 248, 0.12);
  color: #2f72d6;
}

.history-item__status--已到达 {
  background: rgba(35, 183, 170, 0.12);
  color: #1c9b8f;
}

.history-item__status--已取消 {
  background: rgba(120, 130, 140, 0.12);
  color: #6a7683;
}

.history-item__status--未知 {
  background: rgba(120, 130, 140, 0.12);
  color: #6a7683;
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
