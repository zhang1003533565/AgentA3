import { request } from './request'

export function login(data) {
  return request({
    url: '/api/auth/applogin',
    method: 'POST',
    data: {
      username: data.username,
      password: data.password,
    },
  })
}

export function register(data) {
  return request({
    url: '/api/auth/register',
    method: 'POST',
    data,
  })
}

