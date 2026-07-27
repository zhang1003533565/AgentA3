import { request } from './request'

export function getActivityList(params = {}) {
  return request({
    url: '/api/activities',
    method: 'GET',
    params,
  })
}

export function getActivityDetail(id) {
  return request({
    url: `/api/activities/${id}`,
    method: 'GET',
  })
}

export function getCategoryList() {
  return request({
    url: '/api/categories',
    method: 'GET',
  })
}

export function getMyRegistrations(params = {}) {
  return request({
    url: '/api/registrations/my-registrations',
    method: 'GET',
    params,
  })
}

export function registerActivity(activityId) {
  return request({
    url: '/api/registrations',
    method: 'POST',
    params: { activityId },
  })
}

export function cancelRegistration(id) {
  return request({
    url: `/api/registrations/${id}`,
    method: 'DELETE',
  })
}

export function getMyFavorites(params = {}) {
  return request({
    url: '/api/activities/favorites',
    method: 'GET',
    params,
  })
}

export function addFavorite(activityId) {
  return request({
    url: `/api/activities/${activityId}/favorite`,
    method: 'POST',
  })
}

export function removeFavorite(activityId) {
  return request({
    url: `/api/activities/${activityId}/favorite`,
    method: 'DELETE',
  })
}
