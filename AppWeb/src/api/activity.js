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
