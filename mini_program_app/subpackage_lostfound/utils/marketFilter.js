export const DEFAULT_MARKET_FILTER = {
  keyword: '',
  categoryLevel1Id: 'all',
  categoryLevel2Id: '',
  priceRange: 'all',
  condition: 'all',
  tradeLocation: 'all',
  publishTime: 'all',
  attributes: {},
  sortBy: 'latest'
}

const CONDITION_ALIASES = {
  '1': ['1', 'new', '全新', '未使用'],
  '2': ['2', 'like_new', 'like-new', '很新', '几乎全新', '九成新', '很少用', '使用很少'],
  '3': ['3', 'used', 'good', 'normal', '正常使用', '轻微使用', '轻微使用痕迹', '二手'],
  '4': ['4', 'worn', 'obvious', '明显使用', '较多使用痕迹', '有瑕疵'],
  '5': ['5', 'parts', '配件', '零件', '仅限零件'],
  new: ['1', 'new', '全新', '未使用'],
  'like-new': ['2', 'like_new', 'like-new', '很新', '几乎全新', '九成新', '很少用', '使用很少'],
  used: ['3', '4', 'used', 'good', 'normal', '二手', '正常使用', '明显使用', '有瑕疵']
}

const LOCATION_ALIASES = {
  campus: ['campus', '校内', '校内自提', 'library', 'teaching', 'gate'],
  dorm: ['dorm', '宿舍', '宿舍区'],
  nearby: ['nearby', '附近', '校门口', 'gate']
}

export function createDefaultMarketFilter(overrides = {}) {
  return {
    ...DEFAULT_MARKET_FILTER,
    ...overrides,
    attributes: {
      ...DEFAULT_MARKET_FILTER.attributes,
      ...(overrides.attributes || {})
    }
  }
}

export function normalizeMarketFilter(filter = {}) {
  return createDefaultMarketFilter(filter)
}

function includesAny(value, candidates) {
  const text = String(value || '').toLowerCase()
  return candidates.some((item) => text.includes(String(item).toLowerCase()))
}

function matchKeyword(item, keyword) {
  const value = String(keyword || '').trim().toLowerCase()
  if (!value) return true

  const fields = [item.name, item.title, item.desc, item.description, item.categoryName]
    .filter(Boolean)

  // 1. 连续完整匹配（最高优先级）
  const continuousMatch = fields.some((field) =>
    String(field).toLowerCase().includes(value)
  )
  if (continuousMatch) return true

  // 2. 中文非连续字符匹配（补充）
  //    将关键词拆分为字符序列，判断目标文本是否按顺序包含这些字符
  const chars = [...value]
  if (chars.length <= 1) return false

  return fields.some((field) => {
    const text = String(field).toLowerCase()
    let pos = 0
    for (let i = 0; i < text.length && pos < chars.length; i++) {
      if (text[i] === chars[pos]) pos++
    }
    return pos === chars.length
  })
}

function matchCategory(item, filter) {
  const level1 = String(filter.categoryLevel1Id || 'all')
  const level2 = String(filter.categoryLevel2Id || '')
  const itemLevel1 = String(item.categoryLevel1Id || item.cat || item.categoryId || '')
  const itemLevel2 = String(item.categoryLevel2Id || item.categoryId || item.cat || '')

  if (level1 && level1 !== 'all' && itemLevel1 !== level1 && String(item.cat || '') !== level1) {
    return false
  }
  if (level2 && itemLevel2 !== level2 && String(item.cat || '') !== level2) {
    return false
  }
  return true
}

function matchPrice(item, priceRange) {
  if (!priceRange || priceRange === 'all') return true
  const price = Number(item.price) || 0
  if (priceRange === '0-50') return price <= 50
  if (priceRange === '50-200') return price >= 50 && price <= 200
  if (priceRange === '200+') return price >= 200
  if (priceRange.includes('-')) {
    const [min, max] = priceRange.split('-').map((value) => Number(value))
    if (!Number.isNaN(min) && price < min) return false
    if (!Number.isNaN(max) && price > max) return false
  }
  return true
}

function matchCondition(item, condition) {
  if (!condition || condition === 'all') return true
  const aliases = CONDITION_ALIASES[condition] || [condition]
  return includesAny(item.condition, aliases) || includesAny(item.conditionText, aliases)
}

function matchTradeLocation(item, tradeLocation) {
  if (!tradeLocation || tradeLocation === 'all') return true
  const aliases = LOCATION_ALIASES[tradeLocation] || [tradeLocation]
  return includesAny(item.tradeLocation, aliases)
    || includesAny(item.location, aliases)
    || includesAny(item.campusName, aliases)
    || includesAny(item.pickupPoint, aliases)
}

function matchPublishTime(item, publishTime) {
  if (!publishTime || publishTime === 'all') return true
  const itemTime = item.ctime ? new Date(String(item.ctime).replace(/-/g, '/')).getTime() : 0
  if (!itemTime) return false
  const diff = Date.now() - itemTime
  const dayMs = 86400000
  if (publishTime === 'today') return diff <= dayMs
  if (publishTime === '3days') return diff <= 3 * dayMs
  if (publishTime === 'week') return diff <= 7 * dayMs
  return true
}

function matchAttributes(item, attributes = {}) {
  const itemAttributes = item.attributes || {}
  return Object.keys(attributes).every((key) => {
    const expected = attributes[key]
    if (expected === undefined || expected === null || expected === '' || expected === 'all') return true
    const actual = itemAttributes[key]
    if (Array.isArray(expected)) {
      if (!expected.length) return true
      return expected.some((value) => String(actual) === String(value))
    }
    return String(actual) === String(expected)
  })
}

function sortMarketItems(list, sortBy) {
  if (sortBy === 'hot') {
    return [...list].sort((a, b) => {
      const bScore = Number(b.heatScore || b.heat_score || b.viewCount || 0)
      const aScore = Number(a.heatScore || a.heat_score || a.viewCount || 0)
      return bScore - aScore
    })
  }
  return list
}

export function filterMarketItems(items = [], filter = {}) {
  const normalized = normalizeMarketFilter(filter)
  const list = items.filter((item) => {
    if (item.status && ['sold', 'offline'].includes(String(item.status))) return false
    return matchKeyword(item, normalized.keyword)
      && matchCategory(item, normalized)
      && matchPrice(item, normalized.priceRange)
      && matchCondition(item, normalized.condition)
      && matchTradeLocation(item, normalized.tradeLocation)
      && matchPublishTime(item, normalized.publishTime)
      && matchAttributes(item, normalized.attributes)
  })
  return sortMarketItems(list, normalized.sortBy)
}
