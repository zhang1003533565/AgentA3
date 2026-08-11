import { request } from './request'

// ========== 优惠活动 ==========
export function getDiscountActivityList(params = {}) {
  return request({
    url: '/api/discount/activity/list',
    method: 'GET',
    params: {
      current: params.current || 1,
      size: params.size || 10,
      merchantId: params.merchantId || undefined,
      keyword: params.keyword || undefined,
      status: params.status || undefined,
      lat: params.lat || undefined,
      lng: params.lng || undefined,
      sort: params.sort || undefined,
    },
  })
}

export function getDiscountActivityDetail(id) {
  return request({ url: `/api/discount/activity/${id}`, method: 'GET' })
}

// ========== 领取 ==========
export function claimDiscountActivity(activityId) {
  return request({ url: `/api/discount/claim/${activityId}`, method: 'POST' })
}

export function getMyClaims(params = {}) {
  return request({
    url: '/api/discount/claim/my',
    method: 'GET',
    params: { current: params.current || 1, size: params.size || 10 },
  })
}

// ========== 收藏 ==========
export function favoriteActivity(activityId) {
  return request({ url: `/api/discount/favorite/${activityId}`, method: 'POST' })
}

export function unfavoriteActivity(activityId) {
  return request({ url: `/api/discount/favorite/${activityId}`, method: 'DELETE' })
}

export function getMyFavorites(params = {}) {
  return request({
    url: '/api/discount/favorite/my',
    method: 'GET',
    params: { current: params.current || 1, size: params.size || 10 },
  })
}

// ========== 商家列表(公开) ==========
export function getMerchantList(params = {}) {
  return request({
    url: '/api/merchant/list',
    method: 'GET',
    params: { current: params.current || 1, size: params.size || 500, status: 1 },
  })
}
