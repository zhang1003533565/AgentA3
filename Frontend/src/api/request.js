import { clearAuth, getToken } from '../utils/auth'

const trimTrailingSlash = (value) => String(value || '').replace(/\/+$/, '')

export const API_BASE_URL = trimTrailingSlash(
  import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080',
)

const getErrorMessage = (data, fallback = '请求失败') => {
  if (typeof data === 'string') return data
  return data?.msg || data?.message || data?.detail || data?.error || fallback
}

export async function request({ url, method = 'GET', data, params, headers = {} }) {
  const target = url.startsWith('http') ? url : `${API_BASE_URL}${url}`
  const requestUrl = new URL(target)

  if (params && method.toUpperCase() === 'GET') {
    Object.entries(params).forEach(([key, value]) => {
      if (value !== undefined && value !== null && value !== '') {
        requestUrl.searchParams.set(key, value)
      }
    })
  }

  const token = getToken()
  const response = await fetch(requestUrl, {
    method: method.toUpperCase(),
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...headers,
    },
    body: data === undefined ? undefined : JSON.stringify(data),
  })

  const contentType = response.headers.get('content-type') || ''
  const payload = contentType.includes('application/json') ? await response.json() : await response.text()

  if (!response.ok || payload?.code !== 200) {
    if (response.status === 401 || payload?.code === 401) clearAuth()
    throw new Error(getErrorMessage(payload, `请求失败: ${response.status}`))
  }

  return payload
}

