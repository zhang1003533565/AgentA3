export function radarLabelLayout(text, point, center, size) {
  const fontSize = 12
  const padding = 6
  const estimatedWidth = Array.from(String(text || '')).reduce((width, char) => {
    return width + (/^[\x00-\xff]$/.test(char) ? fontSize * 0.62 : fontSize)
  }, 0)
  let align = 'center'
  let x = point.x

  if (point.x < center - 6) {
    align = 'right'
    x = Math.max(point.x, estimatedWidth + padding)
  } else if (point.x > center + 6) {
    align = 'left'
    x = Math.min(point.x, size - estimatedWidth - padding)
  } else {
    x = Math.max(estimatedWidth / 2 + padding, Math.min(point.x, size - estimatedWidth / 2 - padding))
  }

  return {
    x,
    y: Math.max(fontSize, Math.min(point.y, size - fontSize)),
    align,
    fontSize
  }
}
