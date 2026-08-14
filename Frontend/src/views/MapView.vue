<script setup>
/* ═══════════════════════════════════════════════════
   校园导航页 — 高德地图 JS API 2.0 + 纯前端扩展功能
   功能：高德地图/搜索/快捷按钮/聊天助手/暗色模式/
         打卡/留言板/随机漫步/失物招领
   ═══════════════════════════════════════════════════ */
import { ref, reactive, computed, onMounted, onUnmounted, nextTick } from 'vue'
import AppTabBar from '../components/AppTabBar.vue'
import {
  getFloorPlan,
  getFloorPlanPositions,
  getMapPlaceDetail,
  getMapPlaceList,
} from '../api/map'
import markerCanteen from '../assets/map/marker-canteen.svg'
import markerDormitory from '../assets/map/marker-dormitory.svg'
import markerOther from '../assets/map/marker-other.svg'
import markerSports from '../assets/map/marker-sports.svg'
import markerTeaching from '../assets/map/marker-teaching.svg'
import firstCanteenFloorPlan from '../assets/map/first-canteen-floor.png'

/* ── 高德地图配置 ── */
const AMAP_KEY = 'e1790a70dfc91f2d3daf1895c0e9f87a'
const AMAP_SECURITY = 'f3eda2c1d4c4c76bdb907d570b69d8c'
const MAP_CENTER = [114.897014, 40.755502]
const MAP_ZOOM = 17

const mapPlaces = ref([])
const placeLoading = ref(false)

const demoDormitories = [
  { id: 'demo-dorm-1', name: '紫藤宿舍楼', longitude: 114.89495, latitude: 40.75662, sceneType: 'DORMITORY', placeType: 'DORMITORY_BUILDING', description: '第一校区学生宿舍楼', locationDesc: '第一校区生活区', status: 'ENABLED', mapVisible: true },
  { id: 'demo-dorm-2', name: '青松宿舍楼', longitude: 114.89608, latitude: 40.75474, sceneType: 'DORMITORY', placeType: 'DORMITORY_BUILDING', description: '第一校区学生宿舍楼', locationDesc: '第一校区生活区', status: 'ENABLED', mapVisible: true },
  { id: 'demo-dorm-3', name: '明月宿舍楼', longitude: 114.89842, latitude: 40.75612, sceneType: 'DORMITORY', placeType: 'DORMITORY_BUILDING', description: '第二校区学生宿舍楼', locationDesc: '第二校区生活区', status: 'ENABLED', mapVisible: true },
  { id: 'demo-dorm-4', name: '海棠宿舍楼', longitude: 114.89904, latitude: 40.75398, sceneType: 'DORMITORY', placeType: 'DORMITORY_BUILDING', description: '第三校区学生宿舍楼', locationDesc: '第三校区生活区', status: 'ENABLED', mapVisible: true },
]

const SCENE_META = {
  CANTEEN: { label: '食堂', color: '#f97316', icon: markerCanteen },
  SPORTS: { label: '运动场', color: '#10b981', icon: markerSports },
  TEACHING: { label: '教学楼', color: '#3b82f6', icon: markerTeaching },
  DORMITORY: { label: '宿舍', color: '#8b5cf6', icon: markerDormitory },
}
const DEFAULT_SCENE_META = { label: '其他点位', color: '#64748b', icon: markerOther }
const sceneMeta = (sceneType) => SCENE_META[sceneType] || DEFAULT_SCENE_META

const toCoordinate = (value) => {
  if (value === null || value === undefined || value === '') return null
  const coordinate = Number(value)
  return Number.isFinite(coordinate) ? coordinate : null
}

const parseFence = (fence) => {
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
      .map(point => [toCoordinate(point?.[0]), toCoordinate(point?.[1])])
      .filter(point => point[0] !== null && point[1] !== null)
    if (geometryType === 'POLYGON' && path.length >= 3) return { geometryType, path }
    if (geometryType === 'LINESTRING' && path.length >= 2) return { geometryType, path }
    return null
  } catch {
    return null
  }
}

const toMapPoi = (place) => ({
  id: place.id,
  name: place.name || '未命名点位',
  lng: toCoordinate(place.longitude),
  lat: toCoordinate(place.latitude),
  desc: place.description || place.locationDesc || '暂无详细介绍',
  tag: place.placeType || sceneMeta(place.sceneType).label,
  sceneType: place.sceneType,
  placeType: place.placeType,
  locationDesc: place.locationDesc || '',
  status: place.status,
  images: Array.isArray(place.images) ? place.images : [],
  fence: parseFence(place.fence),
})

async function loadMapPlaces() {
  placeLoading.value = true
  try {
    const response = await getMapPlaceList({ status: 'ENABLED' })
    const places = (Array.isArray(response?.data) ? response.data : [])
      .filter(place => place.mapVisible !== false)
    const detailResponses = await Promise.all(places.map(async place => {
      try {
        const detail = await getMapPlaceDetail(place.id)
        return detail?.data || place
      } catch {
        return place
      }
    }))
    const resolvedPlaces = detailResponses
      .map(toMapPoi)
      .filter(place => Number.isFinite(place.lng) && Number.isFinite(place.lat))
    const existingDormitoryCount = resolvedPlaces.filter(place => place.sceneType === 'DORMITORY').length
    const supplementalDormitories = demoDormitories
      .slice(0, Math.max(0, 4 - existingDormitoryCount))
      .map(toMapPoi)
    mapPlaces.value = [...resolvedPlaces, ...supplementalDormitories]
  } catch (error) {
    mapError.value = error.message || '校园点位加载失败'
    mapPlaces.value = []
  } finally {
    placeLoading.value = false
  }
}

const activePoi = ref(null)          /* 当前选中点位 */
const activePoiScreen = ref({ x: 0, y: 0, panelX: 18, panelY: 18 })
const mapViewport = ref({ width: 0, height: 0 })
const selectedNotice = ref('')       /* 搜索选中提示 */
const categoryExpanded = ref(false)
const activeCategory = ref('all')
const campusMenuOpen = ref(false)
const activeCampus = ref('')
const otherMenuOpen = ref(false)
const activeOtherPlace = ref('')
const floorPreviewOpen = ref(false)
const floorPreviewItems = ref([])
let floorPreviewTimer = null
const categoryItems = [
  { key: 'campus', label: '校区', icon: '▦', sceneType: null },
  { key: 'canteen', label: '食堂', icon: '餐', sceneType: 'CANTEEN' },
  { key: 'dormitory', label: '宿舍楼', icon: '宿', sceneType: 'DORMITORY' },
  { key: 'teaching', label: '教学楼', icon: '学', sceneType: 'TEACHING' },
  { key: 'sports', label: '运动场', icon: '场', sceneType: 'SPORTS' },
  { key: 'other', label: '其他', icon: '•••', sceneType: 'OTHER' },
]
const campusItems = [
  { key: 'campus-1', label: '第一校区', center: [114.89565, 40.75585] },
  { key: 'campus-2', label: '第二校区', center: [114.89755, 40.75525] },
  { key: 'campus-3', label: '第三校区', center: [114.89885, 40.75435] },
]
const otherItems = [
  { key: 'landscape', label: '景观区', placeType: 'LANDSCAPE' },
  { key: 'admin', label: '行政楼', placeType: 'ADMIN_BUILDING' },
  { key: 'hospital', label: '校医院', placeType: 'HOSPITAL' },
]

const displayedFloorPreview = computed(() => {
  if (activePoi.value?.name === '第一食堂') {
    return [
      { id: 'first-1', code: '1F', name: '第一层', description: '综合餐饮区' },
      { id: 'first-2', code: '2F', name: '第二层', description: '风味餐饮区' },
      { id: 'first-3', code: '3F', name: '第三层', description: '休闲用餐区' },
      { id: 'first-4', code: '4F', name: '第四层', description: '特色餐饮区' },
    ]
  }
  return floorPreviewItems.value.length
  ? floorPreviewItems.value.map((floor, index) => ({
      id: floor.id,
      code: `${index + 1}F`,
      name: floor.name || `第${index + 1}层`,
      description: ['综合餐饮区', '风味餐饮区', '休闲用餐区'][index] || '餐饮服务区',
    }))
  : [
      { id: 'fallback-1', code: '1F', name: '第一层', description: '综合餐饮区' },
      { id: 'fallback-2', code: '2F', name: '第二层', description: '风味餐饮区' },
      { id: 'fallback-3', code: '3F', name: '第三层', description: '休闲用餐区' },
    ]
})

function updateActivePoiScreen() {
  if (!mapInstance || !activePoi.value) return
  const pixel = mapInstance.lngLatToContainer([activePoi.value.lng, activePoi.value.lat])
  if (!pixel) return
  const size = mapInstance.getSize()
  const width = size?.getWidth?.() || size?.width || document.getElementById('container')?.clientWidth || window.innerWidth
  const height = size?.getHeight?.() || size?.height || document.getElementById('container')?.clientHeight || window.innerHeight
  const x = pixel.getX()
  const y = pixel.getY()
  const cardWidth = Math.min(360, width - 36)
  const cardHeight = Math.min(520, height - 36)
  const gap = 48
  const margin = 18
  let panelX
  let panelY

  if (width - x - gap >= cardWidth + margin) {
    panelX = x + gap
    panelY = Math.max(margin, Math.min(y - cardHeight / 2, height - cardHeight - margin))
  } else if (x - gap >= cardWidth + margin) {
    panelX = x - gap - cardWidth
    panelY = Math.max(margin, Math.min(y - cardHeight / 2, height - cardHeight - margin))
  } else if (height - y - gap >= cardHeight + margin) {
    panelX = Math.max(margin, Math.min(x - cardWidth / 2, width - cardWidth - margin))
    panelY = y + gap
  } else if (y - gap >= cardHeight + margin) {
    panelX = Math.max(margin, Math.min(x - cardWidth / 2, width - cardWidth - margin))
    panelY = y - gap - cardHeight
  } else {
    panelX = Math.max(margin, Math.min(x - cardWidth / 2, width - cardWidth - margin))
    panelY = Math.max(margin, Math.min(y - cardHeight / 2, height - cardHeight - margin))
  }

  mapViewport.value = { width, height }
  activePoiScreen.value = { x, y, panelX, panelY }
}

const floorPreviewStyle = computed(() => {
  const panelWidth = Math.min(360, mapViewport.value.width - 36)
  const floorWidth = Math.min(316, mapViewport.value.width - 36)
  const floorHeight = 294
  const gap = 18
  const margin = 18
  const right = activePoiScreen.value.panelX + panelWidth + gap
  const left = activePoiScreen.value.panelX - floorWidth - gap
  const x = right + floorWidth <= mapViewport.value.width - margin ? right : Math.max(margin, left)
  const y = Math.max(margin, Math.min(
    activePoiScreen.value.panelY + 150,
    mapViewport.value.height - floorHeight - margin,
  ))
  return { left: `${x}px`, top: `${y}px` }
})

function closeActivePoi() {
  activePoi.value = null
  floorPreviewOpen.value = false
}

async function showFloorPreview(poi) {
  if (!poi || poi.sceneType !== 'CANTEEN') return
  if (floorPreviewTimer) clearTimeout(floorPreviewTimer)
  floorPreviewOpen.value = true
  floorPreviewItems.value = []
  try {
    const response = await getMapPlaceList({ sceneType: 'CANTEEN', parentId: poi.id, status: 'ENABLED' })
    floorPreviewItems.value = (Array.isArray(response?.data) ? response.data : [])
      .filter(place => place.placeType === 'FLOOR')
      .sort((left, right) => (left.sortOrder || 0) - (right.sortOrder || 0))
  } catch {
    floorPreviewItems.value = []
  }
}

function keepFloorPreview() {
  if (floorPreviewTimer) clearTimeout(floorPreviewTimer)
  floorPreviewOpen.value = true
}

function hideFloorPreview() {
  if (floorPreviewTimer) clearTimeout(floorPreviewTimer)
  floorPreviewTimer = setTimeout(() => { floorPreviewOpen.value = false }, 500)
}

function selectCategory(item) {
  activeCategory.value = item.key
  campusMenuOpen.value = item.key === 'campus'
  otherMenuOpen.value = item.key === 'other'
  if (item.key !== 'campus') activeCampus.value = ''
  if (item.key !== 'other') activeOtherPlace.value = ''
  const visiblePlaces = item.sceneType === null
    ? mapPlaces.value
    : mapPlaces.value.filter(place => item.sceneType === 'OTHER'
      ? !SCENE_META[place.sceneType]
      : place.sceneType === item.sceneType)
  const visibleIds = new Set(visiblePlaces.map(place => String(place.id)))
  mapPlaces.value.forEach(place => {
    const visible = visibleIds.has(String(place.id))
    markerMap[place.id]?.[visible ? 'show' : 'hide']()
    fenceMap[place.id]?.[visible ? 'show' : 'hide']()
  })
  if (activePoi.value && !visibleIds.has(String(activePoi.value.id))) closeActivePoi()
  const overlays = visiblePlaces.flatMap(place => [markerMap[place.id], fenceMap[place.id]]).filter(Boolean)
  if (mapInstance && overlays.length) mapInstance.setFitView(overlays, false, [90, 90, 90, 190], 18)
}

function selectCampus(campus) {
  activeCampus.value = campus.key
  activeCategory.value = 'campus'
  if (mapInstance) mapInstance.setZoomAndCenter(17, campus.center)
}

function selectOtherPlace(place) {
  activeOtherPlace.value = place.key
  activeCategory.value = 'other'
  otherMenuOpen.value = true
  const visiblePlaces = mapPlaces.value.filter(item => item.placeType === place.placeType)
  const visibleIds = new Set(visiblePlaces.map(item => String(item.id)))
  mapPlaces.value.forEach(item => {
    const visible = visibleIds.has(String(item.id))
    markerMap[item.id]?.[visible ? 'show' : 'hide']()
    fenceMap[item.id]?.[visible ? 'show' : 'hide']()
  })
  if (activePoi.value && !visibleIds.has(String(activePoi.value.id))) closeActivePoi()
  const overlays = visiblePlaces.flatMap(item => [markerMap[item.id], fenceMap[item.id]]).filter(Boolean)
  if (mapInstance && overlays.length) mapInstance.setFitView(overlays, false, [90, 90, 90, 190], 18)
}

function selectAllCategories() {
  categoryExpanded.value = !categoryExpanded.value
  selectCategory(categoryItems[0])
  activeCategory.value = 'all'
  campusMenuOpen.value = false
  activeCampus.value = ''
  otherMenuOpen.value = false
  activeOtherPlace.value = ''
}

/* ═══════════════════════════════════════
   ① 🌓 日间/夜间模式
   ═══════════════════════════════════════ */
const isDark = ref(localStorage.getItem('campus-dark') === '1')
function toggleDark() {
  isDark.value = !isDark.value
  localStorage.setItem('campus-dark', isDark.value ? '1' : '0')
  applyTheme()
}
function applyTheme() {
  document.documentElement.classList.toggle('dark', isDark.value)
}

/* ═══════════════════════════════════════
   ② 搜索
   ═══════════════════════════════════════ */
const searchQuery = ref('')
const searchFocused = ref(false)
const filteredPois = computed(() => {
  const q = searchQuery.value.trim().toLowerCase()
  if (!q) return []
  return mapPlaces.value.filter(p => p.name.includes(q) || p.desc.toLowerCase().includes(q))
})
function selectSearchResult(poi) {
  if (poi.sceneType !== 'CANTEEN') return
  searchQuery.value = poi.name
  searchFocused.value = false
  selectedNotice.value = `已定位到「${poi.name}」— ${poi.desc}`
  flyToPoi(poi) /* 地图自动移动到目标地点 */
  setTimeout(() => { selectedNotice.value = '' }, 4000)
}
function clearSearch() { searchQuery.value = '' }

/* ═══════════════════════════════════════
   ⑩ 高德地图加载与初始化
   ═══════════════════════════════════════ */
const mapReady = ref(false)
const mapError = ref('')
let mapInstance = null        /* AMap.Map 实例 */
const markerMap = {}          /* poi.id → AMap.Marker 映射 */
const fenceMap = {}           /* poi.id → 围栏覆盖物映射 */
const mapOverlays = []
let infoWindow = null         /* 全局信息窗 */

const indoorOpen = ref(false)
const indoorLoading = ref(false)
const indoorCanteen = ref(null)
const indoorFloors = ref([])
const indoorFloorId = ref(null)
const indoorFloorPlan = ref(null)
const indoorStalls = ref([])
const indoorPositions = ref([])
const indoorZoom = ref(1.12)
const indoorSearch = ref('')
const indoorCategory = ref('all')
const indoorView = ref('map')
const indoorDragging = ref(false)
let indoorDragStartX = 0
let indoorDragStartY = 0
let indoorDragScrollLeft = 0
let indoorDragScrollTop = 0
let indoorDragViewport = null

const indoorCategoryLabels = {
  noodle: '面食类', soup: '汤羹类', rice: '炒饭盖饭', local: '地方小吃', drink: '饮品甜点',
}

const demoStalls = [
  { id: 's1', name: '兰州拉面', category: 'noodle', x: 25, y: 28, color: '#ff7a35' },
  { id: 's2', name: '一碗面', category: 'noodle', x: 34, y: 33, color: '#f97316' },
  { id: 's3', name: '瓦罐汤', category: 'soup', x: 43, y: 27, color: '#eab308' },
  { id: 's4', name: '牛肉炒饭', category: 'rice', x: 55, y: 35, color: '#e11d48' },
  { id: 's5', name: '重庆小面', category: 'noodle', x: 66, y: 28, color: '#f59e0b' },
  { id: 's6', name: '奶茶窗口', category: 'drink', x: 73, y: 42, color: '#0ea5e9' },
  { id: 's7', name: '石锅拌饭', category: 'rice', x: 28, y: 48, color: '#ef4444' },
  { id: 's8', name: '麻辣烫', category: 'local', x: 39, y: 55, color: '#8b5cf6' },
  { id: 's9', name: '黄焖鸡米饭', category: 'rice', x: 52, y: 47, color: '#f59e0b' },
  { id: 's10', name: '水饺馄饨', category: 'noodle', x: 66, y: 56, color: '#06b6d4' },
  { id: 's11', name: '煎饼果子', category: 'local', x: 24, y: 66, color: '#14b8a6' },
  { id: 's12', name: '冒菜香锅', category: 'local', x: 34, y: 72, color: '#dc2626' },
  { id: 's13', name: '小碗菜', category: 'rice', x: 48, y: 64, color: '#22c55e' },
  { id: 's14', name: '烘焙甜点', category: 'drink', x: 60, y: 73, color: '#ec4899' },
  { id: 's15', name: '鲜榨果汁', category: 'drink', x: 70, y: 66, color: '#0ea5e9' },
]

const demoFacilities = [
  { id: 'f1', name: '楼梯', category: 'stairs', x: 72, y: 32, symbol: '↗', color: '#64748b' },
  { id: 'f2', name: '消防栓', category: 'hydrant', x: 23, y: 44, symbol: '消', color: '#ef4444' },
  { id: 'f3', name: '卫生间', category: 'toilet', x: 72, y: 68, symbol: 'WC', color: '#2563eb' },
  { id: 'f4', name: '主入口', category: 'entrance', x: 48, y: 75, symbol: '入', color: '#10b981' },
  { id: 'f5', name: '侧入口', category: 'entrance', x: 73, y: 52, symbol: '入', color: '#10b981' },
]

const demoMenuItems = [
  { id: 'm1', name: '牛肉小面', stall: '重庆小面', category: 'noodle', price: 14, image: 'https://images.unsplash.com/photo-1569718212165-3a8278d5f624?auto=format&fit=crop&w=600&q=80' },
  { id: 'm2', name: '豌杂小面', stall: '重庆小面', category: 'noodle', price: 13, image: 'https://images.unsplash.com/photo-1612929633738-8fe44f7ec841?auto=format&fit=crop&w=600&q=80' },
  { id: 'm3', name: '宜宾燃面', stall: '一碗面', category: 'noodle', price: 12, image: 'https://images.unsplash.com/photo-1552611052-33e04de081de?auto=format&fit=crop&w=600&q=80' },
  { id: 'm4', name: '兰州牛肉面', stall: '兰州拉面', category: 'noodle', price: 15, image: 'https://images.unsplash.com/photo-1557872943-16a5ac26437e?auto=format&fit=crop&w=600&q=80' },
  { id: 'm5', name: '鲜肉水饺', stall: '水饺馄饨', category: 'noodle', price: 13, image: 'https://images.unsplash.com/photo-1496116218417-1a781b1c416c?auto=format&fit=crop&w=600&q=80' },
  { id: 'm6', name: '玉米排骨汤', stall: '瓦罐汤', category: 'soup', price: 10, image: 'https://images.unsplash.com/photo-1547592166-23ac45744acd?auto=format&fit=crop&w=600&q=80' },
  { id: 'm7', name: '莲藕肉汤', stall: '瓦罐汤', category: 'soup', price: 9, image: 'https://images.unsplash.com/photo-1603105037880-880cd4edfb0d?auto=format&fit=crop&w=600&q=80' },
  { id: 'm8', name: '牛肉炒饭', stall: '牛肉炒饭', category: 'rice', price: 16, image: 'https://images.unsplash.com/photo-1603133872878-684f208fb84b?auto=format&fit=crop&w=600&q=80' },
  { id: 'm9', name: '黄焖鸡米饭', stall: '黄焖鸡米饭', category: 'rice', price: 18, image: 'https://images.unsplash.com/photo-1512058564366-18510be2db19?auto=format&fit=crop&w=600&q=80' },
  { id: 'm10', name: '石锅拌饭', stall: '石锅拌饭', category: 'rice', price: 17, image: 'https://images.unsplash.com/photo-1590301157890-4810ed352733?auto=format&fit=crop&w=600&q=80' },
  { id: 'm11', name: '麻辣香锅', stall: '冒菜香锅', category: 'local', price: 22, image: 'https://images.unsplash.com/photo-1563245372-f21724e3856d?auto=format&fit=crop&w=600&q=80' },
  { id: 'm12', name: '经典煎饼果子', stall: '煎饼果子', category: 'local', price: 8, image: 'https://images.unsplash.com/photo-1565299507177-b0ac66763828?auto=format&fit=crop&w=600&q=80' },
  { id: 'm13', name: '原味奶茶', stall: '奶茶窗口', category: 'drink', price: 8, image: 'https://images.unsplash.com/photo-1558857563-b371033873b8?auto=format&fit=crop&w=600&q=80' },
  { id: 'm14', name: '鲜榨橙汁', stall: '鲜榨果汁', category: 'drink', price: 10, image: 'https://images.unsplash.com/photo-1621506289937-a8e4df240d0b?auto=format&fit=crop&w=600&q=80' },
  { id: 'm15', name: '红豆蛋糕', stall: '烘焙甜点', category: 'drink', price: 12, image: 'https://images.unsplash.com/photo-1578985545062-69928b1d9587?auto=format&fit=crop&w=600&q=80' },
]

const demoClusters = [
  { id: 'c1', count: 4, x: 36, y: 31, color: '#f97316' },
  { id: 'c2', count: 2, x: 69, y: 35, color: '#0ea5e9' },
  { id: 'c3', count: 4, x: 45, y: 52, color: '#8b5cf6' },
  { id: 'c4', count: 3, x: 29, y: 69, color: '#14b8a6' },
  { id: 'c5', count: 2, x: 65, y: 70, color: '#ec4899' },
]

const demoMediumPoints = [
  { id: 'mc1', type: 'cluster', count: 3, x: 33, y: 30, color: '#f97316' },
  { id: 'ms4', type: 'stall', stallId: 's4' },
  { id: 'mc2', type: 'cluster', count: 2, x: 69, y: 35, color: '#0ea5e9' },
  { id: 'mc3', type: 'cluster', count: 3, x: 40, y: 53, color: '#8b5cf6' },
  { id: 'ms9', type: 'stall', stallId: 's9' },
  { id: 'mc4', type: 'cluster', count: 2, x: 29, y: 69, color: '#14b8a6' },
  { id: 'ms13', type: 'stall', stallId: 's13' },
  { id: 'mc5', type: 'cluster', count: 2, x: 65, y: 70, color: '#ec4899' },
]

const resolvedMediumPoints = computed(() => demoMediumPoints.map(point => point.type === 'stall'
  ? { ...point, stall: demoStalls.find(stall => stall.id === point.stallId) }
  : point))

const filteredDemoStalls = computed(() => {
  const keyword = indoorSearch.value.trim().toLowerCase()
  return demoStalls.filter(stall =>
    (indoorCategory.value === 'all' || stall.category === indoorCategory.value)
    && (!keyword || stall.name.toLowerCase().includes(keyword)))
})

const filteredDemoFacilities = computed(() => demoFacilities.filter(item => indoorCategory.value === item.category))
const filteredDemoMenu = computed(() => {
  const keyword = indoorSearch.value.trim().toLowerCase()
  return demoMenuItems.filter(item =>
    (indoorCategory.value === 'all' || item.category === indoorCategory.value)
    && (!keyword || `${item.name}${item.stall}`.toLowerCase().includes(keyword)))
})

function selectIndoorCategory(category) {
  indoorCategory.value = category
  if (['stairs', 'hydrant', 'toilet', 'entrance'].includes(category)) indoorView.value = 'map'
}

function showStallOnMap(stall) {
  indoorCategory.value = stall.category
  indoorSearch.value = stall.name
  indoorZoom.value = 1.7
  indoorView.value = 'map'
}

function showStallMenu(stall) {
  indoorCategory.value = stall.category
  indoorSearch.value = stall.name
  indoorView.value = 'menu'
}

function openFloorDetail(floor) {
  indoorOpen.value = true
  indoorCanteen.value = activePoi.value
  indoorFloorId.value = floor.id
  indoorZoom.value = 1.12
  indoorSearch.value = ''
  indoorCategory.value = 'all'
  indoorView.value = 'map'
}

function adjustIndoorZoom(delta) {
  indoorZoom.value = Math.max(1.08, Math.min(1.8, Number((indoorZoom.value + delta).toFixed(2))))
}

function onIndoorWheel(event) {
  adjustIndoorZoom(event.deltaY < 0 ? 0.1 : -0.1)
}

function startIndoorDrag(event) {
  if (event.button !== 0) return
  const viewport = event.currentTarget
  indoorDragging.value = true
  indoorDragViewport = viewport
  indoorDragStartX = event.clientX
  indoorDragStartY = event.clientY
  indoorDragScrollLeft = viewport.scrollLeft
  indoorDragScrollTop = viewport.scrollTop
  window.addEventListener('mousemove', moveIndoorDrag)
  window.addEventListener('mouseup', stopIndoorDrag, { once: true })
  event.preventDefault()
}

function moveIndoorDrag(event) {
  if (!indoorDragging.value || !indoorDragViewport) return
  const viewport = indoorDragViewport
  viewport.scrollLeft = indoorDragScrollLeft - (event.clientX - indoorDragStartX)
  viewport.scrollTop = indoorDragScrollTop - (event.clientY - indoorDragStartY)
  event.preventDefault()
}

function stopIndoorDrag() {
  indoorDragging.value = false
  indoorDragViewport = null
  window.removeEventListener('mousemove', moveIndoorDrag)
}

const indoorPositionMap = computed(
  () => new Map(indoorPositions.value.map(position => [String(position.placeId), position])),
)

async function selectIndoorFloor(floorId) {
  indoorFloorId.value = floorId
  indoorFloorPlan.value = null
  indoorStalls.value = []
  indoorPositions.value = []
  if (!floorId) return

  indoorLoading.value = true
  try {
    const [planResponse, stallResponse] = await Promise.all([
      getFloorPlan(floorId),
      getMapPlaceList({ sceneType: 'CANTEEN', parentId: floorId, status: 'ENABLED' }),
    ])
    const plan = planResponse?.data || null
    indoorFloorPlan.value = plan
    indoorStalls.value = (Array.isArray(stallResponse?.data) ? stallResponse.data : [])
      .filter(place => place.placeType === 'CANTEEN_STALL')
      .sort((left, right) => (left.sortOrder || 0) - (right.sortOrder || 0))
    if (plan) {
      const positionResponse = await getFloorPlanPositions(plan.id)
      indoorPositions.value = Array.isArray(positionResponse?.data) ? positionResponse.data : []
    }
  } catch (error) {
    mapError.value = error.message || '楼层档口信息加载失败'
  } finally {
    indoorLoading.value = false
  }
}

async function openIndoorGuide(poi) {
  indoorOpen.value = true
  indoorCanteen.value = poi
  indoorFloors.value = []
  indoorFloorId.value = null
  indoorFloorPlan.value = null
  indoorStalls.value = []
  indoorPositions.value = []
  indoorLoading.value = true
  try {
    const response = await getMapPlaceList({
      sceneType: 'CANTEEN',
      parentId: poi.id,
      status: 'ENABLED',
    })
    indoorFloors.value = (Array.isArray(response?.data) ? response.data : [])
      .filter(place => place.placeType === 'FLOOR')
      .sort((left, right) => (left.sortOrder || 0) - (right.sortOrder || 0))
    if (indoorFloors.value.length) {
      await selectIndoorFloor(indoorFloors.value[0].id)
    }
  } catch (error) {
    mapError.value = error.message || '食堂楼层加载失败'
  } finally {
    indoorLoading.value = false
  }
}

function closeIndoorGuide() {
  indoorOpen.value = false
}

function createInfoWindowContent(poi) {
  const root = document.createElement('div')
  root.className = 'map-info-window'

  const heading = document.createElement('div')
  heading.className = 'map-info-window__heading'
  const pin = document.createElement('img')
  pin.className = 'map-info-window__pin'
  pin.src = sceneMeta(poi.sceneType).icon
  pin.alt = ''
  const name = document.createElement('strong')
  name.textContent = poi.name
  heading.append(pin, name)

  const type = document.createElement('span')
  type.className = 'map-info-window__type'
  type.textContent = poi.tag
  const description = document.createElement('p')
  description.textContent = poi.desc

  root.append(heading, type, description)
  if (poi.sceneType === 'CANTEEN') {
    const indoorButton = document.createElement('button')
    indoorButton.type = 'button'
    indoorButton.className = 'map-info-window__indoor-button'
    indoorButton.textContent = '查看楼层与档口'
    indoorButton.addEventListener('click', () => openIndoorGuide(poi))
    root.append(indoorButton)
  }
  return root
}

async function selectPoi(poi, marker) {
  try {
    const response = await getMapPlaceDetail(poi.id)
    const detail = response?.data ? toMapPoi(response.data) : poi
    activePoi.value = detail
    /* 使用页面右侧的统一详情卡，避免同时出现高德默认信息窗。 */
    infoWindow?.close()
    await nextTick()
    updateActivePoiScreen()
  } catch (error) {
    mapError.value = error.message || '点位详情加载失败'
  }
}

/* 动态加载高德 JS API 脚本 */
function loadAMapScript() {
  return new Promise((resolve, reject) => {
    /* 安全配置必须在脚本加载前设置 */
    window._AMapSecurityConfig = { securityJsCode: AMAP_SECURITY }
    if (window.AMap) { resolve(); return }
    const s = document.createElement('script')
    s.src = `https://webapi.amap.com/maps?v=2.0&key=${AMAP_KEY}`
    s.onload = resolve
    s.onerror = () => reject(new Error('高德地图脚本加载失败'))
    document.head.appendChild(s)
  })
}

/* 初始化地图、添加标记点、绑定事件 */
async function initMap() {
  try {
    await loadAMapScript()
    const AMap = window.AMap
    if (!AMap) throw new Error('AMap 未定义')

    const firstPlace = mapPlaces.value[0]
    mapInstance = new AMap.Map('container', {
      zoom: MAP_ZOOM,
      center: firstPlace ? [firstPlace.lng, firstPlace.lat] : MAP_CENTER,
      viewMode: '2D',
      resizeEnable: true,
    })
    mapInstance.on('mapmove', updateActivePoiScreen)
    mapInstance.on('zoomchange', updateActivePoiScreen)

    /* 全局信息窗实例 */
    infoWindow = new AMap.InfoWindow({
      offset: new AMap.Pixel(0, -43),
      closeWhenClickMap: true,
    })

    /* 为接口返回的真实点位添加标记 */
    mapPlaces.value.forEach(poi => {
      const meta = sceneMeta(poi.sceneType)
      let fenceOverlay = null
      if (poi.fence?.geometryType === 'POLYGON') {
        fenceOverlay = new AMap.Polygon({
          path: poi.fence.path,
          strokeColor: meta.color,
          strokeWeight: 3,
          strokeOpacity: 0.95,
          fillColor: meta.color,
          fillOpacity: 0.2,
          zIndex: 80,
          bubble: false,
        })
      } else if (poi.fence?.geometryType === 'LINESTRING') {
        fenceOverlay = new AMap.Polyline({
          path: poi.fence.path,
          strokeColor: meta.color,
          strokeWeight: 4,
          strokeOpacity: 0.95,
          zIndex: 80,
          bubble: false,
        })
      }
      if (fenceOverlay) {
        mapInstance.add(fenceOverlay)
        mapOverlays.push(fenceOverlay)
        fenceMap[poi.id] = fenceOverlay
      }

      const markerContent = document.createElement('div')
      markerContent.className = 'real-map-marker'
      const markerTitle = document.createElement('span')
      markerTitle.className = 'real-map-marker__title'
      markerTitle.textContent = poi.name
      const markerIcon = document.createElement('img')
      markerIcon.className = 'real-map-marker__icon'
      markerIcon.src = meta.icon
      markerIcon.alt = ''
      markerContent.append(markerTitle, markerIcon)
      const marker = new AMap.Marker({
        position: [poi.lng, poi.lat],
        title: poi.name,
        content: markerContent,
        offset: new AMap.Pixel(-70, -64),
      })

      marker.on('click', () => {
        if (poi.sceneType !== 'CANTEEN') return
        selectPoi(poi, marker)
      })
      fenceOverlay?.on('click', () => {
        if (poi.sceneType !== 'CANTEEN') return
        mapInstance.setZoomAndCenter(Math.max(mapInstance.getZoom() || MAP_ZOOM, MAP_ZOOM), [poi.lng, poi.lat])
        selectPoi(poi, marker)
      })

      markerMap[poi.id] = marker
      mapInstance.add(marker)
    })

    mapReady.value = true
    const visibleOverlays = [...mapOverlays, ...Object.values(markerMap)]
    if (visibleOverlays.length > 1) mapInstance.setFitView(visibleOverlays, false, [80, 80, 100, 80], 18)
  } catch (err) {
    mapError.value = err.message || '地图初始化失败'
    console.error('[MapInit]', err)
  }
}

/* 地图飞行到指定点位并打开信息窗 */
async function flyToPoi(poi) {
  if (!mapInstance) return
  mapInstance.setZoomAndCenter(17, [poi.lng, poi.lat], false, 500)
  const marker = markerMap[poi.id]
  await selectPoi(poi, marker)
}

/* ═══════════════════════════════════════
   ③ 快捷按钮 / 导航
   ═══════════════════════════════════════ */
function flyTo(poi) {
  if (poi.sceneType !== 'CANTEEN') return
  selectedNotice.value = `正在查看「${poi.name}」`
  flyToPoi(poi)
  setTimeout(() => { selectedNotice.value = '' }, 3000)
}

/* ═══════════════════════════════════════
   ④ 聊天助手
   ═══════════════════════════════════════ */
const chatExpanded = ref(false)
const chatTab = ref('chat') /* chat | lostfound */
const chatInput = ref('')
const chatMessages = reactive([
  { role: 'assistant', text: '你好！我是校园助手，可以查询地点信息、失物招领等。试试问我吧！' },
])
const chatBodyRef = ref(null)

function toggleChat() { chatExpanded.value = !chatExpanded.value; if (chatExpanded.value) nextTick(scrollChat) }
function newChat() {
  chatMessages.splice(0, chatMessages.length, { role: 'assistant', text: '新会话已开始，有什么可以帮你？' })
  chatInput.value = ''
}
function sendChat() {
  const t = chatInput.value.trim(); if (!t) return
  chatMessages.push({ role: 'user', text: t }); chatInput.value = ''; nextTick(scrollChat)
  setTimeout(() => { chatMessages.push({ role: 'assistant', text: genReply(t) }); nextTick(scrollChat) }, 400)
}
function genReply(s) {
  const l = s.toLowerCase()
  for (const p of mapPlaces.value) if (l.includes(p.name.toLowerCase())) return `${p.name}：${p.desc}。点击下方快捷按钮可快速定位。`
  if (/路线|怎么走|在哪|位置/.test(l)) return '在搜索框输入地点名，或点击底部快捷按钮即可定位。'
  if (/打卡/.test(l)) return '点击底部快捷按钮选中地点，在弹出的地点面板中点击"打卡"按钮即可。'
  if (/失物|丢|捡/.test(l)) return '请点击右下角助手面板，切换到"失物招领"标签页查看或发布信息。'
  if (/你好|嗨|hello|hi/.test(l)) return '你好！可以问我教学楼、食堂、运动场、快递站、图书馆、校医院的信息。'
  return '你可以问我校园地点相关信息，也可以查询失物招领或进行校园打卡哦！'
}
function scrollChat() { if (chatBodyRef.value) chatBodyRef.value.scrollTop = chatBodyRef.value.scrollHeight }

/* ═══════════════════════════════════════
   ⑤ 校园打卡系统
   ═══════════════════════════════════════ */
const checkins = reactive(JSON.parse(localStorage.getItem('campus-checkins') || '{}'))
function doCheckin(poi) {
  if (checkins[poi.id]) return
  checkins[poi.id] = { time: Date.now() }
  localStorage.setItem('campus-checkins', JSON.stringify({ ...checkins }))
}

/* ═══════════════════════════════════════
   ⑥ 点位留言板
   ═══════════════════════════════════════ */
const boardData = reactive(JSON.parse(localStorage.getItem('campus-board') || '{}'))
const boardInput = ref('')
const boardAuthor = ref('')
function boardMessages(poiId) { return boardData[poiId] || [] }
function postBoard(poi) {
  const t = boardInput.value.trim(); if (!t) return
  if (!boardData[poi.id]) boardData[poi.id] = []
  boardData[poi.id].push({ text: t, author: boardAuthor.value.trim() || '匿名', time: Date.now() })
  localStorage.setItem('campus-board', JSON.stringify({ ...boardData }))
  boardInput.value = ''
}
function fmtTime(ts) {
  const d = new Date(ts)
  return `${d.getMonth() + 1}/${d.getDate()} ${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

/* ═══════════════════════════════════════
   ⑦ 失物招领
   ═══════════════════════════════════════ */
const lostItems = reactive(JSON.parse(localStorage.getItem('campus-lost') || '[]'))
const lostForm = reactive({ title: '', desc: '', type: 'lost', contact: '' })
const showLostForm = ref(false)
function postLost() {
  if (!lostForm.title.trim()) return
  lostItems.unshift({ ...lostForm, time: Date.now() })
  localStorage.setItem('campus-lost', JSON.stringify([...lostItems]))
  Object.assign(lostForm, { title: '', desc: '', type: 'lost', contact: '' })
  showLostForm.value = false
}

/* ═══════════════════════════════════════
   ⑧ 随机漫步
   ═══════════════════════════════════════ */
const randomPoi = ref(null)
function randomWalk() {
  if (!mapPlaces.value.length) return
  const pick = mapPlaces.value[Math.floor(Math.random() * mapPlaces.value.length)]
  randomPoi.value = pick
  flyToPoi(pick) /* 随机漫步也移动地图 */
}

/* ═══════════════════════════════════════
   全局事件
   ═══════════════════════════════════════ */
function onDocClick(e) {
  if (!e.target.closest('.search-wrapper')) searchFocused.value = false
  if (!e.target.closest('.random-modal') && !e.target.closest('.random-btn')) randomPoi.value = null
}
onMounted(async () => {
  applyTheme()
  document.addEventListener('click', onDocClick)
  await loadMapPlaces()
  await nextTick()
  initMap()
})
onUnmounted(() => {
  document.removeEventListener('click', onDocClick)
  stopIndoorDrag()
  if (floorPreviewTimer) clearTimeout(floorPreviewTimer)
  mapOverlays.splice(0, mapOverlays.length)
  if (mapInstance) { mapInstance.destroy(); mapInstance = null }
})
</script>

<template>
  <div class="map-page" :class="{ dark: isDark }">
    <AppTabBar />

    <!-- ═══ 主区域 ═══ -->
    <div class="map-main">

      <!-- 高德地图容器（id="container" 固定） -->
      <div id="container" class="map-canvas">
        <!-- 加载中状态 -->
        <div v-if="!mapReady && !mapError" class="map-loading">
          <div class="spinner"></div>
          <span>地图加载中...</span>
        </div>
        <!-- 加载失败回退 -->
        <div v-if="mapError" class="map-fallback">
          <span>{{ mapError }}</span>
          <small>请检查高德地图 Key 配置和网络连接</small>
        </div>
      </div>

      <!-- ② 搜索栏 -->
      <div class="search-wrapper">
        <div class="search-box">
          <svg class="search-icon" viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2">
            <circle cx="11" cy="11" r="8"/><path d="m21 21-4.35-4.35"/>
          </svg>
          <input v-model="searchQuery" class="search-input" placeholder="搜索校区、食堂、宿舍楼..."
            @input="searchFocused = true" @focus="searchFocused = true"/>
          <button v-if="searchQuery" class="search-clear" @click="clearSearch">✕</button>
          <button class="search-submit" @click="filteredPois[0] && selectSearchResult(filteredPois[0])">搜索</button>
        </div>
        <div v-if="searchFocused && filteredPois.length" class="search-dropdown">
          <div v-for="p in filteredPois" :key="p.id" class="search-item" @click="selectSearchResult(p)">
            <img class="si-icon" :src="sceneMeta(p.sceneType).icon" alt="" />
            <div><div class="si-name">{{ p.name }}</div><div class="si-desc">{{ p.desc }}</div></div>
          </div>
        </div>
      </div>

      <!-- 左侧一级分类：默认收起，仅食堂分类响应点击 -->
      <aside class="category-rail" :class="{ expanded: categoryExpanded }">
        <button class="category-all" type="button" @click="selectAllCategories">
          <span class="category-all-icon">⠿</span>
          <span>全部</span>
        </button>
        <div class="category-list">
          <button
            v-for="item in categoryItems"
            :key="item.key"
            type="button"
            class="category-item"
            :class="[{ active: activeCategory === item.key }, `category-item--${item.key}`]"
            @click="selectCategory(item)"
          >
            <span class="category-item-icon">{{ item.icon }}</span>
            <span>{{ item.label }}</span>
          </button>
        </div>
      </aside>
      <Transition name="campus-menu">
        <nav v-if="categoryExpanded && campusMenuOpen" class="campus-submenu" aria-label="校区分类">
          <button v-for="campus in campusItems" :key="campus.key" type="button" :class="{ active: activeCampus === campus.key }" @click="selectCampus(campus)">
            <span>▦</span>{{ campus.label }}
          </button>
        </nav>
      </Transition>
      <Transition name="campus-menu">
        <nav v-if="categoryExpanded && otherMenuOpen" class="other-submenu" aria-label="其他分类">
          <button v-for="place in otherItems" :key="place.key" type="button" :class="{ active: activeOtherPlace === place.key }" @click="selectOtherPlace(place)">
            <span>•</span>{{ place.label }}
          </button>
        </nav>
      </Transition>

      <!-- 选中提示条 -->
      <Transition name="fade">
        <div v-if="selectedNotice" class="notice-bar">{{ selectedNotice }}</div>
      </Transition>

      <!-- 顶部工具栏 -->
      <div class="top-tools">
        <button class="tool-btn" @click="toggleDark" :title="isDark ? '日间模式' : '夜间模式'">
          {{ isDark ? '☀️' : '🌙' }}
        </button>
        <button class="tool-btn random-btn" @click="randomWalk" title="随机漫步">🎲</button>
      </div>

      <!-- 随机漫步弹窗 -->
      <Transition name="fade">
        <div v-if="randomPoi" class="random-modal">
          <div class="random-name">{{ randomPoi.name }}</div>
          <div class="random-desc">{{ randomPoi.desc }}</div>
          <div class="random-hint">去这里走走吧！</div>
          <button class="random-close" @click="randomPoi = null">好的</button>
        </div>
      </Transition>

      <!-- 选中点位详情面板 -->
      <Transition name="slide-up">
        <div
          v-if="activePoi?.sceneType === 'CANTEEN'"
          class="poi-panel canteen-panel"
          :style="{ left: `${activePoiScreen.panelX}px`, top: `${activePoiScreen.panelY}px` }"
        >
          <div class="poi-header canteen-header">
            <span class="canteen-type-icon">餐</span>
            <span class="canteen-heading">
              <span class="poi-name">{{ activePoi.name }}</span>
              <small>校园综合餐饮服务中心</small>
            </span>
            <span class="canteen-open">营业中</span>
            <button class="poi-close" @click="closeActivePoi">✕</button>
          </div>
          <div class="canteen-badges"><span>食堂</span><span>共{{ displayedFloorPreview.length }}层</span></div>
          <div class="canteen-meta"><b>⌖</b><span>{{ activePoi.locationDesc || '校园生活区' }}</span></div>
          <div class="canteen-meta"><b>◷</b><span>06:30—21:00<small>早餐 06:30–09:00，午餐 10:30–14:00，晚餐 16:30–20:30</small></span></div>
          <div class="canteen-meta"><b>¥</b><span>约12—20元</span></div>
          <div class="canteen-intro">
            <strong>餐厅简介</strong>
            <p>{{ activePoi.desc || `${activePoi.name}提供日常早餐、午餐和晚餐服务，餐品丰富，价格适中。` }}</p>
          </div>
          <div class="canteen-services"><span>早餐供应</span><span>支持打包</span><span>校园卡</span><span>移动支付</span><span>无障碍通道</span></div>
          <div class="more-info-zone" @mouseenter="showFloorPreview(activePoi)" @mouseleave="hideFloorPreview">
            <button class="more-info-button" type="button" @click="showFloorPreview(activePoi)">查看更多信息</button>
          </div>
        </div>
      </Transition>

      <Transition name="fade">
        <section
          v-if="activePoi?.sceneType === 'CANTEEN' && floorPreviewOpen"
          class="floor-preview"
          :style="floorPreviewStyle"
          @mouseenter="keepFloorPreview"
          @mouseleave="hideFloorPreview"
        >
          <h3>选择楼层</h3>
          <p>进入对应室内地图</p>
          <div class="floor-preview-list">
            <button v-for="(floor, index) in displayedFloorPreview" :key="floor.id" type="button" :class="{ active: index === 0 }" @click="openFloorDetail(floor)">
              <b>{{ floor.code }}</b><span><strong>{{ floor.name }}</strong><small>{{ floor.description }}</small></span><i>›</i>
            </button>
          </div>
        </section>
      </Transition>

      <Transition name="fade">
        <div v-if="indoorOpen" class="indoor-guide-mask" @click.self="closeIndoorGuide">
          <section class="indoor-guide-dialog">
            <div class="indoor-breadcrumb">智慧校园 <b>›</b> {{ indoorCanteen?.name || '第一食堂' }} <b>›</b> <strong>第一层</strong></div>
            <button class="indoor-guide-close" type="button" @click="closeIndoorGuide">×</button>

            <header class="indoor-toolbar">
              <h2>{{ indoorCanteen?.name || '第一食堂' }} · 第一层</h2>
              <nav class="indoor-view-tabs">
                <button :class="{ active: indoorView === 'map' }" @click="indoorView = 'map'">▱ 平面地图</button>
                <button :class="{ active: indoorView === 'list' }" @click="indoorView = 'list'">☷ 档口列表</button>
                <button :class="{ active: indoorView === 'menu' }" @click="indoorView = 'menu'">▤ 菜品菜单</button>
              </nav>
              <label class="indoor-search"><span>⌕</span><input v-model="indoorSearch" :placeholder="indoorView === 'menu' ? '搜索第一层菜品' : '搜索第一层档口'" /></label>
            </header>

            <nav class="indoor-categories">
              <button :class="{ active: indoorCategory === 'all' }" @click="selectIndoorCategory('all')">全部</button>
              <button :class="{ active: indoorCategory === 'noodle' }" @click="selectIndoorCategory('noodle')">面食类</button>
              <button :class="{ active: indoorCategory === 'soup' }" @click="selectIndoorCategory('soup')">汤羹类</button>
              <button :class="{ active: indoorCategory === 'rice' }" @click="selectIndoorCategory('rice')">炒饭盖饭</button>
              <button :class="{ active: indoorCategory === 'local' }" @click="selectIndoorCategory('local')">地方小吃</button>
              <button :class="{ active: indoorCategory === 'drink' }" @click="selectIndoorCategory('drink')">饮品甜点</button>
              <button class="indoor-facility indoor-facility--first" :class="{ active: indoorCategory === 'stairs' }" @click="selectIndoorCategory('stairs')">楼梯</button>
              <button class="indoor-facility" :class="{ active: indoorCategory === 'hydrant' }" @click="selectIndoorCategory('hydrant')">消防栓</button>
              <button class="indoor-facility" :class="{ active: indoorCategory === 'toilet' }" @click="selectIndoorCategory('toilet')">卫生间</button>
              <button class="indoor-facility" :class="{ active: indoorCategory === 'entrance' }" @click="selectIndoorCategory('entrance')">出入口</button>
            </nav>

            <main v-if="indoorView === 'map'" class="indoor-map-stage">
              <div class="indoor-plan-viewport" :class="{ dragging: indoorDragging }" @wheel.prevent="onIndoorWheel" @mousedown="startIndoorDrag">
                <div class="indoor-plan-canvas" :style="{ width: `${indoorZoom * 100}%` }">
                  <img :src="firstCanteenFloorPlan" alt="第一食堂第一层平面图" draggable="false" />
                  <template v-if="filteredDemoFacilities.length">
                    <span v-for="facility in filteredDemoFacilities" :key="facility.id" class="facility-map-marker" :style="{ left: `${facility.x}%`, top: `${facility.y}%`, '--facility-color': facility.color }"><i>{{ facility.symbol }}</i><b>{{ facility.name }}</b></span>
                  </template>
                  <template v-else-if="indoorZoom < 1.35 && indoorCategory === 'all' && !indoorSearch">
                    <span v-for="cluster in demoClusters" :key="cluster.id" class="stall-cluster" :style="{ left: `${cluster.x}%`, top: `${cluster.y}%`, background: cluster.color }">{{ cluster.count }}</span>
                  </template>
                  <template v-else-if="indoorZoom < 1.65 && indoorCategory === 'all' && !indoorSearch">
                    <template v-for="point in resolvedMediumPoints" :key="point.id">
                      <span v-if="point.type === 'cluster'" class="stall-cluster" :style="{ left: `${point.x}%`, top: `${point.y}%`, background: point.color }">{{ point.count }}</span>
                      <span v-else-if="point.stall" class="demo-stall-marker" :style="{ left: `${point.stall.x}%`, top: `${point.stall.y}%`, '--stall-color': point.stall.color }"><i></i><b>{{ point.stall.name }}</b></span>
                    </template>
                  </template>
                  <template v-else>
                    <span v-for="stall in filteredDemoStalls" :key="stall.id" class="demo-stall-marker" :style="{ left: `${stall.x}%`, top: `${stall.y}%`, '--stall-color': stall.color }">
                      <i></i><b>{{ stall.name }}</b>
                    </span>
                  </template>
                </div>
              </div>
              <div class="indoor-zoom-controls">
                <button @click="adjustIndoorZoom(.2)">＋</button>
                <button @click="adjustIndoorZoom(-.2)">−</button>
                <button @click="indoorZoom = 1.12">◎</button>
              </div>
              <span class="indoor-zoom-hint">{{ indoorZoom < 1.35 ? '滚轮向上：逐步拆分聚合档口' : indoorZoom < 1.65 ? '继续放大：查看全部档口名称' : `当前显示 ${filteredDemoStalls.length} 个档口` }}</span>
            </main>

            <main v-else-if="indoorView === 'list'" class="indoor-content-view">
              <div class="indoor-content-heading"><strong>第一层档口</strong><span>共{{ filteredDemoStalls.length }}个档口</span></div>
              <div class="indoor-stall-grid">
                <article v-for="stall in filteredDemoStalls" :key="stall.id" class="indoor-stall-card">
                  <div class="stall-card-title"><i :style="{ background: stall.color }">餐</i><span><strong>{{ stall.name }}</strong><small>{{ indoorCategoryLabels[stall.category] }}</small></span></div>
                  <div class="stall-card-actions"><button @click="showStallOnMap(stall)">查看位置</button><button @click="showStallMenu(stall)">查看菜品</button></div>
                </article>
                <p v-if="!filteredDemoStalls.length" class="indoor-empty">当前筛选下暂无档口</p>
              </div>
            </main>

            <main v-else class="indoor-content-view">
              <div class="indoor-content-heading"><strong>第一层菜品</strong><span>共{{ filteredDemoMenu.length }}道菜品</span></div>
              <div class="indoor-menu-grid">
                <article v-for="dish in filteredDemoMenu" :key="dish.id" class="indoor-menu-card">
                  <img :src="dish.image" :alt="dish.name" loading="lazy" />
                  <div><strong>{{ dish.name }}</strong><small>{{ dish.stall }}</small><span>{{ indoorCategoryLabels[dish.category] }}</span></div>
                  <b>¥{{ dish.price }}</b>
                </article>
                <p v-if="!filteredDemoMenu.length" class="indoor-empty">当前筛选下暂无菜品</p>
              </div>
            </main>

            <footer class="indoor-footer"><span>◷ 营业时间 <strong>06:30—21:00</strong></span><button>⌁ 到这去</button></footer>
          </section>
        </div>
      </Transition>
    </div>

    <!-- ═══ 聊天助手 FAB ═══ -->
    <button class="chat-fab" :class="{ expanded: chatExpanded }" @click="toggleChat">
      <svg v-if="!chatExpanded" viewBox="0 0 24 24" fill="none" stroke="#fff" stroke-width="2" width="24" height="24">
        <path d="M21 15a2 2 0 0 1-2 2H7l-4 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/>
      </svg>
      <svg v-else viewBox="0 0 24 24" fill="none" stroke="#fff" stroke-width="2" width="20" height="20">
        <path d="M18 6 6 18M6 6l12 12"/>
      </svg>
    </button>

    <!-- ═══ 聊天/失物招领面板 ═══ -->
    <Transition name="chat-slide">
      <div v-if="chatExpanded" class="chat-panel">
        <div class="chat-header">
          <span class="chat-title">校园助手</span>
          <div class="chat-tabs">
            <button :class="{ active: chatTab === 'chat' }" @click="chatTab = 'chat'">对话</button>
            <button :class="{ active: chatTab === 'lostfound' }" @click="chatTab = 'lostfound'">失物招领</button>
          </div>
          <div class="chat-header-actions">
            <button v-if="chatTab === 'chat'" class="chat-btn" @click="newChat" title="新会话">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16"><path d="M12 5v14M5 12h14"/></svg>
            </button>
            <button class="chat-btn" @click="toggleChat" title="收起">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="16" height="16"><path d="M6 9l6 6 6-6"/></svg>
            </button>
          </div>
        </div>

        <!-- 对话标签页 -->
        <template v-if="chatTab === 'chat'">
          <div ref="chatBodyRef" class="chat-body">
            <div v-for="(m, i) in chatMessages" :key="i" class="chat-msg" :class="m.role">
              <div class="chat-bubble">{{ m.text }}</div>
            </div>
          </div>
          <div class="chat-footer">
            <input v-model="chatInput" class="chat-input" placeholder="输入你的问题..." @keydown.enter="sendChat"/>
            <button class="chat-send" @click="sendChat">
              <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18"><path d="m22 2-7 20-4-9-9-4z"/><path d="m22 2-11 11"/></svg>
            </button>
          </div>
        </template>

        <!-- 失物招领标签页 -->
        <template v-if="chatTab === 'lostfound'">
          <div class="lost-body">
            <button class="lost-new-btn" @click="showLostForm = !showLostForm">
              {{ showLostForm ? '取消' : '+ 发布信息' }}
            </button>
            <div v-if="showLostForm" class="lost-form">
              <select v-model="lostForm.type">
                <option value="lost">我丢失了</option>
                <option value="found">我捡到了</option>
              </select>
              <input v-model="lostForm.title" placeholder="物品名称" />
              <input v-model="lostForm.desc" placeholder="详细描述" />
              <input v-model="lostForm.contact" placeholder="联系方式" />
              <button class="lost-submit" @click="postLost">发布</button>
            </div>
            <div v-if="!lostItems.length" class="lost-empty">暂无失物招领信息</div>
            <div v-for="(item, i) in lostItems" :key="i" class="lost-card">
              <span class="lost-tag" :class="item.type">{{ item.type === 'lost' ? '寻物' : '招领' }}</span>
              <div class="lost-title">{{ item.title }}</div>
              <div v-if="item.desc" class="lost-desc">{{ item.desc }}</div>
              <div class="lost-meta">{{ item.contact || '无联系方式' }} · {{ fmtTime(item.time) }}</div>
            </div>
          </div>
        </template>
      </div>
    </Transition>
  </div>
</template>

<style scoped>
/* ═══ 主题变量：日间 ═══ */
.map-page {
  --bg: #f0f5ff; --surface: #ffffff; --text: #1e293b; --text2: #64748b;
  --border: #e2e8f0; --primary: #3b82f6; --primary-light: #eff6ff;
  --canvas: #dbeafe; --canvas-text: #3b82f6; --shadow: rgba(0,0,0,.08);
  position: relative; width: 100%; height: 100vh; overflow: hidden;
  background: var(--bg); color: var(--text); transition: background .3s, color .3s;
}
.map-page.dark {
  --bg: #0f172a; --surface: #1e293b; --text: #e2e8f0; --text2: #94a3b8;
  --border: #334155; --primary: #60a5fa; --primary-light: #1e3a5f;
  --canvas: #1a2744; --canvas-text: #60a5fa; --shadow: rgba(0,0,0,.3);
}
.map-main { position: absolute; inset: 60px 0 0 0; }

/* ═══ 高德地图容器 ═══ */
.map-canvas {
  width: 100%; height: 100%;
  position: relative; overflow: hidden;
}
.map-loading, .map-fallback {
  position: absolute; inset: 0;
  display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 12px;
  background: linear-gradient(135deg, #e0f2fe, #dbeafe);
  color: var(--primary); font-size: 16px; font-weight: 600; z-index: 5;
}
.map-fallback { color: #ef4444; }
.map-fallback small { font-size: 13px; font-weight: 400; color: var(--text2); }
.map-canvas :global(.real-map-marker) {
  display: flex; width: 140px; height: 64px; align-items: center;
  flex-direction: column; justify-content: flex-end; cursor: pointer;
  transform-origin: 50% 100%; transition: transform .16s ease;
}
.map-canvas :global(.real-map-marker:hover) { transform: translateY(-2px) scale(1.05); }
.map-canvas :global(.real-map-marker__title) {
  overflow: hidden; max-width: 136px; margin-bottom: 3px; padding: 4px 9px;
  border: 1px solid rgba(15,23,42,.1); border-radius: 5px;
  color: #172033; background: rgba(255,255,255,.97);
  box-shadow: 0 2px 8px rgba(15,23,42,.16); font-size: 13px;
  font-weight: 600; line-height: 18px; text-overflow: ellipsis; white-space: nowrap;
}
.map-canvas :global(.real-map-marker__icon) {
  display: block; width: 28px; height: 36px; filter: drop-shadow(0 3px 4px rgba(15,23,42,.22));
}
.map-canvas :global(.map-info-window) {
  min-width: 190px; max-width: 260px; padding: 10px 14px;
  color: #334155; font-family: system-ui, sans-serif;
}
.map-canvas :global(.map-info-window__heading) {
  display: flex; align-items: center; gap: 8px; margin-bottom: 5px;
}
.map-canvas :global(.map-info-window__heading strong) { color: #111827; font-size: 16px; }
.map-canvas :global(.map-info-window__pin) {
  width: 16px; height: 21px; flex: 0 0 auto;
}
.map-canvas :global(.map-info-window__type) {
  display: block; margin-bottom: 5px; color: #3b82f6; font-size: 12px;
}
.map-canvas :global(.map-info-window p) {
  margin: 0; color: #64748b; font-size: 13px; line-height: 1.5;
}
.map-canvas :global(.map-info-window__indoor-button) {
  width: 100%; margin-top: 10px; padding: 8px 12px;
  border: 0; border-radius: 7px; background: #f97316;
  color: #fff; font-size: 13px; font-weight: 600; cursor: pointer;
}
.map-canvas :global(.map-info-window__indoor-button:hover) { background: #ea580c; }
.spinner {
  width: 32px; height: 32px;
  border: 3px solid #bfdbfe; border-top-color: var(--primary);
  border-radius: 50%; animation: spin .8s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }

/* ═══ 搜索栏 ═══ */
.search-wrapper {
  position: absolute; top: 24px; left: 28px;
  width: min(470px, calc(100% - 120px)); z-index: 12;
}
.search-box {
  display: flex; align-items: center; gap: 10px; height: 58px;
  padding: 0 10px 0 18px; background: var(--surface); border-radius: 16px;
  border: 1px solid var(--border); box-shadow: 0 6px 22px rgba(15,23,42,.12);
}
.search-icon { flex: 0 0 18px; width: 18px; height: 18px; color: var(--text2); }
.search-input {
  flex: 1; border: none; outline: none; font-size: 14px;
  color: var(--text); background: transparent;
}
.search-input::placeholder { color: var(--text2); }
.search-clear {
  width: 22px; height: 22px; display: grid; place-items: center;
  border-radius: 50%; background: var(--primary-light); border: none;
  color: var(--text2); font-size: 12px; cursor: pointer;
}
.search-submit {
  height: 38px; padding: 0 20px; border: 0; border-radius: 10px;
  color: #fff; background: #0758cf; font-weight: 700; cursor: pointer;
}
.search-dropdown {
  margin-top: 8px; background: var(--surface); border-radius: 14px;
  box-shadow: 0 8px 30px var(--shadow); overflow: hidden;
}
.search-item {
  display: flex; align-items: center; gap: 10px; padding: 12px 16px;
  cursor: pointer; transition: background .12s;
}
.search-item:hover { background: var(--primary-light); }
.search-item + .search-item { border-top: 1px solid var(--border); }
.si-icon { width: 16px; height: 21px; flex: 0 0 auto; }
.si-name { font-size: 14px; font-weight: 600; color: var(--text); }
.si-desc { font-size: 12px; color: var(--text2); margin-top: 2px; }
.notice-bar {
  position: absolute; top: 70px; left: 50%; transform: translateX(-50%);
  padding: 8px 20px; border-radius: 20px; z-index: 11;
  background: var(--primary); color: #fff; font-size: 13px; font-weight: 600;
  box-shadow: 0 4px 16px rgba(59,130,246,.3); white-space: nowrap;
}

/* ═══ 顶部工具栏 ═══ */
.top-tools { position: absolute; top: 16px; right: 16px; z-index: 12; display: flex; gap: 6px; }
.tool-btn {
  width: 38px; height: 38px; display: grid; place-items: center;
  border-radius: 50%; border: none; font-size: 18px;
  background: var(--surface); box-shadow: 0 2px 10px var(--shadow);
  cursor: pointer; transition: transform .15s;
}
.tool-btn:hover { transform: scale(1.1); }

/* ═══ 左侧校园一级分类 ═══ */
.category-rail {
  position: absolute; top: 120px; left: 28px; z-index: 12;
  width: 96px; height: 94px; overflow: hidden;
  border: 1px solid rgba(148,163,184,.22); border-radius: 24px;
  background: rgba(255,255,255,.96); box-shadow: 0 12px 32px rgba(15,23,42,.14);
  transition: height .28s cubic-bezier(.4,0,.2,1); backdrop-filter: blur(14px);
}
.category-rail.expanded { height: 610px; }
.category-all, .category-item {
  width: 100%; border: 0; background: transparent; color: #4c5b70;
  display: flex; flex-direction: column; align-items: center; justify-content: center;
  gap: 7px; font-size: 13px; font-weight: 650; cursor: default;
}
.category-all { height: 94px; color: #fff; background: #075fd7; cursor: pointer; }
.category-all-icon { font-size: 28px; line-height: 22px; }
.category-list { opacity: 0; transform: translateY(-10px); transition: opacity .18s, transform .24s; }
.category-rail.expanded .category-list { opacity: 1; transform: translateY(0); }
.category-item { height: 84px; border-bottom: 1px solid #edf1f5; }
.category-item-icon {
  width: 34px; height: 34px; display: grid; place-items: center;
  border-radius: 11px; color: #2e75d2; background: #eef5ff; font-weight: 850;
}
.category-item--canteen { cursor: pointer; }
.category-item--canteen .category-item-icon { color: #f97316; background: #fff1e8; }
.category-item.active { color: #0758cf; background: #f4f8ff; }
.campus-submenu, .other-submenu {
  position: absolute; top: 214px; left: 136px; z-index: 13; display: flex; width: 156px;
  flex-direction: column; overflow: hidden; border: 1px solid rgba(148,163,184,.28);
  border-radius: 15px; background: rgba(255,255,255,.97); box-shadow: 0 12px 30px rgba(15,23,42,.16);
  backdrop-filter: blur(14px);
}
.other-submenu { top: 634px; }
.campus-submenu button, .other-submenu button { display: flex; height: 50px; align-items: center; gap: 10px; padding: 0 17px; border: 0; border-bottom: 1px solid #edf1f5; color: #405069; background: transparent; font-size: 14px; font-weight: 700; cursor: pointer; }
.campus-submenu button:last-child, .other-submenu button:last-child { border-bottom: 0; }
.campus-submenu button:hover, .campus-submenu button.active, .other-submenu button:hover, .other-submenu button.active { color: #0758cf; background: #f1f6ff; }
.campus-submenu button span { color: #2e75d2; font-size: 18px; }
.other-submenu { top: 634px; }
.other-submenu button span { color: #64748b; font-size: 22px; line-height: 1; }
.campus-menu-enter-active, .campus-menu-leave-active { transition: opacity .18s ease, transform .18s ease; }
.campus-menu-enter-from, .campus-menu-leave-to { opacity: 0; transform: translateX(-8px); }

/* ═══ 随机漫步 ═══ */
.random-modal {
  position: absolute; top: 50%; left: 50%; transform: translate(-50%, -50%);
  z-index: 15; background: var(--surface); border-radius: 20px;
  padding: 28px 32px; text-align: center;
  box-shadow: 0 12px 40px var(--shadow); min-width: 220px;
}
.random-icon { font-size: 48px; margin-bottom: 8px; }
.random-name { font-size: 20px; font-weight: 700; color: var(--text); }
.random-desc { font-size: 13px; color: var(--text2); margin: 6px 0 12px; }
.random-hint { font-size: 14px; color: var(--primary); font-weight: 600; margin-bottom: 14px; }
.random-close {
  padding: 8px 28px; border-radius: 20px; border: none;
  background: var(--primary); color: #fff; font-weight: 700; cursor: pointer; font-size: 14px;
}

/* ═══ 底部快捷按钮 ═══ */
.quick-bar {
  position: absolute; bottom: 16px; left: 50%; transform: translateX(-50%);
  display: flex; gap: 6px; padding: 8px 12px; z-index: 10;
  background: var(--surface); border-radius: 20px;
  box-shadow: 0 4px 24px var(--shadow); overflow-x: auto;
  max-width: calc(100% - 32px); scrollbar-width: none;
}
.quick-bar::-webkit-scrollbar { display: none; }
.quick-btn {
  display: flex; flex-direction: column; align-items: center; gap: 3px;
  padding: 8px 12px; border: none; border-radius: 14px;
  background: transparent; cursor: pointer; transition: all .15s;
  white-space: nowrap; min-width: 58px;
}
.quick-btn:hover { background: var(--primary-light); }
.quick-btn.active { background: var(--primary-light); }
.quick-icon { width: 16px; height: 21px; }
.quick-label { font-size: 11px; font-weight: 600; color: var(--text2); }
.quick-btn.active .quick-label { color: var(--primary); }
.quick-empty { padding: 12px 18px; color: var(--text2); font-size: 13px; white-space: nowrap; }

/* ═══ 点位详情面板 ═══ */
.poi-panel {
  position: absolute; bottom: 80px; left: 16px; right: 16px;
  max-width: 440px; margin: 0 auto; z-index: 11;
  background: var(--surface); border-radius: 16px;
  box-shadow: 0 8px 32px var(--shadow); padding: 16px;
  max-height: 50vh; overflow-y: auto;
}

.canteen-panel {
  right: auto; bottom: auto; width: 360px;
  max-height: calc(100% - 36px); margin: 0; padding: 22px; overflow-y: auto;
  border: 1px solid rgba(148,163,184,.28); border-radius: 20px;
  background: rgba(255,255,255,.97); box-shadow: 0 16px 46px rgba(15,23,42,.2);
}
.canteen-header { justify-content: flex-start; gap: 12px; }
.canteen-type-icon {
  width: 46px; height: 46px; flex: 0 0 46px; display: grid; place-items: center;
  border-radius: 12px; color: #fff; background: #fb873f; font-size: 15px; font-weight: 800;
}
.canteen-heading { min-width: 0; display: flex; flex: 1; flex-direction: column; gap: 3px; }
.canteen-heading small { color: #7b8ba1; font-size: 12px; font-weight: 500; }
.canteen-open { padding: 4px 9px; border-radius: 999px; color: #16a34a; background: #eaf9ef; font-size: 11px; font-weight: 700; }
.canteen-badges, .canteen-services { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 18px; }
.canteen-badges span { padding: 5px 10px; border-radius: 7px; color: #61738f; background: #edf3ff; font-size: 12px; }
.canteen-meta { display: flex; align-items: flex-start; gap: 10px; margin-top: 16px; color: #3f4d61; font-size: 14px; line-height: 1.45; }
.canteen-meta b { width: 20px; color: #3477d4; text-align: center; }
.canteen-meta span { display: flex; flex-direction: column; }
.canteen-meta small { margin-top: 2px; color: #78889d; font-size: 11px; }
.canteen-intro { margin-top: 18px; }
.canteen-intro strong { font-size: 14px; }
.canteen-intro p { margin: 7px 0 0; color: #566579; font-size: 13px; line-height: 1.55; }
.canteen-services span { padding: 4px 9px; border: 1px solid #dce5ef; border-radius: 999px; color: #687b94; font-size: 11px; }
.more-info-zone { margin-top: 22px; padding-top: 2px; }
.more-info-button { width: 100%; height: 48px; border: 0; border-radius: 10px; color: #fff; background: #0758cf; font-size: 15px; font-weight: 750; cursor: pointer; }

.floor-preview {
  position: absolute; z-index: 13; width: 316px;
  padding: 22px; border: 1px solid rgba(148,163,184,.28); border-radius: 20px;
  background: rgba(255,255,255,.98); box-shadow: 0 16px 46px rgba(15,23,42,.18);
}
.floor-preview h3 { margin: 0; color: #1f2937; font-size: 20px; }
.floor-preview > p { margin: 4px 0 14px; color: #78889d; font-size: 12px; }
.floor-preview-list {
  max-height: 216px; overflow-y: auto; overscroll-behavior: contain;
  scrollbar-width: thin; scrollbar-color: #a9bdd8 transparent;
}
.floor-preview-list::-webkit-scrollbar { width: 6px; }
.floor-preview-list::-webkit-scrollbar-thumb { border-radius: 999px; background: #a9bdd8; }
.floor-preview button {
  width: 100%; height: 72px; padding: 10px 13px; border: 1px solid transparent; border-radius: 12px;
  display: flex; align-items: center; gap: 14px; color: #334155; background: transparent; text-align: left;
}
.floor-preview button.active { border-color: #8ab3f8; background: #edf4ff; color: #0758cf; }
.floor-preview button > b { width: 34px; font-size: 17px; }
.floor-preview button > span { display: flex; flex: 1; flex-direction: column; gap: 3px; }
.floor-preview button strong { font-size: 14px; }
.floor-preview button small { color: #78889d; font-size: 12px; font-weight: 500; }
.floor-preview button i { font-size: 22px; font-style: normal; }

@media (max-width: 900px) {
  .canteen-panel { right: auto; bottom: auto; width: min(360px, calc(100% - 36px)); }
  .floor-preview { right: auto; bottom: auto; width: min(316px, calc(100% - 36px)); }
  .category-rail { left: 18px; }
  .search-wrapper { left: 18px; width: calc(100% - 126px); }
}
.poi-header { display: flex; justify-content: space-between; align-items: center; }
.poi-name { font-size: 18px; font-weight: 700; color: var(--text); }
.poi-type { margin-top: 5px; color: var(--primary); font-size: 12px; }
.poi-cover {
  width: 100%; height: 150px; margin-top: 12px; border-radius: 10px;
  object-fit: cover;
}
.poi-close {
  width: 28px; height: 28px; display: grid; place-items: center;
  border-radius: 50%; border: none; background: var(--primary-light);
  color: var(--text2); cursor: pointer; font-size: 14px;
}
.poi-desc { font-size: 13px; color: var(--text2); margin: 8px 0 12px; line-height: 1.5; }
.poi-location {
  margin-top: -4px; color: var(--text2); font-size: 12px; line-height: 1.5;
}
.poi-section { margin-top: 12px; padding-top: 12px; border-top: 1px solid var(--border); }
.section-label { font-size: 13px; font-weight: 700; color: var(--text); margin-bottom: 8px; }
.checkin-btn {
  padding: 6px 20px; border-radius: 16px; border: none;
  background: var(--primary); color: #fff; font-size: 13px; font-weight: 600; cursor: pointer;
}
.checkin-btn:hover { opacity: .85; }
.checkin-done { font-size: 13px; color: #16a34a; font-weight: 600; }
.board-list { max-height: 120px; overflow-y: auto; margin-bottom: 8px; }
.board-msg {
  padding: 6px 0; font-size: 13px; border-bottom: 1px solid var(--border);
  display: flex; flex-wrap: wrap; gap: 4px; align-items: baseline;
}
.board-author { font-weight: 700; color: var(--primary); margin-right: 6px; }
.board-text { color: var(--text); flex: 1; }
.board-time { font-size: 11px; color: var(--text2); }
.board-empty { font-size: 13px; color: var(--text2); padding: 8px 0; }
.board-form { display: flex; gap: 6px; }
.board-author-input, .board-input {
  padding: 6px 10px; border-radius: 14px; border: 1px solid var(--border);
  font-size: 13px; outline: none; background: var(--bg); color: var(--text);
}
.board-author-input { width: 70px; }
.board-input { flex: 1; }
.board-send {
  padding: 6px 14px; border-radius: 14px; border: none;
  background: var(--primary); color: #fff; font-size: 13px; font-weight: 600; cursor: pointer;
}

/* ═══ 聊天 FAB ═══ */
.chat-fab {
  position: fixed; bottom: 24px; right: 24px;
  width: 52px; height: 52px; display: grid; place-items: center;
  border-radius: 50%; border: none;
  background: linear-gradient(135deg, #3b82f6, #2563eb);
  box-shadow: 0 6px 20px rgba(37,99,235,.4); cursor: pointer; z-index: 50;
  transition: transform .2s;
}
.chat-fab:hover { transform: scale(1.08); }
.chat-fab.expanded { background: #64748b; box-shadow: 0 4px 12px var(--shadow); }

/* ═══ 聊天面板 ═══ */
.chat-panel {
  position: fixed; bottom: 86px; right: 24px;
  width: 370px; max-height: 520px; display: flex; flex-direction: column;
  background: var(--surface); border-radius: 16px;
  box-shadow: 0 12px 40px var(--shadow); z-index: 50; overflow: hidden;
}
.chat-header {
  display: flex; align-items: center; gap: 10px;
  padding: 12px 14px; background: linear-gradient(135deg, #3b82f6, #2563eb); color: #fff;
}
.chat-title { font-size: 15px; font-weight: 700; white-space: nowrap; }
.chat-tabs { display: flex; gap: 4px; flex: 1; }
.chat-tabs button {
  padding: 3px 10px; border-radius: 10px; border: none;
  background: rgba(255,255,255,.15); color: rgba(255,255,255,.7);
  font-size: 12px; font-weight: 600; cursor: pointer; transition: all .12s;
}
.chat-tabs button.active { background: rgba(255,255,255,.3); color: #fff; }
.chat-header-actions { display: flex; gap: 4px; }
.chat-btn {
  width: 28px; height: 28px; display: grid; place-items: center;
  border-radius: 8px; border: none;
  background: rgba(255,255,255,.2); color: #fff; cursor: pointer;
}
.chat-btn:hover { background: rgba(255,255,255,.35); }
.chat-body {
  flex: 1; overflow-y: auto; padding: 14px;
  display: flex; flex-direction: column; gap: 10px;
  min-height: 180px; max-height: 320px; background: var(--bg);
}
.chat-msg { display: flex; }
.chat-msg.user { justify-content: flex-end; }
.chat-bubble {
  max-width: 80%; padding: 10px 14px; border-radius: 14px;
  font-size: 13px; line-height: 1.55;
}
.chat-msg.user .chat-bubble {
  background: var(--primary); color: #fff; border-bottom-right-radius: 4px;
}
.chat-msg.assistant .chat-bubble {
  background: var(--surface); color: var(--text);
  border: 1px solid var(--border); border-bottom-left-radius: 4px;
}
.chat-footer {
  display: flex; align-items: center; gap: 8px;
  padding: 10px 14px; border-top: 1px solid var(--border); background: var(--surface);
}
.chat-input {
  flex: 1; height: 38px; padding: 0 12px;
  border: 1px solid var(--border); border-radius: 19px;
  outline: none; font-size: 13px; color: var(--text);
  background: var(--bg); transition: border-color .15s;
}
.chat-input:focus { border-color: var(--primary); }
.chat-input::placeholder { color: var(--text2); }
.chat-send {
  width: 38px; height: 38px; display: grid; place-items: center;
  border-radius: 50%; border: none; background: var(--primary); color: #fff; cursor: pointer;
}

/* ═══ 失物招领 ═══ */
.lost-body { flex: 1; overflow-y: auto; padding: 12px; max-height: 400px; background: var(--bg); }
.lost-new-btn {
  width: 100%; padding: 8px; border-radius: 14px; border: 1px dashed var(--border);
  background: transparent; color: var(--primary); font-weight: 600;
  cursor: pointer; margin-bottom: 10px; font-size: 13px;
}
.lost-form { display: flex; flex-direction: column; gap: 6px; margin-bottom: 10px; }
.lost-form select, .lost-form input {
  padding: 8px 12px; border-radius: 10px; border: 1px solid var(--border);
  font-size: 13px; outline: none; background: var(--surface); color: var(--text);
}
.lost-submit {
  padding: 8px; border-radius: 12px; border: none;
  background: var(--primary); color: #fff; font-weight: 700; cursor: pointer; font-size: 14px;
}
.lost-empty { text-align: center; color: var(--text2); font-size: 13px; padding: 24px 0; }
.lost-card {
  padding: 10px 12px; background: var(--surface); border-radius: 12px;
  margin-bottom: 8px; border: 1px solid var(--border);
}
.lost-tag {
  display: inline-block; padding: 2px 8px; border-radius: 8px;
  font-size: 11px; font-weight: 700; margin-bottom: 4px;
}
.lost-tag.lost { background: #fef2f2; color: #dc2626; }
.lost-tag.found { background: #f0fdf4; color: #16a34a; }
.dark .lost-tag.lost { background: #451a1a; color: #fca5a5; }
.dark .lost-tag.found { background: #14352a; color: #86efac; }
.lost-title { font-size: 14px; font-weight: 600; color: var(--text); }
.lost-desc { font-size: 13px; color: var(--text2); margin-top: 2px; }
.lost-meta { font-size: 11px; color: var(--text2); margin-top: 4px; }

/* ═══ 动画 ═══ */
.chat-slide-enter-active, .chat-slide-leave-active { transition: all .25s ease; }
.chat-slide-enter-from, .chat-slide-leave-to { opacity: 0; transform: translateY(20px) scale(.95); }
.slide-up-enter-active, .slide-up-leave-active { transition: all .25s ease; }
.slide-up-enter-from, .slide-up-leave-to { opacity: 0; transform: translateY(20px); }
.fade-enter-active, .fade-leave-active { transition: opacity .2s; }
.fade-enter-from, .fade-leave-to { opacity: 0; }

.indoor-guide-mask {
  position: fixed; inset: 0; z-index: 1200;
  display: grid; place-items: center; padding: 32px;
  background: rgba(15, 23, 42, .58); backdrop-filter: blur(4px);
}
.indoor-guide-dialog {
  display: flex; width: min(1180px, 94vw); height: min(820px, 88vh);
  flex-direction: column; overflow: hidden;
  border: 1px solid var(--border); border-radius: 18px;
  background: var(--surface); box-shadow: 0 24px 80px rgba(15, 23, 42, .34);
}
.indoor-guide-header {
  display: flex; align-items: flex-start; justify-content: space-between; gap: 24px;
  padding: 22px 26px 18px; border-bottom: 1px solid var(--border);
}
.indoor-guide-eyebrow {
  color: #f97316; font-size: 12px; font-weight: 700; letter-spacing: .08em;
}
.indoor-guide-header h2 { margin: 5px 0 4px; color: var(--text); font-size: 23px; }
.indoor-guide-header p { margin: 0; color: var(--text2); font-size: 13px; }
.indoor-guide-close {
  display: grid; width: 34px; height: 34px; place-items: center;
  border: 0; border-radius: 50%; background: var(--primary-light);
  color: var(--text2); font-size: 24px; line-height: 1; cursor: pointer;
}
.indoor-guide-tabs {
  display: flex; gap: 8px; padding: 14px 26px;
  overflow-x: auto; border-bottom: 1px solid var(--border);
}
.indoor-guide-tabs button {
  min-width: 82px; padding: 8px 16px;
  border: 1px solid var(--border); border-radius: 8px;
  background: transparent; color: var(--text2); font-weight: 600; cursor: pointer;
}
.indoor-guide-tabs button.active {
  border-color: #f97316; background: #fff7ed; color: #c2410c;
}
.dark .indoor-guide-tabs button.active { background: rgba(249, 115, 22, .14); color: #fb923c; }
.indoor-guide-state {
  display: flex; flex: 1; align-items: center; justify-content: center; gap: 12px;
  color: var(--text2); font-size: 14px;
}
.indoor-guide-body {
  display: grid; min-height: 0; flex: 1;
  grid-template-columns: minmax(0, 1fr) 280px; gap: 18px; padding: 20px 26px 26px;
}
.indoor-guide-plan {
  position: relative; align-self: start; overflow: hidden;
  border: 1px solid var(--border); border-radius: 10px; background: var(--bg);
}
.indoor-guide-plan > img { display: block; width: 100%; height: auto; }
.indoor-guide-marker {
  position: absolute; z-index: 2; transform: translate(-50%, -50%);
  pointer-events: none;
}
.indoor-guide-marker i {
  display: block; width: 15px; height: 15px; margin: auto;
  border: 3px solid #fff; border-radius: 50%; background: #f97316;
  box-shadow: 0 2px 7px rgba(15, 23, 42, .38);
}
.indoor-guide-marker b {
  display: block; max-width: 150px; margin-top: 4px; padding: 4px 7px;
  overflow: hidden; border-radius: 5px; background: rgba(31, 41, 55, .9);
  color: #fff; font-size: 12px; font-weight: 600;
  text-overflow: ellipsis; white-space: nowrap;
}
.indoor-guide-stalls {
  min-height: 0; overflow-y: auto;
  border: 1px solid var(--border); border-radius: 10px; background: var(--bg);
}
.indoor-guide-stalls__title {
  position: sticky; top: 0; z-index: 1;
  display: flex; align-items: center; justify-content: space-between;
  padding: 14px 15px; border-bottom: 1px solid var(--border);
  background: var(--surface); color: var(--text); font-weight: 700;
}
.indoor-guide-stalls__title span {
  min-width: 24px; padding: 2px 7px; border-radius: 12px;
  background: var(--primary-light); color: var(--primary); text-align: center; font-size: 12px;
}
.indoor-guide-stall {
  display: flex; align-items: center; justify-content: space-between; gap: 10px;
  padding: 12px 14px; border-bottom: 1px solid var(--border);
}
.indoor-guide-stall > span { min-width: 0; }
.indoor-guide-stall strong,
.indoor-guide-stall small {
  display: block; overflow: hidden; text-overflow: ellipsis; white-space: nowrap;
}
.indoor-guide-stall strong { color: var(--text); font-size: 13px; }
.indoor-guide-stall small { margin-top: 3px; color: var(--text2); font-size: 11px; }
.indoor-guide-stall em {
  flex: 0 0 auto; padding: 3px 7px; border-radius: 10px;
  background: var(--border); color: var(--text2); font-size: 11px; font-style: normal;
}
.indoor-guide-stall em.ready { background: #dcfce7; color: #15803d; }
.indoor-guide-stalls__empty { padding: 40px 16px; color: var(--text2); text-align: center; }

/* ═══ 食堂楼层平面导览 ═══ */
.indoor-guide-dialog {
  position: relative; width: min(1080px, 72vw); height: min(790px, 78vh);
  min-width: 780px; background: #fff;
}
.indoor-breadcrumb {
  padding: 18px 28px 10px; color: #627086; font-size: 12px;
}
.indoor-breadcrumb b { margin: 0 8px; color: #98a4b4; }
.indoor-breadcrumb strong { color: #0758cf; }
.indoor-guide-dialog > .indoor-guide-close { position: absolute; top: 15px; right: 22px; z-index: 4; background: transparent; }
.indoor-toolbar {
  display: grid; grid-template-columns: minmax(190px, 1fr) auto minmax(220px, .9fr);
  align-items: center; gap: 22px; padding: 14px 28px 18px; border-bottom: 1px solid #dce4ef;
}
.indoor-toolbar h2 { margin: 0; color: #182335; font-size: 24px; }
.indoor-view-tabs { display: flex; padding: 5px; border-radius: 10px; background: #eaf1ff; }
.indoor-view-tabs button {
  height: 42px; padding: 0 18px; border: 0; border-radius: 8px;
  color: #4c5a70; background: transparent; font-weight: 700; cursor: pointer;
}
.indoor-view-tabs button.active { color: #0758cf; background: #fff; box-shadow: 0 2px 8px rgba(33,72,128,.1); }
.indoor-search {
  display: flex; height: 44px; align-items: center; gap: 9px; padding: 0 14px;
  border: 1px solid #b9c8df; border-radius: 9px; background: #f6f9ff;
}
.indoor-search span { color: #53647d; font-size: 20px; }
.indoor-search input { width: 100%; border: 0; outline: 0; color: #27364a; background: transparent; }
.indoor-categories {
  display: flex; align-items: center; gap: 9px; padding: 13px 28px;
  overflow-x: auto; border-bottom: 1px solid #dce4ef; background: #f3f7ff;
}
.indoor-categories button, .indoor-facility {
  flex: 0 0 auto; min-width: 62px; height: 44px; padding: 0 14px;
  border: 1px solid #bdc9db; border-radius: 999px; display: grid; place-items: center;
  color: #435168; background: #fff; font-size: 12px; font-weight: 700;
}
.indoor-categories button { cursor: pointer; }
.indoor-categories button.active { border-color: #0758cf; color: #fff; background: #0758cf; }
.indoor-facility { min-width: 54px; color: #46536a; cursor: pointer; }
.indoor-facility--first { margin-left: clamp(28px, 4vw, 72px); }
.indoor-map-stage {
  position: relative; min-height: 0; flex: 1; padding: 26px 48px;
  background-color: #edf4ff;
  background-image: linear-gradient(#dce8fa 1px, transparent 1px), linear-gradient(90deg, #dce8fa 1px, transparent 1px);
  background-size: 24px 24px;
}
.indoor-plan-viewport {
  width: 100%; height: 100%; overflow: auto; border: 1px solid #bbc8da;
  border-radius: 8px; background: #fff; box-shadow: 0 5px 18px rgba(31,55,88,.12);
  cursor: grab; touch-action: none; user-select: none;
}
.indoor-plan-viewport.dragging { cursor: grabbing; }
.indoor-plan-canvas {
  position: relative; width: 100%; height: auto; min-height: 0; aspect-ratio: 1680 / 1185;
  transition: width .2s ease, height .2s ease;
}
.indoor-plan-canvas > img { width: 100%; height: 100%; display: block; object-fit: contain; pointer-events: none; }
.stall-cluster {
  position: absolute; z-index: 3; width: 38px; height: 38px; display: grid; place-items: center;
  border: 4px solid #fff; border-radius: 50%; color: #fff; font-size: 15px; font-weight: 850;
  box-shadow: 0 4px 12px rgba(15,23,42,.3); transform: translate(-50%, -50%);
}
.demo-stall-marker { position: absolute; z-index: 3; transform: translate(-50%, -50%); }
.demo-stall-marker i {
  display: block; width: 31px; height: 31px; margin: auto; border: 4px solid #fff;
  border-radius: 50%; background: var(--stall-color); box-shadow: 0 3px 10px rgba(15,23,42,.28);
}
.demo-stall-marker b {
  display: block; margin-top: 2px; padding: 4px 7px; border: 1px solid #d4deea;
  border-radius: 5px; color: #253349; background: rgba(255,255,255,.96);
  box-shadow: 0 2px 7px rgba(15,23,42,.14); font-size: 11px; white-space: nowrap;
}
.facility-map-marker { position: absolute; z-index: 4; transform: translate(-50%, -50%); text-align: center; }
.facility-map-marker i { display: grid; width: 34px; height: 34px; margin: auto; place-items: center; border: 4px solid #fff; border-radius: 50%; color: #fff; background: var(--facility-color); box-shadow: 0 3px 10px rgba(15,23,42,.28); font-size: 10px; font-style: normal; font-weight: 800; }
.facility-map-marker b { display: block; margin-top: 3px; padding: 4px 7px; border: 1px solid #d4deea; border-radius: 5px; color: #253349; background: #fff; box-shadow: 0 2px 7px rgba(15,23,42,.14); font-size: 11px; white-space: nowrap; }
.indoor-zoom-controls {
  position: absolute; right: 59px; bottom: 37px; display: flex; flex-direction: column;
  overflow: hidden; border: 1px solid #c5d0df; border-radius: 9px; box-shadow: 0 4px 12px rgba(15,23,42,.14);
}
.indoor-zoom-controls button { width: 40px; height: 40px; border: 0; border-bottom: 1px solid #dce4ef; color: #25354a; background: #fff; font-size: 22px; cursor: pointer; }
.indoor-zoom-controls button:last-child { border-bottom: 0; color: #0758cf; }
.indoor-zoom-hint { position: absolute; left: 59px; bottom: 37px; padding: 7px 11px; border-radius: 8px; color: #53647c; background: rgba(255,255,255,.92); font-size: 11px; }
.indoor-content-view { min-height: 0; flex: 1; margin: 22px 28px; overflow: hidden; border: 1px solid #cbd6e5; border-radius: 12px; background: #fff; }
.indoor-content-heading { display: flex; height: 58px; align-items: center; justify-content: space-between; padding: 0 24px; border-bottom: 1px solid #dce4ef; color: #263449; }
.indoor-content-heading span { color: #74849a; font-size: 12px; }
.indoor-stall-grid, .indoor-menu-grid { height: calc(100% - 58px); padding: 22px; overflow-y: auto; background: #f8faff; }
.indoor-stall-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); align-content: start; gap: 18px; }
.indoor-stall-card { min-height: 132px; padding: 18px; border: 1px solid #cbd6e5; border-radius: 11px; background: #fff; box-shadow: 0 2px 6px rgba(31,55,88,.04); }
.stall-card-title { display: flex; align-items: center; gap: 12px; }
.stall-card-title > i { display: grid; width: 46px; height: 46px; flex: 0 0 auto; place-items: center; border-radius: 10px; color: #fff; font-style: normal; font-weight: 800; }
.stall-card-title span { display: flex; min-width: 0; flex-direction: column; gap: 6px; }
.stall-card-title strong { overflow: hidden; color: #1d2a3d; font-size: 15px; text-overflow: ellipsis; white-space: nowrap; }
.stall-card-title small { width: fit-content; padding: 2px 7px; border-radius: 5px; color: #f97316; background: #fff0e7; }
.stall-card-actions { display: grid; grid-template-columns: 1fr 1fr; gap: 9px; margin-top: 16px; }
.stall-card-actions button { height: 36px; border: 1px solid #0758cf; border-radius: 7px; color: #0758cf; background: #fff; font-weight: 700; cursor: pointer; }
.stall-card-actions button:last-child { color: #fff; background: #0758cf; }
.indoor-menu-grid { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); grid-auto-rows: 242px; align-content: start; gap: 18px; }
.indoor-menu-card { position: relative; min-height: 242px; overflow: hidden; border: 1px solid #cbd6e5; border-radius: 11px; background: #fff; box-shadow: 0 2px 7px rgba(31,55,88,.06); }
.indoor-menu-card > img { display: block; width: 100%; height: 142px; object-fit: cover; background: #e9f0fb; }
.indoor-menu-card > div { display: flex; padding: 13px 14px 16px; flex-direction: column; gap: 5px; }
.indoor-menu-card strong { color: #1d2a3d; font-size: 15px; }
.indoor-menu-card small { color: #65758c; }
.indoor-menu-card span { width: fit-content; padding: 2px 7px; border-radius: 5px; color: #f97316; background: #fff0e7; font-size: 10px; }
.indoor-menu-card > b { position: absolute; right: 13px; bottom: 16px; color: #ef4444; font-size: 15px; }
.indoor-empty { grid-column: 1 / -1; padding: 70px 0; color: #7a889b; text-align: center; }
.indoor-footer { display: flex; min-height: 68px; align-items: center; justify-content: space-between; padding: 0 28px; border-top: 1px solid #dce4ef; }
.indoor-footer span { color: #4e5c70; font-size: 12px; }
.indoor-footer span strong { display: block; margin-left: 22px; color: #1f2c3e; font-size: 14px; }
.indoor-footer button { width: 150px; height: 44px; border: 0; border-radius: 9px; color: #fff; background: #0758cf; font-size: 15px; font-weight: 750; }

/* ═══ 响应式 ═══ */
@media (max-width: 480px) {
  .chat-panel { right: 8px; left: 8px; width: auto; bottom: 80px; }
  .chat-fab { right: 12px; bottom: 16px; width: 46px; height: 46px; }
  .quick-bar { bottom: 10px; gap: 4px; padding: 6px 8px; }
  .quick-btn { padding: 6px 8px; min-width: 50px; }
  .quick-icon { font-size: 18px; } .quick-label { font-size: 10px; }
  .poi-panel { left: 8px; right: 8px; bottom: 70px; }
  .search-wrapper { width: calc(100% - 80px); }
  .random-modal { width: calc(100% - 48px); padding: 20px; }
}
@media (max-width: 760px) {
  .campus-submenu { left: 124px; }
  .indoor-guide-mask { padding: 12px; }
  .indoor-guide-dialog { width: 100%; height: 92vh; min-width: 0; }
  .indoor-toolbar { grid-template-columns: 1fr; gap: 10px; overflow-y: auto; }
  .indoor-map-stage { padding: 16px; }
  .indoor-content-view { margin: 12px; }
  .indoor-stall-grid, .indoor-menu-grid { grid-template-columns: 1fr; padding: 14px; }
  .indoor-menu-grid { grid-auto-rows: 270px; }
  .indoor-menu-card { min-height: 270px; }
  .indoor-menu-card > img { height: 150px; }
  .indoor-guide-header { padding: 18px; }
  .indoor-guide-body {
    display: flex; flex-direction: column; overflow-y: auto; padding: 14px 18px 18px;
  }
  .indoor-guide-stalls { flex: 0 0 auto; max-height: 230px; }
}
</style>
