import request from '../utils/request'

export const getFacilityTypes = () =>
  request({
    url: '/api/v1/facility/types',
    method: 'get',
  })

export const updateFacilityTypes = (types) =>
  request({
    url: '/api/v1/facility/types',
    method: 'put',
    data: types,
  })

export const getFacilityList = (params = {}) =>
  request({
    url: '/api/v1/facility/list',
    method: 'get',
    params: {
      type: params.type,
      name: params.name ?? params.keyword,
      pageNum: params.pageNum ?? params.page ?? 1,
      pageSize: params.pageSize ?? params.size ?? 10,
    },
  })

export const getFacilityDetail = (id) =>
  request({
    url: `/api/v1/facility/${id}`,
    method: 'get',
  })

export const createFacility = (data) =>
  request({
    url: '/api/v1/facility',
    method: 'post',
    data,
  })

export const updateFacility = (id, data) =>
  request({
    url: `/api/v1/facility/${id}`,
    method: 'put',
    data,
  })

export const deleteFacility = (id) =>
  request({
    url: `/api/v1/facility/${id}`,
    method: 'delete',
  })

export const getFacilityFloors = (facilityId) =>
  request({
    url: '/api/v1/facility/floors',
    method: 'get',
    params: { facilityId },
  })

export const createFacilityFloor = (data) =>
  request({
    url: '/api/v1/facility/floors',
    method: 'post',
    data,
  })

export const updateFacilityFloor = (id, data) =>
  request({
    url: `/api/v1/facility/floors/${id}`,
    method: 'put',
    data,
  })

export const deleteFacilityFloor = (id) =>
  request({
    url: `/api/v1/facility/floors/${id}`,
    method: 'delete',
  })

export const getStallCuisines = (restaurantId) =>
  request({
    url: '/api/v1/facility/stall-cuisines',
    method: 'get',
    params: { restaurantId },
  })

export const createStallCuisine = (data) =>
  request({
    url: '/api/v1/facility/stall-cuisines',
    method: 'post',
    data,
  })

export const updateStallCuisine = (id, data) =>
  request({
    url: `/api/v1/facility/stall-cuisines/${id}`,
    method: 'put',
    data,
  })

export const deleteStallCuisine = (id) =>
  request({
    url: `/api/v1/facility/stall-cuisines/${id}`,
    method: 'delete',
  })
