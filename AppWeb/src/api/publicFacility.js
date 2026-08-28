import request from '../utils/request'

// 分页查询公共设施
export const getPublicFacilities = (params = {}) =>
  request({
    url: '/api/public-facilities',
    method: 'get',
    params: {
      type: params.type,
      keyword: params.keyword,
      sortBy: params.sortBy,
      page: params.page ?? 1,
      size: params.size ?? 12,
    },
  })

// 获取全部公共设施
export const getAllPublicFacilities = () =>
  request({
    url: '/api/public-facilities/all',
    method: 'get',
  })

// 按类型获取公共设施列表
export const getPublicFacilitiesByType = (type) =>
  request({
    url: '/api/public-facilities/by-type',
    method: 'get',
    params: { type },
  })

// 获取公共设施详情
export const getPublicFacilityDetail = (id) =>
  request({
    url: `/api/public-facilities/${id}`,
    method: 'get',
  })

// 新增公共设施
export const createPublicFacility = (data) =>
  request({
    url: '/api/public-facilities',
    method: 'post',
    data,
  })

// 更新公共设施
export const updatePublicFacility = (id, data) =>
  request({
    url: `/api/public-facilities/${id}`,
    method: 'put',
    data,
  })

// 删除公共设施
export const deletePublicFacility = (id) =>
  request({
    url: `/api/public-facilities/${id}`,
    method: 'delete',
  })
