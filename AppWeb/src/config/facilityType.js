/** 本地兜底字典；运行时优先使用 GET /api/v1/facility/types */
export const FACILITY_TYPE_OTHER = 99

export const DEFAULT_FACILITY_TYPES = [
  { value: 1, label: '食堂' },
  { value: 2, label: '运动场' },
  { value: 3, label: '教学楼' },
  { value: 4, label: '综合服务' },
  { value: 5, label: '校内商铺' },
  { value: FACILITY_TYPE_OTHER, label: '其他' },
]

export const FACILITY_TYPE_OPTIONS = toFacilityTypeOptions(DEFAULT_FACILITY_TYPES)

export function toFacilityTypeOptions(types) {
  const list = Array.isArray(types) && types.length ? types : DEFAULT_FACILITY_TYPES
  return list.map(({ value, label }) => ({ value, label }))
}

export function createFacilityTypeLabelGetter(types) {
  const labelMap = Object.fromEntries(toFacilityTypeOptions(types).map((item) => [String(item.value), item.label]))
  return (type, fallbackName) => {
    if (fallbackName) return fallbackName
    if (type === undefined || type === null) return labelMap[String(FACILITY_TYPE_OTHER)] || '其他'
    return labelMap[String(type)] || labelMap[String(FACILITY_TYPE_OTHER)] || '其他'
  }
}

export function getFacilityTypeLabel(type, fallbackName, types = DEFAULT_FACILITY_TYPES) {
  return createFacilityTypeLabelGetter(types)(type, fallbackName)
}
