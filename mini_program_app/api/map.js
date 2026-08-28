import { request } from '@/utils/request'
import {
  compactParams,
  formatFloorTabLabel,
  getPlaceTypeLabel,
  isBuildingPlaceType,
  toMapPlaceMarker,
} from '@/utils/mapPlaceCore'

// 点位适配的纯函数统一收敛在 utils/mapPlaceCore.js（无依赖、可被 node 测试直接 import），
// 这里按原有导出签名转发，页面侧引用保持不变。
export { getPlaceTypeLabel, isBuildingPlaceType, formatFloorTabLabel, compactParams, toMapPlaceMarker }

const adaptRequest = (task, transform) => {
  const adapted = task.then(transform)
  adapted.abort = (...args) => task.abort?.(...args)
  adapted.done = adapted
  return adapted
}

const normalizePlaceList = (payload) => {
  if (Array.isArray(payload)) return payload
  if (Array.isArray(payload?.records)) return payload.records
  if (Array.isArray(payload?.list)) return payload.list
  if (Array.isArray(payload?.data)) return payload.data
  return []
}

export function getFacilityList(params = {}) {
  const task = request({
    url: '/api/v1/map-places',
    method: 'GET',
    params: compactParams({ keyword: params.keyword || params.name }),
  })
  return adaptRequest(task, (response) => {
    const records = normalizePlaceList(response.data)
      .filter((item) => item.parentId == null)
      .map(toMapPlaceMarker)
      .filter(Boolean)
      .filter((item) => params.type == null || Number(params.type) === item.facilityType)
    return { ...response, data: { records, total: records.length, page: 1, size: records.length } }
  })
}

export function getMarkerList(params = {}) {
  const task = request({
    url: '/api/v1/map-places',
    method: 'GET',
    params: compactParams({
      keyword: params.keyword,
      status: params.status || 'ENABLED',
    }),
    showError: params.showError !== false,
  })
  return adaptRequest(task, (response) => {
    const selectedTypes = String(params.facilityTypes || params.facilityType || '')
      .split(',')
      .map(Number)
      .filter(Number.isFinite)
    const records = normalizePlaceList(response.data)
      .filter((item) => item.mapVisible !== false)
      .map(toMapPlaceMarker)
      .filter(Boolean)
      .filter((item) => item.longitude != null && item.latitude != null)
      .filter((item) => !selectedTypes.length || selectedTypes.includes(item.facilityType))
    return { ...response, data: { records, total: records.length, page: 1, size: records.length } }
  })
}

/** GET /api/v1/map-places/{id} — place detail only; floor tree/plans load separately */
export function getMarkerDetail(id, params = {}) {
  const task = request({
    url: `/api/v1/map-places/${id}`,
    method: 'GET',
    params: compactParams({
      includeChildren: params.includeChildren === true ? true : false,
    }),
  })
  return adaptRequest(task, (response) => ({
    ...response,
    data: toMapPlaceMarker(response.data),
  }))
}

export function getPlaceChildren(parentId, params = {}) {
  const task = request({
    url: '/api/v1/map-places',
    method: 'GET',
    params: compactParams({
      parentId,
      status: params.status || 'ENABLED',
      placeType: params.placeType,
    }),
    showError: params.showError !== false,
  })
  return adaptRequest(task, (response) => {
    const records = normalizePlaceList(response.data).map(toMapPlaceMarker).filter(Boolean)
    return { ...response, data: records }
  })
}

/** GET /api/v1/map-places/floors/{floorPlaceId}/plan */
export function getFloorPlan(floorPlaceId) {
  return request({
    url: `/api/v1/map-places/floors/${floorPlaceId}/plan`,
    method: 'GET',
    showError: false,
  })
}

/** GET /api/v1/map-places/floor-plans/{floorPlanId}/positions */
export function getIndoorPositions(floorPlanId) {
  return request({
    url: `/api/v1/map-places/floor-plans/${floorPlanId}/positions`,
    method: 'GET',
    showError: false,
  })
}

/** GET /api/v1/map-places/{id}/fence */
export function getPlaceFence(id) {
  return request({
    url: `/api/v1/map-places/${id}/fence`,
    method: 'GET',
    showError: false,
  })
}

export function searchFacilities(params = {}, options = {}) {
  return request({
    url: '/api/v1/map/search',
    method: 'GET',
    params: compactParams(params),
    showError: options.showError !== false,
  })
}

export function locateFacility(keyword) {
  return request({
    url: '/api/v1/map/locate',
    method: 'GET',
    params: compactParams({ keyword }),
  })
}

export function getNearbyFacilities(params = {}) {
  return request({
    url: '/api/v1/map/nearby',
    method: 'GET',
    params: compactParams(params),
  })
}

export function getNearbyCount(params = {}) {
  return request({
    url: '/api/v1/map/nearby/count',
    method: 'GET',
    params: compactParams(params),
  })
}

export function getNavigationRoute(params = {}) {
  return request({
    url: '/api/v1/map/navigation/route',
    method: 'GET',
    params: compactParams(params),
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
    params: compactParams(params),
  })
}

export function reverseGeocode(longitude, latitude) {
  return request({
    url: '/api/v1/map/navigation/reverse-geocode',
    method: 'GET',
    params: compactParams({ longitude, latitude }),
  })
}

export function geocodeAddress(address, region) {
  return request({
    url: '/api/v1/map/navigation/geocode',
    method: 'GET',
    params: compactParams({ address, region }),
  })
}

export function searchPlaces(params = {}, options = {}) {
  return request({
    url: '/api/v1/map/navigation/places/search',
    method: 'GET',
    params: compactParams(params),
    showError: options.showError !== false,
  })
}
