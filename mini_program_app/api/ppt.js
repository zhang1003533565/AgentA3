import { request } from '@/utils/request.js'
import { BASE_URL, PPT_OPTIONS_BYPASS_CACHE } from '@/utils/config.js'
import { getToken } from '@/utils/storage.js'
import { streamSse } from './ai.js'

const base = '/api/app/ai/ppt'
const PPT_OPTIONS_CACHE_KEY = 'aiPptOptions:v1'
const DEFAULT_OPTIONS_CACHE_TTL = 24 * 60 * 60 * 1000
const PPT_GENERATION_TIMEOUT = 5 * 60 * 1000
let pptOptionsRequest = null

export function getPptOptions({ forceRefresh = false } = {}) {
  const bypassCache = forceRefresh || PPT_OPTIONS_BYPASS_CACHE
  if (PPT_OPTIONS_BYPASS_CACHE) {
    try {
      uni.removeStorageSync(PPT_OPTIONS_CACHE_KEY)
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
    showError: false
  }).then(response => {
    const data = response && typeof response === 'object' && Object.prototype.hasOwnProperty.call(response, 'data')
      ? (response.data || {})
      : (response || {})
    if (!Array.isArray(data.scenes) || !data.scenes.length) throw new Error('PPT 场景配置为空')
    const ttl = Math.max(60000, Number(data.cacheTtlSeconds || 0) * 1000 || DEFAULT_OPTIONS_CACHE_TTL)
    if (!bypassCache) {
      try {
        uni.setStorageSync(PPT_OPTIONS_CACHE_KEY, { data, expiresAt: Date.now() + ttl })
      } catch (error) {}
    }
    return data
  }).finally(() => {
    pptOptionsRequest = null
  })
  return pptOptionsRequest
}

function readPptOptionsCache() {
  try {
    const cached = uni.getStorageSync(PPT_OPTIONS_CACHE_KEY)
    if (!cached || Number(cached.expiresAt || 0) <= Date.now()) return null
    return Array.isArray(cached.data?.scenes) && cached.data.scenes.length ? cached.data : null
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
