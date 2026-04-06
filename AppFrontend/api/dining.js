import { request } from '@/utils/request'

export function getCanteenStallList(params = {}) {
  return request({
    url: '/api/v1/canteen-stall/list',
    method: 'GET',
    params,
  })
}

export function getDishList(params = {}) {
  return request({
    url: '/api/v1/dish/list',
    method: 'GET',
    params,
  })
}

export function getDishReviewList(params = {}) {
  return request({
    url: '/api/v1/dish-review/list',
    method: 'GET',
    params,
  })
}

export function getDishReviewCount(params = {}) {
  return request({
    url: '/api/v1/dish-review/count',
    method: 'GET',
    params,
  })
}

export function createDishReview(data) {
  return request({
    url: '/api/v1/dish-review',
    method: 'POST',
    data,
  })
}
