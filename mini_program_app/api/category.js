import { request } from '../utils/request.js'

export function getCategoryList() {
  return request({
    url: '/api/categories',
    method: 'GET'
  })
}
