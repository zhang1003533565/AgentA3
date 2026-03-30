import request from '../utils/request'

// ========== 商家分类 ==========

export const getMerchantCategoryList = () =>
  request({
    url: '/api/merchant/category/list',
    method: 'get',
  })

export const createMerchantCategory = (data) =>
  request({
    url: '/api/merchant/category',
    method: 'post',
    data,
  })

export const updateMerchantCategory = (id, data) =>
  request({
    url: `/api/merchant/category/${id}`,
    method: 'put',
    data,
  })

export const deleteMerchantCategory = (id) =>
  request({
    url: `/api/merchant/category/${id}`,
    method: 'delete',
  })

// ========== 商家管理 ==========

export const getMerchantList = (params = {}) =>
  request({
    url: '/api/merchant/list',
    method: 'get',
    params: {
      current: params.current ?? params.page ?? 1,
      size: params.size ?? 10,
      categoryId: params.categoryId,
      keyword: params.keyword,
      status: params.status,
      lat: params.lat,
      lng: params.lng,
      sort: params.sort,
    },
  })

export const getMerchantDetail = (id) =>
  request({
    url: `/api/merchant/${id}`,
    method: 'get',
  })

export const createMerchant = (data) =>
  request({
    url: '/api/merchant',
    method: 'post',
    data,
  })

export const updateMerchant = (id, data) =>
  request({
    url: `/api/merchant/${id}`,
    method: 'put',
    data,
  })

export const deleteMerchant = (id) =>
  request({
    url: `/api/merchant/${id}`,
    method: 'delete',
  })

export const updateMerchantStatus = (id, data) =>
  request({
    url: `/api/merchant/${id}/status`,
    method: 'put',
    data,
  })

export const getMerchantStatistics = (params = {}) =>
  request({
    url: '/api/merchant/statistics',
    method: 'get',
    params,
  })

// ========== 评价管理 ==========

export const getMerchantReviewAdminList = (params = {}) =>
  request({
    url: '/api/merchant/review/admin/list',
    method: 'get',
    params: {
      current: params.current ?? params.page ?? 1,
      size: params.size ?? 10,
      merchantId: params.merchantId,
      status: params.status,
    },
  })

export const getMerchantReviewList = (merchantId, params = {}) =>
  request({
    url: `/api/merchant/review/list/${merchantId}`,
    method: 'get',
    params: {
      current: params.current ?? params.page ?? 1,
      size: params.size ?? 10,
      score: params.score,
    },
  })

export const createMerchantReview = (data) =>
  request({
    url: '/api/merchant/review',
    method: 'post',
    data,
  })

export const deleteMerchantReview = (id) =>
  request({
    url: `/api/merchant/review/${id}`,
    method: 'delete',
  })
