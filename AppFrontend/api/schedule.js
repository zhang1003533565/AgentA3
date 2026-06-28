import { request } from '../utils/request.js'

export function getCurrentSchedule(params) {
  return request({
    url: '/api/browser/jwx/schedule/current',
    method: 'GET',
    data: params
  })
}

export function getAllSchedules(params) {
  return request({
    url: '/api/schedule',
    method: 'GET',
    data: params
  })
}

export function getScheduleDetail(courseId) {
  return request({
    url: `/api/browser/jwx/schedule/${courseId}`,
    method: 'GET'
  })
}

export function importScheduleAuto(data) {
  return request({
    url: '/api/browser/jwx/schedule/auto',
    method: 'POST',
    data
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
