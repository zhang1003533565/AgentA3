import { request } from '@/utils/request'

/** Backend MapPlace.sceneType → legacy numeric facilityType used by map filters */
const SCENE_FACILITY_TYPE = {
  CANTEEN: 1,
  SPORTS: 2,
  TEACHING: 3,
  DORMITORY: 4,
}

/** Backend MapPlace.placeType display labels */
export const PLACE_TYPE_LABELS = {
  CANTEEN: '食堂',
  SPORTS_GROUND: '体育场馆',
  TEACHING_BUILDING: '教学楼',
  DORMITORY_BUILDING: '宿舍楼',
  LANDSCAPE: '基础设施',
  ADMIN_BUILDING: '基础设施',
  HOSPITAL: '基础设施',
  FLOOR: '楼层',
  CANTEEN_STALL: '档口',
  DINING_AREA: '就餐区',
  CLASSROOM: '教室',
  LABORATORY: '实验室',
  OFFICE: '办公室',
  DORMITORY_ROOM: '宿舍房间',
  RUNNING_TRACK: '跑道',
  FOOTBALL_FIELD: '足球场',
  BASKETBALL_COURT: '篮球场',
  VOLLEYBALL_COURT: '排球场',
  BADMINTON_COURT: '羽毛球场',
  LONG_JUMP_AREA: '跳远区',
  SHOT_PUT_AREA: '铅球区',
  PLATFORM: '主席台',
}

const BUILDING_PLACE_TYPES = new Set([
  'TEACHING_BUILDING',
  'CANTEEN',
  'DORMITORY_BUILDING',
])

export function getPlaceTypeLabel(placeType, fallback = '') {
  if (!placeType) return fallback
  return PLACE_TYPE_LABELS[placeType] || fallback || placeType
}

export function isBuildingPlaceType(placeType) {
  return BUILDING_PLACE_TYPES.has(placeType)
}

export function formatFloorTabLabel(name = '', sortOrder = 0) {
  const text = `${name || ''}`.trim()
  if (!text) return sortOrder ? `${sortOrder}F` : '楼层'
  if (/^B\d+$/i.test(text)) return text.toUpperCase()
  const basementDigit = text.match(/(?:地下|负|B)\s*(\d+)/i)
  if (basementDigit) return `B${basementDigit[1]}`
  const cn = ['一', '二', '三', '四', '五', '六', '七', '八', '九', '十']
  const basementCn = cn.findIndex((item) => text.includes(`地下${item}`) || text.includes(`负${item}`))
  if (basementCn >= 0) return `B${basementCn + 1}`
  if (/地下|负一层/.test(text)) return 'B1'
  const numbered = text.match(/(\d+)\s*(?:层|楼|F)?/i)
  if (numbered) return `${Number(numbered[1])}F`
  const cnIndex = cn.findIndex((item) => text.includes(`${item}层`) || text.includes(`${item}楼`))
  if (cnIndex >= 0) return `${cnIndex + 1}F`
  return text
}

const adaptRequest = (task, transform) => {
  const adapted = task.then(transform)
  adapted.abort = (...args) => task.abort?.(...args)
  adapted.done = adapted
  return adapted
}

/** uni.request GET 会把 undefined 序列化成字符串 "undefined"，必须剔除 */
export const compactParams = (params = {}) => {
  const next = {}
  Object.keys(params || {}).forEach((key) => {
    const value = params[key]
    if (value === undefined || value === null || value === '') return
    if (value === 'undefined' || value === 'null') return
    next[key] = value
  })
  return next
}

const collectImageUrls = (place = {}) => {
  const fromImages = (place.images || [])
    .map((item) => (typeof item === 'string' ? item : item?.imageUrl))
    .filter(Boolean)
  if (place.imageUrl) {
    return [place.imageUrl, ...fromImages.filter((url) => url !== place.imageUrl)]
  }
  return fromImages
}

/**
 * Adapt MapPlaceResponse to the shape map.vue already consumes,
 * while keeping backend field names for detail alignment.
 */
export const toMapPlaceMarker = (place) => {
  if (!place || place.id == null) return null
  const facilityType = SCENE_FACILITY_TYPE[place.sceneType] || 99
  const imageUrls = collectImageUrls(place)
  const cover = imageUrls[0] || ''
  const placeTypeName = PLACE_TYPE_LABELS[place.placeType] || place.placeType || ''
  return {
    id: place.id,
    parentId: place.parentId ?? null,
    sceneType: place.sceneType,
    placeType: place.placeType,
    name: place.name,
    description: place.description || '',
    status: place.status,
    longitude: place.longitude,
    latitude: place.latitude,
    locationDesc: place.locationDesc || '',
    mapVisible: place.mapVisible,
    sortOrder: place.sortOrder,
    stallStatus: place.stallStatus,
    stallCount: place.stallCount,
    avgPrice: place.avgPrice,
    businessHours: place.businessHours || '',
    createdAt: place.createdAt || '',
    updatedAt: place.updatedAt || '',
    imageUrl: place.imageUrl || cover,
    images: imageUrls,
    fence: place.fence || null,
    floorPlan: place.floorPlan || null,
    indoorPosition: place.indoorPosition || null,
    children: Array.isArray(place.children) ? place.children.map(toMapPlaceMarker).filter(Boolean) : [],
    facilityId: place.id,
    markerName: place.name,
    facilityName: place.name,
    facilityType,
    facilityTypeName: placeTypeName,
    location: place.locationDesc || '',
    thumbnailUrl: cover,
  }
}

const normalizePlaceList = (payload) => {
  if (Array.isArray(payload)) return payload
  if (Array.isArray(payload?.records)) return payload.records
  if (Array.isArray(payload?.list)) return payload.list
  if (Array.isArray(payload?.data)) return payload.data
  return []
}

export function getFacilityList(params = {}) {
  const task = request({
    url: '/api/v1/map-places',
    method: 'GET',
    params: compactParams({ keyword: params.keyword || params.name }),
  })
  return adaptRequest(task, (response) => {
    const records = normalizePlaceList(response.data)
      .filter((item) => item.parentId == null)
      .map(toMapPlaceMarker)
      .filter(Boolean)
      .filter((item) => params.type == null || Number(params.type) === item.facilityType)
    return { ...response, data: { records, total: records.length, page: 1, size: records.length } }
  })
}

export function getMarkerList(params = {}) {
  const task = request({
    url: '/api/v1/map-places',
    method: 'GET',
    params: compactParams({
      keyword: params.keyword,
      status: params.status || 'ENABLED',
    }),
    showError: params.showError !== false,
  })
  return adaptRequest(task, (response) => {
    const selectedTypes = String(params.facilityTypes || params.facilityType || '')
      .split(',')
      .map(Number)
      .filter(Number.isFinite)
    const records = normalizePlaceList(response.data)
      .filter((item) => item.mapVisible !== false)
      .map(toMapPlaceMarker)
      .filter(Boolean)
      .filter((item) => item.longitude != null && item.latitude != null)
      .filter((item) => !selectedTypes.length || selectedTypes.includes(item.facilityType))
    return { ...response, data: { records, total: records.length, page: 1, size: records.length } }
  })
}

/** GET /api/v1/map-places/{id} — place detail only; floor tree/plans load separately */
export function getMarkerDetail(id, params = {}) {
  const task = request({
    url: `/api/v1/map-places/${id}`,
    method: 'GET',
    params: compactParams({
      includeChildren: params.includeChildren === true ? true : false,
    }),
  })
  return adaptRequest(task, (response) => ({
    ...response,
    data: toMapPlaceMarker(response.data),
  }))
}

export function getPlaceChildren(parentId, params = {}) {
  const task = request({
    url: '/api/v1/map-places',
    method: 'GET',
    params: compactParams({
      parentId,
      status: params.status || 'ENABLED',
      placeType: params.placeType,
    }),
    showError: params.showError !== false,
  })
  return adaptRequest(task, (response) => {
    const records = normalizePlaceList(response.data).map(toMapPlaceMarker).filter(Boolean)
    return { ...response, data: records }
  })
}

/** GET /api/v1/map-places/floors/{floorPlaceId}/plan */
export function getFloorPlan(floorPlaceId) {
  return request({
    url: `/api/v1/map-places/floors/${floorPlaceId}/plan`,
    method: 'GET',
    showError: false,
  })
}

/** GET /api/v1/map-places/floor-plans/{floorPlanId}/positions */
export function getIndoorPositions(floorPlanId) {
  return request({
    url: `/api/v1/map-places/floor-plans/${floorPlanId}/positions`,
    method: 'GET',
    showError: false,
  })
}

/** GET /api/v1/map-places/{id}/fence */
export function getPlaceFence(id) {
  return request({
    url: `/api/v1/map-places/${id}/fence`,
    method: 'GET',
    showError: false,
  })
}

export function searchFacilities(params = {}, options = {}) {
  return request({
    url: '/api/v1/map/search',
    method: 'GET',
    params: compactParams(params),
    showError: options.showError !== false,
  })
}

export function locateFacility(keyword) {
  return request({
    url: '/api/v1/map/locate',
    method: 'GET',
    params: compactParams({ keyword }),
  })
}

export function getNearbyFacilities(params = {}) {
  return request({
    url: '/api/v1/map/nearby',
    method: 'GET',
    params: compactParams(params),
  })
}

export function getNearbyCount(params = {}) {
  return request({
    url: '/api/v1/map/nearby/count',
    method: 'GET',
    params: compactParams(params),
  })
}

export function getNavigationRoute(params = {}) {
  return request({
    url: '/api/v1/map/navigation/route',
    method: 'GET',
    params: compactParams(params),
  })
}

export function startNavigationRecord(data = {}) {
  return request({
    url: '/api/v1/map/navigation',
    method: 'POST',
    data,
  })
}

export function arriveNavigation(navigationId) {
  return request({
    url: `/api/v1/map/navigation/${navigationId}/arrive`,
    method: 'POST',
  })
}

export function cancelNavigation(navigationId) {
  return request({
    url: `/api/v1/map/navigation/${navigationId}/cancel`,
    method: 'POST',
  })
}

export function getNavigationHistory(params = {}) {
  return request({
    url: '/api/v1/map/navigation/history',
    method: 'GET',
    params: compactParams(params),
  })
}

export function reverseGeocode(longitude, latitude) {
  return request({
    url: '/api/v1/map/navigation/reverse-geocode',
    method: 'GET',
    params: compactParams({ longitude, latitude }),
  })
}

export function geocodeAddress(address, region) {
  return request({
    url: '/api/v1/map/navigation/geocode',
    method: 'GET',
    params: compactParams({ address, region }),
  })
}

export function searchPlaces(params = {}, options = {}) {
  return request({
    url: '/api/v1/map/navigation/places/search',
    method: 'GET',
    params: compactParams(params),
    showError: options.showError !== false,
  })
}
