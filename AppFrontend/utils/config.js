/**
 * 移动端全局配置（API 地址等）
 */
const trimTrailingSlash = value => String(value || '').replace(/\/+$/, '')

export function getApiBaseUrl() {
  const injected = import.meta.env?.VITE_API_BASE_URL || ''
  // #ifdef H5
  return trimTrailingSlash(injected || '')
  // #endif
  // #ifndef H5
  return trimTrailingSlash(injected || 'http://127.0.0.1:8080')
  // #endif
}

export const BASE_URL = getApiBaseUrl()
// Keep this list aligned with AppBackend's AI_ASSISTANT_PUBLIC_RESOURCE_HOSTS.
// Empty is intentionally fail-closed: only same-origin /uploads and owned exports remain usable.
export const ASSISTANT_PUBLIC_RESOURCE_HOSTS = []
export const MAP_PROVIDER = 'amap'
