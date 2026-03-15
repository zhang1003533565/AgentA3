import request from '../utils/request'

// 获取通知列表
export const getNoticeList = (params) => {
  return request({
    url: '/api/notices',
    method: 'get',
    params
  })
}

// 标记通知为已读
export const markAsRead = (id) => {
  return request({
    url: `/api/notices/${id}/read`,
    method: 'put'
  })
}

// 标记所有通知为已读
export const markAllAsRead = () => {
  return request({
    url: '/api/notices/read-all',
    method: 'put'
  })
}

// 获取未读通知数量
export const getUnreadCount = () => {
  return request({
    url: '/api/notices/unread-count',
    method: 'get'
  })
}
