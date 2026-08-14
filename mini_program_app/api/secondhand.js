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

export function updateSecondhandItem(id, data) {
  return request({
    url: `/api/secondhand/item/${id}`,
    method: 'PUT',
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

export function getUserPublicItems(userId, params = {}) {
  return request({
    url: `/api/secondhand/user/${userId}/items`,
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

export function deleteSecondhandItem(id) {
  return request({
    url: `/api/secondhand/item/${id}`,
    method: 'DELETE'
  })
}

export function getChatSessions(params = {}) {
  return request({
    url: '/api/chat/session/list',
    method: 'GET',
    params
  })
}

export function getChatSessionById(sessionId) {
  return request({
    url: `/api/chat/session/${sessionId}`,
    method: 'GET'
  })
}

export function createOrGetChatSession(itemId, targetUserId) {
  return request({
    url: `/api/chat/session/${itemId}`,
    method: 'POST',
    params: targetUserId ? { targetUserId } : {}
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

export function getTradeNotifications(params = {}) {
  return request({
    url: '/api/chat/trade-notifications',
    method: 'GET',
    params
  })
}

export function getTradeNotificationUnreadCount() {
  return request({
    url: '/api/chat/trade-notifications/unread/count',
    method: 'GET'
  })
}

export function getChatMessageSummary(params = {}) {
  return request({
    url: '/api/chat/messages/summary',
    method: 'GET',
    params
  })
}

export function markTradeNotificationRead(id) {
  return request({
    url: `/api/chat/trade-notifications/${id}/read`,
    method: 'PUT'
  })
}

export function markAllTradeNotificationsRead() {
  return request({
    url: '/api/chat/trade-notifications/read-all',
    method: 'PUT'
  })
}

export function uploadSecondhandImage(filePath) {
  const { uploadImage } = require('../utils/upload.js')
  return uploadImage(filePath)
}

export function getTradeRecords(params = {}) {
  return request({
    url: '/api/trade/record/list',
    method: 'GET',
    params
  })
}

export function confirmTradeRecord(id) {
  return request({
    url: `/api/trade/record/${id}/confirm`,
    method: 'POST'
  })
}

export function completeTradeRecord(id) {
  return request({
    url: `/api/trade/record/${id}/complete`,
    method: 'POST'
  })
}

export function cancelTradeRecord(id) {
  return request({
    url: `/api/trade/record/${id}/cancel`,
    method: 'POST'
  })
}

export function ensureTradeRecordBySession(sessionId) {
  return request({
    url: `/api/trade/record/session/${sessionId}/ensure`,
    method: 'POST'
  })
}

export function reserveSecondhandItem(itemId) {
  return request({
    url: `/api/trade/record/reserve/${itemId}`,
    method: 'POST'
  })
}

export function getTradeRecord(id) {
  return request({
    url: `/api/trade/record/${id}`,
    method: 'GET'
  })
}

export function getTradeRecordByItem(itemId) {
  return request({
    url: `/api/trade/record/by-item/${itemId}`,
    method: 'GET'
  })
}

export function favoriteSecondhandItem(itemId) {
  return request({
    url: `/api/secondhand/favorite/${itemId}`,
    method: 'POST'
  })
}

export function unfavoriteSecondhandItem(itemId) {
  return request({
    url: `/api/secondhand/favorite/${itemId}`,
    method: 'DELETE'
  })
}

export function getMyFavorites(params = {}) {
  return request({
    url: '/api/secondhand/favorite/my',
    method: 'GET',
    params
  })
}

export function reportSecondhandItem(data) {
  return request({
    url: '/api/secondhand/reports',
    method: 'POST',
    data
  })
}

export function recordBrowseHistory(itemId) {
  return request({
    url: `/api/secondhand/browse-history/${itemId}`,
    method: 'POST'
  })
}

export function getMyBrowseHistory(params = {}) {
  return request({
    url: '/api/secondhand/browse-history/my',
    method: 'GET',
    params
  })
}

export function clearMyBrowseHistory() {
  return request({
    url: '/api/secondhand/browse-history/my',
    method: 'DELETE'
  })
}
