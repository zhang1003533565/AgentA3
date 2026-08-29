import request from '../utils/request'

const base = '/api/app/ai/ppt'

export const getPptTask = (taskId) => request.get(`${base}/tasks/${encodeURIComponent(taskId)}`, {
  timeout: 30000,
})
