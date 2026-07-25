/**
 * 活动相关接口（后续对接后端活动列表、详情等）
 */
import { request } from '../utils/request.js'

/**
 * 活动列表（分页、分类、关键词）
 * @param {Object} params - { page, size, title, categoryId, status }
 */
export function getActivityList(params = {}) {
  return request({
    url: '/api/activities',
    method: 'GET',
    data: params
  })
}

/** 活动详情 */
export function getActivityDetail(id) {
  return request({
    url: `/api/activities/${id}`,
    method: 'GET'
  })
}

/** 模糊搜索活动 */
export function searchActivities(params = {}) {
  return request({
    url: '/api/activities/search',
    method: 'GET',
    data: params
  })
}

/** 筛选活动 */
export function filterActivities(params = {}) {
  return request({
    url: '/api/activities/filter',
    method: 'GET',
    data: params
  })
}

/** 收藏活动 */
export function addFavorite(activityId) {
  return request({
    url: `/api/activities/${activityId}/favorite`,
    method: 'POST'
  })
}

/** 取消收藏 */
export function removeFavorite(activityId) {
  return request({
    url: `/api/activities/${activityId}/favorite`,
    method: 'DELETE'
  })
}

/** 检查是否已收藏 */
export function checkFavoriteStatus(activityId) {
  return request({
    url: `/api/activities/${activityId}/favorite/status`,
    method: 'GET'
  })
}

/** 获取我的收藏列表 */
export function getMyFavoriteList(params = {}) {
  return request({
    url: '/api/activities/favorites',
    method: 'GET',
    data: params
  })
}
