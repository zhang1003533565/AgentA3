import request from '../utils/request'

// 获取档口列表
export const getCanteenStallList = (params = {}) =>
  request({
    url: '/api/v1/canteen-stall/list',
    method: 'get',
    params: {
      restaurantId: params.restaurantId,
    },
  })

export const createStall = (data) =>
  request({
    url: '/api/v1/canteen-stall',
    method: 'post',
    data,
  })

export const updateStall = (id, data) =>
  request({
    url: `/api/v1/canteen-stall/${id}`,
    method: 'put',
    data,
  })

export const deleteStall = (id) =>
  request({
    url: `/api/v1/canteen-stall/${id}`,
    method: 'delete',
  })

export const getDishList = (params = {}) =>
  request({
    url: '/api/v1/dish/list',
    method: 'get',
    params: {
      stallId: params.stallId,
      stallPlaceId: params.stallPlaceId,
      category: params.category,
      taste: params.taste,
      name: params.name ?? params.keyword,
    },
  })

export const getDishDetail = (id) =>
  request({
    url: `/api/v1/dish/${id}`,
    method: 'get',
  })

export const createDish = (data) =>
  request({
    url: '/api/v1/dish',
    method: 'post',
    data,
  })

export const updateDish = (id, data) =>
  request({
    url: `/api/v1/dish/${id}`,
    method: 'put',
    data,
  })

export const deleteDish = (id) =>
  request({
    url: `/api/v1/dish/${id}`,
    method: 'delete',
  })

export const getDishCuisines = (canteenPlaceId) =>
  request({
    url: '/api/v1/dish-cuisines',
    method: 'get',
    params: { canteenPlaceId },
  })

export const createDishCuisine = (data) =>
  request({
    url: '/api/v1/dish-cuisines',
    method: 'post',
    data,
  })

export const updateDishCuisine = (id, data) =>
  request({
    url: `/api/v1/dish-cuisines/${id}`,
    method: 'put',
    data,
  })

export const deleteDishCuisine = (id) =>
  request({
    url: `/api/v1/dish-cuisines/${id}`,
    method: 'delete',
  })
