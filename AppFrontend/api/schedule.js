import { request } from '../utils/request.js'

export function getCurrentSchedule() {
  return request({
    url: '/api/browser/jwx/schedule/current',
    method: 'GET'
  })
}

export function getAllSchedules() {
  return request({
    url: '/api/schedule',
    method: 'GET'
  })
}

export function getScheduleDetail(courseId) {
  return request({
    url: `/api/browser/jwx/schedule/${courseId}`,
    method: 'GET'
  })
}

export function importScheduleAuto() {
  return request({
    url: '/api/browser/jwx/schedule/auto',
    method: 'POST'
  })
}

export function copyScheduleByShareCode(shareCode) {
  return request({
    url: '/api/schedule/copy',
    method: 'POST',
    data: { shareCode }
  })
}

export function getCurrentUser() {
  return request({
    url: '/api/auth/current-user',
    method: 'GET'
  })
}

export function checkJwxBind() {
  return request({
    url: '/api/browser/jwx/user/check-jwx-bind',
    method: 'GET'
  })
}

export function getScheduleSettings() {
  return request({
    url: '/api/schedule/settings',
    method: 'GET'
  })
}

export function updateScheduleSettings(data) {
  return request({
    url: '/api/schedule/settings',
    method: 'PUT',
    data
  })
}
