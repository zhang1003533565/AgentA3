import { request } from '@/utils/request.js'

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
  return request({
    url: '/api/ai/leader/query',
    method: 'POST',
    data
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
