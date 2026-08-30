/**
 * 地图点位纯函数核心模块。
 *
 * 不携带任何 import 依赖，保证既能被 uniapp 产品代码引用，
 * 也能被 node:test 直接 import 做单元测试（替代曾经的 vm 源码动态执行）。
 */

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
