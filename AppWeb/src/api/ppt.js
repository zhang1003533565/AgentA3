import request from '../utils/request'

const base = '/api/app/ai/ppt'

export const getPptOptions = () => request.get(`${base}/options`, { timeout: 30000 })

export const getPptTask = (taskId) => request.get(`${base}/tasks/${encodeURIComponent(taskId)}`, {
  timeout: 30000,
})

export const buildPptTemplateThumbnailUrl = (thumbnailUrl) => {
  const raw = String(thumbnailUrl || '').trim()
  if (!raw) return ''
  if (raw.startsWith('http://') || raw.startsWith('https://')) return raw
  const token = localStorage.getItem('token') || ''
  const path = raw.startsWith('/') ? raw : `/${raw}`
  return token ? `${path}${path.includes('?') ? '&' : '?'}token=${encodeURIComponent(token)}` : path
}
