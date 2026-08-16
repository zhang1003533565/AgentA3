import request from '../utils/request'

export const createDiscountActivity = (data) =>
  request({
    url: '/api/discount/activity',
    method: 'post',
    data,
  })

export const getDiscountActivityList = (params = {}) =>
  request({
    url: '/api/discount/activity/list',
    method: 'get',
    params: {
      current: params.current ?? params.page ?? 1,
      size: params.size ?? 10,
      merchantId: params.merchantId,
      categoryId: params.categoryId,
      keyword: params.keyword,
      status: params.status,
      lat: params.lat,
      lng: params.lng,
      sort: params.sort,
    },
  })

export const getDiscountActivityDetail = (id) =>
  request({
    url: `/api/discount/activity/${id}`,
    method: 'get',
  })

export const updateDiscountActivity = (id, data) =>
  request({
    url: `/api/discount/activity/${id}`,
    method: 'put',
    data,
  })

export const deleteDiscountActivity = (id) =>
  request({
    url: `/api/discount/activity/${id}`,
    method: 'delete',
  })

export const getMerchantDiscountActivities = (merchantId, params = {}) =>
  request({
    url: `/api/discount/activity/merchant/${merchantId}`,
    method: 'get',
    params: {
      current: params.current ?? params.page ?? 1,
      size: params.size ?? 10,
    },
  })

export const endDiscountActivityEarly = (id) =>
  request({
    url: `/api/discount/activity/${id}/end-early`,
    method: 'put',
  })
