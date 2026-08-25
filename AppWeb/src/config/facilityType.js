/** 本地兜底字典；运行时优先使用 GET /api/v1/facility/types */
export const FACILITY_TYPE_OTHER = 5

export const DEFAULT_FACILITY_TYPES = [
  { value: 1, label: '食堂' },
  { value: 2, label: '运动场' },
  { value: FACILITY_TYPE_OTHER, label: '其他' },
  { value: 6, label: '教学楼' },
  { value: 7, label: '宿舍' },
]

export const FACILITY_TYPE_OPTIONS = toFacilityTypeOptions(DEFAULT_FACILITY_TYPES)

export function toFacilityTypeOptions(types) {
  const list = Array.isArray(types) && types.length ? types : DEFAULT_FACILITY_TYPES
  const options = []
  const values = new Set()
  list.forEach(({ value, label }) => {
    const numericValue = Number(value)
    const normalizedValue = [2, 3, 4].includes(numericValue)
      ? 2
      : numericValue === 99 ? FACILITY_TYPE_OTHER : value
    if (values.has(String(normalizedValue))) return
    values.add(String(normalizedValue))
    options.push({
      value: normalizedValue,
      label: Number(normalizedValue) === 2
        ? '运动场'
        : Number(normalizedValue) === FACILITY_TYPE_OTHER ? '其他' : label,
    })
  })
  return options
}

export function createFacilityTypeLabelGetter(types) {
  const labelMap = Object.fromEntries(toFacilityTypeOptions(types).map((item) => [String(item.value), item.label]))
  return (type, fallbackName) => {
    if ([2, 3, 4].includes(Number(type))) return labelMap['2'] || '运动场'
    if (Number(type) === 99) return labelMap[String(FACILITY_TYPE_OTHER)] || '其他'
    if (fallbackName) return fallbackName
    if (type === undefined || type === null) return labelMap[String(FACILITY_TYPE_OTHER)] || '其他'
    return labelMap[String(type)] || labelMap[String(FACILITY_TYPE_OTHER)] || '其他'
  }
}

export function getFacilityTypeLabel(type, fallbackName, types = DEFAULT_FACILITY_TYPES) {
  return createFacilityTypeLabelGetter(types)(type, fallbackName)
}
