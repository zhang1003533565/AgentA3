import { request } from '../utils/request.js'

export function openSignIn(activityId) {
  return request({
    url: `/api/signins/open?activityId=${activityId}`,
    method: 'POST'
  })
}

export function closeSignIn(activityId) {
  return request({
    url: `/api/signins/close?activityId=${activityId}`,
    method: 'POST'
  })
}

export function getSignInOpenStatus(activityId) {
  return request({
    url: `/api/signins/activity/${activityId}/status`,
    method: 'GET'
  })
}

export function studentSignIn(activityId) {
  return request({
    url: `/api/signins/${activityId}`,
    method: 'POST'
  })
}

export function getStudentSignInStatus(activityId) {
  return request({
    url: `/api/signins/activities/${activityId}/signin-status`,
    method: 'GET'
  })
}
