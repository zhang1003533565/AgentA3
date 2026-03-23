import request from '../utils/request'

// 获取分类列表
export const getCategoryList = (params = {}) => {
  return request({
    url: '/api/categories',
    method: 'get',
    params
  })
}

// 创建分类
export const createCategory = (data) => {
  return request({
    url: '/api/categories',
    method: 'post',
    data
  })
}

// 更新分类
export const updateCategory = (id, data) => {
  return request({
    url: `/api/categories/${id}`,
    method: 'put',
    data
  })
}

// 删除分类
export const deleteCategory = (id) => {
  return request({
    url: `/api/categories/${id}`,
    method: 'delete'
  })
}

// 批量删除分类
export const batchDeleteCategory = (ids) => {
  return request({
    url: '/api/categories/batch',
    method: 'delete',
    data: ids
  })
}
