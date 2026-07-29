import { request } from './request'

const unwrap = (promise) => promise.then((response) => response.data)

export const getSecondhandCategories = () => unwrap(request({ url: '/api/secondhand/category/list' }))
export const getSecondhandItemList = (params = {}) => unwrap(request({ url: '/api/secondhand/item/list', params }))
export const getSecondhandItemDetail = (id) => unwrap(request({ url: `/api/secondhand/item/${id}` }))
export const createSecondhandItem = (data) => unwrap(request({ url: '/api/secondhand/item', method: 'POST', data }))
export const getMySecondhandItems = (params = {}) => unwrap(request({ url: '/api/secondhand/item/my', params }))
export const offlineSecondhandItem = (id) => unwrap(request({ url: `/api/secondhand/item/${id}/offline`, method: 'PUT' }))
export const onlineSecondhandItem = (id) => unwrap(request({ url: `/api/secondhand/item/${id}/online`, method: 'PUT' }))
export const getChatSessions = (params = {}) => unwrap(request({ url: '/api/chat/session/list', params }))
export const createOrGetChatSession = (itemId, targetUserId) => unwrap(request({
  url: `/api/chat/session/${itemId}`,
  method: 'POST',
  params: targetUserId ? { targetUserId } : {},
}))
export const getChatMessages = (sessionId, params = {}) => unwrap(request({
  url: `/api/chat/message/list/${sessionId}`,
  params,
}))
export const sendChatMessage = (data) => unwrap(request({ url: '/api/chat/message', method: 'POST', data }))
export const getTradeRecords = (params = {}) => unwrap(request({ url: '/api/trade/record/list', params }))
export const confirmTradeRecord = (id) => unwrap(request({ url: `/api/trade/record/${id}/confirm`, method: 'POST' }))
export const completeTradeRecord = (id) => unwrap(request({ url: `/api/trade/record/${id}/complete`, method: 'POST' }))
export const cancelTradeRecord = (id) => unwrap(request({ url: `/api/trade/record/${id}/cancel`, method: 'POST' }))
export const ensureTradeRecordBySession = (sessionId) => unwrap(request({
  url: `/api/trade/record/session/${sessionId}/ensure`,
  method: 'POST',
}))
export const reserveSecondhandItem = (itemId) => unwrap(request({
  url: `/api/trade/record/reserve/${itemId}`,
  method: 'POST',
}))
export const favoriteSecondhandItem = (itemId) => unwrap(request({
  url: `/api/secondhand/favorite/${itemId}`,
  method: 'POST',
}))
export const unfavoriteSecondhandItem = (itemId) => unwrap(request({
  url: `/api/secondhand/favorite/${itemId}`,
  method: 'DELETE',
}))
export const getMySecondhandFavorites = (params = {}) => unwrap(request({
  url: '/api/secondhand/favorite/my',
  params,
}))
export const getTradeNotifications = (params = {}) => unwrap(request({
  url: '/api/chat/trade-notifications',
  params,
}))
export const markTradeNotificationRead = (id) => unwrap(request({
  url: `/api/chat/trade-notifications/${id}/read`,
  method: 'PUT',
}))
