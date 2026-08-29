/** 二手市集分类展示配置（与后端 SecondhandCategoryInitializer 对齐） */
export const MARKET_CATEGORY_ICONS = {
  all: '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M4 6h7v7H4V6zm9 0h7v7h-7V6zM4 15h7v7H4v-7zm9 0h7v7h-7v-7z" fill="currentColor"/></svg>',
  digital: '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M7 4h10a2 2 0 0 1 2 2v12a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6a2 2 0 0 1 2-2zm0 2v12h10V6H7zm5 9a1.5 1.5 0 1 0 0-3 1.5 1.5 0 0 0 0 3z" fill="currentColor"/></svg>',
  book: '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M5 4h9a3 3 0 0 1 3 3v13H8a3 3 0 0 1-3-3V4zm2 2v11a1 1 0 0 0 1 1h9V7a1 1 0 0 0-1-1H7zm11 1h2a1 1 0 0 1 1 1v12h-3V7z" fill="currentColor"/></svg>',
  clothes: '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 3l3 2 4-1 1 4-3 1v11H7V9L4 8l1-4 4 1 3-2zm0 3.2L10 7H8l.5 2L7 9.5V20h10V9.5l-1.5.5L16 7h-2l-2-.8z" fill="currentColor"/></svg>',
  life: '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 3c-4 3.5-7 6.8-7 10a7 7 0 0 0 14 0c0-3.2-3-6.5-7-10zm0 4.8c2.2 2.1 4 4.1 4 5.2a4 4 0 0 1-8 0c0-1.1 1.8-3.1 4-5.2z" fill="currentColor"/></svg>',
  other: '<svg viewBox="0 0 24 24" aria-hidden="true"><path d="M12 2a10 10 0 1 0 0 20 10 10 0 0 0 0-20zm1 5v4h4v2h-4v4h-2v-4H7v-2h4V7h2z" fill="currentColor"/></svg>',
}

const NAME_ICON_MAP = [
  { match: /数码|电子|手机|电脑|平板|耳机/, icon: 'digital' },
  { match: /书|教材|考研|资料/, icon: 'book' },
  { match: /服饰|鞋|包|衣/, icon: 'clothes' },
  { match: /生活|日用|家居/, icon: 'life' },
]

export function normalizeCategory(raw) {
  const name = raw?.categoryName || raw?.name || '未命名分类'
  const id = raw?.id
  let iconKey = 'other'
  for (const rule of NAME_ICON_MAP) {
    if (rule.match.test(name)) {
      iconKey = rule.icon
      break
    }
  }
  return { id, name, iconKey, icon: MARKET_CATEGORY_ICONS[iconKey] }
}

export function categoryIconByName(name) {
  return normalizeCategory({ categoryName: name }).icon
}

export function conditionLabel(value, text) {
  if (text) return text
  const map = {
    1: '全新',
    2: '几乎全新',
    3: '轻微使用',
    4: '明显使用',
    5: '仅限零件',
    NEW: '全新',
    LIKE_NEW: '几乎全新',
    GOOD: '轻微使用',
    FAIR: '明显使用',
    POOR: '仅限零件',
  }
  return map[value] || map[String(value).toUpperCase()] || '成色待确认'
}

export function statusLabel(item) {
  if (item?.statusText) return item.statusText
  const map = {
    1: '在售',
    2: '已预订',
    3: '已售出',
    0: '已下架',
    ON_SALE: '在售',
    RESERVED: '已预订',
    SOLD: '已售出',
    OFFLINE: '已下架',
  }
  return map[item?.status] || '在售'
}

export function statusTone(item) {
  const label = statusLabel(item)
  if (label.includes('售') && !label.includes('在售')) return 'sold'
  if (label.includes('预订')) return 'reserved'
  if (label.includes('下架')) return 'offline'
  return 'sale'
}
