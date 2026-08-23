import { request } from './request'

export function getMeetings(params = {}) {
  return request({
    url: '/api/meetings',
    method: 'GET',
    params,
  })
}

export function createQuickMeeting(data) {
  return request({
    url: '/api/meetings/quick',
    method: 'POST',
    data,
  })
}

export function createMeeting(data) {
  return request({ url: '/api/meetings', method: 'POST', data })
}

export function reserveMeeting(data) {
  return request({ url: '/api/meetings/reservations', method: 'POST', data })
}

export function getMeetingDetail(id) {
  return request({ url: `/api/meetings/${encodeURIComponent(id)}`, method: 'GET' })
}

export function joinMeeting(data) {
  return request({ url: '/api/meetings/join', method: 'POST', data })
}

export function startMeeting(id) {
  return request({ url: `/api/meetings/${encodeURIComponent(id)}/start`, method: 'POST' })
}

export function endMeeting(id) {
  return request({ url: `/api/meetings/${encodeURIComponent(id)}/end`, method: 'POST' })
}

export function organizeMeeting(id) {
  return request({ url: `/api/meetings/${encodeURIComponent(id)}/organize`, method: 'POST' })
}

export function deleteMeeting(id) {
  return request({ url: `/api/meetings/${encodeURIComponent(id)}`, method: 'DELETE' })
}

export function getMeetingComments(sessionId) {
  return request({ url: `/api/meetings/${encodeURIComponent(sessionId)}/comments`, method: 'GET' })
}

export function sendMeetingComment(sessionId, data) {
  return request({ url: `/api/meetings/${encodeURIComponent(sessionId)}/comments`, method: 'POST', data })
}

