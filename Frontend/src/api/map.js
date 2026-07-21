import { request } from './request'

export function getMarkerList(params = {}) {
  return request({
    url: '/api/v1/map/marker/list',
    method: 'GET',
    params,
  })
}

