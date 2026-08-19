// 流程图共享布局工具：生成动画页与结果页共用，保证两屏坐标一致。
// 无泳道时采用"自上而下"布局：level 决定行，同层节点水平居中排布；
// 回流边（target.level <= source.level）走右侧弧形虚线。

export const FLOW_NODE_W = 168
export const FLOW_NODE_H = 56
const GAP_X = 56
const GAP_Y = 64
const TOP = 48
const LANE_H = 150
const LANE_LEFT = 28
const LANE_W = 230
const BRANCH_EXIT = 38
const BRANCH_LABEL_GAP = 24

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

export function normalizeFlowDirection(value = 'VERTICAL') {
  const text = String(value || 'VERTICAL').toUpperCase()
  if (text.includes('HORIZONTAL') || text.includes('LANDSCAPE') || text.includes('横')) return 'HORIZONTAL'
  return 'VERTICAL'
}

// 返回 { nodes:[{...node, cx, cy, level}], edges:[{...edge, key, kind, x1,y1,x2,y2, path, label}], canvasW, canvasH, direction }
export function layoutFlowchart(chart = {}) {
  const nodes = normalizeNodes(chart.nodes || [])
  const edges = chart.edges || []
  const direction = normalizeFlowDirection(
    chart.resolvedLayoutDirection || chart.requestedLayoutDirection || chart.layoutDirection || chart.direction
  )
  const lanes = normalizeLanes(chart.lanes || [], nodes, chart.resolvedSwimlaneMode)
  if (lanes.length) return layoutSwimlaneFlow(nodes, edges, lanes, direction)
  return layoutPlainFlow(nodes, edges, direction)
}

function layoutPlainFlow(nodes, edges, direction) {
  if (direction === 'HORIZONTAL') return layoutPlainFlowHorizontal(nodes, edges)
  return layoutPlainFlowVertical(nodes, edges)
}

function layoutPlainFlowVertical(nodes, edges) {
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
    return layoutVerticalEdge(edge, index, s, t, back.has(index), `${edge.source}-${edge.target}-${index}`)
  }).filter(Boolean)

  return { nodes: laidNodes, edges: laidEdges, lanes: [], canvasW, canvasH, direction: 'VERTICAL' }
}

function layoutPlainFlowHorizontal(nodes, edges) {
  const { level, back } = computeLevels(nodes, edges)

  const byLevel = new Map()
  nodes.forEach(node => {
    const l = level.get(String(node.id)) || 0
    if (!byLevel.has(l)) byLevel.set(l, [])
    byLevel.get(l).push(node)
  })

  let maxLevel = 0
  let maxRows = 1
  byLevel.forEach((list, l) => {
    maxLevel = Math.max(maxLevel, l)
    maxRows = Math.max(maxRows, list.length)
  })

  const canvasW = TOP + (maxLevel + 1) * (FLOW_NODE_W + GAP_X) + 80
  const canvasH = Math.max(420, maxRows * (FLOW_NODE_H + GAP_Y) + 80)
  const centerY = canvasH / 2

  const pos = new Map()
  byLevel.forEach((list, l) => {
    const count = list.length
    list.forEach((node, i) => {
      pos.set(String(node.id), {
        x: TOP + FLOW_NODE_W / 2 + l * (FLOW_NODE_W + GAP_X),
        y: centerY + (i - (count - 1) / 2) * (FLOW_NODE_H + GAP_Y)
      })
    })
  })

  const laidNodes = nodes.map(node => {
    const p = pos.get(String(node.id)) || { x: TOP + FLOW_NODE_W / 2, y: centerY }
    return { ...node, cx: p.x, cy: p.y, level: level.get(String(node.id)) || 0 }
  })
  const nodeMap = new Map(laidNodes.map(node => [String(node.id), node]))

  const laidEdges = edges.map((edge, index) => {
    const s = nodeMap.get(String(edge.source))
    const t = nodeMap.get(String(edge.target))
    if (!s || !t) return null
    return layoutHorizontalEdge(edge, index, s, t, back.has(index), `${edge.source}-${edge.target}-${index}`)
  }).filter(Boolean)

  return { nodes: laidNodes, edges: laidEdges, lanes: [], canvasW, canvasH, direction: 'HORIZONTAL' }
}

function layoutSwimlaneFlow(nodes, edges, lanes, direction) {
  if (direction === 'VERTICAL') return layoutSwimlaneFlowVertical(nodes, edges, lanes)
  return layoutSwimlaneFlowHorizontal(nodes, edges, lanes)
}

function layoutSwimlaneFlowHorizontal(nodes, edges, lanes) {
  const { level, back } = computeLevels(nodes, edges)
  const laneIndex = new Map(lanes.map((lane, index) => [lane.id, index]))
  const slots = new Map()
  let maxLevel = 0
  nodes.forEach(node => {
    maxLevel = Math.max(maxLevel, level.get(String(node.id)) || 0)
  })

  const canvasW = Math.max(520, LANE_LEFT + (maxLevel + 1) * (FLOW_NODE_W + GAP_X) + 80)
  const canvasH = TOP + lanes.length * LANE_H + 40
  const laneBands = lanes.map((lane, index) => ({
    ...lane,
    x: 14,
    y: TOP - 30 + index * LANE_H,
    w: canvasW - 28,
    h: LANE_H - 14
  }))

  const laidNodes = nodes.map(node => {
    const l = level.get(String(node.id)) || 0
    const laneId = node.laneId || node.lane || lanes[0]?.id || ''
    const lanePos = laneIndex.has(laneId) ? laneIndex.get(laneId) : 0
    const key = `${laneId}:${l}`
    const slot = slots.get(key) || 0
    slots.set(key, slot + 1)
    return {
      ...node,
      laneId,
      cx: LANE_LEFT + 94 + l * (FLOW_NODE_W + GAP_X),
      cy: TOP + 42 + lanePos * LANE_H + slot * (FLOW_NODE_H + 18),
      level: l
    }
  })
  const nodeMap = new Map(laidNodes.map(node => [String(node.id), node]))

  const laidEdges = edges.map((edge, index) => {
    const s = nodeMap.get(String(edge.source))
    const t = nodeMap.get(String(edge.target))
    if (!s || !t) return null
    return layoutHorizontalEdge(edge, index, s, t, back.has(index), edge.id || `${edge.source}-${edge.target}-${index}`)
  }).filter(Boolean)

  return { nodes: laidNodes, edges: laidEdges, lanes: laneBands, canvasW, canvasH, direction: 'HORIZONTAL' }
}

function layoutSwimlaneFlowVertical(nodes, edges, lanes) {
  const { level, back } = computeLevels(nodes, edges)
  const laneIndex = new Map(lanes.map((lane, index) => [lane.id, index]))
  const slotCounts = new Map()
  const slots = new Map()
  let maxLevel = 0
  nodes.forEach(node => {
    const l = level.get(String(node.id)) || 0
    const laneId = node.laneId || node.lane || lanes[0]?.id || ''
    const key = `${laneId}:${l}`
    maxLevel = Math.max(maxLevel, l)
    slotCounts.set(key, (slotCounts.get(key) || 0) + 1)
  })

  const maxSlots = Math.max(1, ...slotCounts.values())
  const laneW = Math.max(LANE_W, maxSlots * (FLOW_NODE_W + 18) + 52)
  const canvasW = Math.max(520, 14 + lanes.length * laneW + 28)
  const canvasH = TOP + (maxLevel + 1) * (FLOW_NODE_H + GAP_Y) + 84
  const laneBands = lanes.map((lane, index) => ({
    ...lane,
    x: 14 + index * laneW,
    y: TOP - 30,
    w: laneW - 14,
    h: canvasH - 28
  }))

  const laidNodes = nodes.map(node => {
    const l = level.get(String(node.id)) || 0
    const laneId = node.laneId || node.lane || lanes[0]?.id || ''
    const lanePos = laneIndex.has(laneId) ? laneIndex.get(laneId) : 0
    const key = `${laneId}:${l}`
    const slot = slots.get(key) || 0
    const count = slotCounts.get(key) || 1
    slots.set(key, slot + 1)
    return {
      ...node,
      laneId,
      cx: 14 + lanePos * laneW + laneW / 2 + (slot - (count - 1) / 2) * (FLOW_NODE_W + 18),
      cy: TOP + 42 + l * (FLOW_NODE_H + GAP_Y),
      level: l
    }
  })
  const nodeMap = new Map(laidNodes.map(node => [String(node.id), node]))

  const laidEdges = edges.map((edge, index) => {
    const s = nodeMap.get(String(edge.source))
    const t = nodeMap.get(String(edge.target))
    if (!s || !t) return null
    return layoutVerticalEdge(edge, index, s, t, back.has(index), edge.id || `${edge.source}-${edge.target}-${index}`)
  }).filter(Boolean)

  return { nodes: laidNodes, edges: laidEdges, lanes: laneBands, canvasW, canvasH, direction: 'VERTICAL' }
}

function layoutVerticalEdge(edge, index, s, t, isBack, key) {
  const branch = isBranchEdge(edge)
  const fromDecision = isDecisionNode(s)
  const dx = t.cx - s.cx
  let x1, y1, x2, y2, path, kind

  if (isBack) {
    x1 = s.cx + FLOW_NODE_W / 2
    y1 = s.cy
    x2 = t.cx + FLOW_NODE_W / 2
    y2 = t.cy
    path = `M ${x1} ${y1} C ${x1 + 80} ${y1 - 20}, ${x2 + 80} ${y2 + 20}, ${x2} ${y2}`
    kind = 'back'
  } else if (branch && fromDecision && Math.abs(dx) > BRANCH_EXIT) {
    const side = Math.sign(dx)
    x1 = s.cx + side * BRANCH_EXIT
    y1 = s.cy + FLOW_NODE_H * 0.18
    x2 = t.cx
    y2 = t.cy - FLOW_NODE_H / 2
    const bendY = s.cy + Math.min(72, Math.max(42, (t.cy - s.cy) * 0.42))
    path = `M ${x1} ${y1} C ${x1} ${bendY}, ${x2} ${bendY}, ${x2} ${y2}`
    kind = 'branch'
  } else {
    x1 = s.cx
    y1 = s.cy + FLOW_NODE_H / 2
    x2 = t.cx
    y2 = t.cy - FLOW_NODE_H / 2
    const my = (y1 + y2) / 2
    path = `M ${x1} ${y1} C ${x1} ${my}, ${x2} ${my}, ${x2} ${y2}`
    kind = branch ? 'branch' : 'v'
  }

  return withEdgeLabel({
    ...edge,
    key,
    kind,
    x1, y1, x2, y2, path
  }, s, t, 'VERTICAL')
}

function layoutHorizontalEdge(edge, index, s, t, isBack, key) {
  const branch = isBranchEdge(edge)
  const fromDecision = isDecisionNode(s)
  const dy = t.cy - s.cy
  let x1, y1, x2, y2, path, kind

  if (isBack) {
    x1 = s.cx
    y1 = s.cy - FLOW_NODE_H / 2
    x2 = t.cx
    y2 = t.cy - FLOW_NODE_H / 2
    const arcY = Math.min(y1, y2) - 70
    path = `M ${x1} ${y1} C ${x1} ${arcY}, ${x2} ${arcY}, ${x2} ${y2}`
    kind = 'back'
  } else if (branch && fromDecision && Math.abs(dy) > BRANCH_EXIT) {
    const side = Math.sign(dy)
    x1 = s.cx + FLOW_NODE_W * 0.22
    y1 = s.cy + side * BRANCH_EXIT
    x2 = t.cx - FLOW_NODE_W / 2
    y2 = t.cy
    const bendX = s.cx + Math.min(82, Math.max(48, (t.cx - s.cx) * 0.42))
    path = `M ${x1} ${y1} C ${bendX} ${y1}, ${bendX} ${y2}, ${x2} ${y2}`
    kind = 'branch'
  } else {
    x1 = s.cx + FLOW_NODE_W / 2
    y1 = s.cy
    x2 = t.cx - FLOW_NODE_W / 2
    y2 = t.cy
    const mx = (x1 + x2) / 2
    path = `M ${x1} ${y1} C ${mx} ${y1}, ${mx} ${y2}, ${x2} ${y2}`
    kind = branch ? 'branch' : 'h'
  }

  return withEdgeLabel({
    ...edge,
    key,
    kind,
    x1, y1, x2, y2, path
  }, s, t, 'HORIZONTAL')
}

function withEdgeLabel(edge, s, t, direction) {
  const label = edge.label || edge.condition || ''
  if (!label) return { ...edge, label: '' }

  const branch = isBranchEdge(edge)
  let labelX = (edge.x1 + edge.x2) / 2
  let labelY = (edge.y1 + edge.y2) / 2

  if (branch && isDecisionNode(s)) {
    if (direction === 'VERTICAL') {
      const dx = t.cx - s.cx
      if (Math.abs(dx) > BRANCH_EXIT) {
        labelX = s.cx + Math.sign(dx) * (BRANCH_EXIT + BRANCH_LABEL_GAP)
        labelY = s.cy + FLOW_NODE_H * 0.42
      } else {
        labelX = s.cx
        labelY = s.cy + FLOW_NODE_H / 2 + BRANCH_LABEL_GAP
      }
    } else {
      const dy = t.cy - s.cy
      if (Math.abs(dy) > BRANCH_EXIT) {
        labelX = s.cx + FLOW_NODE_W * 0.36
        labelY = s.cy + Math.sign(dy) * (BRANCH_EXIT + BRANCH_LABEL_GAP)
      } else {
        labelX = s.cx + FLOW_NODE_W / 2 + BRANCH_LABEL_GAP
        labelY = s.cy
      }
    }
  }

  return { ...edge, label, labelX, labelY }
}

function isBranchEdge(edge = {}) {
  return String(edge.type || '').toLowerCase() === 'branch' || Boolean(edge.label || edge.condition)
}

function isDecisionNode(node = {}) {
  return String(node.type || '').toLowerCase() === 'decision'
}

// 墨实顺序：按 level 升序，每个节点先画其引导边再落节点；剩余边（分支/回流）最后画。
export function inkSequence(laid) {
  const seq = []
  const used = new Set()
  const ordered = [...laid.nodes].sort((a, b) => {
    if (a.level !== b.level) return a.level - b.level
    if (normalizeFlowDirection(laid.direction) === 'HORIZONTAL') return a.cy - b.cy
    return a.cx - b.cx
  })
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

function normalizeNodes(nodes) {
  return nodes.map(node => {
    const rawType = String(node.type || 'process').toLowerCase()
    const type = rawType.includes('start')
      ? 'start'
      : rawType.includes('end')
        ? 'end'
        : rawType.includes('decision') || rawType.includes('judge')
          ? 'decision'
          : 'process'
    const name = node.name || node.label || '流程步骤'
    return {
      ...node,
      id: String(node.id || name),
      type,
      name,
      label: node.label || name,
      laneId: node.laneId || node.lane || ''
    }
  })
}

function normalizeLanes(lanes, nodes, resolvedMode) {
  const mode = String(resolvedMode || '').toUpperCase()
  if (mode === 'NONE') return []
  const list = lanes.map((lane, index) => {
    const label = lane.label || lane.name || lane.id || `泳道${index + 1}`
    return {
      id: lane.id || lane.name || `lane-${index + 1}`,
      label,
      name: lane.name || label,
      type: lane.type || (mode === 'DEPARTMENT' ? 'department' : 'role')
    }
  })
  const seen = new Set(list.map(lane => String(lane.id)))
  nodes.forEach(node => {
    const laneId = node.laneId || node.lane
    if (laneId && !seen.has(String(laneId))) {
      list.push({
        id: laneId,
        label: node.lane || laneId,
        name: node.lane || laneId,
        type: mode === 'DEPARTMENT' ? 'department' : 'role'
      })
      seen.add(String(laneId))
    }
  })
  return list
}
