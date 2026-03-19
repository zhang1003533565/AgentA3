import { request } from '../utils/request.js'

export function registerActivity(activityId) {
  return request({
    url: `/api/registrations?activityId=${activityId}`,
    method: 'POST'
  })
}

export function cancelRegistration(registrationId) {
  return request({
    url: `/api/registrations/${registrationId}`,
    method: 'DELETE'
  })
}

export function getMyRegistrations(params = {}) {
  return request({
    url: '/api/registrations/my-registrations',
    method: 'GET',
    data: params
  })
}
