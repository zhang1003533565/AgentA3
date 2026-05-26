import request from '../utils/request'

export const getSystemConfigList = (params = {}) =>
  request({
    url: '/api/system-config/list',
    method: 'get',
    params: {
      current: params.current ?? params.page ?? 1,
      size: params.size ?? 10,
      keyword: params.keyword,
      group: params.group,
      prefixes: params.prefixes,
    },
  })

export const updateSystemConfig = (id, data) =>
  request({
    url: `/api/system-config/${id}`,
    method: 'put',
    data,
  })

export const deleteSystemConfig = (id) =>
  request({
    url: `/api/system-config/${id}`,
    method: 'delete',
  })

export const upsertSystemConfig = (data) =>
  request({
    url: '/api/system-config/upsert',
    method: 'post',
    data,
  })

export const testSystemConfig = (id) =>
  request({
    url: `/api/system-config/${id}/test`,
    method: 'post',
  })
