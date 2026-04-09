/**
 * 公告相关接口
 */
import { request } from '../utils/request.js'

/** 获取已启用的公告列表 */
export function getEnabledAnnouncements() {
  return request({
    url: '/api/announcements/enabled',
    method: 'GET'
  })
}

/** 获取公告详情 */
export function getAnnouncementById(id) {
  return request({
    url: `/api/announcements/${id}`,
    method: 'GET'
  })
}
