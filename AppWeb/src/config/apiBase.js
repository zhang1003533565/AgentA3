const LOCAL_API_BASE_URL = 'http://localhost:8080'
// const LOCAL_API_BASE_URL = 'http://120.27.207.149'
export const resolveApiBaseUrl = (env = {}) => {
  const mode = String(env.VITE_API_MODE || 'local').trim().toLowerCase()
  const explicitBaseUrl = String(env.VITE_API_BASE_URL || '').trim()

  if (mode === 'relative' || mode === 'same-origin') {
    return ''
  }

  if (mode === 'remote') {
    return explicitBaseUrl || LOCAL_API_BASE_URL
  }

  return explicitBaseUrl || LOCAL_API_BASE_URL
}

export const API_BASE_URL = resolveApiBaseUrl(import.meta.env)
