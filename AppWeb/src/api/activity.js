import request from '../utils/request'

// ========== 活动管理（管理员 / 教师） ==========

export const getActivityList = (params = {}) => {
  const { page = 1, size = 10, title, categoryId, status } = params
  return request({
    url: '/api/activities',
    method: 'get',
    params: {
      page,
      size,
      title: title ?? params.keyword,
      categoryId,
      status,
    },
  })
}

export const getActivityDetail = (id) =>
  request({
    url: `/api/activities/${id}`,
    method: 'get',
  })

export const createActivity = (data) =>
  request({
    url: '/api/activities',
    method: 'post',
    data,
  })

export const updateActivity = (id, data) =>
  request({
    url: `/api/activities/${id}`,
    method: 'put',
    data,
  })

export const deleteActivity = (id) =>
  request({
    url: `/api/activities/${id}`,
    method: 'delete',
  })

export const batchDeleteActivity = (ids) =>
  request({
    url: '/api/activities/batch',
    method: 'delete',
    data: ids,
  })

export const updateActivityStatus = (id, status) =>
  request({
    url: `/api/activities/${id}/status`,
    method: 'put',
    params: { status },
  })

export const toggleActivityStatus = updateActivityStatus

export const publishActivity = (id) =>
  request({
    url: `/api/activities/publish/${id}`,
    method: 'post',
  })

export const searchActivities = (params = {}) =>
  request({
    url: '/api/activities/search',
    method: 'get',
    params,
  })

export const filterActivities = (params = {}) =>
  request({
    url: '/api/activities/filter',
    method: 'get',
    params,
  })

// ========== 活动收藏（如需要） ==========

export const addActivityFavorite = (id) =>
  request({
    url: `/api/activities/${id}/favorite`,
    method: 'post',
  })

export const removeActivityFavorite = (id) =>
  request({
    url: `/api/activities/${id}/favorite`,
    method: 'delete',
  })

export const getMyActivityFavorites = (params = {}) =>
  request({
    url: '/api/activities/favorites',
    method: 'get',
    params,
  })

export const getActivityFavoriteStatus = (id) =>
  request({
    url: `/api/activities/${id}/favorite/status`,
    method: 'get',
  })

// ========== 兼容旧引用 ==========

export const getCategoryList = () =>
  request({
    url: '/api/categories',
    method: 'get',
  })
