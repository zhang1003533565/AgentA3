import { request } from '@/utils/request.js'
import { streamSse } from './ai.js'

export const getPythonHome = () => request({
  url: '/api/app/learning/courses/python/home',
  method: 'GET'
})

export const submitProfileAnswer = data => request({
  url: '/api/app/learning/courses/python/profile-answers',
  method: 'POST',
  data
})

export const getPythonPath = () => request({
  url: '/api/app/learning/courses/python/path',
  method: 'GET'
})

export const getPythonRecommendations = () => request({
  url: '/api/app/learning/courses/python/recommendations',
  method: 'GET'
})

export const getLearningWorkflow = workflowId => request({
  url: `/api/app/learning/workflows/${encodeURIComponent(workflowId)}`,
  method: 'GET'
})

export const retryLearningResource = (workflowId, resourceType) => request({
  url: `/api/app/learning/workflows/${encodeURIComponent(workflowId)}/resources/${encodeURIComponent(resourceType)}/retry`,
  method: 'POST'
})

export const startPathItem = itemId => request({
  url: `/api/app/learning/path-items/${encodeURIComponent(itemId)}/start`,
  method: 'POST'
})

export const completePathItem = itemId => request({
  url: `/api/app/learning/path-items/${encodeURIComponent(itemId)}/complete`,
  method: 'POST'
})

export const replanPythonPath = () => request({
  url: '/api/app/learning/courses/python/path/replan',
  method: 'POST'
})

export const recordRecommendationInteraction = (itemId, data) => request({
  url: `/api/app/learning/recommendations/${encodeURIComponent(itemId)}/interactions`,
  method: 'POST',
  data
})

export const streamLearningResources = (data, handlers) => streamSse(
  '/api/app/learning/resources/generate/stream',
  data,
  handlers,
  '当前运行环境无法读取学习资源生成进度'
)
