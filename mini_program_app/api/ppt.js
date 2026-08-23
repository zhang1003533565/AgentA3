import { request } from '@/utils/request.js'
import { BASE_URL, PPT_OPTIONS_BYPASS_CACHE } from '@/utils/config.js'
import { getToken } from '@/utils/storage.js'
import { streamSse } from './ai.js'

const base = '/api/app/ai/ppt'
const PPT_OPTIONS_CACHE_KEY = 'aiPptOptions:v8'
const PPT_OPTIONS_LEGACY_CACHE_KEYS = ['aiPptOptions:v1', 'aiPptOptions:v2', 'aiPptOptions:v3', 'aiPptOptions:v4', 'aiPptOptions:v5', 'aiPptOptions:v6']
const DEFAULT_OPTIONS_CACHE_TTL = 24 * 60 * 60 * 1000
const PPT_GENERATION_TIMEOUT = 5 * 60 * 1000
const PPT_TEMPLATE_THUMBNAIL_CACHE_PREFIX = 'aiPptTemplateThumbnail:v2:'
let pptOptionsRequest = null
const pptTemplateThumbnailCache = Object.create(null)
const pptTemplateThumbnailRequests = Object.create(null)

export function getPptOptions({ forceRefresh = false } = {}) {
  const bypassCache = forceRefresh || PPT_OPTIONS_BYPASS_CACHE
  if (PPT_OPTIONS_BYPASS_CACHE) {
    try {
      uni.removeStorageSync(PPT_OPTIONS_CACHE_KEY)
      PPT_OPTIONS_LEGACY_CACHE_KEYS.forEach(key => uni.removeStorageSync(key))
    } catch (error) {}
  }
  if (!bypassCache) {
    const cached = readPptOptionsCache()
    if (cached) return Promise.resolve(cached)
  }
  if (pptOptionsRequest) return pptOptionsRequest
  pptOptionsRequest = request({
    url: `${base}/options`,
    method: 'GET',
    timeout: 15000,
    showError: false
  }).then(response => {
    const data = response && typeof response === 'object' && Object.prototype.hasOwnProperty.call(response, 'data')
      ? (response.data || {})
      : (response || {})
    if (data.templateCatalogAvailable !== false && (!Array.isArray(data.templates) || !data.templates.length)) {
      throw new Error('PPT 模板配置为空')
    }
    const ttl = Math.max(60000, Number(data.cacheTtlSeconds || 0) * 1000 || DEFAULT_OPTIONS_CACHE_TTL)
    if (!bypassCache) {
      try {
        uni.setStorageSync(PPT_OPTIONS_CACHE_KEY, { data, expiresAt: Date.now() + ttl })
      } catch (error) {}
    }
    return data
  }).catch(error => {
    // 模板目录是入口增强能力，不能因为服务短暂抖动阻断整个 PPT 页面。
    // 过期缓存仍然比空模板可靠，后续进入页面时会再次尝试刷新。
    const stale = readPptOptionsCache(true)
    if (stale) return stale
    throw error
  }).finally(() => {
    pptOptionsRequest = null
  })
  return pptOptionsRequest
}

function readPptOptionsCache(allowExpired = false) {
  try {
    const cached = uni.getStorageSync(PPT_OPTIONS_CACHE_KEY)
    if (!cached || (!allowExpired && Number(cached.expiresAt || 0) <= Date.now())) return null
    const templates = cached.data?.templates
    const hasTemplates = Array.isArray(templates) && templates.length
    // layouts 缺失的旧缓存会让模板详情掉进 6 张占位兜底，必须强制刷新
    const hasLayouts = hasTemplates && templates.every(item => Array.isArray(item?.layouts) && item.layouts.length)
    if (cached.data?.templateCatalogAvailable === false) return cached.data
    return hasTemplates && hasLayouts ? cached.data : null
  } catch (error) {
    return null
  }
}

export const generatePptOutline = data => request({
  url: `${base}/outlines`,
  method: 'POST',
  data,
  timeout: PPT_GENERATION_TIMEOUT
})

export const generatePptSlides = data => request({
  url: `${base}/slides`,
  method: 'POST',
  data,
  timeout: PPT_GENERATION_TIMEOUT
})

export const createPptOutlineTask = data => request({
  url: `${base}/outlines/tasks`,
  method: 'POST',
  data,
  timeout: 120000
})

export const renderPptPreview = data => request({
  url: `${base}/previews`,
  method: 'POST',
  data,
  timeout: 120000,
  showError: false
})

export const createPptSlidesTask = data => request({
  url: `${base}/slides/tasks`,
  method: 'POST',
  data,
  timeout: 120000
})

export const createPptTask = data => request({
  url: `${base}/tasks`,
  method: 'POST',
  data,
  timeout: PPT_GENERATION_TIMEOUT
})

export const getPptTask = taskId => request({
  url: `${base}/tasks/${encodeURIComponent(taskId)}`,
  method: 'GET',
  showError: false
})

export const cancelPptTask = taskId => request({
  url: `${base}/tasks/${encodeURIComponent(taskId)}/cancel`,
  method: 'POST',
  showError: false
})

export const retryPptTask = taskId => request({
  url: `${base}/tasks/${encodeURIComponent(taskId)}/retry`,
  method: 'POST'
})

export function uploadPptSourceFile(filePath, name = '') {
  const token = getToken()
  if (!token) return Promise.reject(new Error('登录状态已失效'))
  return new Promise((resolve, reject) => {
    uni.uploadFile({
      url: `${BASE_URL}${base}/files`,
      filePath,
      name: 'file',
      header: { Authorization: `Bearer ${token}` },
      formData: name ? { originalName: name } : {},
      timeout: PPT_GENERATION_TIMEOUT,
      success: response => {
        let payload
        try {
          payload = typeof response.data === 'string' ? JSON.parse(response.data) : response.data
        } catch (error) {
          reject(new Error('PPT 资料上传响应格式无效'))
          return
        }
        if (response.statusCode >= 200 && response.statusCode < 300 && Number(payload?.code ?? 200) === 200) {
          resolve(payload?.data || payload || {})
        } else {
          reject(new Error(payload?.msg || `PPT 资料上传失败: ${response.statusCode || 'unknown'}`))
        }
      },
      fail: reject
    })
  })
}

export const streamPptTask = (taskId, handlers = {}) => streamSse(
  `${base}/tasks/${encodeURIComponent(taskId)}/stream`,
  null,
  handlers,
  '当前运行环境无法读取 PPT 生成进度',
  'GET'
)

export function downloadPptTaskFile(taskId, format) {
  return downloadOwnedPptResource(`${base}/tasks/${encodeURIComponent(taskId)}/files/${encodeURIComponent(format)}`)
}

export function downloadPptPreview(taskId, slideIndex) {
  return downloadOwnedPptResource(`${base}/tasks/${encodeURIComponent(taskId)}/previews/${encodeURIComponent(slideIndex)}`)
}

export function replacePptSlideImage(taskId, slideIndex, imageBase64, extension = 'png') {
  const token = getToken()
  if (!token) return Promise.reject(new Error('未登录'))
  return new Promise((resolve, reject) => {
    uni.request({
      url: `${BASE_URL}${base}/tasks/${encodeURIComponent(taskId)}/slides/${encodeURIComponent(slideIndex)}/image`,
      method: 'POST',
      header: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/json' },
      data: { imageBase64, extension },
      timeout: 120000,
      success: response => {
        const payload = response.data || {}
        if (response.statusCode >= 200 && response.statusCode < 300 && Number(payload?.code ?? 200) === 200) {
          resolve(payload?.data || payload || {})
        } else {
          reject(new Error(payload?.msg || `图片替换失败: ${response.statusCode || 'unknown'}`))
        }
      },
      fail: reject
    })
  })
}

export function downloadPptTemplateThumbnail(templateId) {
  const key = String(templateId || '').trim()
  if (!key) return Promise.reject(new Error('模板编号无效'))
  const cached = readPptTemplateThumbnailCache(key)
  if (cached) return Promise.resolve(cached)
  if (pptTemplateThumbnailRequests[key]) return pptTemplateThumbnailRequests[key]
  const request = downloadOwnedPptResource(`${base}/templates/${encodeURIComponent(key)}/thumbnail`)
    .then(tempFilePath => persistPptTemplateThumbnail(key, tempFilePath))
    .finally(() => {
      delete pptTemplateThumbnailRequests[key]
    })
  pptTemplateThumbnailRequests[key] = request
  return request
}

export function clearPptTemplateThumbnailCache(templateId) {
  const key = String(templateId || '').trim()
  if (!key) return
  delete pptTemplateThumbnailCache[key]
  try {
    uni.removeStorageSync(`${PPT_TEMPLATE_THUMBNAIL_CACHE_PREFIX}${encodeURIComponent(key)}`)
  } catch (error) {}
}

export function downloadPptLayoutPreview(templateId, slideIndex) {
  return downloadOwnedPptResource(
    `${base}/templates/${encodeURIComponent(templateId)}/layout-previews/${encodeURIComponent(slideIndex)}`
  )
}

function downloadOwnedPptResource(path) {
  const token = getToken()
  if (!token) return Promise.reject(new Error('登录状态已失效'))
  return new Promise((resolve, reject) => {
    uni.downloadFile({
      url: `${BASE_URL}${path}`,
      header: { Authorization: `Bearer ${token}` },
      timeout: 120000,
      success: response => {
        if (response.statusCode >= 200 && response.statusCode < 300 && response.tempFilePath) {
          resolve(response.tempFilePath)
        } else {
          reject(new Error(`文件下载失败: ${response.statusCode || 'unknown'}`))
        }
      },
      fail: reject
    })
  })
}

function readPptTemplateThumbnailCache(templateId) {
  if (pptTemplateThumbnailCache[templateId]) return pptTemplateThumbnailCache[templateId]
  try {
    const savedPath = uni.getStorageSync(`${PPT_TEMPLATE_THUMBNAIL_CACHE_PREFIX}${encodeURIComponent(templateId)}`)
    if (savedPath) {
      pptTemplateThumbnailCache[templateId] = savedPath
      return savedPath
    }
  } catch (error) {}
  return ''
}

function persistPptTemplateThumbnail(templateId, tempFilePath) {
  const save = typeof uni.saveFile === 'function'
    ? new Promise(resolve => {
        uni.saveFile({
          tempFilePath,
          success: response => resolve(response?.savedFilePath || tempFilePath),
          fail: () => resolve(tempFilePath)
        })
      })
    : Promise.resolve(tempFilePath)
  return save.then(filePath => {
    pptTemplateThumbnailCache[templateId] = filePath
    try {
      uni.setStorageSync(`${PPT_TEMPLATE_THUMBNAIL_CACHE_PREFIX}${encodeURIComponent(templateId)}`, filePath)
    } catch (error) {}
    return filePath
  })
}
