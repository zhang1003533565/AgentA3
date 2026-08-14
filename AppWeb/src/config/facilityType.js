/** 本地兜底字典；运行时优先使用 GET /api/v1/facility/types */
export const FACILITY_TYPE_OTHER = 5
export const REMOVED_DEFAULT_FACILITY_TYPE_LABELS = new Set([
  '食堂',
  '球类场地',
  '水上及特殊场地',
  '田径及综合场地',
  '其他',
  '教学楼',
  '宿舍',
])

export const DEFAULT_FACILITY_TYPES = [
  { value: 1, label: '食堂', color: '#3b82f6' },
  { value: 2, label: '球类场地', color: '#14b8a6' },
  { value: 3, label: '水上及特殊场地', color: '#f97316' },
  { value: 4, label: '田径及综合场地', color: '#8b5cf6' },
  { value: FACILITY_TYPE_OTHER, label: '其他', color: '#64748b' },
  { value: 6, label: '教学楼', color: '#3b82f6' },
  { value: 7, label: '宿舍', color: '#14b8a6' },
]

export const FACILITY_TYPE_OPTIONS = toFacilityTypeOptions(DEFAULT_FACILITY_TYPES)

export function toFacilityTypeOptions(types) {
  const list = Array.isArray(types) && types.length ? types : DEFAULT_FACILITY_TYPES
  return list.map(({ value, label, color }) => ({ value, label, color: color || '#3b82f6' }))
}

export function toVisibleFacilityTypeOptions(types) {
  return toFacilityTypeOptions(types).filter((item) => !REMOVED_DEFAULT_FACILITY_TYPE_LABELS.has(item.label))
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
