import request from '../utils/request'
import { API_BASE_URL } from '../config/apiBase'

const base = '/api/ai/leader'

export const queryLeaderAgent = (data) => request.post(`${base}/query`, {
  ...(data || {}),
  agentName: 'leader_agent',
  metadata: {
    source: 'web_ai_conversation',
    ...((data && data.metadata) || {}),
  },
}, { timeout: 120000 })

// 与 APP 端使用同一套 SSE 事件协议：session / generation_start / tool_start / search / delta / done / error。
export const streamLeaderAgent = (data, handlers = {}) => {
  const controller = new AbortController()
  const done = (async () => {
    const token = localStorage.getItem('token')
    const response = await fetch(`${API_BASE_URL}${base}/query/stream`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
      body: JSON.stringify({
        ...(data || {}),
        agentName: 'leader_agent',
        metadata: { source: 'web_ai_conversation', ...((data && data.metadata) || {}) },
      }),
      signal: controller.signal,
    })
    if (!response.ok) {
      let detail = `流式请求失败: ${response.status}`
      try {
        const payload = JSON.parse(await response.text())
        detail = payload.msg || payload.message || detail
      } catch { /* 保留状态码错误 */ }
      throw new Error(detail)
    }
    if (!response.body?.getReader) throw new Error('当前环境无法读取流式响应')

    const reader = response.body.getReader()
    const decoder = new TextDecoder('utf-8')
    let buffer = ''
    const flush = (block) => {
      let eventName = 'message'
      const dataLines = []
      block.split(/\r?\n/).forEach((line) => {
        if (line.startsWith('event:')) eventName = line.slice(6).trim() || 'message'
        if (line.startsWith('data:')) dataLines.push(line.slice(5).trim())
      })
      if (!dataLines.length) return
      let payload = dataLines.join('\n')
      try { payload = JSON.parse(payload) } catch { /* 允许非 JSON 事件 */ }
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
        blocks.forEach(flush)
      }
      buffer += decoder.decode()
      if (buffer.trim()) flush(buffer)
    } finally {
      reader.releaseLock()
    }
  })()
  return {
    controller,
    done,
    abort: (reason) => controller.abort(reason),
    then: (...args) => done.then(...args),
    catch: (...args) => done.catch(...args),
    finally: (...args) => done.finally(...args),
  }
}

export const getLeaderSessions = (params = {}) => request.get(`${base}/sessions`, { params })

export const getLeaderSessionDetail = (sessionId) => (
  request.get(`${base}/sessions/${encodeURIComponent(sessionId)}`)
)
