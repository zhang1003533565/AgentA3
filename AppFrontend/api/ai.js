import { request } from '@/utils/request.js'

export function writeWithAi(data) {
  return request({
    url: '/api/ai/write',
    method: 'POST',
    data
  })
}
