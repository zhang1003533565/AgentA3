/**
 * 移动端全局配置（API 地址等）
 */
const trimTrailingSlash = value => String(value || '').replace(/\/+$/, '')

function detectLocalDevApiBase() {
  try {
    if (typeof location === 'undefined') return ''
    const host = location.hostname || ''
    // 用 127.0.0.1，避免 localhost 走到 IPv6 或命中其它占用 8080 的网页
    if (host === 'localhost' || host === '127.0.0.1' || host === '[::1]') {
      return 'http://127.0.0.1:8080'
    }
    // 局域网访问（手机/其它设备）：前端 192.168.x.x → 同主机 8080
    if (/^(192\.168\.|10\.|172\.(1[6-9]|2\d|3[01])\.)/.test(host)) {
      return `http://${host}:8080`
    }
  } catch (error) {
    return ''
  }
  return ''
}

export function getApiBaseUrl() {
  const injected = import.meta.env?.VITE_API_BASE_URL || ''
  if (injected) return trimTrailingSlash(injected)

  const localDev = detectLocalDevApiBase()
  if (localDev) return localDev

  // #ifdef H5
  return 'http://127.0.0.1:8080'
  // #endif
  // #ifndef H5
  // App/小程序真机不能用 localhost，需指向电脑局域网地址。
  // 正式环境请用 VITE_API_BASE_URL 覆盖。
  // 当前本地联调机局域网地址；正式环境和其他电脑请使用
  // VITE_API_BASE_URL（例如 mini_program_app/.env.development.local）覆盖。
  return 'http://192.168.5.2:8080'
  // #endif
}

export const BASE_URL = getApiBaseUrl()

// PPT 模板配置缓存调试开关：
// true  = 跳过并清除本地缓存，每次进入 PPT 页面都请求后端，方便联调。
// false = 启用后端 TTL 缓存策略，用于线上环境。
export const PPT_OPTIONS_BYPASS_CACHE = false

// Keep this list aligned with AppBackend's AI_ASSISTANT_PUBLIC_RESOURCE_HOSTS.
// Empty is intentionally fail-closed: only same-origin /uploads and owned exports remain usable.
export const ASSISTANT_PUBLIC_RESOURCE_HOSTS = []
export const MAP_PROVIDER = 'amap'
