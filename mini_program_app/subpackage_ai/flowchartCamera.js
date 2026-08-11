function normalizeDirection(value = 'VERTICAL') {
  return String(value || 'VERTICAL').toUpperCase() === 'HORIZONTAL' ? 'HORIZONTAL' : 'VERTICAL'
}

function toNumber(value, fallback = 0) {
  const next = Number(value)
  return Number.isFinite(next) ? next : fallback
}

function centerContentOffsetX(viewW, canvasW, contentW, scale) {
  const canvasLeft = viewW / 2 - canvasW / 2
  return Math.round((viewW - contentW * scale) / 2 - canvasLeft)
}

function clamp(value, min, max) {
  return Math.min(max, Math.max(min, value))
}

function clampVerticalSwimlaneX(offsetX, viewW, canvasW, contentW, scale) {
  const scaledW = contentW * scale
  if (scaledW <= viewW) return centerContentOffsetX(viewW, canvasW, contentW, scale)

  const margin = 32
  const canvasLeft = viewW / 2 - canvasW / 2
  const min = Math.round(viewW - margin - canvasLeft - scaledW)
  const max = Math.round(margin - canvasLeft)
  return clamp(offsetX, min, max)
}

export function shouldFollowCameraX(direction, laneCount = 0) {
  const normalized = normalizeDirection(direction)
  return normalized === 'HORIZONTAL' || (normalized === 'VERTICAL' && Number(laneCount || 0) > 0)
}

export function flowCameraFollowKey(node = {}, direction = 'VERTICAL', laneCount = 0) {
  const normalized = normalizeDirection(direction)
  const level = node.level ?? 0
  if (normalized === 'HORIZONTAL') return `x:${level}`
  if (!shouldFollowCameraX(normalized, laneCount)) return `y:${level}`

  const laneId = node.laneId || node.lane || ''
  const cx = Math.round(toNumber(node.cx, 0))
  return `xy:${level}:${laneId}:${cx}`
}

export function targetCameraXForNode({
  node = {},
  direction = 'VERTICAL',
  laneCount = 0,
  viewW = 0,
  canvasW = 0,
  contentW = 0,
  scale = 1
} = {}) {
  const normalized = normalizeDirection(direction)
  const actualViewW = toNumber(viewW, 0)
  const actualCanvasW = toNumber(canvasW, actualViewW)
  const actualContentW = toNumber(contentW, actualCanvasW)
  const actualScale = Math.max(0.1, toNumber(scale, 1))

  if (!shouldFollowCameraX(normalized, laneCount)) {
    return centerContentOffsetX(actualViewW, actualCanvasW, actualContentW, actualScale)
  }

  const targetX = normalized === 'HORIZONTAL' ? actualViewW * 0.42 : actualViewW * 0.5
  const canvasLeft = actualViewW / 2 - actualCanvasW / 2
  const nodeX = toNumber(node.cx, actualContentW / 2)
  const nextX = Math.round(targetX - canvasLeft - nodeX * actualScale)

  if (normalized === 'HORIZONTAL') return nextX
  return clampVerticalSwimlaneX(nextX, actualViewW, actualCanvasW, actualContentW, actualScale)
}
