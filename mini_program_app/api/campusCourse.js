import { request } from '../utils/request.js'

export function getCampusCourses() {
  return request({ url: '/api/app/campus-courses', method: 'GET' })
}

export function getCampusCourseDetail(courseId) {
  return request({
    url: `/api/app/campus-courses/${encodeURIComponent(courseId)}`,
    method: 'GET'
  })
}

export function updateCampusChapterProgress(courseId, chapterId, completed) {
  return request({
    url: `/api/app/campus-courses/${encodeURIComponent(courseId)}/chapters/${encodeURIComponent(chapterId)}/progress`,
    method: 'PUT',
    data: { completed }
  })
}
