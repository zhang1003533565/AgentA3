import request from '../utils/request'

// ========== 活动 AI 辅助发布 ==========

export const generateActivityDraft = (data) =>
  request({
    url: '/api/activity/ai/generate',
    method: 'post',
    data,
    timeout: 120000,
  })
