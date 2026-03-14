import request from '../utils/request'

// 获取分类列表
export const getCategoryList = () => {
  return request({
    url: '/api/categories',
    method: 'get'
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
