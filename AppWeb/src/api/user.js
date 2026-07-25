import request from '../utils/request'

// ========== 认证相关 ==========

// 用户登录
export const login = (data) => {
  return request({
    url: '/api/auth/weblogin',
    method: 'post',
    data,
    skipGlobalErrorMessage: true,
    skipAuthRedirect: true
  })
}

// 获取当前用户信息
export const getCurrentUser = (id) => {
  return request({
    url: `/api/auth/current/${id}`,
    method: 'get'
  })
}

// 修改密码
export const updatePassword = (data) => {
  return request({
    url: '/api/auth/password',
    method: 'put',
    data
  })
}

// ========== 用户管理（管理端） ==========

// 获取用户列表
export const getUserList = (params = {}) => {
  return request({
    url: '/api/users',
    method: 'get',
    params
  })
}

// 更新用户
export const updateUser = (id, data) => {
  return request({
    url: `/api/users/${id}`,
    method: 'put',
    data
  })
}

// 启用用户
export const enableUser = (id) => {
  return request({
    url: `/api/users/${id}/enable`,
    method: 'put'
  })
}

// 禁用用户
export const disableUser = (id) => {
  return request({
    url: `/api/users/${id}/disable`,
    method: 'put'
  })
}

// 重置密码
export const resetPassword = (id, newPassword) => {
  return request({
    url: `/api/users/${id}/reset-password`,
    method: 'post',
    data: { newPassword }
  })
}

// 退出登录
export const logout = () => Promise.resolve({ code: 200, data: null, msg: 'ok' })
