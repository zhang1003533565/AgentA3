import { request } from '@/utils/request'

export function getFacilityList(params = {}) {
  return request({
    url: '/api/v1/facility/list',
    method: 'GET',
    params,
  })
}

export function getMarkerList(params = {}) {
  return request({
    url: '/api/v1/map/marker/list',
    method: 'GET',
    params,
  })
}

export function getNavigationRoute(params = {}) {
  return request({
    url: '/api/v1/map/navigation/route',
    method: 'GET',
    params,
  })
}

export function searchPlaces(params = {}) {
  return request({
    url: '/api/v1/navigation/places/search',
    method: 'GET',
    params,
  })
}
