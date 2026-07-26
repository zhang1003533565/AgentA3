import { request } from '@/utils/request'

export function getAppMessages(params = {}) {
  return request({
    url: '/api/app-message/list',
    method: 'GET',
    params
  })
}

export function getAppMessageUnreadCount({ showError = true } = {}) {
  return request({
    url: '/api/app-message/unread/count',
    method: 'GET',
    showError
  })
}

export function getRealtimeTicket() {
  return request({
    url: '/api/realtime/ticket',
    method: 'POST',
    showError: false
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

export function markAppMessagesReadByCategory(data = {}) {
  return request({
    url: '/api/app-message/read-by-category',
    method: 'PUT',
    data
  })
}
