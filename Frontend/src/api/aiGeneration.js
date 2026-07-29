import { request } from './request'

const unwrap = (promise) => promise.then((response) => response.data)

export const writeWithAi = (data) => unwrap(request({ url: '/api/ai/write', method: 'POST', data }))
export const generateImage = (data) => unwrap(request({ url: '/api/ai/images/generate', method: 'POST', data }))
export const getImageTask = (id) => unwrap(request({ url: `/api/ai/images/tasks/${encodeURIComponent(id)}` }))
export const queryLeaderAgent = (data) => unwrap(request({
  url: '/api/ai/leader/query',
  method: 'POST',
  data: {
    ...data,
    agentName: 'leader_agent',
    metadata: { source: 'web_ai_tools', ...(data.metadata || {}) },
  },
}))
export const getProfileRadar = () => unwrap(request({ url: '/api/profile/radar/my' }))
