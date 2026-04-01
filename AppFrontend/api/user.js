/**
 * 用户相关接口
 */
import { request } from '../utils/request.js'

/** 登录 */
export function login(data) {
  return request({
    url: '/api/auth/applogin',
    method: 'POST',
    data: {
      username: data.username,
      password: data.password
    }
  })
}

/** 注册 */
export function register(data) {
  const body = {
    username: data.username,
    password: data.password
  }
  if (data.email) body.email = data.email
  if (data.phone) body.phone = data.phone
  return request({
    url: '/api/auth/register',
    method: 'POST',
    data: body
  })
}

/** 修改密码 */
export function updatePassword(data) {
  return request({
    url: '/api/auth/password',
    method: 'PUT',
    data
  })
}
