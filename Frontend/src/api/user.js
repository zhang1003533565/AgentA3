import { request } from './request'

export function login(data) {
  return request({
    url: '/api/auth/applogin',
    method: 'POST',
    data: {
      username: data.username,
      password: data.password,
    },
  })
}

export function register(data) {
  return request({
    url: '/api/auth/register',
    method: 'POST',
    data,
  })
}

// ========== 消息中心 ==========

export function getMessages(current = 1, size = 100) {
  return request({ url: '/api/app-message/list', method: 'GET', params: { current, size } })
}

export function getUnreadCount() {
  return request({ url: '/api/app-message/unread/count', method: 'GET' })
}

export function markMessageRead(id) {
  return request({ url: `/api/app-message/${id}/read`, method: 'PUT' })
}

export function markAllMessagesRead() {
  return request({ url: '/api/app-message/read-all', method: 'PUT' })
}

