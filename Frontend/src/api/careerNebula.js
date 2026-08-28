import { request } from './request'

export const getCareerNebulaMap = async () => {
  const response = await request({ url: '/api/admin/career-nebula' })
  return response?.data || { careers: [], skills: [], edges: [] }
}

export const getCareerLearningProgress = async () => {
  const response = await request({ url: '/api/app/career-nebula/progress' })
  return response?.data || { completedItemIds: [] }
}

export const updateCareerLearningProgress = async (itemId, data) => {
  const response = await request({
    url: `/api/app/career-nebula/progress/items/${encodeURIComponent(itemId)}`,
    method: 'PUT',
    data,
  })
  return response?.data
}

export const getCareerExploration = async (careerId) => {
  const response = await request({
    url: `/api/app/career-exploration/careers/${encodeURIComponent(careerId)}`,
  })
  return response?.data || { career: null, planets: [], edges: [] }
}

export const getCareerPlanet = async (careerId, skillId) => {
  const response = await request({
    url: `/api/app/career-exploration/careers/${encodeURIComponent(careerId)}/planets/${encodeURIComponent(skillId)}`,
  })
  return response?.data
}

export const getCareerChapter = async (careerId, skillId, chapterId) => {
  const response = await request({
    url: `/api/app/career-exploration/careers/${encodeURIComponent(careerId)}/planets/${encodeURIComponent(skillId)}/chapters/${chapterId}`,
  })
  return response?.data
}

export const updateCareerVideoProgress = async (careerId, skillId, chapterId, data) => {
  const response = await request({
    url: `/api/app/career-exploration/careers/${encodeURIComponent(careerId)}/planets/${encodeURIComponent(skillId)}/chapters/${chapterId}/video-progress`,
    method: 'PUT', data,
  })
  return response?.data
}

export const answerCareerChapterQuestion = async (careerId, skillId, chapterId, questionId, answer) => {
  const response = await request({
    url: `/api/app/career-exploration/careers/${encodeURIComponent(careerId)}/planets/${encodeURIComponent(skillId)}/chapters/${chapterId}/questions/${encodeURIComponent(questionId)}/answer`,
    method: 'POST', data: { answer },
  })
  return response?.data
}

export const completeCareerChapter = async (careerId, skillId, chapterId) => {
  const response = await request({
    url: `/api/app/career-exploration/careers/${encodeURIComponent(careerId)}/planets/${encodeURIComponent(skillId)}/chapters/${chapterId}/complete`,
    method: 'POST',
  })
  return response?.data
}

export const getCareerFinalExam = async (careerId, skillId) => {
  const response = await request({
    url: `/api/app/career-exploration/careers/${encodeURIComponent(careerId)}/planets/${encodeURIComponent(skillId)}/final-exam`,
  })
  return response?.data
}

export const syncCareerFinalExam = async (careerId, skillId, attemptId) => {
  const response = await request({
    url: `/api/app/career-exploration/careers/${encodeURIComponent(careerId)}/planets/${encodeURIComponent(skillId)}/sync-final-exam`,
    method: 'POST', data: { attemptId },
  })
  return response?.data
}

export const enrollCareerCourse = (courseId) => request({
  url: `/api/app/campus-courses/${courseId}/enroll`, method: 'POST',
})

export const getCareerMaterialUrl = async (courseId, chapterId, materialId) => {
  const response = await request({
    url: `/api/app/campus-courses/${courseId}/chapters/${chapterId}/materials/${materialId}/url`,
  })
  return response?.data
}
