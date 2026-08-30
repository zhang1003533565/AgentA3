import { request } from './request'

/** 岗位雷达不展示 AI 编造薪资，统一引导到 BOSS 直聘。 */
export const JOB_SALARY_HINT = '薪资请前往 BOSS 直聘查看'

export function getLatestJobRecommendations() {
  return request({ url: '/api/app/job-recommendations/latest' })
}

export function refreshJobRecommendations() {
  return request({ url: '/api/app/job-recommendations/refresh', method: 'POST' })
}

export function getJobFavoriteIds() {
  return request({ url: '/api/app/job-favorites/ids' })
}

export function listJobFavorites() {
  return request({ url: '/api/app/job-favorites' })
}

export function addJobFavorite(recommendationId) {
  return request({ url: `/api/app/job-favorites/${recommendationId}`, method: 'POST' })
}

export function removeJobFavorite(recommendationId) {
  return request({ url: `/api/app/job-favorites/${recommendationId}`, method: 'DELETE' })
}
