import { request } from './request'

const unwrap = (promise) => promise.then((response) => response.data)

export const getPromotions = (params = {}) => unwrap(request({ url: '/api/v1/promotion-coupon/list', params }))
export const getPromotion = (id) => unwrap(request({ url: `/api/v1/promotion-coupon/${id}` }))
export const getAnnouncements = () => unwrap(request({ url: '/api/announcements/enabled' }))
export const getAnnouncement = (id) => unwrap(request({ url: `/api/announcements/${id}` }))
export const getFacility = (id) => unwrap(request({ url: `/api/v1/map-places/${id}` }))
export const getTeachingBuildings = () => unwrap(request({ url: '/api/v1/teaching/buildings' }))
export const getTeachingBuilding = (id) => unwrap(request({ url: `/api/v1/teaching/buildings/${id}` }))
export const getCanteenStalls = (params = {}) => unwrap(request({ url: '/api/v1/canteen-stall/list', params }))
export const getDishes = (params = {}) => unwrap(request({ url: '/api/v1/dish/list', params }))
export const getDish = (id) => unwrap(request({ url: `/api/v1/dish/${id}` }))
export const getDishReviews = (params = {}) => unwrap(request({ url: '/api/v1/dish-review/list', params }))
export const createDishReview = (data) => unwrap(request({ url: '/api/v1/dish-review', method: 'POST', data }))
export const getSignInStatus = (activityId) => unwrap(request({ url: `/api/signins/activities/${activityId}/signin-status` }))
export const studentSignIn = (activityId) => unwrap(request({ url: `/api/signins/${activityId}`, method: 'POST' }))
