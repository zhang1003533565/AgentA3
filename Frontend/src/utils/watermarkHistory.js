const STORAGE_KEY = 'frontend-watermark-history'

function formatTime(date = new Date()) {
  const pad = (value) => String(value).padStart(2, '0')
  const time = `${pad(date.getHours())}:${pad(date.getMinutes())}`
  const now = new Date()
  const isToday = date.toDateString() === now.toDateString()
  const yesterday = new Date(now)
  yesterday.setDate(now.getDate() - 1)
  if (isToday) return `今天 ${time}`
  if (date.toDateString() === yesterday.toDateString()) return `昨天 ${time}`
  return `${date.getMonth() + 1}月${date.getDate()}日 ${time}`
}

export function getWatermarkHistory() {
  try {
    const value = JSON.parse(localStorage.getItem(STORAGE_KEY) || '[]')
    return Array.isArray(value) ? value : []
  } catch {
    return []
  }
}

export function addWatermarkHistory({ title, format, previewUrl, editPath = '/ai-original/add' }) {
  const record = {
    id: `${Date.now()}-${Math.random().toString(36).slice(2, 8)}`,
    title,
    time: formatTime(),
    format,
    previewUrl,
    editPath,
    selected: false,
  }
  const records = [record, ...getWatermarkHistory()].slice(0, 30)
  localStorage.setItem(STORAGE_KEY, JSON.stringify(records))
  return record
}

export function removeWatermarkHistory(ids) {
  const selected = new Set(Array.isArray(ids) ? ids : [ids])
  const records = getWatermarkHistory().filter((record) => !selected.has(record.id))
  localStorage.setItem(STORAGE_KEY, JSON.stringify(records))
  return records
}

export function createHistoryThumbnail(blob, width = 220, height = 140) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => {
      const image = new Image()
      image.onload = () => {
        const canvas = document.createElement('canvas')
        canvas.width = width
        canvas.height = height
        const context = canvas.getContext('2d')
        if (!context) {
          reject(new Error('无法生成历史记录缩略图'))
          return
        }
        context.fillStyle = '#eef2f6'
        context.fillRect(0, 0, width, height)
        const ratio = Math.min(width / image.naturalWidth, height / image.naturalHeight)
        const drawWidth = image.naturalWidth * ratio
        const drawHeight = image.naturalHeight * ratio
        context.drawImage(image, (width - drawWidth) / 2, (height - drawHeight) / 2, drawWidth, drawHeight)
        resolve(canvas.toDataURL('image/jpeg', 0.78))
      }
      image.onerror = () => reject(new Error('无法读取历史记录图片'))
      image.src = reader.result
    }
    reader.onerror = () => reject(new Error('无法保存历史记录图片'))
    reader.readAsDataURL(blob)
  })
}
