export const MARKET_CATEGORIES = [
  {
    id: '1',
    name: '数码产品',
    icon: '/static/icons/cat-digital.svg',
    children: [
      { id: '101', name: '手机' },
      { id: '102', name: '平板' },
      { id: '103', name: '电脑' },
      { id: '104', name: '耳机' },
      { id: '105', name: '智能设备' },
      { id: '106', name: '配件' },
      { id: '107', name: '摄影设备' }
    ]
  },
  {
    id: '2',
    name: '书籍教材',
    icon: '/static/icons/cat-book.svg',
    children: [
      { id: '201', name: '专业教材' },
      { id: '202', name: '考研资料' },
      { id: '203', name: '学习资料' },
      { id: '204', name: '小说文学' },
      { id: '205', name: '考级考试' }
    ]
  },
  {
    id: '3',
    name: '服饰鞋包',
    icon: '/static/icons/cat-clothing.svg',
    children: [
      { id: '301', name: '男装' },
      { id: '302', name: '女装' },
      { id: '303', name: '鞋靴' },
      { id: '304', name: '包包配饰' },
      { id: '305', name: '运动服饰' }
    ]
  },
  {
    id: '4',
    name: '生活用品',
    icon: '/static/icons/cat-dorm.svg',
    children: [
      { id: '401', name: '宿舍用品' },
      { id: '402', name: '小家电' },
      { id: '403', name: '收纳用品' },
      { id: '404', name: '日用品' },
      { id: '405', name: '家居用品' }
    ]
  },
  {
    id: '5',
    name: '其他',
    icon: '/static/icons/cat-more.svg',
    children: [
      { id: '501', name: '游戏娱乐' },
      { id: '502', name: '文体用品' },
      { id: '503', name: '乐器' },
      { id: '504', name: '礼品闲置' },
      { id: '505', name: '其他' }
    ]
  }
]

export function createMarketCategoryOptions() {
  return [
    { key: 'all', label: '全部', icon: '/static/icons/marketplace.svg', children: [] },
    ...MARKET_CATEGORIES.map((category) => ({
      key: category.id,
      label: category.name,
      icon: category.icon,
      children: category.children.map((child) => ({
        key: child.id,
        label: child.name
      }))
    }))
  ]
}

export function getMarketCategoryChildren(categories = [], categoryId = '') {
  const current = categories.find((category) => String(category.key || category.id) === String(categoryId))
  const children = Array.isArray(current?.children) ? current.children : []
  return children.map((child) => ({
    key: child.key || child.id,
    label: child.label || child.name
  }))
}

export function getMarketCategoryDisplay(item = {}) {
  const level1Id = String(item.categoryLevel1Id || '')
  const level2Id = String(item.categoryLevel2Id || item.categoryId || item.cat || '')
  const categoryId = String(item.categoryId || item.cat || '')

  let parent = MARKET_CATEGORIES.find((category) => category.id === level1Id)
  let child = null

  if (parent) {
    child = parent.children.find((candidate) => candidate.id === level2Id || candidate.id === categoryId) || null
  }

  if (!child) {
    for (const category of MARKET_CATEGORIES) {
      const matched = category.children.find((candidate) => candidate.id === level2Id || candidate.id === categoryId)
      if (matched) {
        parent = category
        child = matched
        break
      }
    }
  }

  const primary = parent?.name || item.categoryLevel1Name || ''
  const secondary = child?.name || item.categoryLevel2Name || item.categoryName || ''
  const cardLabel = secondary && secondary !== primary ? secondary : primary

  return {
    primary,
    secondary,
    cardLabel: cardLabel || '闲置',
    fullLabel: primary && secondary && secondary !== primary ? `${primary} > ${secondary}` : (cardLabel || '闲置')
  }
}

export function getMarketCategoryLabel(item = {}, mode = 'card') {
  const display = getMarketCategoryDisplay(item)
  return mode === 'full' ? display.fullLabel : display.cardLabel
}

export function getMarketSubcategoryLabel(categoryId, subcategoryId) {
  if (!categoryId || !subcategoryId) return ''
  const parent = MARKET_CATEGORIES.find(c => String(c.id) === String(categoryId))
  if (!parent || !Array.isArray(parent.children)) return ''
  const child = parent.children.find(c => String(c.id) === String(subcategoryId))
  return child ? child.name : ''
}
