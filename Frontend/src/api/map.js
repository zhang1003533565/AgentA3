import { request } from './request'

export function getMapPlaceList(params = {}) {
  return request({
    url: '/api/v1/map-places',
    method: 'GET',
    params,
  })
}

export function getMapPlaceDetail(id) {
  return request({
    url: `/api/v1/map-places/${id}`,
    method: 'GET',
  })
}

