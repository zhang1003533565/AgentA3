export const ACTIVITY_STATUS_MAP = {
  upcoming: { text: '即将开始', class: 'status-upcoming' },
  ongoing: { text: '进行中', class: 'status-ongoing' },
  ended: { text: '已结束', class: 'status-ended' },
  full: { text: '报名已满', class: 'status-full' },
}

export function parseActivityDate(str) {
  if (!str) return new Date(NaN)
  const s = String(str).trim()
  const normalized = s.includes('T') ? s : s.replace(' ', 'T')
  let d = new Date(normalized)
  if (Number.isNaN(d.getTime())) {
    d = new Date(s.replace(' ', 'T').replace(/:00$/, ''))
  }
  return d
}

export function formatActivityDate(dateStr) {
  if (!dateStr) return '待定'
  const date = parseActivityDate(dateStr)
  if (Number.isNaN(date.getTime())) return dateStr
  const month = date.getMonth() + 1
  const day = date.getDate()
  const hour = String(date.getHours()).padStart(2, '0')
  const minute = String(date.getMinutes()).padStart(2, '0')
  return `${month}月${day}日 ${hour}:${minute}`
}

export function getActivityPhase(item) {
  const now = new Date()
  const startTime = parseActivityDate(item?.startTime)
  const endTime = parseActivityDate(item?.endTime)
  if (Number.isNaN(startTime.getTime()) || Number.isNaN(endTime.getTime())) return 'signup'
  if (now < startTime) return 'signup'
  if (now > endTime) return 'ended'
  return 'ongoing'
}

export function getActivityDateBlock(item) {
  const d = parseActivityDate(item?.startTime)
  if (Number.isNaN(d.getTime())) return null
  const days = ['周日', '周一', '周二', '周三', '周四', '周五', '周六']
  return {
    month: `${d.getMonth() + 1}月`,
    day: String(d.getDate()).padStart(2, '0'),
    weekday: days[d.getDay()],
  }
}

export function getActivityStatus(item) {
  const phase = getActivityPhase(item)
  const isFull = (item?.currentPeople || 0) >= (item?.maxPeople || 0)
  if (phase === 'ended') return ACTIVITY_STATUS_MAP.ended
  if (phase === 'ongoing') return ACTIVITY_STATUS_MAP.ongoing
  if (isFull) return ACTIVITY_STATUS_MAP.full
  return ACTIVITY_STATUS_MAP.upcoming
}

export function getActivityRemainingSeats(item) {
  return Math.max(0, (item?.maxPeople || 0) - (item?.currentPeople || 0))
}

export function getActivitySeatEmClass(item) {
  const left = getActivityRemainingSeats(item)
  return { 'seat-low': left > 0 && left <= 10 }
}

export function payloadToActivityItem(payload = {}) {
  if (!payload || typeof payload !== 'object') return null
  const id = payload.businessId || payload.id || payload.activityId
  if (!id) return null
  return {
    id,
    title: payload.title || payload.name || '校园活动',
    startTime: payload.startTime || '',
    endTime: payload.endTime || '',
    location: payload.location || '',
    status: payload.status || '',
    category: payload.category
      ? (typeof payload.category === 'string' ? { categoryName: payload.category } : payload.category)
      : (payload.categoryName ? { categoryName: payload.categoryName } : null),
    coverImage: payload.coverImage || payload.imageUrl || '',
    organizerName: payload.organizerName || '',
    currentPeople: payload.currentPeople,
    maxPeople: payload.maxPeople,
    signupEndTime: payload.signupEndTime || '',
  }
}
