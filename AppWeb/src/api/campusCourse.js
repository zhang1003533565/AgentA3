import request from '../utils/request'

const base = '/api/admin/campus-courses'

export const getCampusCourses = () => request.get(base)
export const getCampusCourse = (id) => request.get(`${base}/${id}`)
export const createCampusCourse = (data) => request.post(base, data)
export const updateCampusCourse = (id, data) => request.put(`${base}/${id}`, data)
export const publishCampusCourse = (id) => request.post(`${base}/${id}/publish`)
export const offlineCampusCourse = (id) => request.post(`${base}/${id}/offline`)
export const deleteCampusCourse = (id) => request.delete(`${base}/${id}`)

export const getCampusCourseTypes = () => request.get(`${base}/types`)
export const createCampusCourseType = (data) => request.post(`${base}/types`, data)

export const createCampusCourseChapter = (courseId, data) =>
  request.post(`${base}/${courseId}/chapters`, data)
export const updateCampusCourseChapter = (courseId, chapterId, data) =>
  request.put(`${base}/${courseId}/chapters/${chapterId}`, data)
export const deleteCampusCourseChapter = (courseId, chapterId) =>
  request.delete(`${base}/${courseId}/chapters/${chapterId}`)

export const linkCampusCourseExam = (courseId, data) =>
  request.post(`${base}/${courseId}/exams`, data)
export const unlinkCampusCourseExam = (courseId, linkId) =>
  request.delete(`${base}/${courseId}/exams/${linkId}`)
