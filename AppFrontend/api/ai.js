import { request } from '@/utils/request.js'
import { BASE_URL } from '@/utils/config.js'
import { getToken } from '@/utils/storage.js'

export function writeWithAi(data) {
  return request({
    url: '/api/ai/write',
    method: 'POST',
    data
  })
}

export function chatWithAi(data) {
  return request({
    url: '/api/ai/chat',
    method: 'POST',
    data
  })
}

export async function streamChatWithAi(data, handlers = {}) {
  const token = getToken()
  const response = await fetch(`${BASE_URL}/api/ai/chat/stream`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {})
    },
    body: JSON.stringify(data)
  })

  if (!response.ok || !response.body) {
    throw new Error(`流式请求失败: ${response.status}`)
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''

  const emit = (event, payload) => {
    const handler = handlers[event]
    if (typeof handler === 'function') {
      handler(payload)
    }
  }

  while (true) {
    const { value, done } = await reader.read()
    if (done) {
      break
    }
    buffer += decoder.decode(value, { stream: true })
    const frames = buffer.split('\n\n')
    buffer = frames.pop() || ''

    frames.forEach((frame) => {
      const lines = frame.split('\n')
      let eventName = 'message'
      const dataLines = []

      lines.forEach((line) => {
        if (line.startsWith('event:')) {
          eventName = line.slice(6).trim()
        } else if (line.startsWith('data:')) {
          dataLines.push(line.slice(5).trim())
        }
      })

      if (!dataLines.length) {
        return
      }

      const raw = dataLines.join('\n')
      let payload = raw
      try {
        payload = JSON.parse(raw)
      } catch (e) {
        payload = raw
      }
      emit(eventName, payload)
    })
  }
}
