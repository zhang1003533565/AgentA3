import request from '../utils/request'

export const getMeetingList = (params = {}) =>
  request({
    url: '/api/meetings',
    method: 'get',
    params: {
      pageNum: params.pageNum ?? params.page ?? 1,
      pageSize: params.pageSize ?? params.size ?? 10,
      keyword: params.keyword,
    },
  })

export const getMeetingDetail = (sessionId) =>
  request({
    url: `/api/meetings/${encodeURIComponent(sessionId)}`,
    method: 'get',
  })
