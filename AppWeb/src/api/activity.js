import request from '../utils/request'

// 获取活动列表
export const getActivityList = (params) => {
  return request({
    url: '/api/activities',
    method: 'get',
    params
  })
}

// 获取活动详情
export const getActivityDetail = (id) => {
  return request({
    url: `/api/activities/${id}`,
    method: 'get'
  })
}

// 创建活动
export const createActivity = (data) => {
  return request({
    url: '/api/activities',
    method: 'post',
    data
  })
}

// 更新活动
export const updateActivity = (id, data) => {
  return request({
    url: `/api/activities/${id}`,
    method: 'put',
    data
  })
}

// 删除活动
export const deleteActivity = (id) => {
  return request({
    url: `/api/activities/${id}`,
    method: 'delete'
  })
}

// 上架/下架活动
export const toggleActivityStatus = (id, status) => {
  return request({
    url: `/api/activities/${id}/status`,
    method: 'put',
    params: { status }
  })
}

// 获取分类列表
export const getCategoryList = () => {
  return request({
    url: '/api/categories',
    method: 'get'
  })
}

// 批量删除活动
export const batchDeleteActivity = (ids) => {
  return request({
    url: '/api/activities/batch',
    method: 'delete',
    data: ids
  })
}

// 搜索活动
export const searchActivities = (params) => {
  return request({
    url: '/api/activities/search',
    method: 'get',
    params
  })
}

// 筛选活动
export const filterActivities = (params) => {
  return request({
    url: '/api/activities/filter',
    method: 'get',
    params
  })
}

// 发布活动
export const publishActivity = (id) => {
  return request({
    url: `/api/activities/publish/${id}`,
    method: 'post'
  })
}
