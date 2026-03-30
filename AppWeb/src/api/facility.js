import request from '../utils/request'

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
