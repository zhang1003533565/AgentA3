import { request } from './request'

const unwrap = (promise) => promise.then((response) => response.data)

export const getMessages = (params = {}) => unwrap(request({ url: '/api/app-message/list', params }))
export const getUnreadCount = () => unwrap(request({ url: '/api/app-message/unread/count' }))
export const markMessageRead = (id) => unwrap(request({ url: `/api/app-message/${id}/read`, method: 'PUT' }))
export const markAllMessagesRead = () => unwrap(request({ url: '/api/app-message/read-all', method: 'PUT' }))
export const markCategoryRead = (data) => unwrap(request({
  url: '/api/app-message/read-by-category',
  method: 'PUT',
  data,
}))
