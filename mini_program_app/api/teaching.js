import { request } from '@/utils/request'

export function getTeachingBuildings() {
  return request({ url: '/api/v1/teaching/buildings', method: 'GET' })
}

export function getTeachingBuilding(id) {
  return request({ url: `/api/v1/teaching/buildings/${id}`, method: 'GET' })
}
