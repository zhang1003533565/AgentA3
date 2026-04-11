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

/** 创建公告（管理员/教师权限） */
export function createAnnouncement(data) {
  return request({
    url: '/api/announcements',
    method: 'POST',
    data
  })
}

/** 更新公告（管理员/教师权限） */
export function updateAnnouncement(id, data) {
  return request({
    url: `/api/announcements/${id}`,
    method: 'PUT',
    data
  })
}

/** 删除公告（管理员/教师权限） */
export function deleteAnnouncement(id) {
  return request({
    url: `/api/announcements/${id}`,
    method: 'DELETE'
  })
}
