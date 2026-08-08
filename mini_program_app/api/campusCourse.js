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

export function getChapterDetail(courseId, chapterId) {
  return request({
    url: `/api/app/campus-courses/${encodeURIComponent(courseId)}/chapters/${encodeURIComponent(chapterId)}`,
    method: 'GET'
  })
}

export function getMyCourses() {
  return request({
    url: '/api/app/campus-courses/my',
    method: 'GET'
  })
}

export function enrollCourse(courseId) {
  return request({
    url: `/api/app/campus-courses/${encodeURIComponent(courseId)}/enroll`,
    method: 'POST'
  })
}

export function unenrollCourse(courseId) {
  return request({
    url: `/api/app/campus-courses/${encodeURIComponent(courseId)}/enroll`,
    method: 'DELETE'
  })
}

/** 轻量检查：某章节的视频/Word/附件是否存在 */
export function getChapterResources(courseId, chapterId) {
  return request({
    url: `/api/app/campus-courses/${encodeURIComponent(courseId)}/chapters/${encodeURIComponent(chapterId)}/resources`,
    method: 'GET'
  })
}

/** 获取 Word 文档指定分页内容，page 从 1 开始，size 默认 500 */
export function getWordContent(courseId, chapterId, materialId, page = 1, size = 500) {
  return request({
    url: `/api/app/campus-courses/${encodeURIComponent(courseId)}/chapters/${encodeURIComponent(chapterId)}/word/${encodeURIComponent(materialId)}/content`,
    method: 'GET',
    data: { page, size }
  })
}
