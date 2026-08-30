import { getToken } from '../utils/auth'
import { API_BASE_URL, request } from './request'
import { streamSse } from './sse'

const base = '/api/app/ai/ppt'
const PPT_OPTIONS_CACHE_KEY = 'aiPptOptions:v8'
const PPT_OPTIONS_LEGACY_CACHE_KEYS = ['aiPptOptions:v1', 'aiPptOptions:v2', 'aiPptOptions:v3', 'aiPptOptions:v4', 'aiPptOptions:v5', 'aiPptOptions:v6']
const DEFAULT_OPTIONS_CACHE_TTL = 24 * 60 * 60 * 1000
const PPT_GENERATION_TIMEOUT = 5 * 60 * 1000
const PPT_TEMPLATE_THUMBNAIL_CACHE_PREFIX = 'aiPptTemplateThumbnail:v2:'

let pptOptionsRequest = null
const pptTemplateThumbnailCache = Object.create(null)
const pptTemplateThumbnailRequests = Object.create(null)
const ownedResourceUrls = new Set()

function readStorage(key) {
  try {
    const raw = localStorage.getItem(key)
    return raw ? JSON.parse(raw) : null
  } catch {
    return null
  }
}

function writeStorage(key, value) {
  try {
    localStorage.setItem(key, JSON.stringify(value))
  } catch {
    // ignore quota errors
  }
}

function removeStorage(key) {
  localStorage.removeItem(key)
}

function readPptOptionsCache(allowExpired = false) {
  try {
    const cached = readStorage(PPT_OPTIONS_CACHE_KEY)
    if (!cached || (!allowExpired && Number(cached.expiresAt || 0) <= Date.now())) return null
    const templates = cached.data?.templates
    const hasTemplates = Array.isArray(templates) && templates.length
    const hasLayouts = hasTemplates && templates.every((item) => Array.isArray(item?.layouts) && item.layouts.length)
    if (cached.data?.templateCatalogAvailable === false) return cached.data
    return hasTemplates && hasLayouts ? cached.data : null
  } catch {
    return null
  }
}

export function getPptOptions({ forceRefresh = false } = {}) {
  const bypassCache = forceRefresh
  if (!bypassCache) {
    const cached = readPptOptionsCache()
    if (cached) return Promise.resolve(cached)
  }
  if (pptOptionsRequest) return pptOptionsRequest
  pptOptionsRequest = request({
    url: `${base}/options`,
    method: 'GET',
  }).then((response) => {
    const data = response && typeof response === 'object' && Object.prototype.hasOwnProperty.call(response, 'data')
      ? (response.data || {})
      : (response || {})
    if (data.templateCatalogAvailable !== false && (!Array.isArray(data.templates) || !data.templates.length)) {
      throw new Error('PPT 模板配置为空')
    }
    const ttl = Math.max(60000, Number(data.cacheTtlSeconds || 0) * 1000 || DEFAULT_OPTIONS_CACHE_TTL)
    if (!bypassCache) {
      writeStorage(PPT_OPTIONS_CACHE_KEY, { data, expiresAt: Date.now() + ttl })
    }
    return data
  }).catch((error) => {
    const stale = readPptOptionsCache(true)
    if (stale) return stale
    throw error
  }).finally(() => {
    pptOptionsRequest = null
  })
  return pptOptionsRequest
}

export const generatePptOutline = (data) => request({
  url: `${base}/outlines`,
  method: 'POST',
  data,
})

export const generatePptSlides = (data) => request({
  url: `${base}/slides`,
  method: 'POST',
  data,
})

export const createPptOutlineTask = (data) => request({
  url: `${base}/outlines/tasks`,
  method: 'POST',
  data,
})

export const renderPptPreview = (data) => request({
  url: `${base}/previews`,
  method: 'POST',
  data,
})

export const createPptSlidesTask = (data) => request({
  url: `${base}/slides/tasks`,
  method: 'POST',
  data,
})

export const createPptTask = (data) => request({
  url: `${base}/tasks`,
  method: 'POST',
  data,
})

export const getPptTask = (taskId) => request({
  url: `${base}/tasks/${encodeURIComponent(taskId)}`,
  method: 'GET',
})

export const cancelPptTask = (taskId) => request({
  url: `${base}/tasks/${encodeURIComponent(taskId)}/cancel`,
  method: 'POST',
})

export const retryPptTask = (taskId) => request({
  url: `${base}/tasks/${encodeURIComponent(taskId)}/retry`,
  method: 'POST',
})

export function uploadPptSourceFile(filePathOrFile, name = '') {
  const token = getToken()
  if (!token) return Promise.reject(new Error('登录状态已失效'))

  const uploadBlob = async () => {
    let file = filePathOrFile
    if (typeof filePathOrFile === 'string') {
      if (filePathOrFile.startsWith('blob:')) {
        const response = await fetch(filePathOrFile)
        const blob = await response.blob()
        file = new File([blob], name || 'source-file', { type: blob.type || 'application/octet-stream' })
      } else {
        return Promise.reject(new Error('当前环境不支持该文件路径上传'))
      }
    }
    if (!(file instanceof Blob)) {
      return Promise.reject(new Error('未获取到待上传文件'))
    }
    const formData = new FormData()
    formData.append('file', file, name || file.name || 'source-file')
    if (name) formData.append('originalName', name)
    const response = await fetch(`${API_BASE_URL}${base}/files`, {
      method: 'POST',
      headers: token ? { Authorization: `Bearer ${token}` } : {},
      body: formData,
    })
    const payload = await response.json().catch(() => ({}))
    if (!response.ok || Number(payload?.code ?? 200) !== 200) {
      throw new Error(payload?.msg || payload?.message || `PPT 资料上传失败: ${response.status}`)
    }
    return payload?.data || payload || {}
  }

  return uploadBlob()
}

export const streamPptTask = (taskId, handlers = {}) => streamSse(
  `${base}/tasks/${encodeURIComponent(taskId)}/stream`,
  null,
  handlers,
  '当前运行环境无法读取 PPT 生成进度',
  'GET',
)

export function downloadPptTaskFile(taskId, format) {
  return downloadOwnedPptResource(`${base}/tasks/${encodeURIComponent(taskId)}/files/${encodeURIComponent(format)}`)
}

export function downloadPptPreview(taskId, slideIndex) {
  return downloadOwnedPptResource(`${base}/tasks/${encodeURIComponent(taskId)}/previews/${encodeURIComponent(slideIndex)}`)
}

export function replacePptSlideImage(taskId, slideIndex, imageBase64, extension = 'png') {
  return request({
    url: `${base}/tasks/${encodeURIComponent(taskId)}/slides/${encodeURIComponent(slideIndex)}/image`,
    method: 'POST',
    data: { imageBase64, extension },
  })
}

export function downloadPptTemplateThumbnail(templateId) {
  const key = String(templateId || '').trim()
  if (!key) return Promise.reject(new Error('模板编号无效'))
  const cached = readPptTemplateThumbnailCache(key)
  if (cached) return Promise.resolve(cached)
  if (pptTemplateThumbnailRequests[key]) return pptTemplateThumbnailRequests[key]
  const pending = downloadOwnedPptResource(`${base}/templates/${encodeURIComponent(key)}/thumbnail`)
    .then((objectUrl) => persistPptTemplateThumbnail(key, objectUrl))
    .finally(() => {
      delete pptTemplateThumbnailRequests[key]
    })
  pptTemplateThumbnailRequests[key] = pending
  return pending
}

export function clearPptTemplateThumbnailCache(templateId) {
  const key = String(templateId || '').trim()
  if (!key) return
  delete pptTemplateThumbnailCache[key]
  removeStorage(`${PPT_TEMPLATE_THUMBNAIL_CACHE_PREFIX}${encodeURIComponent(key)}`)
}

export function downloadPptLayoutPreview(templateId, slideIndex) {
  return downloadOwnedPptResource(
    `${base}/templates/${encodeURIComponent(templateId)}/layout-previews/${encodeURIComponent(slideIndex)}`,
  )
}

function downloadOwnedPptResource(path) {
  const token = getToken()
  if (!token) return Promise.reject(new Error('登录状态已失效'))
  return fetch(`${API_BASE_URL}${path}`, {
    headers: { Authorization: `Bearer ${token}` },
  }).then(async (response) => {
    if (!response.ok) {
      throw new Error(`文件下载失败: ${response.status}`)
    }
    const blob = await response.blob()
    const objectUrl = URL.createObjectURL(blob)
    ownedResourceUrls.add(objectUrl)
    return objectUrl
  })
}

function readPptTemplateThumbnailCache(templateId) {
  if (pptTemplateThumbnailCache[templateId]) return pptTemplateThumbnailCache[templateId]
  const savedPath = readStorage(`${PPT_TEMPLATE_THUMBNAIL_CACHE_PREFIX}${encodeURIComponent(templateId)}`)
  if (savedPath) {
    pptTemplateThumbnailCache[templateId] = savedPath
    return savedPath
  }
  return ''
}

function persistPptTemplateThumbnail(templateId, objectUrl) {
  pptTemplateThumbnailCache[templateId] = objectUrl
  writeStorage(`${PPT_TEMPLATE_THUMBNAIL_CACHE_PREFIX}${encodeURIComponent(templateId)}`, objectUrl)
  return objectUrl
}

export function revokeOwnedPptResourceUrls() {
  ownedResourceUrls.forEach((url) => URL.revokeObjectURL(url))
  ownedResourceUrls.clear()
}

export { PPT_GENERATION_TIMEOUT }
