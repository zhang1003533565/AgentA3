import { getMarketCategoryLabel as getUnifiedMarketCategoryLabel } from '@/subpackage_lostfound/utils/marketCategories.js'

export const MARKET_ITEM_PLACEHOLDERS = ['数码', '教材', '生活', '运动', '服饰', '票务', '好物']

function formatTimestamp(value) {
  if (!value) return ''
  return String(value).replace('T', ' ')
}

export function normalizeSecondhandItem(item = {}) {
  const seller = item.seller || {}
  const categoryId = item.categoryId ?? item.categoryLevel2Id ?? item.categoryLevel1Id ?? 'other'
  const categoryName = item.categoryName || item.categoryLevel2Name || item.categoryLevel1Name || ''
  const condition = item.condition || item.itemCondition || ''
  const location = item.location || item.tradeLocationText || ''
  const status = Number(item.status)

  return {
    id: item.id,
    name: item.title || item.name || '',
    title: item.title || item.name || '',
    desc: item.description || item.desc || '',
    description: item.description || item.desc || '',
    price: item.price,
    originalPrice: item.originalPrice || item.original_price || null,
    type: 'sell',
    status: status === 4 ? 'offline' : status === 3 ? 'sold' : 'online',
    statusText: item.statusText || (status === 3 ? '已售出' : status === 4 ? '已下架' : '在售'),
    rawStatus: item.status,
    cat: String(categoryId),
    images: Array.isArray(item.images) ? item.images : [],
    userId: item.userId,
    userName: seller.username || item.userName || '同学',
    userPhone: seller.phone || '',
    userAva: seller.avatar || '',
    ctime: formatTimestamp(item.createTime || item.ctime),
    categoryId: item.categoryId || categoryId,
    categoryName,
    categoryLevel1Id: item.categoryLevel1Id || item.categoryParentId || item.categoryId || '',
    categoryLevel1Name: item.categoryLevel1Name || item.categoryParentName || item.categoryName || '',
    categoryLevel2Id: item.categoryLevel2Id || item.categoryId || '',
    categoryLevel2Name: item.categoryLevel2Name || item.categoryName || '',
    condition,
    conditionText: item.conditionText || item.conditionName || '',
    location,
    tradeLocation: item.tradeLocation || item.trade_location || location,
    campusId: item.campusId || '',
    campusName: item.campusName || '',
    schoolId: item.schoolId || seller.schoolId || '',
    schoolName: item.schoolName || seller.schoolName || '',
    college: seller.college || item.college || '',
    dormitoryArea: item.dormitoryArea || '',
    allowBargain: Boolean(item.allowBargain ?? item.allow_bargain ?? false),
    deliveryMethod: item.deliveryMethod || item.delivery_method || 'pickup',
    isFree: Boolean(item.isFree ?? Number(item.price) === 0),
    urgency: item.urgency || 'normal',
    viewCount: Number(item.viewCount || item.view_count || 0),
    favoriteCount: Number(item.favoriteCount || item.favorite_count || 0),
    inquiryCount: Number(item.inquiryCount || item.inquiry_count || 0),
    heatScore: Number(item.heatScore || item.heat_score || 0),
    distanceText: item.distanceText || '',
    distanceValue: item.distanceValue || null,
    pickupPoint: item.pickupPoint || item.pickup_point || '',
    attributes: item.attributes || {}
  }
}

export function formatMarketTime(ts) {
  if (!ts) return ''
  const time = typeof ts === 'string' ? new Date(ts.replace(/-/g, '/')).getTime() : ts
  const diff = Date.now() - time
  if (!time) return ''
  if (diff < 60000) return '刚刚'
  if (diff < 3600000) return Math.floor(diff / 60000) + '分钟前'
  if (diff < 86400000) return Math.floor(diff / 3600000) + '小时前'
  const d = new Date(time)
  return `${d.getMonth() + 1}/${d.getDate()}`
}

export function getMarketPriceDisplay(item = {}) {
  const desc = String(item.desc || item.description || '')
  if (desc.includes('免费赠送')) return { prefix: '', text: '免费' }
  if (desc.includes('价格面议')) return { prefix: '', text: '面议' }
  const price = Number(item.price)
  if (!price) return { prefix: '', text: '免费' }
  return { prefix: '¥', text: String(item.price) }
}

export function getMarketConditionLabel(item = {}) {
  if (item.conditionText) return item.conditionText
  const condition = String(item.condition || '')
  if (condition === '1') return '全新'
  if (condition === '2') return '几乎全新'
  if (condition === '3') return '轻微使用'
  if (condition === '4') return '明显使用'
  if (condition === '5') return '仅限零件'
  if (condition === 'new') return '全新'
  if (condition === 'like-new' || condition === 'like_new') return '九成新'
  const text = `${item.name || item.title || ''} ${item.desc || item.description || ''}`
  if (text.includes('全新') || text.includes('未拆')) return '全新'
  if (text.includes('九成新') || text.includes('很少用')) return '九成新'
  return '二手好物'
}

export function getMarketCategoryLabel(item = {}, categories = []) {
  return getUnifiedMarketCategoryLabel(item)
}

export function getMarketLocationLabel(item = {}) {
  const parts = [item.campusName, item.tradeLocation, item.pickupPoint || item.location]
    .filter(Boolean)
  return parts.length ? parts.join(' · ') : '校内自提'
}
