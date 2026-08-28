export const DIAGRAM_ANSWER_TYPE_MAP = {
  mind_map_json: 'mind_map',
  flowchart_json: 'flowchart',
  architecture_json: 'architecture',
}

export function extractDiagramFromResponse(response) {
  if (!response || typeof response !== 'object') return null
  const metadata = response.metadata || response.retrievalMeta || {}
  const answerType = String(response.answerType || metadata.answerType || '').trim()
  const diagramType = String(metadata.diagramType || DIAGRAM_ANSWER_TYPE_MAP[answerType] || '').trim()
  const diagram = metadata.diagram
  if (!diagramType || !diagram || typeof diagram !== 'object') return null
  return { type: diagramType, result: diagram }
}

export function nodeLabel(node) {
  return node?.label || node?.name || node?.title || node?.content || '主题'
}

export function toMindTree(nodes, fallbackTitle) {
  if (!Array.isArray(nodes) || !nodes.length) return { label: fallbackTitle, children: [] }
  const first = nodes[0]
  if (Array.isArray(first.children) && first.children.length) {
    return { label: nodeLabel(first), children: first.children }
  }
  const items = nodes.map((node, index) => ({ ...node, id: String(node.id || index), children: [] }))
  const map = new Map(items.map((node) => [node.id, node]))
  const root = items.find((node) => !node.parentId && !node.parent && !node.pid) || items[0]
  items.forEach((node) => {
    const parentId = node.parentId || node.parent || node.pid
    if (parentId && map.has(String(parentId))) map.get(String(parentId)).children.push(node)
  })
  return {
    label: nodeLabel(root),
    children: root.children?.length ? root.children : items.filter((node) => node !== root && !node.parentId),
  }
}
