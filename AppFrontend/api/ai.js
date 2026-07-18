import { request } from '@/utils/request.js'
import { BASE_URL } from '@/utils/config.js'
import { getToken } from '@/utils/storage.js'
import { buildAssistantDownloadOptions } from '../subpackage_ai/assistantMessage.js'

const ASSISTANT_RESOURCE_INTERACTIONS = new Set([
  'view', 'open', 'download', 'preview', 'follow_up', 'dismiss', 'complete'
])

export function writeWithAi(data) {
  return request({
    url: '/api/ai/write',
    method: 'POST',
    data
  })
}

export function generateImage(data) {
  return request({
    url: '/api/ai/images/generate',
    method: 'POST',
    data,
    timeout: 180000
  })
}

export function generateImagesBatch(data) {
  return request({
    url: '/api/ai/images/batch',
    method: 'POST',
    data,
    timeout: 240000
  })
}

export function getImageTask(taskId) {
  return request({
    url: `/api/ai/images/tasks/${encodeURIComponent(taskId)}`,
    method: 'GET',
    timeout: 60000
  })
}

export function queryLeaderAgent(data) {
  const payload = {
    ...(data || {}),
    agentName: 'leader_agent',
    metadata: {
      source: 'app_ai_conversation',
      ...((data && data.metadata) || {})
    }
  }
  return request({
    url: '/api/ai/leader/query',
    method: 'POST',
    data: payload,
    timeout: 120000
  })
}

export async function streamLeaderAgent(data, handlers = {}) {
  return streamSse('/api/ai/leader/query/stream', {
    ...(data || {}),
    agentName: 'leader_agent',
    metadata: {
      source: 'app_ai_conversation',
      ...((data && data.metadata) || {})
    }
  }, handlers, '当前运行环境暂不支持流式对话')
}

export function getProfileRadarSnapshot() {
  return request({
    url: '/api/profile/radar/my',
    method: 'GET'
  })
}

export function submitProfileEvidence(data) {
  return request({
    url: '/api/profile/evidence',
    method: 'POST',
    data
  })
}

export function submitAssistantResourceInteraction(sessionId, messageId, resourceId, action) {
  action = String(action || '').trim().toLowerCase()
  if (!String(sessionId || '').trim() || !String(messageId || '').trim()
    || !String(resourceId || '').trim() || !ASSISTANT_RESOURCE_INTERACTIONS.has(action)) {
    return Promise.reject(new Error('资源互动参数无效'))
  }
  return request({
    url: `/api/ai/leader/sessions/${encodeURIComponent(sessionId)}/messages/${encodeURIComponent(messageId)}/resources/${encodeURIComponent(resourceId)}/interactions`,
    method: 'POST',
    data: { action }
  })
}

export function downloadAssistantResource(resourceOrUrl, options = {}) {
  const resource = typeof resourceOrUrl === 'string'
    ? { url: resourceOrUrl, authScope: resourceOrUrl.trim().startsWith('/api/') ? 'session_owner' : 'public' }
    : (resourceOrUrl || {})
  const token = getToken()
  const downloadOptions = buildAssistantDownloadOptions(resource, {
    baseUrl: BASE_URL,
    token,
    approvedHosts: options.approvedHosts || []
  })
  if (!downloadOptions) {
    return Promise.reject(new Error('资源地址无效或登录状态已失效'))
  }
  return new Promise((resolve, reject) => {
    uni.downloadFile({
      url: downloadOptions.url,
      header: downloadOptions.header,
      ...(options.timeout ? { timeout: options.timeout } : {}),
      success: (response) => {
        if (response.statusCode >= 200 && response.statusCode < 300 && response.tempFilePath) {
          resolve(response.tempFilePath)
          return
        }
        reject(new Error(`资源下载失败: ${response.statusCode || 'unknown'}`))
      },
      fail: reject
    })
  })
}

export function queryMeetingAgent(data = {}) {
  const payload = { ...data }
  const targetSessionId = payload.sessionId || payload.meetingSessionId
  const nextContent = payload.content !== undefined ? payload.content : payload.input
  if (!targetSessionId) {
    return Promise.reject(new Error('会议ID不能为空'))
  }
  delete payload.sessionId
  delete payload.meetingSessionId
  delete payload.input
  delete payload.prompt
  return runMeetingAgent(targetSessionId, {
    ...payload,
    content: nextContent
  })
}

export function createMeeting(data) {
  return request({
    url: '/api/meetings',
    method: 'POST',
    data
  })
}

export function createQuickMeeting(data) {
  return request({
    url: '/api/meetings/quick',
    method: 'POST',
    data
  })
}

export function reserveMeeting(data) {
  return request({
    url: '/api/meetings/reservations',
    method: 'POST',
    data
  })
}

export function updateMeeting(sessionId, data) {
  return request({
    url: `/api/meetings/${encodeURIComponent(sessionId)}`,
    method: 'PUT',
    data
  })
}

export function getMeetingDetail(sessionId) {
  return request({
    url: `/api/meetings/${encodeURIComponent(sessionId)}`,
    method: 'GET'
  })
}

export function getMeetings(params = {}) {
  return request({
    url: '/api/meetings',
    method: 'GET',
    params
  })
}

export function joinMeeting(data) {
  return request({
    url: '/api/meetings/join',
    method: 'POST',
    data
  })
}

export function startMeeting(sessionId) {
  return request({
    url: `/api/meetings/${encodeURIComponent(sessionId)}/start`,
    method: 'POST'
  })
}

export function endMeeting(sessionId) {
  return request({
    url: `/api/meetings/${encodeURIComponent(sessionId)}/end`,
    method: 'POST'
  })
}

export function organizeMeeting(sessionId) {
  return request({
    url: `/api/meetings/${encodeURIComponent(sessionId)}/organize`,
    method: 'POST',
    timeout: 180000
  })
}

export function deleteMeeting(sessionId) {
  return request({
    url: `/api/meetings/${encodeURIComponent(sessionId)}`,
    method: 'DELETE'
  })
}

export function saveMeetingRecord(sessionId, data) {
  return request({
    url: `/api/meetings/${encodeURIComponent(sessionId)}/records`,
    method: 'POST',
    data
  })
}

export function runMeetingAgent(sessionId, data) {
  return request({
    url: `/api/meetings/${encodeURIComponent(sessionId)}/agents/run`,
    method: 'POST',
    data,
    timeout: 120000
  })
}

export function previewMeetingAgent(sessionId, data) {
  return request({
    url: `/api/meetings/${encodeURIComponent(sessionId)}/agents/preview`,
    method: 'POST',
    data,
    timeout: 120000
  })
}

export async function streamLlmChat(data, handlers = {}) {
  return streamSse('/api/llm/chat/stream', data, handlers, '当前运行环境暂不支持流式总结')
}

export async function streamSse(url, data, handlers = {}, unsupportedMessage = '当前运行环境暂不支持流式响应') {
  if (typeof fetch !== 'function') {
    const error = new Error(unsupportedMessage)
    error.fallbackToNormalRequest = true
    throw error
  }
  const token = getToken()
  const controller = new AbortController()
  const response = await fetch(`${BASE_URL}${url}`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {})
    },
    body: JSON.stringify(data || {}),
    signal: controller.signal
  })
  if (!response.ok) {
    const error = new Error(`流式请求失败: ${response.status}`)
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
    lines.forEach(line => {
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
    } catch (error) {}
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
      const { value, done } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      const blocks = buffer.split(/\n\s*\n/)
      buffer = blocks.pop() || ''
      blocks.forEach(flushEvent)
    }
    buffer += decoder.decode()
    if (buffer.trim()) flushEvent(buffer)
  } finally {
    reader.releaseLock()
  }

  return controller
}

export function getLeaderSessions(params = {}) {
  return request({
    url: '/api/ai/leader/sessions',
    method: 'GET',
    params
  })
}

export function getLeaderSessionDetail(sessionId) {
  return request({
    url: `/api/ai/leader/sessions/${encodeURIComponent(sessionId)}`,
    method: 'GET'
  })
}
