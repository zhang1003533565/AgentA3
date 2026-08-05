// 流程图共享布局工具：生成动画页与结果页共用，保证两屏坐标一致。
// 无泳道时采用"自上而下"布局：level 决定行，同层节点水平居中排布；
// 回流边（target.level <= source.level）走右侧弧形虚线。

export const FLOW_NODE_W = 168
export const FLOW_NODE_H = 56
const GAP_X = 56
const GAP_Y = 64
const TOP = 48

export function computeLevels(nodes, edges) {
  const idSet = new Set(nodes.map(n => String(n.id)))
  const adj = new Map(nodes.map(n => [String(n.id), []]))
  edges.forEach((edge, i) => {
    const s = String(edge.source)
    const t = String(edge.target)
    if (adj.has(s) && idSet.has(t)) adj.get(s).push({ t, i })
  })
  // DFS 标记回流边（指向"正在访问"节点的边）
  const state = new Map()
  const back = new Set()
  const visit = u => {
    state.set(u, 1)
    for (const { t, i } of adj.get(u) || []) {
      const st = state.get(t) || 0
      if (st === 1) back.add(i)
      else if (st === 0) visit(t)
    }
    state.set(u, 2)
  }
  nodes.forEach(n => {
    const id = String(n.id)
    if ((state.get(id) || 0) === 0) visit(id)
  })
  // 仅在无环边上做最长路分层
  const level = new Map(nodes.map(n => [String(n.id), 0]))
  for (let k = 0; k < nodes.length; k += 1) {
    let changed = false
    edges.forEach((edge, i) => {
      if (back.has(i)) return
      const s = String(edge.source)
      const t = String(edge.target)
      const next = (level.get(s) || 0) + 1
      if (next > (level.get(t) || 0)) {
        level.set(t, next)
        changed = true
      }
    })
    if (!changed) break
  }
  return { level, back }
}

// 返回 { nodes:[{...node, cx, cy, level}], edges:[{...edge, key, kind, x1,y1,x2,y2, path, label}], canvasW, canvasH }
export function layoutFlowchart(chart = {}) {
  const nodes = chart.nodes || []
  const edges = chart.edges || []
  const { level, back } = computeLevels(nodes, edges)

  const byLevel = new Map()
  nodes.forEach(node => {
    const l = level.get(String(node.id)) || 0
    if (!byLevel.has(l)) byLevel.set(l, [])
    byLevel.get(l).push(node)
  })

  let maxLevel = 0
  let maxCols = 1
  byLevel.forEach((list, l) => {
    maxLevel = Math.max(maxLevel, l)
    maxCols = Math.max(maxCols, list.length)
  })

  const canvasW = Math.max(380, maxCols * (FLOW_NODE_W + GAP_X) + 60)
  const canvasH = TOP + (maxLevel + 1) * (FLOW_NODE_H + GAP_Y) + 40
  const centerX = canvasW / 2

  const pos = new Map()
  byLevel.forEach((list, l) => {
    const count = list.length
    list.forEach((node, i) => {
      pos.set(String(node.id), {
        x: centerX + (i - (count - 1) / 2) * (FLOW_NODE_W + GAP_X),
        y: TOP + l * (FLOW_NODE_H + GAP_Y)
      })
    })
  })

  const laidNodes = nodes.map(node => {
    const p = pos.get(String(node.id)) || { x: centerX, y: TOP }
    return { ...node, cx: p.x, cy: p.y, level: level.get(String(node.id)) || 0 }
  })
  const nodeMap = new Map(laidNodes.map(node => [String(node.id), node]))

  const laidEdges = edges.map((edge, index) => {
    const s = nodeMap.get(String(edge.source))
    const t = nodeMap.get(String(edge.target))
    if (!s || !t) return null
    const isBack = back.has(index)
    let x1, y1, x2, y2, path
    if (isBack) {
      x1 = s.cx + FLOW_NODE_W / 2
      y1 = s.cy
      x2 = t.cx + FLOW_NODE_W / 2
      y2 = t.cy
      path = `M ${x1} ${y1} C ${x1 + 80} ${y1 - 20}, ${x2 + 80} ${y2 + 20}, ${x2} ${y2}`
    } else {
      x1 = s.cx
      y1 = s.cy + FLOW_NODE_H / 2
      x2 = t.cx
      y2 = t.cy - FLOW_NODE_H / 2
      const my = (y1 + y2) / 2
      path = `M ${x1} ${y1} C ${x1} ${my}, ${x2} ${my}, ${x2} ${y2}`
    }
    return {
      ...edge,
      key: `${edge.source}-${edge.target}-${index}`,
      kind: isBack ? 'back' : 'v',
      x1, y1, x2, y2, path,
      label: edge.label || edge.condition || ''
    }
  }).filter(Boolean)

  return { nodes: laidNodes, edges: laidEdges, canvasW, canvasH }
}

// 墨实顺序：按 level 升序，每个节点先画其引导边再落节点；剩余边（分支/回流）最后画。
export function inkSequence(laid) {
  const seq = []
  const used = new Set()
  const ordered = [...laid.nodes].sort((a, b) => a.level - b.level)
  ordered.forEach(node => {
    const lead = laid.edges.find(e => String(e.target) === String(node.id) && e.kind !== 'back' && !used.has(e.key))
    if (lead) {
      seq.push({ edge: lead })
      used.add(lead.key)
    }
    seq.push({ node })
  })
  laid.edges.forEach(e => {
    if (!used.has(e.key)) seq.push({ edge: e })
  })
  return seq
}
