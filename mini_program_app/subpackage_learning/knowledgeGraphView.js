export const KNOWLEDGE_STATUS = {
  mastered: { label: '已掌握', tone: 'mastered' },
  learning: { label: '学习中', tone: 'learning' },
  weak: { label: '需巩固', tone: 'weak' },
  available: { label: '可学习', tone: 'available' },
  locked: { label: '待解锁', tone: 'locked' },
  new: { label: '可学习', tone: 'available' }
}

export function graphStatus(value) {
  const key = String(value || 'available').toLowerCase()
  return KNOWLEDGE_STATUS[key] || KNOWLEDGE_STATUS.available
}

export function filterGraphNodes(nodes, keyword = '', status = 'all') {
  const query = String(keyword || '').trim().toLowerCase()
  return (Array.isArray(nodes) ? nodes : []).filter(node => {
    const statusMatched = status === 'all' || graphStatus(node?.status).tone === graphStatus(status).tone
    const text = `${node?.title || ''} ${node?.id || ''} ${node?.group || ''} ${node?.description || ''}`.toLowerCase()
    return statusMatched && (!query || text.includes(query))
  })
}

export function graphLevels(nodes) {
  const groups = new Map()
  for (const node of Array.isArray(nodes) ? nodes : []) {
    const level = Number.isFinite(Number(node?.level)) ? Number(node.level) : 0
    if (!groups.has(level)) groups.set(level, [])
    groups.get(level).push(node)
  }
  return [...groups.entries()]
    .sort(([left], [right]) => left - right)
    .map(([level, items]) => ({
      level,
      label: level === 0 ? '起点' : `第 ${level + 1} 层`,
      nodes: items.sort((left, right) => Number(left?.order || 0) - Number(right?.order || 0))
    }))
}

export function displayGraphScore(value) {
  const number = Number(value)
  if (!Number.isFinite(number)) return 0
  return Math.max(0, Math.min(100, Math.round(number)))
}
