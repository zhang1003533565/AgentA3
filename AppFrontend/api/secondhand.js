import { request } from '@/utils/request'

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
  const { uploadImage } = require('../utils/upload.js')
  return uploadImage(filePath)
}
