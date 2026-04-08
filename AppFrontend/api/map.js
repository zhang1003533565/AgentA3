import { request } from '@/utils/request'

export function getMapConfig() {
  return request({
    url: '/api/v1/map/config',
    method: 'GET',
  })
}

export function getFacilityList(params = {}) {
  return request({
    url: '/api/v1/facility/list',
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
