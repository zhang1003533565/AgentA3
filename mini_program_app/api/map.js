import { request } from '@/utils/request'

const SCENE_FACILITY_TYPE = {
  CANTEEN: 1,
  SPORTS: 2,
  TEACHING: 3,
  DORMITORY: 4,
}

const adaptRequest = (task, transform) => {
  const adapted = task.then(transform)
  adapted.abort = (...args) => task.abort?.(...args)
  adapted.done = adapted
  return adapted
}

const toLegacyMarker = (place) => {
  const facilityType = SCENE_FACILITY_TYPE[place.sceneType] || 99
  const imageUrls = (place.images || []).map((item) => item.imageUrl).filter(Boolean)
  return {
    id: place.id,
    facilityId: place.id,
    markerName: place.name,
    facilityName: place.name,
    facilityType,
    facilityTypeName: place.placeType,
    status: place.status === 'ENABLED' ? 1 : 3,
    description: place.description,
    location: place.locationDesc,
    longitude: place.longitude,
    latitude: place.latitude,
    geometryType: place.fence?.geometryType,
    boundaryPoints: place.fence?.geometryData,
    images: JSON.stringify(imageUrls),
    thumbnailUrl: imageUrls[0] || '',
  }
}

export function getFacilityList(params = {}) {
  const task = request({
    url: '/api/v1/map-places',
    method: 'GET',
    params: { keyword: params.keyword || params.name },
  })
  return adaptRequest(task, (response) => {
    const records = (response.data || [])
      .filter((item) => item.parentId == null)
      .map(toLegacyMarker)
      .filter((item) => params.type == null || Number(params.type) === item.facilityType)
    return { ...response, data: { records, total: records.length, page: 1, size: records.length } }
  })
}

export function getMarkerList(params = {}) {
  const task = request({
    url: '/api/v1/map-places',
    method: 'GET',
    params: { keyword: params.keyword },
  })
  return adaptRequest(task, (response) => {
    const selectedTypes = String(params.facilityTypes || params.facilityType || '')
      .split(',')
      .map(Number)
      .filter(Number.isFinite)
    const records = (response.data || [])
      .filter((item) => item.mapVisible !== false)
      .map(toLegacyMarker)
      .filter((item) => item.longitude != null && item.latitude != null)
      .filter((item) => !selectedTypes.length || selectedTypes.includes(item.facilityType))
    return { ...response, data: { records, total: records.length, page: 1, size: records.length } }
  })
}

export function getMarkerDetail(id) {
  const task = request({
    url: `/api/v1/map-places/${id}`,
    method: 'GET',
  })
  return adaptRequest(task, (response) => ({ ...response, data: toLegacyMarker(response.data) }))
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
