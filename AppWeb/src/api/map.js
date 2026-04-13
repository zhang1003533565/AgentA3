import request from '../utils/request'

// ========== 地图标记 ==========

export const getMarkerList = (params = {}) =>
  request({
    url: '/api/v1/map/marker/list',
    method: 'get',
    params: {
      facilityType: params.facilityType,
      keyword: params.keyword,
      pageNum: params.pageNum ?? params.page ?? 1,
      pageSize: params.pageSize ?? params.size ?? 100,
    },
  })

export const getMarkerDetail = (id) =>
  request({
    url: `/api/v1/map/marker/${id}`,
    method: 'get',
  })

export const createMarker = (data) =>
  request({
    url: '/api/v1/map/marker',
    method: 'post',
    data,
  })

export const updateMarker = (id, data) =>
  request({
    url: `/api/v1/map/marker/${id}`,
    method: 'put',
    data,
  })

export const deleteMarker = (id) =>
  request({
    url: `/api/v1/map/marker/${id}`,
    method: 'delete',
  })

export const batchCreateMarker = (facilityIds) =>
  request({
    url: '/api/v1/map/marker/batch',
    method: 'post',
    data: { facilityIds },
  })

export const getMarkerIcons = () =>
  request({
    url: '/api/v1/map/marker/icons',
    method: 'get',
  })

// ========== 地图统计 ==========

export const getNavigationStatistics = (params = {}) =>
  request({
    url: '/api/v1/map/statistics/navigation',
    method: 'get',
    params,
  })

export const getFacilityHeat = (params = {}) =>
  request({
    url: '/api/v1/map/statistics/facility-heat',
    method: 'get',
    params,
  })

export const getMarkerVisit = (params = {}) =>
  request({
    url: '/api/v1/map/statistics/marker-visit',
    method: 'get',
    params,
  })
