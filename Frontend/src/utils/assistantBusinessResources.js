const BUSINESS_CARD_KINDS = new Set(['activity', 'secondhand', 'course', 'meeting', 'dining', 'facility'])

const FIELD_LABELS = {
  title: '名称',
  name: '名称',
  courseName: '课程',
  category: '分类',
  startTime: '时间',
  endTime: '结束时间',
  location: '地点',
  classroom: '教室',
  status: '状态',
  price: '价格',
  condition: '成色',
  teacherName: '教师',
  weekday: '星期',
  weekText: '周次',
  openingHours: '开放时间',
}

const KIND_LABELS = {
  activity: '校园活动',
  secondhand: '二手物品',
  course: '课程',
  meeting: '会议',
  dining: '餐饮',
  facility: '设施',
}

export function isBusinessCardResource(resource) {
  return String(resource?.deliveryType || '').trim() === 'business_card'
    && BUSINESS_CARD_KINDS.has(String(resource?.kind || '').trim())
}

export function businessCardResources(resources = []) {
  return (Array.isArray(resources) ? resources : []).filter(isBusinessCardResource)
}

export function businessCardKindLabel(kind) {
  return KIND_LABELS[kind] || kind || '数据'
}

export function formatBusinessField(key, value) {
  if (value === null || value === undefined || value === '') return ''
  if (key === 'price') {
    const number = Number(value)
    return Number.isFinite(number) ? `¥${number.toFixed(number % 1 === 0 ? 0 : 2)}` : String(value)
  }
  if (key === 'weekday') {
    const map = { 1: '周一', 2: '周二', 3: '周三', 4: '周四', 5: '周五', 6: '周六', 7: '周日' }
    return map[value] || String(value)
  }
  if (key === 'status') {
    const map = { PUBLISHED: '已发布', published: '已发布', available: '在售', open: '开放' }
    return map[value] || String(value)
  }
  return String(value)
}

export function businessCardDetailRows(resource) {
  const payload = resource?.payload && typeof resource.payload === 'object' ? resource.payload : {}
  const kind = String(resource?.kind || '').trim()
  const fieldOrder = {
    activity: ['title', 'startTime', 'endTime', 'location', 'category', 'status'],
    secondhand: ['title', 'price', 'condition', 'category', 'status'],
    course: ['courseName', 'teacherName', 'weekday', 'classroom', 'weekText'],
    meeting: ['title', 'startTime', 'endTime', 'location', 'status'],
    dining: ['name', 'category', 'location', 'openingHours', 'priceRange'],
    facility: ['name', 'category', 'location', 'openingHours', 'status'],
  }[kind] || ['title', 'name', 'category', 'location']

  const rows = []
  for (const key of fieldOrder) {
    const value = formatBusinessField(key, payload[key])
    if (!value) continue
    rows.push({ key, label: FIELD_LABELS[key] || key, value })
  }
  return rows
}

export function resolveBusinessResourceRoute(resource) {
  const payload = resource?.payload && typeof resource.payload === 'object' ? resource.payload : {}
  const businessId = payload.businessId || payload.id
  if (!businessId) return null
  const id = String(businessId)
  switch (String(resource?.kind || '').trim()) {
    case 'activity':
      return { name: 'activity-detail', params: { activityId: id } }
    case 'secondhand':
      return { path: '/marketplace', query: { itemId: id } }
    case 'course':
      return { name: 'campus-course', params: { courseId: id } }
    case 'meeting':
      return { path: '/meetings', query: { meetingId: id } }
    case 'dining':
      return { path: '/discount', query: { highlight: id } }
    case 'facility':
      return { path: '/map', query: { facilityId: id } }
    default:
      return null
  }
}
