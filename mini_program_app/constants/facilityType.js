/** 设施类型数据字典：新增分类只需在此 push，地图标签与路由自动生效 */
export const FACILITY_TYPE_OTHER = 99

export const FACILITY_TYPES = [
  { value: 1, label: '食堂', mapCategory: 3, typeClass: 'canteen', icon: '', poiEmoji: '', serviceHint: '今日菜单', detailPath: '/subpackage_facility/restaurantDetail/restaurantDetail' },
  { value: 2, label: '体育场馆', mapCategory: 5, typeClass: 'sport', icon: '', poiEmoji: '', secondaryLabel: '场地预约', serviceHint: '在线预约', detailPath: '/subpackage_sports/sportsDetail/sportsDetail' },
  { value: 3, label: '教学楼', mapCategory: 1, typeClass: 'teaching', icon: '', poiEmoji: '', detailPath: '/subpackage_teaching/buildingDetail/buildingDetail' },
  { value: 4, label: '基础设施', mapCategory: 4, typeClass: 'dorm', icon: '', poiEmoji: '', detailPath: '/subpackage_dormitory/dormitoryDetail/dormitoryDetail' },
  { value: 5, label: '基础设施', mapCategory: 4, typeClass: 'shop', icon: '', poiEmoji: '' },
  { value: FACILITY_TYPE_OTHER, label: '基础设施', mapCategory: 4, typeClass: 'admin', icon: '', poiEmoji: '' },
]

const BY_VALUE = Object.fromEntries(FACILITY_TYPES.map((item) => [item.value, item]))
let labelOverrides = null

export const FACILITY_TYPE_OPTIONS = FACILITY_TYPES.map(({ value, label }) => ({ value, label }))

export function applyFacilityTypeLabels(apiTypes) {
  if (!Array.isArray(apiTypes) || !apiTypes.length) {
    labelOverrides = null
    return
  }
  labelOverrides = Object.fromEntries(apiTypes.map((item) => [item.value, item.label]))
}

export function resolveFacilityType(type) {
  const meta = BY_VALUE[type] || BY_VALUE[FACILITY_TYPE_OTHER]
  if (labelOverrides && labelOverrides[type]) {
    return { ...meta, label: labelOverrides[type] }
  }
  return meta
}

export function getFacilityTypeLabel(type, fallbackName) {
  if (fallbackName) return fallbackName
  return resolveFacilityType(type).label
}

export function buildFacilityDetailRoute(type, facilityId) {
  const meta = resolveFacilityType(type)
  if (!meta.detailPath || facilityId == null) return ''
  return `${meta.detailPath}?id=${facilityId}`
}
