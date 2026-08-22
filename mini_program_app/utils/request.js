/**
 * 网络请求封装：统一 baseURL、Token、错误与 401 处理
 */
import { getApiBaseUrl } from './config.js'
import { getToken, clearAuth } from './storage.js'

const compactPayload = (payload) => {
  if (payload == null || typeof payload !== 'object' || Array.isArray(payload)) return payload
  const next = {}
  Object.keys(payload).forEach((key) => {
    const value = payload[key]
    if (value === undefined || value === null || value === '') return
    if (value === 'undefined' || value === 'null') return
    next[key] = value
  })
  return next
}

/**
 * @param {Object} options - { url, method, data, header }
 * @returns {Promise<Object> & { abort: (reason?: string) => boolean }}
 * 返回 res.data（接口 body）；仍可直接 await，并可在需要时中止底层 uni.request。
 */
export function request(options) {
  const baseUrl = getApiBaseUrl()
  const url = options.url.startsWith('http') ? options.url : baseUrl + options.url
  const token = getToken()
  const payload = compactPayload(options.data !== undefined ? options.data : options.params)
  const showError = options.showError !== false
  const header = {
    'Content-Type': 'application/json',
    'Cache-Control': 'no-cache',
    Pragma: 'no-cache',
    ...(options.header || {})
  }
  if (token) {
    header.Authorization = `Bearer ${token}`
  }

  let nativeTask = null
  let settled = false
  let aborted = false
  let rejectRequest
  const done = new Promise((resolve, reject) => {
    rejectRequest = reject
    nativeTask = uni.request({
      url,
      method: (options.method || 'GET').toUpperCase(),
      data: payload,
      header,
      ...(options.timeout ? { timeout: options.timeout } : {}),
      success: (res) => {
        if (settled) return
        if (res.statusCode >= 200 && res.statusCode < 300) {
          const data = res.data
          if (typeof data === 'string' && /^\s*</.test(data)) {
            const msg = `API 返回了网页而非 JSON（请确认后端已启动：${baseUrl}）`
            if (showError) {
              uni.showToast({ title: msg, icon: 'none' })
            }
            settled = true
            reject(new Error(msg))
            return
          }
          if (data && typeof data === 'object' && data.code === 200) {
            settled = true
            resolve(data)
          } else {
            if (data && data.code === 401) {
              clearAuth()
            }
            const msg = (data && (data.msg || data.message)) || '请求失败'
            if (showError) {
              uni.showToast({ title: msg, icon: 'none' })
            }
            settled = true
            reject(data || new Error(msg))
          }
        } else {
          if (res.statusCode === 401) {
            clearAuth()
          }
          const msg = (res.data && (res.data.msg || res.data.message)) || `请求失败: ${res.statusCode}`
          if (showError) {
            uni.showToast({ title: msg, icon: 'none' })
          }
          settled = true
          reject(res)
        }
      },
      fail: (err) => {
        if (settled) return
        settled = true
        if (aborted) {
          const abortError = new Error('Request aborted')
          abortError.name = 'AbortError'
          reject(abortError)
          return
        }
        if (showError) {
          uni.showToast({ title: '网络请求失败', icon: 'none' })
        }
        reject(err)
      }
    })
  })
  done.abort = (reason = 'request_aborted') => {
    if (settled || aborted || typeof nativeTask?.abort !== 'function') return false
    aborted = true
    settled = true
    const abortError = new Error(String(reason || 'Request aborted'))
    abortError.name = 'AbortError'
    rejectRequest(abortError)
    try {
      nativeTask.abort()
    } catch (error) {
      // The promise is already rejected as AbortError; native task teardown is best effort.
    }
    return true
  }
  done.done = done
  return done
}
