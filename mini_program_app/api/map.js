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

export function searchFacilities(params = {}, options = {}) {
  return request({
    url: '/api/v1/map/search',
    method: 'GET',
    params,
    showError: options.showError !== false,
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

export function arriveNavigation(navigationId) {
  return request({
    url: `/api/v1/map/navigation/${navigationId}/arrive`,
    method: 'POST',
  })
}

export function cancelNavigation(navigationId) {
  return request({
    url: `/api/v1/map/navigation/${navigationId}/cancel`,
    method: 'POST',
  })
}

export function getNavigationHistory(params = {}) {
  return request({
    url: '/api/v1/map/navigation/history',
    method: 'GET',
    params,
  })
}

export function reverseGeocode(longitude, latitude) {
  return request({
    url: '/api/v1/map/navigation/reverse-geocode',
    method: 'GET',
    params: { longitude, latitude },
  })
}

export function geocodeAddress(address, region) {
  return request({
    url: '/api/v1/map/navigation/geocode',
    method: 'GET',
    params: { address, region },
  })
}

export function searchPlaces(params = {}, options = {}) {
  return request({
    url: '/api/v1/map/navigation/places/search',
    method: 'GET',
    params,
    showError: options.showError !== false,
  })
}
