<script setup>
/* ═══════════════════════════════════════════════════
   校园导航页 — 高德地图 JS API 2.0 + 纯前端扩展功能
   功能：高德地图/搜索/快捷按钮/聊天助手/暗色模式/
         打卡/留言板/随机漫步/失物招领
   ═══════════════════════════════════════════════════ */
import { ref, reactive, computed, onMounted, onUnmounted, nextTick } from 'vue'
import AppTabBar from '../components/AppTabBar.vue'
import { getMapPlaceDetail, getMapPlaceList } from '../api/map'
import markerCanteen from '../assets/map/marker-canteen.svg'
import markerDormitory from '../assets/map/marker-dormitory.svg'
import markerOther from '../assets/map/marker-other.svg'
import markerSports from '../assets/map/marker-sports.svg'
import markerTeaching from '../assets/map/marker-teaching.svg'

/* ── 高德地图配置 ── */
const AMAP_KEY = 'e1790a70dfc91f2d3daf1895c0e9f87a'
const AMAP_SECURITY = 'f3eda2c1d4c4c76bdb907d570b69d8c'
const MAP_CENTER = [114.897014, 40.755502]
const MAP_ZOOM = 17

const mapPlaces = ref([])
const placeLoading = ref(false)

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
    mapPlaces.value = detailResponses
      .map(toMapPoi)
      .filter(place => Number.isFinite(place.lng) && Number.isFinite(place.lat))
  } catch (error) {
    mapError.value = error.message || '校园点位加载失败'
    mapPlaces.value = []
  } finally {
    placeLoading.value = false
  }
}

const activePoi = ref(null)          /* 当前选中点位 */
const selectedNotice = ref('')       /* 搜索选中提示 */

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
const mapOverlays = []
let infoWindow = null         /* 全局信息窗 */

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
  return root
}

async function selectPoi(poi, marker) {
  try {
    const response = await getMapPlaceDetail(poi.id)
    const detail = response?.data ? toMapPoi(response.data) : poi
    activePoi.value = detail
    if (infoWindow && mapInstance) {
      infoWindow.setContent(createInfoWindowContent(detail))
      infoWindow.open(mapInstance, marker?.getPosition?.() || [detail.lng, detail.lat])
    }
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
        selectPoi(poi, marker)
      })
      fenceOverlay?.on('click', () => {
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
          <input v-model="searchQuery" class="search-input" placeholder="搜索校园地点..."
            @input="searchFocused = true" @focus="searchFocused = true"/>
          <button v-if="searchQuery" class="search-clear" @click="clearSearch">✕</button>
        </div>
        <div v-if="searchFocused && filteredPois.length" class="search-dropdown">
          <div v-for="p in filteredPois" :key="p.id" class="search-item" @click="selectSearchResult(p)">
            <img class="si-icon" :src="sceneMeta(p.sceneType).icon" alt="" />
            <div><div class="si-name">{{ p.name }}</div><div class="si-desc">{{ p.desc }}</div></div>
          </div>
        </div>
      </div>

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

      <!-- ③ 底部快捷按钮 -->
      <div class="quick-bar">
        <button v-for="p in mapPlaces" :key="p.id" class="quick-btn"
          :class="{ active: activePoi?.id === p.id }" @click="flyTo(p)">
          <img class="quick-icon" :src="sceneMeta(p.sceneType).icon" alt="" />
          <span class="quick-label">{{ p.name }}</span>
        </button>
        <span v-if="!placeLoading && !mapPlaces.length" class="quick-empty">暂无已设置位置的校园点位</span>
      </div>

      <!-- 选中点位详情面板 -->
      <Transition name="slide-up">
        <div v-if="activePoi" class="poi-panel">
          <div class="poi-header">
            <span class="poi-name">{{ activePoi.name }}</span>
            <button class="poi-close" @click="activePoi = null">✕</button>
          </div>
          <div class="poi-type">{{ activePoi.tag }}</div>
          <img
            v-if="activePoi.images?.length"
            class="poi-cover"
            :src="activePoi.images[0].imageUrl"
            :alt="activePoi.name"
            :style="{ objectPosition: `${activePoi.images[0].focusX ?? 50}% ${activePoi.images[0].focusY ?? 50}%` }"
          />
          <div class="poi-desc">{{ activePoi.desc }}</div>
          <div v-if="activePoi.locationDesc" class="poi-location">{{ activePoi.locationDesc }}</div>

          <!-- 打卡 -->
          <div class="poi-section">
            <div class="section-label">📍 打卡</div>
            <button v-if="!checkins[activePoi.id]" class="checkin-btn" @click="doCheckin(activePoi)">
              立即打卡
            </button>
            <span v-else class="checkin-done">✅ 已打卡 {{ fmtTime(checkins[activePoi.id].time) }}</span>
          </div>

          <!-- 留言板 -->
          <div class="poi-section">
            <div class="section-label">📝 留言板</div>
            <div class="board-list">
              <div v-for="(m, i) in boardMessages(activePoi.id)" :key="i" class="board-msg">
                <span class="board-author">{{ m.author }}</span>
                <span class="board-text">{{ m.text }}</span>
                <span class="board-time">{{ fmtTime(m.time) }}</span>
              </div>
              <div v-if="!boardMessages(activePoi.id).length" class="board-empty">暂无留言，来抢沙发吧！</div>
            </div>
            <div class="board-form">
              <input v-model="boardAuthor" class="board-author-input" placeholder="昵称" />
              <input v-model="boardInput" class="board-input" placeholder="说点什么..."
                @keydown.enter="postBoard(activePoi)" />
              <button class="board-send" @click="postBoard(activePoi)">发送</button>
            </div>
          </div>
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
.spinner {
  width: 32px; height: 32px;
  border: 3px solid #bfdbfe; border-top-color: var(--primary);
  border-radius: 50%; animation: spin .8s linear infinite;
}
@keyframes spin { to { transform: rotate(360deg); } }

/* ═══ 搜索栏 ═══ */
.search-wrapper {
  position: absolute; top: 16px; left: 50%; transform: translateX(-50%);
  width: min(460px, calc(100% - 120px)); z-index: 12;
}
.search-box {
  display: flex; align-items: center; gap: 8px; height: 44px;
  padding: 0 14px; background: var(--surface); border-radius: 22px;
  box-shadow: 0 4px 20px var(--shadow);
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
</style>
