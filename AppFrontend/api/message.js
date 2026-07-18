import { request } from '@/utils/request'

export function getAppMessages(params = {}) {
  return request({
    url: '/api/app-message/list',
    method: 'GET',
    params
  })
}

export function getAppMessageUnreadCount() {
  return request({
    url: '/api/app-message/unread/count',
    method: 'GET'
  })
}

export function markAppMessageRead(id) {
  return request({
    url: `/api/app-message/${id}/read`,
    method: 'PUT'
  })
}

export function markAllAppMessagesRead() {
  return request({
    url: '/api/app-message/read-all',
    method: 'PUT'
  })
}
