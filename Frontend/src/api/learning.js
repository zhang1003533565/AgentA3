import { getToken } from '../utils/auth'
import { API_BASE_URL, request } from './request'

const unwrap = (promise) => promise.then((response) => response.data)
const encode = (value) => encodeURIComponent(String(value))

export const getPythonHome = () => unwrap(request({
  url: '/api/app/learning/courses/python/home',
}))

export const submitProfileAnswer = (data) => unwrap(request({
  url: '/api/app/learning/courses/python/profile-answers',
  method: 'POST',
  data,
}))

export const getPythonPath = () => unwrap(request({
  url: '/api/app/learning/courses/python/path',
}))

export const getPythonRecommendations = () => unwrap(request({
  url: '/api/app/learning/courses/python/recommendations',
}))

export const getPythonKnowledgeGraph = () => unwrap(request({
  url: '/api/app/learning/courses/python/knowledge-graph',
}))

export const getLearningWorkflow = (workflowId) => unwrap(request({
  url: `/api/app/learning/workflows/${encode(workflowId)}`,
}))

export const retryLearningResource = (workflowId, resourceType) => unwrap(request({
  url: `/api/app/learning/workflows/${encode(workflowId)}/resources/${encode(resourceType)}/retry`,
  method: 'POST',
}))

export const startPathItem = (itemId) => unwrap(request({
  url: `/api/app/learning/path-items/${encode(itemId)}/start`,
  method: 'POST',
}))

export const completePathItem = (itemId) => unwrap(request({
  url: `/api/app/learning/path-items/${encode(itemId)}/complete`,
  method: 'POST',
}))

export const replanPythonPath = () => unwrap(request({
  url: '/api/app/learning/courses/python/path/replan',
  method: 'POST',
}))

export const recordRecommendationInteraction = (itemId, data) => unwrap(request({
  url: `/api/app/learning/recommendations/${encode(itemId)}/interactions`,
  method: 'POST',
  data,
}))

export async function streamLearningResources(data, onEvent) {
  const response = await fetch(`${API_BASE_URL}/api/app/learning/resources/generate/stream`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Accept: 'text/event-stream',
      ...(getToken() ? { Authorization: `Bearer ${getToken()}` } : {}),
    },
    body: JSON.stringify(data),
  })
  if (!response.ok || !response.body) {
    throw new Error(`资源生成请求失败: ${response.status}`)
  }
  const reader = response.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''
  while (true) {
    const { value, done } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    const blocks = buffer.split(/\r?\n\r?\n/)
    buffer = blocks.pop() || ''
    for (const block of blocks) {
      let eventName = 'message'
      const dataLines = []
      block.split(/\r?\n/).forEach((line) => {
        if (line.startsWith('event:')) eventName = line.slice(6).trim()
        if (line.startsWith('data:')) dataLines.push(line.slice(5).trim())
      })
      if (!dataLines.length) continue
      const raw = dataLines.join('\n')
      let payload = raw
      try { payload = JSON.parse(raw) } catch { /* plain SSE payload */ }
      onEvent?.(eventName, payload)
    }
  }
}
