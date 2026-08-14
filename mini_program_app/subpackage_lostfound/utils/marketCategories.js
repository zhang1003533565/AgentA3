export const MARKET_CATEGORIES = [
  {
    id: '1',
    name: '数码产品',
    icon: '/static/icons/cat-digital.svg',
    children: []
  },
  {
    id: '2',
    name: '书籍教材',
    icon: '/static/icons/cat-book.svg',
    children: []
  },
  {
    id: '3',
    name: '服饰鞋包',
    icon: '/static/icons/cat-clothing.svg',
    children: []
  },
  {
    id: '4',
    name: '生活用品',
    icon: '/static/icons/cat-dorm.svg',
    children: []
  },
  {
    id: '5',
    name: '其他',
    icon: '/static/icons/cat-more.svg',
    children: []
  }
]

export function createMarketCategoryOptions() {
  return [
    { key: 'all', label: '全部', icon: '/static/icons/marketplace.svg', children: [] },
    ...MARKET_CATEGORIES.map((category) => ({
      key: category.id,
      label: category.name,
      icon: category.icon,
      children: []
    }))
  ]
}

export function getMarketCategoryChildren(categories = [], categoryId = '') {
  return []
}

export function getMarketCategoryDisplay(item = {}) {
  const level1Id = String(item.categoryLevel1Id || '')
  const categoryId = String(item.categoryId || item.cat || '')

  let parent = MARKET_CATEGORIES.find((category) => category.id === level1Id)

  const primary = parent?.name || item.categoryLevel1Name || ''
  const cardLabel = primary || '闲置'

  return {
    primary,
    secondary: '',
    cardLabel,
    fullLabel: cardLabel || '闲置'
  }
}

export function getMarketCategoryLabel(item = {}, mode = 'card') {
  const display = getMarketCategoryDisplay(item)
  return mode === 'full' ? display.fullLabel : display.cardLabel
}

export function getMarketSubcategoryLabel(categoryId, subcategoryId) {
  return ''
}
