import { API_BASE_URL, request } from './request'
import { getToken } from '../utils/auth'

const unwrap = (promise) => promise.then((response) => response.data)

export const writeWithAi = (data) => unwrap(request({ url: '/api/ai/write', method: 'POST', data }))
export const getAiWritingModels = () => unwrap(request({ url: '/api/ai/write/models', method: 'GET' }))
export const generateImage = (data) => unwrap(request({ url: '/api/ai/images/generate', method: 'POST', data }))
export const getImageTask = (id) => unwrap(request({ url: `/api/ai/images/tasks/${encodeURIComponent(id)}` }))
export const queryLeaderAgent = (data) => unwrap(request({
  url: '/api/ai/leader/query',
  method: 'POST',
  data: {
    ...data,
    agentName: 'leader_agent',
    metadata: { source: 'web_ai_tools', ...(data.metadata || {}) },
  },
}))

export function streamLeaderAgent(data, handlers = {}) {
  const controller = typeof AbortController === 'function' ? new AbortController() : null
  const done = (async () => {
    if (typeof fetch !== 'function' || !controller) {
      const error = new Error('当前浏览器暂不支持流式对话')
      error.fallbackToNormalRequest = true
      throw error
    }

    const token = getToken()
    const response = await fetch(`${API_BASE_URL}/api/ai/leader/query/stream`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: JSON.stringify({
        ...(data || {}),
        agentName: 'leader_agent',
        metadata: {
          source: 'web_ai_assistant',
          ...((data && data.metadata) || {}),
        },
      }),
      signal: controller.signal,
    })

    if (!response.ok) {
      const payload = await response.json().catch(() => ({}))
      const error = new Error(payload?.msg || payload?.message || `流式请求失败: ${response.status}`)
      error.status = response.status
      throw error
    }
    if (!response.body?.getReader) {
      const error = new Error('当前浏览器无法读取流式响应')
      error.fallbackToNormalRequest = true
      throw error
    }

    const decoder = new TextDecoder('utf-8')
    const reader = response.body.getReader()
    let buffer = ''
    const dispatch = (block) => {
      const lines = block.split(/\r?\n/)
      let eventName = 'message'
      const dataLines = []
      lines.forEach((line) => {
        if (line.startsWith('event:')) {
          eventName = line.slice(6).trim() || 'message'
        } else if (line.startsWith('data:')) {
          dataLines.push(line.slice(5).trim())
        }
      })
      if (!dataLines.length) return

      let payload = dataLines.join('\n')
      try {
        payload = JSON.parse(payload)
      } catch {
        // Text payloads are valid SSE data too.
      }

      if (eventName === 'delta') handlers.onDelta?.(payload?.content || '')
      else if (eventName === 'session') handlers.onSession?.(payload)
      else if (eventName === 'search') handlers.onSearch?.(payload)
      else if (eventName === 'done') handlers.onDone?.(payload)
      else if (eventName === 'error') handlers.onError?.(payload)
      else handlers.onEvent?.(eventName, payload)
    }

    try {
      while (true) {
        const { value, done: streamDone } = await reader.read()
        if (streamDone) break
        buffer += decoder.decode(value, { stream: true })
        const blocks = buffer.split(/\n\s*\n/)
        buffer = blocks.pop() || ''
        blocks.forEach(dispatch)
      }
      buffer += decoder.decode()
      if (buffer.trim()) dispatch(buffer)
    } finally {
      reader.releaseLock()
    }
  })()

  return {
    controller,
    signal: controller?.signal,
    done,
    abort(reason) {
      if (!controller || controller.signal.aborted) return false
      controller.abort(reason)
      return true
    },
    then(onFulfilled, onRejected) {
      return done.then(onFulfilled, onRejected)
    },
    catch(onRejected) {
      return done.catch(onRejected)
    },
    finally(onFinally) {
      return done.finally(onFinally)
    },
  }
}

export const getProfileRadar = () => unwrap(request({ url: '/api/profile/radar/my' }))
