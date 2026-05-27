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
