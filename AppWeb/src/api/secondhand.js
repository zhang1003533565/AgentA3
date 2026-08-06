import request from '../utils/request'

// ========== 分类管理 ==========

export const getSecondhandCategoryList = () =>
  request({
    url: '/api/secondhand/category/list',
    method: 'get',
  })

export const createSecondhandCategory = (data) =>
  request({
    url: '/api/secondhand/category',
    method: 'post',
    data,
  })

export const updateSecondhandCategory = (id, data) =>
  request({
    url: `/api/secondhand/category/${id}`,
    method: 'put',
    data,
  })

export const deleteSecondhandCategory = (id) =>
  request({
    url: `/api/secondhand/category/${id}`,
    method: 'delete',
  })

// ========== 物品管理 ==========

export const getSecondhandItemList = (params = {}) =>
  request({
    url: '/api/secondhand/item/list',
    method: 'get',
    params: {
      current: params.current ?? params.page ?? 1,
      size: params.size ?? 10,
      categoryId: params.categoryId,
      keyword: params.keyword,
      condition: params.condition,
      minPrice: params.minPrice,
      maxPrice: params.maxPrice,
      sort: params.sort,
    },
  })

export const getSecondhandItemDetail = (id) =>
  request({
    url: `/api/secondhand/item/${id}`,
    method: 'get',
  })

export const createSecondhandItem = (data) =>
  request({
    url: '/api/secondhand/item',
    method: 'post',
    data,
  })

export const updateSecondhandItem = (id, data) =>
  request({
    url: `/api/secondhand/item/${id}`,
    method: 'put',
    data,
  })

export const deleteSecondhandItem = (id) =>
  request({
    url: `/api/secondhand/item/${id}`,
    method: 'delete',
  })

export const getMySecondhandItems = (params = {}) =>
  request({
    url: '/api/secondhand/item/my',
    method: 'get',
    params: {
      current: params.current ?? params.page ?? 1,
      size: params.size ?? 10,
      status: params.status,
    },
  })

export const offlineSecondhandItem = (id) =>
  request({
    url: `/api/secondhand/item/${id}/offline`,
    method: 'put',
  })

export const onlineSecondhandItem = (id) =>
  request({
    url: `/api/secondhand/item/${id}/online`,
    method: 'put',
  })

export const markSecondhandItemSold = (id) =>
  request({
    url: `/api/secondhand/item/${id}/sold`,
    method: 'put',
  })

export const getSecondhandAdminList = (params = {}) =>
  request({
    url: '/api/secondhand/item/admin/list',
    method: 'get',
    params: {
      current: params.current ?? params.page ?? 1,
      size: params.size ?? 10,
      keyword: params.keyword,
      categoryId: params.categoryId,
      status: params.status,
      tradeType: params.tradeType,
      userId: params.userId,
    },
  })

export const batchSecondhandOperation = (data) =>
  request({
    url: '/api/secondhand/item/batch',
    method: 'put',
    data,
  })

export const getSecondhandStatistics = (params = {}) =>
  request({
    url: '/api/secondhand/statistics',
    method: 'get',
    params,
  })

// ========== 举报管理 ==========

export const getSecondhandReportList = (params = {}) =>
  request({
    url: '/api/secondhand/reports',
    method: 'get',
    params: {
      page: params.page ?? 1,
      size: params.size ?? 10,
      status: params.status,
    },
  })

export const getSecondhandReportDetail = (id) =>
  request({
    url: `/api/secondhand/reports/${id}`,
    method: 'get',
  })

export const handleSecondhandReport = (id, data) =>
  request({
    url: `/api/secondhand/reports/${id}/handle`,
    method: 'put',
    data,
  })

export const getSecondhandReportStatistics = () =>
  request({
    url: '/api/secondhand/reports/statistics',
    method: 'get',
  })
