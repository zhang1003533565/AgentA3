import request from '../utils/request'

export const getMapPlaceTree = (sceneType) =>
  request({
    url: '/api/v1/map-places/tree',
    method: 'get',
    params: { sceneType },
  })

export const getMapPlaceList = (params = {}) =>
  request({
    url: '/api/v1/map-places',
    method: 'get',
    params,
  })

export const getMapPlaceDetail = (id) =>
  request({ url: `/api/v1/map-places/${id}`, method: 'get' })

export const createMapPlace = (data) =>
  request({ url: '/api/v1/map-places', method: 'post', data })

export const updateMapPlace = (id, data) =>
  request({ url: `/api/v1/map-places/${id}`, method: 'put', data })

export const deleteMapPlace = (id) =>
  request({ url: `/api/v1/map-places/${id}`, method: 'delete' })

export const addMapPlaceImage = (placeId, data) =>
  request({ url: `/api/v1/map-places/${placeId}/images`, method: 'post', data })

export const updateMapPlaceImage = (imageId, data) =>
  request({ url: `/api/v1/map-places/images/${imageId}`, method: 'put', data })

export const deleteMapPlaceImage = (imageId) =>
  request({ url: `/api/v1/map-places/images/${imageId}`, method: 'delete' })

export const saveMapPlaceFence = (placeId, data) =>
  request({ url: `/api/v1/map-places/${placeId}/fence`, method: 'put', data })

export const deleteMapPlaceFence = (placeId) =>
  request({ url: `/api/v1/map-places/${placeId}/fence`, method: 'delete' })

export const getFloorPlan = (floorPlaceId) =>
  request({ url: `/api/v1/map-places/floors/${floorPlaceId}/plan`, method: 'get' })

export const saveFloorPlan = (floorPlaceId, data) =>
  request({ url: `/api/v1/map-places/floors/${floorPlaceId}/plan`, method: 'put', data })

export const deleteFloorPlan = (floorPlaceId) =>
  request({ url: `/api/v1/map-places/floors/${floorPlaceId}/plan`, method: 'delete' })

export const saveIndoorPosition = (placeId, data) =>
  request({ url: `/api/v1/map-places/${placeId}/indoor-position`, method: 'put', data })

export const deleteIndoorPosition = (positionId) =>
  request({ url: `/api/v1/map-places/indoor-positions/${positionId}`, method: 'delete' })
