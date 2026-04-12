import { request } from '@/utils/request'
import { BASE_URL } from '@/utils/config.js'
import { getToken } from '@/utils/storage.js'

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

export function getDishReviewSummary(params = {}) {
  return request({
    url: '/api/v1/dish-review/summary',
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

export function getDishDetail(dishId) {
  return request({
    url: `/api/v1/dish/${dishId}`,
    method: 'GET',
  })
}

export function uploadDiningImage(filePath) {
  const token = getToken()
  return new Promise((resolve, reject) => {
    uni.uploadFile({
      url: `${BASE_URL}/api/upload/image`,
      filePath,
      name: 'file',
      header: token ? { Authorization: `Bearer ${token}` } : {},
      success: (res) => {
        try {
          const data = JSON.parse(res.data || '{}')
          if (res.statusCode >= 200 && res.statusCode < 300 && data.code === 200) {
            resolve(data.data?.url || '')
            return
          }
          reject(data)
        } catch (error) {
          reject(error)
        }
      },
      fail: reject
    })
  })
}
