import { request } from './request'

export function getEnabledAnnouncements() {
  return request({
    url: '/api/announcements/enabled',
    method: 'GET',
  })
}

