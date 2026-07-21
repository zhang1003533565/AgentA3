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

