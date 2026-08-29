import { request } from './request'

const base = '/api/app/ai/ppt'

export const getPptOptions = () => request({
  url: `${base}/options`,
  method: 'GET',
  timeout: 30000,
})

export const getPptTask = (taskId) => request({
  url: `${base}/tasks/${encodeURIComponent(taskId)}`,
  method: 'GET',
  timeout: 30000,
})
