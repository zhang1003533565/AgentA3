import { request } from './request'

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
