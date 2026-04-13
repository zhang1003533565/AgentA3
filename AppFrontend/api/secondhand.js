import { request } from '@/utils/request'
import { BASE_URL } from '@/utils/config.js'
import { getToken } from '@/utils/storage.js'

export function getSecondhandCategories() {
  return request({
    url: '/api/secondhand/category/list',
    method: 'GET'
  })
}

export function getSecondhandItemList(params = {}) {
  return request({
    url: '/api/secondhand/item/list',
    method: 'GET',
    params
  })
}

export function getSecondhandItemDetail(id) {
  return request({
    url: `/api/secondhand/item/${id}`,
    method: 'GET'
  })
}

export function createSecondhandItem(data) {
  return request({
    url: '/api/secondhand/item',
    method: 'POST',
    data
  })
}

export function getMySecondhandItems(params = {}) {
  return request({
    url: '/api/secondhand/item/my',
    method: 'GET',
    params
  })
}

export function offlineSecondhandItem(id) {
  return request({
    url: `/api/secondhand/item/${id}/offline`,
    method: 'PUT'
  })
}

export function onlineSecondhandItem(id) {
  return request({
    url: `/api/secondhand/item/${id}/online`,
    method: 'PUT'
  })
}

export function getChatSessions(params = {}) {
  return request({
    url: '/api/chat/session/list',
    method: 'GET',
    params
  })
}

export function createOrGetChatSession(itemId) {
  return request({
    url: `/api/chat/session/${itemId}`,
    method: 'POST'
  })
}

export function getChatMessages(sessionId, params = {}) {
  return request({
    url: `/api/chat/message/list/${sessionId}`,
    method: 'GET',
    params
  })
}

export function sendChatMessage(data) {
  return request({
    url: '/api/chat/message',
    method: 'POST',
    data
  })
}

export function getChatUnreadCount() {
  return request({
    url: '/api/chat/message/unread/count',
    method: 'GET'
  })
}

export function uploadSecondhandImage(filePath) {
  const token = getToken()
  return new Promise((resolve, reject) => {
    uni.uploadFile({
      url: `${BASE_URL}/api/upload/image`,
      filePath,
      name: 'file',
      header: token ? { Authorization: `Bearer ${token}` } : {},
      success: (res) => {
        try {
          const data = JSON.parse(res.data || '{}')
          if (res.statusCode >= 200 && res.statusCode < 300 && data.code === 200) {
            resolve(data.data?.url || '')
            return
          }
          reject(data)
        } catch (error) {
          reject(error)
        }
      },
      fail: reject
    })
  })
}
