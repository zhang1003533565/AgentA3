import request from '../utils/request'

// 用户注册
export const register = (data) => {
  return request({
    url: '/api/auth/register',
    method: 'post',
    data
  })
}

// 用户登录
export const login = (data) => {
  return request({
    url: '/api/auth/weblogin',
    method: 'post',
    data
  })
}

// 获取用户信息（示例）
export const getUserInfo = () => {
  return request({
    url: '/api/user/info',
    method: 'get'
  })
}

// 退出登录
export const logout = () => {
  return request({
    url: '/api/user/logout',
    method: 'post'
  })
}
