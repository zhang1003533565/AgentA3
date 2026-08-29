import { getToken } from '../utils/auth'
import { API_BASE_URL } from './request'

export function streamSse(url, data, handlers = {}, unsupportedMessage = '当前运行环境暂不支持流式响应', method = 'POST') {
  const controller = typeof AbortController === 'function' ? new AbortController() : null
  const done = (async () => {
    if (typeof fetch !== 'function' || !controller) {
      const error = new Error(unsupportedMessage)
      error.fallbackToNormalRequest = true
      throw error
    }
    const token = getToken()
    const normalizedMethod = String(method || 'POST').toUpperCase()
    const response = await fetch(`${API_BASE_URL}${url}`, {
      method: normalizedMethod,
      headers: {
        ...(normalizedMethod === 'GET' ? {} : { 'Content-Type': 'application/json' }),
        Accept: 'text/event-stream',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      ...(normalizedMethod === 'GET' ? {} : { body: JSON.stringify(data || {}) }),
      signal: controller.signal,
    })
    if (!response.ok) {
      let message = `流式请求失败: ${response.status}`
      try {
        const raw = await response.text()
        if (raw) {
          const parsed = JSON.parse(raw)
          message = parsed.msg || parsed.message || parsed.data?.message || message
        }
      } catch {
        // keep status fallback
      }
      const error = new Error(message)
      error.status = response.status
      error.statusCode = response.status
      throw error
    }
    if (!response.body?.getReader) {
      const error = new Error('当前运行环境无法读取流式响应')
      error.fallbackToNormalRequest = true
      throw error
    }

    const decoder = new TextDecoder('utf-8')
    const reader = response.body.getReader()
    let buffer = ''
    const flushEvent = (block) => {
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
        // plain text payload
      }
      if (eventName === 'delta') {
        handlers.onDelta?.(payload?.content || '')
      } else if (eventName === 'session') {
        handlers.onSession?.(payload)
      } else if (eventName === 'search') {
        handlers.onSearch?.(payload)
      } else if (eventName === 'done') {
        handlers.onDone?.(payload)
      } else if (eventName === 'error') {
        handlers.onError?.(payload)
      } else {
        handlers.onEvent?.(eventName, payload)
      }
    }

    try {
      while (true) {
        const { value, done: streamDone } = await reader.read()
        if (streamDone) break
        buffer += decoder.decode(value, { stream: true })
        const blocks = buffer.split(/\n\s*\n/)
        buffer = blocks.pop() || ''
        blocks.forEach(flushEvent)
      }
      if (buffer.trim()) flushEvent(buffer)
    } finally {
      reader.releaseLock?.()
    }
  })()

  done.abort = (reason) => {
    controller?.abort(reason)
  }
  return done
}
