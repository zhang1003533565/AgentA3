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

export function getMarkerDetail(id) {
  return request({
    url: `/api/v1/map/marker/${id}`,
    method: 'GET',
  })
}

export function searchFacilities(params = {}) {
  return request({
    url: '/api/v1/map/search',
    method: 'GET',
    params,
  })
}

export function locateFacility(keyword) {
  return request({
    url: '/api/v1/map/locate',
    method: 'GET',
    params: { keyword },
  })
}

export function getNearbyFacilities(params = {}) {
  return request({
    url: '/api/v1/map/nearby',
    method: 'GET',
    params,
  })
}

export function getNearbyCount(params = {}) {
  return request({
    url: '/api/v1/map/nearby/count',
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

export function startNavigationRecord(data = {}) {
  return request({
    url: '/api/v1/map/navigation',
    method: 'POST',
    data,
  })
}

export function searchPlaces(params = {}) {
  return request({
    url: '/api/v1/navigation/places/search',
    method: 'GET',
    params,
  })
}
