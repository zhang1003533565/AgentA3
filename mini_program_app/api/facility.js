import { request } from '@/utils/request'

const SCENE_FACILITY_TYPE = {
  CANTEEN: 1,
  SPORTS: 2,
  TEACHING: 3,
  DORMITORY: 4,
}

const adaptRequest = (task, transform) => {
  const adapted = task.then(transform)
  adapted.abort = (...args) => task.abort?.(...args)
  adapted.done = adapted
  return adapted
}

export function getFacilityTypes() {
  const task = Promise.resolve({
    code: 200,
    data: [
      { value: 1, label: '食堂' },
      { value: 2, label: '运动场' },
      { value: 3, label: '教学楼' },
      { value: 4, label: '宿舍' },
    ],
  })
  task.abort = () => false
  task.done = task
  return task
}

export function getFacilityDetail(id) {
  const task = request({
    url: `/api/v1/map-places/${id}`,
    method: 'GET',
  })
  return adaptRequest(task, (response) => {
    const place = response.data || {}
    const imageUrls = (place.images || []).map((item) => item.imageUrl).filter(Boolean)
    return {
      ...response,
      data: {
        ...place,
        facilityName: place.name,
        facilityType: SCENE_FACILITY_TYPE[place.sceneType] || 99,
        location: place.locationDesc,
        status: place.status === 'ENABLED' ? 1 : 3,
        images: JSON.stringify(imageUrls),
        geometryType: place.fence?.geometryType,
        boundaryPoints: place.fence?.geometryData,
      },
    }
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
