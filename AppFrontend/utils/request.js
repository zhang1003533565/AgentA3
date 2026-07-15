/**
 * 网络请求封装：统一 baseURL、Token、错误与 401 处理
 */
import { BASE_URL } from './config.js'
import { getToken, clearAuth } from './storage.js'

/**
 * @param {Object} options - { url, method, data, header }
 * @returns {Promise<Object>} 返回 res.data（接口 body）
 */
export function request(options) {
  const url = options.url.startsWith('http') ? options.url : BASE_URL + options.url
  const token = getToken()
  const payload = options.data !== undefined ? options.data : options.params
  const header = {
    'Content-Type': 'application/json',
    ...(options.header || {})
  }
  if (token) {
    header.Authorization = `Bearer ${token}`
  }

  return new Promise((resolve, reject) => {
    uni.request({
      url,
      method: (options.method || 'GET').toUpperCase(),
      data: payload,
      header,
      ...(options.timeout ? { timeout: options.timeout } : {}),
      success: (res) => {
        if (res.statusCode >= 200 && res.statusCode < 300) {
          const data = res.data
          if (data.code === 200) {
            resolve(data)
          } else {
            if (data.code === 401) {
              clearAuth()
            }
            uni.showToast({ title: data.msg || data.message || '请求失败', icon: 'none' })
            reject(data)
          }
        } else {
          if (res.statusCode === 401) {
            clearAuth()
          }
          const msg = (res.data && (res.data.msg || res.data.message)) || `请求失败: ${res.statusCode}`
          uni.showToast({ title: msg, icon: 'none' })
          reject(res)
        }
      },
      fail: (err) => {
        uni.showToast({ title: '网络请求失败', icon: 'none' })
        reject(err)
      }
    })
  })
}
