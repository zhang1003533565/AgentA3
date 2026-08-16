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

export function getFloorPlan(floorPlaceId) {
  return request({
    url: `/api/v1/map-places/floors/${floorPlaceId}/plan`,
    method: 'GET',
  })
}

export function getFloorPlanPositions(floorPlanId) {
  return request({
    url: `/api/v1/map-places/floor-plans/${floorPlanId}/positions`,
    method: 'GET',
  })
}

