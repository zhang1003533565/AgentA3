/**
 * 活动相关接口（后续对接后端活动列表、详情等）
 */
import { request } from '../utils/request.js'

/**
 * 活动列表（分页、分类、关键词）
 * @param {Object} params - { page, pageSize, categoryId, keyword }
 */
export function getActivityList(params = {}) {
  return request({
    url: '/api/activity/list',
    method: 'GET',
    data: params
  })
}

/** 活动详情 */
export function getActivityDetail(id) {
  return request({
    url: `/api/activity/${id}`,
    method: 'GET'
  })
}
