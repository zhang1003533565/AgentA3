import { request } from '@/utils/request.js'

export function writeWithAi(data) {
  return request({
    url: '/api/ai/write',
    method: 'POST',
    data
  })
}

export function queryLeaderAgent(data) {
  return request({
    url: '/api/ai/leader/query',
    method: 'POST',
    data
  })
}

export function queryMeetingAgent(data) {
  return request({
    url: '/api/llm/chat',
    method: 'POST',
    data,
    timeout: 120000
  })
}

export function createMeeting(data) {
  return request({
    url: '/api/meetings',
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
