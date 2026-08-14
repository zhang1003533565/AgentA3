import { request } from './request'

const unwrap = (promise) => promise.then((response) => response.data)

export const getCurrentSchedule = (params = {}) => unwrap(request({
  url: params.week ? `/api/schedule/week/${encodeURIComponent(params.week)}` : '/api/schedule/current',
  params: {
    academicYear: params.academicYear,
    semesterTerm: params.semesterTerm,
  },
}))
export const getAllSchedules = (params = {}) => unwrap(request({ url: '/api/schedule', params }))
export const importScheduleAuto = (data) => unwrap(request({
  url: '/api/browser/jwx/schedule/auto',
  method: 'POST',
  data,
}))
export const getScheduleImportProgress = () => unwrap(request({
  url: '/api/browser/jwx/schedule/import-progress',
}))
export const getSchedulePeriods = () => unwrap(request({ url: '/api/schedule/periods' }))
export const updateSchedulePeriods = (data) => unwrap(request({
  url: '/api/schedule/periods',
  method: 'PUT',
  data,
}))
export const copyScheduleByShareCode = (shareCode, semester = {}) => unwrap(request({
  url: '/api/schedule/copy',
  method: 'POST',
  data: { shareCode, academicYear:semester.academicYear, semesterTerm:semester.semesterTerm },
}))
export const checkJwxBind = () => unwrap(request({ url: '/api/browser/jwx/user/check-jwx-bind' }))
export const getScheduleSettings = () => unwrap(request({ url: '/api/schedule/settings' }))
export const updateScheduleSettings = (data) => unwrap(request({
  url: '/api/schedule/settings',
  method: 'PUT',
  data,
}))
export const clearSemesterSchedule = (academicYear, semesterTerm) => unwrap(request({
  url: `/api/schedule/settings/semesters/${encodeURIComponent(academicYear)}/${encodeURIComponent(semesterTerm)}/courses`,
  method: 'DELETE',
}))
export const deleteScheduleSemester = (academicYear, semesterTerm) => unwrap(request({
  url: `/api/schedule/settings/semesters/${encodeURIComponent(academicYear)}/${encodeURIComponent(semesterTerm)}`,
  method: 'DELETE',
}))
