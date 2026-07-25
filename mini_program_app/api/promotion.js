import { request } from '@/utils/request'

export function getPromotionCouponList(params = {}) {
  return request({
    url: '/api/v1/promotion-coupon/list',
    method: 'GET',
    params
  })
}

export function getPromotionCouponDetail(id) {
  return request({
    url: `/api/v1/promotion-coupon/${id}`,
    method: 'GET'
  })
}
