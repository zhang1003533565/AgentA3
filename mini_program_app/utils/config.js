/**
 * 移动端全局配置（API 地址等）
 */
const trimTrailingSlash = value => String(value || '').replace(/\/+$/, '')

export function getApiBaseUrl() {
  const injected = import.meta.env?.VITE_API_BASE_URL || ''
  // #ifdef H5
  return trimTrailingSlash(injected || 'http://localhost:8080')
  // #endif
  // #ifndef H5
  return trimTrailingSlash(injected || 'http://120.27.207.149')
  // #endif
}

export const BASE_URL = getApiBaseUrl()

// PPT 场景配置缓存调试开关：
// true  = 跳过并清除本地缓存，每次进入 PPT 页面都请求后端，方便联调。
// false = 启用后端 TTL 缓存策略，用于线上环境。
export const PPT_OPTIONS_BYPASS_CACHE = true

// Keep this list aligned with AppBackend's AI_ASSISTANT_PUBLIC_RESOURCE_HOSTS.
// Empty is intentionally fail-closed: only same-origin /uploads and owned exports remain usable.
export const ASSISTANT_PUBLIC_RESOURCE_HOSTS = []
export const MAP_PROVIDER = 'amap'
