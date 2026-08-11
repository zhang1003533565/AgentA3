import { request } from '../utils/request.js'

export function getCampusCourses() {
  return request({ url: '/api/app/campus-courses', method: 'GET' })
}

/** 分页获取校园课程（触底加载更多），page 从 1 开始 */
export function getCampusCoursesPage(page = 1, size = 8) {
  return request({
    url: '/api/app/campus-courses',
    method: 'GET',
    data: { page, size }
  })
}

/** 获取课程类型列表（category: BUILTIN-必选类型 / CUSTOM-自定义类型） */
export function getCampusCourseTypes() {
  return request({ url: '/api/app/campus-courses/types', method: 'GET' })
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

/** 按需获取单个材料的访问 URL（含 fileUrl），用于视频播放、附件下载等场景 */
export function getMaterialUrl(courseId, chapterId, materialId) {
  return request({
    url: `/api/app/campus-courses/${encodeURIComponent(courseId)}/chapters/${encodeURIComponent(chapterId)}/materials/${encodeURIComponent(materialId)}/url`,
    method: 'GET'
  })
}
