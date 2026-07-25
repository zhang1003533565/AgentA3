import { request } from '@/utils/request'

export function getFacilityTypes() {
  return request({
    url: '/api/v1/facility/types',
    method: 'GET',
  })
}

export function getFacilityDetail(id) {
  return request({
    url: `/api/v1/facility/${id}`,
    method: 'GET',
  })
}

export function parseFacilityImages(images) {
  if (Array.isArray(images)) return images.filter(Boolean)
  if (!images) return []
  if (typeof images === 'string') {
    try {
      const parsed = JSON.parse(images)
      return Array.isArray(parsed) ? parsed.filter(Boolean) : []
    } catch (error) {
      return []
    }
  }
  return []
}
