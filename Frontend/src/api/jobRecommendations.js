import { request } from './request'

/** 岗位雷达不展示 AI 编造薪资，真实薪资仅在 BOSS 直聘搜索结果中可见。 */
export const JOB_SALARY_HINT = '真实薪资在 BOSS 直聘'
export const JOB_BOSS_CTA = '查看薪资与岗位'

export function resolveBossJobSearchLink(jobTitle) {
  const keyword = String(jobTitle || '软件工程师').trim() || '软件工程师'
  return `https://www.zhipin.com/web/geek/job?query=${encodeURIComponent(keyword)}`
}

export function resolveBossJobSearchLinkFromJob(job) {
  return resolveBossJobSearchLink(job?.jobTitle)
}

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
